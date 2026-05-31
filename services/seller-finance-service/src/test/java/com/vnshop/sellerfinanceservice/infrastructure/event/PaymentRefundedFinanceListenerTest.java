package com.vnshop.sellerfinanceservice.infrastructure.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vnshop.sellerfinanceservice.application.RefundWalletUseCase;
import com.vnshop.sellerfinanceservice.domain.CommissionCalculator;
import com.vnshop.sellerfinanceservice.domain.CommissionTier;
import com.vnshop.sellerfinanceservice.domain.SellerWallet;
import com.vnshop.sellerfinanceservice.domain.port.out.SellerWalletRepositoryPort;
import com.vnshop.sellerfinanceservice.infrastructure.config.CommissionRateConfig;
import com.vnshop.sellerfinanceservice.infrastructure.persistence.ProcessedRefund;
import com.vnshop.sellerfinanceservice.infrastructure.persistence.ProcessedRefundRepository;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.repository.query.FluentQuery;
import java.util.List;
import java.util.function.Function;

class PaymentRefundedFinanceListenerTest {

    private static final String SELLER_ID = "SELLER-7";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final InMemoryWallets wallets = new InMemoryWallets();
    private final InMemoryProcessedRefunds processedRefunds = new InMemoryProcessedRefunds();
    private final CommissionCalculator calculator = standardCalculator();
    private final RefundWalletUseCase useCase = new RefundWalletUseCase(wallets, calculator);
    private final PaymentRefundedFinanceListener listener =
            new PaymentRefundedFinanceListener(useCase, processedRefunds, objectMapper);

    @Test
    void debitsSellerNetWhenRefundEventArrives() {
        SellerWallet seeded = new SellerWallet(SELLER_ID,
                new BigDecimal("90000.00"), BigDecimal.ZERO, new BigDecimal("90000.00"), null);
        wallets.save(seeded);

        listener.onPaymentRefunded(eventJson("100000", SELLER_ID));

        SellerWallet after = wallets.findBySellerId(SELLER_ID).orElseThrow();
        assertThat(after.availableBalance()).isEqualByComparingTo("0.00");
        assertThat(after.totalEarned()).isEqualByComparingTo("90000.00");
        // Processed refund record is saved for idempotency.
        assertThat(processedRefunds.existsById("RF-1")).isTrue();
    }

    @Test
    void clampsToZeroWhenSellerAlreadyPaidOut() {
        wallets.save(new SellerWallet(SELLER_ID,
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("90000.00"), null));

        listener.onPaymentRefunded(eventJson("100000", SELLER_ID));

        assertThat(wallets.findBySellerId(SELLER_ID).orElseThrow().availableBalance())
                .isEqualByComparingTo("0.00");
    }

    @Test
    void skipsWhenWalletMissing() {
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
    void skipsBlankRefundId() {
        wallets.save(new SellerWallet(SELLER_ID,
                new BigDecimal("90000.00"), BigDecimal.ZERO, new BigDecimal("90000.00"), null));

        String noRefundId = String.format(
                "{\"provider\":\"PAYPAL\",\"orderId\":\"O-1\",\"returnId\":\"R-1\",\"sellerId\":\"%s\",\"refundId\":\"\",\"captureId\":\"C-1\",\"status\":\"COMPLETED\",\"amount\":\"100000\",\"currency\":\"VND\"}",
                SELLER_ID);
        listener.onPaymentRefunded(noRefundId);

        // Wallet untouched — refundId is required for idempotency.
        assertThat(wallets.findBySellerId(SELLER_ID).orElseThrow().availableBalance())
                .isEqualByComparingTo("90000.00");
    }

    @Test
    void skipsRedeliveryWhenRefundIdAlreadyProcessed() {
        wallets.save(new SellerWallet(SELLER_ID,
                new BigDecimal("90000.00"), BigDecimal.ZERO, new BigDecimal("90000.00"), null));

        // First delivery — debits.
        listener.onPaymentRefunded(eventJson("100000", SELLER_ID));
        assertThat(wallets.findBySellerId(SELLER_ID).orElseThrow().availableBalance())
                .isEqualByComparingTo("0.00");

        // Re-seed wallet to prove the second delivery is truly skipped.
        wallets.save(new SellerWallet(SELLER_ID,
                new BigDecimal("90000.00"), BigDecimal.ZERO, new BigDecimal("90000.00"), null));

        // Redelivery — same refundId "RF-1" → no-op.
        listener.onPaymentRefunded(eventJson("100000", SELLER_ID));
        assertThat(wallets.findBySellerId(SELLER_ID).orElseThrow().availableBalance())
                .isEqualByComparingTo("90000.00");
    }

    @Test
    void usesCommissionTierFromEventWhenPresent() {
        CommissionRateConfig rateConfig = new CommissionRateConfig();
        rateConfig.setTiers(Map.of(
                CommissionTier.STANDARD, new BigDecimal("0.10"),
                CommissionTier.VERIFIED, new BigDecimal("0.08")));
        CommissionCalculator calc = new CommissionCalculator(rateConfig);
        RefundWalletUseCase tieredUseCase = new RefundWalletUseCase(wallets, calc);
        InMemoryProcessedRefunds tieredRefunds = new InMemoryProcessedRefunds();
        PaymentRefundedFinanceListener tieredListener =
                new PaymentRefundedFinanceListener(tieredUseCase, tieredRefunds, objectMapper);

        wallets.save(new SellerWallet(SELLER_ID,
                new BigDecimal("92000.00"), BigDecimal.ZERO, new BigDecimal("92000.00"), null));

        tieredListener.onPaymentRefunded(eventJsonWithTier("100000", SELLER_ID, "VERIFIED"));

        SellerWallet after = wallets.findBySellerId(SELLER_ID).orElseThrow();
        assertThat(after.availableBalance()).isEqualByComparingTo("0.00");
    }

    @Test
    void defaultsToStandardWhenCommissionTierMissing() {
        wallets.save(new SellerWallet(SELLER_ID,
                new BigDecimal("90000.00"), BigDecimal.ZERO, new BigDecimal("90000.00"), null));

        listener.onPaymentRefunded(eventJson("100000", SELLER_ID));

        SellerWallet after = wallets.findBySellerId(SELLER_ID).orElseThrow();
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

    /**
     * Minimal in-memory implementation of ProcessedRefundRepository for unit tests.
     * Only the methods used by the listener are implemented; the rest throw.
     */
    private static final class InMemoryProcessedRefunds implements ProcessedRefundRepository {
        private final Map<String, ProcessedRefund> rows = new HashMap<>();

        @Override
        public boolean existsById(String id) {
            return rows.containsKey(id);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <S extends ProcessedRefund> S save(S entity) {
            rows.put(entity.refundId(), entity);
            return entity;
        }

        // --- Unused JpaRepository methods — throw UnsupportedOperationException ---

        @Override public <S extends ProcessedRefund> List<S> saveAll(Iterable<S> entities) { throw new UnsupportedOperationException(); }
        @Override public Optional<ProcessedRefund> findById(String s) { return Optional.ofNullable(rows.get(s)); }
        @Override public List<ProcessedRefund> findAll() { throw new UnsupportedOperationException(); }
        @Override public List<ProcessedRefund> findAll(Sort sort) { throw new UnsupportedOperationException(); }
        @Override public Page<ProcessedRefund> findAll(Pageable pageable) { throw new UnsupportedOperationException(); }
        @Override public List<ProcessedRefund> findAllById(Iterable<String> strings) { throw new UnsupportedOperationException(); }
        @Override public long count() { return rows.size(); }
        @Override public void deleteById(String s) { throw new UnsupportedOperationException(); }
        @Override public void delete(ProcessedRefund entity) { throw new UnsupportedOperationException(); }
        @Override public void deleteAllById(Iterable<? extends String> strings) { throw new UnsupportedOperationException(); }
        @Override public void deleteAll(Iterable<? extends ProcessedRefund> entities) { throw new UnsupportedOperationException(); }
        @Override public void deleteAll() { throw new UnsupportedOperationException(); }
        @Override public void flush() { }
        @Override public <S extends ProcessedRefund> S saveAndFlush(S entity) { return save(entity); }
        @Override public <S extends ProcessedRefund> List<S> saveAllAndFlush(Iterable<S> entities) { throw new UnsupportedOperationException(); }
        @Override public void deleteAllInBatch(Iterable<ProcessedRefund> entities) { throw new UnsupportedOperationException(); }
        @Override public void deleteAllByIdInBatch(Iterable<String> strings) { throw new UnsupportedOperationException(); }
        @Override public void deleteAllInBatch() { throw new UnsupportedOperationException(); }
        @Override public ProcessedRefund getOne(String s) { throw new UnsupportedOperationException(); }
        @Override public ProcessedRefund getById(String s) { throw new UnsupportedOperationException(); }
        @Override public ProcessedRefund getReferenceById(String s) { throw new UnsupportedOperationException(); }
        @Override public <S extends ProcessedRefund> Optional<S> findOne(Example<S> example) { throw new UnsupportedOperationException(); }
        @Override public <S extends ProcessedRefund> List<S> findAll(Example<S> example) { throw new UnsupportedOperationException(); }
        @Override public <S extends ProcessedRefund> List<S> findAll(Example<S> example, Sort sort) { throw new UnsupportedOperationException(); }
        @Override public <S extends ProcessedRefund> Page<S> findAll(Example<S> example, Pageable pageable) { throw new UnsupportedOperationException(); }
        @Override public <S extends ProcessedRefund> long count(Example<S> example) { throw new UnsupportedOperationException(); }
        @Override public <S extends ProcessedRefund> boolean exists(Example<S> example) { throw new UnsupportedOperationException(); }
        @Override public <S extends ProcessedRefund, R> R findBy(Example<S> example, Function<FluentQuery.FetchableFluentQuery<S>, R> queryFunction) { throw new UnsupportedOperationException(); }
    }
}
