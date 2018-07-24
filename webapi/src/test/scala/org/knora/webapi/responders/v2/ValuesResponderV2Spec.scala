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

import java.time.Instant
import java.util.UUID

import akka.actor.Props
import akka.testkit.{ImplicitSender, TestActorRef}
import org.knora.webapi.SharedTestDataADM._
import org.knora.webapi._
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.store.triplestoremessages._
import org.knora.webapi.messages.v1.responder.valuemessages.{KnoraCalendarV1, KnoraPrecisionV1}
import org.knora.webapi.messages.v2.responder.SuccessResponseV2
import org.knora.webapi.messages.v2.responder.ontologymessages._
import org.knora.webapi.messages.v2.responder.resourcemessages.{ReadResourceV2, ReadResourcesSequenceV2, ResourcesPreviewGetRequestV2}
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

    private val intValueIri = new MutableTestIri
    private val commentValueIri = new MutableTestIri
    private val decimalValueIri = new MutableTestIri
    private val dateValueIri = new MutableTestIri
    private val booleanValueIri = new MutableTestIri
    private val geometryValueIri = new MutableTestIri
    private val intervalValueIri = new MutableTestIri
    private val listValueIri = new MutableTestIri
    private val colorValueIri = new MutableTestIri
    private val uriValueIri = new MutableTestIri
    private val geonameValueIri = new MutableTestIri
    private val linkValueIri = new MutableTestIri

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

    private def getValue(resourceIri: IRI,
                         maybePreviousLastModDate: Option[Instant],
                         propertyIriForGravsearch: SmartIri,
                         propertyIriInResult: SmartIri,
                         expectedValueIri: IRI,
                         requestingUser: UserADM): ReadValueV2 = {
        // Make a Gravsearch query from a template.
        val gravsearchQuery: String = queries.gravsearch.txt.getResourceWithSpecifiedProperties(
            resourceIri = resourceIri,
            propertyIris = Seq(propertyIriForGravsearch)
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

                resource.lastModificationDate match {
                    case Some(updatedLastModDate) =>
                        maybePreviousLastModDate match {
                            case Some(previousLastModDate) => assert(updatedLastModDate.isAfter(previousLastModDate))
                            case None => ()
                        }

                    case None => throw AssertionException(s"Resource $resourceIri has no knora-base:lastModificationDate")
                }

                val propertyValues = resource.values.getOrElse(propertyIriInResult, throw AssertionException(s"Resource <$resourceIri> does not have property <$propertyIriInResult>"))
                propertyValues.find(_.valueIri == expectedValueIri).getOrElse(throw AssertionException(s"Property <$propertyIriInResult> of resource <$resourceIri> does not have value <$expectedValueIri>"))
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

    private def getResourceLastModificationDate(resourceIri: IRI, requestingUser: UserADM): Option[Instant] = {
        responderManager ! ResourcesPreviewGetRequestV2(resourceIris = Seq(resourceIri), requestingUser = requestingUser)

        expectMsgPF(timeout) {
            case previewResponse: ReadResourcesSequenceV2 =>
                val resourcePreview: ReadResourceV2 = resourcesSequenceToResource(
                    requestedResourceIri = resourceIri,
                    readResourcesSequence = previewResponse,
                    requestingUser = requestingUser
                )

                resourcePreview.lastModificationDate
        }
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
        "create an integer value" in {
            // Add the value.

            val resourceIri = "http://rdfh.ch/8a0b1e75"
            val propertyIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#seqnum".toSmartIri
            val seqnum = 4
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, incunabulaUser)

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
                case createValueResponse: CreateValueResponseV2 => intValueIri.set(createValueResponse.valueIri)
            }

            // Read the value back to check that it was added correctly.

            val valueFromTriplestore = getValue(
                resourceIri = resourceIri,
                maybePreviousLastModDate = maybeResourceLastModDate,
                propertyIriForGravsearch = propertyIri,
                propertyIriInResult = propertyIri,
                expectedValueIri = intValueIri.get,
                requestingUser = incunabulaUser
            )

            valueFromTriplestore.valueContent match {
                case savedValue: IntegerValueContentV2 => savedValue.valueHasInteger should ===(seqnum)
                case _ => throw AssertionException(s"Expected integer value, got $valueFromTriplestore")
            }
        }

        "create a text value without standoff" in {
            val valueHasString = "Comment 1a"
            val propertyIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book_comment".toSmartIri
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(zeitglöckleinIri, incunabulaUser)

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
                maybePreviousLastModDate = maybeResourceLastModDate,
                propertyIriForGravsearch = propertyIri,
                propertyIriInResult = propertyIri,
                expectedValueIri = commentValueIri.get,
                requestingUser = incunabulaUser
            )

            valueFromTriplestore.valueContent match {
                case savedValue: TextValueContentV2 => savedValue.valueHasString should ===(valueHasString)
                case _ => throw AssertionException(s"Expected text value, got $valueFromTriplestore")
            }
        }

        "create a text value with standoff" in {

            val valueHasString = "Comment 1aa"

            val standoffAndMapping = Some(StandoffAndMapping(
                standoff = sampleStandoff,
                mappingIri = "http://rdfh.ch/standoff/mappings/StandardMapping",
                mapping = standardMapping.get
            ))

            val propertyIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book_comment".toSmartIri
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(zeitglöckleinIri, incunabulaUser)

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
                maybePreviousLastModDate = maybeResourceLastModDate,
                propertyIriForGravsearch = propertyIri,
                propertyIriInResult = propertyIri,
                expectedValueIri = commentValueIri.get,
                requestingUser = incunabulaUser
            )

            valueFromTriplestore.valueContent match {
                case savedValue: TextValueContentV2 =>
                    savedValue.valueHasString should ===(valueHasString)
                    savedValue.standoffAndMapping should ===(standoffAndMapping)

                case _ => throw AssertionException(s"Expected text value, got $valueFromTriplestore")
            }
        }

        "create a decimal value" in {
            // Add the value.

            val resourceIri = "http://rdfh.ch/0001/a-thing"
            val propertyIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasDecimal".toSmartIri
            val valueHasDecimal = 4.3
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, incunabulaUser)

            actorUnderTest ! CreateValueRequestV2(
                CreateValueV2(
                    resourceIri = resourceIri,
                    propertyIri = propertyIri,
                    valueContent = DecimalValueContentV2(
                        ontologySchema = ApiV2WithValueObjects,
                        valueHasDecimal = valueHasDecimal
                    )
                ),
                requestingUser = anythingUser,
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case createValueResponse: CreateValueResponseV2 => decimalValueIri.set(createValueResponse.valueIri)
            }

            // Read the value back to check that it was added correctly.

            val valueFromTriplestore = getValue(
                resourceIri = resourceIri,
                maybePreviousLastModDate = maybeResourceLastModDate,
                propertyIriForGravsearch = propertyIri,
                propertyIriInResult = propertyIri,
                expectedValueIri = decimalValueIri.get,
                requestingUser = anythingUser
            )

            valueFromTriplestore.valueContent match {
                case savedValue: DecimalValueContentV2 => savedValue.valueHasDecimal should ===(valueHasDecimal)
                case _ => throw AssertionException(s"Expected decimal value, got $valueFromTriplestore")
            }
        }

        "create a date value" in {
            // Add the value.

            val resourceIri = "http://rdfh.ch/0001/a-thing"
            val propertyIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasDate".toSmartIri
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, incunabulaUser)

            val submittedValueContent = DateValueContentV2(
                ontologySchema = ApiV2WithValueObjects,
                valueHasCalendar = KnoraCalendarV1.GREGORIAN,
                valueHasStartJDN = 2264907,
                valueHasStartPrecision = KnoraPrecisionV1.YEAR,
                valueHasEndJDN = 2265271,
                valueHasEndPrecision = KnoraPrecisionV1.YEAR
            )

            actorUnderTest ! CreateValueRequestV2(
                CreateValueV2(
                    resourceIri = resourceIri,
                    propertyIri = propertyIri,
                    valueContent = submittedValueContent
                ),
                requestingUser = anythingUser,
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case createValueResponse: CreateValueResponseV2 => dateValueIri.set(createValueResponse.valueIri)
            }

            // Read the value back to check that it was added correctly.

            val valueFromTriplestore = getValue(
                resourceIri = resourceIri,
                maybePreviousLastModDate = maybeResourceLastModDate,
                propertyIriForGravsearch = propertyIri,
                propertyIriInResult = propertyIri,
                expectedValueIri = dateValueIri.get,
                requestingUser = anythingUser
            )

            valueFromTriplestore.valueContent match {
                case savedValue: DateValueContentV2 =>
                    savedValue.valueHasCalendar should ===(submittedValueContent.valueHasCalendar)
                    savedValue.valueHasStartJDN should ===(submittedValueContent.valueHasStartJDN)
                    savedValue.valueHasStartPrecision should ===(submittedValueContent.valueHasStartPrecision)
                    savedValue.valueHasEndJDN should ===(submittedValueContent.valueHasEndJDN)
                    savedValue.valueHasEndPrecision should ===(submittedValueContent.valueHasEndPrecision)

                case _ => throw AssertionException(s"Expected date value, got $valueFromTriplestore")
            }
        }

        "create a boolean value" in {
            // Add the value.

            val resourceIri = "http://rdfh.ch/0001/a-thing"
            val propertyIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasBoolean".toSmartIri
            val valueHasBoolean = true
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, incunabulaUser)

            actorUnderTest ! CreateValueRequestV2(
                CreateValueV2(
                    resourceIri = resourceIri,
                    propertyIri = propertyIri,
                    valueContent = BooleanValueContentV2(
                        ontologySchema = ApiV2WithValueObjects,
                        valueHasBoolean = valueHasBoolean
                    )
                ),
                requestingUser = anythingUser,
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case createValueResponse: CreateValueResponseV2 => booleanValueIri.set(createValueResponse.valueIri)
            }

            // Read the value back to check that it was added correctly.

            val valueFromTriplestore = getValue(
                resourceIri = resourceIri,
                maybePreviousLastModDate = maybeResourceLastModDate,
                propertyIriForGravsearch = propertyIri,
                propertyIriInResult = propertyIri,
                expectedValueIri = booleanValueIri.get,
                requestingUser = anythingUser
            )

            valueFromTriplestore.valueContent match {
                case savedValue: BooleanValueContentV2 => savedValue.valueHasBoolean should ===(valueHasBoolean)
                case _ => throw AssertionException(s"Expected boolean value, got $valueFromTriplestore")
            }
        }

        "create a geometry value" in {
            // Add the value.

            val resourceIri = "http://rdfh.ch/0001/a-thing"
            val propertyIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasGeometry".toSmartIri
            val valueHasGeometry = """{"status":"active","lineColor":"#ff3333","lineWidth":2,"points":[{"x":0.08098591549295775,"y":0.16741071428571427},{"x":0.7394366197183099,"y":0.7299107142857143}],"type":"rectangle","original_index":0}"""
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, incunabulaUser)

            actorUnderTest ! CreateValueRequestV2(
                CreateValueV2(
                    resourceIri = resourceIri,
                    propertyIri = propertyIri,
                    valueContent = GeomValueContentV2(
                        ontologySchema = ApiV2WithValueObjects,
                        valueHasGeometry = valueHasGeometry
                    )
                ),
                requestingUser = anythingUser,
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case createValueResponse: CreateValueResponseV2 => geometryValueIri.set(createValueResponse.valueIri)
            }

            // Read the value back to check that it was added correctly.

            val valueFromTriplestore = getValue(
                resourceIri = resourceIri,
                maybePreviousLastModDate = maybeResourceLastModDate,
                propertyIriForGravsearch = propertyIri,
                propertyIriInResult = propertyIri,
                expectedValueIri = geometryValueIri.get,
                requestingUser = anythingUser
            )

            valueFromTriplestore.valueContent match {
                case savedValue: GeomValueContentV2 => savedValue.valueHasGeometry should ===(valueHasGeometry)
                case _ => throw AssertionException(s"Expected geometry value, got $valueFromTriplestore")
            }
        }

        "create an interval value" in {
            // Add the value.

            val resourceIri = "http://rdfh.ch/0001/a-thing"
            val propertyIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInterval".toSmartIri
            val valueHasIntervalStart = BigDecimal("1.2")
            val valueHasIntervalEnd = BigDecimal("3.4")
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, incunabulaUser)

            actorUnderTest ! CreateValueRequestV2(
                CreateValueV2(
                    resourceIri = resourceIri,
                    propertyIri = propertyIri,
                    valueContent = IntervalValueContentV2(
                        ontologySchema = ApiV2WithValueObjects,
                        valueHasIntervalStart = valueHasIntervalStart,
                        valueHasIntervalEnd = valueHasIntervalEnd
                    )
                ),
                requestingUser = anythingUser,
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case createValueResponse: CreateValueResponseV2 => intervalValueIri.set(createValueResponse.valueIri)
            }

            // Read the value back to check that it was added correctly.

            val valueFromTriplestore = getValue(
                resourceIri = resourceIri,
                maybePreviousLastModDate = maybeResourceLastModDate,
                propertyIriForGravsearch = propertyIri,
                propertyIriInResult = propertyIri,
                expectedValueIri = intervalValueIri.get,
                requestingUser = anythingUser
            )

            valueFromTriplestore.valueContent match {
                case savedValue: IntervalValueContentV2 =>
                    savedValue.valueHasIntervalStart should ===(valueHasIntervalStart)
                    savedValue.valueHasIntervalEnd should ===(valueHasIntervalEnd)

                case _ => throw AssertionException(s"Expected interval value, got $valueFromTriplestore")
            }
        }

        "create a list value" in {
            // Add the value.

            val resourceIri = "http://rdfh.ch/0001/a-thing"
            val propertyIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasListItem".toSmartIri
            val valueHasListNode = "http://rdfh.ch/lists/0001/treeList03"
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, incunabulaUser)

            actorUnderTest ! CreateValueRequestV2(
                CreateValueV2(
                    resourceIri = resourceIri,
                    propertyIri = propertyIri,
                    valueContent = HierarchicalListValueContentV2(
                        ontologySchema = ApiV2WithValueObjects,
                        valueHasListNode = valueHasListNode
                    )
                ),
                requestingUser = anythingUser,
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case createValueResponse: CreateValueResponseV2 => listValueIri.set(createValueResponse.valueIri)
            }

            // Read the value back to check that it was added correctly.

            val valueFromTriplestore = getValue(
                resourceIri = resourceIri,
                maybePreviousLastModDate = maybeResourceLastModDate,
                propertyIriForGravsearch = propertyIri,
                propertyIriInResult = propertyIri,
                expectedValueIri = listValueIri.get,
                requestingUser = anythingUser
            )

            valueFromTriplestore.valueContent match {
                case savedValue: HierarchicalListValueContentV2 =>
                    savedValue.valueHasListNode should ===(valueHasListNode)

                case _ => throw AssertionException(s"Expected list value, got $valueFromTriplestore")
            }
        }

        "create a color value" in {
            // Add the value.

            val resourceIri = "http://rdfh.ch/0001/a-thing"
            val propertyIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasColor".toSmartIri
            val valueHasColor = "#ff3333"
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, incunabulaUser)

            actorUnderTest ! CreateValueRequestV2(
                CreateValueV2(
                    resourceIri = resourceIri,
                    propertyIri = propertyIri,
                    valueContent = ColorValueContentV2(
                        ontologySchema = ApiV2WithValueObjects,
                        valueHasColor = valueHasColor
                    )
                ),
                requestingUser = anythingUser,
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case createValueResponse: CreateValueResponseV2 => colorValueIri.set(createValueResponse.valueIri)
            }

            // Read the value back to check that it was added correctly.

            val valueFromTriplestore = getValue(
                resourceIri = resourceIri,
                maybePreviousLastModDate = maybeResourceLastModDate,
                propertyIriForGravsearch = propertyIri,
                propertyIriInResult = propertyIri,
                expectedValueIri = colorValueIri.get,
                requestingUser = anythingUser
            )

            valueFromTriplestore.valueContent match {
                case savedValue: ColorValueContentV2 =>
                    savedValue.valueHasColor should ===(valueHasColor)

                case _ => throw AssertionException(s"Expected color value, got $valueFromTriplestore")
            }
        }

        "create a URI value" in {
            // Add the value.

            val resourceIri = "http://rdfh.ch/0001/a-thing"
            val propertyIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasUri".toSmartIri
            val valueHasUri = "https://www.knora.org"
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, incunabulaUser)

            actorUnderTest ! CreateValueRequestV2(
                CreateValueV2(
                    resourceIri = resourceIri,
                    propertyIri = propertyIri,
                    valueContent = UriValueContentV2(
                        ontologySchema = ApiV2WithValueObjects,
                        valueHasUri = valueHasUri
                    )
                ),
                requestingUser = anythingUser,
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case createValueResponse: CreateValueResponseV2 => uriValueIri.set(createValueResponse.valueIri)
            }

            // Read the value back to check that it was added correctly.

            val valueFromTriplestore = getValue(
                resourceIri = resourceIri,
                maybePreviousLastModDate = maybeResourceLastModDate,
                propertyIriForGravsearch = propertyIri,
                propertyIriInResult = propertyIri,
                expectedValueIri = uriValueIri.get,
                requestingUser = anythingUser
            )

            valueFromTriplestore.valueContent match {
                case savedValue: UriValueContentV2 =>
                    savedValue.valueHasUri should ===(valueHasUri)

                case _ => throw AssertionException(s"Expected URI value, got $valueFromTriplestore")
            }
        }

        "create a geoname value" in {
            // Add the value.

            val resourceIri = "http://rdfh.ch/0001/a-thing"
            val propertyIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasGeoname".toSmartIri
            val valueHasGeonameCode = "2661604"
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, incunabulaUser)

            actorUnderTest ! CreateValueRequestV2(
                CreateValueV2(
                    resourceIri = resourceIri,
                    propertyIri = propertyIri,
                    valueContent = GeonameValueContentV2(
                        ontologySchema = ApiV2WithValueObjects,
                        valueHasGeonameCode = valueHasGeonameCode
                    )
                ),
                requestingUser = anythingUser,
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case createValueResponse: CreateValueResponseV2 => geonameValueIri.set(createValueResponse.valueIri)
            }

            // Read the value back to check that it was added correctly.

            val valueFromTriplestore = getValue(
                resourceIri = resourceIri,
                maybePreviousLastModDate = maybeResourceLastModDate,
                propertyIriForGravsearch = propertyIri,
                propertyIriInResult = propertyIri,
                expectedValueIri = geonameValueIri.get,
                requestingUser = anythingUser
            )

            valueFromTriplestore.valueContent match {
                case savedValue: GeonameValueContentV2 =>
                    savedValue.valueHasGeonameCode should ===(valueHasGeonameCode)

                case _ => throw AssertionException(s"Expected GeoNames value, got $valueFromTriplestore")
            }
        }

        "create a link between two resources" in {
            val resourceIri = "http://rdfh.ch/cb1a74e3e2f6"
            val linkPropertyIri = OntologyConstants.KnoraApiV2WithValueObjects.HasLinkTo.toSmartIri
            val linkValuePropertyIri = OntologyConstants.KnoraApiV2WithValueObjects.HasLinkToValue.toSmartIri
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, incunabulaUser)

            val createValueRequest = CreateValueRequestV2(
                CreateValueV2(
                    resourceIri = resourceIri,
                    propertyIri = linkValuePropertyIri,
                    valueContent = LinkValueContentV2(
                        ontologySchema = ApiV2WithValueObjects,
                        subject = resourceIri,
                        predicate = linkPropertyIri,
                        target = zeitglöckleinIri
                    )
                ),
                requestingUser = incunabulaUser,
                apiRequestID = UUID.randomUUID
            )

            actorUnderTest ! createValueRequest

            expectMsgPF(timeout) {
                case createValueResponse: CreateValueResponseV2 => linkValueIri.set(createValueResponse.valueIri)
            }

            val valueFromTriplestore = getValue(
                resourceIri = resourceIri,
                maybePreviousLastModDate = maybeResourceLastModDate,
                propertyIriForGravsearch = linkPropertyIri,
                propertyIriInResult = linkValuePropertyIri,
                expectedValueIri = linkValueIri.get,
                requestingUser = incunabulaUser
            )

            valueFromTriplestore.valueContent match {
                case savedValue: LinkValueContentV2 =>
                    savedValue.subject should ===(resourceIri)
                    savedValue.predicate should ===(linkPropertyIri)
                    savedValue.target should ===(zeitglöckleinIri)
                    valueFromTriplestore.valueHasRefCount should ===(Some(1))

                case _ => throw AssertionException(s"Expected link value, got $valueFromTriplestore")
            }
        }

        "not accept a link property in a request to create a link between two resources" in {
            val resourceIri = "http://rdfh.ch/cb1a74e3e2f6"
            val linkPropertyIri = OntologyConstants.KnoraApiV2WithValueObjects.HasLinkTo.toSmartIri

            val createValueRequest = CreateValueRequestV2(
                CreateValueV2(
                    resourceIri = resourceIri,
                    propertyIri = linkPropertyIri,
                    valueContent = LinkValueContentV2(
                        ontologySchema = ApiV2WithValueObjects,
                        subject = resourceIri,
                        predicate = linkPropertyIri,
                        target = zeitglöckleinIri
                    )
                ),
                requestingUser = incunabulaUser,
                apiRequestID = UUID.randomUUID
            )

            actorUnderTest ! createValueRequest

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[BadRequestException] should ===(true)
            }
        }

        "not create a link value with a link value property as its predicate" in {
            val resourceIri = "http://rdfh.ch/cb1a74e3e2f6"
            val linkValuePropertyIri = OntologyConstants.KnoraApiV2WithValueObjects.HasLinkToValue.toSmartIri

            val createValueRequest = CreateValueRequestV2(
                CreateValueV2(
                    resourceIri = resourceIri,
                    propertyIri = linkValuePropertyIri,
                    valueContent = LinkValueContentV2(
                        ontologySchema = ApiV2WithValueObjects,
                        subject = resourceIri,
                        predicate = linkValuePropertyIri,
                        target = zeitglöckleinIri
                    )
                ),
                requestingUser = incunabulaUser,
                apiRequestID = UUID.randomUUID
            )

            actorUnderTest ! createValueRequest

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[BadRequestException] should ===(true)
            }
        }
    }
}
