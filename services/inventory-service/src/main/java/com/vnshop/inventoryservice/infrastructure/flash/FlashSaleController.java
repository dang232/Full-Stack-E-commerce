package com.vnshop.inventoryservice.infrastructure.flash;

import com.vnshop.inventoryservice.application.ReserveFlashSaleCommand;
import com.vnshop.inventoryservice.application.ReserveFlashSaleResult;
import com.vnshop.inventoryservice.application.ReserveFlashSaleUseCase;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/flash-sale")
public class FlashSaleController {
	private final ReserveFlashSaleUseCase reserveFlashSaleUseCase;

	public FlashSaleController(ReserveFlashSaleUseCase reserveFlashSaleUseCase) {
		this.reserveFlashSaleUseCase = reserveFlashSaleUseCase;
	}

	@PostMapping("/reserve")
	public ApiResponse<ReserveFlashSaleResponse> reserve(@Valid @RequestBody ReserveFlashSaleRequest request) {
		ReserveFlashSaleResult result = reserveFlashSaleUseCase.reserve(
				new ReserveFlashSaleCommand(request.productId(), request.buyerId(), request.quantity()));
		return ApiResponse.ok(new ReserveFlashSaleResponse(
				result.reservationId(),
				result.status(),
				result.expiresAt()));
	}

	@GetMapping("/stock/{productId}")
	public ApiResponse<StockResponse> stock(@PathVariable String productId) {
		return ApiResponse.ok(new StockResponse(productId, reserveFlashSaleUseCase.getStock(productId)));
	}

	@PostMapping("/release/{reservationId}")
	public ApiResponse<Void> release(@PathVariable String reservationId) {
		reserveFlashSaleUseCase.release(UUID.fromString(reservationId));
		return ApiResponse.ok(null);
	}
}
