/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.service

import org.junit.runner.RunWith
import zio.*
import zio.config.*
import zio.telemetry.opentelemetry.tracing.Tracing
import zio.test.*

import dsp.errors.ConflictException
import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.IRI
import org.knora.webapi.TestDataFactory
import org.knora.webapi.config.AppConfig
import org.knora.webapi.config.ValueHasXmlBackfillConfig
import org.knora.webapi.core.TestAppConfig
import org.knora.webapi.messages.OntologyConstants.KnoraBase
import org.knora.webapi.messages.v2.responder.standoffmessages.MappingXMLtoStandoff
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.infrastructure.OtelSetup
import org.knora.webapi.slice.resources.repo.ValueHasXmlBackfillRepo

@RunWith(classOf[DspZTestJUnitRunner])
class ValueHasXmlBackfillServiceSpec extends ZIOSpecDefault {

  private val project: KnoraProject = TestDataFactory.someProject

  private final class StubRepo(
    candidates: Ref[Vector[IRI]],
    val calls: Ref[Vector[String]],
    selectCount: Ref[Int],
    failSelectOn: Option[Int],
    writesNothing: Boolean,
    gate: Option[Promise[Nothing, Unit]],
  ) extends ValueHasXmlBackfillRepo {

    override def selectCandidates(project: KnoraProject, limit: Int): Task[Seq[IRI]] =
      for {
        n <- selectCount.updateAndGet(_ + 1)
        _ <- calls.update(_ :+ "select")
        _ <- ZIO.foreachDiscard(gate)(_.await)
        _ <- ZIO.fail(new RuntimeException("select failed")).when(failSelectOn.contains(n))
        c <- candidates.get
      } yield c.take(limit)

    override def loadStandoff(project: KnoraProject, valueIris: Seq[IRI]): Task[Map[IRI, Seq[(IRI, String)]]] =
      calls.update(_ :+ "load").as {
        valueIris.flatMap { iri =>
          Seq(
            iri -> Seq(
              KnoraBase.ValueHasString   -> "text",
              KnoraBase.ValueHasMapping  -> mappingOf(iri),
              KnoraBase.ValueHasStandoff -> s"$iri/node",
            ),
            s"$iri/node" -> Seq(KnoraBase.StandoffTagHasStartIndex -> "0"),
          )
        }.toMap
      }

    override def insertXml(project: KnoraProject, values: Seq[(IRI, String)]): Task[Unit] =
      calls.update(_ :+ "insert") *>
        ZIO.unless(writesNothing)(candidates.update(_.filterNot(values.map(_._1).toSet))).unit
  }

  private def mappingOf(valueIri: IRI): IRI = "http://rdfh.ch/standoff/mappings/shared"

  private final class StubRenderer(
    failing: Set[IRI],
    returningNone: Set[IRI],
    val mappingLoads: Ref[Int],
  ) extends ValueHasXmlRenderer {
    override def loadMapping(mappingIri: IRI): Task[MappingXMLtoStandoff] =
      mappingLoads.update(_ + 1).as(MappingXMLtoStandoff(Map.empty, None))

    override def render(value: StoredTextValue, mapping: MappingXMLtoStandoff): Task[Option[String]] =
      if (failing(value.valueIri)) ZIO.fail(new IllegalStateException("secret user text"))
      else if (returningNone(value.valueIri)) ZIO.none
      else ZIO.some(s"<xml>${value.valueIri}</xml>")
  }

  private final case class Fixture(service: ValueHasXmlBackfillService, repo: StubRepo, renderer: StubRenderer)

  private def fixture(
    values: Seq[String],
    batchSize: Int = 2,
    interval: java.time.Duration = java.time.Duration.ofMillis(1),
    maxFailures: Int = 100,
    failing: Set[IRI] = Set.empty,
    returningNone: Set[IRI] = Set.empty,
    failSelectOn: Option[Int] = None,
    writesNothing: Boolean = false,
    gate: Option[Promise[Nothing, Unit]] = None,
  ): ZIO[Tracing, Nothing, Fixture] =
    for {
      tracing   <- ZIO.service[Tracing]
      appConfig <- read(AppConfig.config from TestAppConfig.provider()).orDie
                     .map(_.copy(valueHasXmlBackfill = ValueHasXmlBackfillConfig(batchSize, interval, maxFailures)))
      candidates <- Ref.make(values.toVector)
      calls      <- Ref.make(Vector.empty[String])
      selects    <- Ref.make(0)
      loads      <- Ref.make(0)
      running    <- Ref.make(Option.empty[ProjectIri])
      repo        = StubRepo(candidates, calls, selects, failSelectOn, writesNothing, gate)
      renderer    = StubRenderer(failing, returningNone, loads)
    } yield Fixture(ValueHasXmlBackfillService(repo, renderer, appConfig, tracing, running), repo, renderer)

  private def values(n: Int): Seq[String] = (1 to n).map(i => s"http://rdfh.ch/0001/v$i")

  private def awaitReleased(service: ValueHasXmlBackfillService): UIO[Unit] =
    service.running.get.repeatUntil(_.isEmpty).unit

  private def settledSelects(f: Fixture): UIO[Int] =
    Live.live(ZIO.sleep(100.millis)) *> f.repo.calls.get.map(_.count(_ == "select"))

  private val loopSuite = suite("run")(
    suite("loop")(
      test("stops with NoCandidates when only failed candidates are left and counts correctly") {
        val vs = values(5)
        for {
          f      <- fixture(vs, failing = Set(vs(1)))
          report <- f.service.run(project)
        } yield assertTrue(report == ValueHasXmlBackfillReport(5, 4, 1, false))
      },
      test("each select happens after the previous insert") {
        for {
          f      <- fixture(values(6))
          _      <- f.service.run(project)
          calls  <- f.repo.calls.get
          selects = calls.zipWithIndex.collect { case ("select", i) if i > 0 => calls(i - 1) }
        } yield assertTrue(selects.nonEmpty, selects.forall(_ == "insert"))
      },
      test("reaching maxFailures sets stoppedEarly") {
        val vs = values(6)
        for {
          f      <- fixture(vs, batchSize = 2, maxFailures = 2, failing = Set(vs(0), vs(1)))
          report <- f.service.run(project)
        } yield assertTrue(report.stoppedEarly, report.failed == 2)
      },
      test("a stalled run sets stoppedEarly") {
        for {
          f      <- fixture(values(3), writesNothing = true)
          report <- f.service.run(project)
        } yield assertTrue(report.stoppedEarly)
      },
      test("a batch-level failure fails the run") {
        for {
          f      <- fixture(values(6), failSelectOn = Some(2))
          result <- f.service.run(project).exit
        } yield assertTrue(result.isFailure)
      },
      test("a value-level failure does not fail the run") {
        val vs = values(2)
        for {
          f      <- fixture(vs, failing = Set(vs(0)))
          result <- f.service.run(project).exit
        } yield assertTrue(result.isSuccess)
      },
      test("a value-level failure logs the value IRI and the error class, never the error message") {
        val vs = values(2)
        for {
          f     <- fixture(vs, failing = Set(vs(0)))
          _     <- f.service.run(project)
          lines <- ZTestLogger.logOutput.map(_.map(_.message()))
        } yield assertTrue(
          lines.exists(line => line.contains(vs(0)) && line.contains("IllegalStateException")),
          !lines.exists(_.contains("secret user text")),
        )
      },
      test("a batch-level failure logs the partial report and the error class, never the error message") {
        for {
          f     <- fixture(values(4), failSelectOn = Some(2))
          _     <- f.service.run(project).exit
          lines <- ZTestLogger.logOutput.map(_.filter(_.logLevel == LogLevel.Error).map(_.message()))
        } yield assertTrue(
          lines.exists(line => line.contains("RuntimeException") && line.contains("found=2")),
          !lines.exists(_.contains("select failed")),
        )
      },
      test("a value rendered to None counts as failed") {
        val vs = values(2)
        for {
          f      <- fixture(vs, returningNone = Set(vs(0)))
          report <- f.service.run(project)
        } yield assertTrue(report.failed == 1, report.rendered == 1)
      },
      test("the mapping is loaded once for values sharing it") {
        for {
          f     <- fixture(values(6), batchSize = 3)
          _     <- f.service.run(project)
          loads <- f.renderer.mappingLoads.get
        } yield assertTrue(loads == 1)
      },
    ) @@ TestAspect.withLiveClock,
    test("batches are paced by the batch interval") {
      for {
        f      <- fixture(values(6), interval = java.time.Duration.ofSeconds(1))
        fiber  <- f.service.run(project).fork
        _      <- TestClock.adjust(0.seconds)
        before <- settledSelects(f)
        _      <- TestClock.adjust(1.second)
        mid    <- settledSelects(f)
        _      <- TestClock.adjust(1.second)
        third  <- settledSelects(f)
        _      <- TestClock.adjust(1.second)
        _      <- fiber.join
        after  <- f.repo.calls.get.map(_.count(_ == "select"))
      } yield assertTrue(before == 1, mid == 2, third == 3, after == 4)
    },
  )

  private val startSuite = suite("start")(
    test("a second start conflicts while one runs and succeeds after it ended") {
      for {
        gate   <- Promise.make[Nothing, Unit]
        f      <- fixture(values(2), gate = Some(gate))
        _      <- f.service.start(project)
        second <- f.service.start(project).either
        _      <- gate.succeed(())
        _      <- awaitReleased(f.service)
        third  <- f.service.start(project).either
      } yield assertTrue(second.left.exists(_.isInstanceOf[ConflictException]), third.isRight)
    },
    test("the guard is released after a failed run") {
      for {
        f     <- fixture(values(2), failSelectOn = Some(1))
        fiber <- f.service.startFiber(project)
        _     <- fiber.join
        held  <- f.service.running.get
      } yield assertTrue(held.isEmpty)
    },
    test("the guard is released after an interrupted run") {
      for {
        gate  <- Promise.make[Nothing, Unit]
        f     <- fixture(values(2), gate = Some(gate))
        fiber <- f.service.startFiber(project)
        _     <- fiber.interrupt
        held  <- f.service.running.get
      } yield assertTrue(held.isEmpty)
    },
  ) @@ TestAspect.withLiveClock @@ TestAspect.timeout(30.seconds)

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("ValueHasXmlBackfillService")(loopSuite, startSuite).provideSomeLayer[TestEnvironment](
      OtelSetup.stdOut.map(env => ZEnvironment(env.get[Tracing])),
    )
}
