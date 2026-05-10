package com.vnshop.paymentservice.infrastructure.gateway;

public interface MomoClient {
    MomoCreateResponse create(MomoCreateRequest request);

    MomoQueryDrResponse query(MomoQueryDrRequest request);
}
