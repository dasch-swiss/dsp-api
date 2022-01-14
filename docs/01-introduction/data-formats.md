<!---
 * Copyright © 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
-->

# Data Formats in DSP-API

As explained in [What Is DSP and DSP-API (previous Knora)?](what-is-knora.md), the DSP stores data
in a small number of formats that are suitable for long-term preservation while
facilitating data reuse.

The following is a non-exhaustive list of data formats and how their content
can be stored and managed by DSP-API:

| Original Format                              | Format in DSP                                                                                                              |
|----------------------------------------------|------------------------------------------------------------------------------------------------------------------------------|
| Text (XML, LaTeX, Microsoft Word, etc.)      | [Knora resources](../03-apis/api-v2/editing-resources.md) (RDF) containing [Standoff/RDF](standoff-rdf.md)            |
| Tabular data, including relational databases | [Knora resources](../03-apis/api-v2/editing-resources.md)                                                                  |
| Data in tree or graph structures             | [Knora resources](../03-apis/api-v2/editing-resources.md)                                                                  |
| Images (JPEG, PNG, etc.)                     | JPEG 2000 files stored by [Sipi](https://github.com/dhlab-basel/Sipi)                                                        |
| Audio and video files                        | Audio and video files stored by [Sipi](https://github.com/dhlab-basel/Sipi) (in archival formats to be determined)           |
| PDF                                          | Can be stored by Sipi, but data reuse is improved by extracting the text for storage as [Standoff/RDF](standoff-rdf.md) |
