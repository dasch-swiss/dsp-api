<!---
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
-->

# XML to Standoff Mapping in API v2

## Creating a custom Mapping

The DSP-API's standard standoff mapping only supports a few HTML tags. In order to
submit more complex XML markup, a custom mapping has to be
created first. A mapping expresses the relations between XML
elements and attributes, and their corresponding standoff classes and
properties. The relations expressed in a mapping are one-to-one
relations, so the XML can be recreated from the data in RDF. However,
since HTML offers a very limited set of elements, custom mappings support
the combination of element names and classes. In this way, the same
element can be used several times in combination with another classname
(please note that `<a>` without a class is a hyperlink whereas `<a class="salsah-link">` is an internal link/standoff link).

With a mapping, a default XSL transformation may be provided to
transform the XML to HTML before sending it back to the client. This is
useful when the client is a web-browser expecting HTML (instead of XML).

### Basic Structure of a Mapping

The mapping is written in XML itself (for a formal description, see
`webapi/src/resources/mappingXMLToStandoff.xsd`). It has the following
structure (the indentation corresponds to the nesting in XML):

- `<mapping>`: the root element

  - `<defaultXSLTransformation> (optional)`: the IRI of the
    default XSL transformation to be applied to the XML when
    reading it back from DSP-API. The XSL transformation is
    expected to produce HTML. If given, the IRI has to refer to
    a resource of type `knora-base:XSLTransformation`.

  - `<mappingElement>`: an element of the mapping (at least
    one)

    - `<tag>`: information about the XML element that
      is mapped to a standoff class

      - `<name>`: name of the XML element
      - `<class>`: value of the class attribute of
        the XML element, if any. If the element has
        no class attribute, the keyword `noClass`
        has to be used.
      - `<namespace>`: the namespace the XML element
        belongs to, if any. If the element does not
        belong to a namespace, the keyword
        `noNamespace` has to be used.
      - `<separatesWords>`: a Boolean value
        indicating whether this tag separates words
        in the text. Once an XML document is
        converted to RDF-standoff the markup is
        stripped from the text, possibly leading to
        continuous text that has been separated by
        tags before. For structural tags like
        paragraphs etc., `<separatesWords>` can be
        set to `true` in which case a special
        separator is inserted in the the text in the
        RDF representation. In this way, words stay
        separated and are represented in the
        fulltext index as such.

    - `<standoffClass>`: information about the
      standoff class the XML element is mapped to

      - `<classIri>`: IRI of the standoff class the
        XML element is mapped to

      - `<attributes>`: XML attributes to be
        mapped to standoff properties (other
        than `id` or `class`), if any

        - `<attribute>`: an XML attribute
          to be mapped to a standoff
          property, may be repeated

          - `<attributeName>`: the name
            of the XML attribute
          - `<namespace>`: the namespace
            the attribute belongs to, if
            any. If the attribute does
            not belong to a namespace,
            the keyword `noNamespace`
            has to be used.
          - `<propertyIri>`: the IRI of
            the standoff property the
            XML attribute is mapped to.

      - `<datatype>`: the data type of the
        standoff class, if any.

        - `<type>`: the IRI of the data type
          standoff class
        - `<attributeName>`: the name of the
          attribute holding the typed value in
          the expected standard format

XML structure of a mapping:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<mapping>
    <defaultXSLTransformation>Iri of a knora-base:XSLTransformation</defaultXSLTransformation>
    <mappingElement>
        <tag>
            <name>XML element name</name>
            <class>XML class name or "noClass"</class>
            <namespace>XML namespace or "noNamespace"</namespace>
            <separatesWords>true or false</separatesWords>
        </tag>
        <standoffClass>
            <classIri>standoff class Iri</classIri>
            <attributes>
                <attribute>
                    <attributeName>XML attribute name</attributeName>
                    <namespace>XML namespace or "noNamespace"</namespace>
                    <propertyIri>standoff property Iri</propertyIri>
                </attribute>
            </attributes>
            <datatype>
                <type>standoff data type class</type>
                <attributeName>XML attribute with the typed value</attributeName>
            </datatype>
        </standoffClass>
    </mappingElement>
    <mappingElement>
       ...
    </mappingElement>
</mapping>
```

Please note that the absence of an XML namespace and/or a class have to
be explicitly stated using the keywords `noNamespace` and
`noClass`. This is because we use XML Schema validation to ensure the one-to-one
relations between XML elements and standoff classes. XML Schema validation's unique checks
do not support optional values.

### `id` and `class` Attributes

The `id` and `class` attributes are supported by default and do not have
to be included in the mapping like other attributes. The `id` attribute
identifies an element and must be unique in the document. `id` is an
optional attribute. The `class` attribute allows for the reuse of an
element in the mapping, i.e. the same element can be combined with
different class names and mapped to different standoff classes (mapping
element `<class>` in `<tag>`).

### Respecting Cardinalities

A mapping from XML elements and attributes to standoff classes and
standoff properties must respect the cardinalities defined in the
ontology for those very standoff classes. If an XML element is mapped to
a certain standoff class and this class requires a standoff property, an
attribute must be defined for the XML element mapping to that very
standoff property. Equally, all mappings for attributes of an XML
element must have corresponding cardinalities for standoff properties
defined for the standoff class the XML element maps to.

However, since an XML attribute may occur once at maximum, it makes
sense to make the corresponding standoff property required
(`owl:cardinality` of one) in the ontology or optional
(`owl:maxCardinality` of one), but not allowing it more than once.


### Standoff Data Types

DSP-API allows the use of all its value types as standoff data types
(defined in `knora-base.ttl`):

- `knora-base:StandoffLinkTag`: Represents a reference to a 
  resource (the IRI of the target resource must be submitted in the
  data type attribute).
- `knora-base:StandoffInternalReferenceTag`: Represents an internal
  reference inside a document (the id of the target element inside the
  same document must be indicated in the data type attribute); see
  [Internal References in an XML Document](#internal-references-in-an-xml-document).
- `knora-base:StandoffUriTag`: Represents a reference to a URI (the
  URI of the target resource must be submitted in the data type
  attribute).
- `knora-base:StandoffDateTag`: Represents a date (a date
  string must be submitted in the data type attribute, e.g.
  `GREGORIAN:2017-01-27`).
- `knora-base:StandoffColorTag`: Represents a color (a hexadecimal
  RGB color string must be submitted in the data type attribute, e.g.
  `#0000FF`).
- `knora-base:StandoffIntegerTag`: Represents an integer (the integer
  must be submitted in the data type attribute).
- `knora-base:StandoffDecimalTag`: Represents a number with fractions
  (the decimal number must be submitted in the data type attribute,
  e.g. `1.1`).
- `knora-base:StandoffIntervalTag`: Represents an interval (two
  decimal numbers separated with a comma must be submitted in the data
  type attribute, e.g. `1.1,2.2`).
- `knora-base:StandoffBooleanTag`: Represents a Boolean value (`true`
  or `false` must be submitted in the data type attribute).
- `knora-base:StandoffTimeTag`: Represents a timestamp value (an `xsd:dateTimeStamp`
  must be submitted in the data type attribute).

The basic idea is that parts of a text can be marked up in a way that
allows using DSP-API's built-in data types. In order to do so, the typed
values have to be provided in a standardized way in an attribute that
has to be defined in the mapping.

Data type standoff classes are standoff classes with predefined
properties (e.g., a `knora-base:StandoffLinkTag` has a
`knora-base:standoffTagHasLink` and a `knora-base:StandoffIntegerTag`
has a `knora-base:valueHasInteger`). Please note the data type standoff
classes can not be combined, i.e. a standoff class can only be the
subclass of **one** data type standoff class. However, standoff data
type classes can be subclassed and extended further by assigning
properties to them (see below).

The following simple mapping illustrates this principle:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<mapping>
     <mappingElement>
        <tag>
            <name>text</name>
            <class>noClass</class>
            <namespace>noNamespace</namespace>
            <separatesWords>false</separatesWords>
        </tag>
        <standoffClass>
            <classIri>http://www.knora.org/ontology/standoff#StandoffRootTag</classIri>
        </standoffClass>
    </mappingElement>

    <mappingElement>
        <tag>
            <name>mydate</name>
            <class>noClass</class>
            <namespace>noNamespace</namespace>
            <separatesWords>false</separatesWords>
        </tag>
        <standoffClass>
            <classIri>http://www.knora.org/ontology/0001/anything#StandoffEventTag</classIri>
            <attributes>
                <attribute>
                    <attributeName>description</attributeName>
                    <namespace>noNamespace</namespace>
                    <propertyIri>http://www.knora.org/ontology/0001/anything#standoffEventTagHasDescription</propertyIri>
                </attribute>
            </attributes>
            <datatype>
                <type>http://www.knora.org/ontology/knora-base#StandoffDateTag</type>
                <attributeName>knoraDate</attributeName>
            </datatype>
        </standoffClass>
    </mappingElement>
</mapping>
```

`<datatype>` **must** hold the IRI of a standoff data type class (see
list above). The `<classIri>` must be a subclass of this type or this
type itself (the latter is probably not recommendable since semantics
are missing: what is the meaning of the date?). In the example above,
the standoff class is `anything:StandoffEventTag` which has the
following definition in the ontology `anything-onto.ttl`:

```
anything:StandoffEventTag rdf:type owl:Class ;

    rdfs:subClassOf knora-base:StandoffDateTag,
                   [
                      rdf:type owl:Restriction ;
                      owl:onProperty :standoffEventTagHasDescription ;
                      owl:cardinality "1"^^xsd:nonNegativeInteger
                   ] ;

    rdfs:label "Represents an event in a TextValue"@en ;

    rdfs:comment """Represents an event in a TextValue"""@en .
```

`anything:StandoffEventTag` is a subclass of
`knora-base:StandoffDateTag` and therefore has the data type date. It
also requires the standoff property
`anything:standoffEventTagHasDescription` which is defined as an
attribute in the mapping.

Once the mapping has been created, an XML like the following could be
sent to DSP-API and converted to standoff:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<text>
    We had a party on <mydate description="new year" knoraDate="GREGORIAN:2016-12-31">New Year's Eve</mydate>. It was a lot of fun.
</text>
```

The attribute holds the date in the format of a DSP-API date string (the
format is also documented in the typescript type alias `dateString` in
module `basicMessageComponents`. There you will also find documentation
about the other types like color etc.). DSP-API date strings have this
format: `GREGORIAN|JULIAN):YYYY[-MM[-DD]][:YYYY[-MM[-DD]]]`. This allows
for different formats as well as for imprecision and periods. Intervals
are submitted as one attribute in the following format:
`interval-attribute="1.0,2.0"` (two decimal numbers separated with a
comma).

You will find a sample mapping with all the data types and a sample XML
file in the the test data:
`test_data/test_route/texts/mappingForHTML.xml` and
`test_data/test_route/texts/HTML.xml`.

### Internal References in an XML Document

Internal references inside an XML document can be represented using the
data type standoff class `knora-base:StandoffInternalReferenceTag` or a
subclass of it. This class has a standoff property that points to a
standoff node representing the target XML element when converted to RDF.

The following example shows the definition of a mapping element for an
internal reference (for reasons of simplicity, only the mapping element
for the element is question is depicted):

```xml
<?xml version="1.0" encoding="UTF-8"?>
<mappingElement>
    <tag>
        <name>ref</name>
        <class>noClass</class>
        <namespace>noNamespace</namespace>
        <separatesWords>false</separatesWords>
    </tag>
    <standoffClass>
        <classIri>http://www.knora.org/ontology/knora-base#StandoffInternalReferenceTag</classIri>
        <datatype>
            <type>http://www.knora.org/ontology/knora-base#StandoffInternalReferenceTag</type>
            <attributeName>internalRef</attributeName>
        </datatype>
    </standoffClass>
</mappingElement>
```

Now, an internal reference to an element in the same document can be
made that will be converted to a pointer in RDF:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<text>
    This is an <sample id="1">element</sample> and here is a reference to <ref internalRef="#1">it</ref>.
</text>
```

An internal reference in XML has to start with a `#` followed by the
value of the `id` attribute of the element referred to.

### Predefined Standoff Classes and Properties

The standoff ontology `standoff-onto.ttl` offers a set of predefined
standoff classes that can be used in a custom mapping like the
following:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<mapping>
    <mappingElement>
        <tag>
            <name>myDoc</name>
            <class>noClass</class>
            <namespace>noNamespace</namespace>
            <separatesWords>false</separatesWords>
        </tag>
        <standoffClass>
            <classIri>http://www.knora.org/ontology/standoff#StandoffRootTag</classIri>
            <attributes>
                <attribute>
                    <attributeName>documentType</attributeName>
                    <namespace>noNamespace</namespace>
                    <propertyIri>http://www.knora.org/ontology/standoff#standoffRootTagHasDocumentType</propertyIri>
                </attribute>
            </attributes>
        </standoffClass>
    </mappingElement>

    <mappingElement>
        <tag>
            <name>p</name>
            <class>noClass</class>
            <namespace>noNamespace</namespace>
            <separatesWords>true</separatesWords>
        </tag>
        <standoffClass>
            <classIri>http://www.knora.org/ontology/standoff#StandoffParagraphTag</classIri>
        </standoffClass>
    </mappingElement>

    <mappingElement>
        <tag>
            <name>i</name>
            <class>noClass</class>
            <namespace>noNamespace</namespace>
            <separatesWords>false</separatesWords>
        </tag>
        <standoffClass>
            <classIri>http://www.knora.org/ontology/standoff#StandoffItalicTag</classIri>
        </standoffClass>
    </mappingElement>
</mapping>
```

Predefined standoff classes may be used by various projects, each
providing a custom mapping to be able to recreate the original XML from
RDF. Predefined standoff classes may also be inherited and extended in
project specific ontologies.

The mapping above allows for an XML like this:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<myDoc documentType="letter">
    <p>
        This my text that is <i>very</i> interesting.
    </p>
    <p>
        And here it goes on.
    </p>
</myDoc>
```

### Respecting Property Types

When mapping XML attributes to standoff properties, attention has to be
paid to the properties' object constraints.

In the ontology, standoff property literals may have one of the
following `knora-base:objectDatatypeConstraint`:

- `xsd:string`
- `xsd:integer`
- `xsd:boolean`
- `xsd:decimal`
- `xsd:anyURI`

In XML, all attribute values are submitted as strings. However, these
string representations need to be convertible to the types defined in
the ontology. If they are not, the request will be rejected. It is
recommended to enforce types on attributes by applying XML Schema
validations (restrictions).

Links (object property) to a `knora-base:Resource` can be represented
using the data type standoff class `knora-base:StandoffLinkTag`,
internal links using the data type standoff class
`knora-base:StandoffInternalReferenceTag`.


## Validating a Mapping and sending it to DSP-API

A mapping can be validated before sending it to DSP-API with the following
XML Schema file: `webapi/src/resources/mappingXMLToStandoff.xsd`. Any
mapping that does not conform to this XML Schema file will be rejected
by DSP-API.

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

A successful response returns the IRI of the mapping. However, the IRI
of a mapping is predictable: it consists of the project Iri followed by
`/mappings/` and the `knora-api:mappingHasName` submitted in the JSON-LD (if the name
already exists, the request will be rejected). Once created, a mapping
can be used to create TextValues in Knora. The formats are documented in
the v2 typescript interfaces `AddMappingRequest` and `AddMappingResponse`
in module `MappingFormats`
