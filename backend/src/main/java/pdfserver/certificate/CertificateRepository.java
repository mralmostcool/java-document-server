package pdfserver.certificate;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CertificateRepository extends JpaRepository<Certificate, UUID> {

    Optional<Certificate> findBySigningRequestId(UUID signingRequestId);

    List<Certificate> findByDocumentId(UUID documentId);

    List<Certificate> findByUserId(UUID userId);

    List<Certificate> findBySha256(String sha256);
}

