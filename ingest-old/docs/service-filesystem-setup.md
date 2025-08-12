
# Filesystem Setup

The service expects the following filesystem structure:

```ascii 
 {asset_directory} 
  ├── {project_folder} 
  |    └──{asset_segment_1}
  |        └── {asset_segment_2}
  |              ├── xxx.info
  |              ├── xxx.jpg.orig
  |              └── xxx.jp2
  ├── {project_folder} 
  …    └──{asset_segment_1}
           └── {asset_segment_2}
                 ├── xxx.info
                 ├── xxx.jpg.orig
                 └── xxx.jp2               
 {temp_directory}
  ├── export
  └── import
```

Here is a description of the folders:

* `{asset_directory}`: the folder where the assets are stored
* `{project_folder}`: each project has its own folder containing the assets belonging to that project, the name of the
  folder
  is the project's shortcode
* `{asset_segment_1}`: the first 2 characters of the `internal_filename` of an asset
* `{asset_segment_2}`: the next 2 characters of the `internal_filename` of an asset
* `{temp_directory}`: the folder where temporary files are stored
* `{temp_directory}/export`: the folder where exported files are temporarily stored
* `{temp_directory}/import`: the folder where imported files are temporarily stored

Files are stored in the `{asset_directory}/{project_folder}/{asset_segment_1}/{asset_segment_2}` folder,
`{asset_directory}/0123/ab/cd/abcdefg.jp2` for example.

Each asset is stored in a file named `{internal_filename}` which consists of two parts `{asset_id}.{ext}`.
The `{asset_id}` is a unique identifier for the asset and the `{ext}` is the file extension.
Every derivative and metadata file of an asset are stored in files starting with `{asset_id}` and the respective `{ext}`.

For each asset there are different files in the `{asset_segment_2}` folder:

* `{internal_filename}`: (mandatory) the asset, `{asset_id}.jp2` for images, `{asset_id}.mp4` for videos, etc.
* `{asset_id}.info`: (mandatory) the metadata of the asset
* `{asset_id}.{ext}.orig`: the original file of the asset if it was an image and has been converted to a
  [JPEG 2000](https://jpeg.org/jpeg2000/)
