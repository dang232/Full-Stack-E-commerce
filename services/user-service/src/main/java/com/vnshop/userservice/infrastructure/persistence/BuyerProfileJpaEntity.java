package com.vnshop.userservice.infrastructure.persistence;

import com.vnshop.userservice.domain.BuyerProfile;
import com.vnshop.userservice.domain.PhoneNumber;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "buyer_profiles", schema = "user_svc")
public class BuyerProfileJpaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String keycloakId;

    private String name;

    private String phone;

    private String avatarUrl;

    @OneToMany(mappedBy = "buyerProfile", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AddressJpaEntity> addresses = new ArrayList<>();

    protected BuyerProfileJpaEntity() {
    }

    public BuyerProfileJpaEntity(String keycloakId, String name, String phone, String avatarUrl) {
        this.keycloakId = keycloakId;
        this.name = name;
        this.phone = phone;
        this.avatarUrl = avatarUrl;
    }

    static BuyerProfileJpaEntity fromDomain(BuyerProfile buyerProfile) {
        BuyerProfileJpaEntity entity = new BuyerProfileJpaEntity(
                buyerProfile.keycloakId(),
                buyerProfile.name(),
                buyerProfile.phone() == null ? null : buyerProfile.phone().value(),
                buyerProfile.avatarUrl()
        );
        buyerProfile.addresses().forEach(address -> entity.addAddress(AddressJpaEntity.fromDomain(address)));
        return entity;
    }

    BuyerProfile toDomain() {
        return new BuyerProfile(
                keycloakId,
                name,
                phone == null ? null : new PhoneNumber(phone),
                avatarUrl,
                addresses.stream().map(AddressJpaEntity::toDomain).toList()
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

    private void addAddress(AddressJpaEntity address) {
        address.setBuyerProfile(this);
        addresses.add(address);
    }
}
