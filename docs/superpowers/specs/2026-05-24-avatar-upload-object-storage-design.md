# Avatar Upload via MinIO Object Storage — Design

**Date:** 2026-05-24
**Status:** Draft (awaiting approval)
**Owner:** dang232
**Related:** [`fe/src/app/components/vnshop-context.tsx`](../../../fe/src/app/components/vnshop-context.tsx), [`services/user-service`](../../../services/user-service), [`services/product-service` (reference pattern)](../../../services/product-service)

## Problem

The buyer profile page (`fe/src/app/pages/ProfilePage.tsx:222`) renders a camera button next to the avatar that has no `onClick` handler — it's pure UI. The `avatarUrl` field has been on `BuyerProfile` since pt28 and the FE now reads it (`9830a9f5`), but no path exists to actually populate it. So every user shows the initial-letter placeholder forever.

The platform already has MinIO running for product images and review attachments; the bucket bootstrap (`docker-compose.yml`) creates `vnshop-products`, `vnshop-reviews`, `vnshop-invoices`. Avatars need their own bucket and the same upload pattern.

## Goal

Wire user-service to MinIO/R2 the same way product-service is wired, expose presigned-upload endpoints under `/users/me/avatar`, and make the profile page's camera button open a file picker that uploads → activates → refreshes the avatar.

## Non-Goals

- No image transforms (no resize, no crop, no thumbnail generation). The buyer's uploaded image is what renders, scaled by CSS. Cropping UX is a follow-up.
- No CDN signing rotation. Presigned download URLs are MinIO-style, valid for the bucket's anonymous-download policy. R2 swap will need a re-pass.
- No avatar history / versioning. Upload overwrites; the previous object is deleted.
- No moderation / NSFW scanning. Future ObjectValidationService work could plug in; out of scope for this design.

## Architecture

### BE: user-service object-storage adapter

Mirror the product-service pattern verbatim:

```
services/user-service/src/main/java/com/vnshop/userservice/
├── domain/port/out/
│   └── ObjectStoragePort.java                # interface (5 methods)
├── application/avatar/
│   ├── AvatarUploadService.java              # createUpload + activate
│   ├── AvatarUploadRequest.java              # filename, contentType, contentLength, sha256Hex
│   ├── AvatarUploadResponse.java             # objectKey, uploadUrl, expiresInSeconds
│   ├── AvatarActivationRequest.java          # contentLength, sha256Hex (verify)
│   └── AvatarActivationResponse.java         # avatarUrl (presigned download)
└── infrastructure/storage/
    ├── ObjectStorageProperties.java          # vnshop.user-storage.{enabled, bucket, ...}
    ├── ObjectStorageConfig.java              # @ConditionalOnProperty enabled=true
    ├── ObjectStorageNoopConfig.java          # fallback when disabled
    └── S3ObjectStorageAdapter.java           # AWS SDK v2, same as product-service
```

**Object key shape:** `avatars/{keycloakId}/{timestamp}-{shortRandom}.{ext}`

Why the random suffix: a stable key (`avatars/{keycloakId}.jpg`) would let cached URLs hold a previous upload's content. Timestamped keys + delete-old-on-activate ensures each upload gets a fresh URL that can be cached forever by the browser.

**Activation flow:**

1. FE POSTs `/users/me/avatar/upload` with `{ filename, contentType, contentLength, sha256Hex }`.
2. BE validates: extension ∈ {jpg, jpeg, png, webp}, contentType matches, size ≤ 2 MB. Returns `{ objectKey, uploadUrl }` (5-min TTL presigned PUT).
3. FE PUTs the file body to `uploadUrl` directly (browser → MinIO, no proxy through user-service).
4. FE POSTs `/users/me/avatar/activate` with `{ objectKey, contentLength, sha256Hex }`.
5. BE re-fetches via `headObject(key)` to confirm the upload landed at the expected size + checksum, deletes any prior avatar object for this user, then `upsertBuyerProfile(... avatarUrl=...)`.
6. Response: the updated `BuyerProfileResponse`. FE invalidates `["users", "me"]`.

**Why two-phase, not direct multipart-to-user-service:** the product-service pattern. Streaming multipart through Spring Boot ties up worker threads on a network-bound copy; presigned uploads keep that traffic on the object store. Same pattern, less risk than inventing a new one.

### FE: profile page upload UX

```
fe/src/app/pages/ProfilePage.tsx               # camera button now opens a hidden <input type="file">
fe/src/app/lib/api/endpoints/users.ts          # add avatarUpload + avatarActivate
fe/src/app/types/api/user.ts                   # add avatarUploadResponseSchema, avatarActivationResponseSchema
fe/src/app/hooks/use-avatar-upload.ts          # new — orchestrates POST -> PUT -> POST + invalidate
```

**`useAvatarUpload` flow:**

```ts
const upload = useMutation(async (file: File) => {
  const sha256 = await sha256OfFile(file);
  const init = await api.post("/users/me/avatar/upload", avatarUploadResponseSchema, {
    filename: file.name,
    contentType: file.type,
    contentLength: file.size,
    sha256Hex: sha256,
  });
  await fetch(init.uploadUrl, { method: "PUT", body: file, headers: { "Content-Type": file.type }});
  return api.post("/users/me/avatar/activate", userProfileSchema, {
    objectKey: init.objectKey,
    contentLength: file.size,
    sha256Hex: sha256,
  });
}, {
  onSuccess: () => qc.invalidateQueries({ queryKey: ["users", "me"] }),
});
```

**Error states:**
- File > 2 MB → toast "Avatar must be under 2 MB" (BE returns 400 too; FE pre-checks for snappier UX).
- File extension/type mismatch → toast "Use a JPG, PNG, or WebP image".
- PUT to MinIO fails (network) → toast "Upload failed, try again", no profile mutation.
- Activate fails → toast "Could not save avatar"; previous avatar untouched.

### Compose + bootstrap

```yaml
# docker-compose.yml — extend the existing minio-bootstrap entrypoint
mc mb --ignore-existing local/vnshop-products local/vnshop-reviews local/vnshop-invoices local/vnshop-avatars
mc anonymous set download local/vnshop-products local/vnshop-reviews local/vnshop-avatars
```

Avatars are public-readable like product images. The signed-upload step gates writes to authenticated buyers only.

```yaml
# user-service env
VNSHOP_USER_STORAGE_ENABLED: "true"
VNSHOP_USER_STORAGE_PROFILE: "MINIO"
VNSHOP_USER_STORAGE_ENDPOINT: ${OBJECT_STORAGE_ENDPOINT:-http://minio:9000}
VNSHOP_USER_STORAGE_BUCKET: vnshop-avatars
VNSHOP_USER_STORAGE_ACCESS_KEY: ${MINIO_ROOT_USER:-vnshop}
VNSHOP_USER_STORAGE_SECRET_KEY: ${MINIO_ROOT_PASSWORD:-vnshop123}
```

R2 swap: change `VNSHOP_USER_STORAGE_PROFILE=R2` + the endpoint/credentials. Same code path.

## Data Flow

```
Buyer clicks camera → <input type="file"> opens
  ↓
useAvatarUpload.mutate(file):
  POST /users/me/avatar/upload → { objectKey, uploadUrl }
  ↓
PUT uploadUrl with file body (browser → MinIO direct)
  ↓
POST /users/me/avatar/activate { objectKey, contentLength, sha256Hex }
  → BE headObject(objectKey) verifies size + checksum
  → BE deletes prior avatar object
  → BE writes avatarUrl to BuyerProfile (BE also stores publicUrl pattern)
  → BE returns updated BuyerProfileResponse
  ↓
React Query invalidates ["users", "me"]
  ↓
useProfile() refetches → vnshop-context.user.avatar updates → navbar avatar swaps
```

## Error Handling

- **Object never lands at MinIO** (HEAD returns 404 in `activate`): 410 Gone, FE shows "Upload didn't complete, try again".
- **SHA mismatch on activate** (browser uploaded a different file than it claimed): 422, FE shows "Avatar verification failed".
- **Bucket policy regression** (anonymous read disabled): the avatar URL would render the IconPhotoOff placeholder. Caught by the workday spec assertion below.
- **Concurrent uploads** by the same user: last-write-wins via timestamp keys. Not a correctness problem.

## Testing Approach

**BE jest:** `AvatarUploadServiceTest` — happy path (creates pending metadata), validation rejects (size/type/extension), activation verifies hash + writes profile, activation fails on missing object.

**FE vitest:** `useAvatarUpload` — happy path mocks the three calls + verifies invalidate; error path on PUT failure does NOT invalidate; size pre-check rejects without API call.

**Workday extension** (gates the cross-cutting wire-up):
- New step in `workday-buyer.spec.ts`: "Upload an avatar JPEG and watch the navbar reflect it." Drive the file input, wait for the `<img>` `src` on the navbar to be a non-data URL, assert no IconPhotoOff fallback rendered.

## Files Created / Modified

```
A  services/user-service/src/main/java/com/vnshop/userservice/domain/port/out/ObjectStoragePort.java
A  services/user-service/src/main/java/com/vnshop/userservice/application/avatar/AvatarUploadService.java
A  services/user-service/src/main/java/com/vnshop/userservice/application/avatar/AvatarUploadRequest.java
A  services/user-service/src/main/java/com/vnshop/userservice/application/avatar/AvatarUploadResponse.java
A  services/user-service/src/main/java/com/vnshop/userservice/application/avatar/AvatarActivationRequest.java
A  services/user-service/src/main/java/com/vnshop/userservice/application/avatar/AvatarActivationResponse.java
A  services/user-service/src/main/java/com/vnshop/userservice/infrastructure/storage/ObjectStorageProperties.java
A  services/user-service/src/main/java/com/vnshop/userservice/infrastructure/storage/ObjectStorageConfig.java
A  services/user-service/src/main/java/com/vnshop/userservice/infrastructure/storage/ObjectStorageNoopConfig.java
A  services/user-service/src/main/java/com/vnshop/userservice/infrastructure/storage/S3ObjectStorageAdapter.java
M  services/user-service/src/main/java/com/vnshop/userservice/infrastructure/web/UserController.java       # POST /me/avatar/upload + /me/avatar/activate
M  services/user-service/pom.xml                                                                          # add aws-java-sdk-s3
M  docker-compose.yml                                                                                     # vnshop-avatars bucket + user-service env
A  fe/src/app/hooks/use-avatar-upload.ts
A  fe/src/app/lib/api/endpoints/users.ts                                                                  # avatarUpload, avatarActivate fns
M  fe/src/app/types/api/user.ts                                                                           # add upload + activation schemas
M  fe/src/app/pages/ProfilePage.tsx                                                                       # wire camera button to file input
A  services/user-service/src/test/.../AvatarUploadServiceTest.java
A  fe/src/app/hooks/use-avatar-upload.test.ts
M  fe/e2e/workday-buyer.spec.ts                                                                           # add avatar upload step
M  docs/UI-QA-COVERAGE.md                                                                                 # bump scenarios + buyer step count
```

## Open Questions

1. **2 MB cap is right for buyer-side avatars** (Twitter is 2 MB, Discord is 8). Confirm before locking in.
2. **Anonymous read on `vnshop-avatars` is acceptable** given any image-resolvable user could be enumerated by guessing keycloakIds — but the bucket lists are not enumerable through MinIO's anonymous policy, only direct GETs by exact key. Same exposure model as product images. Confirm.
3. **What happens to the avatar bucket when a user is deleted (account closure)?** Out of scope here, but worth a TODO line in `AvatarUploadService` so we don't ship orphaned objects.

## Risks

| Risk | Mitigation |
|---|---|
| MinIO bucket bootstrap doesn't fire on existing volumes (containers already up before adding `vnshop-avatars`) | `mc mb --ignore-existing` is idempotent; document the manual `docker compose down -v && up` step in the README for stale local stacks |
| Browser sends a Content-Type that MinIO presigned URL didn't sign for | FE PUT explicitly sets `Content-Type: file.type`; AvatarUploadService signs with the same type the FE declared in step 1 |
| User uploads a 1.99 MB GIF named `.jpg` | BE accepts the JPG declaration but contentType mismatch is caught at activate; toast surfaces the failure |
| Prior avatar objects not cleaned up if activate fails after delete-old completes | Order ops in activate: write profile FIRST, then delete-old. A crashed delete leaves an orphan but the profile is correct. A periodic sweeper job is a future cleanup if it becomes a problem |
