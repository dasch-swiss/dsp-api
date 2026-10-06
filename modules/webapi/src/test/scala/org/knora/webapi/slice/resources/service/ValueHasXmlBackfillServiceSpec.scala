/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.service

import org.junit.runner.RunWith
import zio.*
import zio.test.*

import dsp.errors.ConflictException
import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.IRI
import org.knora.webapi.TestDataFactory
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.resources.repo.service.ValueHasXmlBackfillRepoInMemory

@RunWith(classOf[DspZTestJUnitRunner])
class ValueHasXmlBackfillServiceSpec extends ZIOSpecDefault {

  private val project: KnoraProject = TestDataFactory.someProject

  private final class StubRenderer(failing: Set[IRI], returningNone: Set[IRI]) extends ValueHasXmlRenderer {
    override def render(value: StoredTextValue): Task[Option[String]] =
      if (failing(value.valueIri)) ZIO.fail(new IllegalStateException("secret user text"))
      else if (returningNone(value.valueIri)) ZIO.none
      else ZIO.some(s"<xml>${value.valueIri}</xml>")
  }

  private final case class Fixture(service: ValueHasXmlBackfillService, repo: ValueHasXmlBackfillRepoInMemory)

  private def fixture(
    values: Seq[String],
    batchInterval: Duration = 1.milli,
    failing: Set[IRI] = Set.empty,
    returningNone: Set[IRI] = Set.empty,
    failSelectOn: Option[Int] = None,
    failLoadOn: Option[Int] = None,
    gate: Option[Promise[Nothing, Unit]] = None,
  ): UIO[Fixture] =
    for {
      repo    <- ValueHasXmlBackfillRepoInMemory.make(values, failSelectOn, failLoadOn, gate)
      running <- Ref.make(false)
      renderer = StubRenderer(failing, returningNone)
    } yield Fixture(new ValueHasXmlBackfillService(repo, renderer, running, batchSize = 2, batchInterval), repo)

  private def values(n: Int): Seq[String] = (1 to n).map(i => s"http://rdfh.ch/0001/v$i")

  private def awaitReleased(service: ValueHasXmlBackfillService): UIO[Unit] =
    service.running.get.repeat(Schedule.recurUntil[Boolean](!_) && Schedule.spaced(10.millis)).unit

  /** Returns once the forked run is suspended in the batch-interval sleep of the test clock. */
  private val awaitPacing: UIO[Unit] =
    ZIO.yieldNow.repeatUntilZIO(_ => TestClock.sleeps.map(_.nonEmpty)).unit

  private def loads(f: Fixture): UIO[Int] = f.repo.calls.get.map(_.count(_ == "load"))

  private val runSuite = suite("run")(
    suite("loop")(
      test("processes every candidate and counts the failed ones") {
        val vs = values(5)
        for {
          f      <- fixture(vs, failing = Set(vs(1)))
          counts <- f.service.run(project)
        } yield assertTrue(counts == ValueHasXmlBackfillCounts(5, 4, 1))
      },
      test("the candidate SELECT runs exactly once per run, before the first load") {
        for {
          f     <- fixture(values(6))
          _     <- f.service.run(project)
          calls <- f.repo.calls.get
        } yield assertTrue(calls.count(_ == "select") == 1, calls.head == "select", calls.count(_ == "load") == 3)
      },
      test("a batch-level failure fails the run") {
        for {
          f      <- fixture(values(6), failLoadOn = Some(2))
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
      test("a batch-level failure logs the error class, never the error message") {
        for {
          f     <- fixture(values(4), failLoadOn = Some(2))
          _     <- f.service.run(project).exit
          lines <- ZTestLogger.logOutput.map(_.filter(_.logLevel == LogLevel.Error).map(_.message()))
        } yield assertTrue(
          lines.exists(_.contains("RuntimeException")),
          !lines.exists(_.contains("load failed")),
        )
      },
      test("no insert is issued when no value rendered") {
        val vs = values(2)
        for {
          f     <- fixture(vs, failing = vs.toSet)
          _     <- f.service.run(project)
          calls <- f.repo.calls.get
        } yield assertTrue(!calls.contains("insert"))
      },
      test("a value rendered to None counts as failed") {
        val vs = values(2)
        for {
          f      <- fixture(vs, returningNone = Set(vs(0)))
          counts <- f.service.run(project)
        } yield assertTrue(counts.failed == 1, counts.rendered == 1)
      },
    ) @@ TestAspect.withLiveClock,
    test("the run pauses for the batch interval between batches") {
      for {
        f      <- fixture(values(6), batchInterval = 1.second)
        fiber  <- f.service.run(project).fork
        _      <- awaitPacing
        first  <- loads(f)
        _      <- TestClock.adjust(1.second)
        _      <- awaitPacing
        second <- loads(f)
        _      <- TestClock.adjust(1.second)
        _      <- fiber.join
        third  <- loads(f)
      } yield assertTrue(first == 1, second == 2, third == 3)
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
        f    <- fixture(values(2), failSelectOn = Some(1))
        _    <- f.service.start(project)
        _    <- awaitReleased(f.service)
        held <- f.service.running.get
      } yield assertTrue(!held)
    },
  ) @@ TestAspect.withLiveClock @@ TestAspect.timeout(30.seconds)

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("ValueHasXmlBackfillService")(runSuite, startSuite)
}
