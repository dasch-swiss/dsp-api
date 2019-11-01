/*
 * Copyright © 2015-2019 the contributors (see Contributors.md).
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

import java.util.UUID

import akka.actor.Status.Failure
import akka.testkit.ImplicitSender
import com.typesafe.config.{Config, ConfigFactory}
import org.knora.webapi._
import org.knora.webapi.messages.admin.responder.permissionsmessages._
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.util.{CacheUtil, StringFormatter}
import org.scalatest.PrivateMethodTester

import scala.collection.Map
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}


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
class PermissionsResponderADMSpec extends CoreSpec(PermissionsResponderADMSpec.config) with ImplicitSender with PrivateMethodTester {
    private val stringFormatter = StringFormatter.getGeneralInstance

    private val rootUser = SharedTestDataADM.rootUser
    private val multiuserUser = SharedTestDataADM.multiuserUser

    private val responderUnderTest = new PermissionsResponderADM(responderData)

    /* define private method access */
    private val userAdministrativePermissionsGetADM = PrivateMethod[Future[Map[IRI, Set[PermissionADM]]]]('userAdministrativePermissionsGetADM)
    private val defaultObjectAccessPermissionsForGroupsGetADM = PrivateMethod[Future[Set[PermissionADM]]]('defaultObjectAccessPermissionsForGroupsGetADM)

    override lazy val rdfDataObjects = List(
        RdfDataObject(path = "_test_data/responders.admin.PermissionsResponderV1Spec/additional_permissions-data.ttl", name = "http://www.knora.org/data/permissions"),
        RdfDataObject(path = "_test_data/all_data/incunabula-data.ttl", name = "http://www.knora.org/data/0803/incunabula"),
        RdfDataObject(path = "_test_data/all_data/anything-data.ttl", name = "http://www.knora.org/data/0001/anything")
    )

    "The PermissionsResponderADM" when {

        "queried about the permission profile" should {

            "return the permissions profile (root user)" in {
                responderManager ! PermissionDataGetADM(
                    projectIris = SharedTestDataV1.rootUser.projects_info.keys.toSeq,
                    groupIris = SharedTestDataV1.rootUser.groups,
                    isInProjectAdminGroups = Seq.empty[IRI],
                    isInSystemAdminGroup = true,
                    requestingUser = KnoraSystemInstances.Users.SystemUser
                )
                expectMsg(SharedTestDataV1.rootUser.permissionData)
            }

            "return the permissions profile (multi group user)" in {
                responderManager ! PermissionDataGetADM(
                    projectIris = SharedTestDataV1.multiuserUser.projects_info.keys.toSeq,
                    groupIris = SharedTestDataV1.multiuserUser.groups,
                    isInProjectAdminGroups = Seq(
                        SharedTestDataADM.incunabulaProject.id,
                        SharedTestDataADM.imagesProject.id
                    ),
                    isInSystemAdminGroup = false,
                    requestingUser = KnoraSystemInstances.Users.SystemUser
                )
                expectMsg(SharedTestDataV1.multiuserUser.permissionData)
            }

            "return the permissions profile (incunabula project admin user)" in {
                responderManager ! PermissionDataGetADM(
                    projectIris = SharedTestDataV1.incunabulaProjectAdminUser.projects_info.keys.toSeq,
                    groupIris = SharedTestDataV1.incunabulaProjectAdminUser.groups,
                    isInProjectAdminGroups = Seq(
                        SharedTestDataADM.incunabulaProject.id
                    ),
                    isInSystemAdminGroup = false,
                    requestingUser = KnoraSystemInstances.Users.SystemUser
                )
                expectMsg(SharedTestDataV1.incunabulaProjectAdminUser.permissionData)
            }

            "return the permissions profile (incunabula creator user)" in {
                responderManager ! PermissionDataGetADM(
                    projectIris = SharedTestDataV1.incunabulaProjectAdminUser.projects_info.keys.toSeq,
                    groupIris = SharedTestDataV1.incunabulaCreatorUser.groups,
                    isInProjectAdminGroups = Seq.empty[IRI],
                    isInSystemAdminGroup = false,
                    requestingUser = KnoraSystemInstances.Users.SystemUser
                )
                expectMsg(SharedTestDataV1.incunabulaCreatorUser.permissionData)
            }

            "return the permissions profile (incunabula normal project member user)" in {
                responderManager ! PermissionDataGetADM(
                    projectIris = SharedTestDataV1.incunabulaProjectAdminUser.projects_info.keys.toSeq,
                    groupIris = SharedTestDataV1.incunabulaMemberUser.groups,
                    isInProjectAdminGroups = Seq.empty[IRI],
                    isInSystemAdminGroup = false,
                    requestingUser = KnoraSystemInstances.Users.SystemUser
                )
                expectMsg(SharedTestDataV1.incunabulaMemberUser.permissionData)
            }

            "return the permissions profile (images user 01)" in {
                responderManager ! PermissionDataGetADM(
                    projectIris = SharedTestDataV1.imagesUser01.projects_info.keys.toSeq,
                    groupIris = SharedTestDataV1.imagesUser01.groups,
                    isInProjectAdminGroups = Seq(
                        SharedTestDataADM.imagesProject.id
                    ),
                    isInSystemAdminGroup = false,
                    requestingUser = KnoraSystemInstances.Users.SystemUser
                )
                expectMsg(SharedTestDataV1.imagesUser01.permissionData)
            }

            "return the permissions profile (images-reviewer-user)" in {
                responderManager ! PermissionDataGetADM(
                    projectIris = SharedTestDataV1.imagesReviewerUser.projects_info.keys.toSeq,
                    groupIris = SharedTestDataV1.imagesReviewerUser.groups,
                    isInProjectAdminGroups = Seq.empty[IRI],
                    isInSystemAdminGroup = false,
                    requestingUser = KnoraSystemInstances.Users.SystemUser
                )
                expectMsg(SharedTestDataV1.imagesReviewerUser.permissionData)
            }

            "return the permissions profile (anything user 01)" in {
                responderManager ! PermissionDataGetADM(
                    projectIris = SharedTestDataV1.anythingUser1.projects_info.keys.toSeq,
                    groupIris = SharedTestDataV1.anythingUser1.groups,
                    isInProjectAdminGroups = Seq.empty[IRI],
                    isInSystemAdminGroup = false,
                    requestingUser = KnoraSystemInstances.Users.SystemUser
                )
                expectMsg(SharedTestDataV1.anythingUser1.permissionData)
            }

            "return user's administrative permissions (helper method used in queries before)" in {
                val f: Future[Map[IRI, Set[PermissionADM]]] = responderUnderTest invokePrivate userAdministrativePermissionsGetADM(multiuserUser.permissions.groupsPerProject)
                val result: Map[IRI, Set[PermissionADM]] = Await.result(f, 1.seconds)
                result should equal(multiuserUser.permissions.administrativePermissionsPerProject)
            }
        }

        "queried about permissions" should {

            "return all permissions for a project" in {
                responderManager ! PermissionsForProjectGetRequestADM(
                    projectIri = SharedTestDataADM.imagesProject.id,
                    requestingUser = rootUser,
                    apiRequestID = UUID.randomUUID()
                )

                val received = expectMsgType[PermissionsForProjectGetResponseADM](2.seconds)

                assert(received.aps.equals(Seq(
                    SharedPermissionsTestData.perm002_a1.p,
                    SharedPermissionsTestData.perm002_a3.p,
                    SharedPermissionsTestData.perm002_a2.p
                )), "APs not returned as expected.")

                assert(received.doaps.equals(Seq(
                    SharedPermissionsTestData.perm002_d1.p,
                    SharedPermissionsTestData.perm0003_a4.p,
                    SharedPermissionsTestData.perm002_d2.p
                )), "DOAPs not returned as expected.")

            }
        }

        "queried about administrative permissions" should {

            "return all AdministrativePermissions for project " in {
                responderManager ! AdministrativePermissionsForProjectGetRequestADM(
                    projectIri = SharedTestDataADM.imagesProject.id,
                    requestingUser = rootUser,
                    apiRequestID = UUID.randomUUID()
                )
                expectMsg(AdministrativePermissionsForProjectGetResponseADM(
                    Seq(
                        SharedPermissionsTestData.perm002_a1.p,
                        SharedPermissionsTestData.perm002_a3.p,
                        SharedPermissionsTestData.perm002_a2.p
                    )
                ))
            }

            "return AdministrativePermission for project and group" in {
                responderManager ! AdministrativePermissionForProjectGroupGetRequestADM(
                    projectIri = SharedTestDataADM.imagesProject.id,
                    groupIri = OntologyConstants.KnoraAdmin.ProjectMember,
                    requestingUser = rootUser
                )
                expectMsg(
                    AdministrativePermissionForProjectGroupGetResponseADM(
                        SharedPermissionsTestData.perm002_a1.p
                    )
                )
            }

            "return AdministrativePermission for IRI " in {
                responderManager ! AdministrativePermissionForIriGetRequestADM(
                    administrativePermissionIri = SharedPermissionsTestData.perm002_a1.iri,
                    requestingUser = rootUser,
                    apiRequestID = UUID.randomUUID()
                )
                expectMsg(
                    AdministrativePermissionForIriGetResponseADM(
                        SharedPermissionsTestData.perm002_a1.p
                    )
                )
            }

        }

        "asked to create administrative permissions" should {

            "create administrative permission" in {
                responderManager ! AdministrativePermissionCreateRequestADM(
                    createRequest = CreateAdministrativePermissionAPIRequestADM(
                        forProject = SharedTestDataADM.imagesProject.id,
                        forGroup = SharedTestDataADM.imagesReviewerGroup.id,
                        hasPermissions = Set(PermissionADM.ProjectResourceCreateAllPermission)
                    ),
                    requestingUser = rootUser,
                    apiRequestID = UUID.randomUUID()
                )
                val received = expectMsgType[AdministrativePermissionCreateResponseADM](2.seconds)

                assert(
                    received.ap.forProject.equals(
                        SharedTestDataADM.imagesProject.id)
                )
                assert(
                    received.ap.forGroup.equals(
                        SharedTestDataADM.imagesReviewerGroup.id)
                )
                assert(
                    received.ap.hasPermissions.equals(
                        Set(PermissionADM.ProjectResourceCreateAllPermission)))
            }

            "fail and return a 'BadRequestException' when project does not exist" ignore {}

            "fail and return a 'BadRequestException' when group does not exist" ignore {}

            "fail and return a 'NotAuthorizedException' whe the user's permission are not high enough (e.g., not member of ProjectAdmin group" ignore {}

            "fail and return a 'DuplicateValueException' when permission for project and group combination already exists" in {
                responderManager ! AdministrativePermissionCreateRequestADM(
                    createRequest = CreateAdministrativePermissionAPIRequestADM(
                        forProject = SharedTestDataADM.imagesProject.id,
                        forGroup = OntologyConstants.KnoraAdmin.ProjectMember,
                        hasPermissions = Set(PermissionADM.ProjectResourceCreateAllPermission)
                    ),
                    requestingUser = rootUser,
                    apiRequestID = UUID.randomUUID()
                )
                expectMsg(Failure(DuplicateValueException(s"Permission for project: '${SharedTestDataADM.imagesProject.id}' and group: '${OntologyConstants.KnoraAdmin.ProjectMember}' combination already exists.")))
            }

            "create and return an administrative permission " ignore {}
        }

        "queried about object access permissions " should {

            "return object access permissions for a resource" in {
                responderManager ! ObjectAccessPermissionsForResourceGetADM(
                    resourceIri = SharedPermissionsTestData.perm003_o1.iri,
                    requestingUser = rootUser
                )
                expectMsg(Some(SharedPermissionsTestData.perm003_o1.p))
            }

            "return object access permissions for a value" in {
                responderManager ! ObjectAccessPermissionsForValueGetADM(
                    valueIri = SharedPermissionsTestData.perm003_o2.iri,
                    requestingUser = rootUser
                )
                expectMsg(Some(SharedPermissionsTestData.perm003_o2.p))
            }

        }

        "queried about default object access permissions " should {

            "return all DefaultObjectAccessPermissions for project" in {
                responderManager ! DefaultObjectAccessPermissionsForProjectGetRequestADM(
                    projectIri = SharedTestDataADM.imagesProject.id,
                    requestingUser = rootUser,
                    apiRequestID = UUID.randomUUID()
                )

                expectMsg(DefaultObjectAccessPermissionsForProjectGetResponseADM(
                    doaps = Seq(
                        SharedPermissionsTestData.perm002_d1.p,
                        SharedPermissionsTestData.perm0003_a4.p,
                        SharedPermissionsTestData.perm002_d2.p
                    )
                ))
            }

            "return DefaultObjectAccessPermission for IRI" in {
                responderManager ! DefaultObjectAccessPermissionForIriGetRequestADM(
                    defaultObjectAccessPermissionIri = SharedPermissionsTestData.perm002_d1.iri,
                    requestingUser = rootUser,
                    apiRequestID = UUID.randomUUID()
                )
                expectMsg(DefaultObjectAccessPermissionForIriGetResponseADM(
                    doap = SharedPermissionsTestData.perm002_d1.p
                ))
            }

            "return DefaultObjectAccessPermission for project and group" in {
                responderManager ! DefaultObjectAccessPermissionGetRequestADM(
                    projectIRI = SharedTestDataADM.incunabulaProject.id,
                    groupIRI = Some(OntologyConstants.KnoraAdmin.ProjectMember),
                    resourceClassIRI = None,
                    propertyIRI = None,
                    requestingUser = rootUser
                )
                expectMsg(
                    DefaultObjectAccessPermissionGetResponseADM(
                        doap = SharedPermissionsTestData.perm003_d1.p
                    )
                )
            }

            "return DefaultObjectAccessPermission for project and resource class ('incunabula:Page')" in {
                responderManager ! DefaultObjectAccessPermissionGetRequestADM(
                    projectIRI = SharedTestDataADM.incunabulaProject.id,
                    groupIRI = None,
                    resourceClassIRI = Some(SharedOntologyTestDataADM.INCUNABULA_BOOK_RESOURCE_CLASS),
                    propertyIRI = None,
                    requestingUser = rootUser
                )
                expectMsg(
                    DefaultObjectAccessPermissionGetResponseADM(
                        doap = SharedPermissionsTestData.perm003_d2.p
                    )
                )
            }

            "return DefaultObjectAccessPermission for project and property ('knora-base:hasStillImageFileValue') (system property)" in {
                responderManager ! DefaultObjectAccessPermissionGetRequestADM(
                    projectIRI = SharedTestDataADM.incunabulaProject.id,
                    groupIRI = None,
                    resourceClassIRI = None,
                    propertyIRI = Some(OntologyConstants.KnoraBase.HasStillImageFileValue),
                    requestingUser = rootUser
                )
                expectMsg(
                    DefaultObjectAccessPermissionGetResponseADM(
                        doap = SharedPermissionsTestData.perm001_d3.p
                    )
                )
            }

            "cache DefaultObjectAccessPermission" in {
                responderManager ! DefaultObjectAccessPermissionGetRequestADM(
                    projectIRI = SharedTestDataADM.incunabulaProject.id,
                    groupIRI = None,
                    resourceClassIRI = None,
                    propertyIRI = Some(OntologyConstants.KnoraBase.HasStillImageFileValue),
                    requestingUser = rootUser
                )
                expectMsg(
                    DefaultObjectAccessPermissionGetResponseADM(
                        doap = SharedPermissionsTestData.perm001_d3.p
                    )
                )

                val key = SharedPermissionsTestData.perm001_d3.p.cacheKey
                val maybePermission = CacheUtil.get[DefaultObjectAccessPermissionADM](PermissionsResponderADM.PermissionsCacheName, key)
                maybePermission should be (Some(SharedPermissionsTestData.perm001_d3.p))
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
                responderManager ! DefaultObjectAccessPermissionsStringForResourceClassGetADM(
                    projectIri = SharedTestDataADM.incunabulaProject.id,
                    resourceClassIri = OntologyConstants.KnoraBase.LinkObj,
                    targetUser = SharedTestDataADM.incunabulaProjectAdminUser,
                    requestingUser = KnoraSystemInstances.Users.SystemUser
                )
                expectMsg(DefaultObjectAccessPermissionsStringResponseADM("M knora-admin:ProjectMember|V knora-admin:KnownUser,knora-admin:UnknownUser"))
            }

            "return the default object access permissions 'string' for the 'knora-base:hasStillImageFileValue' property (system property)" in {
                responderManager ! DefaultObjectAccessPermissionsStringForPropertyGetADM(
                    projectIri = SharedTestDataADM.incunabulaProject.id,
                    resourceClassIri = OntologyConstants.KnoraBase.StillImageRepresentation,
                    propertyIri = OntologyConstants.KnoraBase.HasStillImageFileValue,
                    targetUser = SharedTestDataADM.incunabulaProjectAdminUser,
                    requestingUser = KnoraSystemInstances.Users.SystemUser
                )
                expectMsg(DefaultObjectAccessPermissionsStringResponseADM("M knora-admin:Creator,knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser"))
            }

            "return the default object access permissions 'string' for the 'incunabula:book' resource class (project resource class)" in {
                responderManager ! DefaultObjectAccessPermissionsStringForResourceClassGetADM(
                    projectIri = SharedTestDataADM.incunabulaProject.id,
                    resourceClassIri = SharedOntologyTestDataADM.INCUNABULA_BOOK_RESOURCE_CLASS,
                    targetUser = SharedTestDataADM.incunabulaProjectAdminUser,
                    requestingUser = KnoraSystemInstances.Users.SystemUser
                )
                expectMsg(DefaultObjectAccessPermissionsStringResponseADM("CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser"))
            }

            "return the default object access permissions 'string' for the 'incunabula:page' resource class (project resource class)" in {
                responderManager ! DefaultObjectAccessPermissionsStringForResourceClassGetADM(
                    projectIri = SharedTestDataADM.incunabulaProject.id,
                    resourceClassIri = SharedOntologyTestDataADM.INCUNABULA_PAGE_RESOURCE_CLASS,
                    targetUser = SharedTestDataADM.incunabulaProjectAdminUser,
                    requestingUser = KnoraSystemInstances.Users.SystemUser
                )
                expectMsg(DefaultObjectAccessPermissionsStringResponseADM("CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser"))
            }

            "return the default object access permissions 'string' for the 'images:jahreszeit' property" in {
                responderManager ! DefaultObjectAccessPermissionsStringForPropertyGetADM(
                    projectIri = SharedTestDataADM.imagesProject.id,
                    resourceClassIri = s"${SharedOntologyTestDataADM.IMAGES_ONTOLOGY_IRI}#bild",
                    propertyIri = s"${SharedOntologyTestDataADM.IMAGES_ONTOLOGY_IRI}#jahreszeit",
                    targetUser = SharedTestDataADM.imagesUser01,
                    requestingUser = KnoraSystemInstances.Users.SystemUser
                )
                expectMsg(DefaultObjectAccessPermissionsStringResponseADM("CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser"))
            }

            "return the default object access permissions 'string' for the 'anything:hasInterval' property" in {
                responderManager ! DefaultObjectAccessPermissionsStringForPropertyGetADM(
                    projectIri = SharedTestDataADM.anythingProject.id,
                    resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing",
                    propertyIri = "http://www.knora.org/ontology/0001/anything#hasInterval",
                    targetUser = SharedTestDataADM.anythingUser1,
                    requestingUser = KnoraSystemInstances.Users.SystemUser
                )
                expectMsg(DefaultObjectAccessPermissionsStringResponseADM("CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser"))
            }

            "return the default object access permissions 'string' for the 'anything:Thing' class" in {
                responderManager ! DefaultObjectAccessPermissionsStringForResourceClassGetADM(
                    projectIri = SharedTestDataADM.anythingProject.id,
                    resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing",
                    targetUser = SharedTestDataADM.anythingUser1,
                    requestingUser = KnoraSystemInstances.Users.SystemUser
                )
                expectMsg(DefaultObjectAccessPermissionsStringResponseADM("CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser"))
            }

            "return the default object access permissions 'string' for the 'anything:Thing' class and 'anything:hasText' property" in {
                responderManager ! DefaultObjectAccessPermissionsStringForPropertyGetADM(
                    projectIri = SharedTestDataADM.anythingProject.id,
                    resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing",
                    propertyIri = "http://www.knora.org/ontology/0001/anything#hasText",
                    targetUser = SharedTestDataADM.anythingUser1,
                    requestingUser = KnoraSystemInstances.Users.SystemUser
                )
                expectMsg(DefaultObjectAccessPermissionsStringResponseADM("CR knora-admin:Creator"))
            }

            "return the default object access permissions 'string' for the 'images:Bild' class and 'anything:hasText' property" in {
                responderManager ! DefaultObjectAccessPermissionsStringForPropertyGetADM(
                    projectIri = SharedTestDataADM.anythingProject.id,
                    resourceClassIri = s"${SharedOntologyTestDataADM.IMAGES_ONTOLOGY_IRI}#bild",
                    propertyIri = "http://www.knora.org/ontology/0001/anything#hasText",
                    targetUser = SharedTestDataADM.anythingUser1,
                    requestingUser = KnoraSystemInstances.Users.SystemUser
                )
                expectMsg(DefaultObjectAccessPermissionsStringResponseADM("CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser"))
            }

            "return the default object access permissions 'string' for the 'anything:Thing' resource class for the root user (system admin and not member of project)" in {
                responderManager ! DefaultObjectAccessPermissionsStringForResourceClassGetADM(
                    projectIri = SharedTestDataADM.anythingProject.id,
                    resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing",
                    targetUser = SharedTestDataADM.rootUser,
                    requestingUser = KnoraSystemInstances.Users.SystemUser
                )
                expectMsg(DefaultObjectAccessPermissionsStringResponseADM("CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser"))
            }

            "return a combined and max set of permissions (default object access permissions) defined on the supplied groups (helper method used in queries before)" in {
                val groups = List("http://rdfh.ch/groups/images-reviewer", s"${OntologyConstants.KnoraAdmin.ProjectMember}", s"${OntologyConstants.KnoraAdmin.ProjectAdmin}")
                val expected = Set(
                        PermissionADM.changeRightsPermission(OntologyConstants.KnoraAdmin.Creator),
                        PermissionADM.viewPermission(OntologyConstants.KnoraAdmin.KnownUser),
                        PermissionADM.modifyPermission(OntologyConstants.KnoraAdmin.ProjectMember)
                    )
                val f: Future[Set[PermissionADM]] = responderUnderTest invokePrivate defaultObjectAccessPermissionsForGroupsGetADM(SharedTestDataADM.imagesProject.id, groups)
                val result: Set[PermissionADM] = Await.result(f, 1.seconds)
                result should equal(expected)
            }

        }
    }
}
