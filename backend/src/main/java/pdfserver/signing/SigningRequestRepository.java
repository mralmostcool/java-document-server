package pdfserver.signing;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SigningRequestRepository extends JpaRepository<SigningRequest, UUID> {

    List<SigningRequest> findByUserId(UUID userId);

    List<SigningRequest> findByDocumentId(UUID documentId);

    List<SigningRequest> findByStatus(SigningStatus status);
}

