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


import os
import time
from datetime import timedelta
import tempfile
import argparse
import getpass
import requests
import rdflib
from rdflib.namespace import RDF, XSD


class UpdateException(Exception):
    def __init__(self, message):
        self.message = message


# The directory where this update's built-in Knora ontologies are stored.
knora_ontologies_dir = "knora-ontologies"

# The filename of the knora-base ontology.
knora_base_filename = "knora-base.ttl"

# The context of the named graph containing the knora-base ontology.
knora_base_context = "http://www.knora.org/ontology/knora-base"

# The namespace of the knora-base ontology.
knora_base_namespace = knora_base_context + "#"

# The filename of the knora-admin ontology.
knora_admin_filename = "knora-admin.ttl"

# The context of the named graph containing the knora-admin ontology.
knora_admin_context = "http://www.knora.org/ontology/knora-admin"

# The namespace of the knora-admin ontology.
knora_admin_namespace = knora_admin_context + "#"

# The IRI of the property knora-base:hasPermissions.
has_permissions = rdflib.URIRef("http://www.knora.org/ontology/knora-base#hasPermissions")


# Represents information about a GraphDB repository.
class GraphDBInfo:
    def __init__(self, graphdb_host, repository, username, password):
        self.graphdb_url = "http://{}:7200/repositories/{}".format(graphdb_host, repository)
        self.contexts_url = self.graphdb_url + "/contexts"
        self.statements_url = self.graphdb_url + "/statements"
        self.username = username
        self.password = password


# Represents the names of the entities in the knora-admin ontology. There are two kinds of
# entities:
#
# - properties
# - objects (anything that can be the object of a property)
class KnoraAdminInfo:
    def __init__(self):
        # The property types used in knora-admin.
        property_types = {
            "http://www.w3.org/2002/07/owl#ObjectProperty",
            "http://www.w3.org/2002/07/owl#DatatypeProperty",
            "http://www.w3.org/1999/02/22-rdf-syntax-ns#Property"
        }

        # Read knora-admin.
        knora_admin_graph = rdflib.Graph()
        knora_admin_graph.parse(knora_ontologies_dir + "/knora-admin.ttl", format="turtle")

        self.properties = []
        self.objects = []

        # Iterate over all the statements in knora-admin.
        for subject, predicate, obj in knora_admin_graph:
            # Is this a statement about an ontology entity?
            _, subj_name = split_namespace_and_local_name(subject)

            if subj_name is not None:
                # Is the predicate rdf:type?
                if predicate == RDF.type:
                    # Yes. Is the object a property type?
                    if str(obj) in property_types:
                        # Yes. Collect the subject as a property IRI.
                        self.properties.append(subj_name)
                    elif subject.__class__.__name__ == "URIRef":
                        # The object isn't a property type, and the subject is an IRI (not a blank node).
                        # Collect the subject as a non-property IRI that can be used as an object in data.
                        self.objects.append(subj_name)


# Represents a named graph.
class NamedGraph:
    def __init__(self, context, graphdb_info, filename=None):
        self.context = context
        self.uri = "<" + context + ">"
        self.graphdb_info = graphdb_info

        if filename is not None:
            self.filename = filename
        else:
            self.filename = context.translate({ord(c): None for c in "/:"}) + ".ttl"

    # Downloads the named graph from the repository to a file in download_dir.
    def download(self, download_dir):
        print("Downloading named graph {}...".format(self.context))
        context_response = requests.get(self.graphdb_info.statements_url,
                                        params={"infer": "false", "context": self.uri, "Accept": "text/turtle"},
                                        auth=(self.graphdb_info.username, self.graphdb_info.password))
        context_response.raise_for_status()
        downloaded_file_path = download_dir + "/" + self.filename

        with open(downloaded_file_path, "wb") as downloaded_file:
            for chunk in context_response.iter_content(chunk_size=1024):
                downloaded_file.write(chunk)

    # Transforms the named graph, writing the output to a file in upload_dir.
    def transform(self, knora_admin_info, download_dir, upload_dir):
        print("Parsing input file for named graph {}...".format(self.context))
        input_file_path = download_dir + "/" + self.filename
        input_graph = rdflib.Graph()
        input_graph.parse(input_file_path, format="turtle")
        graph_size = len(input_graph)

        print("Transforming {} statements...".format(graph_size))
        output_file_path = upload_dir + "/" + self.filename
        output_graph = rdflib.Graph()
        statement_count = 0

        # Iterate over the statements in input_graph, transform them, and add the transformed
        # statements to output_graph.
        for subj, pred, obj in input_graph:
            # Is the predicate knora-base:hasPermissions?
            if pred == has_permissions:
                # Yes. Replace knora-base with knora-admin in the object.
                transformed_string = str(obj).replace("knora-base", "knora-admin")
                transformed_obj = rdflib.Literal(transformed_string, datatype=XSD.string)
                output_graph.add((subj, pred, transformed_obj))
            else:
                # No. Transform the predicate and/or object if necessary.
                transformed_pred = transform_entity(pred, knora_admin_info.properties)
                transformed_obj = transform_entity(obj, knora_admin_info.objects)
                output_graph.add((subj, transformed_pred, transformed_obj))

            statement_count += 1

            if statement_count % 10000 == 0:
                print("Transformed {} statements...".format(statement_count))

        print("Writing transformed file...")
        output_graph.serialize(destination=output_file_path, format="turtle")

    # Uploads the transformed file from upload_dir to the repository.
    def upload(self, upload_dir):
        print("Uploading named graph {}...".format(self.context))
        upload_file_path = upload_dir + "/" + self.filename

        with open(upload_file_path, "r") as file_to_upload:
            file_content = file_to_upload.read().encode("utf-8")

        upload_response = requests.post(self.graphdb_info.statements_url,
                                        params={"context": self.uri},
                                        headers={"Content-Type": "text/turtle"},
                                        auth=(self.graphdb_info.username, self.graphdb_info.password),
                                        data=file_content)

        upload_response.raise_for_status()


# Represents a repository.
class Repository:
    def __init__(self, graphdb_info):
        self.graphdb_info = graphdb_info
        self.named_graphs = []

    # Downloads the repository, saving the named graphs in files in download_dir.
    def download(self, download_dir):
        print("Downloading named graphs...")

        contexts_response = requests.get(self.graphdb_info.contexts_url,
                                         auth=(self.graphdb_info.username, self.graphdb_info.password))
        contexts_response.raise_for_status()
        contexts = contexts_response.text.splitlines()

        if contexts[0] != "contextID":
            raise UpdateException("Unexpected response from GraphDB: " + contexts_response.text)

        contexts.pop(0)

        for context in contexts:
            if not (context == knora_base_context or context == knora_admin_context):
                named_graph = NamedGraph(context=context, graphdb_info=self.graphdb_info)
                named_graph.download(download_dir)
                self.named_graphs.append(named_graph)

        print("Downloaded named graphs to", download_dir)

    # Transforms the named graphs, saving the output in files in upload_dir.
    def transform(self, download_dir, upload_dir):
        print("Transforming downloaded data...")

        knora_admin_info = KnoraAdminInfo()

        for named_graph in self.named_graphs:
            named_graph.transform(
                knora_admin_info=knora_admin_info,
                download_dir=download_dir,
                upload_dir=upload_dir
            )

        print("Wrote transformed data to " + upload_dir)

    # Deletes the contents of the repository.
    def empty(self):
        print("Emptying repository...")
        drop_all_response = requests.post(self.graphdb_info.statements_url,
                                          headers={"Content-Type": "application/sparql-update"},
                                          auth=(self.graphdb_info.username, self.graphdb_info.password),
                                          data="DROP ALL")
        drop_all_response.raise_for_status()
        print("Emptied repository.")

    # Uploads the transformed data to the repository.
    def upload(self, upload_dir):
        print("Uploading named graphs...")

        # Upload the new knora-admin and knora-base ontologies.

        knora_admin_named_graph = NamedGraph(
            context=knora_admin_context,
            graphdb_info=self.graphdb_info,
            filename=knora_admin_filename
        )

        knora_base_named_graph = NamedGraph(
            context=knora_base_context,
            graphdb_info=self.graphdb_info,
            filename=knora_base_filename
        )

        knora_admin_named_graph.upload(knora_ontologies_dir)
        knora_base_named_graph.upload(knora_ontologies_dir)

        # Upload the transformed named graphs.

        for named_graph in self.named_graphs:
            named_graph.upload(upload_dir)

        print("Uploaded named graphs.")

    def update_lucene_index(self):
        print("Updating Lucene index...")

        sparql = """
            PREFIX luc: <http://www.ontotext.com/owlim/lucene#>
            INSERT DATA { luc:fullTextSearchIndex luc:updateIndex _:b1 . }
        """

        update_lucene_index_response = requests.post(self.graphdb_info.statements_url,
                                                     headers={"Content-Type": "application/sparql-update"},
                                                     auth=(self.graphdb_info.username, self.graphdb_info.password),
                                                     data=sparql)
        update_lucene_index_response.raise_for_status()
        print("Updated Lucene index.")


# Given an IRI, returns a tuple containing the namespace and the local name. If there is
# no local name, the second item of the tuple is None.
def split_namespace_and_local_name(entity):
    entity_str = str(entity)
    hash_pos = entity_str.rfind("#")

    if hash_pos != -1:
        return entity_str[:hash_pos + 1], entity_str[hash_pos + 1:len(entity_str)]
    else:
        return entity_str, None


# If the specified entity is an IRI in the knora-base namespace and its local name is
# in local_name_list, returns the equivalent IRI in the knora-admin namespace.
# Otherwise, returns the entity unchanged.
def transform_entity(entity, local_name_list):
    if entity.__class__.__name__ == "URIRef":
        namespace, local_name = split_namespace_and_local_name(entity)

        if local_name is not None and namespace == knora_base_namespace and local_name in local_name_list:
            return rdflib.URIRef(knora_admin_namespace + local_name)
        else:
            return entity
    else:
        return entity


# Updates a repository.
def update_repository(graphdb_info, download_dir, upload_dir):
    start = time.time()
    repository = Repository(graphdb_info)
    repository.download(download_dir)
    repository.transform(download_dir=download_dir, upload_dir=upload_dir)
    repository.empty()
    repository.upload(upload_dir)
    repository.update_lucene_index()
    elapsed = time.time() - start
    print("Update complete. Elapsed time: {}.".format(str(timedelta(seconds=elapsed))))


# Command-line invocation.
def main():
    default_graphdb_host = "localhost"
    default_repository = "knora-test"

    parser = argparse.ArgumentParser(description="Separates knora-admin from knora-base.")
    parser.add_argument("-g", "--graphdb", default=default_graphdb_host, help="GraphDB host (default '{}')".format(default_graphdb_host), type=str)
    parser.add_argument("-r", "--repository", default=default_repository, help="GraphDB repository (default '{}')".format(default_repository),
                        type=str)
    parser.add_argument("-u", "--username", help="GraphDB username", type=str, required=True)
    parser.add_argument("-p", "--password", help="GraphDB password (if not provided, will prompt for password)",
                        type=str)
    parser.add_argument("-t", "--tempdir", help="temporary directory", type=str)

    args = parser.parse_args()
    password = args.password

    if not password:
        password = getpass.getpass()

    graphdb_info = GraphDBInfo(
        graphdb_host=args.graphdb,
        repository=args.repository,
        username=args.username,
        password=password
    )

    tempfile.tempdir = args.tempdir
    temp_dir = tempfile.mkdtemp()
    print("Using temporary directory", temp_dir)

    download_dir = temp_dir + "/download"
    upload_dir = temp_dir + "/upload"
    os.mkdir(download_dir)
    os.mkdir(upload_dir)

    update_repository(
        graphdb_info=graphdb_info,
        download_dir=download_dir,
        upload_dir=upload_dir
    )


if __name__ == "__main__":
    main()
