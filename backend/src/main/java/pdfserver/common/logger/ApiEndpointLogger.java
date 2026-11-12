package pdfserver.common.logger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class ApiEndpointLogger {

        private static final Logger log = LoggerFactory.getLogger(ApiEndpointLogger.class);

        private final RequestMappingHandlerMapping handlerMapping;

        public ApiEndpointLogger(
                        @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping handlerMapping) {
                this.handlerMapping = handlerMapping;
        }

        @EventListener(ApplicationReadyEvent.class)
        public void logEndpoints() {
                List<EndpointRow> endpoints = new ArrayList<>();

                Map<RequestMappingInfo, HandlerMethod> mappings = handlerMapping.getHandlerMethods();

                mappings.forEach((mapping, handler) -> {
                        Set<String> methods = mapping.getMethodsCondition()
                                        .getMethods()
                                        .stream()
                                        .map(RequestMethod::name)
                                        .collect(Collectors.toSet());

                        String httpMethods = methods.isEmpty()
                                        ? "ALL"
                                        : methods.stream()
                                                        .sorted()
                                                        .collect(Collectors.joining(", "));

                        Set<String> patterns = mapping.getPatternValues();

                        String paths = patterns.isEmpty()
                                        ? "/"
                                        : patterns.stream()
                                                        .sorted()
                                                        .collect(Collectors.joining(", "));

                        String controller = handler.getBeanType().getSimpleName();
                        String methodName = handler.getMethod().getName();

                        endpoints.add(new EndpointRow(
                                        httpMethods,
                                        paths,
                                        controller + "#" + methodName));
                });

                endpoints.sort(
                                Comparator.comparing(EndpointRow::path)
                                                .thenComparing(EndpointRow::httpMethod)
                                                .thenComparing(EndpointRow::handler));

                log.info("");
                log.info("╔════════════════════════════════════════════════════════════════════════════════════════════════════════════╗");
                log.info("║                                        REGISTERED API ENDPOINTS                                            ║");
                log.info("╠════════════════════════════════════════════════════════════════════════════════════════════════════════════╣");
                log.info("║ Total registered mappings : {}",
                                String.format("%-79d║", endpoints.size()));
                log.info("╚════════════════════════════════════════════════════════════════════════════════════════════════════════════╝");

                if (endpoints.isEmpty()) {
                        log.info("No request mappings were found.");
                        return;
                }

                log.info(String.format("%-12s | %-40s | %s",
                                "HTTP METHOD", "PATH", "HANDLER"));
                log.info("{}", "-".repeat(110));

                for (EndpointRow endpoint : endpoints) {
                        log.info(String.format("%-12s | %-40s | %s",
                                        endpoint.httpMethod(),
                                        endpoint.path(),
                                        endpoint.handler()));
                }

                log.info("{}", "-".repeat(110));
                log.info("API endpoint report complete. {} mappings registered.",
                                endpoints.size());
                log.info("");
        }

        private record EndpointRow(
                        String httpMethod,
                        String path,
                        String handler) {
        }
}