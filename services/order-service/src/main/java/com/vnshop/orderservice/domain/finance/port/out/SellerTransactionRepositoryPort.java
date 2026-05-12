package com.vnshop.orderservice.domain.finance.port.out;

import com.vnshop.orderservice.domain.finance.SellerTransaction;

public interface SellerTransactionRepositoryPort {
    boolean existsByIdempotencyKey(String idempotencyKey);

    SellerTransaction save(SellerTransaction transaction);
}
