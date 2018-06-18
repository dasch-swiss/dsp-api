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

# TEI/XML: Converting Standoff to TEI/XML

@@toc

## General

Knora offers a way to convert standoff markup to TEI/XML. The conversion is based on the assumption that a whole resource is to be turned into a TEI document.
There is a basic distinction between the body and the header of a TEI document. The resource's property that contains the text with standoff markup is mapped to the TEI document's body.
Other of the resource's property may be mapped to the TEI header.

## Standard Standoff to TEI Conversion

Knora offers a built-in conversion form standard standoff entities (defined in the `standoff` ontology) tags to TEI.

In order to obtain a resource as a TEI document, the following request has to be performed. 
Please note that the URL parameters have to be URL-encoded.

```
HTTP GET to http://host/v2/tei/resourceIri?textProperty=textPropertyIri
```

In addition to the resource's Iri, the Iri of the property containing the text with standoff has to be submitted. This will be converted to the TEI body. 
Please note that the resource can only have one instance of this property and the text must have standoff markup.

The Knora test data contain the resource `http://rdfh.ch/0001/thing_with_richtext_with_markup` with the text property `http://0.0.0.0:3333/ontology/0001/anything/v2#hasRichtext` that can be converted to TEI as follows:

```
HTTP GET to http://host/v2/tei/http%3A%2F%2Frdfh.ch%2F0001%2Fthing_with_richtext_with_markup?textProperty=http%3A%2F%2F0.0.0.0%3A3333%2Fontology%2F0001%2Fanything%2Fv2%23hasRichtext
```

The answer to this request is a TEI XML document:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<TEI xmlns="http://www.tei-c.org/ns/1.0" version="3.3.0">
  <teiHeader>
    <fileDesc>
      <titleStmt>
        <title>test thing with markup</title>
      </titleStmt>
      <publicationStmt>
        <p>
             This is the TEI/XML representation of a resource identified by the Iri http://rdfh.ch/0001/thing_with_richtext_with_markup.
         </p>
      </publicationStmt>
      <sourceDesc>
        <p>Representation of the resource's text as TEI/XML</p>
      </sourceDesc>
    </fileDesc>
  </teiHeader>
  <text>
    <body>
      <p>This is a test that contains marked up elements. This is <hi rend="italic">interesting text</hi> in italics. This is <hi rend="italic">boring text</hi> in italics.</p>
    </body>
  </text>
</TEI>        
```

The body of the TEI document contains the standoff markup as XML. The header contains contains some basic metadata about the resource such as the `rdfs:label` an its IRI. However, this might not be sufficient for more advanced use cases like digital edition projects. 
In that case, a custom conversion has to be performed (see below).

## Custom Conversion

If a project defines its own standoff entities, a custom conversion can be provided (body of the TEI document). Also for the TEI header, a custom conversion can be provided.

For the custom conversion, additional configuration is required.

TEI body:
    
- additional mapping from standoff to XML (URL parameter `mappingIri`)
- XSL transformation to turn the XML into a valid TEI body (referred to by the mapping).

The mapping has to refer to a `defaultXSLTransformation` that transforms the XML that was created from standoff markup (see @ref:[XML To Standoff Mapping in API v1](../api-v1/xml-to-standoff-mapping.md)). This step is necessary because the mapping assumes a one to one relation between standoff classes and properties and XML elements and attributes.
For example, we may want to convert a `standoff:StandoffItalicTag` into TEI/XML. TEI expresses this as `<hi rend="italic">...</hi>`. In the mapping, the `standoff:StandoffItalicTag` may be mapped to a a temporary XML element that is going to be converted to `<hi rend="italic">...</hi>` in a further step by the XSLT. 

For sample data, see `webapi/_test_data/test_route/texts/beol/BEOLTEIMapping.xml` (mapping) and `webapi/_test_data/test_route/texts/beol/standoffToTEI.xsl`. The standoff entities are defined in `beol-onto.ttl`.

TEI header:
    
- Gravsearch template to query the resources metadata, results are serialized to RDF/XML (URL parameter `gravsearchTemplateIri`)
- XSL transformation to turn that RDF/XML into a valid TEI header (URL parameter `teiHeaderXSLTIri`)

The Gravsearch template is expected to be of type `knora-base:TextRepresentation` and to contain a placeholder `$resourceIri` that is to be replaced by the actual resource Iri.
The Gravsearch template is expected to contain a query involving the text property (URL parameter `textProperty`) and more properties that are going to be mapped to the TEI header. The Gravsearch template is a simple text file with the files extension `.txt`.

A Gravsearch template may look like this (see `webapi/_test_data/test_route/texts/beol/gravsearch.txt`):

```
PREFIX beol: <http://0.0.0.0:3333/ontology/0801/beol/simple/v2#>
PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>

    CONSTRUCT {
        ?letter knora-api:isMainResource true .

        ?letter beol:creationDate ?date .

        ?letter beol:hasText ?text .

        ?letter beol:hasAuthor ?person1 .

        ?person1 beol:hasFamilyName ?name1 .

        ?person1 beol:hasGivenName ?givenName1 .

        ?person1 beol:hasIAFIdentifier ?iaf1 .

        ?letter beol:hasRecipient ?person2 .

        ?person2 beol:hasFamilyName ?name2 .

        ?person2 beol:hasGivenName ?givenName2 .

        ?person2 beol:hasIAFIdentifier ?iaf2 .


    } WHERE {
        BIND(<$resourceIri> as ?letter)
        ?letter a knora-api:Resource .
        ?letter a beol:letter .

        ?letter beol:creationDate ?date .

        beol:creationDate knora-api:objectType knora-api:Date .
        ?date a knora-api:Date .

        ?letter beol:hasText ?text .

        beol:hasText knora-api:objectType xsd:string .

        ?text a xsd:string .

        ?letter beol:hasAuthor ?person1 .

        ?person1 beol:hasFamilyName ?name1 .

        ?person1 beol:hasGivenName ?givenName1 .

        ?person1 beol:hasIAFIdentifier ?iaf1 .

        ?name1 a xsd:string .

        ?givenName1 a xsd:string .

        ?iaf1 a xsd:string .

        ?person2 beol:hasFamilyName ?name2 .

        ?person2 beol:hasGivenName ?givenName2 .

        ?person2 beol:hasIAFIdentifier ?iaf2 .

        ?name2 a xsd:string .

        ?givenName2 a xsd:string .

        ?iaf2 a xsd:string .

        beol:hasGivenName knora-api:objectType xsd:string .
        beol:hasFamilyName knora-api:objectType xsd:string .
        beol:hasIAFIdentifier knora-api:objectType xsd:string .

      	beol:hasAuthor knora-api:objectType knora-api:Resource .

        ?letter beol:hasRecipient ?person2 .

      	beol:hasRecipient knora-api:objectType knora-api:Resource .

        ?person1 a knora-api:Resource .
        ?person2 a knora-api:Resource .

    }
```

Note the placeholder `BIND(<$resourceIri> as ?letter)` that is going to be replaced by the Iri of the resource the request is performed for.
The query asks for information about the letter's text `beol:hasText` and information about its author and recipient. This information is converted to the TEI header in the format required by [correspSearch](https://correspsearch.net).

To write the XSLT, do the Gravsearch query and request the data as RDF/XML using content negotiation (see @ref:[Introduction](introduction.md)).

The Gravsearch query's result may look like this (`RDF/XML`):

```xml
<?xml version="1.0" encoding="UTF-8"?>
<rdf:RDF
	xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"
	xmlns:rdfs="http://www.w3.org/2000/01/rdf-schema#"
	xmlns:knora-api="http://api.knora.org/ontology/knora-api/v2#"
	xmlns:beol="http://0.0.0.0:3333/ontology/0801/beol/v2#">
<beol:letter rdf:about="http://rdfh.ch/0801/MbZdHVcsR_Ky5pZoytaiBA">
	<beol:creationDate rdf:resource="http://rdfh.ch/0801/MbZdHVcsR_Ky5pZoytaiBA/values/Ob_1YRO_QmaDxTRI64vGOQ"/>
	<beol:hasAuthorValue rdf:resource="http://rdfh.ch/0801/MbZdHVcsR_Ky5pZoytaiBA/values/zt4a3XoESTq9To4mSN8Dug"/>
	<beol:hasRecipientValue rdf:resource="http://rdfh.ch/0801/MbZdHVcsR_Ky5pZoytaiBA/values/pVerHO_FRXePZQT9kgEp_Q"/>
	<rdfs:label rdf:datatype="http://www.w3.org/2001/XMLSchema#string">Testletter</rdfs:label>
</beol:letter>
<knora-api:DateValue rdf:about="http://rdfh.ch/0801/MbZdHVcsR_Ky5pZoytaiBA/values/Ob_1YRO_QmaDxTRI64vGOQ">
	<knora-api:dateValueHasCalendar rdf:datatype="http://www.w3.org/2001/XMLSchema#string">GREGORIAN</knora-api:dateValueHasCalendar>
	<knora-api:dateValueHasEndDay rdf:datatype="http://www.w3.org/2001/XMLSchema#integer">10</knora-api:dateValueHasEndDay>
	<knora-api:dateValueHasEndEra rdf:datatype="http://www.w3.org/2001/XMLSchema#string">CE</knora-api:dateValueHasEndEra>
	<knora-api:dateValueHasEndMonth rdf:datatype="http://www.w3.org/2001/XMLSchema#integer">6</knora-api:dateValueHasEndMonth>
	<knora-api:dateValueHasEndYear rdf:datatype="http://www.w3.org/2001/XMLSchema#integer">1703</knora-api:dateValueHasEndYear>
	<knora-api:dateValueHasStartDay rdf:datatype="http://www.w3.org/2001/XMLSchema#integer">10</knora-api:dateValueHasStartDay>
	<knora-api:dateValueHasStartEra rdf:datatype="http://www.w3.org/2001/XMLSchema#string">CE</knora-api:dateValueHasStartEra>
	<knora-api:dateValueHasStartMonth rdf:datatype="http://www.w3.org/2001/XMLSchema#integer">6</knora-api:dateValueHasStartMonth>
	<knora-api:dateValueHasStartYear rdf:datatype="http://www.w3.org/2001/XMLSchema#integer">1703</knora-api:dateValueHasStartYear>
	<knora-api:valueAsString rdf:datatype="http://www.w3.org/2001/XMLSchema#string">GREGORIAN:1703-06-10 CE</knora-api:valueAsString>
</knora-api:DateValue>
<knora-api:LinkValue rdf:about="http://rdfh.ch/0801/MbZdHVcsR_Ky5pZoytaiBA/values/zt4a3XoESTq9To4mSN8Dug">
	<knora-api:linkValueHasTarget>
		<beol:person rdf:about="http://rdfh.ch/0801/_9LEnLM7TFuPRjTshOTJpQ">
			<beol:hasFamilyName rdf:resource="http://rdfh.ch/0801/_9LEnLM7TFuPRjTshOTJpQ/values/NG42jDqSTz2U35N6sJ8cqg"/>
			<beol:hasGivenName rdf:resource="http://rdfh.ch/0801/_9LEnLM7TFuPRjTshOTJpQ/values/W2lVG1mvQU2MauAvCGB13w"/>
			<beol:hasIAFIdentifier rdf:resource="http://rdfh.ch/0801/_9LEnLM7TFuPRjTshOTJpQ/values/N2TVtntdToqJQpdZhYPc5g"/>
			<rdfs:label rdf:datatype="http://www.w3.org/2001/XMLSchema#string">Johann Jacob Scheuchzer</rdfs:label>
		</beol:person>
	</knora-api:linkValueHasTarget>
</knora-api:LinkValue>
<knora-api:TextValue rdf:about="http://rdfh.ch/0801/_9LEnLM7TFuPRjTshOTJpQ/values/NG42jDqSTz2U35N6sJ8cqg">
	<knora-api:valueAsString rdf:datatype="http://www.w3.org/2001/XMLSchema#string">Scheuchzer</knora-api:valueAsString>
</knora-api:TextValue>
<knora-api:TextValue rdf:about="http://rdfh.ch/0801/_9LEnLM7TFuPRjTshOTJpQ/values/W2lVG1mvQU2MauAvCGB13w">
	<knora-api:valueAsString rdf:datatype="http://www.w3.org/2001/XMLSchema#string">Johann Jacob</knora-api:valueAsString>
</knora-api:TextValue>
<knora-api:TextValue rdf:about="http://rdfh.ch/0801/_9LEnLM7TFuPRjTshOTJpQ/values/N2TVtntdToqJQpdZhYPc5g">
	<knora-api:valueAsString rdf:datatype="http://www.w3.org/2001/XMLSchema#string">(DE-588)118607308</knora-api:valueAsString>
</knora-api:TextValue>
<knora-api:LinkValue rdf:about="http://rdfh.ch/0801/MbZdHVcsR_Ky5pZoytaiBA/values/pVerHO_FRXePZQT9kgEp_Q">
	<knora-api:linkValueHasTarget>
		<beol:person rdf:about="http://rdfh.ch/0801/JaQwPsYEQJ6GQrAgKC0Gkw">
			<beol:hasFamilyName rdf:resource="http://rdfh.ch/0801/JaQwPsYEQJ6GQrAgKC0Gkw/values/k1Exqf93SsWi7LWK9ozXkw"/>
			<beol:hasGivenName rdf:resource="http://rdfh.ch/0801/JaQwPsYEQJ6GQrAgKC0Gkw/values/gkqK5Ij_R7mtO59xfSDGJA"/>
			<beol:hasIAFIdentifier rdf:resource="http://rdfh.ch/0801/JaQwPsYEQJ6GQrAgKC0Gkw/values/C-Dl15S-SV63L1KCCPFfew"/>
			<rdfs:label rdf:datatype="http://www.w3.org/2001/XMLSchema#string">Jacob Hermann</rdfs:label>
		</beol:person>
	</knora-api:linkValueHasTarget>
</knora-api:LinkValue>
<knora-api:TextValue rdf:about="http://rdfh.ch/0801/JaQwPsYEQJ6GQrAgKC0Gkw/values/k1Exqf93SsWi7LWK9ozXkw">
	<knora-api:valueAsString rdf:datatype="http://www.w3.org/2001/XMLSchema#string">Hermann</knora-api:valueAsString>
</knora-api:TextValue>
<knora-api:TextValue rdf:about="http://rdfh.ch/0801/JaQwPsYEQJ6GQrAgKC0Gkw/values/gkqK5Ij_R7mtO59xfSDGJA">
	<knora-api:valueAsString rdf:datatype="http://www.w3.org/2001/XMLSchema#string">Jacob</knora-api:valueAsString>
</knora-api:TextValue>
<knora-api:TextValue rdf:about="http://rdfh.ch/0801/JaQwPsYEQJ6GQrAgKC0Gkw/values/C-Dl15S-SV63L1KCCPFfew">
	<knora-api:valueAsString rdf:datatype="http://www.w3.org/2001/XMLSchema#string">(DE-588)119112450</knora-api:valueAsString>
</knora-api:TextValue>

</rdf:RDF>
```

In order to convert the metadata (not the actual standoff markup), a `knora-base:knora-base:XSLTransformation` has to be provided. For our example, it looks like this (see `webapi/_test_data/test_route/texts/beol/header.xsl`):

```xml
<?xml version="1.0" encoding="UTF-8"?>
<xsl:transform xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
               xmlns:xs="http://www.w3.org/2001/XMLSchema"
               xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"
               xmlns:rdfs1="http://www.w3.org/2000/01/rdf-schema#"
               xmlns:beol="http://0.0.0.0:3333/ontology/0801/beol/v2#"
               xmlns:knora-api="http://api.knora.org/ontology/knora-api/v2#"
               exclude-result-prefixes="rdf beol knora-api xs rdfs1" version="2.0">

    <xsl:output method="xml" omit-xml-declaration="yes" encoding="utf-8" indent="yes"/>

    <!-- make IAF id a URL -->
    <xsl:function name="knora-api:iaf" as="xs:anyURI">
        <xsl:param name="input" as="xs:string"/>
        <xsl:value-of select="replace($input, '\(DE-588\)', 'http://d-nb.info/gnd/')"/>
    </xsl:function>

    <!-- make a standard date (Gregorian calendar assumed) -->
    <xsl:function name="knora-api:dateformat" as="element()*">
        <xsl:param name="input" as="element()*"/>

        <xsl:choose>
            <xsl:when test="$input/knora-api:dateValueHasStartYear/text() = $input/knora-api:dateValueHasEndYear/text() and $input/knora-api:dateValueHasStartMonth/text() = $input/knora-api:dateValueHasEndMonth/text() and $input/knora-api:dateValueHasStartDay/text() = $input/knora-api:dateValueHasEndDay/text()">
                <!-- no period, day precision -->
                <date>
                    <xsl:attribute name="when">
                        <xsl:value-of select="format-number($input/knora-api:dateValueHasStartYear/text(), '0000')"/>-<xsl:value-of select="format-number($input/knora-api:dateValueHasStartMonth/text(), '00')"/>-<xsl:value-of select="format-number($input/knora-api:dateValueHasStartMonth/text(), '00')"/>
                    </xsl:attribute>
                </date>

            </xsl:when>
            <xsl:otherwise>
                <!-- period -->
                <date>
                    <xsl:attribute name="notBefore">
                        <xsl:value-of select="format-number($input/knora-api:dateValueHasStartYear/text(), '0000')"/>-<xsl:value-of select="format-number($input/knora-api:dateValueHasStartMonth/text(), '00')"/>-<xsl:value-of select="format-number($input/knora-api:dateValueHasStartDay/text(), '00')"/>
                    </xsl:attribute>

                    <xsl:attribute name="notAfter">
                        <xsl:value-of select="format-number($input/knora-api:dateValueHasEndYear/text(), '0000')"/>-<xsl:value-of select="format-number($input/knora-api:dateValueHasEndMonth/text(), '00')"/>-<xsl:value-of select="format-number($input/knora-api:dateValueHasEndDay/text(), '00')"/>
                    </xsl:attribute>
                </date>

            </xsl:otherwise>
        </xsl:choose>


    </xsl:function>

    <xsl:template match="rdf:RDF">
        <xsl:variable name="resourceIri" select="beol:letter/@rdf:about"/>
        <xsl:variable name="label" select="beol:letter/rdfs1:label/text()"/>


        <teiHeader>
            <fileDesc>
                <titleStmt>
                    <title>
                        <xsl:value-of select="$label"/>
                    </title>
                </titleStmt>
                <publicationStmt>
                    <p> This is the TEI/XML representation of the resource identified by the Iri
                        <xsl:value-of select="$resourceIri"/>. </p>
                </publicationStmt>
                <sourceDesc>
                    <p>Representation of the resource's text as TEI/XML</p>
                </sourceDesc>
            </fileDesc>
            <profileDesc>

                <correspDesc>
                    <xsl:attribute name="ref">
                        <xsl:value-of select="$resourceIri"/>
                    </xsl:attribute>
                    <xsl:apply-templates/>
                </correspDesc>
            </profileDesc>
        </teiHeader>
    </xsl:template>

    <xsl:template match="beol:letter/beol:hasAuthorValue">
        <xsl:variable name="authorValue" select="@rdf:resource"/>

        <xsl:variable name="authorIAFValue"
                      select="//knora-api:LinkValue[@rdf:about=$authorValue]//beol:hasIAFIdentifier/@rdf:resource"/>
        <xsl:variable name="authorFamilyNameValue"
                      select="//knora-api:LinkValue[@rdf:about=$authorValue]//beol:hasFamilyName/@rdf:resource"/>
        <xsl:variable name="authorGivenNameValue"
                      select="//knora-api:LinkValue[@rdf:about=$authorValue]//beol:hasGivenName/@rdf:resource"/>

        <correspAction type="sent">

            <xsl:variable name="authorIAFText"
                          select="//knora-api:TextValue[@rdf:about=$authorIAFValue]/knora-api:valueAsString/text()"/>
            <xsl:variable name="authorFamilyNameText"
                          select="//knora-api:TextValue[@rdf:about=$authorFamilyNameValue]/knora-api:valueAsString/text()"/>
            <xsl:variable name="authorGivenNameText"
                          select="//knora-api:TextValue[@rdf:about=$authorGivenNameValue]/knora-api:valueAsString/text()"/>

            <persName>
                <xsl:attribute name="ref"><xsl:value-of select="knora-api:iaf($authorIAFText)"
                /></xsl:attribute>
                <xsl:value-of select="$authorFamilyNameText"/>, <xsl:value-of
                    select="$authorGivenNameText"/>
            </persName>

            <xsl:variable name="dateValue" select="//beol:creationDate/@rdf:resource"/>

            <xsl:variable name="dateObj"
                          select="//knora-api:DateValue[@rdf:about=$dateValue]"/>

            <xsl:copy-of select="knora-api:dateformat($dateObj)"/>

        </correspAction>
    </xsl:template>

    <xsl:template match="beol:letter/beol:hasRecipientValue">
        <xsl:variable name="recipientValue" select="@rdf:resource"/>

        <xsl:variable name="recipientIAFValue"
                      select="//knora-api:LinkValue[@rdf:about=$recipientValue]//beol:hasIAFIdentifier/@rdf:resource"/>
        <xsl:variable name="recipientFamilyNameValue"
                      select="//knora-api:LinkValue[@rdf:about=$recipientValue]//beol:hasFamilyName/@rdf:resource"/>
        <xsl:variable name="recipientGivenNameValue"
                      select="//knora-api:LinkValue[@rdf:about=$recipientValue]//beol:hasGivenName/@rdf:resource"/>

        <correspAction type="received">

            <xsl:variable name="recipientIAFText"
                          select="//knora-api:TextValue[@rdf:about=$recipientIAFValue]/knora-api:valueAsString/text()"/>
            <xsl:variable name="recipientFamilyNameText"
                          select="//knora-api:TextValue[@rdf:about=$recipientFamilyNameValue]/knora-api:valueAsString/text()"/>
            <xsl:variable name="recipientGivenNameText"
                          select="//knora-api:TextValue[@rdf:about=$recipientGivenNameValue]/knora-api:valueAsString/text()"/>

            <persName>
                <xsl:attribute name="ref"><xsl:value-of select="knora-api:iaf($recipientIAFText)"
                /></xsl:attribute>
                <xsl:value-of select="$recipientFamilyNameText"/>, <xsl:value-of
                    select="$recipientGivenNameText"/>
            </persName>

        </correspAction>
    </xsl:template>

    <!-- ignore text if there is no template for the element containing it -->
    <xsl:template match="text()"> </xsl:template>


</xsl:transform>
```

You can use the functions `knora-api:iaf` and `knora-api:dateformat` in your own XSLT in case you want to support `correspSearch`.

The complete request looks like this:

```
HTTP GET request to http://host/v2/tei/resourceIri&textProperty=textPropertyIri&mappingIri=mappingIri&gravsearchTemplateIri=gravsearchTemplateIri&teiHeaderXSLTIri=teiHeaderXSLTIri
```

See `webapi/src/it/scala/org/knora/webapi/e2e/v1/KnoraSipiIntegrationV1ITSpec.scala` for a complete test case involving the sample data ("create a mapping for standoff conversion to TEI referring to an XSLT and also create a Gravsearch template and an XSLT for transforming TEI header data").

When you provide a custom conversion, it is up to you to ensure the validity of the TEI document. You can use this service to validate: [TEI by example validator](http://teibyexample.org/xquery/TBEvalidator.xq).
Problems and bugs caused by XSL transformations are out of scope of the responsibility of the Knora software.