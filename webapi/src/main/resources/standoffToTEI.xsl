<?xml version="1.0" encoding="UTF-8" ?>
<!--
 * Copyright © 2015-2018 the contributors (see Contributors.md).
 *
 * This file is part of Knora.
 *
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
-->

<xsl:transform xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="2.0">

    <xsl:output method="html" encoding="utf-8" indent="yes"/>

    <!--

        http://www.knora.org/ontology/standoff#StandoffRootTag

        http://www.tei-c.org/release/doc/tei-p5-doc/en/html/ref-text.html
        http://www.tei-c.org/release/doc/tei-p5-doc/en/html/ref-body.html

    -->
    <xsl:template match="text">
        <text>
            <body>
            <xsl:apply-templates/>
            </body>
        </text>
    </xsl:template>

    <!--

        http://www.knora.org/ontology/knora-base#StandoffLinkTag

        http://www.tei-c.org/release/doc/tei-p5-doc/en/html/ref-ptr.html

    -->
    <xsl:template match="entity">
        <ptr>
            <xsl:attribute name="target"><xsl:value-of select="@ref"/></xsl:attribute>
            <xsl:apply-templates/>
        </ptr>
    </xsl:template>

    <!--

        http://www.knora.org/ontology/standoff#StandoffHyperlinkTag

        http://www.tei-c.org/release/doc/tei-p5-doc/en/html/ref-ptr.html
    -->
    <xsl:template match="ptr">
        <ptr>
            <xsl:attribute name="target"><xsl:value-of select="@target"/></xsl:attribute>
            <xsl:apply-templates/>
        </ptr>
    </xsl:template>

    <!--

        http://www.knora.org/ontology/knora-base#StandoffUriTag

        http://www.tei-c.org/release/doc/tei-p5-doc/en/html/ref-ptr.html
    -->
    <xsl:template match="a">
        <ptr>
            <xsl:attribute name="target"><xsl:value-of select="@href"/></xsl:attribute>
            <xsl:apply-templates/>
        </ptr>
    </xsl:template>

    <!--
        http://www.knora.org/ontology/standoff#StandoffBlockquoteTag

        http://www.tei-c.org/release/doc/tei-p5-doc/en/html/ref-quote.html

    -->
    <xsl:template match="quote">
        <quote>
            <xsl:apply-templates/>
        </quote>
    </xsl:template>

    <!--
        http://www.knora.org/ontology/standoff#StandoffCodeTag

        http://www.tei-c.org/release/doc/tei-p5-doc/en/html/ref-code.html

    -->
    <xsl:template match="code">
        <code>
            <xsl:apply-templates/>
        </code>
    </xsl:template>

    <!--

        http://www.knora.org/ontology/standoff#StandoffParagraphTag

        http://www.tei-c.org/release/doc/tei-p5-doc/en/html/ref-p.html
    -->
    <xsl:template match="p">
        <p>
            <xsl:apply-templates/>
        </p>
    </xsl:template>

    <!--

        http://www.knora.org/ontology/standoff#StandoffItalicTag

        http://www.tei-c.org/release/doc/tei-p5-doc/en/html/ref-hi.html with attribute rend="italic"

    -->
    <xsl:template match="em">
        <hi>
            <xsl:attribute name="rend">italic</xsl:attribute>
            <xsl:apply-templates/>
        </hi>
    </xsl:template>

    <!--

        http://www.knora.org/ontology/standoff#StandoffBoldTag

        http://www.tei-c.org/release/doc/tei-p5-doc/en/html/ref-hi.html with attribute rend="bold"

        see http://www.tei-c.org/release/doc/tei-p5-doc/en/html/examples-hi.html

    -->
    <xsl:template match="b">
        <hi>
            <xsl:attribute name="rend">bold</xsl:attribute>
            <xsl:apply-templates/>
        </hi>
    </xsl:template>

    <!--

        http://www.knora.org/ontology/standoff#StandoffUnderlineTag

        http://www.tei-c.org/release/doc/tei-p5-doc/en/html/ref-hi.html with attribute rend="underline"

        see http://www.tei-c.org/release/doc/tei-p5-doc/en/html/examples-hi.html

    -->
    <xsl:template match="u">
        <hi>
            <xsl:attribute name="rend">underline</xsl:attribute>
            <xsl:apply-templates/>
        </hi>
    </xsl:template>

    <!--

        http://www.knora.org/ontology/standoff#StandoffSuperscriptTag

        http://www.tei-c.org/release/doc/tei-p5-doc/en/html/ref-hi.html with attribute rend="sup"

        see http://www.tei-c.org/release/doc/tei-p5-doc/en/html/examples-hi.html

    -->
    <xsl:template match="sup">
        <hi>
            <xsl:attribute name="rend">sup</xsl:attribute>
            <xsl:apply-templates/>
        </hi>
    </xsl:template>

    <!--

        http://www.knora.org/ontology/standoff#StandoffSubscriptTag

        Ignore since there seems to be no standard way in TEI to represent this.

    -->
    <xsl:template match="sub">
        <xsl:apply-templates/>
    </xsl:template>







</xsl:transform>