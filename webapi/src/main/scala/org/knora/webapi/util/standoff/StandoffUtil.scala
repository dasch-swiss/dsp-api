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
import org.knora.webapi.InvalidStandoffException
import org.knora.webapi.util.KnoraIdUtil

import scala.xml._

/**
  * Represents markup on a range of characters in a text.
  */
trait StandoffTag {
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
      * The attributes attached to this tag.
      */
    def attributes: Map[String, String]

    /**
      * The start position of the range.
      */
    def startPosition: Int

    /**
      * The end position of the range.
      */
    def endPosition: Int
}

/**
  * Represents a range of characters that have been marked up with a standoff tag.
  *
  * @param uuid             a [[UUID]] representing this tag and any other tags that
  *                         point to semantically equivalent content in other versions of the same text.
  * @param xmlIdAttrName    the name of the attribute that should contain the UUID when it is serialised to XML.
  * @param isPartOfSplitTag if `true`, this is part of a split tag, so its UUID should always be written when it is
  *                         serialised to XML.
  * @param tagName          the name of the tag.
  * @param attributes       the attributes attached to this tag.
  * @param startPosition    the start position of the range of characters marked up with this tag.
  * @param endPosition      the end position of the range of characters marked up with this tag.
  * @param index            the index of this tag. Indexes are numbered from 0 within the context of a particular text.
  * @param parentIndex      the index of the [[SimpleStandoffTag]] that is the parent of this tag.
  */
case class SimpleStandoffTag(uuid: UUID,
                             xmlIdAttrName: String = StandoffUtil.XmlIdAttrName,
                             isPartOfSplitTag: Boolean = false,
                             tagName: String,
                             attributes: Map[String, String] = Map.empty[String, String],
                             startPosition: Int,
                             endPosition: Int,
                             index: Int,
                             parentIndex: Option[Int]) extends StandoffTag

/**
  * Represent a range of characters that have been marked up with a standoff tag and that was represented
  * as two empty tags in XML to permit non-hierarchical markup. When converted back to XML, the same two empty
  * tags will be generated.
  *
  * @param uuid              a [[UUID]] representing this tag and any other tags that
  *                          point to semantically equivalent content in other versions of the same text.
  * @param tagName           the name of the tag.
  * @param attributes        the attributes attached to this tag.
  * @param startPosition     the start position of the range of characters marked up with this tag.
  * @param endPosition       the end position of the range of characters marked up with this tag.
  * @param firstIndex        the index of the first empty tag. Indexes are numbered from 0 within the context of a particular text.
  * @param firstParentIndex  the index of the [[SimpleStandoffTag]] that is the parent of the first empty tag.
  * @param secondIndex       the index of the second empty tag.
  * @param secondParentIndex the index of the [[SimpleStandoffTag]] that is the parent of the second empty tag.
  */
case class SplitStandoffTag(uuid: UUID,
                            tagName: String,
                            attributes: Map[String, String] = Map.empty[String, String],
                            startPosition: Int,
                            endPosition: Int,
                            firstIndex: Int,
                            firstParentIndex: Option[Int],
                            secondIndex: Int,
                            secondParentIndex: Option[Int]) extends StandoffTag

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


object StandoffUtil {
    private val XmlHeader = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
    val XmlIdAttrName = "id"
    val XmlStartIdAttrName = "sID"
    val XmlEndIdAttrName = "eID"
}

/**
  * Converts XML documents to standoff markup and back again.
  *
  * @param writeBase64UuidXmlAttrs if `true`, converts UUIDs to Base64 encoding when generating XML.
  * @param writeOnlyEmptyXmlTags   if `true`, only empty XML tags will be written, ensuring that non-hierarchical
  *                                markup will be converted to valid XML.
  */
class StandoffUtil(writeBase64UuidXmlAttrs: Boolean = true, writeOnlyEmptyXmlTags: Boolean = false) {

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
        val nodes = XML.withSAXParser(saxParser).loadString(xmlStr)

        val standoff = xmlNodes2StandoffTags(
            nodes = nodes,
            startState = Xml2StandoffState()
        ).standoffTags

        TextWithStandoff(
            text = nodes.text,
            standoff = standoff
        )
    }

    /**
      * Converts a [[TextWithStandoff]] to an equivalent XML document.
      *
      * @param textWithStandoff the [[TextWithStandoff]] to be converted.
      * @param includeUuids     if `true`, include the UUID of each standoff tag as an XML attribute.
      * @return an XML document.
      */
    def textWithStandoff2Xml(textWithStandoff: TextWithStandoff, includeUuids: Boolean): String = {
        // Convert non-hierarchical markup to hierarchical markup.
        val hierarchicalTags = textWithStandoff.standoff.foldLeft(Vector.empty[SimpleStandoffTag]) {
            case (acc, splitStandoffTag: SplitStandoffTag) =>
                val startTag = SimpleStandoffTag(
                    uuid = splitStandoffTag.uuid,
                    xmlIdAttrName = XmlStartIdAttrName,
                    isPartOfSplitTag = true,
                    tagName = splitStandoffTag.tagName,
                    attributes = splitStandoffTag.attributes,
                    startPosition = splitStandoffTag.startPosition,
                    endPosition = splitStandoffTag.startPosition,
                    index = splitStandoffTag.firstIndex,
                    parentIndex = splitStandoffTag.firstParentIndex
                )

                val endTag = SimpleStandoffTag(
                    uuid = splitStandoffTag.uuid,
                    xmlIdAttrName = XmlEndIdAttrName,
                    isPartOfSplitTag = true,
                    tagName = splitStandoffTag.tagName,
                    startPosition = splitStandoffTag.endPosition,
                    endPosition = splitStandoffTag.endPosition,
                    index = splitStandoffTag.secondIndex,
                    parentIndex = splitStandoffTag.secondParentIndex
                )

                acc :+ startTag :+ endTag

            case (acc, simpleStandoffTag: SimpleStandoffTag) =>
                if (writeOnlyEmptyXmlTags) {
                    val startTag = simpleStandoffTag.copy(
                        xmlIdAttrName = XmlStartIdAttrName,
                        isPartOfSplitTag = true,
                        endPosition = simpleStandoffTag.startPosition
                    )

                    val endTag = simpleStandoffTag.copy(
                        xmlIdAttrName = XmlEndIdAttrName,
                        isPartOfSplitTag = true,
                        startPosition = simpleStandoffTag.endPosition,
                        attributes = Map.empty[String, String]
                    )
                    acc :+ startTag :+ endTag

                } else {
                    acc :+ simpleStandoffTag
                }
        }

        val groupedTags: Map[Option[Int], Seq[SimpleStandoffTag]] = hierarchicalTags.groupBy(_.parentIndex)
        val stringBuilder = new StringBuilder(StandoffUtil.XmlHeader)

        // Start with the root.
        groupedTags.get(None) match {
            case Some(children) if children.size == 1 =>
                standoffTags2XmlString(
                    text = textWithStandoff.text,
                    groupedTags = groupedTags,
                    posBeforeSiblings = 0,
                    siblings = children,
                    xmlString = stringBuilder,
                    includeUuids
                )

            case Some(children) =>
                throw InvalidStandoffException("The standoff cannot be serialised to XML because it would have multiple root nodes")

            case None => ()
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
        import scala.collection.JavaConversions._

        case class DiffConversionState(standoffDiffs: Vector[StandoffDiff] = Vector.empty[StandoffDiff],
                                       basePos: Int = 0,
                                       derivedPos: Int = 0)

        val diffList = diffMatchPatch.diff_main(baseText, derivedText)
        diffMatchPatch.diff_cleanupSemantic(diffList)
        val diffs: Seq[Diff] = diffList

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
        val stringBuilder = new StringBuilder(StandoffUtil.XmlHeader).append("<diffs>")

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
      * Given the standoff ranges in an old version of a text and the standoff tags in a newer version of the text,
      * finds the UUIDs of the standoff tags that have been added or removed.
      *
      * @param oldStandoff the standoff tags in the old version of the text.
      * @param newStandoff the standoff tags in the new version of the text.
      * @return a tuple containing the UUIDs of the added standoff tags and the UUIDs of the removed standoff tags.
      */
    def findChangedStandoffTags(oldStandoff: Seq[StandoffTag], newStandoff: Seq[StandoffTag]): (Set[UUID], Set[UUID]) = {
        def makeStandoffTagUuidSet(standoff: Seq[StandoffTag]): Set[UUID] = {
            standoff.foldLeft(Set.empty[UUID]) {
                case (acc, standoffTag: SimpleStandoffTag) => acc + standoffTag.uuid
                case (acc, _) => acc
            }
        }

        val oldStandoffTagUuids = makeStandoffTagUuidSet(oldStandoff)
        val newStandoffTagUuids = makeStandoffTagUuidSet(newStandoff)

        val addedTagUuids = newStandoffTagUuids -- oldStandoffTagUuids
        val removedTagUuids = oldStandoffTagUuids -- newStandoffTagUuids

        (addedTagUuids, removedTagUuids)
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Private methods

    /**
      * Represents the state of the conversion of XML text to standoff.
      *
      * @param currentPos   the current position in the text.
      * @param parentId     the ID of the parent [[SimpleStandoffTag]], or [[None]] if the root tag is being generated.
      * @param nextIndex    the next available standoff tag index.
      * @param standoffTags the standoff tags generated so far.
      */
    private case class Xml2StandoffState(currentPos: Int = 0,
                                         parentId: Option[Int] = None,
                                         nextIndex: Int = 0,
                                         standoffTags: Vector[StandoffTag] = Vector.empty[StandoffTag],
                                         emptyStartTags: Map[String, SimpleStandoffTag] = Map.empty[String, SimpleStandoffTag])

    /**
      * Recursively converts XML nodes to standoff.
      *
      * @param nodes      a sequence of sibling XML nodes to be converted.
      * @param startState the current state of the conversion.
      * @return the resulting conversion state.
      */
    private def xmlNodes2StandoffTags(nodes: NodeSeq, startState: Xml2StandoffState): Xml2StandoffState = {
        // Process sibling nodes.
        nodes.foldLeft(startState) {
            case (acc: Xml2StandoffState, elem: Elem) =>
                // We got an XML element. Generate a standoff tag for it.
                // println(s"got Elem <${elem.label}>")

                val newTagIndex = acc.nextIndex
                val attrMap = elem.attributes.asAttrMap
                val isEmptyTag = elem.text.length == 0

                if (isEmptyTag && attrMap.contains(XmlStartIdAttrName)) {
                    // It's the first part of a split tag. Save it until we get the second part.
                    val sID = attrMap(XmlStartIdAttrName)

                    val uuid = if (knoraIdUtil.couldBeUuid(sID)) {
                        knoraIdUtil.decodeUuid(sID)
                    } else {
                        UUID.randomUUID
                    }

                    val standoffTag = SimpleStandoffTag(
                        tagName = elem.label,
                        attributes = attrMap - XmlStartIdAttrName,
                        startPosition = acc.currentPos,
                        endPosition = acc.currentPos,
                        index = newTagIndex,
                        parentIndex = startState.parentId,
                        uuid = uuid
                    )

                    acc.copy(
                        nextIndex = newTagIndex + 1,
                        emptyStartTags = acc.emptyStartTags + (sID -> standoffTag)
                    )
                } else if (isEmptyTag && attrMap.contains(XmlEndIdAttrName)) {
                    // It's the second part of a split tag. Combine it with the first part.
                    val eID = attrMap(XmlEndIdAttrName)
                    val firstPart = acc.emptyStartTags.getOrElse(eID, throw InvalidStandoffException(s"No empty start tag found to match the empty end tag with ID $eID"))

                    if (firstPart.tagName != elem.label) {
                        throw new InvalidStandoffException(s"The empty start tag with ID '$eID' has tag name '${firstPart.tagName}', but the empty end tag with the same ID has tag name ${elem.label}")
                    }

                    val splitStandoffTag = SplitStandoffTag(
                        uuid = firstPart.uuid,
                        tagName = firstPart.tagName,
                        attributes = firstPart.attributes,
                        startPosition = firstPart.startPosition,
                        endPosition = acc.currentPos,
                        firstIndex = firstPart.index,
                        firstParentIndex = firstPart.parentIndex,
                        secondIndex = newTagIndex,
                        secondParentIndex = startState.parentId
                    )

                    acc.copy(
                        nextIndex = newTagIndex + 1,
                        standoffTags = acc.standoffTags :+ splitStandoffTag,
                        emptyStartTags = acc.emptyStartTags - eID
                    )
                } else {
                    // It's an ordinary hierarchical element.
                    val standoffTag = SimpleStandoffTag(
                        tagName = elem.label,
                        attributes = attrMap - XmlIdAttrName,
                        startPosition = acc.currentPos,
                        endPosition = acc.currentPos + elem.text.length,
                        index = newTagIndex,
                        parentIndex = startState.parentId,
                        uuid = attrMap.get(XmlIdAttrName) match {
                            case Some(uuidStr) => knoraIdUtil.decodeUuid(uuidStr)
                            case None => UUID.randomUUID
                        }
                    )

                    // Process the element's child nodes.
                    xmlNodes2StandoffTags(
                        nodes = elem.child,
                        acc.copy(
                            parentId = Some(newTagIndex),
                            nextIndex = newTagIndex + 1,
                            standoffTags = acc.standoffTags :+ standoffTag
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
      * Recursively generates XML text representing [[StandoffTag]] objects.
      *
      * @param text         the text that has been marked up.
      * @param groupedTags  a [[Map]] of all the [[StandoffTag]] objects that refer to the text, grouped by parent tag ID.
      * @param siblings     a sequence of tags having the same parent.
      * @param xmlString    the resulting XML text.
      * @param includeUuids if `true`, include each tag's UUID as an XML attribute.
      */
    private def standoffTags2XmlString(text: String,
                                       groupedTags: Map[Option[Int], Seq[SimpleStandoffTag]],
                                       posBeforeSiblings: Int,
                                       siblings: Seq[SimpleStandoffTag],
                                       xmlString: StringBuilder,
                                       includeUuids: Boolean): Int = {
        def attributes2Xml(standoffTag: SimpleStandoffTag): Unit = {
            val maybeUuid: Option[(String, String)] = if (includeUuids || standoffTag.isPartOfSplitTag) {
                Some(standoffTag.xmlIdAttrName, knoraIdUtil.encodeUuid(standoffTag.uuid, writeBase64UuidXmlAttrs))
            } else {
                None
            }

            val attributesWithUuid = standoffTag.attributes.toVector.sortBy(_._1) ++ maybeUuid

            if (attributesWithUuid.nonEmpty) {
                for ((attrName, attrValue) <- attributesWithUuid) {
                    xmlString.append(" ").append(attrName).append("=\"").append(attrValue).append("\"")
                }

            }
        }

        siblings.sortBy(_.index).foldLeft(posBeforeSiblings) {
            case (posBeforeTag, standoffTag) =>
                if (standoffTag.startPosition > posBeforeTag) {
                    xmlString.append(StringEscapeUtils.escapeXml11(text.substring(posBeforeTag, standoffTag.startPosition)))
                }

                if (standoffTag.endPosition > standoffTag.startPosition) {
                    // Non-empty tag
                    xmlString.append(s"<${standoffTag.tagName}")
                    attributes2Xml(standoffTag)
                    xmlString.append(">")

                    val maybeChildren = groupedTags.get(Some(standoffTag.index))

                    val posAfterChildren = maybeChildren match {
                        case Some(children) =>
                            standoffTags2XmlString(
                                text = text,
                                groupedTags = groupedTags,
                                posBeforeSiblings = standoffTag.startPosition,
                                siblings = children,
                                xmlString = xmlString,
                                includeUuids = includeUuids
                            )

                        case None => standoffTag.startPosition
                    }

                    if (standoffTag.endPosition > posAfterChildren) {
                        xmlString.append(StringEscapeUtils.escapeXml11(text.substring(posAfterChildren, standoffTag.endPosition)))
                    }

                    xmlString.append(s"</${standoffTag.tagName}>")
                } else {
                    // Empty tag
                    xmlString.append(s"<${standoffTag.tagName}")
                    attributes2Xml(standoffTag)
                    xmlString.append("/>")
                }

                standoffTag.endPosition
        }
    }
}
