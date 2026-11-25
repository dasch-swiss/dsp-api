/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.v2.responder

import org.junit.runner.RunWith
import zio.*
import zio.test.*

import dsp.errors.ForbiddenException
import dsp.errors.InconsistentRepositoryDataException
import org.knora.testrunner.DspZTestJUnitRunner

@RunWith(classOf[DspZTestJUnitRunner])
class CanDoResponseV2Spec extends ZIOSpecDefault {

  override def spec: Spec[Any, Throwable] =
    suite("CanDoResponseV2.unlessRejected")(
      test("answers canDo true when the check succeeds") {
        CanDoResponseV2.unlessRejected(ZIO.unit).map(response => assertTrue(response == CanDoResponseV2.yes))
      },
      test("answers canDo false with the message of a rejection") {
        CanDoResponseV2
          .unlessRejected(ZIO.fail(ForbiddenException("no delete permission")))
          .map(response => assertTrue(response == CanDoResponseV2.no("no delete permission")))
      },
      test("propagates a failure that is not a rejection") {
        val internal = InconsistentRepositoryDataException("triplestore answer is malformed")
        CanDoResponseV2.unlessRejected(ZIO.fail(internal)).exit.map(exit => assertTrue(exit == Exit.fail(internal)))
      },
      test("propagates a defect") {
        val defect = new IllegalStateException("boom")
        CanDoResponseV2
          .unlessRejected(ZIO.die(defect))
          .exit
          .map(exit => assertTrue(exit.causeOption.flatMap(_.dieOption).contains(defect)))
      },
    )
}
