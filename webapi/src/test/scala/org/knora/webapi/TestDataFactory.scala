/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi

import zio.Chunk
import zio.NonEmptyChunk

import dsp.valueobjects.LanguageCode
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.slice.admin.domain.model.Email
import org.knora.webapi.slice.admin.domain.model.FamilyName
import org.knora.webapi.slice.admin.domain.model.GivenName
import org.knora.webapi.slice.admin.domain.model.GroupIri
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.model.KnoraProject._
import org.knora.webapi.slice.admin.domain.model.KnoraUser
import org.knora.webapi.slice.admin.domain.model.PasswordHash
import org.knora.webapi.slice.admin.domain.model.SystemAdmin
import org.knora.webapi.slice.admin.domain.model.UserIri
import org.knora.webapi.slice.admin.domain.model.UserStatus
import org.knora.webapi.slice.admin.domain.model.Username
import org.knora.webapi.slice.admin.domain.model._

/**
 * Helps in creating value objects for tests.
 */
object TestDataFactory {

  object User {
    val testUser: KnoraUser = KnoraUser(
      UserIri.unsafeFrom("http://rdfh.ch/users/exists"),
      Username.unsafeFrom("testuser"),
      Email.unsafeFrom("jane@example.com"),
      FamilyName.unsafeFrom("Doe"),
      GivenName.unsafeFrom("""Jane "TheFirst" J"""),
      PasswordHash.unsafeFrom("hashedPassword"),
      LanguageCode.en,
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
      LanguageCode.de,
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
      LanguageCode.en,
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
      GroupDescriptions.unsafeFrom(List(StringLiteralV2.from("one user group to rule them all", None))),
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
    NonEmptyChunk(Description.unsafeFrom(StringLiteralV2.from("Some description", None))),
    List.empty,
    None,
    Status.Active,
    SelfJoin.CannotJoin,
    RestrictedView.default,
  )
}
