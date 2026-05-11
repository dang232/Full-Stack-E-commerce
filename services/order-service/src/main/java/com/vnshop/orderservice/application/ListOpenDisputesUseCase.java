package com.vnshop.orderservice.application;

import com.vnshop.orderservice.domain.Dispute;
import com.vnshop.orderservice.domain.DisputeStatus;
import com.vnshop.orderservice.domain.port.out.DisputeRepositoryPort;

import java.util.List;

public class ListOpenDisputesUseCase {

    private final DisputeRepositoryPort disputeRepositoryPort;

    public ListOpenDisputesUseCase(DisputeRepositoryPort disputeRepositoryPort) {
        this.disputeRepositoryPort = disputeRepositoryPort;
    }

    public List<Dispute> listOpen() {
        return disputeRepositoryPort.findByStatus(DisputeStatus.OPEN.name());
    }
}
