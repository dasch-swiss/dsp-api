<?xml version="1.0" encoding="UTF-8"?>
<xsl:transform xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
               xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"
               xmlns:beol="http://0.0.0.0:3333/ontology/0801/beol/v2#"
               xmlns:knora-api="http://api.knora.org/ontology/knora-api/v2#"
               exclude-result-prefixes="rdf beol knora-api"
               version="2.0">

    <xsl:output method="xml" omit-xml-declaration="yes" encoding="utf-8" indent="yes"/>

    <xsl:template match="rdf:RDF">

        <profileDesc>

            <xsl:variable name="resourceIri" select="beol:letter/@rdf:about"/>

            <xsl:variable name="dateValue" select="beol:letter/beol:creationDate/@rdf:resource"/>

            <xsl:variable name="authorValue" select="beol:letter/beol:hasAuthorValue/@rdf:resource"/>
            <xsl:variable name="authorIAFValue" select="knora-api:LinkValue[@rdf:about=$authorValue]//beol:hasIAFIdentifier/@rdf:resource"/>
            <xsl:variable name="authorFamilyNameValue" select="knora-api:LinkValue[@rdf:about=$authorValue]//beol:hasFamilyName/@rdf:resource"/>
            <xsl:variable name="authorGivenNameValue" select="knora-api:LinkValue[@rdf:about=$authorValue]//beol:hasGivenName/@rdf:resource"/>

            <xsl:variable name="recipientValue" select="beol:letter/beol:hasRecipientValue/@rdf:resource"/>
            <xsl:variable name="recipientIAFValue" select="knora-api:LinkValue[@rdf:about=$recipientValue]//beol:hasIAFIdentifier/@rdf:resource"/>
            <xsl:variable name="recipientFamilyNameValue" select="knora-api:LinkValue[@rdf:about=$recipientValue]//beol:hasFamilyName/@rdf:resource"/>
            <xsl:variable name="recipientGivenNameValue" select="knora-api:LinkValue[@rdf:about=$recipientValue]//beol:hasGivenName/@rdf:resource"/>

            <correspDesc>
                <xsl:attribute name="ref"><xsl:value-of select="$resourceIri"/></xsl:attribute>

                <correspAction type="sent">

                    <xsl:variable name="authorIAFText" select="knora-api:TextValue[@rdf:about=$authorIAFValue]/knora-api:valueAsString/text()"/>
                    <xsl:variable name="authorFamilyNameText" select="knora-api:TextValue[@rdf:about=$authorFamilyNameValue]/knora-api:valueAsString/text()"/>
                    <xsl:variable name="authorGivenNameText" select="knora-api:TextValue[@rdf:about=$authorGivenNameValue]/knora-api:valueAsString/text()"/>

                    <persName>
                        <xsl:attribute name="ref"><xsl:value-of select="$authorIAFText"/></xsl:attribute>
                        <xsl:value-of select="$authorFamilyNameText"/>, <xsl:value-of select="$authorGivenNameText"/>
                    </persName>

                    <xsl:variable name="dateText" select="knora-api:DateValue[@rdf:about=$dateValue]/knora-api:valueAsString/text()"/>

                    <date>
                        <xsl:attribute name="when"><xsl:value-of select="$dateText"/></xsl:attribute>
                    </date>
                </correspAction>

                <correspAction type="received">

                    <xsl:variable name="recipientIAFText" select="knora-api:TextValue[@rdf:about=$recipientIAFValue]/knora-api:valueAsString/text()"/>
                    <xsl:variable name="recipientFamilyNameText" select="knora-api:TextValue[@rdf:about=$recipientFamilyNameValue]/knora-api:valueAsString/text()"/>
                    <xsl:variable name="recipientGivenNameText" select="knora-api:TextValue[@rdf:about=$recipientGivenNameValue]/knora-api:valueAsString/text()"/>

                    <persName>
                        <xsl:attribute name="ref"><xsl:value-of select="$recipientIAFText"/></xsl:attribute>
                        <xsl:value-of select="$recipientFamilyNameText"/>, <xsl:value-of select="$recipientGivenNameText"/>
                    </persName>


                </correspAction>

            </correspDesc>

        </profileDesc>

    </xsl:template>

</xsl:transform>