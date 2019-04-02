# Separate the knora-admin ontology from knora-base

With [PR #1263](https://github.com/dhlab-basel/Knora/pull/1263), the `knora-admin`
ontology has been separated from the `knora-base` ontology. The program
`update-knora-admin.py` in this directory changes your existing data and ontologies
accordingly.

## Instructions

First, back up your repository. Then run `./update-knora-admin.py` in this directory to
update the repository. You will need to specify some command-line options; type
`./update-knora-admin.py --help` for details. 
