package org.knora.webapi.responders.admin
import zio.Scope
import zio.ZIO
import zio.mock._
import zio.test.Assertion
import zio.test.SmartAssertionOps
import zio.test.Spec
import zio.test.TestEnvironment
import zio.test.ZIOSpecDefault
import zio.test.assertTrue

import dsp.errors.ValidationException
import dsp.valueobjects.Project.ShortCode
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectGetRequestADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectGetResponseADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM.ShortcodeIdentifier
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.responders.ActorToZioBridgeMock

object RestProjectsServiceSpec extends ZIOSpecDefault {

  private val id: ShortcodeIdentifier = ShortcodeIdentifier(
    ShortCode.make("0001").getOrElse(throw new IllegalArgumentException())
  )
  private val expectedRequest: ProjectGetRequestADM = ProjectGetRequestADM(id)
  private val expectedResponse: ProjectGetResponseADM = ProjectGetResponseADM(
    ProjectADM(
      "id",
      "shortname",
      "shortcode",
      None,
      List(StringLiteralV2("description")),
      List.empty,
      None,
      List.empty,
      status = false,
      selfjoin = false
    )
  )

  private val expectSuccess = ActorToZioBridgeMock.AskAppActor
    .of[ProjectGetResponseADM]
    .apply(Assertion.equalTo(expectedRequest), Expectation.value(expectedResponse))

  private val expectNoInteraction = ActorToZioBridgeMock.empty
  private val systemUnderTest     = ZIO.service[RestProjectsService]
  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("RestProjectsService")(
      test("should send correct message and return expected response") {
        for {
          sut    <- systemUnderTest
          actual <- sut.getSingleProjectADMRequest(id)
        } yield assertTrue(actual == expectedResponse)
      }
        .provide(RestProjectsService.layer, expectSuccess),
      test("given an invalid iri should return with a failure") {
        for {
          sut    <- systemUnderTest
          result <- sut.getSingleProjectADMRequest("invalid").exit
        } yield assertTrue(result.is(_.failure) == ValidationException("Project IRI is invalid."))
      }.provide(RestProjectsService.layer, expectNoInteraction)
    )
}
