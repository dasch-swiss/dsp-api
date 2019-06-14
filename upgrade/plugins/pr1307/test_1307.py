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


import tempfile
import rdflib
from rdflib.term import URIRef, Literal
from rdflib.namespace import XSD
from plugins.pr1307.update import GraphTransformer

save_output = False


def test_update():
    input_graph = rdflib.Graph()
    input_graph.parse("plugins/pr1307/test_data.ttl", format="turtle")
    transformer = GraphTransformer()
    output_graph = transformer.transform(input_graph)

    query_result = output_graph.query(
        """
        PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>

        SELECT ?s ?maxStartIndex WHERE {
            ?s knora-base:valueHasMaxStandoffStartIndex ?maxStartIndex .
        }
        """
    )

    max_start_index = set([(row["s"], row["maxStartIndex"]) for row in query_result])

    assert max_start_index == {
        (
            URIRef("http://rdfh.ch/0001/qN1igiDRSAemBBktbRHn6g/values/xyUIf8QHS5aFrlt7Q4F1FQ"),
            Literal("7", datatype=XSD.integer)
        )
    }

    query_result = output_graph.query(
        """
        PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>

        SELECT ?tag WHERE {
            <http://rdfh.ch/0001/qN1igiDRSAemBBktbRHn6g/values/xyUIf8QHS5aFrlt7Q4F1FQ> knora-base:valueHasStandoff ?tag .
        }
        """
    )

    tags = set([row["tag"] for row in query_result])

    assert tags == {
        URIRef("http://rdfh.ch/0001/qN1igiDRSAemBBktbRHn6g/values/xyUIf8QHS5aFrlt7Q4F1FQ/standoff/0"),
        URIRef("http://rdfh.ch/0001/qN1igiDRSAemBBktbRHn6g/values/xyUIf8QHS5aFrlt7Q4F1FQ/standoff/1"),
        URIRef("http://rdfh.ch/0001/qN1igiDRSAemBBktbRHn6g/values/xyUIf8QHS5aFrlt7Q4F1FQ/standoff/2"),
        URIRef("http://rdfh.ch/0001/qN1igiDRSAemBBktbRHn6g/values/xyUIf8QHS5aFrlt7Q4F1FQ/standoff/3"),
        URIRef("http://rdfh.ch/0001/qN1igiDRSAemBBktbRHn6g/values/xyUIf8QHS5aFrlt7Q4F1FQ/standoff/4"),
        URIRef("http://rdfh.ch/0001/qN1igiDRSAemBBktbRHn6g/values/xyUIf8QHS5aFrlt7Q4F1FQ/standoff/5"),
        URIRef("http://rdfh.ch/0001/qN1igiDRSAemBBktbRHn6g/values/xyUIf8QHS5aFrlt7Q4F1FQ/standoff/6"),
        URIRef("http://rdfh.ch/0001/qN1igiDRSAemBBktbRHn6g/values/xyUIf8QHS5aFrlt7Q4F1FQ/standoff/7")
    }

    query_result = output_graph.query(
        """
        PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>

        SELECT ?tag ?startIndex ?startParent WHERE {
            ?tag knora-base:standoffTagHasStartIndex ?startIndex .
            
            OPTIONAL {
                ?tag knora-base:standoffTagHasStartParent ?startParent .
            }
        }
        """
    )

    tag_data = set([(row["tag"], row["startIndex"], row["startParent"]) for row in query_result])

    assert tag_data == {
        (
            URIRef("http://rdfh.ch/0001/qN1igiDRSAemBBktbRHn6g/values/xyUIf8QHS5aFrlt7Q4F1FQ/standoff/0"),
            Literal("0", datatype=XSD.integer),
            None
        ),
        (
            URIRef("http://rdfh.ch/0001/qN1igiDRSAemBBktbRHn6g/values/xyUIf8QHS5aFrlt7Q4F1FQ/standoff/1"),
            Literal("1", datatype=XSD.integer),
            URIRef("http://rdfh.ch/0001/qN1igiDRSAemBBktbRHn6g/values/xyUIf8QHS5aFrlt7Q4F1FQ/standoff/0")
        ),
        (
            URIRef("http://rdfh.ch/0001/qN1igiDRSAemBBktbRHn6g/values/xyUIf8QHS5aFrlt7Q4F1FQ/standoff/2"),
            Literal("2", datatype=XSD.integer),
            URIRef("http://rdfh.ch/0001/qN1igiDRSAemBBktbRHn6g/values/xyUIf8QHS5aFrlt7Q4F1FQ/standoff/1")
        ),
        (
            URIRef("http://rdfh.ch/0001/qN1igiDRSAemBBktbRHn6g/values/xyUIf8QHS5aFrlt7Q4F1FQ/standoff/3"),
            Literal("3", datatype=XSD.integer),
            URIRef("http://rdfh.ch/0001/qN1igiDRSAemBBktbRHn6g/values/xyUIf8QHS5aFrlt7Q4F1FQ/standoff/1")
        ),
        (
            URIRef("http://rdfh.ch/0001/qN1igiDRSAemBBktbRHn6g/values/xyUIf8QHS5aFrlt7Q4F1FQ/standoff/4"),
            Literal("4", datatype=XSD.integer),
            URIRef("http://rdfh.ch/0001/qN1igiDRSAemBBktbRHn6g/values/xyUIf8QHS5aFrlt7Q4F1FQ/standoff/1")
        ),
        (
            URIRef("http://rdfh.ch/0001/qN1igiDRSAemBBktbRHn6g/values/xyUIf8QHS5aFrlt7Q4F1FQ/standoff/5"),
            Literal("5", datatype=XSD.integer),
            URIRef("http://rdfh.ch/0001/qN1igiDRSAemBBktbRHn6g/values/xyUIf8QHS5aFrlt7Q4F1FQ/standoff/1")
        ),
        (
            URIRef("http://rdfh.ch/0001/qN1igiDRSAemBBktbRHn6g/values/xyUIf8QHS5aFrlt7Q4F1FQ/standoff/6"),
            Literal("6", datatype=XSD.integer),
            URIRef("http://rdfh.ch/0001/qN1igiDRSAemBBktbRHn6g/values/xyUIf8QHS5aFrlt7Q4F1FQ/standoff/1")
        ),
        (
            URIRef("http://rdfh.ch/0001/qN1igiDRSAemBBktbRHn6g/values/xyUIf8QHS5aFrlt7Q4F1FQ/standoff/7"),
            Literal("7", datatype=XSD.integer),
            URIRef("http://rdfh.ch/0001/qN1igiDRSAemBBktbRHn6g/values/xyUIf8QHS5aFrlt7Q4F1FQ/standoff/1")
        )
    }

    if save_output:
        temp_dir = tempfile.mkdtemp()
        output_file_path = temp_dir + "/output.ttl"
        output_graph.serialize(destination=output_file_path, format="turtle")
        print("Wrote output to", output_file_path)
