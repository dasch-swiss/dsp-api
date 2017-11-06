/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and Sepideh Alassi.
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

/**
  * To be able to test UsersResponder, we need to be able to start UsersResponder isolated. Now the UsersResponder
  * extend ResponderV1 which messes up testing, as we cannot inject the TestActor system.
  */
package org.knora.webapi.responders.v1

import java.util.UUID

import akka.actor.Props
import akka.actor.Status.Failure
import akka.testkit.{ImplicitSender, TestActorRef}
import com.typesafe.config.{Config, ConfigFactory}
import org.knora.webapi.SharedAdminTestData._
import org.knora.webapi._
import org.knora.webapi.messages.store.triplestoremessages._
import org.knora.webapi.messages.v1.responder.ontologymessages.{LoadOntologiesRequest, LoadOntologiesResponse, NamedGraphV1}
import org.knora.webapi.messages.v1.responder.projectmessages._
import org.knora.webapi.messages.v1.responder.usermessages.UserProfileTypeV1
import org.knora.webapi.responders.{RESPONDER_MANAGER_ACTOR_NAME, ResponderManager}
import org.knora.webapi.store.{STORE_MANAGER_ACTOR_NAME, StoreManager}
import org.knora.webapi.util.MutableTestIri

import scala.concurrent.duration._


object ProjectsResponderV1Spec {

    val config: Config = ConfigFactory.parseString(
        """
         akka.loglevel = "DEBUG"
         akka.stdout-loglevel = "DEBUG"
        """.stripMargin)
}

/**
  * This spec is used to test the messages received by the [[ProjectsResponderV1]] actor.
  */
class ProjectsResponderV1Spec extends CoreSpec(ProjectsResponderV1Spec.config) with ImplicitSender {

    private implicit val executionContext = system.dispatcher
    private val timeout = 5.seconds

    private val rootUserProfileV1 = SharedAdminTestData.rootUser

    private val actorUnderTest = TestActorRef[ProjectsResponderV1]
    private val responderManager = system.actorOf(Props(new ResponderManager with LiveActorMaker), name = RESPONDER_MANAGER_ACTOR_NAME)
    private val storeManager = system.actorOf(Props(new StoreManager with LiveActorMaker), name = STORE_MANAGER_ACTOR_NAME)

    val rdfDataObjects = List()

    "Load test data" in {

        storeManager ! ResetTriplestoreContent(rdfDataObjects)
        expectMsg(300.seconds, ResetTriplestoreContentACK())

        responderManager ! LoadOntologiesRequest(SharedAdminTestData.rootUser)
        expectMsg(10.seconds, LoadOntologiesResponse())
    }

    "The ProjectsResponderV1 " when {

        "used to query for project information" should {

            "return information for every project" in {

                actorUnderTest ! ProjectsGetRequestV1(Some(rootUserProfileV1))
                val received = expectMsgType[ProjectsResponseV1](timeout)

                assert(received.projects.contains(SharedAdminTestData.imagesProjectInfo))
                assert(received.projects.contains(SharedAdminTestData.incunabulaProjectInfo))
            }

            "return information about a project identified by IRI" in {

                /* Incunabula project */
                actorUnderTest ! ProjectInfoByIRIGetRequestV1(
                    SharedAdminTestData.incunabulaProjectInfo.id,
                    Some(SharedAdminTestData.rootUser)
                )
                expectMsg(ProjectInfoResponseV1(SharedAdminTestData.incunabulaProjectInfo))

                /* Images project */
                actorUnderTest ! ProjectInfoByIRIGetRequestV1(
                    SharedAdminTestData.imagesProjectInfo.id,
                    Some(SharedAdminTestData.rootUser)
                )
                expectMsg(ProjectInfoResponseV1(SharedAdminTestData.imagesProjectInfo))

                /* 'SystemProject' */
                actorUnderTest ! ProjectInfoByIRIGetRequestV1(
                    SharedAdminTestData.systemProjectInfo.id,
                    Some(SharedAdminTestData.rootUser)
                )
                expectMsg(ProjectInfoResponseV1(SharedAdminTestData.systemProjectInfo))

            }

            "return information about a project identified by shortname" in {
                actorUnderTest ! ProjectInfoByShortnameGetRequestV1(SharedAdminTestData.incunabulaProjectInfo.shortname, Some(rootUserProfileV1))
                expectMsg(ProjectInfoResponseV1(SharedAdminTestData.incunabulaProjectInfo))
            }

            "return 'NotFoundException' when the project IRI is unknown" in {

                actorUnderTest ! ProjectInfoByIRIGetRequestV1("http://data.knora.org/projects/notexisting", Some(rootUserProfileV1))
                expectMsg(Failure(NotFoundException(s"Project 'http://data.knora.org/projects/notexisting' not found")))

            }

            "return 'NotFoundException' when the project shortname unknown " in {
                actorUnderTest ! ProjectInfoByShortnameGetRequestV1("projectwrong", Some(rootUserProfileV1))
                expectMsg(Failure(NotFoundException(s"Project 'projectwrong' not found")))
            }

        }

        "used to modify project information" should {

            val newProjectIri = new MutableTestIri

            "CREATE a project and return the project info if the supplied shortname is unique" in {
                actorUnderTest ! ProjectCreateRequestV1(
                    CreateProjectApiRequestV1(
                        shortname = "newproject",
                        shortcode = None,
                        longname = Some("project longname"),
                        description = Some("project description"),
                        keywords = Some("keywords"),
                        logo = Some("/fu/bar/baz.jpg"),
                        status = true,
                        selfjoin = false
                    ),
                    SharedAdminTestData.rootUser,
                    UUID.randomUUID()
                )
                val received: ProjectOperationResponseV1 = expectMsgType[ProjectOperationResponseV1](timeout)

                received.project_info.shortname should be("newproject")
                received.project_info.longname should contain("project longname")
                received.project_info.description should contain("project description")
                received.project_info.ontologies.isEmpty should be (true)

                newProjectIri.set(received.project_info.id)
                //println(s"newProjectIri: ${newProjectIri.get}")
            }

            "CREATE a project and return the project info if the supplied shortname and shortcode is unique" in {
                actorUnderTest ! ProjectCreateRequestV1(
                    CreateProjectApiRequestV1(
                        shortname = "newproject2",
                        shortcode = Some("1111"),
                        longname = Some("project longname"),
                        description = Some("project description"),
                        keywords = Some("keywords"),
                        logo = Some("/fu/bar/baz.jpg"),
                        status = true,
                        selfjoin = false
                    ),
                    SharedAdminTestData.rootUser,
                    UUID.randomUUID()
                )
                val received: ProjectOperationResponseV1 = expectMsgType[ProjectOperationResponseV1](timeout)

                received.project_info.shortname should be("newproject2")
                received.project_info.shortcode should be(Some("1111"))
                received.project_info.longname should contain("project longname")
                received.project_info.description should contain("project description")
                received.project_info.ontologies.isEmpty should be (true)

                newProjectIri.set(received.project_info.id)
                //println(s"newProjectIri: ${newProjectIri.get}")
            }

            "return a 'DuplicateValueException' during creation if the supplied project shortname is not unique" in {
                actorUnderTest ! ProjectCreateRequestV1(
                    CreateProjectApiRequestV1(
                        shortname = "newproject",
                        shortcode = None,
                        longname = Some("project longname"),
                        description = Some("project description"),
                        keywords = Some("keywords"),
                        logo = Some("/fu/bar/baz.jpg"),
                        status = true,
                        selfjoin = false
                    ),
                    SharedAdminTestData.rootUser,
                    UUID.randomUUID()
                )
                expectMsg(Failure(DuplicateValueException(s"Project with the shortname: 'newproject' already exists")))
            }

            "return a 'DuplicateValueException' during creation if the supplied project shortname is unique but the shortcode is not" in {
                actorUnderTest ! ProjectCreateRequestV1(
                    CreateProjectApiRequestV1(
                        shortname = "newproject3",
                        shortcode = Some("1111"),
                        longname = Some("project longname"),
                        description = Some("project description"),
                        keywords = Some("keywords"),
                        logo = Some("/fu/bar/baz.jpg"),
                        status = true,
                        selfjoin = false
                    ),
                    SharedAdminTestData.rootUser,
                    UUID.randomUUID()
                )
                expectMsg(Failure(DuplicateValueException(s"Project with the shortcode: '1111' already exists")))
            }

            "return 'BadRequestException' if project 'shortname' during creation is missing" in {

                actorUnderTest ! ProjectCreateRequestV1(
                    CreateProjectApiRequestV1(
                        shortname = "",
                        shortcode = None,
                        longname = Some("project longname"),
                        description = Some("project description"),
                        keywords = Some("keywords"),
                        logo = Some("/fu/bar/baz.jpg"),
                        status = true,
                        selfjoin = false
                    ),
                    SharedAdminTestData.rootUser,
                    UUID.randomUUID()
                )
                expectMsg(Failure(BadRequestException("'Shortname' cannot be empty")))
            }

            "UPDATE a project" in {
                actorUnderTest ! ProjectChangeRequestV1(
                    projectIri = newProjectIri.get,
                    changeProjectRequest = ChangeProjectApiRequestV1(
                        shortname = None,
                        longname = Some("updated project longname"),
                        description = Some("updated project description"),
                        keywords = Some("updated keywords"),
                        logo = Some("/fu/bar/baz-updated.jpg"),
                        institution = Some("http://data.knora.org/institutions/dhlab-basel"),
                        status = Some(false),
                        selfjoin = Some(true)
                    ),
                    SharedAdminTestData.rootUser,
                    UUID.randomUUID()
                )
                val received: ProjectOperationResponseV1 = expectMsgType[ProjectOperationResponseV1](timeout)
                received.project_info.longname should be (Some("updated project longname"))
                received.project_info.description should be (Some("updated project description"))
                received.project_info.keywords should be (Some("updated keywords"))
                received.project_info.logo should be (Some("/fu/bar/baz-updated.jpg"))
                received.project_info.institution should be (Some("http://data.knora.org/institutions/dhlab-basel"))
                received.project_info.ontologies.isEmpty should be (true)
                received.project_info.status should be (false)
                received.project_info.selfjoin should be (true)
            }

            "ADD an ontology to the project" in {
                actorUnderTest ! ProjectOntologyAddV1(
                    projectIri = newProjectIri.get,
                    ontologyIri = "http://data.knora.org/ontology/blabla1",
                    apiRequestID = UUID.randomUUID()
                )

                val received: ProjectInfoV1 = expectMsgType[ProjectInfoV1](timeout)
                received.ontologies should be (Seq("http://data.knora.org/ontology/blabla1"))
            }

            "REMOVE an ontology from the project" in {
                actorUnderTest ! ProjectOntologyRemoveV1(
                    projectIri = newProjectIri.get,
                    ontologyIri = "http://data.knora.org/ontology/blabla1",
                    apiRequestID = UUID.randomUUID()
                )

                val received: ProjectInfoV1 = expectMsgType[ProjectInfoV1](timeout)
                received.ontologies.isEmpty should be (true)
            }

            "return 'NotFound' if a not existing project IRI is submitted during update" in {
                actorUnderTest ! ProjectChangeRequestV1(
                    projectIri = "http://data.knora.org/projects/notexisting",
                    changeProjectRequest = ChangeProjectApiRequestV1(longname = Some("new long name")),
                    SharedAdminTestData.rootUser,
                    UUID.randomUUID()
                )
                expectMsg(Failure(NotFoundException(s"Project 'http://data.knora.org/projects/notexisting' not found. Aborting update request.")))
            }

            "return 'BadRequest' if nothing would be changed during the update" in {

                an [BadRequestException] should be thrownBy ChangeProjectApiRequestV1(None, None, None, None, None, None, None, None)

                /*
                actorUnderTest ! ProjectChangeRequestV1(
                    projectIri = "http://data.knora.org/projects/notexisting",
                    changeProjectRequest = ChangeProjectApiRequestV1(None, None, None, None, None, None, None, None, None, None),
                    SharedAdminTestData.rootUser,
                    UUID.randomUUID()
                )
                expectMsg(Failure(BadRequestException("No data would be changed. Aborting update request.")))
                */
            }
        }

        "used to query named graphs" should {
            "return all named graphs" in {
                actorUnderTest ! ProjectsNamedGraphGetV1(SharedAdminTestData.rootUser)

                val received: Seq[NamedGraphV1] = expectMsgType[Seq[NamedGraphV1]]
                received.size should be (7)
            }

            "return all named graphs after adding a new ontology" in {
                actorUnderTest ! ProjectOntologyAddV1(
                    projectIri = IMAGES_PROJECT_IRI,
                    ontologyIri = "http://data.knora.org/ontology/00FF/blabla1",
                    apiRequestID = UUID.randomUUID()
                )
                val received01: ProjectInfoV1 = expectMsgType[ProjectInfoV1](timeout)
                received01.ontologies.size should be (2)

                actorUnderTest ! ProjectsNamedGraphGetV1(SharedAdminTestData.rootUser)

                val received02: Seq[NamedGraphV1] = expectMsgType[Seq[NamedGraphV1]]
                received02.size should be (8)
            }
        }

        "used to query members" should {

            "return all members of a project identified by IRI" in {
                actorUnderTest ! ProjectMembersByIRIGetRequestV1(SharedAdminTestData.imagesProjectInfo.id, SharedAdminTestData.rootUser)
                val received: ProjectMembersGetResponseV1 = expectMsgType[ProjectMembersGetResponseV1](timeout)
                received.members should contain allElementsOf Seq(
                    SharedAdminTestData.imagesUser01.ofType(UserProfileTypeV1.SHORT).userData,
                    SharedAdminTestData.imagesUser02.ofType(UserProfileTypeV1.SHORT).userData,
                    SharedAdminTestData.multiuserUser.ofType(UserProfileTypeV1.SHORT).userData,
                    SharedAdminTestData.imagesReviewerUser.ofType(UserProfileTypeV1.SHORT).userData
                )
                received.userDataV1 should equal (SharedAdminTestData.rootUser.ofType(UserProfileTypeV1.SHORT).userData)
            }

            "return all members of a project identified by shortname" in {
                actorUnderTest ! ProjectMembersByShortnameGetRequestV1(SharedAdminTestData.imagesProjectInfo.shortname, SharedAdminTestData.rootUser)
                val received: ProjectMembersGetResponseV1 = expectMsgType[ProjectMembersGetResponseV1](timeout)
                received.members should contain allElementsOf Seq(
                    SharedAdminTestData.imagesUser01.ofType(UserProfileTypeV1.SHORT).userData,
                    SharedAdminTestData.imagesUser02.ofType(UserProfileTypeV1.SHORT).userData,
                    SharedAdminTestData.multiuserUser.ofType(UserProfileTypeV1.SHORT).userData,
                    SharedAdminTestData.imagesReviewerUser.ofType(UserProfileTypeV1.SHORT).userData
                )
                received.userDataV1 should equal (SharedAdminTestData.rootUser.ofType(UserProfileTypeV1.SHORT).userData)
            }

            "return 'NotFound' when the project IRI is unknown (project membership)" in {
                actorUnderTest ! ProjectMembersByIRIGetRequestV1("http://data.knora.org/projects/notexisting", SharedAdminTestData.rootUser)
                expectMsg(Failure(NotFoundException(s"Project 'http://data.knora.org/projects/notexisting' not found.")))
            }

            "return 'NotFound' when the project shortname is unknown (project membership)" in {
                actorUnderTest ! ProjectMembersByShortnameGetRequestV1("projectwrong", SharedAdminTestData.rootUser)
                expectMsg(Failure(NotFoundException(s"Project 'projectwrong' not found.")))
            }

            "return all project admin members of a project identified by IRI" in {
                actorUnderTest ! ProjectAdminMembersByIRIGetRequestV1(SharedAdminTestData.IMAGES_PROJECT_IRI, SharedAdminTestData.rootUser)
                val received: ProjectAdminMembersGetResponseV1 = expectMsgType[ProjectAdminMembersGetResponseV1](timeout)
                received.members should contain allElementsOf Seq(
                    SharedAdminTestData.imagesUser01.ofType(UserProfileTypeV1.SHORT).userData,
                    SharedAdminTestData.multiuserUser.ofType(UserProfileTypeV1.SHORT).userData
                )
                received.userDataV1 should equal (SharedAdminTestData.rootUser.ofType(UserProfileTypeV1.SHORT).userData)
            }

            "return all project admin members of a project identified by shortname" in {
                actorUnderTest ! ProjectAdminMembersByShortnameGetRequestV1(SharedAdminTestData.imagesProjectInfo.shortname, SharedAdminTestData.rootUser)
                val received: ProjectAdminMembersGetResponseV1 = expectMsgType[ProjectAdminMembersGetResponseV1](timeout)
                received.members should contain allElementsOf Seq(
                    SharedAdminTestData.imagesUser01.ofType(UserProfileTypeV1.SHORT).userData,
                    SharedAdminTestData.multiuserUser.ofType(UserProfileTypeV1.SHORT).userData
                )
                received.userDataV1 should equal (SharedAdminTestData.rootUser.ofType(UserProfileTypeV1.SHORT).userData)
            }

            "return 'NotFound' when the project IRI is unknown (project admin membership)" in {
                actorUnderTest ! ProjectAdminMembersByIRIGetRequestV1("http://data.knora.org/projects/notexisting", SharedAdminTestData.rootUser)
                expectMsg(Failure(NotFoundException(s"Project 'http://data.knora.org/projects/notexisting' not found.")))
            }

            "return 'NotFound' when the project shortname is unknown (project admin membership)" in {
                actorUnderTest ! ProjectAdminMembersByShortnameGetRequestV1("projectwrong", SharedAdminTestData.rootUser)
                expectMsg(Failure(NotFoundException(s"Project 'projectwrong' not found.")))
            }
        }
    }

}
