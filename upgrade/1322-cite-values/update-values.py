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
# Updates knora-base and existing values for PR 1322.
#######################################################################################


import os
import time
import uuid
import base64
from datetime import timedelta
import tempfile
import argparse
import getpass
import requests
import rdflib
from rdflib.namespace import XSD
from collections import defaultdict


class UpdateException(Exception):
    def __init__(self, message):
        self.message = message


# The directory in the Knora source tree where built-in Knora ontologies are stored.
knora_ontologies_dir = "../../knora-ontologies"

# The filename of the knora-admin ontology.
knora_admin_filename = "knora-admin.ttl"

# The context of the named graph containing the knora-admin ontology.
knora_admin_context = "http://www.knora.org/ontology/knora-admin"

# The filename of the knora-base ontology.
knora_base_filename = "knora-base.ttl"

# The context of the named graph containing the knora-base ontology.
knora_base_context = "http://www.knora.org/ontology/knora-base"

# The IRI of knora-base:TextValue.
text_value_type = rdflib.term.URIRef("http://www.knora.org/ontology/knora-base#TextValue")

# The IRI of knora-base:valueHasUUID.
value_has_uuid = rdflib.term.URIRef("http://www.knora.org/ontology/knora-base#valueHasUUID")

# The IRI of knora-base:valueCreationDate.
value_creation_date = rdflib.term.URIRef("http://www.knora.org/ontology/knora-base#valueCreationDate")


# Represents information about a GraphDB repository.
class GraphDBInfo:
    def __init__(self, graphdb_host, repository, username, password):
        self.graphdb_url = "http://{}:7200/repositories/{}".format(graphdb_host, repository)
        self.contexts_url = self.graphdb_url + "/contexts"
        self.statements_url = self.graphdb_url + "/statements"
        self.username = username
        self.password = password


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
    def transform(self, download_dir, upload_dir):
        print("Parsing input file for named graph {}...".format(self.context))
        input_file_path = download_dir + "/" + self.filename
        graph = rdflib.Graph()
        graph.parse(input_file_path, format="turtle")
        graph_size = len(graph)

        print("Transforming {} statements...".format(graph_size))

        # Group the statements in the named graph by subject and by predicate.
        grouped_statements = group_statements(graph)

        # Collect all text values included in the grouped statements.
        value_iris = collect_value_iris(grouped_statements)

        for value_iri in value_iris:
            random_uuid_str = make_random_uuid_str()
            graph.add((value_iri, value_has_uuid, rdflib.Literal(str(random_uuid_str), datatype=XSD.string)))

        output_file_path = upload_dir + "/" + self.filename

        print("Writing transformed file...")
        graph.serialize(destination=output_file_path, format="turtle")

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


# Groups statements by subject and by predicate.
def group_statements(input_graph):
    grouped_statements = {}

    for subj in input_graph.subjects():
        grouped_pred_objs = defaultdict(list)

        for pred, obj in input_graph.predicate_objects(subj):
            grouped_pred_objs[pred].append(obj)

        grouped_statements[subj] = grouped_pred_objs

    return grouped_statements


# Collects all value IRIs found in grouped statements.
def collect_value_iris(grouped_statements):
    value_iris = set()

    for subj, pred_objs in grouped_statements.items():
        if value_creation_date in pred_objs:
            value_iris.add(subj)

    return value_iris


# Returns a random, Base64-encoded, URL-safe UUID.
def make_random_uuid_str():
    random_uuid = uuid.uuid4()
    return base64.urlsafe_b64encode(random_uuid.bytes).decode("ascii").strip("=")


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

        for named_graph in self.named_graphs:
            named_graph.transform(
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

        # Upload the knora-admin and knora-base ontologies first.

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
        knora_base_named_graph.upload(".")

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

    parser = argparse.ArgumentParser(description="Makes values citable.")
    parser.add_argument("-g", "--graphdb", help="GraphDB host (default '{}')".format(default_graphdb_host), type=str)
    parser.add_argument("-r", "--repository", help="GraphDB repository (default '{}')".format(default_repository),
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
        repository = default_repository

    password = args.password

    if not password:
        password = getpass.getpass()

    graphdb_info = GraphDBInfo(
        graphdb_host=graphdb_host,
        repository=repository,
        username=args.username,
        password=password
    )

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
