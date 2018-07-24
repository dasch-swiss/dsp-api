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
import org.knora.webapi._
import org.knora.webapi.messages.store.triplestoremessages._
import org.knora.webapi.messages.v2.responder.SuccessResponseV2
import org.knora.webapi.messages.v2.responder.ontologymessages.Cardinality.KnoraCardinalityInfo
import org.knora.webapi.messages.v2.responder.ontologymessages._
import org.knora.webapi.responders.{RESPONDER_MANAGER_ACTOR_NAME, ResponderManager}
import org.knora.webapi.store.{STORE_MANAGER_ACTOR_NAME, StoreManager}
import org.knora.webapi.util.IriConversions._
import org.knora.webapi.util.{MutableTestIri, SmartIri, StringFormatter}

import scala.concurrent.duration._

/**
  * Tests [[OntologyResponderV2]].
  */
class OntologyResponderV2Spec extends CoreSpec() with ImplicitSender {

    private implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    private val imagesUser = SharedTestDataADM.imagesUser01
    private val imagesProjectIri = SharedTestDataADM.IMAGES_PROJECT_IRI.toSmartIri

    private val anythingAdminUser = SharedTestDataADM.anythingAdminUser
    private val anythingNonAdminUser = SharedTestDataADM.anythingUser1
    private val anythingProjectIri = SharedTestDataADM.ANYTHING_PROJECT_IRI.toSmartIri

    private val actorUnderTest = TestActorRef[OntologyResponderV2]
    private val responderManager = system.actorOf(Props(new ResponderManager with LiveActorMaker), name = RESPONDER_MANAGER_ACTOR_NAME)
    private val storeManager = system.actorOf(Props(new StoreManager with LiveActorMaker), name = STORE_MANAGER_ACTOR_NAME)

    private val anythingData = RdfDataObject(path = "_test_data/all_data/anything-data.ttl", name = "http://www.knora.org/data/0001/anything")

    // The default timeout for receiving reply messages from actors.
    private val timeout = 10.seconds

    private val fooIri = new MutableTestIri
    private var fooLastModDate: Instant = Instant.now

    private val AnythingOntologyIri = "http://0.0.0.0:3333/ontology/0001/anything/v2".toSmartIri
    private var anythingLastModDate: Instant = Instant.now

    private val printErrorMessages = false

    private def loadTestData(rdfDataObjs: List[RdfDataObject], expectOK: Boolean = false): Unit = {
        storeManager ! ResetTriplestoreContent(rdfDataObjs)
        expectMsg(300.seconds, ResetTriplestoreContentACK())

        responderManager ! LoadOntologiesRequestV2(KnoraSystemInstances.Users.SystemUser)

        if (expectOK) {
            expectMsgType[SuccessResponseV2](10.seconds)
        }
    }

    "Load test data" in {
        loadTestData(rdfDataObjs = List(anythingData), expectOK = true)
    }

    "The ontology responder v2" should {
        "not allow a user to create an ontology if they are not a sysadmin or an admin in the ontology's project" in {
            actorUnderTest ! CreateOntologyRequestV2(
                ontologyName = "foo",
                projectIri = imagesProjectIri,
                label = "The foo ontology",
                apiRequestID = UUID.randomUUID,
                requestingUser = SharedTestDataADM.imagesUser02
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[ForbiddenException] should ===(true)
            }
        }

        "create an empty ontology called 'foo' with a project code" in {
            actorUnderTest ! CreateOntologyRequestV2(
                ontologyName = "foo",
                projectIri = imagesProjectIri,
                label = "The foo ontology",
                apiRequestID = UUID.randomUUID,
                requestingUser = imagesUser
            )

            val response = expectMsgType[ReadOntologyMetadataV2](timeout)
            assert(response.ontologies.size == 1)
            val metadata = response.ontologies.head
            assert(metadata.ontologyIri.toString == "http://www.knora.org/ontology/00FF/foo")
            fooIri.set(metadata.ontologyIri.toOntologySchema(ApiV2WithValueObjects).toString)
            fooLastModDate = metadata.lastModificationDate.getOrElse(throw AssertionException(s"${metadata.ontologyIri} has no last modification date"))
        }

        "change the metadata of 'foo'" in {
            val newLabel = "The modified foo ontology"

            actorUnderTest ! ChangeOntologyMetadataRequestV2(
                ontologyIri = fooIri.get.toSmartIri,
                label = newLabel,
                lastModificationDate = fooLastModDate,
                apiRequestID = UUID.randomUUID,
                requestingUser = imagesUser
            )

            val response = expectMsgType[ReadOntologyMetadataV2](timeout)
            assert(response.ontologies.size == 1)
            val metadata = response.ontologies.head
            assert(metadata.ontologyIri.toString == "http://www.knora.org/ontology/00FF/foo")
            assert(metadata.label.contains(newLabel))
            val newFooLastModDate = metadata.lastModificationDate.getOrElse(throw AssertionException(s"${metadata.ontologyIri} has no last modification date"))
            assert(newFooLastModDate.isAfter(fooLastModDate))
            fooLastModDate = newFooLastModDate
        }

        "not create 'foo' again" in {
            actorUnderTest ! CreateOntologyRequestV2(
                ontologyName = "foo",
                projectIri = imagesProjectIri,
                label = "The foo ontology",
                apiRequestID = UUID.randomUUID,
                requestingUser = imagesUser
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure =>
                    msg.cause.isInstanceOf[BadRequestException] should ===(true)
            }
        }

        "not delete an ontology that doesn't exist" in {
            actorUnderTest ! DeleteOntologyRequestV2(
                ontologyIri = "http://0.0.0.0:3333/ontology/1234/nonexistent/v2".toSmartIri,
                lastModificationDate = fooLastModDate,
                apiRequestID = UUID.randomUUID,
                requestingUser = imagesUser
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[NotFoundException] should ===(true)
            }
        }

        "not allow a user to delete an ontology if they are not a sysadmin or an admin in the ontology's project" in {
            actorUnderTest ! DeleteOntologyRequestV2(
                ontologyIri = fooIri.get.toSmartIri,
                lastModificationDate = fooLastModDate,
                apiRequestID = UUID.randomUUID,
                requestingUser = SharedTestDataADM.imagesUser02
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[ForbiddenException] should ===(true)
            }
        }

        "delete the 'foo' ontology" in {
            actorUnderTest ! DeleteOntologyRequestV2(
                ontologyIri = fooIri.get.toSmartIri,
                lastModificationDate = fooLastModDate,
                apiRequestID = UUID.randomUUID,
                requestingUser = imagesUser
            )

            expectMsgType[SuccessResponseV2](timeout)

            // Request the metadata of all ontologies to check that 'foo' isn't listed.

            actorUnderTest ! OntologyMetadataGetRequestV2(
                requestingUser = imagesUser
            )

            val cachedMetadataResponse = expectMsgType[ReadOntologyMetadataV2](timeout)
            assert(!cachedMetadataResponse.ontologies.exists(_.ontologyIri == fooIri.get.toSmartIri))

            // Reload the ontologies from the triplestore and check again.

            responderManager ! LoadOntologiesRequestV2(KnoraSystemInstances.Users.SystemUser)
            expectMsgType[SuccessResponseV2](10.seconds)

            actorUnderTest ! OntologyMetadataGetRequestV2(
                requestingUser = imagesUser
            )

            val loadedMetadataResponse = expectMsgType[ReadOntologyMetadataV2](timeout)
            assert(!loadedMetadataResponse.ontologies.exists(_.ontologyIri == fooIri.get.toSmartIri))
        }

        "not delete the 'anything' ontology, because it is used in data and in the 'something' ontology" in {
            actorUnderTest ! OntologyMetadataGetRequestV2(
                projectIris = Set(anythingProjectIri),
                requestingUser = anythingAdminUser
            )

            val metadataResponse = expectMsgType[ReadOntologyMetadataV2](timeout)
            assert(metadataResponse.ontologies.size == 2)
            anythingLastModDate = metadataResponse.toOntologySchema(ApiV2WithValueObjects).ontologies.find(_.ontologyIri == AnythingOntologyIri).get.lastModificationDate.get

            actorUnderTest ! DeleteOntologyRequestV2(
                ontologyIri = AnythingOntologyIri,
                lastModificationDate = anythingLastModDate,
                apiRequestID = UUID.randomUUID,
                requestingUser = anythingAdminUser
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure =>
                    val cause: Throwable = msg.cause
                    val errorMsg: String = cause.getMessage
                    if (printErrorMessages) println(errorMsg)
                    cause.isInstanceOf[BadRequestException] should ===(true)

                    val expectedSubjects = Set(
                        "<http://rdfh.ch/0001/a-thing>", // rdf:type anything:Thing
                        "<http://rdfh.ch/0001/a-blue-thing>", // rdf:type anything:BlueThing, a subclass of anything:Thing
                        "<http://www.knora.org/ontology/0001/something#Something>", // a subclass of anything:Thing in another ontology
                        "<http://www.knora.org/ontology/0001/something#hasOtherSomething>" // a subproperty of anything:hasOtherThing in another ontology
                    )

                    expectedSubjects.forall(s => errorMsg.contains(s)) should ===(true)
            }
        }


        "not create an ontology called 'rdfs'" in {
            actorUnderTest ! CreateOntologyRequestV2(
                ontologyName = "rdfs",
                projectIri = imagesProjectIri,
                label = "The rdfs ontology",
                apiRequestID = UUID.randomUUID,
                requestingUser = imagesUser
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[BadRequestException] should ===(true)
            }

        }

        "not create an ontology called '0000'" in {
            actorUnderTest ! CreateOntologyRequestV2(
                ontologyName = "0000",
                projectIri = imagesProjectIri,
                label = "The 0000 ontology",
                apiRequestID = UUID.randomUUID,
                requestingUser = imagesUser
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[BadRequestException] should ===(true)
            }

        }

        "not create an ontology called '-foo'" in {
            actorUnderTest ! CreateOntologyRequestV2(
                ontologyName = "-foo",
                projectIri = imagesProjectIri,
                label = "The -foo ontology",
                apiRequestID = UUID.randomUUID,
                requestingUser = imagesUser
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[BadRequestException] should ===(true)
            }

        }

        "not create an ontology called 'v3'" in {
            actorUnderTest ! CreateOntologyRequestV2(
                ontologyName = "v3",
                projectIri = imagesProjectIri,
                label = "The v3 ontology",
                apiRequestID = UUID.randomUUID,
                requestingUser = imagesUser
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[BadRequestException] should ===(true)
            }

        }

        "not create an ontology called 'ontology'" in {
            actorUnderTest ! CreateOntologyRequestV2(
                ontologyName = "ontology",
                projectIri = imagesProjectIri,
                label = "The ontology ontology",
                apiRequestID = UUID.randomUUID,
                requestingUser = imagesUser
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[BadRequestException] should ===(true)
            }

        }

        "not create an ontology called 'knora'" in {
            actorUnderTest ! CreateOntologyRequestV2(
                ontologyName = "knora",
                projectIri = imagesProjectIri,
                label = "The wrong knora ontology",
                apiRequestID = UUID.randomUUID,
                requestingUser = imagesUser
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[BadRequestException] should ===(true)
            }

        }

        "not create an ontology called 'simple'" in {
            actorUnderTest ! CreateOntologyRequestV2(
                ontologyName = "simple",
                projectIri = imagesProjectIri,
                label = "The simple ontology",
                apiRequestID = UUID.randomUUID,
                requestingUser = imagesUser
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[BadRequestException] should ===(true)
            }

        }

        "not allow a user to create a property if they are not a sysadmin or an admin in the ontology's project" in {

            actorUnderTest ! OntologyMetadataGetRequestV2(
                projectIris = Set(anythingProjectIri),
                requestingUser = anythingNonAdminUser
            )

            val metadataResponse = expectMsgType[ReadOntologyMetadataV2](timeout)
            assert(metadataResponse.ontologies.size == 2)
            anythingLastModDate = metadataResponse.toOntologySchema(ApiV2WithValueObjects).ontologies.find(_.ontologyIri == AnythingOntologyIri).get.lastModificationDate.get

            val propertyIri = AnythingOntologyIri.makeEntityIri("hasName")

            val propertyInfoContent = PropertyInfoContentV2(
                propertyIri = propertyIri,
                predicates = Map(
                    OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
                        objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.ObjectProperty.toSmartIri))
                    ),
                    OntologyConstants.KnoraApiV2WithValueObjects.SubjectType.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.KnoraApiV2WithValueObjects.SubjectType.toSmartIri,
                        objects = Seq(SmartIriLiteralV2(AnythingOntologyIri.makeEntityIri("Thing")))
                    ),
                    OntologyConstants.KnoraApiV2WithValueObjects.ObjectType.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.KnoraApiV2WithValueObjects.ObjectType.toSmartIri,
                        objects = Seq(SmartIriLiteralV2(OntologyConstants.KnoraApiV2WithValueObjects.TextValue.toSmartIri))
                    ),
                    OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
                        objects = Seq(
                            StringLiteralV2("has name", Some("en")),
                            StringLiteralV2("hat Namen", Some("de"))
                        )
                    ),
                    OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
                        objects = Seq(
                            StringLiteralV2("The name of a Thing", Some("en")),
                            StringLiteralV2("Der Name eines Dinges", Some("de"))
                        )
                    )
                ),
                subPropertyOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.HasValue.toSmartIri, OntologyConstants.SchemaOrg.Name.toSmartIri),
                ontologySchema = ApiV2WithValueObjects
            )

            actorUnderTest ! CreatePropertyRequestV2(
                propertyInfoContent = propertyInfoContent,
                lastModificationDate = anythingLastModDate,
                apiRequestID = UUID.randomUUID,
                requestingUser = anythingNonAdminUser
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[ForbiddenException] should ===(true)
            }
        }

        "create a property anything:hasName as a subproperty of knora-api:hasValue and schema:name" in {

            actorUnderTest ! OntologyMetadataGetRequestV2(
                projectIris = Set(anythingProjectIri),
                requestingUser = anythingAdminUser
            )

            val metadataResponse = expectMsgType[ReadOntologyMetadataV2](timeout)
            assert(metadataResponse.ontologies.size == 2)
            anythingLastModDate = metadataResponse.toOntologySchema(ApiV2WithValueObjects).ontologies.find(_.ontologyIri == AnythingOntologyIri).get.lastModificationDate.get

            val propertyIri = AnythingOntologyIri.makeEntityIri("hasName")

            val propertyInfoContent = PropertyInfoContentV2(
                propertyIri = propertyIri,
                predicates = Map(
                    OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
                        objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.ObjectProperty.toSmartIri))
                    ),
                    OntologyConstants.KnoraApiV2WithValueObjects.SubjectType.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.KnoraApiV2WithValueObjects.SubjectType.toSmartIri,
                        objects = Seq(SmartIriLiteralV2(AnythingOntologyIri.makeEntityIri("Thing")))
                    ),
                    OntologyConstants.KnoraApiV2WithValueObjects.ObjectType.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.KnoraApiV2WithValueObjects.ObjectType.toSmartIri,
                        objects = Seq(SmartIriLiteralV2(OntologyConstants.KnoraApiV2WithValueObjects.TextValue.toSmartIri))
                    ),
                    OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
                        objects = Seq(
                            StringLiteralV2("has name", Some("en")),
                            StringLiteralV2("hat Namen", Some("de"))
                        )
                    ),
                    OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
                        objects = Seq(
                            StringLiteralV2("The name of a Thing", Some("en")),
                            StringLiteralV2("Der Name eines Dinges", Some("de"))
                        )
                    )
                ),
                subPropertyOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.HasValue.toSmartIri, OntologyConstants.SchemaOrg.Name.toSmartIri),
                ontologySchema = ApiV2WithValueObjects
            )

            actorUnderTest ! CreatePropertyRequestV2(
                propertyInfoContent = propertyInfoContent,
                lastModificationDate = anythingLastModDate,
                apiRequestID = UUID.randomUUID,
                requestingUser = anythingAdminUser
            )

            expectMsgPF(timeout) {
                case msg: ReadOntologyV2 =>
                    val externalOntology = msg.toOntologySchema(ApiV2WithValueObjects)
                    val property = externalOntology.properties(propertyIri)
                    property.entityInfoContent should ===(propertyInfoContent)
                    val metadata = externalOntology.ontologyMetadata
                    val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(throw AssertionException(s"${metadata.ontologyIri} has no last modification date"))
                    assert(newAnythingLastModDate.isAfter(anythingLastModDate))
                    anythingLastModDate = newAnythingLastModDate
            }

            // Reload the ontology cache and see if we get the same result.

            responderManager ! LoadOntologiesRequestV2(KnoraSystemInstances.Users.SystemUser)
            expectMsgType[SuccessResponseV2](10.seconds)

            responderManager ! PropertiesGetRequestV2(
                propertyIris = Set(propertyIri),
                allLanguages = true,
                requestingUser = anythingAdminUser
            )

            expectMsgPF(timeout) {
                case msg: ReadOntologyV2 =>
                    val externalOntology = msg.toOntologySchema(ApiV2WithValueObjects)
                    assert(externalOntology.properties.size == 1)
                    val readPropertyInfo: ReadPropertyInfoV2 = externalOntology.properties.values.head
                    readPropertyInfo.entityInfoContent should ===(propertyInfoContent)
            }
        }

        "create a link property in the 'anything' ontology, and automatically create the corresponding link value property" in {

            actorUnderTest ! OntologyMetadataGetRequestV2(
                projectIris = Set(anythingProjectIri),
                requestingUser = anythingAdminUser
            )

            val metadataResponse = expectMsgType[ReadOntologyMetadataV2](timeout)
            assert(metadataResponse.ontologies.size == 2)
            anythingLastModDate = metadataResponse.toOntologySchema(ApiV2WithValueObjects).ontologies.find(_.ontologyIri == AnythingOntologyIri).get.lastModificationDate.get

            val propertyIri = AnythingOntologyIri.makeEntityIri("hasInterestingThing")

            val propertyInfoContent = PropertyInfoContentV2(
                propertyIri = propertyIri,
                predicates = Map(
                    OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
                        objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.ObjectProperty.toSmartIri))
                    ),
                    OntologyConstants.KnoraApiV2WithValueObjects.SubjectType.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.KnoraApiV2WithValueObjects.SubjectType.toSmartIri,
                        objects = Seq(SmartIriLiteralV2(AnythingOntologyIri.makeEntityIri("Thing")))
                    ),
                    OntologyConstants.KnoraApiV2WithValueObjects.ObjectType.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.KnoraApiV2WithValueObjects.ObjectType.toSmartIri,
                        objects = Seq(SmartIriLiteralV2(AnythingOntologyIri.makeEntityIri("Thing")))
                    ),
                    OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
                        objects = Seq(
                            StringLiteralV2("has interesting thing", Some("en"))
                        )
                    ),
                    OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
                        objects = Seq(
                            StringLiteralV2("an interesting Thing", Some("en"))
                        )
                    )
                ),
                subPropertyOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.HasLinkTo.toSmartIri),
                ontologySchema = ApiV2WithValueObjects
            )

            actorUnderTest ! CreatePropertyRequestV2(
                propertyInfoContent = propertyInfoContent,
                lastModificationDate = anythingLastModDate,
                apiRequestID = UUID.randomUUID,
                requestingUser = anythingAdminUser
            )

            expectMsgPF(timeout) {
                case msg: ReadOntologyV2 =>
                    val externalOntology = msg.toOntologySchema(ApiV2WithValueObjects)
                    val property = externalOntology.properties(propertyIri)
                    assert(property.isLinkProp)
                    assert(!property.isLinkValueProp)
                    externalOntology.properties(propertyIri).entityInfoContent should ===(propertyInfoContent)
                    val metadata = externalOntology.ontologyMetadata
                    val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(throw AssertionException(s"${metadata.ontologyIri} has no last modification date"))
                    assert(newAnythingLastModDate.isAfter(anythingLastModDate))
                    anythingLastModDate = newAnythingLastModDate
            }

            // Check that the link value property was created.

            val linkValuePropIri = propertyIri.fromLinkPropToLinkValueProp

            responderManager ! PropertiesGetRequestV2(
                propertyIris = Set(linkValuePropIri),
                allLanguages = true,
                requestingUser = anythingAdminUser
            )

            expectMsgPF(timeout) {
                case msg: ReadOntologyV2 =>
                    val externalOntology = msg.toOntologySchema(ApiV2WithValueObjects)
                    assert(externalOntology.properties.size == 1)
                    val readPropertyInfo: ReadPropertyInfoV2 = externalOntology.properties.values.head
                    assert(readPropertyInfo.entityInfoContent.propertyIri == linkValuePropIri)
                    assert(!readPropertyInfo.isLinkProp)
                    assert(readPropertyInfo.isLinkValueProp)
            }

            // Reload the ontology cache and see if we get the same result.

            responderManager ! LoadOntologiesRequestV2(KnoraSystemInstances.Users.SystemUser)
            expectMsgType[SuccessResponseV2](10.seconds)

            responderManager ! PropertiesGetRequestV2(
                propertyIris = Set(propertyIri),
                allLanguages = true,
                requestingUser = anythingAdminUser
            )

            expectMsgPF(timeout) {
                case msg: ReadOntologyV2 =>
                    val externalOntology = msg.toOntologySchema(ApiV2WithValueObjects)
                    assert(externalOntology.properties.size == 1)
                    val readPropertyInfo: ReadPropertyInfoV2 = externalOntology.properties.values.head
                    assert(readPropertyInfo.isLinkProp)
                    assert(!readPropertyInfo.isLinkValueProp)
                    readPropertyInfo.entityInfoContent should ===(propertyInfoContent)
            }

            responderManager ! PropertiesGetRequestV2(
                propertyIris = Set(linkValuePropIri),
                allLanguages = true,
                requestingUser = anythingAdminUser
            )

            expectMsgPF(timeout) {
                case msg: ReadOntologyV2 =>
                    val externalOntology = msg.toOntologySchema(ApiV2WithValueObjects)
                    assert(externalOntology.properties.size == 1)
                    val readPropertyInfo: ReadPropertyInfoV2 = externalOntology.properties.values.head
                    assert(readPropertyInfo.entityInfoContent.propertyIri == linkValuePropIri)
                    assert(!readPropertyInfo.isLinkProp)
                    assert(readPropertyInfo.isLinkValueProp)
            }

        }

        "not create a property without an rdf:type" in {

            val propertyIri = AnythingOntologyIri.makeEntityIri("wrongProperty")

            val propertyInfoContent = PropertyInfoContentV2(
                propertyIri = propertyIri,
                predicates = Map(
                    OntologyConstants.KnoraApiV2WithValueObjects.SubjectType.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.KnoraApiV2WithValueObjects.SubjectType.toSmartIri,
                        objects = Seq(SmartIriLiteralV2(AnythingOntologyIri.makeEntityIri("Thing")))
                    ),
                    OntologyConstants.KnoraApiV2WithValueObjects.ObjectType.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.KnoraApiV2WithValueObjects.ObjectType.toSmartIri,
                        objects = Seq(SmartIriLiteralV2(OntologyConstants.KnoraApiV2WithValueObjects.TextValue.toSmartIri))
                    ),
                    OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
                        objects = Seq(
                            StringLiteralV2("wrong property", Some("en"))
                        )
                    ),
                    OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
                        objects = Seq(
                            StringLiteralV2("An invalid property definition", Some("en"))
                        )
                    )
                ),
                subPropertyOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.HasValue.toSmartIri),
                ontologySchema = ApiV2WithValueObjects
            )

            actorUnderTest ! CreatePropertyRequestV2(
                propertyInfoContent = propertyInfoContent,
                lastModificationDate = anythingLastModDate,
                apiRequestID = UUID.randomUUID,
                requestingUser = anythingAdminUser
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure =>
                    if (printErrorMessages) println(msg.cause.getMessage)
                    msg.cause.isInstanceOf[BadRequestException] should ===(true)
            }
        }

        "not create a property with the wrong rdf:type" in {

            val propertyIri = AnythingOntologyIri.makeEntityIri("wrongProperty")

            val propertyInfoContent = PropertyInfoContentV2(
                propertyIri = propertyIri,
                predicates = Map(
                    OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
                        objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.Class.toSmartIri))
                    ),
                    OntologyConstants.KnoraApiV2WithValueObjects.SubjectType.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.KnoraApiV2WithValueObjects.SubjectType.toSmartIri,
                        objects = Seq(SmartIriLiteralV2(AnythingOntologyIri.makeEntityIri("Thing")))
                    ),
                    OntologyConstants.KnoraApiV2WithValueObjects.ObjectType.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.KnoraApiV2WithValueObjects.ObjectType.toSmartIri,
                        objects = Seq(SmartIriLiteralV2(OntologyConstants.KnoraApiV2WithValueObjects.TextValue.toSmartIri))
                    ),
                    OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
                        objects = Seq(
                            StringLiteralV2("wrong property", Some("en"))
                        )
                    ),
                    OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
                        objects = Seq(
                            StringLiteralV2("An invalid property definition", Some("en"))
                        )
                    )
                ),
                subPropertyOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.HasValue.toSmartIri),
                ontologySchema = ApiV2WithValueObjects
            )

            actorUnderTest ! CreatePropertyRequestV2(
                propertyInfoContent = propertyInfoContent,
                lastModificationDate = anythingLastModDate,
                apiRequestID = UUID.randomUUID,
                requestingUser = anythingAdminUser
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure =>
                    if (printErrorMessages) println(msg.cause.getMessage)
                    msg.cause.isInstanceOf[BadRequestException] should ===(true)
            }
        }

        "not create a property that already exists" in {

            val propertyIri = AnythingOntologyIri.makeEntityIri("hasInteger")

            val propertyInfoContent = PropertyInfoContentV2(
                propertyIri = propertyIri,
                predicates = Map(
                    OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
                        objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.ObjectProperty.toSmartIri))
                    ),
                    OntologyConstants.KnoraApiV2WithValueObjects.SubjectType.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.KnoraApiV2WithValueObjects.SubjectType.toSmartIri,
                        objects = Seq(SmartIriLiteralV2(AnythingOntologyIri.makeEntityIri("Thing")))
                    ),
                    OntologyConstants.KnoraApiV2WithValueObjects.ObjectType.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.KnoraApiV2WithValueObjects.ObjectType.toSmartIri,
                        objects = Seq(SmartIriLiteralV2(OntologyConstants.KnoraApiV2WithValueObjects.IntValue.toSmartIri))
                    ),
                    OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
                        objects = Seq(
                            StringLiteralV2("wrong property", Some("en"))
                        )
                    ),
                    OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
                        objects = Seq(
                            StringLiteralV2("An invalid property definition", Some("en"))
                        )
                    )
                ),
                subPropertyOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.HasValue.toSmartIri),
                ontologySchema = ApiV2WithValueObjects
            )

            actorUnderTest ! CreatePropertyRequestV2(
                propertyInfoContent = propertyInfoContent,
                lastModificationDate = anythingLastModDate,
                apiRequestID = UUID.randomUUID,
                requestingUser = anythingAdminUser
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure =>
                    if (printErrorMessages) println(msg.cause.getMessage)
                    msg.cause.isInstanceOf[BadRequestException] should ===(true)
            }
        }

        "not create a property with a nonexistent Knora superproperty" in {

            val propertyIri = AnythingOntologyIri.makeEntityIri("wrongProperty")

            val propertyInfoContent = PropertyInfoContentV2(
                propertyIri = propertyIri,
                predicates = Map(
                    OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
                        objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.ObjectProperty.toSmartIri))
                    ),
                    OntologyConstants.KnoraApiV2WithValueObjects.SubjectType.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.KnoraApiV2WithValueObjects.SubjectType.toSmartIri,
                        objects = Seq(SmartIriLiteralV2(AnythingOntologyIri.makeEntityIri("Thing")))
                    ),
                    OntologyConstants.KnoraApiV2WithValueObjects.ObjectType.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.KnoraApiV2WithValueObjects.ObjectType.toSmartIri,
                        objects = Seq(SmartIriLiteralV2(OntologyConstants.KnoraApiV2WithValueObjects.IntValue.toSmartIri))
                    ),
                    OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
                        objects = Seq(
                            StringLiteralV2("wrong property", Some("en"))
                        )
                    ),
                    OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
                        objects = Seq(
                            StringLiteralV2("An invalid property definition", Some("en"))
                        )
                    )
                ),
                subPropertyOf = Set(AnythingOntologyIri.makeEntityIri("nonexistentProperty")),
                ontologySchema = ApiV2WithValueObjects
            )

            actorUnderTest ! CreatePropertyRequestV2(
                propertyInfoContent = propertyInfoContent,
                lastModificationDate = anythingLastModDate,
                apiRequestID = UUID.randomUUID,
                requestingUser = anythingAdminUser
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure =>
                    if (printErrorMessages) println(msg.cause.getMessage)
                    msg.cause.isInstanceOf[BadRequestException] should ===(true)
            }
        }

        "not create a property that is not a subproperty of knora-api:hasValue or knora-api:hasLinkTo" in {

            val propertyIri = AnythingOntologyIri.makeEntityIri("wrongProperty")

            val propertyInfoContent = PropertyInfoContentV2(
                propertyIri = propertyIri,
                predicates = Map(
                    OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
                        objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.ObjectProperty.toSmartIri))
                    ),
                    OntologyConstants.KnoraApiV2WithValueObjects.SubjectType.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.KnoraApiV2WithValueObjects.SubjectType.toSmartIri,
                        objects = Seq(SmartIriLiteralV2(AnythingOntologyIri.makeEntityIri("Thing")))
                    ),
                    OntologyConstants.KnoraApiV2WithValueObjects.ObjectType.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.KnoraApiV2WithValueObjects.ObjectType.toSmartIri,
                        objects = Seq(SmartIriLiteralV2(OntologyConstants.KnoraApiV2WithValueObjects.TextValue.toSmartIri))
                    ),
                    OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
                        objects = Seq(
                            StringLiteralV2("wrong property", Some("en"))
                        )
                    ),
                    OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
                        objects = Seq(
                            StringLiteralV2("An invalid property definition", Some("en"))
                        )
                    )
                ),
                subPropertyOf = Set("http://xmlns.com/foaf/0.1/name".toSmartIri),
                ontologySchema = ApiV2WithValueObjects
            )

            actorUnderTest ! CreatePropertyRequestV2(
                propertyInfoContent = propertyInfoContent,
                lastModificationDate = anythingLastModDate,
                apiRequestID = UUID.randomUUID,
                requestingUser = anythingAdminUser
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure =>
                    if (printErrorMessages) println(msg.cause.getMessage)
                    msg.cause.isInstanceOf[BadRequestException] should ===(true)
            }
        }

        "not create a property that is a subproperty of both knora-api:hasValue and knora-api:hasLinkTo" in {

            val propertyIri = AnythingOntologyIri.makeEntityIri("wrongProperty")

            val propertyInfoContent = PropertyInfoContentV2(
                propertyIri = propertyIri,
                predicates = Map(
                    OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
                        objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.ObjectProperty.toSmartIri))
                    ),
                    OntologyConstants.KnoraApiV2WithValueObjects.SubjectType.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.KnoraApiV2WithValueObjects.SubjectType.toSmartIri,
                        objects = Seq(SmartIriLiteralV2(AnythingOntologyIri.makeEntityIri("Thing")))
                    ),
                    OntologyConstants.KnoraApiV2WithValueObjects.ObjectType.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.KnoraApiV2WithValueObjects.ObjectType.toSmartIri,
                        objects = Seq(SmartIriLiteralV2(OntologyConstants.KnoraApiV2WithValueObjects.TextValue.toSmartIri))
                    ),
                    OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
                        objects = Seq(
                            StringLiteralV2("wrong property", Some("en"))
                        )
                    ),
                    OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
                        objects = Seq(
                            StringLiteralV2("An invalid property definition", Some("en"))
                        )
                    )
                ),
                subPropertyOf = Set(
                    AnythingOntologyIri.makeEntityIri("hasText"),
                    AnythingOntologyIri.makeEntityIri("hasOtherThing")
                ),
                ontologySchema = ApiV2WithValueObjects
            )

            actorUnderTest ! CreatePropertyRequestV2(
                propertyInfoContent = propertyInfoContent,
                lastModificationDate = anythingLastModDate,
                apiRequestID = UUID.randomUUID,
                requestingUser = anythingAdminUser
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure =>
                    if (printErrorMessages) println(msg.cause.getMessage)
                    msg.cause.isInstanceOf[BadRequestException] should ===(true)
            }
        }

        "not create a property with a knora-base:subjectType that refers to a nonexistent class" in {

            val propertyIri = AnythingOntologyIri.makeEntityIri("wrongProperty")

            val propertyInfoContent = PropertyInfoContentV2(
                propertyIri = propertyIri,
                predicates = Map(
                    OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
                        objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.ObjectProperty.toSmartIri))
                    ),
                    OntologyConstants.KnoraApiV2WithValueObjects.SubjectType.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.KnoraApiV2WithValueObjects.SubjectType.toSmartIri,
                        objects = Seq(SmartIriLiteralV2(AnythingOntologyIri.makeEntityIri("NonexistentClass")))
                    ),
                    OntologyConstants.KnoraApiV2WithValueObjects.ObjectType.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.KnoraApiV2WithValueObjects.ObjectType.toSmartIri,
                        objects = Seq(SmartIriLiteralV2(OntologyConstants.KnoraApiV2WithValueObjects.TextValue.toSmartIri))
                    ),
                    OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
                        objects = Seq(
                            StringLiteralV2("wrong property", Some("en"))
                        )
                    ),
                    OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
                        objects = Seq(
                            StringLiteralV2("An invalid property definition", Some("en"))
                        )
                    )
                ),
                subPropertyOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.HasValue.toSmartIri),
                ontologySchema = ApiV2WithValueObjects
            )

            actorUnderTest ! CreatePropertyRequestV2(
                propertyInfoContent = propertyInfoContent,
                lastModificationDate = anythingLastModDate,
                apiRequestID = UUID.randomUUID,
                requestingUser = anythingAdminUser
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure =>
                    if (printErrorMessages) println(msg.cause.getMessage)
                    msg.cause.isInstanceOf[BadRequestException] should ===(true)
            }
        }

        "not create a property with a knora-base:objectType that refers to a nonexistent class" in {

            val propertyIri = AnythingOntologyIri.makeEntityIri("wrongProperty")

            val propertyInfoContent = PropertyInfoContentV2(
                propertyIri = propertyIri,
                predicates = Map(
                    OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
                        objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.ObjectProperty.toSmartIri))
                    ),
                    OntologyConstants.KnoraApiV2WithValueObjects.SubjectType.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.KnoraApiV2WithValueObjects.SubjectType.toSmartIri,
                        objects = Seq(SmartIriLiteralV2(AnythingOntologyIri.makeEntityIri("Thing")))
                    ),
                    OntologyConstants.KnoraApiV2WithValueObjects.ObjectType.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.KnoraApiV2WithValueObjects.ObjectType.toSmartIri,
                        objects = Seq(SmartIriLiteralV2((OntologyConstants.KnoraApiV2WithValueObjects.KnoraApiV2PrefixExpansion + "NonexistentClass").toSmartIri))
                    ),
                    OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
                        objects = Seq(
                            StringLiteralV2("wrong property", Some("en"))
                        )
                    ),
                    OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
                        objects = Seq(
                            StringLiteralV2("An invalid property definition", Some("en"))
                        )
                    )
                ),
                subPropertyOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.HasValue.toSmartIri),
                ontologySchema = ApiV2WithValueObjects
            )

            actorUnderTest ! CreatePropertyRequestV2(
                propertyInfoContent = propertyInfoContent,
                lastModificationDate = anythingLastModDate,
                apiRequestID = UUID.randomUUID,
                requestingUser = anythingAdminUser
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure =>
                    if (printErrorMessages) println(msg.cause.getMessage)
                    msg.cause.isInstanceOf[BadRequestException] should ===(true)
            }
        }

        "not create a subproperty of anything:hasInteger with a knora-base:subjectType of knora-api:Representation" in {

            val propertyIri = AnythingOntologyIri.makeEntityIri("wrongProperty")

            val propertyInfoContent = PropertyInfoContentV2(
                propertyIri = propertyIri,
                predicates = Map(
                    OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
                        objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.ObjectProperty.toSmartIri))
                    ),
                    OntologyConstants.KnoraApiV2WithValueObjects.SubjectType.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.KnoraApiV2WithValueObjects.SubjectType.toSmartIri,
                        objects = Seq(SmartIriLiteralV2(OntologyConstants.KnoraApiV2WithValueObjects.Representation.toSmartIri))
                    ),
                    OntologyConstants.KnoraApiV2WithValueObjects.ObjectType.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.KnoraApiV2WithValueObjects.ObjectType.toSmartIri,
                        objects = Seq(SmartIriLiteralV2(OntologyConstants.KnoraApiV2WithValueObjects.TextValue.toSmartIri))
                    ),
                    OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
                        objects = Seq(
                            StringLiteralV2("wrong property", Some("en"))
                        )
                    ),
                    OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
                        objects = Seq(
                            StringLiteralV2("An invalid property definition", Some("en"))
                        )
                    )
                ),
                subPropertyOf = Set(AnythingOntologyIri.makeEntityIri("hasInteger")),
                ontologySchema = ApiV2WithValueObjects
            )

            actorUnderTest ! CreatePropertyRequestV2(
                propertyInfoContent = propertyInfoContent,
                lastModificationDate = anythingLastModDate,
                apiRequestID = UUID.randomUUID,
                requestingUser = anythingAdminUser
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure =>
                    if (printErrorMessages) println(msg.cause.getMessage)
                    msg.cause.isInstanceOf[BadRequestException] should ===(true)
            }
        }

        "not create a file value property" in {

            val propertyIri = AnythingOntologyIri.makeEntityIri("wrongProperty")

            val propertyInfoContent = PropertyInfoContentV2(
                propertyIri = propertyIri,
                predicates = Map(
                    OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
                        objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.ObjectProperty.toSmartIri))
                    ),
                    OntologyConstants.KnoraApiV2WithValueObjects.SubjectType.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.KnoraApiV2WithValueObjects.SubjectType.toSmartIri,
                        objects = Seq(SmartIriLiteralV2(AnythingOntologyIri.makeEntityIri("Thing")))
                    ),
                    OntologyConstants.KnoraApiV2WithValueObjects.ObjectType.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.KnoraApiV2WithValueObjects.ObjectType.toSmartIri,
                        objects = Seq(SmartIriLiteralV2(OntologyConstants.KnoraApiV2WithValueObjects.FileValue.toSmartIri))
                    ),
                    OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
                        objects = Seq(
                            StringLiteralV2("wrong property", Some("en"))
                        )
                    ),
                    OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
                        objects = Seq(
                            StringLiteralV2("An invalid property definition", Some("en"))
                        )
                    )
                ),
                subPropertyOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.HasFileValue.toSmartIri),
                ontologySchema = ApiV2WithValueObjects
            )

            actorUnderTest ! CreatePropertyRequestV2(
                propertyInfoContent = propertyInfoContent,
                lastModificationDate = anythingLastModDate,
                apiRequestID = UUID.randomUUID,
                requestingUser = anythingAdminUser
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure =>
                    if (printErrorMessages) println(msg.cause.getMessage)
                    msg.cause.isInstanceOf[BadRequestException] should ===(true)
            }
        }

        "not directly create a link value property" in {

            val propertyIri = AnythingOntologyIri.makeEntityIri("wrongProperty")

            val propertyInfoContent = PropertyInfoContentV2(
                propertyIri = propertyIri,
                predicates = Map(
                    OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
                        objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.ObjectProperty.toSmartIri))
                    ),
                    OntologyConstants.KnoraApiV2WithValueObjects.SubjectType.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.KnoraApiV2WithValueObjects.SubjectType.toSmartIri,
                        objects = Seq(SmartIriLiteralV2(AnythingOntologyIri.makeEntityIri("Thing")))
                    ),
                    OntologyConstants.KnoraApiV2WithValueObjects.ObjectType.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.KnoraApiV2WithValueObjects.ObjectType.toSmartIri,
                        objects = Seq(SmartIriLiteralV2(OntologyConstants.KnoraApiV2WithValueObjects.LinkValue.toSmartIri))
                    ),
                    OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
                        objects = Seq(
                            StringLiteralV2("wrong property", Some("en"))
                        )
                    ),
                    OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
                        objects = Seq(
                            StringLiteralV2("An invalid property definition", Some("en"))
                        )
                    )
                ),
                subPropertyOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.HasLinkToValue.toSmartIri),
                ontologySchema = ApiV2WithValueObjects
            )

            actorUnderTest ! CreatePropertyRequestV2(
                propertyInfoContent = propertyInfoContent,
                lastModificationDate = anythingLastModDate,
                apiRequestID = UUID.randomUUID,
                requestingUser = anythingAdminUser
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure =>
                    if (printErrorMessages) println(msg.cause.getMessage)
                    msg.cause.isInstanceOf[BadRequestException] should ===(true)
            }
        }

        "not directly create a property with a knora-api:objectType of knora-api:LinkValue" in {

            val propertyIri = AnythingOntologyIri.makeEntityIri("wrongProperty")

            val propertyInfoContent = PropertyInfoContentV2(
                propertyIri = propertyIri,
                predicates = Map(
                    OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
                        objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.ObjectProperty.toSmartIri))
                    ),
                    OntologyConstants.KnoraApiV2WithValueObjects.SubjectType.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.KnoraApiV2WithValueObjects.SubjectType.toSmartIri,
                        objects = Seq(SmartIriLiteralV2(AnythingOntologyIri.makeEntityIri("Thing")))
                    ),
                    OntologyConstants.KnoraApiV2WithValueObjects.ObjectType.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.KnoraApiV2WithValueObjects.ObjectType.toSmartIri,
                        objects = Seq(SmartIriLiteralV2(OntologyConstants.KnoraApiV2WithValueObjects.LinkValue.toSmartIri))
                    ),
                    OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
                        objects = Seq(
                            StringLiteralV2("wrong property", Some("en"))
                        )
                    ),
                    OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
                        objects = Seq(
                            StringLiteralV2("An invalid property definition", Some("en"))
                        )
                    )
                ),
                subPropertyOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.HasValue.toSmartIri),
                ontologySchema = ApiV2WithValueObjects
            )

            actorUnderTest ! CreatePropertyRequestV2(
                propertyInfoContent = propertyInfoContent,
                lastModificationDate = anythingLastModDate,
                apiRequestID = UUID.randomUUID,
                requestingUser = anythingAdminUser
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure =>
                    if (printErrorMessages) println(msg.cause.getMessage)
                    msg.cause.isInstanceOf[BadRequestException] should ===(true)
            }
        }

        "not create a property with a knora-api:objectType of xsd:string" in {

            val propertyIri = AnythingOntologyIri.makeEntityIri("wrongProperty")

            val propertyInfoContent = PropertyInfoContentV2(
                propertyIri = propertyIri,
                predicates = Map(
                    OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
                        objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.ObjectProperty.toSmartIri))
                    ),
                    OntologyConstants.KnoraApiV2WithValueObjects.SubjectType.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.KnoraApiV2WithValueObjects.SubjectType.toSmartIri,
                        objects = Seq(SmartIriLiteralV2(AnythingOntologyIri.makeEntityIri("Thing")))
                    ),
                    OntologyConstants.KnoraApiV2WithValueObjects.ObjectType.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.KnoraApiV2WithValueObjects.ObjectType.toSmartIri,
                        objects = Seq(SmartIriLiteralV2(OntologyConstants.Xsd.String.toSmartIri))
                    ),
                    OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
                        objects = Seq(
                            StringLiteralV2("wrong property", Some("en"))
                        )
                    ),
                    OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
                        objects = Seq(
                            StringLiteralV2("An invalid property definition", Some("en"))
                        )
                    )
                ),
                subPropertyOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.HasValue.toSmartIri),
                ontologySchema = ApiV2WithValueObjects
            )

            actorUnderTest ! CreatePropertyRequestV2(
                propertyInfoContent = propertyInfoContent,
                lastModificationDate = anythingLastModDate,
                apiRequestID = UUID.randomUUID,
                requestingUser = anythingAdminUser
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure =>
                    if (printErrorMessages) println(msg.cause.getMessage)
                    msg.cause.isInstanceOf[BadRequestException] should ===(true)
            }
        }

        "not create a property whose object type is knora-api:StillImageFileValue" in {

            val propertyIri = AnythingOntologyIri.makeEntityIri("wrongProperty")

            val propertyInfoContent = PropertyInfoContentV2(
                propertyIri = propertyIri,
                predicates = Map(
                    OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
                        objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.ObjectProperty.toSmartIri))
                    ),
                    OntologyConstants.KnoraApiV2WithValueObjects.SubjectType.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.KnoraApiV2WithValueObjects.SubjectType.toSmartIri,
                        objects = Seq(SmartIriLiteralV2(AnythingOntologyIri.makeEntityIri("Thing")))
                    ),
                    OntologyConstants.KnoraApiV2WithValueObjects.ObjectType.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.KnoraApiV2WithValueObjects.ObjectType.toSmartIri,
                        objects = Seq(SmartIriLiteralV2(OntologyConstants.KnoraApiV2WithValueObjects.StillImageFileValue.toSmartIri))
                    ),
                    OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
                        objects = Seq(
                            StringLiteralV2("wrong property", Some("en"))
                        )
                    ),
                    OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
                        objects = Seq(
                            StringLiteralV2("An invalid property definition", Some("en"))
                        )
                    )
                ),
                subPropertyOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.HasValue.toSmartIri),
                ontologySchema = ApiV2WithValueObjects
            )

            actorUnderTest ! CreatePropertyRequestV2(
                propertyInfoContent = propertyInfoContent,
                lastModificationDate = anythingLastModDate,
                apiRequestID = UUID.randomUUID,
                requestingUser = anythingAdminUser
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure =>
                    if (printErrorMessages) println(msg.cause.getMessage)
                    msg.cause.isInstanceOf[BadRequestException] should ===(true)
            }
        }

        "not create a property whose object type is a Knora resource class if the property isn't a subproperty of knora-api:hasLinkValue" in {

            val propertyIri = AnythingOntologyIri.makeEntityIri("wrongProperty")

            val propertyInfoContent = PropertyInfoContentV2(
                propertyIri = propertyIri,
                predicates = Map(
                    OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
                        objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.ObjectProperty.toSmartIri))
                    ),
                    OntologyConstants.KnoraApiV2WithValueObjects.SubjectType.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.KnoraApiV2WithValueObjects.SubjectType.toSmartIri,
                        objects = Seq(SmartIriLiteralV2(AnythingOntologyIri.makeEntityIri("Thing")))
                    ),
                    OntologyConstants.KnoraApiV2WithValueObjects.ObjectType.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.KnoraApiV2WithValueObjects.ObjectType.toSmartIri,
                        objects = Seq(SmartIriLiteralV2(AnythingOntologyIri.makeEntityIri("Thing")))
                    ),
                    OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
                        objects = Seq(
                            StringLiteralV2("wrong property", Some("en"))
                        )
                    ),
                    OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
                        objects = Seq(
                            StringLiteralV2("An invalid property definition", Some("en"))
                        )
                    )
                ),
                subPropertyOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.HasValue.toSmartIri),
                ontologySchema = ApiV2WithValueObjects
            )

            actorUnderTest ! CreatePropertyRequestV2(
                propertyInfoContent = propertyInfoContent,
                lastModificationDate = anythingLastModDate,
                apiRequestID = UUID.randomUUID,
                requestingUser = anythingAdminUser
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure =>
                    if (printErrorMessages) println(msg.cause.getMessage)
                    msg.cause.isInstanceOf[BadRequestException] should ===(true)
            }
        }

        "not create a link property whose object type is knora-api:TextValue" in {

            val propertyIri = AnythingOntologyIri.makeEntityIri("wrongProperty")

            val propertyInfoContent = PropertyInfoContentV2(
                propertyIri = propertyIri,
                predicates = Map(
                    OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
                        objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.ObjectProperty.toSmartIri))
                    ),
                    OntologyConstants.KnoraApiV2WithValueObjects.SubjectType.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.KnoraApiV2WithValueObjects.SubjectType.toSmartIri,
                        objects = Seq(SmartIriLiteralV2(AnythingOntologyIri.makeEntityIri("Thing")))
                    ),
                    OntologyConstants.KnoraApiV2WithValueObjects.ObjectType.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.KnoraApiV2WithValueObjects.ObjectType.toSmartIri,
                        objects = Seq(SmartIriLiteralV2(OntologyConstants.KnoraApiV2WithValueObjects.TextValue.toSmartIri))
                    ),
                    OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
                        objects = Seq(
                            StringLiteralV2("wrong property", Some("en"))
                        )
                    ),
                    OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
                        objects = Seq(
                            StringLiteralV2("An invalid property definition", Some("en"))
                        )
                    )
                ),
                subPropertyOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.HasLinkTo.toSmartIri),
                ontologySchema = ApiV2WithValueObjects
            )

            actorUnderTest ! CreatePropertyRequestV2(
                propertyInfoContent = propertyInfoContent,
                lastModificationDate = anythingLastModDate,
                apiRequestID = UUID.randomUUID,
                requestingUser = anythingAdminUser
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure =>
                    if (printErrorMessages) println(msg.cause.getMessage)
                    msg.cause.isInstanceOf[BadRequestException] should ===(true)
            }
        }

        "not create a subproperty of anything:hasText with a knora-api:objectType of knora-api:IntegerValue" in {

            val propertyIri = AnythingOntologyIri.makeEntityIri("wrongProperty")

            val propertyInfoContent = PropertyInfoContentV2(
                propertyIri = propertyIri,
                predicates = Map(
                    OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
                        objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.ObjectProperty.toSmartIri))
                    ),
                    OntologyConstants.KnoraApiV2WithValueObjects.SubjectType.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.KnoraApiV2WithValueObjects.SubjectType.toSmartIri,
                        objects = Seq(SmartIriLiteralV2(AnythingOntologyIri.makeEntityIri("Thing")))
                    ),
                    OntologyConstants.KnoraApiV2WithValueObjects.ObjectType.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.KnoraApiV2WithValueObjects.ObjectType.toSmartIri,
                        objects = Seq(SmartIriLiteralV2(OntologyConstants.KnoraApiV2WithValueObjects.IntValue.toSmartIri))
                    ),
                    OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
                        objects = Seq(
                            StringLiteralV2("wrong property", Some("en"))
                        )
                    ),
                    OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
                        objects = Seq(
                            StringLiteralV2("An invalid property definition", Some("en"))
                        )
                    )
                ),
                subPropertyOf = Set(AnythingOntologyIri.makeEntityIri("hasText")),
                ontologySchema = ApiV2WithValueObjects
            )

            actorUnderTest ! CreatePropertyRequestV2(
                propertyInfoContent = propertyInfoContent,
                lastModificationDate = anythingLastModDate,
                apiRequestID = UUID.randomUUID,
                requestingUser = anythingAdminUser
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure =>
                    if (printErrorMessages) println(msg.cause.getMessage)
                    msg.cause.isInstanceOf[BadRequestException] should ===(true)
            }

        }

        "not create a subproperty of anything:hasBlueThing with a knora-api:objectType of anything:Thing" in {

            val propertyIri = AnythingOntologyIri.makeEntityIri("wrongProperty")

            val propertyInfoContent = PropertyInfoContentV2(
                propertyIri = propertyIri,
                predicates = Map(
                    OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
                        objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.ObjectProperty.toSmartIri))
                    ),
                    OntologyConstants.KnoraApiV2WithValueObjects.SubjectType.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.KnoraApiV2WithValueObjects.SubjectType.toSmartIri,
                        objects = Seq(SmartIriLiteralV2(AnythingOntologyIri.makeEntityIri("Thing")))
                    ),
                    OntologyConstants.KnoraApiV2WithValueObjects.ObjectType.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.KnoraApiV2WithValueObjects.ObjectType.toSmartIri,
                        objects = Seq(SmartIriLiteralV2(AnythingOntologyIri.makeEntityIri("Thing")))
                    ),
                    OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
                        objects = Seq(
                            StringLiteralV2("wrong property", Some("en"))
                        )
                    ),
                    OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
                        objects = Seq(
                            StringLiteralV2("An invalid property definition", Some("en"))
                        )
                    )
                ),
                subPropertyOf = Set(AnythingOntologyIri.makeEntityIri("hasBlueThing")),
                ontologySchema = ApiV2WithValueObjects
            )

            actorUnderTest ! CreatePropertyRequestV2(
                propertyInfoContent = propertyInfoContent,
                lastModificationDate = anythingLastModDate,
                apiRequestID = UUID.randomUUID,
                requestingUser = anythingAdminUser
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure =>
                    if (printErrorMessages) println(msg.cause.getMessage)
                    msg.cause.isInstanceOf[BadRequestException] should ===(true)
            }

        }

        "not create a property with an invalid salsah-gui:guiAttribute (invalid integer)" in {

            val propertyIri = AnythingOntologyIri.makeEntityIri("wrongProperty")

            val propertyInfoContent = PropertyInfoContentV2(
                propertyIri = propertyIri,
                predicates = Map(
                    OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
                        objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.ObjectProperty.toSmartIri))
                    ),
                    OntologyConstants.KnoraApiV2WithValueObjects.SubjectType.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.KnoraApiV2WithValueObjects.SubjectType.toSmartIri,
                        objects = Seq(SmartIriLiteralV2(AnythingOntologyIri.makeEntityIri("Thing")))
                    ),
                    OntologyConstants.KnoraApiV2WithValueObjects.ObjectType.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.KnoraApiV2WithValueObjects.ObjectType.toSmartIri,
                        objects = Seq(SmartIriLiteralV2(OntologyConstants.KnoraApiV2WithValueObjects.TextValue.toSmartIri))
                    ),
                    OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
                        objects = Seq(
                            StringLiteralV2("wrong property", Some("en"))
                        )
                    ),
                    OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
                        objects = Seq(
                            StringLiteralV2("An invalid property definition", Some("en"))
                        )
                    ),
                    OntologyConstants.SalsahGuiApiV2WithValueObjects.GuiElementProp.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.SalsahGuiApiV2WithValueObjects.GuiElementProp.toSmartIri,
                        objects = Seq(SmartIriLiteralV2("http://api.knora.org/ontology/salsah-gui/v2#Textarea".toSmartIri))
                    ),
                    OntologyConstants.SalsahGuiApiV2WithValueObjects.GuiAttribute.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.SalsahGuiApiV2WithValueObjects.GuiAttribute.toSmartIri,
                        objects = Seq(StringLiteralV2("rows=10"), StringLiteralV2("cols=80.5"))
                    )
                ),
                subPropertyOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.HasValue.toSmartIri),
                ontologySchema = ApiV2WithValueObjects
            )

            actorUnderTest ! CreatePropertyRequestV2(
                propertyInfoContent = propertyInfoContent,
                lastModificationDate = anythingLastModDate,
                apiRequestID = UUID.randomUUID,
                requestingUser = anythingAdminUser
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure =>
                    if (printErrorMessages) println(msg.cause.getMessage)
                    msg.cause.isInstanceOf[BadRequestException] should ===(true)
            }
        }

        "not create a property with an invalid salsah-gui:guiAttribute (wrong enumerated value)" in {

            val propertyIri = AnythingOntologyIri.makeEntityIri("wrongProperty")

            val propertyInfoContent = PropertyInfoContentV2(
                propertyIri = propertyIri,
                predicates = Map(
                    OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
                        objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.ObjectProperty.toSmartIri))
                    ),
                    OntologyConstants.KnoraApiV2WithValueObjects.SubjectType.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.KnoraApiV2WithValueObjects.SubjectType.toSmartIri,
                        objects = Seq(SmartIriLiteralV2(AnythingOntologyIri.makeEntityIri("Thing")))
                    ),
                    OntologyConstants.KnoraApiV2WithValueObjects.ObjectType.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.KnoraApiV2WithValueObjects.ObjectType.toSmartIri,
                        objects = Seq(SmartIriLiteralV2(OntologyConstants.KnoraApiV2WithValueObjects.TextValue.toSmartIri))
                    ),
                    OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
                        objects = Seq(
                            StringLiteralV2("wrong property", Some("en"))
                        )
                    ),
                    OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
                        objects = Seq(
                            StringLiteralV2("An invalid property definition", Some("en"))
                        )
                    ),
                    OntologyConstants.SalsahGuiApiV2WithValueObjects.GuiElementProp.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.SalsahGuiApiV2WithValueObjects.GuiElementProp.toSmartIri,
                        objects = Seq(SmartIriLiteralV2("http://api.knora.org/ontology/salsah-gui/v2#Textarea".toSmartIri))
                    ),
                    OntologyConstants.SalsahGuiApiV2WithValueObjects.GuiAttribute.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.SalsahGuiApiV2WithValueObjects.GuiAttribute.toSmartIri,
                        objects = Seq(StringLiteralV2("rows=10"), StringLiteralV2("cols=80"), StringLiteralV2("wrap=wrong"))
                    )
                ),
                subPropertyOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.HasValue.toSmartIri),
                ontologySchema = ApiV2WithValueObjects
            )

            actorUnderTest ! CreatePropertyRequestV2(
                propertyInfoContent = propertyInfoContent,
                lastModificationDate = anythingLastModDate,
                apiRequestID = UUID.randomUUID,
                requestingUser = anythingAdminUser
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure =>
                    if (printErrorMessages) println(msg.cause.getMessage)
                    msg.cause.isInstanceOf[BadRequestException] should ===(true)
            }
        }

        "not allow a user to change the labels of a property if they are not a sysadmin or an admin in the ontology's project" in {

            val propertyIri = AnythingOntologyIri.makeEntityIri("hasName")

            val newObjects = Seq(
                StringLiteralV2("has name", Some("en")),
                StringLiteralV2("a nom", Some("fr")),
                StringLiteralV2("hat Namen", Some("de"))
            )

            actorUnderTest ! ChangePropertyLabelsOrCommentsRequestV2(
                propertyIri = propertyIri,
                predicateToUpdate = OntologyConstants.Rdfs.Label.toSmartIri,
                newObjects = newObjects,
                lastModificationDate = anythingLastModDate,
                apiRequestID = UUID.randomUUID,
                requestingUser = anythingNonAdminUser
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[ForbiddenException] should ===(true)
            }

        }

        "change the labels of a property" in {
            val propertyIri = AnythingOntologyIri.makeEntityIri("hasName")

            val newObjects = Seq(
                StringLiteralV2("has name", Some("en")),
                StringLiteralV2("a nom", Some("fr")),
                StringLiteralV2("hat Namen", Some("de"))
            )

            actorUnderTest ! ChangePropertyLabelsOrCommentsRequestV2(
                propertyIri = propertyIri,
                predicateToUpdate = OntologyConstants.Rdfs.Label.toSmartIri,
                newObjects = newObjects,
                lastModificationDate = anythingLastModDate,
                apiRequestID = UUID.randomUUID,
                requestingUser = anythingAdminUser
            )

            expectMsgPF(timeout) {
                case msg: ReadOntologyV2 =>
                    val externalOntology = msg.toOntologySchema(ApiV2WithValueObjects)
                    assert(externalOntology.properties.size == 1)
                    val readPropertyInfo = externalOntology.properties(propertyIri)
                    readPropertyInfo.entityInfoContent.predicates(OntologyConstants.Rdfs.Label.toSmartIri).objects should ===(newObjects)

                    val metadata = externalOntology.ontologyMetadata
                    val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(throw AssertionException(s"${metadata.ontologyIri} has no last modification date"))
                    assert(newAnythingLastModDate.isAfter(anythingLastModDate))
                    anythingLastModDate = newAnythingLastModDate
            }
        }

        "not allow a user to change the comments of a property if they are not a sysadmin or an admin in the ontology's project" in {

            val propertyIri = AnythingOntologyIri.makeEntityIri("hasName")

            val newObjects = Seq(
                StringLiteralV2("The name of a Thing", Some("en")),
                StringLiteralV2("Le nom d\\'une chose", Some("fr")), // This is SPARQL-escaped as it would be if taken from a JSON-LD request.
                StringLiteralV2("Der Name eines Dinges", Some("de"))
            )

            actorUnderTest ! ChangePropertyLabelsOrCommentsRequestV2(
                propertyIri = propertyIri,
                predicateToUpdate = OntologyConstants.Rdfs.Comment.toSmartIri,
                newObjects = newObjects,
                lastModificationDate = anythingLastModDate,
                apiRequestID = UUID.randomUUID,
                requestingUser = anythingNonAdminUser
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[ForbiddenException] should ===(true)
            }

        }

        "change the comments of a property" in {
            val propertyIri = AnythingOntologyIri.makeEntityIri("hasName")

            val newObjects = Seq(
                StringLiteralV2("The name of a Thing", Some("en")),
                StringLiteralV2("Le nom d\\'une chose", Some("fr")), // This is SPARQL-escaped as it would be if taken from a JSON-LD request.
                StringLiteralV2("Der Name eines Dinges", Some("de"))
            )

            // Make an unescaped copy of the new comments, because this is how we will receive them in the API response.
            val newObjectsUnescaped = newObjects.map {
                case StringLiteralV2(text, lang) => StringLiteralV2(stringFormatter.fromSparqlEncodedString(text), lang)
            }

            actorUnderTest ! ChangePropertyLabelsOrCommentsRequestV2(
                propertyIri = propertyIri,
                predicateToUpdate = OntologyConstants.Rdfs.Comment.toSmartIri,
                newObjects = newObjects,
                lastModificationDate = anythingLastModDate,
                apiRequestID = UUID.randomUUID,
                requestingUser = anythingAdminUser
            )

            expectMsgPF(timeout) {
                case msg: ReadOntologyV2 =>
                    val externalOntology = msg.toOntologySchema(ApiV2WithValueObjects)
                    assert(externalOntology.properties.size == 1)
                    val readPropertyInfo = externalOntology.properties(propertyIri)
                    readPropertyInfo.entityInfoContent.predicates(OntologyConstants.Rdfs.Comment.toSmartIri).objects should ===(newObjectsUnescaped)

                    val metadata = externalOntology.ontologyMetadata
                    val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(throw AssertionException(s"${metadata.ontologyIri} has no last modification date"))
                    assert(newAnythingLastModDate.isAfter(anythingLastModDate))
                    anythingLastModDate = newAnythingLastModDate
            }
        }

        "not allow a user to create a class if they are not a sysadmin or an admin in the ontology's project" in {

            val classIri = AnythingOntologyIri.makeEntityIri("WildThing")

            val classInfoContent = ClassInfoContentV2(
                classIri = classIri,
                predicates = Map(
                    OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
                        objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.Class.toSmartIri))
                    ),
                    OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
                        objects = Seq(StringLiteralV2("wild thing", Some("en")))
                    ),
                    OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
                        objects = Seq(StringLiteralV2("A thing that is wild", Some("en")))
                    )
                ),
                directCardinalities = Map(
                    AnythingOntologyIri.makeEntityIri("hasName") -> KnoraCardinalityInfo(Cardinality.MayHaveOne),
                    AnythingOntologyIri.makeEntityIri("hasInteger") -> KnoraCardinalityInfo(cardinality = Cardinality.MayHaveOne, guiOrder = Some(20))
                ),
                subClassOf = Set(AnythingOntologyIri.makeEntityIri("Thing")),
                ontologySchema = ApiV2WithValueObjects
            )

            actorUnderTest ! CreateClassRequestV2(
                classInfoContent = classInfoContent,
                lastModificationDate = anythingLastModDate,
                apiRequestID = UUID.randomUUID,
                requestingUser = anythingNonAdminUser
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[ForbiddenException] should ===(true)
            }

        }

        "create a class anything:WildThing that is a subclass of anything:Thing, with a direct cardinality for anything:hasName, and overriding the cardinality for anything:hasInteger" in {
            val classIri = AnythingOntologyIri.makeEntityIri("WildThing")

            val classInfoContent = ClassInfoContentV2(
                classIri = classIri,
                predicates = Map(
                    OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
                        objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.Class.toSmartIri))
                    ),
                    OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
                        objects = Seq(StringLiteralV2("wild thing", Some("en")))
                    ),
                    OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
                        objects = Seq(StringLiteralV2("A thing that is wild", Some("en")))
                    )
                ),
                directCardinalities = Map(
                    AnythingOntologyIri.makeEntityIri("hasName") -> KnoraCardinalityInfo(Cardinality.MayHaveOne),
                    AnythingOntologyIri.makeEntityIri("hasInteger") -> KnoraCardinalityInfo(cardinality = Cardinality.MayHaveOne, guiOrder = Some(20))
                ),
                subClassOf = Set(AnythingOntologyIri.makeEntityIri("Thing")),
                ontologySchema = ApiV2WithValueObjects
            )

            actorUnderTest ! CreateClassRequestV2(
                classInfoContent = classInfoContent,
                lastModificationDate = anythingLastModDate,
                apiRequestID = UUID.randomUUID,
                requestingUser = anythingAdminUser
            )

            val expectedProperties: Set[SmartIri] = Set(
                "http://api.knora.org/ontology/knora-api/v2#hasStandoffLinkTo",
                "http://api.knora.org/ontology/knora-api/v2#hasStandoffLinkToValue",
                "http://0.0.0.0:3333/ontology/0001/anything/v2#hasOtherThingValue",
                "http://0.0.0.0:3333/ontology/0001/anything/v2#hasThingPictureValue",
                "http://0.0.0.0:3333/ontology/0001/anything/v2#hasGeoname",
                "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger",
                "http://0.0.0.0:3333/ontology/0001/anything/v2#hasName",
                "http://0.0.0.0:3333/ontology/0001/anything/v2#isPartOfOtherThing",
                "http://0.0.0.0:3333/ontology/0001/anything/v2#hasBoolean",
                "http://0.0.0.0:3333/ontology/0001/anything/v2#hasDecimal",
                "http://0.0.0.0:3333/ontology/0001/anything/v2#hasRichtext",
                "http://0.0.0.0:3333/ontology/0001/anything/v2#hasBlueThing",
                "http://0.0.0.0:3333/ontology/0001/anything/v2#hasOtherListItem",
                "http://0.0.0.0:3333/ontology/0001/anything/v2#hasOtherThing",
                "http://0.0.0.0:3333/ontology/0001/anything/v2#hasBlueThingValue",
                "http://0.0.0.0:3333/ontology/0001/anything/v2#hasGeometry",
                "http://0.0.0.0:3333/ontology/0001/anything/v2#hasText",
                "http://0.0.0.0:3333/ontology/0001/anything/v2#hasDate",
                "http://0.0.0.0:3333/ontology/0001/anything/v2#hasColor",
                "http://0.0.0.0:3333/ontology/0001/anything/v2#hasThingPicture",
                "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInterval",
                "http://0.0.0.0:3333/ontology/0001/anything/v2#hasListItem",
                "http://0.0.0.0:3333/ontology/0001/anything/v2#isPartOfOtherThingValue",
                "http://0.0.0.0:3333/ontology/0001/anything/v2#hasUri"
            ).map(_.toSmartIri)

            expectMsgPF(timeout) {
                case msg: ReadOntologyV2 =>
                    val externalOntology = msg.toOntologySchema(ApiV2WithValueObjects)
                    assert(externalOntology.classes.size == 1)
                    val readClassInfo = externalOntology.classes(classIri)
                    readClassInfo.entityInfoContent should ===(classInfoContent)
                    readClassInfo.inheritedCardinalities.keySet.contains("http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri) should ===(false)
                    readClassInfo.allResourcePropertyCardinalities.keySet should ===(expectedProperties)

                    val metadata = externalOntology.ontologyMetadata
                    val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(throw AssertionException(s"${metadata.ontologyIri} has no last modification date"))
                    assert(newAnythingLastModDate.isAfter(anythingLastModDate))
                    anythingLastModDate = newAnythingLastModDate
            }
        }

        "create a class anything:Nothing with no properties" in {
            val classIri = AnythingOntologyIri.makeEntityIri("Nothing")

            val classInfoContent = ClassInfoContentV2(
                classIri = classIri,
                predicates = Map(
                    OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
                        objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.Class.toSmartIri))
                    ),
                    OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
                        objects = Seq(
                            StringLiteralV2("nothing", Some("en")),
                            StringLiteralV2("Nichts", Some("de"))
                        )
                    ),
                    OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
                        objects = Seq(
                            StringLiteralV2("Represents nothing", Some("en")),
                            StringLiteralV2("Stellt nichts dar", Some("de"))
                        )
                    )
                ),
                subClassOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.Resource.toSmartIri),
                ontologySchema = ApiV2WithValueObjects
            )

            actorUnderTest ! CreateClassRequestV2(
                classInfoContent = classInfoContent,
                lastModificationDate = anythingLastModDate,
                apiRequestID = UUID.randomUUID,
                requestingUser = anythingAdminUser
            )

            val expectedProperties = Set(
                "http://api.knora.org/ontology/knora-api/v2#hasStandoffLinkTo",
                "http://api.knora.org/ontology/knora-api/v2#hasStandoffLinkToValue"
            ).map(_.toSmartIri)

            expectMsgPF(timeout) {
                case msg: ReadOntologyV2 =>
                    val externalOntology = msg.toOntologySchema(ApiV2WithValueObjects)
                    assert(externalOntology.classes.size == 1)
                    val readClassInfo = externalOntology.classes(classIri)
                    readClassInfo.entityInfoContent should ===(classInfoContent)
                    readClassInfo.allResourcePropertyCardinalities.keySet should ===(expectedProperties)

                    val metadata = externalOntology.ontologyMetadata
                    val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(throw AssertionException(s"${metadata.ontologyIri} has no last modification date"))
                    assert(newAnythingLastModDate.isAfter(anythingLastModDate))
                    anythingLastModDate = newAnythingLastModDate
            }
        }

        "not allow a user to change the labels of a class if they are not a sysadmin or an admin in the ontology's project" in {

            val classIri = AnythingOntologyIri.makeEntityIri("Nothing")

            val newObjects = Seq(
                StringLiteralV2("nothing", Some("en")),
                StringLiteralV2("rien", Some("fr"))
            )

            actorUnderTest ! ChangeClassLabelsOrCommentsRequestV2(
                classIri = classIri,
                predicateToUpdate = OntologyConstants.Rdfs.Label.toSmartIri,
                newObjects = newObjects,
                lastModificationDate = anythingLastModDate,
                apiRequestID = UUID.randomUUID,
                requestingUser = anythingNonAdminUser
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[ForbiddenException] should ===(true)
            }

        }

        "change the labels of a class" in {
            val classIri = AnythingOntologyIri.makeEntityIri("Nothing")

            val newObjects = Seq(
                StringLiteralV2("nothing", Some("en")),
                StringLiteralV2("rien", Some("fr"))
            )

            actorUnderTest ! ChangeClassLabelsOrCommentsRequestV2(
                classIri = classIri,
                predicateToUpdate = OntologyConstants.Rdfs.Label.toSmartIri,
                newObjects = newObjects,
                lastModificationDate = anythingLastModDate,
                apiRequestID = UUID.randomUUID,
                requestingUser = anythingAdminUser
            )

            expectMsgPF(timeout) {
                case msg: ReadOntologyV2 =>
                    val externalOntology = msg.toOntologySchema(ApiV2WithValueObjects)
                    assert(externalOntology.classes.size == 1)
                    val readClassInfo = externalOntology.classes(classIri)
                    readClassInfo.entityInfoContent.predicates(OntologyConstants.Rdfs.Label.toSmartIri).objects should ===(newObjects)

                    val metadata = externalOntology.ontologyMetadata
                    val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(throw AssertionException(s"${metadata.ontologyIri} has no last modification date"))
                    assert(newAnythingLastModDate.isAfter(anythingLastModDate))
                    anythingLastModDate = newAnythingLastModDate
            }
        }

        "not allow a user to change the comments of a class if they are not a sysadmin or an admin in the ontology's project" in {

            val classIri = AnythingOntologyIri.makeEntityIri("Nothing")

            val newObjects = Seq(
                StringLiteralV2("Represents nothing", Some("en")),
                StringLiteralV2("ne représente rien", Some("fr"))
            )

            actorUnderTest ! ChangeClassLabelsOrCommentsRequestV2(
                classIri = classIri,
                predicateToUpdate = OntologyConstants.Rdfs.Comment.toSmartIri,
                newObjects = newObjects,
                lastModificationDate = anythingLastModDate,
                apiRequestID = UUID.randomUUID,
                requestingUser = anythingNonAdminUser
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[ForbiddenException] should ===(true)
            }

        }

        "change the comments of a class" in {
            val classIri = AnythingOntologyIri.makeEntityIri("Nothing")

            val newObjects = Seq(
                StringLiteralV2("Represents nothing", Some("en")),
                StringLiteralV2("ne représente rien", Some("fr"))
            )

            // Make an unescaped copy of the new comments, because this is how we will receive them in the API response.
            val newObjectsUnescaped = newObjects.map {
                case StringLiteralV2(text, lang) => StringLiteralV2(stringFormatter.fromSparqlEncodedString(text), lang)
            }

            actorUnderTest ! ChangeClassLabelsOrCommentsRequestV2(
                classIri = classIri,
                predicateToUpdate = OntologyConstants.Rdfs.Comment.toSmartIri,
                newObjects = newObjects,
                lastModificationDate = anythingLastModDate,
                apiRequestID = UUID.randomUUID,
                requestingUser = anythingAdminUser
            )

            expectMsgPF(timeout) {
                case msg: ReadOntologyV2 =>
                    val externalOntology = msg.toOntologySchema(ApiV2WithValueObjects)
                    assert(externalOntology.classes.size == 1)
                    val readClassInfo = externalOntology.classes(classIri)
                    readClassInfo.entityInfoContent.predicates(OntologyConstants.Rdfs.Comment.toSmartIri).objects should ===(newObjectsUnescaped)

                    val metadata = externalOntology.ontologyMetadata
                    val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(throw AssertionException(s"${metadata.ontologyIri} has no last modification date"))
                    assert(newAnythingLastModDate.isAfter(anythingLastModDate))
                    anythingLastModDate = newAnythingLastModDate
            }
        }

        "not create a class with the wrong rdf:type" in {
            val classIri = AnythingOntologyIri.makeEntityIri("WrongClass")

            val classInfoContent = ClassInfoContentV2(
                classIri = classIri,
                predicates = Map(
                    OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
                        objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.ObjectProperty.toSmartIri))
                    ),
                    OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
                        objects = Seq(
                            StringLiteralV2("wrong class", Some("en"))
                        )
                    ),
                    OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
                        objects = Seq(
                            StringLiteralV2("An invalid class definition", Some("en"))
                        )
                    )
                ),
                subClassOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.Resource.toSmartIri),
                ontologySchema = ApiV2WithValueObjects
            )

            actorUnderTest ! CreateClassRequestV2(
                classInfoContent = classInfoContent,
                lastModificationDate = anythingLastModDate,
                apiRequestID = UUID.randomUUID,
                requestingUser = anythingAdminUser
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure =>
                    if (printErrorMessages) println(msg.cause.getMessage)
                    msg.cause.isInstanceOf[BadRequestException] should ===(true)
            }
        }

        "not create a class that already exists" in {
            val classIri = AnythingOntologyIri.makeEntityIri("Thing")

            val classInfoContent = ClassInfoContentV2(
                classIri = classIri,
                predicates = Map(
                    OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
                        objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.Class.toSmartIri))
                    ),
                    OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
                        objects = Seq(
                            StringLiteralV2("wrong class", Some("en"))
                        )
                    ),
                    OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
                        objects = Seq(
                            StringLiteralV2("An invalid class definition", Some("en"))
                        )
                    )
                ),
                subClassOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.Resource.toSmartIri),
                ontologySchema = ApiV2WithValueObjects
            )

            actorUnderTest ! CreateClassRequestV2(
                classInfoContent = classInfoContent,
                lastModificationDate = anythingLastModDate,
                apiRequestID = UUID.randomUUID,
                requestingUser = anythingAdminUser
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure =>
                    if (printErrorMessages) println(msg.cause.getMessage)
                    msg.cause.isInstanceOf[BadRequestException] should ===(true)
            }
        }

        "not create a class with a nonexistent base class" in {
            val classIri = AnythingOntologyIri.makeEntityIri("WrongClass")

            val classInfoContent = ClassInfoContentV2(
                classIri = classIri,
                predicates = Map(
                    OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
                        objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.Class.toSmartIri))
                    ),
                    OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
                        objects = Seq(
                            StringLiteralV2("wrong class", Some("en"))
                        )
                    ),
                    OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
                        objects = Seq(
                            StringLiteralV2("An invalid class definition", Some("en"))
                        )
                    )
                ),
                subClassOf = Set(AnythingOntologyIri.makeEntityIri("NonexistentClass")),
                ontologySchema = ApiV2WithValueObjects
            )

            actorUnderTest ! CreateClassRequestV2(
                classInfoContent = classInfoContent,
                lastModificationDate = anythingLastModDate,
                apiRequestID = UUID.randomUUID,
                requestingUser = anythingAdminUser
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure =>
                    if (printErrorMessages) println(msg.cause.getMessage)
                    msg.cause.isInstanceOf[BadRequestException] should ===(true)
            }
        }

        "not create a class that is not a subclass of knora-api:Resource" in {
            val classIri = AnythingOntologyIri.makeEntityIri("WrongClass")

            val classInfoContent = ClassInfoContentV2(
                classIri = classIri,
                predicates = Map(
                    OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
                        objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.Class.toSmartIri))
                    ),
                    OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
                        objects = Seq(
                            StringLiteralV2("wrong class", Some("en"))
                        )
                    ),
                    OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
                        objects = Seq(
                            StringLiteralV2("An invalid class definition", Some("en"))
                        )
                    )
                ),
                subClassOf = Set("http://xmlns.com/foaf/0.1/Person".toSmartIri),
                ontologySchema = ApiV2WithValueObjects
            )

            actorUnderTest ! CreateClassRequestV2(
                classInfoContent = classInfoContent,
                lastModificationDate = anythingLastModDate,
                apiRequestID = UUID.randomUUID,
                requestingUser = anythingAdminUser
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure =>
                    if (printErrorMessages) println(msg.cause.getMessage)
                    msg.cause.isInstanceOf[BadRequestException] should ===(true)
            }
        }

        "not create a class with a cardinality for a Knora property that doesn't exist" in {
            val classIri = AnythingOntologyIri.makeEntityIri("WrongClass")

            val classInfoContent = ClassInfoContentV2(
                classIri = classIri,
                predicates = Map(
                    OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
                        objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.Class.toSmartIri))
                    ),
                    OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
                        objects = Seq(
                            StringLiteralV2("wrong class", Some("en"))
                        )
                    ),
                    OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
                        objects = Seq(
                            StringLiteralV2("An invalid class definition", Some("en"))
                        )
                    )
                ),
                directCardinalities = Map(AnythingOntologyIri.makeEntityIri("nonexistentProperty") -> KnoraCardinalityInfo(Cardinality.MayHaveOne)),
                subClassOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.Resource.toSmartIri),
                ontologySchema = ApiV2WithValueObjects
            )

            actorUnderTest ! CreateClassRequestV2(
                classInfoContent = classInfoContent,
                lastModificationDate = anythingLastModDate,
                apiRequestID = UUID.randomUUID,
                requestingUser = anythingAdminUser
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure =>
                    if (printErrorMessages) println(msg.cause.getMessage)
                    msg.cause.isInstanceOf[NotFoundException] should ===(true)
            }
        }

        "not create a class that has a cardinality for anything:hasInteger but is not a subclass of anything:Thing" in {
            val classIri = AnythingOntologyIri.makeEntityIri("WrongClass")

            val classInfoContent = ClassInfoContentV2(
                classIri = classIri,
                predicates = Map(
                    OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
                        objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.Class.toSmartIri))
                    ),
                    OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
                        objects = Seq(
                            StringLiteralV2("wrong class", Some("en"))
                        )
                    ),
                    OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
                        objects = Seq(
                            StringLiteralV2("An invalid class definition", Some("en"))
                        )
                    )
                ),
                directCardinalities = Map(AnythingOntologyIri.makeEntityIri("hasInteger") -> KnoraCardinalityInfo(Cardinality.MayHaveOne)),
                subClassOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.Resource.toSmartIri),
                ontologySchema = ApiV2WithValueObjects
            )

            actorUnderTest ! CreateClassRequestV2(
                classInfoContent = classInfoContent,
                lastModificationDate = anythingLastModDate,
                apiRequestID = UUID.randomUUID,
                requestingUser = anythingAdminUser
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure =>
                    if (printErrorMessages) println(msg.cause.getMessage)
                    msg.cause.isInstanceOf[BadRequestException] should ===(true)
            }
        }

        "create a subclass of anything:Thing that has cardinality 1 for anything:hasBoolean" in {
            val classIri = AnythingOntologyIri.makeEntityIri("RestrictiveThing")

            val classInfoContent = ClassInfoContentV2(
                classIri = classIri,
                predicates = Map(
                    OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
                        objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.Class.toSmartIri))
                    ),
                    OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
                        objects = Seq(
                            StringLiteralV2("restrictive thing", Some("en"))
                        )
                    ),
                    OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
                        objects = Seq(
                            StringLiteralV2("A more restrictive Thing", Some("en"))
                        )
                    )
                ),
                directCardinalities = Map(AnythingOntologyIri.makeEntityIri("hasBoolean") -> KnoraCardinalityInfo(Cardinality.MustHaveOne)),
                subClassOf = Set(AnythingOntologyIri.makeEntityIri("Thing")),
                ontologySchema = ApiV2WithValueObjects
            )

            actorUnderTest ! CreateClassRequestV2(
                classInfoContent = classInfoContent,
                lastModificationDate = anythingLastModDate,
                apiRequestID = UUID.randomUUID,
                requestingUser = anythingAdminUser
            )

            expectMsgPF(timeout) {
                case msg: ReadOntologyV2 =>
                    val externalOntology = msg.toOntologySchema(ApiV2WithValueObjects)
                    assert(externalOntology.classes.size == 1)
                    val readClassInfo = externalOntology.classes(classIri)
                    readClassInfo.entityInfoContent should ===(classInfoContent)
                    readClassInfo.allCardinalities(AnythingOntologyIri.makeEntityIri("hasBoolean")).cardinality should ===(Cardinality.MustHaveOne)

                    val metadata = externalOntology.ontologyMetadata
                    val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(throw AssertionException(s"${metadata.ontologyIri} has no last modification date"))
                    assert(newAnythingLastModDate.isAfter(anythingLastModDate))
                    anythingLastModDate = newAnythingLastModDate
            }
        }


        "not create a subclass of anything:Thing that has cardinality 0-n for anything:hasBoolean" in {
            val classIri = AnythingOntologyIri.makeEntityIri("WrongClass")

            val classInfoContent = ClassInfoContentV2(
                classIri = classIri,
                predicates = Map(
                    OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
                        objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.Class.toSmartIri))
                    ),
                    OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
                        objects = Seq(
                            StringLiteralV2("wrong class", Some("en"))
                        )
                    ),
                    OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
                        objects = Seq(
                            StringLiteralV2("An invalid class definition", Some("en"))
                        )
                    )
                ),
                directCardinalities = Map(AnythingOntologyIri.makeEntityIri("hasBoolean") -> KnoraCardinalityInfo(Cardinality.MayHaveMany)),
                subClassOf = Set(AnythingOntologyIri.makeEntityIri("Thing")),
                ontologySchema = ApiV2WithValueObjects
            )

            actorUnderTest ! CreateClassRequestV2(
                classInfoContent = classInfoContent,
                lastModificationDate = anythingLastModDate,
                apiRequestID = UUID.randomUUID,
                requestingUser = anythingAdminUser
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure =>
                    if (printErrorMessages) println(msg.cause.getMessage)
                    msg.cause.isInstanceOf[BadRequestException] should ===(true)
            }
        }

        "create a property anything:hasNothingness with knora-api:subjectType anything:Nothing" in {
            val propertyIri = AnythingOntologyIri.makeEntityIri("hasNothingness")

            val propertyInfoContent = PropertyInfoContentV2(
                propertyIri = propertyIri,
                predicates = Map(
                    OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
                        objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.ObjectProperty.toSmartIri))
                    ),
                    OntologyConstants.KnoraApiV2WithValueObjects.SubjectType.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.KnoraApiV2WithValueObjects.SubjectType.toSmartIri,
                        objects = Seq(SmartIriLiteralV2(AnythingOntologyIri.makeEntityIri("Nothing")))
                    ),
                    OntologyConstants.KnoraApiV2WithValueObjects.ObjectType.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.KnoraApiV2WithValueObjects.ObjectType.toSmartIri,
                        objects = Seq(SmartIriLiteralV2(OntologyConstants.KnoraApiV2WithValueObjects.BooleanValue.toSmartIri))
                    ),
                    OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
                        objects = Seq(
                            StringLiteralV2("has nothingness", Some("en")),
                            StringLiteralV2("hat Nichtsein", Some("de"))
                        )
                    ),
                    OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
                        objects = Seq(
                            StringLiteralV2("Indicates whether a Nothing has nothingness", Some("en")),
                            StringLiteralV2("Anzeigt, ob ein Nichts Nichtsein hat", Some("de"))
                        )
                    ),
                    OntologyConstants.SalsahGuiApiV2WithValueObjects.GuiElementProp.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.SalsahGuiApiV2WithValueObjects.GuiElementProp.toSmartIri,
                        objects = Seq(SmartIriLiteralV2("http://api.knora.org/ontology/salsah-gui/v2#Checkbox".toSmartIri))
                    )
                ),
                subPropertyOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.HasValue.toSmartIri),
                ontologySchema = ApiV2WithValueObjects
            )

            actorUnderTest ! CreatePropertyRequestV2(
                propertyInfoContent = propertyInfoContent,
                lastModificationDate = anythingLastModDate,
                apiRequestID = UUID.randomUUID,
                requestingUser = anythingAdminUser
            )

            expectMsgPF(timeout) {
                case msg: ReadOntologyV2 =>
                    val externalOntology = msg.toOntologySchema(ApiV2WithValueObjects)
                    assert(externalOntology.properties.size == 1)
                    val property = externalOntology.properties(propertyIri)
                    property.entityInfoContent should ===(propertyInfoContent)

                    val metadata = externalOntology.ontologyMetadata
                    val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(throw AssertionException(s"${metadata.ontologyIri} has no last modification date"))
                    assert(newAnythingLastModDate.isAfter(anythingLastModDate))
                    anythingLastModDate = newAnythingLastModDate
            }
        }

        "not create a property called anything:Thing, because that IRI is already used for a class" in {
            val propertyIri = AnythingOntologyIri.makeEntityIri("Thing")

            val propertyInfoContent = PropertyInfoContentV2(
                propertyIri = propertyIri,
                predicates = Map(
                    OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
                        objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.ObjectProperty.toSmartIri))
                    ),
                    OntologyConstants.KnoraApiV2WithValueObjects.SubjectType.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.KnoraApiV2WithValueObjects.SubjectType.toSmartIri,
                        objects = Seq(SmartIriLiteralV2(AnythingOntologyIri.makeEntityIri("Nothing")))
                    ),
                    OntologyConstants.KnoraApiV2WithValueObjects.ObjectType.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.KnoraApiV2WithValueObjects.ObjectType.toSmartIri,
                        objects = Seq(SmartIriLiteralV2(OntologyConstants.KnoraApiV2WithValueObjects.BooleanValue.toSmartIri))
                    ),
                    OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
                        objects = Seq(
                            StringLiteralV2("wrong property", Some("en"))
                        )
                    ),
                    OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
                        objects = Seq(
                            StringLiteralV2("An invalid property definition", Some("en"))
                        )
                    )
                ),
                subPropertyOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.HasValue.toSmartIri),
                ontologySchema = ApiV2WithValueObjects
            )

            actorUnderTest ! CreatePropertyRequestV2(
                propertyInfoContent = propertyInfoContent,
                lastModificationDate = anythingLastModDate,
                apiRequestID = UUID.randomUUID,
                requestingUser = anythingAdminUser
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure =>
                    if (printErrorMessages) println(msg.cause.getMessage)
                    msg.cause.isInstanceOf[BadRequestException] should ===(true)
            }
        }

        "not create a class called anything:hasNothingness, because that IRI is already used for a property" in {
            val classIri = AnythingOntologyIri.makeEntityIri("hasNothingness")

            val classInfoContent = ClassInfoContentV2(
                classIri = classIri,
                predicates = Map(
                    OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
                        objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.Class.toSmartIri))
                    ),
                    OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
                        objects = Seq(
                            StringLiteralV2("wrong class", Some("en"))
                        )
                    ),
                    OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
                        objects = Seq(
                            StringLiteralV2("An invalid class definition", Some("en"))
                        )
                    )
                ),
                subClassOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.Resource.toSmartIri),
                ontologySchema = ApiV2WithValueObjects
            )

            actorUnderTest ! CreateClassRequestV2(
                classInfoContent = classInfoContent,
                lastModificationDate = anythingLastModDate,
                apiRequestID = UUID.randomUUID,
                requestingUser = anythingAdminUser
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure =>
                    if (printErrorMessages) println(msg.cause.getMessage)
                    msg.cause.isInstanceOf[BadRequestException] should ===(true)
            }
        }

        "create a class anything:Void as a subclass of anything:Nothing" in {
            val classIri = AnythingOntologyIri.makeEntityIri("Void")

            val classInfoContent = ClassInfoContentV2(
                classIri = classIri,
                predicates = Map(
                    OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
                        objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.Class.toSmartIri))
                    ),
                    OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
                        objects = Seq(StringLiteralV2("void", Some("en")))
                    ),
                    OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
                        objects = Seq(StringLiteralV2("Represents a void", Some("en")))
                    )
                ),
                subClassOf = Set(AnythingOntologyIri.makeEntityIri("Nothing")),
                ontologySchema = ApiV2WithValueObjects
            )

            actorUnderTest ! CreateClassRequestV2(
                classInfoContent = classInfoContent,
                lastModificationDate = anythingLastModDate,
                apiRequestID = UUID.randomUUID,
                requestingUser = anythingAdminUser
            )

            expectMsgPF(timeout) {
                case msg: ReadOntologyV2 =>
                    val externalOntology = msg.toOntologySchema(ApiV2WithValueObjects)
                    assert(externalOntology.classes.size == 1)
                    val readClassInfo = externalOntology.classes(classIri)
                    readClassInfo.entityInfoContent should ===(classInfoContent)

                    val metadata = externalOntology.ontologyMetadata
                    val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(throw AssertionException(s"${metadata.ontologyIri} has no last modification date"))
                    assert(newAnythingLastModDate.isAfter(anythingLastModDate))
                    anythingLastModDate = newAnythingLastModDate
            }
        }

        "not add a cardinality to the class anything:Nothing, because it has a subclass" in {
            val classIri = AnythingOntologyIri.makeEntityIri("Nothing")

            val classInfoContent = ClassInfoContentV2(
                classIri = classIri,
                predicates = Map(
                    OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
                        objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.Class.toSmartIri))
                    )
                ),
                directCardinalities = Map(
                    AnythingOntologyIri.makeEntityIri("hasNothingness") -> KnoraCardinalityInfo(Cardinality.MayHaveOne)
                ),
                ontologySchema = ApiV2WithValueObjects
            )

            actorUnderTest ! AddCardinalitiesToClassRequestV2(
                classInfoContent = classInfoContent,
                lastModificationDate = anythingLastModDate,
                apiRequestID = UUID.randomUUID,
                requestingUser = anythingAdminUser
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure =>
                    if (printErrorMessages) println(msg.cause.getMessage)
                    msg.cause.isInstanceOf[BadRequestException] should ===(true)
            }
        }

        "not allow a user to delete a class if they are not a sysadmin or an admin in the ontology's project" in {
            val classIri = AnythingOntologyIri.makeEntityIri("Void")

            actorUnderTest ! DeleteClassRequestV2(
                classIri = classIri,
                lastModificationDate = anythingLastModDate,
                apiRequestID = UUID.randomUUID,
                requestingUser = anythingNonAdminUser
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[ForbiddenException] should ===(true)
            }
        }


        "delete the class anything:Void" in {
            val classIri = AnythingOntologyIri.makeEntityIri("Void")

            actorUnderTest ! DeleteClassRequestV2(
                classIri = classIri,
                lastModificationDate = anythingLastModDate,
                apiRequestID = UUID.randomUUID,
                requestingUser = anythingAdminUser
            )

            expectMsgPF(timeout) {
                case msg: ReadOntologyMetadataV2 =>
                    assert(msg.ontologies.size == 1)
                    val metadata = msg.ontologies.head
                    val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(throw AssertionException(s"${metadata.ontologyIri} has no last modification date"))
                    assert(newAnythingLastModDate.isAfter(anythingLastModDate))
                    anythingLastModDate = newAnythingLastModDate
            }
        }

        "not allow a user to add a cardinality to a class if they are not a sysadmin or an admin in the user's project" in {

            val classIri = AnythingOntologyIri.makeEntityIri("Nothing")

            val classInfoContent = ClassInfoContentV2(
                classIri = classIri,
                predicates = Map(
                    OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
                        objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.Class.toSmartIri))
                    )
                ),
                directCardinalities = Map(
                    AnythingOntologyIri.makeEntityIri("hasNothingness") -> KnoraCardinalityInfo(cardinality = Cardinality.MayHaveOne, guiOrder = Some(0))
                ),
                ontologySchema = ApiV2WithValueObjects
            )

            actorUnderTest ! AddCardinalitiesToClassRequestV2(
                classInfoContent = classInfoContent,
                lastModificationDate = anythingLastModDate,
                apiRequestID = UUID.randomUUID,
                requestingUser = anythingNonAdminUser
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[ForbiddenException] should ===(true)
            }

        }


        "add a cardinality for the property anything:hasNothingness to the class anything:Nothing" in {
            val classIri = AnythingOntologyIri.makeEntityIri("Nothing")

            val classInfoContent = ClassInfoContentV2(
                classIri = classIri,
                predicates = Map(
                    OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
                        objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.Class.toSmartIri))
                    )
                ),
                directCardinalities = Map(
                    AnythingOntologyIri.makeEntityIri("hasNothingness") -> KnoraCardinalityInfo(cardinality = Cardinality.MayHaveOne, guiOrder = Some(0))
                ),
                ontologySchema = ApiV2WithValueObjects
            )

            actorUnderTest ! AddCardinalitiesToClassRequestV2(
                classInfoContent = classInfoContent,
                lastModificationDate = anythingLastModDate,
                apiRequestID = UUID.randomUUID,
                requestingUser = anythingAdminUser
            )

            val expectedProperties = Set(
                OntologyConstants.KnoraApiV2WithValueObjects.HasStandoffLinkTo.toSmartIri,
                OntologyConstants.KnoraApiV2WithValueObjects.HasStandoffLinkToValue.toSmartIri,
                AnythingOntologyIri.makeEntityIri("hasNothingness")
            )

            expectMsgPF(timeout) {
                case msg: ReadOntologyV2 =>
                    val externalOntology = msg.toOntologySchema(ApiV2WithValueObjects)
                    assert(externalOntology.classes.size == 1)
                    val readClassInfo = externalOntology.classes(classIri)
                    readClassInfo.entityInfoContent.directCardinalities should ===(classInfoContent.directCardinalities)
                    readClassInfo.allResourcePropertyCardinalities.keySet should ===(expectedProperties)

                    val metadata = externalOntology.ontologyMetadata
                    val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(throw AssertionException(s"${metadata.ontologyIri} has no last modification date"))
                    assert(newAnythingLastModDate.isAfter(anythingLastModDate))
                    anythingLastModDate = newAnythingLastModDate
            }
        }

        "not add a cardinality for property anything:hasName to class anything:BlueThing, because the class is used in data" in {
            val classIri = AnythingOntologyIri.makeEntityIri("BlueThing")

            val classInfoContent = ClassInfoContentV2(
                classIri = classIri,
                predicates = Map(
                    OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
                        objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.Class.toSmartIri))
                    )
                ),
                directCardinalities = Map(
                    AnythingOntologyIri.makeEntityIri("hasName") -> KnoraCardinalityInfo(Cardinality.MayHaveOne)
                ),
                ontologySchema = ApiV2WithValueObjects
            )

            actorUnderTest ! AddCardinalitiesToClassRequestV2(
                classInfoContent = classInfoContent,
                lastModificationDate = anythingLastModDate,
                apiRequestID = UUID.randomUUID,
                requestingUser = anythingAdminUser
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure =>
                    if (printErrorMessages) println(msg.cause.getMessage)
                    msg.cause.isInstanceOf[BadRequestException] should ===(true)
            }
        }

        "create a property anything:hasEmptiness with knora-api:subjectType anything:Nothing" in {
            val propertyIri = AnythingOntologyIri.makeEntityIri("hasEmptiness")

            val propertyInfoContent = PropertyInfoContentV2(
                propertyIri = propertyIri,
                predicates = Map(
                    OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
                        objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.ObjectProperty.toSmartIri))
                    ),
                    OntologyConstants.KnoraApiV2WithValueObjects.SubjectType.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.KnoraApiV2WithValueObjects.SubjectType.toSmartIri,
                        objects = Seq(SmartIriLiteralV2(AnythingOntologyIri.makeEntityIri("Nothing")))
                    ),
                    OntologyConstants.KnoraApiV2WithValueObjects.ObjectType.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.KnoraApiV2WithValueObjects.ObjectType.toSmartIri,
                        objects = Seq(SmartIriLiteralV2(OntologyConstants.KnoraApiV2WithValueObjects.BooleanValue.toSmartIri))
                    ),
                    OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
                        objects = Seq(
                            StringLiteralV2("has emptiness", Some("en")),
                            StringLiteralV2("hat Leerheit", Some("de"))
                        )
                    ),
                    OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
                        objects = Seq(
                            StringLiteralV2("Indicates whether a Nothing has emptiness", Some("en")),
                            StringLiteralV2("Anzeigt, ob ein Nichts Leerheit hat", Some("de"))
                        )
                    )
                ),
                subPropertyOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.HasValue.toSmartIri),
                ontologySchema = ApiV2WithValueObjects
            )

            actorUnderTest ! CreatePropertyRequestV2(
                propertyInfoContent = propertyInfoContent,
                lastModificationDate = anythingLastModDate,
                apiRequestID = UUID.randomUUID,
                requestingUser = anythingAdminUser
            )

            expectMsgPF(timeout) {
                case msg: ReadOntologyV2 =>
                    val externalOntology = msg.toOntologySchema(ApiV2WithValueObjects)
                    assert(externalOntology.properties.size == 1)
                    val property = externalOntology.properties(propertyIri)

                    property.entityInfoContent should ===(propertyInfoContent)
                    val metadata = externalOntology.ontologyMetadata
                    val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(throw AssertionException(s"${metadata.ontologyIri} has no last modification date"))
                    assert(newAnythingLastModDate.isAfter(anythingLastModDate))
                    anythingLastModDate = newAnythingLastModDate
            }
        }

        "add a cardinality for the property anything:hasEmptiness to the class anything:Nothing" in {
            val classIri = AnythingOntologyIri.makeEntityIri("Nothing")

            val classInfoContent = ClassInfoContentV2(
                classIri = classIri,
                predicates = Map(
                    OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
                        objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.Class.toSmartIri))
                    )
                ),
                directCardinalities = Map(
                    AnythingOntologyIri.makeEntityIri("hasEmptiness") -> KnoraCardinalityInfo(cardinality = Cardinality.MayHaveOne, guiOrder = Some(1))
                ),
                ontologySchema = ApiV2WithValueObjects
            )

            actorUnderTest ! AddCardinalitiesToClassRequestV2(
                classInfoContent = classInfoContent,
                lastModificationDate = anythingLastModDate,
                apiRequestID = UUID.randomUUID,
                requestingUser = anythingAdminUser
            )

            val expectedDirectCardinalities = Map(
                AnythingOntologyIri.makeEntityIri("hasNothingness") -> KnoraCardinalityInfo(cardinality = Cardinality.MayHaveOne, guiOrder = Some(0)),
                AnythingOntologyIri.makeEntityIri("hasEmptiness") -> KnoraCardinalityInfo(cardinality = Cardinality.MayHaveOne, guiOrder = Some(1))
            )

            val expectedProperties = Set(
                OntologyConstants.KnoraApiV2WithValueObjects.HasStandoffLinkTo.toSmartIri,
                OntologyConstants.KnoraApiV2WithValueObjects.HasStandoffLinkToValue.toSmartIri,
                AnythingOntologyIri.makeEntityIri("hasNothingness"),
                AnythingOntologyIri.makeEntityIri("hasEmptiness")
            )

            expectMsgPF(timeout) {
                case msg: ReadOntologyV2 =>
                    val externalOntology = msg.toOntologySchema(ApiV2WithValueObjects)
                    assert(externalOntology.classes.size == 1)
                    val readClassInfo = externalOntology.classes(classIri)
                    readClassInfo.entityInfoContent.directCardinalities should ===(expectedDirectCardinalities)
                    readClassInfo.allResourcePropertyCardinalities.keySet should ===(expectedProperties)

                    val metadata = externalOntology.ontologyMetadata
                    val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(throw AssertionException(s"${metadata.ontologyIri} has no last modification date"))
                    assert(newAnythingLastModDate.isAfter(anythingLastModDate))
                    anythingLastModDate = newAnythingLastModDate
            }
        }

        "not allow a user to change the cardinalities of a class if they are not a sysadmin or an admin in the user's project" in {

            val classIri = AnythingOntologyIri.makeEntityIri("Nothing")

            val classInfoContent = ClassInfoContentV2(
                classIri = classIri,
                predicates = Map(
                    OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
                        objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.Class.toSmartIri))
                    )
                ),
                directCardinalities = Map(
                    AnythingOntologyIri.makeEntityIri("hasEmptiness") -> KnoraCardinalityInfo(cardinality = Cardinality.MayHaveOne, guiOrder = Some(0))
                ),
                ontologySchema = ApiV2WithValueObjects
            )

            actorUnderTest ! ChangeCardinalitiesRequestV2(
                classInfoContent = classInfoContent,
                lastModificationDate = anythingLastModDate,
                apiRequestID = UUID.randomUUID,
                requestingUser = anythingNonAdminUser
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[ForbiddenException] should ===(true)
            }

        }

        "change the cardinalities of the class anything:Nothing, removing anything:hasNothingness and leaving anything:hasEmptiness" in {
            val classIri = AnythingOntologyIri.makeEntityIri("Nothing")

            val classInfoContent = ClassInfoContentV2(
                classIri = classIri,
                predicates = Map(
                    OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
                        objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.Class.toSmartIri))
                    )
                ),
                directCardinalities = Map(
                    AnythingOntologyIri.makeEntityIri("hasEmptiness") -> KnoraCardinalityInfo(cardinality = Cardinality.MayHaveOne, guiOrder = Some(0))
                ),
                ontologySchema = ApiV2WithValueObjects
            )

            actorUnderTest ! ChangeCardinalitiesRequestV2(
                classInfoContent = classInfoContent,
                lastModificationDate = anythingLastModDate,
                apiRequestID = UUID.randomUUID,
                requestingUser = anythingAdminUser
            )

            val expectedProperties = Set(
                OntologyConstants.KnoraApiV2WithValueObjects.HasStandoffLinkTo.toSmartIri,
                OntologyConstants.KnoraApiV2WithValueObjects.HasStandoffLinkToValue.toSmartIri,
                AnythingOntologyIri.makeEntityIri("hasEmptiness")
            )

            expectMsgPF(timeout) {
                case msg: ReadOntologyV2 =>
                    val externalOntology = msg.toOntologySchema(ApiV2WithValueObjects)
                    assert(externalOntology.classes.size == 1)
                    val readClassInfo = externalOntology.classes(classIri)
                    readClassInfo.entityInfoContent.directCardinalities should ===(classInfoContent.directCardinalities)
                    readClassInfo.allResourcePropertyCardinalities.keySet should ===(expectedProperties)

                    val metadata = externalOntology.ontologyMetadata
                    val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(throw AssertionException(s"${metadata.ontologyIri} has no last modification date"))
                    assert(newAnythingLastModDate.isAfter(anythingLastModDate))
                    anythingLastModDate = newAnythingLastModDate
            }
        }

        "not delete the class anything:Nothing, because the property anything:hasEmptiness refers to it" in {
            val classIri = AnythingOntologyIri.makeEntityIri("Nothing")

            actorUnderTest ! DeleteClassRequestV2(
                classIri = classIri,
                lastModificationDate = anythingLastModDate,
                apiRequestID = UUID.randomUUID,
                requestingUser = anythingAdminUser
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure =>
                    if (printErrorMessages) println(msg.cause.getMessage)
                    msg.cause.isInstanceOf[BadRequestException] should ===(true)
            }
        }

        "delete the property anything:hasNothingness" in {
            val hasNothingness = AnythingOntologyIri.makeEntityIri("hasNothingness")

            actorUnderTest ! DeletePropertyRequestV2(
                propertyIri = hasNothingness,
                lastModificationDate = anythingLastModDate,
                apiRequestID = UUID.randomUUID,
                requestingUser = anythingAdminUser
            )

            expectMsgPF(timeout) {
                case msg: ReadOntologyMetadataV2 =>
                    assert(msg.ontologies.size == 1)
                    val metadata = msg.ontologies.head
                    val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(throw AssertionException(s"${metadata.ontologyIri} has no last modification date"))
                    assert(newAnythingLastModDate.isAfter(anythingLastModDate))
                    anythingLastModDate = newAnythingLastModDate
            }
        }

        "not delete the property anything:hasEmptiness, because the class anything:Nothing refers to it" in {
            val hasNothingness = AnythingOntologyIri.makeEntityIri("hasEmptiness")

            actorUnderTest ! DeletePropertyRequestV2(
                propertyIri = hasNothingness,
                lastModificationDate = anythingLastModDate,
                apiRequestID = UUID.randomUUID,
                requestingUser = anythingAdminUser
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure =>
                    if (printErrorMessages) println(msg.cause.getMessage)
                    msg.cause.isInstanceOf[BadRequestException] should ===(true)
            }
        }

        "not allow a user to remove all cardinalities from a class if they are not a sysadmin or an admin in the user's project" in {

            val classIri = AnythingOntologyIri.makeEntityIri("Nothing")

            val classInfoContent = ClassInfoContentV2(
                classIri = classIri,
                predicates = Map(
                    OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
                        objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.Class.toSmartIri))
                    )
                ),
                ontologySchema = ApiV2WithValueObjects
            )

            actorUnderTest ! ChangeCardinalitiesRequestV2(
                classInfoContent = classInfoContent,
                lastModificationDate = anythingLastModDate,
                apiRequestID = UUID.randomUUID,
                requestingUser = anythingNonAdminUser
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[ForbiddenException] should ===(true)
            }
        }

        "remove all cardinalities from the class anything:Nothing" in {
            val classIri = AnythingOntologyIri.makeEntityIri("Nothing")

            val classInfoContent = ClassInfoContentV2(
                classIri = classIri,
                predicates = Map(
                    OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
                        objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.Class.toSmartIri))
                    )
                ),
                ontologySchema = ApiV2WithValueObjects
            )

            actorUnderTest ! ChangeCardinalitiesRequestV2(
                classInfoContent = classInfoContent,
                lastModificationDate = anythingLastModDate,
                apiRequestID = UUID.randomUUID,
                requestingUser = anythingAdminUser
            )

            val expectedProperties = Set(
                OntologyConstants.KnoraApiV2WithValueObjects.HasStandoffLinkTo.toSmartIri,
                OntologyConstants.KnoraApiV2WithValueObjects.HasStandoffLinkToValue.toSmartIri
            )

            expectMsgPF(timeout) {
                case msg: ReadOntologyV2 =>
                    val externalOntology = msg.toOntologySchema(ApiV2WithValueObjects)
                    assert(externalOntology.classes.size == 1)
                    val readClassInfo = externalOntology.classes(classIri)
                    readClassInfo.entityInfoContent.directCardinalities should ===(classInfoContent.directCardinalities)
                    readClassInfo.allResourcePropertyCardinalities.keySet should ===(expectedProperties)

                    val metadata = externalOntology.ontologyMetadata
                    val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(throw AssertionException(s"${metadata.ontologyIri} has no last modification date"))
                    assert(newAnythingLastModDate.isAfter(anythingLastModDate))
                    anythingLastModDate = newAnythingLastModDate
            }
        }

        "not delete the property anything:hasEmptiness with the wrong knora-api:lastModificationDate" in {
            val hasEmptiness = AnythingOntologyIri.makeEntityIri("hasEmptiness")

            actorUnderTest ! DeletePropertyRequestV2(
                propertyIri = hasEmptiness,
                lastModificationDate = anythingLastModDate.minusSeconds(60),
                apiRequestID = UUID.randomUUID,
                requestingUser = anythingAdminUser
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure =>
                    if (printErrorMessages) println(msg.cause.getMessage)
                    msg.cause.isInstanceOf[EditConflictException] should ===(true)
            }
        }

        "not allow a user to delete a property if they are not a sysadmin or an admin in the ontology's project" in {
            val hasEmptiness = AnythingOntologyIri.makeEntityIri("hasEmptiness")

            actorUnderTest ! DeletePropertyRequestV2(
                propertyIri = hasEmptiness,
                lastModificationDate = anythingLastModDate,
                apiRequestID = UUID.randomUUID,
                requestingUser = anythingNonAdminUser
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[ForbiddenException] should ===(true)
            }
        }

        "delete the property anything:hasEmptiness" in {
            val hasEmptiness = AnythingOntologyIri.makeEntityIri("hasEmptiness")

            actorUnderTest ! DeletePropertyRequestV2(
                propertyIri = hasEmptiness,
                lastModificationDate = anythingLastModDate,
                apiRequestID = UUID.randomUUID,
                requestingUser = anythingAdminUser
            )

            expectMsgPF(timeout) {
                case msg: ReadOntologyMetadataV2 =>
                    assert(msg.ontologies.size == 1)
                    val metadata = msg.ontologies.head
                    val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(throw AssertionException(s"${metadata.ontologyIri} has no last modification date"))
                    assert(newAnythingLastModDate.isAfter(anythingLastModDate))
                    anythingLastModDate = newAnythingLastModDate
            }
        }

        "delete the class anything:Nothing" in {
            val classIri = AnythingOntologyIri.makeEntityIri("Nothing")

            actorUnderTest ! DeleteClassRequestV2(
                classIri = classIri,
                lastModificationDate = anythingLastModDate,
                apiRequestID = UUID.randomUUID,
                requestingUser = anythingAdminUser
            )

            expectMsgPF(timeout) {
                case msg: ReadOntologyMetadataV2 =>
                    assert(msg.ontologies.size == 1)
                    val metadata = msg.ontologies.head
                    val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(throw AssertionException(s"${metadata.ontologyIri} has no last modification date"))
                    assert(newAnythingLastModDate.isAfter(anythingLastModDate))
                    anythingLastModDate = newAnythingLastModDate
            }
        }

        "not load an ontology that has no knora-base:attachedToProject" in {
            val invalidOnto = List(RdfDataObject(
                path = "_test_data/responders.v2.OntologyResponderV2Spec/onto-without-project.ttl", name = "http://www.knora.org/ontology/invalid"
            ))

            loadTestData(invalidOnto)
            expectMsgType[akka.actor.Status.Failure](timeout).cause.isInstanceOf[InconsistentTriplestoreDataException] should ===(true)
        }

        "not load an ontology containing a class that's missing a cardinality for a link value property" in {
            val invalidOnto = List(RdfDataObject(
                path = "_test_data/responders.v2.OntologyResponderV2Spec/missing-link-value-cardinality-onto.ttl", name = "http://www.knora.org/ontology/invalid"
            ))

            loadTestData(invalidOnto)
            expectMsgType[akka.actor.Status.Failure](timeout).cause.isInstanceOf[InconsistentTriplestoreDataException] should ===(true)
        }


        "not load an ontology containing a class that's missing a cardinality for a link property" in {
            val invalidOnto = List(RdfDataObject(
                path = "_test_data/responders.v2.OntologyResponderV2Spec/missing-link-cardinality-onto.ttl", name = "http://www.knora.org/ontology/invalid"
            ))

            loadTestData(invalidOnto)
            expectMsgType[akka.actor.Status.Failure](timeout).cause.isInstanceOf[InconsistentTriplestoreDataException] should ===(true)
        }

        "not load an ontology containing a class with a cardinality whose subject class constraint is incompatible with the class" in {
            val invalidOnto = List(RdfDataObject(
                path = "_test_data/responders.v2.OntologyResponderV2Spec/class-incompatible-with-scc-onto.ttl", name = "http://www.knora.org/ontology/invalid"
            ))

            loadTestData(invalidOnto)
            expectMsgType[akka.actor.Status.Failure](timeout).cause.isInstanceOf[InconsistentTriplestoreDataException] should ===(true)
        }

        "not load an ontology containing a resource class without an rdfs:label" in {
            val invalidOnto = List(RdfDataObject(
                path = "_test_data/responders.v2.OntologyResponderV2Spec/class-without-label-onto.ttl", name = "http://www.knora.org/ontology/invalid"
            ))

            loadTestData(invalidOnto)
            expectMsgType[akka.actor.Status.Failure](timeout).cause.isInstanceOf[InconsistentTriplestoreDataException] should ===(true)
        }

        "not load an ontology containing a resource property without an rdfs:label" in {
            val invalidOnto = List(RdfDataObject(
                path = "_test_data/responders.v2.OntologyResponderV2Spec/property-without-label-onto.ttl", name = "http://www.knora.org/ontology/invalid"
            ))

            loadTestData(invalidOnto)
            expectMsgType[akka.actor.Status.Failure](timeout).cause.isInstanceOf[InconsistentTriplestoreDataException] should ===(true)
        }

        "not load an ontology containing a resource class that is also a standoff class" in {
            val invalidOnto = List(RdfDataObject(
                path = "_test_data/responders.v2.OntologyResponderV2Spec/resource-class-is-standoff-class-onto.ttl", name = "http://www.knora.org/ontology/invalid"
            ))

            loadTestData(invalidOnto)
            expectMsgType[akka.actor.Status.Failure](timeout).cause.isInstanceOf[InconsistentTriplestoreDataException] should ===(true)
        }

        "not load an ontology containing a resource class with a cardinality on an undefined property" in {
            val invalidOnto = List(RdfDataObject(
                path = "_test_data/responders.v2.OntologyResponderV2Spec/class-with-missing-property-onto.ttl", name = "http://www.knora.org/ontology/invalid"
            ))

            loadTestData(invalidOnto)
            expectMsgType[akka.actor.Status.Failure](timeout).cause.isInstanceOf[InconsistentTriplestoreDataException] should ===(true)
        }

        "not load an ontology containing a resource class with a directly defined cardinality on a non-resource property" in {
            val invalidOnto = List(RdfDataObject(
                path = "_test_data/responders.v2.OntologyResponderV2Spec/class-with-non-resource-prop-cardinality-onto.ttl", name = "http://www.knora.org/ontology/invalid"
            ))

            loadTestData(invalidOnto)
            expectMsgType[akka.actor.Status.Failure](timeout).cause.isInstanceOf[InconsistentTriplestoreDataException] should ===(true)
        }

        "not load an ontology containing a resource class with a cardinality on knora-base:resourceProperty" in {
            val invalidOnto = List(RdfDataObject(
                path = "_test_data/responders.v2.OntologyResponderV2Spec/class-with-cardinality-on-kbresprop-onto.ttl", name = "http://www.knora.org/ontology/invalid"
            ))

            loadTestData(invalidOnto)
            expectMsgType[akka.actor.Status.Failure](timeout).cause.isInstanceOf[InconsistentTriplestoreDataException] should ===(true)
        }

        "not load an ontology containing a resource class with a cardinality on knora-base:hasValue" in {
            val invalidOnto = List(RdfDataObject(
                path = "_test_data/responders.v2.OntologyResponderV2Spec/class-with-cardinality-on-kbhasvalue-onto.ttl", name = "http://www.knora.org/ontology/invalid"
            ))

            loadTestData(invalidOnto)
            expectMsgType[akka.actor.Status.Failure](timeout).cause.isInstanceOf[InconsistentTriplestoreDataException] should ===(true)
        }

        "not load an ontology containing a resource class with a base class that has a Knora IRI but isn't a resource class" in {
            val invalidOnto = List(RdfDataObject(
                path = "_test_data/responders.v2.OntologyResponderV2Spec/resource-class-with-invalid-base-class-onto.ttl", name = "http://www.knora.org/ontology/invalid"
            ))

            loadTestData(invalidOnto)
            expectMsgType[akka.actor.Status.Failure](timeout).cause.isInstanceOf[InconsistentTriplestoreDataException] should ===(true)
        }

        "not load an ontology containing a standoff class with a cardinality on a resource property" in {
            val invalidOnto = List(RdfDataObject(
                path = "_test_data/responders.v2.OntologyResponderV2Spec/standoff-class-with-resprop-cardinality-onto.ttl", name = "http://www.knora.org/ontology/invalid"
            ))

            loadTestData(invalidOnto)
            expectMsgType[akka.actor.Status.Failure](timeout).cause.isInstanceOf[InconsistentTriplestoreDataException] should ===(true)
        }

        "not load an ontology containing a standoff class with a base class that's not a standoff class" in {
            val invalidOnto = List(RdfDataObject(
                path = "_test_data/responders.v2.OntologyResponderV2Spec/standoff-class-with-invalid-base-class-onto.ttl", name = "http://www.knora.org/ontology/invalid"
            ))

            loadTestData(invalidOnto)
            expectMsgType[akka.actor.Status.Failure](timeout).cause.isInstanceOf[InconsistentTriplestoreDataException] should ===(true)
        }

        "not load an ontology containing a property with a subject class constraint of foaf:Person" in {
            val invalidOnto = List(RdfDataObject(
                path = "_test_data/responders.v2.OntologyResponderV2Spec/prop-with-non-knora-scc-onto.ttl", name = "http://www.knora.org/ontology/invalid"
            ))

            loadTestData(invalidOnto)
            expectMsgType[akka.actor.Status.Failure](timeout).cause.isInstanceOf[InconsistentTriplestoreDataException] should ===(true)
        }

        "not load an ontology containing a Knora value property with a subject class constraint of knora-base:TextValue" in {
            val invalidOnto = List(RdfDataObject(
                path = "_test_data/responders.v2.OntologyResponderV2Spec/prop-with-value-scc-onto.ttl", name = "http://www.knora.org/ontology/invalid"
            ))

            loadTestData(invalidOnto)
            expectMsgType[akka.actor.Status.Failure](timeout).cause.isInstanceOf[InconsistentTriplestoreDataException] should ===(true)
        }

        "not load an ontology containing a property with a subject class constraint of salsah-gui:Guielement" in {
            val invalidOnto = List(RdfDataObject(
                path = "_test_data/responders.v2.OntologyResponderV2Spec/prop-with-guielement-scc-onto.ttl", name = "http://www.knora.org/ontology/invalid"
            ))

            loadTestData(invalidOnto)
            expectMsgType[akka.actor.Status.Failure](timeout).cause.isInstanceOf[InconsistentTriplestoreDataException] should ===(true)
        }

        "not load an ontology containing a property with an object class constraint of foaf:Person" in {
            val invalidOnto = List(RdfDataObject(
                path = "_test_data/responders.v2.OntologyResponderV2Spec/prop-with-non-knora-occ-onto.ttl", name = "http://www.knora.org/ontology/invalid"
            ))

            loadTestData(invalidOnto)
            expectMsgType[akka.actor.Status.Failure](timeout).cause.isInstanceOf[InconsistentTriplestoreDataException] should ===(true)
        }

        "not load an ontology containing a property whose object class constraint is incompatible with its base property" in {
            val invalidOnto = List(RdfDataObject(
                path = "_test_data/responders.v2.OntologyResponderV2Spec/prop-with-incompatible-occ-onto.ttl", name = "http://www.knora.org/ontology/invalid"
            ))

            loadTestData(invalidOnto)
            expectMsgType[akka.actor.Status.Failure](timeout).cause.isInstanceOf[InconsistentTriplestoreDataException] should ===(true)
        }

        "not load an ontology containing a class with cardinalities for a link property and a matching link value property, except that the link property isn't really a link property" in {
            val invalidOnto = List(RdfDataObject(
                path = "_test_data/responders.v2.OntologyResponderV2Spec/class-with-misdefined-link-property-onto.ttl", name = "http://www.knora.org/ontology/invalid"
            ))

            loadTestData(invalidOnto)
            expectMsgType[akka.actor.Status.Failure](timeout).cause.isInstanceOf[InconsistentTriplestoreDataException] should ===(true)
        }

        "not load an ontology containing a class with cardinalities for a link property and a matching link value property, except that the link value property isn't really a link value property" in {
            val invalidOnto = List(RdfDataObject(
                path = "_test_data/responders.v2.OntologyResponderV2Spec/class-with-misdefined-link-value-property-onto.ttl", name = "http://www.knora.org/ontology/invalid"
            ))

            loadTestData(invalidOnto)
            expectMsgType[akka.actor.Status.Failure](timeout).cause.isInstanceOf[InconsistentTriplestoreDataException] should ===(true)
        }

        "not load an ontology containing a resource property with no object class constraint" ignore { // Consistency checks don't allow this in GraphDB.
            val invalidOnto = List(RdfDataObject(
                path = "_test_data/responders.v2.OntologyResponderV2Spec/resource-prop-without-occ-onto.ttl", name = "http://www.knora.org/ontology/invalid"
            ))

            loadTestData(invalidOnto)
            expectMsgType[akka.actor.Status.Failure](timeout).cause.isInstanceOf[InconsistentTriplestoreDataException] should ===(true)
        }

        "not load an ontology containing a resource property with no rdfs:label" in {
            val invalidOnto = List(RdfDataObject(
                path = "_test_data/responders.v2.OntologyResponderV2Spec/resource-prop-without-label-onto.ttl", name = "http://www.knora.org/ontology/invalid"
            ))

            loadTestData(invalidOnto)
            expectMsgType[akka.actor.Status.Failure](timeout).cause.isInstanceOf[InconsistentTriplestoreDataException] should ===(true)
        }

        "not load an ontology containing a property that's a subproperty of both knora-base:hasValue and knora-base:hasLinkTo" in {
            val invalidOnto = List(RdfDataObject(
                path = "_test_data/responders.v2.OntologyResponderV2Spec/prop-both-value-and-link-onto.ttl", name = "http://www.knora.org/ontology/invalid"
            ))

            loadTestData(invalidOnto)
            expectMsgType[akka.actor.Status.Failure](timeout).cause.isInstanceOf[InconsistentTriplestoreDataException] should ===(true)
        }

        "not load an ontology containing a property that's a subproperty of knora-base:hasFileValue" in {
            val invalidOnto = List(RdfDataObject(
                path = "_test_data/responders.v2.OntologyResponderV2Spec/filevalue-prop-onto.ttl", name = "http://www.knora.org/ontology/invalid"
            ))

            loadTestData(invalidOnto)
            expectMsgType[akka.actor.Status.Failure](timeout).cause.isInstanceOf[InconsistentTriplestoreDataException] should ===(true)
        }

        "not load an ontology containing a resource property with a base property that has a Knora IRI but isn't a resource property" in {
            val invalidOnto = List(RdfDataObject(
                path = "_test_data/responders.v2.OntologyResponderV2Spec/resource-prop-wrong-base-onto.ttl", name = "http://www.knora.org/ontology/invalid"
            ))

            loadTestData(invalidOnto)
            expectMsgType[akka.actor.Status.Failure](timeout).cause.isInstanceOf[InconsistentTriplestoreDataException] should ===(true)
        }

        "not load an ontology containing a property that contains salsah-gui:guiOrder" ignore { // Consistency checks don't allow this in GraphDB.
            val invalidOnto = List(RdfDataObject(
                path = "_test_data/responders.v2.OntologyResponderV2Spec/prop-with-guiorder-onto.ttl", name = "http://www.knora.org/ontology/invalid"
            ))

            loadTestData(invalidOnto)
            expectMsgType[akka.actor.Status.Failure](timeout).cause.isInstanceOf[InconsistentTriplestoreDataException] should ===(true)
        }

        "not load an ontology containing a cardinality that contains salsah-gui:guiElement" in {
            val invalidOnto = List(RdfDataObject(
                path = "_test_data/responders.v2.OntologyResponderV2Spec/cardinality-with-guielement-onto.ttl", name = "http://www.knora.org/ontology/invalid"
            ))

            loadTestData(invalidOnto)
            expectMsgType[akka.actor.Status.Failure](timeout).cause.isInstanceOf[InconsistentTriplestoreDataException] should ===(true)
        }

        "not load an ontology containing a cardinality that contains salsah-gui:guiAttribute" in {
            val invalidOnto = List(RdfDataObject(
                path = "_test_data/responders.v2.OntologyResponderV2Spec/cardinality-with-guiattribute-onto.ttl", name = "http://www.knora.org/ontology/invalid"
            ))

            loadTestData(invalidOnto)
            expectMsgType[akka.actor.Status.Failure](timeout).cause.isInstanceOf[InconsistentTriplestoreDataException] should ===(true)
        }

        "not load a project-specific ontology containing an owl:TransitiveProperty" in {
            val invalidOnto = List(RdfDataObject(
                path = "_test_data/responders.v2.OntologyResponderV2Spec/transitive-prop.ttl", name = "http://www.knora.org/ontology/invalid"
            ))

            loadTestData(invalidOnto)
            expectMsgType[akka.actor.Status.Failure](timeout).cause.isInstanceOf[InconsistentTriplestoreDataException] should ===(true)
        }

        "not load a project-specific ontology containing mismatched cardinalities for a link property and a link value property" in {
            val invalidOnto = List(RdfDataObject(
                path = "_test_data/responders.v2.OntologyResponderV2Spec/class-with-mismatched-link-cardinalities-onto.ttl", name = "http://www.knora.org/ontology/invalid"
            ))

            loadTestData(invalidOnto)
            expectMsgType[akka.actor.Status.Failure](timeout).cause.isInstanceOf[InconsistentTriplestoreDataException] should ===(true)
        }
    }
}
