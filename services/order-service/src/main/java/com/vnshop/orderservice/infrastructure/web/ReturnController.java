package com.vnshop.orderservice.infrastructure.web;

import com.vnshop.orderservice.application.ApproveReturnUseCase;
import com.vnshop.orderservice.application.CompleteReturnUseCase;
import com.vnshop.orderservice.application.DisputeUseCase;
import com.vnshop.orderservice.application.RejectReturnUseCase;
import com.vnshop.orderservice.application.RequestReturnUseCase;
import com.vnshop.orderservice.domain.Dispute;
import com.vnshop.orderservice.domain.Return;
import com.vnshop.orderservice.domain.port.out.ReturnRepositoryPort;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/returns")
public class ReturnController {
    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String BUYER_ID_HEADER = "X-Buyer-Id";

    private final RequestReturnUseCase requestReturnUseCase;
    private final ApproveReturnUseCase approveReturnUseCase;
    private final RejectReturnUseCase rejectReturnUseCase;
    private final CompleteReturnUseCase completeReturnUseCase;
    private final DisputeUseCase disputeUseCase;
    private final ReturnRepositoryPort returnRepositoryPort;

    public ReturnController(
            RequestReturnUseCase requestReturnUseCase,
            ApproveReturnUseCase approveReturnUseCase,
            RejectReturnUseCase rejectReturnUseCase,
            CompleteReturnUseCase completeReturnUseCase,
            DisputeUseCase disputeUseCase,
            ReturnRepositoryPort returnRepositoryPort
    ) {
        this.requestReturnUseCase = requestReturnUseCase;
        this.approveReturnUseCase = approveReturnUseCase;
        this.rejectReturnUseCase = rejectReturnUseCase;
        this.completeReturnUseCase = completeReturnUseCase;
        this.disputeUseCase = disputeUseCase;
        this.returnRepositoryPort = returnRepositoryPort;
    }

    @PostMapping
    public ReturnResponse request(
            @RequestHeader(name = USER_ID_HEADER, required = false) String userId,
            @RequestHeader(name = BUYER_ID_HEADER, required = false) String buyerId,
            @Valid @RequestBody RequestReturnRequest request
    ) {
        return ReturnResponse.fromDomain(requestReturnUseCase.request(currentBuyerId(userId, buyerId), request.subOrderId(), request.reason()));
    }

    @GetMapping
    public List<ReturnResponse> list(
            @RequestHeader(name = USER_ID_HEADER, required = false) String userId,
            @RequestHeader(name = BUYER_ID_HEADER, required = false) String buyerId
    ) {
        return returnRepositoryPort.findByBuyerId(currentBuyerId(userId, buyerId)).stream().map(ReturnResponse::fromDomain).toList();
    }

    @PostMapping("/{returnId}/approve")
    public ReturnResponse approve(@PathVariable String returnId) {
        return ReturnResponse.fromDomain(approveReturnUseCase.approve(returnId));
    }

    @PostMapping("/{returnId}/reject")
    public ReturnResponse reject(@PathVariable String returnId) {
        return ReturnResponse.fromDomain(rejectReturnUseCase.reject(returnId));
    }

    @PostMapping("/{returnId}/complete")
    public ReturnResponse complete(@PathVariable String returnId) {
        return ReturnResponse.fromDomain(completeReturnUseCase.complete(returnId));
    }

    @PostMapping("/{returnId}/disputes")
    public DisputeResponse dispute(@PathVariable String returnId, @Valid @RequestBody DisputeRequest request) {
        return DisputeResponse.fromDomain(disputeUseCase.open(returnId, request.buyerReason(), request.sellerResponse()));
    }

    private static String currentBuyerId(String userId, String buyerId) {
        if (buyerId != null && !buyerId.isBlank()) {
            return buyerId;
        }
        if (userId != null && !userId.isBlank()) {
            return userId;
        }
        throw new IllegalArgumentException("buyer id is required");
    }

    public record RequestReturnRequest(Long subOrderId, @NotBlank String reason) {
    }

    public record DisputeRequest(@NotBlank String buyerReason, String sellerResponse) {
    }

    public record ReturnResponse(
            String returnId,
            String orderId,
            Long subOrderId,
            String buyerId,
            String reason,
            String status,
            Instant requestedAt,
            Instant resolvedAt
    ) {
        static ReturnResponse fromDomain(Return orderReturn) {
            return new ReturnResponse(
                    orderReturn.returnId(),
                    orderReturn.orderId(),
                    orderReturn.subOrderId(),
                    orderReturn.buyerId(),
                    orderReturn.reason(),
                    orderReturn.status().name(),
                    orderReturn.requestedAt(),
                    orderReturn.resolvedAt()
            );
        }
    }

    public record DisputeResponse(
            String disputeId,
            String returnId,
            String buyerReason,
            String sellerResponse,
            String adminResolution,
            String status
    ) {
        static DisputeResponse fromDomain(Dispute dispute) {
            return new DisputeResponse(
                    dispute.disputeId(),
                    dispute.returnId(),
                    dispute.buyerReason(),
                    dispute.sellerResponse(),
                    dispute.adminResolution(),
                    dispute.status().name()
            );
        }
    }
}
