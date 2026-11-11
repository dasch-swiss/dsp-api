/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.util.standoff

import org.junit.runner.RunWith
import zio.test.*

import java.util.UUID

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.GoldenTest
import org.knora.webapi.IRI
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.v2.responder.standoffmessages.*

/**
 * Pins the exact output of [[StandoffTagUtilV2.convertStandoffTagV2ToXML]]. The output is stored as
 * `knora-base:valueHasXml` and served to clients, so any change to it must fail here.
 *
 * - The attribute order of the six-attribute span in the golden is the renderer's current hash order (an attribute
 *   `Set` or `Map` of more than four entries). It is pinned on purpose.
 * - A failure means the client-visible output changed. Rewriting the golden is not enough: every stored
 *   `knora-base:valueHasXml` must be re-rendered, because the backfill skips values that carry the literal.
 */
@RunWith(classOf[DspZTestJUnitRunner])
class StandoffRendererGoldenSpec extends ZIOSpecDefault with GoldenTest {

  private implicit val sf: StringFormatter = StringFormatter.getInitializedTestInstance

  private val text =
    "First bold italic link & <less> \"quoted\" end.\nSecond event see five target."

  private val link: IRI             = OntologyConstants.KnoraBase.StandoffTagHasLink
  private val internalRef: IRI      = OntologyConstants.KnoraBase.StandoffTagHasInternalReference
  private val description: IRI      = "http://www.knora.org/ontology/0001/anything#standoffEventHasDescription"
  private def fiveProp(n: Int): IRI = s"http://www.knora.org/ontology/0001/anything#standoffFiveHasAttr$n"
  private val eventClass: IRI       = "http://www.knora.org/ontology/0001/anything#StandoffEventTag"
  private val fiveClass: IRI        = "http://www.knora.org/ontology/0001/anything#StandoffFiveTag"
  private val anchorClass: IRI      = "http://www.knora.org/ontology/0001/anything#StandoffAnchorTag"

  private def tag(
    classIri: IRI,
    index: Int,
    parent: Option[Int],
    start: Int,
    end: Int,
    dataType: Option[StandoffDataTypeClasses.Value] = None,
    originalXMLID: Option[String] = None,
    attributes: Seq[StandoffTagAttributeV2] = Seq.empty,
  ): StandoffTagV2 =
    StandoffTagV2(
      standoffTagClassIri = sf.toSmartIri(classIri),
      dataType = dataType,
      uuid = new UUID(0L, index.toLong),
      originalXMLID = originalXMLID,
      startPosition = start,
      endPosition = end,
      startIndex = index,
      startParentIndex = parent,
      attributes = attributes,
    )

  private def span(sub: String): (Int, Int) = {
    val start = text.indexOf(sub)
    (start, start + sub.length)
  }

  private val firstParagraphEnd = text.indexOf('\n')

  private val tags: Vector[StandoffTagV2] = {
    val (boldS, boldE)     = span("bold")
    val (italicS, italicE) = span("italic")
    val (linkS, linkE)     = span("link")
    val (eventS, eventE)   = span("event")
    val (refS, refE)       = span("see")
    val (fiveS, fiveE)     = span("five")
    val (targetS, targetE) = span("target")
    val fiveAttributes     = (1 to 5).map(n => StandoffTagStringAttributeV2(sf.toSmartIri(fiveProp(n)), s"v$n"))
    Vector(
      tag(OntologyConstants.Standoff.StandoffRootTag, 0, None, 0, text.length),
      tag(OntologyConstants.Standoff.StandoffParagraphTag, 1, Some(0), 0, firstParagraphEnd),
      tag(OntologyConstants.Standoff.StandoffBoldTag, 2, Some(1), boldS, boldE),
      tag(OntologyConstants.Standoff.StandoffItalicTag, 3, Some(1), italicS, italicE),
      tag(
        OntologyConstants.KnoraBase.StandoffLinkTag,
        4,
        Some(1),
        linkS,
        linkE,
        dataType = Some(StandoffDataTypeClasses.StandoffLinkTag),
        attributes = Seq(StandoffTagIriAttributeV2(sf.toSmartIri(link), "http://rdfh.ch/0001/linkTarget")),
      ),
      tag(OntologyConstants.Standoff.StandoffParagraphTag + "2", 5, Some(0), firstParagraphEnd + 1, text.length),
      tag(
        eventClass,
        6,
        Some(5),
        eventS,
        eventE,
        attributes = Seq(StandoffTagStringAttributeV2(sf.toSmartIri(description), "a \"quote\" & <tag>")),
      ),
      tag(
        OntologyConstants.KnoraBase.StandoffInternalReferenceTag,
        7,
        Some(5),
        refS,
        refE,
        dataType = Some(StandoffDataTypeClasses.StandoffInternalReferenceTag),
        attributes = Seq(StandoffTagInternalReferenceAttributeV2(sf.toSmartIri(internalRef), "target-1")),
      ),
      tag(fiveClass, 8, Some(5), fiveS, fiveE, attributes = fiveAttributes),
      tag(anchorClass, 9, Some(5), targetS, targetE, originalXMLID = Some("target-1")),
    )
  }

  private def xmlTag(
    name: String,
    classIri: IRI,
    attributes: Map[String, IRI] = Map.empty,
    dataType: Option[XMLStandoffDataTypeClass] = None,
  ): XMLTag =
    XMLTag(
      name,
      XMLTagToStandoffClass(classIri, Map("noNamespace" -> attributes).filter(_._2.nonEmpty), dataType),
      separatorRequired = false,
    )

  private val mapping: MappingXMLtoStandoff = MappingXMLtoStandoff(
    namespace = Map(
      "noNamespace" -> Map(
        "text" -> Map("noClass" -> xmlTag("text", OntologyConstants.Standoff.StandoffRootTag)),
        "p"    -> Map(
          "noClass" -> xmlTag("p", OntologyConstants.Standoff.StandoffParagraphTag),
          "second"  -> xmlTag("p", OntologyConstants.Standoff.StandoffParagraphTag + "2"),
        ),
        "strong" -> Map("noClass" -> xmlTag("strong", OntologyConstants.Standoff.StandoffBoldTag)),
        "em"     -> Map("noClass" -> xmlTag("em", OntologyConstants.Standoff.StandoffItalicTag)),
        "a"      -> Map(
          "salsah-link" -> xmlTag(
            "a",
            OntologyConstants.KnoraBase.StandoffLinkTag,
            dataType = Some(XMLStandoffDataTypeClass(StandoffDataTypeClasses.StandoffLinkTag, "href")),
          ),
          "internal" -> xmlTag(
            "a",
            OntologyConstants.KnoraBase.StandoffInternalReferenceTag,
            dataType = Some(XMLStandoffDataTypeClass(StandoffDataTypeClasses.StandoffInternalReferenceTag, "href")),
          ),
        ),
        "span" -> Map(
          "event"  -> xmlTag("span", eventClass, Map("data-description" -> description)),
          "five"   -> xmlTag("span", fiveClass, (1 to 5).map(n => s"data-attr$n" -> fiveProp(n)).toMap),
          "anchor" -> xmlTag("span", anchorClass),
        ),
      ),
    ),
    defaultXSLTransformation = None,
  )

  override def spec: Spec[Any, Any] = suite("StandoffTagUtilV2.convertStandoffTagV2ToXML")(
    test("renders the fixture byte-for-byte as the committed golden") {
      val xml = StandoffTagUtilV2.convertStandoffTagV2ToXML(text, tags, mapping)
      assertGolden(xml, "renderedXml")
    },
  )
}
