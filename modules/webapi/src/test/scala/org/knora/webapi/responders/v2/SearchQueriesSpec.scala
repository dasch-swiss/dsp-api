/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.v2

import org.apache.jena.query.QueryFactory
import org.junit.runner.RunWith
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.messages.IriConversions.ConvertibleIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.common.KnoraIris.ResourceClassIri
import org.knora.webapi.util.FusekiLucenceQuery

@RunWith(classOf[DspZTestJUnitRunner])
class SearchQueriesSpec extends ZIOSpecDefault {

  implicit val sf: StringFormatter = StringFormatter.getInitializedTestInstance

  private val luceneQuery      = FusekiLucenceQuery.unsafeFrom("Anton*")
  private val projectIri       = ProjectIri.unsafeFrom("http://rdfh.ch/projects/0001")
  private val resourceClassIri =
    ResourceClassIri.unsafeFrom("http://www.knora.org/ontology/0001/anything#Thing".toSmartIri)

  // The `SearchQueriesSpec__*.txt` files next to this spec are the verbatim output of the string-interpolating
  // predecessor of SearchQueries — they are kept byte-unchanged and are compared after canonicalisation by Jena,
  // which normalises whitespace and prefix expansion but nothing else. Every invariant listed below is visible in
  // the canonical form, so it is still pinned.
  private def legacy(name: String): String = {
    val resource = s"org/knora/webapi/responders/v2/SearchQueriesSpec__$name.txt"
    val stream   = Option(getClass.getClassLoader.getResourceAsStream(resource))
      .getOrElse(throw new IllegalStateException(s"Legacy query fixture not found on the classpath: $resource"))
    try new String(stream.readAllBytes(), "UTF-8")
    finally stream.close()
  }

  private def canonical(query: String): String = {
    val parsed = QueryFactory.create(query)
    parsed.getPrefixMapping.clearNsPrefixMap()
    parsed.toString
  }

  // Invariants these fixtures exist to protect. All were once broken silently, because a fixture pins the query
  // and cannot tell a correct query from a plausible one — check them by eye when changing a query:
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
      assertTrue(canonical(query.sparql) == canonical(legacy("countWithProjectAndClass")))
    },
    test("selectCountByLabel should produce the correct query without filters") {
      val query = SearchQueries.selectCountByLabel(luceneQuery, None, None)
      assertTrue(canonical(query.sparql) == canonical(legacy("countNoFilters")))
    },
    test("constructSearchByLabel should produce the correct query with project and resource class filters") {
      val query =
        SearchQueries.constructSearchByLabel(luceneQuery, Some(projectIri), Some(resourceClassIri), 25, 0)
      assertTrue(canonical(query.sparql) == canonical(legacy("searchWithProjectAndClass")))
    },
    test("constructSearchByLabel should produce the correct query without filters") {
      val query = SearchQueries.constructSearchByLabel(luceneQuery, None, None, 25, 0)
      assertTrue(canonical(query.sparql) == canonical(legacy("searchNoFilters")))
    },
    test("search terms are SPARQL-escaped exactly once") {
      // FusekiLucenceQuery.getQueryString is already SPARQL-escaped; the DSL literal must escape the raw value.
      val query = SearchQueries.selectCountByLabel(FusekiLucenceQuery.unsafeFrom("O'Brien*"), None, None)
      assertTrue(
        query.sparql.contains("""(rdfs:label "O\'Brien*" """),
        !query.sparql.contains("""\\'"""),
      )
    },
  )
}
