package ru.coproducts.moysklad.reco.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import ru.coproducts.moysklad.reco.vendor.dto.VendorActivationRequest;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AccountInstallationServiceTest {

    private final AccountInstallationRepository repository = mock(AccountInstallationRepository.class);
    private final AccountInstallationService service = new AccountInstallationService(repository);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void activatesUsingVendorApiPayload() {
        VendorActivationRequest request = new VendorActivationRequest();
        request.setAppUid("aitest.askar");
        request.setCause("Install");

        VendorActivationRequest.AccessInfo access = new VendorActivationRequest.AccessInfo();
        access.setResource("https://api.moysklad.ru/api/remap/1.2/");
        access.setAccessToken("token-123");
        request.setAccess(List.of(access));

        VendorActivationRequest.SubscriptionInfo subscription = new VendorActivationRequest.SubscriptionInfo();
        subscription.setTariffName("Бесплатный");
        request.setSubscription(subscription);

        when(repository.findByAccountId("acc-1")).thenReturn(Optional.empty());
        when(repository.save(org.mockito.ArgumentMatchers.any(AccountInstallation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AccountInstallation installation = service.activate("acc-1", request);

        assertEquals("acc-1", installation.getAccountId());
        assertEquals("aitest.askar", installation.getAppId());
        assertEquals("token-123", installation.getAccessToken());
        assertEquals("Бесплатный", installation.getTariff());
        assertTrue(installation.isActive());
    }

    @Test
    void activatesUsingSnakeCaseAccessToken() throws Exception {
        String json = """
                {
                  "appUid": "aitest.askar",
                  "cause": "Install",
                  "access": [
                    {
                      "resource": "https://api.moysklad.ru/api/remap/1.2/",
                      "access_token": "token-456"
                    }
                  ],
                  "subscription": {
                    "tariffName": "Бесплатный"
                  }
                }
                """;

        VendorActivationRequest request = objectMapper.readValue(json, VendorActivationRequest.class);

        when(repository.findByAccountId("acc-2")).thenReturn(Optional.empty());
        when(repository.save(org.mockito.ArgumentMatchers.any(AccountInstallation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AccountInstallation installation = service.activate("acc-2", request);

        assertEquals("token-456", installation.getAccessToken());
        assertEquals("acc-2", installation.getAccountId());
        assertTrue(installation.isActive());
    }
}
