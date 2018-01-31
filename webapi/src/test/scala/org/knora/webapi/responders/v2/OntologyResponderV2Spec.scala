package org.knora.webapi.responders.v2

import java.time.Instant
import java.util.UUID

import akka.actor.Props
import akka.testkit.{ImplicitSender, TestActorRef}
import org.knora.webapi._
import org.knora.webapi.messages.store.triplestoremessages.{RdfDataObject, ResetTriplestoreContent, ResetTriplestoreContentACK}
import org.knora.webapi.messages.v1.responder.ontologymessages.{LoadOntologiesRequest, LoadOntologiesResponse}
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

    private val imagesUserProfile = SharedTestDataV1.imagesUser01
    private val imagesProjectIri = SharedTestDataV1.IMAGES_PROJECT_IRI.toSmartIri

    private val anythingUserProfile = SharedTestDataV1.anythingAdminUser
    private val anythingProjectIri = SharedTestDataV1.ANYTHING_PROJECT_IRI.toSmartIri

    private val actorUnderTest = TestActorRef[OntologyResponderV2]
    private val responderManager = system.actorOf(Props(new ResponderManager with LiveActorMaker), name = RESPONDER_MANAGER_ACTOR_NAME)
    private val storeManager = system.actorOf(Props(new StoreManager with LiveActorMaker), name = STORE_MANAGER_ACTOR_NAME)

    private val rdfDataObjects = List(
        RdfDataObject(path = "_test_data/all_data/anything-data.ttl", name = "http://www.knora.org/data/anything")
    )

    // The default timeout for receiving reply messages from actors.
    private val timeout = 10.seconds

    private val fooIri = new MutableTestIri
    private var fooLastModDate: Instant = Instant.now

    private val AnythingOntologyIri = "http://0.0.0.0:3333/ontology/anything/v2".toSmartIri
    private var anythingLastModDate: Instant = Instant.now

    private val printErrorMessages = true

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

        "create a property anything:hasName as a subproperty of knora-api:hasValue and schema:name" in {

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

        "not create a property with the wrong rdf:type" in {

            val propertyIri = AnythingOntologyIri.makeEntityIri("wrongProperty")

            val propertyInfoContent = PropertyInfoContentV2(
                propertyIri = propertyIri,
                predicates = Map(
                    OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
                        objects = Set(OntologyConstants.Owl.Class)
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

        "not create a property that already exists" in {

            val propertyIri = AnythingOntologyIri.makeEntityIri("hasInteger")

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

        "not create a property with a nonexistent Knora superproperty" in {

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
                subPropertyOf = Set(AnythingOntologyIri.makeEntityIri("nonexistentProperty")),
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
                    msg.cause.isInstanceOf[NotFoundException] should ===(true)
            }
        }

        "not create a property that is not a subproperty of knora-api:hasValue or knora-api:hasLinkTo" in {

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
                subPropertyOf = Set("http://xmlns.com/foaf/0.1/name".toSmartIri),
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

        "not create a property that is a subproperty of both knora-api:hasValue and knora-api:hasLinkTo" in {

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

        "not create a subproperty of anything:hasInteger with a knora-base:subjectType of knora-api:Representation" in {

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
                        objects = Set(OntologyConstants.KnoraApiV2WithValueObjects.Representation)
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
                subPropertyOf = Set(AnythingOntologyIri.makeEntityIri("hasInteger")),
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
                "fr" -> "Le nom d\\'une chose", // This is SPARQL-escaped as it would be if taken from a JSON-LD request.
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

        "create a class anything:WildThing that is a subclass of anything:Thing, with a direct cardinality for anything:hasName" in {
            val classIri = AnythingOntologyIri.makeEntityIri("WildThing")

            val classInfoContent = ClassInfoContentV2(
                classIri = classIri,
                predicates = Map(
                    OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
                        objects = Set(OntologyConstants.Owl.Class)
                    ),
                    OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
                        objectsWithLang = Map(
                            "en" -> "wild thing"
                        )
                    ),
                    OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
                        objectsWithLang = Map(
                            "en" -> "A thing that is wild"
                        )
                    )
                ),
                directCardinalities = Map(AnythingOntologyIri.makeEntityIri("hasName") -> Cardinality.MayHaveOne),
                subClassOf = Set(AnythingOntologyIri.makeEntityIri("Thing")),
                ontologySchema = ApiV2WithValueObjects
            )

            actorUnderTest ! CreateClassRequestV2(
                classInfoContent = classInfoContent,
                lastModificationDate = anythingLastModDate,
                apiRequestID = UUID.randomUUID,
                userProfile = anythingUserProfile
            )

            val expectedProperties: Set[SmartIri] = Set(
                "http://api.knora.org/ontology/knora-api/v2#hasStandoffLinkTo",
                "http://api.knora.org/ontology/knora-api/v2#hasStandoffLinkToValue",
                "http://0.0.0.0:3333/ontology/anything/v2#hasOtherThingValue",
                "http://0.0.0.0:3333/ontology/anything/v2#hasBlueThing",
                "http://0.0.0.0:3333/ontology/anything/v2#hasThingPicture",
                "http://0.0.0.0:3333/ontology/anything/v2#hasDate",
                "http://0.0.0.0:3333/ontology/anything/v2#hasBoolean",
                "http://0.0.0.0:3333/ontology/anything/v2#hasThingPictureValue",
                "http://0.0.0.0:3333/ontology/anything/v2#hasText",
                "http://0.0.0.0:3333/ontology/anything/v2#hasColor",
                "http://0.0.0.0:3333/ontology/anything/v2#hasInterval",
                "http://0.0.0.0:3333/ontology/anything/v2#isPartOfOtherThing",
                "http://0.0.0.0:3333/ontology/anything/v2#hasDecimal",
                "http://0.0.0.0:3333/ontology/anything/v2#hasOtherThing",
                "http://0.0.0.0:3333/ontology/anything/v2#hasBlueThingValue",
                "http://0.0.0.0:3333/ontology/anything/v2#hasInteger",
                "http://0.0.0.0:3333/ontology/anything/v2#hasListItem",
                "http://0.0.0.0:3333/ontology/anything/v2#hasRichtext",
                "http://0.0.0.0:3333/ontology/anything/v2#hasUri",
                "http://0.0.0.0:3333/ontology/anything/v2#hasName",
                "http://0.0.0.0:3333/ontology/anything/v2#isPartOfOtherThingValue",
                "http://0.0.0.0:3333/ontology/anything/v2#hasOtherListItem"
            ).map(_.toSmartIri)

            expectMsgPF(timeout) {
                case msg: ReadOntologiesV2 =>
                    val externalMsg = msg.toOntologySchema(ApiV2WithValueObjects)
                    val ontology = externalMsg.ontologies.head
                    val readClassInfo = ontology.classes(classIri)
                    readClassInfo.entityInfoContent should ===(classInfoContent)
                    readClassInfo.allCardinalities.keySet should ===(expectedProperties)

                    val metadata = ontology.ontologyMetadata
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
                        objects = Set(OntologyConstants.Owl.Class)
                    ),
                    OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
                        objectsWithLang = Map(
                            "en" -> "nothing",
                            "de" -> "Nichts"
                        )
                    ),
                    OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
                        objectsWithLang = Map(
                            "en" -> "Represents nothing",
                            "de" -> "Stellt nichts dar"
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
                userProfile = anythingUserProfile
            )

            val expectedProperties = Set(
                "http://api.knora.org/ontology/knora-api/v2#hasStandoffLinkTo",
                "http://api.knora.org/ontology/knora-api/v2#hasStandoffLinkToValue"
            ).map(_.toSmartIri)

            expectMsgPF(timeout) {
                case msg: ReadOntologiesV2 =>
                    val externalMsg = msg.toOntologySchema(ApiV2WithValueObjects)
                    val ontology = externalMsg.ontologies.head
                    val readClassInfo = ontology.classes(classIri)
                    readClassInfo.entityInfoContent should ===(classInfoContent)
                    readClassInfo.allCardinalities.keySet should ===(expectedProperties)

                    val metadata = ontology.ontologyMetadata
                    val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(throw AssertionException(s"${metadata.ontologyIri} has no last modification date"))
                    assert(newAnythingLastModDate.isAfter(anythingLastModDate))
                    anythingLastModDate = newAnythingLastModDate
            }
        }

        "change the labels of a class" in {
            val classIri = AnythingOntologyIri.makeEntityIri("Nothing")

            val newObjects = Map(
                "en" -> "nothing",
                "fr" -> "rien"
            )

            actorUnderTest ! ChangeClassLabelsOrCommentsRequestV2(
                classIri = classIri,
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
                    val readClassInfo = ontology.classes(classIri)
                    readClassInfo.entityInfoContent.predicates(OntologyConstants.Rdfs.Label.toSmartIri).objectsWithLang should ===(newObjects)

                    val metadata = ontology.ontologyMetadata
                    val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(throw AssertionException(s"${metadata.ontologyIri} has no last modification date"))
                    assert(newAnythingLastModDate.isAfter(anythingLastModDate))
                    anythingLastModDate = newAnythingLastModDate
            }
        }

        "change the comments of a class" in {
            val classIri = AnythingOntologyIri.makeEntityIri("Nothing")

            val newObjects = Map(
                "en" -> "Represents nothing",
                "fr" -> "ne représente rien"
            )

            // Make an unescaped copy of the new comments, because this is how we will receive them in the API response.
            val newObjectsUnescaped = newObjects.map {
                case (lang, obj) => lang -> stringFormatter.fromSparqlEncodedString(obj)
            }

            actorUnderTest ! ChangeClassLabelsOrCommentsRequestV2(
                classIri = classIri,
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
                    val readClassInfo = ontology.classes(classIri)
                    readClassInfo.entityInfoContent.predicates(OntologyConstants.Rdfs.Comment.toSmartIri).objectsWithLang should ===(newObjectsUnescaped)

                    val metadata = ontology.ontologyMetadata
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
                        objects = Set(OntologyConstants.Owl.ObjectProperty)
                    ),
                    OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
                        objectsWithLang = Map(
                            "en" -> "wrong class"
                        )
                    ),
                    OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
                        objectsWithLang = Map(
                            "en" -> "An invalid class definition"
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
                userProfile = anythingUserProfile
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
                        objects = Set(OntologyConstants.Owl.Class)
                    ),
                    OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
                        objectsWithLang = Map(
                            "en" -> "wrong class"
                        )
                    ),
                    OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
                        objectsWithLang = Map(
                            "en" -> "An invalid class definition"
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
                userProfile = anythingUserProfile
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
                        objects = Set(OntologyConstants.Owl.Class)
                    ),
                    OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
                        objectsWithLang = Map(
                            "en" -> "wrong class"
                        )
                    ),
                    OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
                        objectsWithLang = Map(
                            "en" -> "An invalid class definition"
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
                userProfile = anythingUserProfile
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure =>
                    if (printErrorMessages) println(msg.cause.getMessage)
                    msg.cause.isInstanceOf[NotFoundException] should ===(true)
            }
        }

        "not create a class that is not a subclass of knora-api:Resource" in {
            val classIri = AnythingOntologyIri.makeEntityIri("WrongClass")

            val classInfoContent = ClassInfoContentV2(
                classIri = classIri,
                predicates = Map(
                    OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
                        objects = Set(OntologyConstants.Owl.Class)
                    ),
                    OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
                        objectsWithLang = Map(
                            "en" -> "wrong class"
                        )
                    ),
                    OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
                        objectsWithLang = Map(
                            "en" -> "An invalid class definition"
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
                userProfile = anythingUserProfile
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
                        objects = Set(OntologyConstants.Owl.Class)
                    ),
                    OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
                        objectsWithLang = Map(
                            "en" -> "wrong class"
                        )
                    ),
                    OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
                        objectsWithLang = Map(
                            "en" -> "An invalid class definition"
                        )
                    )
                ),
                directCardinalities = Map(AnythingOntologyIri.makeEntityIri("nonexistentProperty") -> Cardinality.MayHaveOne),
                subClassOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.Resource.toSmartIri),
                ontologySchema = ApiV2WithValueObjects
            )

            actorUnderTest ! CreateClassRequestV2(
                classInfoContent = classInfoContent,
                lastModificationDate = anythingLastModDate,
                apiRequestID = UUID.randomUUID,
                userProfile = anythingUserProfile
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
                        objects = Set(OntologyConstants.Owl.Class)
                    ),
                    OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
                        objectsWithLang = Map(
                            "en" -> "wrong class"
                        )
                    ),
                    OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
                        objectsWithLang = Map(
                            "en" -> "An invalid class definition"
                        )
                    )
                ),
                directCardinalities = Map(AnythingOntologyIri.makeEntityIri("hasInteger") -> Cardinality.MayHaveOne),
                subClassOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.Resource.toSmartIri),
                ontologySchema = ApiV2WithValueObjects
            )

            actorUnderTest ! CreateClassRequestV2(
                classInfoContent = classInfoContent,
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

        "create a subclass of anything:Thing that has cardinality 1 for anything:hasBoolean" in {
            val classIri = AnythingOntologyIri.makeEntityIri("RestrictiveThing")

            val classInfoContent = ClassInfoContentV2(
                classIri = classIri,
                predicates = Map(
                    OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
                        objects = Set(OntologyConstants.Owl.Class)
                    ),
                    OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
                        objectsWithLang = Map(
                            "en" -> "restrictive thing"
                        )
                    ),
                    OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
                        objectsWithLang = Map(
                            "en" -> "A more restrictive Thing"
                        )
                    )
                ),
                directCardinalities = Map(AnythingOntologyIri.makeEntityIri("hasBoolean") -> Cardinality.MustHaveOne),
                subClassOf = Set(AnythingOntologyIri.makeEntityIri("Thing")),
                ontologySchema = ApiV2WithValueObjects
            )

            actorUnderTest ! CreateClassRequestV2(
                classInfoContent = classInfoContent,
                lastModificationDate = anythingLastModDate,
                apiRequestID = UUID.randomUUID,
                userProfile = anythingUserProfile
            )

            expectMsgPF(timeout) {
                case msg: ReadOntologiesV2 =>
                    val externalMsg = msg.toOntologySchema(ApiV2WithValueObjects)
                    val ontology = externalMsg.ontologies.head
                    val readClassInfo = ontology.classes(classIri)
                    readClassInfo.entityInfoContent should ===(classInfoContent)
                    readClassInfo.allCardinalities(AnythingOntologyIri.makeEntityIri("hasBoolean")) should ===(Cardinality.MustHaveOne)

                    val metadata = ontology.ontologyMetadata
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
                        objects = Set(OntologyConstants.Owl.Class)
                    ),
                    OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
                        objectsWithLang = Map(
                            "en" -> "wrong class"
                        )
                    ),
                    OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
                        objectsWithLang = Map(
                            "en" -> "An invalid class definition"
                        )
                    )
                ),
                directCardinalities = Map(AnythingOntologyIri.makeEntityIri("hasBoolean") -> Cardinality.MayHaveMany),
                subClassOf = Set(AnythingOntologyIri.makeEntityIri("Thing")),
                ontologySchema = ApiV2WithValueObjects
            )

            actorUnderTest ! CreateClassRequestV2(
                classInfoContent = classInfoContent,
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

        "create a property anything:hasNothingness with knora-api:subjectType anything:Nothing" in {
            val propertyIri = AnythingOntologyIri.makeEntityIri("hasNothingness")

            val propertyInfoContent = PropertyInfoContentV2(
                propertyIri = propertyIri,
                predicates = Map(
                    OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
                        objects = Set(OntologyConstants.Owl.ObjectProperty)
                    ),
                    OntologyConstants.KnoraApiV2WithValueObjects.SubjectType.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.KnoraApiV2WithValueObjects.SubjectType.toSmartIri,
                        objects = Set(AnythingOntologyIri.makeEntityIri("Nothing").toString)
                    ),
                    OntologyConstants.KnoraApiV2WithValueObjects.ObjectType.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.KnoraApiV2WithValueObjects.ObjectType.toSmartIri,
                        objects = Set(OntologyConstants.KnoraApiV2WithValueObjects.BooleanValue)
                    ),
                    OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
                        objectsWithLang = Map(
                            "en" -> "has nothingness",
                            "de" -> "hat Nichtsein"
                        )
                    ),
                    OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
                        objectsWithLang = Map(
                            "en" -> "Indicates whether a Nothing has nothingness",
                            "de" -> "Anzeigt, ob ein Nichts Nichtsein hat"
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
        }

        "add a cardinality for the property anything:hasNothingness to the class anything:Nothing" in {
            val classIri = AnythingOntologyIri.makeEntityIri("Nothing")

            val classInfoContent = ClassInfoContentV2(
                classIri = classIri,
                predicates = Map(
                    OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
                        objects = Set(OntologyConstants.Owl.Class)
                    )
                ),
                directCardinalities = Map(
                    AnythingOntologyIri.makeEntityIri("hasNothingness") -> Cardinality.MayHaveOne
                ),
                ontologySchema = ApiV2WithValueObjects
            )

            actorUnderTest ! AddCardinalitiesToClassRequestV2(
                classInfoContent = classInfoContent,
                lastModificationDate = anythingLastModDate,
                apiRequestID = UUID.randomUUID,
                userProfile = anythingUserProfile
            )

            val expectedProperties = Set(
                OntologyConstants.KnoraApiV2WithValueObjects.HasStandoffLinkTo.toSmartIri,
                OntologyConstants.KnoraApiV2WithValueObjects.HasStandoffLinkToValue.toSmartIri,
                AnythingOntologyIri.makeEntityIri("hasNothingness")
            )

            expectMsgPF(timeout) {
                case msg: ReadOntologiesV2 =>
                    val externalMsg = msg.toOntologySchema(ApiV2WithValueObjects)
                    val ontology = externalMsg.ontologies.head
                    val readClassInfo = ontology.classes(classIri)
                    readClassInfo.entityInfoContent.directCardinalities should ===(classInfoContent.directCardinalities)
                    readClassInfo.allCardinalities.keySet should ===(expectedProperties)

                    val metadata = ontology.ontologyMetadata
                    val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(throw AssertionException(s"${metadata.ontologyIri} has no last modification date"))
                    assert(newAnythingLastModDate.isAfter(anythingLastModDate))
                    anythingLastModDate = newAnythingLastModDate
            }
        }

        "not add a cardinality to a class that is used in data" in {
            val classIri = AnythingOntologyIri.makeEntityIri("Thing")

            val classInfoContent = ClassInfoContentV2(
                classIri = classIri,
                predicates = Map(
                    OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
                        objects = Set(OntologyConstants.Owl.Class)
                    )
                ),
                directCardinalities = Map(
                    AnythingOntologyIri.makeEntityIri("hasName") -> Cardinality.MayHaveOne
                ),
                ontologySchema = ApiV2WithValueObjects
            )

            actorUnderTest ! AddCardinalitiesToClassRequestV2(
                classInfoContent = classInfoContent,
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

        "create a property anything:hasEmptiness with knora-api:subjectType anything:Nothing" in {
            val propertyIri = AnythingOntologyIri.makeEntityIri("hasEmptiness")

            val propertyInfoContent = PropertyInfoContentV2(
                propertyIri = propertyIri,
                predicates = Map(
                    OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
                        objects = Set(OntologyConstants.Owl.ObjectProperty)
                    ),
                    OntologyConstants.KnoraApiV2WithValueObjects.SubjectType.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.KnoraApiV2WithValueObjects.SubjectType.toSmartIri,
                        objects = Set(AnythingOntologyIri.makeEntityIri("Nothing").toString)
                    ),
                    OntologyConstants.KnoraApiV2WithValueObjects.ObjectType.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.KnoraApiV2WithValueObjects.ObjectType.toSmartIri,
                        objects = Set(OntologyConstants.KnoraApiV2WithValueObjects.BooleanValue)
                    ),
                    OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
                        objectsWithLang = Map(
                            "en" -> "has emptiness",
                            "de" -> "hat Leerheit"
                        )
                    ),
                    OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
                        objectsWithLang = Map(
                            "en" -> "Indicates whether a Nothing has emptiness",
                            "de" -> "Anzeigt, ob ein Nichts Leerheit hat"
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
        }

        "change the cardinalities of the class anything:Nothing" in {
            val classIri = AnythingOntologyIri.makeEntityIri("Nothing")

            val classInfoContent = ClassInfoContentV2(
                classIri = classIri,
                predicates = Map(
                    OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
                        predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
                        objects = Set(OntologyConstants.Owl.Class)
                    )
                ),
                directCardinalities = Map(
                    AnythingOntologyIri.makeEntityIri("hasEmptiness") -> Cardinality.MayHaveOne
                ),
                ontologySchema = ApiV2WithValueObjects
            )

            actorUnderTest ! ChangeCardinalitiesRequestV2(
                classInfoContent = classInfoContent,
                lastModificationDate = anythingLastModDate,
                apiRequestID = UUID.randomUUID,
                userProfile = anythingUserProfile
            )

            val expectedProperties = Set(
                OntologyConstants.KnoraApiV2WithValueObjects.HasStandoffLinkTo.toSmartIri,
                OntologyConstants.KnoraApiV2WithValueObjects.HasStandoffLinkToValue.toSmartIri,
                AnythingOntologyIri.makeEntityIri("hasEmptiness")
            )

            expectMsgPF(timeout) {
                case msg: ReadOntologiesV2 =>
                    val externalMsg = msg.toOntologySchema(ApiV2WithValueObjects)
                    val ontology = externalMsg.ontologies.head
                    val readClassInfo = ontology.classes(classIri)
                    readClassInfo.entityInfoContent.directCardinalities should ===(classInfoContent.directCardinalities)
                    readClassInfo.allCardinalities.keySet should ===(expectedProperties)

                    val metadata = ontology.ontologyMetadata
                    val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(throw AssertionException(s"${metadata.ontologyIri} has no last modification date"))
                    assert(newAnythingLastModDate.isAfter(anythingLastModDate))
                    anythingLastModDate = newAnythingLastModDate
            }
        }
    }
}
