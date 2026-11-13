package pdfserver.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WebAuthnCredentialRepository extends JpaRepository<WebAuthnCredential, UUID> {

    Optional<WebAuthnCredential> findByCredentialId(byte[] credentialId);

    List<WebAuthnCredential> findByUserId(UUID userId);
}

