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

import akka.testkit.{ImplicitSender, TestActorRef}
import org.knora.webapi._
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.store.triplestoremessages._
import org.knora.webapi.messages.v2.responder._
import org.knora.webapi.messages.v2.responder.resourcemessages.{ReadResourceV2, ReadResourcesSequenceV2, ResourcesPreviewGetRequestV2}
import org.knora.webapi.messages.v2.responder.searchmessages.GravsearchRequestV2
import org.knora.webapi.messages.v2.responder.standoffmessages._
import org.knora.webapi.messages.v2.responder.valuemessages._
import org.knora.webapi.twirl.{StandoffTagIriAttributeV2, StandoffTagV2}
import org.knora.webapi.util.IriConversions._
import org.knora.webapi.util.date.{CalendarNameGregorian, DatePrecisionYear}
import org.knora.webapi.util.search.gravsearch.GravsearchParser
import org.knora.webapi.util.{MutableTestIri, PermissionUtilADM, SmartIri, StringFormatter}

import scala.concurrent.duration._

/**
  * Tests [[ValuesResponderV2]].
  */
class ValuesResponderV2Spec extends CoreSpec() with ImplicitSender {
    private implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    private val zeitglöckleinIri = "http://rdfh.ch/c5058f3a"
    private val generationeIri = "http://rdfh.ch/c3f913666f"
    private val aThingIri = "http://rdfh.ch/0001/a-thing"
    private val aThingPictureIri = "http://rdfh.ch/0001/a-thing-picture"

    private val incunabulaUser = SharedTestDataADM.incunabulaMemberUser
    private val incunabulaCreatorUser = SharedTestDataADM.incunabulaCreatorUser
    private val anythingUser1 = SharedTestDataADM.anythingUser1
    private val anythingUser2 = SharedTestDataADM.anythingUser2

    override lazy val rdfDataObjects = List(
        RdfDataObject(path = "_test_data/responders.v2.ValuesResponderV2Spec/incunabula-data.ttl", name = "http://www.knora.org/data/0803/incunabula"),
        RdfDataObject(path = "_test_data/demo_data/images-demo-data.ttl", name = "http://www.knora.org/data/00FF/images"),
        RdfDataObject(path = "_test_data/all_data/anything-data.ttl", name = "http://www.knora.org/data/0001/anything")
    )

    private val actorUnderTest = TestActorRef[ValuesResponderV2]

    // The default timeout for receiving reply messages from actors.
    private val timeout = 30.seconds

    private val firstIntValueVersionIri = new MutableTestIri
    private val intValueIri = new MutableTestIri
    private val intValueIriWithCustomPermissions = new MutableTestIri
    private val zeitglöckleinCommentWithoutStandoffIri = new MutableTestIri
    private val zeitglöckleinCommentWithStandoffIri = new MutableTestIri
    private val zeitglöckleinCommentWithCommentIri = new MutableTestIri
    private val zeitglöckleinSecondCommentWithStandoffIri = new MutableTestIri
    private val lobComment1Iri = new MutableTestIri
    private val lobComment2Iri = new MutableTestIri
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
    private val standoffLinkValueIri = new MutableTestIri
    private val stillImageFileValueIri = new MutableTestIri

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

    private val sampleStandoffModified: Vector[StandoffTagV2] = Vector(
        StandoffTagV2(
            standoffTagClassIri = OntologyConstants.Standoff.StandoffBoldTag,
            startPosition = 1,
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

    private val sampleStandoffWithLink: Vector[StandoffTagV2] = Vector(
        StandoffTagV2(
            standoffTagClassIri = OntologyConstants.KnoraBase.StandoffLinkTag,
            dataType = Some(StandoffDataTypeClasses.StandoffLinkTag),
            startPosition = 0,
            endPosition = 7,
            uuid = UUID.randomUUID().toString,
            originalXMLID = None,
            startIndex = 0,
            attributes = Vector(StandoffTagIriAttributeV2(standoffPropertyIri = OntologyConstants.KnoraBase.StandoffTagHasLink, value = aThingIri)),
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

    private def getResourceWithValues(resourceIri: IRI,
                                      propertyIrisForGravsearch: Seq[SmartIri],
                                      requestingUser: UserADM): ReadResourceV2 = {
        // Make a Gravsearch query from a template.
        val gravsearchQuery: String = queries.gravsearch.txt.getResourceWithSpecifiedProperties(
            resourceIri = resourceIri,
            propertyIris = propertyIrisForGravsearch
        ).toString()

        // Run the query.

        val parsedGravsearchQuery = GravsearchParser.parseQuery(gravsearchQuery)
        responderManager ! GravsearchRequestV2(parsedGravsearchQuery, requestingUser)

        expectMsgPF(timeout) {
            case searchResponse: ReadResourcesSequenceV2 =>
                // Get the resource from the response.
                resourcesSequenceToResource(
                    requestedresourceIri = resourceIri,
                    readResourcesSequence = searchResponse,
                    requestingUser = requestingUser
                )
        }
    }

    private def getValuesFromResource(resource: ReadResourceV2,
                                      propertyIriInResult: SmartIri): Seq[ReadValueV2] = {
        resource.values.getOrElse(propertyIriInResult, throw AssertionException(s"Resource <${resource.resourceIri}> does not have property <$propertyIriInResult>"))
    }

    private def getValueFromResource(resource: ReadResourceV2,
                                     propertyIriInResult: SmartIri,
                                     expectedValueIri: IRI): ReadValueV2 = {
        val propertyValues: Seq[ReadValueV2] = getValuesFromResource(resource = resource, propertyIriInResult = propertyIriInResult)
        propertyValues.find(_.valueIri == expectedValueIri).getOrElse(throw AssertionException(s"Property <$propertyIriInResult> of resource <${resource.resourceIri}> does not have value <$expectedValueIri>"))
    }

    private def checkValueIsDeleted(resourceIri: IRI,
                                    maybePreviousLastModDate: Option[Instant],
                                    propertyIriForGravsearch: SmartIri,
                                    propertyIriInResult: SmartIri,
                                    valueIri: IRI,
                                    requestingUser: UserADM): Unit = {
        val resource = getResourceWithValues(
            resourceIri = resourceIri,
            propertyIrisForGravsearch = Seq(propertyIriForGravsearch),
            requestingUser = requestingUser
        )

        checkLastModDate(
            resourceIri = resourceIri,
            maybePreviousLastModDate = maybePreviousLastModDate,
            maybeUpdatedLastModDate = resource.lastModificationDate
        )

        val propertyValues: Seq[ReadValueV2] = getValuesFromResource(resource = resource, propertyIriInResult = propertyIriInResult)

        propertyValues.find(_.valueIri == valueIri) match {
            case Some(_) => throw AssertionException(s"Value <$valueIri was not deleted>")
            case None => ()
        }
    }

    private def checkLastModDate(resourceIri: IRI, maybePreviousLastModDate: Option[Instant], maybeUpdatedLastModDate: Option[Instant]): Unit = {
        maybeUpdatedLastModDate match {
            case Some(updatedLastModDate) =>
                maybePreviousLastModDate match {
                    case Some(previousLastModDate) => assert(updatedLastModDate.isAfter(previousLastModDate))
                    case None => ()
                }

            case None => throw AssertionException(s"Resource $resourceIri has no knora-base:lastModificationDate")
        }
    }

    private def getValue(resourceIri: IRI,
                         maybePreviousLastModDate: Option[Instant],
                         propertyIriForGravsearch: SmartIri,
                         propertyIriInResult: SmartIri,
                         expectedValueIri: IRI,
                         requestingUser: UserADM,
                         checkLastModDateChanged: Boolean = true): ReadValueV2 = {
        val resource = getResourceWithValues(
            resourceIri = resourceIri,
            propertyIrisForGravsearch = Seq(propertyIriForGravsearch),
            requestingUser = requestingUser
        )

        if (checkLastModDateChanged) {
            checkLastModDate(
                resourceIri = resourceIri,
                maybePreviousLastModDate = maybePreviousLastModDate,
                maybeUpdatedLastModDate = resource.lastModificationDate
            )
        }

        getValueFromResource(
            resource = resource,
            propertyIriInResult = propertyIriInResult,
            expectedValueIri = expectedValueIri
        )
    }

    private def resourcesSequenceToResource(requestedresourceIri: IRI, readResourcesSequence: ReadResourcesSequenceV2, requestingUser: UserADM): ReadResourceV2 = {
        if (readResourcesSequence.numberOfResources == 0) {
            throw AssertionException(s"Expected one resource, <$requestedresourceIri>, but no resources were returned")
        }

        if (readResourcesSequence.numberOfResources > 1) {
            throw AssertionException(s"More than one resource returned with IRI <$requestedresourceIri>")
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
                    requestedresourceIri = resourceIri,
                    readResourcesSequence = previewResponse,
                    requestingUser = requestingUser
                )

                resourcePreview.lastModificationDate
        }
    }

    "Load test data" in {
        responderManager ! GetMappingRequestV2(mappingIri = "http://rdfh.ch/standoff/mappings/StandardMapping", requestingUser = KnoraSystemInstances.Users.SystemUser)

        expectMsgPF(timeout) {
            case mappingResponse: GetMappingResponseV2 =>
                standardMapping = Some(mappingResponse.mapping)
        }
    }

    "The values responder" should {
        "create an integer value" in {
            // Add the value.

            val resourceIri: IRI = aThingIri
            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri
            val intValue = 4
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUser1)

            actorUnderTest ! CreateValueRequestV2(
                CreateValueV2(
                    resourceIri = resourceIri,
                    resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
                    propertyIri = propertyIri,
                    valueContent = IntegerValueContentV2(
                        ontologySchema = ApiV2WithValueObjects,
                        valueHasInteger = intValue
                    )
                ),
                requestingUser = anythingUser1,
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case createValueResponse: CreateValueResponseV2 =>
                    intValueIri.set(createValueResponse.valueIri)
                    firstIntValueVersionIri.set(createValueResponse.valueIri)
            }

            // Read the value back to check that it was added correctly.

            val valueFromTriplestore = getValue(
                resourceIri = resourceIri,
                maybePreviousLastModDate = maybeResourceLastModDate,
                propertyIriForGravsearch = propertyIri,
                propertyIriInResult = propertyIri,
                expectedValueIri = intValueIri.get,
                requestingUser = anythingUser1
            )

            valueFromTriplestore.valueContent match {
                case savedValue: IntegerValueContentV2 => savedValue.valueHasInteger should ===(intValue)
                case _ => throw AssertionException(s"Expected integer value, got $valueFromTriplestore")
            }
        }

        "create an integer value with custom permissions" in {
            // Add the value.

            val resourceIri: IRI = aThingIri
            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri
            val intValue = 1
            val permissions = "CR knora-base:Creator|V http://rdfh.ch/groups/0001/thing-searcher"
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUser1)

            actorUnderTest ! CreateValueRequestV2(
                CreateValueV2(
                    resourceIri = resourceIri,
                    resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
                    propertyIri = propertyIri,
                    valueContent = IntegerValueContentV2(
                        ontologySchema = ApiV2WithValueObjects,
                        valueHasInteger = intValue
                    ),
                    permissions = Some(permissions)
                ),
                requestingUser = anythingUser1,
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case createValueResponse: CreateValueResponseV2 => intValueIriWithCustomPermissions.set(createValueResponse.valueIri)
            }

            // Read the value back to check that it was added correctly.

            val valueFromTriplestore = getValue(
                resourceIri = resourceIri,
                maybePreviousLastModDate = maybeResourceLastModDate,
                propertyIriForGravsearch = propertyIri,
                propertyIriInResult = propertyIri,
                expectedValueIri = intValueIriWithCustomPermissions.get,
                requestingUser = anythingUser1
            )

            valueFromTriplestore.valueContent match {
                case savedValue: IntegerValueContentV2 =>
                    savedValue.valueHasInteger should ===(intValue)
                    PermissionUtilADM.parsePermissions(valueFromTriplestore.permissions) should ===(PermissionUtilADM.parsePermissions(permissions))

                case _ => throw AssertionException(s"Expected integer value, got $valueFromTriplestore")
            }
        }

        "not create an integer value with syntactically invalid custom permissions" in {
            val resourceIri: IRI = aThingIri
            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri
            val intValue = 1024
            val permissions = "M knora-base:Creator,V knora-base:KnownUser"

            actorUnderTest ! CreateValueRequestV2(
                CreateValueV2(
                    resourceIri = resourceIri,
                    resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
                    propertyIri = propertyIri,
                    valueContent = IntegerValueContentV2(
                        ontologySchema = ApiV2WithValueObjects,
                        valueHasInteger = intValue
                    ),
                    permissions = Some(permissions)
                ),
                requestingUser = anythingUser1,
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[BadRequestException] should ===(true)
            }

        }

        "not create an integer value with custom permissions referring to a nonexistent group" in {
            val resourceIri: IRI = aThingIri
            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri
            val intValue = 1024
            val permissions = "M knora-base:Creator|V http://rdfh.ch/groups/0001/nonexistent-group"

            actorUnderTest ! CreateValueRequestV2(
                CreateValueV2(
                    resourceIri = resourceIri,
                    resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
                    propertyIri = propertyIri,
                    valueContent = IntegerValueContentV2(
                        ontologySchema = ApiV2WithValueObjects,
                        valueHasInteger = intValue
                    ),
                    permissions = Some(permissions)
                ),
                requestingUser = anythingUser1,
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[NotFoundException] should ===(true)
            }
        }

        "not create a value if the user does not have modify permission on the resource" in {
            val resourceIri: IRI = aThingIri
            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri
            val intValue = 5

            actorUnderTest ! CreateValueRequestV2(
                CreateValueV2(
                    resourceIri = resourceIri,
                    resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
                    propertyIri = propertyIri,
                    valueContent = IntegerValueContentV2(
                        ontologySchema = ApiV2WithValueObjects,
                        valueHasInteger = intValue
                    )
                ),
                requestingUser = incunabulaUser,
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[ForbiddenException] should ===(true)
            }
        }

        "not create a duplicate integer value" in {
            val resourceIri: IRI = aThingIri
            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri
            val intValue = 4

            actorUnderTest ! CreateValueRequestV2(
                CreateValueV2(
                    resourceIri = resourceIri,
                    resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
                    propertyIri = propertyIri,
                    valueContent = IntegerValueContentV2(
                        ontologySchema = ApiV2WithValueObjects,
                        valueHasInteger = intValue
                    )
                ),
                requestingUser = anythingUser1,
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure =>
                    msg.cause.isInstanceOf[DuplicateValueException] should ===(true)
            }
        }

        "create a text value without standoff" in {
            val valueHasString = "Comment 1a"
            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book_comment".toSmartIri
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(zeitglöckleinIri, incunabulaUser)

            actorUnderTest ! CreateValueRequestV2(
                CreateValueV2(
                    resourceIri = zeitglöckleinIri,
                    resourceClassIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book".toSmartIri,
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
                case createValueResponse: CreateValueResponseV2 => zeitglöckleinCommentWithoutStandoffIri.set(createValueResponse.valueIri)
            }

            // Read the value back to check that it was added correctly.

            val valueFromTriplestore = getValue(
                resourceIri = zeitglöckleinIri,
                maybePreviousLastModDate = maybeResourceLastModDate,
                propertyIriForGravsearch = propertyIri,
                propertyIriInResult = propertyIri,
                expectedValueIri = zeitglöckleinCommentWithoutStandoffIri.get,
                requestingUser = incunabulaUser
            )

            valueFromTriplestore.valueContent match {
                case savedValue: TextValueContentV2 => savedValue.valueHasString should ===(valueHasString)
                case _ => throw AssertionException(s"Expected text value, got $valueFromTriplestore")
            }
        }

        "not create a duplicate text value without standoff" in {
            val valueHasString = "Comment 1a"
            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book_comment".toSmartIri

            actorUnderTest ! CreateValueRequestV2(
                CreateValueV2(
                    resourceIri = zeitglöckleinIri,
                    resourceClassIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book".toSmartIri,
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
                case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[DuplicateValueException] should ===(true)
            }
        }

        "create a text value with a comment" in {
            val valueHasString = "this is a text value that has a comment"
            val valueHasComment = "this is a comment"
            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book_comment".toSmartIri
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(zeitglöckleinIri, incunabulaUser)

            actorUnderTest ! CreateValueRequestV2(
                CreateValueV2(
                    resourceIri = zeitglöckleinIri,
                    resourceClassIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book".toSmartIri,
                    propertyIri = propertyIri,
                    valueContent = TextValueContentV2(
                        ontologySchema = ApiV2WithValueObjects,
                        valueHasString = valueHasString,
                        comment = Some(valueHasComment)
                    )
                ),
                requestingUser = incunabulaUser,
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case createValueResponse: CreateValueResponseV2 => zeitglöckleinCommentWithCommentIri.set(createValueResponse.valueIri)
            }

            // Read the value back to check that it was added correctly.

            val valueFromTriplestore = getValue(
                resourceIri = zeitglöckleinIri,
                maybePreviousLastModDate = maybeResourceLastModDate,
                propertyIriForGravsearch = propertyIri,
                propertyIriInResult = propertyIri,
                expectedValueIri = zeitglöckleinCommentWithCommentIri.get,
                requestingUser = incunabulaUser
            )

            valueFromTriplestore.valueContent match {
                case savedValue: TextValueContentV2 =>
                    savedValue.valueHasString should ===(valueHasString)
                    savedValue.comment should ===(Some(valueHasComment))

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

            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book_comment".toSmartIri
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(zeitglöckleinIri, incunabulaUser)

            actorUnderTest ! CreateValueRequestV2(
                CreateValueV2(
                    resourceIri = zeitglöckleinIri,
                    resourceClassIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book".toSmartIri,
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
                case createValueResponse: CreateValueResponseV2 => zeitglöckleinCommentWithStandoffIri.set(createValueResponse.valueIri)
            }

            // Read the value back to check that it was added correctly.

            val valueFromTriplestore = getValue(
                resourceIri = zeitglöckleinIri,
                maybePreviousLastModDate = maybeResourceLastModDate,
                propertyIriForGravsearch = propertyIri,
                propertyIriInResult = propertyIri,
                expectedValueIri = zeitglöckleinCommentWithStandoffIri.get,
                requestingUser = incunabulaUser
            )

            valueFromTriplestore.valueContent match {
                case savedValue: TextValueContentV2 =>
                    savedValue.valueHasString should ===(valueHasString)
                    savedValue.standoffAndMapping should ===(standoffAndMapping)

                case _ => throw AssertionException(s"Expected text value, got $valueFromTriplestore")
            }
        }

        "not create a duplicate text value with standoff (even if the standoff is different)" in {
            val valueHasString = "Comment 1aa"

            val standoffAndMapping = Some(StandoffAndMapping(
                standoff = sampleStandoffModified,
                mappingIri = "http://rdfh.ch/standoff/mappings/StandardMapping",
                mapping = standardMapping.get
            ))

            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book_comment".toSmartIri

            actorUnderTest ! CreateValueRequestV2(
                CreateValueV2(
                    resourceIri = zeitglöckleinIri,
                    resourceClassIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book".toSmartIri,
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
                case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[DuplicateValueException] should ===(true)
            }
        }

        "create a decimal value" in {
            // Add the value.

            val resourceIri: IRI = aThingIri
            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasDecimal".toSmartIri
            val valueHasDecimal = BigDecimal("4.3")
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUser1)

            actorUnderTest ! CreateValueRequestV2(
                CreateValueV2(
                    resourceIri = resourceIri,
                    resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
                    propertyIri = propertyIri,
                    valueContent = DecimalValueContentV2(
                        ontologySchema = ApiV2WithValueObjects,
                        valueHasDecimal = valueHasDecimal
                    )
                ),
                requestingUser = anythingUser1,
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
                requestingUser = anythingUser1
            )

            valueFromTriplestore.valueContent match {
                case savedValue: DecimalValueContentV2 => savedValue.valueHasDecimal should ===(valueHasDecimal)
                case _ => throw AssertionException(s"Expected decimal value, got $valueFromTriplestore")
            }
        }

        "not create a duplicate decimal value" in {
            val resourceIri: IRI = aThingIri
            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasDecimal".toSmartIri
            val valueHasDecimal = BigDecimal("4.3")

            actorUnderTest ! CreateValueRequestV2(
                CreateValueV2(
                    resourceIri = resourceIri,
                    resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
                    propertyIri = propertyIri,
                    valueContent = DecimalValueContentV2(
                        ontologySchema = ApiV2WithValueObjects,
                        valueHasDecimal = valueHasDecimal
                    )
                ),
                requestingUser = anythingUser1,
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[DuplicateValueException] should ===(true)
            }
        }

        "create a date value" in {
            // Add the value.

            val resourceIri: IRI = aThingIri
            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasDate".toSmartIri
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUser1)

            val submittedValueContent = DateValueContentV2(
                ontologySchema = ApiV2WithValueObjects,
                valueHasCalendar = CalendarNameGregorian,
                valueHasStartJDN = 2264907,
                valueHasStartPrecision = DatePrecisionYear,
                valueHasEndJDN = 2265271,
                valueHasEndPrecision = DatePrecisionYear
            )

            actorUnderTest ! CreateValueRequestV2(
                CreateValueV2(
                    resourceIri = resourceIri,
                    resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
                    propertyIri = propertyIri,
                    valueContent = submittedValueContent
                ),
                requestingUser = anythingUser1,
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
                requestingUser = anythingUser1
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

        "not create a duplicate date value" in {
            val resourceIri: IRI = aThingIri
            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasDate".toSmartIri

            val submittedValueContent = DateValueContentV2(
                ontologySchema = ApiV2WithValueObjects,
                valueHasCalendar = CalendarNameGregorian,
                valueHasStartJDN = 2264907,
                valueHasStartPrecision = DatePrecisionYear,
                valueHasEndJDN = 2265271,
                valueHasEndPrecision = DatePrecisionYear
            )

            actorUnderTest ! CreateValueRequestV2(
                CreateValueV2(
                    resourceIri = resourceIri,
                    resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
                    propertyIri = propertyIri,
                    valueContent = submittedValueContent
                ),
                requestingUser = anythingUser1,
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[DuplicateValueException] should ===(true)
            }
        }

        "create a boolean value" in {
            // Add the value.

            val resourceIri: IRI = aThingIri
            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasBoolean".toSmartIri
            val valueHasBoolean = true
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUser1)

            actorUnderTest ! CreateValueRequestV2(
                CreateValueV2(
                    resourceIri = resourceIri,
                    resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
                    propertyIri = propertyIri,
                    valueContent = BooleanValueContentV2(
                        ontologySchema = ApiV2WithValueObjects,
                        valueHasBoolean = valueHasBoolean
                    )
                ),
                requestingUser = anythingUser1,
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
                requestingUser = anythingUser1
            )

            valueFromTriplestore.valueContent match {
                case savedValue: BooleanValueContentV2 => savedValue.valueHasBoolean should ===(valueHasBoolean)
                case _ => throw AssertionException(s"Expected boolean value, got $valueFromTriplestore")
            }
        }

        "create a geometry value" in {
            // Add the value.

            val resourceIri: IRI = aThingIri
            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasGeometry".toSmartIri
            val valueHasGeometry = """{"status":"active","lineColor":"#ff3333","lineWidth":2,"points":[{"x":0.08098591549295775,"y":0.16741071428571427},{"x":0.7394366197183099,"y":0.7299107142857143}],"type":"rectangle","original_index":0}"""
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUser1)

            actorUnderTest ! CreateValueRequestV2(
                CreateValueV2(
                    resourceIri = resourceIri,
                    resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
                    propertyIri = propertyIri,
                    valueContent = GeomValueContentV2(
                        ontologySchema = ApiV2WithValueObjects,
                        valueHasGeometry = valueHasGeometry
                    )
                ),
                requestingUser = anythingUser1,
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
                requestingUser = anythingUser1
            )

            valueFromTriplestore.valueContent match {
                case savedValue: GeomValueContentV2 => savedValue.valueHasGeometry should ===(valueHasGeometry)
                case _ => throw AssertionException(s"Expected geometry value, got $valueFromTriplestore")
            }
        }

        "not create a duplicate geometry value" in {
            val resourceIri: IRI = aThingIri
            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasGeometry".toSmartIri
            val valueHasGeometry = """{"status":"active","lineColor":"#ff3333","lineWidth":2,"points":[{"x":0.08098591549295775,"y":0.16741071428571427},{"x":0.7394366197183099,"y":0.7299107142857143}],"type":"rectangle","original_index":0}"""

            actorUnderTest ! CreateValueRequestV2(
                CreateValueV2(
                    resourceIri = resourceIri,
                    resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
                    propertyIri = propertyIri,
                    valueContent = GeomValueContentV2(
                        ontologySchema = ApiV2WithValueObjects,
                        valueHasGeometry = valueHasGeometry
                    )
                ),
                requestingUser = anythingUser1,
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[DuplicateValueException] should ===(true)
            }
        }

        "create an interval value" in {
            // Add the value.

            val resourceIri: IRI = aThingIri
            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInterval".toSmartIri
            val valueHasIntervalStart = BigDecimal("1.2")
            val valueHasIntervalEnd = BigDecimal("3.4")
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUser1)

            actorUnderTest ! CreateValueRequestV2(
                CreateValueV2(
                    resourceIri = resourceIri,
                    resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
                    propertyIri = propertyIri,
                    valueContent = IntervalValueContentV2(
                        ontologySchema = ApiV2WithValueObjects,
                        valueHasIntervalStart = valueHasIntervalStart,
                        valueHasIntervalEnd = valueHasIntervalEnd
                    )
                ),
                requestingUser = anythingUser1,
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
                requestingUser = anythingUser1
            )

            valueFromTriplestore.valueContent match {
                case savedValue: IntervalValueContentV2 =>
                    savedValue.valueHasIntervalStart should ===(valueHasIntervalStart)
                    savedValue.valueHasIntervalEnd should ===(valueHasIntervalEnd)

                case _ => throw AssertionException(s"Expected interval value, got $valueFromTriplestore")
            }
        }

        "not create a duplicate interval value" in {
            val resourceIri: IRI = aThingIri
            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInterval".toSmartIri
            val valueHasIntervalStart = BigDecimal("1.2")
            val valueHasIntervalEnd = BigDecimal("3.4")

            actorUnderTest ! CreateValueRequestV2(
                CreateValueV2(
                    resourceIri = resourceIri,
                    resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
                    propertyIri = propertyIri,
                    valueContent = IntervalValueContentV2(
                        ontologySchema = ApiV2WithValueObjects,
                        valueHasIntervalStart = valueHasIntervalStart,
                        valueHasIntervalEnd = valueHasIntervalEnd
                    )
                ),
                requestingUser = anythingUser1,
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[DuplicateValueException] should ===(true)
            }
        }

        "create a list value" in {
            // Add the value.

            val resourceIri: IRI = aThingIri
            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasListItem".toSmartIri
            val valueHasListNode = "http://rdfh.ch/lists/0001/treeList03"
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUser1)

            actorUnderTest ! CreateValueRequestV2(
                CreateValueV2(
                    resourceIri = resourceIri,
                    resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
                    propertyIri = propertyIri,
                    valueContent = HierarchicalListValueContentV2(
                        ontologySchema = ApiV2WithValueObjects,
                        valueHasListNode = valueHasListNode
                    )
                ),
                requestingUser = anythingUser1,
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
                requestingUser = anythingUser1
            )

            valueFromTriplestore.valueContent match {
                case savedValue: HierarchicalListValueContentV2 =>
                    savedValue.valueHasListNode should ===(valueHasListNode)

                case _ => throw AssertionException(s"Expected list value, got $valueFromTriplestore")
            }
        }

        "not create a duplicate list value" in {
            val resourceIri: IRI = aThingIri
            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasListItem".toSmartIri
            val valueHasListNode = "http://rdfh.ch/lists/0001/treeList03"

            actorUnderTest ! CreateValueRequestV2(
                CreateValueV2(
                    resourceIri = resourceIri,
                    resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
                    propertyIri = propertyIri,
                    valueContent = HierarchicalListValueContentV2(
                        ontologySchema = ApiV2WithValueObjects,
                        valueHasListNode = valueHasListNode
                    )
                ),
                requestingUser = anythingUser1,
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[DuplicateValueException] should ===(true)
            }
        }

        "not create a list value referring to a nonexistent list node" in {
            val resourceIri: IRI = aThingIri
            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasListItem".toSmartIri
            val valueHasListNode = "http://rdfh.ch/lists/0001/nonexistent"

            actorUnderTest ! CreateValueRequestV2(
                CreateValueV2(
                    resourceIri = resourceIri,
                    resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
                    propertyIri = propertyIri,
                    valueContent = HierarchicalListValueContentV2(
                        ontologySchema = ApiV2WithValueObjects,
                        valueHasListNode = valueHasListNode
                    )
                ),
                requestingUser = anythingUser1,
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[NotFoundException] should ===(true)
            }
        }

        "create a color value" in {
            // Add the value.

            val resourceIri: IRI = aThingIri
            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasColor".toSmartIri
            val valueHasColor = "#ff3333"
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUser1)

            actorUnderTest ! CreateValueRequestV2(
                CreateValueV2(
                    resourceIri = resourceIri,
                    resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
                    propertyIri = propertyIri,
                    valueContent = ColorValueContentV2(
                        ontologySchema = ApiV2WithValueObjects,
                        valueHasColor = valueHasColor
                    )
                ),
                requestingUser = anythingUser1,
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
                requestingUser = anythingUser1
            )

            valueFromTriplestore.valueContent match {
                case savedValue: ColorValueContentV2 =>
                    savedValue.valueHasColor should ===(valueHasColor)

                case _ => throw AssertionException(s"Expected color value, got $valueFromTriplestore")
            }
        }

        "not create a duplicate color value" in {
            val resourceIri: IRI = aThingIri
            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasColor".toSmartIri
            val valueHasColor = "#ff3333"

            actorUnderTest ! CreateValueRequestV2(
                CreateValueV2(
                    resourceIri = resourceIri,
                    resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
                    propertyIri = propertyIri,
                    valueContent = ColorValueContentV2(
                        ontologySchema = ApiV2WithValueObjects,
                        valueHasColor = valueHasColor
                    )
                ),
                requestingUser = anythingUser1,
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[DuplicateValueException] should ===(true)
            }
        }

        "create a URI value" in {
            // Add the value.

            val resourceIri: IRI = aThingIri
            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasUri".toSmartIri
            val valueHasUri = "https://www.knora.org"
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUser1)

            actorUnderTest ! CreateValueRequestV2(
                CreateValueV2(
                    resourceIri = resourceIri,
                    resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
                    propertyIri = propertyIri,
                    valueContent = UriValueContentV2(
                        ontologySchema = ApiV2WithValueObjects,
                        valueHasUri = valueHasUri
                    )
                ),
                requestingUser = anythingUser1,
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
                requestingUser = anythingUser1
            )

            valueFromTriplestore.valueContent match {
                case savedValue: UriValueContentV2 =>
                    savedValue.valueHasUri should ===(valueHasUri)

                case _ => throw AssertionException(s"Expected URI value, got $valueFromTriplestore")
            }
        }

        "not create a duplicate URI value" in {
            val resourceIri: IRI = aThingIri
            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasUri".toSmartIri
            val valueHasUri = "https://www.knora.org"

            actorUnderTest ! CreateValueRequestV2(
                CreateValueV2(
                    resourceIri = resourceIri,
                    resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
                    propertyIri = propertyIri,
                    valueContent = UriValueContentV2(
                        ontologySchema = ApiV2WithValueObjects,
                        valueHasUri = valueHasUri
                    )
                ),
                requestingUser = anythingUser1,
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[DuplicateValueException] should ===(true)
            }
        }

        "create a geoname value" in {
            // Add the value.

            val resourceIri: IRI = aThingIri
            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasGeoname".toSmartIri
            val valueHasGeonameCode = "2661604"
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUser1)

            actorUnderTest ! CreateValueRequestV2(
                CreateValueV2(
                    resourceIri = resourceIri,
                    resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
                    propertyIri = propertyIri,
                    valueContent = GeonameValueContentV2(
                        ontologySchema = ApiV2WithValueObjects,
                        valueHasGeonameCode = valueHasGeonameCode
                    )
                ),
                requestingUser = anythingUser1,
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
                requestingUser = anythingUser1
            )

            valueFromTriplestore.valueContent match {
                case savedValue: GeonameValueContentV2 =>
                    savedValue.valueHasGeonameCode should ===(valueHasGeonameCode)

                case _ => throw AssertionException(s"Expected GeoNames value, got $valueFromTriplestore")
            }
        }

        "not create a duplicate geoname value" in {
            val resourceIri: IRI = aThingIri
            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasGeoname".toSmartIri
            val valueHasGeonameCode = "2661604"

            actorUnderTest ! CreateValueRequestV2(
                CreateValueV2(
                    resourceIri = resourceIri,
                    resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
                    propertyIri = propertyIri,
                    valueContent = GeonameValueContentV2(
                        ontologySchema = ApiV2WithValueObjects,
                        valueHasGeonameCode = valueHasGeonameCode
                    )
                ),
                requestingUser = anythingUser1,
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[DuplicateValueException] should ===(true)
            }
        }

        "create a link between two resources" in {
            val resourceIri: IRI = "http://rdfh.ch/cb1a74e3e2f6"
            val linkPropertyIri: SmartIri = OntologyConstants.KnoraApiV2WithValueObjects.HasLinkTo.toSmartIri
            val linkValuePropertyIri: SmartIri = OntologyConstants.KnoraApiV2WithValueObjects.HasLinkToValue.toSmartIri
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, incunabulaUser)

            val createValueRequest = CreateValueRequestV2(
                CreateValueV2(
                    resourceIri = resourceIri,
                    propertyIri = linkValuePropertyIri,
                    resourceClassIri = OntologyConstants.KnoraApiV2WithValueObjects.LinkObj.toSmartIri,
                    valueContent = LinkValueContentV2(
                        ontologySchema = ApiV2WithValueObjects,
                        referredResourceIri = zeitglöckleinIri
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

            valueFromTriplestore match {
                case readLinkValueV2: ReadLinkValueV2 =>
                    readLinkValueV2.valueContent.referredResourceIri should ===(zeitglöckleinIri)
                    readLinkValueV2.valueHasRefCount should ===(1)

                case _ => throw AssertionException(s"Expected link value, got $valueFromTriplestore")
            }
        }

        "not create a duplicate link" in {
            val resourceIri: IRI = "http://rdfh.ch/cb1a74e3e2f6"
            val linkValuePropertyIri: SmartIri = OntologyConstants.KnoraApiV2WithValueObjects.HasLinkToValue.toSmartIri

            val createValueRequest = CreateValueRequestV2(
                CreateValueV2(
                    resourceIri = resourceIri,
                    resourceClassIri = OntologyConstants.KnoraApiV2WithValueObjects.LinkObj.toSmartIri,
                    propertyIri = linkValuePropertyIri,
                    valueContent = LinkValueContentV2(
                        ontologySchema = ApiV2WithValueObjects,
                        referredResourceIri = zeitglöckleinIri
                    )
                ),
                requestingUser = incunabulaUser,
                apiRequestID = UUID.randomUUID
            )

            actorUnderTest ! createValueRequest

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[DuplicateValueException] should ===(true)
            }
        }

        "not accept a link property in a request to create a link between two resources" in {
            val resourceIri: IRI = "http://rdfh.ch/cb1a74e3e2f6"
            val linkPropertyIri: SmartIri = OntologyConstants.KnoraApiV2WithValueObjects.HasLinkTo.toSmartIri

            val createValueRequest = CreateValueRequestV2(
                CreateValueV2(
                    resourceIri = resourceIri,
                    resourceClassIri = OntologyConstants.KnoraApiV2WithValueObjects.LinkObj.toSmartIri,
                    propertyIri = linkPropertyIri,
                    valueContent = LinkValueContentV2(
                        ontologySchema = ApiV2WithValueObjects,
                        referredResourceIri = zeitglöckleinIri
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

        "not create a standoff link directly" in {
            actorUnderTest ! CreateValueRequestV2(
                CreateValueV2(
                    resourceIri = zeitglöckleinIri,
                    resourceClassIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book".toSmartIri,
                    propertyIri = OntologyConstants.KnoraApiV2WithValueObjects.HasStandoffLinkToValue.toSmartIri,
                    valueContent = LinkValueContentV2(
                        ontologySchema = ApiV2WithValueObjects,
                        referredResourceIri = generationeIri
                    )
                ),
                requestingUser = SharedTestDataADM.superUser,
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[BadRequestException] should ===(true)
            }
        }

        "not add a new value to a nonexistent resource" in {
            val resourceIri: IRI = "http://rdfh.ch/nonexistent"
            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri
            val intValue = 6

            actorUnderTest ! CreateValueRequestV2(
                CreateValueV2(
                    resourceIri = resourceIri,
                    resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
                    propertyIri = propertyIri,
                    valueContent = IntegerValueContentV2(
                        ontologySchema = ApiV2WithValueObjects,
                        valueHasInteger = intValue
                    )
                ),
                requestingUser = anythingUser1,
                apiRequestID = UUID.randomUUID
            )


            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure =>
                    // msg.cause.isInstanceOf[NotFoundException] should ===(true)
                    msg.cause.isInstanceOf[NotFoundException] should ===(true)
            }
        }

        "not add a new value to a deleted resource" in {
            val resourceIri: IRI = "http://rdfh.ch/9935159f67"
            val valueHasString = "Comment 2"
            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book_comment".toSmartIri

            actorUnderTest ! CreateValueRequestV2(
                CreateValueV2(
                    resourceIri = resourceIri,
                    resourceClassIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book".toSmartIri,
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
                case msg: akka.actor.Status.Failure =>
                    // msg.cause.isInstanceOf[NotFoundException] should ===(true)
                    msg.cause.isInstanceOf[NotFoundException] should ===(true)
            }
        }

        "not add a new value if the resource's rdf:type is not correctly given" in {
            val resourceIri: IRI = aThingIri
            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri
            val intValue = 2048

            actorUnderTest ! CreateValueRequestV2(
                CreateValueV2(
                    resourceIri = resourceIri,
                    resourceClassIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book".toSmartIri,
                    propertyIri = propertyIri,
                    valueContent = IntegerValueContentV2(
                        ontologySchema = ApiV2WithValueObjects,
                        valueHasInteger = intValue
                    )
                ),
                requestingUser = anythingUser1,
                apiRequestID = UUID.randomUUID
            )


            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure =>
                    msg.cause.isInstanceOf[BadRequestException] should ===(true)
            }
        }

        "not add a new value of the wrong type" in {
            val resourceIri: IRI = "http://rdfh.ch/21abac2162"
            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#pubdate".toSmartIri

            actorUnderTest ! CreateValueRequestV2(
                CreateValueV2(
                    resourceIri = resourceIri,
                    resourceClassIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book".toSmartIri,
                    propertyIri = propertyIri,
                    valueContent = TextValueContentV2(
                        ontologySchema = ApiV2WithValueObjects,
                        valueHasString = "this is not a date"
                    )
                ),
                requestingUser = incunabulaUser,
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[OntologyConstraintException] should ===(true)
            }
        }

        "not add a new value that would violate a cardinality restriction" in {
            val resourceIri: IRI = "http://rdfh.ch/4f11adaf"

            // The cardinality of incunabula:partOf in incunabula:page is 1, and page http://rdfh.ch/4f11adaf is already part of a book.

            actorUnderTest ! CreateValueRequestV2(
                CreateValueV2(
                    resourceIri = resourceIri,
                    resourceClassIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#page".toSmartIri,
                    propertyIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#partOfValue".toSmartIri,
                    valueContent = LinkValueContentV2(
                        ontologySchema = ApiV2WithValueObjects,
                        referredResourceIri = "http://rdfh.ch/e41ab5695c"
                    )
                ),
                requestingUser = incunabulaUser,
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[OntologyConstraintException] should ===(true)
            }

            // The cardinality of incunabula:seqnum in incunabula:page is 0-1, and page http://rdfh.ch/4f11adaf already has a seqnum.

            actorUnderTest ! CreateValueRequestV2(
                CreateValueV2(
                    resourceIri = "http://rdfh.ch/4f11adaf",
                    resourceClassIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#page".toSmartIri,
                    propertyIri = "http://www.knora.org/ontology/0803/incunabula#seqnum".toSmartIri,
                    valueContent = IntegerValueContentV2(
                        ontologySchema = ApiV2WithValueObjects,
                        valueHasInteger = 1
                    )
                ),
                requestingUser = incunabulaUser,
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure =>
                    msg.cause.isInstanceOf[OntologyConstraintException] should ===(true)
            }
        }

        "add a new text value containing a Standoff resource reference, and create a hasStandoffLinkTo direct link and a corresponding LinkValue" in {
            val resourceIri: IRI = "http://rdfh.ch/21abac2162"
            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book_comment".toSmartIri
            val valueHasString = "This comment refers to another resource"
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, incunabulaUser)

            val standoffAndMapping = Some(StandoffAndMapping(
                standoff = Seq(StandoffTagV2(
                    dataType = Some(StandoffDataTypeClasses.StandoffLinkTag),
                    standoffTagClassIri = OntologyConstants.KnoraBase.StandoffLinkTag,
                    startPosition = 31,
                    endPosition = 39,
                    attributes = Vector(StandoffTagIriAttributeV2(standoffPropertyIri = OntologyConstants.KnoraBase.StandoffTagHasLink, value = zeitglöckleinIri)),
                    uuid = UUID.randomUUID().toString,
                    originalXMLID = None,
                    startIndex = 0
                )),
                mappingIri = "http://rdfh.ch/standoff/mappings/StandardMapping",
                mapping = standardMapping.get
            ))

            actorUnderTest ! CreateValueRequestV2(
                CreateValueV2(
                    resourceIri = resourceIri,
                    resourceClassIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book".toSmartIri,
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
                case createValueResponse: CreateValueResponseV2 => lobComment1Iri.set(createValueResponse.valueIri)
            }

            // Read the value back to check that it was added correctly.

            val updatedResource = getResourceWithValues(
                resourceIri = resourceIri,
                propertyIrisForGravsearch = Seq(propertyIri, OntologyConstants.KnoraApiV2WithValueObjects.HasStandoffLinkTo.toSmartIri),
                requestingUser = incunabulaUser
            )

            checkLastModDate(
                resourceIri = resourceIri,
                maybePreviousLastModDate = maybeResourceLastModDate,
                maybeUpdatedLastModDate = updatedResource.lastModificationDate
            )

            val textValueFromTriplestore: ReadValueV2 = getValueFromResource(
                resource = updatedResource,
                propertyIriInResult = propertyIri,
                expectedValueIri = lobComment1Iri.get
            )

            textValueFromTriplestore.valueContent match {
                case savedTextValue: TextValueContentV2 =>
                    savedTextValue.valueHasString should ===(valueHasString)
                    savedTextValue.standoffAndMapping should ===(standoffAndMapping)

                case _ => throw AssertionException(s"Expected text value, got $textValueFromTriplestore")
            }

            // Since this is the first Standoff resource reference between the source and target resources, we should
            // now have version 1 of a LinkValue (it should have no previous version), with a reference count of 1.

            val linkValuesFromTripletore: Seq[ReadValueV2] = getValuesFromResource(
                resource = updatedResource,
                propertyIriInResult = OntologyConstants.KnoraApiV2WithValueObjects.HasStandoffLinkToValue.toSmartIri
            )

            assert(linkValuesFromTripletore.size == 1)

            val linkValueFromTriplestore: ReadLinkValueV2 = linkValuesFromTripletore.head match {
                case readLinkValueV2: ReadLinkValueV2 => readLinkValueV2
                case other => throw AssertionException(s"Expected link value, got $other")
            }

            linkValueFromTriplestore.previousValueIri.isEmpty should ===(true)
            linkValueFromTriplestore.valueHasRefCount should ===(1)
            linkValueFromTriplestore.valueContent.referredResourceIri should ===(zeitglöckleinIri)
            standoffLinkValueIri.set(linkValueFromTriplestore.valueIri)
        }

        "add another new text value containing a Standoff resource reference, and make a new version of the LinkValue" in {
            val resourceIri: IRI = "http://rdfh.ch/21abac2162"
            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book_comment".toSmartIri
            val valueHasString = "This remark refers to another resource"
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, incunabulaUser)

            val standoffAndMapping = Some(StandoffAndMapping(
                standoff = Seq(StandoffTagV2(
                    dataType = Some(StandoffDataTypeClasses.StandoffLinkTag),
                    standoffTagClassIri = OntologyConstants.KnoraBase.StandoffLinkTag,
                    startPosition = 30,
                    endPosition = 38,
                    attributes = Vector(StandoffTagIriAttributeV2(standoffPropertyIri = OntologyConstants.KnoraBase.StandoffTagHasLink, value = zeitglöckleinIri)),
                    uuid = UUID.randomUUID().toString,
                    originalXMLID = None,
                    startIndex = 0
                )),
                mappingIri = "http://rdfh.ch/standoff/mappings/StandardMapping",
                mapping = standardMapping.get
            ))

            actorUnderTest ! CreateValueRequestV2(
                CreateValueV2(
                    resourceIri = resourceIri,
                    resourceClassIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book".toSmartIri,
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
                case createValueResponse: CreateValueResponseV2 => lobComment2Iri.set(createValueResponse.valueIri)
            }

            // Read the value back to check that it was added correctly.

            val updatedResource = getResourceWithValues(
                resourceIri = resourceIri,
                propertyIrisForGravsearch = Seq(propertyIri, OntologyConstants.KnoraApiV2WithValueObjects.HasStandoffLinkTo.toSmartIri),
                requestingUser = incunabulaUser
            )

            checkLastModDate(
                resourceIri = resourceIri,
                maybePreviousLastModDate = maybeResourceLastModDate,
                maybeUpdatedLastModDate = updatedResource.lastModificationDate
            )

            val textValueFromTriplestore: ReadValueV2 = getValueFromResource(
                resource = updatedResource,
                propertyIriInResult = propertyIri,
                expectedValueIri = lobComment2Iri.get
            )

            textValueFromTriplestore.valueContent match {
                case savedTextValue: TextValueContentV2 =>
                    savedTextValue.valueHasString should ===(valueHasString)
                    savedTextValue.standoffAndMapping should ===(standoffAndMapping)

                case _ => throw AssertionException(s"Expected text value, got $textValueFromTriplestore")
            }

            // Now that we've added a different TextValue that refers to the same resource, we should have version 2
            // of the LinkValue, with a reference count of 2. It should have a previousValue pointing to the previous
            // version.

            val linkValuesFromTripletore: Seq[ReadValueV2] = getValuesFromResource(
                resource = updatedResource,
                propertyIriInResult = OntologyConstants.KnoraApiV2WithValueObjects.HasStandoffLinkToValue.toSmartIri
            )

            assert(linkValuesFromTripletore.size == 1)

            val linkValueFromTriplestore: ReadLinkValueV2 = linkValuesFromTripletore.head match {
                case readLinkValueV2: ReadLinkValueV2 => readLinkValueV2
                case other => throw AssertionException(s"Expected link value, got $other")
            }

            linkValueFromTriplestore.previousValueIri.contains(standoffLinkValueIri.get) should ===(true)
            linkValueFromTriplestore.valueHasRefCount should ===(2)
            linkValueFromTriplestore.valueContent.referredResourceIri should ===(zeitglöckleinIri)
            standoffLinkValueIri.set(linkValueFromTriplestore.valueIri)
        }

        "create a still image file value" in {
            // Add the value.

            val resourceIri: IRI = aThingPictureIri
            val propertyIri: SmartIri = OntologyConstants.KnoraApiV2WithValueObjects.HasStillImageFileValue.toSmartIri
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUser1)

            val valueContent = StillImageFileValueContentV2(
                ontologySchema = ApiV2WithValueObjects,
                fileValue = FileValueV2(
                    internalFilename = "IQUO3t1AABm-FSLC0vNvVpr.jp2",
                    internalMimeType = "image/jp2",
                    originalFilename = "test.tiff",
                    originalMimeType = "image/tiff"
                ),
                dimX = 512,
                dimY = 256
            )

            actorUnderTest ! CreateValueRequestV2(
                CreateValueV2(
                    resourceIri = resourceIri,
                    resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#ThingPicture".toSmartIri,
                    propertyIri = propertyIri,
                    valueContent = valueContent
                ),
                requestingUser = anythingUser1,
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case createValueResponse: CreateValueResponseV2 => stillImageFileValueIri.set(createValueResponse.valueIri)
            }

            // Read the value back to check that it was added correctly.

            val valueFromTriplestore = getValue(
                resourceIri = resourceIri,
                maybePreviousLastModDate = maybeResourceLastModDate,
                propertyIriForGravsearch = propertyIri,
                propertyIriInResult = propertyIri,
                expectedValueIri = stillImageFileValueIri.get,
                requestingUser = anythingUser1
            )

            valueFromTriplestore.valueContent match {
                case savedValue: StillImageFileValueContentV2 => savedValue should ===(valueContent)
                case _ => throw AssertionException(s"Expected file value, got $valueFromTriplestore")
            }
        }

        "not create a duplicate still image file value" in {
            val resourceIri: IRI = aThingPictureIri
            val propertyIri: SmartIri = OntologyConstants.KnoraApiV2WithValueObjects.HasStillImageFileValue.toSmartIri

            val valueContent = StillImageFileValueContentV2(
                ontologySchema = ApiV2WithValueObjects,
                fileValue = FileValueV2(
                    internalFilename = "IQUO3t1AABm-FSLC0vNvVpr.jp2",
                    internalMimeType = "image/jp2",
                    originalFilename = "test.tiff",
                    originalMimeType = "image/tiff"
                ),
                dimX = 512,
                dimY = 256
            )

            actorUnderTest ! CreateValueRequestV2(
                CreateValueV2(
                    resourceIri = resourceIri,
                    resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#ThingPicture".toSmartIri,
                    propertyIri = propertyIri,
                    valueContent = valueContent
                ),
                requestingUser = anythingUser1,
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure =>
                    msg.cause.isInstanceOf[DuplicateValueException] should ===(true)
            }
        }

        "update an integer value" in {
            val resourceIri: IRI = aThingIri
            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUser1)

            // Get the value before update.
            val previousValueFromTriplestore: ReadValueV2 = getValue(
                resourceIri = resourceIri,
                maybePreviousLastModDate = maybeResourceLastModDate,
                propertyIriForGravsearch = propertyIri,
                propertyIriInResult = propertyIri,
                expectedValueIri = intValueIri.get,
                requestingUser = anythingUser1,
                checkLastModDateChanged = false
            )

            // Update the value.

            val intValue = 5

            actorUnderTest ! UpdateValueRequestV2(
                UpdateValueV2(
                    resourceIri = resourceIri,
                    resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
                    propertyIri = propertyIri,
                    valueIri = intValueIri.get,
                    valueContent = IntegerValueContentV2(
                        ontologySchema = ApiV2WithValueObjects,
                        valueHasInteger = intValue
                    )
                ),
                requestingUser = anythingUser1,
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case updateValueResponse: UpdateValueResponseV2 => intValueIri.set(updateValueResponse.valueIri)
            }

            // Read the value back to check that it was added correctly.

            val updatedValueFromTriplestore = getValue(
                resourceIri = resourceIri,
                maybePreviousLastModDate = maybeResourceLastModDate,
                propertyIriForGravsearch = propertyIri,
                propertyIriInResult = propertyIri,
                expectedValueIri = intValueIri.get,
                requestingUser = anythingUser1
            )

            updatedValueFromTriplestore.valueContent match {
                case savedValue: IntegerValueContentV2 =>
                    savedValue.valueHasInteger should ===(intValue)
                    updatedValueFromTriplestore.permissions should ===(previousValueFromTriplestore.permissions)

                case _ => throw AssertionException(s"Expected integer value, got $updatedValueFromTriplestore")
            }
        }

        "not update a value if an outdated value IRI is given" in {
            val resourceIri: IRI = aThingIri
            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri
            val intValue = 3

            actorUnderTest ! UpdateValueRequestV2(
                UpdateValueV2(
                    resourceIri = resourceIri,
                    resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
                    propertyIri = propertyIri,
                    valueIri = firstIntValueVersionIri.get,
                    valueContent = IntegerValueContentV2(
                        ontologySchema = ApiV2WithValueObjects,
                        valueHasInteger = intValue
                    )
                ),
                requestingUser = anythingUser1,
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[NotFoundException] should ===(true)
            }
        }

        "update a value with custom permissions" in {
            val resourceIri: IRI = aThingIri
            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri
            val permissions = "CR knora-base:Creator|V knora-base:ProjectMember"
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUser1)
            val intValue = 6

            actorUnderTest ! UpdateValueRequestV2(
                UpdateValueV2(
                    resourceIri = resourceIri,
                    resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
                    propertyIri = propertyIri,
                    valueIri = intValueIri.get,
                    valueContent = IntegerValueContentV2(
                        ontologySchema = ApiV2WithValueObjects,
                        valueHasInteger = intValue
                    ),
                    permissions = Some(permissions)
                ),
                requestingUser = anythingUser1,
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case updateValueResponse: UpdateValueResponseV2 => intValueIri.set(updateValueResponse.valueIri)
            }

            // Read the value back to check that it was added correctly.

            val updatedValueFromTriplestore = getValue(
                resourceIri = resourceIri,
                maybePreviousLastModDate = maybeResourceLastModDate,
                propertyIriForGravsearch = propertyIri,
                propertyIriInResult = propertyIri,
                expectedValueIri = intValueIri.get,
                requestingUser = anythingUser1
            )

            updatedValueFromTriplestore.valueContent match {
                case savedValue: IntegerValueContentV2 =>
                    savedValue.valueHasInteger should ===(intValue)
                    updatedValueFromTriplestore.permissions should ===(permissions)

                case _ => throw AssertionException(s"Expected integer value, got $updatedValueFromTriplestore")
            }
        }

        "not update a value with custom permissions if the requesting user does not have ChangeRightsPermission on the value" in {
            val resourceIri: IRI = aThingIri
            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri
            val permissions = "CR knora-base:Creator"
            val intValue = 10

            actorUnderTest ! UpdateValueRequestV2(
                UpdateValueV2(
                    resourceIri = resourceIri,
                    resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
                    propertyIri = propertyIri,
                    valueIri = intValueIri.get,
                    valueContent = IntegerValueContentV2(
                        ontologySchema = ApiV2WithValueObjects,
                        valueHasInteger = intValue
                    ),
                    permissions = Some(permissions)
                ),
                requestingUser = anythingUser2,
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[ForbiddenException] should ===(true)
            }
        }

        "not update a value with syntactically invalid custom permissions" in {
            val resourceIri: IRI = aThingIri
            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri
            val permissions = "M knora-base:Creator,V knora-base:KnownUser"
            val intValue = 7

            actorUnderTest ! UpdateValueRequestV2(
                UpdateValueV2(
                    resourceIri = resourceIri,
                    resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
                    propertyIri = propertyIri,
                    valueIri = intValueIri.get,
                    valueContent = IntegerValueContentV2(
                        ontologySchema = ApiV2WithValueObjects,
                        valueHasInteger = intValue
                    ),
                    permissions = Some(permissions)
                ),
                requestingUser = anythingUser1,
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[BadRequestException] should ===(true)
            }
        }

        "not update a value with custom permissions referring to a nonexistent group" in {
            val resourceIri: IRI = aThingIri
            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri
            val permissions = "M knora-base:Creator|V http://rdfh.ch/groups/0001/nonexistent-group"
            val intValue = 8

            actorUnderTest ! UpdateValueRequestV2(
                UpdateValueV2(
                    resourceIri = resourceIri,
                    resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
                    propertyIri = propertyIri,
                    valueIri = intValueIri.get,
                    valueContent = IntegerValueContentV2(
                        ontologySchema = ApiV2WithValueObjects,
                        valueHasInteger = intValue
                    ),
                    permissions = Some(permissions)
                ),
                requestingUser = anythingUser1,
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[NotFoundException] should ===(true)
            }
        }

        "not update a value if the user does not have modify permission on the value" in {
            val resourceIri: IRI = aThingIri
            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri
            val intValue = 9

            actorUnderTest ! UpdateValueRequestV2(
                UpdateValueV2(
                    resourceIri = resourceIri,
                    resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
                    propertyIri = propertyIri,
                    valueIri = intValueIri.get,
                    valueContent = IntegerValueContentV2(
                        ontologySchema = ApiV2WithValueObjects,
                        valueHasInteger = intValue
                    )
                ),
                requestingUser = incunabulaUser,
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[ForbiddenException] should ===(true)
            }
        }

        "not update an integer value, giving it the same value as another integer value of the same property" in {
            val resourceIri: IRI = aThingIri
            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri
            val intValue = 1

            actorUnderTest ! UpdateValueRequestV2(
                UpdateValueV2(
                    resourceIri = resourceIri,
                    resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
                    propertyIri = propertyIri,
                    valueIri = intValueIri.get,
                    valueContent = IntegerValueContentV2(
                        ontologySchema = ApiV2WithValueObjects,
                        valueHasInteger = intValue
                    )
                ),
                requestingUser = anythingUser1,
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[DuplicateValueException] should ===(true)
            }
        }

        "not update an integer value without changing it" in {
            val resourceIri: IRI = aThingIri
            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri
            val intValue = 6

            actorUnderTest ! UpdateValueRequestV2(
                UpdateValueV2(
                    resourceIri = resourceIri,
                    resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
                    propertyIri = propertyIri,
                    valueIri = intValueIri.get,
                    valueContent = IntegerValueContentV2(
                        ontologySchema = ApiV2WithValueObjects,
                        valueHasInteger = intValue
                    )
                ),
                requestingUser = anythingUser1,
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[DuplicateValueException] should ===(true)
            }
        }

        "update a text value (without submitting standoff)" in {
            val valueHasString = "This updated comment has no standoff"
            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book_comment".toSmartIri
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(zeitglöckleinIri, incunabulaUser)

            actorUnderTest ! UpdateValueRequestV2(
                UpdateValueV2(
                    resourceIri = zeitglöckleinIri,
                    resourceClassIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book".toSmartIri,
                    propertyIri = propertyIri,
                    valueIri = zeitglöckleinCommentWithoutStandoffIri.get,
                    valueContent = TextValueContentV2(
                        ontologySchema = ApiV2WithValueObjects,
                        valueHasString = valueHasString
                    )
                ),
                requestingUser = incunabulaUser,
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case updateValueResponse: UpdateValueResponseV2 => zeitglöckleinCommentWithoutStandoffIri.set(updateValueResponse.valueIri)
            }

            // Read the value back to check that it was added correctly.

            val valueFromTriplestore = getValue(
                resourceIri = zeitglöckleinIri,
                maybePreviousLastModDate = maybeResourceLastModDate,
                propertyIriForGravsearch = propertyIri,
                propertyIriInResult = propertyIri,
                expectedValueIri = zeitglöckleinCommentWithoutStandoffIri.get,
                requestingUser = incunabulaUser
            )

            valueFromTriplestore.valueContent match {
                case savedValue: TextValueContentV2 => savedValue.valueHasString should ===(valueHasString)
                case _ => throw AssertionException(s"Expected text value, got $valueFromTriplestore")
            }
        }

        "update a text value (submitting standoff)" in {
            val valueHasString = "Comment 1ab"

            val standoffAndMapping = Some(StandoffAndMapping(
                standoff = sampleStandoffWithLink,
                mappingIri = "http://rdfh.ch/standoff/mappings/StandardMapping",
                mapping = standardMapping.get
            ))

            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book_comment".toSmartIri
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(zeitglöckleinIri, incunabulaUser)

            actorUnderTest ! UpdateValueRequestV2(
                UpdateValueV2(
                    resourceIri = zeitglöckleinIri,
                    resourceClassIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book".toSmartIri,
                    propertyIri = propertyIri,
                    valueIri = zeitglöckleinCommentWithStandoffIri.get,
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
                case updateValueResponse: UpdateValueResponseV2 => zeitglöckleinCommentWithStandoffIri.set(updateValueResponse.valueIri)
            }

            // Read the value back to check that it was added correctly.

            val valueFromTriplestore = getValue(
                resourceIri = zeitglöckleinIri,
                maybePreviousLastModDate = maybeResourceLastModDate,
                propertyIriForGravsearch = propertyIri,
                propertyIriInResult = propertyIri,
                expectedValueIri = zeitglöckleinCommentWithStandoffIri.get,
                requestingUser = incunabulaUser
            )

            valueFromTriplestore.valueContent match {
                case savedValue: TextValueContentV2 =>
                    savedValue.valueHasString should ===(valueHasString)
                    savedValue.standoffAndMapping should ===(standoffAndMapping)

                case _ => throw AssertionException(s"Expected text value, got $valueFromTriplestore")
            }


            // There should be a link value for a standoff link.

            val resource = getResourceWithValues(
                resourceIri = zeitglöckleinIri,
                propertyIrisForGravsearch = Seq(OntologyConstants.KnoraApiV2WithValueObjects.HasStandoffLinkTo.toSmartIri),
                requestingUser = incunabulaUser
            )

            val standoffLinkValues: Seq[ReadValueV2] = getValuesFromResource(
                resource = resource,
                propertyIriInResult = OntologyConstants.KnoraApiV2WithValueObjects.HasStandoffLinkToValue.toSmartIri
            )

            assert(standoffLinkValues.size == 1)
            val standoffLinkValueFromTriplestore = standoffLinkValues.head

            standoffLinkValueFromTriplestore.valueContent match {
                case linkValueContentV2: LinkValueContentV2 =>
                    standoffLinkValueIri.set(standoffLinkValueFromTriplestore.valueIri)
                    assert(linkValueContentV2.referredResourceIri == aThingIri)

                case _ => throw AssertionException(s"Expected a link value, got $standoffLinkValueFromTriplestore")
            }
        }

        "not update a text value, duplicating an existing text value (without submitting standoff)" in {
            val valueHasString = "this is a text value that has a comment"
            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book_comment".toSmartIri

            actorUnderTest ! UpdateValueRequestV2(
                UpdateValueV2(
                    resourceIri = zeitglöckleinIri,
                    resourceClassIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book".toSmartIri,
                    propertyIri = propertyIri,
                    valueIri = zeitglöckleinCommentWithoutStandoffIri.get,
                    valueContent = TextValueContentV2(
                        ontologySchema = ApiV2WithValueObjects,
                        valueHasString = valueHasString
                    )
                ),
                requestingUser = incunabulaUser,
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[DuplicateValueException] should ===(true)
            }
        }

        "create a second text value with standoff" in {
            val valueHasString = "Comment 1ac"

            val standoffAndMapping = Some(StandoffAndMapping(
                standoff = sampleStandoff,
                mappingIri = "http://rdfh.ch/standoff/mappings/StandardMapping",
                mapping = standardMapping.get
            ))

            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book_comment".toSmartIri
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(zeitglöckleinIri, incunabulaUser)

            actorUnderTest ! CreateValueRequestV2(
                CreateValueV2(
                    resourceIri = zeitglöckleinIri,
                    resourceClassIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book".toSmartIri,
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
                case createValueResponse: CreateValueResponseV2 => zeitglöckleinSecondCommentWithStandoffIri.set(createValueResponse.valueIri)
            }

            // Read the value back to check that it was added correctly.

            val valueFromTriplestore = getValue(
                resourceIri = zeitglöckleinIri,
                maybePreviousLastModDate = maybeResourceLastModDate,
                propertyIriForGravsearch = propertyIri,
                propertyIriInResult = propertyIri,
                expectedValueIri = zeitglöckleinSecondCommentWithStandoffIri.get,
                requestingUser = incunabulaUser
            )

            valueFromTriplestore.valueContent match {
                case savedValue: TextValueContentV2 =>
                    savedValue.valueHasString should ===(valueHasString)
                    savedValue.standoffAndMapping should ===(standoffAndMapping)

                case _ => throw AssertionException(s"Expected text value, got $valueFromTriplestore")
            }
        }

        "not update a text value, duplicating an existing text value (submitting standoff)" in {
            val valueHasString = "Comment 1ac"

            val standoffAndMapping = Some(StandoffAndMapping(
                standoff = sampleStandoff,
                mappingIri = "http://rdfh.ch/standoff/mappings/StandardMapping",
                mapping = standardMapping.get
            ))

            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book_comment".toSmartIri

            actorUnderTest ! UpdateValueRequestV2(
                UpdateValueV2(
                    resourceIri = zeitglöckleinIri,
                    resourceClassIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book".toSmartIri,
                    propertyIri = propertyIri,
                    valueIri = zeitglöckleinCommentWithStandoffIri.get,
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
                case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[DuplicateValueException] should ===(true)
            }
        }

        "update a text value, changing only the standoff" in {
            val valueHasString = "Comment 1ac"

            val standoffAndMapping = Some(StandoffAndMapping(
                standoff = sampleStandoffModified,
                mappingIri = "http://rdfh.ch/standoff/mappings/StandardMapping",
                mapping = standardMapping.get
            ))

            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book_comment".toSmartIri
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(zeitglöckleinIri, incunabulaUser)

            actorUnderTest ! UpdateValueRequestV2(
                UpdateValueV2(
                    resourceIri = zeitglöckleinIri,
                    resourceClassIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book".toSmartIri,
                    propertyIri = propertyIri,
                    valueIri = zeitglöckleinSecondCommentWithStandoffIri.get,
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
                case updateValueResponse: UpdateValueResponseV2 => zeitglöckleinSecondCommentWithStandoffIri.set(updateValueResponse.valueIri)
            }

            // Read the value back to check that it was added correctly.

            val valueFromTriplestore = getValue(
                resourceIri = zeitglöckleinIri,
                maybePreviousLastModDate = maybeResourceLastModDate,
                propertyIriForGravsearch = propertyIri,
                propertyIriInResult = propertyIri,
                expectedValueIri = zeitglöckleinSecondCommentWithStandoffIri.get,
                requestingUser = incunabulaUser
            )

            valueFromTriplestore.valueContent match {
                case savedValue: TextValueContentV2 =>
                    savedValue.valueHasString should ===(valueHasString)
                    savedValue.standoffAndMapping should ===(standoffAndMapping)

                case _ => throw AssertionException(s"Expected text value, got $valueFromTriplestore")
            }
        }

        "not update a text value so it differs only from an existing value in that it has different standoff" in {
            val valueHasString = "Comment 1ac"

            val standoffAndMapping = Some(StandoffAndMapping(
                standoff = sampleStandoffModified,
                mappingIri = "http://rdfh.ch/standoff/mappings/StandardMapping",
                mapping = standardMapping.get
            ))

            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book_comment".toSmartIri

            actorUnderTest ! UpdateValueRequestV2(
                UpdateValueV2(
                    resourceIri = zeitglöckleinIri,
                    resourceClassIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book".toSmartIri,
                    propertyIri = propertyIri,
                    valueIri = zeitglöckleinCommentWithStandoffIri.get,
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
                case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[DuplicateValueException] should ===(true)
            }
        }

        "not update a text value without changing it (without submitting standoff)" in {
            val valueHasString = "This updated comment has no standoff"
            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book_comment".toSmartIri

            actorUnderTest ! UpdateValueRequestV2(
                UpdateValueV2(
                    resourceIri = zeitglöckleinIri,
                    resourceClassIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book".toSmartIri,
                    propertyIri = propertyIri,
                    valueIri = zeitglöckleinCommentWithoutStandoffIri.get,
                    valueContent = TextValueContentV2(
                        ontologySchema = ApiV2WithValueObjects,
                        valueHasString = valueHasString
                    )
                ),
                requestingUser = incunabulaUser,
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[DuplicateValueException] should ===(true)
            }
        }

        "update a decimal value" in {
            val resourceIri: IRI = aThingIri
            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasDecimal".toSmartIri
            val valueHasDecimal = BigDecimal("3.1415926")
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUser1)

            actorUnderTest ! UpdateValueRequestV2(
                UpdateValueV2(
                    resourceIri = resourceIri,
                    resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
                    propertyIri = propertyIri,
                    valueIri = decimalValueIri.get,
                    valueContent = DecimalValueContentV2(
                        ontologySchema = ApiV2WithValueObjects,
                        valueHasDecimal = valueHasDecimal
                    )
                ),
                requestingUser = anythingUser1,
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case updateValueResponse: UpdateValueResponseV2 => decimalValueIri.set(updateValueResponse.valueIri)
            }

            // Read the value back to check that it was added correctly.

            val valueFromTriplestore = getValue(
                resourceIri = resourceIri,
                maybePreviousLastModDate = maybeResourceLastModDate,
                propertyIriForGravsearch = propertyIri,
                propertyIriInResult = propertyIri,
                expectedValueIri = decimalValueIri.get,
                requestingUser = anythingUser1
            )

            valueFromTriplestore.valueContent match {
                case savedValue: DecimalValueContentV2 => savedValue.valueHasDecimal should ===(valueHasDecimal)
                case _ => throw AssertionException(s"Expected decimal value, got $valueFromTriplestore")
            }
        }

        "not update a decimal value without changing it" in {
            val resourceIri: IRI = aThingIri
            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasDecimal".toSmartIri
            val valueHasDecimal = BigDecimal("3.1415926")

            actorUnderTest ! UpdateValueRequestV2(
                UpdateValueV2(
                    resourceIri = resourceIri,
                    resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
                    propertyIri = propertyIri,
                    valueIri = decimalValueIri.get,
                    valueContent = DecimalValueContentV2(
                        ontologySchema = ApiV2WithValueObjects,
                        valueHasDecimal = valueHasDecimal
                    )
                ),
                requestingUser = anythingUser1,
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[DuplicateValueException] should ===(true)
            }
        }

        "update a date value" in {
            val resourceIri: IRI = aThingIri
            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasDate".toSmartIri
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUser1)

            val submittedValueContent = DateValueContentV2(
                ontologySchema = ApiV2WithValueObjects,
                valueHasCalendar = CalendarNameGregorian,
                valueHasStartJDN = 2264908,
                valueHasStartPrecision = DatePrecisionYear,
                valueHasEndJDN = 2265272,
                valueHasEndPrecision = DatePrecisionYear
            )

            actorUnderTest ! UpdateValueRequestV2(
                UpdateValueV2(
                    resourceIri = resourceIri,
                    resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
                    propertyIri = propertyIri,
                    valueIri = dateValueIri.get,
                    valueContent = submittedValueContent
                ),
                requestingUser = anythingUser1,
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case updateValueResponse: UpdateValueResponseV2 => dateValueIri.set(updateValueResponse.valueIri)
            }

            // Read the value back to check that it was added correctly.

            val valueFromTriplestore = getValue(
                resourceIri = resourceIri,
                maybePreviousLastModDate = maybeResourceLastModDate,
                propertyIriForGravsearch = propertyIri,
                propertyIriInResult = propertyIri,
                expectedValueIri = dateValueIri.get,
                requestingUser = anythingUser1
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

        "not update a date value without changing it" in {
            val resourceIri: IRI = aThingIri
            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasDate".toSmartIri

            val submittedValueContent = DateValueContentV2(
                ontologySchema = ApiV2WithValueObjects,
                valueHasCalendar = CalendarNameGregorian,
                valueHasStartJDN = 2264908,
                valueHasStartPrecision = DatePrecisionYear,
                valueHasEndJDN = 2265272,
                valueHasEndPrecision = DatePrecisionYear
            )

            actorUnderTest ! UpdateValueRequestV2(
                UpdateValueV2(
                    resourceIri = resourceIri,
                    resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
                    propertyIri = propertyIri,
                    valueIri = dateValueIri.get,
                    valueContent = submittedValueContent
                ),
                requestingUser = anythingUser1,
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[DuplicateValueException] should ===(true)
            }
        }

        "update a boolean value" in {
            val resourceIri: IRI = aThingIri
            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasBoolean".toSmartIri
            val valueHasBoolean = false
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUser1)

            actorUnderTest ! UpdateValueRequestV2(
                UpdateValueV2(
                    resourceIri = resourceIri,
                    resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
                    propertyIri = propertyIri,
                    valueIri = booleanValueIri.get,
                    valueContent = BooleanValueContentV2(
                        ontologySchema = ApiV2WithValueObjects,
                        valueHasBoolean = valueHasBoolean
                    )
                ),
                requestingUser = anythingUser1,
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case updateValueResponse: UpdateValueResponseV2 => booleanValueIri.set(updateValueResponse.valueIri)
            }

            // Read the value back to check that it was added correctly.

            val valueFromTriplestore = getValue(
                resourceIri = resourceIri,
                maybePreviousLastModDate = maybeResourceLastModDate,
                propertyIriForGravsearch = propertyIri,
                propertyIriInResult = propertyIri,
                expectedValueIri = booleanValueIri.get,
                requestingUser = anythingUser1
            )

            valueFromTriplestore.valueContent match {
                case savedValue: BooleanValueContentV2 => savedValue.valueHasBoolean should ===(valueHasBoolean)
                case _ => throw AssertionException(s"Expected boolean value, got $valueFromTriplestore")
            }
        }

        "not update a boolean value without changing it" in {
            val resourceIri: IRI = aThingIri
            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasBoolean".toSmartIri
            val valueHasBoolean = false

            actorUnderTest ! UpdateValueRequestV2(
                UpdateValueV2(
                    resourceIri = resourceIri,
                    resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
                    propertyIri = propertyIri,
                    valueIri = booleanValueIri.get,
                    valueContent = BooleanValueContentV2(
                        ontologySchema = ApiV2WithValueObjects,
                        valueHasBoolean = valueHasBoolean
                    )
                ),
                requestingUser = anythingUser1,
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[DuplicateValueException] should ===(true)
            }
        }

        "update a geometry value" in {
            val resourceIri: IRI = aThingIri
            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasGeometry".toSmartIri
            val valueHasGeometry = """{"status":"active","lineColor":"#ff3334","lineWidth":2,"points":[{"x":0.08098591549295775,"y":0.16741071428571427},{"x":0.7394366197183099,"y":0.7299107142857143}],"type":"rectangle","original_index":0}"""
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUser1)

            actorUnderTest ! UpdateValueRequestV2(
                UpdateValueV2(
                    resourceIri = resourceIri,
                    resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
                    propertyIri = propertyIri,
                    valueIri = geometryValueIri.get,
                    valueContent = GeomValueContentV2(
                        ontologySchema = ApiV2WithValueObjects,
                        valueHasGeometry = valueHasGeometry
                    )
                ),
                requestingUser = anythingUser1,
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case updateValueResponse: UpdateValueResponseV2 => geometryValueIri.set(updateValueResponse.valueIri)
            }

            // Read the value back to check that it was added correctly.

            val valueFromTriplestore = getValue(
                resourceIri = resourceIri,
                maybePreviousLastModDate = maybeResourceLastModDate,
                propertyIriForGravsearch = propertyIri,
                propertyIriInResult = propertyIri,
                expectedValueIri = geometryValueIri.get,
                requestingUser = anythingUser1
            )

            valueFromTriplestore.valueContent match {
                case savedValue: GeomValueContentV2 => savedValue.valueHasGeometry should ===(valueHasGeometry)
                case _ => throw AssertionException(s"Expected geometry value, got $valueFromTriplestore")
            }
        }

        "not update a geometry value without changing it" in {
            val resourceIri: IRI = aThingIri
            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasGeometry".toSmartIri
            val valueHasGeometry = """{"status":"active","lineColor":"#ff3334","lineWidth":2,"points":[{"x":0.08098591549295775,"y":0.16741071428571427},{"x":0.7394366197183099,"y":0.7299107142857143}],"type":"rectangle","original_index":0}"""

            actorUnderTest ! UpdateValueRequestV2(
                UpdateValueV2(
                    resourceIri = resourceIri,
                    resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
                    propertyIri = propertyIri,
                    valueIri = geometryValueIri.get,
                    valueContent = GeomValueContentV2(
                        ontologySchema = ApiV2WithValueObjects,
                        valueHasGeometry = valueHasGeometry
                    )
                ),
                requestingUser = anythingUser1,
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[DuplicateValueException] should ===(true)
            }
        }

        "update an interval value" in {
            val resourceIri: IRI = aThingIri
            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInterval".toSmartIri
            val valueHasIntervalStart = BigDecimal("1.23")
            val valueHasIntervalEnd = BigDecimal("3.45")
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUser1)

            actorUnderTest ! UpdateValueRequestV2(
                UpdateValueV2(
                    resourceIri = resourceIri,
                    resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
                    propertyIri = propertyIri,
                    valueIri = intervalValueIri.get,
                    valueContent = IntervalValueContentV2(
                        ontologySchema = ApiV2WithValueObjects,
                        valueHasIntervalStart = valueHasIntervalStart,
                        valueHasIntervalEnd = valueHasIntervalEnd
                    )
                ),
                requestingUser = anythingUser1,
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case updateValueResponse: UpdateValueResponseV2 => intervalValueIri.set(updateValueResponse.valueIri)
            }

            // Read the value back to check that it was added correctly.

            val valueFromTriplestore = getValue(
                resourceIri = resourceIri,
                maybePreviousLastModDate = maybeResourceLastModDate,
                propertyIriForGravsearch = propertyIri,
                propertyIriInResult = propertyIri,
                expectedValueIri = intervalValueIri.get,
                requestingUser = anythingUser1
            )

            valueFromTriplestore.valueContent match {
                case savedValue: IntervalValueContentV2 =>
                    savedValue.valueHasIntervalStart should ===(valueHasIntervalStart)
                    savedValue.valueHasIntervalEnd should ===(valueHasIntervalEnd)

                case _ => throw AssertionException(s"Expected interval value, got $valueFromTriplestore")
            }
        }

        "not update an interval value without changing it" in {
            val resourceIri: IRI = aThingIri
            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInterval".toSmartIri
            val valueHasIntervalStart = BigDecimal("1.23")
            val valueHasIntervalEnd = BigDecimal("3.45")

            actorUnderTest ! UpdateValueRequestV2(
                UpdateValueV2(
                    resourceIri = resourceIri,
                    resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
                    propertyIri = propertyIri,
                    valueIri = intervalValueIri.get,
                    valueContent = IntervalValueContentV2(
                        ontologySchema = ApiV2WithValueObjects,
                        valueHasIntervalStart = valueHasIntervalStart,
                        valueHasIntervalEnd = valueHasIntervalEnd
                    )
                ),
                requestingUser = anythingUser1,
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[DuplicateValueException] should ===(true)
            }
        }

        "update a list value" in {
            val resourceIri: IRI = aThingIri
            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasListItem".toSmartIri
            val valueHasListNode = "http://rdfh.ch/lists/0001/treeList02"
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUser1)

            actorUnderTest ! UpdateValueRequestV2(
                UpdateValueV2(
                    resourceIri = resourceIri,
                    resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
                    propertyIri = propertyIri,
                    valueIri = listValueIri.get,
                    valueContent = HierarchicalListValueContentV2(
                        ontologySchema = ApiV2WithValueObjects,
                        valueHasListNode = valueHasListNode
                    )
                ),
                requestingUser = anythingUser1,
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case updateValueResponse: UpdateValueResponseV2 => listValueIri.set(updateValueResponse.valueIri)
            }

            // Read the value back to check that it was added correctly.

            val valueFromTriplestore = getValue(
                resourceIri = resourceIri,
                maybePreviousLastModDate = maybeResourceLastModDate,
                propertyIriForGravsearch = propertyIri,
                propertyIriInResult = propertyIri,
                expectedValueIri = listValueIri.get,
                requestingUser = anythingUser1
            )

            valueFromTriplestore.valueContent match {
                case savedValue: HierarchicalListValueContentV2 =>
                    savedValue.valueHasListNode should ===(valueHasListNode)

                case _ => throw AssertionException(s"Expected list value, got $valueFromTriplestore")
            }
        }

        "not update a list value without changing it" in {
            val resourceIri: IRI = aThingIri
            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasListItem".toSmartIri
            val valueHasListNode = "http://rdfh.ch/lists/0001/treeList02"

            actorUnderTest ! UpdateValueRequestV2(
                UpdateValueV2(
                    resourceIri = resourceIri,
                    resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
                    propertyIri = propertyIri,
                    valueIri = listValueIri.get,
                    valueContent = HierarchicalListValueContentV2(
                        ontologySchema = ApiV2WithValueObjects,
                        valueHasListNode = valueHasListNode
                    )
                ),
                requestingUser = anythingUser1,
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[DuplicateValueException] should ===(true)
            }
        }

        "not update a list value with the IRI of a nonexistent list node" in {
            val resourceIri: IRI = aThingIri
            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasListItem".toSmartIri
            val valueHasListNode = "http://rdfh.ch/lists/0001/nonexistent"

            actorUnderTest ! UpdateValueRequestV2(
                UpdateValueV2(
                    resourceIri = resourceIri,
                    resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
                    propertyIri = propertyIri,
                    valueIri = listValueIri.get,
                    valueContent = HierarchicalListValueContentV2(
                        ontologySchema = ApiV2WithValueObjects,
                        valueHasListNode = valueHasListNode
                    )
                ),
                requestingUser = anythingUser1,
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[NotFoundException] should ===(true)
            }
        }

        "update a color value" in {
            val resourceIri: IRI = aThingIri
            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasColor".toSmartIri
            val valueHasColor = "#ff3334"
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUser1)

            actorUnderTest ! UpdateValueRequestV2(
                UpdateValueV2(
                    resourceIri = resourceIri,
                    resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
                    propertyIri = propertyIri,
                    valueIri = colorValueIri.get,
                    valueContent = ColorValueContentV2(
                        ontologySchema = ApiV2WithValueObjects,
                        valueHasColor = valueHasColor
                    )
                ),
                requestingUser = anythingUser1,
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case updateValueResponse: UpdateValueResponseV2 => colorValueIri.set(updateValueResponse.valueIri)
            }

            // Read the value back to check that it was added correctly.

            val valueFromTriplestore = getValue(
                resourceIri = resourceIri,
                maybePreviousLastModDate = maybeResourceLastModDate,
                propertyIriForGravsearch = propertyIri,
                propertyIriInResult = propertyIri,
                expectedValueIri = colorValueIri.get,
                requestingUser = anythingUser1
            )

            valueFromTriplestore.valueContent match {
                case savedValue: ColorValueContentV2 =>
                    savedValue.valueHasColor should ===(valueHasColor)

                case _ => throw AssertionException(s"Expected color value, got $valueFromTriplestore")
            }
        }

        "not update a color value without changing it" in {
            val resourceIri: IRI = aThingIri
            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasColor".toSmartIri
            val valueHasColor = "#ff3334"

            actorUnderTest ! UpdateValueRequestV2(
                UpdateValueV2(
                    resourceIri = resourceIri,
                    resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
                    propertyIri = propertyIri,
                    valueIri = colorValueIri.get,
                    valueContent = ColorValueContentV2(
                        ontologySchema = ApiV2WithValueObjects,
                        valueHasColor = valueHasColor
                    )
                ),
                requestingUser = anythingUser1,
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[DuplicateValueException] should ===(true)
            }
        }

        "update a URI value" in {
            val resourceIri: IRI = aThingIri
            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasUri".toSmartIri
            val valueHasUri = "https://en.wikipedia.org"
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUser1)

            actorUnderTest ! UpdateValueRequestV2(
                UpdateValueV2(
                    resourceIri = resourceIri,
                    resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
                    propertyIri = propertyIri,
                    valueIri = uriValueIri.get,
                    valueContent = UriValueContentV2(
                        ontologySchema = ApiV2WithValueObjects,
                        valueHasUri = valueHasUri
                    )
                ),
                requestingUser = anythingUser1,
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case updateValueResponse: UpdateValueResponseV2 => uriValueIri.set(updateValueResponse.valueIri)
            }

            // Read the value back to check that it was added correctly.

            val valueFromTriplestore = getValue(
                resourceIri = resourceIri,
                maybePreviousLastModDate = maybeResourceLastModDate,
                propertyIriForGravsearch = propertyIri,
                propertyIriInResult = propertyIri,
                expectedValueIri = uriValueIri.get,
                requestingUser = anythingUser1
            )

            valueFromTriplestore.valueContent match {
                case savedValue: UriValueContentV2 =>
                    savedValue.valueHasUri should ===(valueHasUri)

                case _ => throw AssertionException(s"Expected URI value, got $valueFromTriplestore")
            }
        }

        "not update a URI value without changing it" in {
            val resourceIri: IRI = aThingIri
            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasUri".toSmartIri
            val valueHasUri = "https://en.wikipedia.org"

            actorUnderTest ! UpdateValueRequestV2(
                UpdateValueV2(
                    resourceIri = resourceIri,
                    resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
                    propertyIri = propertyIri,
                    valueIri = uriValueIri.get,
                    valueContent = UriValueContentV2(
                        ontologySchema = ApiV2WithValueObjects,
                        valueHasUri = valueHasUri
                    )
                ),
                requestingUser = anythingUser1,
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[DuplicateValueException] should ===(true)
            }
        }

        "update a geoname value" in {
            val resourceIri: IRI = aThingIri
            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasGeoname".toSmartIri
            val valueHasGeonameCode = "2988507"
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUser1)

            actorUnderTest ! UpdateValueRequestV2(
                UpdateValueV2(
                    resourceIri = resourceIri,
                    resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
                    propertyIri = propertyIri,
                    valueIri = geonameValueIri.get,
                    valueContent = GeonameValueContentV2(
                        ontologySchema = ApiV2WithValueObjects,
                        valueHasGeonameCode = valueHasGeonameCode
                    )
                ),
                requestingUser = anythingUser1,
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case updateValueResponse: UpdateValueResponseV2 => geonameValueIri.set(updateValueResponse.valueIri)
            }

            // Read the value back to check that it was added correctly.

            val valueFromTriplestore = getValue(
                resourceIri = resourceIri,
                maybePreviousLastModDate = maybeResourceLastModDate,
                propertyIriForGravsearch = propertyIri,
                propertyIriInResult = propertyIri,
                expectedValueIri = geonameValueIri.get,
                requestingUser = anythingUser1
            )

            valueFromTriplestore.valueContent match {
                case savedValue: GeonameValueContentV2 =>
                    savedValue.valueHasGeonameCode should ===(valueHasGeonameCode)

                case _ => throw AssertionException(s"Expected GeoNames value, got $valueFromTriplestore")
            }
        }

        "not update a geoname value without changing it" in {
            val resourceIri: IRI = aThingIri
            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasGeoname".toSmartIri
            val valueHasGeonameCode = "2988507"

            actorUnderTest ! UpdateValueRequestV2(
                UpdateValueV2(
                    resourceIri = resourceIri,
                    resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
                    propertyIri = propertyIri,
                    valueIri = geonameValueIri.get,
                    valueContent = GeonameValueContentV2(
                        ontologySchema = ApiV2WithValueObjects,
                        valueHasGeonameCode = valueHasGeonameCode
                    )
                ),
                requestingUser = anythingUser1,
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[DuplicateValueException] should ===(true)
            }
        }

        "update a link between two resources" in {
            val resourceIri: IRI = "http://rdfh.ch/cb1a74e3e2f6"
            val linkPropertyIri: SmartIri = OntologyConstants.KnoraApiV2WithValueObjects.HasLinkTo.toSmartIri
            val linkValuePropertyIri: SmartIri = OntologyConstants.KnoraApiV2WithValueObjects.HasLinkToValue.toSmartIri
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, incunabulaUser)

            val updateValueRequest = UpdateValueRequestV2(
                UpdateValueV2(
                    resourceIri = resourceIri,
                    resourceClassIri = OntologyConstants.KnoraApiV2WithValueObjects.LinkObj.toSmartIri,
                    propertyIri = linkValuePropertyIri,
                    valueIri = linkValueIri.get,
                    valueContent = LinkValueContentV2(
                        ontologySchema = ApiV2WithValueObjects,
                        referredResourceIri = generationeIri
                    )
                ),
                requestingUser = incunabulaUser,
                apiRequestID = UUID.randomUUID
            )

            actorUnderTest ! updateValueRequest

            expectMsgPF(timeout) {
                case updateValueResponse: UpdateValueResponseV2 => linkValueIri.set(updateValueResponse.valueIri)
            }

            val valueFromTriplestore = getValue(
                resourceIri = resourceIri,
                maybePreviousLastModDate = maybeResourceLastModDate,
                propertyIriForGravsearch = linkPropertyIri,
                propertyIriInResult = linkValuePropertyIri,
                expectedValueIri = linkValueIri.get,
                requestingUser = incunabulaUser
            )

            valueFromTriplestore match {
                case readLinkValueV2: ReadLinkValueV2 =>
                    readLinkValueV2.valueContent.referredResourceIri should ===(generationeIri)
                    readLinkValueV2.valueHasRefCount should ===(1)

                case _ => throw AssertionException(s"Expected link value, got $valueFromTriplestore")
            }
        }

        "not update a link without changing it" in {
            val resourceIri: IRI = "http://rdfh.ch/cb1a74e3e2f6"
            val linkValuePropertyIri: SmartIri = OntologyConstants.KnoraApiV2WithValueObjects.HasLinkToValue.toSmartIri

            val updateValueRequest = UpdateValueRequestV2(
                UpdateValueV2(
                    resourceIri = resourceIri,
                    resourceClassIri = OntologyConstants.KnoraApiV2WithValueObjects.LinkObj.toSmartIri,
                    propertyIri = linkValuePropertyIri,
                    valueIri = linkValueIri.get,
                    valueContent = LinkValueContentV2(
                        ontologySchema = ApiV2WithValueObjects,
                        referredResourceIri = generationeIri
                    )
                ),
                requestingUser = incunabulaUser,
                apiRequestID = UUID.randomUUID
            )

            actorUnderTest ! updateValueRequest

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[DuplicateValueException] should ===(true)
            }
        }

        "not update a standoff link directly" in {
            actorUnderTest ! UpdateValueRequestV2(
                UpdateValueV2(
                    resourceIri = zeitglöckleinIri,
                    resourceClassIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book".toSmartIri,
                    propertyIri = OntologyConstants.KnoraApiV2WithValueObjects.HasStandoffLinkToValue.toSmartIri,
                    valueIri = zeitglöckleinCommentWithStandoffIri.get,
                    valueContent = LinkValueContentV2(
                        ontologySchema = ApiV2WithValueObjects,
                        referredResourceIri = generationeIri
                    )
                ),
                requestingUser = SharedTestDataADM.superUser,
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[BadRequestException] should ===(true)
            }
        }

        "not update a still image file value without changing it" in {
            val resourceIri: IRI = aThingPictureIri

            val valueContent = StillImageFileValueContentV2(
                ontologySchema = ApiV2WithValueObjects,
                fileValue = FileValueV2(
                    internalFilename = "IQUO3t1AABm-FSLC0vNvVpr.jp2",
                    internalMimeType = "image/jp2",
                    originalFilename = "test.tiff",
                    originalMimeType = "image/tiff"
                ),
                dimX = 512,
                dimY = 256
            )

            val updateValueRequest = UpdateValueRequestV2(
                UpdateValueV2(
                    resourceIri = resourceIri,
                    resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#ThingPicture".toSmartIri,
                    propertyIri = OntologyConstants.KnoraApiV2WithValueObjects.HasStillImageFileValue.toSmartIri,
                    valueIri = stillImageFileValueIri.get,
                    valueContent = valueContent
                ),
                requestingUser = anythingUser1,
                apiRequestID = UUID.randomUUID
            )

            actorUnderTest ! updateValueRequest

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[DuplicateValueException] should ===(true)
            }
        }

        "update a still image file value" in {
            val resourceIri: IRI = aThingPictureIri
            val propertyIri: SmartIri = OntologyConstants.KnoraApiV2WithValueObjects.HasStillImageFileValue.toSmartIri
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUser1)

            // Get the value before update.
            val previousValueFromTriplestore: ReadValueV2 = getValue(
                resourceIri = resourceIri,
                maybePreviousLastModDate = maybeResourceLastModDate,
                propertyIriForGravsearch = propertyIri,
                propertyIriInResult = propertyIri,
                expectedValueIri = stillImageFileValueIri.get,
                requestingUser = anythingUser1,
                checkLastModDateChanged = false
            )

            // Update the value.

            val valueContent = StillImageFileValueContentV2(
                ontologySchema = ApiV2WithValueObjects,
                fileValue = FileValueV2(
                    internalFilename = "updated-filename.jp2",
                    internalMimeType = "image/jp2",
                    originalFilename = "test.tiff",
                    originalMimeType = "image/tiff"
                ),
                dimX = 512,
                dimY = 256
            )

            actorUnderTest ! UpdateValueRequestV2(
                UpdateValueV2(
                    resourceIri = resourceIri,
                    resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#ThingPicture".toSmartIri,
                    propertyIri = propertyIri,
                    valueIri = stillImageFileValueIri.get,
                    valueContent = valueContent
                ),
                requestingUser = anythingUser1,
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case updateValueResponse: UpdateValueResponseV2 => stillImageFileValueIri.set(updateValueResponse.valueIri)
            }

            // Read the value back to check that it was added correctly.

            val updatedValueFromTriplestore = getValue(
                resourceIri = resourceIri,
                maybePreviousLastModDate = maybeResourceLastModDate,
                propertyIriForGravsearch = propertyIri,
                propertyIriInResult = propertyIri,
                expectedValueIri = stillImageFileValueIri.get,
                requestingUser = anythingUser1
            )

            updatedValueFromTriplestore.valueContent match {
                case savedValue: StillImageFileValueContentV2 =>
                    savedValue should ===(valueContent)
                    updatedValueFromTriplestore.permissions should ===(previousValueFromTriplestore.permissions)

                case _ => throw AssertionException(s"Expected still image file value, got $updatedValueFromTriplestore")
            }

        }

        "not delete a value if the requesting user does not have DeletePermission on the value" in {
            val resourceIri: IRI = aThingIri
            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri

            actorUnderTest ! DeleteValueRequestV2(
                resourceIri = resourceIri,
                propertyIri = propertyIri,
                valueIri = intValueIri.get,
                deleteComment = Some("this value was incorrect"),
                requestingUser = anythingUser2,
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[ForbiddenException] should ===(true)
            }
        }

        "delete an integer value" in {
            val resourceIri: IRI = aThingIri
            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUser1)

            actorUnderTest ! DeleteValueRequestV2(
                resourceIri = resourceIri,
                propertyIri = propertyIri,
                valueIri = intValueIri.get,
                deleteComment = Some("this value was incorrect"),
                requestingUser = anythingUser1,
                apiRequestID = UUID.randomUUID
            )

            expectMsgType[SuccessResponseV2](timeout)

            checkValueIsDeleted(resourceIri = resourceIri,
                maybePreviousLastModDate = maybeResourceLastModDate,
                propertyIriForGravsearch = propertyIri,
                propertyIriInResult = propertyIri,
                valueIri = intValueIri.get,
                requestingUser = anythingUser1)
        }

        "not delete a standoff link directly" in {
            actorUnderTest ! DeleteValueRequestV2(
                resourceIri = zeitglöckleinIri,
                propertyIri = OntologyConstants.KnoraApiV2WithValueObjects.HasStandoffLinkToValue.toSmartIri,
                valueIri = standoffLinkValueIri.get,
                requestingUser = SharedTestDataADM.superUser,
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[BadRequestException] should ===(true)
            }
        }

        "delete a text value with a standoff link" in {
            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book_comment".toSmartIri
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(zeitglöckleinIri, incunabulaUser)

            actorUnderTest ! DeleteValueRequestV2(
                resourceIri = zeitglöckleinIri,
                propertyIri = propertyIri,
                valueIri = zeitglöckleinCommentWithStandoffIri.get,
                deleteComment = Some("this value was incorrect"),
                requestingUser = incunabulaUser,
                apiRequestID = UUID.randomUUID
            )

            expectMsgType[SuccessResponseV2](timeout)

            checkValueIsDeleted(resourceIri = zeitglöckleinIri,
                maybePreviousLastModDate = maybeResourceLastModDate,
                propertyIriForGravsearch = propertyIri,
                propertyIriInResult = propertyIri,
                valueIri = zeitglöckleinCommentWithStandoffIri.get,
                requestingUser = incunabulaUser)

            // There should be no standoff link values left in the resource.

            val resource = getResourceWithValues(
                resourceIri = zeitglöckleinIri,
                propertyIrisForGravsearch = Seq(OntologyConstants.KnoraApiV2WithValueObjects.HasStandoffLinkTo.toSmartIri),
                requestingUser = incunabulaUser
            )

            assert(resource.values.get(OntologyConstants.KnoraApiV2WithValueObjects.HasStandoffLinkToValue.toSmartIri).isEmpty)
        }

        "delete a link between two resources" in {
            val resourceIri: IRI = "http://rdfh.ch/cb1a74e3e2f6"
            val linkPropertyIri: SmartIri = OntologyConstants.KnoraApiV2WithValueObjects.HasLinkTo.toSmartIri
            val linkValuePropertyIri: SmartIri = OntologyConstants.KnoraApiV2WithValueObjects.HasLinkToValue.toSmartIri
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUser1)

            actorUnderTest ! DeleteValueRequestV2(
                resourceIri = resourceIri,
                propertyIri = linkValuePropertyIri,
                valueIri = linkValueIri.get,
                requestingUser = incunabulaUser,
                apiRequestID = UUID.randomUUID
            )

            expectMsgType[SuccessResponseV2](timeout)

            checkValueIsDeleted(resourceIri = resourceIri,
                maybePreviousLastModDate = maybeResourceLastModDate,
                propertyIriForGravsearch = linkPropertyIri,
                propertyIriInResult = linkValuePropertyIri,
                valueIri = intValueIri.get,
                requestingUser = anythingUser1)
        }

        "not delete a value if the property's cardinality doesn't allow it" in {
            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#title".toSmartIri

            actorUnderTest ! DeleteValueRequestV2(
                resourceIri = zeitglöckleinIri,
                propertyIri = propertyIri,
                valueIri = "http://rdfh.ch/c5058f3a/values/c3295339",
                requestingUser = incunabulaCreatorUser,
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[OntologyConstraintException] should ===(true)
            }
        }

    }
}
