.. Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
   Tobias Schweizer, André Kilchenmann, and Sepideh Alassi.

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

.. _XML-to-standoff-mapping:

XML to Standoff Mapping
=======================

.. contents:: :local:

**************************
The Knora Standard Mapping
**************************

-----------
Description
-----------

A mapping allows for the conversion of XML to standoff representation in RDF and back. In order to create a TextValue with markup, the text has to be provided in XML format, along with the IRI of the mapping that will be used to convert the markup to standoff.
However, a mapping is only needed if a TextValue with markup should be created. If a text has no markup, it is submitted as a mere sequence of characters.

The two cases are described in the TypeScript interfaces ``simpletext`` and ``richtext`` in module ``basicMessageComponents``.

Knora offers a standard mapping with the IRI ``http://data.knora.org/projects/standoff/mappings/StandardMapping``. The standard mapping covers the HTML elements and attributes supported by the GUI's text editor CKEditor [1]_
(please note that the HTML has to be encoded in strict XML syntax). The standard mapping contains the following elements and attributes that are mapped to standoff classes and properties defined in the ontology:

 - ``<text>`` -> ``standoff:StandoffRootTag``
 - ``<p>`` -> ``standoff:StandoffParagraphTag``
 - ``<em>`` -> ``standoff:StandoffItalicTag``
 - ``<strong>`` -> ``standoff:StandoffBoldTag``
 - ``<u>`` -> ``standoff:StandoffUnderlineTag``
 - ``<sub>`` -> ``standoff:StandoffSubscriptTag``
 - ``<sup>`` -> ``standoff:StandoffSuperscriptTag``
 - ``<strike>`` -> ``standoff:StandoffStrikeTag``
 - ``<a href="URL">`` -> ``knora-base:StandoffUriTag``
 - ``<a class="salsah-link" href="Knora IRI">`` -> ``knora-base:StandoffLinkTag``
 - ``<h1>`` to ``<h6>`` -> ``standoff:StandoffHeader1Tag`` to ``standoff:StandoffHeader6Tag``
 - ``<ol>`` -> ``standoff:StandoffOrderedListTag``
 - ``<ul>`` -> ``standoff:StandoffUnrderedListTag``
 - ``<li>`` -> ``standoff:StandoffListElementTag``
 - ``<tbody>`` -> ``standoff:StandoffTableBodyTag``
 - ``<table>`` -> ``standoff:StandoffTableTag``
 - ``<tr>`` -> ``standoff:StandoffTableRowTag``
 - ``<td>`` -> ``standoff:StandoffTableCellTag``
 - ``<br>`` -> ``standoff:StandoffBrTag``
 - ``<hr>`` -> ``standoff:StandoffLineTag``
 - ``<pre>`` -> ``standoff:StandoffPreTag``
 - ``<cite>`` -> ``standoff:StandoffCiteTag``
 - ``<blockquote>`` -> ``standoff:StandoffBlockquoteTag``
 - ``<code>`` -> ``standoff:StandoffCodeTag``

The HTML produced by CKEditor is wrapped in an XML doctype and a pair of root tags ``<text>...</text>`` and then sent to Knora. The XML sent to the GUI by Knora is unwrapped accordingly (see ``jquery.htmleditor.js``).
Although the GUI supports HTML5, it is treated as if it was XHTML in strict XML notation.

-----------
Maintenance
-----------

The standard mapping definition can be found at ``webapi/_test_data/test_route/texts/mappingForStandardHTML.xml``.  
It was used to generate the default mapping, distributed 
as ``knora-ontologies/standoff-data.ttl`` and that is loaded at a Knora installation.  
It should be used to re-generate it, whenever we want to amend or extend it. 

Note: once the mapping has been generated, one has to rework the resources' UUID in order to maintain backward compatibility. 


*************************
Creating a custom Mapping
*************************

The Knora standard mapping only supports a few HTML tags. In order to submit more complex XML markup to Knora, a custom mapping has to be created first.
Basically, a mapping expresses the relations between XML elements and attributes and their corresponding standoff classes and properties.
The relations expressed in a mapping are one-to-one relations, so the XML can be recreated from the data in RDF. However, since HTML offers a very limited set of elements, Knora mappings support the combination of element names
and classes. In this way, the same element can be used several times in combination with another classname (please note that ``<a>`` without a class is a mere hyperlink whereas ``<a class="salsah-link">`` is an internal link/standoff link).

With a mapping, a default XSL transformation may be provided to transform the XML to HTML before sending it back to the client. This is useful when the client is a web-browser expecting HTML (instead of XML).

----------------------------
Basic Structure of a Mapping
----------------------------

The mapping is written in XML itself (for a formal description, see ``webapi/src/resources/mappingXMLToStandoff.xsd``). It has the following structure (the indentation corresponds to the nesting in XML):

- ``<mapping>``: the root element
    - ``<defaultXSLTransformation> (optional)``: the Iri of the default XSL transformation to be applied to the XML when reading it back from Knora. The XSL transformation is expected to produce HTML. If given, the Iri has to refer to a resource of type ``knora-base:XSLTransformation``.
    - ``<mappingElement>``: an element of the mapping (at least one)
       - ``<tag>``: information about the XML element that is mapped to a standoff class
           - ``<name>``: name of the XML element
           - ``<class>``: value of the class attribute of the XML element, if any. If the element has no class attribute, the keyword ``noClass`` has to be used.
           - ``<namespace>``: the namespace the XML element belongs to, if any. If the element does not belong to a namespace, the keyword ``noNamespace`` has to be used.
           - ``<separatesWords>``: a Boolean value indicating whether this tag separates words in the text. Once an XML document is converted to RDF-standoff the markup is stripped from the text, possibly leading to continuous text that has been separated by tags before. For structural tags like paragraphs etc., ``<separatesWords>`` can be set to ``true`` in which case a special separator is inserted in the the text in the RDF representation. In this way, words stay separated and are represented in the fulltext index as such.
       - ``<standoffClass>``: information about the standoff class the XML element is mapped to
           - ``<classIri>``: Iri of the standoff class the XML element is mapped to
           - ``<attributes>``: XML attributes to be mapped to standoff properties (other than ``id`` or ``class``), if any
               - ``<attribute>``: an XML attribute to be mapped to a standoff property, may be repeated
                   - ``<attributeName>``: the name of the XML attribute
                   - ``<namespace>``: the namespace the attribute belongs to, if any. If the attribute does not belong to a namespace, the keyword ``noNamespace`` has to be used.
                   - ``<propertyIri>``: the Iri of the standoff property the XML attribute is mapped to.
           - ``<datatype>``: the data type of the standoff class, if any.
               - ``<type>``: the Iri of the data type standoff class
               - ``<attributeName>``: the name of the attribute holding the typed value in the expected Knora standard format

XML structure of a mapping::

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

Please note that the absence of an XML namespace and/or a class have to be explicitly stated using the keywords ``noNamespace`` and ``noClass`` [2]_.

-------------------------------
``id`` and ``class`` Attributes
-------------------------------

The ``id`` and ``class`` attributes are supported by default and do not have to be included in the mapping like other attributes.
The ``id`` attribute identifies an element and must be unique in the document. ``id`` is an optional attribute.
The ``class`` attribute allows for the reuse of an element in the mapping, i.e. the same element can be combined with different class names and mapped to different standoff classes (mapping element ``<class>`` in ``<tag>``).

------------------------
Respecting Cardinalities
------------------------

A mapping from XML elements and attributes to standoff classes and standoff properties must respect the cardinalities defined in the ontology for those very standoff classes.
If an XML element is mapped to a certain standoff class and this class requires a standoff property, an attribute must be defined for the XML element mapping to that very standoff property.
Equally, all mappings for attributes of an XML element must have corresponding cardinalities for standoff properties defined for the standoff class the XML element maps to.

However, since an XML attribute may occur once at maximum, it makes sense to make the corresponding standoff property required (``owl:cardinality`` of one) in the ontology or optional (``owl:maxCardinality`` of one),
but not allowing it more than once.


-------------------
Standoff Data Types
-------------------

Knora allows the use of all its value types as standoff data types (defined in ``knora-base.ttl``):

- ``knora-base::StandoffLinkTag``: Represents a reference to a Knora resource (the IRI of the target resource must be submitted in the data type attribute).
- ``knora-base:StandoffInternalReferenceTag``: Represents an internal reference inside a document (the id of the target element inside the same document must be indicated in the data type attribute), see :ref:`internal_references`.
- ``knora-base::StandoffUriTag``: Represents a reference to a URI (the URI of the target resource must be submitted in the data type attribute).
- ``knora-base::StandoffDateTag``: Represents a date (a Knora date string must be submitted in the data type attribute, e.g. ``GREGORIAN:2017-01-27``).
- ``knora-base::StandoffColorTag``: Represents a color (a hexadecimal RGB color string must be submitted in the data type attribute, e.g. ``#0000FF``).
- ``knora-base::StandoffIntegerTag``: Represents an integer (the integer must be submitted in the data type attribute).
- ``knora-base::StandoffDecimalTag``: Represents a number with fractions (the decimal number must be submitted in the data type attribute, e.g. ``1.1``).
- ``knora-base::StandoffIntervalTag``: Represents an interval (two decimal numbers separated with a comma must be submitted in the data type attribute, e.g. ``1.1,2.2``).
- ``knora-base::StandoffBooleanTag``: Represents a Boolean value (``true`` or ``false`` must be submitted in the data type attribute).

The basic idea is that parts of a text can be marked up in a way that allows using Knora's built-in data types. In order to do so, the typed values have to be provided in a standardized way in an attribute that has to be defined in the mapping.

Data type standoff classes are standoff classes with predefined properties (e.g., a ``knora-base:StandoffLinkTag`` has a ``knora-base:standoffTagHasLink`` and a ``knora-base:StandoffIntegerTag`` has a ``knora-base:valueHasInteger``).
Please note the data type standoff classes can not be combined, i.e. a standoff class can only be the subclass of **one** data type standoff class.
However, standoff data type classes can be subclassed and extended further by assigning properties to them (see below).

The following simple mapping illustrates this principle::

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
                <classIri>http://www.knora.org/ontology/anything#StandoffEventTag</classIri>
                <attributes>
                    <attribute>
                        <attributeName>description</attributeName>
                        <namespace>noNamespace</namespace>
                        <propertyIri>http://www.knora.org/ontology/anything#standoffEventTagHasDescription</propertyIri>
                    </attribute>
                </attributes>
                <datatype>
                    <type>http://www.knora.org/ontology/knora-base#StandoffDateTag</type>
                    <attributeName>knoraDate</attributeName>
                </datatype>
            </standoffClass>
        </mappingElement>
    </mapping>

``<datatype>`` **must** hold the Iri of a standoff data type class (see list above). The ``<classIri>`` must be a subclass of this type or this type itself (the latter is probably not recommendable since semantics are missing: what is the meaning of the date?).
In the example above, the standoff class is ``anything:StandoffEventTag`` which has the following definition in the ontology ``anything-onto.ttl``::


    anything:StandoffEventTag rdf:type owl:Class ;

        rdfs:subClassOf knora-base:StandoffDateTag,
                       [
                          rdf:type owl:Restriction ;
                          owl:onProperty :standoffEventTagHasDescription ;
                          owl:cardinality "1"^^xsd:nonNegativeInteger
                       ] ;

        rdfs:label "Represents an event in a TextValue"@en ;

        rdfs:comment """Represents an event in a TextValue"""@en .


``anything:StandoffEventTag`` is a subclass of ``knora-base:StandoffDateTag`` and therefore has the data type date.
It also requires the standoff property ``anything:standoffEventTagHasDescription`` which is defined as an attribute in the mapping.

Once the mapping has been created, an XML like the following could be sent to Knora and converted to standoff::

    <?xml version="1.0" encoding="UTF-8"?>
    <text>
        We had a party on <mydate description="new year" knoraDate="GREGORIAN:2016-12-31">New Year's Eve</mydate>. It was a lot of fun.
    </text>


The attribute holds the date in the format of a Knora date string (the format is also documented in the typescript type alias ``dateString`` in module ``basicMessageComponents``. There you will also find documentation about the other types like color etc.).
Knora date strings have this format: ``GREGORIAN|JULIAN):YYYY[-MM[-DD]][:YYYY[-MM[-DD]]]``. This allows for different formats as well as for imprecision and periods.
Intervals are submitted as one attribute in the following format: ``interval-attribute="1.0,2.0"`` (two decimal numbers separated with a comma).

You will find a sample mapping with all the data types and a sample XML file in the the test data: ``webapi/_test_data/test_route/texts/mappingForHTML.xml`` and ``webapi/_test_data/test_route/texts/HTML.xml``.

.. _internal_references:

Internal References in an XML Document
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

Internal references inside an XML document can be represented using the data type standoff class ``knora-base:StandoffInternalReferenceTag`` or a subclass of it.
This class has a standoff property that points to a standoff node representing the target XML element when converted to RDF.

The following example shows the definition of a mapping element for an internal reference (for reasons of simplicity, only the mapping element for the element is question is depicted)::

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

Now, an internal reference to an element in the same document can be made that will be converted to a pointer in RDF::

    <?xml version="1.0" encoding="UTF-8"?>
    <text>
        This is an <sample id="1">element</sample> and here is a reference to <ref internalRef="#1">it</ref>.
    </text>

An internal reference in XML has to start with a ``#`` followed by the value of the ``id`` attribute of the element referred to.

------------------------------------------
Predefined Standoff Classes and Properties
------------------------------------------

The standoff ontology ``standoff-onto.ttl`` offers a set of predefined standoff classes that can be used in a custom mapping like the following::

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

Predefined standoff classes may be used by various projects, each providing a custom mapping to be able to recreate the original XML from RDF.
Predefined standoff classes may also be inherited and extended in project specific ontologies.

The mapping above allows for an XML like this::

        <?xml version="1.0" encoding="UTF-8"?>
        <myDoc documentType="letter">
            <p>
                This my text that is <i>very</i> interesting.
            </p>
            <p>
                And here it goes on.
            </p>
        </myDoc>

-------------------------
Respecting Property Types
-------------------------

When mapping XML attributes to standoff properties, attention has to be paid to the properties' object constraints.

In the ontology, standoff property literals may have one of the following ``knora-base:objectDatatypeConstraint``:

- ``xsd:string``
- ``xsd:integer``
- ``xsd:boolean``
- ``xsd:decimal``
- ``xsd:anyURI``

In XML, all attribute values are submitted as strings. However, these string representations need to be convertible to the types defined in the ontology.
If they are not, the request will be rejected. It is recommended to enforce types on attributes by applying XML Schema validations (restrictions).

Links (object property) to a ``knora-base:Resource`` can be represented using the data type standoff class ``knora-base::StandoffLinkTag``, internal links using the data type standoff class ``knora-base:StandoffInternalReferenceTag``.

--------------------------------------------
Validating a Mapping and sending it to Knora
--------------------------------------------

A mapping can be validated before sending it to Knora with the following XML Schema file: ``webapi/src/resources/mappingXMLToStandoff.xsd``.
Any mapping that does not conform to this XML Schema file will be rejected by Knora.

The mapping has to be sent as a multipart request to the standoff route using the path segment ``mapping``::

    HTTP POST http://host/v1/mapping

The multipart request consists of two named parts:

- "json" ->::

    {
      "project_id": "projectIRI",
      "label": "my mapping",
      "mappingName": "MappingNameSegment"
    }
- "xml" ->::

    <?xml version="1.0" encoding="UTF-8"?>
    <mapping>
        ...
    </mapping>


A successful response returns the Iri of the mapping. However, the Iri of a mapping is predictable: it consists of the project Iri followed by ``/mappings/`` and the ``mappingName`` submitted in the JSON
(if the name already exists, the request will be rejected).
Once created, a mapping can be used to create TextValues in Knora. The formats are documented in the typescript interfaces ``addMappingRequest`` and ``addMappingResponse`` in module ``mappingFormats``





.. [1] CKeditor offers the possibility to define filter rules (CKEditor_). They should reflect the elements supported by the mapping (see ``jquery.htmleditor.js``).

.. [2] This is because we use XML Schema validation to ensure the one-to-one relations between XML elements and standoff classes. XML Schema validations unique checks do not support optional values.

.. _CKEditor: http://docs.ckeditor.com/#!/guide/dev_acf-section-automatic-mode-but-disallow-certain-tags%2Fproperties
