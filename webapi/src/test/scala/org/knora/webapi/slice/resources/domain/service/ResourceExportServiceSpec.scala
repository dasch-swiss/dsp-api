/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.domain.service

import zio.test.*
import zio.test.Assertion.*

import org.knora.webapi.slice.resources.api.model.ExportFormat
import org.knora.webapi.slice.resources.api.model.ExportRequest
import org.knora.webapi.slice.resources.api.model.ResourceExportRow

object ResourceExportServiceSpec extends ZIOSpecDefault {

  def spec: Spec[TestEnvironment, Any] =
    suite("ResourceExportService")(
      test("ExportRequest should have includeReferenceIris field with default true") {
        val exportRequest = ExportRequest(
          resourceClass = "http://example.com/Book",
          selectedProperties = Some(List("http://example.com/title")),
          format = ExportFormat.CSV,
        )

        assertTrue(exportRequest.includeReferenceIris == true)
      },
      test("ExportRequest should allow setting includeReferenceIris to false") {
        val exportRequest = ExportRequest(
          resourceClass = "http://example.com/Book",
          selectedProperties = Some(List("http://example.com/title")),
          format = ExportFormat.CSV,
          includeReferenceIris = false,
        )

        assertTrue(exportRequest.includeReferenceIris == false)
      },
      test("ResourceExportRow should handle getValue for existing property") {
        val exportRow = ResourceExportRow(
          resourceIri = "http://example.com/resource1",
          resourceClass = "http://example.com/Book",
          projectIri = "http://example.com/project1",
          properties = Map(
            "http://example.com/title"  -> "Test Book",
            "http://example.com/author" -> "Test Author",
          ),
        )

        assertTrue(exportRow.getValue("http://example.com/title") == "Test Book") &&
        assertTrue(exportRow.getValue("http://example.com/author") == "Test Author")
      },
      test("ResourceExportRow should return empty string for non-existing property") {
        val exportRow = ResourceExportRow(
          resourceIri = "http://example.com/resource1",
          resourceClass = "http://example.com/Book",
          projectIri = "http://example.com/project1",
          properties = Map("http://example.com/title" -> "Test Book"),
        )

        assertTrue(exportRow.getValue("http://example.com/nonexistent") == "")
      },
    )
}
