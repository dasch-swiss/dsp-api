# Removing Obsolete Preview Images

With [PR #1211](https://github.com/dhlab-basel/Knora/pull/1211), Knora stores `xsd:dateTime`
in the triplestore instead of `xsd:dateTimeStamp`. The program `update-dates.py` in this directory
changes your existing data and ontologies accordingly.

You will need HTTP access to the triplestore.

Prerequisites on your local machine:

- Python 3 (`python3 --version` should print its version number)
- The Python `requests` library (`pip3 install requests`)

## Instructions

1. Stop Knora. Make sure GraphDB is running.
2. Back up the contents of the triplestore.
3. Use `./update-dates.py` in this directory to update your repository. You will need to specify some
   command-line options; type `./update-dates.py --help` for details.
4. Upgrade to the latest version of Knora.
5. Restart Knora.
