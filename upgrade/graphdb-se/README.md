# Helper scripts for updating GraphDB repositories

This directory contains shell scripts for downloading a GraphDB repository
to a TriG file, emptying the repository, and uploading a TriG file
to GraphDB.

You will need to have [curl](https://curl.haxx.se) installed.

## Dumping a GraphDB repository to a TriG file

```
./dump-repository.sh -r|--repository REPOSITORY -u|--username USERNAME [-p|--password PASSWORD] [-h|--host HOST] FILE
```

- `-r`: the name of the repository.
- `-u`: the GraphDB username.
- `-p`: the GraphDB password. If not supplied, you will be prompted for it.
- `-h`: the GraphDB host and port. Defaults to `localhost:7200`.
- `FILE`: the file path of the TriG file to be written.

## Emptying a GraphDB repository

This empties a GraphDB repository by running a SPARQL `DROP ALL` command. Make sure you
have backed up the repository before you do this.

```
./empty-repository.sh -r|--repository REPOSITORY -u|--username USERNAME [-p|--password PASSWORD] [-h|--host HOST]
```

- `-r`: the name of the repository.
- `-u`: the GraphDB username.
- `-p`: the GraphDB password. If not supplied, you will be prompted for it.
- `-h`: the GraphDB host and port. Defaults to `localhost:7200`.

## Uploading a TriG file to a GraphDB repository

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
