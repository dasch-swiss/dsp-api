package org.knora.webapi.app

import akka.actor.Actor
import org.knora.webapi.messages.ResponderRequest
import com.typesafe.scalalogging.Logger
import org.knora.webapi.responders.ResponderManager
import org.knora.webapi.util.ActorUtil
import scala.concurrent.ExecutionContext
import org.knora.webapi.messages.store.cacheservicemessages.CacheServiceRequest
import org.knora.webapi.messages.store.sipimessages.IIIFRequest
import org.knora.webapi.messages.store.triplestoremessages.TriplestoreRequest
import org.knora.webapi.store.iiif.IIIFServiceManager
import org.knora.webapi.store.triplestore.TriplestoreServiceManager
import org.knora.webapi.store.cache.CacheServiceManager
import org.knora.webapi.config.AppConfig

class ApplicationRouterActor(
  responderManager: ResponderManager,
  cacheServiceManager: CacheServiceManager,
  iiifServiceManager: IIIFServiceManager,
  triplestoreManager: TriplestoreServiceManager,
  appConfig: AppConfig
) extends Actor {
  val log: Logger                   = Logger(this.getClass())
  implicit val ec: ExecutionContext = context.dispatcher
  def receive: Receive = {
    case msg: ResponderRequest.KnoraRequestV1  => ActorUtil.future2Message(sender(), responderManager.receive(msg), log)
    case msg: ResponderRequest.KnoraRequestV2  => ActorUtil.future2Message(sender(), responderManager.receive(msg), log)
    case msg: ResponderRequest.KnoraRequestADM => ActorUtil.future2Message(sender(), responderManager.receive(msg), log)
    case msg: CacheServiceRequest              => ActorUtil.zio2Message(sender(), cacheServiceManager.receive(msg), appConfig, log)
    case msg: IIIFRequest                      => ActorUtil.zio2Message(sender(), iiifServiceManager.receive(msg), appConfig, log)
    case msg: TriplestoreRequest               => ActorUtil.zio2Message(sender(), triplestoreManager.receive(msg), appConfig, log)
  }
}
