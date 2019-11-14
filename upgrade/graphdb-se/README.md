# Scripts for Upgrading GraphDB Repositories

This directory contains shell scripts for updating a GraphDB repository
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
- `-u`: the GraphDB username.
- `-p`: the GraphDB password. If not supplied, you will be prompted for it.
- `-h`: the GraphDB host and port. Defaults to `localhost:7200`.

If the environment variable `KNORA_UPGRADE_DOCKER` is set, this script runs the
upgrade program using the executable `/upgrade/bin/upgrade`. Otherwise, it runs
the program using SBT.

## Scripts for Manual Upgrades

These scripts are useful if you want more control over the upgrade process.

### Dumping a GraphDB Repository to a TriG File

```
./dump-repository.sh -r|--repository REPOSITORY -u|--username USERNAME [-p|--password PASSWORD] [-h|--host HOST] FILE
```

- `-r`: the name of the repository.
- `-u`: the GraphDB username.
- `-p`: the GraphDB password. If not supplied, you will be prompted for it.
- `-h`: the GraphDB host and port. Defaults to `localhost:7200`.
- `FILE`: the file path of the TriG file to be written.

### Emptying a GraphDB Repository

This empties a GraphDB repository by running a SPARQL `DROP ALL` command. Make sure you
have backed up the repository before you do this.

```
./empty-repository.sh -r|--repository REPOSITORY -u|--username USERNAME [-p|--password PASSWORD] [-h|--host HOST]
```

- `-r`: the name of the repository.
- `-u`: the GraphDB username.
- `-p`: the GraphDB password. If not supplied, you will be prompted for it.
- `-h`: the GraphDB host and port. Defaults to `localhost:7200`.

### Uploading a TriG File to a GraphDB Repository

This uploads the TriG file to GraphDB over HTTP.

```
./upload-repository.sh -r|--repository REPOSITORY -u|--username USERNAME [-p|--password PASSWORD] [-h|--host HOST] FILE
```

- `-r`: the name of the repository.
- `-u`: the GraphDB username.
- `-p`: the GraphDB password. If not supplied, you will be prompted for it.
- `-h`: the GraphDB host and port. Defaults to `localhost:7200`.
- `FILE`: the file path of the TriG file to be uploaded.

If the file is very large, it may be more efficient to load it offline,
using GraphDB's [LoadRDF](http://graphdb.ontotext.com/documentation/free/loading-data-using-the-loadrdf-tool.html)
tool.
