package llltorm.task;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public class CrptApi {
    private static final String API_URL = "https://ismp.crpt.ru/api/v3/lk/documents/create";
    private static final String AUTH_HEADER = "Authorization";
    private static final String CONTENT_TYPE_HEADER = "Content-Type";
    private static final String APPLICATION_JSON = "application/json";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String authToken;
    private final int requestLimit;
    private final TimeUnit timeUnit;
    private final Deque<LocalDateTime> requestTimestamps;
    private final ReentrantLock lock;

    public CrptApi(TimeUnit timeUnit, int requestLimit, String authToken) {
        if (requestLimit <= 0) {
            throw new IllegalArgumentException("Request limit must be positive");
        }
        this.timeUnit = Objects.requireNonNull(timeUnit);
        this.requestLimit = requestLimit;
        this.authToken = Objects.requireNonNull(authToken);
        this.requestTimestamps = new ArrayDeque<>(requestLimit);
        this.lock = new ReentrantLock();

        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

        this.objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }

    public void createDocument(Document document, String signature) {
        Objects.requireNonNull(document);
        Objects.requireNonNull(signature);

        waitForRequestLimit();

        try {
            String requestBody = buildRequestBody(document, signature);
            HttpRequest request = buildHttpRequest(requestBody);
            HttpResponse<String> response = sendRequest(request);
            handleResponse(response);
        } catch (Exception e) {
            throw new CrptApiException("Failed to create document", e);
        }
    }

    private void waitForRequestLimit() {
        lock.lock();
        try {
            LocalDateTime now = LocalDateTime.now();
            cleanOldRequests(now);

            while (requestTimestamps.size() >= requestLimit) {
                LocalDateTime oldestRequest = requestTimestamps.peekFirst();
                long timePassed = Duration.between(oldestRequest, now).toMillis();
                long timeToWait = timeUnit.toMillis(1) - timePassed;

                if (timeToWait > 0) {
                    try {
                        Thread.sleep(timeToWait);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new CrptApiException("Thread interrupted while waiting for request limit", e);
                    }
                    now = LocalDateTime.now();
                    cleanOldRequests(now);
                } else {
                    requestTimestamps.pollFirst();
                }
            }

            requestTimestamps.addLast(now);
        } finally {
            lock.unlock();
        }
    }

    private void cleanOldRequests(LocalDateTime now) {
        while (!requestTimestamps.isEmpty()) {
            LocalDateTime oldestRequest = requestTimestamps.peekFirst();
            if (Duration.between(oldestRequest, now).toMillis() > timeUnit.toMillis(1)) {
                requestTimestamps.pollFirst();
            } else {
                break;
            }
        }
    }

    private String buildRequestBody(Document document, String signature) throws JsonProcessingException {
        DocumentRequest request = new DocumentRequest(
            "MANUAL",
            document,
            "LP_INTRODUCE_GOODS",
            signature
        );
        return objectMapper.writeValueAsString(request);
    }

    private HttpRequest buildHttpRequest(String requestBody) {
        return HttpRequest.newBuilder()
            .uri(URI.create(API_URL))
            .header(AUTH_HEADER, "Bearer " + authToken)
            .header(CONTENT_TYPE_HEADER, APPLICATION_JSON)
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build();
    }

    private HttpResponse<String> sendRequest(HttpRequest request) throws IOException, InterruptedException {
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private void handleResponse(HttpResponse<String> response) {
        if (response.statusCode() != 200) {
            throw new CrptApiException("API request failed with status code: " + response.statusCode() +
                ", body: " + response.body());
        }
    }

    @RequiredArgsConstructor
    public static class CrptApiException extends RuntimeException {
        private final String message;

        public CrptApiException(String message, Throwable cause) {
            super(message, cause);
            this.message = message;
        }
    }

    @Getter
    @Setter
    @ToString
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Document {
        private String participantInn;
        private String docId;
        private String docStatus;
        private String docType;
        private boolean importRequest;
        private String ownerInn;
        private String producerInn;
        private String productionDate;
        private String productionType;
        private Product[] products;
        private String regDate;
        private String regNumber;
    }

    @Getter
    @Setter
    @ToString
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Product {
        private String certificateDocument;
        private String certificateDocumentDate;
        private String certificateDocumentNumber;
        private String ownerInn;
        private String producerInn;
        private String productionDate;
        private String tnvedCode;
        private String uitCode;
        private String uituCode;
    }

    @Getter
    @ToString
    @AllArgsConstructor
    private static class DocumentRequest {
        private final String documentFormat;
        private final Document productDocument;
        private final String productGroup = "clothes";
        private final String signature;
        private final String type;
    }
}
