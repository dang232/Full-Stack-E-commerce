package com.vnshop.orderservice.application;

import com.vnshop.orderservice.domain.Return;
import com.vnshop.orderservice.domain.port.out.ReturnRepositoryPort;

import java.util.Objects;

public class ApproveReturnUseCase {
    private final ReturnRepositoryPort returnRepository;

    public ApproveReturnUseCase(ReturnRepositoryPort returnRepository) {
        this.returnRepository = Objects.requireNonNull(returnRepository, "returnRepository is required");
    }

    public Return approve(String returnId) {
        Return orderReturn = returnRepository.findById(returnId)
                .orElseThrow(() -> new IllegalArgumentException("return not found: " + returnId));
        orderReturn.approve();
        return returnRepository.save(orderReturn);
    }
}
