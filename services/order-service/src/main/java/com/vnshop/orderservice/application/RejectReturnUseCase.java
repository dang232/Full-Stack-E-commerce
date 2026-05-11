package com.vnshop.orderservice.application;

import com.vnshop.orderservice.domain.Return;
import com.vnshop.orderservice.domain.port.out.ReturnRepositoryPort;

import java.util.Objects;
import java.util.UUID;

public class RejectReturnUseCase {
    private final ReturnRepositoryPort returnRepository;

    public RejectReturnUseCase(ReturnRepositoryPort returnRepository) {
        this.returnRepository = Objects.requireNonNull(returnRepository, "returnRepository is required");
    }

    public Return reject(UUID returnId) {
        Return orderReturn = returnRepository.findById(returnId)
                .orElseThrow(() -> new IllegalArgumentException("return not found: " + returnId));
        orderReturn.reject();
        return returnRepository.save(orderReturn);
    }
}
