package com.vnshop.paymentservice.domain.port.out;

import com.vnshop.paymentservice.domain.LedgerEntry;

import java.util.List;

public interface LedgerRepositoryPort {
    LedgerEntry save(LedgerEntry ledgerEntry);

    List<LedgerEntry> findByOrderId(String orderId);
}
