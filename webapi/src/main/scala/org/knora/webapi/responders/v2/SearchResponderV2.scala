/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.v2

import akka.http.scaladsl.util.FastFuture
import akka.pattern._

import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success
import scala.util.Try

import dsp.errors.AssertionException
import dsp.errors.BadRequestException
import dsp.errors.GravsearchException
import dsp.errors.InconsistentRepositoryDataException
import org.knora.webapi._
import org.knora.webapi.config.AppConfig
import org.knora.webapi.messages.IriConversions._
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.store.triplestoremessages._
import org.knora.webapi.messages.util.ConstructResponseUtilV2
import org.knora.webapi.messages.util.ConstructResponseUtilV2.MappingAndXSLTransformation
import org.knora.webapi.messages.util.ErrorHandlingMap
import org.knora.webapi.messages.util.ResponderData
import org.knora.webapi.messages.util.rdf.SparqlSelectResult
import org.knora.webapi.messages.util.rdf.SparqlSelectResultBody
import org.knora.webapi.messages.util.rdf.VariableResultsRow
import org.knora.webapi.messages.util.search._
import org.knora.webapi.messages.util.search.gravsearch.GravsearchQueryChecker
import org.knora.webapi.messages.util.search.gravsearch.prequery.AbstractPrequeryGenerator
import org.knora.webapi.messages.util.search.gravsearch.prequery.NonTriplestoreSpecificGravsearchToCountPrequeryTransformer
import org.knora.webapi.messages.util.search.gravsearch.prequery.NonTriplestoreSpecificGravsearchToPrequeryTransformer
import org.knora.webapi.messages.util.search.gravsearch.types._
import org.knora.webapi.messages.util.standoff.StandoffTagUtilV2
import org.knora.webapi.messages.v2.responder.KnoraJsonLDResponseV2
import org.knora.webapi.messages.v2.responder.ontologymessages.EntityInfoGetRequestV2
import org.knora.webapi.messages.v2.responder.ontologymessages.EntityInfoGetResponseV2
import org.knora.webapi.messages.v2.responder.ontologymessages.ReadClassInfoV2
import org.knora.webapi.messages.v2.responder.ontologymessages.ReadPropertyInfoV2
import org.knora.webapi.messages.v2.responder.resourcemessages._
import org.knora.webapi.messages.v2.responder.searchmessages._
import org.knora.webapi.slice.ontology.repo.service.OntologyCache
import org.knora.webapi.store.triplestore.errors.TriplestoreTimeoutException
import org.knora.webapi.util.ApacheLuceneSupport._

class SearchResponderV2(
  responderData: ResponderData,
  implicit val runtime: zio.Runtime[OntologyCache with StandoffTagUtilV2 with StandoffTagUtilV2]
) extends ResponderWithStandoffV2(responderData) {

  // A Gravsearch type inspection runner.
  private val gravsearchTypeInspectionRunner =
    new GravsearchTypeInspectionRunner(appActor = appActor, responderData = responderData)

  /**
   * Receives a message of type [[SearchResponderRequestV2]], and returns an appropriate response message.
   */
  def receive(msg: SearchResponderRequestV2): Future[KnoraJsonLDResponseV2] = msg match {
    case FullTextSearchCountRequestV2(
          searchValue,
          limitToProject,
          limitToResourceClass,
          limitToStandoffClass,
          requestingUser
        ) =>
      fulltextSearchCountV2(
        searchValue,
        limitToProject,
        limitToResourceClass,
        limitToStandoffClass,
        requestingUser
      )

    case FulltextSearchRequestV2(
          searchValue,
          offset,
          limitToProject,
          limitToResourceClass,
          limitToStandoffClass,
          returnFiles,
          targetSchema,
          schemaOptions,
          requestingUser
        ) =>
      fulltextSearchV2(
        searchValue,
        offset,
        limitToProject,
        limitToResourceClass,
        limitToStandoffClass,
        returnFiles,
        targetSchema,
        schemaOptions,
        requestingUser,
        responderData.appConfig
      )

    case GravsearchCountRequestV2(query, requestingUser) =>
      gravsearchCountV2(
        inputQuery = query,
        requestingUser = requestingUser
      )

    case GravsearchRequestV2(query, targetSchema, schemaOptions, requestingUser) =>
      gravsearchV2(
        inputQuery = query,
        targetSchema = targetSchema,
        schemaOptions = schemaOptions,
        requestingUser = requestingUser
      )

    case SearchResourceByLabelCountRequestV2(
          searchValue,
          limitToProject,
          limitToResourceClass,
          requestingUser
        ) =>
      searchResourcesByLabelCountV2(
        searchValue,
        limitToProject,
        limitToResourceClass,
        requestingUser
      )

    case SearchResourceByLabelRequestV2(
          searchValue,
          offset,
          limitToProject,
          limitToResourceClass,
          targetSchema,
          requestingUser
        ) =>
      searchResourcesByLabelV2(
        searchValue,
        offset,
        limitToProject,
        limitToResourceClass,
        targetSchema,
        requestingUser
      )

    case resourcesInProjectGetRequestV2: SearchResourcesByProjectAndClassRequestV2 =>
      searchResourcesByProjectAndClassV2(resourcesInProjectGetRequestV2)

    case other => handleUnexpectedMessage(other, log, this.getClass.getName)
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
   * @param requestingUser       the client making the request.
   * @return a [[ResourceCountV2]] representing the number of resources that have been found.
   */
  private def fulltextSearchCountV2(
    searchValue: String,
    limitToProject: Option[IRI],
    limitToResourceClass: Option[SmartIri],
    limitToStandoffClass: Option[SmartIri],
    requestingUser: UserADM
  ): Future[ResourceCountV2] = {

    val searchTerms: LuceneQueryString = LuceneQueryString(searchValue)

    for {
      countSparql <-
        Future(
          org.knora.webapi.messages.twirl.queries.sparql.v2.txt
            .searchFulltext(
              searchTerms = searchTerms,
              limitToProject = limitToProject,
              limitToResourceClass = limitToResourceClass.map(_.toString),
              limitToStandoffClass = limitToStandoffClass.map(_.toString),
              returnFiles = false, // not relevant for a count query
              separator = None,    // no separator needed for count query
              limit = 1,
              offset = 0,
              countQuery = true // do not get the resources themselves, but the sum of results
            )
            .toString()
        )

      countResponse: SparqlSelectResult <- appActor.ask(SparqlSelectRequest(countSparql)).mapTo[SparqlSelectResult]

      // query response should contain one result with one row with the name "count"
      _ = if (countResponse.results.bindings.length != 1) {
            throw GravsearchException(
              s"Fulltext count query is expected to return exactly one row, but ${countResponse.results.bindings.size} given"
            )
          }

      count = countResponse.results.bindings.head.rowMap("count")

    } yield ResourceCountV2(numberOfResources = count.toInt)
  }

  /**
   * Performs a fulltext search (simple search).
   *
   * @param searchValue          the values to search for.
   * @param offset               the offset to be used for paging.
   * @param limitToProject       limit search to given project.
   * @param limitToResourceClass limit search to given resource class.
   * @param limitToStandoffClass limit search to given standoff class.
   * @param returnFiles          if true, return any file value attached to each matching resource.
   * @param targetSchema         the target API schema.
   * @param schemaOptions        the schema options submitted with the request.
   * @param requestingUser       the client making the request.
   * @param appConfig            the application config
   * @return a [[ReadResourcesSequenceV2]] representing the resources that have been found.
   */
  private def fulltextSearchV2(
    searchValue: String,
    offset: Int,
    limitToProject: Option[IRI],
    limitToResourceClass: Option[SmartIri],
    limitToStandoffClass: Option[SmartIri],
    returnFiles: Boolean,
    targetSchema: ApiV2Schema,
    schemaOptions: Set[SchemaOption],
    requestingUser: UserADM,
    appConfig: AppConfig
  ): Future[ReadResourcesSequenceV2] = {
    import org.knora.webapi.messages.util.search.FullTextMainQueryGenerator.FullTextSearchConstants

    val groupConcatSeparator = StringFormatter.INFORMATION_SEPARATOR_ONE

    val searchTerms: LuceneQueryString = LuceneQueryString(searchValue)

    for {
      searchSparql <-
        Future(
          org.knora.webapi.messages.twirl.queries.sparql.v2.txt
            .searchFulltext(
              searchTerms = searchTerms,
              limitToProject = limitToProject,
              limitToResourceClass = limitToResourceClass.map(_.toString),
              limitToStandoffClass = limitToStandoffClass.map(_.toString),
              returnFiles = returnFiles,
              separator = Some(groupConcatSeparator),
              limit = appConfig.v2.resourcesSequence.resultsPerPage,
              offset = offset * appConfig.v2.resourcesSequence.resultsPerPage, // determine the actual offset
              countQuery = false
            )
            .toString()
        )

      prequeryResponseNotMerged: SparqlSelectResult <- appActor
                                                         .ask(SparqlSelectRequest(searchSparql))
                                                         .mapTo[SparqlSelectResult]

      mainResourceVar = QueryVariable("resource")

      // Merge rows with the same resource IRI.
      prequeryResponse = mergePrequeryResults(prequeryResponseNotMerged, mainResourceVar)

      // a sequence of resource IRIs that match the search criteria
      // attention: no permission checking has been done so far
      resourceIris: Seq[IRI] = prequeryResponse.results.bindings.map { resultRow: VariableResultsRow =>
                                 resultRow.rowMap(FullTextSearchConstants.resourceVar.variableName)
                               }

      // If the prequery returned some results, prepare a main query.
      mainResourcesAndValueRdfData: ConstructResponseUtilV2.MainResourcesAndValueRdfData <-
        if (resourceIris.nonEmpty) {

          // for each resource, create a Set of value object IRIs
          val valueObjectIrisPerResource: Map[IRI, Set[IRI]] =
            prequeryResponse.results.bindings.foldLeft(Map.empty[IRI, Set[IRI]]) {
              (acc: Map[IRI, Set[IRI]], resultRow: VariableResultsRow) =>
                val mainResIri: IRI = resultRow.rowMap(FullTextSearchConstants.resourceVar.variableName)

                resultRow.rowMap.get(FullTextSearchConstants.valueObjectConcatVar.variableName) match {

                  case Some(valObjIris) =>
                    // Filter out empty IRIs (which we could get if a variable used in GROUP_CONCAT is unbound)
                    acc + (mainResIri -> valObjIris.split(groupConcatSeparator).toSet.filterNot(_.isEmpty))

                  case None => acc
                }
            }

          // collect all value object IRIs
          val allValueObjectIris = valueObjectIrisPerResource.values.flatten.toSet

          // create a CONSTRUCT query to query resources and their values
          val mainQuery = FullTextMainQueryGenerator.createMainQuery(
            resourceIris = resourceIris.toSet,
            valueObjectIris = allValueObjectIris,
            targetSchema = targetSchema,
            schemaOptions = schemaOptions,
            appConfig = appConfig
          )

          val queryPatternTransformerConstruct: ConstructToConstructTransformer =
            new SparqlTransformer.NoInferenceConstructToConstructTransformer

          val triplestoreSpecificQuery = QueryTraverser.transformConstructToConstruct(
            inputQuery = mainQuery,
            transformer = queryPatternTransformerConstruct
          )

          for {
            searchResponse: SparqlExtendedConstructResponse <-
              appActor
                .ask(
                  SparqlExtendedConstructRequest(
                    sparql = triplestoreSpecificQuery.toSparql
                  )
                )
                .mapTo[SparqlExtendedConstructResponse]

            // separate resources and value objects
            queryResultsSep: ConstructResponseUtilV2.MainResourcesAndValueRdfData =
              ConstructResponseUtilV2
                .splitMainResourcesAndValueRdfData(
                  constructQueryResults = searchResponse,
                  requestingUser = requestingUser
                )
          } yield queryResultsSep
        } else {

          // the prequery returned no results, no further query is necessary
          Future(
            ConstructResponseUtilV2.MainResourcesAndValueRdfData(resources = Map.empty)
          )
        }

      // Find out whether to query standoff along with text values. This boolean value will be passed to
      // ConstructResponseUtilV2.makeTextValueContentV2.
      queryStandoff: Boolean = SchemaOptions.queryStandoffWithTextValues(
                                 targetSchema = targetSchema,
                                 schemaOptions = schemaOptions
                               )

      // If we're querying standoff, get XML-to standoff mappings.
      mappingsAsMap: Map[IRI, MappingAndXSLTransformation] <-
        if (queryStandoff) {
          getMappingsFromQueryResultsSeparated(
            queryResultsSeparated = mainResourcesAndValueRdfData.resources,
            requestingUser = requestingUser
          )
        } else {
          FastFuture.successful(Map.empty[IRI, MappingAndXSLTransformation])
        }

      apiResponse: ReadResourcesSequenceV2 <-
        ConstructResponseUtilV2.createApiResponse(
          mainResourcesAndValueRdfData = mainResourcesAndValueRdfData,
          orderByResourceIri = resourceIris,
          pageSizeBeforeFiltering = resourceIris.size,
          mappings = mappingsAsMap,
          queryStandoff = queryStandoff,
          calculateMayHaveMoreResults = true,
          versionDate = None,
          appActor = appActor,
          appConfig = appConfig,
          targetSchema = targetSchema,
          requestingUser = requestingUser
        )

    } yield apiResponse
  }

  /**
   * Performs a count query for a Gravsearch query provided by the user.
   *
   * @param inputQuery           a Gravsearch query provided by the client.
   *
   * @param requestingUser       the client making the request.
   * @return a [[ResourceCountV2]] representing the number of resources that have been found.
   */
  private def gravsearchCountV2(
    inputQuery: ConstructQuery,
    requestingUser: UserADM
  ): Future[ResourceCountV2] = {

    // make sure that OFFSET is 0
    if (inputQuery.offset != 0)
      throw GravsearchException(s"OFFSET must be 0 for a count query, but ${inputQuery.offset} given")

    for {

      // Do type inspection and remove type annotations from the WHERE clause.
      typeInspectionResult: GravsearchTypeInspectionResult <-
        gravsearchTypeInspectionRunner.inspectTypes(
          inputQuery.whereClause,
          requestingUser
        )

      whereClauseWithoutAnnotations: WhereClause =
        GravsearchTypeInspectionUtil.removeTypeAnnotations(
          inputQuery.whereClause
        )

      // Validate schemas and predicates in the CONSTRUCT clause.
      _ = GravsearchQueryChecker.checkConstructClause(
            constructClause = inputQuery.constructClause,
            typeInspectionResult = typeInspectionResult
          )

      // Create a Select prequery

      nonTriplestoreSpecificConstructToSelectTransformer: NonTriplestoreSpecificGravsearchToCountPrequeryTransformer =
        new NonTriplestoreSpecificGravsearchToCountPrequeryTransformer(
          constructClause = inputQuery.constructClause,
          typeInspectionResult = typeInspectionResult,
          querySchema = inputQuery.querySchema.getOrElse(throw AssertionException(s"WhereClause has no querySchema"))
        )

      nonTriplestoreSpecificPrequery: SelectQuery =
        QueryTraverser.transformConstructToSelect(
          inputQuery = inputQuery.copy(
            whereClause = whereClauseWithoutAnnotations,
            orderBy = Seq.empty[
              OrderCriterion
            ] // count queries do not need any sorting criteria
          ),
          transformer = nonTriplestoreSpecificConstructToSelectTransformer
        )

      // Convert the non-triplestore-specific query to a triplestore-specific one.

      triplestoreSpecificQueryPatternTransformerSelect: SelectToSelectTransformer =
        new SparqlTransformer.NoInferenceSelectToSelectTransformer(
          simulateInference = nonTriplestoreSpecificConstructToSelectTransformer.useInference
        )

      ontologiesForInferenceMaybe <-
        QueryTraverser.getOntologiesRelevantForInference(
          inputQuery.whereClause,
          appActor
        )

      triplestoreSpecificCountQuery =
        QueryTraverser.transformSelectToSelect(
          inputQuery = nonTriplestoreSpecificPrequery,
          transformer = triplestoreSpecificQueryPatternTransformerSelect,
          ontologiesForInferenceMaybe
        )

      countResponse: SparqlSelectResult <-
        appActor
          .ask(SparqlSelectRequest(triplestoreSpecificCountQuery.toSparql, isGravsearch = true))
          .mapTo[SparqlSelectResult]

      // query response should contain one result with one row with the name "count"
      _ = if (countResponse.results.bindings.length != 1) {
            throw GravsearchException(
              s"Count query is expected to return exactly one row, but ${countResponse.results.bindings.size} given"
            )
          }

      count: String = countResponse.results.bindings.head.rowMap("count")

    } yield ResourceCountV2(numberOfResources = count.toInt)

  }

  /**
   * Performs a search using a Gravsearch query provided by the client.
   *
   * @param inputQuery           a Gravsearch query provided by the client.
   * @param targetSchema         the target API schema.
   * @param schemaOptions        the schema options submitted with the request.
   *
   * @param requestingUser       the client making the request.
   * @return a [[ReadResourcesSequenceV2]] representing the resources that have been found.
   */
  private def gravsearchV2(
    inputQuery: ConstructQuery,
    targetSchema: ApiV2Schema,
    schemaOptions: Set[SchemaOption],
    requestingUser: UserADM
  ): Future[ReadResourcesSequenceV2] = {
    import org.knora.webapi.messages.util.search.gravsearch.mainquery.GravsearchMainQueryGenerator

    for {
      // Do type inspection and remove type annotations from the WHERE clause.
      typeInspectionResult: GravsearchTypeInspectionResult <-
        gravsearchTypeInspectionRunner.inspectTypes(
          inputQuery.whereClause,
          requestingUser
        )
      whereClauseWithoutAnnotations: WhereClause =
        GravsearchTypeInspectionUtil.removeTypeAnnotations(
          inputQuery.whereClause
        )

      // Validate schemas and predicates in the CONSTRUCT clause.
      _ = GravsearchQueryChecker.checkConstructClause(
            constructClause = inputQuery.constructClause,
            typeInspectionResult = typeInspectionResult
          )

      // Create a Select prequery

      nonTriplestoreSpecificConstructToSelectTransformer: NonTriplestoreSpecificGravsearchToPrequeryTransformer =
        new NonTriplestoreSpecificGravsearchToPrequeryTransformer(
          constructClause = inputQuery.constructClause,
          typeInspectionResult = typeInspectionResult,
          querySchema = inputQuery.querySchema.getOrElse(throw AssertionException(s"WhereClause has no querySchema")),
          appConfig = responderData.appConfig
        )

      // TODO: if the ORDER BY criterion is a property whose occurrence is not 1, then the logic does not work correctly
      // TODO: the ORDER BY criterion has to be included in a GROUP BY statement, returning more than one row if property occurs more than once

      ontologiesForInferenceMaybe: Option[Set[SmartIri]] <-
        QueryTraverser.getOntologiesRelevantForInference(
          inputQuery.whereClause,
          appActor
        )

      nonTriplestoreSpecificPrequery: SelectQuery =
        QueryTraverser.transformConstructToSelect(
          inputQuery = inputQuery.copy(whereClause = whereClauseWithoutAnnotations),
          transformer = nonTriplestoreSpecificConstructToSelectTransformer
        )

      // variable representing the main resources
      mainResourceVar: QueryVariable = nonTriplestoreSpecificConstructToSelectTransformer.mainResourceVariable

      // Convert the non-triplestore-specific query to a triplestore-specific one.
      triplestoreSpecificQueryPatternTransformerSelect: SelectToSelectTransformer =
        new SparqlTransformer.NoInferenceSelectToSelectTransformer(
          simulateInference = nonTriplestoreSpecificConstructToSelectTransformer.useInference
        )

      // Convert the preprocessed query to a non-triplestore-specific query.

      triplestoreSpecificPrequery =
        QueryTraverser.transformSelectToSelect(
          inputQuery = nonTriplestoreSpecificPrequery,
          transformer = triplestoreSpecificQueryPatternTransformerSelect,
          limitInferenceToOntologies = ontologiesForInferenceMaybe
        )

      triplestoreSpecificPrequerySparql = triplestoreSpecificPrequery.toSparql

      start = System.currentTimeMillis()
      tryPrequeryResponseNotMerged =
        Try(appActor.ask(SparqlSelectRequest(triplestoreSpecificPrequerySparql, isGravsearch = true)))
      prequeryResponseNotMerged <-
        (tryPrequeryResponseNotMerged match {
          case Failure(exception) => {
            exception match {
              case _: TriplestoreTimeoutException =>
                log.error(s"Gravsearch timed out for query: $inputQuery")
            }
            throw exception
          }
          case Success(value) => value
        }).mapTo[SparqlSelectResult]
      duration = (System.currentTimeMillis() - start) / 1000.0
      _ =
        if (duration < 3) {
          log.debug(s"Prequery took: ${duration}s")
        } else {
          log.warn(s"Slow Prequery ($duration):\n$triplestoreSpecificPrequerySparql\nInitial Query:\n$inputQuery")
        }
      pageSizeBeforeFiltering: Int = prequeryResponseNotMerged.results.bindings.size

      // Merge rows with the same main resource IRI. This could happen if there are unbound variables in a UNION.
      prequeryResponse =
        mergePrequeryResults(
          prequeryResponseNotMerged = prequeryResponseNotMerged,
          mainResourceVar = mainResourceVar
        )

      // a sequence of resource IRIs that match the search criteria
      // attention: no permission checking has been done so far
      mainResourceIris: Seq[IRI] =
        prequeryResponse.results.bindings.map { resultRow: VariableResultsRow =>
          resultRow.rowMap(mainResourceVar.variableName)
        }

      mainQueryResults: ConstructResponseUtilV2.MainResourcesAndValueRdfData <-
        if (mainResourceIris.nonEmpty) {
          // at least one resource matched the prequery

          // get all the IRIs for variables representing dependent resources per main resource
          val dependentResourceIrisPerMainResource: GravsearchMainQueryGenerator.DependentResourcesPerMainResource =
            GravsearchMainQueryGenerator.getDependentResourceIrisPerMainResource(
              prequeryResponse = prequeryResponse,
              transformer = nonTriplestoreSpecificConstructToSelectTransformer,
              mainResourceVar = mainResourceVar
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
              transformer = nonTriplestoreSpecificConstructToSelectTransformer,
              mainResourceVar = mainResourceVar
            )

          // collect all value objects IRIs (for all main resources and for all value object variables)
          val allValueObjectIris: Set[IRI] =
            valueObjectVarsAndIrisPerMainResource.valueObjectVariablesAndValueObjectIris.values.foldLeft(
              Set.empty[IRI]
            ) { case (acc: Set[IRI], valObjIrisForQueryVar: Map[QueryVariable, Set[IRI]]) =>
              acc ++ valObjIrisForQueryVar.values.flatten.toSet
            }

          // create the main query
          // it is a Union of two sets: the main resources and the dependent resources
          val mainQuery: ConstructQuery = GravsearchMainQueryGenerator.createMainQuery(
            mainResourceIris = mainResourceIris.map(iri => IriRef(iri.toSmartIri)).toSet,
            dependentResourceIris = allDependentResourceIris.map(iri => IriRef(iri.toSmartIri)),
            valueObjectIris = allValueObjectIris,
            targetSchema = targetSchema,
            schemaOptions = schemaOptions,
            appConfig = responderData.appConfig
          )

          val queryPatternTransformerConstruct: ConstructToConstructTransformer =
            new SparqlTransformer.NoInferenceConstructToConstructTransformer

          val triplestoreSpecificMainQuery = QueryTraverser.transformConstructToConstruct(
            inputQuery = mainQuery,
            transformer = queryPatternTransformerConstruct,
            limitInferenceToOntologies = ontologiesForInferenceMaybe
          )

          // Convert the result to a SPARQL string and send it to the triplestore.
          val triplestoreSpecificMainQuerySparql: String = triplestoreSpecificMainQuery.toSparql
          log.debug(triplestoreSpecificMainQuerySparql)

          for {
            mainQueryResponse: SparqlExtendedConstructResponse <-
              appActor
                .ask(
                  SparqlExtendedConstructRequest(
                    sparql = triplestoreSpecificMainQuerySparql,
                    isGravsearch = true
                  )
                )
                .mapTo[SparqlExtendedConstructResponse]

            // Filter out values that the user doesn't have permission to see.
            queryResultsFilteredForPermissions: ConstructResponseUtilV2.MainResourcesAndValueRdfData =
              ConstructResponseUtilV2
                .splitMainResourcesAndValueRdfData(
                  constructQueryResults = mainQueryResponse,
                  requestingUser = requestingUser
                )

            // filter out those value objects that the user does not want to be returned by the query (not present in the input query's CONSTRUCT clause)
            queryResWithFullGraphPatternOnlyRequestedValues: Map[
              IRI,
              ConstructResponseUtilV2.ResourceWithValueRdfData
            ] = MainQueryResultProcessor
                  .getRequestedValuesFromResultsWithFullGraphPattern(
                    queryResultsFilteredForPermissions.resources,
                    valueObjectVarsAndIrisPerMainResource,
                    allResourceVariablesFromTypeInspection,
                    dependentResourceIrisFromTypeInspection,
                    nonTriplestoreSpecificConstructToSelectTransformer,
                    typeInspectionResult,
                    inputQuery
                  )
          } yield queryResultsFilteredForPermissions.copy(
            resources = queryResWithFullGraphPatternOnlyRequestedValues
          )

        } else {
          // the prequery returned no results, no further query is necessary
          Future(ConstructResponseUtilV2.MainResourcesAndValueRdfData(resources = Map.empty))
        }

      // Find out whether to query standoff along with text values. This boolean value will be passed to
      // ConstructResponseUtilV2.makeTextValueContentV2.
      queryStandoff: Boolean = SchemaOptions.queryStandoffWithTextValues(
                                 targetSchema = targetSchema,
                                 schemaOptions = schemaOptions
                               )

      // If we're querying standoff, get XML-to standoff mappings.
      mappingsAsMap: Map[IRI, MappingAndXSLTransformation] <-
        if (queryStandoff) {
          getMappingsFromQueryResultsSeparated(
            queryResultsSeparated = mainQueryResults.resources,
            requestingUser = requestingUser
          )
        } else {
          FastFuture.successful(Map.empty[IRI, MappingAndXSLTransformation])
        }

      apiResponse: ReadResourcesSequenceV2 <-
        ConstructResponseUtilV2.createApiResponse(
          mainResourcesAndValueRdfData = mainQueryResults,
          orderByResourceIri = mainResourceIris,
          pageSizeBeforeFiltering = pageSizeBeforeFiltering,
          mappings = mappingsAsMap,
          queryStandoff = queryStandoff,
          versionDate = None,
          calculateMayHaveMoreResults = true,
          appActor = appActor,
          appConfig = responderData.appConfig,
          targetSchema = targetSchema,
          requestingUser = requestingUser
        )

    } yield apiResponse
  }

  /**
   * Gets resources from a project.
   *
   * @param resourcesInProjectGetRequestV2 the request message.
   * @return a [[ReadResourcesSequenceV2]].
   */
  private def searchResourcesByProjectAndClassV2(
    resourcesInProjectGetRequestV2: SearchResourcesByProjectAndClassRequestV2
  ): Future[ReadResourcesSequenceV2] = {
    val internalClassIri = resourcesInProjectGetRequestV2.resourceClass.toOntologySchema(InternalSchema)
    val maybeInternalOrderByPropertyIri: Option[SmartIri] =
      resourcesInProjectGetRequestV2.orderByProperty.map(_.toOntologySchema(InternalSchema))

    for {
      // Get information about the resource class, and about the ORDER BY property if specified.
      entityInfoResponse: EntityInfoGetResponseV2 <-
        appActor
          .ask(
            EntityInfoGetRequestV2(
              classIris = Set(internalClassIri),
              propertyIris = maybeInternalOrderByPropertyIri.toSet,
              requestingUser = resourcesInProjectGetRequestV2.requestingUser
            )
          )
          .mapTo[EntityInfoGetResponseV2]

      classDef: ReadClassInfoV2 = entityInfoResponse.classInfoMap(internalClassIri)

      // If an ORDER BY property was specified, determine which subproperty of knora-base:valueHas to use to get the
      // literal value to sort by.
      maybeOrderByValuePredicate: Option[SmartIri] =
        maybeInternalOrderByPropertyIri match {
          case Some(internalOrderByPropertyIri) =>
            val internalOrderByPropertyDef: ReadPropertyInfoV2 =
              entityInfoResponse.propertyInfoMap(
                internalOrderByPropertyIri
              )

            // Ensure that the ORDER BY property is one that we can sort by.
            if (
              !internalOrderByPropertyDef.isResourceProp || internalOrderByPropertyDef.isLinkProp || internalOrderByPropertyDef.isLinkValueProp || internalOrderByPropertyDef.isFileValueProp
            ) {
              throw BadRequestException(
                s"Cannot sort by property <${resourcesInProjectGetRequestV2.orderByProperty}>"
              )
            }

            // Ensure that the resource class has a cardinality on the ORDER BY property.
            if (
              !classDef.knoraResourceProperties.contains(
                internalOrderByPropertyIri
              )
            ) {
              throw BadRequestException(
                s"Class <${resourcesInProjectGetRequestV2.resourceClass}> has no cardinality on property <${resourcesInProjectGetRequestV2.orderByProperty}>"
              )
            }

            // Get the value class that's the object of the knora-base:objectClassConstraint of the ORDER BY property.
            val orderByValueType: SmartIri =
              internalOrderByPropertyDef.entityInfoContent
                .requireIriObject(
                  OntologyConstants.KnoraBase.ObjectClassConstraint.toSmartIri,
                  throw InconsistentRepositoryDataException(
                    s"Property <$internalOrderByPropertyIri> has no knora-base:objectClassConstraint"
                  )
                )

            // Determine which subproperty of knora-base:valueHas corresponds to that value class.
            val orderByValuePredicate = orderByValueType.toString match {
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

      // Do a SELECT prequery to get the IRIs of the requested page of resources.
      prequery = org.knora.webapi.messages.twirl.queries.sparql.v2.txt
                   .getResourcesByClassInProjectPrequery(
                     projectIri = resourcesInProjectGetRequestV2.projectIri.toString,
                     resourceClassIri = internalClassIri,
                     maybeOrderByProperty = maybeInternalOrderByPropertyIri,
                     maybeOrderByValuePredicate = maybeOrderByValuePredicate,
                     limit = responderData.appConfig.v2.resourcesSequence.resultsPerPage,
                     offset =
                       resourcesInProjectGetRequestV2.page * responderData.appConfig.v2.resourcesSequence.resultsPerPage
                   )
                   .toString

      sparqlSelectResponse      <- appActor.ask(SparqlSelectRequest(prequery)).mapTo[SparqlSelectResult]
      mainResourceIris: Seq[IRI] = sparqlSelectResponse.results.bindings.map(_.rowMap("resource"))

      // Find out whether to query standoff along with text values. This boolean value will be passed to
      // ConstructResponseUtilV2.makeTextValueContentV2.
      queryStandoff: Boolean = SchemaOptions.queryStandoffWithTextValues(
                                 targetSchema = ApiV2Complex,
                                 schemaOptions = resourcesInProjectGetRequestV2.schemaOptions
                               )

      // If we're supposed to query standoff, get the indexes delimiting the first page of standoff. (Subsequent
      // pages, if any, will be queried separately.)
      (maybeStandoffMinStartIndex: Option[Int], maybeStandoffMaxStartIndex: Option[Int]) =
        StandoffTagUtilV2
          .getStandoffMinAndMaxStartIndexesForTextValueQuery(
            queryStandoff = queryStandoff,
            appConfig = responderData.appConfig
          )

      // Are there any matching resources?
      apiResponse: ReadResourcesSequenceV2 <-
        if (mainResourceIris.nonEmpty) {
          for {
            // Yes. Do a CONSTRUCT query to get the contents of those resources. If we're querying standoff, get
            // at most one page of standoff per text value.
            resourceRequestSparql <-
              Future(
                org.knora.webapi.messages.twirl.queries.sparql.v2.txt
                  .getResourcePropertiesAndValues(
                    resourceIris = mainResourceIris,
                    preview = false,
                    withDeleted = false,
                    queryAllNonStandoff = true,
                    maybePropertyIri = None,
                    maybeVersionDate = None,
                    maybeStandoffMinStartIndex = maybeStandoffMinStartIndex,
                    maybeStandoffMaxStartIndex = maybeStandoffMaxStartIndex,
                    stringFormatter = stringFormatter
                  )
                  .toString()
              )

            resourceRequestResponse: SparqlExtendedConstructResponse <-
              appActor
                .ask(
                  SparqlExtendedConstructRequest(
                    sparql = resourceRequestSparql
                  )
                )
                .mapTo[SparqlExtendedConstructResponse]

            // separate resources and values
            mainResourcesAndValueRdfData: ConstructResponseUtilV2.MainResourcesAndValueRdfData =
              ConstructResponseUtilV2
                .splitMainResourcesAndValueRdfData(
                  constructQueryResults = resourceRequestResponse,
                  requestingUser = resourcesInProjectGetRequestV2.requestingUser
                )

            // If we're querying standoff, get XML-to standoff mappings.
            mappings: Map[IRI, MappingAndXSLTransformation] <-
              if (queryStandoff) {
                getMappingsFromQueryResultsSeparated(
                  mainResourcesAndValueRdfData.resources,
                  resourcesInProjectGetRequestV2.requestingUser
                )
              } else {
                FastFuture.successful(Map.empty[IRI, MappingAndXSLTransformation])
              }

            // Construct a ReadResourceV2 for each resource that the user has permission to see.
            readResourcesSequence: ReadResourcesSequenceV2 <-
              ConstructResponseUtilV2.createApiResponse(
                mainResourcesAndValueRdfData = mainResourcesAndValueRdfData,
                orderByResourceIri = mainResourceIris,
                pageSizeBeforeFiltering = mainResourceIris.size,
                mappings = mappings,
                queryStandoff = maybeStandoffMinStartIndex.nonEmpty,
                versionDate = None,
                calculateMayHaveMoreResults = true,
                appActor = appActor,
                targetSchema = resourcesInProjectGetRequestV2.targetSchema,
                appConfig = responderData.appConfig,
                requestingUser = resourcesInProjectGetRequestV2.requestingUser
              )
          } yield readResourcesSequence
        } else {
          FastFuture.successful(ReadResourcesSequenceV2(Vector.empty[ReadResourceV2]))
        }
    } yield apiResponse
  }

  /**
   * Performs a count query for a search for resources by their rdfs:label.
   *
   * @param searchValue          the values to search for.
   * @param limitToProject       limit search to given project.
   * @param limitToResourceClass limit search to given resource class.
   *
   * @param requestingUser       the client making the request.
   * @return a [[ReadResourcesSequenceV2]] representing the resources that have been found.
   */
  private def searchResourcesByLabelCountV2(
    searchValue: String,
    limitToProject: Option[IRI],
    limitToResourceClass: Option[SmartIri],
    requestingUser: UserADM
  ): Future[ResourceCountV2] = {
    val searchPhrase: MatchStringWhileTyping = MatchStringWhileTyping(searchValue)

    for {
      countSparql <-
        Future(
          org.knora.webapi.messages.twirl.queries.sparql.v2.txt
            .searchResourceByLabel(
              searchTerm = searchPhrase,
              limitToProject = limitToProject,
              limitToResourceClass = limitToResourceClass.map(_.toString),
              limit = 1,
              offset = 0,
              countQuery = true
            )
            .toString()
        )

      countResponse: SparqlSelectResult <- appActor.ask(SparqlSelectRequest(countSparql)).mapTo[SparqlSelectResult]

      // query response should contain one result with one row with the name "count"
      _ = if (countResponse.results.bindings.length != 1) {
            throw GravsearchException(
              s"Fulltext count query is expected to return exactly one row, but ${countResponse.results.bindings.size} given"
            )
          }

      count = countResponse.results.bindings.head.rowMap("count")

    } yield ResourceCountV2(
      numberOfResources = count.toInt
    )

  }

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
  private def searchResourcesByLabelV2(
    searchValue: String,
    offset: Int,
    limitToProject: Option[IRI],
    limitToResourceClass: Option[SmartIri],
    targetSchema: ApiV2Schema,
    requestingUser: UserADM
  ): Future[ReadResourcesSequenceV2] = {

    val searchPhrase: MatchStringWhileTyping = MatchStringWhileTyping(searchValue)

    for {
      searchResourceByLabelSparql <-
        Future(
          org.knora.webapi.messages.twirl.queries.sparql.v2.txt
            .searchResourceByLabel(
              searchTerm = searchPhrase,
              limitToProject = limitToProject,
              limitToResourceClass = limitToResourceClass.map(_.toString),
              limit = responderData.appConfig.v2.resourcesSequence.resultsPerPage,
              offset = offset * responderData.appConfig.v2.resourcesSequence.resultsPerPage,
              countQuery = false
            )
            .toString()
        )

      searchResourceByLabelResponse: SparqlExtendedConstructResponse <-
        appActor
          .ask(
            SparqlExtendedConstructRequest(
              sparql = searchResourceByLabelSparql
            )
          )
          .mapTo[SparqlExtendedConstructResponse]

      // collect the IRIs of main resources returned
      mainResourceIris: Set[IRI] =
        searchResourceByLabelResponse.statements.foldLeft(Set.empty[IRI]) {
          case (acc: Set[IRI], (subject: SubjectV2, assertions: Map[SmartIri, Seq[LiteralV2]])) =>
            // check if the assertions represent a main resource and include its IRI if so
            val subjectIsMainResource: Boolean =
              assertions.getOrElse(OntologyConstants.KnoraBase.IsMainResource.toSmartIri, Seq.empty).headOption match {
                case Some(BooleanLiteralV2(booleanVal)) => booleanVal
                case _                                  => false
              }

            if (subjectIsMainResource) {
              val subjIri: IRI = subject match {
                case IriSubjectV2(value) => value
                case other               => throw InconsistentRepositoryDataException(s"Unexpected subject of resource: $other")
              }

              acc + subjIri
            } else {
              acc
            }
        }

      // separate resources and value objects
      mainResourcesAndValueRdfData =
        ConstructResponseUtilV2.splitMainResourcesAndValueRdfData(
          constructQueryResults = searchResourceByLabelResponse,
          requestingUser = requestingUser
        )

      apiResponse: ReadResourcesSequenceV2 <-
        ConstructResponseUtilV2.createApiResponse(
          mainResourcesAndValueRdfData = mainResourcesAndValueRdfData,
          orderByResourceIri = mainResourceIris.toSeq.sorted,
          pageSizeBeforeFiltering = mainResourceIris.size,
          queryStandoff = false,
          versionDate = None,
          calculateMayHaveMoreResults = true,
          appActor = appActor,
          targetSchema = targetSchema,
          appConfig = responderData.appConfig,
          requestingUser = requestingUser
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
    mainResourceVar: QueryVariable
  ): SparqlSelectResult = {
    // Make a Map of merged results per main resource IRI.
    val prequeryRowsMergedMap: Map[IRI, VariableResultsRow] = prequeryResponseNotMerged.results.bindings.groupBy {
      row =>
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
          columnValues.mkString(AbstractPrequeryGenerator.groupConcatSeparator.toString)
        }

        columnName -> mergedColumnValue
      }.toMap

      resourceIri -> VariableResultsRow(
        new ErrorHandlingMap(
          mergedRowMap,
          { key: String =>
            s"No value found for SPARQL query variable '$key' in query result row"
          }
        )
      )
    }

    // Construct a sequence of the distinct main resource IRIs in the query results, preserving the
    // order of the result rows.
    val mainResourceIris: Seq[IRI] = prequeryResponseNotMerged.results.bindings.map { resultRow: VariableResultsRow =>
      resultRow.rowMap(mainResourceVar.variableName)
    }.distinct

    // Arrange the merged rows in the same order.
    val prequeryRowsMerged: Seq[VariableResultsRow] = mainResourceIris.map { resourceIri =>
      prequeryRowsMergedMap(resourceIri)
    }

    prequeryResponseNotMerged.copy(
      results = SparqlSelectResultBody(prequeryRowsMerged)
    )
  }
}
