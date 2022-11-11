package org.knora.webapi.responders.v2.service

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import org.knora.webapi.messages.admin.responder.projectsmessages.{ProjectGetRequestADM, ProjectGetResponseADM}
import org.knora.webapi.messages.util.ResponderData

import scala.concurrent.Future

trait ProjectService {

  def projectGetRequestADM(req: ProjectGetRequestADM): Future[ProjectGetResponseADM]
}

final case class ActorProjectService(rData: ResponderData) extends ProjectService {
  implicit val timeout: Timeout = rData.appConfig.defaultTimeoutAsDuration
  val appActor: ActorRef        = rData.appActor
  def projectGetRequestADM(req: ProjectGetRequestADM): Future[ProjectGetResponseADM] =
    appActor.ask(req).mapTo[ProjectGetResponseADM]
}
