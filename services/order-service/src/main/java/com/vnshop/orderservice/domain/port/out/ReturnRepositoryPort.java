package com.vnshop.orderservice.domain.port.out;

import com.vnshop.orderservice.domain.Return;

import java.util.List;
import java.util.Optional;

public interface ReturnRepositoryPort {
    Return save(Return orderReturn);

    Optional<Return> findById(String returnId);

    List<Return> findByBuyerId(String buyerId);
}
