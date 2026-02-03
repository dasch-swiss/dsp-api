/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.repo

import org.eclipse.rdf4j.sparqlbuilder.core.query.SelectQuery
import zio.test.*

import org.knora.webapi.slice.admin.domain.model.InternalFilename

object FileValuePermissionsQuerySpec extends ZIOSpecDefault {

  private val testFilename: InternalFilename = InternalFilename.unsafeFrom("0001/test-image.jp2")

  // @formatter:off
  private val expectedQuery: String =
    """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
      |SELECT ?creator ?project ?permissions
      |WHERE { ?fileValue knora-base:internalFilename "0001/test-image.jp2" ;
      |    knora-base:attachedToUser ?creator .
      |?currentFileValue knora-base:previousValue* ?fileValue ;
      |    knora-base:hasPermissions ?permissions ;
      |    knora-base:isDeleted false .
      |?resource ?prop ?currentFileValue ;
      |    knora-base:attachedToProject ?project ;
      |    knora-base:isDeleted false . }
      |""".stripMargin
  // @formatter:on

  override def spec: Spec[TestEnvironment, Any] = suite("FileValuePermissionsQuerySpec")(
    test("should produce correct SELECT query for a filename") {
      val actual: SelectQuery = FileValuePermissionsQuery.build(testFilename)
      assertTrue(actual.getQueryString == expectedQuery)
    },
    test("should produce correct SELECT query for a filename with special characters") {
      val specialFilename: InternalFilename = InternalFilename.unsafeFrom("0001/file-with-special_chars.jp2")
      val actual: SelectQuery               = FileValuePermissionsQuery.build(specialFilename)
      assertTrue(actual.getQueryString.contains("\"0001/file-with-special_chars.jp2\""))
    },
  )
}
