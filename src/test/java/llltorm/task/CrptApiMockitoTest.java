package llltorm.task;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class CrptApiMockitoTest {

    private HttpClient mockHttpClient;
    private HttpResponse<String> mockResponse;

    @BeforeEach
    void init() {
        mockHttpClient = mock(HttpClient.class);
        mockResponse = mock(HttpResponse.class);
    }

    @Test
    void createDocument_success_sendsPostToExpectedPath() throws Exception {
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn("ok");
        when(mockHttpClient.send(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any())).thenReturn(mockResponse);

        CrptApi api = new CrptApi(TimeUnit.SECONDS, 5, "http://example.org", mockHttpClient, () -> "Bearer test-token");

        CrptApi.Document document = sampleDocument();
        api.createDocument(document, "sig");

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);

        org.mockito.Mockito.verify(mockHttpClient).send(captor.capture(), any());
        HttpRequest sent = captor.getValue();

        assertEquals("POST", sent.method());
        assertEquals("http://example.org/api/v3/lk/documents/create", sent.uri().toString());
        assertEquals("application/json", sent.headers().firstValue("Content-Type").orElse(""));
        assertEquals("Bearer test-token", sent.headers().firstValue("Authorization").orElse(""));
    }

    @Test
    void createDocument_non200_throwsApiException() throws Exception {
        when(mockResponse.statusCode()).thenReturn(500);
        when(mockResponse.body()).thenReturn("error");
        when(mockHttpClient.send(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any())).thenReturn(mockResponse);

        CrptApi api = new CrptApi(TimeUnit.SECONDS, 5, "http://example.org", mockHttpClient, () -> "Bearer test-token");

        assertThrows(CrptApi.ApiException.class, () -> api.createDocument(sampleDocument(), "sig"));
    }

    @Test
    void createDocument_ioException_wrappedAsApiException() throws Exception {
        when(mockHttpClient.send(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any())).thenThrow(new java.io.IOException("io"));

        CrptApi api = new CrptApi(TimeUnit.SECONDS, 5, "http://example.org", mockHttpClient, () -> "Bearer test-token");

        assertThrows(CrptApi.ApiException.class, () -> api.createDocument(sampleDocument(), "sig"));
    }

    private CrptApi.Document sampleDocument() {
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


