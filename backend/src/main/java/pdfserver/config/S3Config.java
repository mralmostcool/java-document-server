package pdfserver.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;

@Configuration
@EnableConfigurationProperties(S3Properties.class)
public class S3Config {

    @Bean
    public S3Client s3Client(S3Properties s3Properties) {
        var credentials = AwsBasicCredentials.create(s3Properties.accessKey(), s3Properties.secretKey());
        var s3Configuration = S3Configuration.builder()
                .pathStyleAccessEnabled(true)
                .build();

        var builder = S3Client.builder()
                .region(Region.of(s3Properties.region()))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .serviceConfiguration(s3Configuration);

        if (s3Properties.endpoint() != null && !s3Properties.endpoint().isBlank()) {
            builder.endpointOverride(URI.create(s3Properties.endpoint()));
        }

        return builder.build();
    }
}

