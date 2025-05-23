<?xml version="1.0" encoding="UTF-8" ?>
<!--
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
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

        http://www.tei-c.org/release/doc/tei-p5-doc/en/html/ref-ref.html

    -->
    <xsl:template match="entity|entity2">
        <ref>
            <xsl:attribute name="target"><xsl:value-of select="@ref"/></xsl:attribute>
            <xsl:apply-templates/>
        </ref>
    </xsl:template>

    <!--

        http://www.knora.org/ontology/knora-base#StandoffUriTag

        http://www.tei-c.org/release/doc/tei-p5-doc/en/html/ref-ref.html
    -->
    <xsl:template match="ref">
        <ref>
            <xsl:attribute name="target"><xsl:value-of select="@target"/></xsl:attribute>
            <xsl:apply-templates/>
        </ref>
    </xsl:template>

    <!--

        http://www.knora.org/ontology/standoff#StandoffHyperlinkTag

        http://www.tei-c.org/release/doc/tei-p5-doc/en/html/ref-ref.html

    -->
    <xsl:template match="a">
        <ref>
            <xsl:attribute name="target"><xsl:value-of select="@href"/></xsl:attribute>
            <xsl:apply-templates/>
        </ref>
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

        http://www.knora.org/ontology/standoff#StandoffTableCellTag

        http://www.tei-c.org/release/doc/tei-p5-doc/en/html/ref-cell.html
    -->
    <xsl:template match="cell">
        <cell>
            <xsl:apply-templates/>
        </cell>
    </xsl:template>

    <!--

        http://www.knora.org/ontology/standoff#StandoffBrTag
        http://www.knora.org/ontology/0801/beol#StandoffBrTag

        http://www.tei-c.org/release/doc/tei-p5-doc/en/html/ref-lb.html
        -->
    <xsl:template match="br|br2">
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


    <!--

    http://www.knora.org/ontology/0801/beol#StandoffReferenceTag

    http://www.tei-c.org/release/doc/tei-p5-doc/en/html/ref-note.html

    -->
    <xsl:template match="note">
        <note>
            <xsl:apply-templates/>
        </note>
    </xsl:template>

    <!--

    http://www.knora.org/ontology/0801/beol#StandoffMathTag

    http://www.tei-c.org/release/doc/tei-p5-doc/en/html/ref-note.html

    -->
    <xsl:template match="formula">
        <formula>
            <xsl:attribute name="notation">tex</xsl:attribute>
            <xsl:apply-templates/>
        </formula>
    </xsl:template>

    <!--

    http://www.knora.org/ontology/0801/beol#StandoffFacsimileTag
    http://www.knora.org/ontology/0801/beol#StandoffFigureTag

    http://www.tei-c.org/release/doc/tei-p5-doc/en/html/ref-ref.html
    -->
    <xsl:template match="facsimile|figure">
        <ref>
            <xsl:attribute name="target"><xsl:value-of select="@src"/></xsl:attribute>
            <xsl:apply-templates/>
        </ref>
    </xsl:template>

    <!--

    http://www.knora.org/ontology/0801/beol#StandoffPbTag

    http://www.tei-c.org/release/doc/tei-p5-doc/en/html/ref-pb.html
    -->
    <xsl:template match="pb">
        <pb>
            <xsl:apply-templates/>
        </pb>
    </xsl:template>

    <!--

    http://www.knora.org/ontology/0801/beol#StandoffHrTag

    ignore

    -->
    <xsl:template match="hr">
            <xsl:apply-templates/>
    </xsl:template>

    <!--

    http://www.knora.org/ontology/0801/beol#StandoffSmallTag

    ignore

    -->
    <xsl:template match="small">
        <xsl:apply-templates/>
    </xsl:template>


</xsl:transform>
