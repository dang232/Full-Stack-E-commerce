package com.vnshop.orderservice;

import com.vnshop.orderservice.application.InvoiceUseCase;
import com.vnshop.orderservice.domain.InvoiceAccessDeniedException;
import com.vnshop.orderservice.infrastructure.web.ApiExceptionHandler;
import com.vnshop.orderservice.infrastructure.web.InvoiceController;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InvoiceControllerTest {
    @Test
    void unrelatedBuyerRequestingAnotherBuyerInvoiceReturnsForbidden() throws Exception {
        InvoiceUseCase useCase = mock(InvoiceUseCase.class);
        when(useCase.signedDownloadUrl(eq("invoice-a"), argThat(requester -> "buyer-b".equals(requester.buyerId()))))
                .thenThrow(new InvoiceAccessDeniedException("invoice access denied: invoice-a"));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new InvoiceController(useCase))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();

        mvc.perform(get("/invoices/invoice-a/download-url").header("X-Buyer-Id", "buyer-b"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("invoice_access_denied"));

        verify(useCase).signedDownloadUrl(eq("invoice-a"), argThat(requester -> "buyer-b".equals(requester.buyerId())));
    }
}
