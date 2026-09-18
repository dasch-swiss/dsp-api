/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.util.search.gravsearch.prequery

import org.junit.runner.RunWith
import zio.*
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.E2EZSpec
import org.knora.webapi.GoldenTest
import org.knora.webapi.config.AppConfig
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.util.search.*
import org.knora.webapi.messages.util.search.gravsearch.transformers.OntologyInferencer
import org.knora.webapi.messages.util.search.gravsearch.types.GravsearchTypeInspectionRunner
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri

@RunWith(classOf[DspZTestJUnitRunner])
class GravsearchToCountPrequeryTransformerE2ESpec extends E2EZSpec with GoldenTest {

  private implicit val sf: StringFormatter = StringFormatter.getInitializedTestInstance

  /** See [[GravsearchInferencePipelineTestSupport]] for why the golden snapshot needs the full pipeline. */
  private def transformQueryWithInference(
    query: String,
    limitResultsToProject: Option[ProjectIri] = None,
  ): ZIO[
    AppConfig & QueryTraverser & GravsearchTypeInspectionRunner & OntologyInferencer & InferenceOptimizationService,
    Throwable,
    SelectQuery,
  ] =
    GravsearchInferencePipelineTestSupport.transformQueryWithInference(
      query,
      (constructClause, typeInspectionResult, querySchema, appConfig) =>
        new GravsearchToCountPrequeryTransformer(
          constructClause,
          typeInspectionResult,
          querySchema,
          appConfig.v2.fulltextSearch.searchValueMinLength,
        ),
      dropOrderBy = true,
      limitResultsToProject = limitResultsToProject,
    )

  val queryClasslessMatchFulltext: String =
    """PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
      |CONSTRUCT {
      |    ?mainRes knora-api:isMainResource true .
      |} WHERE {
      |    ?mainRes a knora-api:Resource .
      |    FILTER knora-api:matchFulltext(?mainRes, "Zeitglöcklein")
      |}""".stripMargin

  val inputQueryWithDecimalOptionalSortCriterionAndFilter: String =
    """
      |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/simple/v2#>
      |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
      |
      |CONSTRUCT {
      |     ?thing knora-api:isMainResource true .
      |
      |     ?thing anything:hasDecimal ?decimal .
      |} WHERE {
      |
      |     ?thing a anything:Thing .
      |     ?thing a knora-api:Resource .
      |
      |     OPTIONAL {
      |        ?thing anything:hasDecimal ?decimal .
      |        anything:hasDecimal knora-api:objectType xsd:decimal .
      |
      |        ?decimal a xsd:decimal .
      |
      |        FILTER(?decimal > "2"^^xsd:decimal)
      |     }
      |} ORDER BY ASC(?decimal)
        """.stripMargin

  val inputQueryWithDecimalOptionalSortCriterionAndFilterComplex: String =
    """
      |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/v2#>
      |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
      |
      |CONSTRUCT {
      |     ?thing knora-api:isMainResource true .
      |
      |     ?thing anything:hasDecimal ?decimal .
      |} WHERE {
      |
      |     ?thing a anything:Thing .
      |     ?thing a knora-api:Resource .
      |
      |     OPTIONAL {
      |        ?thing anything:hasDecimal ?decimal .
      |
      |        ?decimal knora-api:decimalValueAsDecimal ?decimalVal .
      |
      |        FILTER(?decimalVal > "2"^^xsd:decimal)
      |     }
      |} ORDER BY ASC(?decimal)
        """.stripMargin

  val queryListNodeAnchor: String =
    """
      |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
      |PREFIX beol: <http://0.0.0.0:3333/ontology/0801/beol/v2#>
      |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
      |
      |CONSTRUCT {
      |  ?letter knora-api:isMainResource true .
      |  ?letter beol:hasSubject ?subj .
      |} WHERE {
      |  ?letter a beol:letter .
      |  ?letter beol:hasSubject ?subj .
      |  ?subj knora-api:listValueAsListNode <http://rdfh.ch/lists/0801/logarithmic_curves> .
      |}
        """.stripMargin

  override val e2eSpec = suite("The NonTriplestoreSpecificGravsearchToCountPrequeryGenerator object")(
    test("generate the fulltext-index-anchored matchFulltext expansion for a classless count query") {
      transformQueryWithInference(queryClasslessMatchFulltext)
        .map(actual => assertGolden(actual.toSparql, "classlessMatchFulltext"))
    },
    test("transform an input query with a decimal as an optional sort criterion and a filter") {
      transformQueryWithInference(inputQueryWithDecimalOptionalSortCriterionAndFilter)
        .map(actual => assertGolden(actual.toSparql, "decimalOptionalSortCriterionAndFilter"))
    },
    test(
      "transform an input query with a decimal as an optional sort criterion and a filter (submitted in complex schema)",
    ) {
      transformQueryWithInference(inputQueryWithDecimalOptionalSortCriterionAndFilterComplex)
        .map(actual => assertGolden(actual.toSparql, "decimalOptionalSortCriterionAndFilterComplex"))
    },
    test("transform a count query anchored on a list-node value") {
      transformQueryWithInference(queryListNodeAnchor)
        .map(actual =>
          assertGolden(actual.toSparql, "listNodeAnchor") &&
            assertGolden(GravsearchInferencePipelineTestSupport.shapeSummary(actual), "listNodeAnchorShape"),
        )
    },
  )
}
