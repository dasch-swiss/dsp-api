package org.knora.webapi.util

import javax.xml.parsers.SAXParserFactory

import scala.xml._

/**
  * Provides generic data structures representing standoff markup.
  */
object StandoffUtil {
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
      * @param endPosition the end position of the range.
      * @param index he index of this range. Indexes are numbered from 0 within the context of a particular text.
      * @param parentIndex the index of the [[StandoffTag]] that is the parent of the range.
      */
    case class TextRange(startPosition: Int,
                         endPosition: Int,
                         index: Int,
                         parentIndex: Option[Int]) extends StandoffRange

    /**
      * Represents a range of characters that have been marked up with a standoff tag.
 *
      * @param tagName the name of the tag.
      * @param attributes the attributes attached to this tag.
      * @param startPosition the start position of the range of characters marked up with this tag.
      * @param endPosition the end position of the range of characters marked up with this tag.
      * @param index the index of this tag. IDs are numbered from 0 within the context of a particular text.
      * @param parentIndex the index of the [[StandoffTag]] that is the parent of this tag.
      */
    case class StandoffTag(tagName: String,
                           attributes: Map[String, String],
                           startPosition: Int,
                           endPosition: Int,
                           index: Int,
                           parentIndex: Option[Int]) extends StandoffRange

    /**
      * Represents a text and its standoff markup.
      *
      * @param text the text that has been marked up with standoff.
      * @param standoff the standoff markup.
      */
    case class TextWithStandoff(text: String, standoff: Seq[StandoffRange])
}

/**
  * Converts XML documents to standoff markup and back again.
  */
class StandoffUtil {
    import StandoffUtil._

    /**
      * Converts an XML document to an equivalent [[TextWithStandoff]].
      *
      * @param xmlStr the XML document to be converted.
      * @return a [[TextWithStandoff]].
      */
    def xml2Standoff(xmlStr: String): TextWithStandoff = {
        val nodes = SecureXml.loadString(xmlStr)

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
    def standoff2Xml(textWithStandoff: TextWithStandoff): String = {
        val groupedRanges: Map[Option[Int], Seq[StandoffRange]] = textWithStandoff.standoff.groupBy(_.parentIndex)
        val stringBuilder = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")

        standoffRanges2XmlString(
            text = textWithStandoff.text,
            parentId = None,
            groupedRanges = groupedRanges,
            xmlString = stringBuilder
        )

        stringBuilder.toString
    }

    /**
      * Represents the state of the conversion of XML text to standoff.
      *
      * @param currentPos the current position in the text.
      * @param parentId the ID of the parent [[StandoffTag]] for which standoff ranges are being generated, or [[None]]
      *                 if the root tag is being generated.
      * @param nextIndex the next available standoff range index.
      * @param standoffRanges the standoff ranges generated so far.
      */
    private case class Xml2StandoffState(currentPos: Int = 0,
                                         parentId: Option[Int] = None,
                                         nextIndex: Int = 0,
                                         standoffRanges: Vector[StandoffRange] = Vector.empty[StandoffRange])

    /**
      * Recursively converts XML nodes to standoff.
      *
      * @param nodes a sequence of sibling XML nodes to be converted.
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
      * @param text the text that has been marked up.
      * @param parentId the ID of the parent tag.
      * @param groupedRanges a [[Map]] of all the [[StandoffRange]] objects that refer to the text, grouped by parent tag ID.
      * @param xmlString the resulting XML text.
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

                        xmlString.append(" />")
                    }


                case textRange: TextRange =>
                    xmlString.append(text.substring(textRange.startPosition, textRange.endPosition))
            }
        }
    }
}

/**
  * Parses XML with an XML parser configured to prevent certain security risks.
  * See [[https://github.com/scala/scala-xml/issues/17]].
  */
object SecureXml {
    def loadString(xml: String): NodeSeq = {
        val spf = SAXParserFactory.newInstance()
        spf.setFeature("http://xml.org/sax/features/external-general-entities", false)
        spf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        val saxParser = spf.newSAXParser()
        XML.withSAXParser(saxParser).loadString(xml)
    }
}

object StandoffUtilTest extends App {
    import StandoffUtil._

    val delimiter = "\n==================================================================================================\n"

    val simpleXmlDoc =
        """<?xml version="1.0" encoding="UTF-8"?>
          |<article>
          |    <title>Special Relativity</title>
          |
          |    <paragraph>
          |        In physics, special relativity is the generally accepted and experimentally well confirmed physical
          |        theory regarding the relationship between space and time. In <person id="6789">Albert Einstein</person>'s
          |        original pedagogical treatment, it is based on two postulates:
          |
          |        <orderedList>
          |            <listItem>that the laws of physics are invariant (i.e. identical) in all inertial systems
          |                (non-accelerating frames of reference).</listItem>
          |            <listItem>that the speed of light in a vacuum is the same for all observers, regardless of the
          |                motion of the light source.</listItem>
          |        </orderedList>
          |    </paragraph>
          |
          |    <paragraph>
          |        <person id="6789">Einstein</person> originally proposed it in
          |        <date calendar="gregorian" value="1905">1905</date> in <citation id="einstein_1905a" />.
          |
          |        Here is a sentence with a sequence of empty tags: <foo /><bar /><baz />.
          |    </paragraph>
          |</article>""".stripMargin

    val standoffUtil = new StandoffUtil

    println(delimiter)
    println("Original XML document:\n\n")
    println(simpleXmlDoc)

    // Convert the XML document to standoff.
    val textWithStandoff: TextWithStandoff = standoffUtil.xml2Standoff(simpleXmlDoc)

    println()
    println(delimiter)
    println("Extracted text:\n\n")
    println(textWithStandoff.text)
    println("\n\nGenerated standoff tags:\n\n")
    println(MessageUtil.toSource(textWithStandoff.standoff))

    // Convert the standoff back to XML.
    val backToXml = standoffUtil.standoff2Xml(textWithStandoff)

    println()
    println(delimiter)
    println(s"Converted standoff back to XML:\n\n")
    println(backToXml)

    println(s"\n\nGenerated XML document is identical to original XML document: ${simpleXmlDoc == backToXml}")
}
