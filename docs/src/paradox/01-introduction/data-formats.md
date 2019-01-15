<!---
Copyright © 2015-2019 the contributors (see Contributors.md).

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

# Data Formats in Knora

@@toc

As explained in @ref:[What Is Knora?](what-is-knora.md), Knora stores data
in a small number of formats that are suitable for long-term preservation while
facilitating data reuse.

The following is a non-exhaustive list of data formats and how their content
can be stored and managed by Knora:

| Original Format                              | Format in Knora                                                                                                              |
|----------------------------------------------|------------------------------------------------------------------------------------------------------------------------------|
| Text (XML, LaTeX, Microsoft Word, etc.)      | @ref:[Knora resources](../03-apis/api-v2/editing-resources.md) (RDF) containing @ref:[Standoff/RDF](standoff-rdf.md)            |
| Tabular data, including relational databases | @ref:[Knora resources](../03-apis/api-v2/editing-resources.md)                                                                  |
| Data in tree or graph structures             | @ref:[Knora resources](../03-apis/api-v2/editing-resources.md)                                                                  |
| Images (JPEG, PNG, etc.)                     | JPEG 2000 files stored by [Sipi](https://github.com/dhlab-basel/Sipi)                                                        |
| Audio and video files                        | Audio and video files stored by [Sipi](https://github.com/dhlab-basel/Sipi) (in archival formats to be determined)           |
| PDF                                          | Can be stored by Sipi, but data reuse is improved by extracting the text for storage as @ref:[Standoff/RDF](standoff-rdf.md) |
