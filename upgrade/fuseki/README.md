# Scripts for Upgrading Fuseki Repositories

This directory contains shell scripts for updating a Fuseki repository
to work with a new version of Knora.

You will need to have [curl](https://curl.haxx.se) installed.

## Automatic Upgrade

This dumps your repository to a file, determines which transformations are needed,
transforms the file, then loads the transformed file back into the repository.
Make sure you have backed up the repository before you do this.

```
./auto-upgrade.sh -r|--repository REPOSITORY -u|--username USERNAME [-p|--password PASSWORD] [-h|--host HOST]
```

- `-r`: the name of the repository.
- `-u`: the Fuseki username.
- `-p`: the Fuseki password. If not supplied, you will be prompted for it.
- `-h`: the Fuseki host and port. Defaults to `localhost:8080`.

If the environment variable `KNORA_UPGRADE_DOCKER` is set, this script runs the
upgrade program using the executable `/upgrade/bin/upgrade`. Otherwise, it runs
the program using SBT.

## Scripts for Manual Upgrades

These scripts are useful if you want more control over the upgrade process.

### Dumping a Fuseki Repository to a TriG File

```
./dump-repository.sh -r|--repository REPOSITORY -u|--username USERNAME [-p|--password PASSWORD] [-h|--host HOST] FILE
```

- `-r`: the name of the repository.
- `-u`: the Fuseki username.
- `-p`: the Fuseki password. If not supplied, you will be prompted for it.
- `-h`: the Fuseki host and port. Defaults to `localhost:8080`.
- `FILE`: the file path of the TriG file to be written.

### Emptying a Fuseki Repository

This empties a Fuseki repository by running a SPARQL `DROP ALL` command. Make sure you
have backed up the repository before you do this.

```
./empty-repository.sh -r|--repository REPOSITORY -u|--username USERNAME [-p|--password PASSWORD] [-h|--host HOST]
```

- `-r`: the name of the repository.
- `-u`: the Fuseki username.
- `-p`: the Fuseki password. If not supplied, you will be prompted for it.
- `-h`: the Fuseki host and port. Defaults to `localhost:8080`.

### Uploading a TriG File to a Fuseki Repository

This uploads the TriG file to Fuseki over HTTP.

```
./upload-repository.sh -r|--repository REPOSITORY -u|--username USERNAME [-p|--password PASSWORD] [-h|--host HOST] FILE
```

- `-r`: the name of the repository.
- `-u`: the Fuseki username.
- `-p`: the Fuseki password. If not supplied, you will be prompted for it.
- `-h`: the Fuseki host and port. Defaults to `localhost:8080`.
- `FILE`: the file path of the TriG file to be uploaded.
