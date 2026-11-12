package pdfserver.common.logger;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    private static final Logger log = LoggerFactory.getLogger(CorrelationIdFilter.class);

    // Accept only safe, reasonably short IDs from clients
    private static final Pattern VALID_ID = Pattern.compile("^[A-Za-z0-9\\-_.]{1,64}$");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String correlationId = resolveCorrelationId(request);
        long start = System.currentTimeMillis();

        MDC.put(MDC_KEY, correlationId);
        response.setHeader(HEADER, correlationId);

        try {
            chain.doFilter(request, response);
        } finally {
            long tookMs = System.currentTimeMillis() - start;
            log.info("{} {} -> {} ({} ms)",
                    request.getMethod(), request.getRequestURI(), response.getStatus(), tookMs);
            MDC.remove(MDC_KEY);
        }

    }

    // ================ HELPERS ==================

    private String resolveCorrelationId(HttpServletRequest request) {
        String incoming = request.getHeader(HEADER);
        if (incoming != null && VALID_ID.matcher(incoming).matches()) {
            return incoming;
        }

        // no valid id ? ----> return random UUID
        return UUID.randomUUID().toString();
    }

}
