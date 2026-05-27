# R2 Swap Checklist — Avatar Storage (user-service)

> When R2 credentials arrive, follow this document top-to-bottom.
> No code change is needed **except** the one-liner in §3 (publicUrl bucket-prefix fix).
> Everything else is env-var and dashboard work.

---

## 1. Today's MinIO Config

user-service avatar storage is driven by the `vnshop.user-storage.*` property group
(`ObjectStorageProperties.java`, prefix `vnshop.user-storage`).
The live bean is `S3ObjectStorageAdapter` (activated when `vnshop.user-storage.enabled=true`).
A `NoopObjectStoragePort` fallback fires when the property is absent (unit tests, slim stacks).

Two endpoint surfaces exist by design: `endpoint` is the docker-network address the BE uses
for `headObject` / `deleteObject` (`http://minio:9000`); `publicEndpoint` is what the browser
PUTs against and what `publicUrl()` embeds in `BuyerProfile.avatarUrl` (`http://localhost:9000`).

`publicUrl()` today produces:

```
http://localhost:9000/vnshop-avatars/avatars/{keycloakId}/{ts}-{uuid}.{ext}
```

Shape: `{publicEndpoint}/{bucket}/{key}` — path-style, no signing, relies on the bucket
anonymous-download policy set by `minio-bootstrap` in `docker-compose.yml:304-308`.

**Current env vars (`docker-compose.yml:422-430`):**

| Var | Today's value |
|-----|---------------|
| `VNSHOP_USER_STORAGE_ENABLED` | `true` |
| `VNSHOP_USER_STORAGE_PROFILE` | `MINIO` |
| `VNSHOP_USER_STORAGE_ENDPOINT` | `http://minio:9000` |
| `VNSHOP_USER_STORAGE_PUBLIC_ENDPOINT` | `http://localhost:9000` |
| `VNSHOP_USER_STORAGE_BUCKET` | `vnshop-avatars` |
| `VNSHOP_USER_STORAGE_REGION` | `auto` |
| `VNSHOP_USER_STORAGE_ACCESS_KEY` | `${MINIO_ROOT_USER:-vnshop}` |
| `VNSHOP_USER_STORAGE_SECRET_KEY` | `${MINIO_ROOT_PASSWORD:-vnshop123}` |
| `VNSHOP_USER_STORAGE_PATH_STYLE_ACCESS` | `true` |

---

## 2. What Flips for R2

| Env var | Today (MinIO) | R2 value | Where to set |
|---------|---------------|----------|--------------|
| `VNSHOP_USER_STORAGE_PROFILE` | `MINIO` | `R2` | `docker-compose.yml` line 423 |
| `VNSHOP_USER_STORAGE_ENDPOINT` | `http://minio:9000` | `https://<account-id>.r2.cloudflarestorage.com` | `docker-compose.yml` line 424 |
| `VNSHOP_USER_STORAGE_PUBLIC_ENDPOINT` | `http://localhost:9000` | `https://<bucket>.<account-id>.r2.dev` or `https://cdn.yourdomain.com` | `docker-compose.yml` line 425 |
| `VNSHOP_USER_STORAGE_BUCKET` | `vnshop-avatars` | `vnshop-avatars` (keep or rename) | `docker-compose.yml` line 426 |
| `VNSHOP_USER_STORAGE_REGION` | `auto` | `auto` | no change |
| `VNSHOP_USER_STORAGE_ACCESS_KEY` | MinIO root user | R2 API token Access Key ID | `.env` (secret) |
| `VNSHOP_USER_STORAGE_SECRET_KEY` | MinIO root password | R2 API token Secret Access Key | `.env` (secret) |
| `VNSHOP_USER_STORAGE_PATH_STYLE_ACCESS` | `true` | `false` | `docker-compose.yml` line 430 |

**Total env vars that change: 5 required** (PROFILE, ENDPOINT, PUBLIC_ENDPOINT, ACCESS_KEY,
SECRET_KEY) **+ 1 flag** (PATH_STYLE_ACCESS=false).

> `MINIO_ROOT_USER` / `MINIO_ROOT_PASSWORD` in `.env` are MinIO-only; leave them in place
> (they still drive the local MinIO container for dev). The R2 keys go only into
> `VNSHOP_USER_STORAGE_ACCESS_KEY` / `SECRET_KEY`.

---

## 3. Path-Style vs Virtual-Host — RESOLVED

**S3 API calls (headObject, deleteObject, presign):** R2 requires **virtual-host style**.
Set `VNSHOP_USER_STORAGE_PATH_STYLE_ACCESS=false`.

Evidence:
- `ObjectStorageConfig.java:53` passes `pathStyleAccessEnabled(properties.isPathStyleAccess())`
  to both `S3Client` and `S3Presigner`.
- `ObjectStorageProperties.java:30` defaults `pathStyleAccess = true`.
- `.env.example:60` already documents: `OBJECT_STORAGE_PATH_STYLE=false` for R2.
- `SESSION-HANDOVER-2026-05-25-pt36.md:55` explicitly states:
  `VNSHOP_USER_STORAGE_PATH_STYLE_ACCESS=false   # R2 uses virtual-host style`.

Flipping the env var is sufficient for SDK calls — no code change needed there.

**publicUrl() — one-line code fix required.**

`S3ObjectStorageAdapter.java:55` always produces `{publicEndpoint}/{bucket}/{key}`.
R2 public URLs (R2.dev subdomain and custom domain) map the hostname directly to the bucket
root, so the bucket name must NOT appear in the path. Fix:

```java
// S3ObjectStorageAdapter.java:55 — BEFORE (MinIO path-style):
return URI.create(base + "/" + properties.getBucket() + "/" + key);

// AFTER (bucket-root-relative; works for R2.dev, custom domain, and MinIO
//        when PUBLIC_ENDPOINT includes the bucket as a path segment):
return URI.create(base + "/" + key);
```

Then encode the bucket into `PUBLIC_ENDPOINT`:
- MinIO local dev: `http://localhost:9000/vnshop-avatars`
- R2.dev: `https://<bucket>.<account-id>.r2.dev`
- Custom domain: `https://cdn.yourdomain.com`

The workday E2E assertion (`workday-buyer.spec.ts:406`) checks `src` matches
`/\/vnshop-avatars\/avatars\//`. After the fix, update that regex to `/\/avatars\//`
(the bucket is now in the hostname, not the path).

**Justification:** R2 does not support path-style for its S3 API endpoint, and R2 public
CDN URLs never include the bucket name in the path — the bucket is encoded in the hostname.

---

## 4. One-Time R2 Setup

### 4a. Create the bucket

Cloudflare Dashboard -> R2 Object Storage -> **Create bucket**.
Name: `vnshop-avatars`. Location: automatic. Leave default storage class.

### 4b. Enable public access

**Option A — R2.dev subdomain (quickest):**
Bucket -> Settings -> Public access -> **Allow Access**.
Note the URL: `https://pub-<hash>.r2.dev` — this becomes `PUBLIC_ENDPOINT`.

**Option B — Custom domain (production-grade):**
Bucket -> Settings -> Custom Domains -> **Connect Domain** -> enter `cdn.yourdomain.com`
(must be on Cloudflare DNS). Cloudflare auto-provisions the CNAME and TLS cert.
Use `https://cdn.yourdomain.com` as `PUBLIC_ENDPOINT`.

No S3 bucket policy is needed. R2 public access is controlled by the dashboard toggle only.
The `mc anonymous set download` step from `minio-bootstrap` (`docker-compose.yml:305-307`)
has no R2 equivalent — the dashboard toggle replaces it entirely.

### 4c. CORS policy (required for browser PUT)

Bucket -> Settings -> CORS Policy -> paste:

```json
[
  {
    "AllowedOrigins": [
      "http://localhost:3000",
      "http://localhost:5173",
      "https://yourdomain.com"
    ],
    "AllowedMethods": ["PUT", "HEAD"],
    "AllowedHeaders": ["Content-Type", "Content-Length", "x-amz-meta-sha256"],
    "MaxAgeSeconds": 3600
  }
]
```

Match `AllowedOrigins` to `GATEWAY_CORS_ALLOWED_ORIGINS` in `.env`
(default: `http://localhost:3000,http://localhost:5173`, `docker-compose.yml:361`).

### 4d. Create R2 API token

Cloudflare Dashboard -> R2 -> **Manage R2 API Tokens** -> **Create API Token**.
Permissions: Object Read & Write, scoped to bucket `vnshop-avatars`.
Copy Access Key ID -> `VNSHOP_USER_STORAGE_ACCESS_KEY`.
Copy Secret Access Key -> `VNSHOP_USER_STORAGE_SECRET_KEY`.

---

## 5. Verification Checklist

Run after flipping env vars, applying the `publicUrl()` one-liner, and restarting user-service.

**1. Presigned upload URL shape**
```bash
curl -s -X POST http://localhost:8081/users/me/avatar/upload \
  -H "Authorization: Bearer <buyer-token>" \
  -H "Content-Type: application/json" \
  -d '{"filename":"test.png","contentType":"image/png","contentLength":68,"sha256Hex":"<sha>"}'
```
Expected: `uploadUrl` starts with
`https://<account-id>.r2.cloudflarestorage.com/vnshop-avatars/avatars/`
and contains `X-Amz-Signature`. Confirm it does NOT contain `localhost`.

**2. Browser PUT succeeds**
```bash
curl -X PUT "<uploadUrl-from-step-1>" \
  -H "Content-Type: image/png" \
  --data-binary @test.png
```
Expected: HTTP 200 with empty body (R2 returns 200 on successful PUT).

**3. publicUrl renders in browser**
Call `POST /users/me/avatar/activate` with the `objectKey` from step 1.
Expected: `avatarUrl` in the response matches `https://<PUBLIC_ENDPOINT>/avatars/<keycloakId>/...`.
Open that URL in a browser tab — image must load (HTTP 200, Content-Type: image/png).

**4. CORS check for FE fetch**
```bash
curl -I -X OPTIONS "https://<PUBLIC_ENDPOINT>/avatars/test-key" \
  -H "Origin: http://localhost:5173" \
  -H "Access-Control-Request-Method: PUT"
```
Expected: response includes `Access-Control-Allow-Origin: http://localhost:5173`.

**5. Workday E2E suite passes**
```bash
cd fe && npx playwright test e2e/workday-buyer.spec.ts
```
Expected: 1/1 pass. The avatar step (`workday-buyer.spec.ts:406`) asserts `src` matches
the bucket URL pattern. Ensure the regex is updated to `/\/avatars\//` after the code fix.

---

## 6. Rollback

Revert these vars in `docker-compose.yml` and restart user-service:

```
VNSHOP_USER_STORAGE_PROFILE=MINIO
VNSHOP_USER_STORAGE_ENDPOINT=http://minio:9000
VNSHOP_USER_STORAGE_PUBLIC_ENDPOINT=http://localhost:9000/vnshop-avatars
VNSHOP_USER_STORAGE_ACCESS_KEY=${MINIO_ROOT_USER:-vnshop}
VNSHOP_USER_STORAGE_SECRET_KEY=${MINIO_ROOT_PASSWORD:-vnshop123}
VNSHOP_USER_STORAGE_PATH_STYLE_ACCESS=true
```

If the `publicUrl()` one-liner was applied, revert `S3ObjectStorageAdapter.java:55` to:
```java
return URI.create(base + "/" + properties.getBucket() + "/" + key);
```
and restore `PUBLIC_ENDPOINT` to `http://localhost:9000` (without the bucket suffix).

**Existing avatarUrl values** stored in `BuyerProfile` embed the MinIO hostname and will 404
after the swap. They self-heal on the user's next upload. For zero-downtime URL continuity:
1. Copy objects: `rclone copy minio:vnshop-avatars r2:vnshop-avatars`
2. Rewrite stored URLs:
```sql
UPDATE user_svc.buyer_profile
SET avatar_url = replace(avatar_url,
  'http://localhost:9000/vnshop-avatars', '<NEW_PUBLIC_ENDPOINT>')
WHERE avatar_url LIKE 'http://localhost:9000%';
```

---

## Summary

- **Env-var count:** 6 vars change (PROFILE, ENDPOINT, PUBLIC_ENDPOINT, ACCESS_KEY, SECRET_KEY, PATH_STYLE_ACCESS).
- **Path-style answer:** Set `VNSHOP_USER_STORAGE_PATH_STYLE_ACCESS=false` for R2. The `publicUrl()` method also needs a one-line code fix (`S3ObjectStorageAdapter.java:55`) to drop the bucket segment from the path — R2 public URLs are bucket-root-relative.
- **Biggest open risk:** Existing `avatarUrl` values in `buyer_profile` embed the MinIO hostname. They 404 after the swap until each user re-uploads. Run `rclone copy` + a SQL `replace()` before flipping if zero-downtime URL continuity is required.
- **One code change required:** `S3ObjectStorageAdapter.java:55` — drop `/{bucket}` from `publicUrl()` and move the bucket into `PUBLIC_ENDPOINT` instead.