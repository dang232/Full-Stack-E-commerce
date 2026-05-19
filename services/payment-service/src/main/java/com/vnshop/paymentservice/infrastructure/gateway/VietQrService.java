package com.vnshop.paymentservice.infrastructure.gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * VietQR URL generator. The vietqr.io image service is a stateless URL spec —
 * no API key, no webhook. Encodes bank BIN + account + amount + reference into
 * a QR image URL the FE can render directly.
 *
 * <p>Confirmation is manual: the buyer transfers from their bank app, the
 * merchant sees the credit notification, an admin marks the payment
 * COMPLETED via {@code POST /payment/vietqr/confirm/{paymentId}}. No
 * automatic IPN — this is the path that doesn't need a public domain.
 *
 * <p>VCB BIN is 970436. Other VN banks: TCB 970407, MBB 970422,
 * see https://api.vietqr.io/v2/banks for the full list.
 */
@Service
@ConditionalOnProperty(name = "payment.vietqr.enabled", havingValue = "true", matchIfMissing = true)
public class VietQrService {
    private static final String IMAGE_BASE = "https://img.vietqr.io/image";
    private static final String DEFAULT_TEMPLATE = "compact2";

    private final String bankBin;
    private final String accountNo;
    private final String accountName;
    private final String template;

    public VietQrService(
            @Value("${vietqr.bank-bin:970436}") String bankBin,
            @Value("${vietqr.account-no:}") String accountNo,
            @Value("${vietqr.account-name:}") String accountName,
            @Value("${vietqr.template:compact2}") String template) {
        this.bankBin = bankBin;
        this.accountNo = accountNo;
        this.accountName = accountName;
        this.template = template == null || template.isBlank() ? DEFAULT_TEMPLATE : template;
    }

    public boolean isConfigured() {
        return !accountNo.isBlank() && !accountName.isBlank();
    }

    /**
     * Build the QR image URL for a payment. The reference (typically the
     * payment id or order number) is encoded in the QR's addInfo field so
     * the merchant can match the bank credit back to the payment.
     */
    public VietQrPayment generate(String paymentRef, BigDecimal amount) {
        if (!isConfigured()) {
            throw new IllegalStateException(
                    "VietQR is not configured — set VIETQR_ACCOUNT_NO and VIETQR_ACCOUNT_NAME");
        }
        String url = IMAGE_BASE + "/" + bankBin + "-" + accountNo + "-" + template + ".png"
                + "?amount=" + amount.toPlainString()
                + "&addInfo=" + URLEncoder.encode(paymentRef, StandardCharsets.UTF_8)
                + "&accountName=" + URLEncoder.encode(accountName, StandardCharsets.UTF_8);
        return new VietQrPayment(url, bankBin, accountNo, accountName, paymentRef, amount);
    }

    public record VietQrPayment(
            String qrImageUrl,
            String bankBin,
            String accountNo,
            String accountName,
            String reference,
            BigDecimal amount
    ) {}
}
