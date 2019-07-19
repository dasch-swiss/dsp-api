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
from rdflib.namespace import XSD
from updatelib import rdftools


# The incorrect IRI of xsd:valueHasDecimal.
xsd_value_has_decimal = rdflib.term.URIRef("http://www.w3.org/2001/XMLSchema#valueHasDecimal")


# Updates datatypes for PR 1367.
class GraphTransformer(rdftools.GraphTransformer):
    def transform(self, graph):
        output_graph = rdflib.Graph()

        for (s, p, o) in graph:
            if o.__class__.__name__ == "Literal" and o.datatype == xsd_value_has_decimal:
                new_o = rdflib.Literal(str(o), datatype=XSD.decimal)
                output_graph.add((s, p, new_o))
            else:
                output_graph.add((s, p, o))

        return output_graph
