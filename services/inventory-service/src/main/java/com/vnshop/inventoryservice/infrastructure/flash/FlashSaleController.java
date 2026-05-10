package com.vnshop.inventoryservice.infrastructure.flash;

import com.vnshop.inventoryservice.application.ReserveFlashSaleUseCase;
import com.vnshop.inventoryservice.domain.FlashSaleReservation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
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
	public ResponseEntity<ReserveFlashSaleResponse> reserve(@Valid @RequestBody ReserveFlashSaleRequest request) {
		FlashSaleReservation reservation = reserveFlashSaleUseCase.reserve(
				request.productId(),
				request.buyerId(),
				request.quantity());
		return ResponseEntity.ok(new ReserveFlashSaleResponse(
				reservation.getReservationId(),
				reservation.getStatus().name(),
				reservation.getExpiresAt().toString()));
	}

	@GetMapping("/stock/{productId}")
	public ResponseEntity<StockResponse> stock(@PathVariable String productId) {
		return ResponseEntity.ok(new StockResponse(productId, reserveFlashSaleUseCase.getStock(productId)));
	}

	@PostMapping("/release/{reservationId}")
	public ResponseEntity<Void> release(@PathVariable String reservationId) {
		reserveFlashSaleUseCase.release(reservationId);
		return ResponseEntity.noContent().build();
	}

	public record ReserveFlashSaleRequest(
			@NotBlank String productId,
			@NotBlank String buyerId,
			@Min(1) int quantity) {
	}

	public record ReserveFlashSaleResponse(String reservationId, String status, String expiresAt) {
	}

	public record StockResponse(String productId, long stock) {
	}
}
