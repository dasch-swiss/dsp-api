# Removing Obsolete Preview Images

With [PR #1230](https://github.com/dhlab-basel/Knora/pull/1230), preview images are no longer pre-generated for
Knora API v1. Before you upgrade your Knora installation, existing preview image file values must be deleted from
the triplestore, the `knora-base` ontology must be updated, and preview images should also be deleted from Sipi.
The two programs in this directory will help you do this:

- `get-preview-paths.py` gets the paths of all the preview image files in Sipi, so you can delete those
  files from the filesystem.
- `delete-preview-values.py` deletes all preview image values from the triplestore, and updates
  the `knora-base` ontology.

You will need HTTP access to the triplestore, and shell access to the server that Sipi runs on.

Prerequisites on your local machine:

- Python 3 (`python3 --version` should print its version number)
- The Python `requests` library (`pip3 install requests`)

## Instructions

1. Stop both Knora and Sipi. Make sure GraphDB is running.
2. Back up the contents of the triplestore and the files in Sipi.
3. Use `./get-preview-paths.py` in this directory to download the paths of all the preview
   files from the triplestore. You will need to specify some command-line options;
   type `./get-preview-paths.py --help` for details. The result is a text file, `preview-paths.txt`,
   containing the relative paths of all the files to be deleted from Sipi. The paths are relative to
   Sipi's `imgroot` directory. You can then move this file to Sipi's `imgroot` directory, and delete
   the preview files by typing `xargs rm -f < preview-paths.txt` in that directory. Then delete
   `preview-paths.txt`.
4. Use `./delete-preview-values.py` in this directory to update the triplestore. You will need to specify some
   command-line options; type `./delete-preview-values.py --help` for details.
5. Upgrade to the latest version of Knora.
6. Restart Knora and Sipi.
