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

***************
What Is Knora?
***************

Knora (Knowledge Organization, Representation, and Annotation) is a software
framework for storing, sharing, and working with humanities data.

Knora is based on the idea that the continuous availability and reusability of
digital qualitative research data in the humanities requires a common,
flexible data representation and storage technology capable of performing
queries across large quantities of heterogeneous data, organised according to
project-specific data structures that cannot be known in advance. It also
requires a convenient, storage-independent way for Virtual Research
Environments (VREs) and automated data-processing software to access, query,
and add to this data.

To solve the data representation and storage problem, Knora represents
humanities data as RDF_ graphs, using OWL_ ontologies that express abstract,
cross-disciplinary commonalities in the structure and semantics of research
data. Each project using Knora extends these abstractions by providing its own
project-specific ontology, which more specifically describes the structure and
semantics of its data. Existing non-RDF repositories can readily be converted
to an RDF format based on the proposed abstractions. This design makes it
possible to preserve the semantics of data imported from relational databases,
XML-based markup systems, and other types of storage, as well as to query,
annotate, and link together heterogeneous data in a unified way. By offering
a shared, standards-based, extensible infrastructure for diverse humanities
projects, Knora also deals with the issue of conversion and
migration caused by the obsolescence of file and data formats in an efficient
and feasible manner.

To solve the access problem, Knora offers a generic HTTP-based API. In the
Knora framework, the standard implementation of this API is a server program
called the Knora API Server. The Knora API allows applications to query and
work with data in terms of the concepts expressed by the Knora ontologies,
without dealing with the complexities of the underlying storage system and its
query language (e.g. SPARQL_). It also provides features that are not part of
SPARQL, such as access control and automatic versioning of data. While the
Knora API is best suited to interacting with RDF repositories based on the
Knora ontologies, it can also be implemented as a gateway to other sorts of
repositories, including non-RDF repositories.

Knora uses a high-performance media server, called Sipi, for serving and
converting binary media files such as images and video. Sipi can efficiently
convert between many different formats on demand, preserving embedded
metadata, and implements the
`International Image Interoperability Framework (IIIF)`_.

Knora provides a general-purpose, browser-based VRE called SALSAH, which
relies on the components described above. Using the Knora API, a project can
also create its own VRE or project-specific web site, optionally reusing
components from SALSAH.

Knora is thus a set of standard components that can be used separately or
together, or extended to meet a project's specific needs. You can learn more
about each component:

- :ref:`knora-ontologies`, a set of OWL ontologies describing a common
  structure for describing humanities data in RDF.

- :ref:`knora-api-server`, a server program written in Scala_ that implements
  an HTTP-based API for accessing and working with data stored in an RDF
  triplestore according to the structures defined in the Knora ontologies.

- Sipi (to be released soon), a high-performance media server written in C++.

- The SALSAH GUI (to be release soon), a web-based virtual research
  environment for working with data managed by the Knora API server.


.. _SPARQL: https://www.w3.org/TR/sparql11-overview/
.. _Scala: http://www.scala-lang.org/
.. _RDF: http://www.w3.org/TR/2014/NOTE-rdf11-primer-20140624/
.. _OWL: http://www.w3.org/TR/2012/REC-owl2-primer-20121211/
.. _International Image Interoperability Framework (IIIF): http://iiif.io/
