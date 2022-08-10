/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.app

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.event.LoggingReceive
import org.knora.webapi.messages.ResponderRequest
import org.knora.webapi.messages.admin.responder.groupsmessages.GroupsResponderRequestADM
import org.knora.webapi.messages.admin.responder.listsmessages.ListsResponderRequestADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionsResponderRequestADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectsResponderRequestADM
import org.knora.webapi.messages.admin.responder.sipimessages.SipiResponderRequestADM
import org.knora.webapi.messages.admin.responder.storesmessages.StoreResponderRequestADM
import org.knora.webapi.messages.admin.responder.usersmessages.UsersResponderRequestADM
import org.knora.webapi.messages.util.ResponderData
import org.knora.webapi.messages.v1.responder.ckanmessages.CkanResponderRequestV1
import org.knora.webapi.messages.v1.responder.listmessages.ListsResponderRequestV1
import org.knora.webapi.messages.v1.responder.ontologymessages.OntologyResponderRequestV1
import org.knora.webapi.messages.v1.responder.projectmessages.ProjectsResponderRequestV1
import org.knora.webapi.messages.v1.responder.resourcemessages.ResourcesResponderRequestV1
import org.knora.webapi.messages.v1.responder.searchmessages.SearchResponderRequestV1
import org.knora.webapi.messages.v1.responder.standoffmessages.StandoffResponderRequestV1
import org.knora.webapi.messages.v1.responder.usermessages.UsersResponderRequestV1
import org.knora.webapi.messages.v1.responder.valuemessages.ValuesResponderRequestV1
import org.knora.webapi.messages.v2.responder.listsmessages.ListsResponderRequestV2
import org.knora.webapi.messages.v2.responder.ontologymessages.OntologiesResponderRequestV2
import org.knora.webapi.messages.v2.responder.resourcemessages.ResourcesResponderRequestV2
import org.knora.webapi.messages.v2.responder.searchmessages.SearchResponderRequestV2
import org.knora.webapi.messages.v2.responder.standoffmessages.StandoffResponderRequestV2
import org.knora.webapi.messages.v2.responder.valuemessages.ValuesResponderRequestV2
import org.knora.webapi.responders.admin._
import org.knora.webapi.responders.v1._
import org.knora.webapi.responders.v2._
import org.knora.webapi.settings._
import org.knora.webapi.settings.KnoraDispatchers
import org.knora.webapi.settings.KnoraSettingsImpl
import org.knora.webapi.store.cache.settings.CacheServiceSettings
import org.knora.webapi.util.ActorUtil._

import scala.concurrent.ExecutionContext

import zio._
import org.knora.webapi.core
import scala.concurrent.Future
import org.knora.webapi.config.AppConfig
import zio.macros.accessible
import akka.actor.Props
import org.knora.webapi.store.cache.CacheServiceManager
import org.knora.webapi.store.iiif.IIIFServiceManager
import org.knora.webapi.store.triplestore.TriplestoreServiceManager

@accessible
trait AppRouter {
  val ref: ActorRef
}

object AppRouter {
  val layer: ZLayer[
    core.ActorSystem with CacheServiceManager with IIIFServiceManager with TriplestoreServiceManager with AppConfig,
    Nothing,
    AppRouter
  ] =
    ZLayer {
      for {
        as                        <- ZIO.service[core.ActorSystem]
        cacheServiceManager       <- ZIO.service[CacheServiceManager]
        iiifServiceManager        <- ZIO.service[IIIFServiceManager]
        triplestoreServiceManager <- ZIO.service[TriplestoreServiceManager]
        appConfig                 <- ZIO.service[AppConfig]
      } yield new AppRouter { self =>
        implicit lazy val system: ActorSystem =
          as.system

        implicit val executionContext: ExecutionContext =
          system.dispatchers.lookup(KnoraDispatchers.KnoraActorDispatcher)

        override val ref: ActorRef = system.actorOf(
          Props(
            new core.actors.RoutingActor(
              cacheServiceManager,
              iiifServiceManager,
              triplestoreServiceManager,
              appConfig
            )
          )
            .withDispatcher(KnoraDispatchers.KnoraActorDispatcher),
          name = APPLICATION_MANAGER_ACTOR_NAME
        )
      }
    }
}
