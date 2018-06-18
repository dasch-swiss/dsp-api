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
