package ru.itsbelyaev.marketplace.logging;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {
    private final ObjectMapper objectMapper;

    public RequestLoggingFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper.copy().setSerializationInclusion(JsonInclude.Include.ALWAYS);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long started = System.nanoTime();
        String requestId = UUID.randomUUID().toString();
        OffsetDateTime timestamp = OffsetDateTime.now();
        ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(request);
        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);
        wrappedResponse.setHeader("X-Request-Id", requestId);
        wrappedRequest.setAttribute("request_id", requestId);

        try {
            chain.doFilter(wrappedRequest, wrappedResponse);
        } finally {
            Map<String, Object> log = new LinkedHashMap<>();
            log.put("request_id", requestId);
            log.put("method", request.getMethod());
            log.put("endpoint", request.getRequestURI());
            log.put("status_code", wrappedResponse.getStatus());
            log.put("duration_ms", (System.nanoTime() - started) / 1_000_000.0);
            log.put("user_id", wrappedRequest.getAttribute("user_id"));
            log.put("timestamp", timestamp.toString());
            if (List.of("POST", "PUT", "DELETE").contains(request.getMethod())) {
                Object body = readAndMaskBody(wrappedRequest.getContentAsByteArray());
                if (body != null) {
                    log.put("request_body", body);
                }
            }
            System.out.println(objectMapper.writeValueAsString(log));
            wrappedResponse.copyBodyToResponse();
        }
    }

    private Object readAndMaskBody(byte[] bytes) {
        if (bytes.length == 0) {
            return null;
        }
        try {
            Map<String, Object> body = objectMapper.readValue(bytes, new TypeReference<>() {});
            mask(body);
            return body;
        } catch (Exception exception) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    @SuppressWarnings("unchecked")
    private void mask(Object value) {
        if (value instanceof Map<?, ?> rawMap) {
            Map<String, Object> map = (Map<String, Object>) rawMap;
            for (String key : List.copyOf(map.keySet())) {
                if (key.toLowerCase().contains("password") || key.toLowerCase().contains("token")) {
                    map.put(key, "***");
                } else {
                    mask(map.get(key));
                }
            }
        } else if (value instanceof List<?> list) {
            list.forEach(this::mask);
        }
    }
}
