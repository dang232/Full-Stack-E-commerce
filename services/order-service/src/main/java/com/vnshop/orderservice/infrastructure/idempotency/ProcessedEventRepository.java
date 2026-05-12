package com.vnshop.orderservice.infrastructure.idempotency;

import org.springframework.stereotype.Repository;

@Repository
public class ProcessedEventRepository {
    private final ProcessedEventSpringDataRepository springDataRepository;

    public ProcessedEventRepository(ProcessedEventSpringDataRepository springDataRepository) {
        this.springDataRepository = springDataRepository;
    }

    public ProcessedEvent save(ProcessedEvent event) {
        return springDataRepository.save(event);
    }
}
