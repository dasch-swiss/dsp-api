/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.admin

import zio.*
import zio.test.*
import zio.test.Assertion.*
import java.util.UUID
import dsp.errors.BadRequestException
import dsp.errors.DuplicateValueException
import dsp.errors.ForbiddenException
import dsp.errors.NotFoundException

import org.knora.webapi.*
import org.knora.webapi.messages.IriConversions.ConvertibleIri
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.permissionsmessages.*
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.sharedtestdata.*
import org.knora.webapi.sharedtestdata.SharedPermissionsTestData.*
import org.knora.webapi.sharedtestdata.SharedTestDataADM.*
import org.knora.webapi.slice.admin.domain.model.GroupIri
import org.knora.webapi.slice.admin.domain.model.Permission
import org.knora.webapi.slice.admin.domain.model.PermissionIri
import org.knora.webapi.slice.admin.domain.service.KnoraGroupRepo
import org.knora.webapi.slice.api.admin.service.PermissionRestService

/**
 * This spec is used to test the [[PermissionsResponder]] actor.
 */
object PermissionsResponderSpec extends E2EZSpec {

  private implicit val sf: StringFormatter = StringFormatter.getInitializedTestInstance

  private val permissionResponder   = ZIO.serviceWithZIO[PermissionsResponder]
  private val permissionRestService = ZIO.serviceWithZIO[PermissionRestService]

  override val rdfDataObjects: List[RdfDataObject] =
    anythingRdfOntologyAndData ++ incunabulaRdfOntologyAndData :+
      RdfDataObject(
        path =
          "test_data/generated_test_data/responders.admin.PermissionsResponderADMSpec/additional_permissions-data.ttl",
        name = "http://www.knora.org/data/permissions",
      )

  val e2eSpec = suite("The PermissionsResponderADM")(
    suite("ask about administrative permissions")(
      test("return all Permission.Administrative for project") {
        for {
          result <- permissionResponder(_.getPermissionsApByProjectIri(imagesProjectIri))
        } yield assertTrue(
          result == AdministrativePermissionsForProjectGetResponseADM(
            Seq(perm002_a1.p, perm002_a3.p, perm002_a2.p),
          ),
        )
      },
      test("return Permission.Administrative for project and group") {
        for {
          result <- permissionRestService(
                      _.getPermissionsApByProjectAndGroupIri(rootUser)(
                        imagesProjectIri,
                        KnoraGroupRepo.builtIn.ProjectMember.id,
                      ),
                    )
        } yield assertTrue(result == AdministrativePermissionGetResponseADM(perm002_a1.p))
      },
    ),
    suite("asked to create an administrative permission")(
      test(
        "fail and return a 'DuplicateValueException' when permission for project and group combination already exists",
      ) {
        assertZIO(
          permissionResponder(
            _.createAdministrativePermission(
              CreateAdministrativePermissionAPIRequestADM(
                forProject = imagesProjectIri,
                forGroup = KnoraGroupRepo.builtIn.ProjectMember.id,
                hasPermissions = Set(PermissionADM.from(Permission.Administrative.ProjectResourceCreateAll)),
              ),
              UUID.randomUUID(),
            ),
          ).exit,
        )(
          fails(
            isSubtype[DuplicateValueException](
              hasMessage(
                equalTo(
                  s"An administrative permission for project: 'http://rdfh.ch/projects/00FF' and " +
                    s"group: 'http://www.knora.org/ontology/knora-admin#ProjectMember' combination already exists.",
                ),
              ),
            ),
          ),
        )
      },
      test("create and return an administrative permission with a custom IRI") {
        val customIri = PermissionIri.unsafeFrom("http://rdfh.ch/permissions/0001/24RD7QcoTKqEJKrDBE885Q")
        for {
          actual <- permissionResponder(
                      _.createAdministrativePermission(
                        CreateAdministrativePermissionAPIRequestADM(
                          id = Some(customIri),
                          forProject = anythingProjectIri,
                          forGroup = thingSearcherGroup.groupIri,
                          hasPermissions = Set(PermissionADM.from(Permission.Administrative.ProjectResourceCreateAll)),
                        ),
                        UUID.randomUUID(),
                      ),
                    )
        } yield assertTrue(
          actual.administrativePermission.iri == customIri.value,
          actual.administrativePermission.forProject == anythingProjectIri.value,
          actual.administrativePermission.forGroup == thingSearcherGroup.id,
        )
      },
      test(
        "create and return an administrative permission even if a permission code given",
      ) {
        val customIri      = PermissionIri.unsafeFrom("http://rdfh.ch/permissions/0001/0pd-VUDeShWNJ2Nq3fGGGQ")
        val hasPermissions = Set(
          PermissionADM(
            name = Permission.Administrative.ProjectResourceCreateRestricted.token,
            additionalInformation = Some("http://www.knora.org/ontology/0001/anything#Thing"),
            permissionCode = Some(8), // will be ignored
          ),
        )
        val expectedHasPermissions = Set(
          PermissionADM(
            name = "ProjectResourceCreateRestrictedPermission",
            additionalInformation = Some("http://www.knora.org/ontology/0001/anything#Thing"),
            None,
          ),
        )
        for {
          actual <- permissionResponder(
                      _.createAdministrativePermission(
                        CreateAdministrativePermissionAPIRequestADM(
                          id = Some(customIri),
                          forProject = anythingProjectIri,
                          forGroup = KnoraGroupRepo.builtIn.KnownUser.id,
                          hasPermissions = hasPermissions,
                        ),
                        UUID.randomUUID(),
                      ),
                    )
        } yield assertTrue(
          actual.administrativePermission.iri == customIri.value,
          actual.administrativePermission.forGroup == KnoraGroupRepo.builtIn.KnownUser.id.value,
          actual.administrativePermission.forProject == anythingProjectIri.value,
          actual.administrativePermission.hasPermissions == expectedHasPermissions,
        )
      },
    ),
    suite("ask to query about default object access permissions")(
      test("return all DefaultObjectAccessPermissions for project") {
        for {
          actual <- permissionResponder(_.getPermissionsDaopByProjectIri(imagesProjectIri))
        } yield assert(actual.defaultObjectAccessPermissions)(
          hasSameElements(Seq(perm002_d2.p, perm0003_a4.p, perm002_d1.p)),
        )
      },
    ),
    suite("ask to create a default object access permission")(
      test("create a DefaultObjectAccessPermission for project and group") {
        for {
          actual <- permissionResponder(
                      _.createDefaultObjectAccessPermission(
                        CreateDefaultObjectAccessPermissionAPIRequestADM(
                          forProject = anythingProjectIri.value,
                          forGroup = Some(thingSearcherGroup.id),
                          hasPermissions = Set(
                            PermissionADM
                              .from(Permission.ObjectAccess.RestrictedView, thingSearcherGroup.id),
                          ),
                        ),
                        UUID.randomUUID(),
                      ),
                    )
        } yield assertTrue(
          actual.defaultObjectAccessPermission.forProject == anythingProjectIri.value,
          actual.defaultObjectAccessPermission.forGroup.contains(thingSearcherGroup.id),
          actual.defaultObjectAccessPermission.hasPermissions.contains(
            PermissionADM.from(Permission.ObjectAccess.RestrictedView, thingSearcherGroup.id),
          ),
        )
      },
      test("create a DefaultObjectAccessPermission for project and group with custom IRI") {
        val customIri = "http://rdfh.ch/permissions/0001/4PnSvolsTEa86KJ2EG76SQ"
        for {
          received <- permissionResponder(
                        _.createDefaultObjectAccessPermission(
                          createRequest = CreateDefaultObjectAccessPermissionAPIRequestADM(
                            id = Some(customIri),
                            forProject = anythingProjectIri.value,
                            forGroup = Some(KnoraGroupRepo.builtIn.UnknownUser.id.value),
                            hasPermissions = Set(
                              PermissionADM
                                .from(
                                  Permission.ObjectAccess.RestrictedView,
                                  KnoraGroupRepo.builtIn.UnknownUser.id.value,
                                ),
                            ),
                          ),
                          UUID.randomUUID(),
                        ),
                      )
        } yield assertTrue(
          received.defaultObjectAccessPermission.iri == customIri,
          received.defaultObjectAccessPermission.forGroup.contains(KnoraGroupRepo.builtIn.UnknownUser.id.value),
          received.defaultObjectAccessPermission.forProject == anythingProjectIri.value,
          received.defaultObjectAccessPermission.hasPermissions
            .contains(
              PermissionADM.from(Permission.ObjectAccess.RestrictedView, KnoraGroupRepo.builtIn.UnknownUser.id.value),
            ),
        )
      },
      test("create a DefaultObjectAccessPermission for project and resource class") {
        for {
          actual <- permissionResponder(
                      _.createDefaultObjectAccessPermission(
                        CreateDefaultObjectAccessPermissionAPIRequestADM(
                          forProject = imagesProjectIri.value,
                          forResourceClass = Some(SharedOntologyTestDataADM.IMAGES_BILD_RESOURCE_CLASS),
                          hasPermissions = Set(
                            PermissionADM
                              .from(Permission.ObjectAccess.Modify, KnoraGroupRepo.builtIn.KnownUser.id.value),
                          ),
                        ),
                        UUID.randomUUID(),
                      ),
                    )
        } yield assertTrue(
          actual.defaultObjectAccessPermission.forProject == imagesProjectIri.value,
          actual.defaultObjectAccessPermission.forResourceClass
            .contains(SharedOntologyTestDataADM.IMAGES_BILD_RESOURCE_CLASS),
          actual.defaultObjectAccessPermission.hasPermissions
            .contains(PermissionADM.from(Permission.ObjectAccess.Modify, KnoraGroupRepo.builtIn.KnownUser.id.value)),
        )
      },
      test("create a DefaultObjectAccessPermission for project and property") {
        for {
          actual <- permissionResponder(
                      _.createDefaultObjectAccessPermission(
                        CreateDefaultObjectAccessPermissionAPIRequestADM(
                          forProject = imagesProjectIri.value,
                          forProperty = Some(SharedOntologyTestDataADM.IMAGES_TITEL_PROPERTY),
                          hasPermissions = Set(
                            PermissionADM
                              .from(Permission.ObjectAccess.ChangeRights, KnoraGroupRepo.builtIn.Creator.id.value),
                          ),
                        ),
                        UUID.randomUUID(),
                      ),
                    )
        } yield assertTrue(
          actual.defaultObjectAccessPermission.forProject == imagesProjectIri.value,
          actual.defaultObjectAccessPermission.forProperty.contains(SharedOntologyTestDataADM.IMAGES_TITEL_PROPERTY),
          actual.defaultObjectAccessPermission.hasPermissions.contains(
            PermissionADM.from(Permission.ObjectAccess.ChangeRights, KnoraGroupRepo.builtIn.Creator.id.value),
          ),
        )
      },
      test(
        "fail and return a 'DuplicateValueException' when a doap permission for project and group combination already exists",
      ) {
        assertZIO(
          permissionResponder(
            _.createDefaultObjectAccessPermission(
              CreateDefaultObjectAccessPermissionAPIRequestADM(
                forProject = incunabulaProjectIri.value,
                forGroup = Some(KnoraGroupRepo.builtIn.ProjectMember.id.value),
                hasPermissions = Set(
                  PermissionADM
                    .from(Permission.ObjectAccess.ChangeRights, KnoraGroupRepo.builtIn.ProjectMember.id.value),
                ),
              ),
              UUID.randomUUID(),
            ),
          ).exit,
        )(
          fails(
            isSubtype[DuplicateValueException](
              hasMessage(
                containsString(
                  s"A default object access permission for project: '$incunabulaProjectIri' and group: " +
                    s"'${KnoraGroupRepo.builtIn.ProjectMember.id.value}' combination already exists. " +
                    s"This permission currently has the scope " +
                    s"'CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser'. " +
                    s"Use its IRI http://rdfh.ch/permissions/00FF",
                ),
              ),
            ),
          ),
        )
      },
      test(
        "fail and return a 'DuplicateValueException' when a doap permission for project and resourceClass combination already exists",
      ) {
        assertZIO(
          permissionResponder(
            _.createDefaultObjectAccessPermission(
              CreateDefaultObjectAccessPermissionAPIRequestADM(
                forProject = incunabulaProjectIri.value,
                forResourceClass = Some(SharedOntologyTestDataADM.INCUNABULA_BOOK_RESOURCE_CLASS),
                hasPermissions = Set(
                  PermissionADM.from(Permission.ObjectAccess.ChangeRights, KnoraGroupRepo.builtIn.Creator.id.value),
                  PermissionADM.from(Permission.ObjectAccess.Modify, KnoraGroupRepo.builtIn.ProjectMember.id.value),
                ),
              ),
              UUID.randomUUID(),
            ),
          ).exit,
        )(
          fails(
            isSubtype[DuplicateValueException](
              hasMessage(
                equalTo(
                  s"A default object access permission for project: '$incunabulaProjectIri' and resourceClass: " +
                    s"'${SharedOntologyTestDataADM.INCUNABULA_BOOK_RESOURCE_CLASS}' combination already exists. " +
                    s"This permission currently has the scope " +
                    s"'CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser'. " +
                    s"Use its IRI http://rdfh.ch/permissions/00FF/sdHG20U6RoiwSu8MeAT1vA to modify it, if necessary.",
                ),
              ),
            ),
          ),
        )
      },
      test(
        "fail and return a 'DuplicateValueException' when a doap permission for project and property combination already exists",
      ) {
        assertZIO(
          permissionResponder(
            _.createDefaultObjectAccessPermission(
              CreateDefaultObjectAccessPermissionAPIRequestADM(
                forProject = incunabulaProjectIri.value,
                forProperty = Some(SharedOntologyTestDataADM.INCUNABULA_PartOf_Property),
                hasPermissions = Set(
                  PermissionADM.from(Permission.ObjectAccess.Modify, KnoraGroupRepo.builtIn.KnownUser.id.value),
                ),
              ),
              UUID.randomUUID(),
            ),
          ).exit,
        )(
          fails(
            isSubtype[DuplicateValueException](
              hasMessage(
                equalTo(
                  s"A default object access permission for project: '$incunabulaProjectIri' and property: " +
                    s"'${SharedOntologyTestDataADM.INCUNABULA_PartOf_Property}' combination already exists. " +
                    s"This permission currently has the scope 'V knora-admin:KnownUser|RV knora-admin:UnknownUser'. " +
                    s"Use its IRI http://rdfh.ch/permissions/00FF/T12XnPXxQ42jBMIf6RK1pg to modify it, if necessary.",
                ),
              ),
            ),
          ),
        )
      },
      test(
        "fail and return a 'DuplicateValueException' when a doap permission for project, resource class, and property combination already exists",
      ) {
        assertZIO(
          permissionResponder(
            _.createDefaultObjectAccessPermission(
              CreateDefaultObjectAccessPermissionAPIRequestADM(
                forProject = incunabulaProjectIri.value,
                forResourceClass = Some(SharedOntologyTestDataADM.INCUNABULA_PAGE_RESOURCE_CLASS),
                forProperty = Some(SharedOntologyTestDataADM.INCUNABULA_PartOf_Property),
                hasPermissions = Set(
                  PermissionADM.from(Permission.ObjectAccess.ChangeRights, KnoraGroupRepo.builtIn.Creator.id.value),
                  PermissionADM.from(Permission.ObjectAccess.Modify, KnoraGroupRepo.builtIn.ProjectMember.id.value),
                ),
              ),
              UUID.randomUUID(),
            ),
          ).exit,
        )(
          fails(
            isSubtype[DuplicateValueException](
              hasMessage(
                equalTo(
                  s"A default object access permission for project: '$incunabulaProjectIri' and resourceClass: " +
                    s"'${SharedOntologyTestDataADM.INCUNABULA_PAGE_RESOURCE_CLASS}' and property: " +
                    s"'${SharedOntologyTestDataADM.INCUNABULA_PartOf_Property}' combination already exists. " +
                    s"This permission currently has the scope 'M knora-admin:ProjectMember'. " +
                    s"Use its IRI http://rdfh.ch/permissions/00FF/5r5B_SJzTuCf8Hwj3MZmgw to modify it, if necessary.",
                ),
              ),
            ),
          ),
        )
      },
      test("create a DefaultObjectAccessPermission for project and property even if name of a permission was missing") {
        val hasPermissions = Set(
          PermissionADM(
            name = "",
            additionalInformation = Some(KnoraGroupRepo.builtIn.UnknownUser.id.value),
            permissionCode = Some(1),
          ),
        )
        for {
          actual <- permissionResponder(
                      _.createDefaultObjectAccessPermission(
                        CreateDefaultObjectAccessPermissionAPIRequestADM(
                          forProject = imagesProjectIri.value,
                          forGroup = Some(KnoraGroupRepo.builtIn.UnknownUser.id.value),
                          hasPermissions = hasPermissions,
                        ),
                        UUID.randomUUID(),
                      ),
                    )
        } yield assertTrue(
          actual.defaultObjectAccessPermission.forProject == imagesProjectIri.value,
          actual.defaultObjectAccessPermission.forGroup.contains(KnoraGroupRepo.builtIn.UnknownUser.id.value),
          actual.defaultObjectAccessPermission.hasPermissions.contains(
            PermissionADM.from(Permission.ObjectAccess.RestrictedView, KnoraGroupRepo.builtIn.UnknownUser.id.value),
          ),
        )
      },
      test(
        "create a DefaultObjectAccessPermission for project and property even if permissionCode of a permission was missing",
      ) {
        val hasPermissions = Set(
          PermissionADM(
            name = Permission.ObjectAccess.Delete.token,
            additionalInformation = Some(KnoraGroupRepo.builtIn.ProjectAdmin.id.value),
            permissionCode = None,
          ),
        )
        val expectedPermissions = Set(
          PermissionADM(
            name = Permission.ObjectAccess.Delete.token,
            additionalInformation = Some(KnoraGroupRepo.builtIn.ProjectAdmin.id.value),
            permissionCode = Some(7),
          ),
        )
        for {
          actual <- permissionResponder(
                      _.createDefaultObjectAccessPermission(
                        CreateDefaultObjectAccessPermissionAPIRequestADM(
                          forProject = imagesProjectIri.value,
                          forGroup = Some(KnoraGroupRepo.builtIn.ProjectAdmin.id.value),
                          hasPermissions = hasPermissions,
                        ),
                        UUID.randomUUID(),
                      ),
                    )
        } yield assertTrue(
          actual.defaultObjectAccessPermission.forProject == imagesProjectIri.value,
          actual.defaultObjectAccessPermission.forGroup.contains(KnoraGroupRepo.builtIn.ProjectAdmin.id.value),
          actual.defaultObjectAccessPermission.hasPermissions == expectedPermissions,
        )
      },
    ),
    suite("ask to get all permissions")(
      test("return all permissions for 'image' project") {
        for {
          actual <- permissionResponder(_.getPermissionsByProjectIri(imagesProjectIri))
        } yield assertTrue(actual.permissions.size == 10)
      },
      test("return all permissions for 'incunabula' project") {
        for {
          actual <- permissionResponder(
                      _.getPermissionsByProjectIri(incunabulaProjectIri),
                    )
        } yield assertTrue(
          actual ==
            PermissionsForProjectGetResponseADM(permissions =
              Set(
                PermissionInfoADM(
                  perm003_a1.iri,
                  OntologyConstants.KnoraAdmin.AdministrativePermission,
                ),
                PermissionInfoADM(
                  perm003_a2.iri,
                  OntologyConstants.KnoraAdmin.AdministrativePermission,
                ),
                PermissionInfoADM(
                  perm003_d1.iri,
                  OntologyConstants.KnoraAdmin.DefaultObjectAccessPermission,
                ),
                PermissionInfoADM(
                  perm003_d2.iri,
                  OntologyConstants.KnoraAdmin.DefaultObjectAccessPermission,
                ),
                PermissionInfoADM(
                  perm003_d3.iri,
                  OntologyConstants.KnoraAdmin.DefaultObjectAccessPermission,
                ),
                PermissionInfoADM(
                  perm003_d4.iri,
                  OntologyConstants.KnoraAdmin.DefaultObjectAccessPermission,
                ),
                PermissionInfoADM(
                  perm003_d5.iri,
                  OntologyConstants.KnoraAdmin.DefaultObjectAccessPermission,
                ),
              ),
            ),
        )
      },
    ),
    suite("ask for default object access permissions 'string'")(
      test(
        "return the default object access permissions 'string' for the 'incunabula:book' resource class (project resource class)",
      ) {
        for {
          actual <- permissionResponder(
                      _.newResourceDefaultObjectAccessPermissions(
                        incunabulaProjectIri,
                        SharedOntologyTestDataADM.INCUNABULA_BOOK_RESOURCE_CLASS.toSmartIri,
                        incunabulaProjectAdminUser,
                      ),
                    )
        } yield assertTrue(
          actual == DefaultObjectAccessPermissionsStringResponseADM(
            "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
          ),
        )
      },
      test(
        "return the default object access permissions 'string' for the 'incunabula:page' resource class (project resource class)",
      ) {
        for {
          actual <- permissionResponder(
                      _.newResourceDefaultObjectAccessPermissions(
                        incunabulaProjectIri,
                        SharedOntologyTestDataADM.INCUNABULA_PAGE_RESOURCE_CLASS.toSmartIri,
                        incunabulaProjectAdminUser,
                      ),
                    )
        } yield assertTrue(
          actual == DefaultObjectAccessPermissionsStringResponseADM(
            "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
          ),
        )
      },
      test("return the default object access permissions 'string' for the 'anything:hasInterval' property") {
        for {
          actual <- permissionResponder(
                      _.newValueDefaultObjectAccessPermissions(
                        anythingProjectIri,
                        "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri,
                        "http://www.knora.org/ontology/0001/anything#hasInterval".toSmartIri,
                        anythingUser2,
                      ),
                    )
        } yield assertTrue(
          actual == DefaultObjectAccessPermissionsStringResponseADM(
            "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
          ),
        )
      },
      test("return the default object access permissions 'string' for the 'anything:Thing' class") {
        for {
          actual <- permissionResponder(
                      _.newResourceDefaultObjectAccessPermissions(
                        anythingProjectIri,
                        "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri,
                        anythingUser2,
                      ),
                    )
        } yield assertTrue(
          actual == DefaultObjectAccessPermissionsStringResponseADM(
            "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
          ),
        )
      },
      test(
        "return the default object access permissions 'string' for the 'anything:Thing' class and 'anything:hasText' property",
      ) {
        for {
          actual <- permissionResponder(
                      _.newValueDefaultObjectAccessPermissions(
                        projectIri = anythingProjectIri,
                        resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri,
                        propertyIri = "http://www.knora.org/ontology/0001/anything#hasText".toSmartIri,
                        targetUser = anythingUser1,
                      ),
                    )
        } yield assertTrue(actual.permissionLiteral == "CR knora-admin:Creator")
      },
      test(
        "return the default object access permissions 'string' for the 'images:Bild' class and 'anything:hasText' property",
      ) {
        for {
          actual <- permissionResponder(
                      _.newValueDefaultObjectAccessPermissions(
                        anythingProjectIri,
                        s"${SharedOntologyTestDataADM.IMAGES_ONTOLOGY_IRI}#bild".toSmartIri,
                        "http://www.knora.org/ontology/0001/anything#hasText".toSmartIri,
                        anythingUser2,
                      ),
                    )
        } yield assertTrue(
          actual == DefaultObjectAccessPermissionsStringResponseADM(
            "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
          ),
        )
      },
      test(
        "return 'BadRequest' if the supplied resourceClass IRI for DefaultObjectAccessPermissionsStringForPropertyGetADM is not valid",
      ) {
        assertZIO(
          permissionResponder(
            _.newValueDefaultObjectAccessPermissions(
              imagesProjectIri,
              customResourceIRI.toSmartIri,
              SharedOntologyTestDataADM.IMAGES_TITEL_PROPERTY.toSmartIri,
              imagesReviewerUser,
            ),
          ).exit,
        )(
          fails(
            isSubtype[BadRequestException](
              hasMessage(
                equalTo(
                  s"Invalid resource class IRI: ${customResourceIRI}",
                ),
              ),
            ),
          ),
        )
      },
      test(
        "return 'BadRequest' if the supplied property IRI for DefaultObjectAccessPermissionsStringForPropertyGetADM is not valid",
      ) {
        assertZIO(
          permissionResponder(
            _.newValueDefaultObjectAccessPermissions(
              imagesProjectIri,
              SharedOntologyTestDataADM.IMAGES_BILD_RESOURCE_CLASS.toSmartIri,
              customValueIRI.toSmartIri,
              imagesReviewerUser,
            ),
          ).exit,
        )(
          fails(
            isSubtype[BadRequestException](
              hasMessage(equalTo(s"Invalid property IRI: ${customValueIRI}")),
            ),
          ),
        )
      },
      test(
        "return 'BadRequest' if the target user of DefaultObjectAccessPermissionsStringForPropertyGetADM is an Anonymous user",
      ) {
        assertZIO(
          permissionResponder(
            _.newValueDefaultObjectAccessPermissions(
              imagesProjectIri,
              SharedOntologyTestDataADM.IMAGES_BILD_RESOURCE_CLASS.toSmartIri,
              SharedOntologyTestDataADM.IMAGES_TITEL_PROPERTY.toSmartIri,
              anonymousUser,
            ),
          ).exit,
        )(fails(isSubtype[BadRequestException](hasMessage(equalTo("Anonymous Users are not allowed.")))))
      },
      test(
        "return the default object access permissions 'string' for the 'anything:Thing' resource class for the root user (system admin and not member of project)",
      ) {
        for {
          actual <- permissionResponder(
                      _.newResourceDefaultObjectAccessPermissions(
                        anythingProjectIri,
                        "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri,
                        rootUser,
                      ),
                    )
        } yield assertTrue(
          actual == DefaultObjectAccessPermissionsStringResponseADM(
            "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
          ),
        )
      },
    ),
    suite("ask to update group of a permission")(
      test("update group of an administrative permission") {
        val permissionIri = PermissionIri.unsafeFrom("http://rdfh.ch/permissions/00FF/buxHAlz8SHuu0FuiLN_tKQ")
        val newGroupIri   = GroupIri.unsafeFrom("http://rdfh.ch/groups/00FF/images-reviewer")
        for {
          actual <- permissionResponder(
                      _.updatePermissionsGroup(permissionIri, newGroupIri, rootUser, UUID.randomUUID()),
                    )
          ap = actual.asInstanceOf[AdministrativePermissionGetResponseADM].administrativePermission
        } yield assertTrue(
          ap.iri == permissionIri.value,
          ap.forGroup == newGroupIri.value,
        )
      },
      test(
        "throw ForbiddenException for PermissionChangeGroupRequestADM if requesting user is not system or project Admin",
      ) {
        val permissionIri = PermissionIri.unsafeFrom("http://rdfh.ch/permissions/00FF/buxHAlz8SHuu0FuiLN_tKQ")
        val newGroupIri   = GroupIri.unsafeFrom("http://rdfh.ch/groups/00FF/images-reviewer")
        assertZIO(
          permissionResponder(
            _.updatePermissionsGroup(permissionIri, newGroupIri, imagesUser02, UUID.randomUUID()),
          ).exit,
        )(
          fails(
            isSubtype[ForbiddenException](
              hasMessage(
                equalTo(
                  s"You are logged in with username 'user02.user', but only a system administrator or project administrator has permissions for this operation.",
                ),
              ),
            ),
          ),
        )
      },
      test("update group of a default object access permission") {
        val permissionIri = PermissionIri.unsafeFrom("http://rdfh.ch/permissions/00FF/Mck2xJDjQ_Oimi_9z4aFaA")
        val newGroupIri   = GroupIri.unsafeFrom("http://rdfh.ch/groups/00FF/images-reviewer")
        for {
          actual <- permissionResponder(
                      _.updatePermissionsGroup(permissionIri, newGroupIri, rootUser, UUID.randomUUID()),
                    )
          doap = actual.asInstanceOf[DefaultObjectAccessPermissionGetResponseADM].defaultObjectAccessPermission
        } yield assertTrue(
          doap.iri == permissionIri.value,
          doap.forGroup.contains(newGroupIri.value),
        )
      },
      test("update group of a default object access permission, resource class must be deleted") {
        val permissionIri = PermissionIri.unsafeFrom("http://rdfh.ch/permissions/00FF/sdHG20U6RoiwSu8MeAT1vA")
        val newGroupIri   = GroupIri.unsafeFrom(KnoraGroupRepo.builtIn.ProjectMember.id.value)
        for {
          actual <- permissionResponder(
                      _.updatePermissionsGroup(permissionIri, newGroupIri, rootUser, UUID.randomUUID()),
                    )
          doap = actual.asInstanceOf[DefaultObjectAccessPermissionGetResponseADM].defaultObjectAccessPermission
        } yield assertTrue(
          doap.iri == permissionIri.value,
          doap.forGroup.contains(KnoraGroupRepo.builtIn.ProjectMember.id.value),
          doap.forResourceClass.isEmpty,
        )
      },
      test("update group of a default object access permission, property must be deleted") {
        val permissionIri = PermissionIri.unsafeFrom("http://rdfh.ch/permissions/00FF/T12XnPXxQ42jBMIf6RK1pg")
        val newGroupIri   = GroupIri.unsafeFrom(KnoraGroupRepo.builtIn.ProjectMember.id.value)
        for {
          actual <- permissionResponder(
                      _.updatePermissionsGroup(permissionIri, newGroupIri, rootUser, UUID.randomUUID()),
                    )
          doap = actual.asInstanceOf[DefaultObjectAccessPermissionGetResponseADM].defaultObjectAccessPermission
        } yield assertTrue(
          doap.iri == permissionIri.value,
          doap.forGroup.contains(KnoraGroupRepo.builtIn.ProjectMember.id.value),
          doap.forProperty.isEmpty,
        )
      },
    ),
    suite("ask to update hasPermissions of a permission")(
      test(
        "throw ForbiddenException for PermissionChangeHasPermissionsRequestADM if requesting user is not system or project Admin",
      ) {
        val permissionIri  = "http://rdfh.ch/permissions/00FF/buxHAlz8SHuu0FuiLN_tKQ"
        val hasPermissions = NonEmptyChunk(PermissionADM.from(Permission.Administrative.ProjectResourceCreateAll))

        assertZIO(
          permissionResponder(
            _.updatePermissionHasPermissions(
              PermissionIri.unsafeFrom(permissionIri),
              hasPermissions,
              imagesUser02,
              UUID.randomUUID(),
            ),
          ).exit,
        )(
          fails(
            isSubtype[ForbiddenException](
              hasMessage(
                equalTo(
                  s"You are logged in with username 'user02.user', but only a system administrator or project administrator has permissions for this operation.",
                ),
              ),
            ),
          ),
        )
      },
      test("update hasPermissions of an administrative permission") {
        val permissionIri  = "http://rdfh.ch/permissions/00FF/buxHAlz8SHuu0FuiLN_tKQ"
        val hasPermissions = NonEmptyChunk(PermissionADM.from(Permission.Administrative.ProjectResourceCreateAll))
        for {
          actual <- permissionResponder(
                      _.updatePermissionHasPermissions(
                        PermissionIri.unsafeFrom(permissionIri),
                        hasPermissions,
                        rootUser,
                        UUID.randomUUID(),
                      ),
                    )
          ap = actual.asInstanceOf[AdministrativePermissionGetResponseADM].administrativePermission
        } yield assertTrue(
          ap.iri == permissionIri,
          ap.hasPermissions.size == 1,
          ap.hasPermissions == hasPermissions.toSet,
        )
      },
      test(
        "ignore irrelevant parameters given in ChangePermissionHasPermissionsApiRequestADM for an administrative permission",
      ) {
        val permissionIri  = "http://rdfh.ch/permissions/00FF/buxHAlz8SHuu0FuiLN_tKQ"
        val hasPermissions = NonEmptyChunk(
          PermissionADM(
            name = Permission.Administrative.ProjectAdminAll.token,
            additionalInformation = Some("aIRI"),
            permissionCode = Some(1),
          ),
        )
        for {
          actual <- permissionResponder(
                      _.updatePermissionHasPermissions(
                        PermissionIri.unsafeFrom(permissionIri),
                        hasPermissions,
                        rootUser,
                        UUID.randomUUID(),
                      ),
                    )
          ap                       = actual.asInstanceOf[AdministrativePermissionGetResponseADM].administrativePermission
          expectedSetOfPermissions = Set(PermissionADM.from(Permission.Administrative.ProjectAdminAll))
        } yield assertTrue(
          ap.iri == permissionIri,
          ap.hasPermissions.size == 1,
          ap.hasPermissions == expectedSetOfPermissions,
        )
      },
      test("update hasPermissions of a default object access permission") {
        val permissionIri  = "http://rdfh.ch/permissions/00FF/Q3OMWyFqStGYK8EXmC7KhQ"
        val hasPermissions = NonEmptyChunk(
          PermissionADM.from(Permission.ObjectAccess.ChangeRights, KnoraGroupRepo.builtIn.Creator.id.value),
          PermissionADM.from(Permission.ObjectAccess.Modify, KnoraGroupRepo.builtIn.ProjectMember.id.value),
        )

        for {
          actual <- permissionResponder(
                      _.updatePermissionHasPermissions(
                        PermissionIri.unsafeFrom(permissionIri),
                        hasPermissions,
                        rootUser,
                        UUID.randomUUID(),
                      ),
                    )
          doap = actual.asInstanceOf[DefaultObjectAccessPermissionGetResponseADM].defaultObjectAccessPermission
        } yield assertTrue(
          doap.iri == permissionIri,
          doap.hasPermissions.size == 2,
          doap.hasPermissions == hasPermissions.toSet,
        )
      },
      test(
        "add missing name of the permission, if permissionCode of permission was given in hasPermissions of a default object access permission",
      ) {
        val permissionIri  = "http://rdfh.ch/permissions/00FF/Q3OMWyFqStGYK8EXmC7KhQ"
        val hasPermissions = NonEmptyChunk(
          PermissionADM(
            name = "",
            additionalInformation = Some(KnoraGroupRepo.builtIn.Creator.id.value),
            permissionCode = Some(8),
          ),
        )

        val expectedHasPermissions = Set(
          PermissionADM(
            name = Permission.ObjectAccess.ChangeRights.token,
            additionalInformation = Some(KnoraGroupRepo.builtIn.Creator.id.value),
            permissionCode = Some(8),
          ),
        )

        for {
          actual <- permissionResponder(
                      _.updatePermissionHasPermissions(
                        PermissionIri.unsafeFrom(permissionIri),
                        hasPermissions,
                        rootUser,
                        UUID.randomUUID(),
                      ),
                    )
          doap = actual.asInstanceOf[DefaultObjectAccessPermissionGetResponseADM].defaultObjectAccessPermission
        } yield assertTrue(
          doap.iri == permissionIri,
          doap.hasPermissions == expectedHasPermissions,
        )
      },
      test(
        "add missing permissionCode of the permission, if name of permission was given in hasPermissions of a default object access permission",
      ) {
        val permissionIri  = "http://rdfh.ch/permissions/00FF/Q3OMWyFqStGYK8EXmC7KhQ"
        val hasPermissions = NonEmptyChunk(
          PermissionADM(
            name = Permission.ObjectAccess.Delete.token,
            additionalInformation = Some(KnoraGroupRepo.builtIn.ProjectAdmin.id.value),
            permissionCode = None,
          ),
        )

        val expectedHasPermissions = Set(
          PermissionADM(
            name = Permission.ObjectAccess.Delete.token,
            additionalInformation = Some(KnoraGroupRepo.builtIn.ProjectAdmin.id.value),
            permissionCode = Some(7),
          ),
        )
        for {
          actual <- permissionResponder(
                      _.updatePermissionHasPermissions(
                        PermissionIri.unsafeFrom(permissionIri),
                        hasPermissions,
                        rootUser,
                        UUID.randomUUID(),
                      ),
                    )
          doap = actual.asInstanceOf[DefaultObjectAccessPermissionGetResponseADM].defaultObjectAccessPermission
        } yield assertTrue(
          doap.iri == permissionIri,
          doap.hasPermissions == expectedHasPermissions,
        )
      },
      test(
        "not update hasPermissions of a default object access permission, if both name and project code of a permission were missing",
      ) {
        val permissionIri  = "http://rdfh.ch/permissions/00FF/Q3OMWyFqStGYK8EXmC7KhQ"
        val code           = 1
        val name           = Permission.ObjectAccess.Delete.token
        val hasPermissions = NonEmptyChunk(
          PermissionADM(
            name = name,
            additionalInformation = Some(KnoraGroupRepo.builtIn.ProjectAdmin.id.value),
            permissionCode = Some(code),
          ),
        )
        assertZIO(
          permissionResponder(
            _.updatePermissionHasPermissions(
              PermissionIri.unsafeFrom(permissionIri),
              hasPermissions,
              rootUser,
              UUID.randomUUID(),
            ),
          ).exit,
        )(
          fails(
            isSubtype[BadRequestException](
              hasMessage(
                equalTo(
                  s"Given permission code '$code' and permission name '$name' are not consistent.",
                ),
              ),
            ),
          ),
        )
      },
      test(
        "not update hasPermissions of a default object access permission, if an invalid name was given for a permission",
      ) {
        val permissionIri  = "http://rdfh.ch/permissions/00FF/Q3OMWyFqStGYK8EXmC7KhQ"
        val name           = "invalidName"
        val hasPermissions = NonEmptyChunk(
          PermissionADM(
            name = name,
            additionalInformation = Some(KnoraGroupRepo.builtIn.ProjectAdmin.id.value),
            permissionCode = None,
          ),
        )

        assertZIO(
          permissionResponder(
            _.updatePermissionHasPermissions(
              PermissionIri.unsafeFrom(permissionIri),
              hasPermissions,
              rootUser,
              UUID.randomUUID(),
            ),
          ).exit,
        )(
          fails(
            isSubtype[BadRequestException](
              hasMessage(
                equalTo(
                  s"Invalid permission token '$name', it should be one of RV, M, V, CR, D",
                ),
              ),
            ),
          ),
        )
      },
      test(
        "not update hasPermissions of a default object access permission, if an invalid code was given for a permission",
      ) {
        val permissionIri  = "http://rdfh.ch/permissions/00FF/Q3OMWyFqStGYK8EXmC7KhQ"
        val code           = 10
        val hasPermissions = NonEmptyChunk(
          PermissionADM(
            name = Permission.ObjectAccess.Delete.token,
            additionalInformation = Some(KnoraGroupRepo.builtIn.ProjectAdmin.id.value),
            permissionCode = Some(code),
          ),
        )

        assertZIO(
          permissionResponder(
            _.updatePermissionHasPermissions(
              PermissionIri.unsafeFrom(permissionIri),
              hasPermissions,
              rootUser,
              UUID.randomUUID(),
            ),
          ).exit,
        )(
          fails(
            isSubtype[BadRequestException](
              hasMessage(
                equalTo(
                  s"Invalid permission code '$code', it should be one of 1, 6, 2, 7, 8",
                ),
              ),
            ),
          ),
        )
      },
      test(
        "not update hasPermissions of a default object access permission, if given name and project code are not consistent",
      ) {
        val permissionIri  = "http://rdfh.ch/permissions/00FF/Q3OMWyFqStGYK8EXmC7KhQ"
        val hasPermissions = NonEmptyChunk(
          PermissionADM(
            name = "",
            additionalInformation = Some(KnoraGroupRepo.builtIn.ProjectAdmin.id.value),
            permissionCode = None,
          ),
        )

        assertZIO(
          permissionResponder(
            _.updatePermissionHasPermissions(
              PermissionIri.unsafeFrom(permissionIri),
              hasPermissions,
              rootUser,
              UUID.randomUUID(),
            ),
          ).exit,
        )(
          fails(
            isSubtype[BadRequestException](
              hasMessage(
                equalTo(
                  "Invalid permission token '', it should be one of RV, M, V, CR, D",
                ),
              ),
            ),
          ),
        )
      },
    ),
    suite("ask to update resource class of a permission")(
      test("update resource class of a default object access permission") {
        val permissionIri    = "http://rdfh.ch/permissions/00FF/sdHG20U6RoiwSu8MeAT1vA"
        val resourceClassIri = SharedOntologyTestDataADM.INCUNABULA_PAGE_RESOURCE_CLASS

        for {
          actual <- permissionResponder(
                      _.updatePermissionResourceClass(
                        PermissionIri.unsafeFrom(permissionIri),
                        ChangePermissionResourceClassApiRequestADM(resourceClassIri),
                        UUID.randomUUID(),
                      ),
                    )
          doap = actual.defaultObjectAccessPermission
        } yield assertTrue(
          doap.iri == permissionIri,
          doap.forResourceClass.contains(resourceClassIri),
        )
      },
      test("update resource class of a default object access permission, and delete group") {
        val permissionIri    = "http://rdfh.ch/permissions/00FF/Q3OMWyFqStGYK8EXmC7KhQ"
        val resourceClassIri = SharedOntologyTestDataADM.INCUNABULA_BOOK_RESOURCE_CLASS
        for {
          actual <- permissionResponder(
                      _.updatePermissionResourceClass(
                        PermissionIri.unsafeFrom(permissionIri),
                        ChangePermissionResourceClassApiRequestADM(resourceClassIri),
                        UUID.randomUUID(),
                      ),
                    )
          doap = actual.defaultObjectAccessPermission
        } yield assertTrue(
          doap.iri == permissionIri,
          doap.forResourceClass.contains(resourceClassIri),
          doap.forGroup.isEmpty,
        )
      },
      test("not update resource class of an administrative permission") {
        val permissionIri    = "http://rdfh.ch/permissions/00FF/OySsjGn8QSqIpXUiSYnSSQ"
        val resourceClassIri = SharedOntologyTestDataADM.INCUNABULA_BOOK_RESOURCE_CLASS
        assertZIO(
          permissionResponder(
            _.updatePermissionResourceClass(
              PermissionIri.unsafeFrom(permissionIri),
              ChangePermissionResourceClassApiRequestADM(resourceClassIri),
              UUID.randomUUID(),
            ),
          ).exit,
        )(
          fails(isSubtype[NotFoundException](hasMessage(equalTo(s"DOAP $permissionIri not found.")))),
        )
      },
    ),
    suite("ask to update property of a permission")(
      test("not update property of an administrative permission") {
        val permissionIri = "http://rdfh.ch/permissions/00FF/OySsjGn8QSqIpXUiSYnSSQ"
        val propertyIri   = SharedOntologyTestDataADM.IMAGES_TITEL_PROPERTY

        assertZIO(
          permissionResponder(
            _.updatePermissionProperty(
              PermissionIri.unsafeFrom(permissionIri),
              ChangePermissionPropertyApiRequestADM(propertyIri),
              UUID.randomUUID(),
            ),
          ).exit,
        )(
          fails(isSubtype[NotFoundException](hasMessage(equalTo(s"DOAP $permissionIri not found.")))),
        )
      },
      test("update property of a default object access permission") {
        val permissionIri = "http://rdfh.ch/permissions/00FF/T12XnPXxQ42jBMIf6RK1pg"
        val propertyIri   = OntologyConstants.KnoraBase.TextFileValue

        for {
          actual <- permissionResponder(
                      _.updatePermissionProperty(
                        PermissionIri.unsafeFrom(permissionIri),
                        ChangePermissionPropertyApiRequestADM(propertyIri),
                        UUID.randomUUID(),
                      ),
                    )
          doap = actual.defaultObjectAccessPermission
        } yield assertTrue(
          doap.iri == permissionIri,
          doap.forProperty.contains(propertyIri),
        )
      },
      test("update property of a default object access permission, delete group") {
        val permissionIri = "http://rdfh.ch/permissions/00FF/Mck2xJDjQ_Oimi_9z4aFaA"
        val propertyIri   = SharedOntologyTestDataADM.IMAGES_TITEL_PROPERTY

        for {
          actual <- permissionResponder(
                      _.updatePermissionProperty(
                        PermissionIri.unsafeFrom(permissionIri),
                        ChangePermissionPropertyApiRequestADM(propertyIri),
                        UUID.randomUUID(),
                      ),
                    )
          doap = actual.defaultObjectAccessPermission
        } yield assertTrue(
          doap.iri == permissionIri,
          doap.forProperty.contains(propertyIri),
          doap.forGroup.isEmpty,
        )
      },
    ),
    suite("ask to delete a permission")(
      test("return NotFoundException if given IRI is not a permission IRI") {
        val permissionIri = PermissionIri.unsafeFrom("http://rdfh.ch/permissions/00FF/RkVssk8XRVO9hZ3VR5IpLA")
        assertZIO(permissionResponder(_.deletePermission(permissionIri, rootUser, UUID.randomUUID())).exit)(
          fails(
            isSubtype[NotFoundException](hasMessage(equalTo(s"Permission $permissionIri was not found"))),
          ),
        )
      },
      test("throw ForbiddenException if user requesting PermissionDeleteResponseADM is not a system or project admin") {
        val permissionIri = PermissionIri.unsafeFrom("http://rdfh.ch/permissions/00FF/Mck2xJDjQ_Oimi_9z4aFaA")
        assertZIO(
          permissionResponder(_.deletePermission(permissionIri, imagesUser02, UUID.randomUUID())).exit,
        )(
          fails(
            isSubtype[ForbiddenException](
              hasMessage(
                equalTo(
                  s"You are logged in with username 'user02.user', but only a system administrator or project administrator has permissions for this operation.",
                ),
              ),
            ),
          ),
        )
      },
      test("erase a permission with given IRI") {
        val permissionIri = PermissionIri.unsafeFrom("http://rdfh.ch/permissions/00FF/Mck2xJDjQ_Oimi_9z4aFaA")
        for {
          actual <-
            permissionResponder(_.deletePermission(permissionIri, rootUser, UUID.randomUUID()))
        } yield assertTrue(actual.deleted)
      },
    ),
  )
}
