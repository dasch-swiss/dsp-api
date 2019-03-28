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
from rdflib.namespace import RDF


class UpdateException(Exception):
    def __init__(self, message):
        self.message = message


# The property types used in knora-admin.
property_types = {
    "http://www.w3.org/2002/07/owl#ObjectProperty",
    "http://www.w3.org/2002/07/owl#DatatypeProperty",
    "http://www.w3.org/1999/02/22-rdf-syntax-ns#Property"
}

knora_base_context = "http://www.knora.org/ontology/knora-base"
knora_base_namespace = knora_base_context + "#"
knora_admin_context = "http://www.knora.org/ontology/knora-admin"
knora_admin_namespace = knora_admin_context + "#"

knora_admin_properties = []
knora_admin_objects = []


def split_namespace_and_local_name(entity):
    entity_str = str(entity)
    hash_pos = entity_str.rfind("#")

    if hash_pos != -1:
        return entity_str[:hash_pos], entity_str[hash_pos + 1:len(entity_str)]
    else:
        return entity_str, None


# Read knora-base and knora-admin.
def get_knora_admin_info():
    # Read knora-admin.
    knora_admin_graph = rdflib.Graph()
    knora_admin_graph.parse("../../knora-ontologies/knora-admin.ttl", format="turtle")

    # Iterate over all the statements in knora-admin.
    for subject, predicate, obj in knora_admin_graph:
        # Is this a statement about an ontology entity?
        subj_namespace, subj_name = split_namespace_and_local_name(subject)

        if subj_name is not None:
            # Is the predicate rdf:type?
            if predicate == RDF.type:
                # Yes. Is the object a property type?
                if str(obj) in property_types:
                    # Yes. Collect the subject as a property IRI.
                    knora_admin_properties.append(subj_name)
                elif subject.__class__.__name__ == "URIRef":
                    # The object isn't a property type, and the subject is an IRI (not a blank node).
                    # Collect the subject as a non-property IRI that can be used as an object in data.
                    knora_admin_objects.append(subj_name)


def download_repository(graphdb_url, username, password):
    contexts_response = requests.get(graphdb_url + "/contexts", params={"infer": "false"})
    contexts_response_list = contexts_response.text.splitlines()

    if contexts_response_list[0] != "contextID":
        raise UpdateException("Unexpected response from GraphDB: " + contexts_response.text)

    contexts = contexts_response_list.pop(0)

    for context in contexts:
        print(context)


def transform_entity(entity, local_name_list):
    if entity.__class__.__name__ == "URIRef":
        namespace, local_name = split_namespace_and_local_name(entity)

        if namespace == knora_base_namespace and local_name in local_name_list:
            return rdflib.URIRef(knora_admin_namespace + local_name)
        else:
            return entity
    else:
        return entity


def update_repository(graphdb_url, username, password):
    download_repository(
        graphdb_url=graphdb_url,
        username=args.username,
        password=password
    )


# Command-line invocation.
def main():
    default_graphdb_host = "localhost"
    defaut_repository = "knora-test"

    parser = argparse.ArgumentParser(description="Separates knora-admin from knora-base.")
    parser.add_argument("-g", "--graphdb", help="GraphDB host (default '{}')".format(default_graphdb_host), type=str)
    parser.add_argument("-r", "--repository", help="GraphDB repository (default '{}')".format(defaut_repository),
                        type=str)
    parser.add_argument("-u", "--username", help="GraphDB username", type=str, required=True)
    parser.add_argument("-p", "--password", help="GraphDB password (if not provided, will prompt for password)",
                        type=str)

    args = parser.parse_args()
    graphdb_host = args.graphdb

    if not graphdb_host:
        graphdb_host = default_graphdb_host

    repository = args.repository

    if not repository:
        repository = defaut_repository

    graphdb_url = "http://{}:7200/repositories/{}".format(graphdb_host, repository)

    password = args.password

    if not password:
        password = getpass.getpass()

    update_repository(
        graphdb_url=graphdb_url,
        username=args.username,
        password=password
    )


if __name__ == "__main__":
    main()
