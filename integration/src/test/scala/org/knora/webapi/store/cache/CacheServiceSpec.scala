/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.cache

import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM.*
import org.knora.webapi.sharedtestdata.SharedTestDataADM
import org.knora.webapi.slice.admin.domain.model.*
import zio.ZIO
import zio.test.*

object CacheServiceSpec extends ZIOSpecDefault {

  private val user: User = SharedTestDataADM.imagesUser01

  private val userWithApostrophe = User(
    id = "http://rdfh.ch/users/aaaaaab71e7b0e01",
    username = "user_with_apostrophe",
    email = "userWithApostrophe@example.org",
    givenName = """M\\"Given 'Name""",
    familyName = """M\\tFamily Name""",
    status = true,
    lang = "en"
  )

  val project: ProjectADM = SharedTestDataADM.imagesProject

  private val userTests = suite("User")(
    test("successfully store a user and retrieve by UserIri") {
      for {
        _             <- ZIO.serviceWithZIO[CacheService](_.putUser(user))
        retrievedUser <- ZIO.serviceWithZIO[CacheService](_.getUserByIri(UserIri.unsafeFrom(user.id)))
      } yield assertTrue(retrievedUser.contains(user))
    },
    test("successfully store a user and retrieve by Username")(
      for {
        _             <- ZIO.serviceWithZIO[CacheService](_.putUser(user))
        retrievedUser <- ZIO.serviceWithZIO[CacheService](_.getUserByUsername(Username.unsafeFrom(user.username)))
      } yield assertTrue(retrievedUser.contains(user))
    ),
    test("successfully store a user and retrieve by Email")(
      for {
        _             <- ZIO.serviceWithZIO[CacheService](_.putUser(user))
        retrievedUser <- ZIO.serviceWithZIO[CacheService](_.getUserByEmail(Email.unsafeFrom(user.email)))
      } yield assertTrue(retrievedUser.contains(user))
    ),
    test("successfully store and retrieve a user with special characters in their name")(
      for {
        _             <- ZIO.serviceWithZIO[CacheService](_.putUser(userWithApostrophe))
        retrievedUser <- ZIO.serviceWithZIO[CacheService](_.getUserByIri(userWithApostrophe.userIri))
      } yield assertTrue(retrievedUser.contains(userWithApostrophe))
    ),
    test("given when successfully stored and invalidated a user then the cache should not contain the user")(
      for {
        _           <- ZIO.serviceWithZIO[CacheService](_.putUser(userWithApostrophe))
        _           <- ZIO.serviceWithZIO[CacheService](_.invalidateUser(userWithApostrophe.userIri))
        userByIri   <- ZIO.serviceWithZIO[CacheService](_.getUserByIri(userWithApostrophe.userIri))
        userByEmail <- ZIO.serviceWithZIO[CacheService](_.getUserByEmail(Email.unsafeFrom(userWithApostrophe.email)))
        userByUsername <-
          ZIO.serviceWithZIO[CacheService](_.getUserByUsername(Username.unsafeFrom(userWithApostrophe.username)))
      } yield assertTrue(userByIri.isEmpty, userByEmail.isEmpty, userByUsername.isEmpty)
    )
  )

  private val projectTests = suite("ProjectADM")(
    test("successfully store a project and retrieve by IRI")(
      for {
        _                <- ZIO.serviceWithZIO[CacheService](_.putProjectADM(project))
        retrievedProject <- ZIO.serviceWithZIO[CacheService](_.getProjectADM(IriIdentifier.from(project.projectIri)))
      } yield assertTrue(retrievedProject.contains(project))
    ),
    test("successfully store a project and retrieve by SHORTCODE")(
      for {
        _ <- ZIO.serviceWithZIO[CacheService](_.putProjectADM(project))
        retrievedProject <-
          ZIO.serviceWithZIO[CacheService](_.getProjectADM(ShortcodeIdentifier.from(project.getShortcode)))
      } yield assertTrue(retrievedProject.contains(project))
    ),
    test("successfully store a project and retrieve by SHORTNAME")(
      for {
        _ <- ZIO.serviceWithZIO[CacheService](_.putProjectADM(project))
        retrievedProject <-
          ZIO.serviceWithZIO[CacheService](_.getProjectADM(ShortnameIdentifier.from(project.getShortname)))
      } yield assertTrue(retrievedProject.contains(project))
    )
  )

  private val clearCacheSuite = suite("clearCache")(
    test("successfully clears the cache")(
      for {
        _              <- ZIO.serviceWithZIO[CacheService](_.putUser(user))
        _              <- ZIO.serviceWithZIO[CacheService](_.putProjectADM(project))
        _              <- ZIO.serviceWithZIO[CacheService](_.clearCache())
        userByIri      <- ZIO.serviceWithZIO[CacheService](_.getUserByIri(user.userIri))
        userByUsername <- ZIO.serviceWithZIO[CacheService](_.getUserByUsername(user.getUsername))
        userByEmail    <- ZIO.serviceWithZIO[CacheService](_.getUserByEmail(user.getEmail))
        projectByIri   <- ZIO.serviceWithZIO[CacheService](_.getProjectADM(IriIdentifier.from(project.projectIri)))
        projectByShortcode <-
          ZIO.serviceWithZIO[CacheService](_.getProjectADM(ShortcodeIdentifier.from(project.getShortcode)))
        projectByShortname <-
          ZIO.serviceWithZIO[CacheService](_.getProjectADM(ShortnameIdentifier.from(project.getShortname)))
      } yield assertTrue(
        userByIri.isEmpty,
        userByUsername.isEmpty,
        userByEmail.isEmpty,
        projectByIri.isEmpty,
        projectByShortcode.isEmpty,
        projectByShortname.isEmpty
      )
    )
  )

  def spec: Spec[Any, Throwable] =
    suite("CacheServiceLive")(userTests, projectTests, clearCacheSuite).provide(CacheService.layer)
}
