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

import javax.xml.parsers.SAXParserFactory

import com.sksamuel.diffpatch.DiffMatchPatch
import com.sksamuel.diffpatch.DiffMatchPatch._

import scala.xml._

/**
  * Represents a range of characters in a text.
  */
trait StandoffRange {
    /**
      * The start position of the range.
      */
    def startPosition: Int

    /**
      * The end position of the range.
      */
    def endPosition: Int

    /**
      * The index of this range. Indexes are numbered from 0 within the context of a particular text.
      */
    def index: Int

    /**
      * The index of the standoff tag that is the parent of the range.
      */
    def parentIndex: Option[Int]
}

/**
  * Represents a range of characters containing no standoff tags.
  *
  * @param startPosition the start position of the range.
  * @param endPosition   the end position of the range.
  * @param index         he index of this range. Indexes are numbered from 0 within the context of a particular text.
  * @param parentIndex   the index of the [[StandoffTag]] that is the parent of the range.
  */
case class TextRange(startPosition: Int,
                     endPosition: Int,
                     index: Int,
                     parentIndex: Option[Int]) extends StandoffRange

/**
  * Represents a range of characters that have been marked up with a standoff tag.
  *
  * @param tagName       the name of the tag.
  * @param attributes    the attributes attached to this tag.
  * @param startPosition the start position of the range of characters marked up with this tag.
  * @param endPosition   the end position of the range of characters marked up with this tag.
  * @param index         the index of this tag. IDs are numbered from 0 within the context of a particular text.
  * @param parentIndex   the index of the [[StandoffTag]] that is the parent of this tag.
  */
case class StandoffTag(tagName: String,
                       attributes: Map[String, String] = Map.empty[String, String],
                       startPosition: Int,
                       endPosition: Int,
                       index: Int,
                       parentIndex: Option[Int]) extends StandoffRange

/**
  * Represents a text and its standoff markup.
  *
  * @param text     the text that has been marked up with standoff.
  * @param standoff the standoff markup.
  */
case class TextWithStandoff(text: String, standoff: Seq[StandoffRange])

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
}

/**
  * Converts XML documents to standoff markup and back again.
  */
class StandoffUtil {

    // Parse XML with an XML parser configured to prevent certain security risks.
    // See <https://github.com/scala/scala-xml/issues/17>.
    private val saxParserFactory = SAXParserFactory.newInstance()
    saxParserFactory.setFeature("http://xml.org/sax/features/external-general-entities", false)
    saxParserFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)

    // Computes diffs between texts.
    private val diffMatchPatch = new DiffMatchPatch

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

        val standoff = xmlNodes2StandoffRanges(
            nodes = nodes,
            startState = Xml2StandoffState()
        ).standoffRanges

        TextWithStandoff(
            text = nodes.text,
            standoff = standoff
        )
    }

    /**
      * Converts a [[TextWithStandoff]] to an equivalent XML document.
      *
      * @param textWithStandoff the [[TextWithStandoff]] to be converted.
      * @return an XML document.
      */
    def textWithStandoff2Xml(textWithStandoff: TextWithStandoff): String = {
        val groupedRanges: Map[Option[Int], Seq[StandoffRange]] = textWithStandoff.standoff.groupBy(_.parentIndex)
        val stringBuilder = new StringBuilder(StandoffUtil.XmlHeader)

        standoffRanges2XmlString(
            text = textWithStandoff.text,
            parentId = None,
            groupedRanges = groupedRanges,
            xmlString = stringBuilder
        )

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
      * @param baseText the base text that was used to calculate the diffs.
      * @param derivedText the derived text that was used to calculate the diffs.
      * @param standoffDiffs the standoff diffs.
      * @return an XML representation of the diffs.
      */
    def standoffDiffs2Xml(baseText: String, derivedText: String, standoffDiffs: Seq[StandoffDiff]): String = {
        val stringBuilder = new StringBuilder(StandoffUtil.XmlHeader).append("<diffs>")

        for (standoffDiff <- standoffDiffs) {
            standoffDiff match {
                case equal: StandoffDiffEqual =>
                    stringBuilder.append(baseText.substring(equal.baseStartPosition, equal.baseEndPosition))

                case delete: StandoffDiffDelete =>
                    stringBuilder.append("<del>").append(baseText.substring(delete.baseStartPosition, delete.baseEndPosition)).append("</del>")

                case insert: StandoffDiffInsert =>
                    stringBuilder.append("<ins>").append(derivedText.substring(insert.derivedStartPosition, insert.derivedEndPosition)).append("</ins>")
            }
        }

        stringBuilder.append("</diffs>")
        stringBuilder.toString
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Private methods

    /**
      * Represents the state of the conversion of XML text to standoff.
      *
      * @param currentPos     the current position in the text.
      * @param parentId       the ID of the parent [[StandoffTag]] for which standoff ranges are being generated, or [[None]]
      *                       if the root tag is being generated.
      * @param nextIndex      the next available standoff range index.
      * @param standoffRanges the standoff ranges generated so far.
      */
    private case class Xml2StandoffState(currentPos: Int = 0,
                                         parentId: Option[Int] = None,
                                         nextIndex: Int = 0,
                                         standoffRanges: Vector[StandoffRange] = Vector.empty[StandoffRange])

    /**
      * Recursively converts XML nodes to standoff.
      *
      * @param nodes      a sequence of sibling XML nodes to be converted.
      * @param startState the current state of the conversion.
      * @return the resulting conversion state.
      */
    private def xmlNodes2StandoffRanges(nodes: NodeSeq, startState: Xml2StandoffState): Xml2StandoffState = {
        // Process sibling nodes.
        nodes.foldLeft(startState) {
            case (acc: Xml2StandoffState, elem: Elem) =>
                // We got an XML element. Generate a StandoffTag for it.

                // println(s"got Elem <${elem.label}>")
                val standoffTagIndex = acc.nextIndex

                val standoffTag = StandoffTag(
                    tagName = elem.label,
                    attributes = elem.attributes.asAttrMap,
                    startPosition = acc.currentPos,
                    endPosition = acc.currentPos + elem.text.length,
                    index = acc.nextIndex,
                    parentIndex = startState.parentId
                )

                // Process the element's child nodes.
                xmlNodes2StandoffRanges(
                    nodes = elem.child,
                    Xml2StandoffState(
                        currentPos = acc.currentPos,
                        parentId = Some(standoffTagIndex),
                        nextIndex = acc.nextIndex + 1,
                        standoffRanges = acc.standoffRanges :+ standoffTag
                    )
                )

            case (acc, text: Text) =>
                // We got an XML text node. Generate a TextRange for it.
                val textRange = TextRange(
                    startPosition = acc.currentPos,
                    endPosition = acc.currentPos + text.data.length,
                    index = acc.nextIndex,
                    parentIndex = startState.parentId
                )

                acc.copy(
                    currentPos = textRange.endPosition,
                    parentId = startState.parentId,
                    nextIndex = acc.nextIndex + 1,
                    standoffRanges = acc.standoffRanges :+ textRange
                )

            case (acc, other) =>
                throw new Exception(s"Got unexpected XML node class ${other.getClass.getName}")
        }
    }

    /**
      * Recursively generates XML text representing [[StandoffRange]] objects, starting with those that have a particular parent tag.
      *
      * @param text          the text that has been marked up.
      * @param parentId      the ID of the parent tag.
      * @param groupedRanges a [[Map]] of all the [[StandoffRange]] objects that refer to the text, grouped by parent tag ID.
      * @param xmlString     the resulting XML text.
      */
    private def standoffRanges2XmlString(text: String, parentId: Option[Int], groupedRanges: Map[Option[Int], Seq[StandoffRange]], xmlString: StringBuilder): Unit = {
        def attributes2Xml(standoffTag: StandoffTag): Unit = {
            for ((attrName, attrValue) <- standoffTag.attributes.toVector.sortBy(_._1)) {
                xmlString.append(" ").append(attrName).append("=\"").append(attrValue).append("\"")
            }
        }

        val children = groupedRanges(parentId)

        for (child <- children.sortBy(_.index)) {
            child match {
                case standoffTag: StandoffTag =>
                    if (groupedRanges.contains(Some(standoffTag.index))) {
                        // Non-empty tag
                        xmlString.append(s"<${standoffTag.tagName}")

                        if (standoffTag.attributes.nonEmpty) {
                            attributes2Xml(standoffTag)
                        }

                        xmlString.append(">")

                        standoffRanges2XmlString(
                            text = text,
                            parentId = Some(standoffTag.index),
                            groupedRanges = groupedRanges,
                            xmlString = xmlString
                        )
                        xmlString.append(s"</${standoffTag.tagName}>")
                    } else {
                        // Empty tag
                        xmlString.append(s"<${standoffTag.tagName}")

                        if (standoffTag.attributes.nonEmpty) {
                            attributes2Xml(standoffTag)
                        }

                        xmlString.append("/>")
                    }


                case textRange: TextRange =>
                    xmlString.append(text.substring(textRange.startPosition, textRange.endPosition))
            }
        }
    }
}
