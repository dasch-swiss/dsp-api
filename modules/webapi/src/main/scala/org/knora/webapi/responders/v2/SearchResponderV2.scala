/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.v2

import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import zio.*
import zio.telemetry.opentelemetry.tracing.StatusMapper
import zio.telemetry.opentelemetry.tracing.Tracing

import dsp.errors.AssertionException
import dsp.errors.BadRequestException
import dsp.errors.GravsearchException
import dsp.errors.InconsistentRepositoryDataException
import org.knora.webapi.*
import org.knora.webapi.config.AppConfig
import org.knora.webapi.messages.IriConversions.*
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.store.triplestoremessages.*
import org.knora.webapi.messages.util.ConstructResponseRdfData
import org.knora.webapi.messages.util.ConstructResponseRdfData.MainResourcesAndValueRdfData
import org.knora.webapi.messages.util.ConstructResponseRdfData.MappingAndXSLTransformation
import org.knora.webapi.messages.util.ConstructResponseUtilV2
import org.knora.webapi.messages.util.ErrorHandlingMap
import org.knora.webapi.messages.util.rdf.JsonLDDocument
import org.knora.webapi.messages.util.rdf.JsonLDInt
import org.knora.webapi.messages.util.rdf.JsonLDObject
import org.knora.webapi.messages.util.rdf.JsonLDString
import org.knora.webapi.messages.util.rdf.SparqlSelectResult
import org.knora.webapi.messages.util.rdf.SparqlSelectResultBody
import org.knora.webapi.messages.util.rdf.VariableResultsRow
import org.knora.webapi.messages.util.search.*
import org.knora.webapi.messages.util.search.gravsearch.GravsearchParser
import org.knora.webapi.messages.util.search.gravsearch.GravsearchQueryChecker
import org.knora.webapi.messages.util.search.gravsearch.mainquery.GravsearchMainQueryGenerator
import org.knora.webapi.messages.util.search.gravsearch.prequery.GravsearchToCountPrequeryTransformer
import org.knora.webapi.messages.util.search.gravsearch.prequery.GravsearchToPrequeryTransformer
import org.knora.webapi.messages.util.search.gravsearch.prequery.InferenceOptimizationService
import org.knora.webapi.messages.util.search.gravsearch.transformers.ConstructTransformer
import org.knora.webapi.messages.util.search.gravsearch.transformers.OntologyInferencer
import org.knora.webapi.messages.util.search.gravsearch.transformers.SelectTransformer
import org.knora.webapi.messages.util.search.gravsearch.types.*
import org.knora.webapi.messages.v2.responder.KnoraJsonLDResponseV2
import org.knora.webapi.messages.v2.responder.ontologymessages.ReadClassInfoV2
import org.knora.webapi.messages.v2.responder.ontologymessages.ReadPropertyInfoV2
import org.knora.webapi.messages.v2.responder.resourcemessages.*
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.common.KnoraIris.ResourceClassIri
import org.knora.webapi.slice.common.ResourceIri
import org.knora.webapi.slice.common.StandoffMappingIri
import org.knora.webapi.slice.common.service.IriConverter
import org.knora.webapi.slice.ontology.domain.service.OntologyCacheHelpers
import org.knora.webapi.slice.ontology.domain.service.OntologyRepo
import org.knora.webapi.slice.resources.repo.GetResourcePropertiesAndValuesQuery
import org.knora.webapi.slice.resources.repo.GetResourcesByClassInProjectPrequery
import org.knora.webapi.slice.search.repo.SearchFulltextQuery
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Construct
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Select
import org.knora.webapi.util.ApacheLuceneSupport
import org.knora.webapi.util.ApacheLuceneSupport.*

/**
 * Represents the number of resources found by a search query.
 */
case class ResourceCountV2(numberOfResources: Int) extends KnoraJsonLDResponseV2 {
  override def toJsonLDDocument(
    targetSchema: ApiV2Schema,
    appConfig: AppConfig,
    schemaOptions: Set[Rendering],
  ): JsonLDDocument =
    JsonLDDocument(
      body = JsonLDObject(
        Map(
          OntologyConstants.SchemaOrg.NumberOfItems -> JsonLDInt(numberOfResources),
        ),
      ),
      context = JsonLDObject(
        Map(
          "schema" -> JsonLDString(OntologyConstants.SchemaOrg.SchemaOrgPrefixExpansion),
        ),
      ),
    )
}

trait SearchResponderV2 {

  /**
   * Telemetry used to open the root `gravsearch` span and its per-stage child spans. Declared as an
   * abstract member (rather than injected only into the `Live` constructor) so the `IRI`-overload
   * default methods below can open the root + `gravsearch.parse` spans before delegating.
   */
  protected def tracing: Tracing

  /** Opens an INTERNAL stage span with uniform sanitized-error + interrupt handling (Decision 3). */
  protected final def stageSpan[A](name: String)(effect: Task[A]): Task[A] =
    SearchResponderV2.stageSpan(tracing, name)(effect)

  /** Sets `gravsearch.query.shape` + per-flag attributes + `gravsearch.schema_predicates` on the root span. */
  protected final def setShapeOnRoot(query: ConstructQuery, resultType: SearchResponderV2.QueryResultType): UIO[Unit] =
    SearchResponderV2.setShapeOnRoot(tracing, query, resultType)

  /**
   * Performs a search using a Gravsearch query provided by the client.
   *
   * @param query            a Gravsearch query provided by the client.
   * @param schemaAndOptions the target API schema and its options submitted with the request.
   * @param user             the client making the request.
   * @return a [[ReadResourcesSequenceV2]] representing the resources that have been found.
   */
  def gravsearchV2(
    query: ConstructQuery,
    schemaAndOptions: SchemaRendering,
    user: User,
    limitToProject: Option[ProjectIri] = None,
  ): Task[ReadResourcesSequenceV2]
  def gravsearchV2(
    query: IRI,
    rendering: SchemaRendering,
    user: User,
    limitToProject: Option[ProjectIri],
  ): Task[ReadResourcesSequenceV2] =
    stageSpan("gravsearch") {
      for {
        q <- stageSpan("gravsearch.parse")(ZIO.attempt(GravsearchParser.parseQuery(query)))
        _ <- setShapeOnRoot(q, SearchResponderV2.QueryResultType.ResourceList)
        r <- gravsearchV2(q, rendering, user, limitToProject)
      } yield r
    }

  /**
   * Performs a count query for a Gravsearch query provided by the user.
   *
   * @param query a Gravsearch query provided by the client.
   * @param user  the client making the request.
   * @return a [[ResourceCountV2]] representing the number of resources that have been found.
   */
  def gravsearchCountV2(query: ConstructQuery, user: User, limitToProject: Option[ProjectIri]): Task[ResourceCountV2]
  def gravsearchCountV2(query: IRI, user: User, limitToProject: Option[ProjectIri]): Task[ResourceCountV2] =
    stageSpan("gravsearch") {
      for {
        q <- stageSpan("gravsearch.parse")(ZIO.attempt(GravsearchParser.parseQuery(query)))
        _ <- setShapeOnRoot(q, SearchResponderV2.QueryResultType.Count)
        r <- gravsearchCountV2(q, user, limitToProject)
      } yield r
    }

  /**
   * Performs a Gravsearchquery to find resources that link to the specified resource.
   *
   * @param resourceIri the IRI of the resource to which incoming links are to be found.
   * @param offset      the offset to be used for paging.
   * @param rendering   the schema of the response.
   * @param user        the client making the request.
   * @return a [[ReadResourcesSequenceV2]] representing the linked resources that have been found.
   */
  def searchIncomingLinksV2(
    resourceIri: ResourceIri,
    offset: Int,
    rendering: SchemaRendering,
    user: User,
    limitToProject: Option[ProjectIri],
  ): Task[ReadResourcesSequenceV2]

  /**
   * Performs a Gravsearchquery to find StillImageRepresentations linked to the specified resource.
   *
   * @param resourceIri the IRI of the resource to which StillImageRepresentations are to be found.
   * @param offset      the offset to be used for paging.
   * @param rendering   the schema of the response.
   * @param user        the client making the request.
   * @return a [[ReadResourcesSequenceV2]] representing the StillImageRepresentations that have been found.
   */
  def searchStillImageRepresentationsV2(
    resourceIri: ResourceIri,
    offset: Int,
    rendering: SchemaRendering,
    user: User,
    limitToProject: Option[ProjectIri],
  ): Task[ReadResourcesSequenceV2]

  /**
   * Performs a Gravsearchquery to find StillImageRepresentations count linked to the specified resource.
   *
   * @param resourceIri     the IRI of the resource to which StillImageRepresentations are to be found.
   * @param user            the client making the request.
   * @param limitToProject  the option to limit the search to the specific project.
   * @return a [[ResourceCountV2]] representing the number of StillImageRepresentations that have been found.
   */
  def searchStillImageRepresentationsCountV2(
    resourceIri: ResourceIri,
    user: User,
    limitToProject: Option[ProjectIri],
  ): Task[ResourceCountV2]

  /**
   * Performs a Gravsearchquery to find regions linked to the specified resource.
   *
   * @param resourceIri the IRI of the resource to which incoming regions are to be found.
   * @param offset      the offset to be used for paging.
   * @param rendering   the schema of the response.
   * @param user        the client making the request.
   * @return a [[ReadResourcesSequenceV2]] representing the regions that have been found.
   */
  def searchIncomingRegionsV2(
    resourceIri: ResourceIri,
    offset: Int,
    rendering: SchemaRendering,
    user: User,
    limitToProject: Option[ProjectIri],
  ): Task[ReadResourcesSequenceV2]

  /**
   * Performs a fulltext search and returns the resources count (how many resources match the search criteria),
   * without taking into consideration permission checking.
   *
   * This method does not return the resources themselves.
   *
   * @param searchValue          the values to search for.
   * @param limitToProject       limit search to given project.
   * @param limitToResourceClass limit search to given resource class.
   * @return a [[ResourceCountV2]] representing the number of resources that have been found.
   */
  def fulltextSearchCountV2(
    searchValue: IRI,
    limitToProject: Option[ProjectIri],
    limitToResourceClass: Option[ResourceClassIri],
    limitToStandoffClass: Option[SmartIri],
  ): Task[ResourceCountV2]

  /**
   * Performs a fulltext search (simple search).
   *
   * @param searchValue          the values to search for.
   * @param offset               the offset to be used for paging.
   * @param limitToProject       limit search to given project.
   * @param limitToResourceClass limit search to given resource class.
   * @param limitToStandoffClass limit search to given standoff class.
   * @param returnFiles          if true, return any file value attached to each matching resource.
   * @param schemaAndOptions     the target API schema and the schema options submitted with the request.
   * @param requestingUser       the client making the request.
   * @return a [[ReadResourcesSequenceV2]] representing the resources that have been found.
   */
  def fulltextSearchV2(
    searchValue: IRI,
    offset: Int,
    limitToProject: Option[ProjectIri],
    limitToResourceClass: Option[ResourceClassIri],
    limitToStandoffClass: Option[SmartIri],
    returnFiles: Boolean,
    schemaAndOptions: SchemaRendering,
    requestingUser: User,
  ): Task[ReadResourcesSequenceV2]

  /**
   * Performs a count query for a search for resources by their rdfs:label.
   *
   * @param searchValue          the values to search for.
   * @param limitToProject       limit search to given project.
   * @param limitToResourceClass limit search to given resource class.
   * @return a [[ResourceCountV2]] representing the resources that have been found.
   */
  def searchResourcesByLabelCountV2(
    searchValue: String,
    limitToProject: Option[ProjectIri],
    limitToResourceClass: Option[ResourceClassIri],
  ): Task[ResourceCountV2]

  /**
   * Performs a search for resources by their rdfs:label.
   *
   * @param searchValue          the values to search for.
   * @param offset               the offset to be used for paging.
   * @param limitToProject       limit search to given project.
   * @param limitToResourceClass limit search to given resource class.
   * @param targetSchema         the schema of the response.
   * @param requestingUser       the client making the request.
   * @return a [[ReadResourcesSequenceV2]] representing the resources that have been found.
   */
  def searchResourcesByLabelV2(
    searchValue: String,
    offset: Int,
    limitToProject: Option[ProjectIri],
    limitToResourceClass: Option[ResourceClassIri],
    targetSchema: ApiV2Schema,
    requestingUser: User,
  ): Task[ReadResourcesSequenceV2]

  /**
   * Requests resources of the specified class from the specified project.
   *
   * @param projectIri       the IRI of the project.
   * @param resourceClass    the IRI of the resource class, in the complex schema.
   * @param orderByProperty  the IRI of the property that the resources are to be ordered by, in the complex schema.
   * @param page             the page number of the results page to be returned.
   * @param schemaAndOptions the schema of the response and schema options submitted with the request.
   * @param requestingUser   the user making the request.
   * @return a [[ReadResourcesSequenceV2]].
   */
  def searchResourcesByProjectAndClassV2(
    projectIri: ProjectIri,
    resourceClass: SmartIri,
    orderByProperty: Option[SmartIri],
    page: Int,
    schemaAndOptions: SchemaRendering,
    requestingUser: User,
  ): Task[ReadResourcesSequenceV2]
}

final class SearchResponderV2Live(
  appConfig: AppConfig,
  triplestore: TriplestoreService,
  constructResponseUtilV2: ConstructResponseUtilV2,
  ontologyCacheHelpers: OntologyCacheHelpers,
  queryTraverser: QueryTraverser,
  sparqlTransformerLive: OntologyInferencer,
  gravsearchTypeInspectionRunner: GravsearchTypeInspectionRunner,
  inferenceOptimizationService: InferenceOptimizationService,
  stringFormatter: StringFormatter,
  iriConverter: IriConverter,
  constructTransformer: ConstructTransformer,
  ontologyRepo: OntologyRepo,
  override protected val tracing: Tracing,
) extends SearchResponderV2 {

  private implicit val sf: StringFormatter = stringFormatter

  private def stillImageRepresentationsPreQueryBuilder(resourceIri: ResourceIri, offset: RuntimeFlags = 0) =
    s"""
       |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
       |
       |CONSTRUCT {
       |?page knora-api:isMainResource true .
       |
       |?page knora-api:seqnum ?seqnum .
       |
       |?page knora-api:hasStillImageFile ?file .
       |} WHERE {
       |
       |?page a knora-api:StillImageRepresentation .
       |?page a knora-api:Resource .
       |
       |?page knora-api:isPartOf <$resourceIri> .
       |knora-api:isPartOf knora-api:objectType knora-api:Resource .
       |
       |<$resourceIri> a knora-api:Resource .
       |
       |?page knora-api:seqnum ?seqnum .
       |knora-api:seqnum knora-api:objectType xsd:integer .
       |
       |?seqnum a xsd:integer .
       |
       |?page knora-api:hasStillImageFile ?file .
       |knora-api:hasStillImageFile knora-api:objectType knora-api:File .
       |
       |?file a knora-api:File .
       |
       |} ORDER BY ?seqnum
       |OFFSET $offset
       |""".stripMargin

  /**
   * Performs a Gravsearchquery to find resources that link to the specified resource.
   *
   * @param resourceIri     the IRI of the resource to which incoming links are to be found.
   * @param offset          the offset to be used for paging.
   * @param rendering       the schema of the response.
   * @param user            the client making the request.
   * @param limitToProject  the option to limit the search to the specific project.
   * @return a [[ReadResourcesSequenceV2]] representing the resources that have been found.
   */
  def searchIncomingLinksV2(
    resourceIri: ResourceIri,
    offset: Int,
    rendering: SchemaRendering,
    user: User,
    limitToProject: Option[ProjectIri],
  ): Task[ReadResourcesSequenceV2] = {
    val query: String =
      s"""
         |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
         |
         |CONSTRUCT {
         |?incomingRes knora-api:isMainResource true .
         |
         |?incomingRes ?incomingProp <$resourceIri> .
         |
         |} WHERE {
         |
         |?incomingRes a knora-api:Resource .
         |
         |?incomingRes ?incomingProp <$resourceIri> .
         |
         |<$resourceIri> a knora-api:Resource .
         |
         |?incomingProp knora-api:objectType knora-api:Resource .
         |
         |knora-api:isRegionOf knora-api:objectType knora-api:Resource .
         |knora-api:isPartOf knora-api:objectType knora-api:Resource .
         |
         |FILTER NOT EXISTS {
         |?incomingRes  knora-api:isRegionOf <$resourceIri> .
         |}
         |
         |FILTER NOT EXISTS {
         |?incomingRes  knora-api:isPartOf <$resourceIri> .
         |?incomingRes knora-api:seqnum ?seqnum .
         |}
         |
         |} OFFSET $offset
         |""".stripMargin

    gravsearchV2(query, rendering, user, limitToProject)
  }

  /**
   * Performs a Gravsearchquery to find StillImageRepresentations that link to the specified resource.
   *
   * @param resourceIri     the IRI of the resource to which StillImageRepresentations are to be found.
   * @param offset          the offset to be used for paging.
   * @param rendering       the schema of the response.
   * @param user            the client making the request.
   * @param limitToProject  the option to limit the search to the specific project.
   * @return a [[ReadResourcesSequenceV2]] representing the resources that have been found.
   */
  def searchStillImageRepresentationsV2(
    resourceIri: ResourceIri,
    offset: Int,
    rendering: SchemaRendering,
    user: User,
    limitToProject: Option[ProjectIri],
  ): Task[ReadResourcesSequenceV2] = {
    val query: String = stillImageRepresentationsPreQueryBuilder(resourceIri, offset)

    gravsearchV2(query, rendering, user, limitToProject)
  }

  /**
   * Performs a Gravsearchquery to find StillImageRepresentations count linked to the specified resource.
   *
   * @param resourceIri     the IRI of the resource to which StillImageRepresentations are to be found.
   * @param user            the client making the request.
   * @param limitToProject  the option to limit the search to the specific project.
   * @return a [[ResourceCountV2]] representing the number of StillImageRepresentations that have been found.
   */
  def searchStillImageRepresentationsCountV2(
    resourceIri: ResourceIri,
    user: User,
    limitToProject: Option[ProjectIri],
  ): Task[ResourceCountV2] = {
    val query: String = stillImageRepresentationsPreQueryBuilder(resourceIri)

    gravsearchCountV2(query, user, limitToProject)
  }

  /**
   * Performs a Gravsearchquery to find incoming Regions that link to the specified resource.
   *
   * @param resourceIri     the IRI of the resource to which incoming Regions are to be found.
   * @param offset          the offset to be used for paging.
   * @param rendering       the schema of the response.
   * @param user            the client making the request.
   * @param limitToProject  the option to limit the search to the specific project.
   * @return a [[ReadResourcesSequenceV2]] representing the resources that have been found.
   */
  def searchIncomingRegionsV2(
    resourceIri: ResourceIri,
    offset: Int,
    rendering: SchemaRendering,
    user: User,
    limitToProject: Option[ProjectIri],
  ): Task[ReadResourcesSequenceV2] = {
    val query: String =
      s"""
         |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
         |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
         |
         |CONSTRUCT {
         |  ?region knora-api:isMainResource true .
         |  ?region knora-api:hasGeometry ?geom .
         |  ?region knora-api:hasComment ?comment .
         |  ?region knora-api:hasColor ?color .
         |}
         |WHERE {
         |  ?region a knora-api:Region .
         |  ?region a knora-api:Resource .
         |
         |  ?region knora-api:isRegionOf <$resourceIri> .
         |  knora-api:isRegionOf knora-api:objectType knora-api:Resource .
         |  <$resourceIri> a knora-api:Resource .
         |
         |  ?region knora-api:hasGeometry ?geom .
         |  knora-api:hasGeometry knora-api:objectType knora-api:Geom .
         |  ?geom a knora-api:Geom .
         |
         |  OPTIONAL {
         |    ?region knora-api:hasComment ?comment .
         |    knora-api:hasComment knora-api:objectType xsd:string .
         |    ?comment a xsd:string .
         |  }
         |
         |  ?region knora-api:hasColor ?color .
         |  knora-api:hasColor knora-api:objectType knora-api:Color .
         |  ?color a knora-api:Color .
         |
         |  ?region rdfs:label ?label .
         |}
         |ORDER BY ?label
         |OFFSET $offset
         |""".stripMargin

    gravsearchV2(query, rendering, user, limitToProject)
  }

  /**
   * Performs a fulltext search and returns the resources count (how many resources match the search criteria),
   * without taking into consideration permission checking.
   *
   * This method does not return the resources themselves.
   *
   * @param searchValue          the values to search for.
   * @param limitToProject       limit search to given project.
   * @param limitToResourceClass limit search to given resource class.
   *
   * @return a [[ResourceCountV2]] representing the number of resources that have been found.
   */
  override def fulltextSearchCountV2(
    searchValue: IRI,
    limitToProject: Option[ProjectIri],
    limitToResourceClass: Option[ResourceClassIri],
    limitToStandoffClass: Option[SmartIri],
  ): Task[ResourceCountV2] =
    for {
      _                    <- ensureIsFulltextSearch(searchValue)
      _                    <- validateSearchString(searchValue)
      limitToStandoffClass <- ZIO.foreach(limitToStandoffClass)(ensureStandoffClass)
      countSparql          <- SearchFulltextQuery.build(
                       searchTerms = LuceneQueryString(searchValue),
                       limitToProject = limitToProject,
                       limitToResourceClass = limitToResourceClass,
                       limitToStandoffClass = limitToStandoffClass,
                       returnFiles = false, // not relevant for a count query
                       separator = None,    // no separator needed for count query
                       limit = 1,
                       offset = 0,
                       countQuery = true, // do not get the resources themselves, but the sum of results
                     )
      bindings <- triplestore.query(Select(countSparql)).map(_.results.bindings)
      count    <- // query response should contain one result with one row with the name "count"
        ZIO.fail {
          val msg = s"Fulltext count query is expected to return exactly one row, but ${bindings.size} given"
          GravsearchException(msg)
        }
          .when(bindings.length != 1)
          .as(bindings.head.rowMap("count"))
    } yield ResourceCountV2(numberOfResources = count.toInt)

  /**
   * Performs a fulltext search (simple search).
   *
   * @param searchValue          the values to search for.
   * @param offset               the offset to be used for paging.
   * @param limitToProject       limit search to given project.
   * @param limitToResourceClass limit search to given resource class.
   * @param limitToStandoffClass limit search to given standoff class.
   * @param returnFiles          if true, return any file value attached to each matching resource.
   * @param schemaAndOptions     the target API schema and the schema options submitted with the request.
   * @param requestingUser       the client making the request.
   * @return a [[ReadResourcesSequenceV2]] representing the resources that have been found.
   */
  override def fulltextSearchV2(
    searchValue: String,
    offset: Int,
    limitToProject: Option[ProjectIri],
    limitToResourceClass: Option[ResourceClassIri],
    limitToStandoffClass: Option[SmartIri],
    returnFiles: Boolean,
    schemaAndOptions: SchemaRendering,
    requestingUser: User,
  ): Task[ReadResourcesSequenceV2] = {
    import org.knora.webapi.messages.util.search.FullTextMainQueryGenerator.FullTextSearchConstants
    for {
      _                    <- ensureIsFulltextSearch(searchValue)
      _                    <- validateSearchString(searchValue)
      limitToStandoffClass <- ZIO.foreach(limitToStandoffClass)(ensureStandoffClass)
      searchSparql         <- SearchFulltextQuery.build(
                        searchTerms = LuceneQueryString(searchValue),
                        limitToProject = limitToProject,
                        limitToResourceClass = limitToResourceClass,
                        limitToStandoffClass = limitToStandoffClass,
                        returnFiles = returnFiles,
                        separator = Some(StringFormatter.INFORMATION_SEPARATOR_ONE),
                        limit = appConfig.v2.resourcesSequence.resultsPerPage,
                        offset = offset * appConfig.v2.resourcesSequence.resultsPerPage, // determine the actual offset
                        countQuery = false,
                      )

      prequeryResponseNotMerged <- triplestore.query(Select(searchSparql))

      mainResourceVar = QueryVariable("resource")

      // Merge rows with the same resource IRI.
      prequeryResponse = mergePrequeryResults(prequeryResponseNotMerged, mainResourceVar)

      // a sequence of resource IRIs that match the search criteria
      // attention: no permission checking has been done so far
      resourceIris: Seq[IRI] = prequeryResponse.getColOrThrow(FullTextSearchConstants.resourceVar.variableName)

      // If the prequery returned some results, prepare a main query.
      mainResourcesAndValueRdfData <-
        if (resourceIris.nonEmpty) {

          // for each resource, create a Set of value object IRIs
          val valueObjectIrisPerResource: Map[IRI, Set[IRI]] =
            prequeryResponse.results.bindings.foldLeft(Map.empty[IRI, Set[IRI]]) {
              (acc: Map[IRI, Set[IRI]], resultRow: VariableResultsRow) =>
                val mainResIri: IRI = resultRow.rowMap(FullTextSearchConstants.resourceVar.variableName)

                resultRow.rowMap.get(FullTextSearchConstants.valueObjectConcatVar.variableName) match {

                  case Some(valObjIris) =>
                    // Filter out empty IRIs (which we could get if a variable used in GROUP_CONCAT is unbound)
                    acc + (mainResIri -> valObjIris
                      .split(StringFormatter.INFORMATION_SEPARATOR_ONE)
                      .toSet
                      .filterNot(_.isEmpty))

                  case None => acc
                }
            }

          // collect all value object IRIs
          val allValueObjectIris = valueObjectIrisPerResource.values.flatten.toSet

          // create a CONSTRUCT query to query resources and their values
          val mainQuery = FullTextMainQueryGenerator.createMainQuery(
            resourceIris = resourceIris.toSet,
            valueObjectIris = allValueObjectIris,
            targetSchema = schemaAndOptions.schema,
            schemaOptions = schemaAndOptions.rendering,
          )

          for {
            query          <- constructTransformer.transform(mainQuery).map(_.toSparql)
            searchResponse <- triplestore.query(Construct.gravsearch(query)).flatMap(_.asExtended)
            // separate resources and value objects
            queryResultsSep <- constructResponseUtilV2.splitMainResourcesAndValueRdfData(searchResponse, requestingUser)
          } yield queryResultsSep
        } else {
          // the prequery returned no results, no further query is necessary
          ZIO.attempt(MainResourcesAndValueRdfData(resources = Map.empty))
        }

      // Find out whether to query standoff along with text values. This boolean value will be passed to
      // ConstructResponseUtilV2.makeTextValueContentV2.
      queryStandoff: Boolean = SchemaOptions.queryStandoffWithTextValues(
                                 targetSchema = schemaAndOptions.schema,
                                 schemaOptions = schemaAndOptions.rendering,
                               )

      // If we're querying standoff, get XML-to standoff mappings.
      mappingsAsMap <-
        if (queryStandoff) {
          constructResponseUtilV2.mappingsFromQueryResults(mainResourcesAndValueRdfData.resources)
        } else {
          ZIO.succeed(Map.empty[StandoffMappingIri, MappingAndXSLTransformation])
        }

      apiResponse <-
        constructResponseUtilV2.createApiResponse(
          mainResourcesAndValueRdfData = mainResourcesAndValueRdfData,
          orderByResourceIri = resourceIris,
          pageSizeBeforeFiltering = resourceIris.size,
          mappings = mappingsAsMap,
          queryStandoff = queryStandoff,
          calculateMayHaveMoreResults = true,
          versionDate = None,
          targetSchema = schemaAndOptions.schema,
          requestingUser = requestingUser,
        )

    } yield apiResponse
  }

  override def gravsearchCountV2(
    query: ConstructQuery,
    user: User,
    limitToProject: Option[ProjectIri],
  ): Task[ResourceCountV2] =
    for {
      _ <- // make sure that OFFSET is 0
        ZIO
          .fail(GravsearchException(s"OFFSET must be 0 for a count query, but ${query.offset} given"))
          .when(query.offset != 0)

      // Do type inspection and remove type annotations from the WHERE clause.
      typeInspectionResult <-
        stageSpan("gravsearch.type_inspection")(gravsearchTypeInspectionRunner.inspectTypes(query.whereClause))

      whereClauseWithoutAnnotations <- GravsearchTypeInspectionUtil.removeTypeAnnotations(query.whereClause)

      // Validate schemas and predicates in the CONSTRUCT clause.
      _ <- GravsearchQueryChecker.checkConstructClause(query.constructClause, typeInspectionResult)

      // Create a Select prequery
      querySchema <-
        ZIO.fromOption(query.querySchema).orElseFail(AssertionException(s"WhereClause has no querySchema"))

      countSparql <- stageSpan("gravsearch.prequery.generate") {
                       for {
                         gravsearchToCountTransformer <-
                           ZIO.succeed(
                             new GravsearchToCountPrequeryTransformer(
                               constructClause = query.constructClause,
                               typeInspectionResult = typeInspectionResult,
                               querySchema = querySchema,
                               searchValueMinLength = appConfig.v2.fulltextSearch.searchValueMinLength,
                             ),
                           )
                         prequery <-
                           queryTraverser.transformConstructToSelect(
                             inputQuery = query.copy(
                               whereClause = whereClauseWithoutAnnotations,
                               orderBy = Seq.empty[OrderCriterion], // count queries do not need any sorting criteria
                             ),
                             transformer = gravsearchToCountTransformer,
                           )
                         selectTransformer: SelectTransformer =
                           new SelectTransformer(
                             simulateInference = gravsearchToCountTransformer.useInference,
                             sparqlTransformerLive,
                             gravsearchToCountTransformer.mainResourceVariable,
                             stringFormatter,
                           )
                         ontologiesForInferenceMaybe <-
                           limitToProject.fold(
                             inferenceOptimizationService.getOntologiesRelevantForInference(query.whereClause),
                           )(getProjectOntologies)
                         countQuery <- queryTraverser.transformSelectToSelect(
                                         inputQuery = prequery,
                                         transformer = selectTransformer,
                                         ontologiesForInferenceMaybe,
                                         limitToProject,
                                       )
                       } yield countQuery.toSparql
                     }

      countResponse <- stageSpan("gravsearch.prequery.execute")(triplestore.query(Select.gravsearch(countSparql)))

      _ <- // query response should contain one result with one row with the name "count"
        ZIO
          .fail(
            GravsearchException(s"Count query is expected to return exactly one row, but ${countResponse.size} given"),
          )
          .when(countResponse.size != 1)

      count: String = countResponse.getFirstOrThrow("count")

    } yield ResourceCountV2(numberOfResources = count.toInt)

  override def gravsearchV2(
    query: ConstructQuery,
    schemaAndOptions: SchemaRendering,
    user: User,
    limitToProject: Option[ProjectIri] = None,
  ): Task[ReadResourcesSequenceV2] = {

    for {
      // Do type inspection and remove type annotations from the WHERE clause.
      typeInspectionResult <-
        stageSpan("gravsearch.type_inspection")(gravsearchTypeInspectionRunner.inspectTypes(query.whereClause))
      whereClauseWithoutAnnotations <- GravsearchTypeInspectionUtil.removeTypeAnnotations(query.whereClause)

      // Validate schemas and predicates in the CONSTRUCT clause.
      _ <- GravsearchQueryChecker.checkConstructClause(query.constructClause, typeInspectionResult)

      // Create a Select prequery
      querySchema <-
        ZIO.fromOption(query.querySchema).orElseFail(AssertionException(s"InputQuery has no querySchema"))

      // TODO: if the ORDER BY criterion is a property whose occurrence is not 1, then the logic does not work correctly
      // TODO: the ORDER BY criterion has to be included in a GROUP BY statement, returning more than one row if property occurs more than once

      // Generate the prequery: build the transformers, resolve the ontologies relevant for inference, and
      // turn the input query into a triplestore-specific SELECT prequery.
      prequeryGenerated <- stageSpan("gravsearch.prequery.generate") {
                             for {
                               gravsearchToPrequeryTransformer <-
                                 ZIO.attempt(
                                   new GravsearchToPrequeryTransformer(
                                     constructClause = query.constructClause,
                                     typeInspectionResult = typeInspectionResult,
                                     querySchema = querySchema,
                                     appConfig = appConfig,
                                   ),
                                 )
                               ontologiesForInferenceMaybe <-
                                 limitToProject.fold(
                                   inferenceOptimizationService.getOntologiesRelevantForInference(query.whereClause),
                                 )(getProjectOntologies)
                               prequery <-
                                 queryTraverser.transformConstructToSelect(
                                   inputQuery = query.copy(whereClause = whereClauseWithoutAnnotations),
                                   transformer = gravsearchToPrequeryTransformer,
                                 )
                               mainResourceVar: QueryVariable       = gravsearchToPrequeryTransformer.mainResourceVariable
                               selectTransformer: SelectTransformer =
                                 new SelectTransformer(
                                   simulateInference = gravsearchToPrequeryTransformer.useInference,
                                   sparqlTransformerLive,
                                   mainResourceVar,
                                   stringFormatter,
                                 )
                               transformedPrequery <-
                                 queryTraverser
                                   .transformSelectToSelect(
                                     inputQuery = prequery,
                                     transformer = selectTransformer,
                                     limitInferenceToOntologies = ontologiesForInferenceMaybe,
                                     limitResultsToProject = limitToProject,
                                   )
                             } yield (
                               transformedPrequery.toSparql,
                               gravsearchToPrequeryTransformer,
                               mainResourceVar,
                               ontologiesForInferenceMaybe,
                             )
                           }
      (prequerySparql, gravsearchToPrequeryTransformer, mainResourceVar, ontologiesForInferenceMaybe) =
        prequeryGenerated

      prequeryResponseNotMerged <-
        stageSpan("gravsearch.prequery.execute")(
          triplestore
            .query(Select.gravsearch(prequerySparql))
            .logError(s"Gravsearch timed out for prequery:\n$prequerySparql"),
        )

      pageSizeBeforeFiltering: Int = prequeryResponseNotMerged.size

      // Merge rows with the same main resource IRI. This could happen if there are unbound variables in a UNION.
      prequeryResponse =
        mergePrequeryResults(
          prequeryResponseNotMerged = prequeryResponseNotMerged,
          mainResourceVar = mainResourceVar,
        )

      // a sequence of resource IRIs that match the search criteria
      // attention: no permission checking has been done so far
      mainResourceIris: Seq[IRI] = prequeryResponse.getColOrThrow(mainResourceVar.variableName)

      mainQueryResults <-
        if (mainResourceIris.nonEmpty) {
          // at least one resource matched the prequery

          // get all the IRIs for variables representing dependent resources per main resource
          val dependentResourceIrisPerMainResource: GravsearchMainQueryGenerator.DependentResourcesPerMainResource =
            GravsearchMainQueryGenerator.getDependentResourceIrisPerMainResource(
              prequeryResponse = prequeryResponse,
              transformer = gravsearchToPrequeryTransformer,
              mainResourceVar = mainResourceVar,
            )

          // collect all variables representing resources
          val allResourceVariablesFromTypeInspection: Set[QueryVariable] = typeInspectionResult.entities.collect {
            case (queryVar: TypeableVariable, nonPropTypeInfo: NonPropertyTypeInfo) if nonPropTypeInfo.isResourceType =>
              QueryVariable(queryVar.variableName)
          }.toSet

          // the user may have defined IRIs of dependent resources in the input query (type annotations)
          // only add them if they are mentioned in a positive context (not negated like in a FILTER NOT EXISTS or MINUS)
          val dependentResourceIrisFromTypeInspection: Set[IRI] = typeInspectionResult.entities.collect {
            case (iri: TypeableIri, _: NonPropertyTypeInfo)
                if whereClauseWithoutAnnotations.positiveEntities.contains(IriRef(iri.iri)) =>
              iri.iri.toString
          }.toSet

          // the IRIs of all dependent resources for all main resources
          val allDependentResourceIris: Set[IRI] =
            dependentResourceIrisPerMainResource.dependentResourcesPerMainResource.values.flatten.toSet ++ dependentResourceIrisFromTypeInspection

          // for each main resource, create a Map of value object variables and their Iris
          val valueObjectVarsAndIrisPerMainResource
            : GravsearchMainQueryGenerator.ValueObjectVariablesAndValueObjectIris =
            GravsearchMainQueryGenerator.getValueObjectVarsAndIrisPerMainResource(
              prequeryResponse = prequeryResponse,
              valueObjectVariablesConcat = gravsearchToPrequeryTransformer.valueObjectVariablesGroupConcat,
              mainResourceVar = mainResourceVar,
            )

          // collect all value objects IRIs (for all main resources and for all value object variables)
          val allValueObjectIris: Set[IRI] =
            valueObjectVarsAndIrisPerMainResource.valueObjectVariablesAndValueObjectIris.values.foldLeft(
              Set.empty[IRI],
            ) { case (acc: Set[IRI], valObjIrisForQueryVar: Map[QueryVariable, Set[IRI]]) =>
              acc ++ valObjIrisForQueryVar.values.flatten.toSet
            }

          // create the main query
          // it is a Union of two sets: the main resources and the dependent resources
          val mainQuery: ConstructQuery = GravsearchMainQueryGenerator.createMainQuery(
            mainResourceIris = mainResourceIris.map(iri => IriRef(iri.toSmartIri)).toSet,
            dependentResourceIris = allDependentResourceIris.map(iri => IriRef(iri.toSmartIri)),
            valueObjectIris = allValueObjectIris,
            targetSchema = schemaAndOptions.schema,
            schemaOptions = schemaAndOptions.rendering,
          )

          for {
            mainQuerySparql <-
              stageSpan("gravsearch.mainquery.generate")(
                constructTransformer.transform(mainQuery, ontologiesForInferenceMaybe).map(_.toSparql),
              )
            mainQueryResponse <-
              stageSpan("gravsearch.mainquery.execute")(
                triplestore.query(Construct.gravsearch(mainQuerySparql)).flatMap(_.asExtended),
              )

            result <-
              stageSpan("gravsearch.result_transform") {
                for {
                  // Filter out values that the user doesn't have permission to see.
                  queryResultsFilteredForPermissions <-
                    constructResponseUtilV2.splitMainResourcesAndValueRdfData(mainQueryResponse, user)

                  // filter out those value objects that the user does not want to be returned by the query (not present in the input query's CONSTRUCT clause)
                  queryResWithFullGraphPatternOnlyRequestedValues: ConstructResponseRdfData.RdfResources =
                    MainQueryResultProcessor
                      .getRequestedValuesFromResultsWithFullGraphPattern(
                        queryResultsFilteredForPermissions.resources,
                        valueObjectVarsAndIrisPerMainResource,
                        allResourceVariablesFromTypeInspection,
                        dependentResourceIrisFromTypeInspection,
                        gravsearchToPrequeryTransformer,
                      )
                } yield queryResultsFilteredForPermissions.copy(
                  resources = queryResWithFullGraphPatternOnlyRequestedValues,
                )
              }
          } yield result

        } else {
          // the prequery returned no results, no further query is necessary
          ZIO.attempt(MainResourcesAndValueRdfData(resources = Map.empty))
        }

      // Find out whether to query standoff along with text values. This boolean value will be passed to
      // ConstructResponseUtilV2.makeTextValueContentV2.
      queryStandoff: Boolean = SchemaOptions.queryStandoffWithTextValues(
                                 targetSchema = schemaAndOptions.schema,
                                 schemaOptions = schemaAndOptions.rendering,
                               )

      // If we're querying standoff, get XML-to standoff mappings.
      mappingsAsMap <-
        if (queryStandoff) {
          constructResponseUtilV2.mappingsFromQueryResults(mainQueryResults.resources)
        } else {
          ZIO.succeed(Map.empty[StandoffMappingIri, MappingAndXSLTransformation])
        }

      apiResponse <-
        constructResponseUtilV2.createApiResponse(
          mainResourcesAndValueRdfData = mainQueryResults,
          orderByResourceIri = mainResourceIris,
          pageSizeBeforeFiltering = pageSizeBeforeFiltering,
          mappings = mappingsAsMap,
          queryStandoff = queryStandoff,
          calculateMayHaveMoreResults = true,
          versionDate = None,
          targetSchema = schemaAndOptions.schema,
          requestingUser = user,
        )
    } yield apiResponse
  }

  /**
   * Requests resources of the specified class from the specified project.
   *
   * @param projectIri      the IRI of the project.
   * @param resourceClass   the IRI of the resource class, in the complex schema.
   * @param orderByProperty the IRI of the property that the resources are to be ordered by, in the complex schema.
   * @param page            the page number of the results page to be returned.
   * @param schemaAndOptions    the schema of the response and schema options submitted with the request.
   * @param requestingUser  the user making the request.
   * @return a [[ReadResourcesSequenceV2]].
   */
  override def searchResourcesByProjectAndClassV2(
    projectIri: ProjectIri,
    resourceClass: SmartIri,
    orderByProperty: Option[SmartIri],
    page: Int,
    schemaAndOptions: SchemaRendering,
    requestingUser: User,
  ): Task[ReadResourcesSequenceV2] = {
    val internalClassIri                                  = resourceClass.toOntologySchema(InternalSchema)
    val maybeInternalOrderByPropertyIri: Option[SmartIri] =
      orderByProperty.map(_.toOntologySchema(InternalSchema))

    for {
      // Get information about the resource class, and about the ORDER BY property if specified.
      entityInfoResponse <- ontologyCacheHelpers.getEntityInfoResponseV2(
                              classIris = Set(internalClassIri),
                              propertyIris = maybeInternalOrderByPropertyIri.toSet,
                            )

      classDef: ReadClassInfoV2 = entityInfoResponse.classInfoMap(internalClassIri)

      // If an ORDER BY property was specified, determine which subproperty of knora-base:valueHas to use to get the
      // literal value to sort by.
      maybeOrderByValuePredicate <- ZIO.attempt {
                                      maybeInternalOrderByPropertyIri match {
                                        case Some(internalOrderByPropertyIri) =>
                                          val internalOrderByPropertyDef: ReadPropertyInfoV2 =
                                            entityInfoResponse.propertyInfoMap(
                                              internalOrderByPropertyIri,
                                            )

                                          // Ensure that the ORDER BY property is one that we can sort by.
                                          if (
                                            !internalOrderByPropertyDef.isResourceProp || internalOrderByPropertyDef.isLinkProp || internalOrderByPropertyDef.isLinkValueProp || internalOrderByPropertyDef.isFileValueProp
                                          ) {
                                            throw BadRequestException(
                                              s"Cannot sort by property <$orderByProperty>",
                                            )
                                          }

                                          // Ensure that the resource class has a cardinality on the ORDER BY property.
                                          if (
                                            !classDef.knoraResourceProperties.contains(
                                              internalOrderByPropertyIri,
                                            )
                                          ) {
                                            throw BadRequestException(
                                              s"Class <$resourceClass> has no cardinality on property <$orderByProperty>",
                                            )
                                          }

                                          // Get the value class that's the object of the knora-base:objectClassConstraint of the ORDER BY property.
                                          val orderByValueType: SmartIri =
                                            internalOrderByPropertyDef.entityInfoContent
                                              .requireIriObject(
                                                OntologyConstants.KnoraBase.ObjectClassConstraint.toSmartIri,
                                                throw InconsistentRepositoryDataException(
                                                  s"Property <$internalOrderByPropertyIri> has no knora-base:objectClassConstraint",
                                                ),
                                              )

                                          // Determine which subproperty of knora-base:valueHas corresponds to that value class.
                                          val orderByValuePredicate =
                                            orderByValueType.toString match {
                                              case OntologyConstants.KnoraBase.IntValue =>
                                                OntologyConstants.KnoraBase.ValueHasInteger
                                              case OntologyConstants.KnoraBase.DecimalValue =>
                                                OntologyConstants.KnoraBase.ValueHasDecimal
                                              case OntologyConstants.KnoraBase.BooleanValue =>
                                                OntologyConstants.KnoraBase.ValueHasBoolean
                                              case OntologyConstants.KnoraBase.DateValue =>
                                                OntologyConstants.KnoraBase.ValueHasStartJDN
                                              case OntologyConstants.KnoraBase.ColorValue =>
                                                OntologyConstants.KnoraBase.ValueHasColor
                                              case OntologyConstants.KnoraBase.GeonameValue =>
                                                OntologyConstants.KnoraBase.ValueHasGeonameCode
                                              case OntologyConstants.KnoraBase.IntervalValue =>
                                                OntologyConstants.KnoraBase.ValueHasIntervalStart
                                              case OntologyConstants.KnoraBase.UriValue =>
                                                OntologyConstants.KnoraBase.ValueHasUri
                                              case _ => OntologyConstants.KnoraBase.ValueHasString
                                            }

                                          Some(orderByValuePredicate.toSmartIri)

                                        case None => None
                                      }
                                    }

      // Do a SELECT prequery to get the IRIs of the requested page of resources.
      prequery = GetResourcesByClassInProjectPrequery.build(
                   projectIri = projectIri.value,
                   resourceClassIri = internalClassIri,
                   maybeOrderByProperty = maybeInternalOrderByPropertyIri,
                   maybeOrderByValuePredicate = maybeOrderByValuePredicate,
                   limit = appConfig.v2.resourcesSequence.resultsPerPage,
                   offset = page * appConfig.v2.resourcesSequence.resultsPerPage,
                 )
      sparqlSelectResponse      <- triplestore.query(Select(prequery))
      mainResourceIris: Seq[IRI] = sparqlSelectResponse.getColOrThrow("resource")

      // Find out whether to query standoff along with text values. This boolean value will be passed to
      // ConstructResponseUtilV2.makeTextValueContentV2.
      queryStandoff: Boolean = SchemaOptions.queryStandoffWithTextValues(
                                 targetSchema = ApiV2Complex,
                                 schemaOptions = schemaAndOptions.rendering,
                               )

      // Are there any matching resources?
      apiResponse <-
        if (mainResourceIris.nonEmpty) {
          // Yes. Do a CONSTRUCT query to get the contents of those resources. If we're querying standoff, get
          // at most one page of standoff per text value.
          val resourceRequestSparql =
            Construct(
              GetResourcePropertiesAndValuesQuery.build(
                resourceIris = mainResourceIris,
                preview = false,
                withDeleted = false,
                queryAllNonStandoff = true,
                queryStandoff = queryStandoff,
                maybePropertyIri = None,
                maybeVersionDate = None,
              ),
            )

          for {
            resourceRequestResponse <- triplestore.query(resourceRequestSparql).flatMap(_.asExtended)

            // separate resources and values
            mainResourcesAndValueRdfData <- constructResponseUtilV2.splitMainResourcesAndValueRdfData(
                                              resourceRequestResponse,
                                              requestingUser,
                                            )

            // If we're querying standoff, get XML-to standoff mappings.
            mappings <-
              if (queryStandoff) {
                constructResponseUtilV2.mappingsFromQueryResults(mainResourcesAndValueRdfData.resources)
              } else {
                ZIO.succeed(Map.empty[StandoffMappingIri, MappingAndXSLTransformation])
              }

            // Construct a ReadResourceV2 for each resource that the user has permission to see.
            readResourcesSequence <- constructResponseUtilV2.createApiResponse(
                                       mainResourcesAndValueRdfData = mainResourcesAndValueRdfData,
                                       orderByResourceIri = mainResourceIris,
                                       pageSizeBeforeFiltering = mainResourceIris.size,
                                       mappings = mappings,
                                       queryStandoff = queryStandoff,
                                       versionDate = None,
                                       calculateMayHaveMoreResults = true,
                                       targetSchema = schemaAndOptions.schema,
                                       requestingUser = requestingUser,
                                     )
          } yield readResourcesSequence
        } else {
          ZIO.succeed(ReadResourcesSequenceV2(Vector.empty[ReadResourceV2]))
        }
    } yield apiResponse
  }

  override def searchResourcesByLabelCountV2(
    searchValue: String,
    limitToProject: Option[ProjectIri],
    limitToResourceClass: Option[ResourceClassIri],
  ): Task[ResourceCountV2] =
    for {
      _          <- validateSearchString(searchValue)
      _          <- ensureIsFulltextSearch(searchValue)
      searchTerm <- ApacheLuceneSupport
                      .asLuceneQueryForSearchByLabel(searchValue)
                      .mapError(err => BadRequestException(s"Invalid search string: '$searchValue' ($err)"))
      countSparql    = SearchQueries.selectCountByLabel(searchTerm, limitToProject, limitToResourceClass)
      countResponse <- triplestore.query(countSparql)

      count <- // query response should contain one result with one row with the name "count"
        ZIO
          .fail(
            GravsearchException(
              s"Fulltext count query is expected to return exactly one row, but ${countResponse.size} given",
            ),
          )
          .when(countResponse.size != 1)
          .as(countResponse.getFirstOrThrow("count"))

    } yield ResourceCountV2(count.toInt)

  private def ensureStandoffClass(standoffClassIri: SmartIri): Task[SmartIri] = {
    val errMsg = s"Invalid standoff class IRI: $standoffClassIri"
    if (standoffClassIri.isApiV2ComplexSchema) {
      iriConverter.asInternalSmartIri(standoffClassIri).orElseFail(BadRequestException(errMsg))
    } else { ZIO.fail(BadRequestException(errMsg)) }
  }

  private def ensureIsFulltextSearch(searchStr: String) =
    ZIO
      .fail(BadRequestException("It looks like you are submitting a Gravsearch request to a full-text search route"))
      .when(searchStr.contains(OntologyConstants.KnoraApi.ApiOntologyHostname))

  private def validateSearchString(searchStr: String): Task[Unit] = {
    val searchValueMinLength = appConfig.v2.fulltextSearch.searchValueMinLength
    for {
      _ <- ZIO
             .fail(BadRequestException(s"Invalid search string: '$searchStr'"))
             .when(searchStr.isEmpty || searchStr.contains("\r"))
      _ <-
        ZIO
          .fail(
            BadRequestException(
              s"A search value is expected to have at least length of $searchValueMinLength, but '$searchStr' given of length ${searchStr.length}.",
            ),
          )
          .when(searchStr.length < searchValueMinLength)
    } yield ()
  }

  override def searchResourcesByLabelV2(
    searchValue: String,
    offset: Int,
    limitToProject: Option[ProjectIri],
    limitToResourceClass: Option[ResourceClassIri],
    targetSchema: ApiV2Schema,
    requestingUser: User,
  ): Task[ReadResourcesSequenceV2] = {
    val searchLimit  = appConfig.v2.resourcesSequence.resultsPerPage
    val searchOffset = offset * appConfig.v2.resourcesSequence.resultsPerPage
    for {
      _          <- validateSearchString(searchValue)
      _          <- ensureIsFulltextSearch(searchValue)
      searchTerm <- ApacheLuceneSupport
                      .asLuceneQueryForSearchByLabel(searchValue)
                      .mapError(err => BadRequestException(s"Invalid search string: '$searchValue' ($err)"))
      searchResourceByLabelSparql = SearchQueries.constructSearchByLabel(
                                      searchTerm,
                                      limitToProject,
                                      limitToResourceClass,
                                      searchLimit,
                                      searchOffset,
                                    )
      searchResourceByLabelResponse <- triplestore.query(searchResourceByLabelSparql).flatMap(_.asExtended)

      // collect the IRIs of main resources returned
      mainResourceIris <- ZIO.attempt {
                            searchResourceByLabelResponse.statements.foldLeft(Set.empty[IRI]) {
                              case (
                                    acc: Set[IRI],
                                    (subject: SubjectV2, assertions: Map[SmartIri, Seq[LiteralV2]]),
                                  ) =>
                                // check if the assertions represent a main resource and include its IRI if so
                                val subjectIsMainResource: Boolean =
                                  assertions
                                    .getOrElse(
                                      OntologyConstants.KnoraBase.IsMainResource.toSmartIri,
                                      Seq.empty,
                                    )
                                    .headOption match {
                                    case Some(BooleanLiteralV2(booleanVal)) => booleanVal
                                    case _                                  => false
                                  }

                                if (subjectIsMainResource) {
                                  val subjIri: IRI = subject match {
                                    case IriSubjectV2(value) => value
                                    case other               =>
                                      throw InconsistentRepositoryDataException(
                                        s"Unexpected subject of resource: $other",
                                      )
                                  }

                                  acc + subjIri
                                } else {
                                  acc
                                }
                            }
                          }

      // separate resources and value objects
      mainResourcesAndValueRdfData <-
        constructResponseUtilV2.splitMainResourcesAndValueRdfData(searchResourceByLabelResponse, requestingUser)
      apiResponse <- constructResponseUtilV2.createApiResponse(
                       mainResourcesAndValueRdfData = mainResourcesAndValueRdfData,
                       orderByResourceIri = mainResourceIris.toSeq.sorted,
                       pageSizeBeforeFiltering = mainResourceIris.size,
                       queryStandoff = false,
                       versionDate = None,
                       calculateMayHaveMoreResults = true,
                       targetSchema = targetSchema,
                       requestingUser = requestingUser,
                     )

    } yield apiResponse
  }

  /**
   * Given a prequery result, merges rows with the same main resource IRI. This could happen if there are unbound
   * variables in `GROUP_CONCAT` expressions.
   *
   * @param prequeryResponseNotMerged the prequery response before merging.
   * @param mainResourceVar           the name of the column representing the main resource.
   * @return the merged results.
   */
  private def mergePrequeryResults(
    prequeryResponseNotMerged: SparqlSelectResult,
    mainResourceVar: QueryVariable,
  ): SparqlSelectResult = {
    // Make a Map of merged results per main resource IRI.
    val prequeryRowsMergedMap: Map[IRI, VariableResultsRow] = prequeryResponseNotMerged.results.bindings.groupBy { row =>
      // Get the rows for each main resource IRI.
      row.rowMap(mainResourceVar.variableName)
    }.map { case (resourceIri: IRI, rows: Seq[VariableResultsRow]) =>
      // Make a Set of all the column names in the rows to be merged.
      val columnNamesToMerge: Set[String] = rows.flatMap(_.rowMap.keySet).toSet

      // Make a Map of column names to merged values.
      val mergedRowMap: Map[String, String] = columnNamesToMerge.map { columnName =>
        // For each column name, get the values to be merged.
        val columnValues: Seq[String] = rows.flatMap(_.rowMap.get(columnName))

        // Is this is the column containing the main resource IRI?
        val mergedColumnValue: String = if (columnName == mainResourceVar.variableName) {
          // Yes. Use that IRI as the merged value.
          resourceIri
        } else {
          // No. This must be a column resulting from GROUP_CONCAT, so use the GROUP_CONCAT
          // separator to concatenate the column values.
          columnValues.mkString(StringFormatter.INFORMATION_SEPARATOR_ONE.toString)
        }

        columnName -> mergedColumnValue
      }.toMap

      resourceIri -> VariableResultsRow(
        new ErrorHandlingMap(
          mergedRowMap,
          { (key: String) =>
            s"No value found for SPARQL query variable '$key' in query result row"
          },
        ),
      )
    }

    // Construct a sequence of the distinct main resource IRIs in the query results, preserving the
    // order of the result rows.
    val mainResourceIris: Seq[IRI] = prequeryResponseNotMerged.getColOrThrow(mainResourceVar.variableName).distinct

    // Arrange the merged rows in the same order.
    val prequeryRowsMerged: Seq[VariableResultsRow] = mainResourceIris.map { resourceIri =>
      prequeryRowsMergedMap(resourceIri)
    }

    prequeryResponseNotMerged.copy(
      results = SparqlSelectResultBody(prequeryRowsMerged),
    )
  }

  /**
   * Returns the set of ontologies of a given project to which the search is limited.
   *
   * @param projectIri the IRI of the project.
   * @return the set of ontology IRIs of the project.
   */
  private def getProjectOntologies(projectIri: ProjectIri): Task[Option[Set[SmartIri]]] =
    ontologyRepo.findByProject(projectIri).map { ontologies =>
      val ontologyIris = ontologies.map(_.ontologyMetadata.ontologyIri)
      Option.when(ontologyIris.nonEmpty)(
        ontologyIris.toSet + stringFormatter.toSmartIri(OntologyConstants.KnoraBase.KnoraBaseOntologyIri),
      )
    }
}

object SearchResponderV2 {

  /** The result type of a Gravsearch execution, used as the leading token of `gravsearch.query.shape`. */
  enum QueryResultType(val label: String) {
    case Count        extends QueryResultType("count")
    case ResourceList extends QueryResultType("resource-list")
  }

  // ---- span helpers (Decision 3: uniform interrupt + sanitized-error handling) ----------------------

  /**
   * LOAD-BEARING: this MUST map to UNSET, never ERROR. zio-telemetry's `setFailureStatus` does
   * {{{
   *   if (statusCode == ERROR) span.setStatus(ERROR, cause.prettyPrint) // would leak the FILTER literal
   *   else                     span.setStatus(statusCode)               // UNSET, which the OTel SDK no-ops
   * }}}
   * so mapping to UNSET is the ONLY reason `cause.prettyPrint` (the error string + stacktrace, which for a
   * SPARQL parse failure echoes the offending FILTER literal) never reaches the span status description.
   * The OTel-Java SDK has no ERROR-immutability guard: if this mapper is ever changed to ERROR, the
   * library's `setStatus(ERROR, prettyPrint)` runs after ours and overwrites our sanitized description, so
   * the leak returns silently. Locked by the description-equality regression test, not by run ordering.
   */
  private val unsetOnFailure: StatusMapper[Throwable, Any] =
    StatusMapper.failureNoException[Throwable](_ => StatusCode.UNSET)

  /** Writes the sanitized ERROR status (`"<stage>: <Class>"`, no message) + `error.type` onto the raw span. */
  private def markSanitizedError(span: Span, stage: String, cause: Cause[Throwable]): Unit = {
    val kind = cause.failureOption.map(_.getClass.getSimpleName).getOrElse("defect")
    val _    = span.setStatus(StatusCode.ERROR, s"$stage: $kind")
    cause.failureOption.foreach { e =>
      val _ = span.setAttribute("error.type", e.getClass.getSimpleName)
    }
  }

  /**
   * Opens an INTERNAL span named `name`, applying uniform interrupt + sanitized-error handling so a stage
   * failure yields an ERROR span whose description is exactly `"<stage>: <ClassName>"` (no raw
   * message/stacktrace), and an interruption carries `gravsearch.exit_reason=interrupted` (REQ-1.6, REQ-1.11).
   *
   * The raw OTel span is captured up front (inside the span's context) and written to directly in the
   * `tapErrorCause`/`onExit` finalizers — rather than resolved via `getCurrentSpanUnsafe` at finalizer time,
   * which during interruption teardown no longer points at this span. Both finalizers run before the
   * library's span-end (release), so the writes land; the library's own status-setter then runs with the
   * `unsetOnFailure` mapper (a no-op `setStatus(UNSET)`), leaving our sanitized status intact.
   */
  def stageSpan[A](tracing: Tracing, name: String)(effect: Task[A]): Task[A] =
    tracing.span(name, SpanKind.INTERNAL, statusMapper = unsetOnFailure) {
      tracing.getCurrentSpanUnsafe.flatMap { span =>
        effect
          .tapErrorCause(cause => ZIO.succeed(markSanitizedError(span, name, cause)))
          .onExit {
            case Exit.Failure(cause) if cause.isInterrupted =>
              ZIO.succeed {
                val _ = span.setAttribute("gravsearch.exit_reason", "interrupted")
                val _ = span.setStatus(StatusCode.ERROR, "interrupted")
              }
            case _ => ZIO.unit
          }
      }
    }

  // ---- query shape (Decision 4: bounded, human-readable, literal-invariant) ------------------------

  final case class QueryShape(label: String, predicates: Seq[String], flags: Map[String, Boolean])

  /** Sets the derived shape on the current (root) span: the bounded label, per-flag booleans and predicates. */
  def setShapeOnRoot(tracing: Tracing, query: ConstructQuery, resultType: QueryResultType): UIO[Unit] =
    tracing.getCurrentSpanUnsafe.map { span =>
      val shape = queryShape(query, resultType)
      val _     = span.setAttribute("gravsearch.query.shape", shape.label)
      val _     = span.setAttribute("gravsearch.schema_predicates", shape.predicates.mkString(","))
      shape.flags.foreach { case (flag, value) =>
        val _ = span.setAttribute(s"gravsearch.shape.$flag", value)
      }
    }

  /**
   * Derives the low-cardinality `gravsearch.query.shape` from the parsed [[ConstructQuery]] AST: a result-type
   * token + the structural boolean flags that are set + bucketed pattern/join counts (e.g.
   * `resource-list|has_filter|has_order_by|patterns:4-7|joins:1`). Only structure is used — never literal
   * values — so two queries differing only in a FILTER literal yield the same shape (REQ-1.3).
   */
  def queryShape(query: ConstructQuery, resultType: QueryResultType): QueryShape = {
    val flat = flattenPatterns(query.whereClause.patterns)

    val statementPatterns = flat.collect { case s: StatementPattern => s }
    val statementCount    = statementPatterns.size
    val joinCount         = flat.count(p => p.isInstanceOf[OptionalPattern] || p.isInstanceOf[UnionPattern])

    val hasLinkTraversal = statementPatterns.exists(_.pred match {
      case IriRef(_, propertyPathOperator) => propertyPathOperator.nonEmpty
      case _                               => false
    })
    val isFulltext =
      flat.collect { case FilterPattern(expr) => expr }.flatMap(functionLocalNames).exists(_.startsWith("match"))

    val flags = scala.collection.immutable.ListMap(
      "has_filter"         -> flat.exists(_.isInstanceOf[FilterPattern]),
      "has_optional"       -> flat.exists(_.isInstanceOf[OptionalPattern]),
      "has_union"          -> flat.exists(_.isInstanceOf[UnionPattern]),
      "has_order_by"       -> query.orderBy.nonEmpty,
      "has_offset"         -> (query.offset > 0),
      "has_link_traversal" -> hasLinkTraversal,
      "is_fulltext"        -> isFulltext,
    )

    // Only ontology predicate names (Decision 4) — never instance/data IRIs. `setShapeOnRoot` runs right
    // after parse, before type inspection validates predicates, so a parseable query may carry an instance
    // IRI (e.g. `?s <http://rdfh.ch/...> ?o`) in the predicate position; the `isKnoraEntityIri` guard keeps
    // those (and any non-ontology IRI) out of the span attribute (REQ-1.3).
    val predicates =
      statementPatterns.collect {
        case StatementPattern(_, p: IriRef, _) if p.iri.isKnoraEntityIri => localName(p.iri)
      }.distinct.sorted

    val tokens =
      (resultType.label +:
        flags.collect { case (flag, true) => flag }.toSeq) :+
        s"patterns:${bucket(statementCount)}" :+
        s"joins:${bucket(joinCount)}"

    QueryShape(tokens.mkString("|"), predicates, flags)
  }

  /**
   * Flattens nested group patterns (OPTIONAL / UNION / MINUS / FILTER NOT EXISTS) into a single sequence,
   * keeping the group nodes themselves so their presence and counts are observable.
   */
  private def flattenPatterns(patterns: Seq[QueryPattern]): Seq[QueryPattern] =
    patterns.flatMap {
      case p @ OptionalPattern(ps)        => p +: flattenPatterns(ps)
      case p @ UnionPattern(blocks)       => p +: blocks.flatMap(flattenPatterns)
      case p @ MinusPattern(ps)           => p +: flattenPatterns(ps)
      case p @ FilterNotExistsPattern(ps) => p +: flattenPatterns(ps)
      case p                              => Seq(p)
    }

  /** All function-call local names referenced anywhere in a FILTER expression tree. */
  private def functionLocalNames(expr: Expression): Seq[String] =
    expr match {
      case FunctionCallExpression(functionIri, _) => Seq(localName(functionIri.iri))
      case CompareExpression(l, _, r)             => functionLocalNames(l) ++ functionLocalNames(r)
      case AndExpression(l, r)                    => functionLocalNames(l) ++ functionLocalNames(r)
      case OrExpression(l, r)                     => functionLocalNames(l) ++ functionLocalNames(r)
      case ArithmeticExpression(l, _, r)          => functionLocalNames(l) ++ functionLocalNames(r)
      case _                                      => Seq.empty
    }

  /** The local name of an IRI (after the last `#` or `/`), keeping shape tokens/predicates bounded. */
  private def localName(iri: SmartIri): String = {
    val s         = iri.toString
    val afterHash = s.substring(s.lastIndexOf('#') + 1)
    afterHash.substring(afterHash.lastIndexOf('/') + 1)
  }

  private def bucket(n: Int): String =
    if (n == 0) "0" else if (n == 1) "1" else if (n <= 3) "2-3" else if (n <= 7) "4-7" else "8+"
}

object SearchResponderV2Live {
  val layer = ZLayer.derive[SearchResponderV2Live]
}
