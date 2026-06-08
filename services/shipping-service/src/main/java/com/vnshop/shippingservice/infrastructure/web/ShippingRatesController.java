package com.vnshop.shippingservice.infrastructure.web;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

/**
 * Buyer-facing zone + weight based rate quote endpoint.
 *
 * <p>Returns Standard and Express options whose fees are computed from the
 * destination province (zone) and the parcel weight. The free-shipping
 * threshold is applied server-side so the FE does not need to know the VND
 * constant.
 *
 * <p>Zone mapping (origin = VNShop Warehouse, HCMC):
 * <ul>
 *   <li>Zone 1 — same city (HCMC): base 20 000₫ + 3 000₫/kg</li>
 *   <li>Zone 2 — same region (South VN): base 30 000₫ + 5 000₫/kg</li>
 *   <li>Zone 3 — inter-region: base 45 000₫ + 7 000₫/kg</li>
 * </ul>
 *
 * <p>Free shipping threshold: {@code orderTotalVnd > 500 000} → Standard fee = 0.
 * Express is always charged (1.5× the Standard base calculation).
 */
@RestController
public class ShippingRatesController {

    @Value("${shipping.free-threshold-vnd:500000}")
    private long freeShippingThresholdVnd;

    private static final long DEFAULT_WEIGHT_GRAMS = 1_000L;

    // Zone 1 — same city as the warehouse (HCMC). Accept common abbreviations.
    private static final Set<String> ZONE_1 = Set.of(
            "hcmc", "ho chi minh", "hồ chí minh", "tp hcm", "tp.hcm", "tphcm",
            "tp. hồ chí minh", "thành phố hồ chí minh", "tp. ho chi minh"
    );

    // Zone 2 — southern provinces in the same delivery region as HCMC.
    private static final Set<String> ZONE_2 = Set.of(
            "bình dương", "binh duong", "đồng nai", "dong nai", "long an",
            "tiền giang", "tien giang", "bến tre", "ben tre", "vĩnh long", "vinh long",
            "trà vinh", "tra vinh", "sóc trăng", "soc trang", "bạc liêu", "bac lieu",
            "cà mau", "ca mau", "kiên giang", "kien giang", "an giang",
            "đồng tháp", "dong thap", "cần thơ", "can tho", "hậu giang", "hau giang",
            "tây ninh", "tay ninh", "bình phước", "binh phuoc",
            "bà rịa - vũng tàu", "ba ria vung tau", "vũng tàu", "vung tau"
    );

    @PostMapping("/shipping/rates")
    public ApiResponse<RateQuotesResponse> rates(@Valid @RequestBody ShippingRatesRequest request) {
        String province = normalise(request.province());
        int zone = detectZone(province);

        long weightGrams = (request.parcel() != null && request.parcel().weightGrams() > 0)
                ? request.parcel().weightGrams()
                : DEFAULT_WEIGHT_GRAMS;

        long orderTotal = request.orderTotalVnd() != null ? request.orderTotalVnd() : 0L;
        boolean freeStandard = orderTotal > freeShippingThresholdVnd;

        long baseVnd = switch (zone) {
            case 1 -> 20_000L;
            case 2 -> 30_000L;
            default -> 45_000L;
        };
        long perKgVnd = switch (zone) {
            case 1 -> 3_000L;
            case 2 -> 5_000L;
            default -> 7_000L;
        };

        // Weight surcharge: extra kg above the first 1 kg, rounded up.
        long extraKg = weightGrams > 1_000 ? (weightGrams - 1_000 + 999) / 1_000 : 0;
        long standardBase = baseVnd + perKgVnd * extraKg;
        long standardFee = freeStandard ? 0L : standardBase;
        // Express: 1.5× the unthresholded standard calculation — buyers pay for speed.
        long expressFee = Math.round(standardBase * 1.5);

        List<RateQuoteResponse> options = List.of(
                new RateQuoteResponse("GHN", "STANDARD", standardFee, "3-5 days"),
                new RateQuoteResponse("GHTK", "EXPRESS", expressFee, "1-2 days")
        );
        return ApiResponse.ok(new RateQuotesResponse(options));
    }

    private static int detectZone(String province) {
        if (ZONE_1.contains(province)) return 1;
        if (ZONE_2.contains(province)) return 2;
        return 3;
    }

    private static String normalise(String province) {
        if (province == null) return "";
        return province.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
