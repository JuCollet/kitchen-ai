# Kitchen: infrastructure

Kitchen runs on a **dedicated mini PC** (refurbished Lenovo ThinkCentre M910x Tiny, i5-6500T, 16 GB RAM, 256 GB SSD), under **Debian 13** with **k3s**. The cluster is meant to host other projects too (including the Chacun son truc backend). The Raspberry Pi 5 (Claudine, Actual Budget) is **not** used: physical separation settles the question of sharing a machine with sensitive data.

This document describes the infrastructure **as it is actually deployed** (state of 2026-11-18). Items that are planned but not done yet are marked **[todo]**, and the list is gathered in section 11.

## 0. Split between repositories

| Repository | Content |
|---|---|
| `homelab-deploy` | **Everything the cluster runs**, driven by ArgoCD: the `argocd/` folder (one `Application` per component, watched by the `root` app) and `prod/<project>/` (manifests of each project, including the `kitchen` namespace: api, web, gotenberg, ingress, NetworkPolicies, database, backups, SealedSecrets). Also holds `pub-cert.pem`, the public certificate used to seal secrets |
| `kitchen-ai` | Kitchen's code, its CI, and the Dockerfiles. No Kubernetes manifest: the CI updates the image SHA in `homelab-deploy` |
| Not in Git yet | Machine preparation (manual install), the firewall rules (`/etc/homelab-firewall.nft` and its systemd unit, see section 3), the B2 bucket and the Cloudflare tunnel (created by hand). **[todo]** Ansible for the machine, Terraform (or OpenTofu) to import and manage B2 and Cloudflare |

**Terraform** will manage what has an API and lives outside the cluster: Cloudflare DNS, tunnel and WAF rules, the B2 bucket and application key. Terraform state contains sensitive values: it must be stored outside Git, in an encrypted remote backend **[to verify: chosen backend]**, never committed.

## 1. Overview

```
Internet ── Cloudflare (julco.dev DNS, TLS) ── outbound tunnel
                                                   │
┌──────────────── mini PC (k3s, single node) ──────┼───────────────────┐
│  ns cloudflared : cloudflared ───────────────────┘                   │
│  kube-system    : Traefik (ingress), Sealed Secrets controller       │
│  ns kitchen     : api, web, gotenberg, kitchen-db (CloudNativePG)    │
│  ns cnpg-system : CloudNativePG operator, Barman Cloud plugin        │
│  ns cert-manager: cert-manager (needed by the Barman plugin)         │
│  ns argocd      : ArgoCD                                             │
└──────────────────────────────────────────────────────────────────────┘
        continuous + daily backups ──► Backblaze B2 (EU region)
```

## 2. Machine preparation

Actual state: hostname `homelab`, admin user `julco`, static address `192.168.86.208` (DHCP reservation), Ethernet. Checklist from the plan (**[to verify]** each point was done):

1. Lenovo BIOS updated (bootable USB version).
2. BIOS: automatic restart after power loss, boot from the SSD, unused hardware disabled. The reboot test of 2026-11-18 showed the whole stack (k3s, ArgoCD, Kitchen, firewall) comes back on its own.
3. Debian 13, minimal install.
4. Dedicated admin user, key-only SSH, no root login, `unattended-upgrades` enabled.
5. Static IP through a DHCP reservation on the router.

## 3. Network isolation

The machine is indirectly exposed to the internet: if it were compromised, it must not be able to reach the rest of the network (Raspberry Pi 5 with Actual Budget and Claudine, other devices).

The router (Google Wifi style, flat `192.168.86.0/24` network, no VLAN or firewall between devices) cannot isolate a wired machine, so the isolation is done **on the machine itself with nftables**:

- A dedicated table `inet homelab_lan`, separate from the rules k3s manages (never use the default `/etc/nftables.conf`: its `flush ruleset` would erase them).
- File `/etc/homelab-firewall.nft`, loaded at boot by the systemd unit `homelab-firewall.service` (oneshot, `RemainAfterExit`).
- `output` chain: established/related and loopback accepted, the gateway `192.168.86.1` accepted, **everything else in `192.168.86.0/24` dropped**.
- `forward` chain: same rule for traffic coming from pods (`10.42.0.0/16`).
- Inbound SSH keeps working (replies to established connections are allowed).
- **No port opened or forwarded** on the router: everything goes through the Cloudflare tunnel, an outbound connection.
- Tested on 2026-11-18, from the host and from a pod outside the `kitchen` namespace: the Raspberry Pi times out, the gateway and the internet answer. The table is present after a reboot.
- Limits: IPv4 only (**[todo]** repeat for IPv6 if the network distributes it); it blocks the machine from reaching the LAN, not the LAN from reaching the machine.
- To undo in an emergency: `sudo nft delete table inet homelab_lan`.

## 4. k3s

- Single-node install, with Traefik (k3s default ingress) and the built-in NetworkPolicy controller.
- **One namespace per project**. **[todo]** `ResourceQuota`s per namespace (containers already have requests and limits).
- **Traefik and forwarded headers.** Traefik only trusts `X-Forwarded-*` headers from trusted addresses, otherwise it replaces them with what it sees (plain HTTP), and Spring would then generate `http://` redirects. A `HelmChartConfig` (`prod/traefik/helmchartconfig.yaml`, deployed by the `traefik-config` Application) sets `ports.web.forwardedHeaders.trustedIPs` to the pod network `10.42.0.0/16`, which contains `cloudflared`. The API relies on `server.forward-headers-strategy: framework`.
- **NetworkPolicies** (`prod/kitchen/networkpolicies.yaml`), enforced and tested:
  - default deny **ingress** for every pod of the `kitchen` namespace;
  - `api` and `web` accept traffic only from Traefik (port 8080); `gotenberg` only from `api` (3000); `kitchen-db` only from `api` (5432), the `cnpg-system` namespace (operator and plugin) and its own instance pods;
  - **egress**: `web` and `gotenberg` can reach nothing; `api` can reach the database (5432), `gotenberg` (3000), DNS, and the internet on port 443 **except private ranges** (so never the Raspberry Pi);
  - the database pod's egress is deliberately **not restricted**: its instance manager talks to the Kubernetes API and the Barman sidecar to Backblaze, and blocking them by mistake would silently break backups. **[todo]** tighten later, with a restore test.
  - no access between namespaces of different projects **[todo]** (a per-namespace default deny is to be added with each new project).
- **Pod environment trap.** Kubernetes injects `<SERVICE>_PORT=tcp://ip:port` variables for every Service of the namespace. The Service named `api` produced `API_PORT`, which Gotenberg reads as its listening port and refused to start. All Deployments set `enableServiceLinks: false`: keep it on every new workload.
- **Secrets: Sealed Secrets** (chart 2.19.1, controller `sealed-secrets-controller` in `kube-system`). Secrets are encrypted with `kubeseal --cert pub-cert.pem` and versioned as `SealedSecret`s in `homelab-deploy`; only the controller decrypts them and ArgoCD applies them like any manifest. A SealedSecret is bound to its name and namespace.
  - The controller's key does not rotate automatically (`keyrenewperiod: "0"`): there is a single key to protect.
  - **The controller's private key is backed up outside the cluster**: exported as `main.key`, encrypted with `age` (passphrase in the password manager), stored on Drive with a second copy. Without it, reinstalling the cluster makes every secret unreadable.
  - Rotation: after a leak, change the secret at the source (Google, B2...) then reseal; resealing alone is not enough, the old ciphertext stays in Git history.
  - Never a plaintext secret in Git, in a CI variable or in a committed `.env` file. The only secret stored in GitHub is the CI deploy key (section 9).
  - Existing SealedSecrets: `tunnel-token` (cloudflared), `b2-credentials` and `kitchen-api` (Google client id and secret, OAuth signing key) in the `kitchen` namespace.
- **ArgoCD** is reached with an SSH tunnel and a port-forward, never exposed: `ssh -t -L 8443:localhost:8443 homelab 'KUBECONFIG=~/.kube/config kubectl port-forward svc/argocd-server -n argocd 8443:443'`, then `https://localhost:8443`.
- **App of apps**: the `root` Application watches `argocd/`. To make ArgoCD read a change of an Application definition, refresh `root`; for a change under `prod/kitchen`, refresh `kitchen` (`kubectl annotate application <name> -n argocd argocd.argoproj.io/refresh=hard --overwrite`).

## 5. Kitchen applications

| Component | Image | Resources (requests / limits) |
|---|---|---|
| `api` | `ghcr.io/jucollet/kitchen-api:<commit SHA>`, built from `apps/api/Dockerfile` (3 stages: MCP widget built with Node, Maven build with Java 25, runtime on `eclipse-temurin:25-jre-noble`), user 10001 | 100m CPU, 512 Mi / 1 Gi; `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75` |
| `web` | `ghcr.io/jucollet/kitchen-web:<commit SHA>`, built from `apps/web/Dockerfile` (Vite build, then `nginx-unprivileged`, port 8080, `/healthz`) | 10m, 32 Mi / 128 Mi |
| `gotenberg` | `gotenberg/gotenberg:8.39.0`, same flags as `apps/api/compose.yaml` (allow-list `^file:///tmp/.*`, deny public and private IPs, JavaScript disabled, download-from, webhook, LibreOffice and PDF engine routes disabled, no Prometheus) | 50m, 256 Mi / 1 Gi |

- Images are public (the repository is public), so **no pull secret** is needed. They are tagged with the **full commit SHA**, never `latest`; the SHA is written inline in `prod/kitchen/api.yaml` and `web.yaml`.
- The API gets its configuration from environment variables: database URL (`kitchen-db-rw`, credentials from the `kitchen-db-app` secret created by CloudNativePG), `KITCHEN_PUBLIC_URL=https://kitchen.julco.dev`, `KITCHEN_GOTENBERG_URL=http://gotenberg:3000`, the `kitchen-api` secret (all of it), and `KITCHEN_MCP_ALLOWED_ORIGINS=https://claude.ai` (**[to verify]** whether it is needed, an MCP client from a server does not send an `Origin`).
- `liveness`, `readiness` and `startup` probes on `/actuator/health/liveness` and `/actuator/health/readiness` for the API; `/healthz` for the web; `/health` for Gotenberg.
- Non-root containers, `readOnlyRootFilesystem` with an `emptyDir` on `/tmp`, all capabilities dropped, `allowPrivilegeEscalation: false`, `RuntimeDefault` seccomp. Gotenberg needs an explicit `runAsUser: 1001` (Kubernetes cannot check a non-numeric image user) and the `gotenberg` command as the **first argument** (the image entrypoint is `tini`, passing only flags makes it try to execute a flag).
- Ingress (`prod/kitchen/ingress.yaml`, Traefik): `/mcp`, `/oauth2`, `/.well-known`, `/api`, `/login`, `/logout`, `/webauthn`, `/exports`, `/actuator/health` → `api`; everything else → `web`. Only `/actuator/health` is routed (for the external probe): the other Actuator endpoints are never exposed.

## 6. PostgreSQL

- **CloudNativePG** (operator chart 0.29.1 in `cnpg-system`), not a hand-made StatefulSet. **One `Cluster` per project, inside the project's namespace**: `kitchen-db` in `kitchen`, one instance, PostgreSQL 18 (`ghcr.io/cloudnative-pg/postgresql:18-standard-trixie`), database and owner `kitchen`, 5 Gi on the SSD (k3s local-path). Isolation between projects is therefore by namespace (and NetworkPolicy) rather than by roles in a shared instance; the price is some extra memory per project.
- The operator creates the `kitchen-db-app` secret (username, password, URIs) and the `kitchen-db-rw`, `-ro` and `-r` Services.
- The `Cluster` carries the annotation `argocd.argoproj.io/sync-options: Prune=false`, so that ArgoCD never deletes the database if a manifest disappears from Git.
- **Extensions**: Kitchen's migrations create `unaccent` (V6, accent-insensitive recipe search). It works with the `standard` image, and the `kitchen` role only needs to own its database.
- Flyway migrations run when the API starts.

## 7. Backups

- Done with the **Barman Cloud plugin** (installed in `cnpg-system`, needs cert-manager): continuous WAL archiving plus a **daily base backup at 03:00 UTC** (`ScheduledBackup` `kitchen-db-daily`), sent to the B2 bucket `kitchen-db` (EU region, endpoint `s3.eu-central-003.backblazeb2.com`), private, with an application key limited to this bucket. Retention: **30 days** (`retentionPolicy`), to be stated in the privacy policy.
- Backblaze applies **server-side encryption** at rest.
- **Restore test done on 2026-11-18**: a new cluster built from the backup (`bootstrap.recovery`) contained the test row written before the backup. A restore always creates a **new** cluster, it never overwrites the old one. **[todo]** repeat every quarter, and before opening to more people.
- **Known debt: no client-side encryption yet.** The plan requires it, and allergies are health data. Today anyone obtaining the B2 key could read the backups in clear. Planned fix: a nightly `CronJob` running `pg_dump`, encrypted with `age` (public key in the cluster, private key kept outside, like `main.key`), written to a separate folder of the bucket, next to the CNPG backups. Until then, Kitchen must stay limited to people who know each other. **[todo]**
- A B2 lifecycle rule "keep only the last version" is recommended.
- Also back up: the manifests (in Git) and the keys needed to decrypt secrets (outside the cluster, in the password manager).
- Check after any change touching the database pod or its network: a `kitchen-db-daily-...` backup must reach `completed` the next morning.

## 8. Cloudflare

- Remotely managed tunnel authenticated by a token (SealedSecret `tunnel-token`), `cloudflared` deployed in the `cloudflared` namespace, one public route per project: `kitchen.julco.dev` → `http://traefik.kube-system.svc.cluster.local:80`.
- **[todo]** SSL Full, HSTS and WAF rate-limiting rules on `/mcp`, `/oauth2` and `/exports`.
- **[todo]** DNS, tunnel and WAF rules to be declared in Terraform instead of configured by hand in the dashboard.
- The Google OAuth client for production (`Kitchen prod`) has the redirect URI `https://kitchen.julco.dev/login/oauth2/code/google`. Check the consent screen state (Testing only allows the listed test users).

## 9. Deployment pipeline (GitOps)

1. Push to `main` (through a pull request) → **GitHub Actions**: backend and front-end tests, then the `images` job builds the two images and publishes them to GitHub Container Registry, tag = full commit SHA, using the workflow's own `GITHUB_TOKEN`.
2. The `deploy` job then checks out `homelab-deploy` with a **deploy key** (SSH, write access to that single repository, stored as the secret `HOMELAB_DEPLOY_KEY`), replaces the SHA in `prod/kitchen/api.yaml` and `web.yaml` with `sed`, checks the replacement, commits "Deploy kitchen <short SHA>" as `kitchen-ci` and pushes to `main`. Jobs are queued (`concurrency`) so that two merges cannot race. Kustomize is not used.
3. **ArgoCD** detects the commit (polling about every 3 minutes, or a hard refresh) and syncs automatically, with prune and self-heal.
4. k3s starts the new pods, waits for readiness, switches traffic.

Nobody logs into the server to deploy: Git is the source of truth.

Security notes:
- The deploy key can write to `homelab-deploy`, which defines the whole cluster. It is limited to that repository, and secrets are never given to pull requests from forks; the job runs only on a push to `main`. **Protect `main` of `kitchen-ai` (pull request required)** so that only reviewed code can trigger a deployment.
- `homelab-deploy` has a ruleset on `main` (the default branch). The bypass list contains **Deploy keys** and **Repository admin**, both set to "Always allow", so the CI and the owner can push directly while any other actor needs a pull request.
- Rollback: revert the `Deploy kitchen ...` commit in `homelab-deploy`.

## 10. Minimal monitoring

- **[todo]** External probe on `https://kitchen.julco.dev/actuator/health` with alerting.
- **[todo]** Later: Prometheus and Grafana (plan for about 1 GB of RAM).

## 11. Open items

| Item | Why it matters |
|---|---|
| Client-side encrypted backups (section 7) | Allergies are health data; server-side encryption alone does not cover a leaked B2 key. Do it before opening Kitchen beyond a small circle |
| Verify the first nightly backup | Proof that backups run with the NetworkPolicies in place |
| Cloudflare HSTS and WAF rate limiting (section 8) | Protects `/mcp`, `/oauth2` and `/exports` from abuse |
| External health probe and alerting | Today nobody is warned if the site is down |
| Terraform for B2 and Cloudflare, Ansible for the machine and the firewall | Make the whole setup reproducible |
| `ResourceQuota`s and a per-namespace default deny for new projects | Keep projects isolated from each other |
| Firewall for IPv6 | The nftables rules only cover IPv4 |
| Quarterly restore test | A backup that was never restored is only a hope |
| Review of `KITCHEN_MCP_ALLOWED_ORIGINS` and of the database egress policy | Both were set conservatively and not yet tightened |
