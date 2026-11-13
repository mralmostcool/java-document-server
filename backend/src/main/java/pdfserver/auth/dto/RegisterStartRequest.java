package pdfserver.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record RegisterStartRequest(
        @NotBlank @Email String email,
        String displayName
) {
}

