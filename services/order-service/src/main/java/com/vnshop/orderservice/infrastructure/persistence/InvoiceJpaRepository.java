package com.vnshop.orderservice.infrastructure.persistence;

import com.vnshop.orderservice.domain.Invoice;
import com.vnshop.orderservice.domain.port.out.InvoiceRepositoryPort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class InvoiceJpaRepository implements InvoiceRepositoryPort {
    private final InvoiceJpaSpringDataRepository springDataRepository;

    public InvoiceJpaRepository(InvoiceJpaSpringDataRepository springDataRepository) {
        this.springDataRepository = springDataRepository;
    }

    @Override
    public Invoice save(Invoice invoice) {
        return springDataRepository.save(InvoiceJpaEntity.fromDomain(invoice)).toDomain();
    }

    @Override
    public Optional<Invoice> findById(String invoiceId) {
        return springDataRepository.findById(invoiceId).map(InvoiceJpaEntity::toDomain);
    }

    @Override
    public Optional<Invoice> findBySubOrderId(Long subOrderId) {
        return springDataRepository.findBySubOrderId(subOrderId).map(InvoiceJpaEntity::toDomain);
    }
}

interface InvoiceJpaSpringDataRepository extends JpaRepository<InvoiceJpaEntity, String> {
    Optional<InvoiceJpaEntity> findBySubOrderId(Long subOrderId);
}
