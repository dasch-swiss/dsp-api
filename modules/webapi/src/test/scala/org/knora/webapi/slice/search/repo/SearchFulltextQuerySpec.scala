/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.search.repo

import org.apache.jena.query.QueryFactory
import org.junit.runner.RunWith
import zio.IO
import zio.Runtime
import zio.Unsafe
import zio.test.*

import dsp.errors.SparqlGenerationException
import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.messages.IriConversions.ConvertibleIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.common.KnoraIris.ResourceClassIri
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Select
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.SparqlTimeout
import org.knora.webapi.util.ApacheLuceneSupport.LuceneQueryString

@RunWith(classOf[DspZTestJUnitRunner])
class SearchFulltextQuerySpec extends ZIOSpecDefault {

  implicit val sf: StringFormatter = StringFormatter.getInitializedTestInstance

  private val searchTerms     = LuceneQueryString("test")
  private val testProjectIri  = ProjectIri.unsafeFrom("http://rdfh.ch/projects/0001")
  private val testResourceIri =
    ResourceClassIri.unsafeFrom("http://www.knora.org/ontology/0001/anything#Thing".toSmartIri)
  private val testStandoffIri = "http://www.knora.org/ontology/standoff#StandoffBoldTag".toSmartIri
  private val separator       = StringFormatter.INFORMATION_SEPARATOR_ONE

  // `build` is effectful but pure for valid arguments; run it and compare the resulting query.
  private def render(query: IO[SparqlGenerationException, Select]): Select =
    Unsafe.unsafe(implicit u => Runtime.default.unsafe.run(query).getOrThrow())

  // The `SearchFulltextQuerySpec__*.txt` files next to this spec are the verbatim output of the
  // string-interpolating predecessor of SearchFulltextQuery — they are kept byte-unchanged and are compared after
  // canonicalisation by Jena, which normalises whitespace and prefix expansion but nothing else. Every invariant
  // listed below is visible in the canonical form, so it is still pinned.
  private def legacy(name: String): String = {
    val resource = s"org/knora/webapi/slice/search/repo/SearchFulltextQuerySpec__$name.txt"
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

  private def matchesLegacy(actual: Select, name: String) =
    assertTrue(canonical(actual.sparql) == canonical(legacy(name)))

  // Invariants these fixtures exist to protect. A fixture pins the query and cannot tell a correct query from a
  // plausible one — check them by eye when changing a query:
  //
  //  - The text:query list must carry an explicit hit limit (1000000). Without one Jena caps the Lucene lookup at
  //    10'000 hits and silently drops matches before the project/class filters apply (DEV-6716, DEV-6822).
  //  - The class restriction must use rdfs:subClassOf* — never subClassOf?. The subclass closure is not materialised
  //    and there is no query-time inference, so zero-or-one silently excludes every class more than one hop below the
  //    target (DEV-6833). `?resource a ?resourceClass` is emitted only when a class restriction is requested.
  //  - Resource-ness is asserted via knora-base:creationDate, not a subClassOf* walk to knora-base:Resource; the
  //    value branch asserts value-ness via knora-base:valueCreationDate. Both replace per-hit property-path walks
  //    that DEV-6864 measured at 2.2-6.3x their cost for identical results. See SearchFulltextQuery and DEV-6850.
  //  - There is no outer SELECT DISTINCT: GROUP BY ?resource already deduplicates. The inner SELECT DISTINCT
  //    ?matchingSubject and the count branch's COUNT(DISTINCT ?resource) stay (DEV-6809 (d)).
  override def spec: Spec[TestEnvironment, Any] = suite("SearchFulltextQuery")(
    suite("count query")(
      test("minimal count query") {
        val query = render(
          SearchFulltextQuery.build(
            searchTerms = searchTerms,
            limitToProject = None,
            limitToResourceClass = None,
            limitToStandoffClass = None,
            returnFiles = false,
            separator = None,
            limit = 1,
            offset = 0,
            countQuery = true,
          ),
        )
        matchesLegacy(query, "countNoFilters") && assertTrue(query.timeout == SparqlTimeout.Search)
      },
      test("count query with project and resource class limit") {
        val query = render(
          SearchFulltextQuery.build(
            searchTerms = searchTerms,
            limitToProject = Some(testProjectIri),
            limitToResourceClass = Some(testResourceIri),
            limitToStandoffClass = None,
            returnFiles = false,
            separator = None,
            limit = 1,
            offset = 0,
            countQuery = true,
          ),
        )
        matchesLegacy(query, "countWithProjectAndClass")
      },
    ),
    suite("regular query")(
      test("minimal regular query") {
        val query = render(
          SearchFulltextQuery.build(
            searchTerms = searchTerms,
            limitToProject = None,
            limitToResourceClass = None,
            limitToStandoffClass = None,
            returnFiles = false,
            separator = Some(separator),
            limit = 25,
            offset = 0,
            countQuery = false,
          ),
        )
        matchesLegacy(query, "searchNoFilters")
      },
      test("regular query with all filters") {
        val query = render(
          SearchFulltextQuery.build(
            searchTerms = LuceneQueryString("test search"),
            limitToProject = Some(testProjectIri),
            limitToResourceClass = Some(testResourceIri),
            limitToStandoffClass = Some(testStandoffIri),
            returnFiles = true,
            separator = Some(separator),
            limit = 25,
            offset = 50,
            countQuery = false,
          ),
        )
        matchesLegacy(query, "searchWithAllFilters")
      },
    ),
    suite("probe query")(
      test("probe query without standoff") {
        val query = SearchFulltextQuery.buildProbe(searchTerms, None)
        matchesLegacy(query, "probeNoStandoff") && assertTrue(query.timeout == SparqlTimeout.SearchProbe)
      },
      test("probe query with standoff") {
        val query = SearchFulltextQuery.buildProbe(LuceneQueryString("test search"), Some(testStandoffIri))
        matchesLegacy(query, "probeWithStandoff")
      },
    ),
    suite("escaping of user input")(
      test("apostrophe in search term is correctly escaped") {
        // The caller passes the raw user input — the typed Literal hole handles SPARQL escaping.
        val actual = SearchFulltextQuery.build(
          searchTerms = LuceneQueryString("Knight's"),
          limitToProject = None,
          limitToResourceClass = None,
          limitToStandoffClass = None,
          returnFiles = false,
          separator = None,
          limit = 1,
          offset = 0,
          countQuery = true,
        )
        // ' is escaped even inside a double-quoted literal (valid SPARQL, just conservative). The escape set is
        // byte-for-byte the one Rdf.literalOf applied before the migration.
        assertZIO(actual.map(_.sparql))(Assertion.containsString(""""Knight\'s""""))
      },
      test("double quote in search term is correctly escaped") {
        val actual = SearchFulltextQuery.build(
          searchTerms = LuceneQueryString("""say "hello""""),
          limitToProject = None,
          limitToResourceClass = None,
          limitToStandoffClass = None,
          returnFiles = false,
          separator = None,
          limit = 1,
          offset = 0,
          countQuery = true,
        )
        // " is escaped to \" for the SPARQL double-quoted literal
        assertZIO(actual.map(_.sparql))(Assertion.containsString(""""say \"hello\"""""))
      },
    ),
    suite("validation")(
      test("should fail when separator is missing for non-count query") {
        val effect = SearchFulltextQuery.build(
          searchTerms = searchTerms,
          limitToProject = None,
          limitToResourceClass = None,
          limitToStandoffClass = None,
          returnFiles = false,
          separator = None,
          limit = 25,
          offset = 0,
          countQuery = false,
        )
        assertZIO(effect.exit)(
          Assertion.failsWithA[SparqlGenerationException] &&
            Assertion.fails(Assertion.hasMessage(Assertion.containsString("Separator expected for non count query"))),
        )
      },
    ),
  )
}
