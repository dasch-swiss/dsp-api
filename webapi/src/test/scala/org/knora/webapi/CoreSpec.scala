/*
 * Copyright © 2015-2021 the contributors (see Contributors.md).
 *
 * This file is part of Knora.
 *
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi

import app.{ApplicationActor, LiveManagers}
import core.Core
import feature.{FeatureFactoryConfig, KnoraSettingsFeatureFactoryConfig, TestFeatureFactoryConfig}
import messages.StringFormatter
import messages.app.appmessages.{AppStart, AppStop, SetAllowReloadOverHTTPState}
import messages.store.cacheservicemessages.CacheServiceFlushDB
import messages.store.triplestoremessages.{RdfDataObject, ResetRepositoryContent}
import messages.util.rdf.RdfFeatureFactory
import messages.util.{KnoraSystemInstances, ResponderData}
import messages.v2.responder.ontologymessages.LoadOntologiesRequestV2
import settings.{KnoraDispatchers, KnoraSettings, KnoraSettingsImpl, _}
import store.cacheservice.settings.CacheServiceSettings
import util.StartupUtils

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.event.LoggingAdapter
import akka.pattern.ask
import akka.stream.Materializer
import akka.testkit.{ImplicitSender, TestKit}
import akka.util.Timeout
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

object CoreSpec {

  /*
        Loads the following (first-listed are higher priority):
            - system properties (e.g., -Dconfig.resource=fuseki.conf)
            - test/resources/application.conf
            - main/resources/application.conf
   */
  val defaultConfig: Config = ConfigFactory.load()

  /* Copied from: akka/akka-testkit/src/test/scala/akka/testkit/AkkaSpec.scala */
  def getCallerName(clazz: Class[_]): String = {
    val s = (Thread.currentThread.getStackTrace map (_.getClassName) drop 1)
      .dropWhile(_ matches "(java.lang.Thread|.*CoreSpec.?$)")
    val reduced = s.lastIndexWhere(_ == clazz.getName) match {
      case -1 => s
      case z  => s drop (z + 1)
    }
    reduced.head.replaceFirst(""".*\.""", "").replaceAll("[^a-zA-Z_0-9]", "_")
  }
}

abstract class CoreSpec(_system: ActorSystem)
    extends TestKit(_system)
    with Core
    with StartupUtils
    with AnyWordSpecLike
    with Matchers
    with BeforeAndAfterAll
    with ImplicitSender {

  /* constructors - individual tests can override the configuration by giving their own */
  def this(name: String, config: Config) =
    this(
      ActorSystem(
        name,
        TestContainersAll.PortConfig.withFallback(ConfigFactory.load(config.withFallback(CoreSpec.defaultConfig)))
      )
    )

  def this(config: Config) =
    this(
      ActorSystem(
        CoreSpec.getCallerName(classOf[CoreSpec]),
        TestContainersAll.PortConfig.withFallback(ConfigFactory.load(config.withFallback(CoreSpec.defaultConfig)))
      )
    )

  def this(name: String) = this(ActorSystem(name, TestContainersAll.PortConfig.withFallback(ConfigFactory.load())))

  def this() =
    this(
      ActorSystem(
        CoreSpec.getCallerName(classOf[CoreSpec]),
        TestContainersAll.PortConfig.withFallback(ConfigFactory.load())
      )
    )

  /* needed by the core trait */
  implicit lazy val settings: KnoraSettingsImpl   = KnoraSettings(system)
  implicit val materializer: Materializer         = Materializer.matFromSystem(system)
  implicit val executionContext: ExecutionContext = system.dispatchers.lookup(KnoraDispatchers.KnoraActorDispatcher)

  // can be overridden in individual spec
  lazy val rdfDataObjects = Seq.empty[RdfDataObject]

  // needs to be initialized early on
  StringFormatter.initForTest()
  RdfFeatureFactory.init(settings)

  val log: LoggingAdapter = akka.event.Logging(system, this.getClass)

  lazy val appActor: ActorRef =
    system.actorOf(Props(new ApplicationActor with LiveManagers), name = APPLICATION_MANAGER_ACTOR_NAME)

  // The main application actor forwards messages to the responder manager and the store manager.
  val responderManager: ActorRef = appActor
  val storeManager: ActorRef     = appActor

  val responderData: ResponderData = ResponderData(
    system = system,
    appActor = appActor,
    knoraSettings = settings,
    cacheServiceSettings = new CacheServiceSettings(system.settings.config)
  )

  protected val defaultFeatureFactoryConfig: FeatureFactoryConfig = new TestFeatureFactoryConfig(
    testToggles = Set.empty,
    parent = new KnoraSettingsFeatureFactoryConfig(settings)
  )

  final override def beforeAll(): () = {
    // set allow reload over http
    appActor ! SetAllowReloadOverHTTPState(true)

    // Start Knora, without reading data from the repository
    appActor ! AppStart(ignoreRepository = true, requiresIIIFService = false)

    // waits until knora is up and running
    applicationStateRunning()

    loadTestData(rdfDataObjects)

    // memusage()
  }

  final override def afterAll(): () =
    appActor ! AppStop()

  protected def loadTestData(rdfDataObjects: Seq[RdfDataObject]): Unit = {
    logger.info("Loading test data started ...")
    implicit val timeout: Timeout = Timeout(settings.defaultTimeout)
    Try(Await.result(appActor ? ResetRepositoryContent(rdfDataObjects), 479999.milliseconds)) match {
      case Success(res) => logger.info("... loading test data done.")
      case Failure(e)   => logger.error(s"Loading test data failed: ${e.getMessage}")
    }

    logger.info("Loading load ontologies into cache started ...")
    Try(
      Await.result(
        appActor ? LoadOntologiesRequestV2(
          featureFactoryConfig = defaultFeatureFactoryConfig,
          requestingUser = KnoraSystemInstances.Users.SystemUser
        ),
        1 minute
      )
    ) match {
      case Success(res) => logger.info("... loading ontologies into cache done.")
      case Failure(e)   => logger.error(s"Loading ontologies into cache failed: ${e.getMessage}")
    }

    logger.info("Flush Redis cache started ...")
    Try(Await.result(appActor ? CacheServiceFlushDB(KnoraSystemInstances.Users.SystemUser), 5 seconds)) match {
      case Success(res) => logger.info("... flushing Redis cache done.")
      case Failure(e)   => logger.error(s"Flushing Redis cache failed: ${e.getMessage}")
    }
  }
}
