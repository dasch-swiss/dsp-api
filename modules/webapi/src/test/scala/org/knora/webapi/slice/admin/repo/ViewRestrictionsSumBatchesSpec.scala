/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.repo

import org.junit.runner.RunWith
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.slice.admin.repo.ViewRestrictionsRepo.ValueCountRow

/** Unit tests for [[ViewRestrictionsRepo.sumBatches]]. */
@RunWith(classOf[DspZTestJUnitRunner])
class ViewRestrictionsSumBatchesSpec extends ZIOSpecDefault {

  private val thing   = "http://www.knora.org/ontology/0001/anything#Thing"
  private val hasText = "http://www.knora.org/ontology/0001/anything#hasText"
  private val open    = "V knora-admin:UnknownUser"

  override def spec: Spec[TestEnvironment, Any] = suite("ViewRestrictionsRepo.sumBatches")(
    test("rows with the same key add count and commented") {
      val rows = Seq(
        ValueCountRow(thing, hasText, open, deleted = false, count = 2, commented = 1),
        ValueCountRow(thing, hasText, open, deleted = false, count = 3, commented = 2),
      )
      assertTrue(
        ViewRestrictionsRepo.sumBatches(rows) ==
          Seq(ValueCountRow(thing, hasText, open, deleted = false, count = 5, commented = 3)),
      )
    },
    test("rows with different keys stay separate") {
      val rows = Seq(
        ValueCountRow(thing, hasText, open, deleted = false, count = 2, commented = 1),
        ValueCountRow(thing, hasText, open, deleted = true, count = 3, commented = 0),
        ValueCountRow(thing, hasText, "M knora-admin:ProjectMember", deleted = false, count = 1, commented = 0),
      )
      assertTrue(ViewRestrictionsRepo.sumBatches(rows).toSet == rows.toSet)
    },
  )
}
