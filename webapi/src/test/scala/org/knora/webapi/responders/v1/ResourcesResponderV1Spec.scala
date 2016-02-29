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

package org.knora.webapi.responders.v1

import java.util.UUID

import akka.actor.ActorDSL._
import akka.actor.Props
import akka.testkit.{ImplicitSender, TestActorRef}
import org.knora.webapi._
import org.knora.webapi.messages.v1respondermessages.resourcemessages._
import org.knora.webapi.messages.v1respondermessages.sipimessages._
import org.knora.webapi.messages.v1respondermessages.triplestoremessages._
import org.knora.webapi.messages.v1respondermessages.usermessages.{UserDataV1, UserProfileV1}
import org.knora.webapi.messages.v1respondermessages.valuemessages._
import org.knora.webapi.responders._
import org.knora.webapi.store._
import org.knora.webapi.util.ActorUtil._
import org.knora.webapi.util._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._

/**
  * Static data for testing [[ResourcesResponderV1]].
  */
object ResourcesResponderV1Spec {

    // A test UserDataV1.
    private val userData = UserDataV1(
        email = Some("test@test.ch"),
        lastname = Some("Test"),
        firstname = Some("User"),
        username = Some("testuser"),
        token = None,
        user_id = Some("http://data.knora.org/users/b83acc5f05"),
        lang = "de"
    )

    // A test UserProfileV1.
    private val userProfile = UserProfileV1(
        projects = Vector("http://data.knora.org/projects/77275339"),
        groups = Nil,
        userData = userData
    )
}


/**
  * Tests [[ResourcesResponderV1]].
  */
class ResourcesResponderV1Spec extends CoreSpec() with ImplicitSender {


    /**
      * Imitates the Sipi server by returning a [[SipiResponderConversionResponseV1]] representing an image conversion request.
      *
      * @param conversionRequest the conversion request to be handled.
      * @return a [[SipiResponderConversionResponseV1]] imitating the answer from Sipi.
      */
    private def imageConversionResponse(conversionRequest: SipiResponderConversionRequestV1): Future[SipiResponderConversionResponseV1] = {

        // delete tmp file (depending on the kind of request given: only necessary if Knora stored the file - non GUI-case)
        def deleteTmpFile(conversionRequest: SipiResponderConversionRequestV1): Unit = {
            conversionRequest match {
                case (conversionPathRequest: SipiResponderConversionPathRequestV1) =>
                    // a tmp file has been created by the resources route (non GUI-case), delete it
                    InputValidation.deleteFileFromTmpLocation(conversionPathRequest.source)
                case _ => ()
            }
        }

        val originalFilename = conversionRequest.originalFilename
        val originalMimeType: String = conversionRequest.originalMimeType

        val fileValuesV1 = Vector(StillImageFileValueV1(// full representation
            internalMimeType = "image/jp2",
            originalFilename = originalFilename,
            originalMimeType = Some(originalMimeType),
            dimX = 800,
            dimY = 800,
            internalFilename = "full.jp2",
            qualityLevel = 100,
            qualityName = Some("full")
        ),
            StillImageFileValueV1(// thumbnail representation
                internalMimeType = "image/jpeg",
                originalFilename = originalFilename,
                originalMimeType = Some(originalMimeType),
                dimX = 80,
                dimY = 80,
                internalFilename = "thumb.jpg",
                qualityLevel = 10,
                qualityName = Some("thumbnail"),
                isPreview = true
            ))

        deleteTmpFile(conversionRequest)

        Future(SipiResponderConversionResponseV1(fileValuesV1, file_type = SipiConstants.FileType.IMAGE))
    }

    val sipiMock = actor("mocksipi")(new Act {
        become {
            case sipiResponderConversionFileRequest: SipiResponderConversionFileRequestV1 => future2Message(sender(), imageConversionResponse(sipiResponderConversionFileRequest), logger)
            case sipiResponderConversionPathRequest: SipiResponderConversionPathRequestV1 => future2Message(sender(), imageConversionResponse(sipiResponderConversionPathRequest), logger)
        }
    })

    // Construct the actors needed for this test.
    private val actorUnderTest = TestActorRef[ResourcesResponderV1]

    val responderManager = system.actorOf(Props(new TestResponderManagerV1(Map(SIPI_ROUTER_ACTOR_NAME -> sipiMock))), name = RESPONDER_MANAGER_ACTOR_NAME)

    private val storeManager = system.actorOf(Props(new StoreManager with LiveActorMaker), name = STORE_MANAGER_ACTOR_NAME)

    val rdfDataObjects = List(
        RdfDataObject(path = "../knora-ontologies/knora-base.ttl", name = "http://www.knora.org/ontology/knora-base"),
        RdfDataObject(path = "../knora-ontologies/knora-dc.ttl", name = "http://www.knora.org/ontology/dc"),
        RdfDataObject(path = "../knora-ontologies/salsah-gui.ttl", name = "http://www.knora.org/ontology/salsah-gui"),
        RdfDataObject(path = "_test_data/ontologies/incunabula-onto.ttl", name = "http://www.knora.org/ontology/incunabula"),
        RdfDataObject(path = "_test_data/all_data/incunabula-data.ttl", name = "http://www.knora.org/data/incunabula")
    )

    // The default timeout for receiving reply messages from actors.
    private val timeout = 60.seconds

    private val newResourceIri = new MutableTestIri

    private def compareResourceFullResponses(expected: ResourceFullResponseV1, received: ResourceFullResponseV1): Unit = {
        // println(MessageUtil.toSource(received))

        assert(expected.access == received.access, "access does not match")
        assert(expected.userdata == received.userdata, "userdata does not match")

        val expectedResinfoWithSortedPermissions = expected.resinfo.get.copy(
            permissions = expected.resinfo.get.permissions.sorted
        )

        val receivedResInfoWithSortedPermissions = received.resinfo.get.copy(
            permissions = received.resinfo.get.permissions.sorted
        )

        assert(expectedResinfoWithSortedPermissions == receivedResInfoWithSortedPermissions, "resinfo does not match")
        assert(expected.resdata == received.resdata, "resdata does not match")

        // sort permissions in incoming resinfo
        val expectedIncomingWithSortedPermissions = expected.incoming.map {
            case (incomingReference: IncomingV1) => incomingReference.copy(
                resinfo = incomingReference.resinfo.copy(
                    permissions = incomingReference.resinfo.permissions.sorted
                )
            )
        }

        val receivedIncomingWithSortedPermissions = received.incoming.map {
            case (incomingReference: IncomingV1) => incomingReference.copy(
                resinfo = incomingReference.resinfo.copy(
                    permissions = incomingReference.resinfo.permissions.sorted
                )
            )
        }

        assert(expectedIncomingWithSortedPermissions == receivedIncomingWithSortedPermissions, "incoming does not match")

        val sortedExpectedProps = expected.props.get.properties.sortBy(_.pid)
        val sortedReceivedProps = received.props.get.properties.sortBy(_.pid)

        assert(sortedReceivedProps.length == sortedExpectedProps.length, s"\n********** expected these properties:\n${MessageUtil.toSource(sortedExpectedProps)}\n********** received these properties:\n${MessageUtil.toSource(sortedReceivedProps)}")

        sortedExpectedProps.zip(sortedReceivedProps).foreach {
            case (expectedProp: PropertyV1, receivedProp: PropertyV1) =>

                // sort property attributes
                val expectedPropWithSortedAttr = expectedProp.copy(
                    attributes = expectedProp.attributes.sorted
                )

                val receivedPropWithSortedAttr = receivedProp.copy(
                    attributes = receivedProp.attributes.sorted
                )

                assert(expectedPropWithSortedAttr == receivedPropWithSortedAttr, s"These props do not match:\n********** Expected:\n${MessageUtil.toSource(expectedProp)}\n********** Received:\n${MessageUtil.toSource(receivedProp)}")
        }
    }

    private def compareResourceCompoundContextResponses(expected: ResourceContextResponseV1, received: ResourceContextResponseV1): Unit = {
        val expectedContext = expected.resource_context
        val receivedContext = received.resource_context

        assert(expectedContext.firstprop == receivedContext.firstprop, "firstprop does not match")
        assert(expectedContext.context == receivedContext.context, "context does not match")
        assert(expectedContext.preview == receivedContext.preview, "preview does not match")
        assert(expectedContext.canonical_res_id == receivedContext.canonical_res_id, "canonical_res_id does not match")
        assert(expectedContext.region == receivedContext.region, "region does not match")
        assert(expectedContext.res_id == receivedContext.res_id, "res_id does not match")
    }

    private def compareResourcePartOfContextResponses(expected: ResourceContextResponseV1, received: ResourceContextResponseV1): Unit = {
        val expectedContext = expected.resource_context
        val receivedContext = received.resource_context

        val expectexResinfoWithSortedPermissions = expectedContext.resinfo match {
            case Some(resinfo: ResourceInfoV1) =>
                Some(resinfo.copy(
                    permissions = resinfo.permissions.sorted
                ))
            case None => None
        }

        val receivedResinfoWithSortedPermissions = receivedContext.resinfo match {
            case Some(resinfo: ResourceInfoV1) =>
                Some(resinfo.copy(
                    permissions = resinfo.permissions.sorted
                ))
            case None => None
        }

        assert(expectexResinfoWithSortedPermissions == receivedResinfoWithSortedPermissions, "resinfo does not match")
        assert(expectedContext.parent_res_id == receivedContext.parent_res_id, "parent_res_id does not match")
        assert(expectedContext.context == receivedContext.context, "context does not match")
        assert(expectedContext.canonical_res_id == receivedContext.canonical_res_id, "canonical_res_id does not match")

        val expectexParentResinfoWithSortedPermissions = expectedContext.parent_resinfo match {
            case Some(resinfo: ResourceInfoV1) =>
                Some(resinfo.copy(
                    permissions = resinfo.permissions.sorted
                ))
            case None => None
        }

        val receivedParentResinfoWithSortedPermissions = receivedContext.parent_resinfo match {
            case Some(resinfo: ResourceInfoV1) =>
                Some(resinfo.copy(
                    permissions = resinfo.permissions.sorted
                ))
            case None => None
        }

        assert(expectexParentResinfoWithSortedPermissions == receivedParentResinfoWithSortedPermissions, "parent_resinfo does not match")
    }

    val ReiseInsHeiligelandThreeValues = ResourceSearchResponseV1(
        resources = Vector(ResourceSearchResultRowV1(
            id = "http://data.knora.org/2a6221216701",
            value = Vector("Reise ins Heilige Land", "Reysen und wanderschafften durch das Gelobte Land", "Itinerarius"),
            rights = Some(6)
        )),
        userdata = UserDataV1(
            password = None,
            email = Some("test@test.ch"),
            lastname = Some("Test"),
            firstname = Some("User"),
            username = Some("testuser"),
            token = None,
            user_id = Some("http://data.knora.org/users/b83acc5f05"),
            lang = "de"
        )
    )

    val ReiseInsHeiligelandOneValueRestrictedToBook = ResourceSearchResponseV1(
        resources = Vector(ResourceSearchResultRowV1(
            id = "http://data.knora.org/2a6221216701",
            value = Vector("Reise ins Heilige Land"),
            rights = Some(6)
        )),
        userdata = UserDataV1(
            password = None,
            email = Some("test@test.ch"),
            lastname = Some("Test"),
            firstname = Some("User"),
            username = Some("testuser"),
            token = None,
            user_id = Some("http://data.knora.org/users/b83acc5f05"),
            lang = "de"
        )
    )

    private def compareResourceSearchResults(expected: ResourceSearchResponseV1, received: ResourceSearchResponseV1): Unit = {

        assert(expected.resources == received.resources, "resources did not match")
    }

    private def checkResourceCreation(expected: Map[IRI, Seq[ApiValueV1]], received: ResourceCreateResponseV1): Unit = {
        // sort values by their string representation
        val sortedValuesReceived: Map[IRI, Seq[ResourceCreateValueResponseV1]] = received.results.map {
            case (propIri, propValues: Seq[ResourceCreateValueResponseV1]) => (propIri, propValues.sortBy {
                case valueObject: ResourceCreateValueResponseV1 =>
                    val stringValue = valueObject.value.textval.map {
                        case (valType: LiteralValueType.Value, value: String) => value // get string and ignore value type
                    }.head // each value is represented by a map consisting of only one item (e.g. string -> "book title")
                    stringValue
            })
        }

        // sort values by their string representation
        val sortedValuesExpected: Map[IRI, Seq[ResourceCreateValueResponseV1]] = expected.map {
            case (propIri, propValues) => (propIri, propValues.sortBy(_.toString))
        }.map {
            // turn the expected ApiValueV1s in ResourceCreateValueResponseV1 (these are returned by the actor).
            case (propIri: IRI, propValues: Seq[ApiValueV1]) =>
                (propIri, propValues.map {
                    case (propValue: ApiValueV1) =>
                        val valueResponse = CreateValueResponseV1(
                            value = propValue,
                            rights = 6,
                            id = "http://www.knora.org/test/values/test",
                            userdata = ResourcesResponderV1Spec.userProfile.userData
                        )

                        // convert CreateValueResponseV1 to a ResourceCreateValueResponseV1
                        MessageUtil.convertCreateValueResponseV1ToResourceCreateValueResponseV1(
                            resourceIri = "http://www.knora.org/test",
                            ownerIri = "http://data.knora.org/users/b83acc5f05",
                            propertyIri = propIri,
                            valueResponse = valueResponse
                        )
                })
        }

        // compare expected and received values
        sortedValuesExpected.foreach {
            case (propIri, propValuesExpected) =>
                (propValuesExpected, sortedValuesReceived(propIri)).zipped.foreach {
                    case (expected: ResourceCreateValueResponseV1, received: ResourceCreateValueResponseV1) =>
                        assert(expected.value.textval == received.value.textval, "textval did not match")
                        assert(expected.value.ival == received.value.ival, "ival did not match")
                        assert(expected.value.fval == received.value.fval, "fval did not match")
                        assert(expected.value.dateval1 == received.value.dateval1, "dateval1 did not match")
                        assert(expected.value.dateval2 == received.value.dateval2, "dateval2 did not match")
                        assert(expected.value.calendar == received.value.calendar, "calendar did not match")
                        assert(expected.value.dateprecision1 == received.value.dateprecision1, "dateprecision1 did not match")
                        assert(expected.value.dateprecision2 == received.value.dateprecision2, "dateprecision2 did not match")
                        assert(expected.value.timeval1 == received.value.timeval1, "timeval1 did not match")
                        assert(expected.value.timeval2 == received.value.timeval2, "timeval2 did not match")
                }
        }

    }

    private def getLastModificationDate(resourceIri: IRI): Option[String] = {
        val lastModSparqlQuery = queries.sparql.v1.txt.getLastModificationDate(
            resourceIri = resourceIri
        ).toString()

        storeManager ! SparqlSelectRequest(lastModSparqlQuery)

        expectMsgPF(timeout) {
            case response: SparqlSelectResponse =>
                val rows = response.results.bindings
                (rows.size <= 1) should ===(true)

                if (rows.size == 1) {
                    Some(rows.head.rowMap("lastModificationDate"))
                } else {
                    None
                }
        }
    }

    "Load test data" in {
        storeManager ! ResetTriplestoreContent(rdfDataObjects)
        expectMsg(300.seconds, ResetTriplestoreContentACK())
    }

    "The resources responder" should {
        "return a full description of the book 'Zeitglöcklein des Lebens und Leidens Christi' in the Incunabula test data" in {
            // http://localhost:3333/v1/resources/http%3A%2F%2Fdata.knora.org%2Fc5058f3a
            actorUnderTest ! ResourceFullGetRequestV1(iri = "http://data.knora.org/c5058f3a", userProfile = ResourcesResponderV1Spec.userProfile)

            expectMsgPF(timeout) {
                case response: ResourceFullResponseV1 => compareResourceFullResponses(ResourcesResponderV1SpecFullData.expectedBookResourceFullResponse, response)
            }
        }

        "return a full description of the first page of the book 'Zeitglöcklein des Lebens und Leidens Christi' in the Incunabula test data" in {
            // http://localhost:3333/v1/resources/http%3A%2F%2Fdata.knora.org%2F8a0b1e75
            actorUnderTest ! ResourceFullGetRequestV1(iri = "http://data.knora.org/8a0b1e75", userProfile = ResourcesResponderV1Spec.userProfile)

            expectMsgPF(timeout) {
                case response: ResourceFullResponseV1 => compareResourceFullResponses(ResourcesResponderV1SpecFullData.expectedPageResourceFullResponse, response)
            }
        }

        "return a region with a comment containing standoff information (disabled because of issue 17)" ignore {
            // http://localhost:3333/v1/resources/http%3A%2F%2Fdata.knora.org%2F047db418ae06
            actorUnderTest ! ResourceFullGetRequestV1(iri = "http://data.knora.org/047db418ae06", userProfile = ResourcesResponderV1Spec.userProfile)

            expectMsgPF(timeout) {
                case response: ResourceFullResponseV1 =>
                    println(s"${FormatConstants.ANSI_YELLOW}TODO: this test is temporarily disabled because of issue 17.${FormatConstants.ANSI_RESET}")
                    compareResourceFullResponses(ResourcesResponderV1SpecFullData.expectedRegionFullResource, response)
            }
        }

        "return the context (describing 402 pages) of the book 'Zeitglöcklein des Lebens und Leidens Christi' in the Incunabula test data" in {
            // http://localhost:3333/v1/resources/http%3A%2F%2Fdata.knora.org%2Fc5058f3a?reqtype=context&resinfo=true
            actorUnderTest ! ResourceContextGetRequestV1(iri = "http://data.knora.org/c5058f3a", resinfo = true, userProfile = ResourcesResponderV1Spec.userProfile)

            expectMsgPF(timeout) {
                case response: ResourceContextResponseV1 => compareResourceCompoundContextResponses(ResourcesResponderV1SpecContextData.expectedBookResourceContextResponse, response)
            }
        }

        "return the context of a page of the book 'Zeitglöcklein des Lebens und Leidens Christi' in the Incunabula test data" in {
            // http://localhost:3333/v1/resources/http%3A%2F%2Fdata.knora.org%2F8a0b1e75?reqtype=context&resinfo=true
            actorUnderTest ! ResourceContextGetRequestV1(iri = "http://data.knora.org/8a0b1e75", resinfo = true, userProfile = ResourcesResponderV1Spec.userProfile)

            expectMsgPF(timeout) {
                case response: ResourceContextResponseV1 => compareResourcePartOfContextResponses(ResourcesResponderV1SpecContextData.expectedPageResourceContextResponse, response)
            }
        }

        "return 1 resource containing 'Reise in' in its label with three of its values" in {
            // http://localhost:3333/v1/resources?searchstr=Reise+in&numprops=3&limit=11&restype_id=-1
            actorUnderTest ! ResourceSearchGetRequestV1(
                searchString = "Reise in",
                numberOfProps = 3,
                limitOfResults = 11,
                resourceTypeIri = None,
                userProfile = ResourcesResponderV1Spec.userProfile
            )

            expectMsgPF(timeout) {
                case response: ResourceSearchResponseV1 => compareResourceSearchResults(ReiseInsHeiligelandThreeValues, response)
            }
        }

        "return 1 resource of type incunabula:book containing 'Reis' in its label with its label (first property)" in {
            // http://localhost:3333/v1/resources?searchstr=Reis&numprops=1&limit=11&restype_id=http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23book
            actorUnderTest ! ResourceSearchGetRequestV1(
                searchString = "Reis",
                numberOfProps = 1,
                limitOfResults = 11,
                resourceTypeIri = Some("http://www.knora.org/ontology/incunabula#book"),
                userProfile = ResourcesResponderV1Spec.userProfile
            )

            expectMsgPF(timeout) {
                case response: ResourceSearchResponseV1 => compareResourceSearchResults(ReiseInsHeiligelandOneValueRestrictedToBook, response)
            }
        }

        "not create a resource when too many values are submitted for a property" in {
            // The Incunabula ontology specifies that an incunabula:book must have exactly one title.
            val valuesToBeCreated = Map(
                "http://www.knora.org/ontology/incunabula#title" -> Vector(
                    CreateValueV1WithComment(TextValueV1(utf8str = "De generatione Christi"), None),
                    CreateValueV1WithComment(TextValueV1(utf8str = "Defensorium inviolate perpetueque virginitatis castissime de genitricis Mariae"), None)
                )
            )

            val resourceCreateRequest = ResourceCreateRequestV1(
                resourceTypeIri = "http://www.knora.org/ontology/incunabula#book",
                label = "Test-Book",
                projectIri = "http://data.knora.org/projects/77275339",
                values = valuesToBeCreated,
                userProfile = ResourcesResponderV1Spec.userProfile,
                apiRequestID = UUID.randomUUID
            )

            actorUnderTest ! resourceCreateRequest

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[OntologyConstraintException] should ===(true)
            }
        }

        "not create a resource when a required value is not submitted" in {
            // Title and publoc are required but missing

            val author = Vector(
                CreateValueV1WithComment(TextValueV1(utf8str = "Franciscus de Retza"), None)
            )

            val pubdate = Vector(
                DateValueV1(
                    dateval1 = "1487",
                    dateval2 = "1490",
                    calendar = KnoraCalendarV1.JULIAN
                )
            )

            val valuesToBeCreated = Map(
                "http://www.knora.org/ontology/incunabula#hasAuthor" -> author,
                "http://www.knora.org/ontology/incunabula#pubdate" -> pubdate.map(date => CreateValueV1WithComment(DateUtilV1.dateValueV1ToJulianDayCountValueV1(date), None))
            )

            val resourceCreateRequest = ResourceCreateRequestV1(
                resourceTypeIri = "http://www.knora.org/ontology/incunabula#book",
                label = "Test-Book",
                projectIri = "http://data.knora.org/projects/77275339",
                values = valuesToBeCreated,
                userProfile = ResourcesResponderV1Spec.userProfile,
                apiRequestID = UUID.randomUUID
            )

            actorUnderTest ! resourceCreateRequest

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[OntologyConstraintException] should ===(true)
            }
        }

        "create a new resource of type incunabula:book with values" in {

            val title1 = TextValueV1("A beautiful book")

            val citation1 = TextValueV1("ein Zitat")
            val citation2 = TextValueV1("ein weiteres Zitat")
            val citation3 = TextValueV1("und noch eines")
            val citation4 = TextValueV1("noch ein letztes")

            val publoc = TextValueV1("Entenhausen")

            val pubdateRequest = DateUtilV1.createJDCValueV1FromDateString("GREGORIAN:2015-12-03")
            val pubdateResponse = DateValueV1(dateval1 = "2015-12-03", dateval2 = "2015-12-03", calendar = KnoraCalendarV1.GREGORIAN)

            val valuesToBeCreated: Map[IRI, Seq[CreateValueV1WithComment]] = Map(
                "http://www.knora.org/ontology/incunabula#title" -> Vector(CreateValueV1WithComment(title1)),
                "http://www.knora.org/ontology/incunabula#pubdate" -> Vector(CreateValueV1WithComment(pubdateRequest)),
                "http://www.knora.org/ontology/incunabula#citation" -> Vector(
                    CreateValueV1WithComment(citation4, None),
                    CreateValueV1WithComment(citation1, None),
                    CreateValueV1WithComment(citation3, None),
                    CreateValueV1WithComment(citation2, None)
                ),
                "http://www.knora.org/ontology/incunabula#publoc" -> Vector(CreateValueV1WithComment(publoc))
            )

            val valuesExpected = Map(
                "http://www.knora.org/ontology/incunabula#title" -> Vector(title1),
                "http://www.knora.org/ontology/incunabula#pubdate" -> Vector(pubdateResponse),
                "http://www.knora.org/ontology/incunabula#citation" -> Vector(citation3, citation1, citation4, citation2),
                "http://www.knora.org/ontology/incunabula#publoc" -> Vector(publoc)
            )


            actorUnderTest ! ResourceCreateRequestV1(
                resourceTypeIri = "http://www.knora.org/ontology/incunabula#book",
                label = "Test-Book",
                projectIri = "http://data.knora.org/projects/77275339",
                values = valuesToBeCreated,
                userProfile = ResourcesResponderV1Spec.userProfile,
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case response: ResourceCreateResponseV1 =>
                    newResourceIri.set(response.res_id)
                    checkResourceCreation(valuesExpected, response)
            }

            // Check that the resource doesn't have more than one lastModificationDate.
            getLastModificationDate(newResourceIri.get)

            // See if we can query the resource.

            actorUnderTest ! ResourceFullGetRequestV1(iri = newResourceIri.get, userProfile = ResourcesResponderV1Spec.userProfile)

            expectMsgPF(timeout) {
                case response: ResourceFullResponseV1 => () // If we got a ResourceFullResponseV1, the operation succeeded.
            }
        }

        "create an incunabula:page with a resource pointer" in {
            val recto = TextValueV1("recto")
            val origname = TextValueV1("Blatt")
            val seqnum = IntegerValueV1(1)

            val fileValueFull = StillImageFileValueV1(
                internalMimeType = "image/jp2",
                internalFilename = "gaga.jpg",
                originalFilename = "test.jpg",
                originalMimeType = Some("image/jpg"),
                dimX = 1000,
                dimY = 1000,
                qualityLevel = 100,
                qualityName = Some("full"),
                isPreview = false
            )

            val fileValueThumb = StillImageFileValueV1(
                internalMimeType = "image/jpeg",
                internalFilename = "gaga.jpg",
                originalFilename = "test.jpg",
                originalMimeType = Some("image/jpg"),
                dimX = 1000,
                dimY = 1000,
                qualityLevel = 100,
                qualityName = Some("full"),
                isPreview = false
            )

            val book = newResourceIri.get

            val valuesToBeCreated = Map(
                "http://www.knora.org/ontology/incunabula#hasRightSideband" -> Vector(CreateValueV1WithComment(LinkUpdateV1(targetResourceIri = "http://data.knora.org/482a33d65c36"))),
                "http://www.knora.org/ontology/incunabula#pagenum" -> Vector(CreateValueV1WithComment(recto)),
                "http://www.knora.org/ontology/incunabula#partOf" -> Vector(CreateValueV1WithComment(LinkUpdateV1(book))),
                "http://www.knora.org/ontology/incunabula#origname" -> Vector(CreateValueV1WithComment(origname)),
                "http://www.knora.org/ontology/incunabula#seqnum" -> Vector(CreateValueV1WithComment(seqnum))
            )

            val expected = Map(
                "http://www.knora.org/ontology/incunabula#hasRightSideband" -> Vector(LinkV1(targetResourceIri = "http://data.knora.org/482a33d65c36", valueResourceClass = Some("http://www.knora.org/ontology/incunabula#Sideband"))),
                "http://www.knora.org/ontology/incunabula#pagenum" -> Vector(recto),
                "http://www.knora.org/ontology/incunabula#partOf" -> Vector(LinkV1(book)),
                "http://www.knora.org/ontology/incunabula#origname" -> Vector(origname),
                "http://www.knora.org/ontology/incunabula#seqnum" -> Vector(seqnum),
                OntologyConstants.KnoraBase.HasStillImageFileValue -> Vector(fileValueFull, fileValueThumb)
            )

            actorUnderTest ! ResourceCreateRequestV1(
                resourceTypeIri = "http://www.knora.org/ontology/incunabula#page",
                label = "Test-Page",
                projectIri = "http://data.knora.org/projects/77275339",
                values = valuesToBeCreated,
                file = Some(SipiResponderConversionFileRequestV1(
                    originalFilename = "test.jpg",
                    originalMimeType = "image/jpeg",
                    filename = "./test_server/images/Chlaus.jpg",
                    userProfile = ResourcesResponderV1Spec.userProfile
                )),
                userProfile = ResourcesResponderV1Spec.userProfile,
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case response: ResourceCreateResponseV1 =>
                    newResourceIri.set(response.res_id)
                    checkResourceCreation(expected, response)
            }
        }
    }
}
