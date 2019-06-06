# Make values citable

With [PR #1322](https://github.com/dhlab-basel/Knora/pull/1322), values can be cited
using ARK URLs. To support this, the `knora-base` ontology and existing values must be
updated. The program `update-values.py` in this directory updates your repository
accordingly.

## Instructions

First, back up your repository. Then run `./update-values.py` in this directory
to update the triplestore. You will need to specify some command-line options; type
`./update-values.py --help` for details.
