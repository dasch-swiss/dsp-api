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
  * extend ResponderADM which messes up testing, as we cannot inject the TestActor system.
  */
package org.knora.webapi.responders.v1

import java.util.UUID

import akka.actor.Props
import akka.actor.Status.Failure
import akka.testkit.{ImplicitSender, TestActorRef}
import com.typesafe.config.{Config, ConfigFactory}
import org.knora.webapi.SharedTestDataADM._
import org.knora.webapi._
import org.knora.webapi.messages.admin.responder.ontologiesmessages.OntologyInfoShortADM
import org.knora.webapi.messages.admin.responder.projectsmessages._
import org.knora.webapi.messages.admin.responder.usersmessages.UserInformationTypeADM
import org.knora.webapi.messages.store.triplestoremessages._
import org.knora.webapi.messages.v1.responder.ontologymessages.{LoadOntologiesRequest, LoadOntologiesResponse}
import org.knora.webapi.responders.admin.ProjectsResponderADM
import org.knora.webapi.responders.{RESPONDER_MANAGER_ACTOR_NAME, ResponderManager}
import org.knora.webapi.store.{STORE_MANAGER_ACTOR_NAME, StoreManager}
import org.knora.webapi.util.{MutableTestIri, SmartIri, StringFormatter}

import scala.concurrent.duration._


object ProjectsResponderADMSpec {

    val config: Config = ConfigFactory.parseString(
        """
         akka.loglevel = "DEBUG"
         akka.stdout-loglevel = "DEBUG"
        """.stripMargin)
}

/**
  * This spec is used to test the messages received by the [[ProjectsResponderADM]] actor.
  */
class ProjectsResponderADMSpec extends CoreSpec(ProjectsResponderADMSpec.config) with ImplicitSender {

    private implicit val executionContext = system.dispatcher
    private implicit val stringFormatter = StringFormatter.getGeneralInstance
    private val timeout = 5.seconds

    private val rootUser= SharedTestDataADM.rootUser

    private val actorUnderTest = TestActorRef[ProjectsResponderADM]
    private val responderManager = system.actorOf(Props(new ResponderManager with LiveActorMaker), name = RESPONDER_MANAGER_ACTOR_NAME)
    private val storeManager = system.actorOf(Props(new StoreManager with LiveActorMaker), name = STORE_MANAGER_ACTOR_NAME)

    val rdfDataObjects = List()

    "Load test data" in {

        storeManager ! ResetTriplestoreContent(rdfDataObjects)
        expectMsg(300.seconds, ResetTriplestoreContentACK())

        responderManager ! LoadOntologiesRequest(SharedTestDataV1.rootUser)
        expectMsg(10.seconds, LoadOntologiesResponse())
    }

    "The ProjectsResponderADM " when {

        "used to query for project information" should {

            "return information for every project" in {

                actorUnderTest ! ProjectsGetRequestADM(rootUser)
                val received = expectMsgType[ProjectsGetResponseADM](timeout)

                assert(received.projects.contains(SharedTestDataADM.imagesProject))
                assert(received.projects.contains(SharedTestDataADM.incunabulaProject))
            }

            "return information about a project identified by IRI" in {

                /* Incunabula project */
                actorUnderTest ! ProjectGetRequestADM(
                    maybeIri = Some(SharedTestDataADM.incunabulaProject.id),
                    maybeShortname = None,
                    maybeShortcode = None,
                    requestingUser = SharedTestDataADM.rootUser
                )
                expectMsg(ProjectGetResponseADM(SharedTestDataADM.incunabulaProject))

                /* Images project */
                actorUnderTest ! ProjectGetRequestADM(
                    maybeIri = Some(SharedTestDataADM.imagesProject.id),
                    maybeShortname = None,
                    maybeShortcode = None,
                    requestingUser = SharedTestDataADM.rootUser
                )
                expectMsg(ProjectGetResponseADM(SharedTestDataADM.imagesProject))

                /* 'SystemProject' */
                actorUnderTest ! ProjectGetRequestADM(
                    maybeIri = Some(SharedTestDataADM.systemProject.id),
                    maybeShortname = None,
                    maybeShortcode = None,
                    requestingUser = SharedTestDataADM.rootUser
                )
                expectMsg(ProjectGetResponseADM(SharedTestDataADM.systemProject))

            }

            "return information about a project identified by shortname" in {
                actorUnderTest ! ProjectGetRequestADM(
                    maybeIri = None,
                    maybeShortname = Some(SharedTestDataADM.incunabulaProject.shortname),
                    maybeShortcode = None,
                    requestingUser = SharedTestDataADM.rootUser
                )
                expectMsg(ProjectGetResponseADM(SharedTestDataADM.incunabulaProject))
            }

            "return 'NotFoundException' when the project IRI is unknown" in {

                actorUnderTest ! ProjectGetRequestADM(
                    maybeIri = Some("http://rdfh.ch/projects/notexisting"),
                    maybeShortname = None,
                    maybeShortcode = None,
                    requestingUser = SharedTestDataADM.rootUser
                )
                expectMsg(Failure(NotFoundException(s"Project 'http://rdfh.ch/projects/notexisting' not found")))

            }

            "return 'NotFoundException' when the project shortname is unknown " in {
                actorUnderTest ! ProjectGetRequestADM(
                    maybeIri = None,
                    maybeShortname = Some("wrongshortname"),
                    maybeShortcode = None,
                    requestingUser = SharedTestDataADM.rootUser
                )
                expectMsg(Failure(NotFoundException(s"Project 'wrongshortname' not found")))
            }

            "return 'NotFoundException' when the project shortcode is unknown " in {
                actorUnderTest ! ProjectGetRequestADM(
                    maybeIri = None,
                    maybeShortname = None,
                    maybeShortcode = Some("wrongshortcode"),
                    requestingUser = SharedTestDataADM.rootUser
                )
                expectMsg(Failure(NotFoundException(s"Project 'wrongshortcode' not found")))
            }

        }

        "used to modify project information" should {

            val newProjectIri = new MutableTestIri

            "CREATE a project and return the project info if the supplied shortname is unique" in {
                actorUnderTest ! ProjectCreateRequestADM(
                    CreateProjectApiRequestADM(
                        shortname = "newproject",
                        shortcode = None,
                        longname = Some("project longname"),
                        description = Some("project description"),
                        keywords = Some("keywords"),
                        logo = Some("/fu/bar/baz.jpg"),
                        status = true,
                        selfjoin = false
                    ),
                    SharedTestDataADM.rootUser,
                    UUID.randomUUID()
                )
                val received: ProjectOperationResponseADM = expectMsgType[ProjectOperationResponseADM](timeout)

                received.project.shortname should be("newproject")
                received.project.longname should contain("project longname")
                received.project.description should contain("project description")
                received.project.ontologies.isEmpty should be (true)

                newProjectIri.set(received.project.id)
                //println(s"newProjectIri: ${newProjectIri.get}")
            }

            "CREATE a project and return the project info if the supplied shortname and shortcode is unique" in {
                actorUnderTest ! ProjectCreateRequestADM(
                    CreateProjectApiRequestADM(
                        shortname = "newproject2",
                        shortcode = Some("1111"),
                        longname = Some("project longname"),
                        description = Some("project description"),
                        keywords = Some("keywords"),
                        logo = Some("/fu/bar/baz.jpg"),
                        status = true,
                        selfjoin = false
                    ),
                    SharedTestDataADM.rootUser,
                    UUID.randomUUID()
                )
                val received: ProjectOperationResponseADM = expectMsgType[ProjectOperationResponseADM](timeout)

                received.project.shortname should be("newproject2")
                received.project.shortcode should be(Some("1111"))
                received.project.longname should contain("project longname")
                received.project.description should contain("project description")
                received.project.ontologies.isEmpty should be (true)

                newProjectIri.set(received.project.id)
                //println(s"newProjectIri: ${newProjectIri.get}")
            }

            "return a 'DuplicateValueException' during creation if the supplied project shortname is not unique" in {
                actorUnderTest ! ProjectCreateRequestADM(
                    CreateProjectApiRequestADM(
                        shortname = "newproject",
                        shortcode = None,
                        longname = Some("project longname"),
                        description = Some("project description"),
                        keywords = Some("keywords"),
                        logo = Some("/fu/bar/baz.jpg"),
                        status = true,
                        selfjoin = false
                    ),
                    SharedTestDataADM.rootUser,
                    UUID.randomUUID()
                )
                expectMsg(Failure(DuplicateValueException(s"Project with the shortname: 'newproject' already exists")))
            }

            "return a 'DuplicateValueException' during creation if the supplied project shortname is unique but the shortcode is not" in {
                actorUnderTest ! ProjectCreateRequestADM(
                    CreateProjectApiRequestADM(
                        shortname = "newproject3",
                        shortcode = Some("1111"),
                        longname = Some("project longname"),
                        description = Some("project description"),
                        keywords = Some("keywords"),
                        logo = Some("/fu/bar/baz.jpg"),
                        status = true,
                        selfjoin = false
                    ),
                    SharedTestDataADM.rootUser,
                    UUID.randomUUID()
                )
                expectMsg(Failure(DuplicateValueException(s"Project with the shortcode: '1111' already exists")))
            }

            "return 'BadRequestException' if project 'shortname' during creation is missing" in {

                actorUnderTest ! ProjectCreateRequestADM(
                    CreateProjectApiRequestADM(
                        shortname = "",
                        shortcode = None,
                        longname = Some("project longname"),
                        description = Some("project description"),
                        keywords = Some("keywords"),
                        logo = Some("/fu/bar/baz.jpg"),
                        status = true,
                        selfjoin = false
                    ),
                    SharedTestDataADM.rootUser,
                    UUID.randomUUID()
                )
                expectMsg(Failure(BadRequestException("'Shortname' cannot be empty")))
            }

            "UPDATE a project" in {
                actorUnderTest ! ProjectChangeRequestADM(
                    projectIri = newProjectIri.get,
                    changeProjectRequest = ChangeProjectApiRequestADM(
                        shortname = None,
                        longname = Some("updated project longname"),
                        description = Some("""updated project description with "quotes" and <html tags>"""),
                        keywords = Some("updated keywords"),
                        logo = Some("/fu/bar/baz-updated.jpg"),
                        institution = Some("http://rdfh.ch/institutions/dhlab-basel"),
                        status = Some(false),
                        selfjoin = Some(true)
                    ),
                    SharedTestDataADM.rootUser,
                    UUID.randomUUID()
                )
                val received: ProjectOperationResponseADM = expectMsgType[ProjectOperationResponseADM](timeout)
                received.project.longname should be (Some("updated project longname"))
                received.project.description should be (Some("""updated project description with "quotes" and <html tags>"""))
                received.project.keywords should be (Some("updated keywords"))
                received.project.logo should be (Some("/fu/bar/baz-updated.jpg"))
                received.project.institution should be (Some("http://rdfh.ch/institutions/dhlab-basel"))
                received.project.ontologies.isEmpty should be (true)
                received.project.status should be (false)
                received.project.selfjoin should be (true)
            }

            "ADD an ontology to the project" in {
                actorUnderTest ! ProjectOntologyAddADM(
                    projectIri = newProjectIri.get,
                    ontologyIri = "http://www.knora.org/ontology/blabla1",
                    requestingUser = KnoraSystemInstances.Users.SystemUser,
                    apiRequestID = UUID.randomUUID()
                )

                val received: ProjectADM = expectMsgType[ProjectADM](timeout)
                received.ontologies should be (Seq(OntologyInfoShortADM(SmartIri("http://www.knora.org/ontology/blabla1"), "blabla1")))
            }

            "REMOVE an ontology from the project" in {
                actorUnderTest ! ProjectOntologyRemoveADM(
                    projectIri = newProjectIri.get,
                    ontologyIri = "http://www.knora.org/ontology/blabla1",
                    requestingUser = KnoraSystemInstances.Users.SystemUser,
                    apiRequestID = UUID.randomUUID()
                )

                val received: ProjectADM = expectMsgType[ProjectADM](timeout)
                received.ontologies.isEmpty should be (true)
            }

            "return 'NotFound' if a not existing project IRI is submitted during update" in {
                actorUnderTest ! ProjectChangeRequestADM(
                    projectIri = "http://rdfh.ch/projects/notexisting",
                    changeProjectRequest = ChangeProjectApiRequestADM(longname = Some("new long name")),
                    SharedTestDataADM.rootUser,
                    UUID.randomUUID()
                )
                expectMsg(Failure(NotFoundException(s"Project 'http://rdfh.ch/projects/notexisting' not found. Aborting update request.")))
            }

            "return 'BadRequest' if nothing would be changed during the update" in {

                an [BadRequestException] should be thrownBy ChangeProjectApiRequestADM(None, None, None, None, None, None, None, None)

                /*
                actorUnderTest ! ProjectChangeRequestADM(
                    projectIri = "http://data.knora.org/projects/notexisting",
                    changeProjectRequest = ChangeProjectApiRequestADM(None, None, None, None, None, None, None, None, None, None),
                    SharedAdminTestData.rootUser,
                    UUID.randomUUID()
                )
                expectMsg(Failure(BadRequestException("No data would be changed. Aborting update request.")))
                */
            }
        }

        /*
        "used to query named graphs" should {
            "return all named graphs" in {
                actorUnderTest ! ProjectsNamedGraphGetADM(SharedTestDataADM.rootUser)

                val received: Seq[NamedGraphADM] = expectMsgType[Seq[NamedGraphADM]]
                received.size should be (7)
            }

            "return all named graphs after adding a new ontology" in {
                actorUnderTest ! ProjectOntologyAddADM(
                    projectIri = IMAGES_PROJECT_IRI,
                    ontologyIri = "http://wwww.knora.org/ontology/00FF/blabla1",
                    requestingUser = KnoraSystemInstances.Users.SystemUser,
                    apiRequestID = UUID.randomUUID()
                )
                val received01: ProjectADM = expectMsgType[ProjectADM](timeout)
                received01.ontologies.size should be (2)

                actorUnderTest ! ProjectsNamedGraphGetADM(SharedTestDataADM.rootUser)

                val received02: Seq[NamedGraphADM] = expectMsgType[Seq[NamedGraphADM]]
                received02.size should be (8)
            }
        }
        */

        "used to query members" should {

            "return all members of a project identified by IRI" in {
                actorUnderTest ! ProjectMembersGetRequestADM(
                    maybeIri = Some(SharedTestDataADM.imagesProject.id),
                    maybeShortname = None,
                    maybeShortcode = None,
                    SharedTestDataADM.rootUser
                )
                val received: ProjectMembersGetResponseADM = expectMsgType[ProjectMembersGetResponseADM](timeout)
                received.members should contain allElementsOf Seq(
                    SharedTestDataADM.imagesUser01.ofType(UserInformationTypeADM.RESTRICTED),
                    SharedTestDataADM.imagesUser02.ofType(UserInformationTypeADM.RESTRICTED),
                    SharedTestDataADM.multiuserUser.ofType(UserInformationTypeADM.RESTRICTED),
                    SharedTestDataADM.imagesReviewerUser.ofType(UserInformationTypeADM.RESTRICTED)
                )
            }

            "return all members of a project identified by shortname" in {
                actorUnderTest ! ProjectMembersGetRequestADM(
                    maybeIri = None,
                    maybeShortname = Some(SharedTestDataADM.imagesProject.shortname),
                    maybeShortcode = None,
                    requestingUser = SharedTestDataADM.rootUser
                )
                val received: ProjectMembersGetResponseADM = expectMsgType[ProjectMembersGetResponseADM](timeout)
                received.members should contain allElementsOf Seq(
                    SharedTestDataADM.imagesUser01.ofType(UserInformationTypeADM.SHORT),
                    SharedTestDataADM.imagesUser02.ofType(UserInformationTypeADM.SHORT),
                    SharedTestDataADM.multiuserUser.ofType(UserInformationTypeADM.SHORT),
                    SharedTestDataADM.imagesReviewerUser.ofType(UserInformationTypeADM.SHORT)
                )
            }

            "return all members of a project identified by shortcode" in {
                actorUnderTest ! ProjectMembersGetRequestADM(
                    maybeIri = None,
                    maybeShortname = None,
                    maybeShortcode = Some(SharedTestDataADM.imagesProject.shortcode.get),
                    requestingUser = SharedTestDataADM.rootUser
                )
                val received: ProjectMembersGetResponseADM = expectMsgType[ProjectMembersGetResponseADM](timeout)
                received.members should contain allElementsOf Seq(
                    SharedTestDataADM.imagesUser01.ofType(UserInformationTypeADM.SHORT),
                    SharedTestDataADM.imagesUser02.ofType(UserInformationTypeADM.SHORT),
                    SharedTestDataADM.multiuserUser.ofType(UserInformationTypeADM.SHORT),
                    SharedTestDataADM.imagesReviewerUser.ofType(UserInformationTypeADM.SHORT)
                )
            }

            "return 'NotFound' when the project IRI is unknown (project membership)" in {
                actorUnderTest ! ProjectMembersGetRequestADM(
                    maybeIri = Some("http://rdfh.ch/projects/notexisting"),
                    maybeShortname = None,
                    maybeShortcode = None,
                    SharedTestDataADM.rootUser
                )
                expectMsg(Failure(NotFoundException(s"Project 'http://rdfh.ch/projects/notexisting' not found.")))
            }

            "return 'NotFound' when the project shortname is unknown (project membership)" in {
                actorUnderTest ! ProjectMembersGetRequestADM(
                    maybeIri = None,
                    maybeShortname = Some("wrongshortname"),
                    maybeShortcode = None,
                    requestingUser = SharedTestDataADM.rootUser
                )
                expectMsg(Failure(NotFoundException(s"Project 'wrongshortname' not found.")))
            }

            "return 'NotFound' when the project shortcode is unknown (project membership)" in {
                actorUnderTest ! ProjectMembersGetRequestADM(
                    maybeIri = None,
                    maybeShortname = None,
                    maybeShortcode = Some("wrongshortcode"),
                    requestingUser = SharedTestDataADM.rootUser
                )
                expectMsg(Failure(NotFoundException(s"Project 'wrongshortcode' not found.")))
            }

            "return all project admin members of a project identified by IRI" in {
                actorUnderTest ! ProjectAdminMembersGetRequestADM(
                    maybeIri = Some(SharedTestDataADM.imagesProject.id),
                    maybeShortname = None,
                    maybeShortcode = None,
                    SharedTestDataADM.rootUser
                )
                val received: ProjectAdminMembersGetResponseADM = expectMsgType[ProjectAdminMembersGetResponseADM](timeout)
                received.members should contain allElementsOf Seq(
                    SharedTestDataADM.imagesUser01.ofType(UserInformationTypeADM.SHORT),
                    SharedTestDataADM.multiuserUser.ofType(UserInformationTypeADM.SHORT)
                )
            }

            "return all project admin members of a project identified by shortname" in {
                actorUnderTest ! ProjectAdminMembersGetRequestADM(
                    maybeIri = None,
                    maybeShortname = Some(SharedTestDataADM.imagesProject.shortname),
                    maybeShortcode = None,
                    requestingUser = SharedTestDataADM.rootUser
                )
                val received: ProjectAdminMembersGetResponseADM = expectMsgType[ProjectAdminMembersGetResponseADM](timeout)
                received.members should contain allElementsOf Seq(
                    SharedTestDataADM.imagesUser01.ofType(UserInformationTypeADM.SHORT),
                    SharedTestDataADM.multiuserUser.ofType(UserInformationTypeADM.SHORT)
                )
            }

            "return all project admin members of a project identified by shortcode" in {
                actorUnderTest ! ProjectAdminMembersGetRequestADM(
                    maybeIri = None,
                    maybeShortname = None,
                    maybeShortcode = Some(SharedTestDataADM.imagesProject.shortcode.get),
                    requestingUser = SharedTestDataADM.rootUser
                )
                val received: ProjectAdminMembersGetResponseADM = expectMsgType[ProjectAdminMembersGetResponseADM](timeout)
                received.members should contain allElementsOf Seq(
                    SharedTestDataADM.imagesUser01.ofType(UserInformationTypeADM.SHORT),
                    SharedTestDataADM.multiuserUser.ofType(UserInformationTypeADM.SHORT)
                )
            }

            "return 'NotFound' when the project IRI is unknown (project admin membership)" in {
                actorUnderTest ! ProjectAdminMembersGetRequestADM(
                    maybeIri = Some("http://rdfh.ch/projects/notexisting"),
                    maybeShortname = None,
                    maybeShortcode = None,
                    SharedTestDataADM.rootUser
                )
                expectMsg(Failure(NotFoundException(s"Project 'http://rdfh.ch/projects/notexisting' not found.")))
            }

            "return 'NotFound' when the project shortname is unknown (project admin membership)" in {
                actorUnderTest ! ProjectAdminMembersGetRequestADM(
                    maybeIri = None,
                    maybeShortname = Some("wrongshortname"),
                    maybeShortcode = None,
                    requestingUser = SharedTestDataADM.rootUser
                )
                expectMsg(Failure(NotFoundException(s"Project 'wrongshortname' not found.")))
            }

            "return 'NotFound' when the project shortcode is unknown (project admin membership)" in {
                actorUnderTest ! ProjectAdminMembersGetRequestADM(
                    maybeIri = None,
                    maybeShortname = None,
                    maybeShortcode = Some("wrongshortcode"),
                    requestingUser = SharedTestDataADM.rootUser
                )
                expectMsg(Failure(NotFoundException(s"Project 'wrongshortcode' not found.")))
            }
        }
    }

}
