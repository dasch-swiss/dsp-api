# Updating Repositories When Upgrading Knora

Here you will find tools for updating your Knora repositories when a Knora
upgrade introduces changes that are not backwards-compatible with existing data.

## Prerequisites

You will need HTTP access to the triplestore, and shell access to Sipi.

On your local computer, you will need the following to run the update
scripts:

- Python 3 (`python3 --version` should print its version number)

- The Python `requests` library (type `pip3 install requests` to install it)

- The Python `rdflib` library (type `pip3 install rdflib` to install it)

## General Instructions

### Before Updating Repositories

1. Stop Knora and Sipi. Leave the triplestore running.

2. Back up the data in the triplestore and the files in Sipi.

### Updating Repositories

The Knora release notes specify the directories containing the updates you need
to apply. Each directory's name begins with the number of the GitHub pull request
that introduced the change. The updates must be applied in numerical order.
Follow the instructions in each directory to apply the update.

### After Updating Repositories

1. Upgrade Knora to the latest version.

2. Restart Knora and Sipi.
