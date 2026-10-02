/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.service

import org.junit.runner.RunWith
import zio.*
import zio.test.*

import java.util.UUID

import dsp.errors.InconsistentRepositoryDataException
import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.IRI
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.store.triplestoremessages.LiteralV2
import org.knora.webapi.messages.util.standoff.StandoffTagUtilV2
import org.knora.webapi.messages.v2.responder.ontologymessages.StandoffEntityInfoGetResponseV2
import org.knora.webapi.messages.v2.responder.standoffmessages.*
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.common.StandoffMappingIri
import org.knora.webapi.slice.standoff.service.StandoffMappingService

@RunWith(classOf[DspZTestJUnitRunner])
class ValueHasXmlRendererLiveSpec extends ZIOSpecDefault {

  private implicit val sf: StringFormatter = StringFormatter.getInitializedTestInstance

  private val text = "Hello"

  private val mapping: MappingXMLtoStandoff = MappingXMLtoStandoff(
    namespace = Map(
      "noNamespace" -> Map(
        "text" -> Map(
          "noClass" -> XMLTag(
            "text",
            XMLTagToStandoffClass(OntologyConstants.Standoff.StandoffRootTag, dataType = None),
            separatorRequired = false,
          ),
        ),
      ),
    ),
    defaultXSLTransformation = None,
  )

  private val rootTag: StandoffTagV2 = StandoffTagV2(
    standoffTagClassIri = sf.toSmartIri(OntologyConstants.Standoff.StandoffRootTag),
    uuid = UUID.fromString("12345678-90ab-cdef-1234-567890abcdef"),
    originalXMLID = None,
    startPosition = 0,
    endPosition = text.length,
    startIndex = 0,
  )

  private def tagUtil(tags: Vector[StandoffTagV2]): StandoffTagUtilV2 = new StandoffTagUtilV2 {
    override def createStandoffTagsV2FromConstructResults(
      standoffAssertions: Map[IRI, Map[SmartIri, LiteralV2]],
      requestingUser: User,
    ): Task[Vector[StandoffTagV2]] = ZIO.die(new NotImplementedError)

    override def createStandoffTagsV2FromSelectResults(
      standoffAssertions: Map[IRI, Map[IRI, String]],
      requestingUser: User,
    ): Task[Vector[StandoffTagV2]] = ZIO.succeed(tags)
  }

  private val mappingService: StandoffMappingService = new StandoffMappingService {
    override def getMappingV2(mappingIri: StandoffMappingIri): Task[GetMappingResponseV2] =
      ZIO.succeed(GetMappingResponseV2(mappingIri, mapping, StandoffEntityInfoGetResponseV2(Map.empty, Map.empty)))

    override def getXSLTransformation(xslTransformationIri: IRI): Task[String] = ZIO.die(new NotImplementedError)

    override def getStandoffEntitiesFromMappingV2(
      mappingXMLtoStandoff: MappingXMLtoStandoff,
    ): Task[StandoffEntityInfoGetResponseV2] = ZIO.die(new NotImplementedError)
  }

  private def renderer(tags: Vector[StandoffTagV2]) = ValueHasXmlRendererLive(mappingService, tagUtil(tags))

  private def value(mappingIri: IRI = OntologyConstants.KnoraBase.StandardMapping) =
    StoredTextValue("http://rdfh.ch/0001/v1", text, mappingIri, None, Map("http://rdfh.ch/0001/v1/n1" -> Map.empty))

  override def spec: Spec[Any, Any] =
    suite("ValueHasXmlRendererLive")(
      test("render returns None for a value without standoff tags") {
        renderer(Vector.empty).render(value(), mapping).map(xml => assertTrue(xml.isEmpty))
      },
      test("render returns the XML of a value covered by the mapping") {
        val expected = StandoffTagUtilV2.convertStandoffTagV2ToXML(text, Vector(rootTag), mapping)
        renderer(Vector(rootTag)).render(value(), mapping).map(xml => assertTrue(xml.contains(expected)))
      },
      test("render returns None for an unformatted text value, which must not carry valueHasXml") {
        val unformatted = value().copy(textValueType = Some(OntologyConstants.KnoraBase.UnformattedText))
        renderer(Vector(rootTag)).render(unformatted, mapping).map(xml => assertTrue(xml.isEmpty))
      },
      test("loadMapping returns the mapping of the service") {
        renderer(Vector.empty)
          .loadMapping(OntologyConstants.KnoraBase.StandardMapping)
          .map(loaded => assertTrue(loaded == mapping))
      },
      test("loadMapping fails with InconsistentRepositoryDataException for a malformed mapping IRI") {
        renderer(Vector.empty).loadMapping("not-a-mapping-iri").exit.map { exit =>
          assertTrue(
            exit.causeOption.flatMap(_.failureOption).exists(_.isInstanceOf[InconsistentRepositoryDataException]),
          )
        }
      },
    )
}
