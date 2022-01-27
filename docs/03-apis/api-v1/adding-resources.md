<!---
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
-->

# Adding Resources

To create a resource, the HTTP method `POST` has to be used.
The request has to be sent to the Knora server using the `resources`
path segment:

```
HTTP POST to http://host/v1/resources
```

Unlike in the case of GET requests, the request body consists of JSON
describing the resource to be created.

Creating resources requires authentication since only known users may
add resources.

## Adding Resources Without Image Files

The format of the JSON used to create a resource without an image file is
described in the TypeScript interface
`createResourceWithoutRepresentationRequest` in module
`createResourceFormats`. It requires the IRI of the resource class the
new resource belongs to, a label describing the new resource, the IRI of
the project the new resource belongs to, and the properties to be
assigned to the new resource.

The request header's content type has to be set to `application/json`.

## Adding Resources with Image Files

The first step is to upload an image file to Sipi, using a
`multipart/form-data` request, where `sipihost` represents the host and
port on which Sipi is running:

```
HTTP POST to http://sipihost/upload?token=TOKEN
```

The `TOKEN` is the `sid` returned by Knora in response to the
client's login request (see [Authentication](authentication.md)).
The request must contain a body part providing the file as well as a parameter
`filename`, providing the file's original filename, which both Knora and Sipi will
store; these filenames can be descriptive and need not be unique.

Sipi will then convert the uploaded image file to JPEG 2000 format and store
it in a temporary location. If this is successful, it will return a JSON
response that looks something like this:

```json
{
  "uploadedFiles": [{
    "originalFilename": "manuscript-1234-page-1.tiff",
    "internalFilename": "3UIsXH9bP0j-BV0D4sN51Xz.jp2",
    "temporaryBaseIIIFUrl": "http://sipihost/tmp"
  }]
}
```

This provides:

- the `originalFilename`, which we submitted when uploading the file
- the unique `internalFilename` that Sipi has randomly generated for the file
- the `temporaryBaseIIIFUrl`, which we can use to construct a IIIF URL for
  previewing the file

The client may now wish to get a thumbnail of the uploaded image, to allow
the user to confirm that the correct files have been uploaded. This can be done
by adding the filename and IIIF parameters to `temporaryBaseIIIFUrl`. For example, to get
a JPG thumbnail image whose width and height are at most 128 pixels wide, you would request
`http://sipihost/tmp/3UIsXH9bP0j-BV0D4sN51Xz.jp2/full/!128,128/0/default.jpg`.

The request to Knora works similarly to
[Adding Resources Without Image Files](#adding-resources-without-image-files),
with the addition of `file`, whose value is the `internalFilename` that Sipi returned.
See the TypeScript interface `createResourceWithRepresentationRequest` in
module `createResourceFormats` for details. The request header's content type must be
set to `application/json`.

## Response to a Resource Creation

When a resource has been successfully created, Knora sends back a JSON
containing the new resource's IRI (`res_id`) and its properties. The
resource IRI identifies the resource and can be used to perform future
DSP-API V1 operations.

The JSON format of the response is described in the TypeScript interface
`createResourceResponse` in module `createResourceFormats`.

## Changing a Resource's Label

A resource's label can be changed by making a PUT request to the path
segments `resources/label`. The resource's IRI has to be provided in the
URL (as its last segment). The new label has to submitted as JSON in the
HTTP request's body.

```
HTTP PUT to http://host/v1/resources/label/resourceIRI
```

The JSON format of the request is described in the TypeScript interface
`changeResourceLabelRequest` in module `createResourceFormats`. The
response is described in the TypeScript interface
`changeResourceLabelResponse` in module `createResourceFormats`.

## Bulk Import

If you have a large amount of data to import into Knora, it can be more
convenient to use the bulk import feature than to create resources one
by one. In a bulk import operation, you submit an XML document to Knora,
describing multiple resources to be created. This is especially useful
if the resources to be created have links to one another. Knora checks
the entire request for consistency as as a whole, and performs the
update in a single database transaction.

Only system or project administrators may use the bulk import.

The procedure for using this feature is as follows
(see the [example below](#bulk-import-example)).

1. Make an HTTP GET request to Knora to [get XML schemas](#1-get-xml-schemas) describing
   the XML to be provided for the import.
    
2. If you are importing image files, [upload files to Sipi](#2-upload-files-to-sipi).

3. [Generate an XML import document](#3-generate-xml-import-document) representing the
   data to be imported, following the Knora import schemas that were generated in step 1.
   You will probably want to write a script to do this. Knora is not involved in this step.
   If you are also importing image files, this XML document needs to
   [contain the filenames](#bulk-import-with-image-files) that Sipi returned
   for the files you uploaded in step 2.

4. [Validate your XML import document](#4-validate-xml-import-document), using an XML schema validator such as
   [Apache Xerces](http://xerces.apache.org) or [Saxon](http://www.saxonica.com), or an
   XML development environment such as [Oxygen](https://www.oxygenxml.com). This will
   help ensure that the data you submit to Knora is correct. Knora is not involved in this step.

5. [Submit the XML import document to Knora](#5-submit-xml-import-document-to-knora).

In this procedure, the person responsible for generating the XML import
data need not be familiar with RDF or with the ontologies involved.

When Knora receives an XML import, it validates it first using the
relevant XML schemas, and then using the same internal checks that it
performs when creating any resource.

The details of the XML import format are illustrated in the following
examples.

### Bulk Import Example

Suppose we have a project with existing data (but no image files),
which we want to import into Knora. We have created an
ontology called `http://www.knora.org/ontology/0801/biblio` for the
project, and this ontology also uses definitions from another ontology,
called `http://www.knora.org/ontology/0801/beol`.

#### 1. Get XML Schemas

To get XML schemas for an import, we use the following route, specifying
the (URL-encoded) IRI of our project's main ontology (in this case
`http://www.knora.org/ontology/0801/biblio`):

```
HTTP GET to http://host/v1/resources/xmlimportschemas/ontologyIRI
```

In our example, the URL could be:

```
http://localhost:3333/v1/resources/xmlimportschemas/http%3A%2F%2Fwww.knora.org%2Fontology%2F0801%2Fbiblio
```

This returns a Zip archive called `p0801-biblio-xml-schemas.zip`,
containing three files:

- `p0801-biblio.xsd`: The schema for our main ontology.

- `p0801-beol.xsd`: A schema for another ontology that our main ontology depends on.

- `knoraXmlImport.xsd`: The standard Knora XML import schema, used by all XML imports.

#### 2. Upload Files to Sipi

See [Upload Files to Sipi](../api-v2/editing-values.md#upload-files-to-sipi) in
the DSP-API v2 documentation.

#### 3. Generate XML Import Document

We now convert our existing data to XML, probably by writing a custom
script. The resulting XML import document could look like this:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<knoraXmlImport:resources xmlns="http://api.knora.org/ontology/0801/biblio/xml-import/v1#"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://api.knora.org/ontology/0801/biblio/xml-import/v1# p0801-biblio.xsd"
    xmlns:p0801-biblio="http://api.knora.org/ontology/0801/biblio/xml-import/v1#"
    xmlns:p0801-beol="http://api.knora.org/ontology/0801/beol/xml-import/v1#"
    xmlns:knoraXmlImport="http://api.knora.org/ontology/knoraXmlImport/v1#">
    <p0801-beol:person id="abel">
        <knoraXmlImport:label>Niels Henrik Abel</knoraXmlImport:label>
        <p0801-beol:hasFamilyName knoraType="richtext_value">Abel</p0801-beol:hasFamilyName>
        <p0801-beol:hasGivenName knoraType="richtext_value">Niels Henrik</p0801-beol:hasGivenName>
        <p0801-beol:personHasTitle knoraType="richtext_value" lang="en">Sir</p0801-beol:personHasTitle>
    </p0801-beol:person>
    <p0801-beol:person id="holmes">
        <knoraXmlImport:label>Sherlock Holmes</knoraXmlImport:label>
        <p0801-beol:hasFamilyName knoraType="richtext_value">Holmes</p0801-beol:hasFamilyName>
        <p0801-beol:hasGivenName knoraType="richtext_value">Sherlock</p0801-beol:hasGivenName>
    </p0801-beol:person>
    <p0801-biblio:Journal id="math_intelligencer">
        <knoraXmlImport:label>Math Intelligencer</knoraXmlImport:label>
        <p0801-biblio:hasName knoraType="richtext_value">Math Intelligencer</p0801-biblio:hasName>
    </p0801-biblio:Journal>
    <p0801-biblio:JournalArticle id="strings_in_the_16th_and_17th_centuries" creationDate="2019-01-09T15:45:54Z">
        <knoraXmlImport:label>Strings in the 16th and 17th Centuries</knoraXmlImport:label>
        <p0801-biblio:p0801-beol__comment knoraType="richtext_value" mapping_id="http://rdfh.ch/standoff/mappings/StandardMapping">
            <text xmlns="">The most <strong>interesting</strong> article in <a class="salsah-link" href="ref:math_intelligencer">Math Intelligencer</a>.</text>
        </p0801-biblio:p0801-beol__comment>
        <p0801-biblio:endPage knoraType="richtext_value">73</p0801-biblio:endPage>
        <p0801-biblio:isPartOfJournal>
            <p0801-biblio:Journal knoraType="link_value" target="math_intelligencer" linkType="ref"/>
        </p0801-biblio:isPartOfJournal>
        <p0801-biblio:journalVolume knoraType="richtext_value">27</p0801-biblio:journalVolume>
        <p0801-biblio:publicationHasAuthor>
            <p0801-beol:person knoraType="link_value" linkType="ref" target="abel"/>
        </p0801-biblio:publicationHasAuthor>
        <p0801-biblio:publicationHasAuthor>
            <p0801-beol:person knoraType="link_value" linkType="ref" target="holmes"/>
        </p0801-biblio:publicationHasAuthor>
        <p0801-biblio:publicationHasDate knoraType="date_value">GREGORIAN:1976</p0801-biblio:publicationHasDate>
        <p0801-biblio:publicationHasTitle knoraType="richtext_value" lang="en">Strings in the 16th and 17th Centuries</p0801-biblio:publicationHasTitle>
        <p0801-biblio:publicationHasTitle knoraType="richtext_value">An alternate title</p0801-biblio:publicationHasTitle>
        <p0801-biblio:startPage knoraType="richtext_value">48</p0801-biblio:startPage>
    </p0801-biblio:JournalArticle>
</knoraXmlImport:resources>
```

This illustrates several aspects of XML imports:

  - The root XML element must be `knoraXmlImport:resources`.

  - There is an XML namespace corresponding each ontology used in the
    import. These namespaces can be found in the XML schema files
    returned by Knora.

  - We have copied and pasted
    `xmlns="http://api.knora.org/ontology/0801/biblio/xml-import/v1#"`
    from the main XML schema, `p0801-biblio.xsd`. This enables the Knora
    API server to identify the main ontology we are using.

  - We have used `xsi:schemaLocation` to indicate the main schema's
    namespace and filename. If we put our XML document in the same
    directory as the schemas, and we run an XML validator to check the
    XML, it should load the schemas.

  - The child elements of `knoraXmlImport:resources` represent resources
    to be created. The order of these elements is unimportant.

  - Each resource must have an ID, which must be an XML
    [NCName](https://www.w3.org/TR/REC-xml-names/#NT-NCName), and must
    be unique within the file. These IDs are used only during the
    import, and will not be stored in the triplestore.

  - Each resource can optionally have a `creationDate` attribute, which
    can be an [xsd:dateTime](https://www.w3.org/TR/xmlschema11-2/#dateTime)
    or an [xsd:dateTimeStamp](https://www.w3.org/TR/xmlschema11-2/#dateTimeStamp).
    If `creationDate` is not supplied, the current time is used.

  - The first child element of each resource must be a
    `knoraXmlImport:label`, which will be stored as the resource's
    `rdfs:label`.

  - Optionally, the second child element of a resource can provide
    metadata about a file to be attached to the resource (see
    bulk-import-with-digital-representations).

  - The remaining child elements of each resource represent its property
    values. These must be sorted in alphabetical order by property name.

  - If a property has mutliple values, these are represented as multiple
    adjacent property elements.

  - The type of each value must be specified using the attribute
    `knoraType`.

  - A link to another resource described in the XML import is
    represented as a child element of a property element, with
    attributes `knoraType="link_value"` and `linkType="ref"`, and a
    `target` attribute containing the ID of the target resource.

  - There is a specfic syntax for referring to properties from other
    ontologies. In the example, `p0801-beol:comment` is defined in the
    ontology `http://www.knora.org/ontology/0001/beol`. In the XML, we
    refer to it as `p0801-biblio:p0801-beol__comment`.

  - A text value can contain XML markup. If it does:

      + The text value element must have the attribute `mapping_id`,
        specifying a mapping from XML to standoff markup (see
        XML-to-standoff-mapping).
      + It is necessary to specify the appropriate XML namespace (in
        this case the null namespace, `xmlns=""`) for the XML markup
        in the text value.
      + The XML markup in the text value will not be validated by
        the schema.
      + In an XML tag that is mapped to a standoff link tag, the
        link target can refer either to the IRI of a resoruce that
        already exists in the triplestore, or to the ID of a
        resource described in the import. If a link points to a
        resource described in the import, the ID of the target
        resource must be prefixed with `ref:`. In the example above,
        using the standard mapping, the standoff link to
        `math_intelligencer` has the target
        `ref:math_intelligencer`.

  - A text value can have a `lang` attribute, whose value is an ISO 639-1
    code specifying the language of the text.

#### 4. Validate XML Import Document

You can use an XML schema validator such as [Apache Xerces](http://xerces.apache.org) or
[Saxon](http://saxon.sourceforge.net/), or an XML development environment
such as [Oxygen](https://www.oxygenxml.com), to check that your XML import document
is valid according to the schemas you got from Knora.

For example, using Saxon:

```
java -cp ./saxon9ee.jar com.saxonica.Validate -xsd:p0801-biblio.xsd -s:data.xml
```

#### 5. Submit XML Import Document to Knora

To create these resources in Knora, make an HTTP post request with the XML import document
as the request body. The URL must specify the (URL-encoded) IRI of the project in which
the resources should be created:

```
HTTP POST to http://host/v1/resources/xmlimport/projectIRI
```

For example, using [curl](https://curl.haxx.se/):

```
curl -v -u root@example.com:test --data @data.xml --header "Content-Type: application/xml" http://localhost:3333/v1/resources/xmlimport/http%3A%2F%2Frdfh.ch%2Fprojects%2F0801
```

### Bulk Import with Links to Existing Resources

Having run the import in the previous example, we can import more data
with links to the data that is now in the triplestore:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<knoraXmlImport:resources xmlns="http://api.knora.org/ontology/0801/biblio/xml-import/v1#"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://api.knora.org/ontology/0801/biblio/xml-import/v1# p0801-biblio.xsd"
    xmlns:p0801-biblio="http://api.knora.org/ontology/0801/biblio/xml-import/v1#"
    xmlns:p0801-beol="http://api.knora.org/ontology/0801/beol/xml-import/v1#"
    xmlns:knoraXmlImport="http://api.knora.org/ontology/knoraXmlImport/v1#">
    <p0801-biblio:JournalArticle id="strings_in_the_18th_century">
        <knoraXmlImport:label>Strings in the 18th Century</knoraXmlImport:label>
        <p0801-biblio:p0801-beol__comment knoraType="richtext_value" mapping_id="http://rdfh.ch/standoff/mappings/StandardMapping">
            <text xmlns="">The most <strong>boring</strong> article in <a class="salsah-link" href="http://rdfh.ch/biblio/QMDEHvBNQeOdw85Z2NSi9A">Math Intelligencer</a>.</text>
        </p0801-biblio:p0801-beol__comment>
        <p0801-biblio:endPage knoraType="richtext_value">76</p0801-biblio:endPage>
        <p0801-biblio:isPartOfJournal>
            <p0801-biblio:Journal knoraType="link_value" linkType="iri" target="http://rdfh.ch/biblio/QMDEHvBNQeOdw85Z2NSi9A"/>
        </p0801-biblio:isPartOfJournal>
        <p0801-biblio:journalVolume knoraType="richtext_value">27</p0801-biblio:journalVolume>
        <p0801-biblio:publicationHasAuthor>
            <p0801-beol:person knoraType="link_value" linkType="iri" target="http://rdfh.ch/biblio/c-xMB3qkRs232pWyjdUUvA"/>
        </p0801-biblio:publicationHasAuthor>
        <p0801-biblio:publicationHasDate knoraType="date_value">GREGORIAN:1977</p0801-biblio:publicationHasDate>
        <p0801-biblio:publicationHasTitle knoraType="richtext_value">Strings in the 18th Century</p0801-biblio:publicationHasTitle>
        <p0801-biblio:startPage knoraType="richtext_value">52</p0801-biblio:startPage>
    </p0801-biblio:JournalArticle>
</knoraXmlImport:resources>
```

Note that in the link elements referring to existing resources, the
`linkType` attribute has the value `iri`, and the `target` attribute
contains the IRI of the target resource.

### Bulk Import with Image Files

To attach an image file to a resource, we must provide the
element `knoraXmlImport:file` before the property elements. In this
element, we must provide a `filename` attribute, containing the `internalFilename`
that Sipi returned for the file in [2. Upload Files to Sipi](#2-upload-files-to-sipi).

```xml
<?xml version="1.0" encoding="UTF-8"?>
<knoraXmlImport:resources xmlns="http://api.knora.org/ontology/incunabula/xml-import/v1#"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://api.knora.org/ontology/incunabula/xml-import/v1# incunabula.xsd"
    xmlns:incunabula="http://api.knora.org/ontology/incunabula/xml-import/v1#"
    xmlns:knoraXmlImport="http://api.knora.org/ontology/knoraXmlImport/v1#">
    <incunabula:book id="test_book">
        <knoraXmlImport:label>a book with one page</knoraXmlImport:label>
        <incunabula:title knoraType="richtext_value">the title of a book with one page</incunabula:title>
    </incunabula:book>
    <incunabula:page id="test_page">
        <knoraXmlImport:label>a page with an image</knoraXmlImport:label>
        <knoraXmlImport:file filename="67SEfNU1wK2-CSf5abe2eh3.jp2"/>
        <incunabula:origname knoraType="richtext_value">Chlaus</incunabula:origname>
        <incunabula:pagenum knoraType="richtext_value">1a</incunabula:pagenum>
        <incunabula:partOf>
            <incunabula:book knoraType="link_value" linkType="ref" ref="test_book"/>
        </incunabula:partOf>
        <incunabula:seqnum knoraType="int_value">1</incunabula:seqnum>
    </incunabula:page>
</knoraXmlImport:resources>
```

During the processing of the bulk import, Knora will ask Sipi for the rest of the
file's metadata, and store that metadata in a file value attached to the resource.
