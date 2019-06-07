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
from rdflib.namespace import RDF, XSD
from updatelib import rdftools


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


# Updates standoff for PR 1307.
class GraphTransformer(rdftools.GraphTransformer):
    def transform(self, graph):
        # Check whether the transformation has already been done.
        statements_with_max_start_index = graph.subject_objects(value_has_max_standoff_start_index)

        if not rdftools.generator_is_empty(statements_with_max_start_index):
            print("This transformation seems to have been done already.")
            return graph

        # Group the statements in the named graph by subject and by predicate.
        grouped_statements = rdftools.group_statements(graph)

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

        return rdftools.ungroup_statements(transformed_statements)


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
