package com.vnshop.userservice.infrastructure.persistence;

import com.vnshop.userservice.domain.Address;
import com.vnshop.userservice.infrastructure.persistence.BaseJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "addresses", schema = "user_svc")
@Getter
@Setter
public class AddressJpaEntity extends BaseJpaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String street;

    private String ward;

    @Column(nullable = false)
    private String district;

    @Column(nullable = false)
    private String city;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "buyer_profile_id", nullable = false)
    private BuyerProfileJpaEntity buyerProfile;

    protected AddressJpaEntity() {
    }

    public AddressJpaEntity(String street, String ward, String district, String city, boolean isDefault) {
        this.street = street;
        this.ward = ward;
        this.district = district;
        this.city = city;
        this.isDefault = isDefault;
    }

    static AddressJpaEntity fromDomain(Address address) {
        return new AddressJpaEntity(
                address.street(),
                address.ward(),
                address.district(),
                address.city(),
                address.isDefault()
        );
    }

    Address toDomain() {
        return new Address(street, ward, district, city, isDefault);
    }

}
