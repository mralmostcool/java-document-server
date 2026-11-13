package pdfserver.auth.dto;

import java.util.Map;

public record WebAuthnStartResponse(
        String requestId,
        Map<String, Object> options
) {
}

