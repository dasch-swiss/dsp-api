# Creating and Editing Values

## Creating a Value

To create a value in an existing resource, use this route:

```
HTTP POST to http://host/v2/values
```

The body of the request is a JSON-LD document in the
[complex API schema](introduction.md#api-schema), specifying the resource's IRI and type, the resource property, and the
content of the value. The representation of the value is the same as when it is returned in a `GET` request, except that
its IRI and `knora-api:attachedToUser` are not given. For example, to create an integer value:

```json
{
  "@id": "http://rdfh.ch/0001/a-thing",
  "@type": "anything:Thing",
  "anything:hasInteger": {
    "@type": "knora-api:IntValue",
    "knora-api:intValueAsInt": 4
  },
  "@context": {
    "knora-api": "http://api.knora.org/ontology/knora-api/v2#",
    "anything": "http://0.0.0.0:3333/ontology/0001/anything/v2#"
  }
}
```

Each value can have a comment, given in `knora-api:valueHasComment`. For example:

```json
{
  "@id": "http://rdfh.ch/0001/a-thing",
  "@type": "anything:Thing",
  "anything:hasInteger": {
    "@type": "knora-api:IntValue",
    "knora-api:intValueAsInt": 4,
    "knora-api:valueHasComment": "This is a comment."
  },
  "@context": {
    "knora-api": "http://api.knora.org/ontology/knora-api/v2#",
    "anything": "http://0.0.0.0:3333/ontology/0001/anything/v2#"
  }
}
```

Permissions for the new value can be given by adding `knora-api:hasPermissions`. For example:

```json
{
  "@id": "http://rdfh.ch/0001/a-thing",
  "@type": "anything:Thing",
  "anything:hasInteger": {
    "@type": "knora-api:IntValue",
    "knora-api:intValueAsInt": 4,
    "knora-api:hasPermissions": "CR knora-admin:Creator|V http://rdfh.ch/groups/0001/thing-searcher"
  },
  "@context": {
    "knora-api": "http://api.knora.org/ontology/knora-api/v2#",
    "anything": "http://0.0.0.0:3333/ontology/0001/anything/v2#"
  }
}
```

Each value can have an optional custom IRI (of [Knora IRI](knora-iris.md#iris-for-data) form) specified by the `@id`
attribute, a custom creation date specified by adding `knora-api:valueCreationDate` (an
[xsd:dateTimeStamp](https://www.w3.org/TR/xmlschema11-2/#dateTimeStamp)), or a custom UUID given by
`knora-api:valueHasUUID`. Each custom UUID must be [base64url-encoded](rfc:4648#section-5), without padding. If a custom
UUID is provided, it will be used in value IRI. If a custom IRI is given for the value, its UUID should match the given
custom UUID. If a custom IRI is provided, but there is no custom UUID provided, then the UUID given in the IRI will be
assigned to the `knora-api:valueHasUUID`. A custom value IRI must be the IRI of the containing resource, followed by
a `/values/` and a custom ID string. For example:

```json
{
  "@id": "http://rdfh.ch/0001/a-thing",
  "@type": "anything:Thing",
  "anything:hasInteger": {
    "@id": "http://rdfh.ch/0001/a-thing/values/IN4R19yYR0ygi3K2VEHpUQ",
    "@type": "knora-api:IntValue",
    "knora-api:intValueAsInt": 21,
    "knora-api:valueHasUUID": "IN4R19yYR0ygi3K2VEHpUQ",
    "knora-api:valueCreationDate": {
      "@type": "xsd:dateTimeStamp",
      "@value": "2020-06-04T12:58:54.502951Z"
    }
  },
  "@context": {
    "knora-api": "http://api.knora.org/ontology/knora-api/v2#",
    "anything": "http://0.0.0.0:3333/ontology/0001/anything/v2#",
    "xsd": "http://www.w3.org/2001/XMLSchema#"
  }
}
```

The format of the object of `knora-api:hasPermissions` is described in
[Permissions](../../02-dsp-ontologies/knora-base.md#permissions).

If permissions are not given, configurable default permissions are used (see
[Default Object Access Permissions](../../05-internals/design/api-admin/administration.md#default-object-access-permissions)
).

To create a value, the user must have **modify permission** on the containing resource.

The response is a JSON-LD document containing:

- `@id`: the IRI of the value that was created.
- `@type`: the value's type.
- `knora-api:valueHasUUID`, the value's UUID, which remains stable across value versions
  (except for link values, as explained below).

### Creating a Link Between Resources

To create a link, you must create a `knora-api:LinkValue`, which represents metadata about the link. The property that
connects the resource to the `LinkValue` is a link value property, whose name is constructed by adding `Value` to the
name of the link property (see
[Links Between Resources](../../02-dsp-ontologies/knora-base.md#links-between-resources)). The triple representing the
direct link between the resources is created automatically. For example, if the link property that should connect the
resources is `anything:hasOtherThing`, we can create a link like this:

```json
{
  "@id": "http://rdfh.ch/0001/a-thing",
  "@type": "anything:Thing",
  "anything:hasOtherThingValue": {
    "@type": "knora-api:LinkValue",
    "knora-api:linkValueHasTargetIri": {
      "@id": "http://rdfh.ch/0001/tPfZeNMvRVujCQqbIbvO0A"
    }
  },
  "@context": {
    "xsd": "http://www.w3.org/2001/XMLSchema#",
    "knora-api": "http://api.knora.org/ontology/knora-api/v2#",
    "anything": "http://0.0.0.0:3333/ontology/0001/anything/v2#"
  }
}
```

As with ordinary values, permissions on links can be specified by adding `knora-api:hasPermissions`.

The response is a JSON-LD document containing:

- `@id`: the IRI of the value that was created.
- `@type`: the value's type.
- `knora-api:valueHasUUID`, the value's UUID, which remains stable across value versions, unless the link is changed to
  point to a different resource, in which case it is considered a new link and gets a new UUID. Changing a link's
  metadata, without changing its target, creates a new version of the link value with the same UUID.

### Creating a Text Value Without Standoff Markup

Use the predicate `knora-api:valueAsString` of `knora-api:TextValue`:

```json
{
  "@id": "http://rdfh.ch/0001/a-thing",
  "@type": "anything:Thing",
  "anything:hasText": {
    "@type": "knora-api:TextValue",
    "knora-api:valueAsString": "This is a text without markup."
  },
  "@context": {
    "knora-api": "http://api.knora.org/ontology/knora-api/v2#",
    "anything": "http://0.0.0.0:3333/ontology/0001/anything/v2#"
  }
}
```

### Creating a Text Value with Standoff Markup

Currently, the only way to create a text value with standoff markup
is to submit it in XML format using an XML-to-standoff mapping.
See [the overview](text/overview.md) for more detials.

#### Creating a Text Value with Standard Mapping

To create a value with the standard mapping (`http://rdfh.ch/standoff/mappings/StandardMapping`), we can make an XML
document like this:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<text>
    This text links to another <a class="salsah-link" href="http://rdfh.ch/0001/another-thing">resource</a>.
</text>
```

This document can then be embedded in a JSON-LD request, using the predicate `knora-api:textValueAsXml`:

```json
{
  "@id": "http://rdfh.ch/0001/a-thing",
  "@type": "anything:Thing",
  "anything:hasText": {
    "@type": "knora-api:TextValue",
    "knora-api:textValueAsXml": "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<text>\n   This text links to another <a class=\"salsah-link\" href=\"http://rdfh.ch/0001/another-thing\">resource</a>.\n</text>",
    "knora-api:textValueHasMapping": {
      "@id": "http://rdfh.ch/standoff/mappings/StandardMapping"
    }
  },
  "@context": {
    "knora-api": "http://api.knora.org/ontology/knora-api/v2#",
    "anything": "http://0.0.0.0:3333/ontology/0001/anything/v2#"
  }
}
```

Note that quotation marks and line breaks in the XML must be escaped, and that the IRI of the mapping must be provided.

#### Creating a Text Value with a Custom Mapping

To create a text value with custom mapping, the following steps are required:

1. Optionally, an XSL transformation resource (`kb:XSLTransformation`) can be created that may be defined as the default
   transformation of the mapping.
2. The mapping resource (`kb:XMLToStandoffMapping`) must be created, if it does not already exist.
3. The text value can be created as in the example above, using the mapping resource IRI in `kb:textValueHasMapping`.

The `kb:XSLTransformation` resource is a subclass of `kb:TextRepresentation`, so it has a `kb:hasTextFileValue` pointing
to a `kb:TextFileValue`
which represents the XSLT file stored in SIPI. For more Details, see [Creating File Values](#creating-file-values).

The `kb:XMLToStandoffMapping` resource requires the mapping XML as
specified [in the custom standoff documentation](text/custom-standoff.md#creating-a-custom-mapping).
If an XSL transformation has been defined, the IRI the transformation can be placed in the `<defaultXSLTransformation>`
tag of the mapping XML.

If a mapping has been defined, then requesting the text value will return both the `kb:textValueAsXml` and
the `kb:textValueAsHtml` properties,
where the XML can be used for editing the value, while the HTML can be used to display it.
If no mapping has been defined, only `kb:textValueAsXml` can be returned.

## Creating File Values

DSP-API supports the storage of certain types of data as files, using [SIPI](https://github.com/dhlab-basel/sipi)
(see [FileValue](../../02-dsp-ontologies/knora-base.md#filevalue)). DSP-API v2 currently supports using SIPI to store
the following types of files:

- Images: JPEG, JPEG2000, TIFF, or PNG which are stored internally as JPEG2000
- Documents: PDF
- Audio: MPEG or Waveform audio file format (.wav, .x-wav, .vnd.wave)
- Text files: CSV, JSON, ODD, RNG, TXT, XLS, XLSX, XML, XSD, XSL
- Video files: MP4
- Archive files: ZIP, TAR, GZIP

Support for other types of files will be added in the future.

The following sections describe the steps for creating a file value.

Files can be ingested into DSP using SIPI or DSP-INGEST (experimental).

### Upload Files to SIPI

The first step is to upload one or more files to SIPI, using a `multipart/form-data` request, where `sipihost`
represents the host and port on which SIPI is running:

```
HTTP POST to http://sipihost/upload?token=TOKEN
```

The `token` parameter must provide the [JSON Web Token](https://jwt.io/) that DSP-API returned when the client logged in.
Each body part in the request must contain a parameter `filename`, providing the file's original filename, which both
DSP-API and SIPI will store; these filenames can be descriptive and need not be unique.

SIPI stores the file in a temporary location. If the file is an image, it is converted first to JPEG2000 format, and the
converted file is stored.

SIPI then returns a JSON response that looks something like this:

```json
{
  "uploadedFiles": [
    {
      "originalFilename": "manuscript-1234-page-1.tiff",
      "internalFilename": "3UIsXH9bP0j-BV0D4sN51Xz.jp2",
      "temporaryBaseIIIFUrl": "http://sipihost/tmp"
    },
    {
      "originalFilename": "manuscript-1234-page-2.tiff",
      "internalFilename": "2RvJgguglpe-B45EOk0Gx8H.jp2",
      "temporaryBaseIIIFUrl": "http://sipihost/tmp"
    }
  ]
}
```

In this example, we uploaded two files to SIPI, so `uploadedFiles` is an array with two elements. For each file, we
have:

- the `originalFilename`, which we submitted when uploading the file
- the unique `internalFilename` that SIPI has randomly generated for the file
- the `temporaryBaseIIIFUrl`, which we can use to construct a IIIF URL for previewing the file

In the case of an image file, the client may now wish to get a thumbnail of each uploaded image, to allow the user to
confirm that the correct files have been uploaded. This can be done by adding IIIF parameters to `temporaryBaseIIIFUrl`.
For example, to get a JPG thumbnail image that is 150 pixels wide, you would add `/full/150,/0/default.jpg`.

### Upload Files to DSP-INGEST

Support for uploads with DSP-INGEST is now the preferred method of uploading files (and also for ingesting large amounts of data internally).

### Submit A File Value to DSP-API

A DSP-API `Representation` (i.e. a resource containing information about a file) must always have exactly one file value
attached to it. (see [Representations](../../02-dsp-ontologies/knora-base.md#representations)). Therefore, a request
to create a new file value must always be submitted as part of a request to create a new resource (see
[Creating a Resource](editing-resources.md#creating-a-resource)). You can also update a file value in an existing
`Representation`; see [Updating a Value](#updating-a-value).

Instead of providing the file's complete metadata to DSP-API, you just provide the unique internal filename generated by
SIPI.

#### Still Images

Still Image may be stored in SIPI or in an external IIIF server.

##### Images stored in SIPI

Here is an example of a request to create a resource of class `anything:ThingPicture` with a still image stored in SIPI.
The resource's class is a subclass of `knora-api:StillImageRepresentation` and therefore has the property `knora-api:hasStillImageFileValue`.
The file value is of type `knora-api:StillImageFileValue`:

```json
{
  "@type": "anything:ThingPicture",
  "knora-api:hasStillImageFileValue": {
    "@type": "knora-api:StillImageFileValue",
    "knora-api:fileValueHasFilename": "3UIsXH9bP0j-BV0D4sN51Xz.jp2"
  },
  "knora-api:attachedToProject": {
    "@id": "http://rdfh.ch/projects/0001"
  },
  "rdfs:label": "test thing",
  "@context": {
    "rdf": "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
    "knora-api": "http://api.knora.org/ontology/knora-api/v2#",
    "rdfs": "http://www.w3.org/2000/01/rdf-schema#",
    "xsd": "http://www.w3.org/2001/XMLSchema#",
    "anything": "http://0.0.0.0:3333/ontology/0001/anything/v2#"
  }
}
```

In the case of a `knora-api:StillImageFileValue` DSP-API gets the rest of the file's metadata from SIPI.
If the client's request to DSP-API is valid, DSP-API saves the file value in the triplestore and instructs SIPI to move the file to permanent storage.
Otherwise, the temporary file that was stored by SIPI is deleted.

##### Images stored in an external IIIF server

In the case of a Still image stored in an external IIIF server, the request is similar to the one above, but the file value is of type `knora-api:StillImageExternalFileValue`
and the `knora-api:externalUrl` property is used to provide the URL of the image in the IIIF server:

```json
{
  "@type": "anything:ThingPicture",
  "knora-api:hasStillImageFileValue": {
    "@type": "knora-api:StillImageExternalFileValue",
    "knora-api:stillImageFileValueHasExternalUrl": {
      "@type": "xsd:anyURI",
      "@value": "https://example.com/iiif/3UIsXH9bP0j-BV0D4sN51Xz.jp2/full/max/0/default.jpg"
    }
  },
  "knora-api:attachedToProject": {
    "@id": "http://rdfh.ch/projects/0001"
  },
  "rdfs:label": "test thing",
  "@context": {
    "rdf": "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
    "knora-api": "http://api.knora.org/ontology/knora-api/v2#",
    "rdfs": "http://www.w3.org/2000/01/rdf-schema#",
    "xsd": "http://www.w3.org/2001/XMLSchema#",
    "anything": "http://0.0.0.0:3333/ontology/0001/anything/v2#"
  }
}
```

!!! note
    For backwards compatibility, we support using `knora-api:fileValueHasExternalUrl` and 
    `knora-api:stillImageFileValueExternalFileValue` properties if the value is submitted as a `String` literal, i.e. 
    `"knora-api:stillImageFileValueExternalFileValue" : "https://example.com/iiif/3UIsXH9bP0j-BV0D4sN51Xz.jp2/full/max/0/default.jpg"`.

    Support for `String` literals and the `knora-api:fileValueHasExternalUrl` property 
    is deprecated and will be removed in the future.
    The `knora-api:stillImageFileValueHasExternalUrl` property with a `xsd:anyURI` type 
    is correct and must be used for reading and writing.

#### PDF Documents

If you're submitting a PDF document, use the resource class `knora-api:DocumentRepresentation`, which has the property
`knora-api:hasDocumentFileValue`, pointing to a `knora-api:DocumentFileValue`.

#### Text Files

For a text file, use `knora-api:TextRepresentation`, which has the property `knora-api:hasTextFileValue`, pointing to a
`knora-api:TextFileValue`.

#### Archive Files

For an archive like zip, use `knora-api:ArchiveRepresentation`, which has the property `knora-api:hasArchiveFileValue`,
pointing to a `knora-api:ArchiveFileValue`.

## Updating a Value

To update a value, use this route:

```
HTTP PUT to http://host/v2/values
```

Updating a value means creating a new version of an existing value. The new version will have a different IRI. The
request is the same as for creating a value, except that the `@id` of the current value version is given. For example,
to update an integer value:

```json
{
  "@id": "http://rdfh.ch/0001/a-thing",
  "@type": "anything:Thing",
  "anything:hasInteger": {
    "@id": "http://rdfh.ch/0001/a-thing/values/vp96riPIRnmQcbMhgpv_Rg",
    "@type": "knora-api:IntValue",
    "knora-api:intValueAsInt": 5
  },
  "@context": {
    "knora-api": "http://api.knora.org/ontology/knora-api/v2#",
    "anything": "http://0.0.0.0:3333/ontology/0001/anything/v2#"
  }
}
```

The value can be given a comment by using `knora-api:valueHasComment`. To change only the comment of a value, you can
resubmit the existing value with the updated comment.

Permissions can be specified by adding `knora-api:hasPermissions`. Otherwise, the new version has the same permissions
as the previous one. To change the permissions on a value, the user must have **change rights permission** on the value.

To update only the permissions on a value, submit it with the new permissions and with its `@id` and `@type` but without
any other content, like this:

```json
{
  "@id": "http://rdfh.ch/0001/a-thing",
  "@type": "anything:Thing",
  "anything:hasInteger": {
    "@id": "http://rdfh.ch/0001/a-thing/values/vp96riPIRnmQcbMhgpv_Rg",
    "@type": "knora-api:IntValue",
    "knora-api:hasPermissions": "CR knora-admin:Creator|V knora-admin:KnownUser"
  },
  "@context": {
    "knora-api": "http://api.knora.org/ontology/knora-api/v2#",
    "anything": "http://0.0.0.0:3333/ontology/0001/anything/v2#"
  }
}
```

To update a link, the user must have **modify permission** on the containing resource as well as on the value.

To update a value and give it a custom timestamp, add `knora-api:valueCreationDate` (an
[xsd:dateTimeStamp](https://www.w3.org/TR/xmlschema11-2/#dateTimeStamp)).

To update a value and give the new version a custom IRI, add `knora-api:newValueVersionIri`, like this:

```json
{
  "@id": "http://rdfh.ch/0001/a-thing",
  "@type": "anything:Thing",
  "anything:hasInteger": {
    "@id": "http://rdfh.ch/0001/a-thing/values/vp96riPIRnmQcbMhgpv_Rg",
    "@type": "knora-api:IntValue",
    "knora-api:intValueAsInt": 21,
    "knora-api:newValueVersionIri": {
      "@id": "http://rdfh.ch/0001/a-thing/values/int-value-IRI"
    }
  },
  "@context": {
    "knora-api": "http://api.knora.org/ontology/knora-api/v2#",
    "anything": "http://0.0.0.0:3333/ontology/0001/anything/v2#"
  }
}
```

A custom value IRI must be the IRI of the containing resource, followed by a `/values/` and a custom ID string.

The response is a JSON-LD document containing only `@id` and `@type`, returning the IRI and type of the new value
version.

If you submit an outdated value ID in a request to update a value, the response will be an HTTP 404 (Not Found) error.

The response to a value update request contains:

- `@id`: the IRI of the value that was created.
- `@type`: the value's type.
- `knora-api:valueHasUUID`, the value's UUID, which remains stable across value versions, unless the value is a link
  value and is changed to point to a different resource, in which case it is considered a new link and gets a new UUID.

## Deleting a Value

DSP-API does not normally delete values; instead, it marks them as deleted, which means that they do not appear in normal
query results.

To mark a value as deleted, use this route:

```
HTTP POST to http://host/v2/values/delete
```

The request must include the resource's ID and type, the property that points from the resource to the value, and the
value's ID and type. For example:

```json
{
  "@id": "http://rdfh.ch/0001/a-thing",
  "@type": "anything:Thing",
  "anything:hasInteger": {
    "@id": "http://rdfh.ch/0001/a-thing/values/vp96riPIRnmQcbMhgpv_Rg",
    "@type": "knora-api:IntValue",
    "knora-api:deleteComment": "This value was created by mistake."
  },
  "@context": {
    "knora-api": "http://api.knora.org/ontology/knora-api/v2#",
    "anything": "http://0.0.0.0:3333/ontology/0001/anything/v2#"
  }
}
```

The optional property `knora-api:deleteComment` specifies a comment to be attached to the value, explaining why it has
been marked as deleted

The optional property `knora-api:deleteDate` (an
[xsd:dateTimeStamp](https://www.w3.org/TR/xmlschema11-2/#dateTimeStamp))
specifies a custom timestamp indicating when the value was deleted. If not specified, the current time is used.

The response is a JSON-LD document containing the predicate `knora-api:result` with a confirmation message.

### Requesting Deleted Values

Values marked as deleted are not found in search queries. But when requesting a resource that has deleted values, these
will show up as generic `knora-api:DeletedValue` values. This value will be similar to the deleted value, having e.g.
the same IRI. The `DeletedValue` will contain the deletion date and optionally the deletion comment.

The response to requesting a deleted resource will look as the following example:

```json
{
  "knora-api:DeletedValue": [
    {
      "knora-api:versionArkUrl": {
        "@value": "http://0.0.0.0:3336/ark:/72163/1/0001/a=thingO/sWSymIzAS_qXqyHLhwbwwAU.20211216T18193124797Z",
        "@type": "xsd:anyURI"
      },
      "knora-api:userHasPermission": "RV",
      "knora-api:valueCreationDate": {
        "@value": "2021-12-16T18:19:31.247970Z",
        "@type": "xsd:dateTimeStamp"
      },
      "knora-api:deleteDate": {
        "@type": "xsd:dateTimeStamp",
        "@value": "2021-12-16T18:20:02.550828Z"
      },
      "knora-api:attachedToUser": {
        "@id": "http://rdfh.ch/users/9XBCrDV3SRa7kS1WwynB4Q"
      },
      "knora-api:valueHasUUID": "sWSymIzAS_qXqyHLhwbwwA",
      "knora-api:hasPermissions": "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
      "knora-api:isDeleted": true,
      "@type": "knora-api:DeletedValue",
      "http://www.knora.org/ontology/knora-base#DeletedValue": "DeletedValue",
      "@id": "http://rdfh.ch/0001/a-thing/values/DrXts3Up3DijGriI403nhg",
      "knora-api:deleteComment": "This value is obsolete",
      "knora-api:arkUrl": {
        "@value": "http://0.0.0.0:3336/ark:/72163/1/0001/a=thingO/sWSymIzAS_qXqyHLhwbwwAU",
        "@type": "xsd:anyURI"
      }
    },
    {}
  ]
}
```
