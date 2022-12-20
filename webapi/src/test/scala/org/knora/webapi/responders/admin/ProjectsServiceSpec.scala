package org.knora.webapi.responders.admin
import zio.Scope
import zio.ZIO
import zio.mock._
import zio.test.Assertion
import zio.test.Spec
import zio.test.TestEnvironment
import zio.test.ZIOSpecDefault
import zio.test.assertTrue

import dsp.valueobjects.Project.ShortCode
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectGetRequestADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectGetResponseADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM.ShortcodeIdentifier
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.messages.util.KnoraSystemInstances
import org.knora.webapi.responders.ActorToZioBridgeMock

object ProjectsServiceSpec extends ZIOSpecDefault {

  private val id: ShortcodeIdentifier = ShortcodeIdentifier(
    ShortCode.make("0001").getOrElse(throw new IllegalArgumentException())
  )
  private val user: UserADM                         = KnoraSystemInstances.Users.SystemUser
  private val expectedRequest: ProjectGetRequestADM = ProjectGetRequestADM(id, user)
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

  private val expectation = ActorToZioBridgeMock.AskAppActor
    .of[ProjectGetResponseADM]
    .apply(Assertion.equalTo(expectedRequest), Expectation.value(expectedResponse))

  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("ProjectsService")(test("should send correct message and return expected response") {
      for {
        sut    <- ZIO.service[ProjectsService]
        actual <- sut.getSingleProjectADMRequest(id, user)
      } yield assertTrue(actual == expectedResponse)
    }).provide(ProjectsService.layer, expectation)
}
