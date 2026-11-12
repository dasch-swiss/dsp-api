/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.admin

import org.junit.runner.RunWith
import sttp.model.StatusCode
import zio.*
import zio.json.ast.Json
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.E2EZSpec
import org.knora.webapi.e2ez.ValueHasXmlTestSupport.*
import org.knora.webapi.sharedtestdata.SharedTestDataADM.*
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.common.StandoffMappingIri
import org.knora.webapi.testservices.TestApiClient

/**
 * End-to-end proof of `POST /admin/maintenance/repair-standoff-link-ref-counts`.
 *
 * The repair only logs in a dry run, so the spec captures the log lines of the application under test.
 */
@RunWith(classOf[DspZTestJUnitRunner])
class MaintenanceRepairStandoffLinkRefCountsE2ESpec extends E2EZSpec {

  private val log = new LogCapture

  override protected def additionalLoggerLayer: ULayer[Unit] = Runtime.addLogger(log.logger)

  private def postRepair(body: Json, user: User = rootUser): RIO[TestApiClient, StatusCode] =
    TestApiClient.postJson[Json, Json](repairUri, body, user).map(_.code)

  private def bodyOf(fields: (String, Json)*): Json = Json.Obj(Chunk.from(fields))

  /** Starts a dry run of project 0001, waits for its summary line and returns all captured lines. */
  private val runDryRunToCompletion: RIO[TestApiClient, Seq[String]] =
    repairToCompletion(log, dryRun = true) *> log.lines

  private val linkingXml =
    """<?xml version="1.0" encoding="UTF-8"?>
      |<text documentType="html"><p>a text with a <a class="salsah-link" href="TARGET">link</a></p></text>""".stripMargin

  override val e2eSpec: Spec[env, Any] = suite("POST /admin/maintenance/repair-standoff-link-ref-counts")(
    test("returns 403 when the authenticated user is not a SystemAdmin") {
      postRepair(bodyOf("projectShortcode" -> Json.Str("0001")), normalUser).map(code =>
        assertTrue(code == StatusCode.Forbidden),
      )
    },
    test("returns 400 without a project shortcode") {
      postRepair(bodyOf("dryRun" -> Json.Bool(true))).map(code => assertTrue(code == StatusCode.BadRequest))
    },
    test("returns 400 for an unknown project shortcode") {
      postRepair(bodyOf("projectShortcode" -> Json.Str("FFFE"))).map(code => assertTrue(code == StatusCode.BadRequest))
    },
    test("a dry run logs the planned repair and writes nothing") {
      for {
        target        <- createThing()
        source        <- createResourceWithText(linkingXml.replace("TARGET", target), StandoffMappingIri.StandardMapping)
        _             <- setStoredCount(source, 5)
        lines         <- runDryRunToCompletion
        plannedLines   = lines.filter(l => l.contains("repair planned") && l.contains(source))
        countAfterRun <- storedCount(source)
      } yield assertTrue(
        plannedLines.exists(l => l.contains(target) && l.contains("oldCount=5") && l.contains("newCount=1")),
        !lines.exists(l => l.contains("repair applied") && l.contains(source)),
        countAfterRun.contains("5"),
      )
    },
  )
}
