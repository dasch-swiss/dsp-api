.. Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
   Tobias Schweizer, André Kilchenmann, and André Fatton.

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

A mapping allows for the conversion of XML to standoff representation in RDF and back. In order to create a TextValue with markup, both XML and the IRI of the mapping used to do the conversion to standoff have to be provided.
However, a mapping is only needed if a TextValue with markup should be created. If a text as no markup, it is submitted as a mere sequence of characters.

The two cases are described in the TypeScript interfaces ``simpletext`` and ``richtext`` in module ``basicMessageComponents``.

Knora offers a standard mapping with the IRI ``http://data.knora.org/projects/standoff/mappings/StandardMapping``. The standard mapping covers the HTML elements and attributes supported by the GUI's text editor CKEditor [1]_
(please note that the HTML as to be encoded in strict XML syntax). The standard mapping contains the following elements and attributes that are mapped to standoff classes and properties defined in the ontology:

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
 - ``<br>`` -> ``standoff:StandoffBrTag``

The HTML produced by CKEditor is wrapped in an XML doctype and a pair of root tags ``<text>...</text>`` and then sent to Knora. The XML sent to the GUI by Knora is unwrapped accordingly (see ``jquery.htmleditor.js``).
Although the GUI supports HTML5, it is treated as if it was XHTML in strict XML notation.

*************************
Creating a custom Mapping
*************************

The Knora standard mapping only supports a few HTML tags. In order to submit more complex XML markup to Knora, a custom mapping has to be created first.
Basically, a mapping expresses the relations between XML elements and attributes and their corresponding standoff classes and properties.
The relations expressed in a mapping are one-to-one relations, so the XML can be recreated from the data in RDF. However, since HTML offers a very limited set of elements, Knora mappings support the combination of element names
and classes. In this way, the same element can be used several times in combination with another classname (please note that ``<a>`` without a class is a mere hyperlink whereas ``<a class="salsah-link">`` is an internal link/standoff link).

------------------
Standoff Datatypes
------------------

Knora allows the use of all its ValueTypes as standoff datatypes (defined in ``knora-base.ttl``):

- ``knora-base::StandoffLinkTag``: Represents a reference to a Knora resource
- ``knora-base::StandoffUriTag``: Represents a reference to a URI.
- ``knora-base::StandoffDateTag``: Represents a date.
- ``knora-base::StandoffColorTag``: Represents a color.
- ``knora-base::StandoffIntegerTag``: Represents an integer.
- ``knora-base::StandoffDecimalTag``: Represents a number with fractions.
- ``knora-base::StandoffIntervalTag``: Represents an interval.
- ``knora-base::StandoffBooleanTag``: Represents a Boolean value.

The basic idea is that parts of a text can be marked up in a way that allows using Knora's built-in data types. In order to do so, the typed values have to be provided in a standardized way.

The following simple mapping illustrates this principle::

    <mapping>
         <mappingElement>
            <tag>
                <name>text</name>
                <class>noClass</class>
                <namespace>noNamespace</namespace>
                <separator>false</separator>
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
                <separator>false</separator>
            </tag>
            <standoffClass>
                <classIri>http://www.knora.org/ontology/knora-base#StandoffDateTag</classIri>
                <datatype>
                    <type>http://www.knora.org/ontology/knora-base#StandoffDateTag</type>
                    <attributeName>knoraDate</attributeName>
                </datatype>
            </standoffClass>
        </mappingElement>
    <mapping>

``<tag>`` indicates the name of the XML element that is mapped to a standoff class. Here, it does neither have a namespace nor a class.
Please note that the absence of an XML namespace and a class have to be explicitly stated using the keywords ``noNamespace`` and ``noClass`` [2]_.

``<standoffClass>`` indicates the IRI of the standoff class the XML element is mapped to.
``<type>`` in ```<datatype>`` indicates the data type of the standoff class the XML element is mapped to (if given).
``<attributeName>`` in ``<datatype>`` is the XML attribute holding the typed value in a standardized way.

Once the mapping has been created, an XML like the following could be sent to Knora and converted to standoff::

    <text>
        We had a party on <mydate knoraDate="GREGORIAN:2016-12-31">New Year's Eve</mydate>. It was a lot of fun.
    </text>



------------------------------------------
Predefined Standoff Classes and Properties
------------------------------------------

.. [1] CKeditor offers the possibility to define filter rules (CKEditor_). They should reflect the elements supported by the mapping (see ``jquery.htmleditor.js``).

.. [2] This is because we use XML Schema validation to ensure the one-to-one relations between XML elements and standoff classes. XML Schema validations unique checks do not support optional values.

.. _CKEditor: http://docs.ckeditor.com/#!/guide/dev_acf-section-automatic-mode-but-disallow-certain-tags%2Fproperties