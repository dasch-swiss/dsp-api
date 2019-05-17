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
# Updates standoff for PR 1307.
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

# The IRI of knora-base:standoffTagHasStartParent.
standoff_tag_has_start_parent = rdflib.term.URIRef("http://www.knora.org/ontology/knora-base#standoffTagHasStartParent")

# The IRI of knora-base:standoffTagHasEndParent.
standoff_tag_has_end_parent = rdflib.term.URIRef("http://www.knora.org/ontology/knora-base#standoffTagHasEndParent")

# The IRI of knora-base:standoffTagHasStartIndex.
standoff_tag_has_start_index = rdflib.term.URIRef("http://www.knora.org/ontology/knora-base#standoffTagHasStartIndex")

# The IRI of knora-base:valueHasStandoff.
value_has_standoff = rdflib.term.URIRef("http://www.knora.org/ontology/knora-base#valueHasStandoff")

# The IRI of knora-base:valueHasMaxStandoffStartIndex.
value_has_max_standoff_start_index = rdflib.term.URIRef("http://www.knora.org/ontology/knora-base#valueHasMaxStandoffStartIndex")


# Represents a standoff tag to be be transformed.
class StandoffTag:
    def __init__(self, old_subj, pred_objs):
        self.old_subj = old_subj
        self.pred_objs = pred_objs
        self.start_index = int(pred_objs[standoff_tag_has_start_index][0])
        self.new_subj = make_new_standoff_tag_iri(old_subj, self.start_index)

    # Transforms the statements whose subject is this text value.
    def transform_pred_objs(self, standoff_tags):
        transformed_pred_objs = {}

        for pred, objs in self.pred_objs.items():
            if pred == standoff_tag_has_start_parent or pred == standoff_tag_has_end_parent:
                old_obj = objs[0]
                new_obj = standoff_tags[old_obj].new_subj
                transformed_pred_objs[pred] = [new_obj]
            else:
                transformed_pred_objs[pred] = objs

        return transformed_pred_objs


# Converts an old standoff tag IRI to a new one.
def make_new_standoff_tag_iri(old_subj, start_index):
    old_subj_str = str(old_subj)
    slash_pos = old_subj_str.rfind("/") + 1
    return rdflib.term.URIRef(old_subj_str[0:slash_pos] + str(start_index))


# Represents a text value to be transformed.
class TextValue:
    def __init__(self, subj, pred_objs, standoff_tags):
        self.subj = subj
        self.pred_objs = pred_objs
        self.standoff_tags = standoff_tags

    # Transforms the statements whose subject is this text value or any of its standoff tags.
    def transform_statements(self):
        transformed_value_pred_objs = {}

        for pred, objs in self.pred_objs.items():
            if pred == value_has_standoff:
                new_objs = []

                for old_obj in objs:
                    new_objs.append(self.standoff_tags[old_obj].new_subj)

                transformed_value_pred_objs[pred] = new_objs
            else:
                transformed_value_pred_objs[pred] = objs

        if self.standoff_tags:
            max_start_index = max([tag.start_index for tag in self.standoff_tags.values()])
            max_start_index_literal = rdflib.Literal(str(max_start_index), datatype=XSD.integer)
            transformed_value_pred_objs[value_has_max_standoff_start_index] = [max_start_index_literal]

        transformed_statements = {
            self.subj: transformed_value_pred_objs
        }

        for tag in self.standoff_tags.values():
            transformed_statements[tag.new_subj] = tag.transform_pred_objs(self.standoff_tags)

        return transformed_statements


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
        input_graph = rdflib.Graph()
        input_graph.parse(input_file_path, format="turtle")
        graph_size = len(input_graph)

        print("Transforming {} statements...".format(graph_size))

        # Group the statements in the named graph by subject and by predicate.
        grouped_statements = group_statements(input_graph)

        # Collect all text values included in the grouped statements.
        text_values = collect_text_values(grouped_statements)

        # A set of subjects that will be transformed and should therefore not be copied
        # from the input graph.
        old_subjs = set()

        # A set of transformed statements to be included in the output graph.
        transformed_statements = {}

        # Transform text values.
        for text_value in text_values:
            old_subjs.add(text_value.subj)

            for standoff_tag_old_subj in text_value.standoff_tags.keys():
                old_subjs.add(standoff_tag_old_subj)

            transformed_statements.update(text_value.transform_statements())

        # Copy all non-transformed statements into the output graph.
        for subj, pred_objs in grouped_statements.items():
            if subj not in old_subjs:
                transformed_statements[subj] = pred_objs

        output_graph = ungroup_statements(transformed_statements)
        output_file_path = upload_dir + "/" + self.filename

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


# Groups statements by subject and by predicate.
def group_statements(input_graph):
    grouped_statements = {}

    for subj in input_graph.subjects():
        grouped_pred_objs = defaultdict(list)

        for pred, obj in input_graph.predicate_objects(subj):
            grouped_pred_objs[pred].append(obj)

        grouped_statements[subj] = grouped_pred_objs

    return grouped_statements


# Ungroups statements.
def ungroup_statements(grouped_statements):
    output_graph = rdflib.Graph()

    for subj, pred_objs in grouped_statements.items():
        for pred, objs in pred_objs.items():
            for obj in objs:
                output_graph.add((subj, pred, obj))

    return output_graph


# Collects all text values found in grouped statements.
def collect_text_values(grouped_statements):
    text_values = []

    for subj, pred_objs in grouped_statements.items():
        if pred_objs[RDF.type][0] == text_value_type:
            standoff_tags = {}

            if value_has_standoff in pred_objs:
                standoff_subjs = pred_objs[value_has_standoff]

                for standoff_subj in standoff_subjs:
                    standoff_pred_objs = grouped_statements[standoff_subj]
                    standoff_tag = StandoffTag(old_subj=standoff_subj, pred_objs=standoff_pred_objs)
                    standoff_tags[standoff_subj] = standoff_tag

            text_values.append(TextValue(subj=subj, pred_objs=pred_objs, standoff_tags=standoff_tags))

    return text_values


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
