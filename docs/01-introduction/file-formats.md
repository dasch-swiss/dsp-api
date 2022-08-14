<!---
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
-->

# File Formats in DSP-API

Currently, only a limited number of file formats is accepted to be uploaded onto DSP. Some metadata is extracted from the files during the ingest but the file formats are not validated. Only image file formats are currently migrated into another format. Both, the migrated version of the file and the original are kept.

The following table shows the accepted file formats:

| Category              | Accepted format                | Converted during ingest?                                                   |
| --------------------- |--------------------------------| -------------------------------------------------------------------------- |
| Text, XML<sup>1</sup> | TXT, XML, XSL, XSD             | No                                                                         |
| Tables                | CSV, XLS, XLSX                 | No                                                                         |
| 2D Images             | JPG, JPEG, JP2, PNG, TIF, TIFF | Yes, converted to JPEG 2000 by [Sipi](https://github.com/dasch-swiss/sipi) |
| Audio                 | MPEG (MP3), MP4, WAV           | No                                                                         |
| Video                 | MP4                            | No                                                                         |
| Office                | PDF, DOC, DOCX, PPT, PPTX      | No                                                                         |
| Archives              | ZIP, TAR, ISO, GZ, GZIP, 7Z    | No                                                                         |


1: If your XML files represent text with markup (e.g. [TEI/XML](http://www.tei-c.org/)),
the recommended approach is to allow Knora to store it as [Standoff/RDF](standoff-rdf.md).
