/*
 * Copyright © 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.app

import akka.actor._
import akka.stream.Materializer
import org.knora.webapi.core.Core
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.util.rdf.RdfFeatureFactory
import org.knora.webapi.settings.{KnoraDispatchers, KnoraSettings, KnoraSettingsImpl, _}

import scala.concurrent.ExecutionContext
import scala.language.postfixOps
import scala.languageFeature.postfixOps

/**
 * The applications actor system.
 */
trait LiveCore extends Core {

  /**
   * The application's actor system.
   */
  implicit lazy val system: ActorSystem = ActorSystem("webapi")

  /**
   * The application's configuration.
   */
  implicit lazy val settings: KnoraSettingsImpl = KnoraSettings(system)

  /**
   * Provides the actor materializer (akka-http)
   */
  implicit val materializer: Materializer = Materializer.matFromSystem(system)

  /**
   * Provides the default global execution context
   */
  implicit val executionContext: ExecutionContext = system.dispatchers.lookup(KnoraDispatchers.KnoraActorDispatcher)

  // Initialise StringFormatter and RdfFeatureFactory with the system settings.
  // This must happen before any responders are constructed.
  StringFormatter.init(settings)
  RdfFeatureFactory.init(settings)

  /**
   * The main application supervisor actor which is at the top of the actor
   * hierarchy. All other actors are instantiated as child actors. Further,
   * this actor is responsible for the execution of the startup and shutdown
   * sequences.
   */
  lazy val appActor: ActorRef = system.actorOf(
    Props(new ApplicationActor with LiveManagers)
      .withDispatcher(KnoraDispatchers.KnoraActorDispatcher),
    name = APPLICATION_MANAGER_ACTOR_NAME
  )
}
