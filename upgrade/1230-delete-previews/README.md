# Remove Obsolete Preview Images

With [PR #1230](https://github.com/dhlab-basel/Knora/pull/1230), preview images
are no longer pre-generated for Knora API v1. Existing preview image file values
must be deleted from the triplestore, the `knora-base` ontology must be updated,
and preview images should also be deleted from Sipi. The programs in this
directory will help you do this:

* `get-preview-paths.py` gets the paths of all the preview image files in Sipi,
  so you can delete those files from the filesystem.
  
* `delete-preview-values.py` deletes all preview image values from the
  triplestore, and updates the `knora-base` ontology.

## Instructions

1. Use `./get-preview-paths.py` in this directory to download the paths of all
   the preview files from the triplestore. You will need to specify some
   command-line options; type `./get-preview-paths.py --help` for details. The
   result is a text file, `preview-paths.txt`, containing the relative paths of all
   the files to be deleted from Sipi. The paths are relative to Sipi's `imgroot`
   directory.
   
2. Move the file `preview-paths.txt` to Sipi's `imgroot` directory, and delete
   the preview files by typing `xargs rm -f < preview-paths.txt` in that directory.

3. Delete `preview-paths.txt`.

4. Use `./delete-preview-values.py` in this directory to update the triplestore.
   You will need to specify some command-line options; type
   `./delete-preview-values.py --help` for details.
