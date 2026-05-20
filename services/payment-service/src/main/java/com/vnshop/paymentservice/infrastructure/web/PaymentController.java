package com.vnshop.paymentservice.infrastructure.web;

import com.stripe.exception.StripeException;
import com.vnshop.paymentservice.application.GetPaymentStatusUseCase;
import com.vnshop.paymentservice.application.PaymentMethodInput;
import com.vnshop.paymentservice.application.PaymentPromotionService;
import com.vnshop.paymentservice.application.ProcessPaymentCommand;
import com.vnshop.paymentservice.application.ProcessPaymentUseCase;
import com.vnshop.paymentservice.domain.Payment;
import com.vnshop.paymentservice.domain.port.out.PaymentRepositoryPort;
import com.vnshop.paymentservice.infrastructure.config.JwtPrincipalUtil;
import com.vnshop.paymentservice.infrastructure.gateway.MomoCallbackService;
import com.vnshop.paymentservice.infrastructure.gateway.MomoIpnRequest;
import com.vnshop.paymentservice.infrastructure.gateway.VietQrService;
import com.vnshop.paymentservice.infrastructure.gateway.VnpayCallbackService;
import com.vnshop.paymentservice.infrastructure.paypal.PayPalGateway;
import com.vnshop.paymentservice.infrastructure.stripe.StripeGateway;
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
    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private final ProcessPaymentUseCase processPaymentUseCase;
    private final GetPaymentStatusUseCase getPaymentStatusUseCase;
    private final Optional<VnpayCallbackService> vnpayCallbackService;
    private final Optional<MomoCallbackService> momoCallbackService;
    private final Optional<VietQrService> vietQrService;
    private final Optional<StripeGateway> stripeGateway;
    private final Optional<PayPalGateway> payPalGateway;
    private final PaymentPromotionService promotionService;
    private final PaymentRepositoryPort paymentRepository;

    public PaymentController(
            ProcessPaymentUseCase processPaymentUseCase,
            GetPaymentStatusUseCase getPaymentStatusUseCase,
            Optional<VnpayCallbackService> vnpayCallbackService,
            Optional<MomoCallbackService> momoCallbackService,
            Optional<VietQrService> vietQrService,
            Optional<StripeGateway> stripeGateway,
            Optional<PayPalGateway> payPalGateway,
            PaymentPromotionService promotionService,
            PaymentRepositoryPort paymentRepository) {
        this.processPaymentUseCase = processPaymentUseCase;
        this.getPaymentStatusUseCase = getPaymentStatusUseCase;
        this.vnpayCallbackService = vnpayCallbackService;
        this.momoCallbackService = momoCallbackService;
        this.vietQrService = vietQrService;
        this.stripeGateway = stripeGateway;
        this.payPalGateway = payPalGateway;
        this.promotionService = promotionService;
        this.paymentRepository = paymentRepository;
    }

    @PostMapping("/cod/confirm")
    public ApiResponse<PaymentResponse> confirmCod(
            @RequestHeader(name = IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey,
            @Valid @RequestBody PaymentRequest request
    ) {
        return ApiResponse.ok(PaymentResponse.fromDomain(processPaymentUseCase.process(new ProcessPaymentCommand(request.orderId(), JwtPrincipalUtil.currentUserId(), PaymentMethodInput.COD, idempotencyKey))));
    }

    @PostMapping("/vnpay/create")
    public ApiResponse<PaymentResponse> createVnpay(
            @RequestHeader(name = IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey,
            @Valid @RequestBody PaymentRequest request
    ) {
        return ApiResponse.ok(PaymentResponse.fromDomain(processPaymentUseCase.process(new ProcessPaymentCommand(request.orderId(), JwtPrincipalUtil.currentUserId(), PaymentMethodInput.VNPAY, idempotencyKey))));
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
    public ApiResponse<PaymentResponse> createMomo(
            @RequestHeader(name = IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey,
            @Valid @RequestBody PaymentRequest request
    ) {
        return ApiResponse.ok(PaymentResponse.fromDomain(processPaymentUseCase.process(new ProcessPaymentCommand(request.orderId(), JwtPrincipalUtil.currentUserId(), PaymentMethodInput.MOMO, idempotencyKey))));
    }

    @PostMapping("/momo/ipn")
    public ApiResponse<MomoIpnResponse> momoIpn(@RequestBody MomoIpnRequest request, @RequestHeader Map<String, String> headers) {
        MomoCallbackService.MomoIpnResult result = requireMomoCallbackService().handleIpn(request, headers);
        return ApiResponse.ok(new MomoIpnResponse(result.resultCode(), result.message()));
    }

    /**
     * VietQR create flow. Creates a PENDING payment via the standard pipeline
     * and returns a vietqr.io QR image URL the FE renders for the buyer to
     * scan. Confirmation arrives manually via the admin endpoint
     * {@code POST /admin/vietqr/confirm/{paymentId}} (lives on
     * {@link AdminVietQrController} so the gateway's /admin/vietqr/** route
     * resolves cleanly without colliding with the /payment class prefix).
     */
    @PostMapping("/vietqr/create")
    public ApiResponse<VietQrCreateResponse> createVietQr(
            @RequestHeader(name = IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey,
            @Valid @RequestBody PaymentRequest request
    ) {
        VietQrService qrService = vietQrService.orElseThrow(() ->
                new IllegalStateException("VietQR is not enabled — set payment.vietqr.enabled=true"));
        Payment payment = processPaymentUseCase.process(new ProcessPaymentCommand(
                request.orderId(), JwtPrincipalUtil.currentUserId(),
                PaymentMethodInput.VIETQR, idempotencyKey));
        VietQrService.VietQrPayment qr = qrService.generate(
                payment.paymentId().toString(), payment.amount());
        return ApiResponse.ok(new VietQrCreateResponse(
                PaymentResponse.fromDomain(payment),
                qr.qrImageUrl(),
                qr.bankBin(),
                qr.accountNo(),
                qr.accountName(),
                qr.reference()));
    }

    /**
     * PayPal create flow. Persists a PENDING payment via the standard pipeline,
     * builds a {@code CAPTURE}-intent order on PayPal, and returns the order id
     * the FE Smart Buttons need. Capture is initiated separately at
     * {@code POST /payment/paypal/capture/{paypalOrderId}} so the FE flow stays
     * synchronous (no webhook needed for sandbox).
     */
    @PostMapping("/paypal/create")
    public ApiResponse<PayPalCreateResponse> createPayPal(
            @RequestHeader(name = IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey,
            @Valid @RequestBody PaymentRequest request
    ) {
        PayPalGateway gateway = payPalGateway.orElseThrow(() ->
                new IllegalStateException("PayPal is not enabled — set payment.paypal.enabled=true"));
        Payment payment = processPaymentUseCase.process(new ProcessPaymentCommand(
                request.orderId(), JwtPrincipalUtil.currentUserId(),
                PaymentMethodInput.PAYPAL, idempotencyKey));
        PayPalGateway.PayPalOrder order = gateway.createOrder(payment);
        return ApiResponse.ok(PayPalCreateResponse.of(
                payment,
                gateway.properties().clientId(),
                order.paypalOrderId(),
                order.status(),
                order.externalAmount(),
                order.externalCurrency(),
                order.fxRate()));
    }

    /**
     * PayPal capture endpoint. The FE calls this from {@code onApprove} once
     * the buyer authorises in the Smart Buttons popup. Capture commits via
     * {@link PaymentPromotionService} synchronously — no FE polling needed,
     * the response IS the confirmation.
     *
     * <p>The FE already has both {@code paymentId} (from {@code /paypal/create})
     * and {@code paypalOrderId} (from PayPal's {@code onApprove} callback), so
     * it passes both back here. Resolving the internal id from PayPal's order
     * metadata is technically possible but would need a {@code findByExternalRef}
     * query — not worth adding for a path the FE already knows.
     */
    @PostMapping("/paypal/capture/{paymentId}/{paypalOrderId}")
    public ApiResponse<PaymentResponse> capturePayPal(
            @PathVariable String paymentId,
            @PathVariable String paypalOrderId) {
        PayPalGateway gateway = payPalGateway.orElseThrow(() ->
                new IllegalStateException("PayPal is not enabled — set payment.paypal.enabled=true"));
        java.util.UUID id = java.util.UUID.fromString(paymentId);
        Payment existing = paymentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("payment not found: " + paymentId));
        if (existing.method() != com.vnshop.paymentservice.domain.PaymentMethod.PAYPAL) {
            throw new IllegalArgumentException("payment is not PayPal: " + paymentId);
        }
        PayPalGateway.PayPalCapture capture = gateway.capture(paypalOrderId);
        PaymentPromotionService.PromotionResult result = promotionService.promote(
                PaymentPromotionService.PromotionCommand.fromCallback(
                        id, "PAYPAL", capture.captureId(),
                        java.util.UUID.randomUUID(), capture.captureId(),
                        com.vnshop.paymentservice.infrastructure.gateway.PaymentCallbackHasher.sha256(capture.captureId())));
        return ApiResponse.ok(PaymentResponse.fromDomain(result.payment()));
    }

    @GetMapping("/status/{orderId}")
    public ApiResponse<PaymentResponse> status(@PathVariable String orderId) {
        return ApiResponse.ok(PaymentResponse.fromDomain(getPaymentStatusUseCase.getByOrderId(orderId)));
    }

    /**
     * Stripe create flow. Persists a PENDING payment via the standard pipeline
     * and returns a Stripe {@code clientSecret} the FE Elements provider mounts
     * to render the embedded card form. Webhook completion arrives separately
     * at {@code POST /payment/stripe/webhook}.
     */
    @PostMapping("/stripe/create")
    public ApiResponse<StripeCreateResponse> createStripe(
            @RequestHeader(name = IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey,
            @Valid @RequestBody PaymentRequest request
    ) throws StripeException {
        StripeGateway gateway = stripeGateway.orElseThrow(() ->
                new IllegalStateException("Stripe is not enabled — set payment.stripe.enabled=true"));
        Payment payment = processPaymentUseCase.process(new ProcessPaymentCommand(
                request.orderId(), JwtPrincipalUtil.currentUserId(),
                PaymentMethodInput.STRIPE, idempotencyKey));
        StripeGateway.StripeIntent intent = gateway.createPaymentIntent(payment);
        return ApiResponse.ok(StripeCreateResponse.of(
                payment,
                gateway.properties().publishableKey(),
                intent.clientSecret(),
                intent.intentId(),
                intent.externalAmount(),
                intent.externalCurrency(),
                intent.fxRate()));
    }

    private VnpayCallbackService requireVnpayCallbackService() {
        return vnpayCallbackService.orElseThrow(() -> new IllegalStateException("VNPay gateway is not enabled"));
    }

    private MomoCallbackService requireMomoCallbackService() {
        return momoCallbackService.orElseThrow(() -> new IllegalStateException("MoMo gateway is not enabled"));
    }
}
