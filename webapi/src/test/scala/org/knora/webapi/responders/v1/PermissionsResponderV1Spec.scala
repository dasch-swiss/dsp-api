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

package org.knora.webapi.responders.v1

import akka.actor.Props
import akka.actor.Status.Failure
import akka.testkit.{ImplicitSender, TestActorRef}
import com.typesafe.config.{Config, ConfigFactory}
import org.knora.webapi.SharedAdminTestData._
import org.knora.webapi.SharedPermissionsTestData._
import org.knora.webapi._
import org.knora.webapi.messages.v1.responder.ontologymessages.{LoadOntologiesRequest, LoadOntologiesResponse}
import org.knora.webapi.messages.v1.responder.permissionmessages.{DefaultObjectAccessPermissionsStringForPropertyGetV1, DefaultObjectAccessPermissionsStringForResourceClassGetV1, _}
import org.knora.webapi.messages.v1.store.triplestoremessages.{RdfDataObject, ResetTriplestoreContent, ResetTriplestoreContentACK}
import org.knora.webapi.responders._
import org.knora.webapi.responders.v2.ResponderManagerV2
import org.knora.webapi.store.{STORE_MANAGER_ACTOR_NAME, StoreManager}
import org.knora.webapi.util.KnoraIdUtil

import scala.collection.Map
import scala.concurrent.Await
import scala.concurrent.duration._


object PermissionsResponderV1Spec {

    val config: Config = ConfigFactory.parseString(
        """
         akka.loglevel = "DEBUG"
         akka.stdout-loglevel = "DEBUG"
        """.stripMargin)
}


/**
  * This spec is used to test the [[PermissionsResponderV1]] actor.
  */
class PermissionsResponderV1Spec extends CoreSpec(PermissionsResponderV1Spec.config) with ImplicitSender {

    private implicit val executionContext = system.dispatcher
    private val timeout = 5.seconds

    private val knoraIdUtil = new KnoraIdUtil

    private val rootUser = SharedAdminTestData.rootUser
    private val multiuserUserProfileV1 = SharedAdminTestData.multiuserUser

    private val actorUnderTest = TestActorRef[PermissionsResponderV1]
    private val underlyingActorUnderTest = actorUnderTest.underlyingActor
    private val responderManager = system.actorOf(Props(new ResponderManagerV1 with LiveActorMaker), name = RESPONDER_MANAGER_ACTOR_NAME)
    private val responderManager2 = system.actorOf(Props(new ResponderManagerV2 with LiveActorMaker), name = RESPONDER_MANAGER_ACTOR_NAME2)
    private val storeManager = system.actorOf(Props(new StoreManager with LiveActorMaker), name = STORE_MANAGER_ACTOR_NAME)

    private val rdfDataObjects = List(
        RdfDataObject(path = "_test_data/responders.v1.PermissionsResponderV1Spec/additional_permissions-data.ttl", name = "http://www.knora.org/data/permissions"),
        RdfDataObject(path = "_test_data/all_data/incunabula-data.ttl", name = "http://www.knora.org/data/incunabula"),
        RdfDataObject(path = "_test_data/all_data/anything-data.ttl", name = "http://www.knora.org/data/anything")
    )

    "Load test data" in {
        storeManager ! ResetTriplestoreContent(rdfDataObjects)
        expectMsg(300.seconds, ResetTriplestoreContentACK())

        responderManager ! LoadOntologiesRequest(SharedAdminTestData.rootUser)
        expectMsg(10.seconds, LoadOntologiesResponse())
    }


    "The PermissionsResponderV1 " when {

        "queried about the permission profile" should {

            "return the permissions profile (root user)" in {
                actorUnderTest ! PermissionDataGetV1(
                    projectIris = SharedAdminTestData.rootUser.projects_info.keys.toSeq,
                    groupIris = SharedAdminTestData.rootUser.groups,
                    isInProjectAdminGroups = Seq.empty[IRI],
                    isInSystemAdminGroup = true
                )
                expectMsg(SharedAdminTestData.rootUser.permissionData)
            }

            "return the permissions profile (multi group user)" in {
                actorUnderTest ! PermissionDataGetV1(
                    projectIris = SharedAdminTestData.multiuserUser.projects_info.keys.toSeq,
                    groupIris = SharedAdminTestData.multiuserUser.groups,
                    isInProjectAdminGroups = Seq("http://data.knora.org/projects/77275339", "http://data.knora.org/projects/images"),
                    isInSystemAdminGroup = false
                )
                expectMsg(SharedAdminTestData.multiuserUser.permissionData)
            }

            "return the permissions profile (incunabula project admin user)" in {
                actorUnderTest ! PermissionDataGetV1(
                    projectIris = SharedAdminTestData.incunabulaProjectAdminUser.projects_info.keys.toSeq,
                    groupIris = SharedAdminTestData.incunabulaProjectAdminUser.groups,
                    isInProjectAdminGroups = Seq("http://data.knora.org/projects/77275339"),
                    isInSystemAdminGroup = false
                )
                expectMsg(SharedAdminTestData.incunabulaProjectAdminUser.permissionData)
            }

            "return the permissions profile (incunabula creator user)" in {
                actorUnderTest ! PermissionDataGetV1(
                    projectIris = SharedAdminTestData.incunabulaProjectAdminUser.projects_info.keys.toSeq,
                    groupIris = SharedAdminTestData.incunabulaCreatorUser.groups,
                    isInProjectAdminGroups = Seq.empty[IRI],
                    isInSystemAdminGroup = false
                )
                expectMsg(SharedAdminTestData.incunabulaCreatorUser.permissionData)
            }

            "return the permissions profile (incunabula normal project member user)" in {
                actorUnderTest ! PermissionDataGetV1(
                    projectIris = SharedAdminTestData.incunabulaProjectAdminUser.projects_info.keys.toSeq,
                    groupIris = SharedAdminTestData.incunabulaMemberUser.groups,
                    isInProjectAdminGroups = Seq.empty[IRI],
                    isInSystemAdminGroup = false
                )
                expectMsg(SharedAdminTestData.incunabulaMemberUser.permissionData)
            }

            "return the permissions profile (images user 01)" in {
                actorUnderTest ! PermissionDataGetV1(
                    projectIris = SharedAdminTestData.imagesUser01.projects_info.keys.toSeq,
                    groupIris = SharedAdminTestData.imagesUser01.groups,
                    isInProjectAdminGroups = Seq("http://data.knora.org/projects/images"),
                    isInSystemAdminGroup = false
                )
                expectMsg(SharedAdminTestData.imagesUser01.permissionData)
            }

            "return the permissions profile (images-reviewer-user)" in {
                actorUnderTest ! PermissionDataGetV1(
                    projectIris = SharedAdminTestData.imagesReviewerUser.projects_info.keys.toSeq,
                    groupIris = SharedAdminTestData.imagesReviewerUser.groups,
                    isInProjectAdminGroups = Seq.empty[IRI],
                    isInSystemAdminGroup = false
                )
                expectMsg(SharedAdminTestData.imagesReviewerUser.permissionData)
            }

            "return the permissions profile (anything user 01)" in {
                actorUnderTest ! PermissionDataGetV1(
                    projectIris = SharedAdminTestData.anythingUser1.projects_info.keys.toSeq,
                    groupIris = SharedAdminTestData.anythingUser1.groups,
                    isInProjectAdminGroups = Seq.empty[IRI],
                    isInSystemAdminGroup = false
                )
                expectMsg(SharedAdminTestData.anythingUser1.permissionData)
            }

            "return user's administrative permissions (helper method used in queries before)" in {
                val result: Map[IRI, Set[PermissionV1]] = Await.result(underlyingActorUnderTest.userAdministrativePermissionsGetV1(multiuserUserProfileV1.permissionData.groupsPerProject).mapTo[Map[IRI, Set[PermissionV1]]], 1.seconds)
                result should equal(multiuserUserProfileV1.permissionData.administrativePermissionsPerProject)
            }
        }

        "queried about administrative permissions " should {

            "return all AdministrativePermissions for project " in {
                actorUnderTest ! AdministrativePermissionsForProjectGetRequestV1(
                    projectIri = IMAGES_PROJECT_IRI,
                    SharedAdminTestData.rootUser
                )
                expectMsg(AdministrativePermissionsForProjectGetResponseV1(
                    Seq(perm002_a2.p, perm002_a1.p, perm002_a3.p)
                ))
            }

            "return AdministrativePermission for project and group" in {
                actorUnderTest ! AdministrativePermissionForProjectGroupGetRequestV1(
                    projectIri = IMAGES_PROJECT_IRI,
                    groupIri = OntologyConstants.KnoraBase.ProjectMember,
                    SharedAdminTestData.rootUser
                )
                expectMsg(AdministrativePermissionForProjectGroupGetResponseV1(perm002_a1.p))
            }

            "return AdministrativePermission for IRI " in {
                actorUnderTest ! AdministrativePermissionForIriGetRequestV1(
                    administrativePermissionIri = perm002_a1.iri,
                    SharedAdminTestData.rootUser
                )
                expectMsg(AdministrativePermissionForIriGetResponseV1(perm002_a1.p))
            }

        }

        "asked to create an administrative permission" should {

            "fail and return a 'BadRequestException' when project does not exist" ignore {}

            "fail and return a 'BadRequestException' when group does not exist" ignore {}

            "fail and return a 'NotAuthorizedException' whe the user's permission are not high enough (e.g., not member of ProjectAdmin group" ignore {}

            "fail and return a 'DuplicateValueException' when permission for project and group combination already exists" in {
                val iri = knoraIdUtil.makeRandomPermissionIri
                actorUnderTest ! AdministrativePermissionCreateRequestV1(
                    newAdministrativePermissionV1 = NewAdministrativePermissionV1(
                        iri = iri,
                        forProject = IMAGES_PROJECT_IRI,
                        forGroup = OntologyConstants.KnoraBase.ProjectMember,
                        hasOldPermissions = Set.empty[PermissionV1],
                        hasNewPermissions = Set(PermissionV1.ProjectResourceCreateAllPermission)
                    ),
                    userProfileV1 = rootUser
                )
                expectMsg(Failure(DuplicateValueException(s"Permission for project: '${IMAGES_PROJECT_IRI}' and group: '${OntologyConstants.KnoraBase.ProjectMember}' combination already exists.")))
            }

            "create and return an administrative permission " ignore {}
        }

        "queried about object access permissions " should {

            "return object access permissions for a resource" in {
                actorUnderTest ! ObjectAccessPermissionsForResourceGetV1(resourceIri = perm003_o1.iri)
                expectMsg(Some(perm003_o1.p))
            }

            "return object access permissions for a value" in {
                actorUnderTest ! ObjectAccessPermissionsForValueGetV1(valueIri = perm003_o2.iri)
                expectMsg(Some(perm003_o2.p))
            }

        }

        "queried about default object access permissions " should {

            "return all DefaultObjectAccessPermissions for project" in {
                actorUnderTest ! DefaultObjectAccessPermissionsForProjectGetRequestV1(
                    projectIri = IMAGES_PROJECT_IRI,
                    SharedAdminTestData.rootUser
                )
                expectMsg(DefaultObjectAccessPermissionsForProjectGetResponseV1(
                    defaultObjectAccessPermissions = Seq(perm002_d1.p, perm002_d2.p)
                ))
            }

            "return DefaultObjectAccessPermission for IRI" in {
                actorUnderTest ! DefaultObjectAccessPermissionForIriGetRequestV1(
                    defaultObjectAccessPermissionIri = perm002_d1.iri,
                    SharedAdminTestData.rootUser
                )
                expectMsg(DefaultObjectAccessPermissionForIriGetResponseV1(
                    defaultObjectAccessPermission = perm002_d1.p
                ))
            }

            "return DefaultObjectAccessPermission for project and group" in {
                actorUnderTest ! DefaultObjectAccessPermissionGetRequestV1(
                    projectIRI = INCUNABULA_PROJECT_IRI,
                    groupIRI = Some(OntologyConstants.KnoraBase.ProjectMember),
                    resourceClassIRI = None,
                    propertyIRI = None,
                    userProfile = SharedAdminTestData.rootUser
                )
                expectMsg(DefaultObjectAccessPermissionGetResponseV1(
                    defaultObjectAccessPermission = perm003_d1.p
                ))
            }

            "return DefaultObjectAccessPermission for project and resource class ('incunabula:Page')" in {
                actorUnderTest ! DefaultObjectAccessPermissionGetRequestV1(
                    projectIRI = INCUNABULA_PROJECT_IRI,
                    groupIRI = None,
                    resourceClassIRI = Some(INCUNABULA_BOOK_RESOURCE_CLASS),
                    propertyIRI = None,
                    userProfile = SharedAdminTestData.rootUser
                )
                expectMsg(DefaultObjectAccessPermissionGetResponseV1(
                    defaultObjectAccessPermission = perm003_d2.p
                ))
            }

            "return DefaultObjectAccessPermission for project and property ('knora-base:hasStillImageFileValue') (system property)" in {
                actorUnderTest ! DefaultObjectAccessPermissionGetRequestV1(
                    projectIRI = INCUNABULA_PROJECT_IRI,
                    groupIRI = None,
                    resourceClassIRI = None,
                    propertyIRI = Some(OntologyConstants.KnoraBase.HasStillImageFileValue),
                    userProfile = SharedAdminTestData.rootUser
                )
                expectMsg(DefaultObjectAccessPermissionGetResponseV1(
                    defaultObjectAccessPermission = perm001_d3.p
                ))
            }
        }

        "asked to create a default object access permission" should {

            "create a DefaultObjectAccessPermission for project and group" ignore {}

            "create a DefaultObjectAccessPermission for project and resource class" ignore {}

            "create a DefaultObjectAccessPermission for project and property" ignore {}

            "fail and return a 'BadRequestException' when project does not exist" ignore {}

            "fail and return a  'BadRequestException' when resource class does not exist" ignore {}

            "fail and return a  'BadRequestException' when property does not exist" ignore {}

            "fail and return a 'NotAuthorizedException' whe the user's permission are not high enough (e.g., not member of ProjectAdmin group" ignore {

                /* defining project level default object access permissions, so I need to be at least a member of the 'ProjectAdmin' group */

                /* defining system level default object access permissions, so I need to be at least a member of the 'SystemAdmin' group */
            }

            "fail and return a 'DuplicateValueException' when permission for project / group / resource class / property  combination already exists" ignore {}
        }
        "asked to delete a permission object " should {

            "delete an administrative permission " ignore {}

            "delete a default object access permission " ignore {}
        }
        /*
        "asked to create permissions from a template " should {
            "create and return all permissions defined inside the template " ignore {
                /* the default behaviour is to delete all permissions inside a project prior to applying a template */
                actorUnderTest ! TemplatePermissionsCreateRequestV1(
                    projectIri = IMAGES_PROJECT_IRI,
                    permissionsTemplate = PermissionsTemplate.OPEN,
                    rootEmailEnc
                )
                expectMsg(TemplatePermissionsCreateResponseV1(
                    success = true,
                    msg = "ok",
                    administrativePermissions = List(perm001.p, perm003.p),
                    defaultObjectAccessPermissions = List(perm002.p)
                ))
            }
        }
        */

        "asked for default object access permissions 'string'" should {

            "return the default object access permissions 'string' for the 'knora-base:LinkObj' resource class (system resource class)" in {
                actorUnderTest ! DefaultObjectAccessPermissionsStringForResourceClassGetV1(
                    projectIri = INCUNABULA_PROJECT_IRI, resourceClassIri = OntologyConstants.KnoraBase.LinkObj, incunabulaProjectAdminUser.permissionData
                )
                expectMsg(DefaultObjectAccessPermissionsStringResponseV1("M knora-base:ProjectMember|V knora-base:KnownUser,knora-base:UnknownUser"))
            }

            "return the default object access permissions 'string' for the 'knora-base:hasStillImageFileValue' property (system property)" in {
                actorUnderTest ! DefaultObjectAccessPermissionsStringForPropertyGetV1(
                    projectIri = INCUNABULA_PROJECT_IRI,
                    resourceClassIri = OntologyConstants.KnoraBase.StillImageRepresentation,
                    propertyIri = OntologyConstants.KnoraBase.HasStillImageFileValue,
                    incunabulaProjectAdminUser.permissionData
                )
                expectMsg(DefaultObjectAccessPermissionsStringResponseV1("M knora-base:Creator,knora-base:ProjectMember|V knora-base:KnownUser|RV knora-base:UnknownUser"))
            }

            "return the default object access permissions 'string' for the 'incunabula:book' resource class (project resource class)" in {
                actorUnderTest ! DefaultObjectAccessPermissionsStringForResourceClassGetV1(
                    projectIri = INCUNABULA_PROJECT_IRI, resourceClassIri = INCUNABULA_BOOK_RESOURCE_CLASS, incunabulaProjectAdminUser.permissionData
                )
                expectMsg(DefaultObjectAccessPermissionsStringResponseV1("CR knora-base:Creator|M knora-base:ProjectMember|V knora-base:KnownUser|RV knora-base:UnknownUser"))
            }

            "return the default object access permissions 'string' for the 'incunabula:page' resource class (project resource class)" in {
                actorUnderTest ! DefaultObjectAccessPermissionsStringForResourceClassGetV1(
                    projectIri = INCUNABULA_PROJECT_IRI, resourceClassIri = INCUNABULA_PAGE_RESOURCE_CLASS, incunabulaProjectAdminUser.permissionData
                )
                expectMsg(DefaultObjectAccessPermissionsStringResponseV1("CR knora-base:Creator|M knora-base:ProjectMember|V knora-base:KnownUser|RV knora-base:UnknownUser"))
            }

            "return the default object access permissions 'string' for the 'images:jahreszeit' property" in {
                actorUnderTest ! DefaultObjectAccessPermissionsStringForPropertyGetV1(
                    projectIri = IMAGES_PROJECT_IRI,
                    resourceClassIri = "http://www.knora.org/ontology/images#bild",
                    propertyIri = "http://www.knora.org/ontology/images#jahreszeit",
                    imagesUser01.permissionData
                )
                expectMsg(DefaultObjectAccessPermissionsStringResponseV1("CR knora-base:Creator|M knora-base:ProjectMember|V knora-base:KnownUser"))
            }

            "return the default object access permissions 'string' for the 'anything:hasInterval' property" in {
                actorUnderTest ! DefaultObjectAccessPermissionsStringForPropertyGetV1(
                    projectIri = ANYTHING_PROJECT_IRI,
                    resourceClassIri = "http://www.knora.org/ontology/anything#Thing",
                    propertyIri = "http://www.knora.org/ontology/anything#hasInterval",
                    anythingUser1.permissionData
                )
                expectMsg(DefaultObjectAccessPermissionsStringResponseV1("CR knora-base:Creator|M knora-base:ProjectMember|V knora-base:KnownUser|RV knora-base:UnknownUser"))
            }

            "return the default object access permissions 'string' for the 'anything:Thing' class" in {
                actorUnderTest ! DefaultObjectAccessPermissionsStringForResourceClassGetV1(
                    projectIri = ANYTHING_PROJECT_IRI,
                    resourceClassIri = "http://www.knora.org/ontology/anything#Thing",
                    anythingUser1.permissionData
                )
                expectMsg(DefaultObjectAccessPermissionsStringResponseV1("CR knora-base:Creator|M knora-base:ProjectMember|V knora-base:KnownUser|RV knora-base:UnknownUser"))
            }

            "return the default object access permissions 'string' for the 'anything:Thing' class and 'anything:hasText' property" in {
                actorUnderTest ! DefaultObjectAccessPermissionsStringForPropertyGetV1(
                    projectIri = ANYTHING_PROJECT_IRI,
                    resourceClassIri = "http://www.knora.org/ontology/anything#Thing",
                    propertyIri = "http://www.knora.org/ontology/anything#hasText",
                    anythingUser1.permissionData
                )
                expectMsg(DefaultObjectAccessPermissionsStringResponseV1("CR knora-base:Creator"))
            }

            "return the default object access permissions 'string' for the 'images:Bild' class and 'anything:hasText' property" in {
                actorUnderTest ! DefaultObjectAccessPermissionsStringForPropertyGetV1(
                    projectIri = ANYTHING_PROJECT_IRI,
                    resourceClassIri = "http://www.knora.org/ontology/images#bild",
                    propertyIri = "http://www.knora.org/ontology/anything#hasText",
                    anythingUser1.permissionData
                )
                expectMsg(DefaultObjectAccessPermissionsStringResponseV1("CR knora-base:Creator|M knora-base:ProjectMember|V knora-base:KnownUser|RV knora-base:UnknownUser"))
            }

            "return the default object access permissions 'string' for the 'anything:Thing' resource class for the root user (system admin and not member of project)" in {
                actorUnderTest ! DefaultObjectAccessPermissionsStringForResourceClassGetV1(
                    projectIri = ANYTHING_PROJECT_IRI, resourceClassIri = "http://www.knora.org/ontology/anything#Thing", rootUser.permissionData
                )
                expectMsg(DefaultObjectAccessPermissionsStringResponseV1("CR knora-base:Creator|M knora-base:ProjectMember|V knora-base:KnownUser|RV knora-base:UnknownUser"))
            }

            "return a combined and max set of permissions (default object access permissions) defined on the supplied groups (helper method used in queries before)" in {
                val groups = List("http://data.knora.org/groups/images-reviewer", s"${OntologyConstants.KnoraBase.ProjectMember}", s"${OntologyConstants.KnoraBase.ProjectAdmin}")
                val expected = Set(
                        PermissionV1.changeRightsPermission(OntologyConstants.KnoraBase.Creator),
                        PermissionV1.viewPermission(OntologyConstants.KnoraBase.KnownUser),
                        PermissionV1.modifyPermission(OntologyConstants.KnoraBase.ProjectMember)
                    )
                val result: Set[PermissionV1] = Await.result(underlyingActorUnderTest.defaultObjectAccessPermissionsForGroupsGetV1(IMAGES_PROJECT_IRI, groups), 1.seconds)
                result should equal(expected)
            }

        }
    }
}
