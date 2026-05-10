# Secrets Management

VNShop keeps secret values out of git. Local development uses a private `.env.secrets` file at the repository root. Kubernetes production uses Bitnami SealedSecrets so encrypted Secret manifests can be committed safely while plaintext values remain available only to cluster controllers.

## Local Development

1. Copy `.env.secrets.example` to `.env.secrets`.
2. Replace every placeholder with a local-only value.
3. Load it before starting services, for example with Docker Compose `--env-file .env.secrets` or a shell-specific dotenv loader.
4. Never commit `.env.secrets`; it is ignored by `.gitignore`.

## Kubernetes Production

Production secrets are managed as SealedSecrets:

1. Create or rotate a plaintext Secret locally or in CI from a secure secret store.
2. Seal it with the production cluster public certificate: `kubeseal --format yaml --cert prod-sealed-secrets.pem < secret.yaml > sealed-secret.yaml`.
3. Commit only the sealed manifest under the environment overlay that deploys it.
4. Let the SealedSecrets controller decrypt it into a Kubernetes Secret in-cluster.
5. Mount or project values as environment variables into the service Deployments.
6. Rotate by producing a new sealed manifest from new plaintext values. Do not edit generated ciphertext by hand.

Use one Kubernetes Secret per environment named `vnshop-secrets` unless a platform overlay requires finer service ownership. Scope RBAC so only VNShop workloads and release automation can read the decrypted Secret.

## Secret Inventory

| Secret | Consumers | Local source | Production source | Notes |
| --- | --- | --- | --- | --- |
| `DATASOURCE_PASSWORD` | Java services with PostgreSQL schemas and notification service database access | `.env.secrets` | SealedSecret key in `vnshop-secrets` | PostgreSQL application user password. Rotate with database credential rotation. |
| `KEYCLOAK_ADMIN_PASSWORD` | Keycloak bootstrap/admin operations | `.env.secrets` | SealedSecret key in `vnshop-secrets` | Initial/admin console password. Use only for controlled admin access and rotate after bootstrap if policy requires. |
| `GATEWAY_OAUTH2_CLIENT_SECRET` | API gateway OAuth2 confidential client `vnshop-gateway` | `.env.secrets` | SealedSecret key in `vnshop-secrets` | Must match Keycloak `vnshop-gateway` client secret configured in production realm import or external Keycloak admin automation. |
| `PAYMENT_GATEWAY_API_KEY` | Payment service external payment provider adapter | `.env.secrets` | SealedSecret key in `vnshop-secrets` | Provider credential for payment authorization/callback reconciliation. |
| `CARRIER_API_KEY` | Shipping service carrier integration adapter | `.env.secrets` | SealedSecret key in `vnshop-secrets` | Carrier rating/label API credential. |
| `JWT_SIGNING_KEY` | Token minting or legacy JWT validation components when enabled | `.env.secrets` | SealedSecret key in `vnshop-secrets` | Use high-entropy value. Prefer Keycloak asymmetric keys for user auth tokens; keep this only for components that explicitly require shared JWT signing. |

## Handling Rules

- Commit only `.env.secrets.example`, docs, and sealed ciphertext.
- Never paste plaintext secrets into issue trackers, chat, logs, evidence files, or plan notes.
- Keep example values obviously fake and non-routable.
- Rotate any value that may have been exposed in logs, commits, screenshots, or local terminal recordings.
- Prefer secret references in manifests over literal values: `valueFrom.secretKeyRef.name: vnshop-secrets`.
