/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.util.standoff

import org.apache.commons.text.StringEscapeUtils

import java.io.StringReader
import java.io.StringWriter
import java.util.UUID
import javax.xml.parsers.SAXParserFactory
import javax.xml.transform.stream.StreamSource
import scala.util.control.NonFatal
import scala.xml.*

import dsp.errors.*
import dsp.valueobjects.UuidUtil
import org.knora.webapi.*
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.util.ErrorHandlingMap

/**
 * Represents an attribute of a standoff tag.
 *
 * @param key          the name of the attribute.
 * @param xmlNamespace the XML namespace that is used for the attribute when the tag is represented as XML.
 * @param value        the value of the attribute.
 */
case class StandoffTagAttribute(key: String, xmlNamespace: Option[IRI], value: String)

/**
 * Represents markup on a range of characters in a text.
 */
sealed trait StandoffTag {

  /**
   * The document-specific ID of this tag, if any.
   */
  def originalID: Option[String]

  /**
   * A [[UUID]] representing this tag and any other tags that point to semantically equivalent
   * content in other versions of the same text.
   */
  def uuid: UUID

  /**
   * The name of the tag.
   */
  def tagName: String

  /**
   * The namespace used when this tag is represented as XML.
   */
  def xmlNamespace: Option[IRI]

  /**
   * The start position of the text range.
   */
  def startPosition: Int

  /**
   * The end position of the text range.
   */
  def endPosition: Int

  /**
   * The attributes attached to this tag.
   */
  def attributes: Set[StandoffTagAttribute]
}

/**
 * Represents a [[StandoffTag]] that has a single index indicating its position in a sequence of standoff tags,
 * and optionally the index of the tag that contains it.
 */
sealed trait IndexedStandoffTag extends StandoffTag {
  def index: Int

  def parentIndex: Option[Int]
}

/**
 * Represents a standoff tag that requires a hierarchical document structure. When serialised to XML, it is represented
 * as a single element.
 *
 * @param originalID    a client-specific ID for the tag.
 * @param uuid          a [[UUID]] representing this tag and any other tags that
 *                      point to semantically equivalent content in other versions of the same text.
 * @param tagName       the name of this tag.
 * @param xmlNamespace  the namespace used when this tag is represented as XML.
 * @param attributes    the attributes attached to this tag.
 * @param startPosition the start position of the range of characters marked up with this tag.
 * @param endPosition   the end position of the range of characters marked up with this tag.
 * @param index         the index of this tag. Indexes are numbered from 0 within the context of a particular text,
 *                      and make it possible to order tags that share the same position.
 * @param parentIndex   the index of the [[HierarchicalStandoffTag]] that contains this tag. If a tag has no
 *                      parent, it is the root of the tree.
 */
case class HierarchicalStandoffTag(
  originalID: Option[String],
  uuid: UUID,
  tagName: String,
  xmlNamespace: Option[IRI] = None,
  attributes: Set[StandoffTagAttribute] = Set.empty[StandoffTagAttribute],
  startPosition: Int,
  endPosition: Int,
  index: Int,
  parentIndex: Option[Int] = None,
) extends IndexedStandoffTag

/**
 * Represents a standoff tag that does not require a hierarchical document structure, although it can be used within
 * such a structure. Its XML representation is a pair of empty elements in
 * [[http://conferences.idealliance.org/extreme/html/2004/DeRose01/EML2004DeRose01.html#t6 CLIX]] format.
 *
 * @param originalID       a client-specific ID for the tag.
 * @param uuid             a [[UUID]] representing this tag and any other tags that
 *                         point to semantically equivalent content in other versions of the same text.
 * @param tagName          the name of the tag.
 * @param xmlNamespace     the namespace used when this tag is represented as XML.
 * @param attributes       the attributes attached to this tag.
 * @param startPosition    the start position of the range of characters marked up with this tag.
 * @param endPosition      the end position of the range of characters marked up with this tag.
 * @param startIndex       the index of the start position. Indexes are numbered from 0 within the context of a
 *                         particular text, and make it possible to order tags that share the same position.
 * @param startParentIndex the index of the [[HierarchicalStandoffTag]], if any, that contains the start position.
 * @param endIndex         the index of the end position.
 * @param endParentIndex   the index of the [[HierarchicalStandoffTag]], if any, that contains the end position.
 */
case class FreeStandoffTag(
  originalID: Option[String],
  uuid: UUID,
  tagName: String,
  xmlNamespace: Option[IRI] = None,
  attributes: Set[StandoffTagAttribute] = Set.empty[StandoffTagAttribute],
  startPosition: Int,
  endPosition: Int,
  startIndex: Int,
  startParentIndex: Option[Int] = None,
  endIndex: Int,
  endParentIndex: Option[Int] = None,
) extends StandoffTag

/**
 * Represents a text and its standoff markup.
 *
 * @param text     the text that has been marked up with standoff.
 * @param standoff the standoff markup.
 */
case class TextWithStandoff(text: String, standoff: Seq[StandoffTag])

/**
 * Represents an XML element that requires a separator to be inserted at its end.
 * This is necessary because the markup is going to be represented in standoff (separated from the text).
 *
 * @param maybeNamespace the namespace the element belongs to, if any.
 * @param tagname        the name of the element.
 * @param maybeClassname the class of the element, if any.
 */
case class XMLTagSeparatorRequired(maybeNamespace: Option[String], tagname: String, maybeClassname: Option[String]) {

  // generate an XPath expression to match this element
  def toXPath: String = {

    val prefix: String = maybeNamespace match {
      case Some(namespace) => namespace + ":"
      case None            => ""
    }

    val classSelector: String = maybeClassname match {
      case Some(classSel) =>
        s"""[@class='$classSel']""" // use single quotes because the xpath expression is wrapped in double quotes
      case None => ""
    }

    // the XPath expression matching this element
    s"$prefix$tagname$classSelector"
  }
}

/**
 * Standoff-related constants.
 */
object XMLToStandoffUtil {
  // The header written at the start of every XML document.
  private val XmlHeader = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"

  // The name of the XML attribute that contains the ID of a hierarchical element (i.e. not a CLIX milestone).
  private val XmlHierarchicalIdAttrName = "id"

  // The name of the XML attribute that contains the ID of a CLIX start milestone.
  private val XmlClixStartIdAttrName = "sID"

  // The name of the XML attribute that contains the ID of a CLIX end milestone.
  private val XmlClixEndIdAttrName = "eID"

  // The names of all the XML attributes used as IDs.
  private val XmlIdAttrNames = Set(XmlHierarchicalIdAttrName, XmlClixStartIdAttrName, XmlClixEndIdAttrName)
}

/**
 * Converts XML documents to standoff markup and back again. Supports
 * [[http://conferences.idealliance.org/extreme/html/2004/DeRose01/EML2004DeRose01.html#t6 CLIX]] format
 * for overlapping tags.
 *
 * Every standoff tag has a [[UUID]]. These can be represented in XML in different ways:
 *
 * - In canonical form as a 36-character string.
 * - As a 22-character Base64-encoded string.
 * - As a document-specific ID that can be mapped to a UUID.
 *
 * These IDs are represented using the following XML attributes:
 *
 * - `sID` for CLIX start milestones.
 * - `eID` for CLIX end milestones.
 * - `id` for all other elements.
 *
 * IDs are required on CLIX milestones, and optional on other elements.
 *
 * When converting from XML:
 *
 * - If a document-specific ID is provided and can be mapped to a UUID, that UUID is used.
 * - If a UUID is provided in canonical form or Base64 encoding, that UUID is used.
 * - Otherwise (if no ID is provided, or if an ID is provided but cannot be parsed as a UUID or mapped to one),
 * a random UUID is generated.
 *
 * When converting to XML:
 *
 * - If `writeAllIds` is set to `true` (the default), the ID of every element is written; otherwise, only the IDs of
 * CLIX milestones are included.
 * - If a UUID can be mapped to a document-specific ID, the document-specific ID is used, otherwise the UUID is used.
 * - UUIDs are written in Base64 encoding if `writeBase64Ids` is `true` (the default), otherwise in canonical form.
 *
 * @param xmlNamespaces       A map of prefixes to XML namespaces, to be used when converting standoff to XML.
 * @param writeUuidsToXml     If `true` (the default), adds the ID of every standoff tag as an attribute when writing
 *                            XML. Otherwise, only the IDs of CLIX milestones and elements that originally had an id in XML are included.
 * @param writeBase64IDs      If `true`, writes UUIDs in Base64 encoding; otherwise, writes UUIDs in canonical form.
 * @param documentSpecificIDs An optional mapping between document-specific IDs and UUIDs. When reading XML,
 *                            each document-specific ID will be converted to the corresponding UUID. Elements that
 *                            don't specify an ID will be assigned a random UUID. When writing XML, each UUID will
 *                            be converted to the corresponding document-specific ID if available.
 */
class XMLToStandoffUtil(
  xmlNamespaces: Map[String, IRI] = Map.empty[IRI, String],
  defaultXmlNamespace: Option[IRI] = None,
  writeUuidsToXml: Boolean = true,
  writeBase64IDs: Boolean = true,
  documentSpecificIDs: Map[String, UUID] = Map.empty[String, UUID],
) {

  import XMLToStandoffUtil.*
  // Parse XML with an XML parser configured to prevent certain security risks.
  // See <https://github.com/scala/scala-xml/issues/17>.
  private val saxParserFactory = SAXParserFactory.newInstance()
  saxParserFactory.setFeature("http://xml.org/sax/features/external-general-entities", false)
  saxParserFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)

  // A Map of UUIDs to document-specific IDs.
  private val uuidsToDocumentSpecificIds: Map[UUID, String] = documentSpecificIDs.map(_.swap)

  private val xmlNamespaces2Prefixes = new ErrorHandlingMap(
    xmlNamespaces.map(_.swap),
    { (key: String) =>
      s"No prefix defined for XML namespace $key"
    },
  )

  /**
   * An empty standoff tag representing a CLIX milestone, to facilitate conversion to and from XML.
   *
   * @param isStartTag if `true`, this tag represents the start element, otherwise it represents the end element.
   */
  private case class ClixMilestoneTag(
    originalID: Option[String],
    uuid: UUID,
    tagName: String,
    xmlNamespace: Option[IRI] = None,
    attributes: Set[StandoffTagAttribute] = Set.empty[StandoffTagAttribute],
    startPosition: Int,
    endPosition: Int,
    index: Int,
    parentIndex: Option[Int] = None,
    isStartTag: Boolean,
  ) extends IndexedStandoffTag

  /**
   * Creates XSLT that inserts a separator after each element matching the XPath expression.
   *
   * @param xpath     the XPath expression used to match elements.
   * @param separator the separator to be inserted.
   * @return an XSLT stylesheet as a [[String]].
   */
  private def insertSeparatorsXSLT(xpath: String, separator: Char) =
    s"""<?xml version="1.0" encoding="UTF-8"?>
       |
       |<xsl:transform xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="2.0">
       |
       |    <xsl:output indent="no" encoding="UTF-8"/>
       |
       |    <xsl:template match="@*|node()">
       |        <xsl:copy>
       |            <xsl:apply-templates select="@*|node()"/>
       |        </xsl:copy>
       |    </xsl:template>
       |
       |    <xsl:template match="$xpath">
       |        <xsl:variable name="ele" select="name()"/>
       |
       |        <xsl:element name="{$$ele}">
       |        <xsl:copy-of select="@*"/>
       |            <xsl:apply-templates/>
       |        </xsl:element>
       |        <xsl:text>$separator</xsl:text>
       |    </xsl:template>
       |
       |</xsl:transform>
        """.stripMargin

  ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  // Public methods

  /**
   * Converts an XML document to an equivalent [[TextWithStandoff]].
   *
   * @param xmlStr            the XML document to be converted.
   * @param tagsWithSeparator a Seq with the tags that require a separator
   *                          in the string representation (`valueHasString`) once markup is converted to standoff.
   * @return a [[TextWithStandoff]].
   */
  def xml2TextWithStandoff(
    xmlStr: String,
    tagsWithSeparator: Seq[XMLTagSeparatorRequired] = Seq.empty[XMLTagSeparatorRequired],
  ): TextWithStandoff = {

    // Knora uses Unicode INFORMATION SEPARATOR TWO (U+001E) to indicate word breaks where a tag implicitly separates words. But
    // INFORMATION SEPARATOR TWO is not a valid XML 1.0 character. Therefore, we temporarily insert PARAGRAPH SEPARATOR (U+2029)
    // into the XML, at the positions where such a word break is needed, before converting the XML to standoff. Below, we will
    // replace PARAGRAPH SEPARATOR with INFORMATION SEPARATOR TWO.

    // check that the original XML does not already contain PARAGRAPH SEPARATOR.
    if (xmlStr.contains(StringFormatter.PARAGRAPH_SEPARATOR))
      throw BadRequestException("XML contains special separator character PARAGRAPH_SEPARATOR '\\u2029'")

    val xmlStrWithSeparator = if (tagsWithSeparator.nonEmpty) {
      // build an XSLT to add separators to the XML
      val xPAthExpression: String =
        tagsWithSeparator.map(_.toXPath).mkString("|") // build a union of the collected elements' expressions

      val XSLT = insertSeparatorsXSLT(xPAthExpression, StringFormatter.PARAGRAPH_SEPARATOR)

      // apply XSLT to XML
      // preprocess XML to separate structures
      val proc = new net.sf.saxon.s9api.Processor(false)
      val comp = proc.newXsltCompiler()

      val exp    = comp.compile(new StreamSource(new StringReader(XSLT)))
      val source =
        try {
          proc.newDocumentBuilder().build(new StreamSource(new StringReader(xmlStr)))
        } catch {
          case e: Exception =>
            throw StandoffConversionException(s"The provided XML could not be parsed: ${e.getMessage}")
        }

      val xmlStrWithSep: StringWriter = new StringWriter()
      val out                         = proc.newSerializer(xmlStrWithSep)

      val trans = exp.load()
      trans.setInitialContextNode(source)
      trans.setDestination(out)
      trans.transform()

      xmlStrWithSep.toString
    } else {
      xmlStr
    }

    val saxParser = saxParserFactory.newSAXParser()

    val nodes: Elem =
      try {
        XML.withSAXParser(saxParser).loadString(xmlStrWithSeparator)
      } catch {
        case NonFatal(e) => throw StandoffInternalException(s"XML processing error: ${e.getMessage}", Some(e))
      }

    val finishedConversionState = xmlNodes2Standoff(
      nodes = nodes,
      startState = Xml2StandoffState(),
    )

    if (finishedConversionState.clixStartMilestones.nonEmpty) {
      val missingEndTags = finishedConversionState.clixStartMilestones.map {
        case (startTagID: String, startTag: ClixMilestoneTag) =>
          s"<${startTag.tagName} $XmlClixStartIdAttrName=${'"'}$startTagID${'"'}>"
      }
        .mkString(", ")

      throw InvalidStandoffException(s"One or more CLIX milestones were not closed: $missingEndTags")
    }

    // Replace PARAGRAPH SEPARATOR with INFORMATION SEPARATOR TWO.
    val textWithInformationSeparatorTwo = nodes.text
      .replace(StringFormatter.PARAGRAPH_SEPARATOR.toString, StringFormatter.INFORMATION_SEPARATOR_TWO.toString)

    TextWithStandoff(
      text = textWithInformationSeparatorTwo,
      standoff = finishedConversionState.standoffTags,
    )
  }

  /**
   * Converts a [[TextWithStandoff]] to an equivalent XML document.
   *
   * @param textWithStandoff the [[TextWithStandoff]] to be converted.
   * @return an XML document.
   */
  def textWithStandoff2Xml(textWithStandoff: TextWithStandoff): String = {
    val tags = textWithStandoff.standoff.foldLeft(Vector.empty[IndexedStandoffTag]) {
      // Split each free tag into a pair of CLIX milestones.
      case (acc, freeTag: FreeStandoffTag) =>
        val startTag = ClixMilestoneTag(
          originalID = freeTag.originalID,
          uuid = freeTag.uuid,
          tagName = freeTag.tagName,
          xmlNamespace = freeTag.xmlNamespace,
          attributes = freeTag.attributes,
          startPosition = freeTag.startPosition,
          endPosition = freeTag.startPosition,
          index = freeTag.startIndex,
          parentIndex = freeTag.startParentIndex,
          isStartTag = true,
        )

        val endTag = ClixMilestoneTag(
          originalID = freeTag.originalID,
          uuid = freeTag.uuid,
          tagName = freeTag.tagName,
          xmlNamespace = freeTag.xmlNamespace,
          startPosition = freeTag.endPosition,
          endPosition = freeTag.endPosition,
          index = freeTag.endIndex,
          parentIndex = freeTag.endParentIndex,
          isStartTag = false,
        )

        acc :+ startTag :+ endTag

      case (acc, hierarchicalTag: HierarchicalStandoffTag) =>
        acc :+ hierarchicalTag

      // It seems as if the following line should work, but it doesn't. See https://github.com/scala/bug/issues/10100
      // case (_, clixTag: ClixMilestoneTag) => throw AssertionException(s"CLIX tag $clixTag cannot be in TextWithStandoff") // This should never happen

      // Workaround:
      case (_, other) =>
        throw AssertionException(s"Tag $other cannot be in TextWithStandoff") // This should never happen
    }

    val groupedTags: Map[Option[Int], Seq[IndexedStandoffTag]] = tags.groupBy(_.parentIndex)
    val stringBuilder                                          = new StringBuilder(XmlHeader)

    // Start with the root.
    groupedTags.get(None) match {
      case Some(children) if children.size == 1 =>
        standoffTags2XmlString(
          text = textWithStandoff.text.replace(
            StringFormatter.INFORMATION_SEPARATOR_TWO.toString,
            StringFormatter.PARAGRAPH_SEPARATOR.toString,
          ), // replace information separator (which is an invalid XML character) with paragraph separator
          groupedTags = groupedTags,
          posBeforeSiblings = 0,
          siblings = children,
          writeNamespaces = true,
          xmlString = stringBuilder,
        )

      case Some(_) =>
        throw InvalidStandoffException(
          "The standoff cannot be serialised to XML because it would have multiple root nodes",
        )

      case None =>
        throw InvalidStandoffException("The standoff cannot be serialised to XML because there is no root element")
    }

    // get rid of separator in XML before sending the XML back
    val xmlStr: String = stringBuilder.toString.replace(StringFormatter.PARAGRAPH_SEPARATOR.toString, "")

    // make sure that the XML is well formed
    val saxParser = saxParserFactory.newSAXParser()

    try {
      XML.withSAXParser(saxParser).loadString(xmlStr)
    } catch {
      case e: Exception =>
        throw StandoffConversionException(s"The standoff markup could not be converted to valid XML: ${e.getMessage}")
    }

    xmlStr
  }

  ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  // Private methods

  /**
   * Represents the state of the conversion of XML text to standoff.
   *
   * @param currentPos          the current position in the text.
   * @param parentId            the ID of the parent [[HierarchicalStandoffTag]], or [[None]] if the root element is
   *                            being generated.
   * @param nextIndex           the next available standoff tag index.
   * @param standoffTags        the standoff tags generated so far.
   * @param clixStartMilestones a map of element IDs to CLIX start milestones for which end milestones have not yet
   *                            been encountered.
   */
  private case class Xml2StandoffState(
    currentPos: Int = 0,
    parentId: Option[Int] = None,
    nextIndex: Int = 0,
    standoffTags: Vector[StandoffTag] = Vector.empty[StandoffTag],
    clixStartMilestones: Map[String, ClixMilestoneTag] = Map.empty[String, ClixMilestoneTag],
  )

  /**
   * Recursively converts XML nodes to standoff.
   *
   * @param nodes      a sequence of sibling XML nodes to be converted.
   * @param startState the current state of the conversion.
   * @return the resulting conversion state.
   */
  private def xmlNodes2Standoff(nodes: NodeSeq, startState: Xml2StandoffState): Xml2StandoffState = {

    /**
     * Converts an XML element ID to a UUID.
     *
     * @param id the ID to be converted.
     * @return the corresponding UUID.
     */
    def id2Uuid(id: String): UUID =
      // If the ID was listed as a document-specific ID corresponding to an existing UUID, use that UUID.
      documentSpecificIDs.get(id) match {
        case Some(existingUuid) => existingUuid
        case None               =>
          // Otherwise, try to parse the ID as a UUID.
          if (UuidUtil.hasValidLength(id)) {
            UuidUtil.decode(id)
          } else {
            // If the ID doesn't seem to be a UUID, replace it with a random UUID. TODO: this should throw an exception instead.
            UUID.randomUUID
          }
      }

    /**
     * Converts XML attributes to standoff tag attributes, ignoring ID attributes.
     *
     * @param element the XML element containing the attributes.
     * @return the corresponding standoff tag attributes.
     */
    def xmlAttrs2StandoffAttrs(element: Elem): Set[StandoffTagAttribute] =
      element.attributes.foldLeft(Set.empty[StandoffTagAttribute]) {
        case (acc, xmlAttr: MetaData) if !XmlIdAttrNames.contains(xmlAttr.key) =>
          acc + StandoffTagAttribute(
            key = xmlAttr.key,
            xmlNamespace = Option(xmlAttr.getNamespace(element)),
            value = xmlAttr.value.text,
          )

        case (acc, _) => acc
      }

    // Process sibling nodes.
    nodes.foldLeft(startState) {
      case (acc: Xml2StandoffState, elem: Elem) =>
        // We got an XML element. Generate a standoff tag for it.
        val newTagIndex    = acc.nextIndex
        val attrMap        = elem.attributes.asAttrMap
        val isEmptyElement = elem.text.length == 0

        if (isEmptyElement && attrMap.contains(XmlClixStartIdAttrName)) {
          // It's a CLIX start milestone. Save it until we get the matching end milestone.

          val sID = attrMap(XmlClixStartIdAttrName)

          val tag = ClixMilestoneTag(
            originalID = Some(sID),
            tagName = elem.label,
            xmlNamespace = Option(elem.namespace),
            attributes = xmlAttrs2StandoffAttrs(elem),
            startPosition = acc.currentPos,
            endPosition = acc.currentPos,
            index = newTagIndex,
            parentIndex = startState.parentId,
            uuid = id2Uuid(sID),
            isStartTag = true,
          )

          acc.copy(
            nextIndex = newTagIndex + 1,
            clixStartMilestones = acc.clixStartMilestones + (sID -> tag),
          )
        } else if (isEmptyElement && attrMap.contains(XmlClixEndIdAttrName)) {
          // It's a CLIX end milestone. Combine it with the start milestone to make a FreeStandoffTag.

          val eID: String = attrMap(XmlClixEndIdAttrName)

          val startMilestone: ClixMilestoneTag = acc.clixStartMilestones.getOrElse(
            eID,
            throw InvalidStandoffException(
              s"Found a CLIX milestone with $XmlClixEndIdAttrName $eID, but there was no start milestone with that $XmlClixStartIdAttrName",
            ),
          )

          if (startMilestone.tagName != elem.label) {
            throw InvalidStandoffException(
              s"The CLIX start milestone with $XmlClixStartIdAttrName $eID has tag name <${startMilestone.tagName}>, but the end milestone with that $XmlClixEndIdAttrName has tag name ${elem.label}",
            )
          }

          if (startMilestone.xmlNamespace != Option(elem.namespace)) {
            throw InvalidStandoffException(
              s"The CLIX start milestone with $XmlClixStartIdAttrName $eID is in namespace ${startMilestone.xmlNamespace}, but the end milestone with that $XmlClixEndIdAttrName is in namespace ${elem.namespace}",
            )
          }

          val freeTag = FreeStandoffTag(
            originalID = Some(eID),
            uuid = startMilestone.uuid,
            tagName = startMilestone.tagName,
            xmlNamespace = startMilestone.xmlNamespace,
            attributes = startMilestone.attributes,
            startPosition = startMilestone.startPosition,
            endPosition = acc.currentPos,
            startIndex = startMilestone.index,
            startParentIndex = startMilestone.parentIndex,
            endIndex = newTagIndex,
            endParentIndex = startState.parentId,
          )

          acc.copy(
            nextIndex = newTagIndex + 1,
            standoffTags = acc.standoffTags :+ freeTag,
            clixStartMilestones = acc.clixStartMilestones - eID,
          )
        } else {
          // It's an ordinary hierarchical element.
          val tag = HierarchicalStandoffTag(
            tagName = elem.label,
            xmlNamespace = Option(elem.namespace),
            attributes = xmlAttrs2StandoffAttrs(elem),
            startPosition = acc.currentPos,
            endPosition = acc.currentPos + elem.text.length,
            index = newTagIndex,
            parentIndex = startState.parentId,
            originalID = attrMap.get(XmlHierarchicalIdAttrName),
            uuid = attrMap.get(XmlHierarchicalIdAttrName) match {
              case Some(id) => id2Uuid(id)
              case None     => UUID.randomUUID
            },
          )

          // Process the element's child nodes.
          xmlNodes2Standoff(
            nodes = elem.child,
            acc.copy(
              parentId = Some(newTagIndex),
              nextIndex = newTagIndex + 1,
              standoffTags = acc.standoffTags :+ tag,
            ),
          )
        }

      case (acc, text: Text) =>
        val textData: String = text.data

        // We got an XML text node. Just skip it.
        acc.copy(
          currentPos = acc.currentPos + textData.length,
        )

      case (_, other) =>
        throw new Exception(s"Got unexpected XML node class ${other.getClass.getName}")
    }
  }

  /**
   * Recursively generates XML text representing [[IndexedStandoffTag]] objects.
   *
   * @param text              the text that has been marked up.
   * @param groupedTags       a [[Map]] of all the [[IndexedStandoffTag]] objects that refer to the text, grouped by
   *                          parent tag index.
   * @param posBeforeSiblings the last position that was processed before this method was called. If there is
   *                          any text before `siblings`, this position will be less than the position of the
   *                          first sibling..
   * @param siblings          a sequence of tags having the same parent.
   * @param xmlString         the resulting XML text.
   */
  private def standoffTags2XmlString(
    text: String,
    groupedTags: Map[Option[Int], Seq[IndexedStandoffTag]],
    posBeforeSiblings: Int,
    siblings: Seq[IndexedStandoffTag],
    writeNamespaces: Boolean = false,
    xmlString: StringBuilder,
  ): Int = {

    /**
     * Adds an optional XML namespace prefix to the name of an XML element or attribute.
     *
     * @param unprefixedName the unprefixed name of the element or attribute.
     * @param xmlNamespace   the XML namespace.
     * @return the prefixed name.
     */
    def makePrefixedXmlName(unprefixedName: String, xmlNamespace: Option[IRI]): String =
      (xmlNamespace, defaultXmlNamespace) match {
        case (Some(namespace), Some(defaultNamespace)) if namespace != defaultNamespace =>
          xmlNamespaces2Prefixes(namespace) + ":" + unprefixedName

        case (Some(namespace), None) =>
          xmlNamespaces2Prefixes(namespace) + ":" + unprefixedName

        case _ => unprefixedName
      }

    /**
     * Writes key-value pairs representing the default XML namespaces, prefixes for other XML namespaces,
     * and the attributes of an XML element.
     *
     * @param tag the tag being converted to XML.
     */
    def attributesAndNamespaces2Xml(tag: IndexedStandoffTag): Unit = {
      // If we were asked to write definitions of the default namespace and of namespace prefixes
      // (because we're writing the root element of the XML document), add them first.
      val namespacesAsXml: Vector[(String, String)] = if (writeNamespaces) {
        val maybeDefaultNamespace = defaultXmlNamespace
          .map(namespace => ("xmlns", namespace))
          .toVector

        val prefixedNamespaces = xmlNamespaces.map { case (prefix, namespace) =>
          (s"xmlns:$prefix", namespace)
        }.toVector

        maybeDefaultNamespace ++ prefixedNamespaces
      } else {
        Vector.empty[(String, String)]
      }

      // Convert any standoff attributes to XML attributes.
      val standoffAttrsAsXml: Vector[(String, String)] = tag.attributes.toVector.map { standoffAttr =>
        (makePrefixedXmlName(standoffAttr.key, standoffAttr.xmlNamespace), standoffAttr.value)
      }

      // Add an XML attribute for the standoff tag's UUID, if necessary.

      val id = uuidsToDocumentSpecificIds.get(tag.uuid) match {
        case Some(documentSpecificId) => documentSpecificId
        case None                     => if (writeBase64IDs) UuidUtil.base64Encode(tag.uuid) else tag.uuid.toString
      }

      val maybeIdAttr: Option[(String, String)] = if (writeUuidsToXml) {
        Some(XmlHierarchicalIdAttrName, id)
      } else {
        tag match {
          case splitTag: ClixMilestoneTag =>
            if (splitTag.isStartTag) {
              Some(XmlClixStartIdAttrName, id)
            } else {
              Some(XmlClixEndIdAttrName, id)
            }

          // write the original XML id back, if any
          case _ =>
            tag.originalID match {
              case Some(originalId) => Some(XmlHierarchicalIdAttrName, originalId)
              case None             => None
            }
        }
      }

      val allAttributes = namespacesAsXml ++ standoffAttrsAsXml ++ maybeIdAttr

      if (allAttributes.nonEmpty) {
        for ((attrName, attrValue) <- allAttributes) {
          xmlString
            .append(" ")
            .append(attrName)
            .append("=\"")
            .append(StringEscapeUtils.escapeXml11(attrValue))
            .append("\"")
        }

      }
    }

    // Convert each sibling standoff tag to XML.

    siblings.sortBy(_.index).foldLeft(posBeforeSiblings) { case (posBeforeTag, tag) =>
      // If there's some text between the current position and this tag, include it now.
      if (tag.startPosition > posBeforeTag) {
        xmlString.append(StringEscapeUtils.escapeXml11(text.substring(posBeforeTag, tag.startPosition)))
      }

      // Add a namespace prefix to the tag's name, if necessary.
      val prefixedTagName = makePrefixedXmlName(tag.tagName, tag.xmlNamespace)

      if (tag.endPosition > tag.startPosition) {
        // Non-empty tag
        xmlString.append(s"<$prefixedTagName")
        attributesAndNamespaces2Xml(tag)
        xmlString.append(">")

        val maybeChildren: Option[Seq[IndexedStandoffTag]] = groupedTags.get(Some(tag.index))

        val posAfterChildren = maybeChildren match {
          case Some(children) =>
            standoffTags2XmlString(
              text = text,
              groupedTags = groupedTags,
              posBeforeSiblings = tag.startPosition,
              siblings = children,
              xmlString = xmlString,
            )

          case None => tag.startPosition
        }

        // If there's some text between the last child and the closing tag, include it now.
        if (tag.endPosition > posAfterChildren) {
          xmlString.append(StringEscapeUtils.escapeXml11(text.substring(posAfterChildren, tag.endPosition)))
        }

        xmlString.append(s"</$prefixedTagName>")
      } else {
        // Does this tag have children?
        val maybeChildren: Option[Seq[IndexedStandoffTag]] = groupedTags.get(Some(tag.index))

        maybeChildren match {
          case Some(children) =>
            // Yes. Make a start tag.
            xmlString.append(s"<$prefixedTagName")
            attributesAndNamespaces2Xml(tag)
            xmlString.append(">")

            // Recurse to process the empty children.
            standoffTags2XmlString(
              text = text,
              groupedTags = groupedTags,
              posBeforeSiblings = tag.startPosition,
              siblings = children,
              xmlString = xmlString,
            )

            // Make an end tag.
            xmlString.append(s"</$prefixedTagName>")

          case None =>
            // This tag has no children.
            xmlString.append(s"<$prefixedTagName")
            attributesAndNamespaces2Xml(tag)
            xmlString.append("/>")
        }
      }

      tag.endPosition
    }
  }
}
