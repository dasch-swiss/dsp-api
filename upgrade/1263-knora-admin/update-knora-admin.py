#!/usr/bin/env python3

# Copyright @ 2015-2019 the contributors (see Contributors.md).
#
# This file is part of Knora.
#
# Knora is free software: you can redistribute it and/or modify
# it under the terms of the GNU Affero General Public License as published
# by the Free Software Foundation, either version 3 of the License, or
# (at your option) any later version.
#
# Knora is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU Affero General Public License for more details.
#
# You should have received a copy of the GNU Affero General Public
# License along with Knora.  If not, see <http://www.gnu.org/licenses/>.


#######################################################################################
# Separates knora-admin from knora-base.
#######################################################################################


import requests
import argparse
import getpass
import rdflib
import io
from string import Template


update_obj_template = """DELETE {
    GRAPH ?g {
        ?s ?p knora-base:$obj .
    }
} INSERT {
    GRAPH ?g {
        ?s ?p knora-admin:$obj .
    }
}
USING <http://www.ontotext.com/explicit>
WHERE
{
    GRAPH ?g {
        ?s ?p knora-base:$obj .
    }
}"""

update_pred_template = """DELETE {
    GRAPH ?g {
        ?s knora-base:$pred ?o .
    }
} INSERT {
    GRAPH ?g {
        ?s knora-admin:$pred ?o .
    }
}
USING <http://www.ontotext.com/explicit>
WHERE
{
    GRAPH ?g {
        ?s knora-base:$pred ?o .
    }
}"""


property_types = [
    "http://www.w3.org/2002/07/owl#ObjectProperty",
    "http://www.w3.org/2002/07/owl#DatatypeProperty",
    "http://www.w3.org/1999/02/22-rdf-syntax-ns#Property"
]


# Makes a request to GraphDB to update the repository.
def do_request(graphdb_url, username, password):
    knora_admin = knora_ontology_to_ntriples("knora-admin.ttl")
    knora_base = knora_ontology_to_ntriples("knora-base.ttl")

    with open("sparql/update-knora-admin.rq.tmpl", 'r') as sparql_template_file:
        sparql_template = Template(sparql_template_file.read())

        # TODO
        update_predicates = ""
        update_objects = ""

        template_dict = {
            "knoraAdmin": knora_admin,
            "knoraBase": knora_base,
            "updatePredicates": update_predicates,
            "updateObjects": update_objects
        }

        sparql = sparql_template.substitute(template_dict)

    data = {
        "update": sparql
    }

    r = requests.post(graphdb_url, data=data, auth=(username, password))
    r.raise_for_status()


# Reads an ontology from knora-ontologies and returns it in ntriples format.
def knora_ontology_to_ntriples(filename):
    graph = rdflib.Graph()
    graph.parse("../../knora-ontologies/{}".format(filename), format="turtle")
    nt_bytes = io.BytesIO()
    graph.serialize(nt_bytes, format="ntriples")
    return nt_bytes.getvalue().decode("UTF-8")


# Command-line invocation.
def main():
    default_graphdb_host = "localhost"
    defaut_repository = "knora-test"

    parser = argparse.ArgumentParser(description="Separates knora-admin from knora-base.")
    parser.add_argument("-g", "--graphdb", help="GraphDB host (default '{}')".format(default_graphdb_host), type=str)
    parser.add_argument("-r", "--repository", help="GraphDB repository (default '{}')".format(defaut_repository), type=str)
    parser.add_argument("-u", "--username", help="GraphDB username", type=str, required=True)
    parser.add_argument("-p", "--password", help="GraphDB password (if not provided, will prompt for password)", type=str)

    args = parser.parse_args()
    graphdb_host = args.graphdb

    if not graphdb_host:
        graphdb_host = default_graphdb_host

    repository = args.repository

    if not repository:
        repository = defaut_repository

    graphdb_url = "http://{}:7200/repositories/{}/statements".format(graphdb_host, repository)
    password = args.password

    if not password:
        password = getpass.getpass()

    do_request(
        graphdb_url=graphdb_url,
        username=args.username,
        password=password
    )


if __name__ == "__main__":
    main()
