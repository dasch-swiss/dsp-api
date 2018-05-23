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

    <xsl:template match="text">
        <text>
            <body>
            <xsl:apply-templates/>
            </body>
        </text>
    </xsl:template>

    <xsl:template match="entity">
        <ptr>
            <xsl:attribute name="target"><xsl:value-of select="@ref"/></xsl:attribute>
            <xsl:apply-templates/>
        </ptr>
    </xsl:template>

    <xsl:template match="ptr">
        <ptr>
            <xsl:attribute name="target"><xsl:value-of select="@target"/></xsl:attribute>
            <xsl:apply-templates/>
        </ptr>
    </xsl:template>

    <xsl:template match="a">
        <ptr>
            <xsl:attribute name="target"><xsl:value-of select="@href"/></xsl:attribute>
            <xsl:apply-templates/>
        </ptr>
    </xsl:template>

    <xsl:template match="p">
        <p>
            <xsl:apply-templates/>
        </p>
    </xsl:template>

    <xsl:template match="em">
        <hi>
            <xsl:attribute name="rend">italic</xsl:attribute>
            <xsl:apply-templates/>
        </hi>
    </xsl:template>

    <xsl:template match="b">
        <hi>
            <xsl:attribute name="rend">bold</xsl:attribute>
            <xsl:apply-templates/>
        </hi>
    </xsl:template>

    <xsl:template match="u">
        <hi>
            <xsl:attribute name="rend">underline</xsl:attribute>
            <xsl:apply-templates/>
        </hi>
    </xsl:template>

    <xsl:template match="sup">
        <hi>
            <xsl:attribute name="rend">sup</xsl:attribute>
            <xsl:apply-templates/>
        </hi>
    </xsl:template>








</xsl:transform>