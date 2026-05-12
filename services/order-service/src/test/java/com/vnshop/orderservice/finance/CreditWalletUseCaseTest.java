package com.vnshop.orderservice.finance;

import com.vnshop.orderservice.application.finance.CreditWalletUseCase;
import com.vnshop.orderservice.domain.finance.CommissionCalculator;
import com.vnshop.orderservice.domain.finance.CommissionTier;
import com.vnshop.orderservice.domain.finance.SellerWallet;
import com.vnshop.orderservice.domain.finance.port.out.SellerTransactionRepositoryPort;
import com.vnshop.orderservice.domain.finance.port.out.SellerWalletRepositoryPort;
import com.vnshop.orderservice.infrastructure.config.finance.CommissionRateConfig;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class CreditWalletUseCaseTest {
    @Test
    void creditsSellerNetFromOriginalAmount() {
        FakeWalletRepository walletRepository = new FakeWalletRepository();
        FakeTransactionRepository transactionRepository = new FakeTransactionRepository();
        CommissionRateConfig rateConfig = new CommissionRateConfig();
        rateConfig.setTiers(Map.of(CommissionTier.STANDARD, new BigDecimal("0.10")));
        CreditWalletUseCase useCase = new CreditWalletUseCase(walletRepository, transactionRepository, new CommissionCalculator(rateConfig));

        CreditWalletUseCase.CreditWalletResult result = useCase.credit("seller-a", new BigDecimal("100.00"), CommissionTier.STANDARD, "order-1");

        assertThat(result.commission()).isEqualByComparingTo("10.00");
        assertThat(result.sellerNet()).isEqualByComparingTo("90.00");
        assertThat(result.wallet().availableBalance()).isEqualByComparingTo("90.00");
        assertThat(walletRepository.wallet.totalEarned()).isEqualByComparingTo("90.00");
        assertThat(transactionRepository.saved.amount()).isEqualByComparingTo("90.00");
        assertThat(transactionRepository.saved.feeAmount()).isEqualByComparingTo("10.00");
        assertThat(transactionRepository.saved.idempotencyKey()).isEqualTo("order-1");
    }

    private static final class FakeTransactionRepository implements SellerTransactionRepositoryPort {
        private com.vnshop.orderservice.domain.finance.SellerTransaction saved;

        @Override
        public boolean existsByIdempotencyKey(String idempotencyKey) {
            return false;
        }

        @Override
        public com.vnshop.orderservice.domain.finance.SellerTransaction save(com.vnshop.orderservice.domain.finance.SellerTransaction transaction) {
            this.saved = transaction;
            return transaction;
        }
    }

    private static final class FakeWalletRepository implements SellerWalletRepositoryPort {
        private SellerWallet wallet;

        @Override
        public Optional<SellerWallet> findBySellerId(String sellerId) {
            return Optional.ofNullable(wallet);
        }

        @Override
        public SellerWallet save(SellerWallet wallet) {
            this.wallet = wallet;
            return wallet;
        }
    }
}
