package llltorm.task;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.TimeUnit;
import java.net.http.HttpClient;

public class CrptApiIntegrationTest {

    @Test
    void realApi_withoutToken_resultsInApiException() {
        CrptApi api = new CrptApi(TimeUnit.SECONDS, 1, "https://ismp.crpt.ru",
            HttpClient.newHttpClient(), () -> "Bearer invalid-token");
        CrptApi.Document doc = new CrptApi.Document();
        doc.setDocType("LP_INTRODUCE_GOODS");

        CrptApi.ApiException ex = assertThrows(CrptApi.ApiException.class, () ->
            api.createDocument(doc, "signature")
        );
        assertTrue(ex.getMessage().contains("API request failed") || ex.getMessage().contains("Failed to create document"));
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "CRPT_TOKEN", matches = ".+", disabledReason = "Set CRPT_TOKEN to run this test")
    void realApi_withToken_maySucceedOrRejectInput() {
        String token = System.getenv("CRPT_TOKEN");
        CrptApi api = new CrptApi(TimeUnit.SECONDS, 1, "https://ismp.crpt.ru",
            HttpClient.newHttpClient(), () -> "Bearer " + token);
        CrptApi.Document doc = new CrptApi.Document();
        doc.setDocType("LP_INTRODUCE_GOODS");

        CrptApi.ApiException ex = assertThrows(CrptApi.ApiException.class, () ->
            api.createDocument(doc, "signature")
        );
        assertTrue(ex.getMessage().contains("status:") || ex.getMessage().contains("Failed"));
    }
}


