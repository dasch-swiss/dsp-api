# GraphDB

The latest version of **GraphDB-Free** and **GraphDB-SE** supported by Knora is **8.5.0**.

**GraphDB-Free** is the Free Edition of the triplestore from Ontotext (http://ontotext.com). GraphDB-Free must be licensed
separately by the user, by registering with Ontotext, i.e. filling out the form for downloading the free edition.

**GraphDB-SE** is the Standard Edition of the triplestore from Ontotext (http://ontotext.com). GraphDB-SE must be licensed separately by the user.


## Usage for GraphDB-Free

Download distribution from Ontotext. Unzip ``graphdb-free-x.x.x-dist.zip`` to a place of your choosing and run the following:

```
$ cd /to/unziped/location
$ ./bin/graphdb
```

## Usage for GraphDB-SE

Download distribution from Ontotext. Unzip ``graphdb-se-x.x.x-dist.zip`` to a place of your choosing and run the following:

```
$ cd /to/unziped/location
$ ./bin/graphdb -Dgraphdb.license.file=/path/to/GRAPHDB_SE.license