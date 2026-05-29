package com.vnshop.sellerfinanceservice.infrastructure.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vnshop.sellerfinanceservice.application.RefundWalletUseCase;
import com.vnshop.sellerfinanceservice.domain.CommissionCalculator;
import com.vnshop.sellerfinanceservice.domain.CommissionTier;
import com.vnshop.sellerfinanceservice.domain.SellerWallet;
import com.vnshop.sellerfinanceservice.domain.port.out.SellerWalletRepositoryPort;
import com.vnshop.sellerfinanceservice.infrastructure.config.CommissionRateConfig;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PaymentRefundedFinanceListenerTest {

    private static final String SELLER_ID = "SELLER-7";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final InMemoryWallets wallets = new InMemoryWallets();
    private final CommissionCalculator calculator = standardCalculator();
    private final RefundWalletUseCase useCase = new RefundWalletUseCase(wallets, calculator);
    private final PaymentRefundedFinanceListener listener =
            new PaymentRefundedFinanceListener(useCase, objectMapper);

    @Test
    void debitsSellerNetWhenRefundEventArrives() {
        // Seed wallet as if OrderCreated had previously credited 90,000 (10% commission).
        SellerWallet seeded = new SellerWallet(SELLER_ID,
                new BigDecimal("90000.00"), BigDecimal.ZERO, new BigDecimal("90000.00"), null);
        wallets.save(seeded);

        listener.onPaymentRefunded(eventJson("100000", SELLER_ID));

        SellerWallet after = wallets.findBySellerId(SELLER_ID).orElseThrow();
        assertThat(after.availableBalance()).isEqualByComparingTo("0.00");
        // totalEarned is gross-lifetime — refund does not rewrite history.
        assertThat(after.totalEarned()).isEqualByComparingTo("90000.00");
    }

    @Test
    void clampsToZeroWhenSellerAlreadyPaidOut() {
        // Seller already paid out — wallet balance is zero. Refund cannot
        // produce a negative balance; clamps at zero.
        wallets.save(new SellerWallet(SELLER_ID,
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("90000.00"), null));

        listener.onPaymentRefunded(eventJson("100000", SELLER_ID));

        assertThat(wallets.findBySellerId(SELLER_ID).orElseThrow().availableBalance())
                .isEqualByComparingTo("0.00");
    }

    @Test
    void skipsWhenWalletMissing() {
        // Defensive: refund arrives before any credit ever ran (shouldn't happen
        // in production, but a Kafka redelivery with a fresh consumer group could).
        listener.onPaymentRefunded(eventJson("100000", "UNKNOWN-SELLER"));

        assertThat(wallets.findBySellerId("UNKNOWN-SELLER")).isEmpty();
    }

    @Test
    void skipsMalformedAmount() {
        wallets.save(new SellerWallet(SELLER_ID,
                new BigDecimal("90000.00"), BigDecimal.ZERO, new BigDecimal("90000.00"), null));

        listener.onPaymentRefunded(eventJson("not-a-number", SELLER_ID));

        assertThat(wallets.findBySellerId(SELLER_ID).orElseThrow().availableBalance())
                .isEqualByComparingTo("90000.00");
    }

    @Test
    void skipsBlankSellerId() {
        listener.onPaymentRefunded(eventJson("100000", ""));
        assertThat(wallets.savedCount).isZero();
    }

    @Test
    void usesCommissionTierFromEventWhenPresent() {
        // VERIFIED tier has 8% commission → sellerNet = 92,000 for a 100,000 order.
        CommissionRateConfig rateConfig = new CommissionRateConfig();
        rateConfig.setTiers(Map.of(
                CommissionTier.STANDARD, new BigDecimal("0.10"),
                CommissionTier.VERIFIED, new BigDecimal("0.08")));
        CommissionCalculator calc = new CommissionCalculator(rateConfig);
        RefundWalletUseCase tieredUseCase = new RefundWalletUseCase(wallets, calc);
        PaymentRefundedFinanceListener tieredListener =
                new PaymentRefundedFinanceListener(tieredUseCase, objectMapper);

        wallets.save(new SellerWallet(SELLER_ID,
                new BigDecimal("92000.00"), BigDecimal.ZERO, new BigDecimal("92000.00"), null));

        tieredListener.onPaymentRefunded(eventJsonWithTier("100000", SELLER_ID, "VERIFIED"));

        SellerWallet after = wallets.findBySellerId(SELLER_ID).orElseThrow();
        // Debit = sellerNet at VERIFIED tier = 92,000
        assertThat(after.availableBalance()).isEqualByComparingTo("0.00");
    }

    @Test
    void defaultsToStandardWhenCommissionTierMissing() {
        // Event without commissionTier field — backward compat with older publishers.
        wallets.save(new SellerWallet(SELLER_ID,
                new BigDecimal("90000.00"), BigDecimal.ZERO, new BigDecimal("90000.00"), null));

        // eventJson() does NOT include commissionTier
        listener.onPaymentRefunded(eventJson("100000", SELLER_ID));

        SellerWallet after = wallets.findBySellerId(SELLER_ID).orElseThrow();
        // STANDARD 10% → sellerNet = 90,000 → debit 90,000 → balance 0
        assertThat(after.availableBalance()).isEqualByComparingTo("0.00");
    }

    private static String eventJson(String amount, String sellerId) {
        return String.format(
                "{\"provider\":\"PAYPAL\",\"orderId\":\"O-1\",\"returnId\":\"R-1\",\"sellerId\":\"%s\",\"refundId\":\"RF-1\",\"captureId\":\"C-1\",\"status\":\"COMPLETED\",\"amount\":\"%s\",\"currency\":\"VND\"}",
                sellerId, amount);
    }

    private static String eventJsonWithTier(String amount, String sellerId, String tier) {
        return String.format(
                "{\"provider\":\"PAYPAL\",\"orderId\":\"O-1\",\"returnId\":\"R-1\",\"sellerId\":\"%s\",\"refundId\":\"RF-1\",\"captureId\":\"C-1\",\"status\":\"COMPLETED\",\"amount\":\"%s\",\"currency\":\"VND\",\"commissionTier\":\"%s\"}",
                sellerId, amount, tier);
    }

    private static CommissionCalculator standardCalculator() {
        CommissionRateConfig rateConfig = new CommissionRateConfig();
        rateConfig.setTiers(Map.of(CommissionTier.STANDARD, new BigDecimal("0.10")));
        return new CommissionCalculator(rateConfig);
    }

    private static final class InMemoryWallets implements SellerWalletRepositoryPort {
        private final Map<String, SellerWallet> rows = new HashMap<>();
        int savedCount;

        @Override
        public Optional<SellerWallet> findBySellerId(String sellerId) {
            return Optional.ofNullable(rows.get(sellerId));
        }

        @Override
        public SellerWallet save(SellerWallet wallet) {
            savedCount++;
            rows.put(wallet.sellerId(), wallet);
            return wallet;
        }
    }
}
