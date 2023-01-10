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
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.responders.ActorToZioBridgeMock
import org.knora.webapi.responders.admin.ProjectsService
import org.knora.webapi.responders.ActorToZioBridge
import org.knora.webapi.messages.ResponderRequest
import zio._

object ProjectsServiceLiveSpec extends ZIOSpecDefault {

  private val expectNoInteraction = ActorToZioBridgeMock.empty
  // private val systemUnderTest     = ZIO.service[ProjectsService]

  final case class BridgeMock() extends ActorToZioBridge {
    var msg: ResponderRequest = null

    override def askAppActor[R: Tag](message: ResponderRequest): Task[R] = {
      msg = message
      println("msg", msg)
      println("type", msg.getClass())
      ZIO.attempt(null.asInstanceOf[R])
    }
  }

  private val bridgeMock = ZLayer(ZIO.succeed(BridgeMock()))

  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("RestProjectsService")(
      test("test") {
        val validShortcode: ShortcodeIdentifier =
          ShortcodeIdentifier(ShortCode.make("0001").getOrElse(throw new IllegalArgumentException()))

        for {
          projectsService   <- ZIO.service[ProjectsService]
          _                 <- projectsService.getSingleProjectADMRequest(validShortcode)
          bridge            <- ZIO.service[BridgeMock]
          messageSentToActor = bridge.msg
          _                  = println("xxxx", messageSentToActor)
        } yield assertTrue(messageSentToActor == ProjectGetRequestADM(validShortcode))
      }.provide(ProjectsService.live, bridgeMock),
      test("get single project by shortcode") {

        val validShortcode: ShortcodeIdentifier =
          ShortcodeIdentifier(ShortCode.make("0001").getOrElse(throw new IllegalArgumentException()))

        for {
          projectsService <- ZIO.service[ProjectsService]
          result          <- projectsService.getSingleProjectADMRequest(validShortcode)
        } yield ()

        val expectedRequest: ProjectGetRequestADM = ProjectGetRequestADM(validShortcode)
        val expectedResponse: ProjectGetResponseADM = ProjectGetResponseADM(
          ProjectADM(
            "0001",
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
        val expectSuccess = ActorToZioBridgeMock.AskAppActor
          .of[ProjectGetResponseADM]
          .apply(Assertion.equalTo(expectedRequest), Expectation.value(expectedResponse))

        (for {
          sut    <- ZIO.service[ProjectsService]
          actual <- sut.getSingleProjectADMRequest(validShortcode)
        } yield assertTrue(actual == expectedResponse)).provide(expectSuccess, ProjectsService.live)
      }
      // .provide(ProjectsService.live)
    )
}
