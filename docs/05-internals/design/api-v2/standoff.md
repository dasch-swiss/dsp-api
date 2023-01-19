<!---
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
-->

# Standoff Markup

## Requirements

In Knora, text with markup is stored using standoff markup, i.e. markup that
is stored separately from the content it applies to.

Knora's standoff design is based on these requirements:

- [Overlapping markup](https://en.wikipedia.org/wiki/Overlapping_markup) should be supported.

- Markup should be stored as RDF, so it can be searched and analysed using the same tools that are used
  with other data managed by Knora.

- In particular, [Gravsearch](../../../03-endpoints/api-v2/query-language.md) queries should be able
  to specify search criteria that refer to the markup tags attached to a text, together with
  any other search criteria relating to the resource that contains the text.
    
- It should be possible to import any XML document into Knora, store the markup as standoff, and
  at any time export the document as an equivalent XML document.

## RDF Design

See [Text with Standoff Markup](../../../02-dsp-ontologies/knora-base.md#text-with-standoff-markup).

## Querying Standoff

Since the number of standoff tags that can be attached to a text value is unlimited, standoff is queried
in pages of a limited size, to avoid requesting huge SPARQL query results from the triplestore.

When `ResourcesResponderV2` or `SearchResponderV2` need to return a text value with all its markup,
they first query the text value with at most one page of standoff. If the text value has more than one page of
standoff, `ConstructResponseUtilV2.makeTextValueContentV2` then sends a `GetRemainingStandoffFromTextValueRequestV2`
message to `StandoffResponderV2`, which queries the rest of the standoff in the text value, one page at a time.
The resulting standoff is concatenated together and returned.

To optimise query performance:

- Each text value with standoff has the predicate `knora-base:valueHasMaxStandoffStartIndex`, so that when Knora
  queries a page of standoff, it knows whether it has reached the last page.
  
- The last path component of the IRI of a standoff tag is the integer object of its
  `knora-base:standoffTagHasStartIndex` predicate. When querying standoff, it is necessary to convert
  the IRI objects of `knora-base:standoffTagHasStartParent` and `knora-base:standoffTagHasEndParent` to
  integer indexes (the start indexes of those tags). Including each tag's start index in its IRI makes it
  unnecessary to query the parent tags to determine their start indexes.

## Conversion Between Standoff and XML

`XMLToStandoffUtil` does the low-level conversion of documents between standoff and XML, using a simple
data structure to represent standoff. This data structure knows nothing about RDF, and each standoff tag
contains its XML element name and namespace and those of its attributes.

In Knora, it is possible to define [mappings](../../../03-endpoints/api-v2/xml-to-standoff-mapping.md) to
control how standoff/RDF is converted to XML and vice versa. Different mappings can be used to convert the same
standoff/RDF to different sorts of XML documents. `StandoffTagUtilV2` converts between standoff/RDF and XML using
mappings, delegating the lower-level work to `XMLToStandoffUtil`.
