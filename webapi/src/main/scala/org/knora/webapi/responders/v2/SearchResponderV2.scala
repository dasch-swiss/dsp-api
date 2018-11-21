/*
 * Copyright © 2015-2018 the contributors (see Contributors.md).
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

import akka.pattern._
import org.knora.webapi._
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.store.triplestoremessages._
import org.knora.webapi.messages.v2.responder.resourcemessages._
import org.knora.webapi.messages.v2.responder.searchmessages._
import org.knora.webapi.util.ActorUtil._
import org.knora.webapi.util.IriConversions._
import org.knora.webapi.util._
import org.knora.webapi.util.search.ApacheLuceneSupport.{CombineSearchTerms, MatchStringWhileTyping}
import org.knora.webapi.util.search._
import org.knora.webapi.util.search.gravsearch.GravsearchUtilV2.Gravsearch.GravsearchConstants
import org.knora.webapi.util.search.gravsearch.GravsearchUtilV2.SparqlTransformation._
import org.knora.webapi.util.search.gravsearch._
import scala.concurrent.Future

/**
  * Constants used in [[SearchResponderV2]].
  */
object SearchResponderV2Constants {

    val forbiddenResourceIri: IRI = s"http://${KnoraIdUtil.IriDomain}/permissions/forbiddenResource"
}

class SearchResponderV2 extends ResponderWithStandoffV2 {

    // A Gravsearch type inspection runner.
    private val gravsearchTypeInspectionRunner = new GravsearchTypeInspectionRunner(system = system)

    override def receive: Receive = {
        case FullTextSearchCountRequestV2(searchValue, limitToProject, limitToResourceClass, limitToStandoffClass, requestingUser) => future2Message(sender(), fulltextSearchCountV2(searchValue, limitToProject, limitToResourceClass, limitToStandoffClass, requestingUser), log)
        case FulltextSearchRequestV2(searchValue, offset, limitToProject, limitToResourceClass, limitToStandoffClass, requestingUser) => future2Message(sender(), fulltextSearchV2(searchValue, offset, limitToProject, limitToResourceClass, limitToStandoffClass, requestingUser), log)
        case GravsearchCountRequestV2(query, requestingUser) => future2Message(sender(), gravsearchCountV2(inputQuery = query, requestingUser = requestingUser), log)
        case GravsearchRequestV2(query, requestingUser) => future2Message(sender(), gravsearchV2(inputQuery = query, requestingUser = requestingUser), log)
        case SearchResourceByLabelCountRequestV2(searchValue, limitToProject, limitToResourceClass, requestingUser) => future2Message(sender(), searchResourcesByLabelCountV2(searchValue, limitToProject, limitToResourceClass, requestingUser), log)
        case SearchResourceByLabelRequestV2(searchValue, offset, limitToProject, limitToResourceClass, requestingUser) => future2Message(sender(), searchResourcesByLabelV2(searchValue, offset, limitToProject, limitToResourceClass, requestingUser), log)
        case other => handleUnexpectedMessage(sender(), other, log, this.getClass.getName)
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

        import org.knora.webapi.util.search.gravsearch.GravsearchUtilV2.FulltextSearch.FullTextSearchConstants._

        val groupConcatSeparator = StringFormatter.INFORMATION_SEPARATOR_ONE

        /**
          * Creates a CONSTRUCT query for the given resource and value object IRIs.
          *
          * @param resourceIris    the IRIs of the resources to be queried.
          * @param valueObjectIris the IRIs of the value objects to be queried.
          * @return a [[ConstructQuery]].
          */
        def createMainQuery(resourceIris: Set[IRI], valueObjectIris: Set[IRI]): ConstructQuery = {

            // WHERE patterns for the resources: check that the resource are a knora-base:Resource and that it is not marked as deleted
            val wherePatternsForResources = Seq(
                ValuesPattern(resourceVar, resourceIris.map(iri => IriRef(iri.toSmartIri))), // a ValuePattern that binds the resource IRIs to the resource variable
                StatementPattern.makeInferred(subj = resourceVar, pred = IriRef(OntologyConstants.Rdf.Type.toSmartIri), obj = IriRef(OntologyConstants.KnoraBase.Resource.toSmartIri)),
                StatementPattern.makeExplicit(subj = resourceVar, pred = IriRef(OntologyConstants.KnoraBase.IsDeleted.toSmartIri), obj = XsdLiteral(value = "false", datatype = OntologyConstants.Xsd.Boolean.toSmartIri)),
                StatementPattern.makeExplicit(subj = resourceVar, pred = resourcePropVar, obj = resourceObjectVar)
            )

            //  mark resources as the main resource and a knora-base:Resource in CONSTRUCT clause and return direct assertions about resources
            val constructPatternsForResources = Seq(
                StatementPattern(subj = resourceVar, pred = IriRef(OntologyConstants.KnoraBase.IsMainResource.toSmartIri), obj = XsdLiteral(value = "true", datatype = OntologyConstants.Xsd.Boolean.toSmartIri)),
                StatementPattern(subj = resourceVar, pred = IriRef(OntologyConstants.Rdf.Type.toSmartIri), obj = IriRef(OntologyConstants.KnoraBase.Resource.toSmartIri)),
                StatementPattern(subj = resourceVar, pred = resourcePropVar, obj = resourceObjectVar)
            )

            if (valueObjectIris.nonEmpty) {
                // value objects are to be queried

                // WHERE patterns for statements about the resources' values
                val wherePatternsForValueObjects = Seq(
                    ValuesPattern(resourceValueObject, valueObjectIris.map(iri => IriRef(iri.toSmartIri))),
                    StatementPattern.makeInferred(subj = resourceVar, pred = IriRef(OntologyConstants.KnoraBase.HasValue.toSmartIri), obj = resourceValueObject),
                    StatementPattern.makeExplicit(subj = resourceVar, pred = resourceValueProp, obj = resourceValueObject),
                    StatementPattern.makeExplicit(subj = resourceValueObject, pred = IriRef(OntologyConstants.KnoraBase.IsDeleted.toSmartIri), obj = XsdLiteral(value = "false", datatype = OntologyConstants.Xsd.Boolean.toSmartIri)),
                    StatementPattern.makeExplicit(subj = resourceValueObject, pred = resourceValueObjectProp, obj = resourceValueObjectObj)
                )

                // return assertions about value objects
                val constructPatternsForValueObjects = Seq(
                    StatementPattern(subj = resourceVar, pred = IriRef(OntologyConstants.KnoraBase.HasValue.toSmartIri), obj = resourceValueObject),
                    StatementPattern(subj = resourceVar, pred = resourceValueProp, obj = resourceValueObject),
                    StatementPattern(subj = resourceValueObject, pred = resourceValueObjectProp, obj = resourceValueObjectObj)
                )

                // WHERE patterns for standoff belonging to value objects (if any)
                val wherePatternsForStandoff = Seq(
                    ValuesPattern(resourceValueObject, valueObjectIris.map(iri => IriRef(iri.toSmartIri))),
                    StatementPattern.makeExplicit(subj = resourceValueObject, pred = IriRef(OntologyConstants.KnoraBase.ValueHasStandoff.toSmartIri), obj = standoffNodeVar),
                    StatementPattern.makeExplicit(subj = standoffNodeVar, pred = standoffPropVar, obj = standoffValueVar)
                )

                // return standoff
                val constructPatternsForStandoff = Seq(
                    StatementPattern(subj = resourceValueObject, pred = IriRef(OntologyConstants.KnoraBase.ValueHasStandoff.toSmartIri), obj = standoffNodeVar),
                    StatementPattern(subj = standoffNodeVar, pred = standoffPropVar, obj = standoffValueVar)
                )

                ConstructQuery(
                    constructClause = ConstructClause(
                        statements = constructPatternsForResources ++ constructPatternsForValueObjects ++ constructPatternsForStandoff
                    ),
                    whereClause = WhereClause(
                        Seq(
                            UnionPattern(
                                Seq(wherePatternsForResources, wherePatternsForValueObjects, wherePatternsForStandoff)
                            )
                        )
                    )
                )

            } else {
                // no value objects are to be queried

                ConstructQuery(
                    constructClause = ConstructClause(
                        statements = constructPatternsForResources
                    ),
                    whereClause = WhereClause(
                        Seq(
                            UnionPattern(
                                Seq(wherePatternsForResources)
                            )
                        )
                    )
                )
            }

        }

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
                resultRow: VariableResultsRow => resultRow.rowMap(resourceVar.variableName)
            }

            // make sure that the prequery returned some results
            queryResultsSeparatedWithFullGraphPattern: Map[IRI, ConstructResponseUtilV2.ResourceWithValueRdfData] <- if (resourceIris.nonEmpty) {

                // for each resource, create a Set of value object IRIs
                val valueObjectIrisPerResource: Map[IRI, Set[IRI]] = prequeryResponse.results.bindings.foldLeft(Map.empty[IRI, Set[IRI]]) {
                    (acc: Map[IRI, Set[IRI]], resultRow: VariableResultsRow) =>

                        val mainResIri: IRI = resultRow.rowMap(resourceVar.variableName)

                        resultRow.rowMap.get(valueObjectConcatVar.variableName) match {

                            case Some(valObjIris) =>

                                acc + (mainResIri -> valObjIris.split(groupConcatSeparator).toSet)

                            case None => acc
                        }
                }

                // println(valueObjectIrisPerResource)

                // collect all value object IRIs
                val allValueObjectIris = valueObjectIrisPerResource.values.flatten.toSet

                // create CONSTRUCT queries to query resources and their values
                val mainQuery = createMainQuery(resourceIris.toSet, allValueObjectIris)

                val triplestoreSpecificQueryPatternTransformerConstruct: ConstructToConstructTransformer = {
                    if (settings.triplestoreType.startsWith("graphdb")) {
                        // GraphDB
                        new GraphDBConstructToConstructTransformer
                    } else {
                        // Other
                        new NoInferenceConstructToConstructTransformer
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
                                    val resAndValueObjIris: ResourceIrisAndValueObjectIris = traverseValuePropertyAssertions(valuePropAssertions)

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


        } yield ReadResourcesSequenceV2(
            numberOfResources = resourceIris.size,
            resources = ConstructResponseUtilV2.createSearchResponse(
                searchResults = queryResultsSeparatedWithFullGraphPattern,
                orderByResourceIri = resourceIris,
                mappings = mappingsAsMap,
                forbiddenResource = forbiddenResourceOption
            )
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
            _ = checkConstructClause(
                constructClause = inputQuery.constructClause,
                typeInspectionResult = typeInspectionResult
            )

            // Create a Select prequery

            nonTriplestoreSpecificConstructToSelectTransformer: NonTriplestoreSpecificConstructToSelectTransformerCountQuery = new NonTriplestoreSpecificConstructToSelectTransformerCountQuery(
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
                    new GraphDBSelectToSelectTransformer
                } else {
                    // Other
                    new NoInferenceSelectToSelectTransformer
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

        /**
          *
          * Collects variables representing values that are present in the CONSTRUCT clause of the input query for the given [[Entity]] representing a resource.
          *
          * @param constructClause      the Construct clause to be looked at.
          * @param resource             the [[Entity]] representing the resource whose properties are to be collected
          * @param typeInspectionResult results of type inspection.
          * @param variableConcatSuffix the suffix appended to variable names in prequery results.
          * @return a Set of [[PropertyTypeInfo]] representing the value and link value properties to be returned to the client.
          */
        def collectValueVariablesForResource(constructClause: ConstructClause, resource: Entity, typeInspectionResult: GravsearchTypeInspectionResult, variableConcatSuffix: String): Set[QueryVariable] = {

            // make sure resource is a query variable or an IRI
            resource match {
                case queryVar: QueryVariable => ()
                case iri: IriRef => ()
                case literal: XsdLiteral => throw GravsearchException(s"${literal.toSparql} cannot represent a resource")
                case other => throw GravsearchException(s"${other.toSparql} cannot represent a resource")
            }

            // TODO: check in type information that resource represents a resource

            // get statements with the main resource as a subject
            val statementsWithResourceAsSubject: Seq[StatementPattern] = constructClause.statements.filter {
                statementPattern: StatementPattern => statementPattern.subj == resource
            }

            statementsWithResourceAsSubject.foldLeft(Set.empty[QueryVariable]) {
                (acc: Set[QueryVariable], statementPattern: StatementPattern) =>

                    // check if the predicate is a Knora value  or linking property

                    // create a key for the type annotations map
                    val typeableEntity: TypeableEntity = statementPattern.pred match {
                        case iriRef: IriRef => TypeableIri(iriRef.iri)
                        case variable: QueryVariable => TypeableVariable(variable.variableName)
                        case other => throw GravsearchException(s"Expected an IRI or a variable as the predicate of a statement, but ${other.toSparql} given")
                    }

                    // if the given key exists in the type annotations map, add it to the collection
                    if (typeInspectionResult.entities.contains(typeableEntity)) {

                        val propTypeInfo: PropertyTypeInfo = typeInspectionResult.entities(typeableEntity) match {
                            case propType: PropertyTypeInfo => propType

                            case _: NonPropertyTypeInfo =>
                                throw GravsearchException(s"Expected a property: ${statementPattern.pred.toSparql}")

                        }

                        val valueObjectVariable: Set[QueryVariable] = if (OntologyConstants.KnoraApi.isKnoraApiV2Resource(propTypeInfo.objectTypeIri)) {

                            // linking prop: get value object var and information which values are requested for dependent resource

                            // link value object variable
                            val valObjVar = createUniqueVariableNameFromEntityAndProperty(statementPattern.obj, OntologyConstants.KnoraBase.LinkValue)

                            // return link value object variable and value objects requested for the dependent resource
                            Set(QueryVariable(valObjVar.variableName + variableConcatSuffix))

                        } else {
                            statementPattern.obj match {
                                case queryVar: QueryVariable => Set(QueryVariable(queryVar.variableName + variableConcatSuffix))
                                case other => throw GravsearchException(s"Expected a variable: ${other.toSparql}")
                            }
                        }

                        acc ++ valueObjectVariable

                    } else {
                        // not a knora-api property
                        acc
                    }
            }
        }

        /**
          * Creates the main query to be sent to the triplestore.
          * Requests two sets of information: about the main resources and the dependent resources.
          *
          * @param mainResourceIris      IRIs of main resources to be queried.
          * @param dependentResourceIris IRIs of dependent resources to be queried.
          * @param valueObjectIris       IRIs of value objects to be queried (for both main and dependent resources)
          * @return the main [[ConstructQuery]] query to be executed.
          */
        def createMainQuery(mainResourceIris: Set[IriRef], dependentResourceIris: Set[IriRef], valueObjectIris: Set[IRI]): ConstructQuery = {

            import GravsearchConstants._

            // WHERE patterns for the main resource variable: check that main resource is a knora-base:Resource and that it is not marked as deleted
            val wherePatternsForMainResource = Seq(
                ValuesPattern(mainResourceVar, mainResourceIris), // a ValuePattern that binds the main resources' IRIs to the main resource variable
                StatementPattern.makeInferred(subj = mainResourceVar, pred = IriRef(OntologyConstants.Rdf.Type.toSmartIri), obj = IriRef(OntologyConstants.KnoraBase.Resource.toSmartIri)),
                StatementPattern.makeExplicit(subj = mainResourceVar, pred = IriRef(OntologyConstants.KnoraBase.IsDeleted.toSmartIri), obj = XsdLiteral(value = "false", datatype = OntologyConstants.Xsd.Boolean.toSmartIri))
            )

            // mark main resource variable in CONSTRUCT clause
            val constructPatternsForMainResource = Seq(
                StatementPattern(subj = mainResourceVar, pred = IriRef(OntologyConstants.KnoraBase.IsMainResource.toSmartIri), obj = XsdLiteral(value = "true", datatype = OntologyConstants.Xsd.Boolean.toSmartIri))
            )

            // since a CONSTRUCT query returns a flat list of triples, we can handle main and dependent resources in the same way

            // WHERE patterns for direct statements about the main resource and dependent resources
            val wherePatternsForMainAndDependentResources = Seq(
                ValuesPattern(mainAndDependentResourceVar, mainResourceIris ++ dependentResourceIris), // a ValuePattern that binds the main and dependent resources' IRIs to a variable
                StatementPattern.makeInferred(subj = mainAndDependentResourceVar, pred = IriRef(OntologyConstants.Rdf.Type.toSmartIri), obj = IriRef(OntologyConstants.KnoraBase.Resource.toSmartIri)),
                StatementPattern.makeExplicit(subj = mainAndDependentResourceVar, pred = IriRef(OntologyConstants.KnoraBase.IsDeleted.toSmartIri), obj = XsdLiteral(value = "false", datatype = OntologyConstants.Xsd.Boolean.toSmartIri)),
                StatementPattern.makeExplicit(subj = mainAndDependentResourceVar, pred = mainAndDependentResourcePropVar, obj = mainAndDependentResourceObjectVar)
            )

            // mark main and dependent resources as a knora-base:Resource in CONSTRUCT clause and return direct assertions about all resources
            val constructPatternsForMainAndDependentResources = Seq(
                StatementPattern(subj = mainAndDependentResourceVar, pred = IriRef(OntologyConstants.Rdf.Type.toSmartIri), obj = IriRef(OntologyConstants.KnoraBase.Resource.toSmartIri)),
                StatementPattern(subj = mainAndDependentResourceVar, pred = mainAndDependentResourcePropVar, obj = mainAndDependentResourceObjectVar)
            )

            if (valueObjectIris.nonEmpty) {
                // value objects are to be queried

                val mainAndDependentResourcesValueObjectsValuePattern = ValuesPattern(mainAndDependentResourceValueObject, valueObjectIris.map(iri => IriRef(iri.toSmartIri)))

                // WHERE patterns for statements about the main and dependent resources' values
                val wherePatternsForMainAndDependentResourcesValues = Seq(
                    mainAndDependentResourcesValueObjectsValuePattern,
                    StatementPattern.makeInferred(subj = mainAndDependentResourceVar, pred = IriRef(OntologyConstants.KnoraBase.HasValue.toSmartIri), obj = mainAndDependentResourceValueObject),
                    StatementPattern.makeExplicit(subj = mainAndDependentResourceVar, pred = mainAndDependentResourceValueProp, obj = mainAndDependentResourceValueObject),
                    StatementPattern.makeExplicit(subj = mainAndDependentResourceValueObject, pred = IriRef(OntologyConstants.KnoraBase.IsDeleted.toSmartIri), obj = XsdLiteral(value = "false", datatype = OntologyConstants.Xsd.Boolean.toSmartIri)),
                    StatementPattern.makeExplicit(subj = mainAndDependentResourceValueObject, pred = mainAndDependentResourceValueObjectProp, obj = mainAndDependentResourceValueObjectObj)
                )

                // return assertions about the main and dependent resources' values in CONSTRUCT clause
                val constructPatternsForMainAndDependentResourcesValues = Seq(
                    StatementPattern(subj = mainAndDependentResourceVar, pred = IriRef(OntologyConstants.KnoraBase.HasValue.toSmartIri), obj = mainAndDependentResourceValueObject),
                    StatementPattern(subj = mainAndDependentResourceVar, pred = mainAndDependentResourceValueProp, obj = mainAndDependentResourceValueObject),
                    StatementPattern(subj = mainAndDependentResourceValueObject, pred = mainAndDependentResourceValueObjectProp, obj = mainAndDependentResourceValueObjectObj)
                )

                // WHERE patterns for standoff belonging to value objects (if any)
                val wherePatternsForStandoff = Seq(
                    mainAndDependentResourcesValueObjectsValuePattern,
                    StatementPattern.makeExplicit(subj = mainAndDependentResourceValueObject, pred = IriRef(OntologyConstants.KnoraBase.ValueHasStandoff.toSmartIri), obj = standoffNodeVar),
                    StatementPattern.makeExplicit(subj = standoffNodeVar, pred = standoffPropVar, obj = standoffValueVar)
                )

                // return standoff assertions
                val constructPatternsForStandoff = Seq(
                    StatementPattern(subj = mainAndDependentResourceValueObject, pred = IriRef(OntologyConstants.KnoraBase.ValueHasStandoff.toSmartIri), obj = standoffNodeVar),
                    StatementPattern(subj = standoffNodeVar, pred = standoffPropVar, obj = standoffValueVar)
                )

                // WHERE patterns for list node pointed to by value objects (if any)
                val wherePatternsForListNode = Seq(
                    mainAndDependentResourcesValueObjectsValuePattern,
                    StatementPattern.makeExplicit(subj = mainAndDependentResourceValueObject, pred = IriRef(OntologyConstants.Rdf.Type.toSmartIri), obj = IriRef(OntologyConstants.KnoraBase.ListValue.toSmartIri)),
                    StatementPattern.makeExplicit(subj = mainAndDependentResourceValueObject, pred = IriRef(OntologyConstants.KnoraBase.ValueHasListNode.toSmartIri), obj = listNode),
                    StatementPattern.makeExplicit(subj = listNode, pred = IriRef(OntologyConstants.Rdfs.Label.toSmartIri), obj = listNodeLabel)
                )

                // return list node assertions
                val constructPatternsForListNode = Seq(
                    StatementPattern(subj = mainAndDependentResourceValueObject, pred = IriRef(OntologyConstants.KnoraBase.ValueHasListNode.toSmartIri), obj = listNode),
                    StatementPattern(subj = listNode, pred = IriRef(OntologyConstants.Rdfs.Label.toSmartIri), obj = listNodeLabel)
                )

                ConstructQuery(
                    constructClause = ConstructClause(
                        statements = constructPatternsForMainResource ++ constructPatternsForMainAndDependentResources ++ constructPatternsForMainAndDependentResourcesValues ++ constructPatternsForStandoff ++ constructPatternsForListNode
                    ),
                    whereClause = WhereClause(
                        Seq(
                            UnionPattern(
                                Seq(wherePatternsForMainResource, wherePatternsForMainAndDependentResources, wherePatternsForMainAndDependentResourcesValues, wherePatternsForStandoff, wherePatternsForListNode)
                            )
                        )
                    )
                )

            } else {
                // no value objects are to be queried

                ConstructQuery(
                    constructClause = ConstructClause(
                        statements = constructPatternsForMainResource ++ constructPatternsForMainAndDependentResources
                    ),
                    whereClause = WhereClause(
                        Seq(
                            UnionPattern(
                                Seq(wherePatternsForMainResource, wherePatternsForMainAndDependentResources)
                            )
                        )
                    )
                )
            }
        }

        /**
          * Collects the Iris of dependent resources per main resource from the results returned by the prequery.
          * Dependent resource Iris are grouped by main resource.
          *
          * @param prequeryResponse the results returned by the prequery.
          * @param transformer      the transformer that was used to turn the Gravsearch query into the prequery.
          * @param mainResourceVar  the variable representing the main resource.
          * @return a [[DependentResourcesPerMainResource]].
          */
        def getDependentResourceIrisPerMainResource(prequeryResponse: SparqlSelectResponse,
                                                    transformer: NonTriplestoreSpecificConstructToSelectTransformer,
                                                    mainResourceVar: QueryVariable): DependentResourcesPerMainResource = {

            // variables representing dependent resources
            val dependentResourceVariablesGroupConcat: Set[QueryVariable] = transformer.getDependentResourceVariablesGroupConcat

            val dependentResourcesPerMainRes = prequeryResponse.results.bindings.foldLeft(Map.empty[IRI, Set[IRI]]) {
                case (acc: Map[IRI, Set[IRI]], resultRow: VariableResultsRow) =>
                    // collect all the dependent resource Iris for the current main resource from prequery's response

                    // the main resource's Iri
                    val mainResIri: String = resultRow.rowMap(mainResourceVar.variableName)

                    // get the Iris of all the dependent resources for the given main resource
                    val dependentResIris: Set[IRI] = dependentResourceVariablesGroupConcat.flatMap {
                        dependentResVar: QueryVariable =>

                            // check if key exists: the variable representing dependent resources
                            // could be contained in an OPTIONAL or a UNION and be unbound
                            // It would be suppressed by `VariableResultsRow` in that case.
                            //
                            // Example: the query contains a dependent resource variable ?book within an OPTIONAL or a UNION.
                            // If the query returns results for the dependent resource ?book (Iris of resources that match the given criteria),
                            // those would be accessible via the variable ?book__Concat containing the aggregated results (Iris).
                            val dependentResIriOption: Option[IRI] = resultRow.rowMap.get(dependentResVar.variableName)

                            dependentResIriOption match {
                                case Some(depResIri: IRI) =>

                                    // IRIs are concatenated by GROUP_CONCAT using a separator, split them
                                    depResIri.split(transformer.groupConcatSeparator).toSeq

                                case None => Set.empty[IRI] // no Iri present since variable was inside aan OPTIONAL or UNION
                            }

                    }

                    acc + (mainResIri -> dependentResIris)
            }

            DependentResourcesPerMainResource(new ErrorHandlingMap(dependentResourcesPerMainRes, { key => throw GravsearchException(s"main resource not found: $key") }))
        }

        /**
          * Collects object variables and their values per main resource from the results returned by the prequery.
          * Value objects variables and their Iris are grouped by main resource.
          *
          * @param prequeryResponse the results returned by the prequery.
          * @param transformer      the transformer that was used to turn the Gravsearch query into the prequery.
          * @param mainResourceVar  the variable representing the main resource.
          * @return [[ValueObjectVariablesAndValueObjectIris]].
          */
        def getValueObjectVarsAndIrisPerMainResource(prequeryResponse: SparqlSelectResponse,
                                                     transformer: NonTriplestoreSpecificConstructToSelectTransformer,
                                                     mainResourceVar: QueryVariable): ValueObjectVariablesAndValueObjectIris = {

            // value objects variables present in the prequery's WHERE clause
            val valueObjectVariablesConcat = transformer.getValueObjectVarsGroupConcat

            val valueObjVarsAndIris: Map[IRI, Map[QueryVariable, Set[IRI]]] = prequeryResponse.results.bindings.foldLeft(Map.empty[IRI, Map[QueryVariable, Set[IRI]]]) {
                (acc: Map[IRI, Map[QueryVariable, Set[IRI]]], resultRow: VariableResultsRow) =>

                    // the main resource's Iri
                    val mainResIri: String = resultRow.rowMap(mainResourceVar.variableName)

                    // the the variables representing value objects and their Iris
                    val valueObjVarToIris: Map[QueryVariable, Set[IRI]] = valueObjectVariablesConcat.map {
                        valueObjVarConcat: QueryVariable =>

                            // check if key exists: the variable representing value objects
                            // could be contained in an OPTIONAL or a UNION and be unbound
                            // It would be suppressed by `VariableResultsRow` in that case.

                            // this logic works like in the case of dependent resources, see `getDependentResourceIrisPerMainResource` above.
                            val valueObjIrisOption: Option[IRI] = resultRow.rowMap.get(valueObjVarConcat.variableName)

                            val valueObjIris: Set[IRI] = valueObjIrisOption match {

                                case Some(valObjIris) =>

                                    // IRIs are concatenated by GROUP_CONCAT using a separator, split them
                                    valObjIris.split(transformer.groupConcatSeparator).toSet

                                case None => Set.empty[IRI] // since variable was inside aan OPTIONAL or UNION

                            }

                            valueObjVarConcat -> valueObjIris
                    }.toMap

                    val valueObjVarToIrisErrorHandlingMap = new ErrorHandlingMap(valueObjVarToIris, { key: QueryVariable => throw GravsearchException(s"variable not found: $key") })
                    acc + (mainResIri -> valueObjVarToIrisErrorHandlingMap)
            }

            ValueObjectVariablesAndValueObjectIris(new ErrorHandlingMap(valueObjVarsAndIris, { key => throw GravsearchException(s"main resource not found: $key") }))
        }

        /**
          * Removes the main resources from the main query's results that the requesting user has insufficient permissions on.
          * If the user does not have full permission on the full graph pattern (main resource, dependent resources, value objects)
          * then the main resource is excluded completely from the results.
          *
          * @param mainQueryResponse                     results returned by the main query.
          * @param dependentResourceIrisPerMainResource  Iris of dependent resources per main resource.
          * @param valueObjectVarsAndIrisPerMainResource variable names and Iris of value objects per main resource.
          * @return a Map of main resource Iris and their values.
          */
        def getMainQueryResultsWithFullGraphPattern(mainQueryResponse: SparqlConstructResponse,
                                                    dependentResourceIrisPerMainResource: DependentResourcesPerMainResource,
                                                    valueObjectVarsAndIrisPerMainResource: ValueObjectVariablesAndValueObjectIris): Map[IRI, ConstructResponseUtilV2.ResourceWithValueRdfData] = {

            // separate main resources and value objects (dependent resources are nested)
            // this method removes resources and values the requesting users has insufficient permissions on (missing view permissions).
            val queryResultsSep: Map[IRI, ConstructResponseUtilV2.ResourceWithValueRdfData] = ConstructResponseUtilV2.splitMainResourcesAndValueRdfData(constructQueryResults = mainQueryResponse, requestingUser = requestingUser)

            queryResultsSep.foldLeft(Map.empty[IRI, ConstructResponseUtilV2.ResourceWithValueRdfData]) {
                case (acc: Map[IRI, ConstructResponseUtilV2.ResourceWithValueRdfData], (mainResIri: IRI, values: ConstructResponseUtilV2.ResourceWithValueRdfData)) =>

                    // check for presence of dependent resources: dependentResourceIrisPerMainResource plus the dependent resources whose Iris where provided in the Gravsearch query.
                    val expectedDependentResources: Set[IRI] = dependentResourceIrisPerMainResource.dependentResourcesPerMainResource(mainResIri) /*++ dependentResourceIrisFromTypeInspection*/
                    // TODO: https://github.com/dhlab-basel/Knora/issues/924

                    // println(expectedDependentResources)

                    // check for presence of value objects: valueObjectIrisPerMainResource
                    val expectedValueObjects: Set[IRI] = valueObjectVarsAndIrisPerMainResource.valueObjectVariablesAndValueObjectIris(mainResIri).values.flatten.toSet

                    // value property assertions for the current main resource
                    val valuePropAssertions: Map[IRI, Seq[ConstructResponseUtilV2.ValueRdfData]] = values.valuePropertyAssertions

                    // all the IRIs of dependent resources and value objects contained in `valuePropAssertions`
                    val resAndValueObjIris: ResourceIrisAndValueObjectIris = traverseValuePropertyAssertions(valuePropAssertions)

                    // check if the client has sufficient permissions on all dependent resources present in the graph pattern
                    val allDependentResources: Boolean = resAndValueObjIris.resourceIris.intersect(expectedDependentResources) == expectedDependentResources

                    // check if the client has sufficient permissions on all value objects IRIs present in the graph pattern
                    val allValueObjects: Boolean = resAndValueObjIris.valueObjectIris.intersect(expectedValueObjects) == expectedValueObjects

                    // println(allValueObjects)

                    /*println("+++++++++")

                    println("graph pattern check for " + mainResIri)

                    println("expected dependent resources: " + expectedDependentResources)

                    println("all expected dependent resources present: " + allDependentResources)

                    println("given dependent resources " + resAndValueObjIris.resourceIris)

                    println("expected value objs: " + expectedValueObjects)

                    println("given value objs: " + resAndValueObjIris.valueObjectIris)

                    println("all expected value objects present: " + allValueObjects)*/

                    if (allDependentResources && allValueObjects) {
                        // sufficient permissions, include the main resource and its values
                        acc + (mainResIri -> values)
                    } else {
                        // insufficient permissions, skip the resource
                        acc
                    }
            }

        }

        /**
          * Given the results of the main query, filters out all values that the user did not ask for in the input query,
          * i.e that are not present in its CONSTRUCT clause.
          *
          * @param queryResultsWithFullGraphPattern        results with full graph pattern (that user has sufficient permissions on).
          * @param valueObjectVarsAndIrisPerMainResource   value object variables and their Iris per main resource.
          * @param allResourceVariablesFromTypeInspection  all variables representing resources.
          * @param dependentResourceIrisFromTypeInspection Iris of dependent resources used in the input query.
          * @param transformer                             the transformer that was used to turn the input query into the prequery.
          * @param typeInspectionResult                    results of type inspection of the input query.
          * @return results with only the values the user asked for in the input query's CONSTRUCT clause.
          */
        def getRequestedValuesFromResultsWithFullGraphPattern(queryResultsWithFullGraphPattern: Map[IRI, ConstructResponseUtilV2.ResourceWithValueRdfData],
                                          valueObjectVarsAndIrisPerMainResource: ValueObjectVariablesAndValueObjectIris,
                                          allResourceVariablesFromTypeInspection: Set[QueryVariable],
                                          dependentResourceIrisFromTypeInspection: Set[IRI],
                                          transformer: NonTriplestoreSpecificConstructToSelectTransformer,
                                          typeInspectionResult: GravsearchTypeInspectionResult): Map[IRI, ConstructResponseUtilV2.ResourceWithValueRdfData] = {

            // sort out those value objects that the user did not ask for in the input query's CONSTRUCT clause
            // those are present in the input query's WHERE clause but not in its CONSTRUCT clause

            // for each resource variable (both main and dependent resources),
            // collect the value object variables associated with it in the input query's CONSTRUCT clause
            // resource variables from types inspection are used
            //
            // Example: the statement "?page incunabula:seqnum ?seqnum ." is contained in the input query's CONSTRUCT clause.
            // ?seqnum (?seqnum__Concat) is a requested value and is associated with the resource variable ?page.
            val requestedValueObjectVariablesForAllResVars: Set[QueryVariable] = allResourceVariablesFromTypeInspection.flatMap {
                resVar =>
                    collectValueVariablesForResource(inputQuery.constructClause, resVar, typeInspectionResult, transformer.groupConcatVariableSuffix)
            }

            // for each resource Iri (only dependent resources),
            // collect the value object variables associated with it in the input query's CONSTRUCT clause
            // dependent resource Iris from types inspection are used
            //
            // Example: the statement "<http://rdfh.ch/5e77e98d2603> incunabula:title ?title ." is contained in the input query's CONSTRUCT clause.
            // ?title (?title__Concat) is a requested value and is associated with the dependent resource Iri <http://rdfh.ch/5e77e98d2603>.
            val requestedValueObjectVariablesForDependentResIris: Set[QueryVariable] = dependentResourceIrisFromTypeInspection.flatMap {
                depResIri =>
                    collectValueVariablesForResource(inputQuery.constructClause, IriRef(iri = depResIri.toSmartIri), typeInspectionResult, transformer.groupConcatVariableSuffix)
            }

            // combine all value object variables into one set
            val allRequestedValueObjectVariables: Set[QueryVariable] = requestedValueObjectVariablesForAllResVars ++ requestedValueObjectVariablesForDependentResIris

            // collect requested value object Iris for each main resource
            val requestedValObjIrisPerMainResource: Map[IRI, Set[IRI]] = queryResultsWithFullGraphPattern.keySet.map {
                mainResIri =>

                    // get all value object variables and Iris for the current main resource
                    val valueObjIrisForRes: Map[QueryVariable, Set[IRI]] = valueObjectVarsAndIrisPerMainResource.valueObjectVariablesAndValueObjectIris(mainResIri)

                    // get those value object Iris from the results that the user asked for in the input query's CONSTRUCT clause
                    val valObjIrisRequestedForRes: Set[IRI] = allRequestedValueObjectVariables.flatMap {
                        requestedQueryVar: QueryVariable =>
                            valueObjIrisForRes.getOrElse(requestedQueryVar, throw AssertionException(s"key $requestedQueryVar is absent in prequery's value object IRIs collection for resource $mainResIri"))
                    }

                    mainResIri -> valObjIrisRequestedForRes
            }.toMap

            // for each main resource, get only the requested value objects
            queryResultsWithFullGraphPattern.map {
                case (mainResIri: IRI, assertions: ConstructResponseUtilV2.ResourceWithValueRdfData) =>

                    // get the Iris of all the value objects requested for the current main resource
                    val valueObjIrisRequestedForRes: Set[IRI] = requestedValObjIrisPerMainResource.getOrElse(mainResIri, throw AssertionException(s"key $mainResIri is absent in requested value object IRIs collection for resource $mainResIri"))

                    /**
                      * Recursively filters out those values that the user does not want to see.
                      * Starts with the values of the main resource and also processes link values, possibly containing dependent resources with values.
                      *
                      * @param values the values to be filtered.
                      * @return filtered values.
                      */
                    def traverseAndFilterValues(values: ConstructResponseUtilV2.ResourceWithValueRdfData): Map[IRI, Seq[ConstructResponseUtilV2.ValueRdfData]] = {
                        values.valuePropertyAssertions.foldLeft(Map.empty[IRI, Seq[ConstructResponseUtilV2.ValueRdfData]]) {
                            case (acc, (propIri: IRI, values: Seq[ConstructResponseUtilV2.ValueRdfData])) =>

                                // filter values for the current resource
                                val valuesFiltered: Seq[ConstructResponseUtilV2.ValueRdfData] = values.filter {
                                    valueObj: ConstructResponseUtilV2.ValueRdfData =>
                                        // only return those value objects whose Iris are contained in valueObjIrisRequestedForRes
                                        valueObjIrisRequestedForRes(valueObj.valueObjectIri)
                                }

                                // if there are link values including a target resource, apply filter to their values too
                                val valuesFilteredRecursively: Seq[ConstructResponseUtilV2.ValueRdfData] = valuesFiltered.map {
                                    valObj: ConstructResponseUtilV2.ValueRdfData =>
                                        if (valObj.nestedResource.nonEmpty) {

                                            val targetResourceAssertions: ConstructResponseUtilV2.ResourceWithValueRdfData = valObj.nestedResource.get

                                            // apply filter to the target resource's values
                                            val targetResourceAssertionsFiltered: Map[IRI, Seq[ConstructResponseUtilV2.ValueRdfData]] = traverseAndFilterValues(targetResourceAssertions)

                                            valObj.copy(
                                                nestedResource = Some(targetResourceAssertions.copy(
                                                    valuePropertyAssertions = targetResourceAssertionsFiltered
                                                ))
                                            )
                                        } else {
                                            valObj
                                        }
                                }

                                // ignore properties if there are no value objects to be displayed.
                                // if the user does not want to see a value, the property pointing to that value has to be ignored.
                                if (valuesFilteredRecursively.nonEmpty) {
                                    acc + (propIri -> valuesFilteredRecursively)
                                } else {
                                    // ignore this property since there are no value objects
                                    // Example: the input query's WHERE clause contains the statement "?page incunabula:seqnum ?seqnum .",
                                    // but the statement is not present in its CONSTRUCT clause. Therefore, the property incunabula:seqnum can be ignored
                                    // since no value objects are returned for it.
                                    acc
                                }
                        }
                    }

                    // filter values for the current main resource
                    val requestedValuePropertyAssertions: Map[IRI, Seq[ConstructResponseUtilV2.ValueRdfData]] = traverseAndFilterValues(assertions)

                    // only return the requested values for the current main resource
                    mainResIri -> assertions.copy(
                        valuePropertyAssertions = requestedValuePropertyAssertions
                    )
            }
        }

        /**
          * Represents dependent resources organized by main resource.
          *
          * @param dependentResourcesPerMainResource a set of dependent resource Iris organized by main resource.
          */
        case class DependentResourcesPerMainResource(dependentResourcesPerMainResource: Map[IRI, Set[IRI]])

        /**
          * Represents value object variables and value object Iris organized by main resource.
          *
          * @param valueObjectVariablesAndValueObjectIris a set of value object Iris organized by value object variable and main resource.
          */
        case class ValueObjectVariablesAndValueObjectIris(valueObjectVariablesAndValueObjectIris: Map[IRI, Map[QueryVariable, Set[IRI]]])


        for {
            // Do type inspection and remove type annotations from the WHERE clause.

            typeInspectionResult: GravsearchTypeInspectionResult <- gravsearchTypeInspectionRunner.inspectTypes(inputQuery.whereClause, requestingUser)
            whereClauseWithoutAnnotations: WhereClause = GravsearchTypeInspectionUtil.removeTypeAnnotations(inputQuery.whereClause)

            // Validate schemas and predicates in the CONSTRUCT clause.
            _ = checkConstructClause(
                constructClause = inputQuery.constructClause,
                typeInspectionResult = typeInspectionResult
            )

            // Create a Select prequery

            nonTriplestoreSpecificConstructToSelectTransformer: NonTriplestoreSpecificConstructToSelectTransformer = new NonTriplestoreSpecificConstructToSelectTransformer(
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
                    new GraphDBSelectToSelectTransformer
                } else {
                    // Other
                    new NoInferenceSelectToSelectTransformer
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
                val dependentResourceIrisPerMainResource: DependentResourcesPerMainResource = getDependentResourceIrisPerMainResource(prequeryResponse, nonTriplestoreSpecificConstructToSelectTransformer, mainResourceVar)

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
                val valueObjectVarsAndIrisPerMainResource: ValueObjectVariablesAndValueObjectIris = getValueObjectVarsAndIrisPerMainResource(prequeryResponse, nonTriplestoreSpecificConstructToSelectTransformer, mainResourceVar)

                // collect all value objects IRIs (for all main resources and for all value object variables)
                val allValueObjectIris: Set[IRI] = valueObjectVarsAndIrisPerMainResource.valueObjectVariablesAndValueObjectIris.values.foldLeft(Set.empty[IRI]) {
                    case (acc: Set[IRI], valObjIrisForQueryVar: Map[QueryVariable, Set[IRI]]) =>
                        acc ++ valObjIrisForQueryVar.values.flatten.toSet
                }

                // create the main query
                // it is a Union of two sets: the main resources and the dependent resources
                val mainQuery: ConstructQuery = createMainQuery(
                    mainResourceIris = mainResourceIris.map(iri => IriRef(iri.toSmartIri)).toSet,
                    dependentResourceIris = allDependentResourceIris.map(iri => IriRef(iri.toSmartIri)),
                    valueObjectIris = allValueObjectIris
                )

                val triplestoreSpecificQueryPatternTransformerConstruct: ConstructToConstructTransformer = {
                    if (settings.triplestoreType.startsWith("graphdb")) {
                        // GraphDB
                        new GraphDBConstructToConstructTransformer
                    } else {
                        // Other
                        new NoInferenceConstructToConstructTransformer
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
                    queryResultsWithFullGraphPattern: Map[IRI, ConstructResponseUtilV2.ResourceWithValueRdfData] = getMainQueryResultsWithFullGraphPattern(
                        mainQueryResponse = mainQueryResponse,
                        dependentResourceIrisPerMainResource = dependentResourceIrisPerMainResource,
                        valueObjectVarsAndIrisPerMainResource = valueObjectVarsAndIrisPerMainResource)

                    // filter out those value objects that the user does not want to be returned by the query (not present in the input query's CONSTRUCT clause)
                    queryResWithFullGraphPatternOnlyRequestedValues: Map[IRI, ConstructResponseUtilV2.ResourceWithValueRdfData] = getRequestedValuesFromResultsWithFullGraphPattern(
                        queryResultsWithFullGraphPattern,
                        valueObjectVarsAndIrisPerMainResource,
                        allResourceVariablesFromTypeInspection,
                        dependentResourceIrisFromTypeInspection,
                        nonTriplestoreSpecificConstructToSelectTransformer,
                        typeInspectionResult
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


        } yield ReadResourcesSequenceV2(
            numberOfResources = mainResourceIris.size,
            resources = ConstructResponseUtilV2.createSearchResponse(
                searchResults = queryResultsSeparatedWithFullGraphPattern,
                orderByResourceIri = mainResourceIris,
                mappings = mappingsAsMap,
                forbiddenResource = forbiddenResourceOption
            )
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

        } yield ReadResourcesSequenceV2(
            numberOfResources = queryResultsSeparated.size,
            resources = ConstructResponseUtilV2.createSearchResponse(
                searchResults = queryResultsSeparated,
                orderByResourceIri = mainResourceIris.toSeq.sorted,
                forbiddenResource = forbiddenResourceOption)
        )


    }

}
