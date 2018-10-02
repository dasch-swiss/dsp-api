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

import akka.testkit.{ImplicitSender, TestActorRef}
import org.knora.webapi._
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.messages.v1.responder.valuemessages.{KnoraCalendarV1, KnoraPrecisionV1}
import org.knora.webapi.messages.v2.responder.resourcemessages._
import org.knora.webapi.messages.v2.responder.standoffmessages.{GetMappingRequestV2, GetMappingResponseV2, MappingXMLtoStandoff}
import org.knora.webapi.messages.v2.responder.valuemessages._
import org.knora.webapi.responders.v2.ResourcesResponseCheckerV2.compareReadResourcesSequenceV2Response
import org.knora.webapi.twirl.StandoffTagV2
import org.knora.webapi.util.IriConversions._
import org.knora.webapi.util.{KnoraIdUtil, SmartIri, StringFormatter}
import org.xmlunit.builder.{DiffBuilder, Input}
import org.xmlunit.diff.Diff

import scala.concurrent.duration._

object ResourcesResponderV2Spec {
    private val incunabulaUserProfile = SharedTestDataADM.incunabulaProjectAdminUser

    private val anythingUserProfile = SharedTestDataADM.anythingUser2

    private val defaultAnythingResourcePermissions = "CR knora-base:Creator|M knora-base:ProjectMember|V knora-base:KnownUser|RV knora-base:UnknownUser"
    private val defaultAnythingValuePermissions = defaultAnythingResourcePermissions

    private val sampleStandoff: Vector[StandoffTagV2] = Vector(
        StandoffTagV2(
            standoffTagClassIri = OntologyConstants.Standoff.StandoffRootTag,
            startPosition = 0,
            endPosition = 26,
            uuid = UUID.randomUUID().toString,
            originalXMLID = None,
            startIndex = 0
        ),
        StandoffTagV2(
            standoffTagClassIri = OntologyConstants.Standoff.StandoffParagraphTag,
            startPosition = 0,
            endPosition = 12,
            uuid = UUID.randomUUID().toString,
            originalXMLID = None,
            startIndex = 1,
            startParentIndex = Some(0)
        ),
        StandoffTagV2(
            standoffTagClassIri = OntologyConstants.Standoff.StandoffBoldTag,
            startPosition = 0,
            endPosition = 7,
            uuid = UUID.randomUUID().toString,
            originalXMLID = None,
            startIndex = 2,
            startParentIndex = Some(1)
        )
    )
}

/**
  * Tests [[ResourcesResponderV2]].
  */
class ResourcesResponderV2Spec extends CoreSpec() with ImplicitSender {

    import ResourcesResponderV2Spec._

    // Construct the actors needed for this test.
    private val actorUnderTest = TestActorRef[ResourcesResponderV2]
    private implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance
    private val resourcesResponderV2SpecFullData = new ResourcesResponderV2SpecFullData
    private val knoraIdUtil = new KnoraIdUtil

    private var standardMapping: Option[MappingXMLtoStandoff] = None

    override lazy val rdfDataObjects = List(
        RdfDataObject(path = "_test_data/all_data/incunabula-data.ttl", name = "http://www.knora.org/data/0803/incunabula"),
        RdfDataObject(path = "_test_data/demo_data/images-demo-data.ttl", name = "http://www.knora.org/data/00FF/images"),
        RdfDataObject(path = "_test_data/all_data/anything-data.ttl", name = "http://www.knora.org/data/0001/anything")
    )

    private def getResource(resourceIri: IRI, requestingUser: UserADM): ReadResourceV2 = {
        actorUnderTest ! ResourcesGetRequestV2(resourceIris = Seq(resourceIri), requestingUser = anythingUserProfile)

        expectMsgPF(timeout) {
            case response: ReadResourcesSequenceV2 =>
                resourcesSequenceToResource(
                    requestedresourceIri = resourceIri,
                    readResourcesSequence = response,
                    requestingUser = anythingUserProfile
                )
        }
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

    private def checkCreateResource(inputResource: CreateResourceV2,
                                    outputResource: ReadResourceV2,
                                    defaultResourcePermissions: String,
                                    defaultValuePermissions: String,
                                    requestingUser: UserADM): Unit = {
        assert(outputResource.resourceIri == inputResource.resourceIri)
        assert(outputResource.resourceClassIri == inputResource.resourceClassIri)
        assert(outputResource.label == inputResource.label)
        assert(outputResource.attachedToUser == requestingUser.id)
        assert(outputResource.attachedToProject == inputResource.projectADM.id)

        val expectedPermissions = inputResource.permissions.getOrElse(defaultResourcePermissions)
        assert(outputResource.permissions == expectedPermissions)

        assert(outputResource.values.keySet == inputResource.values.keySet)

        inputResource.values.foreach {
            case (propertyIri: SmartIri, propertyInputValues: Seq[CreateValueInNewResourceV2]) =>
                val propertyOutputValues = outputResource.values(propertyIri)

                assert(propertyOutputValues.size == propertyInputValues.size)

                propertyInputValues.zip(propertyOutputValues).foreach {
                    case (inputValue: CreateValueInNewResourceV2, outputValue: ReadValueV2) =>
                        val expectedPermissions = inputValue.permissions.getOrElse(defaultValuePermissions)
                        assert(outputValue.permissions == expectedPermissions)
                        assert(inputValue.valueContent.wouldDuplicateCurrentVersion(outputValue.valueContent))
                }
        }
    }

    // The default timeout for receiving reply messages from actors.
    private val timeout = 10.seconds

    "Load test data" in {
        responderManager ! GetMappingRequestV2(mappingIri = "http://rdfh.ch/standoff/mappings/StandardMapping", requestingUser = KnoraSystemInstances.Users.SystemUser)

        expectMsgPF(timeout) {
            case mappingResponse: GetMappingResponseV2 =>
                standardMapping = Some(mappingResponse.mapping)
        }
    }

    "The resources responder v2" should {
        "return a full description of the book 'Zeitglöcklein des Lebens und Leidens Christi' in the Incunabula test data" in {

            actorUnderTest ! ResourcesGetRequestV2(Seq("http://rdfh.ch/c5058f3a"), incunabulaUserProfile)

            expectMsgPF(timeout) {
                case response: ReadResourcesSequenceV2 =>
                    compareReadResourcesSequenceV2Response(expected = resourcesResponderV2SpecFullData.expectedFullResourceResponseForZeitgloecklein, received = response)
            }

        }

        "return a preview descriptions of the book 'Zeitglöcklein des Lebens und Leidens Christi' in the Incunabula test data" in {

            actorUnderTest ! ResourcesPreviewGetRequestV2(Seq("http://rdfh.ch/c5058f3a"), incunabulaUserProfile)

            expectMsgPF(timeout) {
                case response: ReadResourcesSequenceV2 =>
                    compareReadResourcesSequenceV2Response(expected = resourcesResponderV2SpecFullData.expectedPreviewResourceResponseForZeitgloecklein, received = response)
            }

        }

        "return a full description of the book 'Reise ins Heilige Land' in the Incunabula test data" in {

            actorUnderTest ! ResourcesGetRequestV2(Seq("http://rdfh.ch/2a6221216701"), incunabulaUserProfile)

            expectMsgPF(timeout) {
                case response: ReadResourcesSequenceV2 =>
                    compareReadResourcesSequenceV2Response(expected = resourcesResponderV2SpecFullData.expectedFullResourceResponseForReise, received = response)
            }

        }

        "return two full description of the book 'Zeitglöcklein des Lebens und Leidens Christi' and the book 'Reise ins Heilige Land' in the Incunabula test data" in {

            actorUnderTest ! ResourcesGetRequestV2(Seq("http://rdfh.ch/c5058f3a", "http://rdfh.ch/2a6221216701"), incunabulaUserProfile)

            expectMsgPF(timeout) {
                case response: ReadResourcesSequenceV2 =>
                    compareReadResourcesSequenceV2Response(expected = resourcesResponderV2SpecFullData.expectedFullResourceResponseForZeitgloeckleinAndReise, received = response)
            }

        }

        "return two preview descriptions of the book 'Zeitglöcklein des Lebens und Leidens Christi' and the book 'Reise ins Heilige Land' in the Incunabula test data" in {

            actorUnderTest ! ResourcesPreviewGetRequestV2(Seq("http://rdfh.ch/c5058f3a", "http://rdfh.ch/2a6221216701"), incunabulaUserProfile)

            expectMsgPF(timeout) {
                case response: ReadResourcesSequenceV2 =>
                    compareReadResourcesSequenceV2Response(expected = resourcesResponderV2SpecFullData.expectedPreviewResourceResponseForZeitgloeckleinAndReise, received = response)
            }

        }

        "return two full description of the 'Reise ins Heilige Land' and the book 'Zeitglöcklein des Lebens und Leidens Christi' in the Incunabula test data (inversed order)" in {

            actorUnderTest ! ResourcesGetRequestV2(Seq("http://rdfh.ch/2a6221216701", "http://rdfh.ch/c5058f3a"), incunabulaUserProfile)

            expectMsgPF(timeout) {
                case response: ReadResourcesSequenceV2 =>
                    compareReadResourcesSequenceV2Response(expected = resourcesResponderV2SpecFullData.expectedFullResourceResponseForReiseAndZeitgloeckleinInversedOrder, received = response)
            }

        }

        "return two full description of the book 'Zeitglöcklein des Lebens und Leidens Christi' and the book 'Reise ins Heilige Land' in the Incunabula test data providing redundant resource Iris" in {

            actorUnderTest ! ResourcesGetRequestV2(Seq("http://rdfh.ch/c5058f3a", "http://rdfh.ch/c5058f3a", "http://rdfh.ch/2a6221216701"), incunabulaUserProfile)

            // the redundant Iri should be ignored (distinct)
            expectMsgPF(timeout) {
                case response: ReadResourcesSequenceV2 =>
                    compareReadResourcesSequenceV2Response(expected = resourcesResponderV2SpecFullData.expectedFullResourceResponseForZeitgloeckleinAndReise, received = response)
            }

        }

        "return a resource of type thing with text as TEI/XML" in {

            actorUnderTest ! ResourceTEIGetRequestV2(resourceIri = "http://rdfh.ch/0001/thing_with_richtext_with_markup", textProperty = "http://www.knora.org/ontology/0001/anything#hasRichtext".toSmartIri, mappingIri = None, gravsearchTemplateIri = None, headerXSLTIri = None, requestingUser = anythingUserProfile)

            expectMsgPF(timeout) {
                case response: ResourceTEIGetResponseV2 =>

                    val expectedBody =
                        """<text><body><p>This is a test that contains marked up elements. This is <hi rend="italic">interesting text</hi> in italics. This is <hi rend="italic">boring text</hi> in italics.</p></body></text>""".stripMargin

                    // Compare the original XML with the regenerated XML.
                    val xmlDiff: Diff = DiffBuilder.compare(Input.fromString(response.body.toXML)).withTest(Input.fromString(expectedBody)).build()

                    xmlDiff.hasDifferences should be(false)
            }

        }

        "return a resource of type Something with text with standoff as TEI/XML" in {

            actorUnderTest ! ResourceTEIGetRequestV2(resourceIri = "http://rdfh.ch/0001/qN1igiDRSAemBBktbRHn6g", textProperty = "http://www.knora.org/ontology/0001/anything#hasRichtext".toSmartIri, mappingIri = None, gravsearchTemplateIri = None, headerXSLTIri = None, requestingUser = anythingUserProfile)

            expectMsgPF(timeout) {
                case response: ResourceTEIGetResponseV2 =>

                    val expectedBody =
                        """<text><body><p><hi rend="bold">Something</hi> <hi rend="italic">with</hi> a <del>lot</del> of <hi rend="underline">different</hi> <hi rend="sup">markup</hi>. And more <ref target="http://www.google.ch">markup</ref>.</p></body></text>""".stripMargin

                    // Compare the original XML with the regenerated XML.
                    val xmlDiff: Diff = DiffBuilder.compare(Input.fromString(response.body.toXML)).withTest(Input.fromString(expectedBody)).build()

                    xmlDiff.hasDifferences should be(false)
            }

        }

        "create a resource with no values" in {
            // Create the resource.

            val resourceIri: IRI = knoraIdUtil.makeRandomResourceIri(SharedTestDataADM.anythingProject.shortcode)

            val inputResource = CreateResourceV2(
                resourceIri = resourceIri,
                resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
                label = "test thing",
                values = Map.empty,
                projectADM = SharedTestDataADM.anythingProject
            )

            actorUnderTest ! CreateResourceRequestV2(
                createResource = inputResource,
                requestingUser = anythingUserProfile,
                apiRequestID = UUID.randomUUID
            )

            // Check that the response contains the correct metadata.

            expectMsgPF(timeout) {
                case response: ReadResourcesSequenceV2 =>
                    val outputResource: ReadResourceV2 = resourcesSequenceToResource(
                        requestedresourceIri = resourceIri,
                        readResourcesSequence = response,
                        requestingUser = anythingUserProfile
                    )

                    checkCreateResource(
                        inputResource = inputResource,
                        outputResource = outputResource,
                        defaultResourcePermissions = defaultAnythingResourcePermissions,
                        defaultValuePermissions = defaultAnythingValuePermissions,
                        requestingUser = anythingUserProfile
                    )
            }

            // Get the resource from the triplestore and check it again.

            val outputResource = getResource(resourceIri, anythingUserProfile)

            checkCreateResource(
                inputResource = inputResource,
                outputResource = outputResource,
                defaultResourcePermissions = defaultAnythingResourcePermissions,
                defaultValuePermissions = defaultAnythingValuePermissions,
                requestingUser = anythingUserProfile
            )
        }

        "create a resource with no values and custom permissions" in {
            // Create the resource.

            val resourceIri: IRI = knoraIdUtil.makeRandomResourceIri(SharedTestDataADM.anythingProject.shortcode)

            val inputResource = CreateResourceV2(
                resourceIri = resourceIri,
                resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
                label = "test thing",
                values = Map.empty,
                projectADM = SharedTestDataADM.anythingProject,
                permissions = Some("CR knora-base:Creator|V http://rdfh.ch/groups/0001/thing-searcher")
            )

            actorUnderTest ! CreateResourceRequestV2(
                createResource = inputResource,
                requestingUser = anythingUserProfile,
                apiRequestID = UUID.randomUUID
            )

            expectMsgType[ReadResourcesSequenceV2]

            // Get the resource from the triplestore and check it.

            val outputResource = getResource(resourceIri, anythingUserProfile)

            checkCreateResource(
                inputResource = inputResource,
                outputResource = outputResource,
                defaultResourcePermissions = defaultAnythingResourcePermissions,
                defaultValuePermissions = defaultAnythingValuePermissions,
                requestingUser = anythingUserProfile
            )
        }

        "create a resource with values" in {
            // Create the resource.

            val resourceIri: IRI = knoraIdUtil.makeRandomResourceIri(SharedTestDataADM.anythingProject.shortcode)

            val inputValues: Map[SmartIri, Seq[CreateValueInNewResourceV2]] = Map(
                "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri -> Seq(
                    CreateValueInNewResourceV2(
                        valueContent = IntegerValueContentV2(
                            ontologySchema = ApiV2WithValueObjects,
                            valueHasInteger = 5,
                            comment = Some("this is the number five")
                        ),
                        permissions = Some("CR knora-base:Creator|V http://rdfh.ch/groups/0001/thing-searcher")
                    ),
                    CreateValueInNewResourceV2(
                        valueContent = IntegerValueContentV2(
                            ontologySchema = ApiV2WithValueObjects,
                            valueHasInteger = 6
                        )
                    )
                ),
                "http://0.0.0.0:3333/ontology/0001/anything/v2#hasText".toSmartIri -> Seq(
                    CreateValueInNewResourceV2(
                        valueContent = TextValueContentV2(
                            ontologySchema = ApiV2WithValueObjects,
                            valueHasString = "this is text without standoff"
                        )
                    )
                ),
                "http://0.0.0.0:3333/ontology/0001/anything/v2#hasRichtext".toSmartIri -> Seq(
                    CreateValueInNewResourceV2(
                        valueContent = TextValueContentV2(
                            ontologySchema = ApiV2WithValueObjects,
                            valueHasString = "this is text with standoff",
                            standoffAndMapping = Some(StandoffAndMapping(
                                standoff = sampleStandoff,
                                mappingIri = "http://rdfh.ch/standoff/mappings/StandardMapping",
                                mapping = standardMapping.get
                            ))
                        )
                    )
                ),
                "http://0.0.0.0:3333/ontology/0001/anything/v2#hasDecimal".toSmartIri -> Seq(
                    CreateValueInNewResourceV2(
                        valueContent = DecimalValueContentV2(
                            ontologySchema = ApiV2WithValueObjects,
                            valueHasDecimal = BigDecimal("100000000000000.000000000000001")
                        )
                    )
                ),
                "http://0.0.0.0:3333/ontology/0001/anything/v2#hasDate".toSmartIri -> Seq(
                    CreateValueInNewResourceV2(
                        valueContent = DateValueContentV2(
                            ontologySchema = ApiV2WithValueObjects,
                            valueHasCalendar = KnoraCalendarV1.GREGORIAN,
                            valueHasStartJDN = 2264907,
                            valueHasStartPrecision = KnoraPrecisionV1.YEAR,
                            valueHasEndJDN = 2265271,
                            valueHasEndPrecision = KnoraPrecisionV1.YEAR
                        )
                    )
                ),
                "http://0.0.0.0:3333/ontology/0001/anything/v2#hasBoolean".toSmartIri -> Seq(
                    CreateValueInNewResourceV2(
                        valueContent = BooleanValueContentV2(
                            ontologySchema = ApiV2WithValueObjects,
                            valueHasBoolean = true
                        )
                    )
                ),
                "http://0.0.0.0:3333/ontology/0001/anything/v2#hasGeometry".toSmartIri -> Seq(
                    CreateValueInNewResourceV2(
                        valueContent = GeomValueContentV2(
                            ontologySchema = ApiV2WithValueObjects,
                            valueHasGeometry = """{"status":"active","lineColor":"#ff3333","lineWidth":2,"points":[{"x":0.08098591549295775,"y":0.16741071428571427},{"x":0.7394366197183099,"y":0.7299107142857143}],"type":"rectangle","original_index":0}"""
                        )
                    )
                ),
                "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInterval".toSmartIri -> Seq(
                    CreateValueInNewResourceV2(
                        valueContent = IntervalValueContentV2(
                            ontologySchema = ApiV2WithValueObjects,
                            valueHasIntervalStart = BigDecimal("1.2"),
                            valueHasIntervalEnd = BigDecimal("3.4")
                        )
                    )
                ),
                "http://0.0.0.0:3333/ontology/0001/anything/v2#hasListItem".toSmartIri -> Seq(
                    CreateValueInNewResourceV2(
                        valueContent = HierarchicalListValueContentV2(
                            ontologySchema = ApiV2WithValueObjects,
                            valueHasListNode = "http://rdfh.ch/lists/0001/treeList03"
                        )
                    )
                ),
                "http://0.0.0.0:3333/ontology/0001/anything/v2#hasColor".toSmartIri -> Seq(
                    CreateValueInNewResourceV2(
                        valueContent = ColorValueContentV2(
                            ontologySchema = ApiV2WithValueObjects,
                            valueHasColor = "#ff3333"
                        )
                    )
                ),
                "http://0.0.0.0:3333/ontology/0001/anything/v2#hasUri".toSmartIri -> Seq(
                    CreateValueInNewResourceV2(
                        valueContent = UriValueContentV2(
                            ontologySchema = ApiV2WithValueObjects,
                            valueHasUri = "https://www.knora.org"
                        )
                    )
                ),
                "http://0.0.0.0:3333/ontology/0001/anything/v2#hasGeoname".toSmartIri -> Seq(
                    CreateValueInNewResourceV2(
                        valueContent = GeonameValueContentV2(
                            ontologySchema = ApiV2WithValueObjects,
                            valueHasGeonameCode = "2661604"
                        )
                    )
                ),
                "http://0.0.0.0:3333/ontology/0001/anything/v2#hasOtherThingValue".toSmartIri -> Seq(
                    CreateValueInNewResourceV2(
                        valueContent = LinkValueContentV2(
                            ontologySchema = ApiV2WithValueObjects,
                            referredResourceIri = "http://rdfh.ch/0001/a-thing"
                        )
                    )
                )
            )

            val inputResource = CreateResourceV2(
                resourceIri = resourceIri,
                resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
                label = "test thing",
                values = inputValues,
                projectADM = SharedTestDataADM.anythingProject
            )

            actorUnderTest ! CreateResourceRequestV2(
                createResource = inputResource,
                requestingUser = anythingUserProfile,
                apiRequestID = UUID.randomUUID
            )

            expectMsgType[ReadResourcesSequenceV2]

            // Get the resource from the triplestore and check it.

            val outputResource = getResource(resourceIri, anythingUserProfile)

            checkCreateResource(
                inputResource = inputResource,
                outputResource = outputResource,
                defaultResourcePermissions = defaultAnythingResourcePermissions,
                defaultValuePermissions = defaultAnythingValuePermissions,
                requestingUser = anythingUserProfile
            )
        }
    }
}