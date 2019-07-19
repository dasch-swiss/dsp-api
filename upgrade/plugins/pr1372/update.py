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


import rdflib
from updatelib import rdftools


# The IRI of knora-base:valueCreationDate.
value_creation_date = rdflib.term.URIRef("http://www.knora.org/ontology/knora-base#valueCreationDate")

# The IRI of knora-base:previousValue.
previous_value = rdflib.term.URIRef("http://www.knora.org/ontology/knora-base#previousValue")

# The IRI of knora-base:hasPermissions.
has_permissions = rdflib.term.URIRef("http://www.knora.org/ontology/knora-base#hasPermissions")


# Updates values for PR 1372.
class GraphTransformer(rdftools.GraphTransformer):
    def transform(self, graph):
        # Group the statements in the named graph by subject and by predicate.
        grouped_statements = rdftools.group_statements(graph)

        # Collect the IRIs of values to be transformed.
        value_iris = collect_value_iris(graph, grouped_statements)

        # Remove knora-base:hasPermissions from those values.
        for value_iri in value_iris:
            for o in graph.objects(value_iri, has_permissions):
                graph.remove((value_iri, has_permissions, o))

        return graph


# Given a graph, collects the IRIs of all values that are past value versions.
def collect_value_iris(graph, grouped_statements):
    value_iris = set()

    for subj, pred_objs in grouped_statements.items():
        if value_creation_date in pred_objs:
            # This is a value. Is it a past value version?
            if not rdftools.generator_is_empty(graph.subjects(previous_value, subj)):
                # Yes. Include its IRI.
                value_iris.add(subj)

    return value_iris
