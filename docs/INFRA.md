# Kitchen: infrastructure

Kitchen runs on a **dedicated mini PC** (refurbished Lenovo ThinkCentre M910x Tiny, i5-6500T, 16 GB RAM, 256 GB SSD), under Ubuntu Server with **k3s**. The cluster is meant to host other projects too (including the Chacun son truc backend). The Raspberry Pi 5 (Claudine, Actual Budget) is **not** used: physical separation settles the question of sharing a machine with sensitive data.

The commands and manifests in this document are starting points: adapt them, and document what was actually done.

## 0. Split between repositories

| Repository | Content |
|---|---|
| `homelab` | What is common to the cluster: machine preparation (script or Ansible), k3s installation, ArgoCD and its `Application`s (pointing to each project's `deploy/k8s`), Postgres and the backup CronJob, cloudflared, Sealed Secrets controller, baseline NetworkPolicies, **Terraform** (or OpenTofu) for resources outside the cluster |
| `kitchen-ai` | Kitchen's code, its CI, and `deploy/k8s`: only the manifests of the `kitchen` namespace (deployments, services, ingress, NetworkPolicies, SealedSecrets) |

**Terraform** manages what has an API and lives outside the cluster: Cloudflare DNS, tunnel and WAF rules, the B2 bucket and application key. It manages neither the machine (manual install, or Ansible) nor the cluster content (ArgoCD). Terraform state contains sensitive values: it is stored outside Git, in an encrypted remote backend **[to verify: chosen backend]**, never committed.

The rest of this document describes the content of `homelab`, unless `kitchen-ai/deploy/k8s` is mentioned.

## 1. Overview

```
Internet ── Cloudflare (julco.dev DNS, TLS, WAF) ── outbound tunnel
                                                       │
┌──────────────── mini PC (k3s, single node) ──────────┼─────────────┐
│  namespace cloudflared   : cloudflared ──────────────┘             │
│  namespace kitchen       : api, web, gotenberg                     │
│  namespace postgres      : PostgreSQL (one db + one role/project)  │
│  namespace argocd        : ArgoCD                                  │
│  namespace other-projects: chacun-son-truc, …                      │
└────────────────────────────────────────────────────────────────────┘
        encrypted backups ──► Backblaze B2 (EU region)
```

## 2. Machine preparation

On delivery: check the hardware under Windows (RAM detected, ports, Ethernet) during the withdrawal period, then:

1. **Update the Lenovo BIOS** (bootable USB version).
2. In the BIOS: **automatic restart after power loss**, boot from the SSD, disable what is not needed (no Wi-Fi anyway, Bluetooth).
3. **Ubuntu Server LTS**, minimal install, optional disk encryption (requires typing the passphrase at boot, to weigh against automatic restart).
4. Dedicated admin user, **key-only SSH**, no root login, `unattended-upgrades` enabled.
5. **Static IP** (DHCP reservation on the router).

## 3. Network isolation

The machine is indirectly exposed to the internet: if it were compromised, it must not be able to reach the rest of the network (Raspberry Pi 5 with Actual Budget and Claudine, Home Assistant, computers).

- **Ideal**: put it on a separate network (VLAN or the router's guest network, if available).
- **Otherwise**: a firewall on the machine (`nftables`) that blocks every **outbound** connection to private LAN ranges, except the router (DNS, gateway) and the admin workstation for inbound SSH.
- **No port opened** or forwarded on the router: everything goes through the Cloudflare tunnel, an outbound connection.
- Tests to run: from a debug pod, reaching the Raspberry Pi 5, Home Assistant or the router (except DNS) must fail; reaching `accounts.google.com` must succeed.

## 4. k3s

- Single-node install, with Traefik (k3s default ingress) and the built-in NetworkPolicy controller.
- **One namespace per project**, `ResourceQuota`s and limits on every container.
- **NetworkPolicies** (default deny, then explicit allows):
  - `kitchen/api` → `postgres` (port 5432), `gotenberg`, internet (Google, Cloudflare);
  - `kitchen/web` → no egress;
  - `gotenberg` → **no internet egress** (it only needs the HTML we send it);
  - ingress only from Traefik;
  - no access between namespaces of different projects.
- **Secrets: Sealed Secrets.** Secrets (Google client, OAuth signing key `KITCHEN_OAUTH_SIGNING_KEY` generated with `openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072`, Postgres passwords, B2 key) are encrypted with `kubeseal` and versioned as `SealedSecret`s: an application's in its own `deploy/k8s` (for example `kitchen-ai/deploy/k8s`), the cluster's (Postgres, B2, cloudflared) in `homelab`. Only the cluster controller can decrypt them, and ArgoCD applies them like any other manifest, without a plugin.
  - **The controller's private key must be backed up outside the cluster** (password manager): without it, reinstalling the cluster makes every secret unreadable.
  - Rotation: after a leak, change the secret at the source (Google, B2…) then reseal; resealing alone is not enough, the old ciphertext stays in Git history.
  - Never a plaintext secret in Git, in a CI variable or in a committed `.env` file.

## 5. Kitchen applications

| Component | Image | Indicative resources |
|---|---|---|
| `api` | GHCR, built by CI (Spring Boot buildpacks or Jib) | requests 512 Mi, limit 1 Gi; capped JVM (`MaxRAMPercentage`) |
| `web` | nginx + static files | 32 Mi |
| `gotenberg` | `gotenberg/gotenberg:8.39.0`, same flags as `apps/api/compose.yaml`: `--chromium-allow-list=^file:///tmp/.*`, `--chromium-deny-public-ips`, `--chromium-deny-private-ips`, `--chromium-disable-javascript`, `--api-disable-download-from`, `--webhook-disable`, `--libreoffice-disable-routes`, `--pdfengines-disable-routes`, `--prometheus-disable-collect`. The API reaches it through `KITCHEN_GOTENBERG_URL` | limit 1 Gi |

- `liveness` and `readiness` probes on `/actuator/health/liveness` and `/actuator/health/readiness` for the API (enabled since phase 1).
- Non-root containers, read-only file system, capabilities dropped, `allowPrivilegeEscalation: false`.
- Ingress: `/mcp`, `/oauth2`, `/.well-known`, `/api`, `/login`, `/logout`, `/webauthn`, `/exports`, `/actuator/health` → `api`; everything else → `web`. Only `/actuator/health` is routed (for the external probe): the other Actuator endpoints are never exposed (and the API only exposes `health` anyway).

## 6. PostgreSQL

- One instance in the `postgres` namespace, with **one database and one role per project**: the `kitchen` role has no rights on the Chacun son truc database, and vice versa.
- **Plain Postgres**: official image, pinned (major version 18, same as `apps/api/compose.yaml` and the tests), deployed as a `StatefulSet`, with a nightly **CronJob** that runs `pg_dump` per database, encrypts it (age) and sends it to B2. Maximum loss on disk failure: one day, acceptable for v1. Moving to CloudNativePG (continuous backups) remains possible later.
- Volume on the SSD (k3s local-path).
- **Extensions**: Kitchen's migrations create `unaccent` (V6, accent-insensitive recipe search). It is a trusted extension shipped with the official image: the `kitchen` role only needs to own its database (or have `CREATE` on it), not to be superuser.

## 7. Backups

- Dedicated B2 bucket (created by Terraform), **EU region**, private, application key limited to this bucket, client-side encryption.
- Retention: 30 days of daily backups, to be stated in the privacy policy.
- **Mandatory restore test** before opening to testers, then every quarter, into a temporary database.
- Also back up: the manifests (already in Git) and the keys needed to decrypt secrets (outside the cluster, in a password manager).

## 8. Cloudflare

- Tunnel named "homelab", cloudflared deployed in the cluster, one route per project (`kitchen.julco.dev` → Traefik).
- SSL Full, HSTS, WAF rate-limiting rules on `/mcp`, `/oauth2` and `/exports`.
- DNS, tunnel and WAF rules are declared in Terraform (`homelab` repository), not configured by hand in the dashboard.

## 9. Deployment pipeline (GitOps)

1. Push to `main` → **GitHub Actions**: backend and front-end tests, image builds, publication to GitHub Container Registry, tag = commit SHA.
2. CI updates the image tag in `kitchen-ai/deploy/k8s` (Kustomize) and commits to the same repository. That commit must not trigger CI again (path or author filter, **[to verify]** in phase 13).
3. **ArgoCD** (installed and configured from `homelab`) detects the change and syncs the cluster.
4. k3s starts the new pods, waits for readiness, switches traffic.

Nobody logs into the server to deploy: Git is the source of truth. Flyway migrations run when the API starts.

## 10. Minimal monitoring

- External probe on `https://kitchen.julco.dev/actuator/health` with alerting.
- Later: Prometheus and Grafana (plan for about 1 GB of RAM).
