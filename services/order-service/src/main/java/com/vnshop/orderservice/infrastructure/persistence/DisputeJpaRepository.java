package com.vnshop.orderservice.infrastructure.persistence;

import com.vnshop.orderservice.domain.Dispute;
import com.vnshop.orderservice.domain.DisputeStatus;
import com.vnshop.orderservice.domain.port.out.DisputeRepositoryPort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class DisputeJpaRepository implements DisputeRepositoryPort {
    private final DisputeJpaSpringDataRepository springDataRepository;

    public DisputeJpaRepository(DisputeJpaSpringDataRepository springDataRepository) {
        this.springDataRepository = springDataRepository;
    }

    @Override
    public Dispute save(Dispute dispute) {
        return springDataRepository.save(DisputeJpaEntity.fromDomain(dispute)).toDomain();
    }

    @Override
    public Optional<Dispute> findById(String disputeId) {
        return springDataRepository.findById(disputeId).map(DisputeJpaEntity::toDomain);
    }

    @Override
    public List<Dispute> findByStatus(String status) {
        return springDataRepository.findByStatus(DisputeStatus.valueOf(status)).stream().map(DisputeJpaEntity::toDomain).toList();
    }
}

interface DisputeJpaSpringDataRepository extends JpaRepository<DisputeJpaEntity, String> {
    List<DisputeJpaEntity> findByStatus(DisputeStatus status);
}
