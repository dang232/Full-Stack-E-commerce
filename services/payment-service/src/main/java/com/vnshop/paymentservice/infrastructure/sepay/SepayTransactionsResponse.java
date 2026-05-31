package com.vnshop.paymentservice.infrastructure.sepay;

import java.util.List;

/**
 * Minimal model of the {@code /transactions/list} response. SePay's actual
 * payload carries more fields (gateway, account number, balance) but the
 * poller only needs the id, the credit amount, the memo, and a way to detect
 * end-of-page.
 */
public record SepayTransactionsResponse(
        int status,
        String error,
        List<SepayTransaction> transactions) {

    public record SepayTransaction(
            String id,
            String amount_in,
            String transaction_content) {
    }
}
