package pdfserver.data;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import pdfserver.audit.AuditLog;
import pdfserver.audit.AuditLogRepository;
import pdfserver.certificate.Certificate;
import pdfserver.certificate.CertificateRepository;
import pdfserver.document.Document;
import pdfserver.document.DocumentRepository;
import pdfserver.signing.SigningRequest;
import pdfserver.signing.SigningRequestRepository;
import pdfserver.signing.SigningStatus;
import pdfserver.user.User;
import pdfserver.user.UserRepository;
import pdfserver.user.UserRole;
import pdfserver.user.UserStatus;
import pdfserver.user.WebAuthnCredential;
import pdfserver.user.WebAuthnCredentialRepository;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
class DataFoundationIntegrationTests {

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
    private UserRepository userRepository;

    @Autowired
    private WebAuthnCredentialRepository credentialRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private SigningRequestRepository signingRequestRepository;

    @Autowired
    private CertificateRepository certificateRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("Repository round-trips work across all entities")
    void testEntitiesRoundTrip() {
        // User
        User user = User.builder()
                .email("alice@example.com")
                .displayName("Alice Tester")
                .userHandle("handle-alice".getBytes(StandardCharsets.UTF_8))
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build();
        User savedUser = userRepository.save(user);
        assertThat(savedUser.getId()).isNotNull();

        // WebAuthnCredential
        WebAuthnCredential credential = WebAuthnCredential.builder()
                .user(savedUser)
                .credentialId("cred-123".getBytes(StandardCharsets.UTF_8))
                .publicKey("public-key-bytes".getBytes(StandardCharsets.UTF_8))
                .signCount(1L)
                .label("YubiKey 5")
                .transports(new String[]{"usb", "nfc"})
                .build();
        WebAuthnCredential savedCredential = credentialRepository.save(credential);
        assertThat(savedCredential.getId()).isNotNull();

        // Document
        Document doc = Document.builder()
                .owner(savedUser)
                .fileName("contract.pdf")
                .contentType("application/pdf")
                .sizeBytes(2048L)
                .sha256("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855")
                .storageKey("storage/key/1")
                .build();
        Document savedDoc = documentRepository.save(doc);
        assertThat(savedDoc.getId()).isNotNull();

        // SigningRequest
        SigningRequest request = SigningRequest.builder()
                .user(savedUser)
                .document(savedDoc)
                .status(SigningStatus.PENDING)
                .expiresAt(Instant.now().plus(7, ChronoUnit.DAYS))
                .build();
        SigningRequest savedRequest = signingRequestRepository.save(request);
        assertThat(savedRequest.getId()).isNotNull();

        // Certificate
        Certificate certificate = Certificate.builder()
                .signingRequest(savedRequest)
                .document(savedDoc)
                .user(savedUser)
                .credential(savedCredential)
                .signedAt(Instant.now())
                .ip("127.0.0.1")
                .userAgent("Mozilla/5.0")
                .latitude(new BigDecimal("37.774900"))
                .longitude(new BigDecimal("-122.419400"))
                .accuracyM(new BigDecimal("10.50"))
                .storageKey("storage/cert/1")
                .sha256("ca978112ca1bbdcafac231b39a23dc4da78608144164370eef540f4f7e8d212d")
                .build();
        Certificate savedCert = certificateRepository.save(certificate);
        assertThat(savedCert.getId()).isNotNull();

        // AuditLog
        AuditLog auditLog = AuditLog.builder()
                .eventType("USER_CREATED")
                .userId(savedUser.getId())
                .ip("127.0.0.1")
                .correlationId("corr-123")
                .entryHash("entryhash123")
                .build();
        AuditLog savedAudit = auditLogRepository.save(auditLog);
        assertThat(savedAudit.getId()).isNotNull();

        // Queries
        Optional<User> foundUser = userRepository.findByEmailIgnoreCase("ALICE@EXAMPLE.COM");
        assertThat(foundUser).isPresent();
        assertThat(foundUser.get().getId()).isEqualTo(savedUser.getId());

        Optional<WebAuthnCredential> foundCred = credentialRepository.findByCredentialId("cred-123".getBytes(StandardCharsets.UTF_8));
        assertThat(foundCred).isPresent();

        List<Document> userDocs = documentRepository.findByOwnerId(savedUser.getId());
        assertThat(userDocs).hasSize(1);

        Optional<Document> docBySha = documentRepository.findBySha256("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
        assertThat(docBySha).isPresent();
    }

    @Test
    @DisplayName("Duplicate email (case-insensitive) is rejected")
    void testDuplicateEmailRejected() {
        User user1 = User.builder()
                .email("bob@example.com")
                .displayName("Bob One")
                .userHandle("handle-bob-1".getBytes(StandardCharsets.UTF_8))
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build();
        userRepository.saveAndFlush(user1);

        User user2 = User.builder()
                .email("BOB@EXAMPLE.COM")
                .displayName("Bob Two")
                .userHandle("handle-bob-2".getBytes(StandardCharsets.UTF_8))
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build();

        assertThatThrownBy(() -> userRepository.saveAndFlush(user2))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Duplicate credential_id is rejected")
    void testDuplicateCredentialIdRejected() {
        User user = User.builder()
                .email("charlie@example.com")
                .displayName("Charlie")
                .userHandle("handle-charlie".getBytes(StandardCharsets.UTF_8))
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build();
        User savedUser = userRepository.saveAndFlush(user);

        byte[] duplicateCredId = "shared-cred-id".getBytes(StandardCharsets.UTF_8);

        WebAuthnCredential cred1 = WebAuthnCredential.builder()
                .user(savedUser)
                .credentialId(duplicateCredId)
                .publicKey("pk1".getBytes(StandardCharsets.UTF_8))
                .build();
        credentialRepository.saveAndFlush(cred1);

        WebAuthnCredential cred2 = WebAuthnCredential.builder()
                .user(savedUser)
                .credentialId(duplicateCredId)
                .publicKey("pk2".getBytes(StandardCharsets.UTF_8))
                .build();

        assertThatThrownBy(() -> credentialRepository.saveAndFlush(cred2))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("AuditLog immutability trigger rejects UPDATE and DELETE")
    void testAuditLogImmutability() {
        AuditLog log = AuditLog.builder()
                .eventType("SECURITY_EVENT")
                .entryHash("hash-secure-001")
                .build();
        AuditLog saved = auditLogRepository.saveAndFlush(log);
        Long id = saved.getId();

        // Attempt direct UPDATE via SQL
        assertThatThrownBy(() -> jdbcTemplate.update("UPDATE audit_log SET event_type = 'TAMPERED' WHERE id = ?", id))
                .hasMessageContaining("audit_log records are immutable");

        // Attempt direct DELETE via SQL
        assertThatThrownBy(() -> jdbcTemplate.update("DELETE FROM audit_log WHERE id = ?", id))
                .hasMessageContaining("audit_log records are immutable");
    }
}

