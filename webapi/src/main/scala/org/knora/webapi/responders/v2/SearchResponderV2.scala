/*
 * Copyright © 2015-2019 the contributors (see Contributors.md).
 *
 * This file is part of Knora.
 *
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.responders.v2

import akka.actor.{ActorRef, ActorSystem}
import akka.pattern._
import org.knora.webapi._
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.store.triplestoremessages._
import org.knora.webapi.messages.v2.responder.resourcemessages._
import org.knora.webapi.messages.v2.responder.searchmessages._
import org.knora.webapi.responders.Responder.handleUnexpectedMessage
import org.knora.webapi.responders.v2.search.ApacheLuceneSupport._
import org.knora.webapi.responders.v2.search._
import org.knora.webapi.responders.v2.search.gravsearch._
import org.knora.webapi.responders.v2.search.gravsearch.prequery._
import org.knora.webapi.responders.v2.search.gravsearch.types._
import org.knora.webapi.util.IriConversions._
import org.knora.webapi.util._

import scala.concurrent.Future

/**
  * Constants used in [[SearchResponderV2]].
  */
object SearchResponderV2Constants {

    val forbiddenResourceIri: IRI = s"http://${KnoraIdUtil.IriDomain}/permissions/forbiddenResource"
}

class SearchResponderV2(system: ActorSystem, applicationStateActor: ActorRef, responderManager: ActorRef, storeManager: ActorRef) extends ResponderWithStandoffV2(system, applicationStateActor, responderManager, storeManager) {

    // A Gravsearch type inspection runner.
    private val gravsearchTypeInspectionRunner = new GravsearchTypeInspectionRunner(system = system)

    /**
      * Receives a message of type [[SearchResponderRequestV2]], and returns an appropriate response message.
      */
    def receive(msg: SearchResponderRequestV2) = msg match {
        case FullTextSearchCountRequestV2(searchValue, limitToProject, limitToResourceClass, limitToStandoffClass, requestingUser) => fulltextSearchCountV2(searchValue, limitToProject, limitToResourceClass, limitToStandoffClass, requestingUser)
        case FulltextSearchRequestV2(searchValue, offset, limitToProject, limitToResourceClass, limitToStandoffClass, requestingUser) => fulltextSearchV2(searchValue, offset, limitToProject, limitToResourceClass, limitToStandoffClass, requestingUser)
        case GravsearchCountRequestV2(query, requestingUser) => gravsearchCountV2(inputQuery = query, requestingUser = requestingUser)
        case GravsearchRequestV2(query, requestingUser) => gravsearchV2(inputQuery = query, requestingUser = requestingUser)
        case SearchResourceByLabelCountRequestV2(searchValue, limitToProject, limitToResourceClass, requestingUser) => searchResourcesByLabelCountV2(searchValue, limitToProject, limitToResourceClass, requestingUser)
        case SearchResourceByLabelRequestV2(searchValue, offset, limitToProject, limitToResourceClass, requestingUser) => searchResourcesByLabelV2(searchValue, offset, limitToProject, limitToResourceClass, requestingUser)
        case other => handleUnexpectedMessage(other, log, this.getClass.getName)
    }

    /**
      * Gets the forbidden resource.
      *
      * @param requestingUser the user making the request.
      * @return the forbidden resource.
      */
    private def getForbiddenResource(requestingUser: UserADM): Future[Some[ReadResourceV2]] = {
        import SearchResponderV2Constants.forbiddenResourceIri

        for {
            forbiddenResSeq: ReadResourcesSequenceV2 <- (responderManager ? ResourcesGetRequestV2(resourceIris = Seq(forbiddenResourceIri), requestingUser = requestingUser)).mapTo[ReadResourcesSequenceV2]
            forbiddenRes = forbiddenResSeq.resources.headOption.getOrElse(throw InconsistentTriplestoreDataException(s"$forbiddenResourceIri was not returned"))
        } yield Some(forbiddenRes)
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
      * @param requestingUser       the the client making the request.
      * @return a [[ResourceCountV2]] representing the number of resources that have been found.
      */
    private def fulltextSearchCountV2(searchValue: String, limitToProject: Option[IRI], limitToResourceClass: Option[SmartIri], limitToStandoffClass: Option[SmartIri], requestingUser: UserADM): Future[ResourceCountV2] = {

        val searchTerms: CombineSearchTerms = CombineSearchTerms(searchValue)

        for {
            countSparql <- Future(queries.sparql.v2.txt.searchFulltext(
                triplestore = settings.triplestoreType,
                searchTerms = searchTerms,
                limitToProject = limitToProject,
                limitToResourceClass = limitToResourceClass.map(_.toString),
                limitToStandoffClass.map(_.toString),
                separator = None, // no separator needed for count query
                limit = 1,
                offset = 0,
                countQuery = true // do  not get the resources themselves, but the sum of results
            ).toString())

            // _ = println(countSparql)

            countResponse: SparqlSelectResponse <- (storeManager ? SparqlSelectRequest(countSparql)).mapTo[SparqlSelectResponse]

            // query response should contain one result with one row with the name "count"
            _ = if (countResponse.results.bindings.length != 1) {
                throw GravsearchException(s"Fulltext count query is expected to return exactly one row, but ${countResponse.results.bindings.size} given")
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
      * @param requestingUser       the the client making the request.
      * @return a [[ReadResourcesSequenceV2]] representing the resources that have been found.
      */
    private def fulltextSearchV2(searchValue: String, offset: Int, limitToProject: Option[IRI], limitToResourceClass: Option[SmartIri], limitToStandoffClass: Option[SmartIri], requestingUser: UserADM): Future[ReadResourcesSequenceV2] = {
        import FullTextMainQueryGenerator.FullTextSearchConstants

        val groupConcatSeparator = StringFormatter.INFORMATION_SEPARATOR_ONE

        val searchTerms: CombineSearchTerms = CombineSearchTerms(searchValue)

        for {
            searchSparql <- Future(queries.sparql.v2.txt.searchFulltext(
                triplestore = settings.triplestoreType,
                searchTerms = searchTerms,
                limitToProject = limitToProject,
                limitToResourceClass = limitToResourceClass.map(_.toString),
                limitToStandoffClass = limitToStandoffClass.map(_.toString),
                separator = Some(groupConcatSeparator),
                limit = settings.v2ResultsPerPage,
                offset = offset * settings.v2ResultsPerPage, // determine the actual offset
                countQuery = false
            ).toString())

            // _ = println(searchSparql)

            prequeryResponse: SparqlSelectResponse <- (storeManager ? SparqlSelectRequest(searchSparql)).mapTo[SparqlSelectResponse]

            // _ = println(prequeryResponse)

            // a sequence of resource IRIs that match the search criteria
            // attention: no permission checking has been done so far
            resourceIris: Seq[IRI] = prequeryResponse.results.bindings.map {
                resultRow: VariableResultsRow => resultRow.rowMap(FullTextSearchConstants.resourceVar.variableName)
            }

            // make sure that the prequery returned some results
            queryResultsSeparatedWithFullGraphPattern: Map[IRI, ConstructResponseUtilV2.ResourceWithValueRdfData] <- if (resourceIris.nonEmpty) {

                // for each resource, create a Set of value object IRIs
                val valueObjectIrisPerResource: Map[IRI, Set[IRI]] = prequeryResponse.results.bindings.foldLeft(Map.empty[IRI, Set[IRI]]) {
                    (acc: Map[IRI, Set[IRI]], resultRow: VariableResultsRow) =>

                        val mainResIri: IRI = resultRow.rowMap(FullTextSearchConstants.resourceVar.variableName)

                        resultRow.rowMap.get(FullTextSearchConstants.valueObjectConcatVar.variableName) match {

                            case Some(valObjIris) =>

                                acc + (mainResIri -> valObjIris.split(groupConcatSeparator).toSet)

                            case None => acc
                        }
                }

                // println(valueObjectIrisPerResource)

                // collect all value object IRIs
                val allValueObjectIris = valueObjectIrisPerResource.values.flatten.toSet

                // create CONSTRUCT queries to query resources and their values
                val mainQuery = FullTextMainQueryGenerator.createMainQuery(resourceIris.toSet, allValueObjectIris)

                val triplestoreSpecificQueryPatternTransformerConstruct: ConstructToConstructTransformer = {
                    if (settings.triplestoreType.startsWith("graphdb")) {
                        // GraphDB
                        new SparqlTransformer.GraphDBConstructToConstructTransformer
                    } else {
                        // Other
                        new SparqlTransformer.NoInferenceConstructToConstructTransformer
                    }
                }

                val triplestoreSpecificQuery = QueryTraverser.transformConstructToConstruct(
                    inputQuery = mainQuery,
                    transformer = triplestoreSpecificQueryPatternTransformerConstruct
                )

                // println(triplestoreSpecificQuery.toSparql)

                for {
                    searchResponse: SparqlConstructResponse <- (storeManager ? SparqlConstructRequest(triplestoreSpecificQuery.toSparql)).mapTo[SparqlConstructResponse]

                    // separate resources and value objects
                    queryResultsSep = ConstructResponseUtilV2.splitMainResourcesAndValueRdfData(constructQueryResults = searchResponse, requestingUser = requestingUser)

                    // for each main resource check if all dependent resources and value objects are still present after permission checking
                    // this ensures that the user has sufficient permissions on the whole graph pattern
                    queryResWithFullGraphPattern = queryResultsSep.foldLeft(Map.empty[IRI, ConstructResponseUtilV2.ResourceWithValueRdfData]) {
                        case (acc: Map[IRI, ConstructResponseUtilV2.ResourceWithValueRdfData], (mainResIri: IRI, values: ConstructResponseUtilV2.ResourceWithValueRdfData)) =>

                            valueObjectIrisPerResource.get(mainResIri) match {

                                case Some(valObjIris) =>

                                    // check for presence of value objects: valueObjectIrisPerResource
                                    val expectedValueObjects: Set[IRI] = valueObjectIrisPerResource(mainResIri)

                                    // value property assertions for the current resource
                                    val valuePropAssertions: Map[IRI, Seq[ConstructResponseUtilV2.ValueRdfData]] = values.valuePropertyAssertions

                                    // all value objects contained in `valuePropAssertions`
                                    val resAndValueObjIris: MainQueryResultProcessor.ResourceIrisAndValueObjectIris = MainQueryResultProcessor.collectResourceIrisAndValueObjectIrisFromMainQueryResult(valuePropAssertions)

                                    // check if the client has sufficient permissions on all value objects IRIs present in the graph pattern
                                    val allValueObjects: Boolean = resAndValueObjIris.valueObjectIris.intersect(expectedValueObjects) == expectedValueObjects

                                    if (allValueObjects) {
                                        // sufficient permissions, include the main resource and its values
                                        acc + (mainResIri -> values)
                                    } else {
                                        // insufficient permissions, skip the resource
                                        acc
                                    }

                                case None =>
                                    // no properties -> rfs:label matched
                                    acc + (mainResIri -> values)
                            }
                    }

                } yield queryResWithFullGraphPattern
            } else {

                // the prequery returned no results, no further query is necessary
                Future(Map.empty[IRI, ConstructResponseUtilV2.ResourceWithValueRdfData])
            }

            // check if there are resources the user does not have sufficient permissions to see
            forbiddenResourceOption: Option[ReadResourceV2] <- if (resourceIris.size > queryResultsSeparatedWithFullGraphPattern.size) {
                // some of the main resources have been suppressed, represent them using the forbidden resource

                getForbiddenResource(requestingUser)
            } else {
                // all resources visible, no need for the forbidden resource
                Future(None)
            }

            // get the mappings
            mappingsAsMap <- getMappingsFromQueryResultsSeparated(queryResultsSeparatedWithFullGraphPattern, requestingUser)

            // _ = println(mappingsAsMap)

            resources: Vector[ReadResourceV2] <- ConstructResponseUtilV2.createSearchResponse(
                searchResults = queryResultsSeparatedWithFullGraphPattern,
                orderByResourceIri = resourceIris,
                mappings = mappingsAsMap,
                forbiddenResource = forbiddenResourceOption,
                responderManager = responderManager,
                requestingUser = requestingUser
            )

        } yield ReadResourcesSequenceV2(
            numberOfResources = resourceIris.size,
            resources = resources
        )

    }


    /**
      * Performs a count query for a Gravsearch query provided by the user.
      *
      * @param inputQuery     a Gravsearch query provided by the client.
      * @param requestingUser the the client making the request.
      * @return a [[ResourceCountV2]] representing the number of resources that have been found.
      */
    private def gravsearchCountV2(inputQuery: ConstructQuery, apiSchema: ApiV2Schema = ApiV2Simple, requestingUser: UserADM): Future[ResourceCountV2] = {

        // make sure that OFFSET is 0
        if (inputQuery.offset != 0) throw GravsearchException(s"OFFSET must be 0 for a count query, but ${inputQuery.offset} given")

        for {

            // Do type inspection and remove type annotations from the WHERE clause.

            typeInspectionResult: GravsearchTypeInspectionResult <- gravsearchTypeInspectionRunner.inspectTypes(inputQuery.whereClause, requestingUser)
            whereClauseWithoutAnnotations: WhereClause = GravsearchTypeInspectionUtil.removeTypeAnnotations(inputQuery.whereClause)

            // Validate schemas and predicates in the CONSTRUCT clause.
            _ = GravsearchQueryChecker.checkConstructClause(
                constructClause = inputQuery.constructClause,
                typeInspectionResult = typeInspectionResult
            )

            // Create a Select prequery

            nonTriplestoreSpecificConstructToSelectTransformer: NonTriplestoreSpecificGravsearchToCountPrequeryGenerator = new NonTriplestoreSpecificGravsearchToCountPrequeryGenerator(
                typeInspectionResult = typeInspectionResult,
                querySchema = inputQuery.querySchema.getOrElse(throw AssertionException(s"WhereClause has no querySchema"))
            )

            nonTriplestoreSpecficPrequery: SelectQuery = QueryTraverser.transformConstructToSelect(
                inputQuery = inputQuery.copy(whereClause = whereClauseWithoutAnnotations),
                transformer = nonTriplestoreSpecificConstructToSelectTransformer
            )

            // Convert the non-triplestore-specific query to a triplestore-specific one.
            triplestoreSpecificQueryPatternTransformerSelect: SelectToSelectTransformer = {
                if (settings.triplestoreType.startsWith("graphdb")) {
                    // GraphDB
                    new SparqlTransformer.GraphDBSelectToSelectTransformer
                } else {
                    // Other
                    new SparqlTransformer.NoInferenceSelectToSelectTransformer
                }
            }

            // Convert the preprocessed query to a non-triplestore-specific query.
            triplestoreSpecificCountQuery = QueryTraverser.transformSelectToSelect(
                inputQuery = nonTriplestoreSpecficPrequery,
                transformer = triplestoreSpecificQueryPatternTransformerSelect
            )

            // _ = println(triplestoreSpecificCountQuery.toSparql)

            countResponse: SparqlSelectResponse <- (storeManager ? SparqlSelectRequest(triplestoreSpecificCountQuery.toSparql)).mapTo[SparqlSelectResponse]

            // query response should contain one result with one row with the name "count"
            _ = if (countResponse.results.bindings.length != 1) {
                throw GravsearchException(s"Fulltext count query is expected to return exactly one row, but ${countResponse.results.bindings.size} given")
            }

            count: String = countResponse.results.bindings.head.rowMap("count")

        } yield ResourceCountV2(numberOfResources = count.toInt)

    }

    /**
      * Performs a search using a Gravsearch query provided by the client.
      *
      * @param inputQuery     a Gravsearch query provided by the client.
      * @param requestingUser the the client making the request.
      * @return a [[ReadResourcesSequenceV2]] representing the resources that have been found.
      */
    private def gravsearchV2(inputQuery: ConstructQuery, requestingUser: UserADM): Future[ReadResourcesSequenceV2] = {
        import org.knora.webapi.responders.v2.search.MainQueryResultProcessor
        import org.knora.webapi.responders.v2.search.gravsearch.mainquery.GravsearchMainQueryGenerator

        for {
            // Do type inspection and remove type annotations from the WHERE clause.

            typeInspectionResult: GravsearchTypeInspectionResult <- gravsearchTypeInspectionRunner.inspectTypes(inputQuery.whereClause, requestingUser)
            whereClauseWithoutAnnotations: WhereClause = GravsearchTypeInspectionUtil.removeTypeAnnotations(inputQuery.whereClause)

            // Validate schemas and predicates in the CONSTRUCT clause.
            _ = GravsearchQueryChecker.checkConstructClause(
                constructClause = inputQuery.constructClause,
                typeInspectionResult = typeInspectionResult
            )

            // Create a Select prequery

            nonTriplestoreSpecificConstructToSelectTransformer: NonTriplestoreSpecificGravsearchToPrequeryGenerator = new NonTriplestoreSpecificGravsearchToPrequeryGenerator(
                typeInspectionResult = typeInspectionResult,
                querySchema = inputQuery.querySchema.getOrElse(throw AssertionException(s"WhereClause has no querySchema")),
                settings = settings
            )


            // TODO: if the ORDER BY criterion is a property whose occurrence is not 1, then the logic does not work correctly
            // TODO: the ORDER BY criterion has to be included in a GROUP BY statement, returning more than one row if property occurs more than once

            nonTriplestoreSpecficPrequery: SelectQuery = QueryTraverser.transformConstructToSelect(
                inputQuery = inputQuery.copy(whereClause = whereClauseWithoutAnnotations),
                transformer = nonTriplestoreSpecificConstructToSelectTransformer
            )

            // Convert the non-triplestore-specific query to a triplestore-specific one.
            triplestoreSpecificQueryPatternTransformerSelect: SelectToSelectTransformer = {
                if (settings.triplestoreType.startsWith("graphdb")) {
                    // GraphDB
                    new SparqlTransformer.GraphDBSelectToSelectTransformer
                } else {
                    // Other
                    new SparqlTransformer.NoInferenceSelectToSelectTransformer
                }
            }

            // Convert the preprocessed query to a non-triplestore-specific query.
            triplestoreSpecificPrequery = QueryTraverser.transformSelectToSelect(
                inputQuery = nonTriplestoreSpecficPrequery,
                transformer = triplestoreSpecificQueryPatternTransformerSelect
            )

            // _ = println(triplestoreSpecificPrequery.toSparql)

            prequeryResponse: SparqlSelectResponse <- (storeManager ? SparqlSelectRequest(triplestoreSpecificPrequery.toSparql)).mapTo[SparqlSelectResponse]

            // variable representing the main resources
            mainResourceVar: QueryVariable = nonTriplestoreSpecificConstructToSelectTransformer.getMainResourceVariable

            // a sequence of resource IRIs that match the search criteria
            // attention: no permission checking has been done so far
            mainResourceIris: Seq[IRI] = prequeryResponse.results.bindings.map {
                resultRow: VariableResultsRow =>
                    resultRow.rowMap(mainResourceVar.variableName)
            }

            queryResultsSeparatedWithFullGraphPattern: Map[IRI, ConstructResponseUtilV2.ResourceWithValueRdfData] <- if (mainResourceIris.nonEmpty) {
                // at least one resource matched the prequery

                // get all the IRIs for variables representing dependent resources per main resource
                val dependentResourceIrisPerMainResource: MainQueryResultProcessor.DependentResourcesPerMainResource = MainQueryResultProcessor.getDependentResourceIrisPerMainResource(prequeryResponse, nonTriplestoreSpecificConstructToSelectTransformer, mainResourceVar)

                // collect all variables representing resources
                val allResourceVariablesFromTypeInspection: Set[QueryVariable] = typeInspectionResult.entities.collect {
                    case (queryVar: TypeableVariable, nonPropTypeInfo: NonPropertyTypeInfo) if OntologyConstants.KnoraApi.isKnoraApiV2Resource(nonPropTypeInfo.typeIri) => QueryVariable(queryVar.variableName)
                }.toSet

                // the user may have defined IRIs of dependent resources in the input query (type annotations)
                // only add them if they are mentioned in a positive context (not negated like in a FILTER NOT EXISTS or MINUS)
                val dependentResourceIrisFromTypeInspection: Set[IRI] = typeInspectionResult.entities.collect {
                    case (iri: TypeableIri, _: NonPropertyTypeInfo) if whereClauseWithoutAnnotations.positiveEntities.contains(IriRef(iri.iri)) =>
                        iri.iri.toString
                }.toSet

                // the IRIs of all dependent resources for all main resources
                val allDependentResourceIris: Set[IRI] = dependentResourceIrisPerMainResource.dependentResourcesPerMainResource.values.flatten.toSet ++ dependentResourceIrisFromTypeInspection

                // for each main resource, create a Map of value object variables and their Iris
                val valueObjectVarsAndIrisPerMainResource: MainQueryResultProcessor.ValueObjectVariablesAndValueObjectIris = MainQueryResultProcessor.getValueObjectVarsAndIrisPerMainResource(prequeryResponse, nonTriplestoreSpecificConstructToSelectTransformer, mainResourceVar)

                // collect all value objects IRIs (for all main resources and for all value object variables)
                val allValueObjectIris: Set[IRI] = valueObjectVarsAndIrisPerMainResource.valueObjectVariablesAndValueObjectIris.values.foldLeft(Set.empty[IRI]) {
                    case (acc: Set[IRI], valObjIrisForQueryVar: Map[QueryVariable, Set[IRI]]) =>
                        acc ++ valObjIrisForQueryVar.values.flatten.toSet
                }

                // create the main query
                // it is a Union of two sets: the main resources and the dependent resources
                val mainQuery: ConstructQuery = GravsearchMainQueryGenerator.createMainQuery(
                    mainResourceIris = mainResourceIris.map(iri => IriRef(iri.toSmartIri)).toSet,
                    dependentResourceIris = allDependentResourceIris.map(iri => IriRef(iri.toSmartIri)),
                    valueObjectIris = allValueObjectIris
                )

                val triplestoreSpecificQueryPatternTransformerConstruct: ConstructToConstructTransformer = {
                    if (settings.triplestoreType.startsWith("graphdb")) {
                        // GraphDB
                        new SparqlTransformer.GraphDBConstructToConstructTransformer
                    } else {
                        // Other
                        new SparqlTransformer.NoInferenceConstructToConstructTransformer
                    }
                }

                val triplestoreSpecificQuery = QueryTraverser.transformConstructToConstruct(
                    inputQuery = mainQuery,
                    transformer = triplestoreSpecificQueryPatternTransformerConstruct
                )

                // Convert the result to a SPARQL string and send it to the triplestore.
                val triplestoreSpecificSparql: String = triplestoreSpecificQuery.toSparql

                // println("++++++++")
                // println(triplestoreSpecificSparql)

                for {
                    mainQueryResponse: SparqlConstructResponse <- (storeManager ? SparqlConstructRequest(triplestoreSpecificSparql)).mapTo[SparqlConstructResponse]

                    // for each main resource, check if all dependent resources and value objects are still present after permission checking
                    // this ensures that the user has sufficient permissions on the whole graph pattern
                    queryResultsWithFullGraphPattern: Map[IRI, ConstructResponseUtilV2.ResourceWithValueRdfData] = MainQueryResultProcessor.getMainQueryResultsWithFullGraphPattern(
                        mainQueryResponse = mainQueryResponse,
                        dependentResourceIrisPerMainResource = dependentResourceIrisPerMainResource,
                        valueObjectVarsAndIrisPerMainResource = valueObjectVarsAndIrisPerMainResource,
                        requestingUser = requestingUser)

                    // filter out those value objects that the user does not want to be returned by the query (not present in the input query's CONSTRUCT clause)
                    queryResWithFullGraphPatternOnlyRequestedValues: Map[IRI, ConstructResponseUtilV2.ResourceWithValueRdfData] = MainQueryResultProcessor.getRequestedValuesFromResultsWithFullGraphPattern(
                        queryResultsWithFullGraphPattern,
                        valueObjectVarsAndIrisPerMainResource,
                        allResourceVariablesFromTypeInspection,
                        dependentResourceIrisFromTypeInspection,
                        nonTriplestoreSpecificConstructToSelectTransformer,
                        typeInspectionResult,
                        inputQuery
                    )

                } yield queryResWithFullGraphPatternOnlyRequestedValues

            } else {
                // the prequery returned no results, no further query is necessary
                Future(Map.empty[IRI, ConstructResponseUtilV2.ResourceWithValueRdfData])
            }

            // check if there are resources the user does not have sufficient permissions to see
            forbiddenResourceOption: Option[ReadResourceV2] <- if (mainResourceIris.size > queryResultsSeparatedWithFullGraphPattern.size) {
                // some of the main resources have been suppressed, represent them using the forbidden resource

                getForbiddenResource(requestingUser)
            } else {
                // all resources visible, no need for the forbidden resource
                Future(None)
            }

            // get the mappings
            mappingsAsMap <- getMappingsFromQueryResultsSeparated(queryResultsSeparatedWithFullGraphPattern, requestingUser)

            resources <- ConstructResponseUtilV2.createSearchResponse(
                searchResults = queryResultsSeparatedWithFullGraphPattern,
                orderByResourceIri = mainResourceIris,
                mappings = mappingsAsMap,
                forbiddenResource = forbiddenResourceOption,
                responderManager = responderManager,
                requestingUser = requestingUser
            )

        } yield ReadResourcesSequenceV2(
            numberOfResources = mainResourceIris.size,
            resources = resources
        )
    }

    /**
      * Performs a count query for a search for resources by their rdfs:label.
      *
      * @param searchValue          the values to search for.
      * @param limitToProject       limit search to given project.
      * @param limitToResourceClass limit search to given resource class.
      * @param requestingUser       the the client making the request.
      * @return a [[ReadResourcesSequenceV2]] representing the resources that have been found.
      */
    private def searchResourcesByLabelCountV2(searchValue: String, limitToProject: Option[IRI], limitToResourceClass: Option[SmartIri], requestingUser: UserADM) = {

        val searchPhrase: MatchStringWhileTyping = MatchStringWhileTyping(searchValue)

        for {
            countSparql <- Future(queries.sparql.v2.txt.searchResourceByLabel(
                triplestore = settings.triplestoreType,
                searchTerm = searchPhrase,
                limitToProject = limitToProject,
                limitToResourceClass = limitToResourceClass.map(_.toString),
                limit = 1,
                offset = 0,
                countQuery = true
            ).toString())

            // _ = println(countSparql)

            countResponse: SparqlSelectResponse <- (storeManager ? SparqlSelectRequest(countSparql)).mapTo[SparqlSelectResponse]

            // query response should contain one result with one row with the name "count"
            _ = if (countResponse.results.bindings.length != 1) {
                throw GravsearchException(s"Fulltext count query is expected to return exactly one row, but ${countResponse.results.bindings.size} given")
            }

            count = countResponse.results.bindings.head.rowMap("count")

        } yield ReadResourcesSequenceV2(
            numberOfResources = count.toInt,
            resources = Seq.empty[ReadResourceV2] // no results for a count query
        )

    }

    /**
      * Performs a search for resources by their rdfs:label.
      *
      * @param searchValue          the values to search for.
      * @param offset               the offset to be used for paging.
      * @param limitToProject       limit search to given project.
      * @param limitToResourceClass limit search to given resource class.
      * @param requestingUser       the the client making the request.
      * @return a [[ReadResourcesSequenceV2]] representing the resources that have been found.
      */
    private def searchResourcesByLabelV2(searchValue: String, offset: Int, limitToProject: Option[IRI], limitToResourceClass: Option[SmartIri], requestingUser: UserADM): Future[ReadResourcesSequenceV2] = {

        val searchPhrase: MatchStringWhileTyping = MatchStringWhileTyping(searchValue)

        for {
            searchResourceByLabelSparql <- Future(queries.sparql.v2.txt.searchResourceByLabel(
                triplestore = settings.triplestoreType,
                searchTerm = searchPhrase,
                limitToProject = limitToProject,
                limitToResourceClass = limitToResourceClass.map(_.toString),
                limit = settings.v2ResultsPerPage,
                offset = offset * settings.v2ResultsPerPage,
                countQuery = false
            ).toString())

            // _ = println(searchResourceByLabelSparql)

            searchResourceByLabelResponse: SparqlConstructResponse <- (storeManager ? SparqlConstructRequest(searchResourceByLabelSparql)).mapTo[SparqlConstructResponse]

            // collect the IRIs of main resources returned
            mainResourceIris: Set[IRI] = searchResourceByLabelResponse.statements.foldLeft(Set.empty[IRI]) {
                case (acc: Set[IRI], (subjIri: IRI, assertions: Seq[(IRI, String)])) =>
                    //statement.pred == OntologyConstants.KnoraBase.IsMainResource && statement.obj.toBoolean

                    // check if the assertions represent a main resource and include its IRI if so
                    val subjectIsMainResource: Boolean = assertions.contains((OntologyConstants.Rdf.Type, OntologyConstants.KnoraBase.Resource)) && assertions.exists {
                        case (pred, obj) =>
                            pred == OntologyConstants.KnoraBase.IsMainResource && obj.toBoolean
                    }

                    if (subjectIsMainResource) {
                        acc + subjIri
                    } else {
                        acc
                    }
            }

            // _ = println(mainResourceIris.size)

            // separate resources and value objects
            queryResultsSeparated = ConstructResponseUtilV2.splitMainResourcesAndValueRdfData(constructQueryResults = searchResourceByLabelResponse, requestingUser = requestingUser)

            // check if there are resources the user does not have sufficient permissions to see
            forbiddenResourceOption: Option[ReadResourceV2] <- if (mainResourceIris.size > queryResultsSeparated.size) {
                // some of the main resources have been suppressed, represent them using the forbidden resource
                getForbiddenResource(requestingUser)
            } else {
                // all resources visible, no need for the forbidden resource
                Future(None)
            }

            //_ = println(queryResultsSeparated)

            resources <- ConstructResponseUtilV2.createSearchResponse(
                searchResults = queryResultsSeparated,
                orderByResourceIri = mainResourceIris.toSeq.sorted,
                forbiddenResource = forbiddenResourceOption,
                responderManager = responderManager,
                requestingUser = requestingUser
            )

        } yield ReadResourcesSequenceV2(
            numberOfResources = queryResultsSeparated.size,
            resources = resources
        )

    }

}