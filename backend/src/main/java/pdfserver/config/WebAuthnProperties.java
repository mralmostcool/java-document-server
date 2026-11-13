package pdfserver.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Set;

@ConfigurationProperties(prefix = "app.webauthn")
public record WebAuthnProperties(
        String rpId,
        String rpName,
        Set<String> allowedOrigins,
        String userVerification
) {
}

