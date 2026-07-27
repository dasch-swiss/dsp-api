# Grafana Dashboards (Git Sync)

Selected Grafana dashboards for DSP, kept under version control in `dsp-api` and synced into
Grafana Cloud via **Git Sync** (native provisioning).

## How it's wired

- **Source of truth:** this folder. Grafana Cloud pulls from it.
- **Mode:** **read-only** — dashboards synced from here are locked in the Grafana UI. Change them by editing the JSON here and pushing to `main`, not in Grafana.
- **Placement:** **folderless** — each subdirectory below becomes a **top-level folder** in Grafana (no wrapper folder). `sipi/` → the "SIPI" folder.
- **Scope:** only files under this path (`grafana-dashboards/`) are synced; the rest of the monorepo is ignored.

## Layout convention

```text
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

Dashboards use the **v2 dashboard schema** (`dashboard.grafana.app/v2`): panels live under
`spec.elements` (keyed by name), are arranged by `spec.layout`, and template variables under
`spec.variables`. `metadata.name` is the dashboard **UID** and must stay stable. Datasources
are referenced by **UID** in `datasource.name` (e.g. `grafanacloud-prom`).

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
  name: dsp-api-dashboards
  namespace: stacks-726087
spec:
  title: DSP-API Dashboards
  type: github
  # No `workflows` → read-only (Grafana UI cannot write back to Git).
  sync:
    enabled: true
    target: folderless
    intervalSeconds: 60
  github:
    url: https://github.com/dasch-swiss/dsp-api
    branch: main
    path: grafana-dashboards
  connection:
    name: dasch-grafana-git-sync
```
