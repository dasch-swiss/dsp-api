/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.v2

import org.junit.runner.RunWith
import zio.*
import zio.telemetry.opentelemetry.tracing.Tracing
import zio.test.*
import zio.test.Assertion.failsWithA

import dsp.errors.BadRequestException
import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.*
import org.knora.webapi.SchemaRendering.apiV2SchemaWithOption
import org.knora.webapi.config.AppConfig
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.messages.util.ConstructResponseUtilV2
import org.knora.webapi.messages.util.search.QueryTraverser
import org.knora.webapi.messages.util.search.gravsearch.prequery.InferenceOptimizationService
import org.knora.webapi.messages.util.search.gravsearch.transformers.ConstructTransformer
import org.knora.webapi.messages.util.search.gravsearch.transformers.OntologyInferencer
import org.knora.webapi.messages.util.search.gravsearch.types.GravsearchTypeInspectionRunner
import org.knora.webapi.sharedtestdata.SharedTestDataADM.*
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.service.KnoraProjectService
import org.knora.webapi.slice.admin.domain.service.ProjectDataGraphsLive
import org.knora.webapi.slice.common.domain.InternalIri
import org.knora.webapi.slice.common.service.IriConverter
import org.knora.webapi.slice.ontology.domain.service.OntologyCacheHelpers
import org.knora.webapi.slice.ontology.domain.service.OntologyRepo
import org.knora.webapi.slice.search.FulltextBreadthGuard
import org.knora.webapi.slice.search.FulltextBreadthGuard.BreadthKey
import org.knora.webapi.slice.search.repo.SearchFulltextQuery
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.util.ApacheLuceneSupport.LuceneQueryString

/**
 * Gravsearch `matchFulltext` runs behind the [[FulltextBreadthGuard]], page and count alike. Each test builds the
 * responder with a guard whose probe is a double, so breadth is decided without repository-scale data.
 */
@RunWith(classOf[DspZTestJUnitRunner])
class GravsearchFulltextBreadthGuardSpec extends E2EZSpec {

  override val rdfDataObjects: List[RdfDataObject] = List(
    RdfDataObject(
      path = "test_data/project_data/incunabula-data.ttl",
      name = "http://www.knora.org/data/0803/incunabula",
    ),
  )

  private val cap = 250000

  // Two terms, so FulltextSearchTerms.shouldProbe holds; both occur in incunabula book titles.
  private val term = "Zeitglöcklein Lebens"

  private val matchFulltextQuery =
    s"""PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
       |CONSTRUCT {
       |    ?mainRes knora-api:isMainResource true .
       |} WHERE {
       |    ?mainRes a knora-api:Resource .
       |    FILTER knora-api:matchFulltext(?mainRes, "$term")
       |}""".stripMargin

  private val plainQuery =
    """PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
      |PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
      |CONSTRUCT {
      |    ?book knora-api:isMainResource true .
      |} WHERE {
      |    ?book a incunabula:book .
      |}""".stripMargin

  private val scopedProbe = SearchFulltextQuery.buildProbe(
    LuceneQueryString(term),
    None,
    Some(InternalIri("http://www.knora.org/data/0803/incunabula")),
  )

  // What SearchResponderV2Live needs from the spec environment besides the guard double.
  private type ResponderDeps = AppConfig & TriplestoreService & ConstructResponseUtilV2 & OntologyCacheHelpers &
    QueryTraverser & OntologyInferencer & GravsearchTypeInspectionRunner & InferenceOptimizationService &
    StringFormatter & IriConverter & OntologyRepo & KnoraProjectService & Tracing

  private def responderWith(probe: BreadthKey => Task[Long]) =
    for {
      guard     <- FulltextBreadthGuard.make(cap, cacheCapacity = 16, cacheTtl = 1.minute, maxConcurrent = 4)(probe)
      responder <- ZLayer
                     .makeSome[ResponderDeps, SearchResponderV2Live](
                       ZLayer.succeed(guard),
                       ConstructTransformer.layer,
                       ProjectDataGraphsLive.layer,
                       SearchResponderV2Live.layer,
                     )
                     .build
                     .map(_.get[SearchResponderV2Live])
    } yield responder

  private def countingProbe(breadth: Long) =
    Ref.make(0).map(probes => (probes, (_: BreadthKey) => probes.update(_ + 1).as(breadth)))

  private def page(responder: SearchResponderV2, query: String, project: Option[ProjectIri] = None) =
    responder.gravsearchV2(query, apiV2SchemaWithOption(MarkupRendering.Xml), anonymousUser, project)

  private def count(responder: SearchResponderV2, query: String, project: Option[ProjectIri] = None) =
    responder.gravsearchCountV2(query, anonymousUser, project)

  override val e2eSpec = suite("Gravsearch matchFulltext behind the fulltext breadth guard")(
    test("refuses an over-cap term with a 400, page and count") {
      for {
        responder <- responderWith(_ => ZIO.succeed(cap.toLong + 1))
        pageExit  <- page(responder, matchFulltextQuery).exit
        countExit <- count(responder, matchFulltextQuery).exit
      } yield assert(pageExit)(failsWithA[BadRequestException]) && assert(countExit)(failsWithA[BadRequestException])
    },
    test("admits a term within the cap, after probing it") {
      for {
        (probes, probe) <- countingProbe(1L)
        responder       <- responderWith(probe)
        resources       <- page(responder, matchFulltextQuery)
        counted         <- count(responder, matchFulltextQuery)
        probed          <- probes.get
      } yield assertTrue(resources.resources.nonEmpty, counted.numberOfResources > 0, probed >= 1)
    },
    test("fails open when the probe errors") {
      for {
        responder <- responderWith(_ => ZIO.fail(new RuntimeException("probe down")))
        resources <- page(responder, matchFulltextQuery)
        counted   <- count(responder, matchFulltextQuery)
      } yield assertTrue(resources.resources.nonEmpty, counted.numberOfResources > 0)
    },
    test("never probes a query without matchFulltext") {
      for {
        (probes, probe) <- countingProbe(cap.toLong + 1)
        responder       <- responderWith(probe)
        resources       <- page(responder, plainQuery)
        counted         <- count(responder, plainQuery)
        probed          <- probes.get
      } yield assertTrue(resources.resources.nonEmpty, counted.numberOfResources > 0, probed == 0)
    },
    // The term is too broad repository-wide but rare in the project, so only the scoped probe admits it.
    test("measures a project-scoped matchFulltext with the scoped probe") {
      for {
        responder <- responderWith(key => ZIO.succeed(if (key.probeSparql == scopedProbe) 1L else cap.toLong + 1))
        scoped    <- page(responder, matchFulltextQuery, Some(incunabulaProjectIri))
        unscoped  <- page(responder, matchFulltextQuery).exit
      } yield assertTrue(scoped.resources.nonEmpty) && assert(unscoped)(failsWithA[BadRequestException])
    },
  )
}
