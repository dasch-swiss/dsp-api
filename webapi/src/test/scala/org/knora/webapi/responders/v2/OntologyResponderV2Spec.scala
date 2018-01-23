package org.knora.webapi.responders.v2

import java.time.Instant
import java.util.UUID

import akka.actor.Props
import akka.testkit.{ImplicitSender, TestActorRef}
import org.knora.webapi._
import org.knora.webapi.messages.store.triplestoremessages.{ResetTriplestoreContent, ResetTriplestoreContentACK}
import org.knora.webapi.messages.v1.responder.ontologymessages.{LoadOntologiesRequest, LoadOntologiesResponse}
import org.knora.webapi.messages.v1.responder.usermessages.UserProfileV1
import org.knora.webapi.messages.v2.responder.ontologymessages._
import org.knora.webapi.responders.{RESPONDER_MANAGER_ACTOR_NAME, ResponderManager}
import org.knora.webapi.store.{STORE_MANAGER_ACTOR_NAME, StoreManager}
import org.knora.webapi.util.IriConversions._
import org.knora.webapi.util.{MutableTestIri, SmartIri, StringFormatter}

import scala.concurrent.duration._

class OntologyResponderV2Spec extends CoreSpec() with ImplicitSender {

    private implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    private val imagesUserProfile = SharedTestDataV1.imagesUser01
    private val imagesProjectIri = SharedTestDataV1.IMAGES_PROJECT_IRI.toSmartIri

    private val anythingUserProfile = SharedTestDataV1.anythingAdminUser
    private val anythingProjectIri = SharedTestDataV1.ANYTHING_PROJECT_IRI.toSmartIri

    private val actorUnderTest = TestActorRef[OntologyResponderV2]
    private val responderManager = system.actorOf(Props(new ResponderManager with LiveActorMaker), name = RESPONDER_MANAGER_ACTOR_NAME)
    private val storeManager = system.actorOf(Props(new StoreManager with LiveActorMaker), name = STORE_MANAGER_ACTOR_NAME)

    private val rdfDataObjects = List()

    // The default timeout for receiving reply messages from actors.
    private val timeout = 10.seconds

    private val fooIri = new MutableTestIri
    private var fooLastModDate: Instant = Instant.now

    private val AnythingOntologyIri = "http://0.0.0.0:3333/ontology/anything/v2".toSmartIri
    private var anythingLastModDate: Instant = Instant.now

    private val printErrorMessages = false

    "Load test data" in {
        storeManager ! ResetTriplestoreContent(rdfDataObjects)
        expectMsg(300.seconds, ResetTriplestoreContentACK())

        responderManager ! LoadOntologiesRequest(anythingUserProfile)
        expectMsg(10.seconds, LoadOntologiesResponse())
    }

    "The ontology responder v2" should {
        "create an empty ontology called 'foo' with a project code" in {
            actorUnderTest ! CreateOntologyRequestV2(
                ontologyName = "foo",
                projectIri = imagesProjectIri,
                label = "The foo ontology",
                apiRequestID = UUID.randomUUID,
                userProfile = imagesUserProfile
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
                userProfile = imagesUserProfile
            )

            val response = expectMsgType[ReadOntologyMetadataV2](timeout)
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
                userProfile = imagesUserProfile
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
                projectIri = imagesProjectIri,
                label = "The 0000 ontology",
                apiRequestID = UUID.randomUUID,
                userProfile = imagesUserProfile
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[BadRequestException] should ===(true)
            }

        }

        "not create an ontology called '-foo'" ignore {
            // TODO: re-enable when #667 is resolved.

            actorUnderTest ! CreateOntologyRequestV2(
                ontologyName = "-foo",
                projectIri = imagesProjectIri,
                label = "The -foo ontology",
                apiRequestID = UUID.randomUUID,
                userProfile = imagesUserProfile
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
                userProfile = imagesUserProfile
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
                userProfile = imagesUserProfile
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
                userProfile = imagesUserProfile
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
                userProfile = imagesUserProfile
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[BadRequestException] should ===(true)
            }

        }

        "create an ordinary property in the 'anything' ontology as a subproperty of knora-api:hasValue and schema:name" in {

            actorUnderTest ! OntologyMetadataGetRequestV2(
                projectIris = Set(anythingProjectIri),
                userProfile = anythingUserProfile
            )

            val metadataResponse = expectMsgType[ReadOntologyMetadataV2](timeout)
            anythingLastModDate = metadataResponse.ontologies.head.lastModificationDate.get

            val propertyIri = AnythingOntologyIri.makeEntityIri("hasName")

            val propertyInfoContent = PropertyInfoContentV2(
                propertyIri = propertyIri,
                predicates = Map(
                    OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
                        objects = Set(OntologyConstants.Owl.ObjectProperty)
                    ),
                    OntologyConstants.KnoraApiV2WithValueObjects.SubjectType.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.KnoraApiV2WithValueObjects.SubjectType.toSmartIri,
                        objects = Set(AnythingOntologyIri.makeEntityIri("Thing").toString)
                    ),
                    OntologyConstants.KnoraApiV2WithValueObjects.ObjectType.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.KnoraApiV2WithValueObjects.ObjectType.toSmartIri,
                        objects = Set(OntologyConstants.KnoraApiV2WithValueObjects.TextValue)
                    ),
                    OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
                        objectsWithLang = Map(
                            "en" -> "has name",
                            "de" -> "hat Namen"
                        )
                    ),
                    OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
                        objectsWithLang = Map(
                            "en" -> "The name of a Thing",
                            "de" -> "Der Name eines Dinges"
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
                userProfile = anythingUserProfile
            )

            expectMsgPF(timeout) {
                case msg: ReadOntologiesV2 =>
                    val externalMsg = msg.toOntologySchema(ApiV2WithValueObjects)
                    val ontology = externalMsg.ontologies.head
                    val property = ontology.properties(propertyIri)
                    property.entityInfoContent should ===(propertyInfoContent)
                    val metadata = ontology.ontologyMetadata
                    val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(throw AssertionException(s"${metadata.ontologyIri} has no last modification date"))
                    assert(newAnythingLastModDate.isAfter(anythingLastModDate))
                    anythingLastModDate = newAnythingLastModDate
            }

            // Reload the ontology cache and see if we get the same result.

            responderManager ! LoadOntologiesRequest(anythingUserProfile)
            expectMsg(10.seconds, LoadOntologiesResponse())

            responderManager ! PropertiesGetRequestV2(
                propertyIris = Set(propertyIri),
                allLanguages = true,
                userProfile = anythingUserProfile
            )

            expectMsgPF(timeout) {
                case msg: ReadOntologiesV2 =>
                    val externalMsg = msg.toOntologySchema(ApiV2WithValueObjects)
                    val readPropertyInfo: ReadPropertyInfoV2 = externalMsg.ontologies.head.properties.values.head
                    readPropertyInfo.entityInfoContent should ===(propertyInfoContent)
            }
        }

        "create a link property in the 'anything' ontology, and automatically create the corresponding link value property" in {

            actorUnderTest ! OntologyMetadataGetRequestV2(
                projectIris = Set(anythingProjectIri),
                userProfile = anythingUserProfile
            )

            val metadataResponse = expectMsgType[ReadOntologyMetadataV2](timeout)
            anythingLastModDate = metadataResponse.ontologies.head.lastModificationDate.get

            val propertyIri = AnythingOntologyIri.makeEntityIri("hasInterestingThing")

            val propertyInfoContent = PropertyInfoContentV2(
                propertyIri = propertyIri,
                predicates = Map(
                    OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
                        objects = Set(OntologyConstants.Owl.ObjectProperty)
                    ),
                    OntologyConstants.KnoraApiV2WithValueObjects.SubjectType.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.KnoraApiV2WithValueObjects.SubjectType.toSmartIri,
                        objects = Set(AnythingOntologyIri.makeEntityIri("Thing").toString)
                    ),
                    OntologyConstants.KnoraApiV2WithValueObjects.ObjectType.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.KnoraApiV2WithValueObjects.ObjectType.toSmartIri,
                        objects = Set(AnythingOntologyIri.makeEntityIri("Thing").toString)
                    ),
                    OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
                        objectsWithLang = Map(
                            "en" -> "has interesting thing"
                        )
                    ),
                    OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
                        objectsWithLang = Map(
                            "en" -> "an interesting Thing"
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
                userProfile = anythingUserProfile
            )

            expectMsgPF(timeout) {
                case msg: ReadOntologiesV2 =>
                    val externalMsg = msg.toOntologySchema(ApiV2WithValueObjects)
                    val ontology = externalMsg.ontologies.head
                    val property = ontology.properties(propertyIri)
                    assert(property.isLinkProp)
                    assert(!property.isLinkValueProp)
                    ontology.properties(propertyIri).entityInfoContent should ===(propertyInfoContent)
                    val metadata = ontology.ontologyMetadata
                    val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(throw AssertionException(s"${metadata.ontologyIri} has no last modification date"))
                    assert(newAnythingLastModDate.isAfter(anythingLastModDate))
                    anythingLastModDate = newAnythingLastModDate
            }

            // Check that the link value property was created.

            val linkValuePropIri = propertyIri.fromLinkPropToLinkValueProp

            responderManager ! PropertiesGetRequestV2(
                propertyIris = Set(linkValuePropIri),
                allLanguages = true,
                userProfile = anythingUserProfile
            )

            expectMsgPF(timeout) {
                case msg: ReadOntologiesV2 =>
                    val externalMsg = msg.toOntologySchema(ApiV2WithValueObjects)
                    val readPropertyInfo: ReadPropertyInfoV2 = externalMsg.ontologies.head.properties.values.head
                    assert(readPropertyInfo.entityInfoContent.propertyIri == linkValuePropIri)
                    assert(!readPropertyInfo.isLinkProp)
                    assert(readPropertyInfo.isLinkValueProp)
            }

            // Reload the ontology cache and see if we get the same result.

            responderManager ! LoadOntologiesRequest(anythingUserProfile)
            expectMsg(10.seconds, LoadOntologiesResponse())

            responderManager ! PropertiesGetRequestV2(
                propertyIris = Set(propertyIri),
                allLanguages = true,
                userProfile = anythingUserProfile
            )

            expectMsgPF(timeout) {
                case msg: ReadOntologiesV2 =>
                    val externalMsg = msg.toOntologySchema(ApiV2WithValueObjects)
                    val readPropertyInfo: ReadPropertyInfoV2 = externalMsg.ontologies.head.properties.values.head
                    assert(readPropertyInfo.isLinkProp)
                    assert(!readPropertyInfo.isLinkValueProp)
                    readPropertyInfo.entityInfoContent should ===(propertyInfoContent)
            }

            responderManager ! PropertiesGetRequestV2(
                propertyIris = Set(linkValuePropIri),
                allLanguages = true,
                userProfile = anythingUserProfile
            )

            expectMsgPF(timeout) {
                case msg: ReadOntologiesV2 =>
                    val externalMsg = msg.toOntologySchema(ApiV2WithValueObjects)
                    val readPropertyInfo: ReadPropertyInfoV2 = externalMsg.ontologies.head.properties.values.head
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
                        objects = Set(AnythingOntologyIri.makeEntityIri("Thing").toString)
                    ),
                    OntologyConstants.KnoraApiV2WithValueObjects.ObjectType.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.KnoraApiV2WithValueObjects.ObjectType.toSmartIri,
                        objects = Set(OntologyConstants.KnoraApiV2WithValueObjects.TextValue)
                    ),
                    OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
                        objectsWithLang = Map(
                            "en" -> "wrong property"
                        )
                    ),
                    OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
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
                lastModificationDate = anythingLastModDate,
                apiRequestID = UUID.randomUUID,
                userProfile = anythingUserProfile
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
                        objects = Set(OntologyConstants.Owl.ObjectProperty)
                    ),
                    OntologyConstants.KnoraApiV2WithValueObjects.SubjectType.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.KnoraApiV2WithValueObjects.SubjectType.toSmartIri,
                        objects = Set(AnythingOntologyIri.makeEntityIri("NonexistentClass").toString)
                    ),
                    OntologyConstants.KnoraApiV2WithValueObjects.ObjectType.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.KnoraApiV2WithValueObjects.ObjectType.toSmartIri,
                        objects = Set(OntologyConstants.KnoraApiV2WithValueObjects.TextValue)
                    ),
                    OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
                        objectsWithLang = Map(
                            "en" -> "wrong property"
                        )
                    ),
                    OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
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
                lastModificationDate = anythingLastModDate,
                apiRequestID = UUID.randomUUID,
                userProfile = anythingUserProfile
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
                        objects = Set(OntologyConstants.Owl.ObjectProperty)
                    ),
                    OntologyConstants.KnoraApiV2WithValueObjects.SubjectType.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.KnoraApiV2WithValueObjects.SubjectType.toSmartIri,
                        objects = Set(AnythingOntologyIri.makeEntityIri("Thing").toString)
                    ),
                    OntologyConstants.KnoraApiV2WithValueObjects.ObjectType.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.KnoraApiV2WithValueObjects.ObjectType.toSmartIri,
                        objects = Set(OntologyConstants.KnoraApiV2WithValueObjects.KnoraApiV2PrefixExpansion + "NonexistentClass")
                    ),
                    OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
                        objectsWithLang = Map(
                            "en" -> "wrong property"
                        )
                    ),
                    OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
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
                lastModificationDate = anythingLastModDate,
                apiRequestID = UUID.randomUUID,
                userProfile = anythingUserProfile
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
                        objects = Set(OntologyConstants.Owl.ObjectProperty)
                    ),
                    OntologyConstants.KnoraApiV2WithValueObjects.SubjectType.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.KnoraApiV2WithValueObjects.SubjectType.toSmartIri,
                        objects = Set(AnythingOntologyIri.makeEntityIri("Thing").toString)
                    ),
                    OntologyConstants.KnoraApiV2WithValueObjects.ObjectType.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.KnoraApiV2WithValueObjects.ObjectType.toSmartIri,
                        objects = Set(OntologyConstants.KnoraApiV2WithValueObjects.FileValue)
                    ),
                    OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
                        objectsWithLang = Map(
                            "en" -> "wrong property"
                        )
                    ),
                    OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
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
                lastModificationDate = anythingLastModDate,
                apiRequestID = UUID.randomUUID,
                userProfile = anythingUserProfile
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
                        objects = Set(OntologyConstants.Owl.ObjectProperty)
                    ),
                    OntologyConstants.KnoraApiV2WithValueObjects.SubjectType.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.KnoraApiV2WithValueObjects.SubjectType.toSmartIri,
                        objects = Set(AnythingOntologyIri.makeEntityIri("Thing").toString)
                    ),
                    OntologyConstants.KnoraApiV2WithValueObjects.ObjectType.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.KnoraApiV2WithValueObjects.ObjectType.toSmartIri,
                        objects = Set(OntologyConstants.KnoraApiV2WithValueObjects.LinkValue)
                    ),
                    OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
                        objectsWithLang = Map(
                            "en" -> "wrong property"
                        )
                    ),
                    OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
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
                lastModificationDate = anythingLastModDate,
                apiRequestID = UUID.randomUUID,
                userProfile = anythingUserProfile
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
                        objects = Set(OntologyConstants.Owl.ObjectProperty)
                    ),
                    OntologyConstants.KnoraApiV2WithValueObjects.SubjectType.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.KnoraApiV2WithValueObjects.SubjectType.toSmartIri,
                        objects = Set(AnythingOntologyIri.makeEntityIri("Thing").toString)
                    ),
                    OntologyConstants.KnoraApiV2WithValueObjects.ObjectType.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.KnoraApiV2WithValueObjects.ObjectType.toSmartIri,
                        objects = Set(OntologyConstants.KnoraApiV2WithValueObjects.LinkValue)
                    ),
                    OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
                        objectsWithLang = Map(
                            "en" -> "wrong property"
                        )
                    ),
                    OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
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
                lastModificationDate = anythingLastModDate,
                apiRequestID = UUID.randomUUID,
                userProfile = anythingUserProfile
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
                        objects = Set(OntologyConstants.Owl.ObjectProperty)
                    ),
                    OntologyConstants.KnoraApiV2WithValueObjects.SubjectType.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.KnoraApiV2WithValueObjects.SubjectType.toSmartIri,
                        objects = Set(AnythingOntologyIri.makeEntityIri("Thing").toString)
                    ),
                    OntologyConstants.KnoraApiV2WithValueObjects.ObjectType.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.KnoraApiV2WithValueObjects.ObjectType.toSmartIri,
                        objects = Set(OntologyConstants.KnoraApiV2WithValueObjects.StillImageFileValue)
                    ),
                    OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
                        objectsWithLang = Map(
                            "en" -> "wrong property"
                        )
                    ),
                    OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
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
                lastModificationDate = anythingLastModDate,
                apiRequestID = UUID.randomUUID,
                userProfile = anythingUserProfile
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
                        objects = Set(OntologyConstants.Owl.ObjectProperty)
                    ),
                    OntologyConstants.KnoraApiV2WithValueObjects.SubjectType.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.KnoraApiV2WithValueObjects.SubjectType.toSmartIri,
                        objects = Set(AnythingOntologyIri.makeEntityIri("Thing").toString)
                    ),
                    OntologyConstants.KnoraApiV2WithValueObjects.ObjectType.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.KnoraApiV2WithValueObjects.ObjectType.toSmartIri,
                        objects = Set(AnythingOntologyIri.makeEntityIri("Thing").toString)
                    ),
                    OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
                        objectsWithLang = Map(
                            "en" -> "wrong property"
                        )
                    ),
                    OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
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
                lastModificationDate = anythingLastModDate,
                apiRequestID = UUID.randomUUID,
                userProfile = anythingUserProfile
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
                        objects = Set(OntologyConstants.Owl.ObjectProperty)
                    ),
                    OntologyConstants.KnoraApiV2WithValueObjects.SubjectType.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.KnoraApiV2WithValueObjects.SubjectType.toSmartIri,
                        objects = Set(AnythingOntologyIri.makeEntityIri("Thing").toString)
                    ),
                    OntologyConstants.KnoraApiV2WithValueObjects.ObjectType.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.KnoraApiV2WithValueObjects.ObjectType.toSmartIri,
                        objects = Set(OntologyConstants.KnoraApiV2WithValueObjects.TextValue)
                    ),
                    OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
                        objectsWithLang = Map(
                            "en" -> "wrong property"
                        )
                    ),
                    OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
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
                lastModificationDate = anythingLastModDate,
                apiRequestID = UUID.randomUUID,
                userProfile = anythingUserProfile
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
                        objects = Set(OntologyConstants.Owl.ObjectProperty)
                    ),
                    OntologyConstants.KnoraApiV2WithValueObjects.SubjectType.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.KnoraApiV2WithValueObjects.SubjectType.toSmartIri,
                        objects = Set(AnythingOntologyIri.makeEntityIri("Thing").toString)
                    ),
                    OntologyConstants.KnoraApiV2WithValueObjects.ObjectType.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.KnoraApiV2WithValueObjects.ObjectType.toSmartIri,
                        objects = Set(OntologyConstants.KnoraApiV2WithValueObjects.IntValue)
                    ),
                    OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
                        objectsWithLang = Map(
                            "en" -> "wrong property"
                        )
                    ),
                    OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
                        objectsWithLang = Map(
                            "en" -> "An invalid property definition"
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
                userProfile = anythingUserProfile
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
                        objects = Set(OntologyConstants.Owl.ObjectProperty)
                    ),
                    OntologyConstants.KnoraApiV2WithValueObjects.SubjectType.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.KnoraApiV2WithValueObjects.SubjectType.toSmartIri,
                        objects = Set(AnythingOntologyIri.makeEntityIri("Thing").toString)
                    ),
                    OntologyConstants.KnoraApiV2WithValueObjects.ObjectType.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.KnoraApiV2WithValueObjects.ObjectType.toSmartIri,
                        objects = Set(AnythingOntologyIri.makeEntityIri("Thing").toString)
                    ),
                    OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
                        objectsWithLang = Map(
                            "en" -> "wrong property"
                        )
                    ),
                    OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
                        objectsWithLang = Map(
                            "en" -> "An invalid property definition"
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
                userProfile = anythingUserProfile
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure =>
                    if (printErrorMessages) println(msg.cause.getMessage)
                    msg.cause.isInstanceOf[BadRequestException] should ===(true)
            }

        }

        "change the labels of a property" in {
            val propertyIri = AnythingOntologyIri.makeEntityIri("hasName")

            val newObjects = Map(
                "en" -> "has name",
                "fr" -> "a nom",
                "de" -> "hat Namen"
            )

            actorUnderTest ! ChangePropertyLabelsOrCommentsRequestV2(
                propertyIri = propertyIri,
                predicateToUpdate = OntologyConstants.Rdfs.Label.toSmartIri,
                newObjects = newObjects,
                lastModificationDate = anythingLastModDate,
                apiRequestID = UUID.randomUUID,
                userProfile = anythingUserProfile
            )

            expectMsgPF(timeout) {
                case msg: ReadOntologiesV2 =>
                    val externalMsg = msg.toOntologySchema(ApiV2WithValueObjects)
                    val ontology = externalMsg.ontologies.head
                    val readPropertyInfo = ontology.properties(propertyIri)
                    readPropertyInfo.entityInfoContent.predicates(OntologyConstants.Rdfs.Label.toSmartIri).objectsWithLang should ===(newObjects)

                    val metadata = ontology.ontologyMetadata
                    val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(throw AssertionException(s"${metadata.ontologyIri} has no last modification date"))
                    assert(newAnythingLastModDate.isAfter(anythingLastModDate))
                    anythingLastModDate = newAnythingLastModDate
            }
        }

        "change the comments of a property" in {
            val propertyIri = AnythingOntologyIri.makeEntityIri("hasName")

            val newObjects = Map(
                "en" -> "The name of a Thing",
                "fr" -> "Le nom d\\'une chose", // This is SPARQL-escaped as it would be if it was taken from a JSON-LD request.
                "de" -> "Der Name eines Dinges"
            )

            // Make an unescaped copy of the new comments, because this is how we will receive them in the API response.
            val newObjectsUnescaped = newObjects.map {
                case (lang, obj) => lang -> stringFormatter.fromSparqlEncodedString(obj)
            }

            actorUnderTest ! ChangePropertyLabelsOrCommentsRequestV2(
                propertyIri = propertyIri,
                predicateToUpdate = OntologyConstants.Rdfs.Comment.toSmartIri,
                newObjects = newObjects,
                lastModificationDate = anythingLastModDate,
                apiRequestID = UUID.randomUUID,
                userProfile = anythingUserProfile
            )

            expectMsgPF(timeout) {
                case msg: ReadOntologiesV2 =>
                    val externalMsg = msg.toOntologySchema(ApiV2WithValueObjects)
                    val ontology = externalMsg.ontologies.head
                    val readPropertyInfo = ontology.properties(propertyIri)
                    readPropertyInfo.entityInfoContent.predicates(OntologyConstants.Rdfs.Comment.toSmartIri).objectsWithLang should ===(newObjectsUnescaped)

                    val metadata = ontology.ontologyMetadata
                    val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(throw AssertionException(s"${metadata.ontologyIri} has no last modification date"))
                    assert(newAnythingLastModDate.isAfter(anythingLastModDate))
                    anythingLastModDate = newAnythingLastModDate
            }
        }
    }
}
