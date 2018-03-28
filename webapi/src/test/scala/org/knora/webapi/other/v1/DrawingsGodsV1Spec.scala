/*
 * Copyright © 2015-2018 the contributors (see Contributors.md).
 * This file is part of Knora.
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.other.v1

import java.util.UUID

import akka.actor.Props
import akka.event.LoggingAdapter
import com.typesafe.config.ConfigFactory
import org.knora.webapi._
import org.knora.webapi.messages.admin.responder.permissionsmessages.{DefaultObjectAccessPermissionsStringForPropertyGetADM, DefaultObjectAccessPermissionsStringForResourceClassGetADM, DefaultObjectAccessPermissionsStringResponseADM}
import org.knora.webapi.messages.admin.responder.usersmessages.{UserADM, UserGetADM, UserInformationTypeADM}
import org.knora.webapi.messages.store.triplestoremessages.{RdfDataObject, ResetTriplestoreContent, ResetTriplestoreContentACK, TriplestoreJsonProtocol}
import org.knora.webapi.messages.v1.responder.ontologymessages.{LoadOntologiesRequest, LoadOntologiesResponse}
import org.knora.webapi.messages.v1.responder.resourcemessages.{ResourceCreateRequestV1, ResourceCreateResponseV1, _}
import org.knora.webapi.messages.v1.responder.valuemessages.{CreateValueV1WithComment, TextValueSimpleV1, _}
import org.knora.webapi.responders.{RESPONDER_MANAGER_ACTOR_NAME, ResponderManager}
import org.knora.webapi.store.{STORE_MANAGER_ACTOR_NAME, StoreManager}
import org.knora.webapi.util.MutableUserADM

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._

object DrawingsGodsV1Spec {
    val config = ConfigFactory.parseString(
        """
          akka.loglevel = "DEBUG"
          akka.stdout-loglevel = "DEBUG"
        """.stripMargin)
}

/**
  * Test specification for testing a complex permissions structure of the drawings-gods-project.
  */
class DrawingsGodsV1Spec extends CoreSpec(DrawingsGodsV1Spec.config) with TriplestoreJsonProtocol {

    private implicit val executionContext: ExecutionContextExecutor = system.dispatcher
    private val timeout = 5.seconds
    private implicit val log: LoggingAdapter = akka.event.Logging(system, this.getClass())

    private val responderManager = system.actorOf(Props(new ResponderManager with LiveActorMaker), name = RESPONDER_MANAGER_ACTOR_NAME)
    private val storeManager = system.actorOf(Props(new StoreManager with LiveActorMaker), name = STORE_MANAGER_ACTOR_NAME)

    private val rdfDataObjects: List[RdfDataObject] = List(
        RdfDataObject(path = "_test_data/other.v1.DrawingsGodsV1Spec/drawings-gods_admin-data.ttl", name = "http://www.knora.org/data/admin"),
        RdfDataObject(path = "_test_data/other.v1.DrawingsGodsV1Spec/drawings-gods_permissions-data.ttl", name = "http://www.knora.org/data/permissions"),
        RdfDataObject(path = "_test_data/other.v1.DrawingsGodsV1Spec/drawings-gods_ontology.ttl", name = "http://www.knora.org/ontology/0105/drawings-gods"),
        RdfDataObject(path = "_test_data/other.v1.DrawingsGodsV1Spec/drawings-gods_data.ttl", name = "http://www.knora.org/data/0105/drawings-gods")
    )

    "Load test data" in {
        storeManager ! ResetTriplestoreContent(rdfDataObjects)
        expectMsg(300.seconds, ResetTriplestoreContentACK())

        responderManager ! LoadOntologiesRequest(KnoraSystemInstances.Users.SystemUser)
        expectMsg(10.seconds, LoadOntologiesResponse())
    }

    /**
      * issues:
      * - https://github.com/dhlab-basel/Knora/issues/416
      * - https://github.com/dhlab-basel/Knora/issues/610
      */
    "Using the DrawingsGods project data" should {

        val drawingsGodsProjectIri = "http://rdfh.ch/projects/0105"
        val drawingsGodsOntologyIri = "http://www.knora.org/ontology/0105/drawings-gods"
        val rootUserIri = "http://rdfh.ch/users/root"
        val rootUser = new MutableUserADM
        val ddd1UserIri = "http://rdfh.ch/users/drawings-gods-test-ddd1"
        val ddd1 = new MutableUserADM
        val ddd2UserIri = "http://rdfh.ch/users/drawings-gods-test-ddd2"
        val ddd2 = new MutableUserADM

        "retrieve the drawings gods user's profile" in {
            responderManager ! UserGetADM(maybeIri = Some(rootUserIri), maybeEmail = None, userInformationTypeADM = UserInformationTypeADM.FULL, requestingUser = KnoraSystemInstances.Users.SystemUser)
            rootUser.set(expectMsgType[Option[UserADM]](timeout).get)

            responderManager ! UserGetADM(maybeIri = Some(ddd1UserIri), maybeEmail = None, userInformationTypeADM = UserInformationTypeADM.FULL, requestingUser = KnoraSystemInstances.Users.SystemUser)
            ddd1.set(expectMsgType[Option[UserADM]](timeout).get)

            responderManager ! UserGetADM(maybeIri = Some(ddd2UserIri), maybeEmail = None, userInformationTypeADM = UserInformationTypeADM.FULL, requestingUser = KnoraSystemInstances.Users.SystemUser)
            ddd2.set(expectMsgType[Option[UserADM]](timeout).get)
        }

        "return correct drawings-gods:QualityData resource permissions string for drawings-gods-test-ddd2 user" in {
            val qualityDataResourceClass = s"$drawingsGodsOntologyIri#QualityData"
            responderManager ! DefaultObjectAccessPermissionsStringForResourceClassGetADM(drawingsGodsProjectIri, qualityDataResourceClass, targetUser = ddd2.get, requestingUser = KnoraSystemInstances.Users.SystemUser)
            expectMsg(DefaultObjectAccessPermissionsStringResponseADM("CR http://rdfh.ch/groups/0105/drawings-gods-admin|D http://rdfh.ch/groups/0105/drawings-gods-snf-team,knora-base:Creator|M http://rdfh.ch/groups/0105/drawings-gods-meta-annotators,http://rdfh.ch/groups/0105/drawings-gods-add-drawings"))
        }

        "return correct drawings-gods:Person resource class permissions string for drawings-gods-test-ddd1 user" in {
            val personResourceClass = s"$drawingsGodsOntologyIri#Person"
            responderManager ! DefaultObjectAccessPermissionsStringForResourceClassGetADM(drawingsGodsProjectIri, personResourceClass, targetUser = ddd1.get, requestingUser = KnoraSystemInstances.Users.SystemUser)
            expectMsg(DefaultObjectAccessPermissionsStringResponseADM("CR http://rdfh.ch/groups/0105/drawings-gods-admin|D http://rdfh.ch/groups/0105/drawings-gods-snf-team,knora-base:Creator|M http://rdfh.ch/groups/0105/drawings-gods-meta-annotators,http://rdfh.ch/groups/0105/drawings-gods-add-drawings|V knora-base:KnownUser,knora-base:UnknownUser,knora-base:ProjectMember"))
        }

        "return correct drawings-gods:hasLastname property permissions string for drawings-gods-test-ddd1 user" in {
            val personResourceClass = s"$drawingsGodsOntologyIri#Person"
            val hasLastnameProperty = s"$drawingsGodsOntologyIri#hasLastname"
            responderManager ! DefaultObjectAccessPermissionsStringForPropertyGetADM(drawingsGodsProjectIri, personResourceClass, hasLastnameProperty, targetUser = ddd1.get, requestingUser = KnoraSystemInstances.Users.SystemUser)
            expectMsg(DefaultObjectAccessPermissionsStringResponseADM("CR http://rdfh.ch/groups/0105/drawings-gods-admin|D http://rdfh.ch/groups/0105/drawings-gods-snf-team"))
        }

        "return correct drawings-gods:DrawingPublic / knora-base:hasStillImageFileValue combination permissions string for drawings-gods-test-ddd1 user" in {
            val drawingPublicResourceClass = s"$drawingsGodsOntologyIri#DrawingPublic"
            val hasStillImageFileValue = OntologyConstants.KnoraBase.HasStillImageFileValue
            responderManager ! DefaultObjectAccessPermissionsStringForPropertyGetADM(drawingsGodsProjectIri, drawingPublicResourceClass, hasStillImageFileValue, targetUser = ddd1.get, requestingUser = KnoraSystemInstances.Users.SystemUser)
            expectMsg(DefaultObjectAccessPermissionsStringResponseADM("CR http://rdfh.ch/groups/0105/drawings-gods-admin|D http://rdfh.ch/groups/0105/drawings-gods-snf-team|M http://rdfh.ch/groups/0105/drawings-gods-add-drawings|V knora-base:KnownUser,knora-base:UnknownUser,http://rdfh.ch/groups/0105/drawings-gods-meta-annotators,knora-base:ProjectMember"))
        }

        "return correct drawings-gods:DrawingPrivate / knora-base:hasStillImageFileValue combination permissions string for drawings-gods-test-ddd1 user" in {
            val drawingPrivateResourceClass = s"$drawingsGodsOntologyIri#DrawingPrivate"
            val hasStillImageFileValue = OntologyConstants.KnoraBase.HasStillImageFileValue
            responderManager ! DefaultObjectAccessPermissionsStringForPropertyGetADM(drawingsGodsProjectIri, drawingPrivateResourceClass, hasStillImageFileValue, targetUser = ddd1.get, requestingUser = KnoraSystemInstances.Users.SystemUser)
            expectMsg(DefaultObjectAccessPermissionsStringResponseADM("CR http://rdfh.ch/groups/0105/drawings-gods-admin|D http://rdfh.ch/groups/0105/drawings-gods-snf-team|M http://rdfh.ch/groups/0105/drawings-gods-meta-annotators,http://rdfh.ch/groups/0105/drawings-gods-add-drawings|V knora-base:ProjectMember"))
        }

        "allow drawings-gods-test-ddd1 user to create a resource, then query it and see its label and properties" in {

            val valuesToBeCreated = Map(
                s"$drawingsGodsOntologyIri#hasLastname" -> Vector(CreateValueV1WithComment(TextValueSimpleV1("PersonTest DDD1"))),
                s"$drawingsGodsOntologyIri#hasCodePerson" -> Vector(CreateValueV1WithComment(TextValueSimpleV1("Code"))),
                s"$drawingsGodsOntologyIri#hasPersonGender" -> Vector(CreateValueV1WithComment(HierarchicalListValueV1("http://rdfh.ch/lists/0105/drawings-gods-2016-list-FiguresHList-polysexual"))),
                s"$drawingsGodsOntologyIri#hasDrawingChildTotal" -> Vector(CreateValueV1WithComment(IntegerValueV1(99)))
            )

            responderManager ! ResourceCreateRequestV1(
                resourceTypeIri = s"$drawingsGodsOntologyIri#Person",
                label = "Test-Person",
                projectIri = drawingsGodsProjectIri,
                values = valuesToBeCreated,
                file = None,
                userProfile = ddd1.get,
                apiRequestID = UUID.randomUUID
            )

            val createResponse = expectMsgType[ResourceCreateResponseV1](timeout)
            val resourceIri = createResponse.res_id

            responderManager ! ResourceFullGetRequestV1(iri = resourceIri, userProfile = ddd1.get)

            val getResponse = expectMsgType[ResourceFullResponseV1](timeout)

            val maybeLabel: Option[String] = getResponse.resinfo.get.firstproperty
            assert(maybeLabel.isDefined, "Response returned no resource label")
            assert(maybeLabel.get == "Test-Person")

            val maybeLastNameProp: Option[PropertyV1] = getResponse.props.get.properties.find(prop => prop.pid == s"$drawingsGodsOntologyIri#hasLastname")
            assert(maybeLastNameProp.isDefined, "Response returned no property hasLastname")
            assert(maybeLastNameProp.get.values.head.asInstanceOf[TextValueV1].utf8str == "PersonTest DDD1")
        }

        "allow root user (SystemAdmin) to create a resource" in {

            val valuesToBeCreated = Map(
                s"$drawingsGodsOntologyIri#hasLastname" -> Vector(CreateValueV1WithComment(TextValueSimpleV1("PersonTest DDD1"))),
                s"$drawingsGodsOntologyIri#hasCodePerson" -> Vector(CreateValueV1WithComment(TextValueSimpleV1("Code"))),
                s"$drawingsGodsOntologyIri#hasPersonGender" -> Vector(CreateValueV1WithComment(HierarchicalListValueV1("http://rdfh.ch/lists/0105/drawings-gods-2016-list-FiguresHList-polysexual"))),
                s"$drawingsGodsOntologyIri#hasDrawingChildTotal" -> Vector(CreateValueV1WithComment(IntegerValueV1(99)))
            )

            responderManager ! ResourceCreateRequestV1(
                resourceTypeIri = s"$drawingsGodsOntologyIri#Person",
                label = "Test-Person",
                projectIri = drawingsGodsProjectIri,
                values = valuesToBeCreated,
                file = None,
                userProfile = rootUser.get,
                apiRequestID = UUID.randomUUID
            )

            expectMsgType[ResourceCreateResponseV1](timeout)
        }
    }
}
