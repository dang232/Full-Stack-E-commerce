package com.vnshop.paymentservice.infrastructure.sepay;

import java.util.Optional;

public interface SepayCursorRepository {
    Optional<String> readCursor();

    void writeCursor(String lastTxId);
}
