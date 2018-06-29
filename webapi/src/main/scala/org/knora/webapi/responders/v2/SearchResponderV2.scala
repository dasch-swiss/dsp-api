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
import org.knora.webapi.messages.v1.responder.valuemessages.JulianDayNumberValueV1
import org.knora.webapi.messages.v2.responder.resourcemessages._
import org.knora.webapi.messages.v2.responder.searchmessages._
import org.knora.webapi.responders.ResponderWithStandoffV2
import org.knora.webapi.util.ActorUtil._
import org.knora.webapi.util.IriConversions._
import org.knora.webapi.util._
import org.knora.webapi.util.search.ApacheLuceneSupport.{CombineSearchTerms, MatchStringWhileTyping}
import org.knora.webapi.util.search._
import org.knora.webapi.util.search.gravsearch._

import scala.collection.mutable
import scala.concurrent.Future

/**
  * Constants used in [[SearchResponderV2]].
  */
object SearchResponderV2Constants {

    val forbiddenResourceIri: IRI = s"http://${KnoraIdUtil.IriDomain}/permissions/forbiddenResource"

    /**
      * Constants for fulltext query.
      *
      * These constants are used to create SPARQL CONSTRUCT queries to be executed by the triplestore and to process the results that are returned.
      */
    object FullTextSearchConstants {

        // SPARQL variable representing the concatenated IRIs of value objects matching the search criteria
        val valueObjectConcatVar: QueryVariable = QueryVariable("valueObjectConcat")

        // SPARQL variable representing the resources matching the search criteria
        val resourceVar: QueryVariable = QueryVariable("resource")

        // SPARQL variable representing the predicates of a resource
        val resourcePropVar: QueryVariable = QueryVariable("resourceProp")

        // SPARQL variable representing the objects of a resource
        val resourceObjectVar: QueryVariable = QueryVariable("resourceObj")

        // SPARQL variable representing the property pointing to a value object from a resource
        val resourceValueProp: QueryVariable = QueryVariable("resourceValueProp")

        // SPARQL variable representing the value objects of a resource
        val resourceValueObject: QueryVariable = QueryVariable("resourceValueObject")

        // SPARQL variable representing the predicates of a value object
        val resourceValueObjectProp: QueryVariable = QueryVariable("resourceValueObjectProp")

        // SPARQL variable representing the objects of a value object
        val resourceValueObjectObj: QueryVariable = QueryVariable("resourceValueObjectObj")

        // SPARQL variable representing the standoff nodes of a (text) value object
        val standoffNodeVar: QueryVariable = QueryVariable("standoffNode")

        // SPARQL variable representing the predicates of a standoff node of a (text) value object
        val standoffPropVar: QueryVariable = QueryVariable("standoffProp")

        // SPARQL variable representing the objects of a standoff node of a (text) value object
        val standoffValueVar: QueryVariable = QueryVariable("standoffValue")
    }

    /**
      * Constants used in the processing of Gravsearch queries.
      *
      * These constants are used to create SPARQL CONSTRUCT queries to be executed by the triplestore and to process the results that are returned.
      */
    object GravsearchConstants {

        // SPARQL variable representing the main resource and its properties
        val mainResourceVar: QueryVariable = QueryVariable("mainResourceVar")

        // SPARQL variable representing main and dependent resources
        val mainAndDependentResourceVar: QueryVariable = QueryVariable("mainAndDependentResource")

        // SPARQL variable representing the predicates of the main and dependent resources
        val mainAndDependentResourcePropVar: QueryVariable = QueryVariable("mainAndDependentResourceProp")

        // SPARQL variable representing the objects of the main and dependent resources
        val mainAndDependentResourceObjectVar: QueryVariable = QueryVariable("mainAndDependentResourceObj")

        // SPARQL variable representing the value objects of the main and dependent resources
        val mainAndDependentResourceValueObject: QueryVariable = QueryVariable("mainAndDependentResourceValueObject")

        // SPARQL variable representing the properties pointing to value objects from the main and dependent resources
        val mainAndDependentResourceValueProp: QueryVariable = QueryVariable("mainAndDependentResourceValueProp")

        // SPARQL variable representing the predicates of value objects of the main and dependent resources
        val mainAndDependentResourceValueObjectProp: QueryVariable = QueryVariable("mainAndDependentResourceValueObjectProp")

        // SPARQL variable representing the objects of value objects of the main and dependent resources
        val mainAndDependentResourceValueObjectObj: QueryVariable = QueryVariable("mainAndDependentResourceValueObjectObj")

        // SPARQL variable representing the standoff nodes of a (text) value object
        val standoffNodeVar: QueryVariable = QueryVariable("standoffNode")

        // SPARQL variable representing the predicates of a standoff node of a (text) value object
        val standoffPropVar: QueryVariable = QueryVariable("standoffProp")

        // SPARQL variable representing the objects of a standoff node of a (text) value object
        val standoffValueVar: QueryVariable = QueryVariable("standoffValue")

        // SPARQL variable representing a list node pointed to by a (list) value object
        val listNode: QueryVariable = QueryVariable("listNode")

        // SPARQL variable representing the label of a list node pointed to by a (list) value object
        val listNodeLabel: QueryVariable = QueryVariable("listNodeLabel")
    }

}

class SearchResponderV2 extends ResponderWithStandoffV2 {

    // A Gravsearch type inspection runner.
    private val gravsearchTypeInspectionRunner = new GravsearchTypeInspectionRunner(system = system)

    def receive = {
        case FullTextSearchCountRequestV2(searchValue, limitToProject, limitToResourceClass, limitToStandoffClass, requestingUser) => future2Message(sender(), fulltextSearchCountV2(searchValue, limitToProject, limitToResourceClass, limitToStandoffClass, requestingUser), log)
        case FulltextSearchRequestV2(searchValue, offset, limitToProject, limitToResourceClass, limitToStandoffClass, requestingUser) => future2Message(sender(), fulltextSearchV2(searchValue, offset, limitToProject, limitToResourceClass, limitToStandoffClass, requestingUser), log)
        case GravsearchCountRequestV2(query, requestingUser) => future2Message(sender(), gravsearchCountV2(inputQuery = query, requestingUser = requestingUser), log)
        case GravsearchRequestV2(query, requestingUser) => future2Message(sender(), gravsearchV2(inputQuery = query, requestingUser = requestingUser), log)
        case SearchResourceByLabelCountRequestV2(searchValue, limitToProject, limitToResourceClass, requestingUser) => future2Message(sender(), searchResourcesByLabelCountV2(searchValue, limitToProject, limitToResourceClass, requestingUser), log)
        case SearchResourceByLabelRequestV2(searchValue, offset, limitToProject, limitToResourceClass, requestingUser) => future2Message(sender(), searchResourcesByLabelV2(searchValue, offset, limitToProject, limitToResourceClass, requestingUser), log)
        case other => handleUnexpectedMessage(sender(), other, log, this.getClass.getName)
    }

    /**
      * An abstract base class providing shared methods for [[WhereTransformer]] instances.
      */
    abstract class AbstractSparqlTransformer(typeInspectionResult: GravsearchTypeInspectionResult, querySchema: ApiV2Schema) extends WhereTransformer {

        // Contains the variable representing the main resource: knora-base:isMainResource
        protected var mainResourceVariable: Option[QueryVariable] = None

        // get method for public access
        def getMainResourceVariable: QueryVariable = mainResourceVariable.getOrElse(throw GravsearchException("Could not get main resource variable from transformer"))

        // a Set containing all `TypeableEntity` (keys of `typeInspectionResult`) that have already been processed
        // in order to prevent duplicates
        protected val processedTypeInformationKeysWhereClause = mutable.Set.empty[TypeableEntity]

        // Contains the variables of dependent resources
        protected var dependentResourceVariables = mutable.Set.empty[QueryVariable]

        // separator used by GroupConcat
        val groupConcatSeparator: Char = StringFormatter.INFORMATION_SEPARATOR_ONE

        // contains variables representing group concatenated dependent resource IRIs
        protected var dependentResourceVariablesGroupConcat = Set.empty[QueryVariable]

        // get method for public access
        def getDependentResourceVariablesGroupConcat: Set[QueryVariable] = dependentResourceVariablesGroupConcat

        // contains the variables of value objects (including those for link values)
        protected var valueObjectVariables = mutable.Set.empty[QueryVariable]

        // contains variables representing group concatenated value objects IRIs
        protected var valueObjectVarsGroupConcat = Set.empty[QueryVariable]

        // get method for public access
        def getValueObjectVarsGroupConcat: Set[QueryVariable] = valueObjectVarsGroupConcat

        // suffix appended to variables that are returned by a SPARQL aggregation function.
        val groupConcatVariableSuffix = "__Concat"

        /**
          * A container for a generated variable representing a value literal.
          *
          * @param variable     the generated variable.
          * @param useInOrderBy if `true`, the generated variable can be used in ORDER BY.
          */
        private case class GeneratedQueryVariable(variable: QueryVariable, useInOrderBy: Boolean)

        // variables that are created when processing filter statements
        // they represent the value of a literal pointed to by a value object
        private val valueVariablesGeneratedInFilters = mutable.Map.empty[QueryVariable, Set[GeneratedQueryVariable]]

        /**
          * Saves a generated variable representing a value literal, if it hasn't been saved already.
          *
          * @param valueVar     the variable representing the value.
          * @param generatedVar the generated variable representing the value literal.
          * @param useInOrderBy if `true`, the generated variable can be used in ORDER BY.
          * @return `true` if the generated variable was saved, `false` if it had already been saved.
          */
        protected def addGeneratedVariableForValueLiteral(valueVar: QueryVariable, generatedVar: QueryVariable, useInOrderBy: Boolean = true): Boolean = {
            val currentGeneratedVars = valueVariablesGeneratedInFilters.getOrElse(valueVar, Set.empty[GeneratedQueryVariable])

            if (!currentGeneratedVars.exists(currentGeneratedVar => currentGeneratedVar.variable == generatedVar)) {
                valueVariablesGeneratedInFilters.put(valueVar, currentGeneratedVars + GeneratedQueryVariable(generatedVar, useInOrderBy))
                true
            } else {
                false
            }
        }

        /**
          * Gets a saved generated variable representing a value literal, for use in ORDER BY.
          *
          * @param valueVar the variable representing the value.
          * @return a generated variable that represents a value literal and can be used in ORDER BY, or `None` if no such variable has been saved.
          */
        protected def getGeneratedVariableForValueLiteralInOrderBy(valueVar: QueryVariable): Option[QueryVariable] = {
            valueVariablesGeneratedInFilters.get(valueVar) match {
                case Some(generatedVars: Set[GeneratedQueryVariable]) =>
                    val generatedVarsForOrderBy: Set[QueryVariable] = generatedVars.filter(_.useInOrderBy).map(_.variable)

                    if (generatedVarsForOrderBy.size > 1) {
                        throw AssertionException(s"More than one variable was generated for the literal values of ${valueVar.toSparql} and marked for use in ORDER BY: ${generatedVarsForOrderBy.map(_.toSparql).mkString(", ")}")
                    }

                    generatedVarsForOrderBy.headOption

                case None => None
            }
        }

        // Generated statements for date literals, so we don't generate the same statements twice.
        protected val generatedDateStatements = mutable.Set.empty[StatementPattern]

        // Variables generated to represent marked-up text in standoff, so we don't generate the same variables twice.
        protected val standoffMarkedUpVariables = mutable.Set.empty[QueryVariable]

        /**
          * Create a unique variable from a whole statement.
          *
          * @param baseStatement the statement to be used to create the variable base name.
          * @param suffix        the suffix to be appended to the base name.
          * @return a unique variable.
          */
        protected def createUniqueVariableFromStatement(baseStatement: StatementPattern, suffix: String): QueryVariable = {
            QueryVariable(escapeEntityForVariable(baseStatement.subj) + "__" + escapeEntityForVariable(baseStatement.pred) + "__" + escapeEntityForVariable(baseStatement.obj) + "__" + suffix)
        }

        /**
          * Checks if a statement represents the knora-base:isMainResource statement and returns the query variable representing the main resource if so.
          *
          * @param statementPattern the statement pattern to be checked.
          * @return query variable representing the main resource or None.
          */
        protected def isMainResourceVariable(statementPattern: StatementPattern): Option[QueryVariable] = {
            statementPattern.pred match {
                case IriRef(iri, _) =>

                    val iriStr = iri.toString

                    if (iriStr == OntologyConstants.KnoraApiV2Simple.IsMainResource || iriStr == OntologyConstants.KnoraApiV2WithValueObjects.IsMainResource) {
                        statementPattern.obj match {
                            case XsdLiteral(value, SmartIri(OntologyConstants.Xsd.Boolean)) if value.toBoolean =>
                                statementPattern.subj match {
                                    case queryVariable: QueryVariable => Some(queryVariable)
                                    case _ => throw GravsearchException(s"The subject of ${iri.toSparql} must be a variable")
                                }

                            case _ => None
                        }
                    } else {
                        None
                    }

                case _ => None
            }
        }

        /**
          * Creates additional statements for a non property type (e.g., a resource).
          *
          * @param nonPropertyTypeInfo type information about non property type.
          * @param inputEntity         the [[Entity]] to make the statements about.
          * @return a sequence of [[QueryPattern]] representing the additional statements.
          */
        protected def createAdditionalStatementsForNonPropertyType(nonPropertyTypeInfo: NonPropertyTypeInfo, inputEntity: Entity): Seq[QueryPattern] = {
            if (OntologyConstants.KnoraApi.isKnoraApiV2Resource(nonPropertyTypeInfo.typeIri)) {

                // inputEntity is either source or target of a linking property
                // create additional statements in order to query permissions and other information for a resource

                // add the inputEntity (a variable representing a resource) to the SELECT
                inputEntity match {
                    case queryVar: QueryVariable =>
                        // make sure that this is not the mainVar
                        mainResourceVariable match {
                            case Some(mainVar: QueryVariable) =>

                                if (mainVar != queryVar) {
                                    // it is a variable representing a dependent resource
                                    dependentResourceVariables += queryVar
                                }

                            case None => ()

                        }

                    case _ => ()
                }

                Seq(
                    StatementPattern.makeInferred(subj = inputEntity, pred = IriRef(OntologyConstants.Rdf.Type.toSmartIri), obj = IriRef(OntologyConstants.KnoraBase.Resource.toSmartIri)),
                    StatementPattern.makeExplicit(subj = inputEntity, pred = IriRef(OntologyConstants.KnoraBase.IsDeleted.toSmartIri), obj = XsdLiteral(value = "false", datatype = OntologyConstants.Xsd.Boolean.toSmartIri))
                )
            } else {
                // inputEntity is target of a value property
                // properties are handled by `convertStatementForPropertyType`, no processing needed here

                Seq.empty[QueryPattern]
            }
        }

        /**
          * Generates statements matching a `knora-base:LinkValue`.
          *
          * @param linkSource the resource that is the source of the link.
          * @param linkPred   the link predicate.
          * @param linkTarget the resource that is the target of the link.
          * @return statements matching the `knora-base:LinkValue` that describes the link.
          */
        private def generateStatementsForLinkValue(linkSource: Entity, linkPred: Entity, linkTarget: Entity): Seq[StatementPattern] = {
            // Generate a variable name representing the link value
            val linkValueObjVar: QueryVariable = createUniqueVariableNameFromEntityAndProperty(
                base = linkTarget,
                propertyIri = OntologyConstants.KnoraBase.LinkValue
            )

            // add variable to collection representing value objects
            valueObjectVariables += linkValueObjVar

            // create an Entity that connects the subject of the linking property with the link value object
            val linkValueProp: Entity = linkPred match {
                case linkingPropQueryVar: QueryVariable =>
                    // Generate a variable name representing the link value property
                    // in case FILTER patterns are given restricting the linking property's possible IRIs, the same variable will recreated when processing FILTER patterns
                    createlinkValuePropertyVariableFromLinkingPropertyVariable(linkingPropQueryVar)

                case propIri: IriRef =>
                    // convert the given linking property IRI to the corresponding link value property IRI
                    // only matches the linking property's link value
                    IriRef(propIri.iri.toOntologySchema(InternalSchema).fromLinkPropToLinkValueProp)

                case literal: XsdLiteral => throw GravsearchException(s"literal ${literal.toSparql} cannot be used as a predicate")

                case other => throw GravsearchException(s"${other.toSparql} cannot be used as a predicate")
            }

            // Add statements that represent the link value's properties for the given linking property
            // do not check for the predicate because inference would not work
            // instead, linkValueProp restricts the link value objects to be returned
            Seq(
                StatementPattern.makeInferred(subj = linkSource, pred = linkValueProp, obj = linkValueObjVar),
                StatementPattern.makeExplicit(subj = linkValueObjVar, pred = IriRef(OntologyConstants.Rdf.Type.toSmartIri), obj = IriRef(OntologyConstants.KnoraBase.LinkValue.toSmartIri)),
                StatementPattern.makeExplicit(subj = linkValueObjVar, pred = IriRef(OntologyConstants.KnoraBase.IsDeleted.toSmartIri), obj = XsdLiteral(value = "false", datatype = OntologyConstants.Xsd.Boolean.toSmartIri)),
                StatementPattern.makeExplicit(subj = linkValueObjVar, pred = IriRef(OntologyConstants.Rdf.Subject.toSmartIri), obj = linkSource),
                StatementPattern.makeExplicit(subj = linkValueObjVar, pred = IriRef(OntologyConstants.Rdf.Object.toSmartIri), obj = linkTarget)
            )
        }

        protected def convertStatementForPropertyType(inputOrderBy: Seq[OrderCriterion])(propertyTypeInfo: PropertyTypeInfo, statementPattern: StatementPattern, typeInspectioResult: GravsearchTypeInspectionResult): Seq[QueryPattern] = {
            /**
              * Ensures that if the object of a statement is a variable, and is used in the ORDER BY clause of the input query, the subject of the statement
              * is the main resource. Throws an exception otherwise.
              *
              * @param objectVar the variable that is the object of the statement.
              */
            def checkSubjectInOrderBy(objectVar: QueryVariable): Unit = {
                statementPattern.subj match {
                    case subjectVar: QueryVariable =>
                        if (!mainResourceVariable.contains(subjectVar) && inputOrderBy.exists(criterion => criterion.queryVariable == objectVar)) {
                            throw GravsearchException(s"Variable ${objectVar.toSparql} is used in ORDER BY, but does not represent a value of the main resource")
                        }

                    case _ => ()
                }
            }

            val maybeSubjectTypeIri: Option[SmartIri] = typeInspectionResult.getTypeOfEntity(statementPattern.subj) match {
                case Some(NonPropertyTypeInfo(subjectTypeIri)) => Some(subjectTypeIri)
                case _ => None
            }

            val subjectIsResource: Boolean = maybeSubjectTypeIri.exists(iri => OntologyConstants.KnoraApi.isKnoraApiV2Resource(iri))
            val objectIsResource: Boolean = OntologyConstants.KnoraApi.isKnoraApiV2Resource(propertyTypeInfo.objectTypeIri)

            // Is the subject of the statement a resource?
            if (subjectIsResource) {
                // Yes. Is the object of the statement also a resource?
                if (objectIsResource) {
                    // Yes. This is a link property. Make sure that the object is either an IRI or a variable (cannot be a literal).
                    statementPattern.obj match {
                        case _: IriRef => ()
                        case objectVar: QueryVariable => checkSubjectInOrderBy(objectVar)
                        case other => throw GravsearchException(s"Object of a linking statement must be an IRI or a variable, but ${other.toSparql} given.")
                    }

                    // Generate statement patterns to match the link value.
                    val linkValueStatements = generateStatementsForLinkValue(
                        linkSource = statementPattern.subj,
                        linkPred = statementPattern.pred,
                        linkTarget = statementPattern.obj
                    )

                    // Add the input statement, which uses the link property, to the generated statements about the link value.
                    statementPatternToInternalSchema(statementPattern, typeInspectionResult) +: linkValueStatements

                } else {
                    // The subject is a resource, but the object isn't, so this isn't a link property. Make sure that the object of the property is a variable.
                    val objectVar: QueryVariable = statementPattern.obj match {
                        case queryVar: QueryVariable =>
                            checkSubjectInOrderBy(queryVar)
                            queryVar

                        case other => throw GravsearchException(s"Object of a value property statement must be a QueryVariable, but ${other.toSparql} given.")
                    }

                    // Does the variable refer to a Knora value object? We assume it does if the query just uses the
                    // simple schema. If the query uses the complex schema, check whether the property's object type is
                    // a Knora API v2 value class.

                    val objectVarIsValueObject = querySchema == ApiV2Simple || OntologyConstants.KnoraApiV2WithValueObjects.ValueClasses.contains(propertyTypeInfo.objectTypeIri.toString)

                    if (objectVarIsValueObject) {
                        // The variable refers to a value object. Add it to the collection representing value objects.
                        valueObjectVariables += objectVar

                        // Convert the statement to the internal schema, and add a statement to check that the value object is not marked as deleted.
                        val valueObjectIsNotDeleted = StatementPattern.makeExplicit(subj = statementPattern.obj, pred = IriRef(OntologyConstants.KnoraBase.IsDeleted.toSmartIri), obj = XsdLiteral(value = "false", datatype = OntologyConstants.Xsd.Boolean.toSmartIri))
                        Seq(statementPatternToInternalSchema(statementPattern, typeInspectionResult), valueObjectIsNotDeleted)
                    } else {
                        // The variable doesn't refer to a value object. Just convert the statement pattern to the internal schema.
                        Seq(statementPatternToInternalSchema(statementPattern, typeInspectionResult))
                    }


                }
            } else {
                // The subject isn't a resource, so it must be a value object or standoff node. Is the query in the complex schema?
                if (querySchema == ApiV2WithValueObjects) {
                    // Yes. If the subject is a standoff tag and the object is a resource, that's an error, because the client
                    // has to use the knora-api:standoffLink function instead.
                    if (maybeSubjectTypeIri.contains(OntologyConstants.KnoraApiV2WithValueObjects.StandoffTag.toSmartIri) && objectIsResource) {
                        throw GravsearchException(s"Invalid statement pattern (use the knora-api:standoffLink function instead): ${statementPattern.toSparql.trim}")
                    } else {
                        // Otherwise, just convert the statement pattern to the internal schema.
                        Seq(statementPatternToInternalSchema(statementPattern, typeInspectionResult))
                    }
                } else {
                    // The query is in the simple schema, so the statement is invalid.
                    throw GravsearchException(s"Invalid statement pattern: ${statementPattern.toSparql.trim}")
                }
            }
        }

        protected def processStatementPatternFromWhereClause(statementPattern: StatementPattern, inputOrderBy: Seq[OrderCriterion]): Seq[QueryPattern] = {

            // look at the statement's subject, predicate, and object and generate additional statements if needed based on the given type information.
            // transform the originally given statement if necessary when processing the predicate

            // check if there exists type information for the given statement's subject
            val additionalStatementsForSubj: Seq[QueryPattern] = checkForNonPropertyTypeInfoForEntity(
                entity = statementPattern.subj,
                typeInspectionResult = typeInspectionResult,
                processedTypeInfo = processedTypeInformationKeysWhereClause,
                conversionFuncForNonPropertyType = createAdditionalStatementsForNonPropertyType
            )

            // check if there exists type information for the given statement's object
            val additionalStatementsForObj: Seq[QueryPattern] = checkForNonPropertyTypeInfoForEntity(
                entity = statementPattern.obj,
                typeInspectionResult = typeInspectionResult,
                processedTypeInfo = processedTypeInformationKeysWhereClause,
                conversionFuncForNonPropertyType = createAdditionalStatementsForNonPropertyType
            )

            // Add additional statements based on the whole input statement, e.g. to deal with the value object or the link value, and transform the original statement.
            val additionalStatementsForWholeStatement: Seq[QueryPattern] = checkForPropertyTypeInfoForStatement(
                statementPattern = statementPattern,
                typeInspectionResult = typeInspectionResult,
                conversionFuncForPropertyType = convertStatementForPropertyType(inputOrderBy)
            )

            additionalStatementsForSubj ++ additionalStatementsForWholeStatement ++ additionalStatementsForObj

        }

        /**
          * Creates additional statements for a given [[Entity]] based on type information using `conversionFuncForNonPropertyType`
          * for a non property type (e.g., a resource).
          *
          * @param entity                           the entity to be taken into consideration (a statement's subject or object).
          * @param typeInspectionResult             type information.
          * @param processedTypeInfo                the keys of type information that have already been looked at.
          * @param conversionFuncForNonPropertyType the function to use to create additional statements.
          * @return a sequence of [[QueryPattern]] representing the additional statements.
          */
        protected def checkForNonPropertyTypeInfoForEntity(entity: Entity, typeInspectionResult: GravsearchTypeInspectionResult, processedTypeInfo: mutable.Set[TypeableEntity], conversionFuncForNonPropertyType: (NonPropertyTypeInfo, Entity) => Seq[QueryPattern]): Seq[QueryPattern] = {
            val typesNotYetProcessed = typeInspectionResult.copy(entities = typeInspectionResult.entities -- processedTypeInfo)

            typesNotYetProcessed.getTypeOfEntity(entity) match {
                case Some(nonPropInfo: NonPropertyTypeInfo) =>
                    // add a TypeableEntity for subject to prevent duplicates
                    processedTypeInfo += GravsearchTypeInspectionUtil.toTypeableEntity(entity)
                    conversionFuncForNonPropertyType(nonPropInfo, entity)

                case Some(other) => throw AssertionException(s"NonPropertyTypeInfo expected for $entity, got $other")

                case None => Seq.empty[QueryPattern]
            }
        }

        /**
          * Converts the given statement based on the given type information using `conversionFuncForPropertyType`.
          *
          * @param statementPattern              the statement to be converted.
          * @param typeInspectionResult          type information.
          * @param conversionFuncForPropertyType the function to use for the conversion.
          * @return a sequence of [[QueryPattern]] representing the converted statement.
          */
        protected def checkForPropertyTypeInfoForStatement(statementPattern: StatementPattern, typeInspectionResult: GravsearchTypeInspectionResult, conversionFuncForPropertyType: (PropertyTypeInfo, StatementPattern, GravsearchTypeInspectionResult) => Seq[QueryPattern]): Seq[QueryPattern] = {
            typeInspectionResult.getTypeOfEntity(statementPattern.pred) match {
                case Some(propInfo: PropertyTypeInfo) =>
                    // process type information for the predicate into additional statements
                    conversionFuncForPropertyType(propInfo, statementPattern, typeInspectionResult)

                case Some(other) => throw AssertionException(s"PropertyTypeInfo expected for ${statementPattern.pred}, got $other")

                case None =>
                    // no type information given and thus no further processing needed, just return the originally given statement (e.g., rdf:type), converted to the internal schema.
                    Seq(statementPatternToInternalSchema(statementPattern, typeInspectionResult))
            }
        }

        // A Map of knora-api value types (both complex and simple) to the corresponding knora-base value predicates
        // that point to literals. This is used only for generating additional statements for ORDER BY clauses, so it only needs to include
        // types that have a meaningful order.
        protected val valueTypesToValuePredsForOrderBy: Map[IRI, IRI] = Map(
            OntologyConstants.Xsd.Integer -> OntologyConstants.KnoraBase.ValueHasInteger,
            OntologyConstants.Xsd.Decimal -> OntologyConstants.KnoraBase.ValueHasDecimal,
            OntologyConstants.Xsd.Boolean -> OntologyConstants.KnoraBase.ValueHasBoolean,
            OntologyConstants.Xsd.String -> OntologyConstants.KnoraBase.ValueHasString,
            OntologyConstants.KnoraApiV2Simple.Date -> OntologyConstants.KnoraBase.ValueHasStartJDN,
            OntologyConstants.KnoraApiV2Simple.Color -> OntologyConstants.KnoraBase.ValueHasColor,
            OntologyConstants.KnoraApiV2Simple.Geoname -> OntologyConstants.KnoraBase.ValueHasGeonameCode,
            OntologyConstants.KnoraApiV2WithValueObjects.TextValue -> OntologyConstants.KnoraBase.ValueHasString,
            OntologyConstants.KnoraApiV2WithValueObjects.IntValue -> OntologyConstants.KnoraBase.ValueHasInteger,
            OntologyConstants.KnoraApiV2WithValueObjects.DecimalValue -> OntologyConstants.KnoraBase.ValueHasDecimal,
            OntologyConstants.KnoraApiV2WithValueObjects.BooleanValue -> OntologyConstants.KnoraBase.ValueHasBoolean,
            OntologyConstants.KnoraApiV2WithValueObjects.DateValue -> OntologyConstants.KnoraBase.ValueHasStartJDN,
            OntologyConstants.KnoraApiV2WithValueObjects.ColorValue -> OntologyConstants.KnoraBase.ValueHasColor,
            OntologyConstants.KnoraApiV2WithValueObjects.GeonameValue -> OntologyConstants.KnoraBase.ValueHasGeonameCode
        )

        /**
          * Calls [[checkStatement]], then converts the specified statement pattern to the internal schema.
          *
          * @param statementPattern     the statement pattern to be converted.
          * @param typeInspectionResult the type inspection result.
          * @return the converted statement pattern.
          */
        protected def statementPatternToInternalSchema(statementPattern: StatementPattern, typeInspectionResult: GravsearchTypeInspectionResult): StatementPattern = {
            checkStatement(
                statementPattern = statementPattern,
                querySchema = querySchema,
                typeInspectionResult = typeInspectionResult
            )

            statementPattern.toOntologySchema(InternalSchema)
        }

        /**
          * Given a variable representing a linking property, creates a variable representing the corresponding link value property.
          *
          * @param linkingPropertyQueryVariable variable representing a linking property.
          * @return variable representing the corresponding link value property.
          */
        protected def createlinkValuePropertyVariableFromLinkingPropertyVariable(linkingPropertyQueryVariable: QueryVariable): QueryVariable = {
            createUniqueVariableNameFromEntityAndProperty(
                base = linkingPropertyQueryVariable,
                propertyIri = OntologyConstants.KnoraBase.HasLinkToValue
            )
        }

        /**
          * Represents a transformed Filter expression and additional statement patterns that possibly had to be created during transformation.
          *
          * @param expression         the transformed FILTER expression. In some cases, a given FILTER expression is replaced by additional statements, but
          *                           only if it is the top-level expression in the FILTER.
          * @param additionalPatterns additionally created query patterns.
          */
        protected case class TransformedFilterPattern(expression: Option[Expression], additionalPatterns: Seq[QueryPattern] = Seq.empty[QueryPattern])

        /**
          * Handles query variables that represent properties in a [[FilterPattern]].
          *
          * @param queryVar           the query variable to be handled.
          * @param comparisonOperator the comparison operator used in the filter pattern.
          * @param iriRef             the IRI the property query variable is restricted to.
          * @param propInfo           information about the query variable's type.
          * @return a [[TransformedFilterPattern]].
          */
        private def handlePropertyIriQueryVar(queryVar: QueryVariable, comparisonOperator: CompareExpressionOperator.Value, iriRef: IriRef, propInfo: PropertyTypeInfo): TransformedFilterPattern = {

            iriRef.iri.checkApiV2Schema(querySchema, throw GravsearchException(s"Invalid schema for IRI: ${iriRef.toSparql}"))

            val internalIriRef = iriRef.toOntologySchema(InternalSchema)

            // make sure that the comparison operator is a CompareExpressionOperator.EQUALS
            if (comparisonOperator != CompareExpressionOperator.EQUALS)
                throw GravsearchException(s"Comparison operator in a CompareExpression for a property type must be ${CompareExpressionOperator.EQUALS}, but '$comparisonOperator' given (for negations use 'FILTER NOT EXISTS')")

            val userProvidedRestriction = CompareExpression(queryVar, comparisonOperator, internalIriRef)

            // check if the objectTypeIri of propInfo is knora-api:Resource
            // if so, it is a linking property and its link value property must be restricted too
            if (OntologyConstants.KnoraApi.isKnoraApiV2Resource(propInfo.objectTypeIri)) {
                // it is a linking property, restrict the link value property
                val restrictionForLinkValueProp = CompareExpression(
                    leftArg = createlinkValuePropertyVariableFromLinkingPropertyVariable(queryVar), // the same variable was created during statement processing in WHERE clause in `convertStatementForPropertyType`
                    operator = comparisonOperator,
                    rightArg = IriRef(internalIriRef.iri.fromLinkPropToLinkValueProp)) // create link value property from linking property

                TransformedFilterPattern(
                    Some(
                        AndExpression(
                            leftArg = userProvidedRestriction,
                            rightArg = restrictionForLinkValueProp)
                    )
                )
            } else {
                // not a linking property, just return the provided restriction
                TransformedFilterPattern(Some(userProvidedRestriction))
            }
        }

        /**
          * Handles query variables that represent literals in a [[FilterPattern]].
          *
          * @param queryVar                 the query variable to be handled.
          * @param comparisonOperator       the comparison operator used in the filter pattern.
          * @param literalValueExpression   the literal provided in the [[FilterPattern]] as an [[Expression]].
          * @param xsdType                  valid xsd types of the literal.
          * @param valueHasProperty         the property of the value object pointing to the literal (in the internal schema).
          * @param validComparisonOperators a set of valid comparison operators, if to be restricted.
          * @return a [[TransformedFilterPattern]].
          */
        private def handleLiteralQueryVar(queryVar: QueryVariable, comparisonOperator: CompareExpressionOperator.Value, literalValueExpression: Expression, xsdType: Set[IRI], valueHasProperty: IRI, validComparisonOperators: Set[CompareExpressionOperator.Value] = Set.empty[CompareExpressionOperator.Value]): TransformedFilterPattern = {

            // make sure that the expression is a literal of the expected type
            val literal: XsdLiteral = literalValueExpression match {
                case xsdLiteral: XsdLiteral if xsdType(xsdLiteral.datatype.toString) => xsdLiteral

                case other => throw GravsearchException(s"Invalid right argument ${other.toSparql} in comparison (allowed types in this context are ${xsdType.map(_.toSmartIri.toSparql).mkString(", ")})")
            }

            // check if comparison operator is supported for given type
            if (validComparisonOperators.nonEmpty && !validComparisonOperators(comparisonOperator))
                throw GravsearchException(s"Invalid operator '$comparisonOperator' in expression (allowed operators in this context are ${validComparisonOperators.map(op => "'" + op + "'").mkString(", ")})")

            // Generate a variable name representing the literal attached to the value object
            val valueObjectLiteralVar: QueryVariable = createUniqueVariableNameFromEntityAndProperty(
                base = queryVar,
                propertyIri = valueHasProperty
            )

            // Add a statement to assign the literal to a variable, which we'll use in the transformed FILTER expression,
            // if that statement hasn't been added already.

            val statementToAddForValueHas: Seq[StatementPattern] = if (addGeneratedVariableForValueLiteral(queryVar, valueObjectLiteralVar)) {
                Seq(
                    // connects the query variable with the value object (internal structure: values are represented as objects)
                    StatementPattern.makeExplicit(subj = queryVar, pred = IriRef(valueHasProperty.toSmartIri), valueObjectLiteralVar)
                )
            } else {
                Seq.empty[StatementPattern]
            }


            TransformedFilterPattern(
                Some(CompareExpression(valueObjectLiteralVar, comparisonOperator, literal)), // compares the provided literal to the value object's literal value
                statementToAddForValueHas
            )

        }

        /**
          * Handles query variables that represent a date in a [[FilterPattern]].
          *
          * @param queryVar            the query variable to be handled.
          * @param comparisonOperator  the comparison operator used in the filter pattern.
          * @param dateValueExpression the date literal provided in the [[FilterPattern]] as an [[Expression]].
          * @return a [[TransformedFilterPattern]].
          */
        private def handleDateQueryVar(queryVar: QueryVariable, comparisonOperator: CompareExpressionOperator.Value, dateValueExpression: Expression): TransformedFilterPattern = {

            // make sure that the right argument is a string literal (dates are represented as knora date strings in knora-api simple)
            val dateStringLiteral: XsdLiteral = dateValueExpression match {
                case dateStrLiteral: XsdLiteral if dateStrLiteral.datatype.toString == OntologyConstants.KnoraApiV2Simple.Date => dateStrLiteral

                case other => throw GravsearchException(s"Invalid right argument ${other.toSparql} in date comparison")
            }

            // validate Knora date string
            val dateStr: String = stringFormatter.validateDate(dateStringLiteral.value, throw BadRequestException(s"${dateStringLiteral.value} is not a valid date string"))

            val date: JulianDayNumberValueV1 = DateUtilV1.createJDNValueV1FromDateString(dateStr)

            // Generate a variable name representing the period's start
            val dateValueHasStartVar = createUniqueVariableNameFromEntityAndProperty(base = queryVar, propertyIri = OntologyConstants.KnoraBase.ValueHasStartJDN)

            // sort dates by their period's start (in the prequery)
            addGeneratedVariableForValueLiteral(queryVar, dateValueHasStartVar)

            // Generate a variable name representing the period's end
            val dateValueHasEndVar = createUniqueVariableNameFromEntityAndProperty(base = queryVar, propertyIri = OntologyConstants.KnoraBase.ValueHasEndJDN)

            // connects the value object with the periods start variable
            val dateValStartStatement = StatementPattern.makeExplicit(subj = queryVar, pred = IriRef(OntologyConstants.KnoraBase.ValueHasStartJDN.toSmartIri), obj = dateValueHasStartVar)

            // connects the value object with the periods end variable
            val dateValEndStatement = StatementPattern.makeExplicit(subj = queryVar, pred = IriRef(OntologyConstants.KnoraBase.ValueHasEndJDN.toSmartIri), obj = dateValueHasEndVar)

            // process filter expression based on given comparison operator
            comparisonOperator match {

                case CompareExpressionOperator.EQUALS =>

                    // any overlap in considered as equality
                    val leftArgFilter = CompareExpression(XsdLiteral(date.dateval1.toString, OntologyConstants.Xsd.Integer.toSmartIri), CompareExpressionOperator.LESS_THAN_OR_EQUAL_TO, dateValueHasEndVar)

                    val rightArgFilter = CompareExpression(XsdLiteral(date.dateval2.toString, OntologyConstants.Xsd.Integer.toSmartIri), CompareExpressionOperator.GREATER_THAN_OR_EQUAL_TO, dateValueHasStartVar)

                    val filter = AndExpression(leftArgFilter, rightArgFilter)

                    val statementsToAdd = Seq(dateValStartStatement, dateValEndStatement).filterNot(statement => generatedDateStatements.contains(statement))
                    generatedDateStatements ++= statementsToAdd

                    TransformedFilterPattern(
                        Some(filter),
                        statementsToAdd
                    )

                case CompareExpressionOperator.NOT_EQUALS =>

                    // no overlap in considered as inequality (negation of equality)
                    val leftArgFilter = CompareExpression(XsdLiteral(date.dateval1.toString, OntologyConstants.Xsd.Integer.toSmartIri), CompareExpressionOperator.GREATER_THAN, dateValueHasEndVar)

                    val rightArgFilter = CompareExpression(XsdLiteral(date.dateval2.toString, OntologyConstants.Xsd.Integer.toSmartIri), CompareExpressionOperator.LESS_THAN, dateValueHasStartVar)

                    val filter = OrExpression(leftArgFilter, rightArgFilter)

                    val statementsToAdd = Seq(dateValStartStatement, dateValEndStatement).filterNot(statement => generatedDateStatements.contains(statement))
                    generatedDateStatements ++= statementsToAdd

                    TransformedFilterPattern(
                        Some(filter),
                        statementsToAdd
                    )

                case CompareExpressionOperator.LESS_THAN =>

                    // period ends before indicated period
                    val filter = CompareExpression(dateValueHasEndVar, CompareExpressionOperator.LESS_THAN, XsdLiteral(date.dateval1.toString, OntologyConstants.Xsd.Integer.toSmartIri))

                    val statementsToAdd = Seq(dateValStartStatement, dateValEndStatement).filterNot(statement => generatedDateStatements.contains(statement)) // dateValStartStatement may be used as ORDER BY statement
                    generatedDateStatements ++= statementsToAdd

                    TransformedFilterPattern(
                        Some(filter),
                        statementsToAdd
                    )

                case CompareExpressionOperator.LESS_THAN_OR_EQUAL_TO =>

                    // period ends before indicated period or equals it (any overlap)
                    val filter = CompareExpression(dateValueHasStartVar, CompareExpressionOperator.LESS_THAN_OR_EQUAL_TO, XsdLiteral(date.dateval2.toString, OntologyConstants.Xsd.Integer.toSmartIri))

                    val statementToAdd = if (!generatedDateStatements.contains(dateValStartStatement)) {
                        generatedDateStatements += dateValStartStatement
                        Seq(dateValStartStatement)
                    } else {
                        Seq.empty[StatementPattern]
                    }

                    TransformedFilterPattern(
                        Some(filter),
                        statementToAdd
                    )

                case CompareExpressionOperator.GREATER_THAN =>

                    // period starts after end of indicated period
                    val filter = CompareExpression(dateValueHasStartVar, CompareExpressionOperator.GREATER_THAN, XsdLiteral(date.dateval2.toString, OntologyConstants.Xsd.Integer.toSmartIri))

                    val statementToAdd = if (!generatedDateStatements.contains(dateValStartStatement)) {
                        generatedDateStatements += dateValStartStatement
                        Seq(dateValStartStatement)
                    } else {
                        Seq.empty[StatementPattern]
                    }

                    TransformedFilterPattern(
                        Some(filter),
                        statementToAdd
                    )

                case CompareExpressionOperator.GREATER_THAN_OR_EQUAL_TO =>

                    // period starts after indicated period or equals it (any overlap)
                    val filter = CompareExpression(dateValueHasEndVar, CompareExpressionOperator.GREATER_THAN_OR_EQUAL_TO, XsdLiteral(date.dateval1.toString, OntologyConstants.Xsd.Integer.toSmartIri))

                    val statementsToAdd = Seq(dateValStartStatement, dateValEndStatement).filterNot(statement => generatedDateStatements.contains(statement)) // dateValStartStatement may be used as ORDER BY statement
                    generatedDateStatements ++= statementsToAdd

                    TransformedFilterPattern(
                        Some(filter),
                        statementsToAdd
                    )

                case other => throw GravsearchException(s"Invalid operator '$other' in date comparison")

            }

        }

        /**
          * Handles a [[FilterPattern]] containing a query variable.
          *
          * @param queryVar             the query variable.
          * @param compareExpression    the filter pattern's compare expression.
          * @param typeInspectionResult the type inspection results.
          * @return a [[TransformedFilterPattern]].
          */
        private def handleQueryVar(queryVar: QueryVariable, compareExpression: CompareExpression, typeInspectionResult: GravsearchTypeInspectionResult): TransformedFilterPattern = {

            typeInspectionResult.getTypeOfEntity(queryVar) match {
                case Some(typeInfo) =>
                    // check if queryVar represents a property or a value
                    typeInfo match {

                        case propInfo: PropertyTypeInfo =>

                            // left arg queryVar is a variable representing a property
                            // therefore the right argument must be an IRI restricting the property variable to a certain property
                            compareExpression.rightArg match {
                                case iriRef: IriRef =>

                                    handlePropertyIriQueryVar(
                                        queryVar = queryVar,
                                        comparisonOperator = compareExpression.operator,
                                        iriRef = iriRef,
                                        propInfo = propInfo
                                    )

                                case other => throw GravsearchException(s"Invalid right argument ${other.toSparql} in comparison (expected a property IRI)")
                            }

                        case nonPropInfo: NonPropertyTypeInfo =>

                            // Is the query using the API v2 simple schema?
                            if (querySchema == ApiV2Simple) {
                                // Yes. Depending on the value type, transform the given Filter pattern.
                                // Add an extra level by getting the value literal from the value object.
                                // If queryVar refers to a value object as a literal, for the value literal an extra variable has to be created, taking its type into account.
                                nonPropInfo.typeIri.toString match {

                                    case OntologyConstants.Xsd.Integer =>

                                        handleLiteralQueryVar(
                                            queryVar = queryVar,
                                            comparisonOperator = compareExpression.operator,
                                            literalValueExpression = compareExpression.rightArg,
                                            xsdType = Set(OntologyConstants.Xsd.Integer),
                                            valueHasProperty = OntologyConstants.KnoraBase.ValueHasInteger
                                        )

                                    case OntologyConstants.Xsd.Decimal =>

                                        handleLiteralQueryVar(
                                            queryVar = queryVar,
                                            comparisonOperator = compareExpression.operator,
                                            literalValueExpression = compareExpression.rightArg,
                                            xsdType = Set(OntologyConstants.Xsd.Decimal, OntologyConstants.Xsd.Integer), // an integer literal is also valid
                                            valueHasProperty = OntologyConstants.KnoraBase.ValueHasDecimal
                                        )

                                    case OntologyConstants.Xsd.Boolean =>

                                        handleLiteralQueryVar(
                                            queryVar = queryVar,
                                            comparisonOperator = compareExpression.operator,
                                            literalValueExpression = compareExpression.rightArg,
                                            xsdType = Set(OntologyConstants.Xsd.Boolean),
                                            valueHasProperty = OntologyConstants.KnoraBase.ValueHasBoolean,
                                            validComparisonOperators = Set(CompareExpressionOperator.EQUALS, CompareExpressionOperator.NOT_EQUALS)
                                        )

                                    case OntologyConstants.Xsd.String =>

                                        handleLiteralQueryVar(
                                            queryVar = queryVar,
                                            comparisonOperator = compareExpression.operator,
                                            literalValueExpression = compareExpression.rightArg,
                                            xsdType = Set(OntologyConstants.Xsd.String),
                                            valueHasProperty = OntologyConstants.KnoraBase.ValueHasString,
                                            validComparisonOperators = Set(CompareExpressionOperator.EQUALS, CompareExpressionOperator.NOT_EQUALS)
                                        )

                                    case OntologyConstants.Xsd.Uri =>

                                        handleLiteralQueryVar(
                                            queryVar = queryVar,
                                            comparisonOperator = compareExpression.operator,
                                            literalValueExpression = compareExpression.rightArg,
                                            xsdType = Set(OntologyConstants.Xsd.Uri),
                                            valueHasProperty = OntologyConstants.KnoraBase.ValueHasUri,
                                            validComparisonOperators = Set(CompareExpressionOperator.EQUALS, CompareExpressionOperator.NOT_EQUALS)
                                        )

                                    case OntologyConstants.KnoraApiV2Simple.Date =>

                                        handleDateQueryVar(queryVar = queryVar, comparisonOperator = compareExpression.operator, dateValueExpression = compareExpression.rightArg)

                                    case other => throw NotImplementedException(s"Value type $other not supported in FilterExpression")

                                }

                            } else {
                                // The query is using the complex schema. Keep the expression as it is.
                                TransformedFilterPattern(Some(compareExpression))
                            }

                    }

                case None =>
                    throw GravsearchException(s"No type information found about ${queryVar.toSparql}")
            }
        }

        /**
          *
          * Handles the use of the SPARQL lang function in a [[FilterPattern]].
          *
          * @param langFunctionCall     the lang function call to be handled.
          * @param compareExpression    the filter pattern's compare expression.
          * @param typeInspectionResult the type inspection results.
          * @return a [[TransformedFilterPattern]].
          */
        private def handleLangFunctionCall(langFunctionCall: LangFunction, compareExpression: CompareExpression, typeInspectionResult: GravsearchTypeInspectionResult): TransformedFilterPattern = {

            if (querySchema == ApiV2WithValueObjects) {
                throw GravsearchException(s"The lang function is not allowed in a Gravsearch query that uses the API v2 complex schema")
            }

            // make sure that the query variable represents a text value
            typeInspectionResult.getTypeOfEntity(langFunctionCall.textValueVar) match {
                case Some(typeInfo) =>
                    typeInfo match {

                        case nonPropInfo: NonPropertyTypeInfo =>

                            nonPropInfo.typeIri.toString match {

                                case OntologyConstants.Xsd.String => () // xsd:string is expected

                                case _ => throw GravsearchException(s"${langFunctionCall.textValueVar.toSparql} must be an xsd:string")
                            }

                        case _ => throw GravsearchException(s"${langFunctionCall.textValueVar.toSparql} must be an xsd:string")
                    }

                case None =>
                    throw GravsearchException(s"No type information found about ${langFunctionCall.textValueVar.toSparql}")
            }

            // comparison operator must be '=' or '!='
            if (!Set(CompareExpressionOperator.EQUALS, CompareExpressionOperator.NOT_EQUALS).contains(compareExpression.operator)) throw GravsearchException(s"Comparison operator must be '=' or '!=' for use with a 'lang' function call")

            val langLiteral: XsdLiteral = compareExpression.rightArg match {
                case strLiteral: XsdLiteral if strLiteral.datatype == OntologyConstants.Xsd.String.toSmartIri => strLiteral

                case other => throw GravsearchException(s"Right argument of comparison statement must be a string literal for use with 'lang' function call")
            }

            // Generate a variable name representing the language of the text value
            val textValHasLanguage: QueryVariable = createUniqueVariableNameFromEntityAndProperty(langFunctionCall.textValueVar, OntologyConstants.KnoraBase.ValueHasLanguage)

            // Add a statement to assign the literal to a variable, which we'll use in the transformed FILTER expression,
            // if that statement hasn't been added already.
            val statementToAddForValueHasLanguage = if (addGeneratedVariableForValueLiteral(valueVar = langFunctionCall.textValueVar, generatedVar = textValHasLanguage, useInOrderBy = false)) {
                Seq(
                    // connects the value object with the value language code
                    StatementPattern.makeExplicit(subj = langFunctionCall.textValueVar, pred = IriRef(OntologyConstants.KnoraBase.ValueHasLanguage.toSmartIri), textValHasLanguage)
                )
            } else {
                Seq.empty[StatementPattern]
            }

            TransformedFilterPattern(
                Some(CompareExpression(textValHasLanguage, compareExpression.operator, langLiteral)),
                statementToAddForValueHasLanguage
            )

        }

        /**
          * Handles the use of the SPARQL regex function in a [[FilterPattern]].
          *
          * @param regexFunctionCall    the regex function call to be handled.
          * @param typeInspectionResult the type inspection results.
          * @return a [[TransformedFilterPattern]].
          */
        private def handleRegexFunctionCall(regexFunctionCall: RegexFunction, typeInspectionResult: GravsearchTypeInspectionResult): TransformedFilterPattern = {

            // If the query uses the API v2 complex schema, leave the function call as it is.
            if (querySchema == ApiV2WithValueObjects) {
                TransformedFilterPattern(Some(regexFunctionCall))
            } else {
                // If the query uses only the simple schema, transform the function call.

                // make sure that the query variable (first argument of regex function) represents a text value
                typeInspectionResult.getTypeOfEntity(regexFunctionCall.textVar) match {
                    case Some(typeInfo) =>
                        typeInfo match {

                            case nonPropInfo: NonPropertyTypeInfo =>

                                nonPropInfo.typeIri.toString match {

                                    case OntologyConstants.Xsd.String => () // xsd:string is expected, TODO: should also xsd:anyUri be allowed?

                                    case _ => throw GravsearchException(s"${regexFunctionCall.textVar.toSparql} must be of type xsd:string")
                                }

                            case _ => throw GravsearchException(s"${regexFunctionCall.textVar.toSparql} must be of type NonPropertyTypeInfo")
                        }

                    case None =>
                        throw GravsearchException(s"No type information found about ${regexFunctionCall.textVar.toSparql}")
                }

                // Generate a variable name representing the string literal
                val textValHasString: QueryVariable = createUniqueVariableNameFromEntityAndProperty(base = regexFunctionCall.textVar, propertyIri = OntologyConstants.KnoraBase.ValueHasString)

                // Add a statement to assign the literal to a variable, which we'll use in the transformed FILTER expression,
                // if that statement hasn't been added already.
                val statementToAddForValueHasString: Seq[StatementPattern] = if (addGeneratedVariableForValueLiteral(regexFunctionCall.textVar, textValHasString)) {
                    Seq(
                        // connects the value object with the value literal
                        StatementPattern.makeExplicit(subj = regexFunctionCall.textVar, pred = IriRef(OntologyConstants.KnoraBase.ValueHasString.toSmartIri), textValHasString)
                    )
                } else {
                    Seq.empty[StatementPattern]
                }


                TransformedFilterPattern(
                    Some(RegexFunction(textValHasString, regexFunctionCall.pattern, regexFunctionCall.modifier)),
                    statementToAddForValueHasString
                )

            }

        }

        /**
          * Handles the function `knora-api:match` in the simple schema.
          *
          * @param functionCallExpression the function call to be handled.
          * @param typeInspectionResult   the type inspection results.
          * @param isTopLevel             if `true`, this is the top-level expression in the `FILTER`.
          * @return a [[TransformedFilterPattern]].
          */
        private def handleMatchFunctionInSimpleSchema(functionCallExpression: FunctionCallExpression, typeInspectionResult: GravsearchTypeInspectionResult, isTopLevel: Boolean): TransformedFilterPattern = {
            val functionIri: SmartIri = functionCallExpression.functionIri.iri

            if (querySchema == ApiV2WithValueObjects) {
                throw GravsearchException(s"Function ${functionIri.toSparql} cannot be used in a Gravsearch query written in the complex schema; use ${OntologyConstants.KnoraApiV2WithValueObjects.MatchFunction.toSmartIri.toSparql} instead")
            }

            // The match function must be the top-level expression, otherwise boolean logic won't work properly.
            if (!isTopLevel) {
                throw GravsearchException(s"Function ${functionIri.toSparql} must be the top-level expression in a FILTER")
            }

            // two arguments are expected: the first must be a variable representing a string value,
            // the second must be a string literal

            if (functionCallExpression.args.size != 2) throw GravsearchException(s"Two arguments are expected for ${functionIri.toSparql}")

            // a QueryVariable expected to represent a text value
            val textValueVar: QueryVariable = functionCallExpression.getArgAsQueryVar(pos = 0)

            typeInspectionResult.getTypeOfEntity(textValueVar) match {
                case Some(nonPropInfo: NonPropertyTypeInfo) =>

                    nonPropInfo.typeIri.toString match {

                        case OntologyConstants.Xsd.String => () // xsd:string is expected, TODO: should also xsd:anyUri be allowed?

                        case _ => throw GravsearchException(s"${textValueVar.toSparql} must be an xsd:string")
                    }

                case _ => throw GravsearchException(s"${textValueVar.toSparql} must be an xsd:string")
            }

            val textValHasString: QueryVariable = createUniqueVariableNameFromEntityAndProperty(base = textValueVar, propertyIri = OntologyConstants.KnoraBase.ValueHasString)

            // Add a statement to assign the literal to a variable, which we'll use in the transformed FILTER expression,
            // if that statement hasn't been added already.
            val valueHasStringStatement = if (addGeneratedVariableForValueLiteral(textValueVar, textValHasString)) {
                Seq(StatementPattern.makeExplicit(subj = textValueVar, pred = IriRef(OntologyConstants.KnoraBase.ValueHasString.toSmartIri), textValHasString))
            } else {
                Seq.empty[StatementPattern]
            }

            val searchTerm: XsdLiteral = functionCallExpression.getArgAsLiteral(1, xsdDatatype = OntologyConstants.Xsd.String.toSmartIri)

            // combine search terms with a logical AND (Lucene syntax)
            val searchTerms: CombineSearchTerms = CombineSearchTerms(searchTerm.value)

            TransformedFilterPattern(
                None, // FILTER has been replaced by statements
                valueHasStringStatement ++ Seq(
                    StatementPattern.makeExplicit(subj = textValHasString, pred = IriRef(OntologyConstants.KnoraBase.MatchesTextIndex.toSmartIri), XsdLiteral(searchTerms.combineSearchTermsWithLogicalAnd, OntologyConstants.Xsd.String.toSmartIri))
                )
            )
        }

        /**
          * Handles the function `knora-api:match` in the complex schema.
          *
          * @param functionCallExpression the function call to be handled.
          * @param typeInspectionResult   the type inspection results.
          * @param isTopLevel             if `true`, this is the top-level expression in the `FILTER`.
          * @return a [[TransformedFilterPattern]].
          */
        private def handleMatchFunctionInComplexSchema(functionCallExpression: FunctionCallExpression, typeInspectionResult: GravsearchTypeInspectionResult, isTopLevel: Boolean): TransformedFilterPattern = {
            val functionIri: SmartIri = functionCallExpression.functionIri.iri


            if (querySchema == ApiV2Simple) {
                throw GravsearchException(s"Function ${functionIri.toSparql} cannot be used in a Gravsearch query written in the simple schema; use ${OntologyConstants.KnoraApiV2Simple.MatchFunctionIri.toSmartIri.toSparql} instead")
            }

            // The match function must be the top-level expression, otherwise boolean logic won't work properly.
            if (!isTopLevel) {
                throw GravsearchException(s"Function ${functionIri.toSparql} must be the top-level expression in a FILTER")
            }

            // two arguments are expected: the first must be a variable representing a string value,
            // the second must be a string literal

            if (functionCallExpression.args.size != 2) throw GravsearchException(s"Two arguments are expected for ${functionIri.toSparql}")

            // a QueryVariable expected to represent a string
            val stringVar: QueryVariable = functionCallExpression.getArgAsQueryVar(pos = 0)

            val searchTermStr: XsdLiteral = functionCallExpression.getArgAsLiteral(1, xsdDatatype = OntologyConstants.Xsd.String.toSmartIri)

            // combine search terms with a logical AND (Lucene syntax)
            val searchTerms: CombineSearchTerms = CombineSearchTerms(searchTermStr.value)

            TransformedFilterPattern(
                None, // FILTER has been replaced by statements
                Seq(
                    StatementPattern.makeExplicit(subj = stringVar, pred = IriRef(OntologyConstants.KnoraBase.MatchesTextIndex.toSmartIri), XsdLiteral(searchTerms.combineSearchTermsWithLogicalAnd, OntologyConstants.Xsd.String.toSmartIri))
                )
            )
        }

        /**
          * Handles the function `knora-api:matchInStandoff`.
          *
          * @param functionCallExpression the function call to be handled.
          * @param typeInspectionResult   the type inspection results.
          * @param isTopLevel             if `true`, this is the top-level expression in the `FILTER`.
          * @return a [[TransformedFilterPattern]].
          */
        private def handleMatchInStandoffFunction(functionCallExpression: FunctionCallExpression, typeInspectionResult: GravsearchTypeInspectionResult, isTopLevel: Boolean): TransformedFilterPattern = {
            val functionIri: SmartIri = functionCallExpression.functionIri.iri

            if (querySchema == ApiV2Simple) {
                throw GravsearchException(s"Function ${functionIri.toSparql} cannot be used in a Gravsearch query written in the simple schema")
            }

            if (!isTopLevel) {
                throw GravsearchException(s"Function ${functionIri.toSparql} must be the top-level expression in a FILTER")
            }

            // Three arguments are expected:
            // 1. a variable representing the string literal value of the text value
            // 2. a variable representing the standoff tag
            // 3. a string literal containing space-separated search terms

            if (functionCallExpression.args.size != 3) throw GravsearchException(s"Three arguments are expected for ${functionIri.toSparql}")

            // A variable representing the object of the text value's valueHasString.
            val textValueStringLiteralVar: QueryVariable = functionCallExpression.getArgAsQueryVar(pos = 0)

            // A variable representing the standoff tag.
            val standoffTagVar: QueryVariable = functionCallExpression.getArgAsQueryVar(pos = 1)

            // A string literal representing the search terms.
            val searchTermStr: XsdLiteral = functionCallExpression.getArgAsLiteral(pos = 2, xsdDatatype = OntologyConstants.Xsd.String.toSmartIri)

            // Combine the search terms with logical AND (Lucene syntax).
            val searchTerms: CombineSearchTerms = CombineSearchTerms(searchTermStr.value)

            // Generate a statement to search the full-text search index, to assert that text value contains
            // the search terms.
            val fullTextSearchStatement: Seq[StatementPattern] = Seq(StatementPattern.makeInferred(subj = textValueStringLiteralVar, pred = IriRef(OntologyConstants.KnoraBase.MatchesTextIndex.toSmartIri), XsdLiteral(searchTerms.combineSearchTermsWithLogicalAnd, OntologyConstants.Xsd.String.toSmartIri)))

            // Generate query patterns to assign the text in the standoff tag to a variable, if we
            // haven't done so already.

            val startVariable = QueryVariable(standoffTagVar.variableName + "__start")
            val endVariable = QueryVariable(standoffTagVar.variableName + "__end")
            val markedUpVariable = QueryVariable(standoffTagVar.variableName + "__markedUp")

            val markedUpPatternsToAdd: Seq[QueryPattern] = if (!standoffMarkedUpVariables.contains(markedUpVariable)) {
                standoffMarkedUpVariables += markedUpVariable

                Seq(
                    // ?standoffTag knora-base:standoffTagHasStart ?standoffTag__start .
                    StatementPattern.makeExplicit(standoffTagVar, IriRef(OntologyConstants.KnoraBase.StandoffTagHasStart.toSmartIri), startVariable),
                    // ?standoffTag knora-base:standoffTagHasEnd ?standoffTag__end .
                    StatementPattern.makeExplicit(standoffTagVar, IriRef(OntologyConstants.KnoraBase.StandoffTagHasEnd.toSmartIri), endVariable),
                    // BIND(SUBSTR(?textValueStr, ?standoffTag__start + 1, ?standoffTag__end - ?standoffTag__start) AS ?standoffTag__markedUp)
                    BindPattern(
                        variable = markedUpVariable,
                        expression = SubStrFunction(
                            textLiteralVar = textValueStringLiteralVar,
                            startExpression = ArithmeticExpression(
                                leftArg = startVariable,
                                operator = PlusOperator,
                                rightArg = IntegerLiteral(1)
                            ),
                            lengthExpression = ArithmeticExpression(
                                leftArg = endVariable,
                                operator = MinusOperator,
                                rightArg = startVariable
                            )
                        )
                    )
                )
            } else {
                Seq.empty[QueryPattern]
            }

            // Generate a FILTER pattern for each search term, using the regex function to assert that the text in the
            // standoff tag contains the term:
            // FILTER REGEX(?standoffTag__markedUp, 'term', "i")
            // TODO: handle the differences between regex syntax and Lucene syntax.
            val regexFilters: Seq[FilterPattern] = searchTerms.terms.map {
                term =>
                    FilterPattern(
                        expression = RegexFunction(
                            textVar = markedUpVariable,
                            pattern = term,
                            modifier = Some("i")
                        )
                    )
            }

            TransformedFilterPattern(
                expression = None, // The expression has been replaced by additional patterns.
                additionalPatterns = fullTextSearchStatement ++ markedUpPatternsToAdd ++ regexFilters
            )
        }

        /**
          * Handles the function `knora-api:StandoffLink`.
          *
          * @param functionCallExpression the function call to be handled.
          * @param typeInspectionResult   the type inspection results.
          * @param isTopLevel             if `true`, this is the top-level expression in the `FILTER`.
          * @return a [[TransformedFilterPattern]].
          */
        private def handleStandoffLinkFunction(functionCallExpression: FunctionCallExpression, typeInspectionResult: GravsearchTypeInspectionResult, isTopLevel: Boolean): TransformedFilterPattern = {
            val functionIri: SmartIri = functionCallExpression.functionIri.iri

            if (querySchema == ApiV2Simple) {
                throw GravsearchException(s"Function ${functionIri.toSparql} cannot be used in a Gravsearch query written in the simple schema")
            }

            if (!isTopLevel) {
                throw GravsearchException(s"Function ${functionIri.toSparql} must be the top-level expression in a FILTER")
            }

            // Three arguments are expected:
            // 1. a variable or IRI representing the resource that is the source of the link
            // 2. a variable representing the standoff link tag
            // 3. a variable or IRI the resource that is the target of the link

            if (functionCallExpression.args.size != 3) throw GravsearchException(s"Three arguments are expected for ${functionIri.toSparql}")

            // A variable or IRI representing the resource that is the source of the link.
            val linkSourceEntity = functionCallExpression.args.head match {
                case queryVar: QueryVariable => queryVar
                case iriRef: IriRef => iriRef
                case _ => throw GravsearchException(s"The first argument of ${functionIri.toSparql} must be a variable or IRI")
            }

            typeInspectionResult.getTypeOfEntity(linkSourceEntity) match {
                case Some(NonPropertyTypeInfo(typeIri)) if OntologyConstants.KnoraApi.isKnoraApiV2Resource(typeIri) => ()
                case _ => throw GravsearchException(s"The first argument of ${functionIri.toSparql} must represent a knora-api:Resource")
            }

            // A variable representing the standoff link tag.
            val standoffTagVar = functionCallExpression.getArgAsQueryVar(pos = 1)

            typeInspectionResult.getTypeOfEntity(standoffTagVar) match {
                case Some(NonPropertyTypeInfo(typeIri)) if typeIri.toString == OntologyConstants.KnoraApiV2WithValueObjects.StandoffTag => ()
                case _ => throw GravsearchException(s"The second argument of ${functionIri.toSparql} must represent a knora-api:StandoffTag")
            }

            val linkTargetEntity = functionCallExpression.args(2) match {
                case queryVar: QueryVariable => queryVar
                case iriRef: IriRef => iriRef
                case _ => throw GravsearchException(s"The third argument of ${functionIri.toSparql} must be a variable or IRI")
            }

            typeInspectionResult.getTypeOfEntity(linkTargetEntity) match {
                case Some(NonPropertyTypeInfo(typeIri)) if OntologyConstants.KnoraApi.isKnoraApiV2Resource(typeIri) => ()
                case _ => throw GravsearchException(s"The third argument of ${functionIri.toSparql} must represent a knora-api:Resource")
            }

            val hasStandoffLinkToIriRef = IriRef(OntologyConstants.KnoraBase.HasStandoffLinkTo.toSmartIri)

            // Generate statements linking the source resource and the standoff tag to the target resource.
            val linkStatements = Seq(
                StatementPattern.makeExplicit(subj = linkSourceEntity, pred = hasStandoffLinkToIriRef, obj = linkTargetEntity),
                StatementPattern.makeInferred(subj = standoffTagVar, pred = IriRef(OntologyConstants.KnoraBase.StandoffTagHasLink.toSmartIri), obj = linkTargetEntity)
            )

            // Generate statements matching the link value that describes the standoff link between the source and target resources.
            val statementsForLinkValue: Seq[StatementPattern] = generateStatementsForLinkValue(
                linkSource = linkSourceEntity,
                linkPred = hasStandoffLinkToIriRef,
                linkTarget = linkTargetEntity
            )

            TransformedFilterPattern(
                None, // FILTER has been replaced with statements
                linkStatements ++ statementsForLinkValue
            )
        }

        /**
          *
          * Handles a Gravsearch-specific function call in a [[FilterPattern]].
          *
          * @param functionCallExpression the function call to be handled.
          * @param typeInspectionResult   the type inspection results.
          * @param isTopLevel             if `true`, this is the top-level expression in the `FILTER`.
          * @return a [[TransformedFilterPattern]].
          */
        private def handleKnoraFunctionCall(functionCallExpression: FunctionCallExpression, typeInspectionResult: GravsearchTypeInspectionResult, isTopLevel: Boolean): TransformedFilterPattern = {
            val functionIri: SmartIri = functionCallExpression.functionIri.iri

            functionIri.toString match {

                case OntologyConstants.KnoraApiV2Simple.MatchFunctionIri =>
                    handleMatchFunctionInSimpleSchema(
                        functionCallExpression = functionCallExpression,
                        typeInspectionResult = typeInspectionResult,
                        isTopLevel = isTopLevel
                    )

                case OntologyConstants.KnoraApiV2WithValueObjects.MatchFunction =>
                    handleMatchFunctionInComplexSchema(
                        functionCallExpression = functionCallExpression,
                        typeInspectionResult = typeInspectionResult,
                        isTopLevel = isTopLevel
                    )

                case OntologyConstants.KnoraApiV2WithValueObjects.MatchInStandoffFunction =>
                    handleMatchInStandoffFunction(
                        functionCallExpression = functionCallExpression,
                        typeInspectionResult = typeInspectionResult,
                        isTopLevel = isTopLevel
                    )

                case OntologyConstants.KnoraApiV2WithValueObjects.StandoffLinkFunction =>
                    handleStandoffLinkFunction(
                        functionCallExpression = functionCallExpression,
                        typeInspectionResult = typeInspectionResult,
                        isTopLevel = isTopLevel
                    )

                case _ => throw NotImplementedException(s"Function ${functionCallExpression.functionIri} not found")
            }

        }

        /**
          * Handles the `knora-api:toSimpleDate` function in a comparison.
          *
          * @param filterCompare        the comparison expression.
          * @param functionCallExpr     the function call expression.
          * @param typeInspectionResult the type inspection result.
          * @return a [[TransformedFilterPattern]].
          */
        private def handleToSimpleDateFunction(filterCompare: CompareExpression, functionCallExpr: FunctionCallExpression, typeInspectionResult: GravsearchTypeInspectionResult): TransformedFilterPattern = {
            if (querySchema == ApiV2Simple) {
                throw GravsearchException(s"Function ${functionCallExpr.functionIri.toSparql} cannot be used in a query written in the simple schema")
            }

            if (functionCallExpr.args.size != 1) throw GravsearchException(s"One argument is expected for ${functionCallExpr.functionIri.toSparql}")

            // A QueryVariable expected to represent a date value.
            val dateValueVar: QueryVariable = functionCallExpr.getArgAsQueryVar(pos = 0)

            typeInspectionResult.getTypeOfEntity(dateValueVar) match {
                case Some(nonPropInfo: NonPropertyTypeInfo) =>
                    if (nonPropInfo.typeIri.toString != OntologyConstants.KnoraApiV2WithValueObjects.DateValue) {
                        throw GravsearchException(s"${dateValueVar.toSparql} must be a ${OntologyConstants.KnoraApiV2WithValueObjects.DateValue.toSmartIri.toSparql}")
                    }

                case _ => GravsearchException(s"${dateValueVar.toSparql} must be a ${OntologyConstants.KnoraApiV2WithValueObjects.DateValue.toSmartIri.toSparql}")
            }

            handleDateQueryVar(queryVar = dateValueVar, comparisonOperator = filterCompare.operator, dateValueExpression = filterCompare.rightArg)
        }

        /**
          * Transforms a Filter expression provided in the input query (knora-api simple) into a knora-base compliant Filter expression.
          *
          * @param filterExpression     the `FILTER` expression to be transformed.
          * @param typeInspectionResult the results of type inspection.
          * @return a [[TransformedFilterPattern]].
          */
        protected def transformFilterPattern(filterExpression: Expression, typeInspectionResult: GravsearchTypeInspectionResult, isTopLevel: Boolean): TransformedFilterPattern = {

            filterExpression match {

                case filterCompare: CompareExpression =>

                    // left argument of a CompareExpression must be a QueryVariable or a function call
                    filterCompare.leftArg match {

                        case queryVar: QueryVariable =>
                            handleQueryVar(queryVar = queryVar, compareExpression = filterCompare, typeInspectionResult = typeInspectionResult)

                        case functionCallExpr: FunctionCallExpression if functionCallExpr.functionIri.iri.toString == OntologyConstants.KnoraApiV2WithValueObjects.ToSimpleDateFunction =>
                            handleToSimpleDateFunction(
                                filterCompare = filterCompare,
                                functionCallExpr = functionCallExpr,
                                typeInspectionResult = typeInspectionResult
                            )

                        case lang: LangFunction =>
                            handleLangFunctionCall(langFunctionCall = lang, compareExpression = filterCompare, typeInspectionResult = typeInspectionResult)

                        case other => throw GravsearchException(s"Invalid left argument ${other.toSparql} in comparison")
                    }


                case filterOr: OrExpression =>
                    // recursively call this method for both arguments
                    val filterExpressionLeft: TransformedFilterPattern = transformFilterPattern(filterOr.leftArg, typeInspectionResult, isTopLevel = false)
                    val filterExpressionRight: TransformedFilterPattern = transformFilterPattern(filterOr.rightArg, typeInspectionResult, isTopLevel = false)

                    // recreate Or expression and include additional statements
                    TransformedFilterPattern(
                        Some(OrExpression(filterExpressionLeft.expression.getOrElse(throw DataConversionException("Expression was expected from previous FILTER conversion, but None given")), filterExpressionRight.expression.getOrElse(throw DataConversionException("Expression was expected from previous FILTER conversion, but None given")))),
                        filterExpressionLeft.additionalPatterns ++ filterExpressionRight.additionalPatterns
                    )


                case filterAnd: AndExpression =>
                    // recursively call this method for both arguments
                    val filterExpressionLeft: TransformedFilterPattern = transformFilterPattern(filterAnd.leftArg, typeInspectionResult, isTopLevel = false)
                    val filterExpressionRight: TransformedFilterPattern = transformFilterPattern(filterAnd.rightArg, typeInspectionResult, isTopLevel = false)

                    // recreate And expression and include additional statements
                    TransformedFilterPattern(
                        Some(AndExpression(filterExpressionLeft.expression.getOrElse(throw DataConversionException("Expression was expected from previous FILTER conversion, but None given")), filterExpressionRight.expression.getOrElse(throw DataConversionException("Expression was expected from previous FILTER conversion, but None given")))),
                        filterExpressionLeft.additionalPatterns ++ filterExpressionRight.additionalPatterns
                    )

                case regexFunction: RegexFunction =>

                    handleRegexFunctionCall(regexFunctionCall = regexFunction, typeInspectionResult = typeInspectionResult)

                case functionCall: FunctionCallExpression =>

                    handleKnoraFunctionCall(functionCallExpression = functionCall, typeInspectionResult, isTopLevel = isTopLevel)

                case other => throw NotImplementedException(s"$other not supported as FilterExpression")
            }

        }

    }

    /**
      * Transform the the Knora explicit graph name to GraphDB explicit graph name.
      *
      * @param statement the given statement whose graph name has to be renamed.
      * @return the statement with the renamed graph, if given.
      */
    private def transformKnoraExplicitToGraphDBExplicit(statement: StatementPattern): Seq[StatementPattern] = {
        val transformedPattern = statement.copy(
            pred = statement.pred match {
                case iri: IriRef if iri.iri == OntologyConstants.KnoraBase.MatchesTextIndex.toSmartIri => IriRef(OntologyConstants.Ontotext.LuceneFulltext.toSmartIri) // convert to special Lucene property
                case other => other // no conversion needed
            },
            namedGraph = statement.namedGraph match {
                case Some(IriRef(SmartIri(OntologyConstants.NamedGraphs.KnoraExplicitNamedGraph), _)) => Some(IriRef(OntologyConstants.NamedGraphs.GraphDBExplicitNamedGraph.toSmartIri))
                case Some(IriRef(_, _)) => throw AssertionException(s"Named graphs other than ${OntologyConstants.NamedGraphs.KnoraExplicitNamedGraph} cannot occur in non-triplestore-specific generated search query SPARQL")
                case None => None
            }
        )

        Seq(transformedPattern)
    }

    private class GraphDBSelectToSelectTransformer extends SelectToSelectTransformer {
        def transformStatementInSelect(statementPattern: StatementPattern): Seq[StatementPattern] = Seq(statementPattern)

        def transformStatementInWhere(statementPattern: StatementPattern, inputOrderBy: Seq[OrderCriterion]): Seq[StatementPattern] = {
            transformKnoraExplicitToGraphDBExplicit(statementPattern)
        }

        def transformFilter(filterPattern: FilterPattern): Seq[QueryPattern] = Seq(filterPattern)

    }

    private class NoInferenceSelectToSelectTransformer extends SelectToSelectTransformer {
        def transformStatementInSelect(statementPattern: StatementPattern): Seq[StatementPattern] = Seq(statementPattern)

        def transformStatementInWhere(statementPattern: StatementPattern, inputOrderBy: Seq[OrderCriterion]): Seq[StatementPattern] = {
            // TODO: if OntologyConstants.NamedGraphs.KnoraExplicitNamedGraph occurs, remove it and use property path syntax to emulate inference.
            Seq(statementPattern)
        }

        def transformFilter(filterPattern: FilterPattern): Seq[QueryPattern] = Seq(filterPattern)

    }

    /**
      * Transforms non-triplestore-specific query patterns to GraphDB-specific ones.
      */
    private class GraphDBConstructToConstructTransformer extends ConstructToConstructTransformer {
        def transformStatementInConstruct(statementPattern: StatementPattern): Seq[StatementPattern] = Seq(statementPattern)

        def transformStatementInWhere(statementPattern: StatementPattern, inputOrderBy: Seq[OrderCriterion]): Seq[StatementPattern] = {
            transformKnoraExplicitToGraphDBExplicit(statementPattern)
        }

        def transformFilter(filterPattern: FilterPattern): Seq[QueryPattern] = Seq(filterPattern)
    }

    /**
      * Transforms non-triplestore-specific query patterns for a triplestore that does not have inference enabled.
      */
    private class NoInferenceConstructToConstructTransformer extends ConstructToConstructTransformer {
        def transformStatementInConstruct(statementPattern: StatementPattern): Seq[StatementPattern] = Seq(statementPattern)

        def transformStatementInWhere(statementPattern: StatementPattern, inputOrderBy: Seq[OrderCriterion]): Seq[StatementPattern] = {
            // TODO: if OntologyConstants.NamedGraphs.KnoraExplicitNamedGraph occurs, remove it and use property path syntax to emulate inference.
            Seq(statementPattern)
        }

        def transformFilter(filterPattern: FilterPattern): Seq[QueryPattern] = Seq(filterPattern)
    }

    /**
      * Creates a syntactically valid variable base name, based on the given entity.
      *
      * @param entity the entity to be used to create a base name for a variable.
      * @return a base name for a variable.
      */
    private def escapeEntityForVariable(entity: Entity): String = {
        val entityStr = entity match {
            case QueryVariable(varName) => varName
            case IriRef(iriLiteral, _) => iriLiteral.toString
            case XsdLiteral(stringLiteral, _) => stringLiteral
            case _ => throw GravsearchException(s"A unique variable name could not be made for ${entity.toSparql}")
        }

        entityStr.replaceAll("[:/.#-]", "").replaceAll("\\s", "") // TODO: check if this is complete and if it could lead to collision of variable names
    }

    /**
      * Creates a unique variable name from the given entity and the local part of a property IRI.
      *
      * @param base        the entity to use to create the variable base name.
      * @param propertyIri the IRI of the property whose local part will be used to form the unique name.
      * @return a unique variable.
      */
    private def createUniqueVariableNameFromEntityAndProperty(base: Entity, propertyIri: IRI): QueryVariable = {
        val propertyHashIndex = propertyIri.lastIndexOf('#')

        if (propertyHashIndex > 0) {
            val propertyName = propertyIri.substring(propertyHashIndex + 1)
            QueryVariable(escapeEntityForVariable(base) + "__" + escapeEntityForVariable(QueryVariable(propertyName)))
        } else {
            throw AssertionException(s"Invalid property IRI: $propertyIri")
        }
    }

    /**
      * Represents the IRIs of resources and value objects.
      *
      * @param resourceIris    resource IRIs.
      * @param valueObjectIris value object IRIs.
      */
    private case class ResourceIrisAndValueObjectIris(resourceIris: Set[IRI], valueObjectIris: Set[IRI])

    /**
      * Traverses value property assertions and returns the IRIs of the value objects and the dependent resources, recursively traversing their value properties as well.
      * This is method is needed in order to determine if the full query path is still present in the results after permissions checking handled in [[ConstructResponseUtilV2.splitMainResourcesAndValueRdfData]].
      * Due to insufficient permissions, some of the resources (both main and dependent resources) and/or values may have been filtered out.
      *
      * @param valuePropertyAssertions the assertions to be traversed.
      * @return a [[ResourceIrisAndValueObjectIris]] representing all resource and value object IRIs that have been found in `valuePropertyAssertions`.
      */
    private def traverseValuePropertyAssertions(valuePropertyAssertions: Map[IRI, Seq[ConstructResponseUtilV2.ValueRdfData]]): ResourceIrisAndValueObjectIris = {

        // look at the value objects and ignore the property IRIs (we are only interested in value instances)
        val resAndValObjIris: Seq[ResourceIrisAndValueObjectIris] = valuePropertyAssertions.values.flatten.foldLeft(Seq.empty[ResourceIrisAndValueObjectIris]) {
            (acc: Seq[ResourceIrisAndValueObjectIris], assertion) =>

                if (assertion.nestedResource.nonEmpty) {
                    // this is a link value
                    // recursively traverse the dependent resource's values

                    val dependentRes: ConstructResponseUtilV2.ResourceWithValueRdfData = assertion.nestedResource.get

                    // recursively traverse the link value's nested resource and its assertions
                    val resAndValObjIrisForDependentRes: ResourceIrisAndValueObjectIris = traverseValuePropertyAssertions(dependentRes.valuePropertyAssertions)
                    // get the dependent resource's IRI from the current link value's rdf:object or rdf:subject in case of an incoming link
                    val dependentResIri: IRI = if (!assertion.incomingLink) {
                        assertion.assertions.getOrElse(OntologyConstants.Rdf.Object, throw InconsistentTriplestoreDataException(s"expected ${OntologyConstants.Rdf.Object} for link value ${assertion.valueObjectIri}"))
                    } else {
                        assertion.assertions.getOrElse(OntologyConstants.Rdf.Subject, throw InconsistentTriplestoreDataException(s"expected ${OntologyConstants.Rdf.Subject} for link value ${assertion.valueObjectIri}"))
                    }
                    // append results from recursion and current value object
                    ResourceIrisAndValueObjectIris(
                        resourceIris = resAndValObjIrisForDependentRes.resourceIris + dependentResIri,
                        valueObjectIris = resAndValObjIrisForDependentRes.valueObjectIris + assertion.valueObjectIri
                    ) +: acc
                } else {
                    // not a link value or no dependent resource given (in order to avoid infinite recursion)
                    // no dependent resource present
                    // append results for current value object
                    ResourceIrisAndValueObjectIris(
                        resourceIris = Set.empty[IRI],
                        valueObjectIris = Set(assertion.valueObjectIri)
                    ) +: acc
                }
        }

        // convert the collection of `ResourceIrisAndValueObjectIris` into one
        ResourceIrisAndValueObjectIris(
            resourceIris = resAndValObjIris.flatMap(_.resourceIris).toSet,
            valueObjectIris = resAndValObjIris.flatMap(_.valueObjectIris).toSet
        )

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

        import SearchResponderV2Constants.FullTextSearchConstants._

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
            queryResultsSeparatedWithFullQueryPath: Map[IRI, ConstructResponseUtilV2.ResourceWithValueRdfData] <- if (resourceIris.nonEmpty) {

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
                    // this ensures that the user has sufficient permissions on the whole query path
                    queryResWithFullQueryPath = queryResultsSep.foldLeft(Map.empty[IRI, ConstructResponseUtilV2.ResourceWithValueRdfData]) {
                        case (acc: Map[IRI, ConstructResponseUtilV2.ResourceWithValueRdfData], (mainResIri: IRI, values: ConstructResponseUtilV2.ResourceWithValueRdfData)) =>

                            valueObjectIrisPerResource.get(mainResIri) match {

                                case Some(valObjIris) =>

                                    // check for presence of value objects: valueObjectIrisPerResource
                                    val expectedValueObjects: Set[IRI] = valueObjectIrisPerResource(mainResIri)

                                    // value property assertions for the current resource
                                    val valuePropAssertions: Map[IRI, Seq[ConstructResponseUtilV2.ValueRdfData]] = values.valuePropertyAssertions

                                    // all value objects contained in `valuePropAssertions`
                                    val resAndValueObjIris: ResourceIrisAndValueObjectIris = traverseValuePropertyAssertions(valuePropAssertions)

                                    // check if the client has sufficient permissions on all value objects IRIs present in the query path
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

                } yield queryResWithFullQueryPath
            } else {

                // the prequery returned no results, no further query is necessary
                Future(Map.empty[IRI, ConstructResponseUtilV2.ResourceWithValueRdfData])
            }

            // check if there are resources the user does not have sufficient permissions to see
            forbiddenResourceOption: Option[ReadResourceV2] <- if (resourceIris.size > queryResultsSeparatedWithFullQueryPath.size) {
                // some of the main resources have been suppressed, represent them using the forbidden resource

                getForbiddenResource(requestingUser)
            } else {
                // all resources visible, no need for the forbidden resource
                Future(None)
            }

            // get the mappings
            mappingsAsMap <- getMappingsFromQueryResultsSeparated(queryResultsSeparatedWithFullQueryPath, requestingUser)

            // _ = println(mappingsAsMap)


        } yield ReadResourcesSequenceV2(
            numberOfResources = resourceIris.size,
            resources = ConstructResponseUtilV2.createSearchResponse(
                searchResults = queryResultsSeparatedWithFullQueryPath,
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

        /**
          * Transforms a preprocessed CONSTRUCT query into a SELECT query that returns only the IRIs and sort order of the main resources that matched
          * the search criteria. This query will be used to get resource IRIs for a single page of results. These IRIs will be included in a CONSTRUCT
          * query to get the actual results for the page.
          *
          * @param typeInspectionResult the result of type inspection of the original query.
          */
        class NonTriplestoreSpecificConstructToSelectTransformer(typeInspectionResult: GravsearchTypeInspectionResult, querySchema: ApiV2Schema) extends AbstractSparqlTransformer(typeInspectionResult, querySchema) with ConstructToSelectTransformer {

            def handleStatementInConstruct(statementPattern: StatementPattern): Unit = {
                // Just identify the main resource variable and put it in mainResourceVariable.

                isMainResourceVariable(statementPattern) match {
                    case Some(queryVariable: QueryVariable) => mainResourceVariable = Some(queryVariable)
                    case None => ()
                }
            }

            def transformStatementInWhere(statementPattern: StatementPattern, inputOrderBy: Seq[OrderCriterion]): Seq[QueryPattern] = {

                // Include any statements needed to meet the user's search criteria, but not statements that would be needed for permission checking or
                // other information about the matching resources or values.

                processStatementPatternFromWhereClause(
                    statementPattern = statementPattern,
                    inputOrderBy = inputOrderBy
                )

            }

            def transformFilter(filterPattern: FilterPattern): Seq[QueryPattern] = {
                val filterExpression: TransformedFilterPattern = transformFilterPattern(filterPattern.expression, typeInspectionResult = typeInspectionResult, isTopLevel = true)

                filterExpression.expression match {
                    case Some(expression: Expression) => filterExpression.additionalPatterns :+ FilterPattern(expression)

                    case None => filterExpression.additionalPatterns // no FILTER expression given
                }

            }

            def getSelectVariables: Seq[SelectQueryColumn] = {

                val mainResVar = mainResourceVariable match {
                    case Some(mainVar: QueryVariable) => mainVar

                    case None => throw GravsearchException(s"No ${OntologyConstants.KnoraBase.IsMainResource.toSmartIri.toSparql} found in CONSTRUCT query.")
                }

                // return count aggregation function for main variable
                Seq(Count(inputVariable = mainResVar, distinct = true, outputVariableName = "count"))
            }

            def getGroupBy(orderByCriteria: TransformedOrderBy): Seq[QueryVariable] = {
                Seq.empty[QueryVariable]
            }

            def getOrderBy(inputOrderBy: Seq[OrderCriterion]): TransformedOrderBy = {
                // empty by default
                TransformedOrderBy()
            }

            def getLimit: Int = 1 // one row expected for count query

            def getOffset(inputQueryOffset: Long, limit: Int): Long = {
                // count queries do not consider offsets since there is only one result row
                0
            }

        }


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
          * Transforms a preprocessed CONSTRUCT query into a SELECT query that returns only the IRIs and sort order of the main resources that matched
          * the search criteria. This query will be used to get resource IRIs for a single page of results. These IRIs will be included in a CONSTRUCT
          * query to get the actual results for the page.
          *
          * @param typeInspectionResult the result of type inspection of the original query.
          */
        class NonTriplestoreSpecificConstructToSelectTransformer(typeInspectionResult: GravsearchTypeInspectionResult, querySchema: ApiV2Schema) extends AbstractSparqlTransformer(typeInspectionResult, querySchema) with ConstructToSelectTransformer {

            /**
              * Collects information from a statement pattern in the CONSTRUCT clause of the input query, e.g. variables
              * that need to be returned by the SELECT.
              *
              * @param statementPattern the statement to be handled.
              */
            override def handleStatementInConstruct(statementPattern: StatementPattern): Unit = {
                // Just identify the main resource variable and put it in mainResourceVariable.

                isMainResourceVariable(statementPattern) match {
                    case Some(queryVariable: QueryVariable) => mainResourceVariable = Some(queryVariable)
                    case None => ()
                }

            }

            /**
              * Transforms a [[StatementPattern]] in a WHERE clause into zero or more query patterns.
              *
              * @param statementPattern the statement to be transformed.
              * @param inputOrderBy     the ORDER BY clause in the input query.
              * @return the result of the transformation.
              */
            override def transformStatementInWhere(statementPattern: StatementPattern, inputOrderBy: Seq[OrderCriterion]): Seq[QueryPattern] = {
                // Include any statements needed to meet the user's search criteria, but not statements that would be needed for permission checking or
                // other information about the matching resources or values.

                processStatementPatternFromWhereClause(
                    statementPattern = statementPattern,
                    inputOrderBy = inputOrderBy
                )
            }

            /**
              * Transforms a [[FilterPattern]] in a WHERE clause into zero or more statement patterns.
              *
              * @param filterPattern the filter to be transformed.
              * @return the result of the transformation.
              */
            override def transformFilter(filterPattern: FilterPattern): Seq[QueryPattern] = {

                val filterExpression: TransformedFilterPattern = transformFilterPattern(filterPattern.expression, typeInspectionResult = typeInspectionResult, isTopLevel = true)

                filterExpression.expression match {
                    case Some(expression: Expression) => filterExpression.additionalPatterns :+ FilterPattern(expression)

                    case None => filterExpression.additionalPatterns // no FILTER expression given
                }

            }

            /**
              * Returns the variables that should be included in the results of the SELECT query. This method will be called
              * by [[QueryTraverser]] after the whole input query has been traversed.
              *
              * @return the variables that should be returned by the SELECT.
              */
            override def getSelectVariables: Seq[SelectQueryColumn] = {
                // Return the main resource variable and the generated variable that we're using for ordering.

                val dependentResourceGroupConcat: Set[GroupConcat] = dependentResourceVariables.map {
                    dependentResVar: QueryVariable =>
                        GroupConcat(inputVariable = dependentResVar,
                            separator = groupConcatSeparator,
                            outputVariableName = dependentResVar.variableName + groupConcatVariableSuffix)
                }.toSet

                dependentResourceVariablesGroupConcat = dependentResourceGroupConcat.map(_.outputVariable)

                val valueObjectGroupConcat = valueObjectVariables.map {
                    valueObjVar: QueryVariable =>
                        GroupConcat(inputVariable = valueObjVar,
                            separator = groupConcatSeparator,
                            outputVariableName = valueObjVar.variableName + groupConcatVariableSuffix)
                }.toSet

                valueObjectVarsGroupConcat = valueObjectGroupConcat.map(_.outputVariable)

                mainResourceVariable match {
                    case Some(mainVar: QueryVariable) => Seq(mainVar) ++ dependentResourceGroupConcat ++ valueObjectGroupConcat

                    case None => throw GravsearchException(s"No ${OntologyConstants.KnoraBase.IsMainResource.toSmartIri.toSparql} found in CONSTRUCT query.")
                }

            }

            /**
              * Returns the criteria, if any, that should be used in the ORDER BY clause of the SELECT query. This method will be called
              * by [[QueryTraverser]] after the whole input query has been traversed.
              *
              * @return the ORDER BY criteria, if any.
              */
            override def getOrderBy(inputOrderBy: Seq[OrderCriterion]): TransformedOrderBy = {

                val transformedOrderBy = inputOrderBy.foldLeft(TransformedOrderBy()) {
                    case (acc, criterion) =>
                        // Did a FILTER already generate a unique variable for the literal value of this value object?

                        getGeneratedVariableForValueLiteralInOrderBy(criterion.queryVariable) match {
                            case Some(generatedVariable) =>
                                // Yes. Use the already generated variable in the ORDER BY.
                                acc.copy(
                                    orderBy = acc.orderBy :+ OrderCriterion(queryVariable = generatedVariable, isAscending = criterion.isAscending)
                                )

                            case None =>
                                // No. Generate such a variable and generate an additional statement to get its literal value in the WHERE clause.

                                val propertyIri: SmartIri = typeInspectionResult.getTypeOfEntity(criterion.queryVariable) match {
                                    case Some(nonPropertyTypeInfo: NonPropertyTypeInfo) =>
                                        valueTypesToValuePredsForOrderBy.getOrElse(nonPropertyTypeInfo.typeIri.toString, throw GravsearchException(s"${criterion.queryVariable.toSparql} cannot be used in ORDER BY")).toSmartIri

                                    case Some(_) => throw GravsearchException(s"Variable ${criterion.queryVariable.toSparql} represents a property, and therefore cannot be used in ORDER BY")

                                    case None => throw GravsearchException(s"No type information found for ${criterion.queryVariable.toSparql}")
                                }

                                // Generate the variable name.
                                val variableForLiteral: QueryVariable = createUniqueVariableNameFromEntityAndProperty(criterion.queryVariable, propertyIri.toString)

                                // Generate a statement to get the literal value.
                                val statementPattern = StatementPattern.makeExplicit(subj = criterion.queryVariable, pred = IriRef(propertyIri), obj = variableForLiteral)

                                acc.copy(
                                    statementPatterns = acc.statementPatterns :+ statementPattern,
                                    orderBy = acc.orderBy :+ OrderCriterion(queryVariable = variableForLiteral, isAscending = criterion.isAscending)
                                )
                        }
                }

                // main resource variable as order by criterion
                val orderByMainResVar: OrderCriterion = OrderCriterion(
                    queryVariable = mainResourceVariable.getOrElse(throw GravsearchException(s"No ${OntologyConstants.KnoraBase.IsMainResource.toSmartIri.toSparql} found in CONSTRUCT query")),
                    isAscending = true
                )

                // order by: user provided variables and main resource variable
                // all variables present in the GROUP BY must be included in the order by statements to make the results predictable for paging
                transformedOrderBy.copy(
                    orderBy = transformedOrderBy.orderBy :+ orderByMainResVar
                )
            }

            /**
              * Creates the GROUP BY statement based on the ORDER BY statement.
              *
              * @param orderByCriteria the criteria used to sort the query results. They have to be included in the GROUP BY statement, otherwise they are unbound.
              * @return a list of variables that the result rows are grouped by.
              */
            def getGroupBy(orderByCriteria: TransformedOrderBy): Seq[QueryVariable] = {
                // get they query variables form the order by criteria and return them in reverse order:
                // main resource variable first, followed by other sorting criteria, if any.
                orderByCriteria.orderBy.map(_.queryVariable).reverse
            }

            /**
              * Gets the maximal amount of result rows to be returned by the prequery.
              *
              * @return the LIMIT, if any.
              */
            def getLimit: Int = {
                // get LIMIT from settings
                settings.v2ResultsPerPage
            }

            /**
              * Gets the OFFSET to be used in the prequery (needed for paging).
              *
              * @param inputQueryOffset the OFFSET provided in the input query.
              * @param limit            the maximum amount of result rows to be returned by the prequery.
              * @return the OFFSET.
              */
            def getOffset(inputQueryOffset: Long, limit: Int): Long = {

                if (inputQueryOffset < 0) throw AssertionException("Negative OFFSET is illegal.")

                // determine offset for paging -> multiply given offset with limit (indicating the maximum amount of results per page).
                inputQueryOffset * limit

            }

        }

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

            import SearchResponderV2Constants.GravsearchConstants._

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
                querySchema = inputQuery.querySchema.getOrElse(throw AssertionException(s"WhereClause has no querySchema"))
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

            queryResultsSeparatedWithFullQueryPath: Map[IRI, ConstructResponseUtilV2.ResourceWithValueRdfData] <- if (mainResourceIris.nonEmpty) {
                // at least one resource matched the prequery

                // variables representing dependent resources
                val dependentResourceVariablesConcat: Set[QueryVariable] = nonTriplestoreSpecificConstructToSelectTransformer.getDependentResourceVariablesGroupConcat

                // get all the IRIs for variables representing dependent resources per main resource
                val dependentResourceIrisPerMainResource: Map[IRI, Set[IRI]] = prequeryResponse.results.bindings.foldLeft(Map.empty[IRI, Set[IRI]]) {
                    case (acc: Map[IRI, Set[IRI]], resultRow: VariableResultsRow) =>
                        // collect all the values for the current main resource from prequery response

                        val mainResIri: String = resultRow.rowMap(mainResourceVar.variableName)

                        val dependentResIris: Set[IRI] = dependentResourceVariablesConcat.flatMap {
                            dependentResVar: QueryVariable =>

                                // check if key exists (the variable could be contained in an OPTIONAL or a UNION)
                                val dependentResIriOption: Option[IRI] = resultRow.rowMap.get(dependentResVar.variableName)

                                dependentResIriOption match {
                                    case Some(depResIri: IRI) =>

                                        // IRIs are concatenated, split them
                                        depResIri.split(nonTriplestoreSpecificConstructToSelectTransformer.groupConcatSeparator).toSeq

                                    case None => Set.empty[IRI] // no value present
                                }

                        }

                        acc + (mainResIri -> dependentResIris)
                }

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
                val allDependentResourceIris: Set[IRI] = dependentResourceIrisPerMainResource.values.flatten.toSet ++ dependentResourceIrisFromTypeInspection

                // value objects variables present in the prequery's WHERE clause
                val valueObjectVariablesConcat = nonTriplestoreSpecificConstructToSelectTransformer.getValueObjectVarsGroupConcat

                // for each main resource, create a Map of value object variables and their values
                val valueObjectIrisPerMainResource: Map[IRI, Map[QueryVariable, Set[IRI]]] = prequeryResponse.results.bindings.foldLeft(Map.empty[IRI, Map[QueryVariable, Set[IRI]]]) {
                    (acc: Map[IRI, Map[QueryVariable, Set[IRI]]], resultRow: VariableResultsRow) =>

                        val mainResIri: String = resultRow.rowMap(mainResourceVar.variableName)

                        val valueObjVarToIris: Map[QueryVariable, Set[IRI]] = valueObjectVariablesConcat.map {
                            valueObjVarConcat: QueryVariable =>

                                // check if key exists (the variable could be contained in an OPTIONAL or a UNION)
                                val valueObjIrisOption: Option[IRI] = resultRow.rowMap.get(valueObjVarConcat.variableName)

                                val valueObjIris: Set[IRI] = valueObjIrisOption match {

                                    case Some(valObjIris) =>

                                        // IRIs are concatenated, split them
                                        valObjIris.split(nonTriplestoreSpecificConstructToSelectTransformer.groupConcatSeparator).toSet

                                    case None => Set.empty[IRI] // no value present

                                }

                                valueObjVarConcat -> valueObjIris
                        }.toMap

                        acc + (mainResIri -> valueObjVarToIris)
                }

                // collect all value objects IRIs (for all main resources and for all value object variables)
                val allValueObjectIris: Set[IRI] = valueObjectIrisPerMainResource.values.foldLeft(Set.empty[IRI]) {
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
                    searchResponse: SparqlConstructResponse <- (storeManager ? SparqlConstructRequest(triplestoreSpecificSparql)).mapTo[SparqlConstructResponse]

                    // separate main resources and value objects (dependent resources are nested)
                    queryResultsSep: Map[IRI, ConstructResponseUtilV2.ResourceWithValueRdfData] = ConstructResponseUtilV2.splitMainResourcesAndValueRdfData(constructQueryResults = searchResponse, requestingUser = requestingUser)

                    // for each main resource check if all dependent resources and value objects are still present after permission checking
                    // this ensures that the user has sufficient permissions on the whole query path
                    queryResWithFullQueryPath = queryResultsSep.foldLeft(Map.empty[IRI, ConstructResponseUtilV2.ResourceWithValueRdfData]) {
                        case (acc: Map[IRI, ConstructResponseUtilV2.ResourceWithValueRdfData], (mainResIri: IRI, values: ConstructResponseUtilV2.ResourceWithValueRdfData)) =>

                            // check for presence of dependent resources:  dependentResourceIrisPerMainResource
                            val expectedDependentResources: Set[IRI] = dependentResourceIrisPerMainResource(mainResIri)

                            // check for presence of value objects: valueObjectIrisPerMainResource
                            val expectedValueObjects: Set[IRI] = valueObjectIrisPerMainResource(mainResIri).values.flatten.toSet

                            // value property assertions for the current main resource
                            val valuePropAssertions: Map[IRI, Seq[ConstructResponseUtilV2.ValueRdfData]] = values.valuePropertyAssertions

                            // all the IRIs of dependent resources and value objects contained in `valuePropAssertions`
                            val resAndValueObjIris: ResourceIrisAndValueObjectIris = traverseValuePropertyAssertions(valuePropAssertions)

                            // check if the client has sufficient permissions on all dependent resources present in the query path
                            val allDependentResources: Boolean = resAndValueObjIris.resourceIris.intersect(expectedDependentResources) == expectedDependentResources

                            // check if the client has sufficient permissions on all value objects IRIs present in the query path
                            val allValueObjects: Boolean = resAndValueObjIris.valueObjectIris.intersect(expectedValueObjects) == expectedValueObjects

                            if (allDependentResources && allValueObjects) {
                                // sufficient permissions, include the main resource and its values
                                acc + (mainResIri -> values)
                            } else {
                                // insufficient permissions, skip the resource
                                acc
                            }
                    }

                    // sort out those value objects that the user did not ask for in the input query's CONSTRUCT clause
                    valueObjectVariablesForAllResVars: Set[QueryVariable] = allResourceVariablesFromTypeInspection.flatMap {
                        depResVar =>
                            collectValueVariablesForResource(inputQuery.constructClause, depResVar, typeInspectionResult, nonTriplestoreSpecificConstructToSelectTransformer.groupConcatVariableSuffix)
                    }

                    valueObjectVariablesForDependentResIris: Set[QueryVariable] = dependentResourceIrisFromTypeInspection.flatMap {
                        depResIri =>
                            collectValueVariablesForResource(inputQuery.constructClause, IriRef(iri = depResIri.toSmartIri), typeInspectionResult, nonTriplestoreSpecificConstructToSelectTransformer.groupConcatVariableSuffix)
                    }

                    allValueObjectVariables: Set[QueryVariable] = valueObjectVariablesForAllResVars ++ valueObjectVariablesForDependentResIris

                    // collect requested value object IRIs for each resource
                    requestedValObjIrisPerResource: Map[IRI, Set[IRI]] = queryResWithFullQueryPath.map {
                        case (resIri: IRI, assertions: ConstructResponseUtilV2.ResourceWithValueRdfData) =>

                            val valueObjIrisForRes: Map[QueryVariable, Set[IRI]] = valueObjectIrisPerMainResource(resIri)

                            val valObjIrisRequestedForRes: Set[IRI] = allValueObjectVariables.flatMap {
                                requestedQueryVar: QueryVariable =>
                                    valueObjIrisForRes.getOrElse(requestedQueryVar, throw AssertionException(s"key $requestedQueryVar is absent in prequery's value object IRIs collection for resource $resIri"))
                            }

                            resIri -> valObjIrisRequestedForRes
                    }

                    // filter out those value objects that the user does not want to be returned by the query
                    queryResWithFullQueryPathOnlyRequestedValues: Map[IRI, ConstructResponseUtilV2.ResourceWithValueRdfData] = queryResWithFullQueryPath.map {
                        case (resIri: IRI, assertions: ConstructResponseUtilV2.ResourceWithValueRdfData) =>

                            // get the IRIs of all the value objects requested for this resource
                            val valueObjIrisRequestedForRes: Set[IRI] = requestedValObjIrisPerResource.getOrElse(resIri, throw AssertionException(s"key $resIri is absent in requested value object IRIs collection for resource $resIri"))

                            /**
                              * Filter out those values that the user does not want to see.
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
                                                // only return those value objects whose IRIs are contained in valueObjIrisRequestedForRes
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

                                        // ignore properties if there are no value object to be displayed
                                        if (valuesFilteredRecursively.nonEmpty) {
                                            acc + (propIri -> valuesFilteredRecursively)
                                        } else {
                                            // ignore this property since there are no value objects
                                            acc
                                        }


                                }
                            }

                            val requestedValuePropertyAssertions = traverseAndFilterValues(assertions)

                            resIri -> assertions.copy(
                                valuePropertyAssertions = requestedValuePropertyAssertions
                            )

                    }


                } yield queryResWithFullQueryPathOnlyRequestedValues

            } else {
                // the prequery returned no results, no further query is necessary
                Future(Map.empty[IRI, ConstructResponseUtilV2.ResourceWithValueRdfData])
            }

            // check if there are resources the user does not have sufficient permissions to see
            forbiddenResourceOption: Option[ReadResourceV2] <- if (mainResourceIris.size > queryResultsSeparatedWithFullQueryPath.size) {
                // some of the main resources have been suppressed, represent them using the forbidden resource

                getForbiddenResource(requestingUser)
            } else {
                // all resources visible, no need for the forbidden resource
                Future(None)
            }

            // get the mappings
            mappingsAsMap <- getMappingsFromQueryResultsSeparated(queryResultsSeparatedWithFullQueryPath, requestingUser)


        } yield ReadResourcesSequenceV2(
            numberOfResources = mainResourceIris.size,
            resources = ConstructResponseUtilV2.createSearchResponse(
                searchResults = queryResultsSeparatedWithFullQueryPath,
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


    /**
      * Checks that the correct schema is used in a statement pattern and that the predicate is allowed in Gravsearch.
      * If the statement is in the CONSTRUCT clause in the complex schema, non-property variables may refer only to resources or Knora values.
      *
      * @param statementPattern     the statement pattern to be checked.
      * @param querySchema          the API v2 ontology schema used in the query.
      * @param typeInspectionResult the type inspection result.
      * @param inConstructClause    `true` if the statement is in the CONSTRUCT clause.
      */
    protected def checkStatement(statementPattern: StatementPattern, querySchema: ApiV2Schema, typeInspectionResult: GravsearchTypeInspectionResult, inConstructClause: Boolean = false): Unit = {
        // Check each entity in the statement.
        for (entity <- Seq(statementPattern.subj, statementPattern.pred, statementPattern.obj)) {
            entity match {
                case iriRef: IriRef =>
                    // The entity is an IRI. If it has a schema, check that it's the query schema.
                    iriRef.iri.checkApiV2Schema(querySchema, throw GravsearchException(s"${iriRef.toSparql} is not in the correct schema"))

                    // If we're in the CONSTRUCT clause, don't allow rdf, rdfs, or owl IRIs.
                    if (inConstructClause && iriRef.iri.toString.contains('#')) {
                        iriRef.iri.getOntologyFromEntity.toString match {
                            case OntologyConstants.Rdf.RdfOntologyIri |
                                 OntologyConstants.Rdfs.RdfsOntologyIri |
                                 OntologyConstants.Owl.OwlOntologyIri =>
                                throw GravsearchException(s"${iriRef.toSparql} is not allowed in a CONSTRUCT clause")

                            case _ => ()
                        }
                    }

                case queryVar: QueryVariable =>
                    // If the entity is a variable and its type is a Knora IRI, check that the type IRI is in the query schema.
                    typeInspectionResult.getTypeOfEntity(entity) match {
                        case Some(typeInfo: GravsearchEntityTypeInfo) =>
                            typeInfo match {
                                case propertyTypeInfo: PropertyTypeInfo =>
                                    propertyTypeInfo.objectTypeIri.checkApiV2Schema(querySchema, throw GravsearchException(s"${entity.toSparql} is not in the correct schema"))

                                case nonPropertyTypeInfo: NonPropertyTypeInfo =>
                                    nonPropertyTypeInfo.typeIri.checkApiV2Schema(querySchema, throw GravsearchException(s"${entity.toSparql} is not in the correct schema"))

                                    // If it's a variable that doesn't represent a property, and we're using the complex schema and the statement
                                    // is in the CONSTRUCT clause, check that it refers to a resource or value.
                                    if (inConstructClause && querySchema == ApiV2WithValueObjects) {
                                        val typeIriStr = nonPropertyTypeInfo.typeIri.toString

                                        if (!(typeIriStr == OntologyConstants.KnoraApiV2WithValueObjects.Resource || OntologyConstants.KnoraApiV2WithValueObjects.ValueClasses.contains(typeIriStr))) {
                                            throw GravsearchException(s"${queryVar.toSparql} is not allowed in a CONSTRUCT clause")
                                        }
                                    }
                            }

                        case None => ()
                    }

                case xsdLiteral: XsdLiteral =>
                    val literalOK: Boolean = if (inConstructClause) {
                        // The only literal allowed in the CONSTRUCT clause is the boolean object of knora-api:isMainResource .
                        if (xsdLiteral.datatype.toString == OntologyConstants.Xsd.Boolean) {
                            statementPattern.pred match {
                                case iriRef: IriRef =>
                                    val iriStr = iriRef.iri.toString
                                    iriStr == OntologyConstants.KnoraApiV2Simple.IsMainResource || iriStr == OntologyConstants.KnoraApiV2WithValueObjects.IsMainResource

                                case _ => false
                            }
                        } else {
                            false
                        }
                    } else {
                        true
                    }

                    if (!literalOK) {
                        throw GravsearchException(s"Statement not allowed in CONSTRUCT clause: ${statementPattern.toSparql.trim}")
                    }

                case _ => ()
            }
        }

        // Check that the predicate is allowed in a Gravsearch query.
        statementPattern.pred match {
            case iriRef: IriRef =>
                if (forbiddenPredicates.contains(iriRef.iri.toString)) {
                    throw GravsearchException(s"Predicate ${iriRef.iri.toSparql} cannot be used in a Gravsearch query")
                }

            case _ => ()
        }
    }

    /**
      * Checks that the correct schema is used in a CONSTRUCT clause, that all the predicates used are allowed in Gravsearch,
      * and that in the complex schema, non-property variables refer only to resources or Knora values.
      *
      * @param constructClause      the CONSTRUCT clause to be checked.
      * @param typeInspectionResult the type inspection result.
      */
    protected def checkConstructClause(constructClause: ConstructClause, typeInspectionResult: GravsearchTypeInspectionResult): Unit = {
        for (statementPattern <- constructClause.statements) {
            checkStatement(
                statementPattern = statementPattern,
                querySchema = constructClause.querySchema.getOrElse(throw AssertionException(s"ConstructClause has no QuerySchema")),
                typeInspectionResult = typeInspectionResult,
                inConstructClause = true
            )
        }
    }

    // A set of predicates that aren't allowed in Gravsearch.
    private val forbiddenPredicates: Set[IRI] =
        Set(
            OntologyConstants.Rdfs.Label,
            OntologyConstants.KnoraApiV2WithValueObjects.AttachedToUser,
            OntologyConstants.KnoraApiV2WithValueObjects.AttachedToProject,
            OntologyConstants.KnoraApiV2WithValueObjects.HasPermissions,
            OntologyConstants.KnoraApiV2WithValueObjects.CreationDate,
            OntologyConstants.KnoraApiV2WithValueObjects.LastModificationDate,
            OntologyConstants.KnoraApiV2WithValueObjects.Result,
            OntologyConstants.KnoraApiV2WithValueObjects.IsEditable,
            OntologyConstants.KnoraApiV2WithValueObjects.IsLinkProperty,
            OntologyConstants.KnoraApiV2WithValueObjects.IsLinkValueProperty,
            OntologyConstants.KnoraApiV2WithValueObjects.IsInherited,
            OntologyConstants.KnoraApiV2WithValueObjects.OntologyName,
            OntologyConstants.KnoraApiV2WithValueObjects.MappingHasName,
            OntologyConstants.KnoraApiV2WithValueObjects.HasIncomingLink,
            OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasStartYear,
            OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasEndYear,
            OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasStartMonth,
            OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasEndMonth,
            OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasStartDay,
            OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasEndDay,
            OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasStartEra,
            OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasEndEra,
            OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasCalendar,
            OntologyConstants.KnoraApiV2WithValueObjects.TextValueAsHtml,
            OntologyConstants.KnoraApiV2WithValueObjects.TextValueAsXml,
            OntologyConstants.KnoraApiV2WithValueObjects.GeometryValueAsGeometry,
            OntologyConstants.KnoraApiV2WithValueObjects.LinkValueHasTarget,
            OntologyConstants.KnoraApiV2WithValueObjects.LinkValueHasTargetIri,
            OntologyConstants.KnoraApiV2WithValueObjects.ListValueAsListNodeLabel,
            OntologyConstants.KnoraApiV2WithValueObjects.FileValueAsUrl,
            OntologyConstants.KnoraApiV2WithValueObjects.FileValueHasFilename,
            OntologyConstants.KnoraApiV2WithValueObjects.StillImageFileValueHasIIIFBaseUrl
        )
}
