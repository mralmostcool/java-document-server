package pdfserver.config;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.stereotype.Service;
import pdfserver.user.User;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;

@Service
public class JwtService {

    private final JwtProperties properties;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
    }

    public String issueToken(User user) {
        return issueToken(user.getId().toString(), user.getRole().name());
    }

    public String issueToken(String userId, String role) {
        try {
            JWSSigner signer = new MACSigner(properties.secret().getBytes(StandardCharsets.UTF_8));
            Instant now = Instant.now();
            Instant expiresAt = now.plus(properties.ttl());

            JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                    .issuer(properties.issuer())
                    .subject(userId)
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(expiresAt))
                    .claim("role", role)
                    .claim("amr", List.of("hwk"))
                    .build();

            SignedJWT signedJWT = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claimsSet);
            signedJWT.sign(signer);

            return signedJWT.serialize();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to issue JWT token", e);
        }
    }

    public long getExpiresInSeconds() {
        return properties.ttl().toSeconds();
    }
}

