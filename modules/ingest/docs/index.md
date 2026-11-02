# Ingest

Ingest manages the digital assets of the DaSCH Service Platform. It is part of DSP-API, which serves its RESTful
[API](openapi.md) under the `/ingest/` path prefix.

# Assets

Assets are the core of ingest. They can be different sort of files, like: images, videos, audios, etc.
For each asset, ingest stores the metadata, its original file and possibly derivative files such as keyframes for
videos.

Assets are identified by a unique identifier, the `internal_filename`, eg. `100W6vpQQtG-Fk81LUxUheF.jp2`.

## Types of Assets

Ingest supports several different types of assets:

* Images
* Videos
* Audio files
* Excel files
* PDF files
* Ebooks (`.epub`)
* Html files (`.html`, `.htm`)
* And others in binary format

The supported file formats are explained in detail in the [DSP-API documentation](https://docs.dasch.swiss/DSP-API/01-introduction/file-formats/).

# Projects

Assets are group within projects. A project is a collection of assets and corresponds to a project in the DSP-API.
 