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

# XML to Standoff Mapping in API v2

@@toc

## General Information

Please see v1 documentation for general information about the XML to standoff mapping: @ref:[XML To Standoff Mapping in API v1](../api-v1/xml-to-standoff-mapping.md).

## Validating a Mapping and sending it to Knora

A mapping can be validated before sending it to Knora with the following
XML Schema file: `webapi/src/resources/mappingXMLToStandoff.xsd`. Any
mapping that does not conform to this XML Schema file will be rejected
by Knora.

The mapping has to be sent as a multipart request to the standoff route
using the path segment `mapping`:

    HTTP POST http://host/v2/mapping

The multipart request consists of two named parts:

```
"json":

  {
      "knora-api:mappingHasName": "My Mapping",
      "knora-api:attachedToProject": "projectIRI",
      "rdfs:label": "MappingNameSegment",
      "@context": {
          "rdfs": "http://www.w3.org/2000/01/rdf-schema#",
          "knora-api": "http://api.knora.org/ontology/knora-api/v2#"
      }
  }

"xml":

  <?xml version="1.0" encoding="UTF-8"?>
  <mapping>
      ...
  </mapping>
```

A successful response returns the Iri of the mapping. However, the Iri
of a mapping is predictable: it consists of the project Iri followed by
`/mappings/` and the `knora-api:mappingHasName` submitted in the JSON-LD (if the name
already exists, the request will be rejected). Once created, a mapping
can be used to create TextValues in Knora. The formats are documented in
the v2 typescript interfaces `AddMappingRequest` and `AddMappingResponse`
in module `MappingFormats`
