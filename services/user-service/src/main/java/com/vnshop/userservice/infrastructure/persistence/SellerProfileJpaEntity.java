package com.vnshop.userservice.infrastructure.persistence;

import com.vnshop.userservice.domain.Address;
import com.vnshop.userservice.domain.SellerProfile;
import com.vnshop.userservice.domain.Tier;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "seller_profiles", schema = "user_svc")
public class SellerProfileJpaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String keycloakId;

    @Column(nullable = false)
    private String shopName;

    @Column(nullable = false)
    private String bankName;

    @Column(nullable = false)
    private String bankAccount;

    private String pickupAddressStreet;
    private String pickupAddressWard;
    private String pickupAddressDistrict;
    private String pickupAddressCity;
    private boolean pickupAddressDefault;

    @Column(nullable = false)
    private boolean approved;

    @Column(nullable = false)
    private String tier;

    @Column(nullable = false)
    private boolean vacationMode;

    protected SellerProfileJpaEntity() {
    }

    public SellerProfileJpaEntity(String keycloakId, String shopName, String bankName, String bankAccount, boolean approved, String tier, boolean vacationMode) {
        this.keycloakId = keycloakId;
        this.shopName = shopName;
        this.bankName = bankName;
        this.bankAccount = bankAccount;
        this.approved = approved;
        this.tier = tier;
        this.vacationMode = vacationMode;
    }

    static SellerProfileJpaEntity fromDomain(SellerProfile sellerProfile) {
        SellerProfileJpaEntity entity = new SellerProfileJpaEntity(
                sellerProfile.id(),
                sellerProfile.shopName(),
                sellerProfile.bankName(),
                sellerProfile.bankAccount(),
                sellerProfile.approved(),
                sellerProfile.tier().name(),
                sellerProfile.vacationMode()
        );
        entity.setPickupAddress(sellerProfile.pickupAddress());
        return entity;
    }

    SellerProfile toDomain() {
        return new SellerProfile(
                keycloakId,
                shopName,
                bankName,
                bankAccount,
                pickupAddress(),
                approved,
                Tier.valueOf(tier),
                vacationMode
        );
    }

    public Long getId() {
        return id;
    }

    public String getKeycloakId() {
        return keycloakId;
    }

    void setId(Long id) {
        this.id = id;
    }

    private void setPickupAddress(Address pickupAddress) {
        if (pickupAddress == null) {
            return;
        }
        this.pickupAddressStreet = pickupAddress.street();
        this.pickupAddressWard = pickupAddress.ward();
        this.pickupAddressDistrict = pickupAddress.district();
        this.pickupAddressCity = pickupAddress.city();
        this.pickupAddressDefault = pickupAddress.isDefault();
    }

    private Address pickupAddress() {
        if (pickupAddressStreet == null || pickupAddressDistrict == null || pickupAddressCity == null) {
            return null;
        }
        return new Address(pickupAddressStreet, pickupAddressWard, pickupAddressDistrict, pickupAddressCity, pickupAddressDefault);
    }
}
