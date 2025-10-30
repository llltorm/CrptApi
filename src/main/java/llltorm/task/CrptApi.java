package llltorm.task;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.function.Supplier;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class CrptApi {
    private final Semaphore semaphore;
    private final long timeWindowMillis;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final Supplier<String> authorizationHeaderSupplier;
    Logger logger = Logger.getLogger(getClass().getName());

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this(timeUnit, requestLimit, "https://ismp.crpt.ru");
    }

    public CrptApi(TimeUnit timeUnit, int requestLimit, String baseUrl) {
        this(timeUnit, requestLimit, baseUrl,
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build(),
            () -> "Bearer <token>");
    }

    public CrptApi(TimeUnit timeUnit, int requestLimit, String baseUrl, HttpClient httpClient, Supplier<String> authorizationHeaderSupplier) {
        if (requestLimit <= 0) {
            throw new IllegalArgumentException("Request limit must be positive");
        }
        this.semaphore = new Semaphore(requestLimit);
        this.timeWindowMillis = timeUnit.toMillis(1);
        this.baseUrl = baseUrl;
        this.httpClient = httpClient;
        this.authorizationHeaderSupplier = authorizationHeaderSupplier;
        this.objectMapper = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }

    public void createDocument(Document document, String signature) {
        acquirePermission();

        try {
            String requestBody = buildRequestBody(document, signature);
            HttpRequest request = buildHttpRequest(requestBody);

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new ApiException("API request failed with status: " + response.statusCode() +
                    ", body: " + response.body());
            }

            logger.info("Document created successfully: " + response.body());

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException("Request was interrupted", e);
        } catch (IOException e) {
            throw new ApiException("Failed to create document", e);
        }
    }

    private void acquirePermission() {
        try {
            semaphore.acquire();
            new Thread(() -> {
                try {
                    Thread.sleep(timeWindowMillis);
                    semaphore.release();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }).start();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException("Failed to acquire API permission", e);
        }
    }

    private String buildRequestBody(Document document, String signature) throws JsonProcessingException {
        DocumentRequest request = new DocumentRequest(
            document,
            DocumentFormat.MANUAL,
            DocumentType.LP_INTRODUCE_GOODS,
            signature
        );
        return objectMapper.writeValueAsString(request);
    }

    private HttpRequest buildHttpRequest(String requestBody) {
        return HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/api/v3/lk/documents/create"))
            .header("Content-Type", "application/json")
            .header("Authorization", authorizationHeaderSupplier.get())
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .timeout(Duration.ofSeconds(30))
            .build();
    }

    @Setter
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Document {
        private Description description;
        private String docId;
        private String docStatus;
        private String docType;
        private boolean importRequest;
        private String ownerInn;
        private String participantInn;
        private String producerInn;
        private String productionDate;
        private String productionType;
        private Product[] products;
        private String regDate;
        private String regNumber;
    }

    @Setter
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Description {
        private String participantInn;
    }

    @Setter
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
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
    @AllArgsConstructor
    private static class DocumentRequest {
        private final Document productDocument;
        private final DocumentFormat documentFormat;
        private final DocumentType type;
        private final String signature;
    }

    private enum DocumentFormat {
        MANUAL,
        XML,
        CSV
    }

    private enum DocumentType {
        LP_INTRODUCE_GOODS
    }

    public static class ApiException extends RuntimeException {
        public ApiException(String message) {
            super(message);
        }

        public ApiException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
