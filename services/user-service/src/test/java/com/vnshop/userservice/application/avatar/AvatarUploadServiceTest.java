package com.vnshop.userservice.application.avatar;

import com.vnshop.userservice.application.RegisterBuyerCommand;
import com.vnshop.userservice.application.RegisterBuyerUseCase;
import com.vnshop.userservice.domain.BuyerProfile;
import com.vnshop.userservice.domain.PhoneNumber;
import com.vnshop.userservice.domain.port.out.ObjectStoragePort;
import com.vnshop.userservice.domain.port.out.UserRepositoryPort;
import com.vnshop.userservice.domain.storage.AvatarObjectMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AvatarUploadServiceTest {

    @Mock
    private UserRepositoryPort userRepositoryPort;
    @Mock
    private ObjectStoragePort objectStoragePort;
    @Mock
    private RegisterBuyerUseCase registerBuyerUseCase;

    private AvatarUploadService service;

    private static final String KEYCLOAK_ID = "user-123";
    private static final String VALID_SHA = "a".repeat(64);

    @BeforeEach
    void setUp() {
        service = new AvatarUploadService(userRepositoryPort, objectStoragePort, registerBuyerUseCase);
    }

    @Test
    void createUpload_signsPresignedUrlAndReturnsObjectKey() {
        when(objectStoragePort.getSignedUploadUrl(any(), any(), eq(Duration.ofMinutes(5))))
                .thenReturn(URI.create("http://minio:9000/vnshop-avatars/sig"));

        AvatarUploadResponse response = service.createUpload(KEYCLOAK_ID, new AvatarUploadRequest(
                "selfie.jpg", "image/jpeg", 100_000L, VALID_SHA));

        assertThat(response.objectKey()).startsWith("avatars/" + KEYCLOAK_ID + "/");
        assertThat(response.objectKey()).endsWith(".jpg");
        assertThat(response.uploadUrl().toString()).isEqualTo("http://minio:9000/vnshop-avatars/sig");
        assertThat(response.expiresInSeconds()).isEqualTo(300L);
    }

    @Test
    void createUpload_rejectsOversizeFile() {
        assertThatThrownBy(() -> service.createUpload(KEYCLOAK_ID, new AvatarUploadRequest(
                "selfie.jpg", "image/jpeg", 3L * 1024 * 1024, VALID_SHA)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("2 MB");
        verify(objectStoragePort, never()).getSignedUploadUrl(any(), any(), any());
    }

    @Test
    void createUpload_rejectsUnsupportedExtension() {
        assertThatThrownBy(() -> service.createUpload(KEYCLOAK_ID, new AvatarUploadRequest(
                "selfie.gif", "image/gif", 100L, VALID_SHA)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("file extension");
    }

    @Test
    void createUpload_rejectsUnsupportedContentType() {
        assertThatThrownBy(() -> service.createUpload(KEYCLOAK_ID, new AvatarUploadRequest(
                "selfie.jpg", "application/pdf", 100L, VALID_SHA)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("content type");
    }

    @Test
    void createUpload_rejectsMalformedSha256() {
        assertThatThrownBy(() -> service.createUpload(KEYCLOAK_ID, new AvatarUploadRequest(
                "selfie.jpg", "image/jpeg", 100L, "not-a-real-checksum")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sha256Hex");
    }

    @Test
    void activate_writesProfileAndReturnsAvatarUrl() {
        String key = "avatars/" + KEYCLOAK_ID + "/123-abc.jpg";
        BuyerProfile existing = new BuyerProfile(KEYCLOAK_ID, "Buyer", new PhoneNumber("+84912345678"), null, List.of());
        when(objectStoragePort.headObject(key)).thenReturn(Optional.of(
                new AvatarObjectMetadata(key, "image/jpeg", 100_000L, VALID_SHA)));
        when(objectStoragePort.publicUrl(key))
                .thenReturn(URI.create("http://localhost:9000/vnshop-avatars/" + key));
        when(userRepositoryPort.findBuyerByKeycloakId(KEYCLOAK_ID)).thenReturn(Optional.of(existing));
        when(userRepositoryPort.saveBuyer(any())).thenAnswer(inv -> inv.getArgument(0));

        AvatarActivationResponse response = service.activate(KEYCLOAK_ID,
                new AvatarActivationRequest(key, 100_000L, VALID_SHA));

        assertThat(response.avatarUrl()).contains("/vnshop-avatars/avatars/" + KEYCLOAK_ID);
        ArgumentCaptor<BuyerProfile> captor = ArgumentCaptor.forClass(BuyerProfile.class);
        verify(userRepositoryPort).saveBuyer(captor.capture());
        assertThat(captor.getValue().avatarUrl()).contains("/vnshop-avatars/avatars/" + KEYCLOAK_ID);
        verify(objectStoragePort, never()).deleteObject(any());
    }

    @Test
    void activate_deletesPriorObjectWhenAvatarReplaced() {
        String oldKey = "avatars/" + KEYCLOAK_ID + "/100-old.jpg";
        String newKey = "avatars/" + KEYCLOAK_ID + "/200-new.jpg";
        BuyerProfile existing = new BuyerProfile(
                KEYCLOAK_ID, "Buyer", new PhoneNumber("+84912345678"),
                "http://localhost:9000/vnshop-avatars/" + oldKey,
                List.of());
        when(objectStoragePort.headObject(newKey)).thenReturn(Optional.of(
                new AvatarObjectMetadata(newKey, "image/jpeg", 100L, VALID_SHA)));
        when(objectStoragePort.publicUrl(newKey))
                .thenReturn(URI.create("http://localhost:9000/vnshop-avatars/" + newKey));
        when(userRepositoryPort.findBuyerByKeycloakId(KEYCLOAK_ID)).thenReturn(Optional.of(existing));
        when(userRepositoryPort.saveBuyer(any())).thenAnswer(inv -> inv.getArgument(0));

        service.activate(KEYCLOAK_ID, new AvatarActivationRequest(newKey, 100L, VALID_SHA));

        verify(objectStoragePort).deleteObject(oldKey);
    }

    @Test
    void activate_rejectsKeyOwnedByAnotherUser() {
        assertThatThrownBy(() -> service.activate(KEYCLOAK_ID,
                new AvatarActivationRequest("avatars/other-user/123.jpg", 100L, VALID_SHA)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not belong");
        verify(objectStoragePort, never()).headObject(any());
    }

    @Test
    void activate_rejectsWhenObjectDidNotLand() {
        String key = "avatars/" + KEYCLOAK_ID + "/123.jpg";
        when(objectStoragePort.headObject(key)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.activate(KEYCLOAK_ID,
                new AvatarActivationRequest(key, 100L, VALID_SHA)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("never landed");
        verify(userRepositoryPort, never()).saveBuyer(any());
    }

    @Test
    void activate_rejectsContentLengthMismatch() {
        String key = "avatars/" + KEYCLOAK_ID + "/123.jpg";
        when(objectStoragePort.headObject(key)).thenReturn(Optional.of(
                new AvatarObjectMetadata(key, "image/jpeg", 100L, VALID_SHA)));

        assertThatThrownBy(() -> service.activate(KEYCLOAK_ID,
                new AvatarActivationRequest(key, 999L, VALID_SHA)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("contentLength");
        verify(userRepositoryPort, never()).saveBuyer(any());
    }

    @Test
    void activate_rejectsSha256Mismatch() {
        String key = "avatars/" + KEYCLOAK_ID + "/123.jpg";
        when(objectStoragePort.headObject(key)).thenReturn(Optional.of(
                new AvatarObjectMetadata(key, "image/jpeg", 100L, VALID_SHA)));

        assertThatThrownBy(() -> service.activate(KEYCLOAK_ID,
                new AvatarActivationRequest(key, 100L, "b".repeat(64))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sha256");
        verify(userRepositoryPort, never()).saveBuyer(any());
    }

    @Test
    void activate_registersBuyerWhenProfileMissing() {
        String key = "avatars/" + KEYCLOAK_ID + "/123.jpg";
        when(objectStoragePort.headObject(key)).thenReturn(Optional.of(
                new AvatarObjectMetadata(key, "image/jpeg", 100L, VALID_SHA)));
        when(objectStoragePort.publicUrl(key))
                .thenReturn(URI.create("http://localhost:9000/vnshop-avatars/" + key));
        when(userRepositoryPort.findBuyerByKeycloakId(KEYCLOAK_ID)).thenReturn(Optional.empty());
        BuyerProfile fresh = new BuyerProfile(
                KEYCLOAK_ID, null, null, "http://localhost:9000/vnshop-avatars/" + key, List.of());
        when(registerBuyerUseCase.register(any())).thenReturn(fresh);

        AvatarActivationResponse response = service.activate(KEYCLOAK_ID,
                new AvatarActivationRequest(key, 100L, VALID_SHA));

        ArgumentCaptor<RegisterBuyerCommand> captor = ArgumentCaptor.forClass(RegisterBuyerCommand.class);
        verify(registerBuyerUseCase).register(captor.capture());
        assertThat(captor.getValue().keycloakId()).isEqualTo(KEYCLOAK_ID);
        assertThat(captor.getValue().avatarUrl()).contains("/vnshop-avatars/");
        assertThat(response.avatarUrl()).contains("/vnshop-avatars/");
    }

    @Test
    void activate_acceptsMissingStorageChecksumWhenSizeMatches() {
        // MinIO can strip non-amz user-metadata on HEAD; size already proves
        // structural integrity. activate must not fail on a missing
        // storage-side sha.
        String key = "avatars/" + KEYCLOAK_ID + "/123.jpg";
        when(objectStoragePort.headObject(key)).thenReturn(Optional.of(
                new AvatarObjectMetadata(key, "image/jpeg", 100L, null)));
        when(objectStoragePort.publicUrl(key))
                .thenReturn(URI.create("http://localhost:9000/vnshop-avatars/" + key));
        when(userRepositoryPort.findBuyerByKeycloakId(KEYCLOAK_ID))
                .thenReturn(Optional.of(new BuyerProfile(
                        KEYCLOAK_ID, "Buyer", new PhoneNumber("+84912345678"), null, List.of())));
        when(userRepositoryPort.saveBuyer(any())).thenAnswer(inv -> inv.getArgument(0));

        AvatarActivationResponse response = service.activate(KEYCLOAK_ID,
                new AvatarActivationRequest(key, 100L, VALID_SHA));

        assertThat(response.avatarUrl()).contains("/vnshop-avatars/");
    }

    @Test
    void activate_doesNotDeleteWhenPreviousAvatarIsExternalUrl() {
        // A buyer whose avatarUrl came from somewhere else (legacy gravatar,
        // OAuth provider) has no /avatars/ prefix in the URL — we don't own
        // that object and must not call deleteObject on a derived "key" that
        // happens to come out of substring math.
        String newKey = "avatars/" + KEYCLOAK_ID + "/200.jpg";
        BuyerProfile existing = new BuyerProfile(
                KEYCLOAK_ID, "Buyer", new PhoneNumber("+84912345678"),
                "https://gravatar.com/avatar/legacy.jpg",
                List.of());
        when(objectStoragePort.headObject(newKey)).thenReturn(Optional.of(
                new AvatarObjectMetadata(newKey, "image/jpeg", 100L, VALID_SHA)));
        when(objectStoragePort.publicUrl(newKey))
                .thenReturn(URI.create("http://localhost:9000/vnshop-avatars/" + newKey));
        when(userRepositoryPort.findBuyerByKeycloakId(KEYCLOAK_ID)).thenReturn(Optional.of(existing));
        when(userRepositoryPort.saveBuyer(any())).thenAnswer(inv -> inv.getArgument(0));

        service.activate(KEYCLOAK_ID, new AvatarActivationRequest(newKey, 100L, VALID_SHA));

        verify(objectStoragePort, never()).deleteObject(any());
    }

    @Test
    void activate_swallowsDeleteFailureRatherThanRollingBackProfileWrite() {
        // If the new URL is committed and the prior-object delete throws,
        // an orphan in storage is the right tradeoff — the profile is
        // correct from the BE's point of view.
        String oldKey = "avatars/" + KEYCLOAK_ID + "/100-old.jpg";
        String newKey = "avatars/" + KEYCLOAK_ID + "/200-new.jpg";
        BuyerProfile existing = new BuyerProfile(
                KEYCLOAK_ID, "Buyer", new PhoneNumber("+84912345678"),
                "http://localhost:9000/vnshop-avatars/" + oldKey,
                List.of());
        when(objectStoragePort.headObject(newKey)).thenReturn(Optional.of(
                new AvatarObjectMetadata(newKey, "image/jpeg", 100L, VALID_SHA)));
        when(objectStoragePort.publicUrl(newKey))
                .thenReturn(URI.create("http://localhost:9000/vnshop-avatars/" + newKey));
        when(userRepositoryPort.findBuyerByKeycloakId(KEYCLOAK_ID)).thenReturn(Optional.of(existing));
        when(userRepositoryPort.saveBuyer(any())).thenAnswer(inv -> inv.getArgument(0));
        org.mockito.Mockito.doThrow(new RuntimeException("storage hiccup"))
                .when(objectStoragePort).deleteObject(oldKey);

        AvatarActivationResponse response = service.activate(KEYCLOAK_ID,
                new AvatarActivationRequest(newKey, 100L, VALID_SHA));

        assertThat(response.avatarUrl()).contains(newKey);
    }

    @Test
    void createUpload_rejectsBlankFilename() {
        assertThatThrownBy(() -> service.createUpload(KEYCLOAK_ID, new AvatarUploadRequest(
                "", "image/jpeg", 100L, VALID_SHA)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("filename");
    }

    @Test
    void createUpload_rejectsNullRequest() {
        assertThatThrownBy(() -> service.createUpload(KEYCLOAK_ID, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createUpload_rejectsZeroByteFile() {
        assertThatThrownBy(() -> service.createUpload(KEYCLOAK_ID, new AvatarUploadRequest(
                "selfie.jpg", "image/jpeg", 0L, VALID_SHA)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("2 MB");
    }

    @Test
    void createUpload_rejectsNullSha256() {
        assertThatThrownBy(() -> service.createUpload(KEYCLOAK_ID, new AvatarUploadRequest(
                "selfie.jpg", "image/jpeg", 100L, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sha256Hex");
    }

    @Test
    void activate_doesNotDeleteWhenPreviousAvatarUrlIsBlank() {
        String key = "avatars/" + KEYCLOAK_ID + "/123.jpg";
        BuyerProfile existing = new BuyerProfile(
                KEYCLOAK_ID, "Buyer", new PhoneNumber("+84912345678"), "", List.of());
        when(objectStoragePort.headObject(key)).thenReturn(Optional.of(
                new AvatarObjectMetadata(key, "image/jpeg", 100L, VALID_SHA)));
        when(objectStoragePort.publicUrl(key))
                .thenReturn(URI.create("http://localhost:9000/vnshop-avatars/" + key));
        when(userRepositoryPort.findBuyerByKeycloakId(KEYCLOAK_ID)).thenReturn(Optional.of(existing));
        when(userRepositoryPort.saveBuyer(any())).thenAnswer(inv -> inv.getArgument(0));

        service.activate(KEYCLOAK_ID, new AvatarActivationRequest(key, 100L, VALID_SHA));

        verify(objectStoragePort, never()).deleteObject(any());
    }

    @Test
    void activate_doesNotDeleteWhenSameKeyReuploaded() {
        // If the same key activates twice (network retry, idempotent client),
        // we must not delete the object we just committed to.
        String key = "avatars/" + KEYCLOAK_ID + "/200-same.jpg";
        BuyerProfile existing = new BuyerProfile(
                KEYCLOAK_ID, "Buyer", new PhoneNumber("+84912345678"),
                "http://localhost:9000/vnshop-avatars/" + key,
                List.of());
        when(objectStoragePort.headObject(key)).thenReturn(Optional.of(
                new AvatarObjectMetadata(key, "image/jpeg", 100L, VALID_SHA)));
        when(objectStoragePort.publicUrl(key))
                .thenReturn(URI.create("http://localhost:9000/vnshop-avatars/" + key));
        when(userRepositoryPort.findBuyerByKeycloakId(KEYCLOAK_ID)).thenReturn(Optional.of(existing));
        when(userRepositoryPort.saveBuyer(any())).thenAnswer(inv -> inv.getArgument(0));

        service.activate(KEYCLOAK_ID, new AvatarActivationRequest(key, 100L, VALID_SHA));

        verify(objectStoragePort, never()).deleteObject(any());
    }

    @Test
    void createUpload_rejectsFilenameWithoutExtension() {
        // extension() returns "" when there's no dot; validateExtension then
        // rejects "" because it's not in ALLOWED_EXTENSIONS.
        assertThatThrownBy(() -> service.createUpload(KEYCLOAK_ID, new AvatarUploadRequest(
                "noextension", "image/jpeg", 100L, VALID_SHA)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("file extension");
    }

    @Test
    void activate_acceptsEmptyStorageChecksumAsMissingMetadata() {
        // Different from null: MinIO can return an empty-string user-metadata
        // value instead of dropping the header. Both should be treated as
        // "storage didn't echo it" so size is the only structural check.
        String key = "avatars/" + KEYCLOAK_ID + "/empty-meta.jpg";
        when(objectStoragePort.headObject(key)).thenReturn(Optional.of(
                new AvatarObjectMetadata(key, "image/jpeg", 100L, "")));
        when(objectStoragePort.publicUrl(key))
                .thenReturn(URI.create("http://localhost:9000/vnshop-avatars/" + key));
        when(userRepositoryPort.findBuyerByKeycloakId(KEYCLOAK_ID))
                .thenReturn(Optional.of(new BuyerProfile(
                        KEYCLOAK_ID, "Buyer", new PhoneNumber("+84912345678"), null, List.of())));
        when(userRepositoryPort.saveBuyer(any())).thenAnswer(inv -> inv.getArgument(0));

        AvatarActivationResponse response = service.activate(KEYCLOAK_ID,
                new AvatarActivationRequest(key, 100L, VALID_SHA));

        assertThat(response.avatarUrl()).contains(key);
    }
}
