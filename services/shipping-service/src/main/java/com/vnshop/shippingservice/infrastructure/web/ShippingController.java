package com.vnshop.shippingservice.infrastructure.web;

import com.vnshop.shippingservice.application.GetTrackingUseCase;
import com.vnshop.shippingservice.domain.model.CarrierCode;
import com.vnshop.shippingservice.domain.model.TrackingInfo;
import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/shipping")
@Validated
public class ShippingController {
    private final GetTrackingUseCase getTrackingUseCase;

    public ShippingController(GetTrackingUseCase getTrackingUseCase) {
        this.getTrackingUseCase = getTrackingUseCase;
    }

    @GetMapping("/tracking/{trackingCode}")
    public ApiResponse<TrackingResponse> getTracking(
            @PathVariable @NotBlank String trackingCode,
            @RequestParam("carrier") CarrierCode carrier) {
        if (trackingCode == null || trackingCode.isBlank()) {
            throw new IllegalArgumentException("trackingCode must not be blank");
        }
        Optional<TrackingInfo> info = getTrackingUseCase.get(carrier, trackingCode);
        TrackingInfo tracking = info.orElseThrow(() ->
                new TrackingNotFoundException("Tracking not found for code " + trackingCode));
        return ApiResponse.ok(toResponse(tracking));
    }

    private static TrackingResponse toResponse(TrackingInfo info) {
        return new TrackingResponse(
                info.trackingCode(),
                info.carrier() == null ? null : info.carrier().name(),
                info.status(),
                info.updatedAt(),
                List.of());
    }
}
