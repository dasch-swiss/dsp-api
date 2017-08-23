/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and Sepideh Alassi.
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

import akka.http.scaladsl.util.FastFuture
import akka.pattern._
import org.knora.webapi._
import org.knora.webapi.messages.store.triplestoremessages.{SparqlConstructRequest, SparqlConstructResponse}
import org.knora.webapi.messages.v1.responder.usermessages.UserProfileV1
import org.knora.webapi.messages.v2.responder._
import org.knora.webapi.messages.v2.responder.searchmessages._
import org.knora.webapi.responders.Responder
import org.knora.webapi.util.ActorUtil._
import org.knora.webapi.util.search._
import org.knora.webapi.util.search.v2.{ApiV2Schema, _}
import org.knora.webapi.util.{ConstructResponseUtilV2, InputValidation}

import scala.collection.mutable
import scala.concurrent.Future

class SearchResponderV2 extends Responder {

    def receive = {
        case FulltextSearchGetRequestV2(searchValue, userProfile) => future2Message(sender(), fulltextSearchV2(searchValue, userProfile), log)
        case ExtendedSearchGetRequestV2(query, userProfile) => future2Message(sender(), extendedSearchV2(inputQuery = query, userProfile = userProfile), log)
        case SearchResourceByLabelRequestV2(searchValue, userProfile) => future2Message(sender(), searchResourcesByLabelV2(searchValue, userProfile), log)
        case other => handleUnexpectedMessage(sender(), other, log, this.getClass.getName)
    }

    /**
      * Performs a fulltext search (simple search).
      *
      * @param searchValue the values to search for.
      * @param userProfile the profile of the client making the request.
      * @return a [[ReadResourcesSequenceV2]] representing the resources that have been found.
      */
    private def fulltextSearchV2(searchValue: String, userProfile: UserProfileV1): Future[ReadResourcesSequenceV2] = {

        for {
            searchSparql <- Future(queries.sparql.v2.txt.searchFulltext(
                triplestore = settings.triplestoreType,
                searchTerms = searchValue
            ).toString())

            searchResponse: SparqlConstructResponse <- (storeManager ? SparqlConstructRequest(searchSparql)).mapTo[SparqlConstructResponse]

            // separate resources and value objects
            queryResultsSeparated = ConstructResponseUtilV2.splitResourcesAndValueRdfData(constructQueryResults = searchResponse, userProfile = userProfile)

        } yield ReadResourcesSequenceV2(numberOfResources = queryResultsSeparated.size, resources = ConstructResponseUtilV2.createSearchResponse(queryResultsSeparated))

    }

    /**
      * Performs an extended search using a Sparql query provided by the user.
      *
      * @param inputQuery  Sparql construct query provided by the client.
      * @param userProfile the profile of the client making the request.
      * @return a [[ReadResourcesSequenceV2]] representing the resources that have been found.
      */
    private def extendedSearchV2(inputQuery: ConstructQuery, apiSchema: ApiV2Schema.Value = ApiV2Schema.SIMPLE, userProfile: UserProfileV1): Future[ReadResourcesSequenceV2] = {

        /**
          * A [[QueryPatternTransformer]] that preprocesses the input CONSTRUCT query by converting external IRIs to internal ones
          * and disabling inference for individual statements as necessary.
          */
        class Preprocessor extends QueryPatternTransformer {
            def transformStatementInConstruct(statementPattern: StatementPattern): Seq[StatementPattern] = Seq(preprocessStatementPattern(statementPattern = statementPattern))

            def transformStatementInWhere(statementPattern: StatementPattern): Seq[StatementPattern] = Seq(preprocessStatementPattern(statementPattern = statementPattern))

            def transformFilter(filterPattern: FilterPattern): Seq[QueryPattern] = Seq(FilterPattern(preprocessFilterExpression(filterPattern.expression)))

            /**
              * Preprocesses a [[FilterExpression]] by converting external IRIs to internal ones.
              *
              * @param filterExpression a filter expression.
              * @return the preprocessed expression.
              */
            def preprocessFilterExpression(filterExpression: FilterExpression): FilterExpression = {
                filterExpression match {
                    case entity: Entity => preprocessEntity(entity)
                    case compareExpr: CompareExpression => CompareExpression(leftArg = preprocessFilterExpression(compareExpr.leftArg), operator = compareExpr.operator, rightArg = preprocessFilterExpression(compareExpr.rightArg))
                    case andExpr: AndExpression => AndExpression(leftArg = preprocessFilterExpression(andExpr.leftArg), rightArg = preprocessFilterExpression(andExpr.rightArg))
                    case orExpr: OrExpression => OrExpression(leftArg = preprocessFilterExpression(orExpr.leftArg), rightArg = preprocessFilterExpression(orExpr.rightArg))
                }
            }

            /**
              * Preprocesses an [[Entity]] by converting external IRIs to internal ones.
              *
              * @param entity an entity provided by [[SearchParserV2]].
              * @return the preprocessed entity.
              */
            def preprocessEntity(entity: Entity): Entity = {
                // convert external Iris to internal Iris if needed

                entity match {
                    case iriRef: IriRef => // if an Iri is an external knora-api entity (with value object or simple), convert it to an internal Iri
                        if (InputValidation.isKnoraApiEntityIri(iriRef.iri)) {
                            IriRef(InputValidation.externalIriToInternalIri(iriRef.iri, () => throw BadRequestException(s"${iriRef.iri} is not a valid external knora-api entity Iri")))
                        } else {
                            IriRef(InputValidation.toIri(iriRef.iri, () => throw BadRequestException(s"$iriRef is not a valid IRI")))
                        }

                    case other => other
                }
            }

            /**
              * Preprocesses a [[StatementPattern]] by converting external IRIs to internal ones and disabling inference if necessary.
              *
              * @param statementPattern a statement provided by SearchParserV2.
              * @return the preprocessed statement pattern.
              */
            def preprocessStatementPattern(statementPattern: StatementPattern): StatementPattern = {

                val subj = preprocessEntity(statementPattern.subj)
                val pred = preprocessEntity(statementPattern.pred)
                val obj = preprocessEntity(statementPattern.obj)

                val namedGraph = None // use inference for all user-provided statements in Where clause

                StatementPattern(
                    subj = subj,
                    pred = pred,
                    obj = obj,
                    namedGraph = namedGraph
                )
            }
        }

        /**
          * A [[QueryPatternTransformer]] that generates non-triplestore-specific SPARQL.
          *
          * @param typeInspectionResult the result of type inspection of the original query.
          */
        class NonTriplestoreSpecificQueryPatternTransformer(typeInspectionResult: TypeInspectionResult) extends QueryPatternTransformer {

            // a set containing all `TypeableEntity` (keys of `typeInspectionResult`) that have already been processed
            // in order to prevent duplicates
            val processedTypeInformationKeysConstructClause = mutable.Set.empty[TypeableEntity]
            val processedTypeInformationKeysWhereClause = mutable.Set.empty[TypeableEntity]

            /**
              * Convert an [[Entity]] to a [[TypeableEntity]] (key of type inspection results).
              * The entity is expected to be a variable or an Iri, otherwise `None` is returned.
              *
              * @param entity the entity to be converted to a [[TypeableEntity]].
              * @return an Option of a [[TypeableEntity]].
              */
            def toTypeableEntityKey(entity: Entity): Option[TypeableEntity] = {

                entity match {
                    case queryVar: QueryVariable => Some(TypeableVariable(queryVar.variableName))

                    case iriRef: IriRef => Some(TypeableIri(iriRef.iri))

                    case _ => None
                }

            }

            /**
              * Create additional statements for the given non property type information.
              *
              * @param nonPropertyTypeInfo           type information about the statement provided by the user.
              * @param additionalStatementsForEntity the entity to create additional statements for.
              * @param statementPattern              statement provided by the user.
              * @return a sequence of [[QueryPattern]] representing additional statements created from the given type information.
              */
            def createAdditionalStatementsForNonPropertyType(nonPropertyTypeInfo: NonPropertyTypeInfo, additionalStatementsForEntity: Entity, statementPattern: StatementPattern): Seq[StatementPattern] = {

                //println(s"create statements for $nonPropertyTypeInfo about $additionalStatementsForEntity in statement $statementPattern")

                // check which version of the api is used: simple or with value object

                Seq.empty[StatementPattern]
            }

            /**
              * Create additional statements for the given property type information.
              *
              * @param propertyTypeInfo type information about the statement provided by the user.
              * @param statementPattern statement provided by the user.
              * @return a sequence of [[QueryPattern]] representing additional statements created from the given type information.
              */
            def convertStatementForPropertyType(propertyTypeInfo: PropertyTypeInfo, statementPattern: StatementPattern): Seq[StatementPattern] = {

                //println(s"create statements for $propertyTypeInfo in $statementPattern")

                // check which version of the api is used: simple or with value object

                // decide whether to keep the originally given statement or not
                // if pred is a valueProp and the simple api is used, do not return the original statement
                // it had to be converted to comply with Knora's value object structure

                Seq.empty[StatementPattern]
            }

            /**
              * Process a given statement pattern based on type information.
              * This function is used for the Construct and Where clause of a user provided Sparql query.
              *
              * @param processedTypeInformationKeys type information keys that already have been processed.
              * @param statementPattern the statement to be processed.
              * @param inWhereClause indicates whether the given statement is inside a Where clause or not (needed to handle inference).
              * @return a sequence of [[StatementPattern]].
              */
            def processStatementPattern(processedTypeInformationKeys: mutable.Set[TypeableEntity], statementPattern: StatementPattern, inWhereClause: Boolean): Seq[StatementPattern] = {
                // look at the statement's subject, predicate, and object and generate additional statements if needed based on the given type information.
                // transform the originally given statement if necessary when processing the predicate

                // create `TypeableEntity` (keys in `typeInspectionResult`) from the given statement's elements
                val subjTypeInfoKey: Option[TypeableEntity] = toTypeableEntityKey(statementPattern.subj)

                val predTypeInfoKey: Option[TypeableEntity] = toTypeableEntityKey(statementPattern.pred)

                val objTypeInfoKey: Option[TypeableEntity] = toTypeableEntityKey(statementPattern.obj)

                // check if there exists type information for the given statement's subject
                val additionalStatementsForSubj: Seq[StatementPattern] = if (subjTypeInfoKey.nonEmpty && (typeInspectionResult.typedEntities -- processedTypeInformationKeys contains subjTypeInfoKey.get)) {
                    // process type information for the subject into additional statements

                    // add TypeableEntity (keys of `typeInspectionResult`) for subject in order to prevent duplicates
                    processedTypeInformationKeys += subjTypeInfoKey.get

                    val nonPropTypeInfo: NonPropertyTypeInfo = typeInspectionResult.typedEntities(subjTypeInfoKey.get) match {
                        case nonPropInfo: NonPropertyTypeInfo => nonPropInfo

                        case _ => throw AssertionException(s"NonPropertyTypeInfo expected for ${subjTypeInfoKey.get}")
                    }

                    createAdditionalStatementsForNonPropertyType(nonPropTypeInfo, statementPattern.subj, statementPattern)
                } else {
                    Seq.empty[StatementPattern]
                }

                // process type information for the given statement's predicate, possibly converting the originally given statement (e.g., in case of a non linking property for the simple api)
                val additionalStatementsForPred: Seq[StatementPattern] = if (predTypeInfoKey.nonEmpty && (typeInspectionResult.typedEntities contains predTypeInfoKey.get)) {
                    // process type information for the predicate into additional statements

                    val propTypeInfo = typeInspectionResult.typedEntities(predTypeInfoKey.get) match {
                        case propInfo: PropertyTypeInfo => propInfo

                        case _ => throw AssertionException(s"PropertyTypeInfo expected for ${predTypeInfoKey.get}")
                    }

                    convertStatementForPropertyType(propTypeInfo, statementPattern)
                } else {
                    // no type information given and thus no further processing needed, just return the originally given statement (e.g., rdf:type)
                    Seq(statementPattern)
                }

                // check if there exists type information for the given statement's object
                val additionalStatementsForObj: Seq[StatementPattern] = if (objTypeInfoKey.nonEmpty && (typeInspectionResult.typedEntities -- processedTypeInformationKeys contains objTypeInfoKey.get)) {
                    // process type information for the object into additional statements

                    // add TypeableEntity (keys of `typeInspectionResult`) for object in order to prevent duplicates
                    processedTypeInformationKeys += objTypeInfoKey.get

                    val nonPropTypeInfo: NonPropertyTypeInfo = typeInspectionResult.typedEntities(objTypeInfoKey.get) match {
                        case nonPropInfo: NonPropertyTypeInfo => nonPropInfo

                        case _ => throw AssertionException(s"NonPropertyTypeInfo expected for ${objTypeInfoKey.get}")
                    }
                    createAdditionalStatementsForNonPropertyType(nonPropTypeInfo, statementPattern.obj, statementPattern)
                } else {
                    Seq.empty[StatementPattern]
                }

                additionalStatementsForSubj ++ additionalStatementsForPred ++ additionalStatementsForObj
            }

            def transformStatementInConstruct(statementPattern: StatementPattern): Seq[StatementPattern] = {

                processStatementPattern(processedTypeInformationKeys = processedTypeInformationKeysConstructClause,
                    statementPattern = statementPattern,
                    inWhereClause = false)
            }

            def transformStatementInWhere(statementPattern: StatementPattern): Seq[StatementPattern] = {

                processStatementPattern(processedTypeInformationKeys = processedTypeInformationKeysWhereClause,
                    statementPattern = statementPattern,
                    inWhereClause = true)

            }

            def transformFilter(filterPattern: FilterPattern): Seq[QueryPattern] = {

                Seq.empty[QueryPattern]
            }
        }



        /**
          * Transforms non-triplestore-specific query patterns to GraphDB-specific ones.
          */
        class GraphDBQueryPatternTransformer extends QueryPatternTransformer {
            def transformStatementInConstruct(statementPattern: StatementPattern): Seq[StatementPattern] = Seq(statementPattern)

            def transformStatementInWhere(statementPattern: StatementPattern): Seq[StatementPattern] = {
                val transformedPattern = statementPattern.copy(
                    namedGraph = statementPattern.namedGraph match {
                        case Some(IriRef(OntologyConstants.NamedGraphs.KnoraExplicitNamedGraph)) => Some(IriRef(OntologyConstants.NamedGraphs.GraphDBExplicitNamedGraph))
                        case Some(IriRef(_)) => throw AssertionException(s"Named graphs other than ${OntologyConstants.NamedGraphs.KnoraExplicitNamedGraph} cannot occur in non-triplestore-specific generated search query SPARQL")
                        case None => None
                    }
                )

                Seq(transformedPattern)
            }

            def transformFilter(filterPattern: FilterPattern): Seq[QueryPattern] = Seq(filterPattern)
        }

        /**
          * Transforms non-triplestore-specific query patterns for a triplestore that does not have inference enabled.
          */
        class NoInferenceQueryPatternTransformer extends QueryPatternTransformer {
            def transformStatementInConstruct(statementPattern: StatementPattern): Seq[StatementPattern] = Seq(statementPattern)

            def transformStatementInWhere(statementPattern: StatementPattern): Seq[StatementPattern] = {
                // TODO: if OntologyConstants.NamedGraphs.KnoraExplicitNamedGraph occurs, remove it and use property path syntax to emulate inference.
                Seq(statementPattern)
            }

            def transformFilter(filterPattern: FilterPattern): Seq[QueryPattern] = Seq(filterPattern)
        }

        for {
        // Do type inspection and remove type annotations from the WHERE clause.

            typeInspector <- FastFuture.successful(new ExplicitTypeInspectorV2(apiSchema))
            whereClauseWithoutAnnotations: WhereClause = typeInspector.removeTypeAnnotations(inputQuery.whereClause)
            typeInspectionResult: TypeInspectionResult = typeInspector.inspectTypes(inputQuery.whereClause)

            // Preprocess the query to convert API IRIs to internal IRIs and to set inference per statement.

            preprocessedQuery: ConstructQuery = ConstructQueryTransformer.transformQuery(
                inputQuery = inputQuery.copy(whereClause = whereClauseWithoutAnnotations),
                queryPatternTransformer = new Preprocessor
            )

            // Convert the preprocessed query to a non-triplestore-specific query.

            nonTriplestoreSpecificQuery: ConstructQuery = ConstructQueryTransformer.transformQuery(
                inputQuery = preprocessedQuery,
                queryPatternTransformer = new NonTriplestoreSpecificQueryPatternTransformer(typeInspectionResult)
            )

            _ = println(nonTriplestoreSpecificQuery)

            // Convert the non-triplestore-specific query to a triplestore-specific one.

            triplestoreSpecificQueryPatternTransformer: QueryPatternTransformer = {
                if (settings.triplestoreType.startsWith("graphdb")) {
                    // GraphDB
                    new GraphDBQueryPatternTransformer
                } else {
                    // Other
                    new NoInferenceQueryPatternTransformer
                }
            }

            triplestoreSpecificQuery = ConstructQueryTransformer.transformQuery(
                inputQuery = nonTriplestoreSpecificQuery,
                queryPatternTransformer = triplestoreSpecificQueryPatternTransformer
            )

            // Convert the result to a SPARQL string and send it to the triplestore.

            triplestoreSpecificSparql: String = triplestoreSpecificQuery.toSparql

            searchResponse: SparqlConstructResponse <- (storeManager ? SparqlConstructRequest(triplestoreSpecificSparql)).mapTo[SparqlConstructResponse]

            // separate resources and value objects
            queryResultsSeparated: Map[IRI, ConstructResponseUtilV2.ResourceWithValueRdfData] = ConstructResponseUtilV2.splitResourcesAndValueRdfData(constructQueryResults = searchResponse, userProfile = userProfile)

        } yield ReadResourcesSequenceV2(numberOfResources = queryResultsSeparated.size, resources = ConstructResponseUtilV2.createSearchResponse(queryResultsSeparated))
    }

    /**
      * Performs a search for resources by their label.
      *
      * @param searchValue the values to search for.
      * @param userProfile the profile of the client making the request.
      * @return a [[ReadResourcesSequenceV2]] representing the resources that have been found.
      */
    private def searchResourcesByLabelV2(searchValue: String, userProfile: UserProfileV1): Future[ReadResourcesSequenceV2] = {

        for {
            searchResourceByLabelSparql <- Future(queries.sparql.v2.txt.searchResourceByLabel(
                triplestore = settings.triplestoreType,
                searchTerms = searchValue
            ).toString())

            searchResourceByLabelResponse: SparqlConstructResponse <- (storeManager ? SparqlConstructRequest(searchResourceByLabelSparql)).mapTo[SparqlConstructResponse]

            // separate resources and value objects
            queryResultsSeparated = ConstructResponseUtilV2.splitResourcesAndValueRdfData(constructQueryResults = searchResourceByLabelResponse, userProfile = userProfile)

        //_ = println(queryResultsSeparated)

        } yield ReadResourcesSequenceV2(numberOfResources = queryResultsSeparated.size, resources = ConstructResponseUtilV2.createSearchResponse(queryResultsSeparated))


    }
}