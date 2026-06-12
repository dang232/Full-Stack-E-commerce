package com.vnshop.inventoryservice.infrastructure.flash;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record ReserveFlashSaleRequest(
		@NotBlank String productId,
		@Min(1) @Max(5) int quantity) {
}
