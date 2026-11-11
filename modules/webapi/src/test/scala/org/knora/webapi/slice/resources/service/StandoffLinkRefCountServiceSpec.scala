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
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.common.ValueIri
import org.knora.webapi.slice.resources.repo.service.StandoffLinkRefCountMismatch
import org.knora.webapi.slice.resources.repo.service.StandoffLinkRefCountRepoInMemory

@RunWith(classOf[DspZTestJUnitRunner])
class StandoffLinkRefCountServiceSpec extends ZIOSpecDefault {

  private val project: KnoraProject = TestDataFactory.someProject
  private val other: KnoraProject   = project.copy(shortcode = KnoraProject.Shortcode.unsafeFrom("0002"))

  private val mismatch = StandoffLinkRefCountMismatch(
    source = "http://rdfh.ch/0001/source",
    target = "http://rdfh.ch/0001/target",
    linkValue = Some("http://rdfh.ch/0001/source/values/link"),
    storedCount = Some(3),
    expectedCount = 1,
    directLink = true,
  )

  private def service(
    repo: StandoffLinkRefCountRepoInMemory,
    existingResources: Set[IRI] = Set.empty,
  ): UIO[StandoffLinkRefCountService] =
    Ref.make(false).map { running =>
      new StandoffLinkRefCountService(
        repo,
        iri => ZIO.succeed(existingResources(iri)),
        resource => ZIO.succeed(ValueIri.unsafeFrom(s"${resource.value}/values/minted")),
        running,
      )(using StringFormatter.getInitializedTestInstance)
    }

  private def serviceOf(
    repo: UIO[StandoffLinkRefCountRepoInMemory],
    existingResources: Set[IRI] = Set.empty,
  ): UIO[StandoffLinkRefCountService] =
    repo.flatMap(service(_, existingResources))

  private def awaitReleased(service: StandoffLinkRefCountService): UIO[Unit] =
    service.running.get.repeat(Schedule.recurUntil[Boolean](!_) && Schedule.spaced(10.millis)).unit

  private val checkSuite = suite("check")(
    test("returns no mismatches and logs INFO for a consistent project") {
      for {
        s     <- serviceOf(StandoffLinkRefCountRepoInMemory.make())
        found <- s.check(project)
        lines <- ZTestLogger.logOutput
      } yield assertTrue(
        found.isEmpty,
        lines.map(_.logLevel) == Chunk(LogLevel.Info),
        lines.head.message().contains("mismatches=0"),
      )
    },
    test("logs the count and one WARN line per mismatch") {
      for {
        s     <- serviceOf(StandoffLinkRefCountRepoInMemory.make(Map("0001" -> Seq(mismatch))))
        found <- s.check(project)
        lines <- ZTestLogger.logOutput
        warns  = lines.filter(_.logLevel == LogLevel.Warning).map(_.message())
      } yield assertTrue(
        found == Seq(mismatch),
        warns.exists(_.contains("mismatches=1")),
        warns.exists(l =>
          l.contains(mismatch.source) && l.contains(mismatch.target) && l.contains("stored=3") &&
            l.contains("expected=1") && l.contains("directLink=true"),
        ),
        lines.forall(_.annotations.get("shortcode").contains("0001")),
      )
    },
    test("a failing project logs the error class, never the error message, and fails") {
      for {
        s      <- serviceOf(StandoffLinkRefCountRepoInMemory.make(failing = Set("0001")))
        result <- s.check(project).exit
        lines  <- ZTestLogger.logOutput.map(_.filter(_.logLevel == LogLevel.Error).map(_.message()))
      } yield assertTrue(
        result.isFailure,
        lines.exists(_.contains("RuntimeException")),
        !lines.exists(_.contains("secret triplestore detail")),
      )
    },
  )

  private val checkAllSuite = suite("checkAll")(
    test("sums the mismatches of every project and logs WARN") {
      for {
        s <- serviceOf(
               StandoffLinkRefCountRepoInMemory.make(Map("0001" -> Seq(mismatch, mismatch), "0002" -> Seq(mismatch))),
             )
        summary <- s.checkAll(Seq(project, other))
        lines   <- ZTestLogger.logOutput.map(_.filter(_.message().contains("of all projects finished")))
      } yield assertTrue(
        summary == StandoffLinkRefCountSummary(2, 0, 3),
        lines.map(_.logLevel) == Chunk(LogLevel.Warning),
        lines.head.message().contains("projects=2 projectsFailed=0 mismatches=3"),
      )
    },
    test("a failed project does not stop the next project and the summary line is ERROR") {
      for {
        s       <- serviceOf(StandoffLinkRefCountRepoInMemory.make(Map("0002" -> Seq(mismatch)), failing = Set("0001")))
        summary <- s.checkAll(Seq(project, other))
        lines   <- ZTestLogger.logOutput.map(_.filter(_.message().contains("of all projects finished")))
      } yield assertTrue(
        summary == StandoffLinkRefCountSummary(2, 1, 1),
        lines.map(_.logLevel) == Chunk(LogLevel.Error),
      )
    },
    test("a defect in one project is a failed project, the next is checked and the summary is logged") {
      for {
        s       <- serviceOf(StandoffLinkRefCountRepoInMemory.make(Map("0002" -> Seq(mismatch)), dying = Set("0001")))
        summary <- s.checkAll(Seq(project, other))
        lines   <- ZTestLogger.logOutput.map(_.filter(_.message().contains("of all projects finished")))
      } yield assertTrue(
        summary == StandoffLinkRefCountSummary(2, 1, 1),
        lines.map(_.logLevel) == Chunk(LogLevel.Error),
      )
    },
    test("the summary line is INFO without mismatches") {
      for {
        s     <- serviceOf(StandoffLinkRefCountRepoInMemory.make())
        _     <- s.checkAll(Seq(project, other))
        lines <- ZTestLogger.logOutput.map(_.filter(_.message().contains("of all projects finished")))
      } yield assertTrue(lines.map(_.logLevel) == Chunk(LogLevel.Info))
    },
  )

  private val startSuite = suite("startCheck")(
    test("a second start conflicts while one runs and succeeds after it ended") {
      for {
        gate   <- Promise.make[Nothing, Unit]
        s      <- serviceOf(StandoffLinkRefCountRepoInMemory.make(gate = Some(gate)))
        _      <- s.startCheck(Seq(project))
        second <- s.startCheck(Seq(project)).either
        _      <- gate.succeed(())
        _      <- awaitReleased(s)
        third  <- s.startCheck(Seq(project)).either
      } yield assertTrue(second.left.exists(_.isInstanceOf[ConflictException]), third.isRight)
    },
    test("the guard is released after a run whose project failed") {
      for {
        s    <- serviceOf(StandoffLinkRefCountRepoInMemory.make(failing = Set("0001")))
        _    <- s.startCheck(Seq(project))
        _    <- awaitReleased(s)
        held <- s.running.get
      } yield assertTrue(!held)
    },
    test("the guard is released after a run with a defect in a project") {
      for {
        s     <- serviceOf(StandoffLinkRefCountRepoInMemory.make(dying = Set("0001")))
        _     <- s.startCheck(Seq(project, other))
        _     <- awaitReleased(s)
        held  <- s.running.get
        lines <- ZTestLogger.logOutput.map(_.filter(_.message().contains("of all projects finished")))
      } yield assertTrue(!held, lines.size == 1)
    },
  ) @@ TestAspect.withLiveClock @@ TestAspect.timeout(30.seconds)

  private val repairable = Map("0001" -> Seq(mismatch))

  private val secondMismatch = mismatch.copy(
    source = "http://rdfh.ch/0001/source2",
    linkValue = Some("http://rdfh.ch/0001/source2/values/link"),
  )
  private val twoRows = Map("0001" -> Seq(mismatch, secondMismatch))

  private def infos(lines: Chunk[ZTestLogger.LogEntry], text: String): Chunk[String] =
    lines.filter(_.logLevel == LogLevel.Info).map(_.message()).filter(_.contains(text))

  private val repairSuite = suite("repair")(
    test("a dry run writes nothing and logs one planned line and the summary") {
      for {
        repo    <- StandoffLinkRefCountRepoInMemory.make(repairable)
        s       <- service(repo, Set(mismatch.target))
        summary <- s.repair(project, dryRun = true)
        applied <- repo.applied.get
        lines   <- ZTestLogger.logOutput
        planned  = infos(lines, "repair planned")
      } yield assertTrue(
        applied.isEmpty,
        summary.planned == 1,
        summary.applied == 0,
        summary.remaining.isEmpty,
        planned.size == 1,
        planned.head.contains(mismatch.source) && planned.head.contains(mismatch.target) &&
          planned.head.contains("oldCount=3") && planned.head.contains("newCount=1") &&
          planned.head.contains("directLink=none"),
        infos(lines, "repair finished").size == 1,
        lines.forall(_.annotations.get("shortcode").contains("0001")),
      )
    },
    test("a real run applies each repair, logs it and reports the remaining mismatches") {
      for {
        repo    <- StandoffLinkRefCountRepoInMemory.make(repairable)
        s       <- service(repo, Set(mismatch.target))
        summary <- s.repair(project, dryRun = false)
        applied <- repo.applied.get
        lines   <- ZTestLogger.logOutput
      } yield assertTrue(
        applied.map(_._1) == Chunk(mismatch.source),
        applied.head._2.newLinkValueIri.value == s"${mismatch.source}/values/minted",
        summary.applied == 1,
        summary.planned == 0,
        summary.remaining.contains(1),
        infos(lines, "repair applied").size == 1,
        infos(lines, "remaining mismatches=1").size == 1,
        infos(lines, "repair finished").size == 1,
      )
    },
    test("a repair that no longer applies is logged as stale") {
      for {
        repo    <- StandoffLinkRefCountRepoInMemory.make(repairable, applyResult = false)
        s       <- service(repo, Set(mismatch.target))
        summary <- s.repair(project, dryRun = false)
        warns   <- ZTestLogger.logOutput.map(_.filter(_.logLevel == LogLevel.Warning).map(_.message()))
      } yield assertTrue(
        summary.stale == 1,
        summary.applied == 0,
        warns.exists(_.contains("changed since the check")),
      )
    },
    test("a missing target is skipped with a WARN line and nothing is applied") {
      for {
        repo    <- StandoffLinkRefCountRepoInMemory.make(repairable)
        s       <- service(repo)
        summary <- s.repair(project, dryRun = false)
        applied <- repo.applied.get
        warns   <- ZTestLogger.logOutput.map(_.filter(_.logLevel == LogLevel.Warning).map(_.message()))
      } yield assertTrue(
        applied.isEmpty,
        summary.skipped == Map(StandoffLinkRefCountSkipReason.TargetMissing -> 1),
        warns.exists(l => l.contains(mismatch.source) && l.contains(mismatch.target) && l.contains("TargetMissing")),
      )
    },
    test("a row whose repair fails does not stop the next row") {
      for {
        repo    <- StandoffLinkRefCountRepoInMemory.make(twoRows, applyFailing = Set(mismatch.source))
        s       <- service(repo, Set(mismatch.target))
        summary <- s.repair(project, dryRun = false)
        applied <- repo.applied.get
        errors  <- ZTestLogger.logOutput.map(_.filter(_.logLevel == LogLevel.Error).map(_.message()))
      } yield assertTrue(
        summary.failed == 1,
        summary.applied == 1,
        applied.map(_._1) == Chunk(mismatch.source, secondMismatch.source),
        errors.exists(l => l.contains(mismatch.source) && l.contains("RuntimeException")),
        !errors.exists(_.contains("secret triplestore detail")),
      )
    },
    test("a row whose repair dies does not stop the next row") {
      for {
        repo    <- StandoffLinkRefCountRepoInMemory.make(twoRows, applyDying = Set(mismatch.source))
        s       <- service(repo, Set(mismatch.target))
        summary <- s.repair(project, dryRun = false)
        applied <- repo.applied.get
      } yield assertTrue(
        summary.failed == 1,
        summary.applied == 1,
        applied.map(_._1) == Chunk(mismatch.source, secondMismatch.source),
      )
    },
    test("a failing project fails the repair and logs the error class only") {
      for {
        s      <- serviceOf(StandoffLinkRefCountRepoInMemory.make(failing = Set("0001")))
        result <- s.repair(project, dryRun = true).exit
        lines  <- ZTestLogger.logOutput.map(_.filter(_.logLevel == LogLevel.Error).map(_.message()))
      } yield assertTrue(
        result.isFailure,
        lines.exists(_.contains("RuntimeException")),
        !lines.exists(_.contains("secret triplestore detail")),
      )
    },
  )

  private val guardSuite = suite("run guard shared by check and repair")(
    test("a running check makes startRepair conflict") {
      for {
        gate   <- Promise.make[Nothing, Unit]
        s      <- serviceOf(StandoffLinkRefCountRepoInMemory.make(gate = Some(gate)))
        _      <- s.startCheck(Seq(project))
        repair <- s.startRepair(project, dryRun = true).either
        _      <- gate.succeed(())
        _      <- awaitReleased(s)
      } yield assertTrue(repair.left.exists(_.isInstanceOf[ConflictException]))
    },
    test("a running repair makes startCheck conflict and the guard is released afterwards") {
      for {
        gate  <- Promise.make[Nothing, Unit]
        s     <- serviceOf(StandoffLinkRefCountRepoInMemory.make(gate = Some(gate)))
        _     <- s.startRepair(project, dryRun = false)
        check <- s.startCheck(Seq(project)).either
        _     <- gate.succeed(())
        _     <- awaitReleased(s)
        again <- s.startRepair(project, dryRun = true).either
      } yield assertTrue(check.left.exists(_.isInstanceOf[ConflictException]), again.isRight)
    },
  ) @@ TestAspect.withLiveClock @@ TestAspect.timeout(30.seconds)

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("StandoffLinkRefCountService")(checkSuite, checkAllSuite, startSuite, repairSuite, guardSuite)
}
