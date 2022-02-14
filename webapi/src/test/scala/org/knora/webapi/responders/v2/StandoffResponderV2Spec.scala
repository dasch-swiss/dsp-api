/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.v2

import akka.actor.{ActorRef, Props}
import akka.testkit.ImplicitSender
import org.knora.webapi._
import org.knora.webapi.app.ApplicationActor
import org.knora.webapi.exceptions._
import org.knora.webapi.messages.IriConversions._
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.store.triplestoremessages._
import org.knora.webapi.messages.util.rdf.SparqlSelectResult
import org.knora.webapi.messages.util.search.gravsearch.GravsearchParser
import org.knora.webapi.messages.util.{
  CalendarNameGregorian,
  DatePrecisionYear,
  KnoraSystemInstances,
  PermissionUtilADM
}
import org.knora.webapi.messages.v2.responder._
import org.knora.webapi.messages.v2.responder.resourcemessages._
import org.knora.webapi.messages.v2.responder.searchmessages.GravsearchRequestV2
import org.knora.webapi.messages.v2.responder.standoffmessages._
import org.knora.webapi.messages.v2.responder.valuemessages._
import org.knora.webapi.messages.{OntologyConstants, SmartIri, StringFormatter}
import org.knora.webapi.models.filemodels.{ChangeFileRequest, FileType}
import org.knora.webapi.models.standoffmodels.DefineStandoffMapping
import org.knora.webapi.settings._
import org.knora.webapi.sharedtestdata.SharedTestDataADM
import org.knora.webapi.store.iiif.MockSipiConnector
import org.knora.webapi.util.MutableTestIri

import java.time.Instant
import java.util.UUID
import scala.concurrent.Future
import scala.concurrent.duration._

/**
 * Tests [[ValuesResponderV2]].
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
      case r => {
        println(r)
        throw AssertionException("Something went wrong")
      }
    }
    response.resources.head
  }

//  "Load test data" in {
//    responderManager ! GetMappingRequestV2(
//      mappingIri = "http://rdfh.ch/standoff/mappings/StandardMapping",
//      featureFactoryConfig = defaultFeatureFactoryConfig,
//      requestingUser = KnoraSystemInstances.Users.SystemUser
//    )
//
//    expectMsgPF(timeout) { case mappingResponse: GetMappingResponseV2 =>
//      standardMapping = Some(mappingResponse.mapping)
//    }
//  }

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
//      val mappingFromDB = getResource(response.mappingIri)
//      println(mappingFromDB)
      println()

//      // Add the value.
//
//      val resourceIri: IRI = aThingIri
//      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri
//      val intValue = 4
//      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUser1)
//
//      responderManager ! CreateValueRequestV2(
//        CreateValueV2(
//          resourceIri = resourceIri,
//          resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
//          propertyIri = propertyIri,
//          valueContent = IntegerValueContentV2(
//            ontologySchema = ApiV2Complex,
//            valueHasInteger = intValue
//          )
//        ),
//        featureFactoryConfig = defaultFeatureFactoryConfig,
//        requestingUser = anythingUser1,
//        apiRequestID = UUID.randomUUID
//      )
//
//      expectMsgPF(timeout) { case createValueResponse: CreateValueResponseV2 =>
//        intValueIri.set(createValueResponse.valueIri)
//        firstIntValueVersionIri.set(createValueResponse.valueIri)
//        integerValueUUID = createValueResponse.valueUUID
//      }
//
//      // Read the value back to check that it was added correctly.
//
//      val valueFromTriplestore = getValue(
//        resourceIri = resourceIri,
//        maybePreviousLastModDate = maybeResourceLastModDate,
//        propertyIriForGravsearch = propertyIri,
//        propertyIriInResult = propertyIri,
//        expectedValueIri = intValueIri.get,
//        requestingUser = anythingUser1
//      )
//
//      valueFromTriplestore.valueContent match {
//        case savedValue: IntegerValueContentV2 => savedValue.valueHasInteger should ===(intValue)
//        case _                                 => throw AssertionException(s"Expected integer value, got $valueFromTriplestore")
//      }
    }

  }
}
