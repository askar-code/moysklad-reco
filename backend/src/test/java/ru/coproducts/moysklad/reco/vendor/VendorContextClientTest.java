package ru.coproducts.moysklad.reco.vendor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import ru.coproducts.moysklad.reco.util.HmacBase64Support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class VendorContextClientTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void buildVendorJwtEscapesAppUidViaJsonSerializer() throws Exception {
        String appUid = "app\"uid\\test";
        VendorContextClient client = new VendorContextClient(
                "https://apps-api.moysklad.ru/api/vendor/1.0",
                appUid,
                "secret",
                5000,
                15,
                2,
                500,
                WebClient.builder()
        );

        String jwt = client.buildVendorJwt();
        String[] parts = jwt.split("\\.");
        JsonNode payload = OBJECT_MAPPER.readTree(HmacBase64Support.base64UrlDecode(parts[1]));

        assertEquals(3, parts.length);
        assertEquals(appUid, payload.path("sub").asText());
        assertNotNull(payload.path("jti").asText(null));
        assertEquals("JWT", OBJECT_MAPPER.readTree(HmacBase64Support.base64UrlDecode(parts[0])).path("typ").asText());
        assertEquals("HS256", OBJECT_MAPPER.readTree(HmacBase64Support.base64UrlDecode(parts[0])).path("alg").asText());
    }
}
