/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.v2

import org.junit.runner.RunWith
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.GoldenTest
import org.knora.webapi.messages.IriConversions.ConvertibleIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.common.KnoraIris.ResourceClassIri
import org.knora.webapi.util.FusekiLucenceQuery

@RunWith(classOf[DspZTestJUnitRunner])
class SearchQueriesSpec extends ZIOSpecDefault with GoldenTest {

  implicit val sf: StringFormatter = StringFormatter.getInitializedTestInstance

  private val luceneQuery      = FusekiLucenceQuery.unsafeFrom("Anton*")
  private val projectIri       = ProjectIri.unsafeFrom("http://rdfh.ch/projects/0001")
  private val resourceClassIri =
    ResourceClassIri.unsafeFrom("http://www.knora.org/ontology/0001/anything#Thing".toSmartIri)

  // The golden outputs must contain an explicit hit limit in the text:query list — without one, Jena
  // caps the Lucene lookup at 10'000 hits and silently drops matches before the project/class filters
  // apply, making resources unfindable (DEV-6822).
  override def spec: Spec[TestEnvironment, Any] = suite("SearchQueriesSpec")(
    test("selectCountByLabel should produce the correct query with project and resource class filters") {
      val query = SearchQueries.selectCountByLabel(luceneQuery, Some(projectIri), Some(resourceClassIri))
      assertGolden(query.sparql, "countWithProjectAndClass")
    },
    test("selectCountByLabel should produce the correct query without filters") {
      val query = SearchQueries.selectCountByLabel(luceneQuery, None, None)
      assertGolden(query.sparql, "countNoFilters")
    },
    test("constructSearchByLabel should produce the correct query with project and resource class filters") {
      val query =
        SearchQueries.constructSearchByLabel(luceneQuery, Some(projectIri), Some(resourceClassIri), 25, 0)
      assertGolden(query.sparql, "searchWithProjectAndClass")
    },
  )
}
