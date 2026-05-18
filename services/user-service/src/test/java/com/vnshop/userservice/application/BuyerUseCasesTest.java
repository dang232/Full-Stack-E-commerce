package com.vnshop.userservice.application;

import com.vnshop.userservice.domain.BuyerProfile;
import com.vnshop.userservice.domain.PhoneNumber;
import com.vnshop.userservice.domain.port.out.UserRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BuyerUseCasesTest {

    @Mock
    private UserRepositoryPort userRepositoryPort;

    private static final PhoneNumber PHONE = new PhoneNumber("+84912345678");

    private BuyerProfile buyer(String id) {
        return new BuyerProfile(id, "Alice", PHONE, "avatar", null);
    }

    // --- ViewBuyerProfileUseCase ---

    @Test
    void viewBuyer_happyPath_returnsBuyer() {
        BuyerProfile b = buyer("kc-1");
        when(userRepositoryPort.findBuyerByKeycloakId("kc-1")).thenReturn(Optional.of(b));

        ViewBuyerProfileUseCase useCase = new ViewBuyerProfileUseCase(userRepositoryPort);
        BuyerProfile result = useCase.view("kc-1");

        assertThat(result.keycloakId()).isEqualTo("kc-1");
    }

    @Test
    void viewBuyer_notFound_throws() {
        when(userRepositoryPort.findBuyerByKeycloakId("kc-1")).thenReturn(Optional.empty());

        ViewBuyerProfileUseCase useCase = new ViewBuyerProfileUseCase(userRepositoryPort);
        assertThatThrownBy(() -> useCase.view("kc-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("buyer profile not found");
    }

    // --- UpsertBuyerProfileUseCase (existing buyer branch) ---

    @Test
    void upsert_existingBuyer_updatesAndSaves() {
        BuyerProfile existing = buyer("kc-1");
        when(userRepositoryPort.findBuyerByKeycloakId("kc-1")).thenReturn(Optional.of(existing));
        when(userRepositoryPort.saveBuyer(existing)).thenReturn(existing);

        RegisterBuyerUseCase registerUseCase = new RegisterBuyerUseCase(userRepositoryPort);
        UpsertBuyerProfileUseCase useCase = new UpsertBuyerProfileUseCase(userRepositoryPort, registerUseCase);

        UpsertBuyerProfileCommand cmd = new UpsertBuyerProfileCommand("kc-1", "Bob", "+84987654321", "new-avatar");
        BuyerProfile result = useCase.upsert(cmd);

        assertThat(result.keycloakId()).isEqualTo("kc-1");
        verify(userRepositoryPort).saveBuyer(existing);
    }

    // --- UpsertBuyerProfileUseCase (new buyer branch) ---

    @Test
    void upsert_newBuyer_registersViaRegisterUseCase() {
        BuyerProfile saved = buyer("kc-2");
        when(userRepositoryPort.findBuyerByKeycloakId("kc-2")).thenReturn(Optional.empty());
        when(userRepositoryPort.saveBuyer(any())).thenReturn(saved);

        RegisterBuyerUseCase registerUseCase = new RegisterBuyerUseCase(userRepositoryPort);
        UpsertBuyerProfileUseCase useCase = new UpsertBuyerProfileUseCase(userRepositoryPort, registerUseCase);

        UpsertBuyerProfileCommand cmd = new UpsertBuyerProfileCommand("kc-2", "Carol", "+84912345678", "avatar");
        BuyerProfile result = useCase.upsert(cmd);

        assertThat(result.keycloakId()).isEqualTo("kc-2");
        verify(userRepositoryPort).saveBuyer(any());
    }
}
