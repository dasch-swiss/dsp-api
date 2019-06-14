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


import os
import time
from datetime import timedelta
import tempfile
import argparse
import getpass
import rdflib
import importlib
import re
from updatelib import rdftools


# A list of built-in Knora ontologies in the order in which they should be uploaded.
knora_ontologies = [
    {
        "filename": "knora-admin.ttl",
        "context": "http://www.knora.org/ontology/knora-admin"
    },
    {
        "filename": "knora-base.ttl",
        "context": "http://www.knora.org/ontology/knora-base"
    },
    {
        "filename": "salsah-gui.ttl",
        "context": "http://www.knora.org/ontology/salsah-gui"
    },
    {
        "filename": "standoff-onto.ttl",
        "context": "http://www.knora.org/ontology/standoff"
    }
]

# A set of the IRIs of the named graphs containing built-in Knora ontologies.
knora_ontology_contexts = set([onto["context"] for onto in knora_ontologies])

# A regex that matches the object of knora-base:ontologyVersion.
knora_base_version_string_regex = re.compile(r"^PR ([0-9]+)$")

# A regex that matches the name of a directory containing an update plugin.
plugin_dirname_regex = re.compile(r"^pr([0-9]+)$")


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

        self.namespaces = []

    # Downloads the named graph from the repository to a file in download_dir.
    def download(self, download_dir):
        print("Downloading named graph {}...".format(self.context))
        downloaded_file_path = download_dir + "/" + self.filename
        rdftools.do_download_request(graphdb_info=self.graphdb_info, context=self.uri, file_path=downloaded_file_path)

    # Parses the input graph.
    def parse(self, download_dir):
        print("Parsing input file for named graph {}...".format(self.context))
        input_file_path = download_dir + "/" + self.filename
        graph = rdflib.Graph()
        graph.parse(input_file_path, format="turtle")
        self.namespaces = list(graph.namespace_manager.namespaces())
        return graph

    # Formats the output graph.
    def format(self, graph, upload_dir):
        for prefix, namespace in self.namespaces:
            graph.namespace_manager.bind(prefix=prefix, namespace=namespace, override=True, replace=True)

        output_file_path = upload_dir + "/" + self.filename
        print("Writing transformed file...")
        graph.serialize(destination=output_file_path, format="turtle")

    # Uploads the transformed file from upload_dir to the repository.
    def upload(self, upload_dir):
        print("Uploading named graph {}...".format(self.context))
        upload_file_path = upload_dir + "/" + self.filename
        rdftools.do_upload_request(graphdb_info=self.graphdb_info, context=self.uri, file_path=upload_file_path)


# Represents a repository.
class Repository:
    def __init__(self, graphdb_info):
        self.graphdb_info = graphdb_info
        self.named_graphs = []

    # Downloads the repository, saving the named graphs in files in download_dir.
    def download(self, download_dir):
        print("Downloading named graphs...")
        contexts = rdftools.do_contexts_request(graphdb_info=self.graphdb_info)

        for context in contexts:
            if not (context in knora_ontology_contexts):
                named_graph = NamedGraph(context=context, graphdb_info=self.graphdb_info)
                named_graph.download(download_dir)
                self.named_graphs.append(named_graph)

        print("Downloaded named graphs to", download_dir)

    # Uses a GraphTransformer to transform the named graphs in download_dir, saving the output in upload_dir.
    def transform(self, graph_transformer, download_dir, upload_dir):
        print("Transforming data...")

        for named_graph in self.named_graphs:
            input_graph = named_graph.parse(download_dir)
            graph_size = len(input_graph)
            print("Transforming {} statements...".format(graph_size))
            output_graph = graph_transformer.transform(input_graph)
            named_graph.format(output_graph, upload_dir)

        print("Wrote transformed data to " + upload_dir)

    # Deletes the contents of the repository.
    def empty(self):
        print("Emptying repository...")
        rdftools.do_update_request(graphdb_info=self.graphdb_info, sparql="DROP ALL")
        print("Emptied repository.")

    # Uploads the PR-specific knora ontologies and transformed named graphs to the repository.
    def upload(self, knora_ontologies_dir, upload_dir):
        print("Uploading named graphs...")

        # Upload built-in Knora ontologies.

        for ontology in knora_ontologies:
            ontology_named_graph = NamedGraph(
                context=ontology["context"],
                graphdb_info=self.graphdb_info,
                filename=ontology["filename"]
            )

            ontology_named_graph.upload(knora_ontologies_dir)

        # Upload the transformed named graphs.

        for named_graph in self.named_graphs:
            named_graph.upload(upload_dir)

        print("Uploaded named graphs.")

    # Updates the Lucene index.
    def update_lucene_index(self):
        print("Updating Lucene index...")

        sparql = """
            PREFIX luc: <http://www.ontotext.com/owlim/lucene#>
            INSERT DATA { luc:fullTextSearchIndex luc:updateIndex _:b1 . }
        """

        rdftools.do_update_request(graphdb_info=self.graphdb_info, sparql=sparql)
        print("Updated Lucene index.")


# Updates the repository using the specified list of GraphTransformer instances.
def run_updates(graphdb_info, transformers):
    repository = Repository(graphdb_info)
    last_upload_dir = None
    last_transformer = None

    for transformer in transformers:
        temp_dir = tempfile.mkdtemp()
        print("Running transformation for PR {}...".format(transformer.pr_num))
        print("Using temporary directory {}".format(temp_dir))

        upload_dir = temp_dir + "/upload"
        os.mkdir(upload_dir)

        # Is this the first transformation?
        if last_upload_dir is not None:
            # No. Use the result of the last transformation as input.
            repository.transform(graph_transformer=transformer, download_dir=last_upload_dir, upload_dir=upload_dir)
        else:
            # Yes. Download the repository.
            download_dir = temp_dir + "/download"
            os.mkdir(download_dir)
            repository.download(download_dir)
            repository.transform(graph_transformer=transformer, download_dir=download_dir, upload_dir=upload_dir)

        last_upload_dir = upload_dir
        last_transformer = transformer
        print("Transformation for PR {} complete.".format(transformer.pr_num))

    # Empty the repository.
    repository.empty()

    # Upload the results of the last transformation.
    last_transformer_knora_ontologies_dir = "plugins/pr{}/knora-ontologies".format(last_transformer.pr_num)
    repository.upload(knora_ontologies_dir=last_transformer_knora_ontologies_dir, upload_dir=last_upload_dir)
    repository.update_lucene_index()


# Determines which transformations need to be run, and returns a corresponding list of GraphTransformer instances.
def load_transformers(graphdb_info):
    # Get the list of available transformations.

    plugins_subdirs = os.listdir("plugins")
    pr_nums = []

    for dirname in plugins_subdirs:
        match = plugin_dirname_regex.match(dirname)

        if match is not None:
            pr_nums.append(int(match.group(1)))

    # Get the version string attached to knora-base in the repository.

    knora_base_pr_num_sparql = """
        PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>

        SELECT ?ontologyVersion
        WHERE {
            <http://www.knora.org/ontology/knora-base> knora-base:ontologyVersion ?ontologyVersion .
        }
    """

    query_result_rows = rdftools.do_select_request(graphdb_info=graphdb_info, sparql=knora_base_pr_num_sparql)

    # Did we find a version string?
    if len(query_result_rows) > 0:
        # Yes. Parse it.

        ontology_version_string = query_result_rows[0]["ontologyVersion"]["value"]
        match = knora_base_version_string_regex.match(ontology_version_string)

        if match is None:
            raise rdftools.UpdateException("Could not parse knora-base:ontologyVersion: {}".format(ontology_version_string))

        print("Repository version: {}".format(ontology_version_string))
        repository_pr_num = int(match.group(1))
    else:
        # No. Run all available transformations.
        repository_pr_num = 0
        print("Repository has no knora-base:ontologyVersion.")

    # Make a sorted list of transformations needed for this repository.
    needed_pr_nums = sorted(list(filter(lambda pr_num: pr_num > repository_pr_num, pr_nums)))

    # If the list of transformations is empty, do nothing.
    if len(needed_pr_nums) == 0:
        return []

    needed_pr_nums_str = ', '.join(map(str, needed_pr_nums))
    print("Required updates: " + needed_pr_nums_str)
    transformers = []

    # Load a GraphTransformer instance for each transformation.
    for transformer_pr_num in needed_pr_nums:
        # Load the transformer's module.
        transformer_module = importlib.import_module("plugins.pr{}.update".format(transformer_pr_num))

        # Get the transformer class definition from the module.
        transformer_class = getattr(transformer_module, "GraphTransformer")

        # Make an instance of the transformer class.
        transformer = transformer_class()

        # Add its PR number to it.
        transformer.pr_num = transformer_pr_num

        # Add it to the list.
        transformers.append(transformer)

    return transformers


# Command-line invocation.
def main():
    default_graphdb_host = "localhost"
    default_repository = "knora-test"

    parser = argparse.ArgumentParser(description="Updates a Knora repository.")
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

    start = time.time()
    transformers = load_transformers(graphdb_info)

    if len(transformers) > 0:
        tempfile.tempdir = args.tempdir
        run_updates(graphdb_info=graphdb_info, transformers=transformers)
        elapsed = time.time() - start
        print("Update complete. Elapsed time: {}.".format(str(timedelta(seconds=elapsed))))
    else:
        print("No updates needed.")


if __name__ == "__main__":
    main()
