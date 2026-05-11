package com.vnshop.orderservice.application;

import com.vnshop.orderservice.domain.Return;
import com.vnshop.orderservice.domain.port.out.ReturnRepositoryPort;

import java.util.Objects;
import java.util.UUID;

public class ApproveReturnUseCase {
    private final ReturnRepositoryPort returnRepository;

    public ApproveReturnUseCase(ReturnRepositoryPort returnRepository) {
        this.returnRepository = Objects.requireNonNull(returnRepository, "returnRepository is required");
    }

    public Return approve(UUID returnId) {
        Return orderReturn = returnRepository.findById(returnId)
                .orElseThrow(() -> new IllegalArgumentException("return not found: " + returnId));
        orderReturn.approve();
        return returnRepository.save(orderReturn);
    }
}
