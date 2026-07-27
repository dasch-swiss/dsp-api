# Grafana Dashboards (Git Sync)

Selected Grafana dashboards for the DaSCH Ops Platform, kept under version control and synced into Grafana Cloud via **Git Sync** (native provisioning).

## How it's wired

- **Source of truth:** this folder. Grafana Cloud pulls from it.
- **Mode:** **read-only** — dashboards synced from here are locked in the Grafana UI. Change them by editing the JSON here and pushing to `main`, not in Grafana.
- **Placement:** **folderless** — each subdirectory below becomes a **top-level folder** in Grafana (no wrapper folder). `claude/` → the "Claude Dashboards" folder.
- **Scope:** only files under this path (`grafana-dashboards/`) are synced; the rest of the monorepo is ignored.

## Layout convention

```
grafana-dashboards/
└── <category>/
    ├── _folder.json      # sets the Grafana folder's display name
    └── <dashboard>.json  # one file per dashboard; title comes from the JSON, not the filename
```

`_folder.json` decouples the Grafana folder name from the directory name:

```json
{
  "apiVersion": "folder.grafana.app/v1",
  "kind": "Folder",
  "metadata": { "name": "<stable-uid>" },
  "spec": { "title": "<Display Name>" }
}
```

### Adding a category

Create `grafana-dashboards/<name>/`, add a `_folder.json` with the display title, drop the dashboard JSON files in, and push. Grafana creates the top-level folder on the next sync.

## Provisioning config (applied once, Grafana-side)

These resources live in Grafana Cloud (namespace `stacks-726087`), not in this repo — secrets must not be committed. Kept here for reference.

**Connection** (GitHub App):

```yaml
apiVersion: provisioning.grafana.app/v0alpha1
kind: Connection
metadata:
  name: dasch-grafana-git-sync
  namespace: stacks-726087
spec:
  title: dasch-grafana-git-sync
  type: github
  url: https://github.com
  github:
    appID: '<GITHUB_APP_ID>'
    installationID: '<GITHUB_INSTALL_ID>'
secure:
  privateKey:
    create: '<GITHUB_APP_PRIVATE_KEY>'
```

**Repository**:

```yaml
apiVersion: provisioning.grafana.app/v0alpha1
kind: Repository
metadata:
  name: dasch-ops-dashboards
  namespace: stacks-726087
spec:
  title: DaSCH Ops Dashboards
  type: github
  # No `workflows` → read-only (Grafana UI cannot write back to Git).
  sync:
    enabled: true
    target: folderless
    intervalSeconds: 60
  github:
    url: https://github.com/dasch-swiss/dasch-ops-platform
    branch: main
    path: grafana-dashboards
  connection:
    name: dasch-grafana-git-sync
```
