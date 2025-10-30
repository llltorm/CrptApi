package llltorm.task;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class CrptApiTest {

    private HttpServer server;
    private int port;
    private final List<String> receivedBodies = new ArrayList<>();
    private volatile int responseStatus = 200;
    private volatile String responseBody = "ok";

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        port = server.getAddress().getPort();
        server.createContext("/api/v3/lk/documents/create", new RecordingHandler());
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        receivedBodies.clear();
        responseStatus = 200;
        responseBody = "ok";
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void testCreateDocument_Success_sendsExpectedRequest() {
        CrptApi api = new CrptApi(TimeUnit.SECONDS, 5, "http://localhost:" + port);
        CrptApi.Document document = createTestDocument();

        api.createDocument(document, "test-signature");

        assertEquals(1, receivedBodies.size());
        String body = receivedBodies.get(0);
        assertTrue(body.contains("\"product_document\""));
        assertTrue(body.contains("\"document_format\":\"MANUAL\""));
        assertTrue(body.contains("\"type\":\"LP_INTRODUCE_GOODS\""));
        assertTrue(body.contains("\"signature\":\"test-signature\""));
    }

    @Test
    void testCreateDocument_Non200_throwsApiException() {
        responseStatus = 500;
        responseBody = "server-error";
        CrptApi api = new CrptApi(TimeUnit.SECONDS, 5, "http://localhost:" + port);
        CrptApi.Document document = createTestDocument();

        CrptApi.ApiException ex = assertThrows(CrptApi.ApiException.class, () ->
            api.createDocument(document, "sig")
        );
        assertTrue(ex.getMessage().contains("status: 500"));
    }

    @Test
    void testRateLimiting_1_per_second_blocks_second_call() {
        CrptApi api = new CrptApi(TimeUnit.SECONDS, 1, "http://localhost:" + port);
        CrptApi.Document document = createTestDocument();

        api.createDocument(document, "sig1");

        Instant start = Instant.now();
        api.createDocument(document, "sig2");
        long elapsedMs = Duration.between(start, Instant.now()).toMillis();

        assertTrue(elapsedMs >= 900, "Second call should block ~1s due to rate limit, was " + elapsedMs + "ms");
    }

    @Test
    void testRateLimiting_2_per_second_third_call_waits() {
        CrptApi api = new CrptApi(TimeUnit.SECONDS, 2, "http://localhost:" + port);
        CrptApi.Document document = createTestDocument();

        api.createDocument(document, "sig1");
        api.createDocument(document, "sig2");

        Instant start = Instant.now();
        api.createDocument(document, "sig3");
        long elapsedMs = Duration.between(start, Instant.now()).toMillis();

        assertTrue(elapsedMs >= 900, "Third call should block ~1s due to rate limit, was " + elapsedMs + "ms");
    }

    private class RecordingHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try (InputStream is = exchange.getRequestBody()) {
                String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                receivedBodies.add(body);
            }
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(responseStatus, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    private CrptApi.Document createTestDocument() {
        CrptApi.Description description = new CrptApi.Description();
        description.setParticipantInn("1234567890");

        CrptApi.Product product = new CrptApi.Product();
        product.setCertificateDocument("certificate");
        product.setCertificateDocumentDate("2024-01-01");
        product.setCertificateDocumentNumber("12345");
        product.setOwnerInn("1234567890");
        product.setProducerInn("0987654321");
        product.setProductionDate("2024-01-01");
        product.setTnvedCode("6401100000");
        product.setUitCode("test-uit-code");

        CrptApi.Product[] products = {product};

        CrptApi.Document document = new CrptApi.Document();
        document.setDescription(description);
        document.setDocId("doc-123");
        document.setDocStatus("DRAFT");
        document.setDocType("LP_INTRODUCE_GOODS");
        document.setImportRequest(false);
        document.setOwnerInn("1234567890");
        document.setParticipantInn("1234567890");
        document.setProducerInn("0987654321");
        document.setProductionDate("2024-01-01");
        document.setProductionType("LOCAL");
        document.setProducts(products);
        document.setRegDate("2024-01-01");
        document.setRegNumber("reg-123");

        return document;
    }
}
