/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.admin

import org.junit.runner.RunWith
import sttp.client4.*
import sttp.model.StatusCode
import zio.*
import zio.json.ast.Json
import zio.test.*

import java.util.concurrent.ConcurrentLinkedQueue
import scala.jdk.CollectionConverters.*

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.E2EZSpec
import org.knora.webapi.e2ez.ValueHasXmlTestSupport.*
import org.knora.webapi.sharedtestdata.SharedTestDataADM.*
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.common.StandoffMappingIri
import org.knora.webapi.testservices.TestApiClient

/**
 * End-to-end proof of `POST /admin/maintenance/check-standoff-link-ref-counts`.
 *
 * The check only logs, so the spec captures the log lines of the application under test.
 */
@RunWith(classOf[DspZTestJUnitRunner])
class MaintenanceCheckStandoffLinkRefCountsE2ESpec extends E2EZSpec {

  private val checkUri      = uri"/admin/maintenance/check-standoff-link-ref-counts"
  private val pollSchedule  = Schedule.spaced(500.millis) && Schedule.recurs(120)
  private val summaryMarker = "of all projects finished"

  private val captured = new ConcurrentLinkedQueue[String]()

  private val capturingLogger: ZLogger[String, Unit] =
    (_, _, _, message, _, _, _, _) => { captured.add(message()); () }

  override protected def additionalLoggerLayer: ULayer[Unit] = Runtime.addLogger(capturingLogger)

  private def postCheck(user: User = rootUser): RIO[TestApiClient, StatusCode] =
    TestApiClient.postJson[Json](checkUri, user).map(_.code)

  private final case class StillRunning() extends RuntimeException("a check is still running")

  private def summaryLines: UIO[Seq[String]] =
    ZIO.succeed(captured.asScala.toSeq.filter(_.contains(summaryMarker)))

  /** Starts a check and waits for its summary line; a 409 means another run is active and is retried. */
  private val runCheckToCompletion: RIO[TestApiClient, Seq[String]] =
    for {
      before <- summaryLines.map(_.size)
      _      <- postCheck().flatMap {
             case StatusCode.Accepted => ZIO.unit
             case StatusCode.Conflict => ZIO.fail(StillRunning())
             case other               => ZIO.die(new IllegalStateException(s"unexpected status $other"))
           }.retry(pollSchedule)
      _     <- summaryLines.filterOrFail(_.size > before)(StillRunning()).retry(pollSchedule)
      lines <- ZIO.succeed(captured.asScala.toSeq)
    } yield lines

  private val linkingXml =
    """<?xml version="1.0" encoding="UTF-8"?>
      |<text documentType="html"><p>a text with a <a class="salsah-link" href="TARGET">link</a></p></text>""".stripMargin

  override val e2eSpec: Spec[env, Any] = suite("POST /admin/maintenance/check-standoff-link-ref-counts")(
    test("returns 403 when the authenticated user is not a SystemAdmin") {
      postCheck(normalUser).map(code => assertTrue(code == StatusCode.Forbidden))
    },
    test("logs a mismatch line for a link value whose count was changed") {
      for {
        target       <- createThing()
        source       <- createResourceWithText(linkingXml.replace("TARGET", target), StandoffMappingIri.StandardMapping)
        _            <- setStoredCount(source, 5)
        lines        <- runCheckToCompletion
        mismatchLines = lines.filter(l => l.contains("reference count mismatch") && l.contains(source))
      } yield assertTrue(
        mismatchLines.exists(l => l.contains(target) && l.contains("stored=5") && l.contains("expected=1")),
      )
    },
  )
}
