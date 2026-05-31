package com.vnshop.userservice.domain.port.out;

import com.vnshop.userservice.domain.storage.AvatarObjectMetadata;

import java.net.URI;
import java.time.Duration;
import java.util.Optional;

/**
 * Object-storage adapter port for avatar uploads. Mirrors the surface area
 * product-service uses, narrowed to what the avatar flow needs:
 *  - createUpload signs a PUT URL (browser uploads directly to MinIO/R2).
 *  - activate calls headObject to verify the upload landed at the declared
 *    size + sha, then publicUrl resolves the canonical address the FE
 *    should render.
 *  - delete-old runs after a fresh upload activates so we don't accumulate
 *    orphaned objects under the same user.
 *
 * The resolveUploadEndpoint distinction matters: the BE talks to MinIO at
 * its docker-network hostname (http://minio:9000), but the browser's PUT
 * has to use http://localhost:9000. Without two endpoints the presigned
 * URL would be unreachable from the browser.
 */
public interface ObjectStoragePort {
    URI getSignedUploadUrl(String key, AvatarObjectMetadata metadata, Duration ttl);

    URI publicUrl(String key);

    void deleteObject(String key);

    Optional<AvatarObjectMetadata> headObject(String key);
}
