package com.vnshop.shippingservice.application;

import com.vnshop.shippingservice.domain.exception.CarrierTrackingNotFoundException;
import com.vnshop.shippingservice.domain.model.CarrierCode;
import com.vnshop.shippingservice.domain.model.TrackingInfo;
import com.vnshop.shippingservice.domain.model.TrackingRequest;
import com.vnshop.shippingservice.domain.port.out.CarrierGatewayPort;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GetTrackingUseCaseTest {

    @Test
    void returnsTrackingWhenCarrierResolvesIt() {
        TrackingInfo info = new TrackingInfo(CarrierCode.GHN, "GHN-001", "delivering", "On the way", "2026-05-10T10:00:00Z");
        StubGateway gateway = new StubGateway();
        gateway.response = info;
        GetTrackingUseCase useCase = new GetTrackingUseCase(gateway);

        Optional<TrackingInfo> result = useCase.get(CarrierCode.GHN, "GHN-001");

        assertThat(result).isPresent().get().isSameAs(info);
        assertThat(gateway.lastRequest.carrier()).isEqualTo(CarrierCode.GHN);
        assertThat(gateway.lastRequest.trackingCode()).isEqualTo("GHN-001");
    }

    @Test
    void returnsEmptyWhenCarrierConfirmsNotFound() {
        StubGateway gateway = new StubGateway();
        gateway.failure = new CarrierTrackingNotFoundException("not found");
        GetTrackingUseCase useCase = new GetTrackingUseCase(gateway);

        Optional<TrackingInfo> result = useCase.get(CarrierCode.GHN, "GHN-MISS");

        assertThat(result).isEmpty();
    }

    @Test
    void propagatesCarrierFailureSoControllerCanReport500() {
        StubGateway gateway = new StubGateway();
        gateway.failure = new RuntimeException("carrier timeout");
        GetTrackingUseCase useCase = new GetTrackingUseCase(gateway);

        assertThatThrownBy(() -> useCase.get(CarrierCode.GHN, "GHN-001"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("carrier timeout");
    }

    @Test
    void rejectsBlankTrackingCode() {
        GetTrackingUseCase useCase = new GetTrackingUseCase(new StubGateway());

        assertThatThrownBy(() -> useCase.get(CarrierCode.GHN, "   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("trackingCode");
    }

    private static final class StubGateway implements CarrierGatewayPort {
        TrackingInfo response;
        RuntimeException failure;
        TrackingRequest lastRequest;

        @Override
        public com.vnshop.shippingservice.domain.model.RateQuote quote(com.vnshop.shippingservice.domain.model.RateQuoteRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public com.vnshop.shippingservice.domain.model.ShippingLabel createLabel(com.vnshop.shippingservice.domain.model.LabelRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TrackingInfo track(TrackingRequest request) {
            this.lastRequest = request;
            if (failure != null) {
                throw failure;
            }
            return response;
        }
    }
}

