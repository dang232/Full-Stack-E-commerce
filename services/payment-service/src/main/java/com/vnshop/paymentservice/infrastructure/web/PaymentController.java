package com.vnshop.paymentservice.infrastructure.web;

import com.vnshop.paymentservice.application.GetPaymentStatusUseCase;
import com.vnshop.paymentservice.application.ProcessPaymentCommand;
import com.vnshop.paymentservice.application.ProcessPaymentUseCase;
import com.vnshop.paymentservice.domain.PaymentMethod;
import com.vnshop.paymentservice.infrastructure.gateway.MomoCallbackService;
import com.vnshop.paymentservice.infrastructure.gateway.MomoIpnRequest;
import com.vnshop.paymentservice.infrastructure.gateway.VnpayCallbackService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
    public ApiResponse<PaymentResponse> confirmCod(@Valid @RequestBody PaymentRequest request) {
        return ApiResponse.ok(PaymentResponse.fromDomain(processPaymentUseCase.process(new ProcessPaymentCommand(request.orderId(), request.buyerId(), request.amount(), PaymentMethod.COD))));
    }

    @PostMapping("/vnpay/create")
    public ApiResponse<PaymentResponse> createVnpay(@Valid @RequestBody PaymentRequest request) {
        return ApiResponse.ok(PaymentResponse.fromDomain(processPaymentUseCase.process(new ProcessPaymentCommand(request.orderId(), request.buyerId(), request.amount(), PaymentMethod.VNPAY))));
    }

    @GetMapping("/vnpay/ipn")
    public ApiResponse<VnpayIpnResponse> vnpayIpn(@RequestParam Map<String, String> parameters, @RequestHeader Map<String, String> headers) {
        VnpayCallbackService.VnpayIpnResult result = requireVnpayCallbackService().handleIpn(parameters, headers);
        return ApiResponse.ok(new VnpayIpnResponse(result.responseCode(), result.message()));
    }

    @GetMapping("/vnpay/return")
    public ApiResponse<VnpayReturnResponse> vnpayReturn(@RequestParam Map<String, String> parameters) {
        var verification = requireVnpayCallbackService().verifyReturn(parameters);
        return ApiResponse.ok(new VnpayReturnResponse(verification.validSignature(), verification.status().name(), verification.paymentId(), verification.transactionNo()));
    }

    @PostMapping("/momo/create")
    public ApiResponse<PaymentResponse> createMomo(@Valid @RequestBody PaymentRequest request) {
        return ApiResponse.ok(PaymentResponse.fromDomain(processPaymentUseCase.process(new ProcessPaymentCommand(request.orderId(), request.buyerId(), request.amount(), PaymentMethod.MOMO))));
    }

    @PostMapping("/momo/ipn")
    public ApiResponse<MomoIpnResponse> momoIpn(@RequestBody MomoIpnRequest request, @RequestHeader Map<String, String> headers) {
        MomoCallbackService.MomoIpnResult result = requireMomoCallbackService().handleIpn(request, headers);
        return ApiResponse.ok(new MomoIpnResponse(result.resultCode(), result.message()));
    }

    @GetMapping("/status/{orderId}")
    public ApiResponse<PaymentResponse> status(@PathVariable String orderId) {
        return ApiResponse.ok(PaymentResponse.fromDomain(getPaymentStatusUseCase.getByOrderId(orderId)));
    }

    private VnpayCallbackService requireVnpayCallbackService() {
        return vnpayCallbackService.orElseThrow(() -> new IllegalStateException("VNPay gateway is not enabled"));
    }

    private MomoCallbackService requireMomoCallbackService() {
        return momoCallbackService.orElseThrow(() -> new IllegalStateException("MoMo gateway is not enabled"));
    }
}
