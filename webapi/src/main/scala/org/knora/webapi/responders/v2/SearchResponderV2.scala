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
            def transformStatementInConstruct(statementPattern: StatementPattern): Seq[StatementPattern] = Seq(preprocessStatementPattern(statementPattern = statementPattern, inWhereClause = false))

            def transformStatementInWhere(statementPattern: StatementPattern): Seq[QueryPattern] = Seq(preprocessStatementPattern(statementPattern = statementPattern, inWhereClause = true))

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
            def preprocessStatementPattern(statementPattern: StatementPattern, inWhereClause: Boolean): StatementPattern = {

                val subj = preprocessEntity(statementPattern.subj)
                val pred = preprocessEntity(statementPattern.pred)
                val obj = preprocessEntity(statementPattern.obj)

                val disableInference = inWhereClause && (pred match { // disable inference if `inWhereClause` is set to true and the statement's predicate is a variable.
                    case variable: QueryVariable => true // disable inference to get the actual IRI for the predicate and not an inferred information
                    // TODO: this has the effect that subproperties are not found by the query!
                    // TODO: I think this may be omitted since we can get the actual property from the reification (ConstructResponseUtilV2 does not look at subproperties of hasLinkTo, this property is needed to get information about the resource referred to)
                    case _ => false
                })

                val namedGraph = if (disableInference) Some(IriRef(OntologyConstants.NamedGraphs.KnoraExplicitNamedGraph)) else None

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
            // TODO: refactor the code below and move it into this class.

            def transformStatementInConstruct(statementPattern: StatementPattern): Seq[StatementPattern] = ???

            def transformStatementInWhere(statementPattern: StatementPattern): Seq[QueryPattern] = ???

            def transformFilter(filterPattern: FilterPattern): Seq[QueryPattern] = ???
        }

        /**
          * Creates additional statements based on a non property type Iri.
          *
          * @param typeIriExternal       the non property type Iri.
          * @param subject               the entity that is the subject in the additional statement to be generated.
          * @param typeInfoKeysProcessed a Set of keys that indicates which type info entries already habe been processed.
          * @param index                 the index to be used to create variables in Sparql.
          * @return a sequence of [[StatementPattern]].
          */
        def createAdditionalStatementsForNonPropertyType(typeIriExternal: IRI, subject: Entity, typeInfoKeysProcessed: Set[TypeableEntity], index: Int): AdditionalStatements = {
            val typeIriInternal = InputValidation.externalIriToInternalIri(typeIriExternal, () => throw BadRequestException(s"$typeIriExternal is not a valid external knora-api entity Iri"))

            if (typeIriInternal == OntologyConstants.KnoraBase.Resource) {

                // create additional statements in order to query permissions and other information for a resource

                AdditionalStatements(additionalStatements = Vector(
                    StatementPattern(subj = subject, pred = IriRef(OntologyConstants.Rdf.Type), obj = IriRef(OntologyConstants.KnoraBase.Resource)),
                    StatementPattern(subj = subject, pred = IriRef(OntologyConstants.KnoraBase.IsDeleted), obj = XsdLiteral(value = "false", datatype = OntologyConstants.Xsd.Boolean)).toKnoraExplicit,
                    StatementPattern(subj = subject, pred = IriRef(OntologyConstants.Rdfs.Label), obj = QueryVariable("resourceLabel" + index)).toKnoraExplicit,
                    StatementPattern(subj = subject, pred = IriRef(OntologyConstants.Rdf.Type), obj = QueryVariable("resourceType" + index)).toKnoraExplicit,
                    StatementPattern(subj = subject, pred = IriRef(OntologyConstants.KnoraBase.AttachedToUser), obj = QueryVariable("resourceCreator" + index)).toKnoraExplicit,
                    StatementPattern(subj = subject, pred = IriRef(OntologyConstants.KnoraBase.HasPermissions), obj = QueryVariable("resourcePermissions" + index)).toKnoraExplicit,
                    StatementPattern(subj = subject, pred = IriRef(OntologyConstants.KnoraBase.AttachedToProject), obj = QueryVariable("resourceProject" + index)).toKnoraExplicit
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
          * @return a sequence of statement patterns.
          */
        def createAdditionalStatementsForPropertyType(typeIriExternal: IRI, statementPattern: StatementPattern, typeInfoKeysProcessed: Set[TypeableEntity], index: Int): AdditionalStatements = {

            // convert the type information into an internal Knora Iri if possible
            val objectIri = if (InputValidation.isKnoraApiEntityIri(typeIriExternal)) {
                InputValidation.externalIriToInternalIri(typeIriExternal, () => throw BadRequestException(s"$typeIriExternal is not a valid external knora-api entity Iri"))
            } else {
                typeIriExternal
            }

            objectIri match {
                case OntologyConstants.KnoraBase.Resource =>

                    // the given statement pattern's object is of type resource
                    // this means that the predicate of the statement pattern is a linking property
                    // create statements in order to query the link value describing the link in question

                    // variable referring to the link's value object (reification)
                    val linkValueObjVar = QueryVariable("linkValueObj" + index)

                    AdditionalStatements(additionalStatements = Vector(
                        StatementPattern(subj = statementPattern.subj, pred = IriRef(OntologyConstants.KnoraBase.HasValue), obj = linkValueObjVar), // use inference since this is a generic property
                        StatementPattern(subj = statementPattern.subj, pred = QueryVariable("linkValueProp" + index), obj = linkValueObjVar).toKnoraExplicit,
                        StatementPattern(subj = linkValueObjVar, pred = IriRef(OntologyConstants.KnoraBase.IsDeleted), obj = XsdLiteral(value = "false", datatype = OntologyConstants.Xsd.Boolean)).toKnoraExplicit,
                        StatementPattern(subj = linkValueObjVar, pred = IriRef(OntologyConstants.Rdf.Type), obj = IriRef(OntologyConstants.KnoraBase.LinkValue)).toKnoraExplicit,
                        StatementPattern(subj = linkValueObjVar, pred = IriRef(OntologyConstants.Rdf.Subject), obj = statementPattern.subj).toKnoraExplicit,
                        StatementPattern(subj = linkValueObjVar, pred = IriRef(OntologyConstants.Rdf.Object), obj = statementPattern.obj).toKnoraExplicit,
                        StatementPattern(subj = linkValueObjVar, pred = QueryVariable("linkValueObjProp" + index), obj = QueryVariable("linkValueObjVal" + index)).toKnoraExplicit
                    ), typeInfoKeysProcessed = typeInfoKeysProcessed)
                case OntologyConstants.Xsd.String =>

                    if (apiSchema == ApiV2Schema.SIMPLE) {
                        // the direct statement from the resource to a string literal (simplified) has to be translated to a value object (extra level).
                        // please note that the original direct statement will be filtered out and not end up in the Sparql submitted to the triplestore.

                        statementPattern.obj match {

                            case stringLiteral: XsdLiteral =>
                                // the statement's object is a literal

                                // TODO: assure that this is a string literal (valueHasString expects a string literal)

                                // insert an extra level so that the resource points to the literal via a value object

                                // variable referring to the string value object
                                val stringValueObjVar = QueryVariable("stringValueObj" + index)

                                AdditionalStatements(additionalStatements = Vector(
                                    StatementPattern(subj = statementPattern.subj, pred = IriRef(OntologyConstants.KnoraBase.HasValue), obj = stringValueObjVar), // use inference since this is a generic property
                                    StatementPattern(subj = statementPattern.subj, pred = statementPattern.pred, obj = stringValueObjVar), // use inference in order to get all subproperties as well
                                    StatementPattern(subj = stringValueObjVar, pred = IriRef(OntologyConstants.KnoraBase.IsDeleted), obj = XsdLiteral(value = "false", datatype = OntologyConstants.Xsd.Boolean)).toKnoraExplicit,
                                    StatementPattern(subj = stringValueObjVar, pred = IriRef(OntologyConstants.Rdf.Type), obj = IriRef(OntologyConstants.KnoraBase.TextValue)).toKnoraExplicit,
                                    StatementPattern(subj = stringValueObjVar, pred = QueryVariable("stringValueObjProp" + index), obj = QueryVariable("stringValueObjVal" + index)).toKnoraExplicit,
                                    StatementPattern(subj = stringValueObjVar, pred = IriRef(OntologyConstants.KnoraBase.ValueHasString), obj = statementPattern.obj).toKnoraExplicit // check that valueHasString equals the given string literal
                                ), typeInfoKeysProcessed = typeInfoKeysProcessed)

                            case stringVar: QueryVariable =>
                                // the statement's object is a variable

                                // since all value property statements are eliminated, recreate the statement using the given predicate

                                // TODO: maybe only those value property statements have to be eliminated whose object is a literal

                                AdditionalStatements(additionalStatements = Vector(
                                    StatementPattern(subj = statementPattern.subj, pred = IriRef(OntologyConstants.KnoraBase.HasValue), obj = stringVar), // use inference since this is a generic property
                                    StatementPattern(subj = statementPattern.subj, pred = statementPattern.pred, obj = stringVar), // use inference in order to get all subproperties as well
                                    StatementPattern(subj = stringVar, pred = IriRef(OntologyConstants.KnoraBase.IsDeleted), obj = XsdLiteral(value = "false", datatype = OntologyConstants.Xsd.Boolean)).toKnoraExplicit,
                                    StatementPattern(subj = stringVar, pred = IriRef(OntologyConstants.Rdf.Type), obj = IriRef(OntologyConstants.KnoraBase.TextValue)).toKnoraExplicit,
                                    StatementPattern(subj = stringVar, pred = QueryVariable("stringValueObjProp" + index), obj = QueryVariable("stringValueObjVal" + index)).toKnoraExplicit
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

                            case booleanLiteral: XsdLiteral =>

                                // variable referring to the integer value object
                                val booleanValueObjVar = QueryVariable("booleanValueObj" + index)

                                AdditionalStatements(additionalStatements = Vector(
                                    StatementPattern(subj = statementPattern.subj, pred = IriRef(OntologyConstants.KnoraBase.HasValue), obj = booleanValueObjVar),
                                    StatementPattern(subj = statementPattern.subj, pred = statementPattern.pred, obj = booleanValueObjVar),
                                    StatementPattern(subj = booleanValueObjVar, pred = IriRef(OntologyConstants.KnoraBase.IsDeleted), obj = XsdLiteral(value = "false", datatype = OntologyConstants.Xsd.Boolean)).toKnoraExplicit,
                                    StatementPattern(subj = booleanValueObjVar, pred = IriRef(OntologyConstants.Rdf.Type), obj = IriRef(OntologyConstants.KnoraBase.BooleanValue)).toKnoraExplicit,
                                    StatementPattern(subj = booleanValueObjVar, pred = QueryVariable("booleanValueObjProp" + index), obj = QueryVariable("booleanValueObjVal" + index)).toKnoraExplicit,
                                    StatementPattern(subj = booleanValueObjVar, pred = IriRef(OntologyConstants.KnoraBase.ValueHasBoolean), obj = statementPattern.obj).toKnoraExplicit
                                ), typeInfoKeysProcessed = typeInfoKeysProcessed)

                            case decimalVar: QueryVariable =>

                                // the statement's object is a variable

                                // since all value property statements are eliminated, recreate the statement using the given predicate

                                // TODO: maybe only those value property statements have to be eliminated whose object is a literal

                                AdditionalStatements(additionalStatements = Vector(
                                    StatementPattern(subj = statementPattern.subj, pred = IriRef(OntologyConstants.KnoraBase.HasValue), obj = decimalVar),
                                    StatementPattern(subj = statementPattern.subj, pred = statementPattern.pred, obj = decimalVar),
                                    StatementPattern(subj = decimalVar, pred = IriRef(OntologyConstants.KnoraBase.IsDeleted), obj = XsdLiteral(value = "false", datatype = OntologyConstants.Xsd.Boolean)).toKnoraExplicit,
                                    StatementPattern(subj = decimalVar, pred = IriRef(OntologyConstants.Rdf.Type), obj = IriRef(OntologyConstants.KnoraBase.BooleanValue)).toKnoraExplicit,
                                    StatementPattern(subj = decimalVar, pred = QueryVariable("booleanValueObjProp" + index), obj = QueryVariable("booleanValueObjVal" + index)).toKnoraExplicit
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

                            case integerLiteral: XsdLiteral =>

                                // variable referring to the integer value object
                                val integerValueObjVar = QueryVariable("integerValueObj" + index)

                                AdditionalStatements(additionalStatements = Vector(
                                    StatementPattern(subj = statementPattern.subj, pred = IriRef(OntologyConstants.KnoraBase.HasValue), obj = integerValueObjVar),
                                    StatementPattern(subj = statementPattern.subj, pred = statementPattern.pred, obj = integerValueObjVar),
                                    StatementPattern(subj = integerValueObjVar, pred = IriRef(OntologyConstants.KnoraBase.IsDeleted), obj = XsdLiteral(value = "false", datatype = OntologyConstants.Xsd.Boolean)).toKnoraExplicit,
                                    StatementPattern(subj = integerValueObjVar, pred = IriRef(OntologyConstants.Rdf.Type), obj = IriRef(OntologyConstants.KnoraBase.IntValue)).toKnoraExplicit,
                                    StatementPattern(subj = integerValueObjVar, pred = QueryVariable("integerValueObjProp" + index), obj = QueryVariable("integerValueObjVal" + index)).toKnoraExplicit,
                                    StatementPattern(subj = integerValueObjVar, pred = IriRef(OntologyConstants.KnoraBase.ValueHasInteger), obj = statementPattern.obj).toKnoraExplicit
                                ), typeInfoKeysProcessed = typeInfoKeysProcessed)

                            case integerVar: QueryVariable =>

                                // the statement's object is a variable

                                // since all value property statements are eliminated, recreate the statement using the given predicate

                                // TODO: maybe only those value property statements have to be eliminated whose object is a literal

                                AdditionalStatements(additionalStatements = Vector(
                                    StatementPattern(subj = statementPattern.subj, pred = IriRef(OntologyConstants.KnoraBase.HasValue), obj = integerVar),
                                    StatementPattern(subj = statementPattern.subj, pred = statementPattern.pred, obj = integerVar),
                                    StatementPattern(subj = integerVar, pred = IriRef(OntologyConstants.KnoraBase.IsDeleted), obj = XsdLiteral(value = "false", datatype = OntologyConstants.Xsd.Boolean)).toKnoraExplicit,
                                    StatementPattern(subj = integerVar, pred = IriRef(OntologyConstants.Rdf.Type), obj = IriRef(OntologyConstants.KnoraBase.IntValue)).toKnoraExplicit,
                                    StatementPattern(subj = integerVar, pred = QueryVariable("integerValueObjProp" + index), obj = QueryVariable("integerValueObjVal" + index)).toKnoraExplicit
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

                            case decimalLiteral: XsdLiteral =>

                                // variable referring to the integer value object
                                val decimalValueObjVar = QueryVariable("decimalValueObj" + index)

                                AdditionalStatements(additionalStatements = Vector(
                                    StatementPattern(subj = statementPattern.subj, pred = IriRef(OntologyConstants.KnoraBase.HasValue), obj = decimalValueObjVar),
                                    StatementPattern(subj = statementPattern.subj, pred = statementPattern.pred, obj = decimalValueObjVar),
                                    StatementPattern(subj = decimalValueObjVar, pred = IriRef(OntologyConstants.KnoraBase.IsDeleted), obj = XsdLiteral(value = "false", datatype = OntologyConstants.Xsd.Boolean)).toKnoraExplicit,
                                    StatementPattern(subj = decimalValueObjVar, pred = IriRef(OntologyConstants.Rdf.Type), obj = IriRef(OntologyConstants.KnoraBase.DecimalValue)).toKnoraExplicit,
                                    StatementPattern(subj = decimalValueObjVar, pred = QueryVariable("decimalValueObjProp" + index), obj = QueryVariable("decimalValueObjVal" + index)).toKnoraExplicit,
                                    StatementPattern(subj = decimalValueObjVar, pred = IriRef(OntologyConstants.KnoraBase.ValueHasDecimal), obj = statementPattern.obj).toKnoraExplicit
                                ), typeInfoKeysProcessed = typeInfoKeysProcessed)

                            case decimalVar: QueryVariable =>

                                // the statement's object is a variable

                                // since all value property statements are eliminated, recreate the statement using the given predicate

                                // TODO: maybe only those value property statements have to be eliminated whose object is a literal

                                AdditionalStatements(additionalStatements = Vector(
                                    StatementPattern(subj = statementPattern.subj, pred = IriRef(OntologyConstants.KnoraBase.HasValue), obj = decimalVar),
                                    StatementPattern(subj = statementPattern.subj, pred = statementPattern.pred, obj = decimalVar),
                                    StatementPattern(subj = decimalVar, pred = IriRef(OntologyConstants.KnoraBase.IsDeleted), obj = XsdLiteral(value = "false", datatype = OntologyConstants.Xsd.Boolean)).toKnoraExplicit,
                                    StatementPattern(subj = decimalVar, pred = IriRef(OntologyConstants.Rdf.Type), obj = IriRef(OntologyConstants.KnoraBase.DecimalValue)).toKnoraExplicit,
                                    StatementPattern(subj = decimalVar, pred = QueryVariable("decimalValueObjProp" + index), obj = QueryVariable("decimalValueObjVal" + index)).toKnoraExplicit
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

                            case dateLiteral: XsdLiteral =>

                                val dateStr = InputValidation.toDate(dateLiteral.value, () => throw BadRequestException(s"${dateLiteral.value} is not a valid date string"))

                                val date: JulianDayNumberValueV1 = DateUtilV1.createJDNValueV1FromDateString(dateStr)

                                val filterPattern = FilterPattern(AndExpression(CompareExpression(XsdLiteral(date.dateval1.toString, OntologyConstants.Xsd.Integer), "<=", QueryVariable("dateValEnd" + index)), CompareExpression(XsdLiteral(date.dateval2.toString, OntologyConstants.Xsd.Integer), ">=", QueryVariable("dateValStart" + index))))

                                // variable referring to the date value object
                                val dateValueObject = QueryVariable("dateValueObj" + index)

                                AdditionalStatements(additionalStatements = Vector(
                                    StatementPattern(subj = statementPattern.subj, pred = IriRef(OntologyConstants.KnoraBase.HasValue), obj = dateValueObject),
                                    StatementPattern(subj = statementPattern.subj, pred = statementPattern.pred, obj = dateValueObject),
                                    StatementPattern(subj = dateValueObject, pred = IriRef(OntologyConstants.KnoraBase.IsDeleted), obj = XsdLiteral(value = "false", datatype = OntologyConstants.Xsd.Boolean)).toKnoraExplicit,
                                    StatementPattern(subj = dateValueObject, pred = IriRef(OntologyConstants.Rdf.Type), obj = IriRef(OntologyConstants.KnoraBase.DateValue)).toKnoraExplicit,
                                    StatementPattern(subj = dateValueObject, pred = QueryVariable("dateValueObjProp" + index), obj = QueryVariable("dateValueObjVal" + index)).toKnoraExplicit,
                                    StatementPattern(subj = dateValueObject, pred = IriRef(OntologyConstants.KnoraBase.ValueHasStartJDN), obj = QueryVariable("dateValStart" + index)).toKnoraExplicit,
                                    StatementPattern(subj = dateValueObject, pred = IriRef(OntologyConstants.KnoraBase.ValueHasEndJDN), obj = QueryVariable("dateValEnd" + index)).toKnoraExplicit
                                ), additionalFilterPatterns = Vector(filterPattern), typeInfoKeysProcessed = typeInfoKeysProcessed)

                            case dateVar: QueryVariable =>

                                // the statement's object is a variable

                                // since all value property statements are eliminated, recreate the statement using the given predicate

                                // TODO: maybe only those value property statements have to be eliminated whose object is a literal

                                AdditionalStatements(additionalStatements = Vector(
                                    StatementPattern(subj = statementPattern.subj, pred = IriRef(OntologyConstants.KnoraBase.HasValue), obj = dateVar),
                                    StatementPattern(subj = statementPattern.subj, pred = statementPattern.pred, obj = dateVar),
                                    StatementPattern(subj = dateVar, pred = IriRef(OntologyConstants.KnoraBase.IsDeleted), obj = XsdLiteral(value = "false", datatype = OntologyConstants.Xsd.Boolean)).toKnoraExplicit,
                                    StatementPattern(subj = dateVar, pred = IriRef(OntologyConstants.Rdf.Type), obj = IriRef(OntologyConstants.KnoraBase.DateValue)).toKnoraExplicit,
                                    StatementPattern(subj = dateVar, pred = QueryVariable("dateValueObjProp" + index), obj = QueryVariable("dateValueObjVal" + index)).toKnoraExplicit
                                ), typeInfoKeysProcessed = typeInfoKeysProcessed)

                            case _ => AdditionalStatements()

                        }

                    } else {
                        throw NotImplementedException(s"Extended search not implemented for schema $apiSchema")
                    }

                case OntologyConstants.KnoraBase.StillImageFile =>
                    if (apiSchema == ApiV2Schema.SIMPLE) {

                        statementPattern.obj match {

                            case fileVar: QueryVariable =>

                                // the statement's object is a variable

                                // since all value property statements are eliminated, recreate the statement using the given predicate

                                // TODO: maybe only those value property statements have to be eliminated whose object is a literal

                                AdditionalStatements(additionalStatements = Vector(
                                    StatementPattern(subj = statementPattern.subj, pred = IriRef(OntologyConstants.KnoraBase.HasValue), obj = fileVar),
                                    StatementPattern(subj = statementPattern.subj, pred = statementPattern.pred, obj = fileVar),
                                    StatementPattern(subj = fileVar, pred = IriRef(OntologyConstants.KnoraBase.IsDeleted), obj = XsdLiteral(value = "false", datatype = OntologyConstants.Xsd.Boolean)).toKnoraExplicit,
                                    StatementPattern(subj = fileVar, pred = IriRef(OntologyConstants.Rdf.Type), obj = IriRef(OntologyConstants.KnoraBase.StillImageFileValue)).toKnoraExplicit,
                                    StatementPattern(subj = fileVar, pred = QueryVariable("fileValueObjProp" + index), obj = QueryVariable("fileValueObjVal" + index)).toKnoraExplicit
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

                            case fileVar: QueryVariable =>
                                AdditionalStatements(additionalStatements = Vector(
                                    StatementPattern(subj = statementPattern.subj, pred = IriRef(OntologyConstants.KnoraBase.HasValue), obj = fileVar),
                                    StatementPattern(subj = statementPattern.subj, pred = statementPattern.pred, obj = fileVar),
                                    StatementPattern(subj = fileVar, pred = IriRef(OntologyConstants.KnoraBase.IsDeleted), obj = XsdLiteral(value = "false", datatype = OntologyConstants.Xsd.Boolean)).toKnoraExplicit,
                                    StatementPattern(subj = fileVar, pred = IriRef(OntologyConstants.Rdf.Type), obj = IriRef(OntologyConstants.KnoraBase.GeomValue)).toKnoraExplicit,
                                    StatementPattern(subj = fileVar, pred = QueryVariable("geomValueObjProp" + index), obj = QueryVariable("geomValueObjVal" + index)).toKnoraExplicit
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

                            case colorLiteral: XsdLiteral =>

                                // variable referring to the color value object
                                val colorValueObject = QueryVariable("colorValueObj" + index)

                                AdditionalStatements(additionalStatements = Vector(
                                    StatementPattern(subj = statementPattern.subj, pred = IriRef(OntologyConstants.KnoraBase.HasValue), obj = colorValueObject),
                                    StatementPattern(subj = statementPattern.subj, pred = statementPattern.pred, obj = colorValueObject),
                                    StatementPattern(subj = colorValueObject, pred = IriRef(OntologyConstants.KnoraBase.IsDeleted), obj = XsdLiteral(value = "false", datatype = OntologyConstants.Xsd.Boolean)).toKnoraExplicit,
                                    StatementPattern(subj = colorValueObject, pred = IriRef(OntologyConstants.Rdf.Type), obj = IriRef(OntologyConstants.KnoraBase.ColorValue)).toKnoraExplicit,
                                    StatementPattern(subj = colorValueObject, pred = QueryVariable("colorValueObjProp" + index), obj = QueryVariable("colorValueObjVal" + index)).toKnoraExplicit,
                                    StatementPattern(subj = colorValueObject, pred = IriRef(OntologyConstants.KnoraBase.ValueHasColor), obj = statementPattern.obj).toKnoraExplicit
                                ), typeInfoKeysProcessed = typeInfoKeysProcessed)

                            case colorVar: QueryVariable =>

                                // the statement's object is a variable

                                // since all value property statements are eliminated, recreate the statement using the given predicate

                                // TODO: maybe only those value property statements have to be eliminated whose object is a literal

                                AdditionalStatements(additionalStatements = Vector(
                                    StatementPattern(subj = statementPattern.subj, pred = IriRef(OntologyConstants.KnoraBase.HasValue), obj = colorVar),
                                    StatementPattern(subj = statementPattern.subj, pred = statementPattern.pred, obj = colorVar),
                                    StatementPattern(subj = colorVar, pred = IriRef(OntologyConstants.KnoraBase.IsDeleted), obj = XsdLiteral(value = "false", datatype = OntologyConstants.Xsd.Boolean)).toKnoraExplicit,
                                    StatementPattern(subj = colorVar, pred = IriRef(OntologyConstants.Rdf.Type), obj = IriRef(OntologyConstants.KnoraBase.ColorValue)).toKnoraExplicit,
                                    StatementPattern(subj = colorVar, pred = QueryVariable("colorValueObjProp" + index), obj = QueryVariable("colorValueObjVal" + index)).toKnoraExplicit
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
        case class AdditionalStatements(additionalStatements: Vector[StatementPattern] = Vector.empty[StatementPattern], additionalFilterPatterns: Vector[FilterPattern] = Vector.empty[FilterPattern], typeInfoKeysProcessed: Set[TypeableEntity] = Set.empty[TypeableEntity])

        /**
          *
          * @param originalStatement    the statement originally given by the user. Since some statements have to be converted (e.g. direct statements from resources to literals in the simplified Api schema), original statements might not be returned.
          * @param additionalStatements statements created based on the given type annotations.
          */
        case class ConvertedStatement(originalStatement: Option[StatementPattern], additionalStatements: AdditionalStatements)

        /**
          * Based on the given type annotations, convert the given statement.
          *
          * @param statementP                        the given statement.
          * @param index                             the current index (used to create unique variable names).
          * @param typeInfoKeysProcessedInStatements a Set of keys that indicates which type info entries already habe been processed.
          * @return a sequence of [[AdditionalStatements]].
          */
        def addTypeInformationStatementsToStatement(typeInspectionResult: TypeInspectionResult, statementP: StatementPattern, index: Int, typeInfoKeysProcessedInStatements: Set[TypeableEntity]): ConvertedStatement = {

            // check if subject is contained in the type info
            val additionalStatementsForSubj: AdditionalStatements = statementP.subj match {
                case variableSubj: QueryVariable =>
                    val key = TypeableVariable(variableSubj.variableName)

                    if (typeInspectionResult.typedEntities -- typeInfoKeysProcessedInStatements contains key) {

                        val additionalStatements: AdditionalStatements = typeInspectionResult.typedEntities(key) match {
                            case nonPropTypeInfo: NonPropertyTypeInfo =>
                                createAdditionalStatementsForNonPropertyType(nonPropTypeInfo.typeIri, statementP.subj, typeInfoKeysProcessedInStatements + key, index)
                            case propTypeInfo: PropertyTypeInfo =>
                                AdditionalStatements()
                        }

                        additionalStatements


                    } else {
                        AdditionalStatements()
                    }


                case iriSubj: IriRef =>
                    val key = TypeableIri(iriSubj.iri)

                    AdditionalStatements()

                case other =>
                    AdditionalStatements()
            }

            // check the predicate: must be either a variable or an Iri (cannot be a literal)
            // the predicate represents a property
            val additionalStatementsForPred: AdditionalStatements = statementP.pred match {
                case variablePred: QueryVariable =>
                    val key = TypeableVariable(variablePred.variableName)

                    // get type information about the predicate variable (if not already processed before)
                    if (typeInspectionResult.typedEntities -- typeInfoKeysProcessedInStatements contains key) {

                        val additionalStatements: AdditionalStatements = typeInspectionResult.typedEntities(key) match {
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

                case iriPred: IriRef =>
                    // TODO: figure out another way to deal with the fact that the type inspector uses knora-api simple IRIs.
                    // Two options:
                    // 1. convert the IRIs from the type inspector to internal ones
                    // 2. add a flag in IriRef to indicate whether it's internal

                    val key = if (apiSchema == ApiV2Schema.SIMPLE) {
                        // convert this Iri to knora-api simple since the type inspector uses knora-api simple Iris
                        TypeableIri(InputValidation.internalEntityIriToSimpleApiV2EntityIri(iriPred.iri, () => throw AssertionException(s"${iriPred.iri} could not be converted back to knora-api simple format")))
                    } else {
                        throw NotImplementedException("The extended search for knora-api with value object has not been implemented yet")
                    }

                    // get type information about the predicate Iri (if not already processed before)
                    if (typeInspectionResult.typedEntities -- typeInfoKeysProcessedInStatements contains key) {

                        val additionalStatements: AdditionalStatements = typeInspectionResult.typedEntities(key) match {
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
                /*
                case externalIri: IriRef =>
                    // no additional statements needed (no type information available)
                    // externalIri could be rdf:type for instance
                    AdditionalStatements() */

                case other => throw SparqlSearchException(s"predicate (property) must either be a variable or an Iri, not $other")
            }

            val additionalStatementsForObj: AdditionalStatements = statementP.obj match {
                case variableObj: QueryVariable =>
                    AdditionalStatements()

                // TODO: figure out another way to deal with the fact that the type inspector uses knora-api simple IRIs.
                // Two options:
                // 1. convert the IRIs from the type inspector to internal ones
                // 2. add a flag in IriRef to indicate whether it's internal

                /*
                case internalEntityIriObj: IriRef =>
                    AdditionalStatements()
                */

                case iriObj: IriRef =>
                    val key = TypeableIri(iriObj.iri)

                    if (typeInspectionResult.typedEntities -- typeInfoKeysProcessedInStatements contains key) {

                        val additionalStatements: AdditionalStatements = typeInspectionResult.typedEntities(key) match {
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
                    // TODO: figure out another way to deal with the fact that the type inspector uses knora-api simple IRIs.
                    // Two options:
                    // 1. convert the IRIs from the type inspector to internal ones
                    // 2. add a flag in IriRef to indicate whether it's internal

                    case internalIriPred: IriRef =>

                        // convert this Iri to knora-api simple since the type inspector uses knora-api simple Iris
                        val key = TypeableIri(InputValidation.internalEntityIriToSimpleApiV2EntityIri(internalIriPred.iri, () => throw AssertionException(s"${internalIriPred.iri} could not be converted back to knora-api simple format")))

                        typeInspectionResult.typedEntities.get(key) match {
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

                    /*
                    case iriPred: IriRef =>
                        // preserve original statement
                        ConvertedStatement(originalStatement = Some(statementP), additionalStatements = additionalStatementsAll)
                    */

                    case varPred: QueryVariable =>

                        val key = TypeableVariable(varPred.variableName)

                        typeInspectionResult.typedEntities.get(key) match {
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
          * Processes a given Filter expression.
          * Given Filter expression may have to be converted to more complex expressions (e.g., in case of a date).
          *
          * @param acc              the query patterns to add to.
          * @param index            the current index used to create unqiue varibale names.
          * @param filterExpression the Filter expression to be processed.
          * @return a [[ConvertedQueryPatterns]] containing the processed Filter expression and additional statements.
          */
        def processFilterPattern(typeInspectionResult: TypeInspectionResult, acc: ConvertedQueryPatterns, index: Int, filterExpression: FilterExpression): ConvertedQueryPatterns = {
            filterExpression match {
                case filterCompare: CompareExpression =>

                    // TODO: check validity of comparison operator (see extended search v1) -> make it an enum

                    filterCompare.leftArg match {
                        case searchVar: QueryVariable =>

                            val objectType: SparqlEntityTypeInfo = typeInspectionResult.typedEntities(TypeableVariable(searchVar.variableName))

                            objectType match {
                                case nonPropTypeInfo: NonPropertyTypeInfo =>

                                    nonPropTypeInfo.typeIri match {
                                        case OntologyConstants.Xsd.String =>
                                            val statement = StatementPattern(subj = searchVar, pred = IriRef(OntologyConstants.KnoraBase.ValueHasString), obj = QueryVariable("stringVar" + index)).toKnoraExplicit
                                            val filterPattern = FilterPattern(CompareExpression(leftArg = QueryVariable("stringVar" + index), operator = filterCompare.operator, rightArg = filterCompare.rightArg))

                                            ConvertedQueryPatterns(originalPatterns = acc.originalPatterns :+ filterPattern, additionalPatterns = acc.additionalPatterns :+ statement, typeInfoKeysProcessedInStatements = acc.typeInfoKeysProcessedInStatements)

                                        case OntologyConstants.Xsd.Integer =>
                                            val statement = StatementPattern(subj = searchVar, pred = IriRef(OntologyConstants.KnoraBase.ValueHasInteger), obj = QueryVariable("intVar" + index)).toKnoraExplicit
                                            val filterPattern = FilterPattern(CompareExpression(leftArg = QueryVariable("intVar" + index), operator = filterCompare.operator, rightArg = filterCompare.rightArg))

                                            ConvertedQueryPatterns(originalPatterns = acc.originalPatterns :+ filterPattern, additionalPatterns = acc.additionalPatterns :+ statement, typeInfoKeysProcessedInStatements = acc.typeInfoKeysProcessedInStatements)

                                        case OntologyConstants.KnoraApiV2Simplified.Date =>

                                            // expect rightArg to be a string literal
                                            val dateStringLiteral = filterCompare.rightArg match {
                                                case stringLiteral: XsdLiteral if stringLiteral.datatype == OntologyConstants.Xsd.String =>
                                                    stringLiteral.value
                                                case other => throw SparqlSearchException(s"$other is expected to be a string literal")
                                            }

                                            val dateStr = InputValidation.toDate(dateStringLiteral, () => throw BadRequestException(s"$dateStringLiteral is not a valid date string"))

                                            val date: JulianDayNumberValueV1 = DateUtilV1.createJDNValueV1FromDateString(dateStr)

                                            filterCompare.operator match {
                                                case "=" =>

                                                    // overlap in considered as equality
                                                    val filterPattern = FilterPattern(AndExpression(CompareExpression(XsdLiteral(date.dateval1.toString, OntologyConstants.Xsd.Integer), "<=", QueryVariable("dateValEnd" + index)), CompareExpression(XsdLiteral(date.dateval2.toString, OntologyConstants.Xsd.Integer), ">=", QueryVariable("dateValStart" + index))))

                                                    val addStatements = Vector(StatementPattern(subj = searchVar, pred = IriRef(OntologyConstants.KnoraBase.ValueHasStartJDN), obj = QueryVariable("dateValStart" + index)).toKnoraExplicit,
                                                        StatementPattern(subj = searchVar, pred = IriRef(OntologyConstants.KnoraBase.ValueHasEndJDN), obj = QueryVariable("dateValEnd" + index)).toKnoraExplicit)

                                                    ConvertedQueryPatterns(originalPatterns = acc.originalPatterns :+ filterPattern, additionalPatterns = acc.additionalPatterns ++ addStatements, typeInfoKeysProcessedInStatements = acc.typeInfoKeysProcessedInStatements)

                                                case "!=" =>
                                                    // no overlap in considered as inequality
                                                    val filterPattern = FilterPattern(OrExpression(CompareExpression(XsdLiteral(date.dateval1.toString, OntologyConstants.Xsd.Integer), ">", QueryVariable("dateValEnd" + index)), CompareExpression(XsdLiteral(date.dateval2.toString, OntologyConstants.Xsd.Integer), "<", QueryVariable("dateValStart" + index))))

                                                    val addStatements = Vector(StatementPattern(subj = searchVar, pred = IriRef(OntologyConstants.KnoraBase.ValueHasStartJDN), obj = QueryVariable("dateValStart" + index)).toKnoraExplicit,
                                                        StatementPattern(subj = searchVar, pred = IriRef(OntologyConstants.KnoraBase.ValueHasEndJDN), obj = QueryVariable("dateValEnd" + index)).toKnoraExplicit)

                                                    ConvertedQueryPatterns(originalPatterns = acc.originalPatterns :+ filterPattern, additionalPatterns = acc.additionalPatterns ++ addStatements, typeInfoKeysProcessedInStatements = acc.typeInfoKeysProcessedInStatements)

                                                case "<" =>
                                                    // no overlap in considered as inequality
                                                    val filterPattern = FilterPattern(CompareExpression(QueryVariable("dateValEnd" + index), "<", XsdLiteral(date.dateval1.toString, OntologyConstants.Xsd.Integer)))

                                                    val addStatements = Vector(StatementPattern(subj = searchVar, pred = IriRef(OntologyConstants.KnoraBase.ValueHasEndJDN), obj = QueryVariable("dateValEnd" + index)).toKnoraExplicit)

                                                    ConvertedQueryPatterns(originalPatterns = acc.originalPatterns :+ filterPattern, additionalPatterns = acc.additionalPatterns ++ addStatements, typeInfoKeysProcessedInStatements = acc.typeInfoKeysProcessedInStatements)

                                                case ">=" =>
                                                    // no overlap in considered as inequality
                                                    val filterPattern = FilterPattern(CompareExpression(QueryVariable("dateValEnd" + index), ">=", XsdLiteral(date.dateval1.toString, OntologyConstants.Xsd.Integer)))

                                                    val addStatements = Vector(StatementPattern(subj = searchVar, pred = IriRef(OntologyConstants.KnoraBase.ValueHasEndJDN), obj = QueryVariable("dateValEnd" + index)).toKnoraExplicit)

                                                    ConvertedQueryPatterns(originalPatterns = acc.originalPatterns :+ filterPattern, additionalPatterns = acc.additionalPatterns ++ addStatements, typeInfoKeysProcessedInStatements = acc.typeInfoKeysProcessedInStatements)

                                                case ">" =>
                                                    // no overlap in considered as inequality
                                                    val filterPattern = FilterPattern(CompareExpression(QueryVariable("dateValStart" + index), ">", XsdLiteral(date.dateval2.toString, OntologyConstants.Xsd.Integer)))

                                                    val addStatements = Vector(StatementPattern(subj = searchVar, pred = IriRef(OntologyConstants.KnoraBase.ValueHasStartJDN), obj = QueryVariable("dateValStart" + index)).toKnoraExplicit)

                                                    ConvertedQueryPatterns(originalPatterns = acc.originalPatterns :+ filterPattern, additionalPatterns = acc.additionalPatterns ++ addStatements, typeInfoKeysProcessedInStatements = acc.typeInfoKeysProcessedInStatements)


                                                case "<=" =>
                                                    // no overlap in considered as inequality
                                                    val filterPattern = FilterPattern(CompareExpression(QueryVariable("dateValStart" + index), "<=", XsdLiteral(date.dateval2.toString, OntologyConstants.Xsd.Integer)))

                                                    val addStatements = Vector(StatementPattern(subj = searchVar, pred = IriRef(OntologyConstants.KnoraBase.ValueHasStartJDN), obj = QueryVariable("dateValStart" + index)).toKnoraExplicit)

                                                    ConvertedQueryPatterns(originalPatterns = acc.originalPatterns :+ filterPattern, additionalPatterns = acc.additionalPatterns ++ addStatements, typeInfoKeysProcessedInStatements = acc.typeInfoKeysProcessedInStatements)


                                                case other => throw SparqlSearchException(s"operator not implemented for date filter: $other")
                                            }

                                        case OntologyConstants.Xsd.Decimal =>
                                            val statement = StatementPattern(subj = searchVar, pred = IriRef(OntologyConstants.KnoraBase.ValueHasDecimal), obj = QueryVariable("decimalVar" + index)).toKnoraExplicit
                                            val filterPattern = FilterPattern(CompareExpression(leftArg = QueryVariable("decimalVar" + index), operator = filterCompare.operator, rightArg = filterCompare.rightArg))

                                            ConvertedQueryPatterns(originalPatterns = acc.originalPatterns :+ filterPattern, additionalPatterns = acc.additionalPatterns :+ statement, typeInfoKeysProcessedInStatements = acc.typeInfoKeysProcessedInStatements)

                                        case OntologyConstants.Xsd.Boolean =>
                                            val statement = StatementPattern(subj = searchVar, pred = IriRef(OntologyConstants.KnoraBase.ValueHasBoolean), obj = QueryVariable("booleanVar" + index)).toKnoraExplicit
                                            val filterPattern = FilterPattern(CompareExpression(leftArg = QueryVariable("booleanVar" + index), operator = filterCompare.operator, rightArg = filterCompare.rightArg))

                                            ConvertedQueryPatterns(originalPatterns = acc.originalPatterns :+ filterPattern, additionalPatterns = acc.additionalPatterns :+ statement, typeInfoKeysProcessedInStatements = acc.typeInfoKeysProcessedInStatements)


                                        case otherType =>
                                            throw SparqlSearchException(s"type not implemented yet for filter: $otherType")
                                    }


                                case propType: PropertyTypeInfo =>
                                    ConvertedQueryPatterns(originalPatterns = acc.originalPatterns ++ Seq(FilterPattern(filterExpression)), additionalPatterns = acc.additionalPatterns, typeInfoKeysProcessedInStatements = acc.typeInfoKeysProcessedInStatements)
                            }

                        case nonVariable =>
                            throw SparqlSearchException(s"expected a variable as the left argument of a Filter expression, but $nonVariable given")
                    }


                case filterOr: OrExpression =>

                    val filterPatternsLeft: ConvertedQueryPatterns = processFilterPattern(typeInspectionResult, ConvertedQueryPatterns(originalPatterns = Vector.empty[QueryPattern], additionalPatterns = Vector.empty[StatementPattern], typeInfoKeysProcessedInStatements = Set.empty[TypeableEntity]), index, filterOr.leftArg)
                    val filterPatternsRight: ConvertedQueryPatterns = processFilterPattern(typeInspectionResult, ConvertedQueryPatterns(originalPatterns = Vector.empty[QueryPattern], additionalPatterns = Vector.empty[StatementPattern], typeInfoKeysProcessedInStatements = Set.empty[TypeableEntity]), index, filterOr.rightArg)

                    val leftArg = filterPatternsLeft.originalPatterns match {
                        case queryP: Vector[QueryPattern] if queryP.size == 1 =>
                            queryP.head match {
                                case filterP: FilterPattern =>
                                    filterP.expression
                                case _ => throw SparqlSearchException("Could not process filter Or expression: leftArg is not a FilterPattern")
                            }

                        case _ => throw SparqlSearchException("Could not process filter Or expression: leftArg is not a Vector of type QueryPattern with size 1")
                    }

                    val rightArg = filterPatternsRight.originalPatterns match {
                        case queryP: Vector[QueryPattern] if queryP.size == 1 =>
                            queryP.head match {
                                case filterP: FilterPattern =>
                                    filterP.expression
                                case _ => throw SparqlSearchException("Could not process filter Or expression: rightArg is not a FilterPattern")
                            }

                        case _ => throw SparqlSearchException("Could not process filter Or expression: rightArg is not a Vector of type QueryPattern with size 1")
                    }

                    // recreate the Or expression and also return statements that were additionally created
                    val orExpression = OrExpression(leftArg = leftArg, rightArg = rightArg)

                    ConvertedQueryPatterns(originalPatterns = acc.originalPatterns ++ Seq(FilterPattern(orExpression)), additionalPatterns = acc.additionalPatterns ++ filterPatternsLeft.additionalPatterns ++ filterPatternsRight.additionalPatterns, typeInfoKeysProcessedInStatements = acc.typeInfoKeysProcessedInStatements)

                case filterAnd: AndExpression =>

                    val filterPatternsLeft: ConvertedQueryPatterns = processFilterPattern(typeInspectionResult, ConvertedQueryPatterns(originalPatterns = Vector.empty[QueryPattern], additionalPatterns = Vector.empty[StatementPattern], typeInfoKeysProcessedInStatements = Set.empty[TypeableEntity]), index, filterAnd.leftArg)
                    val filterPatternsRight: ConvertedQueryPatterns = processFilterPattern(typeInspectionResult, ConvertedQueryPatterns(originalPatterns = Vector.empty[QueryPattern], additionalPatterns = Vector.empty[StatementPattern], typeInfoKeysProcessedInStatements = Set.empty[TypeableEntity]), index, filterAnd.rightArg)

                    val leftArg = filterPatternsLeft.originalPatterns match {
                        case queryP: Vector[QueryPattern] if queryP.size == 1 =>
                            queryP.head match {
                                case filterP: FilterPattern =>
                                    filterP.expression
                                case _ => throw SparqlSearchException("Could not process filter And expression: leftArg is not a FilterPattern")
                            }

                        case _ => throw SparqlSearchException("Could not process filter And expression: leftArg is not a Vector of type QueryPattern with size 1")
                    }

                    val rightArg = filterPatternsRight.originalPatterns match {
                        case queryP: Vector[QueryPattern] if queryP.size == 1 =>
                            queryP.head match {
                                case filterP: FilterPattern =>
                                    filterP.expression
                                case _ => throw SparqlSearchException("Could not process filter And expression: rightArg is not a FilterPattern")
                            }

                        case _ => throw SparqlSearchException("Could not process filter And expression: rightArg is not a Vector of type QueryPattern with size 1")
                    }

                    // recreate the And expression and also return statements that were additionally created
                    val andExpression = AndExpression(leftArg = leftArg, rightArg = rightArg)

                    ConvertedQueryPatterns(originalPatterns = acc.originalPatterns ++ Seq(FilterPattern(andExpression)), additionalPatterns = acc.additionalPatterns ++ filterPatternsLeft.additionalPatterns ++ filterPatternsRight.additionalPatterns, typeInfoKeysProcessedInStatements = acc.typeInfoKeysProcessedInStatements)


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
        case class ConvertedQueryPatterns(originalPatterns: Vector[QueryPattern], additionalPatterns: Vector[StatementPattern], typeInfoKeysProcessedInStatements: Set[TypeableEntity])

        // TODO: remove this method, use ConstructQueryTransformer to do the traversal, and put the pattern transformation in NonTriplestoreSpecificQueryPatternTransformer.
        def convertQueryPatterns(typeInspectionResult: TypeInspectionResult, patterns: Seq[QueryPattern]): ConvertedQueryPatterns = {
            patterns.zipWithIndex.foldLeft(ConvertedQueryPatterns(originalPatterns = Vector.empty[QueryPattern], additionalPatterns = Vector.empty[StatementPattern], typeInfoKeysProcessedInStatements = Set.empty[TypeableEntity])) {

                case (acc: ConvertedQueryPatterns, (pattern: QueryPattern, index: Int)) =>

                    pattern match {
                        case statementP: StatementPattern =>

                            val convertedStatement: ConvertedStatement = addTypeInformationStatementsToStatement(typeInspectionResult, statementP, index, acc.typeInfoKeysProcessedInStatements)

                            ConvertedQueryPatterns(originalPatterns = acc.originalPatterns ++ convertedStatement.originalStatement ++ convertedStatement.additionalStatements.additionalFilterPatterns, additionalPatterns = acc.additionalPatterns ++ convertedStatement.additionalStatements.additionalStatements, typeInfoKeysProcessedInStatements = acc.typeInfoKeysProcessedInStatements ++ convertedStatement.additionalStatements.typeInfoKeysProcessed)


                        case optionalP: OptionalPattern =>
                            val optionalPatterns = Seq(OptionalPattern(convertQueryPatterns(typeInspectionResult, optionalP.patterns).originalPatterns))

                            ConvertedQueryPatterns(originalPatterns = acc.originalPatterns ++ optionalPatterns, additionalPatterns = acc.additionalPatterns, typeInfoKeysProcessedInStatements = acc.typeInfoKeysProcessedInStatements)

                        case unionP: UnionPattern =>
                            val blocks = unionP.blocks.map {
                                blockPatterns: Seq[QueryPattern] => convertQueryPatterns(typeInspectionResult, blockPatterns).originalPatterns
                            }

                            ConvertedQueryPatterns(originalPatterns = acc.originalPatterns ++ Seq(UnionPattern(blocks)), additionalPatterns = acc.additionalPatterns, typeInfoKeysProcessedInStatements = acc.typeInfoKeysProcessedInStatements)

                        case filterP: FilterPattern =>

                            processFilterPattern(typeInspectionResult, acc, index, filterP.expression)

                    }


            }
        }

        /**
          * Transforms non-triplestore-specific query patterns to GraphDB-specific ones.
          */
        class GraphDBQueryPatternTransformer extends QueryPatternTransformer {
            def transformStatementInConstruct(statementPattern: StatementPattern): Seq[StatementPattern] = Seq(statementPattern)

            def transformStatementInWhere(statementPattern: StatementPattern): Seq[QueryPattern] = {
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

            def transformStatementInWhere(statementPattern: StatementPattern): Seq[QueryPattern] = {
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