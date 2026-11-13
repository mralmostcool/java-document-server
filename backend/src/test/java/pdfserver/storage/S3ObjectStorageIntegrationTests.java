package pdfserver.storage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import pdfserver.config.S3Properties;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5432/java_document_server",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
        "spring.data.redis.password=redispassword123",
        "management.health.redis.enabled=false",
        "management.health.db.enabled=false",
        "management.health.diskspace.enabled=false",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=none"
})
@Testcontainers
class S3ObjectStorageIntegrationTests {

    private static final String ACCESS_KEY = "testaccess";
    private static final String SECRET_KEY = "testsecret";
    private static final String BUCKET = "documents";

    private static final String S3_CONFIG_JSON = """
            {
              "identities": [
                {
                  "name": "app",
                  "credentials": [
                    {
                      "accessKey": "testaccess",
                      "secretKey": "testsecret"
                    }
                  ],
                  "actions": ["Read", "Write", "List", "Tagging", "Admin"]
                }
              ]
            }
            """;

    @Container
    static GenericContainer<?> seaweedfs = new GenericContainer<>("chrislusf/seaweedfs:3.80")
            .withCreateContainerCmdModifier(cmd -> cmd.withEntrypoint(
                    "sh", "-c",
                    "mkdir -p /data /etc/seaweedfs && " +
                    "echo '" + S3_CONFIG_JSON.replace("\n", " ") + "' > /etc/seaweedfs/s3.json && " +
                    "exec weed server -dir=/data -filer -s3 -s3.port=8333 -s3.config=/etc/seaweedfs/s3.json"
            ))
            .withExposedPorts(8333)
            .waitingFor(Wait.forHttp("/status").forPort(8333));

    @TestConfiguration
    static class TestS3Config {

        @Bean
        @Primary
        public S3Properties testS3Properties() {
            String endpoint = "http://" + seaweedfs.getHost() + ":" + seaweedfs.getMappedPort(8333);
            return new S3Properties(endpoint, "us-east-1", BUCKET, ACCESS_KEY, SECRET_KEY);
        }

        @Bean
        @Primary
        public S3Client testS3Client(S3Properties props) {
            var credentials = AwsBasicCredentials.create(props.accessKey(), props.secretKey());
            var s3Configuration = S3Configuration.builder()
                    .pathStyleAccessEnabled(true)
                    .build();

            S3Client client = S3Client.builder()
                    .region(Region.of(props.region()))
                    .credentialsProvider(StaticCredentialsProvider.create(credentials))
                    .serviceConfiguration(s3Configuration)
                    .endpointOverride(URI.create(props.endpoint()))
                    .build();

            // Create test bucket
            try {
                client.createBucket(CreateBucketRequest.builder().bucket(props.bucket()).build());
            } catch (Exception ignored) {
            }

            return client;
        }

        @Bean
        @Primary
        public ObjectStorage testObjectStorage(S3Client testS3Client, S3Properties testS3Properties) {
            return new S3ObjectStorage(testS3Client, testS3Properties);
        }
    }

    @Autowired
    private ObjectStorage objectStorage;

    @Test
    @DisplayName("S3 round trip: put, exists, get stream, and delete")
    void testS3RoundTrip() throws Exception {
        String ownerId = UUID.randomUUID().toString();
        String documentId = UUID.randomUUID().toString();
        byte[] content = "Hello SeaweedFS Object Storage Streaming!".getBytes(StandardCharsets.UTF_8);

        // Initially not exists
        assertThat(objectStorage.exists(ownerId, documentId)).isFalse();

        // Put object
        try (InputStream in = new ByteArrayInputStream(content)) {
            objectStorage.put(ownerId, documentId, in, content.length, "text/plain");
        }

        // Exists
        assertThat(objectStorage.exists(ownerId, documentId)).isTrue();

        // Get object stream
        try (InputStream retrieved = objectStorage.get(ownerId, documentId)) {
            byte[] readBytes = retrieved.readAllBytes();
            assertThat(readBytes).isEqualTo(content);
        }

        // Delete object
        objectStorage.delete(ownerId, documentId);

        // Exists is false after delete
        assertThat(objectStorage.exists(ownerId, documentId)).isFalse();
    }
}

