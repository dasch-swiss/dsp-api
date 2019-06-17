# Updating Repositories When Upgrading Knora

The command-line program `update-repository.py`, located in this directory,
updates a Knora repository when a Knora upgrade introduces changes that are not
backwards-compatible with existing data. The program checks your repository
and applies any necessary changes.

## Prerequisites

You will need HTTP access to the triplestore.

On your local computer, you will need the following to run the update
scripts:

- Python 3 (`python3 --version` should print its version number)

- The libraries in `requirements.txt` (use `pip3 install -r requirements.txt` to install them)

## General Instructions

### Before Updating Repositories

1. Stop Knora and Sipi. Leave the triplestore running.

2. Back up the data in the triplestore.

### Updating Repositories

Run `./update-repository.py` in this directory. You will need to provide some
command-line options; type `./update-repository.py --help` for instructions.

### After Updating Repositories

1. Upgrade Knora to the latest version.

2. Restart Knora and Sipi.
