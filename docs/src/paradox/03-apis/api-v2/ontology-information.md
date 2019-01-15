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

# Querying, Creating, and Updating Ontologies

@@toc

## Querying Ontology Information

Before reading this document, you should have a basic understanding of
Knora API v2 external ontology schemas (see @ref:[API Schema](introduction.md#api-schema)).

Each request returns a single RDF graph, which can be represented in
[JSON-LD](https://json-ld.org/spec/latest/json-ld/),
[Turtle](https://www.w3.org/TR/turtle/),
or [RDF/XML](https://www.w3.org/TR/rdf-syntax-grammar/), using
@extref[HTTP content negotiation](rfc:7231#section-5.3.2) (see
@ref:[Response Formats](introduction.md#response-formats)).

The response format uses prefixes to shorten IRIs, making them more
human-readable. A client may wish to convert these to full IRIs for
processing. This can be done with responses in JSON-LD by using a library
that implements the [JSON-LD API](https://www.w3.org/TR/json-ld-api/)
to compact the document with an empty JSON-LD `@context`.

### Querying Ontology Metadata

Requests for ontology metadata can return information about more than one
ontology, unlike other requests for ontology information. To get metadata
about all ontologies:

```
HTTP GET to http://host/v2/ontologies/metadata
```

The response is in the complex API v2 schema. Sample response:

```jsonld
{
  "@graph" : [ {
    "@id" : "http://0.0.0.0:3333/ontology/00FF/images/v2",
    "@type" : "owl:Ontology",
    "knora-api:attachedToProject" : {
      "@id" : "http://rdfh.ch/projects/00FF"
    },
    "rdfs:label" : "The images demo ontology"
  }, {
    "@id" : "http://0.0.0.0:3333/ontology/0801/beol/v2",
    "@type" : "owl:Ontology",
    "knora-api:attachedToProject" : {
      "@id" : "http://rdfh.ch/projects/yTerZGyxjZVqFMNNKXCDPF"
    },
    "rdfs:label" : "The BEOL ontology"
  }, {
    "@id" : "http://0.0.0.0:3333/ontology/0804/dokubib/v2",
    "@type" : "owl:Ontology",
    "knora-api:attachedToProject" : {
      "@id" : "http://rdfh.ch/projects/0804"
    },
    "rdfs:label" : "The dokubib ontology"
  }, {
    "@id" : "http://api.knora.org/ontology/salsah-gui/v2",
    "@type" : "owl:Ontology",
    "knora-api:attachedToProject" : {
      "@id" : "http://www.knora.org/ontology/knora-base#SystemProject"
    },
    "rdfs:label" : "The salsah-gui ontology"
  }, {
    "@id" : "http://api.knora.org/ontology/standoff/v2",
    "@type" : "owl:Ontology",
    "knora-api:attachedToProject" : {
      "@id" : "http://www.knora.org/ontology/knora-base#SystemProject"
    },
    "rdfs:label" : "The standoff ontology"
  } ],
  "@context" : {
    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
    "owl" : "http://www.w3.org/2002/07/owl#"
  }
}
```

To get metadata about the ontologies that belong to a particular
project:

```
HTTP GET to http://host/v2/ontologies/metadata/PROJECT_IRI
```

The project IRI must be URL-encoded. Example response for the `images` test project
(project IRI `http://rdfh.ch/projects/00FF`):

```jsonld
{
  "@id" : "http://0.0.0.0:3333/ontology/00FF/images/v2",
  "@type" : "owl:Ontology",
  "knora-api:attachedToProject" : {
    "@id" : "http://rdfh.ch/projects/00FF"
  },
  "rdfs:label" : "The images demo ontology",
  "@context" : {
    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
    "owl" : "http://www.w3.org/2002/07/owl#"
  }
}
```

### Querying an Ontology

An ontology can be queried either by using an API route directly or by
simply dereferencing the ontology IRI. The API route is as follows:

```
HTTP GET to http://host/v2/ontologies/allentities/ONTOLOGY_IRI
```

The ontology IRI must be URL-encoded, and may be in either the complex
or the simple schema. The response will be in the same schema. For
example, if the server is running on `0.0.0.0:3333`, you can request
the `knora-api` ontology in the complex schema as follows:

```
HTTP GET to http://0.0.0.0:3333/v2/ontologies/allentities/http%3A%2F%2Fapi.knora.org%2Fontology%2Fknora-api%2Fv2
```

By default, this returns the ontology in JSON-LD; to request Turtle
or RDF/XML, add an HTTP `Accept` header
(see @ref:[Response Formats](introduction.md#response-formats)).

If the client dereferences a project-specific ontology IRI as a URL, the
Knora API server running on the hostname in the IRI will serve the
ontology. For example, if the server is running on `0.0.0.0:3333`, the
IRI `http://0.0.0.0:3333/ontology/00FF/images/simple/v2` can be
dereferenced to request the `images` sample ontology in the simple
schema.

If the client dereferences a built-in Knora ontology, such as
`http://api.knora.org/ontology/knora-api/simple/v2`, there must be a
Knora API server running at `api.knora.org` that can serve the ontology.
The [DaSCH](http://dasch.swiss/) intends to run such as server. For
testing, you can configure your local `/etc/hosts` file to resolve
`api.knora.org` as `localhost`.

#### Differences Between Internal and External Ontologies

The external ontologies used by Knora API v2 are different to the internal
ontologies that are actually stored in the triplestore (see
@ref:[API Schema](introduction.md#api-schema)). In general, the external
ontologies use simpler data structures, but they also provide additional
information to make it easier for clients to use them. This is illustrated
in the examples in the next sections.

The internal predicates `knora-base:subjectClassConstraint` and
`knora-base:objectClassConstraint` (see
@ref:[Constraints on the Types of Property Subjects and Objects](../../02-knora-ontologies/knora-base.md#constraints-on-the-types-of-property-subjects-and-objects))
are represented as `knora-api:subjectType` and `knora-api:objectType` in
external ontologies.

#### JSON-LD Representation of an Ontology in the Simple Schema

The simple schema is suitable for client applications that need to read
but not update data in Knora. For example, here is the response for the
`images` sample ontology in the simple schema,
`http://0.0.0.0:3333/ontology/00FF/images/simple/v2` (simplified for
clarity):

```jsonld
{
  "@id" : "http://0.0.0.0:3333/ontology/00FF/images/simple/v2",
  "@type" : "owl:Ontology",
  "rdfs:label" : "The images demo ontology",
  "@graph" : [ {
    "@id" : "images:bild",
    "@type" : "owl:Class",
    "knora-api:resourceIcon" : "bild.png",
    "rdfs:comment" : "An image of the demo image collection",
    "rdfs:label" : "Image",
    "rdfs:subClassOf" : [ {
      "@id" : "knora-api:StillImageRepresentation"
    }, {
      "@type" : "owl:Restriction",
      "owl:cardinality" : 1,
      "owl:onProperty" : {
        "@id" : "knora-api:creationDate"
      }
    }, {
      "@type" : "owl:Restriction",
      "owl:minCardinality" : 0,
      "owl:onProperty" : {
        "@id" : "knora-api:hasIncomingLink"
      }
    }, {
      "@type" : "owl:Restriction",
      "owl:minCardinality" : 0,
      "owl:onProperty" : {
        "@id" : "knora-api:hasStandoffLinkTo"
      }
    }, {
      "@type" : "owl:Restriction",
      "owl:minCardinality" : 1,
      "owl:onProperty" : {
        "@id" : "knora-api:hasStillImageFile"
      }
    }, {
      "@type" : "owl:Restriction",
      "owl:maxCardinality" : 1,
      "owl:onProperty" : {
        "@id" : "knora-api:lastModificationDate"
      }
    }, {
      "@type" : "owl:Restriction",
      "owl:cardinality" : 1,
      "owl:onProperty" : {
        "@id" : "rdfs:label"
      }
    }, {
      "@type" : "owl:Restriction",
      "owl:cardinality" : 1,
      "owl:onProperty" : {
        "@id" : "images:description"
      }
    }, {
      "@type" : "owl:Restriction",
      "owl:cardinality" : 1,
      "owl:onProperty" : {
        "@id" : "images:erfassungsdatum"
      }
    }, {
      "@type" : "owl:Restriction",
      "owl:maxCardinality" : 1,
      "owl:onProperty" : {
        "@id" : "images:urheber"
      }
    } ]
  }, {
    "@id" : "images:description",
    "@type" : "owl:DatatypeProperty",
    "knora-api:objectType" : {
      "@id" : "xsd:string"
    },
    "knora-api:subjectType" : {
      "@id" : "images:bild"
    },
    "rdfs:label" : "Description",
    "rdfs:subPropertyOf" : [ {
      "@id" : "knora-api:hasValue"
    }, {
      "@id" : "http://purl.org/dc/terms/description"
    } ]
  }, {
    "@id" : "images:erfassungsdatum",
    "@type" : "owl:DatatypeProperty",
    "knora-api:objectType" : {
      "@id" : "knora-api:Date"
    },
    "knora-api:subjectType" : {
      "@id" : "images:bild"
    },
    "rdfs:label" : "Date of acquisition",
    "rdfs:subPropertyOf" : [ {
      "@id" : "knora-api:hasValue"
    }, {
      "@id" : "http://purl.org/dc/terms/date"
    } ]
  }, {
    "@id" : "images:firstname",
    "@type" : "owl:DatatypeProperty",
    "knora-api:objectType" : {
      "@id" : "xsd:string"
    },
    "knora-api:subjectType" : {
      "@id" : "images:person"
    },
    "rdfs:comment" : "First name of a person",
    "rdfs:label" : "First name",
    "rdfs:subPropertyOf" : {
      "@id" : "knora-api:hasValue"
    }
  }, {
    "@id" : "images:lastname",
    "@type" : "owl:DatatypeProperty",
    "knora-api:objectType" : {
      "@id" : "xsd:string"
    },
    "knora-api:subjectType" : {
      "@id" : "images:person"
    },
    "rdfs:comment" : "Last name of a person",
    "rdfs:label" : "Name",
    "rdfs:subPropertyOf" : {
      "@id" : "knora-api:hasValue"
    }
  }, {
    "@id" : "images:person",
    "@type" : "owl:Class",
    "knora-api:resourceIcon" : "person.png",
    "rdfs:comment" : "Person",
    "rdfs:label" : "Person",
    "rdfs:subClassOf" : [ {
      "@id" : "knora-api:Resource"
    }, {
      "@type" : "owl:Restriction",
      "owl:cardinality" : 1,
      "owl:onProperty" : {
        "@id" : "knora-api:creationDate"
      }
    }, {
      "@type" : "owl:Restriction",
      "owl:minCardinality" : 0,
      "owl:onProperty" : {
        "@id" : "knora-api:hasIncomingLink"
      }
    }, {
      "@type" : "owl:Restriction",
      "owl:minCardinality" : 0,
      "owl:onProperty" : {
        "@id" : "knora-api:hasStandoffLinkTo"
      }
    }, {
      "@type" : "owl:Restriction",
      "owl:maxCardinality" : 1,
      "owl:onProperty" : {
        "@id" : "knora-api:lastModificationDate"
      }
    }, {
      "@type" : "owl:Restriction",
      "owl:cardinality" : 1,
      "owl:onProperty" : {
        "@id" : "rdfs:label"
      }
    }, {
      "@type" : "owl:Restriction",
      "owl:cardinality" : 1,
      "owl:onProperty" : {
        "@id" : "images:lastname"
      }
    }, {
      "@type" : "owl:Restriction",
      "owl:cardinality" : 1,
      "owl:onProperty" : {
        "@id" : "images:firstname"
      }
    } ]
  }, {
    "@id" : "images:urheber",
    "@type" : "owl:ObjectProperty",
    "knora-api:objectType" : {
      "@id" : "images:person"
    },
    "knora-api:subjectType" : {
      "@id" : "images:bild"
    },
    "rdfs:comment" : "An entity primarily responsible for making the resource. Examples of a Creator include a person, an organization, or a service. Typically, the name of a Creator should be used to indicate the entity.",
    "rdfs:label" : "Creator",
    "rdfs:subPropertyOf" : {
      "@id" : "knora-api:hasLinkTo"
    }
  } ],
  "@context" : {
    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
    "images" : "http://0.0.0.0:3333/ontology/00FF/images/simple/v2#",
    "knora-api" : "http://api.knora.org/ontology/knora-api/simple/v2#",
    "owl" : "http://www.w3.org/2002/07/owl#",
    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
    "xsd" : "http://www.w3.org/2001/XMLSchema#"
  }
}
```

The response format is an RDF graph. The top level object describes the ontology
itself, providing its IRI (in the `@id` member) and its `rdfs:label`.
The `@graph` member (see
[Named Graphs](https://json-ld.org/spec/latest/json-ld/#named-graphs) in the
JSON-LD specification) contains an array of entities that belong to the
ontology.

In a class definition, cardinalities for properties of the class are
represented as in OWL, using objects of type `owl:Restriction`. The
supported cardinalities are the ones indicated in
@ref:[OWL Cardinalities](../../02-knora-ontologies/knora-base.md#owl-cardinalities).

The class definitions include cardinalities that are directly defined on
each class, as well as cardinalities inherited from base classes. For
example, we can see cardinalities inherited from `knora-api:Resource`,
such as `knora-api:hasStandoffLinkTo` and `http://schema.org/name`
(which represents `rdfs:label`).

In the simple schema, Knora value properties can be datatype properties.
The `knora-base:objectType` of a Knora value property such as
`images:description` is a literal datatype, in this case
`xsd:string`. Moreover, `images:description` is a subproperty of
the standard property `dcterms:description`, whose object can be a
literal value. A client that understands `rdfs:subPropertyOf`, and is
familiar with `dcterms:description`, can then work with
`images:description` on the basis of its knowledge about
`dcterms:description`.

By default, values for `rdfs:label` and `rdfs:comment` are returned only
in the user's preferred language, or in the system default language. To
obtain these values in all available languages, add the URL parameter
`?allLanguages=true`. For example, with this parameter, the definition
of `images:description` becomes:

```jsonld
{
  "@id" : "images:description",
  "@type" : "owl:DatatypeProperty",
  "knora-api:objectType" : {
    "@id" : "xsd:string"
  },
  "knora-api:subjectType" : {
    "@id" : "images:bild"
  },
  "rdfs:label" : [ {
    "@language" : "en",
    "@value" : "Description"
  }, {
    "@language" : "de",
    "@value" : "Beschreibung"
  }, {
    "@language" : "fr",
    "@value" : "Description"
  }, {
    "@language" : "it",
    "@value" : "Descrizione"
  } ],
  "rdfs:subPropertyOf" : [ {
    "@id" : "knora-api:hasValue"
  }, {
    "@id" : "http://purl.org/dc/terms/description"
  } ]
}
```

To find out more about the `knora-api` entities used in the response,
the client can request the `knora-api` ontology in the simple schema:
`http://api.knora.org/ontology/knora-api/simple/v2`. For example,
`images:erfassungsdatum` has a `knora-api:objectType` of
`knora-api:Date`, which is a subtype of `xsd:string` with a
Knora-specific, human-readable format. In the `knora-api` simple
ontology, there is a definition of this type:

```jsonld
{
  "@id" : "http://api.knora.org/ontology/knora-api/simple/v2",
  "@type" : "owl:Ontology",
  "rdfs:label" : "The knora-api ontology in the simple schema",
  "@graph" : [ {
    "@id" : "knora-api:Date",
    "@type" : "rdfs:Datatype",
    "rdfs:comment" : "Represents a date as a period with different possible precisions.",
    "rdfs:label" : "Date literal",
    "rdfs:subClassOf" : {
      "@type" : "rdfs:Datatype",
      "owl:onDatatype" : {
        "@id" : "xsd:string"
      },
      "owl:withRestrictions" : {
        "xsd:pattern" : "(GREGORIAN|JULIAN):\\d{1,4}(-\\d{1,2}(-\\d{1,2})?)?( BC| AD| BCE| CE)?(:\\d{1,4}(-\\d{1,2}(-\\d{1,2})?)?( BC| AD| BCE| CE)?)?"
      }
    }
  } ],
  "@context" : {
    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
    "knora-api" : "http://api.knora.org/ontology/knora-api/simple/v2#",
    "owl" : "http://www.w3.org/2002/07/owl#",
    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
    "xsd" : "http://www.w3.org/2001/XMLSchema#"
  }
}
```

#### JSON-LD Representation of an Ontology in the Complex Schema

The complex schema is suitable for client applications that need to
update data in Knora. For example, here is the response for the `images`
sample ontology in the complex schema, `http://0.0.0.0:3333/ontology/00FF/images/v2`
(simplified for clarity):

```jsonld
{
  "@id" : "http://0.0.0.0:3333/ontology/00FF/images/v2",
  "@type" : "owl:Ontology",
  "knora-api:attachedToProject" : {
    "@id" : "http://rdfh.ch/projects/00FF"
  },
  "rdfs:label" : "The images demo ontology",
  "@graph" : [ {
    "@id" : "images:bild",
    "@type" : "owl:Class",
    "knora-api:canBeInstantiated" : true,
    "knora-api:isResourceClass" : true,
    "knora-api:resourceIcon" : "bild.png",
    "rdfs:comment" : "An image of the demo image collection",
    "rdfs:label" : "Image",
    "rdfs:subClassOf" : [ {
      "@id" : "knora-api:StillImageRepresentation"
    }, {
      "@type" : "owl:Restriction",
      "knora-api:isInherited" : true,
      "owl:cardinality" : 1,
      "owl:onProperty" : {
        "@id" : "knora-api:attachedToProject"
      }
    }, {
      "@type" : "owl:Restriction",
      "knora-api:isInherited" : true,
      "owl:cardinality" : 1,
      "owl:onProperty" : {
        "@id" : "knora-api:attachedToUser"
      }
    }, {
      "@type" : "owl:Restriction",
      "knora-api:isInherited" : true,
      "owl:cardinality" : 1,
      "owl:onProperty" : {
        "@id" : "knora-api:creationDate"
      }
    }, {
      "@type" : "owl:Restriction",
      "knora-api:isInherited" : true,
      "owl:minCardinality" : 0,
      "owl:onProperty" : {
        "@id" : "knora-api:hasIncomingLink"
      }
    }, {
      "@type" : "owl:Restriction",
      "knora-api:isInherited" : true,
      "owl:cardinality" : 1,
      "owl:onProperty" : {
        "@id" : "knora-api:hasPermissions"
      }
    }, {
      "@type" : "owl:Restriction",
      "knora-api:isInherited" : true,
      "owl:minCardinality" : 0,
      "owl:onProperty" : {
        "@id" : "knora-api:hasStandoffLinkTo"
      }
    }, {
      "@type" : "owl:Restriction",
      "knora-api:isInherited" : true,
      "owl:minCardinality" : 0,
      "owl:onProperty" : {
        "@id" : "knora-api:hasStandoffLinkToValue"
      }
    }, {
      "@type" : "owl:Restriction",
      "knora-api:isInherited" : true,
      "owl:minCardinality" : 1,
      "owl:onProperty" : {
        "@id" : "knora-api:hasStillImageFileValue"
      }
    }, {
      "@type" : "owl:Restriction",
      "knora-api:isInherited" : true,
      "owl:maxCardinality" : 1,
      "owl:onProperty" : {
        "@id" : "knora-api:lastModificationDate"
      }
    }, {
      "@type" : "owl:Restriction",
      "knora-api:isInherited" : true,
      "owl:cardinality" : 1,
      "owl:onProperty" : {
        "@id" : "rdfs:label"
      }
    }, {
      "@type" : "owl:Restriction",
      "salsah-gui:guiOrder" : 3,
      "owl:cardinality" : 1,
      "owl:onProperty" : {
        "@id" : "images:description"
      }
    }, {
      "@type" : "owl:Restriction",
      "salsah-gui:guiOrder" : 8,
      "owl:cardinality" : 1,
      "owl:onProperty" : {
        "@id" : "images:erfassungsdatum"
      }
    }, {
      "@type" : "owl:Restriction",
      "salsah-gui:guiOrder" : 12,
      "owl:maxCardinality" : 1,
      "owl:onProperty" : {
        "@id" : "images:urheber"
      }
    }, {
      "@type" : "owl:Restriction",
      "salsah-gui:guiOrder" : 12,
      "owl:maxCardinality" : 1,
      "owl:onProperty" : {
        "@id" : "images:urheberValue"
      }
    } ]
  }, {
    "@id" : "images:description",
    "@type" : "owl:ObjectProperty",
    "knora-api:isEditable" : true,
    "knora-api:isResourceProperty" : true,
    "knora-api:objectType" : {
      "@id" : "knora-api:TextValue"
    },
    "knora-api:subjectType" : {
      "@id" : "images:bild"
    },
    "salsah-gui:guiAttribute" : [ "rows=10", "width=95%", "wrap=soft" ],
    "salsah-gui:guiElement" : {
      "@id" : "salsah-gui:Textarea"
    },
    "rdfs:label" : "Description",
    "rdfs:subPropertyOf" : [ {
      "@id" : "knora-api:hasValue"
    }, {
      "@id" : "http://purl.org/dc/terms/description"
    } ]
  }, {
    "@id" : "images:erfassungsdatum",
    "@type" : "owl:ObjectProperty",
    "knora-api:isEditable" : true,
    "knora-api:isResourceProperty" : true,
    "knora-api:objectType" : {
      "@id" : "knora-api:DateValue"
    },
    "knora-api:subjectType" : {
      "@id" : "images:bild"
    },
    "salsah-gui:guiElement" : {
      "@id" : "salsah-gui:Date"
    },
    "rdfs:label" : "Date of acquisition",
    "rdfs:subPropertyOf" : [ {
      "@id" : "knora-api:hasValue"
    }, {
      "@id" : "http://purl.org/dc/terms/date"
    } ]
  }, {
    "@id" : "images:firstname",
    "@type" : "owl:ObjectProperty",
    "knora-api:isEditable" : true,
    "knora-api:isResourceProperty" : true,
    "knora-api:objectType" : {
      "@id" : "knora-api:TextValue"
    },
    "knora-api:subjectType" : {
      "@id" : "images:person"
    },
    "salsah-gui:guiAttribute" : [ "maxlength=32", "size=32" ],
    "salsah-gui:guiElement" : {
      "@id" : "salsah-gui:SimpleText"
    },
    "rdfs:comment" : "First name of a person",
    "rdfs:label" : "First name",
    "rdfs:subPropertyOf" : {
      "@id" : "knora-api:hasValue"
    }
  }, {
    "@id" : "images:lastname",
    "@type" : "owl:ObjectProperty",
    "knora-api:isEditable" : true,
    "knora-api:isResourceProperty" : true,
    "knora-api:objectType" : {
      "@id" : "knora-api:TextValue"
    },
    "knora-api:subjectType" : {
      "@id" : "images:person"
    },
    "salsah-gui:guiAttribute" : [ "maxlength=32", "size=32" ],
    "salsah-gui:guiElement" : {
      "@id" : "salsah-gui:SimpleText"
    },
    "rdfs:comment" : "Last name of a person",
    "rdfs:label" : "Name",
    "rdfs:subPropertyOf" : {
      "@id" : "knora-api:hasValue"
    }
  }, {
    "@id" : "images:person",
    "@type" : "owl:Class",
    "knora-api:canBeInstantiated" : true,
    "knora-api:isResourceClass" : true,
    "knora-api:resourceIcon" : "person.png",
    "rdfs:comment" : "Person",
    "rdfs:label" : "Person",
    "rdfs:subClassOf" : [ {
      "@id" : "knora-api:Resource"
    }, {
      "@type" : "owl:Restriction",
      "knora-api:isInherited" : true,
      "owl:cardinality" : 1,
      "owl:onProperty" : {
        "@id" : "knora-api:attachedToProject"
      }
    }, {
      "@type" : "owl:Restriction",
      "knora-api:isInherited" : true,
      "owl:cardinality" : 1,
      "owl:onProperty" : {
        "@id" : "knora-api:attachedToUser"
      }
    }, {
      "@type" : "owl:Restriction",
      "knora-api:isInherited" : true,
      "owl:cardinality" : 1,
      "owl:onProperty" : {
        "@id" : "knora-api:creationDate"
      }
    }, {
      "@type" : "owl:Restriction",
      "knora-api:isInherited" : true,
      "owl:minCardinality" : 0,
      "owl:onProperty" : {
        "@id" : "knora-api:hasIncomingLink"
      }
    }, {
      "@type" : "owl:Restriction",
      "knora-api:isInherited" : true,
      "owl:cardinality" : 1,
      "owl:onProperty" : {
        "@id" : "knora-api:hasPermissions"
      }
    }, {
      "@type" : "owl:Restriction",
      "knora-api:isInherited" : true,
      "owl:minCardinality" : 0,
      "owl:onProperty" : {
        "@id" : "knora-api:hasStandoffLinkTo"
      }
    }, {
      "@type" : "owl:Restriction",
      "knora-api:isInherited" : true,
      "owl:minCardinality" : 0,
      "owl:onProperty" : {
        "@id" : "knora-api:hasStandoffLinkToValue"
      }
    }, {
      "@type" : "owl:Restriction",
      "knora-api:isInherited" : true,
      "owl:maxCardinality" : 1,
      "owl:onProperty" : {
        "@id" : "knora-api:lastModificationDate"
      }
    }, {
      "@type" : "owl:Restriction",
      "knora-api:isInherited" : true,
      "owl:cardinality" : 1,
      "owl:onProperty" : {
        "@id" : "rdfs:label"
      }
    }, {
      "@type" : "owl:Restriction",
      "salsah-gui:guiOrder" : 0,
      "owl:cardinality" : 1,
      "owl:onProperty" : {
        "@id" : "images:lastname"
      }
    }, {
      "@type" : "owl:Restriction",
      "salsah-gui:guiOrder" : 1,
      "owl:cardinality" : 1,
      "owl:onProperty" : {
        "@id" : "images:firstname"
      }
    } ]
  }, {
    "@id" : "images:urheber",
    "@type" : "owl:ObjectProperty",
    "knora-api:isEditable" : true,
    "knora-api:isLinkProperty" : true,
    "knora-api:isResourceProperty" : true,
    "knora-api:objectType" : {
      "@id" : "images:person"
    },
    "knora-api:subjectType" : {
      "@id" : "images:bild"
    },
    "salsah-gui:guiAttribute" : "numprops=2",
    "salsah-gui:guiElement" : {
      "@id" : "salsah-gui:Searchbox"
    },
    "rdfs:comment" : "An entity primarily responsible for making the resource. Examples of a Creator include a person, an organization, or a service. Typically, the name of a Creator should be used to indicate the entity.",
    "rdfs:label" : "Creator",
    "rdfs:subPropertyOf" : {
      "@id" : "knora-api:hasLinkTo"
    }
  }, {
    "@id" : "images:urheberValue",
    "@type" : "owl:ObjectProperty",
    "knora-api:isEditable" : true,
    "knora-api:isLinkValueProperty" : true,
    "knora-api:isResourceProperty" : true,
    "knora-api:objectType" : {
      "@id" : "knora-api:LinkValue"
    },
    "knora-api:subjectType" : {
      "@id" : "images:bild"
    },
    "salsah-gui:guiAttribute" : "numprops=2",
    "salsah-gui:guiElement" : {
      "@id" : "salsah-gui:Searchbox"
    },
    "rdfs:comment" : "An entity primarily responsible for making the resource. Examples of a Creator include a person, an organization, or a service. Typically, the name of a Creator should be used to indicate the entity.",
    "rdfs:label" : "Creator",
    "rdfs:subPropertyOf" : {
      "@id" : "knora-api:hasLinkToValue"
    }
  } ],
  "@context" : {
    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
    "images" : "http://0.0.0.0:3333/ontology/00FF/images/v2#",
    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
    "owl" : "http://www.w3.org/2002/07/owl#",
    "salsah-gui" : "http://api.knora.org/ontology/salsah-gui/v2#",
    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
    "xsd" : "http://www.w3.org/2001/XMLSchema#"
  }
}
```

In the complex schema, all Knora value properties are object properties,
whose objects are IRIs, each of which uniquely identifies a value that
contains metadata and can potentially be edited. The
`knora-base:objectType` of a Knora value property such as
`images:description` is a Knora value class, in this case
`knora-api:TextValue`. Similarly, `images:erfassungsdatum` has a
`knora-api:objectType` of `knora-api:DateValue`, which has a more
complex structure than the `knora-api:Date` datatype shown in the
previous section. A client can find out more about these value classes
by requesting the `knora-api` ontology in the complex schema,
`http://api.knora.org/ontology/knora-api/v2`.

Moreover, additional information is provided in the complex schema, to
help clients that wish to create or update resources and values. A Knora
resource class that can be instantiated is identified with the boolean
properties `knora-api:isResourceClass` and
`knora-api:canBeInstantiated`, to distinguish it from built-in abstract
classes. Knora resource properties whose values can be edited by clients
are identified with `knora-api:isResourceProperty` and
`knora-api:isEditable`, to distinguish them from properties whose values
are maintained automatically by Knora. Link value
properties are shown along with link properties, because a client that
updates links will need the IRIs of their link values. The predicate
`salsah-gui:guiOrder` tells a GUI client in what order to display the
properties of a class, and the predicates `salsah-gui:guiElement` and
`salsah-gui:guiAttribute` specify how to configure a GUI element for
editing the value of a property. For more information on the
`salsah-gui` ontology, see @ref:[The SALSAH GUI Ontology](../../02-knora-ontologies/salsah-gui.md).

## Ontology Updates

The ontology update API must ensure that the ontologies it creates are
valid and consistent, and that existing data is not invalidated by a
change to an ontology. To make this easier to enforce, the ontology
update API allows only one entity to be created or modified at a time.
It is not possible to submit an entire ontology all at once. Each
update request is a JSON-LD document providing only the information that is
relevant to the update.

Moreover, the API enforces the following rules:

  - An entity (i.e. a class or property) cannot be referred to until it
    has been created.
  - An entity cannot be modified or deleted if it is used in data,
    except for changes to its `rdfs:label` or `rdfs:comment`.
  - An entity cannot be modified if another entity refers to it, with
    one exception: a `knora-api:subjectType` or `knora-api:objectType`
    that refers to a class will not prevent the class's cardinalities
    from being modified.

Because of these rules, some operations have to be done in a specific
order:

  - Properties have to be defined before they can be used in the
    cardinalities of a class, but a property's `knora-api:subjectType`
    cannot refer to a class that does not yet exist. The recommended
    approach is to first create a class with no cardinalities, then
    create the properties that it needs, then add cardinalities for
    those properties to the class.
  - To delete a class along with its properties, the client must first
    remove the cardinalities from the class, then delete the property
    definitions, then delete the class definition.

When changing an existing ontology, the client must always supply the
ontology's `knora-api:lastModificationDate`, which is returned in the
response to each update. If user A attempts to update an ontology, but
user B has already updated it since the last time user A received the
ontology's `knora-api:lastModificationDate`, user A's update will be
rejected with an HTTP 409 Conflict error. This means that it is possible
for two different users to work concurrently on the same ontology, but
this is discouraged since it is likely to lead to confusion.

An ontology can be created or updated only by a system administrator, or
by a project administrator in the ontology's project.

Ontology updates always use the complex schema.

### Creating a New Ontology

An ontology is always created within a particular project.

```
HTTP POST to http://host/v2/ontologies
```

```jsonld
{
  "knora-api:ontologyName" : "ONTOLOGY_NAME",
  "knora-api:attachedToProject" : {
    "@id" : "PROJECT_IRI",
  },
  "rdfs:label" : "ONTOLOGY_NAME",
  "@context" : {
    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#"
  }
}
```

The ontology name must follow the rules given in
@ref:[Knora IRIs](knora-iris.md).

If the ontology is to be shared by multiple projects, it must be
created in the default shared ontologies project,
`http://www.knora.org/ontology/knora-base#DefaultSharedOntologiesProject`,
and the request must have this additional boolean property:

```
"knora-api:isShared" : true
```

See @ref:[Shared Ontologies](knora-iris.md#shared-ontologies) for details about
shared ontologies.

A successful response will be a JSON-LD document providing only the
ontology's metadata, which includes the ontology's IRI. When the client
makes further requests to create entities (classes and properties) in
the ontology, it must construct entity IRIs by concatenating the
ontology IRI, a `#` character, and the entity name. An entity name must
be a valid XML [NCName](https://www.w3.org/TR/xml-names/#NT-NCName).

### Changing an Ontology's Metadata

Currently, the only modifiable ontology metadata is the ontology's
`rdfs:label`.

```
HTTP PUT to http://host/v2/ontologies/metadata
```

```jsonld
{
  "@id": "ONTOLOGY_IRI",
  "rdfs:label": "NEW_ONTOLOGY_LABEL",
  "knora-api:lastModificationDate": "ONTOLOGY_LAST_MODIFICATION_DATE",
  "@context": {
    "rdfs": "http://www.w3.org/2000/01/rdf-schema#",
    "knora-api": "http://api.knora.org/ontology/knora-api/v2#"
  }
}
```

A successful response will be a JSON-LD document providing only the
ontology's metadata.

### Deleting an Ontology

An ontology can be deleted only if it is not used in data.

```
HTTP DELETE to http://host/v2/ontologies/ONTOLOGY_IRI?lastModificationDate=ONTOLOGY_LAST_MODIFICATION_DATE
```

The ontology IRI and the ontology's last modification date must be
URL-encoded.

A successful response will be a JSON-LD document containing a
confirmation message.

### Creating a Class Without Cardinalities

```
HTTP POST to http://host/v2/ontologies/classes
```

```jsonld
{
  "@id" : "ONTOLOGY_IRI",
  "@type" : "owl:Ontology",
  "knora-api:lastModificationDate" : "ONTOLOGY_LAST_MODIFICATION_DATE",
  "@graph" : [ {
    "CLASS_IRI" : {
      "@id" : "CLASS_IRI",
      "@type" : "owl:Class",
      "rdfs:label" : {
        "@language" : "LANGUAGE_CODE",
        "@value" : "LABEL"
      },
      "rdfs:comment" : {
        "@language" : "LANGUAGE_CODE",
        "@value" : "COMMENT"
      },
      "rdfs:subClassOf" : {
        "@id" : "BASE_CLASS_IRI"
      }
    }
  } ],
  "@context" : {
    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
    "owl" : "http://www.w3.org/2002/07/owl#",
    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
    "xsd" : "http://www.w3.org/2001/XMLSchema#"
  }
}
```

Values for `rdfs:label` and `rdfs:comment` must be submitted in at least
one language, either as an object or as an array of objects.

At least one base class must be provided, which can be
`knora-api:Resource` or any of its subclasses.

A successful response will be a JSON-LD document providing the new class
definition (but not any of the other entities in the ontology).

### Creating a Class With Cardinalities

This can work if the new class will have cardinalities for properties
that have no `knora-api:subjectType`, or if the new class will be a
subclass of their `knora-api:subjectType`.

```
HTTP POST to http://host/v2/ontologies/classes
```

```jsonld
{
  "@id" : "ONTOLOGY_IRI",
  "@type" : "owl:Ontology",
  "knora-api:lastModificationDate" : "ONTOLOGY_LAST_MODIFICATION_DATE",
  "@graph" : [ {
    "CLASS_IRI" : {
      "@id" : "CLASS_IRI",
      "@type" : "owl:Class",
      "rdfs:label" : {
        "@language" : "LANGUAGE_CODE",
        "@value" : "LABEL"
      },
      "rdfs:comment" : {
        "@language" : "LANGUAGE_CODE",
        "@value" : "COMMENT"
      },
      "rdfs:subClassOf" : [ {
        "@id" : "BASE_CLASS_IRI"
      }, {
        "@type": "owl:Restriction",
        "OWL_CARDINALITY_PREDICATE": "OWL_CARDINALITY_VALUE",
        "owl:onProperty": {
          "@id" : "PROPERTY_IRI"
        }
      } ]
    }
  } ],
  "@context" : {
    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
    "owl" : "http://www.w3.org/2002/07/owl#",
    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
    "xsd" : "http://www.w3.org/2001/XMLSchema#"
  }
}
```

`OWL_CARDINALITY_PREDICATE` and `OWL_CARDINALITY_VALUE` must correspond
to the supported combinations given in
@ref:[OWL Cardinalities](../../02-knora-ontologies/knora-base.md#owl-cardinalities). (The placeholder
`OWL_CARDINALITY_VALUE` is shown here in quotes, but it should be an
unquoted integer.)

Values for `rdfs:label` and `rdfs:comment` must be submitted in at least
one language, either as an object or as an array of objects.

At least one base class must be provided.

When a cardinality on a link property is submitted, an identical cardinality
on the corresponding link value property is automatically added (see
@ref:[Links Between Resources](../../02-knora-ontologies/knora-base.md#links-between-resources)).

A successful response will be a JSON-LD document providing the new class
definition (but not any of the other entities in the ontology).

### Changing the Labels of a Class

This operation is permitted even if the class is used in data.

```
HTTP PUT to http://host/v2/ontologies/classes
```

```jsonld
{
  "@id" : "ONTOLOGY_IRI",
  "@type" : "owl:Ontology",
  "knora-api:lastModificationDate" : "ONTOLOGY_LAST_MODIFICATION_DATE",
  "@graph" : [ {
    "CLASS_IRI" : {
      "@id" : "CLASS_IRI",
      "@type" : "owl:Class",
      "rdfs:label" : {
        "@language" : "LANGUAGE_CODE",
        "@value" : "LABEL"
      }
    }
  } ],
  "@context" : {
    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
    "owl" : "http://www.w3.org/2002/07/owl#",
    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
    "xsd" : "http://www.w3.org/2001/XMLSchema#"
  }
}
```

Values for `rdfs:label` must be submitted in at least one language,
either as an object or as an array of objects. The submitted labels will
replace the existing ones.

### Changing the Comments of a Class

This operation is permitted even if the class is used in data.

```
HTTP PUT to http://host/v2/ontologies/classes
```

```jsonld
{
  "@id" : "ONTOLOGY_IRI",
  "@type" : "owl:Ontology",
  "knora-api:lastModificationDate" : "ONTOLOGY_LAST_MODIFICATION_DATE",
  "@graph" : [ {
    "CLASS_IRI" : {
      "@id" : "CLASS_IRI",
      "@type" : "owl:Class",
      "rdfs:comment" : {
        "@language" : "LANGUAGE_CODE",
        "@value" : "COMMENT"
      }
    }
  } ],
  "@context" : {
    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
    "owl" : "http://www.w3.org/2002/07/owl#",
    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
    "xsd" : "http://www.w3.org/2001/XMLSchema#"
  }
}
```

Values for `rdfs:comment` must be submitted in at least one language,
either as an object or as an array of objects. The submitted comments
will replace the existing ones.

### Creating a Property

```
HTTP POST to http://host/v2/ontologies/properties
```

```jsonld
{
  "@id" : "ONTOLOGY_IRI",
  "@type" : "owl:Ontology",
  "knora-api:lastModificationDate" : "ONTOLOGY_LAST_MODIFICATION_DATE",
  "@graph" : [ {
    "PROPERTY_IRI" : {
      "@id" : "PROPERTY_IRI",
      "@type" : "owl:ObjectProperty",
      "knora-api:subjectType" : {
        "@id" : "SUBJECT_TYPE"
      },
      "knora-api:objectType" : {
        "@id" : "OBJECT_TYPE"
      },
      "rdfs:label" : {
        "@language" : "LANGUAGE_CODE",
        "@value" : "LABEL"
      },
      "rdfs:comment" : {
        "@language" : "LANGUAGE_CODE",
        "@value" : "COMMENT"
      },
      "rdfs:subPropertyOf" : {
        "@id" : "BASE_PROPERTY_IRI"
      },
      "salsah-gui:guiElement" : {
        "@id" : "GUI_ELEMENT_IRI"
      }
      "salsah-gui:guiAttribute" : [ "GUI_ATTRIBUTE" ]
    }
  } ],
  "@context" : {
    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
    "salsah-gui" : "http://api.knora.org/ontology/salsah-gui/v2#",
    "owl" : "http://www.w3.org/2002/07/owl#",
    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
    "xsd" : "http://www.w3.org/2001/XMLSchema#"
  }
}
```

Values for `rdfs:label` and `rdfs:comment` must be submitted in at least
one language, either as an object or as an array of objects.

At least one base property must be provided, which can be
`knora-api:hasValue`, `knora-api:hasLinkTo`, or any of their
subproperties, with the exception of file properties (subproperties of
`knora-api:hasFileValue`) and link value properties (subproperties of
`knora-api:hasLinkToValue`).

If the property is a link property, the corresponding link value property
(see @ref:[Links Between Resources](../../02-knora-ontologies/knora-base.md#links-between-resources))
will automatically be created.

The property definition must specify its `knora-api:objectType`. If the
new property is a subproperty of `knora-api:hasValue`, its
`knora-api:objectType` must be one of the built-in subclasses of
`knora-api:Value`, which are defined in the `knora-api` ontology in the
complex schema. If the new property is a subproperty of
`knora-base:hasLinkTo`, its `knora-api:objectType` must be a subclass of
`knora-api:Resource`.

To improve consistency checking, it is recommended, but not required, to
provide `knora-api:subjectType`, which must be a subclass of
`knora-api:Resource`.

The predicates `salsah-gui:guiElement` and `salsah-gui:guiAttribute` are
optional. If provided, the object of `guiElement` must be one of the OWL
named individuals defined in
@ref:[The SALSAH GUI Ontology](../../02-knora-ontologies/salsah-gui.md#individuals). Some GUI elements
take required or optional attributes, which are provided as objects of
`salsah-gui:guiAttribute`; see @ref:[The SALSAH GUI Ontology](../../02-knora-ontologies/salsah-gui.md)
for details.

A successful response will be a JSON-LD document providing the new
property definition (but not any of the other entities in the ontology).

### Changing the Labels of a Property

This operation is permitted even if the property is used in data.

```
HTTP PUT to http://host/v2/ontologies/properties
```

```jsonld
{
  "@id" : "ONTOLOGY_IRI",
  "@type" : "owl:Ontology",
  "knora-api:lastModificationDate" : "ONTOLOGY_LAST_MODIFICATION_DATE",
  "@graph" : [ {
    "PROPERTY_IRI" : {
      "@id" : "PROPERTY_IRI",
      "@type" : "owl:ObjectProperty",
      "rdfs:label" : {
        "@language" : "LANGUAGE_CODE",
        "@value" : "LABEL"
      }
    }
  } ],
  "@context" : {
    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
    "owl" : "http://www.w3.org/2002/07/owl#",
    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
    "xsd" : "http://www.w3.org/2001/XMLSchema#"
  }
}
```

Values for `rdfs:label` must be submitted in at least one language,
either as an object or as an array of objects.

### Changing the Comments of a Property

This operation is permitted even if the property is used in data.

```
HTTP PUT to http://host/v2/ontologies/properties
```

```jsonld
{
  "@id" : "ONTOLOGY_IRI",
  "@type" : "owl:Ontology",
  "knora-api:lastModificationDate" : "ONTOLOGY_LAST_MODIFICATION_DATE",
  "@graph" : [ {
    "PROPERTY_IRI" : {
      "@id" : "PROPERTY_IRI",
      "@type" : "owl:ObjectProperty",
      "rdfs:comment" : {
        "@language" : "LANGUAGE_CODE",
        "@value" : "COMMENT"
      }
    }
  } ],
  "@context" : {
    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
    "owl" : "http://www.w3.org/2002/07/owl#",
    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
    "xsd" : "http://www.w3.org/2001/XMLSchema#"
  }
}
```

Values for `rdfs:comment` must be submitted in at least one language,
either as an object or as an array of objects.

### Adding Cardinalities to a Class

This operation is not permitted if the class is used in data, or if it
has a subclass.

```
HTTP POST to http://host/v2/ontologies/cardinalities
```

```jsonld
{
  "@id" : "ONTOLOGY_IRI",
  "@type" : "owl:Ontology",
  "knora-api:lastModificationDate" : "ONTOLOGY_LAST_MODIFICATION_DATE",
  "@graph" : [ {
    "CLASS_IRI" : {
      "@id" : "CLASS_IRI",
      "@type" : "owl:Class",
      "rdfs:subClassOf" : {
        "@type": "owl:Restriction",
        "OWL_CARDINALITY_PREDICATE": "OWL_CARDINALITY_VALUE",
        "owl:onProperty": {
          "@id" : "PROPERTY_IRI"
        }
      }
    }
  } ],
  "@context" : {
    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
    "owl" : "http://www.w3.org/2002/07/owl#",
    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
    "xsd" : "http://www.w3.org/2001/XMLSchema#"
  }
}
```

At least one cardinality must be submitted.

`OWL_CARDINALITY_PREDICATE` and `OWL_CARDINALITY_VALUE` must correspond
to the supported combinations given in
@ref:[OWL Cardinalities](../../02-knora-ontologies/knora-base.md#owl-cardinalities). (The placeholder
`OWL_CARDINALITY_VALUE` is shown here in quotes, but it should be an
unquoted integer.)

When a cardinality on a link property is submitted, an identical cardinality
on the corresponding link value property is automatically added (see
@ref:[Links Between Resources](../../02-knora-ontologies/knora-base.md#links-between-resources)).

A successful response will be a JSON-LD document providing the new class
definition (but not any of the other entities in the ontology).

### Replacing the Cardinalities of a Class

This removes all the cardinalities from the class and replaces them with
the submitted cardinalities. If no cardinalities are submitted (i.e. the
request contains no `rdfs:subClassOf`), the class is left with no
cardinalities.

This operation is not permitted if the class is used in data, or if it
has a subclass.

```
HTTP PUT to http://host/v2/ontologies/cardinalities
```

```jsonld
{
  "@id" : "ONTOLOGY_IRI",
  "@type" : "owl:Ontology",
  "knora-api:lastModificationDate" : "ONTOLOGY_LAST_MODIFICATION_DATE",
  "@graph" : [ {
    "CLASS_IRI" : {
      "@id" : "CLASS_IRI",
      "@type" : "owl:Class",
      "rdfs:subClassOf" : {
        "@type": "owl:Restriction",
        "OWL_CARDINALITY_PREDICATE": "OWL_CARDINALITY_VALUE",
        "owl:onProperty": {
          "@id" : "PROPERTY_IRI"
        }
      }
    }
  } ],
  "@context" : {
    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
    "owl" : "http://www.w3.org/2002/07/owl#",
    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
    "xsd" : "http://www.w3.org/2001/XMLSchema#"
  }
}
```

`OWL_CARDINALITY_PREDICATE` and `OWL_CARDINALITY_VALUE` must correspond
to the supported combinations given in
@ref:[OWL Cardinalities](../../02-knora-ontologies/knora-base.md#owl-cardinalities). (The placeholder
`OWL_CARDINALITY_VALUE` is shown here in quotes, but it should be an
unquoted integer.)

When a cardinality on a link property is submitted, an identical cardinality
on the corresponding link value property is automatically added (see
@ref:[Links Between Resources](../../02-knora-ontologies/knora-base.md#links-between-resources)).

A successful response will be a JSON-LD document providing the new class
definition (but not any of the other entities in the ontology).

### Deleting a Property

A property can be deleted only if no other ontology entity refers to it,
and if it is not used in data.

```
HTTP DELETE to http://host/v2/ontologies/properties/PROPERTY_IRI?lastModificationDate=ONTOLOGY_LAST_MODIFICATION_DATE
```

The property IRI and the ontology's last modification date must be
URL-encoded.

If the property is a link property, the corresponding link value property
(see @ref:[Links Between Resources](../../02-knora-ontologies/knora-base.md#links-between-resources))
will automatically be deleted.

A successful response will be a JSON-LD document providing only the
ontology's metadata.

### Deleting a Class

A class can be deleted only if no other ontology entity refers to it,
and if it is not used in data.

```
HTTP DELETE to http://host/v2/ontologies/classes/CLASS_IRI?lastModificationDate=ONTOLOGY_LAST_MODIFICATION_DATE
```

The class IRI and the ontology's last modification date must be
URL-encoded.

A successful response will be a JSON-LD document providing only the
ontology's metadata.
