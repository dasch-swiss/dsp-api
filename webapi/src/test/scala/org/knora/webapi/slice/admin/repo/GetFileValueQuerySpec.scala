/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.repo

import org.eclipse.rdf4j.sparqlbuilder.core.query.ConstructQuery
import zio.test.*

import org.knora.webapi.slice.admin.domain.model.InternalFilename

object GetFileValueQuerySpec extends ZIOSpecDefault {

  private val testFilename: InternalFilename = InternalFilename.unsafeFrom("0001/test-image.jp2")

  // @formatter:off
  private val expectedQuery: String =
    """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
      |CONSTRUCT { ?fileValue ?objPred ?objObj .
      |?fileValue knora-base:attachedToProject ?resourceProject .
      |?fileValue knora-base:hasPermissions ?currentFileValuePermissions . }
      |WHERE { ?fileValue knora-base:internalFilename "0001/test-image.jp2" .
      |?currentFileValue knora-base:previousValue* ?fileValue ;
      |    knora-base:hasPermissions ?currentFileValuePermissions .
      |?resource ?prop ?currentFileValue ;
      |    knora-base:attachedToProject ?resourceProject .
      |?fileValue ?objPred ?objObj .
      |?currentFileValue knora-base:isDeleted false .
      |?resource knora-base:isDeleted false .
      |FILTER ( ?objPred != knora-base:previousValue ) }
      |""".stripMargin
  // @formatter:on

  override def spec: Spec[TestEnvironment, Any] = suite("GetFileValueQuerySpec")(
    test("should produce correct CONSTRUCT query for a filename") {
      val actual: ConstructQuery = GetFileValueQuery.build(testFilename)
      assertTrue(actual.getQueryString == expectedQuery)
    },
    test("should produce correct CONSTRUCT query for a filename with special characters") {
      val specialFilename: InternalFilename = InternalFilename.unsafeFrom("0001/file-with-special_chars.jp2")
      val actual: ConstructQuery            = GetFileValueQuery.build(specialFilename)
      assertTrue(actual.getQueryString.contains("\"0001/file-with-special_chars.jp2\""))
    },
  )
}
