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
import org.knora.webapi.messages.v1.responder.valuemessages.JulianDayNumberValueV1
import org.knora.webapi.messages.v2.responder._
import org.knora.webapi.messages.v2.responder.searchmessages._
import org.knora.webapi.responders.Responder
import org.knora.webapi.util.ActorUtil._
import org.knora.webapi.util.search._
import org.knora.webapi.util.search.v2.{ApiV2Schema, _}
import org.knora.webapi.util.{ConstructResponseUtilV2, DateUtilV1, InputValidation}

import scala.concurrent.Future

class SearchResponderV2 extends Responder {

    def receive = {
        case FulltextSearchGetRequestV2(searchValue, userProfile) => future2Message(sender(), fulltextSearchV2(searchValue, userProfile), log)
        case ExtendedSearchGetRequestV2(query, userProfile) => future2Message(sender(), extendedSearchV2(simpleConstructQuery = query, userProfile = userProfile), log)
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

    private def newExtendedSearchV2(constructQuery: ConstructQuery, apiSchema: ApiV2Schema.Value = ApiV2Schema.SIMPLE, userProfile: UserProfileV1): Future[ReadResourcesSequenceV2] = {
        for {
            typeInspector <- FastFuture.successful(new ExplicitTypeInspectorV2(apiSchema))
            whereClauseWithoutAnnotations: WhereClause = typeInspector.removeTypeAnnotations(constructQuery.whereClause)
            typeInspectionResultWhere: TypeInspectionResult = typeInspector.inspectTypes(constructQuery.whereClause)

            // TODO: transform the construct query as needed.

            searchSparql = constructQuery.toSparql
            searchResponse: SparqlConstructResponse <- (storeManager ? SparqlConstructRequest(searchSparql)).mapTo[SparqlConstructResponse]

            // separate resources and value objects
            queryResultsSeparated: Map[IRI, ConstructResponseUtilV2.ResourceWithValueRdfData] = ConstructResponseUtilV2.splitResourcesAndValueRdfData(constructQueryResults = searchResponse, userProfile = userProfile)

        } yield ReadResourcesSequenceV2(numberOfResources = queryResultsSeparated.size, resources = ConstructResponseUtilV2.createSearchResponse(queryResultsSeparated))
    }

    /**
      * Performs an extended search using a Sparql query provided by the user.
      *
      * @param simpleConstructQuery Sparql construct query provided by the client.
      * @param userProfile          the profile of the client making the request.
      * @return a [[ReadResourcesSequenceV2]] representing the resources that have been found.
      */
    private def extendedSearchV2(simpleConstructQuery: ConstructQuery, apiSchema: ApiV2Schema.Value = ApiV2Schema.SIMPLE, userProfile: UserProfileV1): Future[ReadResourcesSequenceV2] = {

        /**
          * Converts a [[FilterExpression]] to an [[ExtendedSearchFilterExpression]].
          *
          * @param filterExpression a filter expression provided by [[SearchParserV2]].
          * @return a [[ExtendedSearchFilterExpression]].
          */
        def convertFilterExpressionToExtendedSearchFilterExpression(filterExpression: FilterExpression): ExtendedSearchFilterExpression = {
            filterExpression match {
                case entity: Entity => convertSearchParserEntityToExtendedSearchEntity(entity)
                case compareExpr: CompareExpression => ExtendedSearchCompareExpression(leftArg = convertFilterExpressionToExtendedSearchFilterExpression(compareExpr.leftArg), operator = compareExpr.operator, rightArg = convertFilterExpressionToExtendedSearchFilterExpression(compareExpr.rightArg))
                case andExpr: AndExpression => ExtendedSearchAndExpression(leftArg = convertFilterExpressionToExtendedSearchFilterExpression(andExpr.leftArg), rightArg = convertFilterExpressionToExtendedSearchFilterExpression(andExpr.rightArg))
                case orExpr: OrExpression => ExtendedSearchOrExpression(leftArg = convertFilterExpressionToExtendedSearchFilterExpression(orExpr.leftArg), rightArg = convertFilterExpressionToExtendedSearchFilterExpression(orExpr.rightArg))
            }
        }

        /**
          * Converts an [[Entity]] to an [[ExtendedSearchEntity]].
          *
          * @param entity an entity provided by [[SearchParserV2]].
          * @return a [[ExtendedSearchEntity]].
          */
        def convertSearchParserEntityToExtendedSearchEntity(entity: Entity): ExtendedSearchEntity = {
            // convert external Iris to internal Iris if needed

            entity match {
                case iriRef: IriRef => // if an Iri is an external knora-api entity (with value object or simple), convert it to an internal Iri
                    if (InputValidation.isKnoraApiEntityIri(iriRef.iri)) {
                        ExtendedSearchInternalEntityIri(InputValidation.externalIriToInternalIri(iriRef.iri, () => throw BadRequestException(s"${iriRef.iri} is not a valid external knora-api entity Iri")))
                    } else {
                        ExtendedSearchIri(InputValidation.toIri(iriRef.iri, () => throw BadRequestException(s"$iriRef is not a valid IRI")))
                    }
                case queryVar: QueryVariable => ExtendedSearchVar(queryVar.variableName)
                case literal: XsdLiteral => ExtendedSearchXsdLiteral(value = literal.value, datatype = literal.datatype)
            }
        }

        /**
          * Converts a [[StatementPattern]] provided by [[SearchParserV2]] to an [[ExtendedSearchStatementPattern]].
          *
          * @param statementPattern a statement provided by SearchParserV2.
          * @param disableInference indicates if inference should be disabled for this statement.
          * @return a [[ExtendedSearchStatementPattern]].
          */
        def convertSearchParserStatementPatternToExtendedSearchStatement(statementPattern: StatementPattern, disableInference: Boolean): ExtendedSearchStatementPattern = {

            val subj = convertSearchParserEntityToExtendedSearchEntity(statementPattern.subj)
            val pred = convertSearchParserEntityToExtendedSearchEntity(statementPattern.pred)
            val obj = convertSearchParserEntityToExtendedSearchEntity(statementPattern.obj)

            ExtendedSearchStatementPattern(
                subj = subj,
                pred = pred,
                obj = obj,
                disableInference = disableInference || (pred match { // disable inference if `disableInference` is set to true or if the statement's predicate is a variable.
                    case variable: ExtendedSearchVar => true // disable inference to get the actual IRI for the predicate and not an inferred information
                    // TODO: this has the effect that subproperties are not found by the query!
                    // TODO: I think this may be omitted since we can get the actual property from the reification (ConstructResponseUtilV2 does not look at subproperties of hasLinkTo, this property is needed to get information about the resource referred to)
                    case _ => false
                })
            )
        }

        /**
          *
          * Converts a [[QueryPattern]] provided by [[SearchParserV2]] to an [[ExtendedSearchQueryPattern]].
          *
          * @param patterns a query pattern provided by SearchParserV2.
          * @return a [[ExtendedSearchQueryPattern]].
          */
        def convertSearchParserQueryPatternsToExtendedSearchPatterns(patterns: Seq[QueryPattern]): Seq[ExtendedSearchQueryPattern] = {
            // convert the statement patterns to a sequence of `ExtendedSearchStatementPattern`.
            patterns.map {
                (queryP: QueryPattern) =>
                    queryP match {
                        case statementPattern: StatementPattern =>
                            convertSearchParserStatementPatternToExtendedSearchStatement(statementPattern = statementPattern, disableInference = false) // use inference (will be disabled only for statements whose predicate is a variable).

                        case optionalPattern: OptionalPattern => ExtendedSearchOptionalPattern(convertSearchParserQueryPatternsToExtendedSearchPatterns(optionalPattern.patterns))

                        case unionPattern: UnionPattern =>
                            val blocksWithoutAnnotations = unionPattern.blocks.map {
                                patterns: Seq[QueryPattern] => convertSearchParserQueryPatternsToExtendedSearchPatterns(patterns)
                            }

                            ExtendedSearchUnionPattern(blocksWithoutAnnotations)

                        case filterPattern: FilterPattern => ExtendedSearchFilterPattern(convertFilterExpressionToExtendedSearchFilterExpression(filterPattern.expression))
                    }
            }
        }

        /**
          * Converts a sequence of [[StatementPattern]] representing the Construct clause of a query to a sequence of [[ExtendedSearchStatementPattern]].
          *
          * @param patterns a sequence of statement patterns provided by [[SearchParserV2]].
          * @return a sequence of [[ExtendedSearchStatementPattern]].
          */
        def convertSearchParserConstructPatternsToExtendedSearchPatterns(patterns: Seq[StatementPattern]): Seq[ExtendedSearchStatementPattern] = {
            patterns.map(statementPattern => convertSearchParserStatementPatternToExtendedSearchStatement(statementPattern = statementPattern, disableInference = true))
        }

        val typeInspector = new ExplicitTypeInspectorV2(apiSchema)
        val whereClauseWithoutAnnotations: WhereClause = typeInspector.removeTypeAnnotations(simpleConstructQuery.whereClause)
        val typeInspectionResultWhere: TypeInspectionResult = typeInspector.inspectTypes(simpleConstructQuery.whereClause)

        val extendedSearchConstructClauseStatementPatterns: Seq[ExtendedSearchStatementPattern] = convertSearchParserConstructPatternsToExtendedSearchPatterns(simpleConstructQuery.constructClause.statements)
        val extendedSearchWhereClausePatternsWithOriginalFilters: Seq[ExtendedSearchQueryPattern] = convertSearchParserQueryPatternsToExtendedSearchPatterns(whereClauseWithoutAnnotations.patterns)

        /**
          * Creates additional statements based on a non property type Iri.
          *
          * @param typeIriExternal       the non property type Iri.
          * @param subject               the entity that is the subject in the additional statement to be generated.
          * @param typeInfoKeysProcessed a Set of keys that indicates which type info entries already habe been processed.
          * @param index                 the index to be used to create variables in Sparql.
          * @return a sequence of [[ExtendedSearchStatementPattern]].
          */
        def createAdditionalStatementsForNonPropertyType(typeIriExternal: IRI, subject: ExtendedSearchEntity, typeInfoKeysProcessed: Set[TypeableEntity], index: Int): AdditionalStatements = {
            val typeIriInternal = InputValidation.externalIriToInternalIri(typeIriExternal, () => throw BadRequestException(s"${typeIriExternal} is not a valid external knora-api entity Iri"))

            if (typeIriInternal == OntologyConstants.KnoraBase.Resource) {

                // create additional statements in order to query permissions and other information for a resource

                AdditionalStatements(additionalStatements = Vector(
                    ExtendedSearchStatementPattern(subj = subject, pred = ExtendedSearchIri(OntologyConstants.Rdf.Type), obj = ExtendedSearchIri(OntologyConstants.KnoraBase.Resource), false),
                    ExtendedSearchStatementPattern(subj = subject, pred = ExtendedSearchIri(OntologyConstants.KnoraBase.IsDeleted), obj = ExtendedSearchXsdLiteral(value = "false", datatype = OntologyConstants.Xsd.Boolean), true),
                    ExtendedSearchStatementPattern(subj = subject, pred = ExtendedSearchIri(OntologyConstants.Rdfs.Label), obj = ExtendedSearchVar("resourceLabel" + index), true),
                    ExtendedSearchStatementPattern(subj = subject, pred = ExtendedSearchIri(OntologyConstants.Rdf.Type), obj = ExtendedSearchVar("resourceType" + index), true),
                    ExtendedSearchStatementPattern(subj = subject, pred = ExtendedSearchInternalEntityIri(OntologyConstants.KnoraBase.AttachedToUser), obj = ExtendedSearchVar("resourceCreator" + index), true),
                    ExtendedSearchStatementPattern(subj = subject, pred = ExtendedSearchInternalEntityIri(OntologyConstants.KnoraBase.HasPermissions), obj = ExtendedSearchVar("resourcePermissions" + index), true),
                    ExtendedSearchStatementPattern(subj = subject, pred = ExtendedSearchInternalEntityIri(OntologyConstants.KnoraBase.AttachedToProject), obj = ExtendedSearchVar("resourceProject" + index), true)
                ), typeInfoKeysProcessed = typeInfoKeysProcessed)
            } else {
                throw SparqlSearchException(s"non property type is expected to be of type ${OntologyConstants.KnoraBase.Resource}, but $typeIriInternal is given")
            }
        }

        /**
          * Creates additional statements based on a property type Iri.
          * The predicate of the given statement pattern is a property (value property or linking property).
          *
          * @param typeIriExternal       the property type Iri as an external Knora Iri (the type of the thing the property/predicate points to).
          * @param statementPattern      the statement to be processed (its predicate is the property whose type is given above).
          * @param typeInfoKeysProcessed a Set of keys that indicates which type info entries already have been processed (to be returned to the calling context to avoid multiple processing of the same type information).
          * @param index                 the index to be used to create unique variable names in Sparql.
          * @return a sequence of [[ExtendedSearchStatementPattern]].
          */
        def createAdditionalStatementsForPropertyType(typeIriExternal: IRI, statementPattern: ExtendedSearchStatementPattern, typeInfoKeysProcessed: Set[TypeableEntity], index: Int): AdditionalStatements = {

            // convert the type information into an internal Knora Iri if possible
            val objectIri = if (InputValidation.isKnoraApiEntityIri(typeIriExternal)) {
                InputValidation.externalIriToInternalIri(typeIriExternal, () => throw BadRequestException(s"${typeIriExternal} is not a valid external knora-api entity Iri"))
            } else {
                typeIriExternal
            }

            objectIri match {
                case OntologyConstants.KnoraBase.Resource =>

                    // the given statement pattern's object is of type resource
                    // this means that the predicate of the statement pattern is a linking property
                    // create statements in order to query the link value describing the link in question

                    // variable referring to the link's value object (reification)
                    val linkValueObjVar = ExtendedSearchVar("linkValueObj" + index)

                    AdditionalStatements(additionalStatements = Vector(
                        ExtendedSearchStatementPattern(subj = statementPattern.subj, pred = ExtendedSearchInternalEntityIri(OntologyConstants.KnoraBase.HasValue), obj = linkValueObjVar, false), // use inference since this is a generic property
                        ExtendedSearchStatementPattern(subj = statementPattern.subj, pred = ExtendedSearchVar("linkValueProp" + index), obj = linkValueObjVar, true),
                        ExtendedSearchStatementPattern(subj = linkValueObjVar, pred = ExtendedSearchIri(OntologyConstants.KnoraBase.IsDeleted), obj = ExtendedSearchXsdLiteral(value = "false", datatype = OntologyConstants.Xsd.Boolean), true),
                        ExtendedSearchStatementPattern(subj = linkValueObjVar, pred = ExtendedSearchInternalEntityIri(OntologyConstants.Rdf.Type), obj = ExtendedSearchInternalEntityIri(OntologyConstants.KnoraBase.LinkValue), true),
                        ExtendedSearchStatementPattern(subj = linkValueObjVar, pred = ExtendedSearchIri(OntologyConstants.Rdf.Subject), obj = statementPattern.subj, true),
                        ExtendedSearchStatementPattern(subj = linkValueObjVar, pred = ExtendedSearchInternalEntityIri(OntologyConstants.Rdf.Object), obj = statementPattern.obj, true),
                        ExtendedSearchStatementPattern(subj = linkValueObjVar, pred = ExtendedSearchVar("linkValueObjProp" + index), obj = ExtendedSearchVar("linkValueObjVal" + index), true)
                    ), typeInfoKeysProcessed = typeInfoKeysProcessed)
                case OntologyConstants.Xsd.String =>

                    if (apiSchema == ApiV2Schema.SIMPLE) {
                        // the direct statement from the resource to a string literal (simplified) has to be translated to a value object (extra level).
                        // please note that the original direct statement will be filtered out and not end up in the Sparql submitted to the triplestore.

                        statementPattern.obj match {

                            case stringLiteral: ExtendedSearchXsdLiteral =>
                                // the statement's object is a literal

                                // TODO: assure that this is a string literal (valueHasString expects a string literal)

                                // insert an extra level so that the resource points to the literal via a value object

                                // variable referring to the string value object
                                val stringValueObjVar = ExtendedSearchVar("stringValueObj" + index)

                                AdditionalStatements(additionalStatements = Vector(
                                    ExtendedSearchStatementPattern(subj = statementPattern.subj, pred = ExtendedSearchInternalEntityIri(OntologyConstants.KnoraBase.HasValue), obj = stringValueObjVar, false), // use inference since this is a generic property
                                    ExtendedSearchStatementPattern(subj = statementPattern.subj, pred = statementPattern.pred, obj = stringValueObjVar, false), // use inference in order to get all subproperties as well
                                    ExtendedSearchStatementPattern(subj = stringValueObjVar, pred = ExtendedSearchIri(OntologyConstants.KnoraBase.IsDeleted), obj = ExtendedSearchXsdLiteral(value = "false", datatype = OntologyConstants.Xsd.Boolean), true),
                                    ExtendedSearchStatementPattern(subj = stringValueObjVar, pred = ExtendedSearchInternalEntityIri(OntologyConstants.Rdf.Type), obj = ExtendedSearchInternalEntityIri(OntologyConstants.KnoraBase.TextValue), true),
                                    ExtendedSearchStatementPattern(subj = stringValueObjVar, pred = ExtendedSearchVar("stringValueObjProp" + index), obj = ExtendedSearchVar("stringValueObjVal" + index), true),
                                    ExtendedSearchStatementPattern(subj = stringValueObjVar, pred = ExtendedSearchIri(OntologyConstants.KnoraBase.ValueHasString), obj = statementPattern.obj, true) // check that valueHasString equals the given string literal
                                ), typeInfoKeysProcessed = typeInfoKeysProcessed)

                            case stringVar: ExtendedSearchVar =>
                                // the statement's object is a variable

                                // since all value property statements are eliminated, recreate the statement using the given predicate

                                // TODO: maybe only those value property statements have to be eliminated whose object is a literal

                                AdditionalStatements(additionalStatements = Vector(
                                    ExtendedSearchStatementPattern(subj = statementPattern.subj, pred = ExtendedSearchInternalEntityIri(OntologyConstants.KnoraBase.HasValue), obj = stringVar, false), // use inference since this is a generic property
                                    ExtendedSearchStatementPattern(subj = statementPattern.subj, pred = statementPattern.pred, obj = stringVar, false), // use inference in order to get all subproperties as well
                                    ExtendedSearchStatementPattern(subj = stringVar, pred = ExtendedSearchIri(OntologyConstants.KnoraBase.IsDeleted), obj = ExtendedSearchXsdLiteral(value = "false", datatype = OntologyConstants.Xsd.Boolean), true),
                                    ExtendedSearchStatementPattern(subj = stringVar, pred = ExtendedSearchInternalEntityIri(OntologyConstants.Rdf.Type), obj = ExtendedSearchInternalEntityIri(OntologyConstants.KnoraBase.TextValue), true),
                                    ExtendedSearchStatementPattern(subj = stringVar, pred = ExtendedSearchVar("stringValueObjProp" + index), obj = ExtendedSearchVar("stringValueObjVal" + index), true)
                                ), typeInfoKeysProcessed = typeInfoKeysProcessed)

                            case _ => throw SparqlSearchException(s"object ${statementPattern.obj} in statement $statementPattern of type xsd:string must be either a literal or a variable")

                        }

                    } else {
                        throw NotImplementedException(s"Extended search not implemented for schema $apiSchema")
                    }


                case OntologyConstants.Xsd.Boolean =>
                    if (apiSchema == ApiV2Schema.SIMPLE) {
                        // do not include the given statement pattern in the answer because a direct statement from the resource to a string literal (simplified) has to be translated to a value object (extra level).

                        statementPattern.obj match {

                            case booleanLiteral: ExtendedSearchXsdLiteral =>

                                // variable referring to the integer value object
                                val booleanValueObjVar = ExtendedSearchVar("booleanValueObj" + index)

                                AdditionalStatements(additionalStatements = Vector(
                                    ExtendedSearchStatementPattern(subj = statementPattern.subj, pred = ExtendedSearchInternalEntityIri(OntologyConstants.KnoraBase.HasValue), obj = booleanValueObjVar, false),
                                    ExtendedSearchStatementPattern(subj = statementPattern.subj, pred = statementPattern.pred, obj = booleanValueObjVar, false),
                                    ExtendedSearchStatementPattern(subj = booleanValueObjVar, pred = ExtendedSearchIri(OntologyConstants.KnoraBase.IsDeleted), obj = ExtendedSearchXsdLiteral(value = "false", datatype = OntologyConstants.Xsd.Boolean), true),
                                    ExtendedSearchStatementPattern(subj = booleanValueObjVar, pred = ExtendedSearchInternalEntityIri(OntologyConstants.Rdf.Type), obj = ExtendedSearchInternalEntityIri(OntologyConstants.KnoraBase.BooleanValue), true),
                                    ExtendedSearchStatementPattern(subj = booleanValueObjVar, pred = ExtendedSearchVar("booleanValueObjProp" + index), obj = ExtendedSearchVar("booleanValueObjVal" + index), true),
                                    ExtendedSearchStatementPattern(subj = booleanValueObjVar, pred = ExtendedSearchIri(OntologyConstants.KnoraBase.ValueHasBoolean), obj = statementPattern.obj, true)
                                ), typeInfoKeysProcessed = typeInfoKeysProcessed)

                            case decimalVar: ExtendedSearchVar =>

                                // the statement's object is a variable

                                // since all value property statements are eliminated, recreate the statement using the given predicate

                                // TODO: maybe only those value property statements have to be eliminated whose object is a literal

                                AdditionalStatements(additionalStatements = Vector(
                                    ExtendedSearchStatementPattern(subj = statementPattern.subj, pred = ExtendedSearchInternalEntityIri(OntologyConstants.KnoraBase.HasValue), obj = decimalVar, false),
                                    ExtendedSearchStatementPattern(subj = statementPattern.subj, pred = statementPattern.pred, obj = decimalVar, false),
                                    ExtendedSearchStatementPattern(subj = decimalVar, pred = ExtendedSearchIri(OntologyConstants.KnoraBase.IsDeleted), obj = ExtendedSearchXsdLiteral(value = "false", datatype = OntologyConstants.Xsd.Boolean), true),
                                    ExtendedSearchStatementPattern(subj = decimalVar, pred = ExtendedSearchInternalEntityIri(OntologyConstants.Rdf.Type), obj = ExtendedSearchInternalEntityIri(OntologyConstants.KnoraBase.BooleanValue), true),
                                    ExtendedSearchStatementPattern(subj = decimalVar, pred = ExtendedSearchVar("booleanValueObjProp" + index), obj = ExtendedSearchVar("booleanValueObjVal" + index), true)
                                ), typeInfoKeysProcessed = typeInfoKeysProcessed)

                            case _ => AdditionalStatements()

                        }

                    } else {
                        throw NotImplementedException(s"Extended search not implemented for schema $apiSchema")
                    }

                case OntologyConstants.Xsd.Integer =>
                    if (apiSchema == ApiV2Schema.SIMPLE) {
                        // do not include the given statement pattern in the answer because a direct statement from the resource to a string literal (simplified) has to be translated to a value object (extra level).

                        statementPattern.obj match {

                            case integerLiteral: ExtendedSearchXsdLiteral =>

                                // variable referring to the integer value object
                                val integerValueObjVar = ExtendedSearchVar("integerValueObj" + index)

                                AdditionalStatements(additionalStatements = Vector(
                                    ExtendedSearchStatementPattern(subj = statementPattern.subj, pred = ExtendedSearchInternalEntityIri(OntologyConstants.KnoraBase.HasValue), obj = integerValueObjVar, false),
                                    ExtendedSearchStatementPattern(subj = statementPattern.subj, pred = statementPattern.pred, obj = integerValueObjVar, false),
                                    ExtendedSearchStatementPattern(subj = integerValueObjVar, pred = ExtendedSearchIri(OntologyConstants.KnoraBase.IsDeleted), obj = ExtendedSearchXsdLiteral(value = "false", datatype = OntologyConstants.Xsd.Boolean), true),
                                    ExtendedSearchStatementPattern(subj = integerValueObjVar, pred = ExtendedSearchInternalEntityIri(OntologyConstants.Rdf.Type), obj = ExtendedSearchInternalEntityIri(OntologyConstants.KnoraBase.IntValue), true),
                                    ExtendedSearchStatementPattern(subj = integerValueObjVar, pred = ExtendedSearchVar("integerValueObjProp" + index), obj = ExtendedSearchVar("integerValueObjVal" + index), true),
                                    ExtendedSearchStatementPattern(subj = integerValueObjVar, pred = ExtendedSearchIri(OntologyConstants.KnoraBase.ValueHasInteger), obj = statementPattern.obj, true)
                                ), typeInfoKeysProcessed = typeInfoKeysProcessed)

                            case integerVar: ExtendedSearchVar =>

                                // the statement's object is a variable

                                // since all value property statements are eliminated, recreate the statement using the given predicate

                                // TODO: maybe only those value property statements have to be eliminated whose object is a literal

                                AdditionalStatements(additionalStatements = Vector(
                                    ExtendedSearchStatementPattern(subj = statementPattern.subj, pred = ExtendedSearchInternalEntityIri(OntologyConstants.KnoraBase.HasValue), obj = integerVar, false),
                                    ExtendedSearchStatementPattern(subj = statementPattern.subj, pred = statementPattern.pred, obj = integerVar, false),
                                    ExtendedSearchStatementPattern(subj = integerVar, pred = ExtendedSearchIri(OntologyConstants.KnoraBase.IsDeleted), obj = ExtendedSearchXsdLiteral(value = "false", datatype = OntologyConstants.Xsd.Boolean), true),
                                    ExtendedSearchStatementPattern(subj = integerVar, pred = ExtendedSearchInternalEntityIri(OntologyConstants.Rdf.Type), obj = ExtendedSearchInternalEntityIri(OntologyConstants.KnoraBase.IntValue), true),
                                    ExtendedSearchStatementPattern(subj = integerVar, pred = ExtendedSearchVar("integerValueObjProp" + index), obj = ExtendedSearchVar("integerValueObjVal" + index), true)
                                ), typeInfoKeysProcessed = typeInfoKeysProcessed)

                            case _ => AdditionalStatements()

                        }

                    } else {
                        throw NotImplementedException(s"Extended search not implemented for schema $apiSchema")
                    }

                case OntologyConstants.Xsd.Decimal =>
                    if (apiSchema == ApiV2Schema.SIMPLE) {
                        // do not include the given statement pattern in the answer because a direct statement from the resource to a string literal (simplified) has to be translated to a value object (extra level).

                        statementPattern.obj match {

                            case decimalLiteral: ExtendedSearchXsdLiteral =>

                                // variable referring to the integer value object
                                val decimalValueObjVar = ExtendedSearchVar("decimalValueObj" + index)

                                AdditionalStatements(additionalStatements = Vector(
                                    ExtendedSearchStatementPattern(subj = statementPattern.subj, pred = ExtendedSearchInternalEntityIri(OntologyConstants.KnoraBase.HasValue), obj = decimalValueObjVar, false),
                                    ExtendedSearchStatementPattern(subj = statementPattern.subj, pred = statementPattern.pred, obj = decimalValueObjVar, false),
                                    ExtendedSearchStatementPattern(subj = decimalValueObjVar, pred = ExtendedSearchIri(OntologyConstants.KnoraBase.IsDeleted), obj = ExtendedSearchXsdLiteral(value = "false", datatype = OntologyConstants.Xsd.Boolean), true),
                                    ExtendedSearchStatementPattern(subj = decimalValueObjVar, pred = ExtendedSearchInternalEntityIri(OntologyConstants.Rdf.Type), obj = ExtendedSearchInternalEntityIri(OntologyConstants.KnoraBase.DecimalValue), true),
                                    ExtendedSearchStatementPattern(subj = decimalValueObjVar, pred = ExtendedSearchVar("decimalValueObjProp" + index), obj = ExtendedSearchVar("decimalValueObjVal" + index), true),
                                    ExtendedSearchStatementPattern(subj = decimalValueObjVar, pred = ExtendedSearchIri(OntologyConstants.KnoraBase.ValueHasDecimal), obj = statementPattern.obj, true)
                                ), typeInfoKeysProcessed = typeInfoKeysProcessed)

                            case decimalVar: ExtendedSearchVar =>

                                // the statement's object is a variable

                                // since all value property statements are eliminated, recreate the statement using the given predicate

                                // TODO: maybe only those value property statements have to be eliminated whose object is a literal

                                AdditionalStatements(additionalStatements = Vector(
                                    ExtendedSearchStatementPattern(subj = statementPattern.subj, pred = ExtendedSearchInternalEntityIri(OntologyConstants.KnoraBase.HasValue), obj = decimalVar, false),
                                    ExtendedSearchStatementPattern(subj = statementPattern.subj, pred = statementPattern.pred, obj = decimalVar, false),
                                    ExtendedSearchStatementPattern(subj = decimalVar, pred = ExtendedSearchIri(OntologyConstants.KnoraBase.IsDeleted), obj = ExtendedSearchXsdLiteral(value = "false", datatype = OntologyConstants.Xsd.Boolean), true),
                                    ExtendedSearchStatementPattern(subj = decimalVar, pred = ExtendedSearchInternalEntityIri(OntologyConstants.Rdf.Type), obj = ExtendedSearchInternalEntityIri(OntologyConstants.KnoraBase.DecimalValue), true),
                                    ExtendedSearchStatementPattern(subj = decimalVar, pred = ExtendedSearchVar("decimalValueObjProp" + index), obj = ExtendedSearchVar("decimalValueObjVal" + index), true)
                                ), typeInfoKeysProcessed = typeInfoKeysProcessed)

                            case _ => AdditionalStatements()

                        }

                    } else {
                        throw NotImplementedException(s"Extended search not implemented for schema $apiSchema")
                    }

                case OntologyConstants.KnoraBase.Date =>
                    if (apiSchema == ApiV2Schema.SIMPLE) {
                        // do not include the given statement pattern in the answer because a direct statement from the resource to a string literal (simplified) has to be translated to a value object (extra level).

                        statementPattern.obj match {

                            case dateLiteral: ExtendedSearchXsdLiteral =>

                                val dateStr = InputValidation.toDate(dateLiteral.value, () => throw BadRequestException(s"${dateLiteral.value} is not a valid date string"))

                                val date: JulianDayNumberValueV1 = DateUtilV1.createJDNValueV1FromDateString(dateStr)

                                val filterPattern = ExtendedSearchFilterPattern(ExtendedSearchAndExpression(ExtendedSearchCompareExpression(ExtendedSearchXsdLiteral(date.dateval1.toString, OntologyConstants.Xsd.Integer), "<=", ExtendedSearchVar("dateValEnd" + index)), ExtendedSearchCompareExpression(ExtendedSearchXsdLiteral(date.dateval2.toString, OntologyConstants.Xsd.Integer), ">=", ExtendedSearchVar("dateValStart" + index))))

                                // variable referring to the date value object
                                val dateValueObject = ExtendedSearchVar("dateValueObj" + index)

                                AdditionalStatements(additionalStatements = Vector(
                                    ExtendedSearchStatementPattern(subj = statementPattern.subj, pred = ExtendedSearchInternalEntityIri(OntologyConstants.KnoraBase.HasValue), obj = dateValueObject, false),
                                    ExtendedSearchStatementPattern(subj = statementPattern.subj, pred = statementPattern.pred, obj = dateValueObject, false),
                                    ExtendedSearchStatementPattern(subj = dateValueObject, pred = ExtendedSearchIri(OntologyConstants.KnoraBase.IsDeleted), obj = ExtendedSearchXsdLiteral(value = "false", datatype = OntologyConstants.Xsd.Boolean), true),
                                    ExtendedSearchStatementPattern(subj = dateValueObject, pred = ExtendedSearchInternalEntityIri(OntologyConstants.Rdf.Type), obj = ExtendedSearchInternalEntityIri(OntologyConstants.KnoraBase.DateValue), true),
                                    ExtendedSearchStatementPattern(subj = dateValueObject, pred = ExtendedSearchVar("dateValueObjProp" + index), obj = ExtendedSearchVar("dateValueObjVal" + index), true),
                                    ExtendedSearchStatementPattern(subj = dateValueObject, pred = ExtendedSearchInternalEntityIri(OntologyConstants.KnoraBase.ValueHasStartJDN), obj = ExtendedSearchVar("dateValStart" + index), true),
                                    ExtendedSearchStatementPattern(subj = dateValueObject, pred = ExtendedSearchInternalEntityIri(OntologyConstants.KnoraBase.ValueHasEndJDN), obj = ExtendedSearchVar("dateValEnd" + index), true)
                                ), additionalFilterPatterns = Vector(filterPattern), typeInfoKeysProcessed = typeInfoKeysProcessed)

                            case dateVar: ExtendedSearchVar =>

                                // the statement's object is a variable

                                // since all value property statements are eliminated, recreate the statement using the given predicate

                                // TODO: maybe only those value property statements have to be eliminated whose object is a literal

                                AdditionalStatements(additionalStatements = Vector(
                                    ExtendedSearchStatementPattern(subj = statementPattern.subj, pred = ExtendedSearchInternalEntityIri(OntologyConstants.KnoraBase.HasValue), obj = dateVar, false),
                                    ExtendedSearchStatementPattern(subj = statementPattern.subj, pred = statementPattern.pred, obj = dateVar, false),
                                    ExtendedSearchStatementPattern(subj = dateVar, pred = ExtendedSearchIri(OntologyConstants.KnoraBase.IsDeleted), obj = ExtendedSearchXsdLiteral(value = "false", datatype = OntologyConstants.Xsd.Boolean), true),
                                    ExtendedSearchStatementPattern(subj = dateVar, pred = ExtendedSearchInternalEntityIri(OntologyConstants.Rdf.Type), obj = ExtendedSearchInternalEntityIri(OntologyConstants.KnoraBase.DateValue), true),
                                    ExtendedSearchStatementPattern(subj = dateVar, pred = ExtendedSearchVar("dateValueObjProp" + index), obj = ExtendedSearchVar("dateValueObjVal" + index), true)
                                ), typeInfoKeysProcessed = typeInfoKeysProcessed)

                            case _ => AdditionalStatements()

                        }

                    } else {
                        throw NotImplementedException(s"Extended search not implemented for schema $apiSchema")
                    }

                case OntologyConstants.KnoraBase.StillImageFile =>
                    if (apiSchema == ApiV2Schema.SIMPLE) {

                        statementPattern.obj match {

                            case fileVar: ExtendedSearchVar =>

                                // the statement's object is a variable

                                // since all value property statements are eliminated, recreate the statement using the given predicate

                                // TODO: maybe only those value property statements have to be eliminated whose object is a literal

                                AdditionalStatements(additionalStatements = Vector(
                                    ExtendedSearchStatementPattern(subj = statementPattern.subj, pred = ExtendedSearchInternalEntityIri(OntologyConstants.KnoraBase.HasValue), obj = fileVar, false),
                                    ExtendedSearchStatementPattern(subj = statementPattern.subj, pred = statementPattern.pred, obj = fileVar, false),
                                    ExtendedSearchStatementPattern(subj = fileVar, pred = ExtendedSearchIri(OntologyConstants.KnoraBase.IsDeleted), obj = ExtendedSearchXsdLiteral(value = "false", datatype = OntologyConstants.Xsd.Boolean), true),
                                    ExtendedSearchStatementPattern(subj = fileVar, pred = ExtendedSearchInternalEntityIri(OntologyConstants.Rdf.Type), obj = ExtendedSearchInternalEntityIri(OntologyConstants.KnoraBase.StillImageFileValue), true),
                                    ExtendedSearchStatementPattern(subj = fileVar, pred = ExtendedSearchVar("fileValueObjProp" + index), obj = ExtendedSearchVar("fileValueObjVal" + index), true)
                                ), typeInfoKeysProcessed = typeInfoKeysProcessed)

                            case _ => throw BadRequestException("Could not handle file value. Literals are not supported.")

                        }

                    } else {
                        throw NotImplementedException(s"Extended search not implemented for schema $apiSchema")
                    }

                case OntologyConstants.KnoraBase.Geom =>
                    if (apiSchema == ApiV2Schema.SIMPLE) {
                        // do not include the given statement pattern in the answer because a direct statement from the resource to a string literal (simplified) has to be translated to a value object (extra level).

                        statementPattern.obj match {

                            case fileVar: ExtendedSearchVar =>
                                AdditionalStatements(additionalStatements = Vector(
                                    ExtendedSearchStatementPattern(subj = statementPattern.subj, pred = ExtendedSearchInternalEntityIri(OntologyConstants.KnoraBase.HasValue), obj = fileVar, false),
                                    ExtendedSearchStatementPattern(subj = statementPattern.subj, pred = statementPattern.pred, obj = fileVar, false),
                                    ExtendedSearchStatementPattern(subj = fileVar, pred = ExtendedSearchIri(OntologyConstants.KnoraBase.IsDeleted), obj = ExtendedSearchXsdLiteral(value = "false", datatype = OntologyConstants.Xsd.Boolean), true),
                                    ExtendedSearchStatementPattern(subj = fileVar, pred = ExtendedSearchInternalEntityIri(OntologyConstants.Rdf.Type), obj = ExtendedSearchInternalEntityIri(OntologyConstants.KnoraBase.GeomValue), true),
                                    ExtendedSearchStatementPattern(subj = fileVar, pred = ExtendedSearchVar("geomValueObjProp" + index), obj = ExtendedSearchVar("geomValueObjVal" + index), true)
                                ), typeInfoKeysProcessed = typeInfoKeysProcessed)

                            case _ => throw BadRequestException("Could not handle geom value. Literals are not supported.")

                        }

                    } else {
                        throw NotImplementedException(s"Extended search not implemented for schema $apiSchema")
                    }

                case OntologyConstants.KnoraBase.Color =>
                    if (apiSchema == ApiV2Schema.SIMPLE) {
                        // do not include the given statement pattern in the answer because a direct statement from the resource to a string literal (simplified) has to be translated to a value object (extra level).

                        statementPattern.obj match {

                            case colorLiteral: ExtendedSearchXsdLiteral =>

                                // variable referring to the color value object
                                val colorValueObject = ExtendedSearchVar("colorValueObj" + index)

                                AdditionalStatements(additionalStatements = Vector(
                                    ExtendedSearchStatementPattern(subj = statementPattern.subj, pred = ExtendedSearchInternalEntityIri(OntologyConstants.KnoraBase.HasValue), obj = colorValueObject, false),
                                    ExtendedSearchStatementPattern(subj = statementPattern.subj, pred = statementPattern.pred, obj = colorValueObject, false),
                                    ExtendedSearchStatementPattern(subj = colorValueObject, pred = ExtendedSearchIri(OntologyConstants.KnoraBase.IsDeleted), obj = ExtendedSearchXsdLiteral(value = "false", datatype = OntologyConstants.Xsd.Boolean), true),
                                    ExtendedSearchStatementPattern(subj = colorValueObject, pred = ExtendedSearchInternalEntityIri(OntologyConstants.Rdf.Type), obj = ExtendedSearchInternalEntityIri(OntologyConstants.KnoraBase.ColorValue), true),
                                    ExtendedSearchStatementPattern(subj = colorValueObject, pred = ExtendedSearchVar("colorValueObjProp" + index), obj = ExtendedSearchVar("colorValueObjVal" + index), true),
                                    ExtendedSearchStatementPattern(subj = colorValueObject, pred = ExtendedSearchIri(OntologyConstants.KnoraBase.ValueHasColor), obj = statementPattern.obj, true)
                                ), typeInfoKeysProcessed = typeInfoKeysProcessed)

                            case colorVar: ExtendedSearchVar =>

                                // the statement's object is a variable

                                // since all value property statements are eliminated, recreate the statement using the given predicate

                                // TODO: maybe only those value property statements have to be eliminated whose object is a literal

                                AdditionalStatements(additionalStatements = Vector(
                                    ExtendedSearchStatementPattern(subj = statementPattern.subj, pred = ExtendedSearchInternalEntityIri(OntologyConstants.KnoraBase.HasValue), obj = colorVar, false),
                                    ExtendedSearchStatementPattern(subj = statementPattern.subj, pred = statementPattern.pred, obj = colorVar, false),
                                    ExtendedSearchStatementPattern(subj = colorVar, pred = ExtendedSearchIri(OntologyConstants.KnoraBase.IsDeleted), obj = ExtendedSearchXsdLiteral(value = "false", datatype = OntologyConstants.Xsd.Boolean), true),
                                    ExtendedSearchStatementPattern(subj = colorVar, pred = ExtendedSearchInternalEntityIri(OntologyConstants.Rdf.Type), obj = ExtendedSearchInternalEntityIri(OntologyConstants.KnoraBase.ColorValue), true),
                                    ExtendedSearchStatementPattern(subj = colorVar, pred = ExtendedSearchVar("colorValueObjProp" + index), obj = ExtendedSearchVar("colorValueObjVal" + index), true)
                                ), typeInfoKeysProcessed = typeInfoKeysProcessed)

                            case _ => throw BadRequestException("Could not handle color value")

                        }

                    } else {
                        throw NotImplementedException(s"Extended search not implemented for schema $apiSchema")
                    }


                case other =>
                    throw NotImplementedException(s"type $other not implemented")
            }

        }

        /**
          * Represents the originally given statement in the query provided by the user and statements that were additionally cretaed based on the given type annotations.
          *
          * @param additionalStatements  statements created based on the given type annotations.
          * @param typeInfoKeysProcessed a Set of keys that indicates which type info entries already habe been processed.
          */
        case class AdditionalStatements(additionalStatements: Vector[ExtendedSearchStatementPattern] = Vector.empty[ExtendedSearchStatementPattern], additionalFilterPatterns: Vector[ExtendedSearchFilterPattern] = Vector.empty[ExtendedSearchFilterPattern], typeInfoKeysProcessed: Set[TypeableEntity] = Set.empty[TypeableEntity])

        /**
          *
          * @param originalStatement    the statement originally given by the user. Since some statements have to be converted (e.g. direct statements from resources to literals in the simplified Api schema), original statements might not be returned.
          * @param additionalStatements statements created based on the given type annotations.
          */
        case class ConvertedStatement(originalStatement: Option[ExtendedSearchStatementPattern], additionalStatements: AdditionalStatements)

        /**
          * Based on the given type annotations, convert the given statement.
          *
          * @param statementP                        the given statement.
          * @param index                             the current index (used to create unique variable names).
          * @param typeInfoKeysProcessedInStatements a Set of keys that indicates which type info entries already habe been processed.
          * @return a sequence of [[AdditionalStatements]].
          */
        def addTypeInformationStatementsToStatement(statementP: ExtendedSearchStatementPattern, index: Int, typeInfoKeysProcessedInStatements: Set[TypeableEntity]): ConvertedStatement = {

            // check if subject is contained in the type info
            val additionalStatementsForSubj: AdditionalStatements = statementP.subj match {
                case variableSubj: ExtendedSearchVar =>
                    val key = TypeableVariable(variableSubj.variableName)

                    if (typeInspectionResultWhere.typedEntities -- typeInfoKeysProcessedInStatements contains key) {

                        val additionalStatements: AdditionalStatements = typeInspectionResultWhere.typedEntities(key) match {
                            case nonPropTypeInfo: NonPropertyTypeInfo =>
                                createAdditionalStatementsForNonPropertyType(nonPropTypeInfo.typeIri, statementP.subj, typeInfoKeysProcessedInStatements + key, index)
                            case propTypeInfo: PropertyTypeInfo =>
                                AdditionalStatements()
                        }

                        additionalStatements


                    } else {
                        AdditionalStatements()
                    }


                case iriSubj: ExtendedSearchInternalEntityIri =>
                    val key = TypeableIri(iriSubj.iri)

                    AdditionalStatements()

                case other =>
                    AdditionalStatements()
            }

            // check the predicate: must be either a variable or an Iri (cannot be a literal)
            // the predicate represents a property
            val additionalStatementsForPred: AdditionalStatements = statementP.pred match {
                case variablePred: ExtendedSearchVar =>
                    val key = TypeableVariable(variablePred.variableName)

                    // get type information about the predicate variable (if not already processed before)
                    if (typeInspectionResultWhere.typedEntities -- typeInfoKeysProcessedInStatements contains key) {

                        val additionalStatements: AdditionalStatements = typeInspectionResultWhere.typedEntities(key) match {
                            case nonPropTypeInfo: NonPropertyTypeInfo =>
                                throw SparqlSearchException(s"got non property type information for predicate variable $variablePred from type inspector v2")

                            case propTypeInfo: PropertyTypeInfo =>
                                // create additional statements based on the type of the predicate (property)
                                createAdditionalStatementsForPropertyType(propTypeInfo.objectTypeIri, statementP, typeInfoKeysProcessedInStatements + key, index)
                        }

                        additionalStatements

                    } else {
                        // type information has already been processed, no more action needed
                        AdditionalStatements()
                    }

                case iriPred: ExtendedSearchInternalEntityIri =>

                    val key = if (apiSchema == ApiV2Schema.SIMPLE) {
                        // convert this Iri to knora-api simple since the type inspector uses knora-api simple Iris
                        TypeableIri(InputValidation.internalEntityIriToSimpleApiV2EntityIri(iriPred.iri, () => throw AssertionException(s"${iriPred.iri} could not be converted back to knora-api simple format")))
                    } else {
                        throw NotImplementedException("The extended search for knora-api with value object has not been implemented yet")
                    }

                    // get type information about the predicate Iri (if not already processed before)
                    if (typeInspectionResultWhere.typedEntities -- typeInfoKeysProcessedInStatements contains key) {

                        val additionalStatements: AdditionalStatements = typeInspectionResultWhere.typedEntities(key) match {
                            case nonPropTypeInfo: NonPropertyTypeInfo =>
                                throw SparqlSearchException(s"got non property type information for predicate iri $iriPred from type inspector v2")

                            case propTypeInfo: PropertyTypeInfo =>
                                // create additional statements based on the type of the predicate (property)
                                createAdditionalStatementsForPropertyType(propTypeInfo.objectTypeIri, statementP, typeInfoKeysProcessedInStatements + key, index)
                        }

                        additionalStatements

                    } else {
                        // type information has already been processed, no more action needed
                        AdditionalStatements()
                    }

                case externalIri: ExtendedSearchIri =>
                    // no additional statements needed (no type information available)
                    // externalIri could be rdf:type for instance
                    AdditionalStatements()

                case other => throw SparqlSearchException(s"predicate (property) must either be a variable or an Iri, not $other")
            }

            val additionalStatementsForObj: AdditionalStatements = statementP.obj match {
                case variableObj: ExtendedSearchVar =>
                    AdditionalStatements()

                case internalEntityIriObj: ExtendedSearchInternalEntityIri =>
                    AdditionalStatements()

                case iriObj: ExtendedSearchIri =>
                    val key = TypeableIri(iriObj.iri)

                    if (typeInspectionResultWhere.typedEntities -- typeInfoKeysProcessedInStatements contains key) {

                        val additionalStatements: AdditionalStatements = typeInspectionResultWhere.typedEntities(key) match {
                            case nonPropTypeInfo: NonPropertyTypeInfo =>
                                createAdditionalStatementsForNonPropertyType(nonPropTypeInfo.typeIri, statementP.obj, typeInfoKeysProcessedInStatements + key, index)
                            case propTypeInfo: PropertyTypeInfo =>
                                AdditionalStatements()

                        }

                        additionalStatements

                    } else {
                        AdditionalStatements()
                    }

                case other => AdditionalStatements()
            }

            val additionalStatementsAll: AdditionalStatements = Vector(additionalStatementsForSubj, additionalStatementsForPred, additionalStatementsForObj).foldLeft(AdditionalStatements()) {
                case (acc: AdditionalStatements, addStatements: AdditionalStatements) =>

                    AdditionalStatements(additionalStatements = acc.additionalStatements ++ addStatements.additionalStatements, additionalFilterPatterns = acc.additionalFilterPatterns ++ addStatements.additionalFilterPatterns, typeInfoKeysProcessed = acc.typeInfoKeysProcessed ++ addStatements.typeInfoKeysProcessed)
            }

            // decide whether the given statement has to be included
            if (apiSchema == ApiV2Schema.SIMPLE) {

                // if pred is a valueProp, do not return the original statement
                // it had to be converted to comply with Knora's value object structure

                statementP.pred match {
                    case internalIriPred: ExtendedSearchInternalEntityIri =>

                        // convert this Iri to knora-api simple since the type inspector uses knora-api simple Iris
                        val key = TypeableIri(InputValidation.internalEntityIriToSimpleApiV2EntityIri(internalIriPred.iri, () => throw AssertionException(s"${internalIriPred.iri} could not be converted back to knora-api simple format")))

                        typeInspectionResultWhere.typedEntities.get(key) match {
                            case Some(propTypeInfo: PropertyTypeInfo) =>
                                // value types like xsd:string are not recognised as Knora entity Iris

                                if (InputValidation.isKnoraApiEntityIri(propTypeInfo.objectTypeIri)) {
                                    val internalIri = InputValidation.externalIriToInternalIri(propTypeInfo.objectTypeIri, () => throw BadRequestException(s"${propTypeInfo.objectTypeIri} is not a valid external knora-api entity Iri"))

                                    if (internalIri == OntologyConstants.KnoraBase.Resource) {
                                        // linking prop
                                        //println(s"linking prop $key")
                                        ConvertedStatement(originalStatement = Some(statementP), additionalStatements = additionalStatementsAll)
                                    } else {
                                        // no linking prop
                                        // value prop -> additional statements have been created to comply with Knora's value object structure
                                        //println(s"value prop $key")
                                        ConvertedStatement(originalStatement = None, additionalStatements = additionalStatementsAll)
                                    }


                                } else {
                                    // no linking prop
                                    // value prop -> additional statements have been created to comply with Knora's value object structure
                                    //println(s"value prop $key")
                                    ConvertedStatement(originalStatement = None, additionalStatements = additionalStatementsAll)
                                }

                            case _ =>
                                // there should be a property type annotation for the predicate
                                throw SparqlSearchException(s"no property type information found for $key")

                        }

                    case iriPred: ExtendedSearchIri =>
                        // preserve original statement
                        ConvertedStatement(originalStatement = Some(statementP), additionalStatements = additionalStatementsAll)

                    case varPred: ExtendedSearchVar =>

                        val key = TypeableVariable(varPred.variableName)

                        typeInspectionResultWhere.typedEntities.get(key) match {
                            case Some(propTypeInfo: PropertyTypeInfo) =>
                                // value types like xsd:string are not recognised as Knora entity Iris

                                if (InputValidation.isKnoraApiEntityIri(propTypeInfo.objectTypeIri)) {
                                    val internalIri = InputValidation.externalIriToInternalIri(propTypeInfo.objectTypeIri, () => throw BadRequestException(s"${propTypeInfo.objectTypeIri} is not a valid external knora-api entity Iri"))

                                    if (internalIri == OntologyConstants.KnoraBase.Resource) {
                                        // linking prop
                                        //println(s"linking prop $key")
                                        ConvertedStatement(originalStatement = Some(statementP), additionalStatements = additionalStatementsAll)
                                    } else {
                                        // no linking prop
                                        // value prop -> additional statements have been created to comply with Knora's value object structure
                                        //println(s"int value prop $key")
                                        ConvertedStatement(originalStatement = None, additionalStatements = additionalStatementsAll)
                                    }


                                } else {
                                    // no linking prop
                                    // value prop -> additional statements have been created to comply with Knora's value object structure
                                    //println(s"ext value prop $key")
                                    ConvertedStatement(originalStatement = None, additionalStatements = additionalStatementsAll)
                                }

                            case _ =>
                                // there should be a property type annotation for the predicate
                                throw SparqlSearchException(s"no property type information found for $key")

                        }

                    case other =>
                        throw NotImplementedException(s"preserve original statement not implemented for ${other}")

                }

            } else {
                throw NotImplementedException(s"Extended search not implemented for schema $apiSchema")
            }

        }

        /**
          * Processes a given Filter expression [[ExtendedSearchFilterExpression]].
          * Given Filter expression may have to be converted to more complex expressions (e.g., in case of a date).
          *
          * @param acc              the query patterns to add to.
          * @param index            the current index used to create unqiue varibale names.
          * @param filterExpression the Filter expression to be processed.
          * @return a [[ConvertedQueryPatterns]] containing the processed Filter expression and additional statements.
          */
        def processExtendedSearchFilterPattern(acc: ConvertedQueryPatterns, index: Int, filterExpression: ExtendedSearchFilterExpression): ConvertedQueryPatterns = {
            filterExpression match {
                case filterCompare: ExtendedSearchCompareExpression =>

                    // TODO: check validity of comparison operator (see extended search v1) -> make it an enum

                    filterCompare.leftArg match {
                        case searchVar: ExtendedSearchVar =>

                            val objectType: SparqlEntityTypeInfo = typeInspectionResultWhere.typedEntities(TypeableVariable(searchVar.variableName))

                            objectType match {
                                case nonPropTypeInfo: NonPropertyTypeInfo =>

                                    nonPropTypeInfo.typeIri match {
                                        case OntologyConstants.Xsd.String =>
                                            val statement = ExtendedSearchStatementPattern(subj = searchVar, pred = ExtendedSearchInternalEntityIri(OntologyConstants.KnoraBase.ValueHasString), obj = ExtendedSearchVar("stringVar" + index), true)
                                            val filterPattern = ExtendedSearchFilterPattern(ExtendedSearchCompareExpression(leftArg = ExtendedSearchVar("stringVar" + index), operator = filterCompare.operator, rightArg = filterCompare.rightArg))

                                            ConvertedQueryPatterns(originalPatterns = acc.originalPatterns :+ filterPattern, additionalPatterns = acc.additionalPatterns :+ statement, typeInfoKeysProcessedInStatements = acc.typeInfoKeysProcessedInStatements)

                                        case OntologyConstants.Xsd.Integer =>
                                            val statement = ExtendedSearchStatementPattern(subj = searchVar, pred = ExtendedSearchInternalEntityIri(OntologyConstants.KnoraBase.ValueHasInteger), obj = ExtendedSearchVar("intVar" + index), true)
                                            val filterPattern = ExtendedSearchFilterPattern(ExtendedSearchCompareExpression(leftArg = ExtendedSearchVar("intVar" + index), operator = filterCompare.operator, rightArg = filterCompare.rightArg))

                                            ConvertedQueryPatterns(originalPatterns = acc.originalPatterns :+ filterPattern, additionalPatterns = acc.additionalPatterns :+ statement, typeInfoKeysProcessedInStatements = acc.typeInfoKeysProcessedInStatements)

                                        case OntologyConstants.KnoraApiV2Simplified.Date =>

                                            // expect rightArg to be a string literal
                                            val dateStringLiteral = filterCompare.rightArg match {
                                                case stringLiteral: ExtendedSearchXsdLiteral if stringLiteral.datatype == OntologyConstants.Xsd.String =>
                                                    stringLiteral.value
                                                case other => throw SparqlSearchException(s"$other is expected to be a string literal")
                                            }

                                            val dateStr = InputValidation.toDate(dateStringLiteral, () => throw BadRequestException(s"${dateStringLiteral} is not a valid date string"))

                                            val date: JulianDayNumberValueV1 = DateUtilV1.createJDNValueV1FromDateString(dateStr)

                                            filterCompare.operator match {
                                                case "=" =>

                                                    // overlap in considered as equality
                                                    val filterPattern = ExtendedSearchFilterPattern(ExtendedSearchAndExpression(ExtendedSearchCompareExpression(ExtendedSearchXsdLiteral(date.dateval1.toString, OntologyConstants.Xsd.Integer), "<=", ExtendedSearchVar("dateValEnd" + index)), ExtendedSearchCompareExpression(ExtendedSearchXsdLiteral(date.dateval2.toString, OntologyConstants.Xsd.Integer), ">=", ExtendedSearchVar("dateValStart" + index))))

                                                    val addStatements = Vector(ExtendedSearchStatementPattern(subj = searchVar, pred = ExtendedSearchInternalEntityIri(OntologyConstants.KnoraBase.ValueHasStartJDN), obj = ExtendedSearchVar("dateValStart" + index), true),
                                                        ExtendedSearchStatementPattern(subj = searchVar, pred = ExtendedSearchInternalEntityIri(OntologyConstants.KnoraBase.ValueHasEndJDN), obj = ExtendedSearchVar("dateValEnd" + index), true))

                                                    ConvertedQueryPatterns(originalPatterns = acc.originalPatterns :+ filterPattern, additionalPatterns = acc.additionalPatterns ++ addStatements, typeInfoKeysProcessedInStatements = acc.typeInfoKeysProcessedInStatements)

                                                case "!=" =>
                                                    // no overlap in considered as inequality
                                                    val filterPattern = ExtendedSearchFilterPattern(ExtendedSearchOrExpression(ExtendedSearchCompareExpression(ExtendedSearchXsdLiteral(date.dateval1.toString, OntologyConstants.Xsd.Integer), ">", ExtendedSearchVar("dateValEnd" + index)), ExtendedSearchCompareExpression(ExtendedSearchXsdLiteral(date.dateval2.toString, OntologyConstants.Xsd.Integer), "<", ExtendedSearchVar("dateValStart" + index))))

                                                    val addStatements = Vector(ExtendedSearchStatementPattern(subj = searchVar, pred = ExtendedSearchInternalEntityIri(OntologyConstants.KnoraBase.ValueHasStartJDN), obj = ExtendedSearchVar("dateValStart" + index), true),
                                                        ExtendedSearchStatementPattern(subj = searchVar, pred = ExtendedSearchInternalEntityIri(OntologyConstants.KnoraBase.ValueHasEndJDN), obj = ExtendedSearchVar("dateValEnd" + index), true))

                                                    ConvertedQueryPatterns(originalPatterns = acc.originalPatterns :+ filterPattern, additionalPatterns = acc.additionalPatterns ++ addStatements, typeInfoKeysProcessedInStatements = acc.typeInfoKeysProcessedInStatements)

                                                case "<" =>
                                                    // no overlap in considered as inequality
                                                    val filterPattern = ExtendedSearchFilterPattern(ExtendedSearchCompareExpression(ExtendedSearchVar("dateValEnd" + index), "<", ExtendedSearchXsdLiteral(date.dateval1.toString, OntologyConstants.Xsd.Integer)))

                                                    val addStatements = Vector(ExtendedSearchStatementPattern(subj = searchVar, pred = ExtendedSearchInternalEntityIri(OntologyConstants.KnoraBase.ValueHasEndJDN), obj = ExtendedSearchVar("dateValEnd" + index), true))

                                                    ConvertedQueryPatterns(originalPatterns = acc.originalPatterns :+ filterPattern, additionalPatterns = acc.additionalPatterns ++ addStatements, typeInfoKeysProcessedInStatements = acc.typeInfoKeysProcessedInStatements)

                                                case ">=" =>
                                                    // no overlap in considered as inequality
                                                    val filterPattern = ExtendedSearchFilterPattern(ExtendedSearchCompareExpression(ExtendedSearchVar("dateValEnd" + index), ">=", ExtendedSearchXsdLiteral(date.dateval1.toString, OntologyConstants.Xsd.Integer)))

                                                    val addStatements = Vector(ExtendedSearchStatementPattern(subj = searchVar, pred = ExtendedSearchInternalEntityIri(OntologyConstants.KnoraBase.ValueHasEndJDN), obj = ExtendedSearchVar("dateValEnd" + index), true))

                                                    ConvertedQueryPatterns(originalPatterns = acc.originalPatterns :+ filterPattern, additionalPatterns = acc.additionalPatterns ++ addStatements, typeInfoKeysProcessedInStatements = acc.typeInfoKeysProcessedInStatements)

                                                case ">" =>
                                                    // no overlap in considered as inequality
                                                    val filterPattern = ExtendedSearchFilterPattern(ExtendedSearchCompareExpression(ExtendedSearchVar("dateValStart" + index), ">", ExtendedSearchXsdLiteral(date.dateval2.toString, OntologyConstants.Xsd.Integer)))

                                                    val addStatements = Vector(ExtendedSearchStatementPattern(subj = searchVar, pred = ExtendedSearchInternalEntityIri(OntologyConstants.KnoraBase.ValueHasStartJDN), obj = ExtendedSearchVar("dateValStart" + index), true))

                                                    ConvertedQueryPatterns(originalPatterns = acc.originalPatterns :+ filterPattern, additionalPatterns = acc.additionalPatterns ++ addStatements, typeInfoKeysProcessedInStatements = acc.typeInfoKeysProcessedInStatements)


                                                case "<=" =>
                                                    // no overlap in considered as inequality
                                                    val filterPattern = ExtendedSearchFilterPattern(ExtendedSearchCompareExpression(ExtendedSearchVar("dateValStart" + index), "<=", ExtendedSearchXsdLiteral(date.dateval2.toString, OntologyConstants.Xsd.Integer)))

                                                    val addStatements = Vector(ExtendedSearchStatementPattern(subj = searchVar, pred = ExtendedSearchInternalEntityIri(OntologyConstants.KnoraBase.ValueHasStartJDN), obj = ExtendedSearchVar("dateValStart" + index), true))

                                                    ConvertedQueryPatterns(originalPatterns = acc.originalPatterns :+ filterPattern, additionalPatterns = acc.additionalPatterns ++ addStatements, typeInfoKeysProcessedInStatements = acc.typeInfoKeysProcessedInStatements)


                                                case other => throw SparqlSearchException(s"operator not implemented for date filter: $other")
                                            }

                                        case OntologyConstants.Xsd.Decimal =>
                                            val statement = ExtendedSearchStatementPattern(subj = searchVar, pred = ExtendedSearchInternalEntityIri(OntologyConstants.KnoraBase.ValueHasDecimal), obj = ExtendedSearchVar("decimalVar" + index), true)
                                            val filterPattern = ExtendedSearchFilterPattern(ExtendedSearchCompareExpression(leftArg = ExtendedSearchVar("decimalVar" + index), operator = filterCompare.operator, rightArg = filterCompare.rightArg))

                                            ConvertedQueryPatterns(originalPatterns = acc.originalPatterns :+ filterPattern, additionalPatterns = acc.additionalPatterns :+ statement, typeInfoKeysProcessedInStatements = acc.typeInfoKeysProcessedInStatements)

                                        case OntologyConstants.Xsd.Boolean =>
                                            val statement = ExtendedSearchStatementPattern(subj = searchVar, pred = ExtendedSearchInternalEntityIri(OntologyConstants.KnoraBase.ValueHasBoolean), obj = ExtendedSearchVar("booleanVar" + index), true)
                                            val filterPattern = ExtendedSearchFilterPattern(ExtendedSearchCompareExpression(leftArg = ExtendedSearchVar("booleanVar" + index), operator = filterCompare.operator, rightArg = filterCompare.rightArg))

                                            ConvertedQueryPatterns(originalPatterns = acc.originalPatterns :+ filterPattern, additionalPatterns = acc.additionalPatterns :+ statement, typeInfoKeysProcessedInStatements = acc.typeInfoKeysProcessedInStatements)


                                        case otherType =>
                                            throw SparqlSearchException(s"type not implemented yet for filter: $otherType")
                                    }


                                case propType: PropertyTypeInfo =>
                                    ConvertedQueryPatterns(originalPatterns = acc.originalPatterns ++ Seq(ExtendedSearchFilterPattern(filterExpression)), additionalPatterns = acc.additionalPatterns, typeInfoKeysProcessedInStatements = acc.typeInfoKeysProcessedInStatements)
                            }

                        case nonVariable =>
                            throw SparqlSearchException(s"expected a variable as the left argument of a Filter expression, but $nonVariable given")
                    }


                case filterOr: ExtendedSearchOrExpression =>

                    val filterPatternsLeft: ConvertedQueryPatterns = processExtendedSearchFilterPattern(ConvertedQueryPatterns(originalPatterns = Vector.empty[ExtendedSearchQueryPattern], additionalPatterns = Vector.empty[ExtendedSearchStatementPattern], typeInfoKeysProcessedInStatements = Set.empty[TypeableEntity]), index, filterOr.leftArg)
                    val filterPatternsRight: ConvertedQueryPatterns = processExtendedSearchFilterPattern(ConvertedQueryPatterns(originalPatterns = Vector.empty[ExtendedSearchQueryPattern], additionalPatterns = Vector.empty[ExtendedSearchStatementPattern], typeInfoKeysProcessedInStatements = Set.empty[TypeableEntity]), index, filterOr.rightArg)

                    val leftArg = filterPatternsLeft.originalPatterns match {
                        case queryP: Vector[ExtendedSearchQueryPattern] if queryP.size == 1 =>
                            queryP.head match {
                                case filterP: ExtendedSearchFilterPattern =>
                                    filterP.expression
                                case _ => throw SparqlSearchException("Could not process filter Or expression: leftArg is not a ExtendedSearchFilterPattern")
                            }

                        case _ => throw SparqlSearchException("Could not process filter Or expression: leftArg is not a Vector of type ExtendedSearchQueryPattern with size 1")
                    }

                    val rightArg = filterPatternsRight.originalPatterns match {
                        case queryP: Vector[ExtendedSearchQueryPattern] if queryP.size == 1 =>
                            queryP.head match {
                                case filterP: ExtendedSearchFilterPattern =>
                                    filterP.expression
                                case _ => throw SparqlSearchException("Could not process filter Or expression: rightArg is not a ExtendedSearchFilterPattern")
                            }

                        case _ => throw SparqlSearchException("Could not process filter Or expression: rightArg is not a Vector of type ExtendedSearchQueryPattern with size 1")
                    }

                    // recreate the Or expression and also return statements that were additionally created
                    val orExpression = ExtendedSearchOrExpression(leftArg = leftArg, rightArg = rightArg)

                    ConvertedQueryPatterns(originalPatterns = acc.originalPatterns ++ Seq(ExtendedSearchFilterPattern(orExpression)), additionalPatterns = acc.additionalPatterns ++ filterPatternsLeft.additionalPatterns ++ filterPatternsRight.additionalPatterns, typeInfoKeysProcessedInStatements = acc.typeInfoKeysProcessedInStatements)

                case filterAnd: ExtendedSearchAndExpression =>

                    val filterPatternsLeft: ConvertedQueryPatterns = processExtendedSearchFilterPattern(ConvertedQueryPatterns(originalPatterns = Vector.empty[ExtendedSearchQueryPattern], additionalPatterns = Vector.empty[ExtendedSearchStatementPattern], typeInfoKeysProcessedInStatements = Set.empty[TypeableEntity]), index, filterAnd.leftArg)
                    val filterPatternsRight: ConvertedQueryPatterns = processExtendedSearchFilterPattern(ConvertedQueryPatterns(originalPatterns = Vector.empty[ExtendedSearchQueryPattern], additionalPatterns = Vector.empty[ExtendedSearchStatementPattern], typeInfoKeysProcessedInStatements = Set.empty[TypeableEntity]), index, filterAnd.rightArg)

                    val leftArg = filterPatternsLeft.originalPatterns match {
                        case queryP: Vector[ExtendedSearchQueryPattern] if queryP.size == 1 =>
                            queryP.head match {
                                case filterP: ExtendedSearchFilterPattern =>
                                    filterP.expression
                                case _ => throw SparqlSearchException("Could not process filter And expression: leftArg is not a ExtendedSearchFilterPattern")
                            }

                        case _ => throw SparqlSearchException("Could not process filter And expression: leftArg is not a Vector of type ExtendedSearchQueryPattern with size 1")
                    }

                    val rightArg = filterPatternsRight.originalPatterns match {
                        case queryP: Vector[ExtendedSearchQueryPattern] if queryP.size == 1 =>
                            queryP.head match {
                                case filterP: ExtendedSearchFilterPattern =>
                                    filterP.expression
                                case _ => throw SparqlSearchException("Could not process filter And expression: rightArg is not a ExtendedSearchFilterPattern")
                            }

                        case _ => throw SparqlSearchException("Could not process filter And expression: rightArg is not a Vector of type ExtendedSearchQueryPattern with size 1")
                    }

                    // recreate the And expression and also return statements that were additionally created
                    val andExpression = ExtendedSearchAndExpression(leftArg = leftArg, rightArg = rightArg)

                    ConvertedQueryPatterns(originalPatterns = acc.originalPatterns ++ Seq(ExtendedSearchFilterPattern(andExpression)), additionalPatterns = acc.additionalPatterns ++ filterPatternsLeft.additionalPatterns ++ filterPatternsRight.additionalPatterns, typeInfoKeysProcessedInStatements = acc.typeInfoKeysProcessedInStatements)


                case other =>
                    throw SparqlSearchException(s"unsupported Filter expression: $other")
            }
        }

        /**
          * Represents original query patterns provided by the user a additional statements created on the bases of the given type annotations.
          *
          * @param originalPatterns                  the patterns originally provided by the user.
          * @param additionalPatterns                additional statements created on the bases of the given type annotations.
          * @param typeInfoKeysProcessedInStatements a Set of keys that indicates which type info entries already habe been processed.
          */
        case class ConvertedQueryPatterns(originalPatterns: Vector[ExtendedSearchQueryPattern], additionalPatterns: Vector[ExtendedSearchStatementPattern], typeInfoKeysProcessedInStatements: Set[TypeableEntity])

        def convertQueryPatterns(patterns: Seq[ExtendedSearchQueryPattern]): ConvertedQueryPatterns = {
            patterns.zipWithIndex.foldLeft(ConvertedQueryPatterns(originalPatterns = Vector.empty[ExtendedSearchQueryPattern], additionalPatterns = Vector.empty[ExtendedSearchStatementPattern], typeInfoKeysProcessedInStatements = Set.empty[TypeableEntity])) {

                case (acc: ConvertedQueryPatterns, (pattern: ExtendedSearchQueryPattern, index: Int)) =>

                    pattern match {
                        case statementP: ExtendedSearchStatementPattern =>

                            val convertedStatement: ConvertedStatement = addTypeInformationStatementsToStatement(statementP, index, acc.typeInfoKeysProcessedInStatements)

                            ConvertedQueryPatterns(originalPatterns = acc.originalPatterns ++ convertedStatement.originalStatement ++ convertedStatement.additionalStatements.additionalFilterPatterns, additionalPatterns = acc.additionalPatterns ++ convertedStatement.additionalStatements.additionalStatements, typeInfoKeysProcessedInStatements = acc.typeInfoKeysProcessedInStatements ++ convertedStatement.additionalStatements.typeInfoKeysProcessed)


                        case optionalP: ExtendedSearchOptionalPattern =>
                            val optionalPatterns = Seq(ExtendedSearchOptionalPattern(convertQueryPatterns(optionalP.patterns).originalPatterns))

                            ConvertedQueryPatterns(originalPatterns = acc.originalPatterns ++ optionalPatterns, additionalPatterns = acc.additionalPatterns, typeInfoKeysProcessedInStatements = acc.typeInfoKeysProcessedInStatements)

                        case unionP: ExtendedSearchUnionPattern =>
                            val blocks = unionP.blocks.map {
                                blockPatterns: Seq[ExtendedSearchQueryPattern] => convertQueryPatterns(blockPatterns).originalPatterns
                            }

                            ConvertedQueryPatterns(originalPatterns = acc.originalPatterns ++ Seq(ExtendedSearchUnionPattern(blocks)), additionalPatterns = acc.additionalPatterns, typeInfoKeysProcessedInStatements = acc.typeInfoKeysProcessedInStatements)

                        case filterP: ExtendedSearchFilterPattern =>

                            processExtendedSearchFilterPattern(acc, index, filterP.expression)

                    }


            }
        }

        // convert where statements (handle FILTER expressions)
        val extendedSearchWhereClausePatternsConverted: ConvertedQueryPatterns = convertQueryPatterns(extendedSearchWhereClausePatternsWithOriginalFilters)

        val constructQuery = ExtendedSearchQuery(
            constructClause = extendedSearchConstructClauseStatementPatterns.toVector ++ extendedSearchWhereClausePatternsConverted.additionalPatterns,
            whereClause = extendedSearchWhereClausePatternsConverted.originalPatterns ++ extendedSearchWhereClausePatternsConverted.additionalPatterns
        )

        for {

            searchSparql <- Future(queries.sparql.v2.txt.searchExtended(
                triplestore = settings.triplestoreType,
                query = constructQuery
            ).toString())

            // _ = println(searchSparql)


            searchResponse: SparqlConstructResponse <- (storeManager ? SparqlConstructRequest(searchSparql)).mapTo[SparqlConstructResponse]


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