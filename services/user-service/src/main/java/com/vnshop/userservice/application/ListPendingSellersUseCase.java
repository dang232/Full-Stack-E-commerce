package com.vnshop.userservice.application;

import com.vnshop.userservice.domain.SellerProfile;
import com.vnshop.userservice.domain.port.out.UserRepositoryPort;

import java.util.List;

public class ListPendingSellersUseCase {

    private final UserRepositoryPort userRepositoryPort;

    public ListPendingSellersUseCase(UserRepositoryPort userRepositoryPort) {
        this.userRepositoryPort = userRepositoryPort;
    }

    public List<SellerProfile> listPending() {
        return userRepositoryPort.findPendingSellers();
    }
}
