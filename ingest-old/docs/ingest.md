# Ingesting Assets

Every [asset](index.md#assets) belongs to a project. 

## _Bulk Ingest_

### Preparation
The assets must be in [`STORAGE_TEMP_DIR/import/{shortcode}`](service-configuration.md#storage), a folder named after the Project Shortcode.
The assets may be organized in sub-folders inside the `import/{shortcode}` folder.
For example:
```
import/
    └── 0001/ 
        ├── someImage.jp2
        └── some-sub-folder/
            ├── someText.txt
            …
```

### Triggering a _Bulk Ingest_
You can trigger a _Bulk Ingest_ with a request to the [_Bulk Ingest_ Endpoint](api-endpoints-projects.md#post-projectsshortcodebulk-ingest). 
The _Bulk Ingest_ will run in the background, and you can check its status in the logs of the service.

The _Bulk Ingest_ will create a new asset for each valid file in the `import/{shortcode}/` folder, and move the file to the [`STORAGE_ASSET_DIR/{shortcode}`](service-configuration.md#storage) folder.
It will create relevant derivatives for the asset, such as keyframes for videos or and archival version of images.
It will extract the metadata from the file and store it with the asset.
Once a file was successfully ingested, it will be deleted from the `import/{shortcode}/` folder.
Files that are not valid or could not be ingested will be ignored and remain in the `import/{shortcode}/` folder unchanged.

Additionally, the _Bulk Ingest_ will create a `mapping-{shortcode}.csv` [CSV](https://www.rfc-editor.org/rfc/rfc4180) file in the [`{STORAGE_TEMP_DIR}/import`](service-configuration.md#storage) folder. 
This file contains a mapping of the original filenames to the `internal_filename` of the assets.

```csv
original,derivative
someImage.jp2,4lNd38JEiHC-lofT60RQbYm.jp2
some-sub-folder/someText.txt,2hWjjyahcMM-Kkkm8qJpSGk.txt
```

You can use this mapping to create the respective resource in the DSP-API.

### Finalizing a _Bulk Ingest_
Once you have verified the success of the ingest and/or created the resources in the DSP-API, 
you should finalize the _Bulk Ingest_ with a request to the [_Bulk Ingest Finalize_ Endpoint](api-endpoints-projects.md#post-projectsshortcodebulk-ingestfinalize).
The _Bulk Ingest_ will then delete the `mapping-{shortcode}.csv` file and the `import/{shortcode}/` folder and all its content.
