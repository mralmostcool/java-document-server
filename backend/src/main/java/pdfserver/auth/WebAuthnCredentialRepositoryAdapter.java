package pdfserver.auth;

import com.yubico.webauthn.CredentialRepository;
import com.yubico.webauthn.RegisteredCredential;
import com.yubico.webauthn.data.ByteArray;
import com.yubico.webauthn.data.PublicKeyCredentialDescriptor;
import com.yubico.webauthn.data.PublicKeyCredentialType;
import org.springframework.stereotype.Component;
import pdfserver.user.User;
import pdfserver.user.UserRepository;
import pdfserver.user.WebAuthnCredential;
import pdfserver.user.WebAuthnCredentialRepository;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class WebAuthnCredentialRepositoryAdapter implements CredentialRepository {

    private final UserRepository userRepository;
    private final WebAuthnCredentialRepository credentialRepository;

    public WebAuthnCredentialRepositoryAdapter(UserRepository userRepository,
                                               WebAuthnCredentialRepository credentialRepository) {
        this.userRepository = userRepository;
        this.credentialRepository = credentialRepository;
    }

    @Override
    public Set<PublicKeyCredentialDescriptor> getCredentialIdsForUsername(String username) {
        Optional<User> userOpt = userRepository.findByEmailIgnoreCase(username);
        if (userOpt.isEmpty()) {
            return Collections.emptySet();
        }
        List<WebAuthnCredential> creds = credentialRepository.findByUserId(userOpt.get().getId());
        return creds.stream()
                .filter(c -> c.getRevokedAt() == null)
                .map(c -> PublicKeyCredentialDescriptor.builder()
                        .id(new ByteArray(c.getCredentialId()))
                        .type(PublicKeyCredentialType.PUBLIC_KEY)
                        .build())
                .collect(Collectors.toSet());
    }

    @Override
    public Optional<ByteArray> getUserHandleForUsername(String username) {
        return userRepository.findByEmailIgnoreCase(username)
                .map(User::getUserHandle)
                .map(ByteArray::new);
    }

    @Override
    public Optional<String> getUsernameForUserHandle(ByteArray userHandle) {
        return userRepository.findByUserHandle(userHandle.getBytes())
                .map(User::getEmail);
    }

    @Override
    public Optional<RegisteredCredential> lookup(ByteArray credentialId, ByteArray userHandle) {
        Optional<WebAuthnCredential> credOpt = credentialRepository.findByCredentialId(credentialId.getBytes());
        if (credOpt.isEmpty()) {
            return Optional.empty();
        }
        WebAuthnCredential cred = credOpt.get();
        if (cred.getRevokedAt() != null) {
            return Optional.empty();
        }
        if (userHandle != null && !new ByteArray(cred.getUser().getUserHandle()).equals(userHandle)) {
            return Optional.empty();
        }

        try {
            RegisteredCredential reg = RegisteredCredential.builder()
                    .credentialId(new ByteArray(cred.getCredentialId()))
                    .userHandle(new ByteArray(cred.getUser().getUserHandle()))
                    .publicKeyCose(new ByteArray(cred.getPublicKey()))
                    .signatureCount(cred.getSignCount())
                    .build();
            return Optional.of(reg);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @Override
    public Set<RegisteredCredential> lookupAll(ByteArray credentialId) {
        Optional<WebAuthnCredential> credOpt = credentialRepository.findByCredentialId(credentialId.getBytes());
        if (credOpt.isEmpty() || credOpt.get().getRevokedAt() != null) {
            return Collections.emptySet();
        }
        WebAuthnCredential cred = credOpt.get();
        try {
            RegisteredCredential reg = RegisteredCredential.builder()
                    .credentialId(new ByteArray(cred.getCredentialId()))
                    .userHandle(new ByteArray(cred.getUser().getUserHandle()))
                    .publicKeyCose(new ByteArray(cred.getPublicKey()))
                    .signatureCount(cred.getSignCount())
                    .build();
            return Collections.singleton(reg);
        } catch (Exception e) {
            return Collections.emptySet();
        }
    }
}

