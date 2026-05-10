package com.vnshop.userservice.infrastructure.web;

import com.vnshop.userservice.application.ManageAddressUseCase;
import com.vnshop.userservice.application.RegisterBuyerUseCase;
import com.vnshop.userservice.domain.Address;
import com.vnshop.userservice.domain.BuyerProfile;
import com.vnshop.userservice.domain.PhoneNumber;
import com.vnshop.userservice.domain.port.out.UserRepositoryPort;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/users")
public class UserController {
    private final UserRepositoryPort userRepositoryPort;
    private final RegisterBuyerUseCase registerBuyerUseCase;
    private final ManageAddressUseCase manageAddressUseCase;

    public UserController(UserRepositoryPort userRepositoryPort, RegisterBuyerUseCase registerBuyerUseCase, ManageAddressUseCase manageAddressUseCase) {
        this.userRepositoryPort = userRepositoryPort;
        this.registerBuyerUseCase = registerBuyerUseCase;
        this.manageAddressUseCase = manageAddressUseCase;
    }

    @GetMapping("/me")
    public BuyerProfileResponse getMyProfile() {
        return userRepositoryPort.findBuyerByKeycloakId(SecurityKeycloakId.current())
                .map(BuyerProfileResponse::fromDomain)
                .orElseThrow(() -> new IllegalArgumentException("buyer profile not found"));
    }

    @PutMapping("/me")
    public BuyerProfileResponse upsertMyProfile(@RequestBody BuyerProfileRequest request) {
        String keycloakId = SecurityKeycloakId.current();
        BuyerProfile buyerProfile = userRepositoryPort.findBuyerByKeycloakId(keycloakId)
                .map(existing -> {
                    existing.updateProfile(request.name(), new PhoneNumber(request.phone()), request.avatarUrl());
                    return userRepositoryPort.saveBuyer(existing);
                })
                .orElseGet(() -> registerBuyerUseCase.register(keycloakId, request.name(), request.phone(), request.avatarUrl()));
        return BuyerProfileResponse.fromDomain(buyerProfile);
    }

    @PostMapping("/me/addresses")
    public BuyerProfileResponse addAddress(@RequestBody AddressRequest request) {
        return BuyerProfileResponse.fromDomain(manageAddressUseCase.addAddress(SecurityKeycloakId.current(), request.toDomain()));
    }

    @DeleteMapping("/me/addresses/{index}")
    public BuyerProfileResponse removeAddress(@PathVariable int index) {
        return BuyerProfileResponse.fromDomain(manageAddressUseCase.removeAddress(SecurityKeycloakId.current(), index));
    }

    @PutMapping("/me/addresses/{index}/default")
    public BuyerProfileResponse setDefaultAddress(@PathVariable int index) {
        return BuyerProfileResponse.fromDomain(manageAddressUseCase.setDefaultAddress(SecurityKeycloakId.current(), index));
    }

    public record BuyerProfileRequest(String name, String phone, String avatarUrl) {
    }

    public record AddressRequest(String street, String ward, String district, String city, boolean isDefault) {
        Address toDomain() {
            return new Address(street, ward, district, city, isDefault);
        }
    }

    public record BuyerProfileResponse(String keycloakId, String name, String phone, String avatarUrl, List<AddressResponse> addresses) {
        static BuyerProfileResponse fromDomain(BuyerProfile buyerProfile) {
            return new BuyerProfileResponse(
                    buyerProfile.keycloakId(),
                    buyerProfile.name(),
                    buyerProfile.phone() == null ? null : buyerProfile.phone().value(),
                    buyerProfile.avatarUrl(),
                    buyerProfile.addresses().stream().map(AddressResponse::fromDomain).toList()
            );
        }
    }

    public record AddressResponse(String street, String ward, String district, String city, boolean isDefault) {
        static AddressResponse fromDomain(Address address) {
            return new AddressResponse(address.street(), address.ward(), address.district(), address.city(), address.isDefault());
        }
    }
}
