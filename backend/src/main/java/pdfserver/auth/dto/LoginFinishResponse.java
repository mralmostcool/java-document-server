package pdfserver.auth.dto;

public record LoginFinishResponse(
        String token,
        long expiresIn
) {
}

