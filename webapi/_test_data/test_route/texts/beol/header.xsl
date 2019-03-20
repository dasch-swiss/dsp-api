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
    <xsl:function name="knora-api:iaf" as="xs:string">
        <xsl:param name="input" as="xs:string"/>
        <xsl:sequence select="replace($input, '\(DE-588\)', 'http://d-nb.info/gnd/')"/>
    </xsl:function>

    <!-- Given a link value IRI and the document root node, returns the IRI of the target resource. -->
    <xsl:function name="knora-api:getTargetResourceIri" as="xs:anyURI">
        <xsl:param name="linkValueIri" as="xs:anyURI"/>
        <xsl:param name="documentRoot" as="item()"/>

        <xsl:choose>
            <xsl:when test="boolean($documentRoot//knora-api:LinkValue[@rdf:about=$linkValueIri]//beol:person)">
                <!-- The target resource is nested in the LinkValue. -->
                <xsl:value-of
                        select="$documentRoot//knora-api:LinkValue[@rdf:about=$linkValueIri]//beol:person/@rdf:about"/>
            </xsl:when>
            <xsl:otherwise>
                <!-- The target resource is not nested in the LinkValue. -->
                <xsl:value-of
                        select="$documentRoot//knora-api:LinkValue[@rdf:about=$linkValueIri]//knora-api:linkValueHasTarget/@rdf:resource"/>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:function>

    <!-- https://www.safaribooksonline.com/library/view/xslt-cookbook/0596003722/ch03s03.html?orpq -->
    <xsl:function name="knora-api:last-day-of-month" as="xs:string">
        <xsl:param name="month"/>
        <xsl:param name="year"/>
        <xsl:choose>
            <xsl:when test="$month = 2 and
            not($year mod 4) and
            ($year mod 100 or not($year mod 400))">
                <xsl:value-of select="29"/>
            </xsl:when>
            <xsl:otherwise>
                <xsl:value-of
                        select="substring('312831303130313130313031',
         2 * $month - 1,2)"/>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:function>

    <!-- make a standard date (Gregorian calendar assumed) -->
    <xsl:function name="knora-api:dateformat" as="element()*">
        <xsl:param name="input" as="element()*"/>

        <xsl:choose>
            <xsl:when
                    test="$input/knora-api:dateValueHasStartYear/text() = $input/knora-api:dateValueHasEndYear/text() and $input/knora-api:dateValueHasStartMonth/text() = $input/knora-api:dateValueHasEndMonth/text() and $input/knora-api:dateValueHasStartDay/text() = $input/knora-api:dateValueHasEndDay/text()">
                <!-- no period, day precision -->
                <date>
                    <xsl:attribute name="when">
                        <xsl:value-of
                                select="format-number($input/knora-api:dateValueHasStartYear/text(), '0000')"/>-<xsl:value-of
                            select="format-number($input/knora-api:dateValueHasStartMonth/text(), '00')"/>-<xsl:value-of
                            select="format-number($input/knora-api:dateValueHasStartDay/text(), '00')"/>
                    </xsl:attribute>
                </date>

            </xsl:when>
            <xsl:otherwise>
                <!-- period -->
                <date>
                    <!-- start date could be imprecise -->
                    <xsl:variable name="startDay">
                        <xsl:choose>
                            <xsl:when test="$input/knora-api:dateValueHasStartDay">
                                <xsl:value-of select="$input/knora-api:dateValueHasStartDay/text()"/>
                            </xsl:when>
                            <xsl:otherwise>01</xsl:otherwise>
                        </xsl:choose>
                    </xsl:variable>

                    <xsl:variable name="startMonth">
                        <xsl:choose>
                            <xsl:when test="$input/knora-api:dateValueHasStartMonth">
                                <xsl:value-of select="$input/knora-api:dateValueHasStartMonth/text()"/>
                            </xsl:when>
                            <xsl:otherwise>01</xsl:otherwise>
                        </xsl:choose>
                    </xsl:variable>

                    <xsl:attribute name="notBefore">
                        <xsl:value-of
                                select="format-number($input/knora-api:dateValueHasStartYear/text(), '0000')"/>-<xsl:value-of
                            select="format-number($startMonth, '00')"/>-<xsl:value-of
                            select="format-number($startDay, '00')"/>
                    </xsl:attribute>

                    <!-- end date could be imprecise -->

                    <xsl:variable name="endMonth">
                        <xsl:choose>
                            <xsl:when test="$input/knora-api:dateValueHasEndMonth">
                                <xsl:value-of select="$input/knora-api:dateValueHasEndMonth/text()"/>
                            </xsl:when>
                            <xsl:otherwise>12</xsl:otherwise>
                        </xsl:choose>
                    </xsl:variable>

                    <xsl:variable name="endDay">
                        <xsl:choose>
                            <xsl:when test="$input/knora-api:dateValueHasEndDay">
                                <xsl:value-of select="$input/knora-api:dateValueHasEndDay/text()"/>
                            </xsl:when>
                            <xsl:otherwise>
                                <xsl:value-of
                                        select="knora-api:last-day-of-month(number($endMonth), number($input/knora-api:dateValueHasEndYear/text()))"/>
                            </xsl:otherwise>
                        </xsl:choose>
                    </xsl:variable>


                    <xsl:attribute name="notAfter">
                        <xsl:value-of
                                select="format-number($input/knora-api:dateValueHasEndYear/text(), '0000')"/>-<xsl:value-of
                            select="format-number($endMonth, '00')"/>-<xsl:value-of
                            select="format-number($endDay, '00')"/>
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
                    <p>This is the TEI/XML representation of the resource identified by the Iri
                        <xsl:value-of select="$resourceIri"/>.
                    </p>
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
        <xsl:variable name="authorValueIri" select="@rdf:resource"/>
        <xsl:variable name="authorIri" select="knora-api:getTargetResourceIri($authorValueIri, /.)"/>

        <xsl:variable name="authorIAFValue"
                      select="//beol:person[@rdf:about=$authorIri]//beol:hasIAFIdentifier/@rdf:resource"/>
        <xsl:variable name="authorFamilyNameValue"
                      select="//beol:person[@rdf:about=$authorIri]//beol:hasFamilyName/@rdf:resource"/>
        <xsl:variable name="authorGivenNameValue"
                      select="//beol:person[@rdf:about=$authorIri]//beol:hasGivenName/@rdf:resource"/>

        <correspAction type="sent">

            <xsl:variable name="authorIAFText"
                          select="//knora-api:TextValue[@rdf:about=$authorIAFValue]/knora-api:valueAsString/text()"/>
            <xsl:variable name="authorFamilyNameText"
                          select="//knora-api:TextValue[@rdf:about=$authorFamilyNameValue]/knora-api:valueAsString/text()"/>
            <xsl:variable name="authorGivenNameText"
                          select="//knora-api:TextValue[@rdf:about=$authorGivenNameValue]/knora-api:valueAsString/text()"/>

            <persName>
                <xsl:attribute name="ref">
                    <xsl:value-of select="knora-api:iaf($authorIAFText)"
                    />
                </xsl:attribute>
                <xsl:value-of select="$authorFamilyNameText"/>,
                <xsl:value-of
                        select="$authorGivenNameText"/>
            </persName>

            <xsl:variable name="dateValue" select="//beol:creationDate/@rdf:resource"/>

            <xsl:variable name="dateObj"
                          select="//knora-api:DateValue[@rdf:about=$dateValue]"/>

            <xsl:copy-of select="knora-api:dateformat($dateObj)"/>

        </correspAction>
    </xsl:template>

    <xsl:template match="beol:letter/beol:hasRecipientValue">
        <xsl:variable name="recipientValueIri" select="@rdf:resource"/>
        <xsl:variable name="recipientIri" select="knora-api:getTargetResourceIri($recipientValueIri, /.)"/>

        <xsl:variable name="recipientIAFValue"
                      select="//beol:person[@rdf:about=$recipientIri]//beol:hasIAFIdentifier/@rdf:resource"/>
        <xsl:variable name="recipientFamilyNameValue"
                      select="//beol:person[@rdf:about=$recipientIri]//beol:hasFamilyName/@rdf:resource"/>
        <xsl:variable name="recipientGivenNameValue"
                      select="//beol:person[@rdf:about=$recipientIri]//beol:hasGivenName/@rdf:resource"/>

        <correspAction type="received">

            <xsl:variable name="recipientIAFText"
                          select="//knora-api:TextValue[@rdf:about=$recipientIAFValue]/knora-api:valueAsString/text()"/>
            <xsl:variable name="recipientFamilyNameText"
                          select="//knora-api:TextValue[@rdf:about=$recipientFamilyNameValue]/knora-api:valueAsString/text()"/>
            <xsl:variable name="recipientGivenNameText"
                          select="//knora-api:TextValue[@rdf:about=$recipientGivenNameValue]/knora-api:valueAsString/text()"/>

            <persName>
                <xsl:attribute name="ref">
                    <xsl:value-of select="knora-api:iaf($recipientIAFText)"
                    />
                </xsl:attribute>
                <xsl:value-of select="$recipientFamilyNameText"/>,
                <xsl:value-of
                        select="$recipientGivenNameText"/>
            </persName>

        </correspAction>
    </xsl:template>

    <!-- ignore text if there is no template for the element containing it -->
    <xsl:template match="text()"></xsl:template>


</xsl:transform>
