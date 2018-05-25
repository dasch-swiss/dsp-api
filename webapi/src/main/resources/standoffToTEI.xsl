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

    <xsl:output method="xml" omit-xml-declaration="yes" encoding="utf-8" indent="no"/>

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

        http://www.knora.org/ontology/standoff#StandoffHeader1Tag

        http://www.tei-c.org/release/doc/tei-p5-doc/en/html/ref-head.html
    -->
    <xsl:template match="header1">
        <head>
            <xsl:apply-templates/>
        </head>
    </xsl:template>

    <!--

        http://www.knora.org/ontology/standoff#StandoffHeader2Tag

        http://www.tei-c.org/release/doc/tei-p5-doc/en/html/ref-head.html
    -->
    <xsl:template match="header2">
        <head>
            <xsl:apply-templates/>
        </head>
    </xsl:template>

    <!--

        http://www.knora.org/ontology/standoff#StandoffHeader3Tag

        http://www.tei-c.org/release/doc/tei-p5-doc/en/html/ref-head.html
    -->
    <xsl:template match="header3">
        <head>
            <xsl:apply-templates/>
        </head>
    </xsl:template>

    <!--

        http://www.knora.org/ontology/standoff#StandoffHeader4Tag

        http://www.tei-c.org/release/doc/tei-p5-doc/en/html/ref-head.html
    -->
    <xsl:template match="header4">
        <head>
            <xsl:apply-templates/>
        </head>
    </xsl:template>

    <!--

        http://www.knora.org/ontology/standoff#StandoffHeader5Tag

        http://www.tei-c.org/release/doc/tei-p5-doc/en/html/ref-head.html
    -->
    <xsl:template match="header5">
        <head>
            <xsl:apply-templates/>
        </head>
    </xsl:template>

    <!--

        http://www.knora.org/ontology/standoff#StandoffHeader6Tag

        http://www.tei-c.org/release/doc/tei-p5-doc/en/html/ref-head.html
    -->
    <xsl:template match="header6">
        <head>
            <xsl:apply-templates/>
        </head>
    </xsl:template>

    <!--

        http://www.knora.org/ontology/standoff#OrderedListTag

        http://www.tei-c.org/release/doc/tei-p5-doc/en/html/ref-list.html with attribute rend="numbered"
    -->
    <xsl:template match="ol">
        <list>
            <xsl:attribute name="rend">numbered</xsl:attribute>
            <xsl:apply-templates/>
        </list>
    </xsl:template>

    <!--

        http://www.knora.org/ontology/standoff#UnorderedListTag

        http://www.tei-c.org/release/doc/tei-p5-doc/en/html/ref-list.html with attribute rend="bulleted"
    -->
    <xsl:template match="ul">
        <list>
            <xsl:attribute name="rend">bulleted</xsl:attribute>
            <xsl:apply-templates/>
        </list>
    </xsl:template>

    <!--

        http://www.knora.org/ontology/standoff#StandoffListElementTag

        http://www.tei-c.org/release/doc/tei-p5-doc/en/html/ref-item.html
    -->
    <xsl:template match="listitem">
        <item>
            <xsl:apply-templates/>
        </item>
    </xsl:template>

    <!--

        http://www.knora.org/ontology/standoff#StandoffTableTag

        http://www.tei-c.org/release/doc/tei-p5-doc/en/html/ref-table.html
    -->
    <xsl:template match="table">
        <table>
            <xsl:apply-templates/>
        </table>
    </xsl:template>

    <!--

        http://www.knora.org/ontology/standoff#StandoffTableTag

        ignore this element
    -->
    <xsl:template match="tablebody">
        <xsl:apply-templates/>
    </xsl:template>

    <!--

        http://www.knora.org/ontology/standoff#StandoffTableRowTag

        http://www.tei-c.org/release/doc/tei-p5-doc/en/html/ref-row.html
    -->
    <xsl:template match="row">
        <row>
            <xsl:apply-templates/>
        </row>
    </xsl:template>

    <!--

        http://www.knora.org/ontology/standoff#StandoffTableBrTag

        http://www.tei-c.org/release/doc/tei-p5-doc/en/html/ref-cell.html
    -->
    <xsl:template match="cell">
        <cell>
            <xsl:apply-templates/>
        </cell>
    </xsl:template>

    <!--

        http://www.knora.org/ontology/standoff#StandoffTableCellTag

        http://www.tei-c.org/release/doc/tei-p5-doc/en/html/ref-lb.html
        -->
    <xsl:template match="br">
        <lb>
            <xsl:apply-templates/>
        </lb>
    </xsl:template>

    <!--

        http://www.knora.org/ontology/standoff#StandoffTableCiteTag

        http://www.tei-c.org/release/doc/tei-p5-doc/en/html/ref-lb.html
    -->
    <xsl:template match="cite">
        <bibl>
            <xsl:apply-templates/>
        </bibl>
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

        http://www.knora.org/ontology/standoff#StandoffStrikethroughTag

        http://www.tei-c.org/release/doc/tei-p5-doc/en/html/ref-del.html

    -->
    <xsl:template match="strike">
        <del>
            <xsl:apply-templates/>
        </del>
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

    <!--

        http://www.knora.org/ontology/standoff#StandoffLineTag

        Ignore since there seems to be no standard way in TEI to represent this.

    -->
    <xsl:template match="line">
        <xsl:apply-templates/>
    </xsl:template>


    <!--

        http://www.knora.org/ontology/standoff#StandoffPreTag

        Ignore since there seems to be no standard way in TEI to represent this.

    -->
    <xsl:template match="pre">
        <xsl:apply-templates/>
    </xsl:template>




</xsl:transform>