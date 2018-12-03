<!---
Copyright © 2015-2018 the contributors (see Contributors.md).

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
-->

# What Is Knora?

@@toc

Knora (Knowledge Organization, Representation, and Annotation) is a
a content management system for the long-term preservation and reuse of
humanities data. It is designed to accommodate data with a complex internal
structure, including data that could be stored in relational databases.

A key problem in the long-term preservation of data is the multitude of
data formats in use, many of which are proprietary and quickly become obsolete.
It is not practical to maintain all the software that was used to create and
read old data files.

Instead of preserving a multitude of different data formats, Knora supports
the conversion of all sorts of data to a @ref:[small number of formats](data-formats.md)
that are suitable for long-term preservation, and that maintain the data's meaning and
structure:

- Non-binary data is stored as
  [RDF](http://www.w3.org/TR/2014/NOTE-rdf11-primer-20140624/), in a dedicated
  database called a triplestore. RDF is an open, vendor-independent standard
  that can express any data structure.
- Binary media files (images, audio, and video) are converted to a few specialised
  archival file formats and stored by [Sipi](https://github.com/dhlab-basel/Sipi),
  with metadata stored in the triplestore.

Knora then makes this data available for reuse via its generic, standards-based
application programming interfaces (APIs). A virtual research environment
(VRE) can then use these APIs to search, link together, and add to data
from different research projects in a unified way.

## Humanities-Focused Data Storage

Each project creates its own data model (or *ontology*), describing the types of
items it wishes to store, using basic data types defined in Knora's
@ref:[base ontology](../02-knora-ontologies/knora-base.md).
This gives projects the freedom to describe their data in a way that makes
sense to them, while allowing Knora to support searching and linking across projects.

Knora has built-in support for data structures that are commonly needed in
humanities data, and that present unique challenges for any type of database storage.

### Calendar-Independent Dates

In the humanities, a date could be based on any sort of calendar (e.g.
Gregorian, Julian, Islamic, or Hebrew). Knora stores dates using a calendar-independent,
astronomical representation, and converts between calendars as needed. This makes
it possible to search for a date in one calendar, and get search results in other calendars.

### Flexible, Searchable Text Markup

Commonly used text markup systems, such as [TEI/XML](http://www.tei-c.org/),
have to represent a text as a hierarchy, and therefore have trouble supporting
overlapping markup. Knora supports @ref:[Standoff/RDF markup](standoff-rdf.md): the markup is stored
as RDF data, separately from the text, allowing for overlapping markup. Knora's RDF-based standoff
is designed to support the needs of complex digital critical editions. Knora
can import any XML document (including TEI/XML) for storage as standoff/RDF,
and can regenerate the original XML document at any time.

## Powerful Searches

Knora's API provides a search language, @ref:[Gravsearch](../03-apis/api-v2/query-language.md),
that is designed to meet the needs of humanities researchers. Gravsearch supports Knora's
humanites-focused data structures, including calendar-independent dates and standoff markup, as well
as fast full-text searches. This allows searches to combine text-related criteria with any other
criteria. For example, you could search for a text that contains a certain word
and also mentions a person who lived in the same city as another person who is the
author of a text that mentions an event that occurred during a certain time period.

## Access Control

The RDF standards do not include any concept of permissions. Knora's permission
system allows project administrators and users to determine who can see or
modify each item of data. Knora filters search results according to each
user's permissions.

## Data History

RDF does not have a concept of data history. Knora maintains all previous
versions of each item of data. Ordinary searches return only the latest version,
but it is possible to obtain an item as it was at any point in the past.

## Data Consistency

RDF triplestores do not implement a standardised way of ensuring the consistency
of data in a repository. Knora ensures that all data is consistent, conforms
the project-specific data models, and meets Knora's minimum requirements
for interoperability and reusability of data.

## Linked Open Data

Knora supports publishing data online as as [Linked Open Data](http://linkeddata.org/),
using open standards to allow interoperability between different repositories
on the web.

## Build Your Own Application

Knora can be used with a general-purpose, browser-based VRE called
[SALSAH](https://dhlab-basel.github.io/Salsah/). Using the Knora API, a project
can also create its own VRE or project-specific web site, optionally
reusing components from SALSAH.
