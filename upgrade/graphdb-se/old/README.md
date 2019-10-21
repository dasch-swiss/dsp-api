# Upgrading from a Knora Version Before 7.0.0

If you are upgrading from a version of Knora before 7.0.0, you will need
the scripts in `old`. See "Updating Repositories When Upgrading Knora"
in the Knora documentation for details on which scripts to run.

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

Follow the the instructions in the `README` in the subdirectory containing
each script you need to run.

### After Updating Repositories

1. Upgrade Knora to the latest version.

2. Restart Knora and Sipi.
