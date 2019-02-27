# Change xsd:dateTimeStamp to xsd:dateTime

With [PR #1211](https://github.com/dhlab-basel/Knora/pull/1211), Knora stores
`xsd:dateTime` in the triplestore instead of `xsd:dateTimeStamp`. The program
`update-dates.py` in this directory changes your existing data and ontologies
accordingly.

## Instructions

Use `./update-dates.py` in this directory to update your repository. You will
need to specify some command-line options; type `./update-dates.py --help` for
details.
