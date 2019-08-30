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


import requests
import rdflib
from collections import defaultdict
from abc import ABC, abstractmethod
import re


# An abstract class representing a PR-specific graph transformation. Each
# implementation must be in its own module and be called GraphTransformer.
class GraphTransformer(ABC):
    @abstractmethod
    def transform(self, graph):
        pass


# Raised when an error occurs during an update.
class UpdateException(Exception):
    def __init__(self, message):
        self.message = message


# Makes a SELECT request to the triplestore, returning the result as a list of rows.
def do_select_request(graphdb_info, sparql):
    headers = {
        "Content-Type": "application/sparql-query",
        "Accept": "application/sparql-results+json"
    }

    response = requests.post(
        url=graphdb_info.graphdb_url,
        headers=headers,
        data=sparql,
        auth=(graphdb_info.username, graphdb_info.password)
    )

    response.raise_for_status()
    json_response = response.json()
    return json_response["results"]["bindings"]


# Makes an update request to the triplestore.
def do_update_request(graphdb_info, sparql):
    headers = {
        "Content-Type": "application/sparql-update"
    }

    response = requests.post(
        url=graphdb_info.statements_url,
        headers=headers,
        data=sparql,
        auth=(graphdb_info.username, graphdb_info.password)
    )

    response.raise_for_status()


# Returns a list of available named graphs from the triplestore.
def do_contexts_request(graphdb_info):
    response = requests.get(
        url=graphdb_info.contexts_url,
        auth=(graphdb_info.username, graphdb_info.password)
    )

    response.raise_for_status()
    contexts = response.text.splitlines()

    if contexts[0] != "contextID":
        raise UpdateException("Unexpected response from GraphDB:\n" + response.text)

    contexts.pop(0)
    return contexts


# Downloads a named graph from the triplestore in Turtle format and saves it to a file.
def do_download_request(graphdb_info, context, file_path):
    params = {
        "infer": "false",
        "context": context
    }

    headers = {
        "Accept": "text/turtle"
    }

    response = requests.get(
        url=graphdb_info.statements_url,
        params=params,
        headers=headers,
        auth=(graphdb_info.username, graphdb_info.password)
    )

    response.raise_for_status()

    with open(file_path, "wb") as downloaded_file:
        for chunk in response.iter_content(chunk_size=1024):
            downloaded_file.write(chunk)


# Represents a file in Turtle format.
class TurtleFile:
    def __init__(self, context, namespaces, file_path):
        self.context = context
        self.namespaces = namespaces
        self.file_path = file_path


# A regex that matches the name of a Knora named graph that needs a prefix.
context_regex = re.compile(r"^http://www.knora.org/(.+)/(.+)/(.+)$")


# Given a collection of TurtleFiles, combines them into a Trig file and uploads that file to the triplestore.
def do_upload_request(graphdb_info, turtle_files, trig_file_path):
    namespaces = {
        "ontology": rdflib.URIRef("http://www.knora.org/ontology/"),
        "data": rdflib.URIRef("http://www.knora.org/data/")
    }

    for turtle_file in turtle_files:
        namespaces.update(turtle_file.namespaces)

        # Add prefixes, otherwise rdflib will add random ones.

        context_str = str(turtle_file.context)
        match = context_regex.match(context_str)

        if match is not None:
            prefix = match.group(1) + "-" + match.group(2)
            namespaces[prefix] = "http://www.knora.org/{}/{}/".format(match.group(1), match.group(2))

    dataset = rdflib.Dataset()

    for turtle_file in turtle_files:
        print("Reading named graph {}...".format(turtle_file.context))
        graph = dataset.graph(turtle_file.context)
        graph.parse(source=turtle_file.file_path, format="turtle")

    for prefix, namespace in namespaces.items():
        dataset.namespace_manager.bind(prefix=prefix, namespace=namespace, override=True, replace=True)

    print("Writing repository file for upload...")

    dataset.serialize(destination=trig_file_path, format="trig")

    headers = {
        "Content-Type": "application/trig"
    }

    print("Uploading repository...")

    with open(trig_file_path, "r") as file_to_upload:
        file_content = file_to_upload.read().encode("utf-8")

    response = requests.post(
        url=graphdb_info.statements_url,
        headers=headers,
        auth=(graphdb_info.username, graphdb_info.password),
        data=file_content
    )

    response.raise_for_status()


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
    ungrouped_statements = rdflib.Graph()

    for subj, pred_objs in grouped_statements.items():
        for pred, objs in pred_objs.items():
            for obj in objs:
                ungrouped_statements.add((subj, pred, obj))

    return ungrouped_statements


# Returns true if the specified generator is empty.
def generator_is_empty(gen):
    try:
        next(gen)
    except StopIteration:
        return True

    return False
