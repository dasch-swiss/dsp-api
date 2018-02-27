.. Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
   Tobias Schweizer, André Kilchenmann, and Sepideh Alassi.

   This file is part of Knora.

   Knora is free software: you can redistribute it and/or modify
   it under the terms of the GNU Affero General Public License as published
   by the Free Software Foundation, either version 3 of the License, or
   (at your option) any later version.

   Knora is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
   GNU Affero General Public License for more details.

   You should have received a copy of the GNU Affero General Public
   License along with Knora.  If not, see <http://www.gnu.org/licenses/>.

.. _knora-api-server:

####################
The Knora API Server
####################

The Knora API server implements Knora's HTTP-based API, and manages data
stored in an RDF triplestore and in files. It is designed to work with any
standards-compliant RDF triplestore, and is configured to work out of the box
with `Ontotext GraphDB`_ and `Apache Jena`_.

.. toctree::
   :maxdepth: 2

   deployment/index
   design-documentation/index
   development/index
   api_v1/index
   api_v2/index

.. _Ontotext GraphDB: http://ontotext.com/products/graphdb/
.. _Apache Jena: https://jena.apache.org/
