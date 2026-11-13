package pdfserver.config;

import com.yubico.webauthn.CredentialRepository;
import com.yubico.webauthn.RelyingParty;
import com.yubico.webauthn.data.RelyingPartyIdentity;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Collections;
import java.util.Set;

@Configuration
@EnableConfigurationProperties({WebAuthnProperties.class, AuthProperties.class})
public class WebAuthnConfig {

    @Bean
    public RelyingParty relyingParty(WebAuthnProperties properties, CredentialRepository credentialRepository) {
        RelyingPartyIdentity rpIdentity = RelyingPartyIdentity.builder()
                .id(properties.rpId())
                .name(properties.rpName())
                .build();

        Set<String> origins = properties.allowedOrigins() != null ? properties.allowedOrigins() : Collections.emptySet();

        return RelyingParty.builder()
                .identity(rpIdentity)
                .credentialRepository(credentialRepository)
                .origins(origins)
                .build();
    }
}

