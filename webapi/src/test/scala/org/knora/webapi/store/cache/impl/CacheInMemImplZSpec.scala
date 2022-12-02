package org.knora.webapi.store.cache.impl

import dsp.errors.BadRequestException
import dsp.valueobjects.V2UuidValidation
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM.IriIdentifier
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM.ShortcodeIdentifier
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM.ShortnameIdentifier
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM.UuidIdentifier
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.admin.responder.usersmessages.UserIdentifierADM
import org.knora.webapi.sharedtestdata.SharedTestDataADM
import org.knora.webapi.store.cache.api.CacheService
import zio.ZLayer
import zio.test.Assertion.equalTo
import zio.test.TestAspect
import zio.test.ZIOSpecDefault
import zio.test.assert
import zio.test.assertTrue

/**
 * This spec is used to test [[org.knora.webapi.store.cache.impl.CacheServiceInMemImpl]].
 */
object CacheInMemImplZSpec extends ZIOSpecDefault {

  StringFormatter.initForTest()
  implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

  val user: UserADM = SharedTestDataADM.imagesUser01
  val userWithApostrophe = UserADM(
    id = "http://rdfh.ch/users/aaaaaab71e7b0e01",
    username = "user_with_apostrophe",
    email = "userWithApostrophe@example.org",
    givenName = """M\\"Given 'Name""",
    familyName = """M\\tFamily Name""",
    status = true,
    lang = "en"
  )

  val project: ProjectADM = SharedTestDataADM.imagesProject

  /**
   * Defines a layer which encompases all dependencies that are needed for
   * running the tests.
   */
  val testLayers = ZLayer.make[CacheService](CacheServiceInMemImpl.layer)

  def spec = (userTests + projectTests + otherTests).provideLayerShared(testLayers) @@ TestAspect.sequential

  val userTests = suite("CacheInMemImplZSpec - user")(
    test("successfully store a user and retrieve by IRI") {
      for {
        _             <- CacheService.putUserADM(user)
        retrievedUser <- CacheService.getUserADM(UserIdentifierADM(maybeIri = Some(user.id)))
      } yield assertTrue(retrievedUser == Some(user))
    },
    test("successfully store a user and retrieve by USERNAME")(
      for {
        _             <- CacheService.putUserADM(user)
        retrievedUser <- CacheService.getUserADM(UserIdentifierADM(maybeUsername = Some(user.username)))
      } yield assert(retrievedUser)(equalTo(Some(user)))
    ),
    test("successfully store a user and retrieve by EMAIL")(
      for {
        _             <- CacheService.putUserADM(user)
        retrievedUser <- CacheService.getUserADM(UserIdentifierADM(maybeEmail = Some(user.email)))
      } yield assert(retrievedUser)(equalTo(Some(user)))
    ),
    test("successfully store and retrieve a user with special characters in his name")(
      for {
        _             <- CacheService.putUserADM(userWithApostrophe)
        retrievedUser <- CacheService.getUserADM(UserIdentifierADM(maybeIri = Some(userWithApostrophe.id)))
      } yield assert(retrievedUser)(equalTo(Some(userWithApostrophe)))
    )
  )

  val projectTests = suite("CacheInMemImplZSpec - project")(
    test("successfully store a project and retrieve by IRI")(
      for {
        _ <- CacheService.putProjectADM(project)
        retrievedProject <- CacheService.getProjectADM(
                              IriIdentifier
                                .fromString(project.id)
                                .getOrElseWith(e => throw BadRequestException(e.head.getMessage))
                            )
      } yield assert(retrievedProject)(equalTo(Some(project)))
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
      } yield assert(retrievedProject)(equalTo(Some(project)))
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
      } yield assert(retrievedProject)(equalTo(Some(project)))
    ),
    test("successfully store a project and retrieve by UUID")(
      for {
        _ <- CacheService.putProjectADM(project)
        retrievedProject <-
          CacheService.getProjectADM(
            UuidIdentifier
              .fromString(V2UuidValidation.getUuidFromIri(project.id))
              .getOrElseWith(e => throw BadRequestException(e.head.getMessage))
          )
      } yield assert(retrievedProject)(equalTo(Some(project)))
    )
  )

  val otherTests = suite("CacheInMemImplZSpec - other")(
    test("successfully store string value")(
      for {
        _              <- CacheService.putStringValue("my-new-key", "my-new-value")
        retrievedValue <- CacheService.getStringValue("my-new-key")
      } yield assert(retrievedValue)(equalTo(Some("my-new-value")))
    ),
    test("successfully delete stored value")(
      for {
        _              <- CacheService.putStringValue("my-new-key", "my-new-value")
        _              <- CacheService.removeValues(Set("my-new-key"))
        retrievedValue <- CacheService.getStringValue("my-new-key")
      } yield assert(retrievedValue)(equalTo(None))
    )
  )
}
