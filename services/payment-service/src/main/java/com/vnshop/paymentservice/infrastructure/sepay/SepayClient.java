package com.vnshop.paymentservice.infrastructure.sepay;

/**
 * Outbound seam over SePay's REST API. Production wires
 * {@link RestSepayClient}; tests stub directly.
 */
public interface SepayClient {
    /**
     * @param sinceId the cursor — pass {@code null} on first poll to fetch the
     *                most-recent page.
     * @return the response (never {@code null}; an empty list when there are
     *         no transactions to process).
     */
    SepayTransactionsResponse listTransactions(String sinceId);
}
