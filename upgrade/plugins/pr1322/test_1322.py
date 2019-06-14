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
from rdflib.term import URIRef
from plugins.pr1322.update import GraphTransformer


save_output = False


def test_update():
    input_graph = rdflib.Graph()
    input_graph.parse("plugins/pr1322/test_data.ttl", format="turtle")
    transformer = GraphTransformer()
    output_graph = transformer.transform(input_graph)

    query_result = output_graph.query(
        """
        PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>

        SELECT ?value WHERE {
            ?value knora-base:valueHasUUID ?valueHasUUID .
        }
        """
    )

    values_with_uuids = set([row["value"] for row in query_result])

    assert values_with_uuids == {
        URIRef("http://rdfh.ch/0001/thing-with-history/values/1c"),
        URIRef("http://rdfh.ch/0001/thing-with-history/values/2c")
    }

    if save_output:
        temp_dir = tempfile.mkdtemp()
        output_file_path = temp_dir + "/output.ttl"
        output_graph.serialize(destination=output_file_path, format="turtle")
        print("Wrote output to", output_file_path)
