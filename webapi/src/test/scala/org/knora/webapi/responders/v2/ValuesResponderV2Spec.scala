/*
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
 */

package org.knora.webapi.responders.v2

import java.util.UUID

import akka.actor.Props
import akka.testkit.{ImplicitSender, TestActorRef}
import org.knora.webapi.SharedTestDataADM._
import org.knora.webapi._
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.store.triplestoremessages._
import org.knora.webapi.messages.v2.responder.SuccessResponseV2
import org.knora.webapi.messages.v2.responder.ontologymessages._
import org.knora.webapi.messages.v2.responder.resourcemessages.{ReadResourceV2, ReadResourcesSequenceV2}
import org.knora.webapi.messages.v2.responder.searchmessages.GravsearchRequestV2
import org.knora.webapi.messages.v2.responder.standoffmessages.{GetMappingRequestV2, GetMappingResponseV2, MappingXMLtoStandoff, XMLTag}
import org.knora.webapi.messages.v2.responder.valuemessages._
import org.knora.webapi.responders._
import org.knora.webapi.store.{STORE_MANAGER_ACTOR_NAME, StoreManager}
import org.knora.webapi.twirl.StandoffTagV2
import org.knora.webapi.util.IriConversions._
import org.knora.webapi.util.search.gravsearch.GravsearchParser
import org.knora.webapi.util.{MutableTestIri, SmartIri, StringFormatter}

import scala.concurrent.duration._

/**
  * Tests [[ValuesResponderV2]].
  */
class ValuesResponderV2Spec extends CoreSpec() with ImplicitSender {
    private implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    private val incunabulaProjectIri = INCUNABULA_PROJECT_IRI
    private val anythingProjectIri = ANYTHING_PROJECT_IRI

    private val zeitglöckleinIri = "http://rdfh.ch/c5058f3a"
    private val miscResourceIri = "http://rdfh.ch/miscResource"
    private val aThingIri = "http://rdfh.ch/0001/a-thing"

    private val incunabulaUser = SharedTestDataADM.incunabulaMemberUser
    private val imagesUser = SharedTestDataADM.imagesUser01
    private val anythingUser = SharedTestDataADM.anythingUser1

    val rdfDataObjects = List(
        RdfDataObject(path = "_test_data/responders.v2.ValuesResponderV2Spec/incunabula-data.ttl", name = "http://www.knora.org/data/0803/incunabula"),
        RdfDataObject(path = "_test_data/demo_data/images-demo-data.ttl", name = "http://www.knora.org/data/00FF/images"),
        RdfDataObject(path = "_test_data/all_data/anything-data.ttl", name = "http://www.knora.org/data/0001/anything")
    )

    private val actorUnderTest = TestActorRef[ValuesResponderV2]
    private val responderManager = system.actorOf(Props(new ResponderManager with LiveActorMaker), name = RESPONDER_MANAGER_ACTOR_NAME)
    private val storeManager = system.actorOf(Props(new StoreManager with LiveActorMaker), name = STORE_MANAGER_ACTOR_NAME)

    // The default timeout for receiving reply messages from actors.
    private val timeout = 30.seconds

    private val seqnumValueIri = new MutableTestIri
    private val commentValueIri = new MutableTestIri

    private val sampleStandoff: Vector[StandoffTagV2] = Vector(
        StandoffTagV2(
            standoffTagClassIri = OntologyConstants.Standoff.StandoffBoldTag,
            startPosition = 0,
            endPosition = 7,
            uuid = UUID.randomUUID().toString,
            originalXMLID = None,
            startIndex = 0
        ),
        StandoffTagV2(
            standoffTagClassIri = OntologyConstants.Standoff.StandoffParagraphTag,
            startPosition = 0,
            endPosition = 10,
            uuid = UUID.randomUUID().toString,
            originalXMLID = None,
            startIndex = 1
        )
    )

    private var standardMapping: Option[MappingXMLtoStandoff] = None

    private def getValue(resourceIri: IRI, propertyIri: SmartIri, expectedValueIri: IRI, requestingUser: UserADM): ReadValueV2 = {
        // Make a Gravsearch query from a template.
        val gravsearchQuery: String = queries.gravsearch.txt.getResourceWithSpecifiedProperties(
            resourceIri = resourceIri,
            propertyIris = Seq(propertyIri)
        ).toString()

        // Run the query.

        val parsedGravsearchQuery = GravsearchParser.parseQuery(gravsearchQuery)
        responderManager ! GravsearchRequestV2(parsedGravsearchQuery, requestingUser)

        expectMsgPF(timeout) {
            case searchResponse: ReadResourcesSequenceV2 =>
                // Get the resource from the response.
                val resource = resourcesSequenceToResource(
                    requestedResourceIri = resourceIri,
                    readResourcesSequence = searchResponse,
                    requestingUser = requestingUser
                )

                val propertyValues = resource.values.getOrElse(propertyIri, throw AssertionException(s"Resource <$resourceIri> does not have property <$propertyIri>"))
                propertyValues.find(_.valueIri == expectedValueIri).getOrElse(throw AssertionException(s"Property <$propertyIri> of resource <$resourceIri> does not have value <$expectedValueIri>"))
        }
    }

    private def resourcesSequenceToResource(requestedResourceIri: IRI, readResourcesSequence: ReadResourcesSequenceV2, requestingUser: UserADM): ReadResourceV2 = {
        if (readResourcesSequence.numberOfResources == 0) {
            throw AssertionException(s"Expected one resource, <$requestedResourceIri>, but no resources were returned")
        }

        if (readResourcesSequence.numberOfResources > 1) {
            throw AssertionException(s"More than one resource returned with IRI <$requestedResourceIri>")
        }

        val resourceInfo = readResourcesSequence.resources.head

        if (resourceInfo.resourceIri == SearchResponderV2Constants.forbiddenResourceIri) {
            throw ForbiddenException(s"User ${requestingUser.email} does not have permission to view resource <${resourceInfo.resourceIri}>")
        }

        resourceInfo.toOntologySchema(ApiV2WithValueObjects)
    }

    "Load test data" in {
        storeManager ! ResetTriplestoreContent(rdfDataObjects)
        expectMsg(300.seconds, ResetTriplestoreContentACK())

        responderManager ! LoadOntologiesRequestV2(KnoraSystemInstances.Users.SystemUser)
        expectMsgType[SuccessResponseV2](timeout)

        responderManager ! GetMappingRequestV2(mappingIri = "http://rdfh.ch/standoff/mappings/StandardMapping", requestingUser = KnoraSystemInstances.Users.SystemUser)

        expectMsgPF(timeout) {
            case mappingResponse: GetMappingResponseV2 =>
                standardMapping = Some(mappingResponse.mapping)
        }
    }

    "The values responder" should {
        "add a new integer value (seqnum of a page)" in {
            // Add the value.

            val resourceIri = "http://rdfh.ch/8a0b1e75"
            val propertyIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#seqnum".toSmartIri
            val seqnum = 4

            actorUnderTest ! CreateValueRequestV2(
                CreateValueV2(
                    resourceIri = resourceIri,
                    propertyIri = propertyIri,
                    valueContent = IntegerValueContentV2(
                        ontologySchema = ApiV2WithValueObjects,
                        valueHasInteger = seqnum
                    )
                ),
                requestingUser = incunabulaUser,
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case createValueResponse: CreateValueResponseV2 => seqnumValueIri.set(createValueResponse.valueIri)
            }

            // Read the value back to check that it was added correctly.

            val valueFromTriplestore = getValue(
                resourceIri = resourceIri,
                propertyIri = propertyIri,
                expectedValueIri = seqnumValueIri.get,
                requestingUser = incunabulaUser
            )

            valueFromTriplestore.valueContent match {
                case intValue: IntegerValueContentV2 => intValue.valueHasInteger should ===(seqnum)
                case _ => throw AssertionException(s"Expected integer value, got $valueFromTriplestore")
            }
        }

        "add a new text value without standoff" in {
            val valueHasString = "Comment 1a"
            val propertyIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book_comment".toSmartIri

            actorUnderTest ! CreateValueRequestV2(
                CreateValueV2(
                    resourceIri = zeitglöckleinIri,
                    propertyIri = propertyIri,
                    valueContent = TextValueContentV2(
                        ontologySchema = ApiV2WithValueObjects,
                        valueHasString = valueHasString
                    )
                ),
                requestingUser = incunabulaUser,
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case createValueResponse: CreateValueResponseV2 => commentValueIri.set(createValueResponse.valueIri)
            }

            // Read the value back to check that it was added correctly.

            val valueFromTriplestore = getValue(
                resourceIri = zeitglöckleinIri,
                propertyIri = propertyIri,
                expectedValueIri = commentValueIri.get,
                requestingUser = incunabulaUser
            )

            valueFromTriplestore.valueContent match {
                case textValue: TextValueContentV2 => textValue.valueHasString should ===(valueHasString)
                case _ => throw AssertionException(s"Expected text value, got $valueFromTriplestore")
            }
        }

        "add a new text value with standoff" in {

            val valueHasString = "Comment 1aa"

            val standoffAndMapping = Some(StandoffAndMapping(
                standoff = sampleStandoff,
                mappingIri = "http://rdfh.ch/standoff/mappings/StandardMapping",
                mapping = standardMapping.get
            ))

            val propertyIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book_comment".toSmartIri

            actorUnderTest ! CreateValueRequestV2(
                CreateValueV2(
                    resourceIri = zeitglöckleinIri,
                    propertyIri = propertyIri,
                    valueContent = TextValueContentV2(
                        ontologySchema = ApiV2WithValueObjects,
                        valueHasString = valueHasString,
                        standoffAndMapping = standoffAndMapping
                    )
                ),
                requestingUser = incunabulaUser,
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case createValueResponse: CreateValueResponseV2 => commentValueIri.set(createValueResponse.valueIri)
            }

            // Read the value back to check that it was added correctly.

            val valueFromTriplestore = getValue(
                resourceIri = zeitglöckleinIri,
                propertyIri = propertyIri,
                expectedValueIri = commentValueIri.get,
                requestingUser = incunabulaUser
            )

            valueFromTriplestore.valueContent match {
                case textValue: TextValueContentV2 =>
                    textValue.valueHasString should ===(valueHasString)
                    textValue.standoffAndMapping should ===(standoffAndMapping)

                case _ => throw AssertionException(s"Expected text value, got $valueFromTriplestore")
            }
        }
    }
}
