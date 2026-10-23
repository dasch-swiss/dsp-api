/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.v2

import org.apache.jena.query.QueryFactory
import org.junit.runner.RunWith
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.GoldenTest
import org.knora.webapi.messages.IriConversions.ConvertibleIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.slice.common.KnoraIris.ResourceClassIri
import org.knora.webapi.slice.common.domain.InternalIri
import org.knora.webapi.util.FusekiLucenceQuery

@RunWith(classOf[DspZTestJUnitRunner])
class SearchQueriesSpec extends ZIOSpecDefault with GoldenTest {

  implicit val sf: StringFormatter = StringFormatter.getInitializedTestInstance

  private val luceneQuery      = FusekiLucenceQuery.unsafeFrom("Anton*")
  private val scopedToProject  = Some(InternalIri("http://www.knora.org/data/0001/anything"))
  private val resourceClassIri =
    ResourceClassIri.unsafeFrom("http://www.knora.org/ontology/0001/anything#Thing".toSmartIri)

  // The legacy `SearchQueriesSpec__*.txt` fixtures (those read by `legacy`, not the `__scoped*` goldens) are the
  // verbatim output of the string-interpolating predecessor of SearchQueries — they are kept byte-unchanged and are
  // compared after canonicalisation by Jena, which normalises whitespace and prefix expansion but nothing else. Every
  // invariant listed below is visible in the canonical form, so it is still pinned.
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

  // The predecessor emitted `attachedToProject` for a project limit; the builders no longer do (a known project is a
  // data graph, an unknown one never reaches the builders). The fixtures that carry the join are compared without that
  // one statement, so every other part of them stays pinned.
  private val legacyProjectJoin =
    "?resource <http://www.knora.org/ontology/knora-base#attachedToProject> <http://rdfh.ch/projects/0001> ."

  private def matchesLegacyWithoutProjectJoin(actual: String, name: String) = {
    val fixture = legacy(name)
    assertTrue(
      fixture.contains(legacyProjectJoin),
      canonical(actual) == canonical(fixture.replace(legacyProjectJoin, "")),
    )
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
  //  - A data graph wraps only the text:query statement in GRAPH <dataGraph> { }, in the count and page queries
  //    alike, and no attachedToProject restriction is emitted. The class walk and every other pattern stay outside:
  //    the class walk reads ontology graphs, so a wider GRAPH returns no rows. Without a data graph the lookup is
  //    unscoped (docs/adr/0012-graph-scoped-lucene-text-lookups.md).
  //
  // New cases use exact-text goldens (`GoldenTest`), not legacy fixtures.
  override def spec: Spec[TestEnvironment, Any] = suite("SearchQueriesSpec")(
    test(
      "selectCountByLabel should produce the correct query with a resource class filter (legacy fixture without the project join)",
    ) {
      val query = SearchQueries.selectCountByLabel(luceneQuery, None, Some(resourceClassIri))
      matchesLegacyWithoutProjectJoin(query.sparql, "countWithProjectAndClass")
    },
    test("selectCountByLabel should produce the correct query without filters") {
      val query = SearchQueries.selectCountByLabel(luceneQuery, None, None)
      assertTrue(canonical(query.sparql) == canonical(legacy("countNoFilters")))
    },
    test(
      "constructSearchByLabel should produce the correct query with a resource class filter (legacy fixture without the project join)",
    ) {
      val query = SearchQueries.constructSearchByLabel(luceneQuery, None, Some(resourceClassIri), 25, 0)
      matchesLegacyWithoutProjectJoin(query.sparql, "searchWithProjectAndClass")
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
    suite("scoped to a known project")(
      test("count query with class limit") {
        val query = SearchQueries.selectCountByLabel(luceneQuery, scopedToProject, Some(resourceClassIri))
        assertGolden(query.sparql, "scopedCountWithClass")
      },
      test("page query with class limit") {
        val query = SearchQueries.constructSearchByLabel(luceneQuery, scopedToProject, Some(resourceClassIri), 25, 0)
        assertGolden(query.sparql, "scopedSearchWithClass")
      },
    ),
  )
}
