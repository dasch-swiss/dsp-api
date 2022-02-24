<!---
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
-->

# Standoff/RDF Text Markup

[Standoff markup](https://lexiconse.uantwerpen.be/lexicon/markupStandoff.html)
is text markup that is stored separately from the content it describes. DSP-API's
Standoff/RDF markup stores content as a simple Unicode string, and represents markup
separately as RDF data. This approach has some advantages over commonly used markup systems
such as XML:

First, XML and other hierarchical markup systems assume that a document is a hierarchy, and
have difficulty representing [non-hierarchical structures](http://www.tei-c.org/release/doc/tei-p5-doc/en/html/NH.html)
or multiple overlapping hierarchies. Standoff markup can easily represent these structures.

Second, markup languages are typically designed to be used in text files. But there is no
standard system for searching and linking together many different text files containing
markup. It is possible to do this in a non-standard way by using an XML database
such as [eXist](http://exist-db.org), but this still does not allow for queries that include
text as well as non-textual data not stored in XML.

By storing markup as RDF, DSP-API can search for markup structures in the same way that it
searches for any RDF data structure. This makes it possible to do searches that combine
text-related criteria with other sorts of criteria. For example, if persons and events are
represented as resources, and texts are represented in Standoff/RDF, a text can contain
tags representing links to persons or events. You could then search for a text that mentions a
person who lived in the same city as another person who is the author of a text that mentions an
event that occurred during a certain time period.

In DSP-API's Standoff/RDF, a tag is an RDF entity that is linked to a
[text value](../02-knora-ontologies/knora-base.md#textvalue). Each tag points to a substring
of the text, and has semantic properties of its own. You can define your own tag classes
in your ontology by making subclasses of `knora-base:StandoffTag`, and attach your own
properties to them. You can then search for those properties using DSP-API's search language,
[Gravsearch](../03-apis/api-v2/query-language.md).

The built-in [knora-base](../02-knora-ontologies/knora-base.md) and `standoff` ontologies
provide some basic tags that can be reused or extended. These include tags that represent
DSP-API data types. For example, `knora-base:StandoffDateTag` represents a date in exactly the
same way as a [date value](../02-knora-ontologies/knora-base.md#datevalue), i.e. as a
calendar-independent astronomical date. You can use this tag as-is, or extend it by making
a subclass, to represent dates in texts. Gravsearch includes built-in functionality for
searching for these data type tags. For example, you can search for text containing a date that
falls within a certain [date range](../03-apis/api-v2/query-language.md#matching-standoff-dates).

DSP-API supports automatic conversion between XML and Standoff/RDF. To make this work,
Standoff/RDF stores the order of tags and their hierarchical relationships. You must define an
[XML-to-Standoff Mapping](../03-apis/api-v2/xml-to-standoff-mapping.md) for your standoff tag classes and properties.
Then you can import an XML document into DSP-API, which will store it as Standoff/RDF. The text and markup
can then be searched using Gravsearch. When you retrieve the document, DSP-API converts it back to the
original XML.

To represent overlapping or non-hierarchical markup in exported and imported XML, DSP-API supports
[CLIX](https://web.archive.org/web/20171222112655/http://conferences.idealliance.org/extreme/html/2004/DeRose01/EML2004DeRose01.html) tags.

As XML-to-Standoff has proved to be overly complicated and not very well performing, the use of standoff with custom mappings is discouraged. 
Improved integration of text with XML mark up, particularly TEI-XML, is in planning.
