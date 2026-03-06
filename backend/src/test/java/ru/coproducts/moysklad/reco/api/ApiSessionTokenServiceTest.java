package ru.coproducts.moysklad.reco.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiSessionTokenServiceTest {

    @Test
    void issuesAndVerifiesToken() {
        ApiSessionTokenService service = new ApiSessionTokenService("test-secret", 300);
        ApiSessionTokenService.IssuedSession session = service.issue("acc-1");

        String accountId = service.verifyAndExtractAccountId(session.token()).orElseThrow();
        assertEquals("acc-1", accountId);
    }

    @Test
    void rejectsTamperedToken() {
        ApiSessionTokenService service = new ApiSessionTokenService("test-secret", 300);
        ApiSessionTokenService.IssuedSession session = service.issue("acc-1");

        String[] parts = session.token().split("\\.");
        String payload = parts[1];
        String mutatedPayload = payload.substring(0, payload.length() - 1)
                + (payload.endsWith("A") ? "B" : "A");
        String tampered = parts[0] + "." + mutatedPayload + "." + parts[2];
        assertTrue(service.verifyAndExtractAccountId(tampered).isEmpty());
    }
}
