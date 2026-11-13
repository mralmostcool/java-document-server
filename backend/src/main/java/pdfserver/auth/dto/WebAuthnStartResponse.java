package pdfserver.auth.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record WebAuthnStartResponse(
        String requestId,
        JsonNode options
) {
}

