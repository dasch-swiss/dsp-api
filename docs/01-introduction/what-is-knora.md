<!---
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
-->

# What Is DSP and DSP-API (previous Knora)?

The DaSCH Service Platform (DSP) is a
a content management system for the long-term preservation and reuse of
humanities data. It is designed to accommodate data with a complex internal
structure, including data that could be stored in relational databases.

DSP aims to solve key problems in the long-term preservation and reuse
of humanities data:

First, traditional archives preserve data, but do not facilitate reuse. Typically,
only metadata can be searched, not the data itself. You have to first identify
an information package that might be of interest, then download it, and only
then can you find out what's really in it. This is time-consuming, and
makes it impractical to reuse data from many different sources.

DSP solves this problem by keeping the data alive. You can query all the data
in a DSP repository, not just the metadata. You can import thousands of databases into
DSP, and run queries that search through all of them at once.

Another problem is that researchers use a multitude of different file formats, many of
which are proprietary and quickly become obsolete. It is not practical to maintain
all the programs that were used to create and read old files, or even
all the operating systems that these programs ran on. Therefore, DSP only accepts a
certain number of [file formats](file-formats.md).

- Non-binary data is stored as
  [RDF](http://www.w3.org/TR/2014/NOTE-rdf11-primer-20140624/), in a dedicated
  database called a triplestore. RDF is an open, vendor-independent standard
  that can express any data structure.
- Binary media files (images, audio, and video) are converted to a few specialised
  archival file formats and stored by [Sipi](https://github.com/dhlab-basel/Sipi),
  with metadata stored in the triplestore.

DSP then makes this data available for reuse via its generic, standards-based
application programming interfaces (APIs = DSP-API). A virtual research environment
(VRE) can then use these APIs to search, link together, and add to data
from different research projects in a unified way.

## Humanities-Focused Data Storage

Each project creates its own data model (or *ontology*), describing the types of
items it wishes to store, using basic data types defined in Knora's
[base ontology](../02-knora-ontologies/knora-base.md).
This gives projects the freedom to describe their data in a way that makes
sense to them, while allowing DSP to support searching and linking across projects.

DSP has built-in support for data structures that are commonly needed in
humanities data, and that present unique challenges for any type of database storage.

### Calendar-Independent Dates

In the humanities, a date could be based on any sort of calendar (e.g.
Gregorian, Julian, Islamic, or Hebrew). The DSP stores dates using a calendar-independent,
astronomical representation, and converts between calendars as needed. This makes
it possible to search for a date in one calendar, and get search results in other calendars.

### Flexible, Searchable Text Markup

Commonly used text markup systems, such as [TEI/XML](http://www.tei-c.org/),
have to represent a text as a hierarchy, and therefore have trouble supporting
overlapping markup. DSP supports [Standoff/RDF markup](standoff-rdf.md): the markup is stored
as RDF data, separately from the text, allowing for overlapping markup. DSP's RDF-based standoff
is designed to support the needs of complex digital critical editions. The DSP
can import any XML document (including TEI/XML) for storage as standoff/RDF,
and can regenerate the original XML document at any time.

## Powerful Searches

DSP-API provides a search language, [Gravsearch](../03-apis/api-v2/query-language.md),
that is designed to meet the needs of humanities researchers. Gravsearch supports DSP-API's
humanities-focused data structures, including calendar-independent dates and standoff markup, as well
as fast full-text searches. This allows searches to combine text-related criteria with any other
criteria. For example, you could search for a text that contains a certain word
and also mentions a person who lived in the same city as another person who is the
author of a text that mentions an event that occurred during a certain time period.

## Access Control

The RDF standards do not include any concept of permissions. DSP-API's permission
system allows project administrators and users to determine who can see or
modify each item of data. DSP-API filters search results according to each
user's permissions.

## Data History

RDF does not have a concept of data history. DSP-API maintains all previous
versions of each item of data. Ordinary searches return only the latest version,
but you can
[obtain](../03-apis/api-v2/reading-and-searching-resources.md#get-a-full-representation-of-a-version-of-a-resource-by-iri)
and
[cite](../03-apis/api-v2/permalinks.md)
an item as it was at any point in the past.

## Data Consistency

RDF triplestores do not implement a standardised way of ensuring the consistency
of data in a repository. DSP-API ensures that all data is consistent, conforms
the project-specific data models, and meets DSP-API's minimum requirements
for interoperability and reusability of data.

## Linked Open Data

DSP-API supports publishing data online as as [Linked Open Data](http://linkeddata.org/),
using open standards to allow interoperability between different repositories
on the web.

## Build Your Own Application

DSP-API can be used with a general-purpose, browser-based VRE called [DSP-APP](https://dasch-swiss.github.io/dsp-app) or 
[SALSAH](https://dhlab-basel.github.io/Salsah/).
Using the DSP-API and [DSP-JS](https://github.com/dasch-swiss/dsp-js-lib) and/or [DSP-UI](https://github.com/dasch-swiss/dsp-ui-lib), a set of
reusable user-interface components, you can also create your own VRE or project-specific
web site.
