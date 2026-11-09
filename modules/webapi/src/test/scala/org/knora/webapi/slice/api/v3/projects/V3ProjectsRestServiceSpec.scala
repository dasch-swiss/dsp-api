/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v3.projects

import org.junit.runner.RunWith
import zio.*
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.TestDataFactory
import org.knora.webapi.slice.`export`.domain.CurrentDataTask
import org.knora.webapi.slice.`export`.domain.ImportExistsError
import org.knora.webapi.slice.`export`.domain.ImportSlotBusyError
import org.knora.webapi.slice.api.v3.ErrorDetail
import org.knora.webapi.slice.api.v3.V3ErrorCode

@RunWith(classOf[DspZTestJUnitRunner])
class V3ProjectsRestServiceSpec extends ZIOSpecDefault {

  def spec: Spec[Any, Any] = suite("V3ProjectsRestService.importConflict")(
    test("a busy slot maps to import_running without details") {
      val conflict = V3ProjectsRestService.importConflict(ImportSlotBusyError())
      val message  = V3ErrorCode.import_running.template
      assertTrue(
        conflict.message == message,
        conflict.errors == Chunk(ErrorDetail(V3ErrorCode.import_running, message, Map.empty)),
      )
    },
    test("an existing import task maps to import_exists with its id and project") {
      for {
        task    <- CurrentDataTask.makeNew(TestDataFactory.someProject.id, TestDataFactory.User.rootUser.userIri)
        conflict = V3ProjectsRestService.importConflict(ImportExistsError(task))
        details  = Map("id" -> task.id.value, "projectIri" -> task.projectIri.value)
        message  = V3ErrorCode.import_exists.template
                    .replace("{id}", task.id.value)
                    .replace("{projectIri}", task.projectIri.value)
      } yield assertTrue(
        conflict.message == message,
        conflict.errors == Chunk(ErrorDetail(V3ErrorCode.import_exists, message, details)),
      )
    },
  )
}
