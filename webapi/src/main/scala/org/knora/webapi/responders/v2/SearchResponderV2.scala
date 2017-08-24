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

        if (apiSchema != ApiV2Schema.SIMPLE) {
            throw SparqlSearchException("Only api v2 simple is supported in v2 extended search")
        }

        /**
          * A [[QueryPatternTransformer]] that preprocesses the input CONSTRUCT query by converting external IRIs to internal ones
          * and disabling inference for individual statements as necessary.
          */
        class Preprocessor extends QueryPatternTransformer {
            def transformStatementInConstruct(statementPattern: StatementPattern): Seq[StatementPattern] = Seq(preprocessStatementPattern(statementPattern = statementPattern))

            def transformStatementInWhere(statementPattern: StatementPattern): Seq[QueryPattern] = Seq(preprocessStatementPattern(statementPattern = statementPattern))

            def transformFilter(filterPattern: FilterPattern): Seq[QueryPattern] = Seq(FilterPattern(preprocessFilterExpression(filterPattern.expression)))

            /**
              * Preprocesses a [[Expression]] by converting external IRIs to internal ones.
              *
              * @param filterExpression a filter expression.
              * @return the preprocessed expression.
              */
            def preprocessFilterExpression(filterExpression: Expression): Expression = {
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

            // a Set containing all `TypeableEntity` (keys of `typeInspectionResult`) that have already been processed
            // in order to prevent duplicates
            val processedTypeInformationKeysWhereClause = mutable.Set.empty[TypeableEntity]

            // a Map containing all additional StatementPattern that have been created based on the type information, FilterPattern excluded
            // will be integrated in the Construct clause
            val additionalStatementsCreatedForEntities = mutable.Map.empty[Entity, Seq[StatementPattern]]

            // a Map containing all the statements of the Where clause (possibly converted) and additional statements created for them
            // will be integrated in the Construct clause
            val convertedStatementsCreatedForWholeStatements = mutable.Map.empty[StatementPattern, Seq[StatementPattern]]

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

                    case iriRef: IriRef =>
                        // type info keys are external api v2 simple Iris,
                        // so convert this internal Iri to an external api v2 simple if possible
                        val externalIri = if (InputValidation.isInternalEntityIri(iriRef.iri)) {
                            InputValidation.internalEntityIriToSimpleApiV2EntityIri(iriRef.iri, () => throw BadRequestException(s"${iriRef.iri} is not a valid internal knora-api entity Iri"))
                        } else {
                            iriRef.iri
                        }

                        Some(TypeableIri(externalIri))

                    case _ => None
                }

            }

            /**
              * Create a syntactically valid variable base name, based on the given entity.
              *
              * @param entity the entity to be used to create a base name for a variable.
              * @return a base name for a variable.
              */
            def escapeEntityForVariable(entity: Entity): String = {
                val entityStr = entity match {
                    case QueryVariable(varName) => varName
                    case IriRef(iriLiteral) => iriLiteral
                    case _ => throw SparqlSearchException(s"A unique variable could not be made for $entity")
                }

                entityStr.replaceAll("[:/.#-]", "") // TODO: check if this is complete
            }

            /**
              * Create a unique variable from the given entity and a suffix.
              *
              * @param base   the entity to use to create the variable base name.
              * @param suffix the suffix to append to the base name.
              * @return a unique variable.
              */
            def createUniqueVariableFromEntity(base: Entity, suffix: String): QueryVariable = {
                QueryVariable(escapeEntityForVariable(base) + "__" + suffix)
            }

            /**
              * Create a unique variable from a whole statement.
              *
              * @param baseStatement the statement to be used to create the variable base name.
              * @param suffix        the suffix to be appended to the base name.
              * @return a unique variable.
              */
            def createUniqueVariableFromStatement(baseStatement: StatementPattern, suffix: String): QueryVariable = {
                QueryVariable(escapeEntityForVariable(baseStatement.subj) + "__" + escapeEntityForVariable(baseStatement.pred) + "__" + escapeEntityForVariable(baseStatement.obj) + "__" + suffix)
            }

            /**
              * Create additional statements for the given non property type information.
              *
              * @param nonPropertyTypeInfo type information about the statement provided by the user.
              * @param inputEntity         the entity to create additional statements for.
              * @return a sequence of [[QueryPattern]] representing additional statements created from the given type information.
              */
            def createAdditionalStatementsForNonPropertyType(nonPropertyTypeInfo: NonPropertyTypeInfo, inputEntity: Entity): Seq[QueryPattern] = {

                val typeIriInternal = if (InputValidation.isKnoraApiEntityIri(nonPropertyTypeInfo.typeIri)) {
                    InputValidation.externalIriToInternalIri(nonPropertyTypeInfo.typeIri, () => throw BadRequestException(s"${nonPropertyTypeInfo.typeIri} is not a valid external knora-api entity Iri"))
                } else {
                    nonPropertyTypeInfo.typeIri
                }

                if (typeIriInternal == OntologyConstants.KnoraBase.Resource) {

                    // inputEntity is either source or target of a linking property
                    // create additional statements in order to query permissions and other information for a resource

                    Seq(
                        StatementPattern(subj = inputEntity, pred = IriRef(OntologyConstants.Rdf.Type), obj = IriRef(OntologyConstants.KnoraBase.Resource), None),
                        StatementPattern(subj = inputEntity, pred = IriRef(OntologyConstants.KnoraBase.IsDeleted), obj = XsdLiteral(value = "false", datatype = OntologyConstants.Xsd.Boolean)).toKnoraExplicit,
                        StatementPattern(subj = inputEntity, pred = IriRef(OntologyConstants.Rdfs.Label), obj = createUniqueVariableFromEntity(inputEntity, "ResourceLabel")).toKnoraExplicit,
                        StatementPattern(subj = inputEntity, pred = IriRef(OntologyConstants.Rdf.Type), obj = createUniqueVariableFromEntity(inputEntity, "ResourceType")).toKnoraExplicit,
                        StatementPattern(subj = inputEntity, pred = IriRef(OntologyConstants.KnoraBase.AttachedToUser), obj = createUniqueVariableFromEntity(inputEntity, "ResourceCreator")).toKnoraExplicit,
                        StatementPattern(subj = inputEntity, pred = IriRef(OntologyConstants.KnoraBase.HasPermissions), obj = createUniqueVariableFromEntity(inputEntity, "ResourcePermissions")).toKnoraExplicit,
                        StatementPattern(subj = inputEntity, pred = IriRef(OntologyConstants.KnoraBase.AttachedToProject), obj = createUniqueVariableFromEntity(inputEntity, "ResourceProject")).toKnoraExplicit
                    )
                } else {
                    // inputEntity is target of a value property
                    // properties are handled by `convertStatementForPropertyType`, no processing needed here

                    Seq.empty[QueryPattern]
                }
            }

            /**
              * Create additional statements for the given property type information.
              *
              * @param propertyTypeInfo type information about the statement provided by the user.
              * @param statementPattern statement provided by the user.
              * @return a sequence of [[QueryPattern]] representing additional statements created from the given type information.
              */
            def convertStatementForPropertyType(propertyTypeInfo: PropertyTypeInfo, statementPattern: StatementPattern): Seq[QueryPattern] = {

                // decide whether to keep the originally given statement or not
                // if pred is a valueProp and the simple api is used, do not return the original statement
                // it had to be converted to comply with Knora's value object structure

                // convert the type information into an internal Knora Iri if possible
                val objectIri = if (InputValidation.isKnoraApiEntityIri(propertyTypeInfo.objectTypeIri)) {
                    InputValidation.externalIriToInternalIri(propertyTypeInfo.objectTypeIri, () => throw BadRequestException(s"${propertyTypeInfo.objectTypeIri} is not a valid external knora-api entity Iri"))
                } else {
                    propertyTypeInfo.objectTypeIri
                }

                objectIri match {
                    case OntologyConstants.KnoraBase.Resource =>

                        // the given statement pattern's object is of type resource
                        // this means that the predicate of the statement pattern is a linking property
                        // create statements in order to query the link value describing the link in question

                        // variable referring to the link's value object (reification)
                        val linkValueVar = createUniqueVariableFromStatement(statementPattern, "linkObj") // A variable representing the reification
                    val linkPropVar = createUniqueVariableFromStatement(statementPattern, "linkProp") // A variable representing the explicit property that actually points to the target resource
                    val linkValuePropVar = createUniqueVariableFromStatement(statementPattern, "linkValueProp") // A variable representing the explicit property that actually points to the reification
                    val linkValuePredVar = createUniqueVariableFromStatement(statementPattern, "linkValuePred") // A variable representing a predicate of the reification
                    val linkValueObjVar = createUniqueVariableFromStatement(statementPattern, "linkValueObj") // A variable representing a predicate of the reification

                        Seq(statementPattern, // keep the original statement pointing from the source to the target resource, using inference
                            StatementPattern(subj = statementPattern.subj, pred = linkPropVar, obj = statementPattern.obj).toKnoraExplicit, // find out what the actual link property is
                            StatementPattern(subj = statementPattern.subj, pred = IriRef(OntologyConstants.KnoraBase.HasValue), obj = linkValueVar), // include knora-base:hasValue pointing to the link value, because the construct clause needs it
                            StatementPattern(subj = statementPattern.subj, pred = linkValuePropVar, obj = linkValueVar).toKnoraExplicit, // find out what the actual link value property is
                            StatementPattern(subj = linkValueVar, pred = IriRef(OntologyConstants.KnoraBase.IsDeleted), obj = XsdLiteral(value = "false", datatype = OntologyConstants.Xsd.Boolean)).toKnoraExplicit, // ensure the link value isn't deleted
                            StatementPattern(subj = linkValueVar, pred = IriRef(OntologyConstants.Rdf.Type), obj = IriRef(OntologyConstants.KnoraBase.LinkValue)).toKnoraExplicit, // it's a link value
                            StatementPattern(subj = linkValueVar, pred = IriRef(OntologyConstants.Rdf.Subject), obj = statementPattern.subj).toKnoraExplicit, // the rdf:subject of the link value must be the source resource
                            StatementPattern(subj = linkValueVar, pred = IriRef(OntologyConstants.Rdf.Predicate), obj = linkPropVar).toKnoraExplicit, // the rdf:predicate of the link value must be the link property
                            StatementPattern(subj = linkValueVar, pred = IriRef(OntologyConstants.Rdf.Object), obj = statementPattern.obj).toKnoraExplicit, // the rdf:object of the link value must be the target resource
                            StatementPattern(subj = linkValueVar, pred = linkValuePredVar, obj = linkValueObjVar).toKnoraExplicit // get any other statements about the link value
                        )

                    case OntologyConstants.Xsd.Integer =>

                        // the given statement pattern's object is of type xsd:integer
                        // this means that the predicate of the statement pattern is a value property
                        // the original statement is not included because it represents the value as a literal (in Knora, it is an object)

                        // variable referring to the link's value object (reification)
                        val intPropVar = createUniqueVariableFromStatement(statementPattern, "intProp") // A variable representing the property pointing to the value object
                    val integerValuePred = createUniqueVariableFromStatement(statementPattern, "intValuePred") // A variable representing a predicates of the integer value object
                    val intValueObj = createUniqueVariableFromStatement(statementPattern, "intValueObj") // A variable representing a predicate of the reification

                        // check if the statement pattern's object is a literal or a variable
                        statementPattern.obj match {

                            // create statements in order to query the integer value object
                            // AND match the given literal value with the integer value object's `valueHasInteger`

                            case integerLiteral: XsdLiteral =>

                                Seq(
                                    StatementPattern(subj = statementPattern.subj, pred = IriRef(OntologyConstants.KnoraBase.HasValue), obj = statementPattern.obj), // include knora-base:hasValue pointing to the link value, because the construct clause needs it
                                    StatementPattern(subj = statementPattern.subj, pred = statementPattern.pred, obj = statementPattern.obj, includeInConstructClause = false), // do not include the originally given  property in the Construct clause because inference is used
                                    StatementPattern(subj = statementPattern.subj, pred = intPropVar, obj = statementPattern.obj).toKnoraExplicit, // the actually matching property
                                    StatementPattern(subj = statementPattern.obj, pred = IriRef(OntologyConstants.KnoraBase.IsDeleted), obj = XsdLiteral(value = "false", datatype = OntologyConstants.Xsd.Boolean)).toKnoraExplicit,
                                    StatementPattern(subj = statementPattern.obj, pred = IriRef(OntologyConstants.Rdf.Type), obj = IriRef(OntologyConstants.KnoraBase.IntValue)).toKnoraExplicit,
                                    StatementPattern(subj = statementPattern.obj, pred = integerValuePred, obj = intValueObj).toKnoraExplicit,
                                    StatementPattern(subj = statementPattern.obj, pred = IriRef(OntologyConstants.KnoraBase.ValueHasInteger), obj = statementPattern.obj).toKnoraExplicit
                                )

                            case integerVar: QueryVariable =>

                                // just create the statements to query the integer value object, possibly a Filter statement exists to restrict the possible integer values
                                Seq(
                                    StatementPattern(subj = statementPattern.subj, pred = IriRef(OntologyConstants.KnoraBase.HasValue), obj = statementPattern.obj), // include knora-base:hasValue pointing to the link value, because the construct clause needs it
                                    StatementPattern(subj = statementPattern.subj, pred = statementPattern.pred, obj = statementPattern.obj, includeInConstructClause = false), // do not include the originally given property in the Construct clause because inference is used
                                    StatementPattern(subj = statementPattern.subj, pred = intPropVar, obj = statementPattern.obj).toKnoraExplicit, // the actually matching property
                                    StatementPattern(subj = statementPattern.obj, pred = IriRef(OntologyConstants.KnoraBase.IsDeleted), obj = XsdLiteral(value = "false", datatype = OntologyConstants.Xsd.Boolean)).toKnoraExplicit,
                                    StatementPattern(subj = statementPattern.obj, pred = IriRef(OntologyConstants.Rdf.Type), obj = IriRef(OntologyConstants.KnoraBase.IntValue)).toKnoraExplicit,
                                    StatementPattern(subj = statementPattern.obj, pred = integerValuePred, obj = intValueObj).toKnoraExplicit
                                )

                            case other => throw SparqlSearchException("Not supported as an object of type integer: $other")

                        }

                    case _ => throw NotImplementedException(s"property type $objectIri not implemented yet.")

                }

            }

            /**
              * Process a given statement pattern based on type information.
              * This function is used for the Construct and Where clause of a user provided Sparql query.
              *
              * @param statementPattern the statement to be processed.
              * @return a sequence of [[StatementPattern]].
              */
            def processStatementPatternFromWhereClause(statementPattern: StatementPattern): Seq[QueryPattern] = {
                // look at the statement's subject, predicate, and object and generate additional statements if needed based on the given type information.
                // transform the originally given statement if necessary when processing the predicate

                // create `TypeableEntity` (keys in `typeInspectionResult`) from the given statement's elements
                val subjTypeInfoKey: Option[TypeableEntity] = toTypeableEntityKey(statementPattern.subj)

                val predTypeInfoKey: Option[TypeableEntity] = toTypeableEntityKey(statementPattern.pred)

                val objTypeInfoKey: Option[TypeableEntity] = toTypeableEntityKey(statementPattern.obj)

                // check if there exists type information for the given statement's subject
                val additionalStatementsForSubj: Seq[QueryPattern] = if (subjTypeInfoKey.nonEmpty && (typeInspectionResult.typedEntities -- processedTypeInformationKeysWhereClause contains subjTypeInfoKey.get)) {
                    // process type information for the subject into additional statements

                    // add TypeableEntity (keys of `typeInspectionResult`) for subject in order to prevent duplicates
                    processedTypeInformationKeysWhereClause += subjTypeInfoKey.get

                    val nonPropTypeInfo: NonPropertyTypeInfo = typeInspectionResult.typedEntities(subjTypeInfoKey.get) match {
                        case nonPropInfo: NonPropertyTypeInfo => nonPropInfo

                        case _ => throw AssertionException(s"NonPropertyTypeInfo expected for ${subjTypeInfoKey.get}")
                    }

                    val additionalStatements = createAdditionalStatementsForNonPropertyType(
                        nonPropertyTypeInfo = nonPropTypeInfo,
                        inputEntity = statementPattern.subj
                    )

                    val existingAdditionalStatementsCreated: Seq[StatementPattern] = additionalStatementsCreatedForEntities.get(statementPattern.subj).toSeq.flatten

                    val newAdditionalStatementPatterns: Seq[StatementPattern] = additionalStatements.collect {
                        // only include StatementPattern
                        case statementP: StatementPattern => statementP
                    }

                    additionalStatementsCreatedForEntities += statementPattern.subj -> (existingAdditionalStatementsCreated ++ newAdditionalStatementPatterns)

                    additionalStatements
                } else {
                    Seq.empty[QueryPattern]
                }

                // check if there exists type information for the given statement's object
                val additionalStatementsForObj: Seq[QueryPattern] = if (objTypeInfoKey.nonEmpty && (typeInspectionResult.typedEntities -- processedTypeInformationKeysWhereClause contains objTypeInfoKey.get)) {
                    // process type information for the object into additional statements

                    // add TypeableEntity (keys of `typeInspectionResult`) for object in order to prevent duplicates
                    processedTypeInformationKeysWhereClause += objTypeInfoKey.get

                    val nonPropTypeInfo: NonPropertyTypeInfo = typeInspectionResult.typedEntities(objTypeInfoKey.get) match {
                        case nonPropInfo: NonPropertyTypeInfo => nonPropInfo

                        case _ => throw AssertionException(s"NonPropertyTypeInfo expected for ${objTypeInfoKey.get}")
                    }

                    val additionalStatements = createAdditionalStatementsForNonPropertyType(
                        nonPropertyTypeInfo = nonPropTypeInfo,
                        inputEntity = statementPattern.obj
                    )

                    val existingAdditionalStatementsCreated: Seq[StatementPattern] = additionalStatementsCreatedForEntities.get(statementPattern.obj).toSeq.flatten

                    val newAdditionalStatementPatterns: Seq[StatementPattern] = additionalStatements.collect {
                        // only include StatementPattern
                        case statementP: StatementPattern => statementP
                    }

                    additionalStatementsCreatedForEntities += statementPattern.obj -> (existingAdditionalStatementsCreated ++ newAdditionalStatementPatterns)

                    additionalStatements
                } else {
                    Seq.empty[QueryPattern]
                }

                // Add additional statements based on the whole input statement, e.g. to deal with the value object or the link value, and transform the original statement.
                val additionalStatementsForWholeStatement: Seq[QueryPattern] = if (predTypeInfoKey.nonEmpty && (typeInspectionResult.typedEntities contains predTypeInfoKey.get)) {
                    // process type information for the predicate into additional statements

                    val propTypeInfo = typeInspectionResult.typedEntities(predTypeInfoKey.get) match {
                        case propInfo: PropertyTypeInfo => propInfo

                        case _ => throw AssertionException(s"PropertyTypeInfo expected for ${predTypeInfoKey.get}")
                    }

                    val additionalStatements = convertStatementForPropertyType(
                        propertyTypeInfo = propTypeInfo,
                        statementPattern = statementPattern
                    )

                    val existingAdditionalStatementsCreated: Seq[StatementPattern] = convertedStatementsCreatedForWholeStatements.get(statementPattern).toSeq.flatten

                    val newAdditionalStatementPatterns: Seq[StatementPattern] = additionalStatements.collect {
                        // only include StatementPattern

                        // filter out those statements mentioning the originally given predicate (property) with inference turned on
                        // this is necessary to return the explicit property to the user
                        // the originally given property will only be needed in the Where clause and get all subproperties too
                        case statementP: StatementPattern if statementP.includeInConstructClause => statementP
                    }

                    convertedStatementsCreatedForWholeStatements += statementPattern -> (existingAdditionalStatementsCreated ++ newAdditionalStatementPatterns)

                    additionalStatements
                } else {
                    // no type information given and thus no further processing needed, just return the originally given statement (e.g., rdf:type)
                    Seq(statementPattern)
                }


                additionalStatementsForSubj ++ additionalStatementsForWholeStatement ++ additionalStatementsForObj
            }

            def transformStatementInConstruct(statementPattern: StatementPattern): Seq[StatementPattern] = {

                // check if there is an entry for the given statementPattern in additionalStatementsCreated

                val additionalStatementsForSubj = additionalStatementsCreatedForEntities.get(statementPattern.subj) match {
                    case Some(statementPatterns: Seq[StatementPattern]) =>
                        additionalStatementsCreatedForEntities -= statementPattern.subj

                        statementPatterns.map {
                            // set graph to None for Construct clause
                            additionalStatementP: StatementPattern => additionalStatementP.copy(namedGraph = None)
                        }

                    case None => Seq.empty[StatementPattern]
                }

                val additionalStatementsForObj = additionalStatementsCreatedForEntities.get(statementPattern.obj) match {
                    case Some(statementPatterns: Seq[StatementPattern]) =>
                        additionalStatementsCreatedForEntities -= statementPattern.obj

                        statementPatterns.map {
                            // set graph to None for Construct clause
                            additionalStatementP: StatementPattern => additionalStatementP.copy(namedGraph = None)
                        }

                    case None => Seq.empty[StatementPattern]
                }

                val convertedStatementsForWholeStatement = convertedStatementsCreatedForWholeStatements.get(statementPattern) match {
                    case Some(statementPatterns: Seq[StatementPattern]) =>
                        convertedStatementsCreatedForWholeStatements -= statementPattern

                        statementPatterns.map {
                            // set graph to None for Construct clause
                            additionalStatementP: StatementPattern => additionalStatementP.copy(namedGraph = None)
                        }

                    case None => Seq(statementPattern)
                }

                convertedStatementsForWholeStatement ++ additionalStatementsForSubj ++ additionalStatementsForObj
            }

            def transformStatementInWhere(statementPattern: StatementPattern): Seq[QueryPattern] = {

                processStatementPatternFromWhereClause(
                    statementPattern = statementPattern
                )

            }

            case class TransformedFilterExpression(expression: Expression, additionalStatements: Seq[StatementPattern] = Seq.empty[StatementPattern])

            def transformFilterExpression(filterExpression: Expression): TransformedFilterExpression = {

                filterExpression match {

                    case filterCompare: CompareExpression =>

                        // left argument of a CompareExpression is expected to be a QueryVariable

                        val queryVar: QueryVariable = filterCompare.leftArg match {

                            case queryVar: QueryVariable => queryVar

                            case other => throw SparqlSearchException(s"Left argument of a Filter CompareExpression is expected to be a QueryVariable, but $other is given")
                        }

                        val queryVarTypeInfoKey = toTypeableEntityKey(queryVar)

                        // get information about the queryVar's type
                        if (queryVarTypeInfoKey.nonEmpty && (typeInspectionResult.typedEntities contains queryVarTypeInfoKey.get)) {

                            val typeInfo: SparqlEntityTypeInfo = typeInspectionResult.typedEntities(queryVarTypeInfoKey.get)

                            // check if type information is about a property or a value
                            typeInfo match {

                                case propInfo: PropertyTypeInfo =>
                                    // the left arg (a variable) represents a property

                                    // get the internal objectIri of the property type info
                                    val typeIriInternal = if (InputValidation.isKnoraApiEntityIri(propInfo.objectTypeIri)) {
                                        InputValidation.externalIriToInternalIri(propInfo.objectTypeIri, () => throw BadRequestException(s"${propInfo.objectTypeIri} is not a valid external knora-api entity Iri"))
                                    } else {
                                        propInfo.objectTypeIri
                                    }

                                    typeIriInternal match {

                                        case OntologyConstants.KnoraBase.Resource =>
                                            // left arg is variable representing a property pointing to a knora-base:Resource, so the right argument must be an Iri
                                            filterCompare.rightArg match {
                                                case iriRef: IriRef =>
                                                    // make sure that the comparison operator is "="
                                                    if (filterCompare.operator != CompareExpressionOperator.EQUALS) throw SparqlSearchException(s"Comparison operator in a CompareExpression for a property type is expected to be '=', but ${filterCompare.operator}")

                                                    TransformedFilterExpression(CompareExpression(filterCompare.leftArg, filterCompare.operator, filterCompare.rightArg))

                                                case other => throw SparqlSearchException(s"right argument of CompareExpression is expected to be an Iri representing a property, but $other is given")
                                            }

                                    }

                                case nonPropInfo: NonPropertyTypeInfo =>
                                    // the left arg (a variable) represents a value

                                    nonPropInfo.typeIri match {

                                        case OntologyConstants.Xsd.Integer =>

                                            val intValHasInteger = createUniqueVariableFromEntity(queryVar, "intValueHasInteger")

                                            TransformedFilterExpression(
                                                CompareExpression(intValHasInteger, filterCompare.operator, filterCompare.rightArg),
                                                Seq(
                                                    StatementPattern(subj = queryVar, pred = IriRef(OntologyConstants.KnoraBase.ValueHasInteger), intValHasInteger)
                                                )
                                            )

                                        case other => throw NotImplementedException(s"$other not supported in FilterExpression")


                                    }


                            }


                        } else {
                            throw SparqlSearchException(s"type information about $queryVar is missing")
                        }


                    case filterOr: OrExpression =>
                        val filterExpressionLeft = transformFilterExpression(filterOr.leftArg)
                        val filterExpressionRight = transformFilterExpression(filterOr.rightArg)

                        TransformedFilterExpression(
                            OrExpression(filterExpressionLeft.expression, filterExpressionRight.expression),
                            filterExpressionLeft.additionalStatements ++ filterExpressionRight.additionalStatements
                        )



                    case filterAnd: AndExpression =>
                        val filterExpressionLeft = transformFilterExpression(filterAnd.leftArg)
                        val filterExpressionRight = transformFilterExpression(filterAnd.rightArg)

                        TransformedFilterExpression(
                            AndExpression(filterExpressionLeft.expression, filterExpressionRight.expression),
                            filterExpressionLeft.additionalStatements ++ filterExpressionRight.additionalStatements
                        )

                    case other => throw NotImplementedException(s"$other not supported as FilterExpression")
                }


            }

            def transformFilter(filterPattern: FilterPattern): Seq[QueryPattern] = {

                val filterExpression: TransformedFilterExpression = transformFilterExpression(filterPattern.expression)

                filterExpression.additionalStatements :+ FilterPattern(filterExpression.expression)
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

            /*
                        statementsInWhereClause = triplestoreSpecificQuery.whereClause.patterns.collect {
                            case statementPattern: StatementPattern => statementPattern.toKnoraExplicit
                        }

                        nonStatementsInWhereClause = triplestoreSpecificQuery.whereClause.patterns.filter {
                            case statementPattern: StatementPattern => false
                            case _ => true
                        }

                        statementsInConstructClause = triplestoreSpecificQuery.constructClause.statements.map(_.toKnoraExplicit)


                        statementsInWhereButNotInConstruct = statementsInWhereClause.diff(statementsInConstructClause)
                        statementsInConstructButNotInWhere = statementsInConstructClause.diff(statementsInWhereClause)

                        _ = println(s"statementsInWhereButNotInConstruct: $statementsInWhereButNotInConstruct")
                        _ = println(s"statementsInConstructButNotInWhere: $statementsInConstructButNotInWhere")
                        _ = println(s"nonStatementsInWhereClause: $nonStatementsInWhereClause")
            */

            triplestoreSpecificSparql: String = triplestoreSpecificQuery.toSparql

            _ = println(triplestoreSpecificQuery.toSparql)

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