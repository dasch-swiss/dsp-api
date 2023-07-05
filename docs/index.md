# Dsp-Ingest

The Dsp-Ingest service is designed to streamline and optimize the process of ingesting and managing data within the
DaSCH Service Platform.

It provides a RESTful [API](api-endpoints-projects.md) for the digital asset management.

# Assets

Assets are the core of the Dsp-Ingest service. They can be different sort of files, like: images, videos, audios, etc.
For each asset, the service stores the metadata, its original file and possibly derivative files such as keyframes for
videos.

Assets are identified by a unique identifier, the `internal_filename`, eg. `100W6vpQQtG-Fk81LUxUheF.jp2`.

## Types of Assets

The service supports different types of assets:

* Images
* Videos
* Audio files
* Excel files
* PDF files
* And others in binary format

The supported file formats are explained in detail in the [DSP-API documentation](https://docs.dasch.swiss/2023.06.02/DSP-API/01-introduction/file-formats/).

# Projects

Assets are group within projects. A project is a collection of assets and corresponds to a project in the DSP-API.
 