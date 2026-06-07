package com.vnshop.paymentservice.application.chargeback;

import java.util.UUID;

public class ChargebackNotFoundException extends RuntimeException {
    public ChargebackNotFoundException(UUID id) {
        super("Chargeback not found: " + id);
    }
}
