package pdfserver.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yubico.webauthn.AssertionRequest;
import com.yubico.webauthn.AssertionResult;
import com.yubico.webauthn.FinishAssertionOptions;
import com.yubico.webauthn.FinishRegistrationOptions;
import com.yubico.webauthn.RegistrationResult;
import com.yubico.webauthn.RelyingParty;
import com.yubico.webauthn.StartAssertionOptions;
import com.yubico.webauthn.StartRegistrationOptions;
import com.yubico.webauthn.data.AuthenticatorAssertionResponse;
import com.yubico.webauthn.data.AuthenticatorAttestationResponse;
import com.yubico.webauthn.data.ByteArray;
import com.yubico.webauthn.data.ClientAssertionExtensionOutputs;
import com.yubico.webauthn.data.ClientRegistrationExtensionOutputs;
import com.yubico.webauthn.data.PublicKeyCredential;
import com.yubico.webauthn.data.PublicKeyCredentialCreationOptions;
import com.yubico.webauthn.data.UserIdentity;
import com.yubico.webauthn.data.UserVerificationRequirement;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pdfserver.audit.AuditLogService;
import pdfserver.auth.dto.LoginFinishResponse;
import pdfserver.auth.dto.LoginStartRequest;
import pdfserver.auth.dto.RegisterStartRequest;
import pdfserver.auth.dto.WebAuthnFinishRequest;
import pdfserver.auth.dto.WebAuthnStartResponse;
import pdfserver.config.AuthProperties;
import pdfserver.config.JwtService;
import pdfserver.config.WebAuthnProperties;
import pdfserver.user.User;
import pdfserver.user.UserRepository;
import pdfserver.user.UserRole;
import pdfserver.user.UserStatus;
import pdfserver.user.WebAuthnCredential;
import pdfserver.user.WebAuthnCredentialRepository;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class WebAuthnService {

    private final RelyingParty relyingParty;
    private final UserRepository userRepository;
    private final WebAuthnCredentialRepository credentialRepository;
    private final WebAuthnChallengeStore challengeStore;
    private final JwtService jwtService;
    private final AuditLogService auditLogService;
    private final AuthProperties authProperties;
    private final WebAuthnProperties webAuthnProperties;
    private final ObjectMapper objectMapper;
    private final SecureRandom secureRandom = new SecureRandom();

    public WebAuthnService(RelyingParty relyingParty,
                           UserRepository userRepository,
                           WebAuthnCredentialRepository credentialRepository,
                           WebAuthnChallengeStore challengeStore,
                           JwtService jwtService,
                           AuditLogService auditLogService,
                           AuthProperties authProperties,
                           WebAuthnProperties webAuthnProperties,
                           ObjectMapper objectMapper) {
        this.relyingParty = relyingParty;
        this.userRepository = userRepository;
        this.credentialRepository = credentialRepository;
        this.challengeStore = challengeStore;
        this.jwtService = jwtService;
        this.auditLogService = auditLogService;
        this.authProperties = authProperties;
        this.webAuthnProperties = webAuthnProperties;
        this.objectMapper = objectMapper;
    }

    private UserVerificationRequirement parseUserVerification(String val) {
        if ("required".equalsIgnoreCase(val)) {
            return UserVerificationRequirement.REQUIRED;
        } else if ("discouraged".equalsIgnoreCase(val)) {
            return UserVerificationRequirement.DISCOURAGED;
        }
        return UserVerificationRequirement.PREFERRED;
    }

    @Transactional
    public WebAuthnStartResponse startRegistration(RegisterStartRequest request, UUID authenticatedUserId) {
        String email = request.email().trim().toLowerCase();
        Optional<User> existingUser = userRepository.findByEmailIgnoreCase(email);

        User user;
        if (existingUser.isPresent()) {
            user = existingUser.get();
            List<WebAuthnCredential> creds = credentialRepository.findByUserId(user.getId());
            boolean hasActiveCred = creds.stream().anyMatch(c -> c.getRevokedAt() == null);
            if (hasActiveCred) {
                // Second credential rule: Adding another requires valid JWT for that same user
                if (authenticatedUserId == null || !authenticatedUserId.equals(user.getId())) {
                    throw new AccessDeniedException("Adding an additional key requires authentication as the key owner");
                }
            }
        } else {
            // New user registration
            if (!authProperties.openRegistration()) {
                throw new AccessDeniedException("Registration is closed");
            }
            byte[] userHandle = new byte[32];
            secureRandom.nextBytes(userHandle);

            String displayName = (request.displayName() != null && !request.displayName().isBlank())
                    ? request.displayName().trim()
                    : email;

            user = User.builder()
                    .email(email)
                    .displayName(displayName)
                    .userHandle(userHandle)
                    .role(UserRole.USER)
                    .status(UserStatus.ACTIVE)
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();
            user = userRepository.save(user);
        }

        UserIdentity userIdentity = UserIdentity.builder()
                .name(user.getEmail())
                .displayName(user.getDisplayName())
                .id(new ByteArray(user.getUserHandle()))
                .build();

        com.yubico.webauthn.data.AuthenticatorSelectionCriteria authSelection =
                com.yubico.webauthn.data.AuthenticatorSelectionCriteria.builder()
                        .userVerification(parseUserVerification(webAuthnProperties.userVerification()))
                        .build();

        StartRegistrationOptions startOptions = StartRegistrationOptions.builder()
                .user(userIdentity)
                .authenticatorSelection(authSelection)
                .build();

        PublicKeyCredentialCreationOptions creationOptions = relyingParty.startRegistration(startOptions);
        String requestId = UUID.randomUUID().toString();

        try {
            String serializedOptions = creationOptions.toJson();
            challengeStore.save(requestId, serializedOptions);

            JsonNode optionsNode = objectMapper.readTree(serializedOptions);
            return new WebAuthnStartResponse(requestId, optionsNode);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize registration options", e);
        }
    }

    @Transactional
    public void finishRegistration(WebAuthnFinishRequest request, String ip, String userAgent, String correlationId) {
        String requestId = request.requestId();
        String serializedOptions = challengeStore.getAndRemove(requestId)
                .orElseThrow(() -> new BadCredentialsException("Invalid or expired registration request ID"));

        PublicKeyCredentialCreationOptions creationOptions;
        PublicKeyCredential<AuthenticatorAttestationResponse, ClientRegistrationExtensionOutputs> pkc;
        try {
            creationOptions = PublicKeyCredentialCreationOptions.fromJson(serializedOptions);
            String credJson = objectMapper.writeValueAsString(request.credential());
            pkc = PublicKeyCredential.parseRegistrationResponseJson(credJson);
        } catch (Exception e) {
            throw new BadCredentialsException("Malformed registration credential", e);
        }

        RegistrationResult result;
        try {
            result = relyingParty.finishRegistration(FinishRegistrationOptions.builder()
                    .request(creationOptions)
                    .response(pkc)
                    .build());
        } catch (Exception e) {
            throw new BadCredentialsException("WebAuthn registration verification failed: " + e.getMessage(), e);
        }

        ByteArray userHandle = creationOptions.getUser().getId();
        User user = userRepository.findByUserHandle(userHandle.getBytes())
                .orElseThrow(() -> new BadCredentialsException("User not found for registration response"));

        WebAuthnCredential credential = WebAuthnCredential.builder()
                .user(user)
                .credentialId(result.getKeyId().getId().getBytes())
                .publicKey(result.getPublicKeyCose().getBytes())
                .signCount(result.getSignatureCount())
                .aaguid(result.getAaguid() != null && result.getAaguid().getBytes().length == 16
                        ? uuidFromBytes(result.getAaguid().getBytes())
                        : null)
                .label("Security Key")
                .createdAt(Instant.now())
                .build();

        credentialRepository.save(credential);

        Map<String, Object> auditDetails = new HashMap<>();
        auditDetails.put("action", "register");
        auditDetails.put("userAgent", userAgent);
        auditDetails.put("credentialId", result.getKeyId().getId().getBase64Url());
        auditLogService.log("USER_REGISTER", user.getId(), ip, correlationId, auditDetails);
    }

    public WebAuthnStartResponse startLogin(LoginStartRequest request) {
        String email = request.email().trim().toLowerCase();
        Optional<User> userOpt = userRepository.findByEmailIgnoreCase(email);

        StartAssertionOptions.StartAssertionOptionsBuilder builder = StartAssertionOptions.builder()
                .userVerification(parseUserVerification(webAuthnProperties.userVerification()));

        if (userOpt.isPresent()) {
            builder.username(userOpt.get().getEmail());
        }

        AssertionRequest assertionRequest = relyingParty.startAssertion(builder.build());
        String requestId = UUID.randomUUID().toString();

        try {
            String serializedRequest = assertionRequest.toJson();
            challengeStore.save(requestId, serializedRequest);

            JsonNode optionsNode = objectMapper.readTree(assertionRequest.toCredentialsGetJson());
            return new WebAuthnStartResponse(requestId, optionsNode);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize assertion request", e);
        }
    }

    @Transactional
    public LoginFinishResponse finishLogin(WebAuthnFinishRequest request, String ip, String userAgent, String correlationId) {
        String requestId = request.requestId();
        String serializedRequest = challengeStore.getAndRemove(requestId)
                .orElseThrow(() -> {
                    Map<String, Object> details = Map.of("reason", "expired_or_invalid_request_id", "userAgent", userAgent != null ? userAgent : "");
                    auditLogService.log("LOGIN_FAILURE", null, ip, correlationId, details);
                    return new BadCredentialsException("Invalid or expired authentication request");
                });

        AssertionRequest assertionRequest;
        PublicKeyCredential<AuthenticatorAssertionResponse, ClientAssertionExtensionOutputs> pkc;
        try {
            assertionRequest = AssertionRequest.fromJson(serializedRequest);
            String credJson = objectMapper.writeValueAsString(request.credential());
            pkc = PublicKeyCredential.parseAssertionResponseJson(credJson);
        } catch (Exception e) {
            Map<String, Object> details = Map.of("reason", "malformed_credential", "userAgent", userAgent != null ? userAgent : "");
            auditLogService.log("LOGIN_FAILURE", null, ip, correlationId, details);
            throw new BadCredentialsException("Invalid or expired authentication request", e);
        }

        AssertionResult result;
        try {
            result = relyingParty.finishAssertion(FinishAssertionOptions.builder()
                    .request(assertionRequest)
                    .response(pkc)
                    .build());
        } catch (Exception e) {
            Map<String, Object> details = Map.of("reason", "verification_failed", "error", e.getMessage(), "userAgent", userAgent != null ? userAgent : "");
            auditLogService.log("LOGIN_FAILURE", null, ip, correlationId, details);
            throw new BadCredentialsException("Invalid or expired authentication request", e);
        }

        if (!result.isSuccess()) {
            Map<String, Object> details = Map.of("reason", "assertion_not_successful", "userAgent", userAgent != null ? userAgent : "");
            auditLogService.log("LOGIN_FAILURE", null, ip, correlationId, details);
            throw new BadCredentialsException("Invalid or expired authentication request");
        }

        byte[] credIdBytes = result.getCredential().getCredentialId().getBytes();
        WebAuthnCredential cred = credentialRepository.findByCredentialId(credIdBytes)
                .orElseThrow(() -> {
                    Map<String, Object> details = Map.of("reason", "credential_not_found", "userAgent", userAgent != null ? userAgent : "");
                    auditLogService.log("LOGIN_FAILURE", null, ip, correlationId, details);
                    return new BadCredentialsException("Invalid or expired authentication request");
                });

        cred.setSignCount(result.getSignatureCount());
        cred.setLastUsedAt(Instant.now());
        credentialRepository.save(cred);

        User user = cred.getUser();
        String token = jwtService.issueToken(user);

        Map<String, Object> auditDetails = new HashMap<>();
        auditDetails.put("action", "login");
        auditDetails.put("userAgent", userAgent);
        auditDetails.put("credentialId", result.getCredential().getCredentialId().getBase64Url());
        auditLogService.log("LOGIN_SUCCESS", user.getId(), ip, correlationId, auditDetails);

        return new LoginFinishResponse(token, jwtService.getExpiresInSeconds());
    }

    private static UUID uuidFromBytes(byte[] bytes) {
        long msb = 0;
        long lsb = 0;
        for (int i = 0; i < 8; i++) {
            msb = (msb << 8) | (bytes[i] & 0xff);
        }
        for (int i = 8; i < 16; i++) {
            lsb = (lsb << 8) | (bytes[i] & 0xff);
        }
        return new UUID(msb, lsb);
    }
}

