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

package org.knora.webapi.responders.admin

import akka.actor.Props
import akka.actor.Status.Failure
import akka.testkit.{ImplicitSender, TestActorRef}
import com.typesafe.config.{Config, ConfigFactory}
import org.knora.webapi.SharedOntologyTestDataADM._
import org.knora.webapi.SharedPermissionsTestData._
import org.knora.webapi.SharedTestDataV1._
import org.knora.webapi._
import org.knora.webapi.messages.admin.responder.permissionsmessages._
import org.knora.webapi.messages.store.triplestoremessages.{RdfDataObject, ResetTriplestoreContent, ResetTriplestoreContentACK}
import org.knora.webapi.messages.v1.responder.ontologymessages.{LoadOntologiesRequest, LoadOntologiesResponse}
import org.knora.webapi.responders._
import org.knora.webapi.store.{STORE_MANAGER_ACTOR_NAME, StoreManager}
import org.knora.webapi.util.KnoraIdUtil

import scala.collection.Map
import scala.concurrent.Await
import scala.concurrent.duration._


object PermissionsResponderADMSpec {

    val config: Config = ConfigFactory.parseString(
        """
         akka.loglevel = "DEBUG"
         akka.stdout-loglevel = "DEBUG"
        """.stripMargin)
}


/**
  * This spec is used to test the [[PermissionsResponderADM]] actor.
  */
class PermissionsResponderADMSpec extends CoreSpec(PermissionsResponderADMSpec.config) with ImplicitSender {

    private implicit val executionContext = system.dispatcher
    private val timeout = 30.seconds

    private val knoraIdUtil = new KnoraIdUtil

    private val rootUser = SharedTestDataADM.rootUser
    private val multiuserUser = SharedTestDataADM.multiuserUser

    private val actorUnderTest = TestActorRef[PermissionsResponderADM]
    private val underlyingActorUnderTest = actorUnderTest.underlyingActor
    private val responderManager = system.actorOf(Props(new ResponderManager with LiveActorMaker), name = RESPONDER_MANAGER_ACTOR_NAME)
    private val storeManager = system.actorOf(Props(new StoreManager with LiveActorMaker), name = STORE_MANAGER_ACTOR_NAME)

    private val rdfDataObjects = List(
        RdfDataObject(path = "_test_data/responders.v1.PermissionsResponderV1Spec/additional_permissions-data.ttl", name = "http://www.knora.org/data/permissions"),
        RdfDataObject(path = "_test_data/all_data/incunabula-data.ttl", name = "http://www.knora.org/data/incunabula"),
        RdfDataObject(path = "_test_data/all_data/anything-data.ttl", name = "http://www.knora.org/data/anything")
    )

    "Load test data" in {
        storeManager ! ResetTriplestoreContent(rdfDataObjects)
        expectMsg(300.seconds, ResetTriplestoreContentACK())

        responderManager ! LoadOntologiesRequest(SharedTestDataV1.rootUser)
        expectMsg(20.seconds, LoadOntologiesResponse())
    }


    "The PermissionsResponderADM" when {

        "queried about the permission profile" should {

            "return the permissions profile (root user)" in {
                actorUnderTest ! PermissionDataGetADM(
                    projectIris = SharedTestDataV1.rootUser.projects_info.keys.toSeq,
                    groupIris = SharedTestDataV1.rootUser.groups,
                    isInProjectAdminGroups = Seq.empty[IRI],
                    isInSystemAdminGroup = true,
                    requestingUser = KnoraSystemInstances.Users.SystemUser
                )
                expectMsg(SharedTestDataV1.rootUser.permissionData)
            }

            "return the permissions profile (multi group user)" in {
                actorUnderTest ! PermissionDataGetADM(
                    projectIris = SharedTestDataV1.multiuserUser.projects_info.keys.toSeq,
                    groupIris = SharedTestDataV1.multiuserUser.groups,
                    isInProjectAdminGroups = Seq(INCUNABULA_PROJECT_IRI, IMAGES_PROJECT_IRI),
                    isInSystemAdminGroup = false,
                    requestingUser = KnoraSystemInstances.Users.SystemUser
                )
                expectMsg(SharedTestDataV1.multiuserUser.permissionData)
            }

            "return the permissions profile (incunabula project admin user)" in {
                actorUnderTest ! PermissionDataGetADM(
                    projectIris = SharedTestDataV1.incunabulaProjectAdminUser.projects_info.keys.toSeq,
                    groupIris = SharedTestDataV1.incunabulaProjectAdminUser.groups,
                    isInProjectAdminGroups = Seq(INCUNABULA_PROJECT_IRI),
                    isInSystemAdminGroup = false,
                    requestingUser = KnoraSystemInstances.Users.SystemUser
                )
                expectMsg(SharedTestDataV1.incunabulaProjectAdminUser.permissionData)
            }

            "return the permissions profile (incunabula creator user)" in {
                actorUnderTest ! PermissionDataGetADM(
                    projectIris = SharedTestDataV1.incunabulaProjectAdminUser.projects_info.keys.toSeq,
                    groupIris = SharedTestDataV1.incunabulaCreatorUser.groups,
                    isInProjectAdminGroups = Seq.empty[IRI],
                    isInSystemAdminGroup = false,
                    requestingUser = KnoraSystemInstances.Users.SystemUser
                )
                expectMsg(SharedTestDataV1.incunabulaCreatorUser.permissionData)
            }

            "return the permissions profile (incunabula normal project member user)" in {
                actorUnderTest ! PermissionDataGetADM(
                    projectIris = SharedTestDataV1.incunabulaProjectAdminUser.projects_info.keys.toSeq,
                    groupIris = SharedTestDataV1.incunabulaMemberUser.groups,
                    isInProjectAdminGroups = Seq.empty[IRI],
                    isInSystemAdminGroup = false,
                    requestingUser = KnoraSystemInstances.Users.SystemUser
                )
                expectMsg(SharedTestDataV1.incunabulaMemberUser.permissionData)
            }

            "return the permissions profile (images user 01)" in {
                actorUnderTest ! PermissionDataGetADM(
                    projectIris = SharedTestDataV1.imagesUser01.projects_info.keys.toSeq,
                    groupIris = SharedTestDataV1.imagesUser01.groups,
                    isInProjectAdminGroups = Seq(IMAGES_PROJECT_IRI),
                    isInSystemAdminGroup = false,
                    requestingUser = KnoraSystemInstances.Users.SystemUser
                )
                expectMsg(SharedTestDataV1.imagesUser01.permissionData)
            }

            "return the permissions profile (images-reviewer-user)" in {
                actorUnderTest ! PermissionDataGetADM(
                    projectIris = SharedTestDataV1.imagesReviewerUser.projects_info.keys.toSeq,
                    groupIris = SharedTestDataV1.imagesReviewerUser.groups,
                    isInProjectAdminGroups = Seq.empty[IRI],
                    isInSystemAdminGroup = false,
                    requestingUser = KnoraSystemInstances.Users.SystemUser
                )
                expectMsg(SharedTestDataV1.imagesReviewerUser.permissionData)
            }

            "return the permissions profile (anything user 01)" in {
                actorUnderTest ! PermissionDataGetADM(
                    projectIris = SharedTestDataV1.anythingUser1.projects_info.keys.toSeq,
                    groupIris = SharedTestDataV1.anythingUser1.groups,
                    isInProjectAdminGroups = Seq.empty[IRI],
                    isInSystemAdminGroup = false,
                    requestingUser = KnoraSystemInstances.Users.SystemUser
                )
                expectMsg(SharedTestDataV1.anythingUser1.permissionData)
            }

            "return user's administrative permissions (helper method used in queries before)" in {
                val result: Map[IRI, Set[PermissionADM]] = Await.result(underlyingActorUnderTest.userAdministrativePermissionsGetADM(multiuserUser.permissions.groupsPerProject).mapTo[Map[IRI, Set[PermissionADM]]], 1.seconds)
                result should equal(multiuserUser.permissions.administrativePermissionsPerProject)
            }
        }

        "queried about administrative permissions " should {

            "return all AdministrativePermissions for project " in {
                actorUnderTest ! AdministrativePermissionsForProjectGetRequestADM(
                    projectIri = IMAGES_PROJECT_IRI,
                    requestingUser = rootUser
                )
                expectMsg(AdministrativePermissionsForProjectGetResponseADM(
                    Seq(perm002_a1.p, perm002_a3.p, perm002_a2.p)
                ))
            }

            "return AdministrativePermission for project and group" in {
                actorUnderTest ! AdministrativePermissionForProjectGroupGetRequestADM(
                    projectIri = IMAGES_PROJECT_IRI,
                    groupIri = OntologyConstants.KnoraBase.ProjectMember,
                    requestingUser = rootUser
                )
                expectMsg(AdministrativePermissionForProjectGroupGetResponseADM(perm002_a1.p))
            }

            "return AdministrativePermission for IRI " in {
                actorUnderTest ! AdministrativePermissionForIriGetRequestADM(
                    administrativePermissionIri = perm002_a1.iri,
                    requestingUser = rootUser
                )
                expectMsg(AdministrativePermissionForIriGetResponseADM(perm002_a1.p))
            }

        }

        "asked to create an administrative permission" should {

            "fail and return a 'BadRequestException' when project does not exist" ignore {}

            "fail and return a 'BadRequestException' when group does not exist" ignore {}

            "fail and return a 'NotAuthorizedException' whe the user's permission are not high enough (e.g., not member of ProjectAdmin group" ignore {}

            "fail and return a 'DuplicateValueException' when permission for project and group combination already exists" in {
                val iri = knoraIdUtil.makeRandomPermissionIri
                actorUnderTest ! AdministrativePermissionCreateRequestADM(
                    newAdministrativePermission = NewAdministrativePermissionADM(
                        iri = iri,
                        forProject = IMAGES_PROJECT_IRI,
                        forGroup = OntologyConstants.KnoraBase.ProjectMember,
                        hasOldPermissions = Set.empty[PermissionADM],
                        hasNewPermissions = Set(PermissionADM.ProjectResourceCreateAllPermission)
                    ),
                    requestingUser = rootUser
                )
                expectMsg(Failure(DuplicateValueException(s"Permission for project: '${IMAGES_PROJECT_IRI}' and group: '${OntologyConstants.KnoraBase.ProjectMember}' combination already exists.")))
            }

            "create and return an administrative permission " ignore {}
        }

        "queried about object access permissions " should {

            "return object access permissions for a resource" in {
                actorUnderTest ! ObjectAccessPermissionsForResourceGetADM(resourceIri = perm003_o1.iri, requestingUser = rootUser)
                expectMsg(Some(perm003_o1.p))
            }

            "return object access permissions for a value" in {
                actorUnderTest ! ObjectAccessPermissionsForValueGetADM(valueIri = perm003_o2.iri, requestingUser = rootUser)
                expectMsg(Some(perm003_o2.p))
            }

        }

        "queried about default object access permissions " should {

            "return all DefaultObjectAccessPermissions for project" in {
                actorUnderTest ! DefaultObjectAccessPermissionsForProjectGetRequestADM(
                    projectIri = IMAGES_PROJECT_IRI,
                    requestingUser = rootUser
                )
                expectMsg(DefaultObjectAccessPermissionsForProjectGetResponseADM(
                    defaultObjectAccessPermissions = Seq(perm002_d1.p, perm002_d2.p)
                ))
            }

            "return DefaultObjectAccessPermission for IRI" in {
                actorUnderTest ! DefaultObjectAccessPermissionForIriGetRequestADM(
                    defaultObjectAccessPermissionIri = perm002_d1.iri,
                    requestingUser = rootUser
                )
                expectMsg(DefaultObjectAccessPermissionForIriGetResponseADM(
                    defaultObjectAccessPermission = perm002_d1.p
                ))
            }

            "return DefaultObjectAccessPermission for project and group" in {
                actorUnderTest ! DefaultObjectAccessPermissionGetRequestADM(
                    projectIRI = INCUNABULA_PROJECT_IRI,
                    groupIRI = Some(OntologyConstants.KnoraBase.ProjectMember),
                    resourceClassIRI = None,
                    propertyIRI = None,
                    requestingUser = rootUser
                )
                expectMsg(DefaultObjectAccessPermissionGetResponseADM(
                    defaultObjectAccessPermission = perm003_d1.p
                ))
            }

            "return DefaultObjectAccessPermission for project and resource class ('incunabula:Page')" in {
                actorUnderTest ! DefaultObjectAccessPermissionGetRequestADM(
                    projectIRI = INCUNABULA_PROJECT_IRI,
                    groupIRI = None,
                    resourceClassIRI = Some(INCUNABULA_BOOK_RESOURCE_CLASS),
                    propertyIRI = None,
                    requestingUser = rootUser
                )
                expectMsg(DefaultObjectAccessPermissionGetResponseADM(
                    defaultObjectAccessPermission = perm003_d2.p
                ))
            }

            "return DefaultObjectAccessPermission for project and property ('knora-base:hasStillImageFileValue') (system property)" in {
                actorUnderTest ! DefaultObjectAccessPermissionGetRequestADM(
                    projectIRI = INCUNABULA_PROJECT_IRI,
                    groupIRI = None,
                    resourceClassIRI = None,
                    propertyIRI = Some(OntologyConstants.KnoraBase.HasStillImageFileValue),
                    requestingUser = rootUser
                )
                expectMsg(DefaultObjectAccessPermissionGetResponseADM(
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
                actorUnderTest ! DefaultObjectAccessPermissionsStringForResourceClassGetADM(
                    projectIri = INCUNABULA_PROJECT_IRI, resourceClassIri = OntologyConstants.KnoraBase.LinkObj,
                    targetUser = SharedTestDataV1.incunabulaProjectAdminUser,
                    requestingUser = KnoraSystemInstances.Users.SystemUser
                )
                expectMsg(DefaultObjectAccessPermissionsStringResponseADM("M knora-base:ProjectMember|V knora-base:KnownUser,knora-base:UnknownUser"))
            }

            "return the default object access permissions 'string' for the 'knora-base:hasStillImageFileValue' property (system property)" in {
                actorUnderTest ! DefaultObjectAccessPermissionsStringForPropertyGetADM(
                    projectIri = INCUNABULA_PROJECT_IRI,
                    resourceClassIri = OntologyConstants.KnoraBase.StillImageRepresentation,
                    propertyIri = OntologyConstants.KnoraBase.HasStillImageFileValue,
                    targetUser = SharedTestDataV1.incunabulaProjectAdminUser,
                    requestingUser = KnoraSystemInstances.Users.SystemUser
                )
                expectMsg(DefaultObjectAccessPermissionsStringResponseADM("M knora-base:Creator,knora-base:ProjectMember|V knora-base:KnownUser|RV knora-base:UnknownUser"))
            }

            "return the default object access permissions 'string' for the 'incunabula:book' resource class (project resource class)" in {
                actorUnderTest ! DefaultObjectAccessPermissionsStringForResourceClassGetADM(
                    projectIri = INCUNABULA_PROJECT_IRI, resourceClassIri = INCUNABULA_BOOK_RESOURCE_CLASS,
                    targetUser = SharedTestDataV1.incunabulaProjectAdminUser,
                    requestingUser = KnoraSystemInstances.Users.SystemUser
                )
                expectMsg(DefaultObjectAccessPermissionsStringResponseADM("CR knora-base:Creator|M knora-base:ProjectMember|V knora-base:KnownUser|RV knora-base:UnknownUser"))
            }

            "return the default object access permissions 'string' for the 'incunabula:page' resource class (project resource class)" in {
                actorUnderTest ! DefaultObjectAccessPermissionsStringForResourceClassGetADM(
                    projectIri = INCUNABULA_PROJECT_IRI, resourceClassIri = INCUNABULA_PAGE_RESOURCE_CLASS,
                    targetUser = SharedTestDataV1.incunabulaProjectAdminUser,
                    requestingUser = KnoraSystemInstances.Users.SystemUser
                )
                expectMsg(DefaultObjectAccessPermissionsStringResponseADM("CR knora-base:Creator|M knora-base:ProjectMember|V knora-base:KnownUser|RV knora-base:UnknownUser"))
            }

            "return the default object access permissions 'string' for the 'images:jahreszeit' property" in {
                actorUnderTest ! DefaultObjectAccessPermissionsStringForPropertyGetADM(
                    projectIri = IMAGES_PROJECT_IRI,
                    resourceClassIri = s"$IMAGES_ONTOLOGY_IRI#bild",
                    propertyIri = s"$IMAGES_ONTOLOGY_IRI#jahreszeit",
                    targetUser = SharedTestDataV1.imagesUser01,
                    requestingUser = KnoraSystemInstances.Users.SystemUser
                )
                expectMsg(DefaultObjectAccessPermissionsStringResponseADM("CR knora-base:Creator|M knora-base:ProjectMember|V knora-base:KnownUser"))
            }

            "return the default object access permissions 'string' for the 'anything:hasInterval' property" in {
                actorUnderTest ! DefaultObjectAccessPermissionsStringForPropertyGetADM(
                    projectIri = ANYTHING_PROJECT_IRI,
                    resourceClassIri = "http://www.knora.org/ontology/anything#Thing",
                    propertyIri = "http://www.knora.org/ontology/anything#hasInterval",
                    targetUser = SharedTestDataV1.anythingUser1,
                    requestingUser = KnoraSystemInstances.Users.SystemUser
                )
                expectMsg(DefaultObjectAccessPermissionsStringResponseADM("CR knora-base:Creator|M knora-base:ProjectMember|V knora-base:KnownUser|RV knora-base:UnknownUser"))
            }

            "return the default object access permissions 'string' for the 'anything:Thing' class" in {
                actorUnderTest ! DefaultObjectAccessPermissionsStringForResourceClassGetADM(
                    projectIri = ANYTHING_PROJECT_IRI,
                    resourceClassIri = "http://www.knora.org/ontology/anything#Thing",
                    targetUser = SharedTestDataV1.anythingUser1,
                    requestingUser = KnoraSystemInstances.Users.SystemUser
                )
                expectMsg(DefaultObjectAccessPermissionsStringResponseADM("CR knora-base:Creator|M knora-base:ProjectMember|V knora-base:KnownUser|RV knora-base:UnknownUser"))
            }

            "return the default object access permissions 'string' for the 'anything:Thing' class and 'anything:hasText' property" in {
                actorUnderTest ! DefaultObjectAccessPermissionsStringForPropertyGetADM(
                    projectIri = ANYTHING_PROJECT_IRI,
                    resourceClassIri = "http://www.knora.org/ontology/anything#Thing",
                    propertyIri = "http://www.knora.org/ontology/anything#hasText",
                    targetUser = SharedTestDataV1.anythingUser1,
                    requestingUser = KnoraSystemInstances.Users.SystemUser
                )
                expectMsg(DefaultObjectAccessPermissionsStringResponseADM("CR knora-base:Creator"))
            }

            "return the default object access permissions 'string' for the 'images:Bild' class and 'anything:hasText' property" in {
                actorUnderTest ! DefaultObjectAccessPermissionsStringForPropertyGetADM(
                    projectIri = ANYTHING_PROJECT_IRI,
                    resourceClassIri = s"$IMAGES_ONTOLOGY_IRI#bild",
                    propertyIri = "http://www.knora.org/ontology/anything#hasText",
                    targetUser = SharedTestDataV1.anythingUser1,
                    requestingUser = KnoraSystemInstances.Users.SystemUser
                )
                expectMsg(DefaultObjectAccessPermissionsStringResponseADM("CR knora-base:Creator|M knora-base:ProjectMember|V knora-base:KnownUser|RV knora-base:UnknownUser"))
            }

            "return the default object access permissions 'string' for the 'anything:Thing' resource class for the root user (system admin and not member of project)" in {
                actorUnderTest ! DefaultObjectAccessPermissionsStringForResourceClassGetADM(
                    projectIri = ANYTHING_PROJECT_IRI, resourceClassIri = "http://www.knora.org/ontology/anything#Thing",
                    targetUser = SharedTestDataV1.rootUser,
                    requestingUser = KnoraSystemInstances.Users.SystemUser
                )
                expectMsg(DefaultObjectAccessPermissionsStringResponseADM("CR knora-base:Creator|M knora-base:ProjectMember|V knora-base:KnownUser|RV knora-base:UnknownUser"))
            }

            "return a combined and max set of permissions (default object access permissions) defined on the supplied groups (helper method used in queries before)" in {
                val groups = List("http://rdfh.ch/groups/images-reviewer", s"${OntologyConstants.KnoraBase.ProjectMember}", s"${OntologyConstants.KnoraBase.ProjectAdmin}")
                val expected = Set(
                        PermissionADM.changeRightsPermission(OntologyConstants.KnoraBase.Creator),
                        PermissionADM.viewPermission(OntologyConstants.KnoraBase.KnownUser),
                        PermissionADM.modifyPermission(OntologyConstants.KnoraBase.ProjectMember)
                    )
                val result: Set[PermissionADM] = Await.result(underlyingActorUnderTest.defaultObjectAccessPermissionsForGroupsGetADM(IMAGES_PROJECT_IRI, groups), 1.seconds)
                result should equal(expected)
            }

        }
    }
}
