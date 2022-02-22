/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.v2

import akka.pattern.ask
import akka.testkit.ImplicitSender
import akka.util.Timeout
import org.knora.webapi._
import org.knora.webapi.exceptions._
import org.knora.webapi.messages.store.triplestoremessages._
import org.knora.webapi.messages.v2.responder.resourcemessages._
import org.knora.webapi.messages.v2.responder.standoffmessages._
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.models.standoffmodels.DefineStandoffMapping
import org.knora.webapi.sharedtestdata.SharedTestDataADM

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

/**
 * Tests [[StandoffResponderV2]].
 */
class StandoffResponderV2Spec extends CoreSpec() with ImplicitSender {
  private implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

  // The default timeout for receiving reply messages from actors.
  private val timeout = 30.seconds

  private def getResource(iri: String): ReadResourceV2 = {
    val msg = ResourcesGetRequestV2(
      resourceIris = List(iri),
      targetSchema = ApiV2Complex,
      featureFactoryConfig = defaultFeatureFactoryConfig,
      requestingUser = SharedTestDataADM.rootUser
    )
    responderManager ! msg
    val response = expectMsgPF(timeout) {
      case res: ReadResourcesSequenceV2 => res
      case r                            => throw AssertionException(f"Failed to get resource: $iri ($r)")
    }
    response.resources.head
  }

  private def getMapping(iri: String): SparqlConstructResponse = {

    val getMappingSparql = org.knora.webapi.messages.twirl.queries.sparql.v2.txt
      .getMapping(
        triplestore = settings.triplestoreType,
        mappingIri = iri
      )
      .toString()

    implicit val timeout: Timeout = Duration(10, SECONDS)
    val resF: Future[SparqlConstructResponse] = (storeManager ? SparqlConstructRequest(
      sparql = getMappingSparql,
      featureFactoryConfig = defaultFeatureFactoryConfig
    )).mapTo[SparqlConstructResponse]
    Await.result(resF, 10.seconds)
  }

  "The standoff responder" should {
    "create a standoff mapping" in {
      val mappingName = "customMapping"
      val mapping = DefineStandoffMapping.make(mappingName)
      val xmlContent =
        s"""<?xml version="1.0" encoding="UTF-8"?>
           |<mapping xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
           |         xsi:noNamespaceSchemaLocation="../../../webapi/src/main/resources/mappingXMLToStandoff.xsd">
           |
           |    <mappingElement>
           |        <tag>
           |            <name>text</name>
           |            <class>noClass</class>
           |            <namespace>noNamespace</namespace>
           |            <separatesWords>false</separatesWords>
           |        </tag>
           |        <standoffClass>
           |            <classIri>http://www.knora.org/ontology/standoff#StandoffRootTag</classIri>
           |            <attributes>
           |                <attribute>
           |                    <attributeName>documentType</attributeName>
           |                    <namespace>noNamespace</namespace>
           |                    <propertyIri>http://www.knora.org/ontology/standoff#standoffRootTagHasDocumentType</propertyIri>
           |                </attribute>
           |            </attributes>
           |        </standoffClass>
           |    </mappingElement>
           |
           |    <mappingElement>
           |        <tag>
           |            <name>section</name>
           |            <class>noClass</class>
           |            <namespace>noNamespace</namespace>
           |            <separatesWords>false</separatesWords>
           |        </tag>
           |        <standoffClass>
           |            <classIri>http://www.knora.org/ontology/standoff#StandoffParagraphTag</classIri>
           |        </standoffClass>
           |    </mappingElement>
           |
           |    <mappingElement>
           |        <tag>
           |            <name>italic</name>
           |            <class>noClass</class>
           |            <namespace>noNamespace</namespace>
           |            <separatesWords>false</separatesWords>
           |        </tag>
           |        <standoffClass>
           |            <classIri>http://www.knora.org/ontology/standoff#StandoffItalicTag</classIri>
           |        </standoffClass>
           |    </mappingElement>
           |
           |</mapping>
           |""".stripMargin
      val message = mapping.toMessage(
        xml = xmlContent,
        featureFactoryConfig = defaultFeatureFactoryConfig,
        user = SharedTestDataADM.rootUser
      )
      responderManager ! message
      val response = expectMsgPF(timeout) {
        case res: CreateMappingResponseV2 => res
        case _                            => throw AssertionException("Could not create a mapping")
      }

      val expectedMappingIRI = f"${mapping.projectIRI}/mappings/$mappingName"
      response.mappingIri should equal(expectedMappingIRI)
      val mappingFromDB: SparqlConstructResponse = getMapping(response.mappingIri)
      println(mappingFromDB)
      mappingFromDB.statements should not be (Map.empty)
      mappingFromDB.statements.get(expectedMappingIRI) should not be (Map.empty)
    }

  }
  // TODO: create and get text resources here
}
