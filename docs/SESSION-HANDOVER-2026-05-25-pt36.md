# Session handover — 2026-05-25 (pt36: avatar upload, MinIO-first with R2 swap path)

**Last commit (HEAD before this block):** `43679625` (`docs(pt35): payout audit trail block + avatar-upload design spec`)

**Gates (live stack):**
- FE typecheck: 0 errors clean.
- FE vitest: 165 / 165 (was 159; +6 for `useAvatarUpload`).
- user-service mvn: 141 / 141 (was 119; +22 for `AvatarUploadServiceTest`). Jacoco 90% line + branch on `application.avatar` package.
- order-service / product-service / seller-finance-service mvn: untouched, still green.
- Workday suite: 3 / 3, ~28 s. Buyer step now includes "Upload an avatar via the profile camera button" — drives the hidden file input, asserts the success toast, asserts the rendered `<img>` `src` points at `/vnshop-avatars/avatars/`.
- Journey suite: 7 / 7 in ~35 s (regression check; no avatar work in the journey, but verified nothing else broke).
- MinIO bootstrap log confirms `Bucket created successfully local/vnshop-avatars` + anonymous-download policy applied.

## What this block was

Pt32-pt34 carryover: the buyer profile page had a camera button next to the avatar with no `onClick` — a pure UI placeholder. `BuyerProfile.avatarUrl` had been on the domain since pt28, and the FE had been *reading* it, but no code path could *populate* it. Every user showed the gradient-letter initial.

Pt35 closed the payout audit trail thread. This block closes the avatar thread end-to-end.

The design doc (`docs/superpowers/specs/2026-05-24-avatar-upload-object-storage-design.md`, committed in pt35) called for mirroring product-service's object-storage adapter pattern into user-service, with a two-phase presigned PUT flow. That's what landed.

## Architecture

```
ProfilePage camera button
  └─ <input type="file"> hidden, accept=image/jpeg,png,webp
        ↓ onChange
useAvatarUpload mutation:
  1. preflight: size ≤ 2 MB, ext ∈ {jpg,jpeg,png,webp}, type ∈ {image/jpeg,image/png,image/webp}
  2. sha256OfFile(file) via crypto.subtle.digest
  3. POST /users/me/avatar/upload { filename, contentType, contentLength, sha256Hex }
       → { objectKey: "avatars/{keycloakId}/{ms}-{uuid}.jpg",
           uploadUrl: presigned PUT against publicEndpoint,
           expiresInSeconds: 300 }
  4. PUT file body straight to MinIO (browser → http://localhost:9000/...&sig)
       — no proxy through user-service
  5. POST /users/me/avatar/activate { objectKey, contentLength, sha256Hex }
       → BE headObject() verifies size + sha
       → BE writes BuyerProfile.avatarUrl = publicUrl(key)
       → BE deleteObject(prior key) best-effort
       → returns updated BuyerProfileResponse
  6. invalidateQueries(["users","me"]) → ProfilePage re-fetches → <img> src updates
```

**Two endpoint surfaces, same MinIO bucket.** `endpoint=http://minio:9000` is what the BE talks to (headObject, deleteObject). `publicEndpoint=http://localhost:9000` is what the browser PUTs against. Without the split the presigned URL would resolve to a docker-internal hostname the browser can't reach. The presigner is bound to `publicEndpoint` so the URL it signs is one the browser can use.

**MinIO-first, R2-swappable.** The `vnshop.user-storage.profile` property is `MINIO | R2`. When R2 credentials land, flip:
```
VNSHOP_USER_STORAGE_PROFILE=R2
VNSHOP_USER_STORAGE_ENDPOINT=https://<account>.r2.cloudflarestorage.com
VNSHOP_USER_STORAGE_PUBLIC_ENDPOINT=https://<custom-domain-or-r2-public>
VNSHOP_USER_STORAGE_REGION=auto
VNSHOP_USER_STORAGE_ACCESS_KEY=<r2-key>
VNSHOP_USER_STORAGE_SECRET_KEY=<r2-secret>
VNSHOP_USER_STORAGE_PATH_STYLE_ACCESS=false   # R2 uses virtual-host style
```
No code change. The S3ObjectStorageAdapter uses AWS SDK v2 which speaks both. R2's anonymous-read story is custom-domain or public bucket, so `publicUrl()` (path-style URL composition) may need to switch to virtual-host on the R2 path — that's the only known follow-up when R2 lands.

**One avatar per user, last-write-wins.** No metadata table (unlike product-service which has multi-image-per-product + quarantine state machine). The objectKey embeds `System.currentTimeMillis()` + UUID so even rapid re-uploads don't collide; the previous object gets best-effort deleted *after* the new URL commits. A failed delete leaves an orphan but never undoes a successful profile write — a sweep job is a future cleanup.

## BE deviations from the design doc

The design doc said `AvatarUploadResponse(objectKey, uploadUrl, expiresInSeconds)` and `AvatarActivationResponse(avatarUrl)`. Implementation matches except `AvatarActivationResponse` carries the full `BuyerProfile` so the controller can return the same `BuyerProfileResponse` shape every other `/users/me/...` endpoint returns — no FE schema fork. The `avatarUrl` field on the response is redundant with `profile.avatarUrl()` but kept for explicitness in tests.

The doc also said `previousObject` deletion happens *before* the profile write. Flipped that: profile write first, then delete-old. A crashed delete now leaves an orphan rather than corrupting the profile.

## FE deviations from the design doc

The doc said the camera button's `onClick` opens a file picker. Implementation uses a hidden `<input type="file">` with a `ref` and the camera button calls `fileInputRef.current?.click()`. Same UX, but Playwright can drive the hidden input directly via `setInputFiles` without going through the OS file-picker dialog.

The hook reset's `event.target.value = ""` after read so picking the same file twice still triggers `onChange` — easy to miss, but the workday spec would catch it on re-runs.

## Files touched this block

```
M  services/user-service/pom.xml                                                                     # +s3 sdk dep + storage exclude
M  services/user-service/src/main/java/com/vnshop/userservice/infrastructure/web/UserController.java # +/me/avatar/upload, /activate
M  services/user-service/src/main/java/com/vnshop/userservice/infrastructure/config/UseCaseConfig.java
A  services/user-service/src/main/java/com/vnshop/userservice/infrastructure/web/AvatarUploadHttpResponse.java
A  services/user-service/src/main/java/com/vnshop/userservice/domain/port/out/ObjectStoragePort.java
A  services/user-service/src/main/java/com/vnshop/userservice/domain/storage/AvatarObjectMetadata.java
A  services/user-service/src/main/java/com/vnshop/userservice/application/avatar/AvatarUploadService.java
A  services/user-service/src/main/java/com/vnshop/userservice/application/avatar/AvatarUploadRequest.java
A  services/user-service/src/main/java/com/vnshop/userservice/application/avatar/AvatarUploadResponse.java
A  services/user-service/src/main/java/com/vnshop/userservice/application/avatar/AvatarActivationRequest.java
A  services/user-service/src/main/java/com/vnshop/userservice/application/avatar/AvatarActivationResponse.java
A  services/user-service/src/main/java/com/vnshop/userservice/infrastructure/storage/ObjectStorageProperties.java
A  services/user-service/src/main/java/com/vnshop/userservice/infrastructure/storage/ObjectStorageConfig.java
A  services/user-service/src/main/java/com/vnshop/userservice/infrastructure/storage/ObjectStorageNoopConfig.java
A  services/user-service/src/main/java/com/vnshop/userservice/infrastructure/storage/S3ObjectStorageAdapter.java
A  services/user-service/src/test/java/com/vnshop/userservice/application/avatar/AvatarUploadServiceTest.java   # +22 tests
M  fe/src/app/types/api/user.ts                                                                     # avatarUploadResponseSchema
M  fe/src/app/lib/api/endpoints/users.ts                                                            # avatarUpload + avatarActivate fns
A  fe/src/app/hooks/use-avatar-upload.ts                                                            # mutation orchestrator + error mapper
A  fe/src/app/hooks/use-avatar-upload.test.tsx                                                      # +6 tests
M  fe/src/app/pages/ProfilePage.tsx                                                                 # camera button → hidden input
M  fe/src/app/lib/i18n/en.json + vi.json                                                            # profile.avatar.* keys
M  fe/e2e/workday-buyer.spec.ts                                                                     # avatar upload step
M  docker-compose.yml                                                                               # vnshop-avatars bucket + user-service env
A  docs/SESSION-HANDOVER-2026-05-25-pt36.md                                                         # this file
```

## Gotchas this block (extends pt35 list, #91-96)

**97. Presigner endpoint is what the *browser* sees, not what the *BE* uses.** Spent a moment confused about why the design called for two endpoints; the answer is that `S3Presigner.endpointOverride()` is what gets baked into the URL it signs. Sign against the docker-network endpoint and the browser will try to resolve `http://minio:9000/` — fail. Sign against `http://localhost:9000/` and the BE has to use a separate `S3Client` for headObject/deleteObject. Two beans, one shared properties class, both wired in `ObjectStorageConfig`.

**98. Jacoco branch coverage gate is brutal on optional/null edge paths.** First test pass: 79% branch coverage. Second pass: 88%. Final pass: 90% green. The gap was usually one branch where I'd assumed "this is too obviously the wrong path to test." The discipline pays off — adding `activate_doesNotDeleteWhenSameKeyReuploaded` caught a real edge case (idempotent retry of the same activation should not delete the just-committed object). Coverage gates push you to write the test for the case that "couldn't possibly happen" and then it does.

**99. MinIO can strip user-metadata on HEAD responses.** The `x-amz-meta-sha256` header we set on PUT may not come back on HEAD if MinIO's version strips non-standard prefixes. The activation path falls back to size-only verification when `headObject().sha256Hex()` is null/empty, with a comment explaining why. Don't fail activation on a missing storage-side checksum — content-length is the structural-corruption signal that matters.

**100. `<input type="file">` retains the last-picked file's value.** Picking the same file twice fires `onChange` only the first time unless you reset `event.target.value = ""` in the handler. Easy to miss; the workday spec's re-run cycles would surface it eventually but the comment in `onAvatarFilePick` makes it explicit.

## Open thread for the next session

**Higher priority:**
- **R2 swap when credentials arrive.** Mostly env-flip, but verify `publicUrl()` against R2's URL pattern (custom domain vs public bucket vs presigned-GET). Path-style vs virtual-host: R2 uses virtual-host, which is `https://{bucket}.{account}.r2.cloudflarestorage.com/{key}`. The current `publicUrl()` does `{publicEndpoint}/{bucket}/{key}` — for R2 with custom domain, that's `https://avatars.example.com/{key}` and the bucket should be elided from the URL. Either configure `publicEndpoint` to already include the bucket (then drop the bucket-prefix in `publicUrl()`), or add a `urlStyle` property.
- **PayPal capture round-trip** (carryover from pt32-pt35).
- **Shipping tracking ownership check** (carryover).

**Lower priority:**
- VNPay/MoMo `redirectUrl` from PaymentResponse.
- Kafka env-override audit on the other six services.
- Avatar moderation/NSFW scanning hook (the design doc explicitly out-of-scoped this, but listed as a future ObjectValidationService extension point).
- Avatar bucket cleanup on account deletion (orphan sweeper job — same risk as the delete-on-replace failure mode, just at a different lifecycle).

## How to resume

1. **Verify HEAD.** `git log --oneline -3` should show pt36 commits at the top.
2. **Cloud-stub sanity check.** `Get-ChildItem -Force fe/e2e/journey | Format-Table Name, Mode` — every Mode column should be `-a----`.
3. **Smoke gates:**
   - `cd fe; npx tsc --noEmit` → 0 errors.
   - `cd fe; npm test -- --run` → 165 / 165.
   - `cd services/user-service; ./mvnw test` → 141 / 141 (jacoco 90% gate green).
   - Workday suite → 3 / 3.
   - Journey suite → 7 / 7.
4. **Visual sanity:** Profile page → click camera → pick a JPG → toast "Avatar updated" → gradient placeholder is replaced by the picked image. Network panel: POST /upload returns 200, PUT to MinIO returns 200, POST /activate returns 200.
5. **MinIO sanity:** `docker compose exec minio mc ls local/vnshop-avatars/avatars/<keycloak-id>/` — should list the .jpg/.png/.webp objects for that user.

## Final session ledger (pt27 → pt36)

- **pt27**: i18n duplicate-key fix + Tabler migration.
- **pt28**: dark-mode pilot + 47-file codemod.
- **pt29**: 27 UI Playwright specs + 3 BE bugs caught.
- **pt30**: persona-workday suite.
- **pt31**: BA-grade journey chapters 1-4 + 5 caught bugs.
- **pt32**: chapters 5+6 + journey 16/16 PASS.
- **pt33**: 8-issue UX fix-it block. Misdiagnosed chapter-6 flake as kafka.
- **pt34**: chapter-6 flake root-cause: OneDrive cloud-stubs, JSX leak, dialog-onError. 3× 7/7 stable.
- **pt35**: payout audit trail end-to-end — V5 migration, JWT-subject capture, /completed endpoint, FE Pending/Completed tabs, AC-6.4. 3× 7/7 stable. Three new gotchas (#94-96).
- **pt36 (this block)**: avatar upload end-to-end — user-service S3 adapter mirroring product-service, two-phase presigned PUT flow, FE camera-button wiring, workday-buyer step. MinIO-first with documented R2-swap path. 22 new BE tests + 6 new FE tests. Four new gotchas (#97-100).

The story this block tells: one of the rare features where the FE design is plain ("camera button uploads an avatar") but the BE pattern is the work. Picking the right pattern (mirror product-service rather than invent a new one), then narrowing it (no metadata table — avatars are simpler than product images), is what kept the diff at +280 lines instead of +800. The R2 swap path was designed in from day one; when R2 credentials land, the upgrade is an env flip plus one open question on URL style.
