package com.vnshop.paymentservice.infrastructure.web;

import com.vnshop.paymentservice.application.GetPaymentStatusUseCase;
import com.vnshop.paymentservice.application.ProcessPaymentUseCase;
import com.vnshop.paymentservice.domain.Payment;
import com.vnshop.paymentservice.infrastructure.gateway.MomoCallbackService;
import com.vnshop.paymentservice.infrastructure.gateway.MomoIpnRequest;
import com.vnshop.paymentservice.infrastructure.gateway.VnpayCallbackService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/payment")
public class PaymentController {
    private final ProcessPaymentUseCase processPaymentUseCase;
    private final GetPaymentStatusUseCase getPaymentStatusUseCase;
    private final Optional<VnpayCallbackService> vnpayCallbackService;
    private final Optional<MomoCallbackService> momoCallbackService;

    public PaymentController(ProcessPaymentUseCase processPaymentUseCase, GetPaymentStatusUseCase getPaymentStatusUseCase, Optional<VnpayCallbackService> vnpayCallbackService, Optional<MomoCallbackService> momoCallbackService) {
        this.processPaymentUseCase = processPaymentUseCase;
        this.getPaymentStatusUseCase = getPaymentStatusUseCase;
        this.vnpayCallbackService = vnpayCallbackService;
        this.momoCallbackService = momoCallbackService;
    }

    @PostMapping("/cod/confirm")
    public PaymentResponse confirmCod(@Valid @RequestBody PaymentRequest request) {
        return PaymentResponse.fromDomain(processPaymentUseCase.process(request.orderId(), request.buyerId(), request.amount(), Payment.Method.COD));
    }

    @PostMapping("/vnpay/create")
    public PaymentResponse createVnpay(@Valid @RequestBody PaymentRequest request) {
        return PaymentResponse.fromDomain(processPaymentUseCase.process(request.orderId(), request.buyerId(), request.amount(), Payment.Method.VNPAY));
    }

    @GetMapping("/vnpay/ipn")
    public VnpayIpnResponse vnpayIpn(@RequestParam Map<String, String> parameters) {
        VnpayCallbackService.VnpayIpnResult result = requireVnpayCallbackService().handleIpn(parameters);
        return new VnpayIpnResponse(result.responseCode(), result.message());
    }

    @GetMapping("/vnpay/return")
    public VnpayReturnResponse vnpayReturn(@RequestParam Map<String, String> parameters) {
        var verification = requireVnpayCallbackService().verifyReturn(parameters);
        return new VnpayReturnResponse(verification.validSignature(), verification.status().name(), verification.paymentId(), verification.transactionNo());
    }

    @PostMapping("/momo/create")
    public PaymentResponse createMomo(@Valid @RequestBody PaymentRequest request) {
        return PaymentResponse.fromDomain(processPaymentUseCase.process(request.orderId(), request.buyerId(), request.amount(), Payment.Method.MOMO));
    }

    @PostMapping("/momo/ipn")
    public MomoIpnResponse momoIpn(@RequestBody MomoIpnRequest request) {
        MomoCallbackService.MomoIpnResult result = requireMomoCallbackService().handleIpn(request);
        return new MomoIpnResponse(result.resultCode(), result.message());
    }

    @GetMapping("/status/{orderId}")
    public PaymentResponse status(@PathVariable String orderId) {
        return PaymentResponse.fromDomain(getPaymentStatusUseCase.getByOrderId(orderId));
    }

    private VnpayCallbackService requireVnpayCallbackService() {
        return vnpayCallbackService.orElseThrow(() -> new IllegalStateException("VNPay gateway is not enabled"));
    }

    private MomoCallbackService requireMomoCallbackService() {
        return momoCallbackService.orElseThrow(() -> new IllegalStateException("MoMo gateway is not enabled"));
    }

    public record PaymentRequest(
            @NotBlank String orderId,
            @NotBlank String buyerId,
            @NotNull @Positive BigDecimal amount
    ) {
    }

    public record VnpayIpnResponse(String RspCode, String Message) {
    }

    public record VnpayReturnResponse(boolean validSignature, String gatewayStatus, String paymentId, String transactionNo) {
    }

    public record MomoIpnResponse(int resultCode, String message) {
    }

    public record PaymentResponse(
            String paymentId,
            String orderId,
            String buyerId,
            BigDecimal amount,
            String method,
            String status,
            String transactionRef,
            Instant createdAt
    ) {
        static PaymentResponse fromDomain(Payment payment) {
            return new PaymentResponse(
                    payment.paymentId(),
                    payment.orderId(),
                    payment.buyerId(),
                    payment.amount(),
                    payment.method().name(),
                    payment.status().name(),
                    payment.transactionRef(),
                    payment.createdAt()
            );
        }
    }
}
