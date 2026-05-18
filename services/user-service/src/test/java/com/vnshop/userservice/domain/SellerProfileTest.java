package com.vnshop.userservice.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SellerProfileTest {

    @Test
    void eightArgConstructor_defaultsNewFieldsToNull() {
        SellerProfile p = new SellerProfile("id", "Shop", "Bank", "ACC", null, true, Tier.STANDARD, false);
        assertThat(p.description()).isNull();
        assertThat(p.logoUrl()).isNull();
        assertThat(p.bannerUrl()).isNull();
        assertThat(p.createdAt()).isNull();
    }

    @Test
    void fullConstructor_surfacesAllFields() {
        Instant now = Instant.now();
        SellerProfile p = new SellerProfile(
                "id", "Shop", "Bank", "ACC", null, true, Tier.VERIFIED, false,
                "desc", "http://logo", "http://banner", now
        );
        assertThat(p.description()).isEqualTo("desc");
        assertThat(p.logoUrl()).isEqualTo("http://logo");
        assertThat(p.bannerUrl()).isEqualTo("http://banner");
        assertThat(p.createdAt()).isEqualTo(now);
        assertThat(p.tier()).isEqualTo(Tier.VERIFIED);
    }

    @Test
    void nullTier_defaultsToStandard() {
        SellerProfile p = new SellerProfile("id", "Shop", "Bank", "ACC", null, false, null, false);
        assertThat(p.tier()).isEqualTo(Tier.STANDARD);
    }

    @Test
    void blankShopName_throwsIllegalArgument() {
        assertThatThrownBy(() -> new SellerProfile("id", " ", "Bank", "ACC", null, false, Tier.STANDARD, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("shopName");
    }

    @Test
    void updateShop_changesShopName() {
        SellerProfile p = new SellerProfile("id", "Shop", "Bank", "ACC", null, false, Tier.STANDARD, false);
        p.updateShop("New Shop", null);
        assertThat(p.shopName()).isEqualTo("New Shop");
    }

    @Test
    void approve_setsApprovedTrue() {
        SellerProfile p = new SellerProfile("id", "Shop", "Bank", "ACC", null, false, Tier.STANDARD, false);
        p.approve();
        assertThat(p.approved()).isTrue();
    }

    @Test
    void changeTier_updatesTier() {
        SellerProfile p = new SellerProfile("id", "Shop", "Bank", "ACC", null, false, Tier.STANDARD, false);
        p.changeTier(Tier.PREFERRED);
        assertThat(p.tier()).isEqualTo(Tier.PREFERRED);
    }

    @Test
    void setVacationMode_updatesFlag() {
        SellerProfile p = new SellerProfile("id", "Shop", "Bank", "ACC", null, false, Tier.STANDARD, false);
        p.setVacationMode(true);
        assertThat(p.vacationMode()).isTrue();
    }

    @Test
    void blankBankName_throwsIllegalArgument() {
        assertThatThrownBy(() -> new SellerProfile("id", "Shop", " ", "ACC", null, false, Tier.STANDARD, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bankName");
    }

    @Test
    void blankBankAccount_throwsIllegalArgument() {
        assertThatThrownBy(() -> new SellerProfile("id", "Shop", "Bank", "", null, false, Tier.STANDARD, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bankAccount");
    }

    @Test
    void updateBankDetails_blankBankName_throws() {
        SellerProfile p = new SellerProfile("id", "Shop", "Bank", "ACC", null, false, Tier.STANDARD, false);
        assertThatThrownBy(() -> p.updateBankDetails(" ", "ACC"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bankName");
    }

    @Test
    void updateBankDetails_blankBankAccount_throws() {
        SellerProfile p = new SellerProfile("id", "Shop", "Bank", "ACC", null, false, Tier.STANDARD, false);
        assertThatThrownBy(() -> p.updateBankDetails("Bank", ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bankAccount");
    }

    @Test
    void updateShop_blankShopName_throws() {
        SellerProfile p = new SellerProfile("id", "Shop", "Bank", "ACC", null, false, Tier.STANDARD, false);
        assertThatThrownBy(() -> p.updateShop("  ", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("shopName");
    }

    @Test
    void changeTier_null_throwsNullPointer() {
        SellerProfile p = new SellerProfile("id", "Shop", "Bank", "ACC", null, false, Tier.STANDARD, false);
        assertThatThrownBy(() -> p.changeTier(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void allTierValues_roundTrip() {
        for (Tier t : Tier.values()) {
            SellerProfile p = new SellerProfile("id", "Shop", "Bank", "ACC", null, false, t, false);
            assertThat(p.tier()).isEqualTo(t);
        }
    }

    @Test
    void sellerNotFoundException_containsSellerId() {
        SellerNotFoundException ex = new SellerNotFoundException("seller-xyz");
        assertThat(ex.getMessage()).contains("seller-xyz");
    }
}
