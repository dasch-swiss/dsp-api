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
import org.knora.webapi.messages.v2.responder.resourcemessages._
import org.knora.webapi.responders.v2.ResourcesResponseCheckerV2.compareReadResourcesSequenceV2Response
import org.knora.webapi.util.IriConversions._
import org.knora.webapi.util.{KnoraIdUtil, StringFormatter}
import org.xmlunit.builder.{DiffBuilder, Input}
import org.xmlunit.diff.Diff

import scala.concurrent.duration._

object ResourcesResponderV2Spec {
    private val incunabulaUserProfile = SharedTestDataADM.incunabulaProjectAdminUser

    private val anythingUserProfile = SharedTestDataADM.anythingUser2
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

    override lazy val rdfDataObjects = List(
        RdfDataObject(path = "_test_data/all_data/incunabula-data.ttl", name = "http://www.knora.org/data/0803/incunabula"),
        RdfDataObject(path = "_test_data/demo_data/images-demo-data.ttl", name = "http://www.knora.org/data/00FF/images"),
        RdfDataObject(path = "_test_data/all_data/anything-data.ttl", name = "http://www.knora.org/data/0001/anything")
    )

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

    // The default timeout for receiving reply messages from actors.
    private val timeout = 10.seconds

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
            val resourceIri: IRI = knoraIdUtil.makeRandomResourceIri(SharedTestDataADM.anythingProject.shortcode)
            val resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri
            val label = "test thing"
            val expectedPermissions = "CR knora-base:Creator|M knora-base:ProjectMember|V knora-base:KnownUser|RV knora-base:UnknownUser"

            actorUnderTest ! CreateResourceRequestV2(
                createResource = CreateResourceV2(
                    resourceIri = resourceIri,
                    resourceClassIri = resourceClassIri,
                    label = label,
                    values = Map.empty,
                    projectADM = SharedTestDataADM.anythingProject
                ),
                requestingUser = anythingUserProfile,
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case response: ReadResourcesSequenceV2 =>
                    val resource = resourcesSequenceToResource(
                        requestedresourceIri = resourceIri,
                        readResourcesSequence = response,
                        requestingUser = anythingUserProfile
                    )

                    resource.resourceIri should ===(resourceIri)
                    resource.resourceClassIri should ===(resourceClassIri)
                    resource.label should ===(label)
                    resource.attachedToUser should ===(anythingUserProfile.id)
                    resource.attachedToProject should ===(SharedTestDataADM.anythingProject.id)
                    resource.permissions should ===(expectedPermissions)
            }

            actorUnderTest ! ResourcesGetRequestV2(resourceIris = Seq(resourceIri), requestingUser = anythingUserProfile)

            expectMsgPF(timeout) {
                case response: ReadResourcesSequenceV2 =>
                    val resource = resourcesSequenceToResource(
                        requestedresourceIri = resourceIri,
                        readResourcesSequence = response,
                        requestingUser = anythingUserProfile
                    )

                    resource.resourceIri should ===(resourceIri)
                    resource.resourceClassIri should ===(resourceClassIri)
                    resource.label should ===(label)
                    resource.attachedToUser should ===(anythingUserProfile.id)
                    resource.attachedToProject should ===(SharedTestDataADM.anythingProject.id)
                    resource.permissions should ===(expectedPermissions)
            }
        }

        "create a resource with no values and custom permissions" in {
            val resourceIri: IRI = knoraIdUtil.makeRandomResourceIri(SharedTestDataADM.anythingProject.shortcode)
            val resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri
            val label = "test thing"
            val permissions = "CR knora-base:Creator|V http://rdfh.ch/groups/0001/thing-searcher"

            actorUnderTest ! CreateResourceRequestV2(
                createResource = CreateResourceV2(
                    resourceIri = resourceIri,
                    resourceClassIri = resourceClassIri,
                    label = label,
                    values = Map.empty,
                    projectADM = SharedTestDataADM.anythingProject,
                    permissions = Some(permissions)
                ),
                requestingUser = anythingUserProfile,
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case response: ReadResourcesSequenceV2 =>
                    val resource = resourcesSequenceToResource(
                        requestedresourceIri = resourceIri,
                        readResourcesSequence = response,
                        requestingUser = anythingUserProfile
                    )

                    resource.resourceIri should ===(resourceIri)
                    resource.resourceClassIri should ===(resourceClassIri)
                    resource.label should ===(label)
                    resource.attachedToUser should ===(anythingUserProfile.id)
                    resource.attachedToProject should ===(SharedTestDataADM.anythingProject.id)
                    resource.permissions should ===(permissions)
            }

            actorUnderTest ! ResourcesGetRequestV2(resourceIris = Seq(resourceIri), requestingUser = anythingUserProfile)

            expectMsgPF(timeout) {
                case response: ReadResourcesSequenceV2 =>
                    val resource = resourcesSequenceToResource(
                        requestedresourceIri = resourceIri,
                        readResourcesSequence = response,
                        requestingUser = anythingUserProfile
                    )

                    resource.resourceIri should ===(resourceIri)
                    resource.resourceClassIri should ===(resourceClassIri)
                    resource.label should ===(label)
                    resource.attachedToUser should ===(anythingUserProfile.id)
                    resource.attachedToProject should ===(SharedTestDataADM.anythingProject.id)
                    resource.permissions should ===(permissions)
            }
        }

    }


}