/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi

import akka.actor.{ActorRef, ActorSystem}
import akka.dispatch.MessageDispatcher
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.store.triplestoremessages._
import org.knora.webapi.settings.{KnoraDispatchers, KnoraSettings, KnoraSettingsImpl}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpecLike
import zio.Console.printLine
import zio.Schedule.{Decision, WithState}
import zio.{Schedule, _}

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

object IntegrationSpec {

  /*
        Loads the following (first-listed are higher priority):
            - system properties (e.g., -Dconfig.resource=test.conf)
            - test/resources/test.conf
            - main/resources/application.conf
   */
  val defaultConfig: Config = ConfigFactory.load()

  /* Copied from: akka/akka-testkit/src/test/scala/akka/testkit/AkkaSpec.scala */
  def getCallerName(clazz: Class[_]): String = {
    val s = (Thread.currentThread.getStackTrace map (_.getClassName) drop 1)
      .dropWhile(_ matches "(java.lang.Thread|.*UnitSpec.?$)")
    val reduced = s.lastIndexWhere(_ == clazz.getName) match {
      case -1 => s
      case z  => s drop (z + 1)
    }
    reduced.head.replaceFirst(""".*\.""", "").replaceAll("[^a-zA-Z_0-9]", "_")
  }
}

/**
 * Defines a base class used in tests where only a running Fuseki Container is needed.
 * Does not start the DSP-API server.
 */
abstract class IntegrationSpec(_config: Config)
    extends AsyncWordSpecLike
    with Matchers
    with BeforeAndAfterAll
    with LazyLogging {

  def this() =
    this(UnitSpec.defaultConfig)

  implicit val system: ActorSystem =
    ActorSystem(
      IntegrationSpec.getCallerName(classOf[IntegrationSpec]),
      TestContainerFuseki.PortConfig.withFallback(IntegrationSpec.defaultConfig)
    )

  implicit val settings: KnoraSettingsImpl = KnoraSettings(system)
  override implicit val executionContext: ExecutionContext =
    system.dispatchers.lookup(KnoraDispatchers.KnoraActorDispatcher)
  implicit val timeout: Timeout = settings.defaultTimeout

  // needs to be initialized early on
  StringFormatter.initForTest()

  protected def waitForReadyTriplestore(actorRef: ActorRef): Unit = {
    logger.info("Waiting for triplestore to be ready ...")
    implicit val ctx: MessageDispatcher = system.dispatchers.lookup(KnoraDispatchers.KnoraBlockingDispatcher)
    val checkTriplestore: ZIO[Any, Throwable, Unit] = for {
      checkResult <- ZIO.fromTry(
        Try(
          Await
            .result(actorRef ? CheckTriplestoreRequest(), 1.second.asScala)
            .asInstanceOf[CheckTriplestoreResponse]
        )
      )

      value <-
        if (checkResult.triplestoreStatus == TriplestoreStatus.ServiceAvailable) {
          ZIO.succeed(logger.info("... triplestore is ready."))
        } else {
          ZIO.fail(
            new Exception(
              s"Triplestore not ready: ${checkResult.triplestoreStatus}"
            )
          )
        }
    } yield value

    implicit val rt: Runtime[Clock with Console] = Runtime.default
    rt.unsafeRun(
      checkTriplestore
        .retry(ScheduleUtil.schedule)
        .foldZIO(ex => printLine("Exception Failed"), v => printLine(s"Succeeded with $v"))
    )
  }

  protected def loadTestData(
    actorRef: ActorRef,
    rdfDataObjects: Seq[RdfDataObject] = Seq.empty[RdfDataObject]
  ): Unit = {
    logger.info("Loading test data started ...")
    implicit val timeout: Timeout = Timeout(settings.defaultTimeout)
    Try(Await.result(actorRef ? ResetRepositoryContent(rdfDataObjects), 479999.milliseconds.asScala)) match {
      case Success(res) => logger.info("... loading test data done.")
      case Failure(e)   => logger.error(s"Loading test data failed: ${e.getMessage}")
    }
  }
}

object ScheduleUtil {

  /**
   * Retry every second for 60 times, i.e., 60 seconds in total.
   */
  def schedule[A]: WithState[(Long, Long), Console, Any, (Long, Long)] = Schedule.spaced(1.second) && Schedule
    .recurs(60)
    .onDecision({
      case (_, _, Decision.Done)              => printLine(s"done trying").orDie
      case (_, attempt, Decision.Continue(_)) => printLine(s"attempt #$attempt").orDie
    })
}

//// ZIO helpers ////
object LegacyRuntime {

  val runtime: Runtime[Clock with Console] = Runtime.default

  /**
   * Transforms a [[Task]] into a [[Future]].
   */
  def fromTask[Res](body: => Task[Res]): Future[Res] =
    runtime.unsafeRunToFuture(body)
}
