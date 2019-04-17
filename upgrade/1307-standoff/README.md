# Add knora-base:valueHasMaxStandoffStartIndex

With [PR #1307](https://github.com/dhlab-basel/Knora/pull/1307), standoff markup
can be queried separately from text values. To support this, the `knora-base`
ontology must be updated, and the property `knora-base:valueHasMaxStandoffStartIndex`
must be added to all text values that have standoff markup. The program
`update-standoff.py` in this directory updates your repository accordingly.

## Instructions

First, back up your repository. Then run `./update-standoff.py` in this directory
to update the triplestore. You will need to specify some command-line options; type
`./update-standoff.py --help` for details.
