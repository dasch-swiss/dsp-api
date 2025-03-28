# Querying, Creating, and Updating Ontologies

## Querying Ontology Information

Before reading this document, you should have a basic understanding of
DSP-API v2 external ontology schemas (see [API Schema](introduction.md#api-schema)).

Each request returns a single RDF graph, which can be represented in
[JSON-LD](https://json-ld.org/spec/latest/json-ld/),
[Turtle](https://www.w3.org/TR/turtle/),
or [RDF/XML](https://www.w3.org/TR/rdf-syntax-grammar/), using
[HTTP content negotiation](https://tools.ietf.org/html/rfc7231#section-5.3.2) (see
[Response Formats](introduction.md#response-formats)).

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

If you submit a project IRI in the `X-Knora-Accept-Project` header, only the
ontologies for that project will be returned.

The response is in the complex API v2 schema. Sample response:

```json
{
  "@graph": [
    {
      "knora-api:lastModificationDate": {
        "@value": "2017-12-19T15:23:42.166Z",
        "@type": "xsd:dateTimeStamp"
      },
      "rdfs:label": "The anything ontology",
      "knora-api:attachedToProject": {
        "@id": "http://rdfh.ch/projects/0001"
      },
      "@type": "owl:Ontology",
      "@id": "http://0.0.0.0:3333/ontology/0001/anything/v2"
    },
    {
      "knora-api:lastModificationDate": {
        "@value": "2022-03-23T07:14:17.445208Z",
        "@type": "xsd:dateTimeStamp"
      },
      "rdfs:label": "The something ontology",
      "knora-api:attachedToProject": {
        "@id": "http://rdfh.ch/projects/0001"
      },
      "@type": "owl:Ontology",
      "@id": "http://0.0.0.0:3333/ontology/0001/something/v2"
    },
    {
      "knora-api:lastModificationDate": {
        "@value": "2022-03-23T07:14:17.445208Z",
        "@type": "xsd:dateTimeStamp"
      },
      "rdfs:label": "The images demo ontology",
      "knora-api:attachedToProject": {
        "@id": "http://rdfh.ch/projects/00FF" 
      },
      "@type": "owl:Ontology",
      "@id": "http://0.0.0.0:3333/ontology/00FF/images/v2"
    },
    {
      "knora-api:lastModificationDate": {
        "@value": "2022-03-23T07:14:17.445208Z",
        "@type": "xsd:dateTimeStamp"
      },
      "rdfs:label": "The BEOL ontology",
      "knora-api:attachedToProject": {
        "@id": "http://rdfh.ch/projects/yTerZGyxjZVqFMNNKXCDPF"
      },
      "@type": "owl:Ontology",
      "@id": "http://0.0.0.0:3333/ontology/0801/beol/v2"
    },
    {
      "knora-api:lastModificationDate": {
        "@value": "2022-03-23T07:14:17.445208Z",
        "@type": "xsd:dateTimeStamp"
      },
      "rdfs:label": "The Biblio ontology",
      "knora-api:attachedToProject": {
        "@id": "http://rdfh.ch/projects/yTerZGyxjZVqFMNNKXCDPF"
      },
      "@type": "owl:Ontology",
      "@id": "http://0.0.0.0:3333/ontology/0801/biblio/v2"
    },
    {
      "knora-api:lastModificationDate": {
        "@value": "2022-03-23T07:14:17.445208Z",
        "@type": "xsd:dateTimeStamp"
      },
      "rdfs:label": "The Newton-Project ontology",
      "knora-api:attachedToProject": {
        "@id": "http://rdfh.ch/projects/yTerZGyxjZVqFMNNKXCDPF"
      },
      "@type": "owl:Ontology",
      "@id": "http://0.0.0.0:3333/ontology/0801/newton/v2"
    },
    {
      "knora-api:lastModificationDate": {
        "@value": "2022-03-23T07:14:17.445208Z",
        "@type": "xsd:dateTimeStamp"
      },
      "rdfs:label": "The incunabula ontology",
      "knora-api:attachedToProject": {
        "@id": "http://rdfh.ch/projects/0803"
      },
      "@type": "owl:Ontology",
      "@id": "http://0.0.0.0:3333/ontology/0803/incunabula/v2"
    },
    {
      "knora-api:lastModificationDate": {
        "@value": "2022-03-23T07:14:17.445208Z",
        "@type": "xsd:dateTimeStamp"
      },
      "rdfs:label": "The dokubib ontology",
      "knora-api:attachedToProject": {
        "@id": "http://rdfh.ch/projects/0804"
      },
      "@type": "owl:Ontology",
      "@id": "http://0.0.0.0:3333/ontology/0804/dokubib/v2"
    },
    {
      "knora-api:lastModificationDate": {
        "@value": "2022-03-23T07:14:17.445208Z",
        "@type": "xsd:dateTimeStamp"
      },
      "rdfs:label": "The Anton Webern project ontology",
      "knora-api:attachedToProject": {
        "@id": "http://rdfh.ch/projects/08AE"
      },
      "@type": "owl:Ontology",
      "@id": "http://0.0.0.0:3333/ontology/08AE/webern/v2"
    },
    {
      "rdfs:label": "The Knora admin ontology",
      "knora-api:attachedToProject": {
        "@id": "http://www.knora.org/ontology/knora-admin#SystemProject"
      },
      "knora-api:isBuiltIn": true,
      "@type": "owl:Ontology",
      "@id": "http://api.knora.org/ontology/knora-admin/v2"
    },
    {
      "rdfs:label": "The knora-api ontology in the complex schema",
      "knora-api:attachedToProject": {
        "@id": "http://www.knora.org/ontology/knora-admin#SystemProject"
      },
      "knora-api:isBuiltIn": true,
      "@type": "owl:Ontology",
      "@id": "http://api.knora.org/ontology/knora-api/v2"
    },
    {
      "rdfs:label": "The salsah-gui ontology",
      "knora-api:attachedToProject": {
        "@id": "http://www.knora.org/ontology/knora-admin#SystemProject"
      },
      "knora-api:isBuiltIn": true,
      "@type": "owl:Ontology",
      "@id": "http://api.knora.org/ontology/salsah-gui/v2"
    },
    {
      "rdfs:label": "The standoff ontology",
      "knora-api:attachedToProject": {
        "@id": "http://www.knora.org/ontology/knora-admin#SystemProject"
      },
      "knora-api:isBuiltIn": true,
      "@type": "owl:Ontology",
      "@id": "http://api.knora.org/ontology/standoff/v2"
    }
  ],
  "@context": {
    "knora-api": "http://api.knora.org/ontology/knora-api/v2#",
    "xsd": "http://www.w3.org/2001/XMLSchema#",
    "rdfs": "http://www.w3.org/2000/01/rdf-schema#",
    "owl": "http://www.w3.org/2002/07/owl#"
  }
}
```

To get metadata about the ontologies that belong to one or more particular
projects:

```
HTTP GET to http://host/v2/ontologies/metadata/PROJECT_IRI[/PROJECT_IRI...]
```

The project IRIs must be URL-encoded.

Example response for the `anything` test project
(project IRI `http://rdfh.ch/projects/0001`):

```json
{
  "@id" : "http://0.0.0.0:3333/ontology/0001/anything/v2",
  "@type" : "owl:Ontology",
  "knora-api:attachedToProject" : {
    "@id" : "http://rdfh.ch/projects/0001"
  },
  "knora-api:lastModificationDate": "2017-12-19T15:23:42.166Z",
  "rdfs:label" : "The anything ontology",
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
(see [Response Formats](introduction.md#response-formats)).

If the client dereferences a project-specific ontology IRI as a URL, the
DSP-API server running on the hostname in the IRI will serve the
ontology. For example, if the server is running on `0.0.0.0:3333`, the
IRI `http://0.0.0.0:3333/ontology/00FF/images/simple/v2` can be
dereferenced to request the `images` sample ontology in the simple
schema.

If the client dereferences a built-in Knora ontology, such as
`http://api.knora.org/ontology/knora-api/simple/v2`, there must be a
DSP-API server running at `api.knora.org` that can serve the ontology.
The [DaSCH](http://dasch.swiss/) intends to run such as server. For
testing, you can configure your local `/etc/hosts` file to resolve
`api.knora.org` as `localhost`.

#### Differences Between Internal and External Ontologies

The external ontologies used by DSP-API v2 are different to the internal
ontologies that are actually stored in the triplestore (see
[API Schema](introduction.md#api-schema)). In general, the external
ontologies use simpler data structures, but they also provide additional
information to make it easier for clients to use them. This is illustrated
in the examples in the next sections.

The internal predicates `knora-base:subjectClassConstraint` and
`knora-base:objectClassConstraint` (see
[Constraints on the Types of Property Subjects and Objects](../../02-dsp-ontologies/knora-base.md#constraints-on-the-types-of-property-subjects-and-objects))
are represented as `knora-api:subjectType` and `knora-api:objectType` in
external ontologies.

#### JSON-LD Representation of an Ontology in the Simple Schema

The simple schema is suitable for client applications that need to read
but not update data in Knora. For example, here is the response for the
`images` sample ontology in the simple schema,
`http://0.0.0.0:3333/ontology/00FF/images/simple/v2` (simplified for
clarity):

```json
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
[OWL Cardinalities](../../02-dsp-ontologies/knora-base.md#owl-cardinalities).

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

```json
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

```json
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
        "xsd:pattern" : "(GREGORIAN|JULIAN|ISLAMIC):\\d{1,4}(-\\d{1,2}(-\\d{1,2})?)?( BC| AD| BCE| CE)?(:\\d{1,4}(-\\d{1,2}(-\\d{1,2})?)?( BC| AD| BCE| CE)?)?"
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

```json
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
`salsah-gui` ontology, see [The SALSAH GUI Ontology](../../02-dsp-ontologies/salsah-gui.md).

### Querying class definition

To get the definition of a class, use the following route:

```
HTTP GET to http://host/v2/ontologies/classes/CLASS_IRI
```

Sample response:

```json
{
    "knora-api:lastModificationDate": {
        "@value": "2023-01-09T15:39:48.548298041Z",
        "@type": "xsd:dateTimeStamp"
    },
    "rdfs:label": "The anything ontology",
    "@graph": [
        {
            "knora-api:isResourceClass": true,
            "rdfs:label": "Institution",
            "knora-api:canBeInstantiated": true,
            "rdfs:subClassOf": [
                {
                    "@id": "knora-api:Resource"
                },
                {
                    "@type": "owl:Restriction",
                    "owl:onProperty": {
                        "@id": "knora-api:arkUrl"
                    },
                    "owl:cardinality": 1,
                    "knora-api:isInherited": true
                },
                {
                    "@type": "owl:Restriction",
                    "owl:onProperty": {
                        "@id": "knora-api:attachedToProject"
                    },
                    "owl:cardinality": 1,
                    "knora-api:isInherited": true
                },
                {
                    "@type": "owl:Restriction",
                    "owl:onProperty": {
                        "@id": "knora-api:attachedToUser"
                    },
                    "owl:cardinality": 1,
                    "knora-api:isInherited": true
                },
                {
                    "@type": "owl:Restriction",
                    "owl:onProperty": {
                        "@id": "knora-api:creationDate"
                    },
                    "owl:cardinality": 1,
                    "knora-api:isInherited": true
                },
                {
                    "@type": "owl:Restriction",
                    "owl:onProperty": {
                        "@id": "knora-api:deleteComment"
                    },
                    "owl:maxCardinality": 1,
                    "knora-api:isInherited": true
                },
                {
                    "@type": "owl:Restriction",
                    "owl:onProperty": {
                        "@id": "knora-api:deleteDate"
                    },
                    "owl:maxCardinality": 1,
                    "knora-api:isInherited": true
                },
                {
                    "@type": "owl:Restriction",
                    "owl:onProperty": {
                        "@id": "knora-api:deletedBy"
                    },
                    "owl:maxCardinality": 1,
                    "knora-api:isInherited": true
                },
                {
                    "@type": "owl:Restriction",
                    "owl:onProperty": {
                        "@id": "knora-api:hasIncomingLinkValue"
                    },
                    "owl:minCardinality": 0,
                    "knora-api:isInherited": true
                },
                {
                    "@type": "owl:Restriction",
                    "owl:onProperty": {
                        "@id": "knora-api:hasPermissions"
                    },
                    "owl:cardinality": 1,
                    "knora-api:isInherited": true
                },
                {
                    "@type": "owl:Restriction",
                    "owl:onProperty": {
                        "@id": "knora-api:hasStandoffLinkTo"
                    },
                    "owl:minCardinality": 0,
                    "knora-api:isInherited": true
                },
                {
                    "@type": "owl:Restriction",
                    "owl:onProperty": {
                        "@id": "knora-api:hasStandoffLinkToValue"
                    },
                    "owl:minCardinality": 0,
                    "knora-api:isInherited": true
                },
                {
                    "@type": "owl:Restriction",
                    "owl:onProperty": {
                        "@id": "knora-api:isDeleted"
                    },
                    "owl:maxCardinality": 1,
                    "knora-api:isInherited": true
                },
                {
                    "@type": "owl:Restriction",
                    "owl:onProperty": {
                        "@id": "knora-api:lastModificationDate"
                    },
                    "owl:maxCardinality": 1,
                    "knora-api:isInherited": true
                },
                {
                    "@type": "owl:Restriction",
                    "owl:onProperty": {
                        "@id": "knora-api:userHasPermission"
                    },
                    "owl:cardinality": 1,
                    "knora-api:isInherited": true
                },
                {
                    "@type": "owl:Restriction",
                    "owl:onProperty": {
                        "@id": "knora-api:versionArkUrl"
                    },
                    "owl:cardinality": 1,
                    "knora-api:isInherited": true
                },
                {
                    "@type": "owl:Restriction",
                    "owl:onProperty": {
                        "@id": "knora-api:versionDate"
                    },
                    "owl:maxCardinality": 1,
                    "knora-api:isInherited": true
                },
                {
                    "@type": "owl:Restriction",
                    "owl:onProperty": {
                        "@id": "rdfs:label"
                    },
                    "owl:cardinality": 1,
                    "knora-api:isInherited": true
                },
                {
                    "@type": "owl:Restriction",
                    "owl:onProperty": {
                        "@id": "anything:hasName"
                    },
                    "owl:cardinality": 1,
                    "salsah-gui:guiOrder": 1
                },
                {
                    "@type": "owl:Restriction",
                    "owl:onProperty": {
                        "@id": "anything:hasLocation"
                    },
                    "owl:cardinality": 1,
                    "salsah-gui:guiOrder": 2
                }
            ],
            "rdfs:comment": "some comment",
            "@type": "owl:Class",
            "@id": "anything:Institution"
        }
    ],
    "knora-api:attachedToProject": {
        "@id": "http://rdfh.ch/projects/0001"
    },
    "@type": "owl:Ontology",
    "@id": "http://0.0.0.0:3333/ontology/0001/anything/v2",
    "@context": {
        "rdf": "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
        "knora-api": "http://api.knora.org/ontology/knora-api/v2#",
        "anything": "http://0.0.0.0:3333/ontology/0001/anything/v2#",
        "owl": "http://www.w3.org/2002/07/owl#",
        "salsah-gui": "http://api.knora.org/ontology/salsah-gui/v2#",
        "rdfs": "http://www.w3.org/2000/01/rdf-schema#",
        "xsd": "http://www.w3.org/2001/XMLSchema#"
    }
}
```


## Ontology Updates

The ontology update API must ensure that the ontologies it creates are
valid and consistent, and that existing data is not invalidated by a
change to an ontology. To make this easier to enforce, the ontology
update API allows only one entity to be created or modified at a time.
It is not possible to submit an entire ontology all at once. Each
update request is a JSON-LD document providing only the information that is
relevant to the update.

Moreover, the API enforces the following rules:

- An entity (i.e. a class or property) cannot be referred to until it has been created.
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
response to each update or when [querying the ontology](#querying-an-ontology).
If user A attempts to update an ontology, but
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

```json
{
  "knora-api:ontologyName" : "ONTOLOGY_NAME",
  "knora-api:attachedToProject" : {
    "@id" : "PROJECT_IRI"
  },
  "rdfs:label" : "ONTOLOGY_NAME",
  "@context" : {
    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#"
  }
}
```

The ontology name must follow the rules given in
[Knora IRIs](knora-iris.md).

The ontology metadata can have an optional comment given in the request 
body as:

```
"rdfs:comment": "some comment",
``` 

If the ontology is to be shared by multiple projects, it must be
created in the default shared ontologies project,
`http://www.knora.org/ontology/knora-base#DefaultSharedOntologiesProject`,
and the request must have this additional boolean property:

```
"knora-api:isShared" : true
```

See [Shared Ontologies](../../02-dsp-ontologies/introduction.md#shared-ontologies) for details about
shared ontologies.

A successful response will be a JSON-LD document providing only the
ontology's metadata, which includes the ontology's IRI. When the client
makes further requests to create entities (classes and properties) in
the ontology, it must construct entity IRIs by concatenating the
ontology IRI, a `#` character, and the entity name. An entity name must
be a valid XML [NCName](https://www.w3.org/TR/xml-names/#NT-NCName).

### Changing an Ontology's Metadata

One can modify an ontology's metadata by updating its `rdfs:label` or `rdfs:comment` 
or both. The example below shows the request for changing the label of an ontology.

```
HTTP PUT to http://host/v2/ontologies/metadata
```

```json
{
  "@id" : "ONTOLOGY_IRI",
  "rdfs:label" : "NEW_ONTOLOGY_LABEL",
  "knora-api:lastModificationDate" : {
    "@type" : "xsd:dateTimeStamp",
    "@value" : "ONTOLOGY_LAST_MODIFICATION_DATE"
  },
  "@context" : {
    "xsd" : "http://www.w3.org/2001/XMLSchema#",
    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#"
  }
}
```

Similarly, a user can change an ontology's existing comment or add one by specifying 
the new comment in the request body:

```json
{
  "@id" : "ONTOLOGY_IRI",
  "rdfs:comment" : "NEW_ONTOLOGY_COMMENT",
  "knora-api:lastModificationDate" : {
    "@type" : "xsd:dateTimeStamp",
    "@value" : "ONTOLOGY_LAST_MODIFICATION_DATE"
  },
  "@context" : {
    "xsd" : "http://www.w3.org/2001/XMLSchema#",
    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#"
  }
}
```

The request body can also contain a new label and a new comment for the ontology's metadata. 
A successful response will be a JSON-LD document providing only the
ontology's metadata.

### Deleting an Ontology's comment

```
HTTP DELETE to http://host/v2/ontologies/comment/ONTOLOGY_IRI?lastModificationDate=ONTOLOGY_LAST_MODIFICATION_DATE
```

The ontology IRI and the ontology's last modification date must be
URL-encoded.

A successful response will be a JSON-LD document containing the ontology's
updated metadata.

### Deleting an Ontology

An ontology can be deleted only if it is not used in data.

```
HTTP DELETE to http://host/v2/ontologies/ONTOLOGY_IRI?lastModificationDate=ONTOLOGY_LAST_MODIFICATION_DATE
```

The ontology IRI and the ontology's last modification date must be
URL-encoded.

A successful response will be a JSON-LD document containing a
confirmation message.

To check whether an ontology can be deleted:

```
HTTP GET to http://host/v2/ontologies/candeleteontology/ONTOLOGY_IRI
```

The response will look like this:

```json
{
    "knora-api:canDo": false,
    "@context": {
        "knora-api": "http://api.knora.org/ontology/knora-api/v2#"
    }
}
```

### Creating a Class Without Cardinalities

```
HTTP POST to http://host/v2/ontologies/classes
```

```json
{
  "@id" : "ONTOLOGY_IRI",
  "@type" : "owl:Ontology",
  "knora-api:lastModificationDate" : {
    "@type" : "xsd:dateTimeStamp",
    "@value" : "ONTOLOGY_LAST_MODIFICATION_DATE"
  },
  "@graph" : [
    {
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
  ],
  "@context" : {
    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
    "owl" : "http://www.w3.org/2002/07/owl#",
    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
    "xsd" : "http://www.w3.org/2001/XMLSchema#"
  }
}
```

Values for `rdfs:label` must be submitted in at least
one language, either as an object or as an array of objects.

Values for `rdfs:comment` are optional, but if they are provided, they must include a language code.

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

```json
{
  "@id" : "ONTOLOGY_IRI",
  "@type" : "owl:Ontology",
  "knora-api:lastModificationDate" : {
    "@type" : "xsd:dateTimeStamp",
    "@value" : "ONTOLOGY_LAST_MODIFICATION_DATE"
  },
  "@graph" : [
    {
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
  ],
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
[OWL Cardinalities](../../02-dsp-ontologies/knora-base.md#owl-cardinalities). (The placeholder
`OWL_CARDINALITY_VALUE` is shown here in quotes, but it should be an
unquoted integer.)

Values for `rdfs:label` must be submitted in at least
one language, either as an object or as an array of objects.

Values for `rdfs:comment` are optional, but if they are provided, they must include a language code.

At least one base class must be provided.

When a cardinality on a link property is submitted, an identical cardinality
on the corresponding link value property is automatically added (see
[Links Between Resources](../../02-dsp-ontologies/knora-base.md#links-between-resources)).

A successful response will be a JSON-LD document providing the new class
definition (but not any of the other entities in the ontology).

### Changing the Labels of a Class

This operation is permitted even if the class is used in data.

```
HTTP PUT to http://host/v2/ontologies/classes
```

```json
{
  "@id" : "ONTOLOGY_IRI",
  "@type" : "owl:Ontology",
  "knora-api:lastModificationDate" : {
    "@type" : "xsd:dateTimeStamp",
    "@value" : "ONTOLOGY_LAST_MODIFICATION_DATE"
  },
  "@graph" : [
    {
      "@id" : "CLASS_IRI",
      "@type" : "owl:Class",
      "rdfs:label" : {
        "@language" : "LANGUAGE_CODE",
        "@value" : "LABEL"
      }
    }
  ],
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

To get the current labels use the [class definition](#querying-class-definition).

### Changing the Comments of a Class

This operation is permitted even if the class is used in data.

```
HTTP PUT to http://host/v2/ontologies/classes
```

```json
{
  "@id" : "ONTOLOGY_IRI",
  "@type" : "owl:Ontology",
  "knora-api:lastModificationDate" : {
    "@type" : "xsd:dateTimeStamp",
    "@value" : "ONTOLOGY_LAST_MODIFICATION_DATE"
  },
  "@graph" : [
    {
      "@id" : "CLASS_IRI",
      "@type" : "owl:Class",
      "rdfs:comment" : {
        "@language" : "LANGUAGE_CODE",
        "@value" : "COMMENT"
      }
    }
  ],
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

To get the current comments use the [class definition](#querying-class-definition).

### Deleting the Comments of a Class

This operation is permitted even if the class is used in data.

```
HTTP DELETE to http://host/v2/ontologies/classes/comment/CLASS_IRI?lastModificationDate=ONTOLOGY_LAST_MODIFICATION_DATE
```

The class IRI and the ontology's last modification date must be URL-encoded.

All values i.e. all languages for `rdfs:comment` are deleted.

A successful response will be a JSON-LD document providing the class definition.

### Creating a Property

```
HTTP POST to http://host/v2/ontologies/properties
```

```json
{
  "@id" : "ONTOLOGY_IRI",
  "@type" : "owl:Ontology",
  "knora-api:lastModificationDate" : {
    "@type" : "xsd:dateTimeStamp",
    "@value" : "ONTOLOGY_LAST_MODIFICATION_DATE"
  },
  "@graph" : [
    {
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
      },
      "salsah-gui:guiAttribute" : [ "GUI_ATTRIBUTE" ]
    }
  ],
  "@context" : {
    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
    "salsah-gui" : "http://api.knora.org/ontology/salsah-gui/v2#",
    "owl" : "http://www.w3.org/2002/07/owl#",
    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
    "xsd" : "http://www.w3.org/2001/XMLSchema#"
  }
}
```

Values for `rdfs:label` must be submitted in at least
one language, either as an object or as an array of objects.

Values for `rdfs:comment` are optional, but if they are provided, they must include a language code.

At least one base property must be provided, which can be
`knora-api:hasValue`, `knora-api:hasLinkTo`, or any of their
subproperties, with the exception of file properties (subproperties of
`knora-api:hasFileValue`) and link value properties (subproperties of
`knora-api:hasLinkToValue`).

If the property is a link property, the corresponding link value property
(see [Links Between Resources](../../02-dsp-ontologies/knora-base.md#links-between-resources))
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
[The SALSAH GUI Ontology](../../02-dsp-ontologies/salsah-gui.md#individuals). Some GUI elements
take required or optional attributes, which are provided as objects of
`salsah-gui:guiAttribute`; see [The SALSAH GUI Ontology](../../02-dsp-ontologies/salsah-gui.md)
for details.

A successful response will be a JSON-LD document providing the new
property definition (but not any of the other entities in the ontology).

### Changing the Labels of a Property

This operation is permitted even if the property is used in data.

```
HTTP PUT to http://host/v2/ontologies/properties
```

```json
{
  "@id" : "ONTOLOGY_IRI",
  "@type" : "owl:Ontology",
  "knora-api:lastModificationDate" : {
    "@type" : "xsd:dateTimeStamp",
    "@value" : "ONTOLOGY_LAST_MODIFICATION_DATE"
  },
  "@graph" : [
    {
      "@id" : "PROPERTY_IRI",
      "@type" : "owl:ObjectProperty",
      "rdfs:label" : {
        "@language" : "LANGUAGE_CODE",
        "@value" : "LABEL"
      }
    }
  ],
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

```json
{
  "@id" : "ONTOLOGY_IRI",
  "@type" : "owl:Ontology",
  "knora-api:lastModificationDate" : {
    "@type" : "xsd:dateTimeStamp",
    "@value" : "ONTOLOGY_LAST_MODIFICATION_DATE"
  },
  "@graph" : [
    {
      "@id" : "PROPERTY_IRI",
      "@type" : "owl:ObjectProperty",
      "rdfs:comment" : {
        "@language" : "LANGUAGE_CODE",
        "@value" : "COMMENT"
      }
    }
  ],
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

### Deleting the Comments of a Property

This operation is permitted even if the property is used in data.

```
HTTP DELETE to http://host/v2/ontologies/properties/comment/PROPERTY_IRI?lastModificationDate=ONTOLOGY_LAST_MODIFICATION_DATE
```

The property IRI and the ontology's last modification date must be URL-encoded.

All values i.e. all languages for `rdfs:comment` are deleted.

If the property is a link property, the `rdfs:comment` of its corresponding link value property will automatically be deleted.

A successful response will be a JSON-LD document providing the property definition.

### Changing the GUI Element and GUI Attributes of a Property

This operation is permitted even if the property is used in data.

```
HTTP PUT to http://host/v2/ontologies/properties/guielement
```

```json
{
  "@id": "ONTOLOGY_IRI",
  "@type": "owl:Ontology",
  "knora-api:lastModificationDate": {
    "@type": "xsd:dateTimeStamp",
    "@value": "ONTOLOGY_LAST_MODIFICATION_DATE"
  },
  "@graph": [
    {
      "@id": "PROPERTY_IRI",
      "@type": "owl:ObjectProperty",
      "salsah-gui:guiElement": {
        "@id": "salsah-gui:Textarea"
      },
      "salsah-gui:guiAttribute": [
        "cols=80",
        "rows=24"
      ]
    }
  ],
  "@context": {
    "rdf": "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
    "knora-api": "http://api.knora.org/ontology/knora-api/v2#",
    "salsah-gui": "http://api.knora.org/ontology/salsah-gui/v2#",
    "owl": "http://www.w3.org/2002/07/owl#",
    "rdfs": "http://www.w3.org/2000/01/rdf-schema#",
    "xsd": "http://www.w3.org/2001/XMLSchema#"
  }
}
```

To remove the values of `salsah-gui:guiElement` and `salsah-gui:guiAttribute` from
the property definition, submit the request without those predicates.

### Adding Cardinalities to a Class

If the class (or any of its sub-classes) is used in data, 
it is not allowed to add cardinalities `owl:minCardinality` greater than 0 or `owl:cardinality 1` to the class.

```
HTTP POST to http://host/v2/ontologies/cardinalities
```

```json
{
  "@id" : "ONTOLOGY_IRI",
  "@type" : "owl:Ontology",
  "knora-api:lastModificationDate" : {
    "@type" : "xsd:dateTimeStamp",
    "@value" : "ONTOLOGY_LAST_MODIFICATION_DATE"
  },
  "@graph" : [ 
    {
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
  ],
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
[OWL Cardinalities](../../02-dsp-ontologies/knora-base.md#owl-cardinalities). (The placeholder
`OWL_CARDINALITY_VALUE` is shown here in quotes, but it should be an
unquoted integer.)

When a cardinality on a link property is submitted, an identical cardinality
on the corresponding link value property is automatically added (see
[Links Between Resources](../../02-dsp-ontologies/knora-base.md#links-between-resources)).

A successful response will be a JSON-LD document providing the new class
definition (but not any of the other entities in the ontology).

### Replacing the Cardinalities of a Class

It is possible to replace all cardinalities on properties used by a class.  
If it succeeds the request will effectively replace all direct cardinalities of the class as specified.
That is, it removes all the cardinalities from the class and replaces them with the submitted cardinalities. 
Meaning that, if no cardinalities are submitted (i.e. the request contains no `rdfs:subClassOf`), 
the class is left with no cardinalities.

The request will fail if any of the "Pre-Update Checks" fails. 
A partial update of the ontology will not be performed.

#### Pre-Update Checks

- _Ontology Check_
    - Any given cardinality on a property must be included in any of the existing cardinalities 
      for the same property of the super-classes.
    - Any given cardinality on a property must include the effective cardinalities 
      for the same property of all subclasses, 
      taking into account the respective inherited cardinalities from the class hierarchy of the subclasses.
- _Consistency Check with existing data_
    - Given that instances of the class or any of its subclasses exist,
      then these instances are checked if they conform to the given cardinality.

!!! note "Subproperty handling for cardinality pre-update checks"
    The Pre-Update check does not take into account any `subproperty` relations between the properties. 
    Every cardinality is checked against only the given property and not its subproperties, 
    neither in the ontology nor the consistency check with existing data. 
    This means that currently it is necessary to maintain the cardinalities on all subproperties of a property 
    in sync with the cardinalities on the superproperty.

```
HTTP PUT to http://host/v2/ontologies/cardinalities
```

```json
{
  "@id" : "ONTOLOGY_IRI",
  "@type" : "owl:Ontology",
  "knora-api:lastModificationDate" : {
    "@type" : "xsd:dateTimeStamp",
    "@value" : "ONTOLOGY_LAST_MODIFICATION_DATE"
  },
  "@graph" : [ {
    "@id" : "CLASS_IRI",
    "@type" : "owl:Class",
    "rdfs:subClassOf" : {
      "@type": "owl:Restriction",
      "OWL_CARDINALITY_PREDICATE": "OWL_CARDINALITY_VALUE",
      "owl:onProperty": {
        "@id" : "PROPERTY_IRI"
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
[OWL Cardinalities](../../02-dsp-ontologies/knora-base.md#owl-cardinalities). (The placeholder
`OWL_CARDINALITY_VALUE` is shown here in quotes, but it should be an
unquoted integer.)

When a cardinality on a link property is submitted, an identical cardinality
on the corresponding link value property is automatically added (see
[Links Between Resources](../../02-dsp-ontologies/knora-base.md#links-between-resources)).

A successful response will be a JSON-LD document providing the new class definition (but not any of the other entities in the ontology).
If any of the "Pre-Update Checks" fail the endpoint will respond with a _400 Bad Request_ containing the reasons why the update failed.

The "Pre-Update Checks" are available on a dedicated endpoint.
For a check whether a particular cardinality can be set on a class/property combination, use the following request:

```
HTTP GET to http://host/v2/ontologies/canreplacecardinalities/CLASS_IRI?propertyIri=PROPERTY_IRI&newCardinality=[0-1|1|1-n|0-n]
```

The response will look like this:

Failure:

```json
{
  "knora-api:canDo": false,
  "knora-api:cannotDoReason": "An explanation, understandable to humans, why the update cannot be carried out.",
  "knora-api:cannotDoContext": {
    "knora-api:canSetCardinalityCheckFailure": [
      {
        "knora-api:canSetCardinalityOntologySuperClassCheckFailed": [
          {
            "@id": "http://0.0.0.0:3333/ontology/0801/biblio/v2#somePublicationInstance"
          },
          {
            "@id": "http://0.0.0.0:3333/ontology/0801/biblio/v2#someArticleInstance"
          }
        ]
      },
      {
        "knora-api:canSetCardinalityOntologySubclassCheckFailed": {
          "@id": "http://0.0.0.0:3333/ontology/0801/biblio/v2#someJournalArticleInstance"
        }
      }
    ]
  },
  "@context": {
    "knora-api": "http://api.knora.org/ontology/knora-api/v2#"
  }
}
```

Success:

```json
{
    "knora-api:canDo": true,
    "@context": {
        "knora-api": "http://api.knora.org/ontology/knora-api/v2#"
    } 
}
```

!!! note
    The following check is still available but deprecated - use the more detailed check above.  
    This request is only checking if the class is in use. 

To check whether all class's cardinalities can be replaced:

```
HTTP GET to http://host/v2/ontologies/canreplacecardinalities/CLASS_IRI
```

The response will look like this:

```json
{
    "knora-api:canDo": false,
    "@context": {
        "knora-api": "http://api.knora.org/ontology/knora-api/v2#"
    }
}
```

The `ontologies/canreplacecardinalities/CLASS_IRI` request is only checking if the class is in use. 


### Delete a single cardinality from a class

If a class is used in data, it is only allowed to delete a cardinality, if the
property a cardinality refers to, is not used inside the data. Also, the property
isn't allowed to be used inside the data in any subclasses of this class.

```
HTTP PATCH to http://host/v2/ontologies/cardinalities
```

```json
{
  "@id" : "ONTOLOGY_IRI",
  "@type" : "owl:Ontology",
  "knora-api:lastModificationDate" : {
    "@type" : "xsd:dateTimeStamp",
    "@value" : "ONTOLOGY_LAST_MODIFICATION_DATE"
  },
  "@graph" : [ {
    "@id" : "CLASS_IRI",
    "@type" : "owl:Class",
    "rdfs:subClassOf" : {
      "@type": "owl:Restriction",
      "OWL_CARDINALITY_PREDICATE": "OWL_CARDINALITY_VALUE",
      "owl:onProperty": {
        "@id" : "PROPERTY_IRI"
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
[OWL Cardinalities](../../02-dsp-ontologies/knora-base.md#owl-cardinalities). (The placeholder
`OWL_CARDINALITY_VALUE` is shown here in quotes, but it should be an
unquoted integer.)

When a cardinality on a link property is submitted, an identical cardinality
on the corresponding link value property is automatically added (see
[Links Between Resources](../../02-dsp-ontologies/knora-base.md#links-between-resources)).

A successful response will be a JSON-LD document providing the new class
definition (but not any of the other entities in the ontology).

To check whether a class's cardinality can be deleted:

```
HTTP POST to http://host/v2/ontologies/candeletecardinalities
```

The response will look like this:

```json
{
    "knora-api:canDo": false,
    "@context": {
        "knora-api": "http://api.knora.org/ontology/knora-api/v2#"
    }
}
```

### Changing the GUI Order of Cardinalities

To change the GUI order of one or more cardinalities in a class:

```
HTTP PUT to http://host/v2/ontologies/guiorder
```

This can be done even if the class is used in data.

The request body includes the cardinalities whose GUI order should be changed,
using the predicate `salsah-gui:guiOrder`, whose object is an integer:

```json
{
  "@id" : "ONTOLOGY_IRI",
  "@type" : "owl:Ontology",
  "knora-api:lastModificationDate" : {
    "@type" : "xsd:dateTimeStamp",
    "@value" : "ONTOLOGY_LAST_MODIFICATION_DATE"
  },
  "@graph" : [ {
    "@id" : "CLASS_IRI",
    "@type" : "owl:Class",
    "rdfs:subClassOf" : {
      "@type": "owl:Restriction",
      "OWL_CARDINALITY_PREDICATE": "OWL_CARDINALITY_VALUE",
      "owl:onProperty": {
        "@id" : "PROPERTY_IRI"
      },
      "salsah-gui:guiOrder": "GUI_ORDER_VALUE"
    }
  } ],
  "@context" : {
    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
    "salsah-gui" : "http://api.knora.org/ontology/salsah-gui/v2#",
    "owl" : "http://www.w3.org/2002/07/owl#",
    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
    "xsd" : "http://www.w3.org/2001/XMLSchema#"
  }
}
```

Only the cardinalities whose GUI order is to be changed need to be included
in the request. The `OWL_CARDINALITY_PREDICATE` and `OWL_CARDINALITY_VALUE`
are ignored; only the `GUI_ORDER_VALUE` is changed.

### Deleting a Property

A property can be deleted only if no other ontology entity refers to it,
and if it is not used in data.

```
HTTP DELETE to http://host/v2/ontologies/properties/PROPERTY_IRI?lastModificationDate=ONTOLOGY_LAST_MODIFICATION_DATE
```

The property IRI and the ontology's last modification date must be
URL-encoded.

If the property is a link property, the corresponding link value property
(see [Links Between Resources](../../02-dsp-ontologies/knora-base.md#links-between-resources))
will automatically be deleted.

A successful response will be a JSON-LD document providing only the
ontology's metadata.

To check whether a property can be deleted:

```
HTTP GET to http://host/v2/ontologies/candeleteproperty/PROPERTY_IRI
```

The response will look like this:

```json
{
    "knora-api:canDo": false,
    "@context": {
        "knora-api": "http://api.knora.org/ontology/knora-api/v2#"
    }
}
```

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

To check whether a class can be deleted:

```
HTTP GET to http://host/v2/ontologies/candeleteclass/CLASS_IRI
```

The response will look like this:

```json
{
    "knora-api:canDo": false,
    "@context": {
        "knora-api": "http://api.knora.org/ontology/knora-api/v2#"
    }
}
```
