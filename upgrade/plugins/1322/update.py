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


import uuid
import base64
import rdflib
from rdflib.namespace import XSD
from updatelib import rdftools


# The IRI of knora-base:TextValue.
text_value_type = rdflib.term.URIRef("http://www.knora.org/ontology/knora-base#TextValue")

# The IRI of knora-base:valueHasUUID.
value_has_uuid = rdflib.term.URIRef("http://www.knora.org/ontology/knora-base#valueHasUUID")

# The IRI of knora-base:valueCreationDate.
value_creation_date = rdflib.term.URIRef("http://www.knora.org/ontology/knora-base#valueCreationDate")

# The IRI of knora-base:previousValue.
previous_value = rdflib.term.URIRef("http://www.knora.org/ontology/knora-base#previousValue")


# Updates values for PR 1322.
class GraphTransformer(rdftools.GraphTransformer):
    def transform(self, graph):
        # Check whether the transformation has already been done.
        statements_with_value_has_uuid = graph.subject_objects(value_has_uuid)

        if not rdftools.generator_is_empty(statements_with_value_has_uuid):
            print("This transformation seems to have been done already.")
            return graph

        # Group the statements in the named graph by subject and by predicate.
        grouped_statements = rdftools.group_statements(graph)

        # Collect the IRIs of values to be transformed.
        value_iris = collect_value_iris(graph, grouped_statements)

        for value_iri in value_iris:
            random_uuid_str = make_random_uuid_str()
            graph.add((value_iri, value_has_uuid, rdflib.Literal(str(random_uuid_str), datatype=XSD.string)))

        return graph


# Given a graph, collects the IRIs of all values that are current value versions.
def collect_value_iris(graph, grouped_statements):
    value_iris = set()

    for subj, pred_objs in grouped_statements.items():
        if value_creation_date in pred_objs:
            # This is a value. Is it a current value version?
            if rdftools.generator_is_empty(graph.subjects(previous_value, subj)):
                # Yes. Include its IRI.
                value_iris.add(subj)

    return value_iris


# Returns a random, Base64-encoded, URL-safe UUID.
def make_random_uuid_str():
    random_uuid = uuid.uuid4()
    return base64.urlsafe_b64encode(random_uuid.bytes).decode("ascii").strip("=")

