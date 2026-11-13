package pdfserver.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pdfserver.auth.dto.LoginFinishResponse;
import pdfserver.auth.dto.LoginStartRequest;
import pdfserver.auth.dto.RegisterStartRequest;
import pdfserver.auth.dto.WebAuthnFinishRequest;
import pdfserver.auth.dto.WebAuthnStartResponse;
import pdfserver.common.logger.CorrelationIdFilter;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth/webauthn")
public class WebAuthnController {

    private final WebAuthnService webAuthnService;

    public WebAuthnController(WebAuthnService webAuthnService) {
        this.webAuthnService = webAuthnService;
    }

    @PostMapping("/register/start")
    public ResponseEntity<WebAuthnStartResponse> registerStart(@Valid @RequestBody RegisterStartRequest request,
                                                               @AuthenticationPrincipal Jwt jwt) {
        UUID authenticatedUserId = null;
        if (jwt != null && jwt.getSubject() != null) {
            try {
                authenticatedUserId = UUID.fromString(jwt.getSubject());
            } catch (Exception ignored) {
            }
        }
        WebAuthnStartResponse response = webAuthnService.startRegistration(request, authenticatedUserId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/register/finish")
    public ResponseEntity<Map<String, Object>> registerFinish(@Valid @RequestBody WebAuthnFinishRequest request,
                                                              HttpServletRequest httpRequest) {
        String ip = extractClientIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");
        String correlationId = (String) org.slf4j.MDC.get(CorrelationIdFilter.MDC_KEY);

        webAuthnService.finishRegistration(request, ip, userAgent, correlationId);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @PostMapping("/login/start")
    public ResponseEntity<WebAuthnStartResponse> loginStart(@Valid @RequestBody LoginStartRequest request) {
        WebAuthnStartResponse response = webAuthnService.startLogin(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/login/finish")
    public ResponseEntity<LoginFinishResponse> loginFinish(@Valid @RequestBody WebAuthnFinishRequest request,
                                                           HttpServletRequest httpRequest) {
        String ip = extractClientIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");
        String correlationId = (String) org.slf4j.MDC.get(CorrelationIdFilter.MDC_KEY);

        LoginFinishResponse response = webAuthnService.finishLogin(request, ip, userAgent, correlationId);
        return ResponseEntity.ok(response);
    }

    private String extractClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}

