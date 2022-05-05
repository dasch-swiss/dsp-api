/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.cacheservice.impl

import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.admin.responder.usersmessages.UserIdentifierADM
import org.knora.webapi.sharedtestdata.SharedTestDataADM
import org.knora.webapi.store.cacheservice.api.CacheService
import zio.ZLayer
import zio.test.Assertion._
import zio.test._

/**
 * This spec is used to test [[org.knora.webapi.store.cacheservice.impl.CacheServiceInMemImpl]].
 */
object CacheInMemImplSpec extends ZIOSpec[CacheService] {

  StringFormatter.initForTest()
  implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

  private val user: UserADM = SharedTestDataADM.imagesUser01
  private val userWithApostrophe = UserADM(
    id = "http://rdfh.ch/users/aaaaaab71e7b0e01",
    username = "user_with_apostrophe",
    email = "userWithApostrophe@example.org",
    givenName = """M\\"Given 'Name""",
    familyName = """M\\tFamily Name""",
    status = true,
    lang = "en"
  )

  private val project: ProjectADM = SharedTestDataADM.imagesProject

  /**
   * Defines a layer which encompases all dependencies that are needed for
   * for running the tests.
   */
  val layer = ZLayer.make[CacheService](CacheServiceInMemImpl.layer)

  def spec = (userTests + projectTests + otherTests)

  val userTests = suite("CacheInMemImplSpec - user")(
    test("successfully store a user and retrieve by IRI") {
      for {
        _             <- CacheService(_.putUserADM(user))
        retrievedUser <- CacheService(_.getUserADM(UserIdentifierADM(maybeIri = Some(user.id))))
      } yield assert(retrievedUser)(equalTo(Some(user)))
    } +
      test("successfully store a user and retrieve by USERNAME")(
        for {
          _             <- CacheService(_.putUserADM(user))
          retrievedUser <- CacheService(_.getUserADM(UserIdentifierADM(maybeUsername = Some(user.username))))
        } yield assert(retrievedUser)(equalTo(Some(user)))
      ) +
      test("successfully store a user and retrieve by EMAIL")(
        for {
          _             <- CacheService(_.putUserADM(user))
          retrievedUser <- CacheService(_.getUserADM(UserIdentifierADM(maybeEmail = Some(user.email))))
        } yield assert(retrievedUser)(equalTo(Some(user)))
      ) +
      test("successfully store and retrieve a user with special characters in his name")(
        for {
          _             <- CacheService(_.putUserADM(userWithApostrophe))
          retrievedUser <- CacheService(_.getUserADM(UserIdentifierADM(maybeIri = Some(userWithApostrophe.id))))
        } yield assert(retrievedUser)(equalTo(Some(userWithApostrophe)))
      )
  )

  val projectTests = suite("CacheInMemImplSpec - project")(
    test("successfully store a project and retrieve by IRI")(
      for {
        _                <- CacheService(_.putProjectADM(project))
        retrievedProject <- CacheService(_.getProjectADM(ProjectIdentifierADM(maybeIri = Some(project.id))))
      } yield assert(retrievedProject)(equalTo(Some(project)))
    ) +
      test("successfully store a project and retrieve by SHORTCODE")(
        for {
          _ <- CacheService(_.putProjectADM(project))
          retrievedProject <-
            CacheService(_.getProjectADM(ProjectIdentifierADM(maybeShortcode = Some(project.shortcode))))
        } yield assert(retrievedProject)(equalTo(Some(project)))
      ) +
      test("successfully store a project and retrieve by SHORTNAME")(
        for {
          _ <- CacheService(_.putProjectADM(project))
          retrievedProject <-
            CacheService(_.getProjectADM(ProjectIdentifierADM(maybeShortname = Some(project.shortname))))
        } yield assert(retrievedProject)(equalTo(Some(project)))
      )
  )

  val otherTests = suite("CacheInMemImplSpec - other")(
    test("successfully store string value")(
      for {
        _              <- CacheService(_.putStringValue("my-new-key", "my-new-value"))
        retrievedValue <- CacheService(_.getStringValue("my-new-key"))
      } yield assert(retrievedValue)(equalTo(Some("my-new-value")))
    ) +
      test("successfully delete stored value")(
        for {
          _              <- CacheService(_.putStringValue("my-new-key", "my-new-value"))
          _              <- CacheService(_.removeValues(Set("my-new-key")))
          retrievedValue <- CacheService(_.getStringValue("my-new-key"))
        } yield assert(retrievedValue)(equalTo(None))
      )
  )
}
