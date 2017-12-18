package org.knora.webapi.responders.v2

import java.time.Instant
import java.util.UUID

import akka.actor.Props
import akka.testkit.{ImplicitSender, TestActorRef}
import org.knora.webapi._
import org.knora.webapi.messages.store.triplestoremessages.{ResetTriplestoreContent, ResetTriplestoreContentACK}
import org.knora.webapi.messages.v1.responder.ontologymessages.{LoadOntologiesRequest, LoadOntologiesResponse}
import org.knora.webapi.messages.v2.responder.ontologymessages._
import org.knora.webapi.responders.{RESPONDER_MANAGER_ACTOR_NAME, ResponderManager}
import org.knora.webapi.store.{STORE_MANAGER_ACTOR_NAME, StoreManager}
import org.knora.webapi.util.IriConversions._
import org.knora.webapi.util.{MutableTestIri, StringFormatter}

import scala.concurrent.duration._

class OntologyResponderV2Spec extends CoreSpec() with ImplicitSender {

    private implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    private val userProfile = SharedAdminTestData.imagesUser01
    private val projectWithProjectID = SharedAdminTestData.IMAGES_PROJECT_IRI.toSmartIri

    private val actorUnderTest = TestActorRef[OntologyResponderV2]
    private val responderManager = system.actorOf(Props(new ResponderManager with LiveActorMaker), name = RESPONDER_MANAGER_ACTOR_NAME)
    private val storeManager = system.actorOf(Props(new StoreManager with LiveActorMaker), name = STORE_MANAGER_ACTOR_NAME)

    private val rdfDataObjects = List()

    // The default timeout for receiving reply messages from actors.
    private val timeout = 10.seconds

    private val fooIri = new MutableTestIri
    private var fooLastModDate: Instant = Instant.now

    "Load test data" in {
        storeManager ! ResetTriplestoreContent(rdfDataObjects)
        expectMsg(300.seconds, ResetTriplestoreContentACK())

        responderManager ! LoadOntologiesRequest(userProfile)
        expectMsg(10.seconds, LoadOntologiesResponse())
    }

    "The ontology responder v2" should {
        "create an empty ontology called 'foo' with a project code" in {
            actorUnderTest ! CreateOntologyRequestV2(
                ontologyName = "foo",
                projectIri = projectWithProjectID,
                label = "The foo ontology",
                apiRequestID = UUID.randomUUID,
                userProfile = userProfile
            )

            val response = expectMsgType[ReadOntologyMetadataV2](timeout)
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
                userProfile = userProfile
            )

            val response = expectMsgType[ReadOntologyMetadataV2](timeout)
            val metadata = response.ontologies.head
            assert(metadata.ontologyIri.toString == "http://www.knora.org/ontology/00FF/foo")
            assert(metadata.label == newLabel)
            val newFooLastModDate = metadata.lastModificationDate.getOrElse(throw AssertionException(s"${metadata.ontologyIri} has no last modification date"))
            assert(newFooLastModDate.isAfter(fooLastModDate))
            fooLastModDate = newFooLastModDate
        }

        "not create 'foo' again" in {
            actorUnderTest ! CreateOntologyRequestV2(
                ontologyName = "foo",
                projectIri = projectWithProjectID,
                label = "The foo ontology",
                apiRequestID = UUID.randomUUID,
                userProfile = userProfile
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure =>
                    msg.cause.isInstanceOf[BadRequestException] should ===(true)
            }
        }

        "not create an ontology called '0000'" ignore {
            // TODO: re-enable when #667 is resolved.

            actorUnderTest ! CreateOntologyRequestV2(
                ontologyName = "0000",
                projectIri = projectWithProjectID,
                label = "The 0000 ontology",
                apiRequestID = UUID.randomUUID,
                userProfile = userProfile
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[BadRequestException] should ===(true)
            }

        }

        "not create an ontology called '-foo'" ignore {
            // TODO: re-enable when #667 is resolved.

            actorUnderTest ! CreateOntologyRequestV2(
                ontologyName = "-foo",
                projectIri = projectWithProjectID,
                label = "The -foo ontology",
                apiRequestID = UUID.randomUUID,
                userProfile = userProfile
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[BadRequestException] should ===(true)
            }

        }

        "not create an ontology called 'v3'" in {
            actorUnderTest ! CreateOntologyRequestV2(
                ontologyName = "v3",
                projectIri = projectWithProjectID,
                label = "The v3 ontology",
                apiRequestID = UUID.randomUUID,
                userProfile = userProfile
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[BadRequestException] should ===(true)
            }

        }

        "not create an ontology called 'ontology'" in {
            actorUnderTest ! CreateOntologyRequestV2(
                ontologyName = "ontology",
                projectIri = projectWithProjectID,
                label = "The ontology ontology",
                apiRequestID = UUID.randomUUID,
                userProfile = userProfile
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[BadRequestException] should ===(true)
            }

        }

        "not create an ontology called 'knora'" in {
            actorUnderTest ! CreateOntologyRequestV2(
                ontologyName = "knora",
                projectIri = projectWithProjectID,
                label = "The wrong knora ontology",
                apiRequestID = UUID.randomUUID,
                userProfile = userProfile
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[BadRequestException] should ===(true)
            }

        }

        "not create an ontology called 'simple'" in {
            actorUnderTest ! CreateOntologyRequestV2(
                ontologyName = "simple",
                projectIri = projectWithProjectID,
                label = "The simple ontology",
                apiRequestID = UUID.randomUUID,
                userProfile = userProfile
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[BadRequestException] should ===(true)
            }

        }

        "create an ordinary property in the 'foo' ontology" in {

            val propertyIri = (fooIri.get + "#hasName").toSmartIri

            val propertyInfoContent = PropertyInfoContentV2(
                propertyIri = propertyIri,
                ontologyIri = fooIri.get.toSmartIri,
                predicates = Map(
                    OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
                        ontologyIri = OntologyConstants.Rdf.RdfOntologyIri.toSmartIri,
                        objects = Set(OntologyConstants.Owl.ObjectProperty)
                    ),
                    OntologyConstants.KnoraApiV2WithValueObjects.SubjectType.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.KnoraApiV2WithValueObjects.SubjectType.toSmartIri,
                        ontologyIri = OntologyConstants.KnoraApiV2WithValueObjects.KnoraApiOntologyIri.toSmartIri,
                        objects = Set(fooIri.get + "#FooThing")
                    ),
                    OntologyConstants.KnoraApiV2WithValueObjects.ObjectType.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.KnoraApiV2WithValueObjects.ObjectType.toSmartIri,
                        ontologyIri = OntologyConstants.KnoraApiV2WithValueObjects.KnoraApiOntologyIri.toSmartIri,
                        objects = Set(OntologyConstants.KnoraApiV2WithValueObjects.TextValue)
                    ),
                    OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
                        ontologyIri = OntologyConstants.Rdfs.RdfsOntologyIri.toSmartIri,
                        objectsWithLang = Map(
                            "en" -> "has name",
                            "de" -> "hat Namen"
                        )
                    ),
                    OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
                        ontologyIri = OntologyConstants.Rdfs.RdfsOntologyIri.toSmartIri,
                        objectsWithLang = Map(
                            "en" -> "The name of a FooThing",
                            "de" -> "Der Name eines FooThings"
                        )
                    )
                ),
                subPropertyOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.HasValue.toSmartIri),
                ontologySchema = ApiV2WithValueObjects
            )

            actorUnderTest ! CreatePropertyRequestV2(
                propertyInfoContent = propertyInfoContent,
                lastModificationDate = fooLastModDate,
                apiRequestID = UUID.randomUUID,
                userProfile = userProfile
            )

            expectMsgPF(timeout) {
                case msg: ReadOntologiesV2 =>
                    val externalMsg = msg.toOntologySchema(ApiV2WithValueObjects)
                    val ontology = externalMsg.ontologies.head
                    ontology.properties(propertyIri).entityInfoContent should ===(propertyInfoContent)
                    val metadata = ontology.ontologyMetadata
                    val newFooLastModDate = metadata.lastModificationDate.getOrElse(throw AssertionException(s"${metadata.ontologyIri} has no last modification date"))
                    assert(newFooLastModDate.isAfter(fooLastModDate))
                    fooLastModDate = newFooLastModDate
            }
        }

        "not create a property without an rdf:type" in {

            val propertyIri = (fooIri.get + "#wrongProperty").toSmartIri

            val propertyInfoContent = PropertyInfoContentV2(
                propertyIri = propertyIri,
                ontologyIri = fooIri.get.toSmartIri,
                predicates = Map(
                    OntologyConstants.KnoraApiV2WithValueObjects.SubjectType.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.KnoraApiV2WithValueObjects.SubjectType.toSmartIri,
                        ontologyIri = OntologyConstants.KnoraApiV2WithValueObjects.KnoraApiOntologyIri.toSmartIri,
                        objects = Set(fooIri.get + "#FooThing")
                    ),
                    OntologyConstants.KnoraApiV2WithValueObjects.ObjectType.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.KnoraApiV2WithValueObjects.ObjectType.toSmartIri,
                        ontologyIri = OntologyConstants.KnoraApiV2WithValueObjects.KnoraApiOntologyIri.toSmartIri,
                        objects = Set(OntologyConstants.KnoraApiV2WithValueObjects.TextValue)
                    ),
                    OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
                        ontologyIri = OntologyConstants.Rdfs.RdfsOntologyIri.toSmartIri,
                        objectsWithLang = Map(
                            "en" -> "wrong property"
                        )
                    ),
                    OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
                        ontologyIri = OntologyConstants.Rdfs.RdfsOntologyIri.toSmartIri,
                        objectsWithLang = Map(
                            "en" -> "An invalid property definition"
                        )
                    )
                ),
                subPropertyOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.HasValue.toSmartIri),
                ontologySchema = ApiV2WithValueObjects
            )

            actorUnderTest ! CreatePropertyRequestV2(
                propertyInfoContent = propertyInfoContent,
                lastModificationDate = fooLastModDate,
                apiRequestID = UUID.randomUUID,
                userProfile = userProfile
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure =>
                    msg.cause.isInstanceOf[BadRequestException] should ===(true)
            }
        }

        "not create a property without a knora-base:subjectType" in {

            val propertyIri = (fooIri.get + "#wrongProperty").toSmartIri

            val propertyInfoContent = PropertyInfoContentV2(
                propertyIri = propertyIri,
                ontologyIri = fooIri.get.toSmartIri,
                predicates = Map(
                    OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
                        ontologyIri = OntologyConstants.Rdf.RdfOntologyIri.toSmartIri,
                        objects = Set(OntologyConstants.Owl.ObjectProperty)
                    ),
                    OntologyConstants.KnoraApiV2WithValueObjects.ObjectType.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.KnoraApiV2WithValueObjects.ObjectType.toSmartIri,
                        ontologyIri = OntologyConstants.KnoraApiV2WithValueObjects.KnoraApiOntologyIri.toSmartIri,
                        objects = Set(OntologyConstants.KnoraApiV2WithValueObjects.TextValue)
                    ),
                    OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
                        ontologyIri = OntologyConstants.Rdfs.RdfsOntologyIri.toSmartIri,
                        objectsWithLang = Map(
                            "en" -> "wrong property"
                        )
                    ),
                    OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
                        ontologyIri = OntologyConstants.Rdfs.RdfsOntologyIri.toSmartIri,
                        objectsWithLang = Map(
                            "en" -> "An invalid property definition"
                        )
                    )
                ),
                subPropertyOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.HasValue.toSmartIri),
                ontologySchema = ApiV2WithValueObjects
            )

            actorUnderTest ! CreatePropertyRequestV2(
                propertyInfoContent = propertyInfoContent,
                lastModificationDate = fooLastModDate,
                apiRequestID = UUID.randomUUID,
                userProfile = userProfile
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure =>
                    msg.cause.isInstanceOf[BadRequestException] should ===(true)
            }
        }

        "not create a property with a knora-base:subjectType whose IRI isn't a valid Knora internal entity IRI" in {

            val propertyIri = (fooIri.get + "#wrongProperty").toSmartIri

            val propertyInfoContent = PropertyInfoContentV2(
                propertyIri = propertyIri,
                ontologyIri = fooIri.get.toSmartIri,
                predicates = Map(
                    OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
                        ontologyIri = OntologyConstants.Rdf.RdfOntologyIri.toSmartIri,
                        objects = Set(OntologyConstants.Owl.ObjectProperty)
                    ),
                    OntologyConstants.KnoraApiV2WithValueObjects.SubjectType.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.KnoraApiV2WithValueObjects.SubjectType.toSmartIri,
                        ontologyIri = OntologyConstants.KnoraApiV2WithValueObjects.KnoraApiOntologyIri.toSmartIri,
                        objects = Set(OntologyConstants.Xsd.String)
                    ),
                    OntologyConstants.KnoraApiV2WithValueObjects.ObjectType.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.KnoraApiV2WithValueObjects.ObjectType.toSmartIri,
                        ontologyIri = OntologyConstants.KnoraApiV2WithValueObjects.KnoraApiOntologyIri.toSmartIri,
                        objects = Set(OntologyConstants.KnoraApiV2WithValueObjects.TextValue)
                    ),
                    OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
                        ontologyIri = OntologyConstants.Rdfs.RdfsOntologyIri.toSmartIri,
                        objectsWithLang = Map(
                            "en" -> "wrong property"
                        )
                    ),
                    OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
                        ontologyIri = OntologyConstants.Rdfs.RdfsOntologyIri.toSmartIri,
                        objectsWithLang = Map(
                            "en" -> "An invalid property definition"
                        )
                    )
                ),
                subPropertyOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.HasValue.toSmartIri),
                ontologySchema = ApiV2WithValueObjects
            )

            actorUnderTest ! CreatePropertyRequestV2(
                propertyInfoContent = propertyInfoContent,
                lastModificationDate = fooLastModDate,
                apiRequestID = UUID.randomUUID,
                userProfile = userProfile
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure =>
                    msg.cause.isInstanceOf[BadRequestException] should ===(true)
            }
        }

        "not create a property with a knora-base:objectType that refers to a nonexistent class" in {

            val propertyIri = (fooIri.get + "#wrongProperty").toSmartIri

            val propertyInfoContent = PropertyInfoContentV2(
                propertyIri = propertyIri,
                ontologyIri = fooIri.get.toSmartIri,
                predicates = Map(
                    OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
                        ontologyIri = OntologyConstants.Rdf.RdfOntologyIri.toSmartIri,
                        objects = Set(OntologyConstants.Owl.ObjectProperty)
                    ),
                    OntologyConstants.KnoraApiV2WithValueObjects.SubjectType.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.KnoraApiV2WithValueObjects.SubjectType.toSmartIri,
                        ontologyIri = OntologyConstants.KnoraApiV2WithValueObjects.KnoraApiOntologyIri.toSmartIri,
                        objects = Set(fooIri.get + "#FooThing")
                    ),
                    OntologyConstants.KnoraApiV2WithValueObjects.ObjectType.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.KnoraApiV2WithValueObjects.ObjectType.toSmartIri,
                        ontologyIri = OntologyConstants.KnoraApiV2WithValueObjects.KnoraApiOntologyIri.toSmartIri,
                        objects = Set(OntologyConstants.KnoraApiV2WithValueObjects.KnoraApiV2PrefixExpansion + "NonexistentClass")
                    ),
                    OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
                        ontologyIri = OntologyConstants.Rdfs.RdfsOntologyIri.toSmartIri,
                        objectsWithLang = Map(
                            "en" -> "wrong property"
                        )
                    ),
                    OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
                        ontologyIri = OntologyConstants.Rdfs.RdfsOntologyIri.toSmartIri,
                        objectsWithLang = Map(
                            "en" -> "An invalid property definition"
                        )
                    )
                ),
                subPropertyOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.HasValue.toSmartIri),
                ontologySchema = ApiV2WithValueObjects
            )

            actorUnderTest ! CreatePropertyRequestV2(
                propertyInfoContent = propertyInfoContent,
                lastModificationDate = fooLastModDate,
                apiRequestID = UUID.randomUUID,
                userProfile = userProfile
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure =>
                    msg.cause.isInstanceOf[BadRequestException] should ===(true)
            }
        }

        "not create a file value property" in {

            val propertyIri = (fooIri.get + "#wrongProperty").toSmartIri

            val propertyInfoContent = PropertyInfoContentV2(
                propertyIri = propertyIri,
                ontologyIri = fooIri.get.toSmartIri,
                predicates = Map(
                    OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
                        ontologyIri = OntologyConstants.Rdf.RdfOntologyIri.toSmartIri,
                        objects = Set(OntologyConstants.Owl.ObjectProperty)
                    ),
                    OntologyConstants.KnoraApiV2WithValueObjects.SubjectType.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.KnoraApiV2WithValueObjects.SubjectType.toSmartIri,
                        ontologyIri = OntologyConstants.KnoraApiV2WithValueObjects.KnoraApiOntologyIri.toSmartIri,
                        objects = Set(fooIri.get + "#FooThing")
                    ),
                    OntologyConstants.KnoraApiV2WithValueObjects.ObjectType.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.KnoraApiV2WithValueObjects.ObjectType.toSmartIri,
                        ontologyIri = OntologyConstants.KnoraApiV2WithValueObjects.KnoraApiOntologyIri.toSmartIri,
                        objects = Set(OntologyConstants.KnoraApiV2WithValueObjects.FileValue)
                    ),
                    OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
                        ontologyIri = OntologyConstants.Rdfs.RdfsOntologyIri.toSmartIri,
                        objectsWithLang = Map(
                            "en" -> "wrong property"
                        )
                    ),
                    OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
                        ontologyIri = OntologyConstants.Rdfs.RdfsOntologyIri.toSmartIri,
                        objectsWithLang = Map(
                            "en" -> "An invalid property definition"
                        )
                    )
                ),
                subPropertyOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.HasFileValue.toSmartIri),
                ontologySchema = ApiV2WithValueObjects
            )

            actorUnderTest ! CreatePropertyRequestV2(
                propertyInfoContent = propertyInfoContent,
                lastModificationDate = fooLastModDate,
                apiRequestID = UUID.randomUUID,
                userProfile = userProfile
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure =>
                    msg.cause.isInstanceOf[BadRequestException] should ===(true)
            }
        }

        "not directly create a link value property directly" in {

            val propertyIri = (fooIri.get + "#wrongProperty").toSmartIri

            val propertyInfoContent = PropertyInfoContentV2(
                propertyIri = propertyIri,
                ontologyIri = fooIri.get.toSmartIri,
                predicates = Map(
                    OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
                        ontologyIri = OntologyConstants.Rdf.RdfOntologyIri.toSmartIri,
                        objects = Set(OntologyConstants.Owl.ObjectProperty)
                    ),
                    OntologyConstants.KnoraApiV2WithValueObjects.SubjectType.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.KnoraApiV2WithValueObjects.SubjectType.toSmartIri,
                        ontologyIri = OntologyConstants.KnoraApiV2WithValueObjects.KnoraApiOntologyIri.toSmartIri,
                        objects = Set(fooIri.get + "#FooThing")
                    ),
                    OntologyConstants.KnoraApiV2WithValueObjects.ObjectType.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.KnoraApiV2WithValueObjects.ObjectType.toSmartIri,
                        ontologyIri = OntologyConstants.KnoraApiV2WithValueObjects.KnoraApiOntologyIri.toSmartIri,
                        objects = Set(OntologyConstants.KnoraApiV2WithValueObjects.LinkValue)
                    ),
                    OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
                        ontologyIri = OntologyConstants.Rdfs.RdfsOntologyIri.toSmartIri,
                        objectsWithLang = Map(
                            "en" -> "wrong property"
                        )
                    ),
                    OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
                        ontologyIri = OntologyConstants.Rdfs.RdfsOntologyIri.toSmartIri,
                        objectsWithLang = Map(
                            "en" -> "An invalid property definition"
                        )
                    )
                ),
                subPropertyOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.HasLinkToValue.toSmartIri),
                ontologySchema = ApiV2WithValueObjects
            )

            actorUnderTest ! CreatePropertyRequestV2(
                propertyInfoContent = propertyInfoContent,
                lastModificationDate = fooLastModDate,
                apiRequestID = UUID.randomUUID,
                userProfile = userProfile
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure =>
                    msg.cause.isInstanceOf[BadRequestException] should ===(true)
            }
        }

        "not directly create a property with a knora-api:objectType of knora-api:LinkValue" in {

            val propertyIri = (fooIri.get + "#wrongProperty").toSmartIri

            val propertyInfoContent = PropertyInfoContentV2(
                propertyIri = propertyIri,
                ontologyIri = fooIri.get.toSmartIri,
                predicates = Map(
                    OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
                        ontologyIri = OntologyConstants.Rdf.RdfOntologyIri.toSmartIri,
                        objects = Set(OntologyConstants.Owl.ObjectProperty)
                    ),
                    OntologyConstants.KnoraApiV2WithValueObjects.SubjectType.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.KnoraApiV2WithValueObjects.SubjectType.toSmartIri,
                        ontologyIri = OntologyConstants.KnoraApiV2WithValueObjects.KnoraApiOntologyIri.toSmartIri,
                        objects = Set(fooIri.get + "#FooThing")
                    ),
                    OntologyConstants.KnoraApiV2WithValueObjects.ObjectType.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.KnoraApiV2WithValueObjects.ObjectType.toSmartIri,
                        ontologyIri = OntologyConstants.KnoraApiV2WithValueObjects.KnoraApiOntologyIri.toSmartIri,
                        objects = Set(OntologyConstants.KnoraApiV2WithValueObjects.LinkValue)
                    ),
                    OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
                        ontologyIri = OntologyConstants.Rdfs.RdfsOntologyIri.toSmartIri,
                        objectsWithLang = Map(
                            "en" -> "wrong property"
                        )
                    ),
                    OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
                        ontologyIri = OntologyConstants.Rdfs.RdfsOntologyIri.toSmartIri,
                        objectsWithLang = Map(
                            "en" -> "An invalid property definition"
                        )
                    )
                ),
                subPropertyOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.HasValue.toSmartIri),
                ontologySchema = ApiV2WithValueObjects
            )

            actorUnderTest ! CreatePropertyRequestV2(
                propertyInfoContent = propertyInfoContent,
                lastModificationDate = fooLastModDate,
                apiRequestID = UUID.randomUUID,
                userProfile = userProfile
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure =>
                    msg.cause.isInstanceOf[BadRequestException] should ===(true)
            }
        }


        "not create a property whose object type is knora-api:StillImageFileValue" in {

            val propertyIri = (fooIri.get + "#wrongProperty").toSmartIri

            val propertyInfoContent = PropertyInfoContentV2(
                propertyIri = propertyIri,
                ontologyIri = fooIri.get.toSmartIri,
                predicates = Map(
                    OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
                        ontologyIri = OntologyConstants.Rdf.RdfOntologyIri.toSmartIri,
                        objects = Set(OntologyConstants.Owl.ObjectProperty)
                    ),
                    OntologyConstants.KnoraApiV2WithValueObjects.SubjectType.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.KnoraApiV2WithValueObjects.SubjectType.toSmartIri,
                        ontologyIri = OntologyConstants.KnoraApiV2WithValueObjects.KnoraApiOntologyIri.toSmartIri,
                        objects = Set(fooIri.get + "#FooThing")
                    ),
                    OntologyConstants.KnoraApiV2WithValueObjects.ObjectType.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.KnoraApiV2WithValueObjects.ObjectType.toSmartIri,
                        ontologyIri = OntologyConstants.KnoraApiV2WithValueObjects.KnoraApiOntologyIri.toSmartIri,
                        objects = Set(OntologyConstants.KnoraApiV2WithValueObjects.StillImageFileValue)
                    ),
                    OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
                        ontologyIri = OntologyConstants.Rdfs.RdfsOntologyIri.toSmartIri,
                        objectsWithLang = Map(
                            "en" -> "wrong property"
                        )
                    ),
                    OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
                        ontologyIri = OntologyConstants.Rdfs.RdfsOntologyIri.toSmartIri,
                        objectsWithLang = Map(
                            "en" -> "An invalid property definition"
                        )
                    )
                ),
                subPropertyOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.HasValue.toSmartIri),
                ontologySchema = ApiV2WithValueObjects
            )

            actorUnderTest ! CreatePropertyRequestV2(
                propertyInfoContent = propertyInfoContent,
                lastModificationDate = fooLastModDate,
                apiRequestID = UUID.randomUUID,
                userProfile = userProfile
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure =>
                    msg.cause.isInstanceOf[BadRequestException] should ===(true)
            }
        }

        "not create a property whose object type is a Knora resource class if the property isn't a subproperty of knora-api:hasLinkValue" in {

            val propertyIri = (fooIri.get + "#wrongProperty").toSmartIri

            val propertyInfoContent = PropertyInfoContentV2(
                propertyIri = propertyIri,
                ontologyIri = fooIri.get.toSmartIri,
                predicates = Map(
                    OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
                        ontologyIri = OntologyConstants.Rdf.RdfOntologyIri.toSmartIri,
                        objects = Set(OntologyConstants.Owl.ObjectProperty)
                    ),
                    OntologyConstants.KnoraApiV2WithValueObjects.SubjectType.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.KnoraApiV2WithValueObjects.SubjectType.toSmartIri,
                        ontologyIri = OntologyConstants.KnoraApiV2WithValueObjects.KnoraApiOntologyIri.toSmartIri,
                        objects = Set(fooIri.get + "#FooThing")
                    ),
                    OntologyConstants.KnoraApiV2WithValueObjects.ObjectType.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.KnoraApiV2WithValueObjects.ObjectType.toSmartIri,
                        ontologyIri = OntologyConstants.KnoraApiV2WithValueObjects.KnoraApiOntologyIri.toSmartIri,
                        objects = Set(OntologyConstants.KnoraApiV2WithValueObjects.Resource)
                    ),
                    OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
                        ontologyIri = OntologyConstants.Rdfs.RdfsOntologyIri.toSmartIri,
                        objectsWithLang = Map(
                            "en" -> "wrong property"
                        )
                    ),
                    OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
                        ontologyIri = OntologyConstants.Rdfs.RdfsOntologyIri.toSmartIri,
                        objectsWithLang = Map(
                            "en" -> "An invalid property definition"
                        )
                    )
                ),
                subPropertyOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.HasValue.toSmartIri),
                ontologySchema = ApiV2WithValueObjects
            )

            actorUnderTest ! CreatePropertyRequestV2(
                propertyInfoContent = propertyInfoContent,
                lastModificationDate = fooLastModDate,
                apiRequestID = UUID.randomUUID,
                userProfile = userProfile
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure =>
                    msg.cause.isInstanceOf[BadRequestException] should ===(true)
            }
        }

        "not create a link property whose object type is knora-api:TextValue" in {

            val propertyIri = (fooIri.get + "#wrongProperty").toSmartIri

            val propertyInfoContent = PropertyInfoContentV2(
                propertyIri = propertyIri,
                ontologyIri = fooIri.get.toSmartIri,
                predicates = Map(
                    OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
                        ontologyIri = OntologyConstants.Rdf.RdfOntologyIri.toSmartIri,
                        objects = Set(OntologyConstants.Owl.ObjectProperty)
                    ),
                    OntologyConstants.KnoraApiV2WithValueObjects.SubjectType.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.KnoraApiV2WithValueObjects.SubjectType.toSmartIri,
                        ontologyIri = OntologyConstants.KnoraApiV2WithValueObjects.KnoraApiOntologyIri.toSmartIri,
                        objects = Set(fooIri.get + "#FooThing")
                    ),
                    OntologyConstants.KnoraApiV2WithValueObjects.ObjectType.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.KnoraApiV2WithValueObjects.ObjectType.toSmartIri,
                        ontologyIri = OntologyConstants.KnoraApiV2WithValueObjects.KnoraApiOntologyIri.toSmartIri,
                        objects = Set(OntologyConstants.KnoraApiV2WithValueObjects.TextValue)
                    ),
                    OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
                        ontologyIri = OntologyConstants.Rdfs.RdfsOntologyIri.toSmartIri,
                        objectsWithLang = Map(
                            "en" -> "wrong property"
                        )
                    ),
                    OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
                        ontologyIri = OntologyConstants.Rdfs.RdfsOntologyIri.toSmartIri,
                        objectsWithLang = Map(
                            "en" -> "An invalid property definition"
                        )
                    )
                ),
                subPropertyOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.HasLinkTo.toSmartIri),
                ontologySchema = ApiV2WithValueObjects
            )

            actorUnderTest ! CreatePropertyRequestV2(
                propertyInfoContent = propertyInfoContent,
                lastModificationDate = fooLastModDate,
                apiRequestID = UUID.randomUUID,
                userProfile = userProfile
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure =>
                    msg.cause.isInstanceOf[BadRequestException] should ===(true)
            }
        }
    }
}
