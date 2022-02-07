<?xml version="1.0" encoding="UTF-8" ?>

<xsl:transform xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="2.0">

    <xsl:output method="html" encoding="utf-8" indent="no"/>

    <xsl:template match="text">
        <div>
            <xsl:apply-templates/>
        </div>
    </xsl:template>

    <xsl:template match="section">
        <p>
            <xsl:apply-templates/>
        </p>
    </xsl:template>

    <xsl:template match="italic">
        <i>
            <xsl:apply-templates/>
        </i>
    </xsl:template>

</xsl:transform>
