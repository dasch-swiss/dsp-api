# File Formats in DSP-API

Currently, only a limited number of file formats is accepted to be uploaded onto DSP.
Some metadata is extracted from the files during the ingest but the file formats are not validated.
Only image file formats are currently migrated into another format.
Both, the migrated version of the file and the original are kept.

The following table shows the accepted file formats:

| Category              | Accepted format                          | Converted during ingest?                                                   |
| --------------------- |------------------------------------------|----------------------------------------------------------------------------|
| Text, XML *)          | HTML, JSON, ODD, RNG, TXT, XML, XSD, XSL | No                                                                         |
| Tables                | CSV, XLS, XLSX                           | No                                                                         |
| 2D Images             | JPG, JPEG, JP2, PNG, TIF, TIFF           | Yes, converted to JPEG 2000 by [Sipi](https://github.com/dasch-swiss/sipi) |
| Audio                 | MPEG (MP3), WAV                          | No                                                                         |
| Video                 | MP4                                      | No                                                                         |
| Office                | EPUB, PDF, DOC, DOCX, PPT, PPTX          | No                                                                         |
| Archives              | ZIP, TAR, GZ, Z, TAR.GZ, TGZ, GZIP, 7Z   | No                                                                         |


*) If your XML files represent text with markup (e.g. [TEI/XML](http://www.tei-c.org/)),
it is possible to store it as [Standoff/RDF](standoff-rdf.md),
as described [in the api overview](../03-endpoints/api-v2/text/overview.md).
