package org.knora.webapi.responders.v2.service

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import org.knora.webapi.messages.admin.responder.permissionsmessages.{
  DefaultObjectAccessPermissionsStringForResourceClassGetADM,
  DefaultObjectAccessPermissionsStringResponseADM
}
import org.knora.webapi.messages.util.ResponderData

import scala.concurrent.Future

trait PermissionService {
  def defaultObjectAccessPermissionsStringForResourceClassGetADM(
    req: DefaultObjectAccessPermissionsStringForResourceClassGetADM
  ): Future[DefaultObjectAccessPermissionsStringResponseADM]
}

final case class ActorPermissionService(rData: ResponderData) extends PermissionService {
  implicit val timeout: Timeout = rData.appConfig.defaultTimeoutAsDuration
  val appActor: ActorRef        = rData.appActor

  def defaultObjectAccessPermissionsStringForResourceClassGetADM(
    req: DefaultObjectAccessPermissionsStringForResourceClassGetADM
  ): Future[DefaultObjectAccessPermissionsStringResponseADM] =
    appActor.ask(req).mapTo[DefaultObjectAccessPermissionsStringResponseADM]
}
