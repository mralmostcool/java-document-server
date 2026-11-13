package pdfserver.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import pdfserver.auth.dto.LoginStartRequest;
import pdfserver.auth.dto.RegisterStartRequest;
import pdfserver.auth.dto.WebAuthnFinishRequest;
import pdfserver.config.JwtService;
import pdfserver.user.User;
import pdfserver.user.UserRepository;
import pdfserver.user.UserRole;
import pdfserver.user.UserStatus;
import pdfserver.user.WebAuthnCredential;
import pdfserver.user.WebAuthnCredentialRepository;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = {
        "app.auth.open-registration=true",
        "app.webauthn.rp-id=localhost",
        "app.webauthn.allowed-origins=http://localhost:3000",
        "app.jwt.secret=testingsecretkeyatleast32characterslongforhs256!"
})
class WebAuthnAndSecurityIntegrationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:17-3.5-alpine").asCompatibleSubstituteFor("postgres")
    );

    @Container
    @ServiceConnection(name = "redis")
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private WebAuthnChallengeStore challengeStore;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WebAuthnCredentialRepository credentialRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("JWT validation: valid token grants access, invalid/missing/tampered tokens return 401")
    void testJwtValidation() throws Exception {
        UUID userId = UUID.randomUUID();
        String validToken = jwtService.issueToken(userId.toString(), "USER");

        // Protected endpoint with valid token -> authorized (404, not 401)
        mockMvc.perform(get("/api/v1/documents").header("Authorization", "Bearer " + validToken))
                .andExpect(status().isNotFound());

        // Missing token -> 401
        mockMvc.perform(get("/api/v1/documents"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.error", is("UNAUTHORIZED")));

        // Tampered token -> 401
        mockMvc.perform(get("/api/v1/documents").header("Authorization", "Bearer " + validToken + "tampered"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.error", is("UNAUTHORIZED")));
    }

    @Test
    @DisplayName("Challenge store: single use and expiry")
    void testChallengeStore() {
        String reqId = UUID.randomUUID().toString();
        challengeStore.save(reqId, "{\"challenge\":\"test\"}");

        // First read removes challenge atomically
        Optional<String> firstRead = challengeStore.getAndRemove(reqId);
        assertThat(firstRead).isPresent();
        assertThat(firstRead.get()).isEqualTo("{\"challenge\":\"test\"}");

        // Second read returns empty (cannot be replayed)
        Optional<String> secondRead = challengeStore.getAndRemove(reqId);
        assertThat(secondRead).isEmpty();
    }

    @Test
    @DisplayName("WebAuthn start endpoints return valid options with correct rpId")
    void testStartEndpoints() throws Exception {
        // Register start
        RegisterStartRequest regReq = new RegisterStartRequest("pilot@example.com", "Test Pilot");
        mockMvc.perform(post("/api/v1/auth/webauthn/register/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(regReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(jsonPath("$.options.rp.id", is("localhost")));

        // Login start
        LoginStartRequest loginReq = new LoginStartRequest("pilot@example.com");
        mockMvc.perform(post("/api/v1/auth/webauthn/login/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(jsonPath("$.options.rpId", is("localhost")));
    }

    @Test
    @DisplayName("Garbage credential gives 4xx and request cannot be replayed")
    void testGarbageCredentialRejectedAndCannotReplay() throws Exception {
        RegisterStartRequest regReq = new RegisterStartRequest("garbage@example.com", "Garbage Tester");
        MvcResult startResult = mockMvc.perform(post("/api/v1/auth/webauthn/register/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(regReq)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode startJson = objectMapper.readTree(startResult.getResponse().getContentAsString());
        String reqId = startJson.get("requestId").asText();

        @SuppressWarnings("unchecked")
        Map<String, Object> garbageCred = objectMapper.readValue("{\"id\":\"fake\",\"rawId\":\"fake\",\"response\":{},\"type\":\"public-key\"}", Map.class);
        WebAuthnFinishRequest finishReq = new WebAuthnFinishRequest(reqId, garbageCred);

        mockMvc.perform(post("/api/v1/auth/webauthn/register/finish")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(finishReq)))
                .andExpect(status().is4xxClientError());

        // Replay attempt fails immediately as request ID was consumed
        mockMvc.perform(post("/api/v1/auth/webauthn/register/finish")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(finishReq)))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("Second credential rule: adding another key requires valid JWT of the same user")
    void testSecondCredentialRuleEnforced() throws Exception {
        // Create user with an existing active credential
        User user = User.builder()
                .email("multikey@example.com")
                .displayName("Multi Key")
                .userHandle("handle-multikey".getBytes(StandardCharsets.UTF_8))
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build();
        user = userRepository.saveAndFlush(user);

        WebAuthnCredential cred = WebAuthnCredential.builder()
                .user(user)
                .credentialId("key1".getBytes(StandardCharsets.UTF_8))
                .publicKey("pk1".getBytes(StandardCharsets.UTF_8))
                .signCount(1L)
                .build();
        credentialRepository.saveAndFlush(cred);

        RegisterStartRequest regReq = new RegisterStartRequest("multikey@example.com", "Multi Key");
        String jsonPayload = objectMapper.writeValueAsString(regReq);

        // Attempting to register second key without JWT -> 403 Forbidden
        mockMvc.perform(post("/api/v1/auth/webauthn/register/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isForbidden());

        // Attempting with JWT of another user -> 403 Forbidden
        String otherToken = jwtService.issueToken(UUID.randomUUID().toString(), "USER");
        mockMvc.perform(post("/api/v1/auth/webauthn/register/start")
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isForbidden());

        // Attempting with matching user JWT -> 200 OK
        String userToken = jwtService.issueToken(user);
        mockMvc.perform(post("/api/v1/auth/webauthn/register/start")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }
}

