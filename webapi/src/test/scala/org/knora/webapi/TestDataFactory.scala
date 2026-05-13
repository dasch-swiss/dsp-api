/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi

import zio.Chunk
import zio.NonEmptyChunk

import org.knora.webapi.TestDataFactory.Project.systemProjectIri
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionsDataADM
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.slice.admin.domain.model.*
import org.knora.webapi.slice.admin.domain.model.KnoraProject.*
import org.knora.webapi.slice.admin.domain.service.KnoraGroupRepo
import org.knora.webapi.slice.admin.domain.service.KnoraProjectRepo
import org.knora.webapi.slice.api.admin.model.Project
import org.knora.webapi.slice.common.domain.LanguageCode

/**
 * Helps in creating value objects for tests.
 */
object TestDataFactory {
  object Project {
    val systemProjectIri: IRI = KnoraProjectRepo.builtIn.SystemProject.id.value // built-in project
  }

  object User {
    /* represents the user profile of 'root' as found in admin-data.ttl */
    val rootUser: User =
      org.knora.webapi.slice.admin.domain.model.User(
        id = "http://rdfh.ch/users/root",
        username = "root",
        email = "root@example.com",
        givenName = "System",
        familyName = "Administrator",
        status = true,
        lang = "de",
        password = Option("$2a$12$7XEBehimXN1rbhmVgQsyve08.vtDmKK7VMin4AdgCEtE4DWgfQbTK"),
        groups = Seq.empty[Group],
        projects = Seq.empty[Project],
        permissions = PermissionsDataADM(
          groupsPerProject = Map(
            systemProjectIri -> List(KnoraGroupRepo.builtIn.SystemAdmin.id.value),
          ),
          administrativePermissionsPerProject = Map.empty[IRI, Set[PermissionADM]],
        ),
      )

    val testUser: KnoraUser = KnoraUser(
      UserIri.unsafeFrom("http://rdfh.ch/users/exists"),
      Username.unsafeFrom("testuser"),
      Email.unsafeFrom("jane@example.com"),
      FamilyName.unsafeFrom("Doe"),
      GivenName.unsafeFrom("""Jane "TheFirst" J"""),
      PasswordHash.unsafeFrom("hashedPassword"),
      LanguageCode.EN,
      UserStatus.Active,
      isInProject = Chunk(
        ProjectIri.unsafeFrom("http://rdfh.ch/projects/0001"),
        ProjectIri.unsafeFrom("http://rdfh.ch/projects/0002"),
      ),
      isInGroup = Chunk(GroupIri.unsafeFrom("http://rdfh.ch/groups/0001/1234")),
      isInSystemAdminGroup = SystemAdmin.IsNotSystemAdmin,
      isInProjectAdminGroup = Chunk(ProjectIri.unsafeFrom("http://rdfh.ch/projects/0001")),
    )
    val testUserWithoutAnyGroups: KnoraUser = KnoraUser(
      UserIri.unsafeFrom("http://rdfh.ch/users/exists2"),
      Username.unsafeFrom("testuser2"),
      Email.unsafeFrom("jane2@example.com"),
      FamilyName.unsafeFrom("""D’Oe"""),
      GivenName.unsafeFrom("""D'Juan"""),
      PasswordHash.unsafeFrom("hashedPassword2"),
      LanguageCode.DE,
      UserStatus.Inactive,
      isInProject = Chunk.empty,
      isInGroup = Chunk.empty,
      isInSystemAdminGroup = SystemAdmin.IsSystemAdmin,
      isInProjectAdminGroup = Chunk.empty,
    )
    val testUser3: KnoraUser = KnoraUser(
      UserIri.unsafeFrom("http://rdfh.ch/users/exists3"),
      Username.unsafeFrom("testuser3"),
      Email.unsafeFrom("john@example.com"),
      FamilyName.unsafeFrom("Doe"),
      GivenName.unsafeFrom("John"),
      PasswordHash.unsafeFrom("hashedPassword"),
      LanguageCode.EN,
      UserStatus.Active,
      isInProject = Chunk(ProjectIri.unsafeFrom("http://rdfh.ch/projects/0002")),
      isInGroup = Chunk(GroupIri.unsafeFrom("http://rdfh.ch/groups/0001/12345")),
      isInSystemAdminGroup = SystemAdmin.IsNotSystemAdmin,
      isInProjectAdminGroup = Chunk(ProjectIri.unsafeFrom("http://rdfh.ch/projects/0003")),
    )
  }

  object UserGroup {
    val testUserGroup: KnoraGroup = KnoraGroup(
      GroupIri.unsafeFrom("http://rdfh.ch/groups/0001/1234"),
      GroupName.unsafeFrom("User Group"),
      GroupDescriptions.unsafeFrom(List(StringLiteralV2.from("one user group to rule them all"))),
      GroupStatus.from(true),
      Some(ProjectIri.unsafeFrom("http://rdfh.ch/projects/0001")),
      GroupSelfJoin.from(false),
    )
  }

  val someProject = KnoraProject(
    ProjectIri.unsafeFrom("http://rdfh.ch/projects/0001"),
    Shortname.unsafeFrom("shortname"),
    Shortcode.unsafeFrom("0001"),
    None,
    NonEmptyChunk(Description.unsafeFrom(StringLiteralV2.from("Some description"))),
    List.empty,
    None,
    Status.Active,
    SelfJoin.CannotJoin,
    RestrictedView.default,
    Set.empty,
    Set.empty,
  )

  val someProjectADM = org.knora.webapi.slice.api.admin.model.Project(
    ProjectIri.unsafeFrom("http://rdfh.ch/projects/0001"),
    Shortname.unsafeFrom("shortname"),
    Shortcode.unsafeFrom("0001"),
    None,
    Seq(StringLiteralV2.from("Some description")),
    List.empty,
    None,
    Seq.empty,
    Status.Active,
    SelfJoin.CannotJoin,
    Set.empty,
    Set.empty,
  )
}
