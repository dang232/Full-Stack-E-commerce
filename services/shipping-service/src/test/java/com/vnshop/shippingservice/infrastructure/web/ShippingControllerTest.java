package com.vnshop.shippingservice.infrastructure.web;

import com.vnshop.shippingservice.application.GetTrackingUseCase;
import com.vnshop.shippingservice.domain.model.CarrierCode;
import com.vnshop.shippingservice.domain.model.LabelRequest;
import com.vnshop.shippingservice.domain.model.RateQuote;
import com.vnshop.shippingservice.domain.model.RateQuoteRequest;
import com.vnshop.shippingservice.domain.model.ShippingLabel;
import com.vnshop.shippingservice.domain.model.TrackingInfo;
import com.vnshop.shippingservice.domain.model.TrackingRequest;
import com.vnshop.shippingservice.domain.port.out.CarrierGatewayPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ShippingControllerTest {

    private MockMvc mockMvc;
    private StubGateway gateway;

    @BeforeEach
    void setUp() {
        gateway = new StubGateway();
        ShippingController controller = new ShippingController(new GetTrackingUseCase(gateway));
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void returns200WithBodyShapeWhenTrackingFound() throws Exception {
        gateway.response = new TrackingInfo(CarrierCode.GHN, "GHN-1", "delivering", "On the way", "2026-05-10T10:00:00Z");

        mockMvc.perform(get("/shipping/tracking/GHN-1").param("carrier", "GHN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.trackingCode").value("GHN-1"))
                .andExpect(jsonPath("$.data.carrier").value("GHN"))
                .andExpect(jsonPath("$.data.status").value("delivering"))
                .andExpect(jsonPath("$.data.estimatedDelivery").value("2026-05-10T10:00:00Z"))
                .andExpect(jsonPath("$.data.events").isArray());
    }

    @Test
    void returns404WhenCarrierGatewayThrows() throws Exception {
        gateway.failure = new RuntimeException("not found");

        mockMvc.perform(get("/shipping/tracking/MISSING").param("carrier", "GHN"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("TRACKING_NOT_FOUND"));
    }

    @Test
    void returns400WhenCarrierParamMissing() throws Exception {
        mockMvc.perform(get("/shipping/tracking/GHN-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("MISSING_PARAMETER"));
    }

    private static final class StubGateway implements CarrierGatewayPort {
        TrackingInfo response;
        RuntimeException failure;

        @Override
        public RateQuote quote(RateQuoteRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ShippingLabel createLabel(LabelRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TrackingInfo track(TrackingRequest request) {
            if (failure != null) {
                throw failure;
            }
            return response;
        }
    }
}
