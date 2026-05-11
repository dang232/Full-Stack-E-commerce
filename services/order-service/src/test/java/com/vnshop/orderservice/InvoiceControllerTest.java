package com.vnshop.orderservice;

import com.vnshop.orderservice.application.InvoiceUseCase;
import com.vnshop.orderservice.domain.InvoiceAccessDeniedException;
import com.vnshop.orderservice.infrastructure.web.ApiExceptionHandler;
import com.vnshop.orderservice.infrastructure.web.InvoiceController;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InvoiceControllerTest {
    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void unrelatedBuyerRequestingAnotherBuyerInvoiceReturnsForbidden() throws Exception {
        InvoiceUseCase useCase = mock(InvoiceUseCase.class);
        UUID invoiceId = UUID.fromString("00000000-0000-0000-0000-000000000010");
        when(useCase.signedDownloadUrl(eq(invoiceId), argThat(requester -> "buyer-b".equals(requester.buyerId()))))
                .thenThrow(new InvoiceAccessDeniedException("invoice access denied: " + invoiceId));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new InvoiceController(useCase))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("sub", "buyer-b")
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

        mvc.perform(get("/invoices/{invoiceId}/download-url", invoiceId))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("INVOICE_ACCESS_DENIED"));

        verify(useCase).signedDownloadUrl(eq(invoiceId), argThat(requester -> "buyer-b".equals(requester.buyerId())));
    }
}
