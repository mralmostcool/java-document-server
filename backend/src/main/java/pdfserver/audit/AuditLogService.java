package pdfserver.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public AuditLogService(AuditLogRepository auditLogRepository, ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
    }

    public void log(String eventType, UUID userId, String ip, String correlationId, Map<String, Object> detailsMap) {
        String detailsJson = null;
        if (detailsMap != null && !detailsMap.isEmpty()) {
            try {
                detailsJson = objectMapper.writeValueAsString(detailsMap);
            } catch (Exception ignored) {
            }
        }

        AuditLog entry = AuditLog.builder()
                .eventType(eventType)
                .userId(userId)
                .ip(ip)
                .correlationId(correlationId)
                .details(detailsJson)
                .entryHash("temp-hash-" + UUID.randomUUID())
                .createdAt(Instant.now())
                .build();

        auditLogRepository.save(entry);
    }
}

