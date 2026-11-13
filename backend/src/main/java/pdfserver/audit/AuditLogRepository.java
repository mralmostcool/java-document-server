package pdfserver.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findByUserId(UUID userId);

    List<AuditLog> findByEventType(String eventType);

    List<AuditLog> findByCorrelationId(String correlationId);
}

