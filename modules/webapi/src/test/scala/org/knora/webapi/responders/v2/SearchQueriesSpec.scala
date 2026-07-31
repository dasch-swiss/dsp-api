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

  // Invariants these goldens exist to protect. All were once broken silently, because a golden pins the query
  // text and cannot tell a correct query from a plausible one — check them by eye when regenerating:
  //
  //  - The text:query list must carry an explicit hit limit. Without one Jena caps the Lucene lookup at 10'000
  //    hits and silently drops matches before the project/class filters apply (DEV-6822).
  //  - The class restriction must use rdfs:subClassOf* — never subClassOf?. The subclass closure is not
  //    materialised and there is no query-time inference, so zero-or-one silently excluded every class more than
  //    one hop below the target, returning no results at all for deeper hierarchies (DEV-6833).
  //  - Standoff must be excluded by predicate (?valueObjectProperty != knora-base:valueHasStandoff), not by the
  //    object's type. `?valueObjectValue a knora-base:StandoffTag` matches nothing: standoff nodes carry concrete
  //    subclass types and nothing infers the base class (DEV-6833).
  //  - The count query asserts resource-ness via knora-base:creationDate rather than a subClassOf* walk to
  //    knora-base:Resource; see the note on selectCountByLabel and DEV-6850.
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
    test("constructSearchByLabel should produce the correct query without filters") {
      val query = SearchQueries.constructSearchByLabel(luceneQuery, None, None, 25, 0)
      assertGolden(query.sparql, "searchNoFilters")
    },
  )
}
