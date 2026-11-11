/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.repo.service

import org.junit.runner.RunWith
import zio.test.*

import dsp.errors.InconsistentRepositoryDataException
import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.messages.util.rdf.VariableResultsRow

@RunWith(classOf[DspZTestJUnitRunner])
class StandoffLinkRefCountRepoLiveSpec extends ZIOSpecDefault {

  private val source = "http://rdfh.ch/0001/res1"
  private val target = "http://rdfh.ch/0001/res2"
  private val link   = "http://rdfh.ch/0001/res1/values/l1"

  override def spec: Spec[TestEnvironment & zio.Scope, Any] = suite("StandoffLinkRefCountRepoLiveSpec")(
    test("parses a row with a link value") {
      val row = VariableResultsRow(
        Map(
          "source"        -> source,
          "target"        -> target,
          "linkValue"     -> link,
          "storedCount"   -> "2",
          "expectedCount" -> "1",
          "directLink"    -> "true",
        ),
      )
      assertTrue(
        StandoffLinkRefCountRepoLive.toMismatch(row) ==
          StandoffLinkRefCountMismatch(source, target, Some(link), Some(2), 1, true),
      )
    },
    test("parses a row without a link value") {
      val row = VariableResultsRow(
        Map("source" -> source, "target" -> target, "expectedCount" -> "1", "directLink" -> "false"),
      )
      assertTrue(
        StandoffLinkRefCountRepoLive.toMismatch(row) ==
          StandoffLinkRefCountMismatch(source, target, None, None, 1, false),
      )
    },
    test("fails on a malformed count") {
      val row = VariableResultsRow(
        Map("source" -> source, "target" -> target, "expectedCount" -> "x", "directLink" -> "false"),
      )
      assertTrue(scala.util.Try(StandoffLinkRefCountRepoLive.toMismatch(row)).failed.toOption.exists {
        case _: InconsistentRepositoryDataException => true
        case _                                      => false
      })
    },
  )
}
