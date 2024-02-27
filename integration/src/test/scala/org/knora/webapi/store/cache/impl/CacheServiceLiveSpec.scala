/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.cache.impl

import zio.test.*

import dsp.errors.BadRequestException
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM.*
import org.knora.webapi.sharedtestdata.SharedTestDataADM
import org.knora.webapi.slice.admin.domain.model.*
import org.knora.webapi.store.cache.api.CacheService

object CacheServiceLiveSpec extends ZIOSpecDefault {

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
        _             <- CacheService.putUser(user)
        retrievedUser <- CacheService.getUserByIri(UserIri.unsafeFrom(user.id))
      } yield assertTrue(retrievedUser.contains(user))
    },
    test("successfully store a user and retrieve by Username")(
      for {
        _             <- CacheService.putUser(user)
        retrievedUser <- CacheService.getUserByUsername(Username.unsafeFrom(user.username))
      } yield assertTrue(retrievedUser.contains(user))
    ),
    test("successfully store a user and retrieve by Email")(
      for {
        _             <- CacheService.putUser(user)
        retrievedUser <- CacheService.getUserByEmail(Email.unsafeFrom(user.email))
      } yield assertTrue(retrievedUser.contains(user))
    ),
    test("successfully store and retrieve a user with special characters in their name")(
      for {
        _             <- CacheService.putUser(userWithApostrophe)
        retrievedUser <- CacheService.getUserByIri(userWithApostrophe.userIri)
      } yield assertTrue(retrievedUser.contains(userWithApostrophe))
    ),
    test("given when successfully stored and invalidated a user then the cache should not contain the user")(
      for {
        _              <- CacheService.putUser(userWithApostrophe)
        _              <- CacheService.invalidateUser(userWithApostrophe.userIri)
        userByIri      <- CacheService.getUserByIri(userWithApostrophe.userIri)
        userByEmail    <- CacheService.getUserByEmail(Email.unsafeFrom(userWithApostrophe.email))
        userByUsername <- CacheService.getUserByUsername(Username.unsafeFrom(userWithApostrophe.username))
      } yield assertTrue(userByIri.isEmpty, userByEmail.isEmpty, userByUsername.isEmpty)
    )
  )

  private val projectTests = suite("ProjectADM")(
    test("successfully store a project and retrieve by IRI")(
      for {
        _ <- CacheService.putProjectADM(project)
        retrievedProject <- CacheService.getProjectADM(
                              IriIdentifier
                                .fromString(project.id)
                                .getOrElseWith(e => throw BadRequestException(e.head.getMessage))
                            )
      } yield assertTrue(retrievedProject.contains(project))
    ),
    test("successfully store a project and retrieve by SHORTCODE")(
      for {
        _ <- CacheService.putProjectADM(project)
        retrievedProject <-
          CacheService.getProjectADM(
            ShortcodeIdentifier
              .fromString(project.shortcode)
              .getOrElseWith(e => throw BadRequestException(e.head.getMessage))
          )
      } yield assertTrue(retrievedProject.contains(project))
    ),
    test("successfully store a project and retrieve by SHORTNAME")(
      for {
        _ <- CacheService.putProjectADM(project)
        retrievedProject <-
          CacheService.getProjectADM(
            ShortnameIdentifier
              .fromString(project.shortname)
              .getOrElseWith(e => throw BadRequestException(e.head.getMessage))
          )
      } yield assertTrue(retrievedProject.contains(project))
    )
  )

  def spec: Spec[Any, Throwable] =
    suite("CacheServiceLive")(userTests, projectTests).provide(CacheServiceLive.layer)

}
