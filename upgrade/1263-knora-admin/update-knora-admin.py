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
import io
from string import Template

# The property types used in knora-admin.
property_types = {
    "http://www.w3.org/2002/07/owl#ObjectProperty",
    "http://www.w3.org/2002/07/owl#DatatypeProperty",
    "http://www.w3.org/1999/02/22-rdf-syntax-ns#Property"
}

pred_obj_pairs = [
    ("belongsToProject", "DefaultSharedOntologiesProject"),
    ("belongsToProject", "SystemProject"),
    ("currentproject", "DefaultSharedOntologiesProject"),
    ("currentproject", "SystemProject"),
    ("forGroup", "Creator"),
    ("forGroup", "KnownUser"),
    ("forGroup", "ProjectAdmin"),
    ("forGroup", "ProjectMember"),
    ("forGroup", "SystemAdmin"),
    ("forGroup", "UnknownUser"),
    ("forProject", "DefaultSharedOntologiesProject"),
    ("forProject", "SystemProject"),
    ("isInGroup", "Creator"),
    ("isInGroup", "KnownUser"),
    ("isInGroup", "ProjectAdmin"),
    ("isInGroup", "ProjectMember"),
    ("isInGroup", "SystemAdmin"),
    ("isInGroup", "UnknownUser"),
    ("isInProject", "DefaultSharedOntologiesProject"),
    ("isInProject", "SystemProject"),
    ("isInProjectAdminGroup", "DefaultSharedOntologiesProject"),
    ("isInProjectAdminGroup", "SystemProject")
]


# Makes a request to GraphDB to update the repository.
def do_request(graphdb_url, username, password):
    # Read knora-admin.
    knora_admin_graph = rdflib.Graph()
    knora_admin_graph.parse("../../knora-ontologies/knora-admin.ttl", format="turtle")
    knora_admin_nt = knora_ontology_to_ntriples(knora_admin_graph)

    # Read knora-base.
    knora_base_graph = rdflib.Graph()
    knora_base_graph.parse("../../knora-ontologies/knora-base.ttl", format="turtle")
    knora_base_nt = knora_ontology_to_ntriples(knora_base_graph)

    # From knora-admin, collect the property IRIs, and the IRIs of non-properties that can be used as objects in data.

    knora_admin_properties = []
    knora_admin_objects = []

    # Iterate over all the statements in knora-admin.
    for subject, predicate, obj in knora_admin_graph:
        subject_str = str(subject)

        # Is this a statement about an ontology entity?

        hash_pos = subject_str.rfind("#")

        if hash_pos != -1:
            # Yes. Get the local name of the subject.
            subj_name = subject_str[hash_pos + 1:len(subject_str)]

            # Is the predicate is rdf:type?
            if predicate == RDF.type:
                # Yes. Is the object a property type?
                if str(obj) in property_types:
                    # Yes. Collect the subject as a property IRI.
                    knora_admin_properties.append(subj_name)
                elif subject.__class__.__name__ == "URIRef":
                    # The object isn't a property type, and the subject is an IRI (not a blank node).
                    # Collect the subject as a non-property IRI that can be used as an object in data.
                    knora_admin_objects.append(subj_name)

    # Use those IRIs to generate VALUES.

    predicates = list(map(lambda knora_admin_prop:
                          "(knora-base:{prop} knora-admin:{prop})".format(prop=knora_admin_prop),
                          knora_admin_properties))
    predicates_str = " ".join(predicates)

    objects = list(map(lambda knora_admin_obj:
                       "(knora-base:{obj} knora-admin:{obj})".format(obj=knora_admin_obj),
                       knora_admin_objects))
    objects_str = " ".join(objects)

    preds_with_objs = list(map(lambda pred_and_obj:
                               "(knora-base:{prop} knora-base:{obj} knora-admin:{prop} knora-admin:{obj})".format(
                                   prop=pred_and_obj[0], obj=pred_and_obj[1]), pred_obj_pairs))
    preds_with_objs_str = " ".join(preds_with_objs)

    # Generate SPARQL using the main template file.

    with open("sparql/update-knora-admin.rq.tmpl", 'r') as sparql_template_file:
        sparql_template = Template(sparql_template_file.read())

        template_dict = {
            "knoraAdmin": knora_admin_nt,
            "knoraBase": knora_base_nt,
            "predicates": predicates_str,
            "objects": objects_str,
            "preds_with_objs": preds_with_objs_str
        }

        sparql = sparql_template.substitute(template_dict)

        with open("/Users/benjamingeer/Desktop/sparql.rq", "w") as sparql_file:
            sparql_file.write(sparql)

        # Post the SPARQL to the triplestore.

        data = {
            "update": sparql
        }

        # r = requests.post(graphdb_url, data=data, auth=(username, password))
        # r.raise_for_status()


# Reads an ontology from knora-ontologies and returns it in ntriples format.
def knora_ontology_to_ntriples(graph):
    nt_bytes = io.BytesIO()
    graph.serialize(nt_bytes, format="ntriples")
    return nt_bytes.getvalue().decode("UTF-8")


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
