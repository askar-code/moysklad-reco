package ru.coproducts.moysklad.reco.vendor.controller;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import ru.coproducts.moysklad.reco.api.ApiSessionTokenService;
import ru.coproducts.moysklad.reco.domain.AccountInstallationService;
import ru.coproducts.moysklad.reco.vendor.security.VendorJwtAuthFilter;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(VendorController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(VendorApiExceptionHandler.class)
class VendorApiExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AccountInstallationService accountInstallationService;

    @MockBean
    private VendorJwtAuthFilter vendorJwtAuthFilter;

    @MockBean
    private ApiSessionTokenService apiSessionTokenService;

    @Test
    void returnsBadRequestForIllegalArgumentException() throws Exception {
        Mockito.when(accountInstallationService.activate(Mockito.eq("account-1"), Mockito.any()))
                .thenThrow(new IllegalArgumentException("Missing JSON API access token in Vendor API payload"));

        mockMvc.perform(put("/api/moysklad/vendor/1.0/apps/app-1/account-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "appUid": "app-1",
                                  "cause": "Install",
                                  "access": [
                                    {
                                      "resource": "https://api.moysklad.ru/api/remap/1.2/"
                                    }
                                  ],
                                  "subscription": {
                                    "tariffName": "Бесплатный"
                                  }
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Missing JSON API access token in Vendor API payload"));
    }
}
