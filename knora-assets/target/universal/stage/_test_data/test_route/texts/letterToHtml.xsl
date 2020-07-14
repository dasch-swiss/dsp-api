<?xml version="1.0" encoding="UTF-8"?>

<xsl:transform xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="2.0">

    <xsl:output method="html" encoding="utf-8" indent="yes"/>

    <xsl:template match="text">
        <div>

            <div id="transcription">
                <xsl:apply-templates/>
            </div>

            <div id="references">
                <ol>
                    <xsl:apply-templates select="//ref" mode="references"/>
                </ol>
            </div>
        </div>

    </xsl:template>

    <xsl:template match="ref">
        <a class="ref-marker">
            <xsl:attribute name="href">
                <xsl:text>#ref</xsl:text>
                <xsl:number level="any" count="ref" format="1"/>
            </xsl:attribute>
            <sup><xsl:number level="any" count="ref" format="1"/></sup>
        </a>
    </xsl:template>

    <xsl:template match="ref" mode="references">
        <li>
            <xsl:attribute name="id">
                <xsl:text>ref</xsl:text>
                <xsl:number level="any" count="ref" format="1"/>
            </xsl:attribute>

            <xsl:apply-templates/>

        </li>
    </xsl:template>

    <xsl:template match="i">
        <em><xsl:apply-templates/></em>
    </xsl:template>

    <xsl:template match="b">
        <strong><xsl:apply-templates/></strong>
    </xsl:template>

    <xsl:template match="u">
        <span style="text-decoration: underline;"><xsl:apply-templates/></span>
    </xsl:template>

    <xsl:template match="sub">
        <sub><xsl:apply-templates/></sub>
    </xsl:template>

    <xsl:template match="sup">
        <sup><xsl:apply-templates/></sup>
    </xsl:template>

    <xsl:template match="math">
        <span class="math">\(<xsl:apply-templates/>\)</span>
    </xsl:template>

    <xsl:template match="facsimile|figure">
        <a class="facsimile salsah-link">
            <xsl:attribute name="href"><xsl:value-of select="@src" /></xsl:attribute>
            <xsl:apply-templates/>
        </a>
    </xsl:template>

    <xsl:template match="entity">
        <a class="salsah-link">
            <xsl:attribute name="href"><xsl:value-of select="@ref" /></xsl:attribute>
            <xsl:apply-templates/>
        </a>
    </xsl:template>

    <xsl:template match="a">
        <a class="external_link">
            <xsl:attribute name="href"><xsl:value-of select="@href" /></xsl:attribute>
            <xsl:apply-templates/>
        </a>
    </xsl:template>

    <xsl:template match="br">
        <br/>
    </xsl:template>

    <xsl:template match="pb">
        <span class="pagebreak"></span>
    </xsl:template>

    <xsl:template match="p">
        <p><xsl:apply-templates/></p>
    </xsl:template>

    <xsl:template match="hr">
        <hr/>
    </xsl:template>

    <!-- table -->
    <xsl:template match="table">
        <table>
            <xsl:apply-templates/>
        </table>
    </xsl:template>

    <!-- row -->
    <xsl:template match="table/tr">
        <tr>
            <xsl:apply-templates/>
        </tr></xsl:template>

    <!-- cell: get all attributes: border and align -->
    <xsl:template match="table/tr/td">
        <td><xsl:apply-templates/></td>
    </xsl:template>

</xsl:transform>
