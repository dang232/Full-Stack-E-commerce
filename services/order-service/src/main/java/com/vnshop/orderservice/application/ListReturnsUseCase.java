package com.vnshop.orderservice.application;

import com.vnshop.orderservice.domain.Return;
import com.vnshop.orderservice.domain.port.out.ReturnRepositoryPort;

import java.util.List;

public class ListReturnsUseCase {

    private final ReturnRepositoryPort returnRepositoryPort;

    public ListReturnsUseCase(ReturnRepositoryPort returnRepositoryPort) {
        this.returnRepositoryPort = returnRepositoryPort;
    }

    public List<Return> listByBuyerId(String buyerId) {
        return returnRepositoryPort.findByBuyerId(buyerId);
    }
}
