package com.vnshop.orderservice;

import com.vnshop.orderservice.application.CalculateCheckoutUseCase;
import com.vnshop.orderservice.infrastructure.web.ApiExceptionHandler;
import com.vnshop.orderservice.infrastructure.web.CheckoutController;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CheckoutControllerTest {

    @Test
    void paymentMethodsReturnsRichCatalogMatchingFeSchema() throws Exception {
        // The FE paymentMethodSchema (fe/src/app/lib/api/endpoints/checkout.ts)
        // expects { code, name, description?, enabled } records, not bare strings.
        MockMvc mvc = MockMvcBuilders
                .standaloneSetup(new CheckoutController(mock(CalculateCheckoutUseCase.class)))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();

        mvc.perform(get("/checkout/payment-methods"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].code").value("COD"))
                .andExpect(jsonPath("$.data[0].name").exists())
                .andExpect(jsonPath("$.data[0].enabled").value(true))
                .andExpect(jsonPath("$.data[1].code").value("VNPAY"))
                .andExpect(jsonPath("$.data[1].enabled").value(true))
                .andExpect(jsonPath("$.data[2].code").value("MOMO"))
                .andExpect(jsonPath("$.data[2].enabled").value(true));
    }
}
