package pdfserver.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

public record WebAuthnFinishRequest(
        @NotBlank String requestId,
        @NotNull Map<String, Object> credential
) {
}


