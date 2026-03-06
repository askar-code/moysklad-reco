package ru.coproducts.moysklad.reco.api;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class ApiSessionAuthFilterTest {

    @Test
    void skipsVendorApiRoutes() throws Exception {
        ApiSessionTokenService tokenService = mock(ApiSessionTokenService.class);
        ApiSessionAuthFilter filter = new ApiSessionAuthFilter(tokenService);

        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/api/moysklad/vendor/1.0/apps/app-1/account-1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicBoolean proceeded = new AtomicBoolean(false);
        FilterChain chain = (req, res) -> proceeded.set(true);

        filter.doFilter(request, response, chain);

        assertTrue(proceeded.get(), "Vendor API routes must bypass API session auth");
        assertEquals(200, response.getStatus() == 0 ? 200 : response.getStatus());
    }
}
