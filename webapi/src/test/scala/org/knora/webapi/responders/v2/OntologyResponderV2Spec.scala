package org.knora.webapi.responders.v2

import java.util.UUID

import akka.actor.Props
import akka.testkit.{ImplicitSender, TestActorRef}
import org.knora.webapi._
import org.knora.webapi.messages.store.triplestoremessages.{ResetTriplestoreContent, ResetTriplestoreContentACK}
import org.knora.webapi.messages.v1.responder.ontologymessages.{LoadOntologiesRequest, LoadOntologiesResponse}
import org.knora.webapi.messages.v2.responder.ontologymessages.{CreateOntologyRequestV2, ReadEntityDefinitionsV2}
import org.knora.webapi.responders.{RESPONDER_MANAGER_ACTOR_NAME, ResponderManager}
import org.knora.webapi.store.{STORE_MANAGER_ACTOR_NAME, StoreManager}
import org.knora.webapi.util.IriConversions._
import org.knora.webapi.util.StringFormatter

import scala.concurrent.duration._

class OntologyResponderV2Spec extends CoreSpec() with ImplicitSender {

    private implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance
    private val userProfile = SharedAdminTestData.anythingUser1
    private val projectWithoutProjectID = SharedAdminTestData.ANYTHING_PROJECT_IRI.toSmartIri
    private val projectWithProjectID = SharedAdminTestData.ANYTHING_PROJECT_IRI.toSmartIri // TODO: use a project that has a project ID, when one exists.

    private val actorUnderTest = TestActorRef[OntologyResponderV2]
    private val responderManager = system.actorOf(Props(new ResponderManager with LiveActorMaker), name = RESPONDER_MANAGER_ACTOR_NAME)
    private val storeManager = system.actorOf(Props(new StoreManager with LiveActorMaker), name = STORE_MANAGER_ACTOR_NAME)

    private val rdfDataObjects = List()

    // The default timeout for receiving reply messages from actors.
    private val timeout = 10.seconds

    "Load test data" in {
        storeManager ! ResetTriplestoreContent(rdfDataObjects)
        expectMsg(300.seconds, ResetTriplestoreContentACK())

        responderManager ! LoadOntologiesRequest(userProfile)
        expectMsg(10.seconds, LoadOntologiesResponse())
    }

    "The ontology responder v2" should {
        "create an empty ontology called 'foo' without a project code" in {

            actorUnderTest ! CreateOntologyRequestV2(
                ontologyName = "foo",
                projectIri = projectWithoutProjectID,
                apiRequestID = UUID.randomUUID,
                userProfile = userProfile
            )

            val response = expectMsgType[ReadEntityDefinitionsV2](timeout)
            response.ontologies should ===(Map("http://0.0.0.0:3333/ontology/foo/v2" -> Set.empty[IRI]))
        }

        "create an empty ontology called 'bar' with a project code" in {

            actorUnderTest ! CreateOntologyRequestV2(
                ontologyName = "bar",
                projectIri = projectWithProjectID,
                apiRequestID = UUID.randomUUID,
                userProfile = userProfile
            )

            val response = expectMsgType[ReadEntityDefinitionsV2](timeout)
            response.ontologies should ===(Map("http://0.0.0.0:3333/ontology/00FF/bar/v2" -> Set.empty[IRI]))
        }

        "not create 'foo' again" in {
            actorUnderTest ! CreateOntologyRequestV2(
                ontologyName = "foo",
                projectIri = projectWithoutProjectID,
                apiRequestID = UUID.randomUUID,
                userProfile = userProfile
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[BadRequestException] should ===(true)
            }
        }

        "not create 'bar' again" in {
            actorUnderTest ! CreateOntologyRequestV2(
                ontologyName = "bar",
                projectIri = projectWithProjectID,
                apiRequestID = UUID.randomUUID,
                userProfile = userProfile
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[BadRequestException] should ===(true)
            }
        }
        "not create an ontology called '0000'" in {
            actorUnderTest ! CreateOntologyRequestV2(
                ontologyName = "0000",
                projectIri = projectWithoutProjectID,
                apiRequestID = UUID.randomUUID,
                userProfile = userProfile
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[BadRequestException] should ===(true)
            }

        }

        "not create an ontology called '-foo'" in {
            actorUnderTest ! CreateOntologyRequestV2(
                ontologyName = "-foo",
                projectIri = projectWithoutProjectID,
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
                projectIri = projectWithoutProjectID,
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
                projectIri = projectWithoutProjectID,
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
                projectIri = projectWithoutProjectID,
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
                projectIri = projectWithoutProjectID,
                apiRequestID = UUID.randomUUID,
                userProfile = userProfile
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[BadRequestException] should ===(true)
            }

        }

    }

}


