package org.gpproject.backend.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Component
public class ApiRequestSignatureFilter extends OncePerRequestFilter {

    private static final String CLIENT_ID_HEADER = "X-Client-Id";
    private static final String TIMESTAMP_HEADER = "X-Timestamp";
    private static final String SIGNATURE_HEADER = "X-Signature";

    @Value("${app.security.signing-secret:}")
    private String signingSecret;

    @Value("${app.security.max-skew-seconds:300}")
    private long maxSkewSeconds;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/v1/petition");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        if (signingSecret == null || signingSecret.isBlank()) {
            reject(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Sunucuda app.security.signing-secret tanımlı değil.");
            return;
        }

        String clientId = request.getHeader(CLIENT_ID_HEADER);
        String timestampHeader = request.getHeader(TIMESTAMP_HEADER);
        String signatureHeader = request.getHeader(SIGNATURE_HEADER);

        if (isBlank(clientId) || isBlank(timestampHeader) || isBlank(signatureHeader)) {
            reject(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "Eksik güvenlik başlığı: X-Client-Id, X-Timestamp, X-Signature zorunludur.");
            return;
        }

        long timestamp;
        try {
            timestamp = Long.parseLong(timestampHeader);
        } catch (NumberFormatException e) {
            reject(response, HttpServletResponse.SC_UNAUTHORIZED, "X-Timestamp geçerli bir epoch saniyesi olmalıdır.");
            return;
        }

        long now = Instant.now().getEpochSecond();
        if (Math.abs(now - timestamp) > maxSkewSeconds) {
            reject(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "İstek zaman damgası geçersiz veya süresi dolmuş.");
            return;
        }

        String payload = request.getMethod() + "\n" + request.getRequestURI() + "\n" + timestamp + "\n" + clientId;
        String expectedSignature = hmacSha256Hex(signingSecret, payload);

        if (!safeEquals(expectedSignature, signatureHeader)) {
            reject(response, HttpServletResponse.SC_UNAUTHORIZED, "İmza doğrulanamadı.");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private boolean safeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    private String hmacSha256Hex(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);
            byte[] hmac = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));

            StringBuilder sb = new StringBuilder(hmac.length * 2);
            for (byte b : hmac) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("HMAC hesaplanamadı", e);
        }
    }

    private void reject(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        Map<String, String> body = new HashMap<>();
        body.put("error", message);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}