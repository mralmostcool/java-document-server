package pdfserver.auth.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record WebAuthnFinishRequest(
        @NotBlank String requestId,
        @NotNull JsonNode credential
) {
}

