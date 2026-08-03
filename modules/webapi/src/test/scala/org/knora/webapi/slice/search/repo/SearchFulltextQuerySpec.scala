/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.search.repo

import org.junit.runner.RunWith
import zio.IO
import zio.Runtime
import zio.Unsafe
import zio.test.*

import dsp.errors.SparqlGenerationException
import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.GoldenTest
import org.knora.webapi.messages.IriConversions.ConvertibleIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.common.KnoraIris.ResourceClassIri
import org.knora.webapi.util.ApacheLuceneSupport.LuceneQueryString

@RunWith(classOf[DspZTestJUnitRunner])
class SearchFulltextQuerySpec extends ZIOSpecDefault with GoldenTest {

  implicit val sf: StringFormatter = StringFormatter.getInitializedTestInstance

  private val searchTerms     = LuceneQueryString("test")
  private val testProjectIri  = ProjectIri.unsafeFrom("http://rdfh.ch/projects/0001")
  private val testResourceIri =
    ResourceClassIri.unsafeFrom("http://www.knora.org/ontology/0001/anything#Thing".toSmartIri)
  private val testStandoffIri = "http://www.knora.org/ontology/standoff#StandoffBoldTag".toSmartIri
  private val separator       = '\u001F'

  // `build` is effectful but pure for valid arguments; render it to a String and pass that expression straight to
  // `assertGolden`. A bare local val bound to the result collides with `assertGolden`'s own `actual` parameter during
  // inlining and makes its `assertTrue` macro lose the source position (a None.get in zio-test's showExpr).
  private def render(query: IO[SparqlGenerationException, String]): String =
    Unsafe.unsafe(implicit u => Runtime.default.unsafe.run(query).getOrThrow())

  // Invariants these goldens exist to protect. A golden pins the query text and cannot tell a correct query from a
  // plausible one — check them by eye when regenerating:
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
        val sparql = render(
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
        assertGolden(sparql, "countNoFilters")
      },
      test("count query with project and resource class limit") {
        val sparql = render(
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
        assertGolden(sparql, "countWithProjectAndClass")
      },
    ),
    suite("regular query")(
      test("minimal regular query") {
        val sparql = render(
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
        assertGolden(sparql, "searchNoFilters")
      },
      test("regular query with all filters") {
        val sparql = render(
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
        assertGolden(sparql, "searchWithAllFilters")
      },
    ),
    suite("probe query")(
      test("probe query without standoff") {
        val sparql = SearchFulltextQuery.buildProbe(searchTerms, None)
        assertGolden(sparql, "probeNoStandoff")
      },
      test("probe query with standoff") {
        val sparql = SearchFulltextQuery.buildProbe(LuceneQueryString("test search"), Some(testStandoffIri))
        assertGolden(sparql, "probeWithStandoff")
      },
    ),
    suite("escaping of user input")(
      test("apostrophe in search term is correctly escaped") {
        // The caller passes the raw user input — Rdf.literalOf handles SPARQL escaping.
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
        // rdf4j escapes ' even in double-quoted literals (valid SPARQL, just conservative)
        assertZIO(actual)(Assertion.containsString(""""Knight\'s""""))
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
        // " is escaped to \" by Rdf.literalOf for the SPARQL double-quoted literal
        assertZIO(actual)(Assertion.containsString(""""say \"hello\"""""))
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
