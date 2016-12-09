/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and André Fatton.
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
 */

package org.knora.webapi.util.standoff

import java.util.UUID
import javax.xml.parsers.SAXParserFactory

import com.sksamuel.diffpatch.DiffMatchPatch
import com.sksamuel.diffpatch.DiffMatchPatch._
import org.apache.commons.lang3.StringEscapeUtils
import org.knora.webapi.util.{ErrorHandlingMap, InputValidation, KnoraIdUtil}
import org.knora.webapi.{BadRequestException, IRI, InvalidStandoffException}

import scala.xml._

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
case class HierarchicalStandoffTag(uuid: UUID,
                                   tagName: String,
                                   xmlNamespace: Option[IRI] = None,
                                   attributes: Set[StandoffTagAttribute] = Set.empty[StandoffTagAttribute],
                                   startPosition: Int,
                                   endPosition: Int,
                                   index: Int,
                                   parentIndex: Option[Int] = None) extends IndexedStandoffTag

/**
  * Represents a standoff tag that does not require a hierarchical document structure, although it can be used within
  * such a structure. Its XML representation is a pair of empty elements in
  * [[http://conferences.idealliance.org/extreme/html/2004/DeRose01/EML2004DeRose01.html#t6 CLIX]] format.
  *
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
case class FreeStandoffTag(uuid: UUID,
                           tagName: String,
                           xmlNamespace: Option[IRI] = None,
                           attributes: Set[StandoffTagAttribute] = Set.empty[StandoffTagAttribute],
                           startPosition: Int,
                           endPosition: Int,
                           startIndex: Int,
                           startParentIndex: Option[Int] = None,
                           endIndex: Int,
                           endParentIndex: Option[Int] = None) extends StandoffTag

/**
  * Represents a text and its standoff markup.
  *
  * @param text     the text that has been marked up with standoff.
  * @param standoff the standoff markup.
  */
case class TextWithStandoff(text: String, standoff: Seq[StandoffTag])

/**
  * Represents a difference between two texts, a base text and a derived text.
  */
trait StandoffDiff {
    /**
      * The position in the base text where the difference starts.
      */
    def baseStartPosition: Int

    /**
      * The position in the derived text where the difference starts.
      */
    def derivedStartPosition: Int
}

/**
  * Represents a string that is in both the base text and the derived text.
  *
  * @param baseStartPosition    the start position of the string in the base text.
  * @param baseEndPosition      the end position of the string in the base text.
  * @param derivedStartPosition the start position of the string in the derived text.
  * @param derivedEndPosition   the end position of the string in the derived text.
  */
case class StandoffDiffEqual(baseStartPosition: Int,
                             baseEndPosition: Int,
                             derivedStartPosition: Int,
                             derivedEndPosition: Int) extends StandoffDiff

/**
  * Represents a string that is present in the derived text but not in the base text.
  *
  * @param baseStartPosition    the position in the base text where the string would have to be inserted to match
  *                             the derived text.
  * @param derivedStartPosition the start position of the inserted string in the derived text.
  * @param derivedEndPosition   the end position of the inserted string in the derived text.
  */
case class StandoffDiffInsert(baseStartPosition: Int,
                              derivedStartPosition: Int,
                              derivedEndPosition: Int) extends StandoffDiff

/**
  * Represents a string that is present in the base text but not in the derived text.
  *
  * @param baseStartPosition    the start position of the deleted string in the base text.
  * @param baseEndPosition      the end position of the deleted string in the base text.
  * @param derivedStartPosition the position in the derived text where the string would have to be inserted to
  *                             match the base text.
  */
case class StandoffDiffDelete(baseStartPosition: Int,
                              baseEndPosition: Int,
                              derivedStartPosition: Int) extends StandoffDiff

/**
  * Standoff-related constants.
  */
object StandoffUtil {
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
  * @param writeAllIDs         If `true` (the default), adds the ID of every standoff tag as an attribute when writing
  *                            XML. Otherwise, only the IDs of CLIX milestones are included.
  * @param writeBase64IDs      If `true`, writes UUIDs in Base64 encoding; otherwise, writes UUIDs in canonical form.
  * @param documentSpecificIDs An optional mapping between document-specific IDs and UUIDs. When reading XML,
  *                            each document-specific ID will be converted to the corresponding UUID. Elements that
  *                            don't specify an ID will be assigned a random UUID. When writing XML, each UUID will
  *                            be converted to the corresponding document-specific ID if available.
  */
class StandoffUtil(xmlNamespaces: Map[String, IRI] = Map.empty[IRI, String],
                   defaultXmlNamespace: Option[IRI] = None,
                   writeAllIDs: Boolean = true,
                   writeBase64IDs: Boolean = true,
                   documentSpecificIDs: Map[String, UUID] = Map.empty[String, UUID]) {

    import StandoffUtil._

    // Parse XML with an XML parser configured to prevent certain security risks.
    // See <https://github.com/scala/scala-xml/issues/17>.
    private val saxParserFactory = SAXParserFactory.newInstance()
    saxParserFactory.setFeature("http://xml.org/sax/features/external-general-entities", false)
    saxParserFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)

    // Computes diffs between texts.
    private val diffMatchPatch = new DiffMatchPatch

    // Encodes and decodes UUIDs in Base 64.
    private val knoraIdUtil = new KnoraIdUtil

    // A Map of UUIDs to document-specific IDs.
    private val uuidsToDocumentSpecificIds: Map[UUID, String] = documentSpecificIDs.map(_.swap)

    private val xmlNamespaces2Prefixes = new ErrorHandlingMap(
        xmlNamespaces.map(_.swap), { key: String => s"No prefix defined for XML namespace $key" }
    )

    /**
      * An empty standoff tag representing a CLIX milestone, to facilitate conversion to and from XML.
      *
      * @param isStartTag if `true`, this tag represents the start element, otherwise it represents the end element.
      */
    private case class ClixMilestoneTag(uuid: UUID,
                                        tagName: String,
                                        xmlNamespace: Option[IRI] = None,
                                        attributes: Set[StandoffTagAttribute] = Set.empty[StandoffTagAttribute],
                                        startPosition: Int,
                                        endPosition: Int,
                                        index: Int,
                                        parentIndex: Option[Int] = None,
                                        isStartTag: Boolean) extends IndexedStandoffTag

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Public methods

    /**
      * Converts an XML document to an equivalent [[TextWithStandoff]].
      *
      * @param xmlStr the XML document to be converted.
      * @return a [[TextWithStandoff]].
      */
    def xml2TextWithStandoff(xmlStr: String): TextWithStandoff = {
        val saxParser = saxParserFactory.newSAXParser()
        val nodes: Elem = XML.withSAXParser(saxParser).loadString(xmlStr)

        // TODO: make strings SPARQL safe

        // TODO: ensure that text nodes are not concatenated to one another (e.g. <p> tags)

        // TODO: add support for schema validation

        val finishedConversionState = xmlNodes2Standoff(
            nodes = nodes,
            startState = Xml2StandoffState()
        )

        if (finishedConversionState.clixStartMilestones.nonEmpty) {
            val missingEndTags = finishedConversionState.clixStartMilestones.map {
                case (startTagID, startTag) => s"<${startTag.tagName} $XmlClixStartIdAttrName=${'"'}$startTagID${'"'}>"
            }.mkString(", ")

            throw InvalidStandoffException(s"One or more CLIX milestones were not closed: $missingEndTags")
        }

        // TODO: How to unescape backslashes when the XML is recreated?
        TextWithStandoff(
            text = InputValidation.toSparqlEncodedString(nodes.text, () => throw BadRequestException("The submitted XML contains illegal characters")),
            standoff = finishedConversionState.standoffTags
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
                    uuid = freeTag.uuid,
                    tagName = freeTag.tagName,
                    xmlNamespace = freeTag.xmlNamespace,
                    attributes = freeTag.attributes,
                    startPosition = freeTag.startPosition,
                    endPosition = freeTag.startPosition,
                    index = freeTag.startIndex,
                    parentIndex = freeTag.startParentIndex,
                    isStartTag = true
                )

                val endTag = ClixMilestoneTag(
                    uuid = freeTag.uuid,
                    tagName = freeTag.tagName,
                    xmlNamespace = freeTag.xmlNamespace,
                    startPosition = freeTag.endPosition,
                    endPosition = freeTag.endPosition,
                    index = freeTag.endIndex,
                    parentIndex = freeTag.endParentIndex,
                    isStartTag = false
                )

                acc :+ startTag :+ endTag

            case (acc, hierarchicalTag: HierarchicalStandoffTag) =>
                acc :+ hierarchicalTag

            // It seems as if the following line should work, but it doesn't. See https://issues.scala-lang.org/browse/SI-10100
            // case (_, clixTag: ClixMilestoneTag) => throw InvalidStandoffException(s"CLIX tag $clixTag cannot be in TextWithStandoff") // This should never happen

            // Workaround:
            case (_, other) => throw InvalidStandoffException(s"Tag $other cannot be in TextWithStandoff") // This should never happen
        }

        val groupedTags: Map[Option[Int], Seq[IndexedStandoffTag]] = tags.groupBy(_.parentIndex)
        val stringBuilder = new StringBuilder(XmlHeader)

        // Start with the root.
        groupedTags.get(None) match {
            case Some(children) if children.size == 1 =>
                standoffTags2XmlString(
                    text = textWithStandoff.text,
                    groupedTags = groupedTags,
                    posBeforeSiblings = 0,
                    siblings = children,
                    writeNamespaces = true,
                    xmlString = stringBuilder
                )

            case Some(_) =>
                throw InvalidStandoffException("The standoff cannot be serialised to XML because it would have multiple root nodes")

            case None =>
                throw InvalidStandoffException("The standoff cannot be serialised to XML because there is no root element")
        }

        stringBuilder.toString
    }

    /**
      * Computes the differences between a base text and a derived text.
      *
      * @param baseText    the base text.
      * @param derivedText the derived text.
      * @return the differences between the two texts.
      */
    def makeStandoffDiffs(baseText: String, derivedText: String): Seq[StandoffDiff] = {
        import scala.collection.JavaConverters._

        case class DiffConversionState(standoffDiffs: Vector[StandoffDiff] = Vector.empty[StandoffDiff],
                                       basePos: Int = 0,
                                       derivedPos: Int = 0)

        val diffList = diffMatchPatch.diff_main(baseText, derivedText)
        diffMatchPatch.diff_cleanupSemantic(diffList)
        val diffs: Seq[Diff] = diffList.asScala

        val conversionResult = diffs.foldLeft(DiffConversionState()) {
            case (conversionState, diff) =>
                diff.operation match {
                    case Operation.EQUAL =>
                        val standoffDiff = StandoffDiffEqual(
                            baseStartPosition = conversionState.basePos,
                            baseEndPosition = conversionState.basePos + diff.text.length,
                            derivedStartPosition = conversionState.derivedPos,
                            derivedEndPosition = conversionState.derivedPos + diff.text.length
                        )

                        DiffConversionState(
                            standoffDiffs = conversionState.standoffDiffs :+ standoffDiff,
                            basePos = standoffDiff.baseEndPosition,
                            derivedPos = standoffDiff.derivedEndPosition
                        )

                    case Operation.DELETE =>
                        val standoffDiff = StandoffDiffDelete(
                            baseStartPosition = conversionState.basePos,
                            baseEndPosition = conversionState.basePos + diff.text.length,
                            derivedStartPosition = conversionState.derivedPos
                        )

                        DiffConversionState(
                            standoffDiffs = conversionState.standoffDiffs :+ standoffDiff,
                            basePos = standoffDiff.baseEndPosition,
                            derivedPos = conversionState.derivedPos
                        )

                    case Operation.INSERT =>
                        val standoffDiff = StandoffDiffInsert(
                            baseStartPosition = conversionState.basePos,
                            derivedStartPosition = conversionState.derivedPos,
                            derivedEndPosition = conversionState.derivedPos + diff.text.length
                        )

                        DiffConversionState(
                            standoffDiffs = conversionState.standoffDiffs :+ standoffDiff,
                            basePos = conversionState.basePos,
                            derivedPos = standoffDiff.derivedEndPosition
                        )
                }
        }

        conversionResult.standoffDiffs
    }

    /**
      * Converts standoff diffs to XML. The resulting XML has a root element called `<diff>` containing the base
      * text, along with `<del>` tags representing deletions and `<ins>` tags representing insertions.
      *
      * @param baseText      the base text that was used to calculate the diffs.
      * @param derivedText   the derived text that was used to calculate the diffs.
      * @param standoffDiffs the standoff diffs.
      * @return an XML representation of the diffs.
      */
    def standoffDiffs2Xml(baseText: String, derivedText: String, standoffDiffs: Seq[StandoffDiff]): String = {
        val stringBuilder = new StringBuilder(XmlHeader).append("<diffs>")

        for (standoffDiff <- standoffDiffs) {
            standoffDiff match {
                case equal: StandoffDiffEqual =>
                    stringBuilder.append(StringEscapeUtils.escapeXml11(baseText.substring(equal.baseStartPosition, equal.baseEndPosition)))

                case delete: StandoffDiffDelete =>
                    stringBuilder.append("<del>").append(StringEscapeUtils.escapeXml11(baseText.substring(delete.baseStartPosition, delete.baseEndPosition))).append("</del>")

                case insert: StandoffDiffInsert =>
                    stringBuilder.append("<ins>").append(StringEscapeUtils.escapeXml11(derivedText.substring(insert.derivedStartPosition, insert.derivedEndPosition))).append("</ins>")
            }
        }

        stringBuilder.append("</diffs>")
        stringBuilder.toString
    }

    /**
      * Given a set of standoff tags referring to an old version of a text and a set of standoff tags referring to a newer
      * version of the text, finds the standoff tags that have been added or removed.
      *
      * @param oldStandoff the standoff tags referring to the old version of the text.
      * @param newStandoff the standoff tags referring to the new version of the text.
      * @return a tuple containing the added standoff tags and the removed standoff tags.
      */
    def findChangedStandoffTags(oldStandoff: Seq[StandoffTag], newStandoff: Seq[StandoffTag]): (Set[StandoffTag], Set[StandoffTag]) = {
        def makeStandoffTagUuidMap(standoff: Seq[StandoffTag]): Map[UUID, StandoffTag] = standoff.map {
            tag => tag.uuid -> tag
        }.toMap

        val oldTags = makeStandoffTagUuidMap(oldStandoff)
        val oldUuids = oldTags.keySet

        val newTags = makeStandoffTagUuidMap(newStandoff)
        val newUuids = newTags.keySet

        val addedTagUuids = newUuids -- oldUuids
        val addedTags = addedTagUuids.map(uuid => newTags(uuid))

        val removedTagUuids = oldUuids -- newUuids
        val removedTags = removedTagUuids.map(uuid => oldTags(uuid))

        (addedTags, removedTags)
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
    private case class Xml2StandoffState(currentPos: Int = 0,
                                         parentId: Option[Int] = None,
                                         nextIndex: Int = 0,
                                         standoffTags: Vector[StandoffTag] = Vector.empty[StandoffTag],
                                         clixStartMilestones: Map[String, ClixMilestoneTag] = Map.empty[String, ClixMilestoneTag])

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
        def id2Uuid(id: String): UUID = {
            // If the ID was listed as a document-specific ID corresponding to an existing UUID, use that UUID.
            documentSpecificIDs.get(id) match {
                case Some(existingUuid) => existingUuid
                case None =>
                    // Otherwise, try to parse the ID as a UUID.
                    if (knoraIdUtil.couldBeUuid(id)) {
                        knoraIdUtil.decodeUuid(id)
                    } else {
                        // If the ID doesn't seem to be a UUID, replace it with a random UUID.
                        UUID.randomUUID
                    }
            }
        }

        /**
          * Converts XML attributes to standoff tag attributes, ignoring ID attributes.
          *
          * @param element the XML element containing the attributes.
          * @return the corresponding standoff tag attributes.
          */
        def xmlAttrs2StandoffAttrs(element: Elem): Set[StandoffTagAttribute] = {
            element.attributes.foldLeft(Set.empty[StandoffTagAttribute]) {
                case (acc, xmlAttr: MetaData) if !XmlIdAttrNames.contains(xmlAttr.key) => acc + StandoffTagAttribute(
                    key = xmlAttr.key,
                    xmlNamespace = Option(xmlAttr.getNamespace(element)),
                    value = xmlAttr.value.text
                )

                case (acc, _) => acc
            }
        }

        // Process sibling nodes.
        nodes.foldLeft(startState) {
            case (acc: Xml2StandoffState, elem: Elem) =>
                // We got an XML element. Generate a standoff tag for it.
                // println(s"got Elem <${elem.label}>")

                val newTagIndex = acc.nextIndex
                val attrMap = elem.attributes.asAttrMap
                val isEmptyElement = elem.text.length == 0

                if (isEmptyElement && attrMap.contains(XmlClixStartIdAttrName)) {
                    // It's a CLIX start milestone. Save it until we get the matching end milestone.

                    val sID = attrMap(XmlClixStartIdAttrName)

                    val tag = ClixMilestoneTag(
                        tagName = elem.label,
                        xmlNamespace = Option(elem.namespace),
                        attributes = xmlAttrs2StandoffAttrs(elem),
                        startPosition = acc.currentPos,
                        endPosition = acc.currentPos,
                        index = newTagIndex,
                        parentIndex = startState.parentId,
                        uuid = id2Uuid(sID),
                        isStartTag = true
                    )

                    acc.copy(
                        nextIndex = newTagIndex + 1,
                        clixStartMilestones = acc.clixStartMilestones + (sID -> tag)
                    )
                } else if (isEmptyElement && attrMap.contains(XmlClixEndIdAttrName)) {
                    // It's a CLIX end milestone. Combine it with the start milestone to make a FreeStandoffTag.

                    val eID: String = attrMap(XmlClixEndIdAttrName)

                    val startMilestone: ClixMilestoneTag = acc.clixStartMilestones.getOrElse(
                        eID,
                        throw InvalidStandoffException(s"Found a CLIX milestone with $XmlClixEndIdAttrName $eID, but there was no start milestone with that $XmlClixStartIdAttrName")
                    )

                    if (startMilestone.tagName != elem.label) {
                        throw InvalidStandoffException(s"The CLIX start milestone with $XmlClixStartIdAttrName $eID has tag name <${startMilestone.tagName}>, but the end milestone with that $XmlClixEndIdAttrName has tag name ${elem.label}")
                    }

                    if (startMilestone.xmlNamespace != Option(elem.namespace)) {
                        throw InvalidStandoffException(s"The CLIX start milestone with $XmlClixStartIdAttrName $eID is in namespace ${startMilestone.xmlNamespace}, but the end milestone with that $XmlClixEndIdAttrName is in namespace ${elem.namespace}")
                    }

                    val freeTag = FreeStandoffTag(
                        uuid = startMilestone.uuid,
                        tagName = startMilestone.tagName,
                        xmlNamespace = startMilestone.xmlNamespace,
                        attributes = startMilestone.attributes,
                        startPosition = startMilestone.startPosition,
                        endPosition = acc.currentPos,
                        startIndex = startMilestone.index,
                        startParentIndex = startMilestone.parentIndex,
                        endIndex = newTagIndex,
                        endParentIndex = startState.parentId
                    )

                    acc.copy(
                        nextIndex = newTagIndex + 1,
                        standoffTags = acc.standoffTags :+ freeTag,
                        clixStartMilestones = acc.clixStartMilestones - eID
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
                        uuid = attrMap.get(XmlHierarchicalIdAttrName) match {
                            case Some(id) => id2Uuid(id)
                            case None => UUID.randomUUID
                        }
                    )

                    // Process the element's child nodes.
                    xmlNodes2Standoff(
                        nodes = elem.child,
                        acc.copy(
                            parentId = Some(newTagIndex),
                            nextIndex = newTagIndex + 1,
                            standoffTags = acc.standoffTags :+ tag
                        )
                    )
                }

            case (acc, text: Text) =>
                // We got an XML text node. Just skip it.
                acc.copy(
                    currentPos = acc.currentPos + text.data.length
                )

            case (acc, other) =>
                throw new Exception(s"Got unexpected XML node class ${other.getClass.getName}")
        }
    }

    /**
      * Recursively generates XML text representing [[IndexedStandoffTag]] objects.
      *
      * @param text        the text that has been marked up.
      * @param groupedTags a [[Map]] of all the [[IndexedStandoffTag]] objects that refer to the text, grouped by
      *                    parent tag index.
      * @param siblings    a sequence of tags having the same parent.
      * @param xmlString   the resulting XML text.
      */
    private def standoffTags2XmlString(text: String,
                                       groupedTags: Map[Option[Int], Seq[IndexedStandoffTag]],
                                       posBeforeSiblings: Int,
                                       siblings: Seq[IndexedStandoffTag],
                                       writeNamespaces: Boolean = false,
                                       xmlString: StringBuilder): Int = {
        /**
          * Adds an optional XML namespace prefix to the name of an XML element or attribute.
          *
          * @param unprefixedName the unprefixed name of the element or attribute.
          * @param xmlNamespace   the XML namespace.
          * @return the prefixed name.
          */
        def makePrefixedXmlName(unprefixedName: String, xmlNamespace: Option[IRI]): String = {
            (xmlNamespace, defaultXmlNamespace) match {
                case (Some(namespace), Some(defaultNamespace)) if namespace != defaultNamespace =>
                    xmlNamespaces2Prefixes(namespace) + ":" + unprefixedName

                case (Some(namespace), None) =>
                    xmlNamespaces2Prefixes(namespace) + ":" + unprefixedName

                case _ => unprefixedName
            }
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
                val maybeDefaultNamespace = defaultXmlNamespace.map(
                    namespace => ("xmlns", namespace)
                ).toVector

                val prefixedNamespaces = xmlNamespaces.map {
                    case (prefix, namespace) => (s"xmlns:$prefix", namespace)
                }.toVector

                maybeDefaultNamespace ++ prefixedNamespaces
            } else {
                Vector.empty[(String, String)]
            }

            // Convert any standoff attributes to XML attributes.
            val standoffAttrsAsXml: Vector[(String, String)] = tag.attributes.toVector.map {
                standoffAttr => (makePrefixedXmlName(standoffAttr.key, standoffAttr.xmlNamespace), standoffAttr.value)
            }

            // Add an XML attribute for the standoff tag's UUID, if necessary.

            val id = uuidsToDocumentSpecificIds.get(tag.uuid) match {
                case Some(documentSpecificId) => documentSpecificId
                case None => knoraIdUtil.encodeUuid(tag.uuid, writeBase64IDs)
            }

            val maybeIdAttr: Option[(String, String)] = if (writeAllIDs) {
                Some(XmlHierarchicalIdAttrName, id)
            } else {
                tag match {
                    case splitTag: ClixMilestoneTag =>
                        if (splitTag.isStartTag) {
                            Some(XmlClixStartIdAttrName, id)
                        } else {
                            Some(XmlClixEndIdAttrName, id)
                        }

                    case _ => None
                }
            }

            val allAttributes = namespacesAsXml ++ standoffAttrsAsXml ++ maybeIdAttr

            if (allAttributes.nonEmpty) {
                for ((attrName, attrValue) <- allAttributes) {
                    xmlString.append(" ").append(attrName).append("=\"").append(attrValue).append("\"")
                }

            }
        }

        // Convert each sibling standoff tag to XML.

        siblings.sortBy(_.index).foldLeft(posBeforeSiblings) {
            case (posBeforeTag, tag) =>
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

                    val maybeChildren = groupedTags.get(Some(tag.index))

                    val posAfterChildren = maybeChildren match {
                        case Some(children) =>
                            standoffTags2XmlString(
                                text = text,
                                groupedTags = groupedTags,
                                posBeforeSiblings = tag.startPosition,
                                siblings = children,
                                xmlString = xmlString
                            )

                        case None => tag.startPosition
                    }

                    // If there's some text between the last child and the closing tag, include it now.
                    if (tag.endPosition > posAfterChildren) {
                        xmlString.append(StringEscapeUtils.escapeXml11(text.substring(posAfterChildren, tag.endPosition)))
                    }

                    xmlString.append(s"</$prefixedTagName>")
                } else {
                    // Empty tag
                    xmlString.append(s"<$prefixedTagName")
                    attributesAndNamespaces2Xml(tag)
                    xmlString.append("/>")
                }

                tag.endPosition
        }
    }
}
