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

import akka.http.scaladsl.util.FastFuture
import akka.pattern._
import org.knora.webapi._
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.store.triplestoremessages._
import org.knora.webapi.messages.v1.responder.valuemessages.JulianDayNumberValueV1
import org.knora.webapi.messages.v2.responder._
import org.knora.webapi.messages.v2.responder.resourcemessages._
import org.knora.webapi.messages.v2.responder.searchmessages._
import org.knora.webapi.responders.ResponderWithStandoffV2
import org.knora.webapi.util.ActorUtil._
import org.knora.webapi.util.IriConversions._
import org.knora.webapi.util._
import org.knora.webapi.util.search.ApacheLuceneSupport.{CombineSearchTerms, MatchStringWhileTyping}
import org.knora.webapi.util.search.v2._
import org.knora.webapi.util.search._

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
      * Constants for extended search.
      *
      * These constants are used to create SPARQL CONSTRUCT queries to be executed by the triplestore and to process the results that are returned.
      */
    object ExtendedSearchConstants {

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

    def receive = {
        case FullTextSearchCountGetRequestV2(searchValue, limitToProject, limitToResourceClass, requestingUser) => future2Message(sender(), fulltextSearchCountV2(searchValue, limitToProject, limitToResourceClass, requestingUser), log)
        case FulltextSearchGetRequestV2(searchValue, offset, limitToProject, limitToResourceClass, requestingUser) => future2Message(sender(), fulltextSearchV2(searchValue, offset, limitToProject, limitToResourceClass, requestingUser), log)
        case ExtendedSearchCountGetRequestV2(query, requestingUser) => future2Message(sender(), extendedSearchCountV2(inputQuery = query, requestingUser = requestingUser), log)
        case ExtendedSearchGetRequestV2(query, requestingUser) => future2Message(sender(), extendedSearchV2(inputQuery = query, requestingUser = requestingUser), log)
        case SearchResourceByLabelCountGetRequestV2(searchValue, limitToProject, limitToResourceClass, requestingUser) => future2Message(sender(), searchResourcesByLabelCountV2(searchValue, limitToProject, limitToResourceClass, requestingUser), log)
        case SearchResourceByLabelGetRequestV2(searchValue, offset, limitToProject, limitToResourceClass, requestingUser) => future2Message(sender(), searchResourcesByLabelV2(searchValue, offset, limitToProject, limitToResourceClass, requestingUser), log)
        case other => handleUnexpectedMessage(sender(), other, log, this.getClass.getName)
    }

    /**
      * A [[ConstructToConstructTransformer]] that preprocesses the input CONSTRUCT query by converting external IRIs to internal ones
      * and disabling inference for individual statements as necessary.
      */
    class Preprocessor extends ConstructToConstructTransformer {

        def transformStatementInConstruct(statementPattern: StatementPattern): Seq[StatementPattern] = Seq(preprocessStatementPattern(statementPattern = statementPattern))

        def transformStatementInWhere(statementPattern: StatementPattern): Seq[QueryPattern] = Seq(preprocessStatementPattern(statementPattern = statementPattern))

        def transformFilter(filterPattern: FilterPattern): Seq[QueryPattern] = Seq(FilterPattern(preprocessFilterExpression(filterPattern.expression)))

        /**
          * Preprocesses a [[Expression]] by converting external IRIs to internal ones.
          *
          * @param filterExpression a filter expression.
          * @return the preprocessed expression.
          */
        private def preprocessFilterExpression(filterExpression: Expression): Expression = {
            filterExpression match {
                case entity: Entity => preprocessEntity(entity)
                case compareExpr: CompareExpression => CompareExpression(leftArg = preprocessFilterExpression(compareExpr.leftArg), operator = compareExpr.operator, rightArg = preprocessFilterExpression(compareExpr.rightArg))
                case andExpr: AndExpression => AndExpression(leftArg = preprocessFilterExpression(andExpr.leftArg), rightArg = preprocessFilterExpression(andExpr.rightArg))
                case orExpr: OrExpression => OrExpression(leftArg = preprocessFilterExpression(orExpr.leftArg), rightArg = preprocessFilterExpression(orExpr.rightArg))
                case regex: RegexFunction => regex // no preprocessing needed since no Knora entity IRIs are involved (schema conversion)
                case lang: LangFunction => lang
                case functionCallExpr: FunctionCallExpression => FunctionCallExpression(functionIri = functionCallExpr.functionIri, args = functionCallExpr.args.map(arg => preprocessEntity(arg)))
            }
        }

        /**
          * Preprocesses an [[Entity]] by converting external IRIs to internal ones.
          *
          * @param entity an entity provided by [[SearchParserV2]].
          * @return the preprocessed entity.
          */
        private def preprocessEntity(entity: Entity): Entity = {
            // convert external Iris to internal Iris if needed

            entity match {
                case iriRef: IriRef => // if an Iri is an external knora-api entity (assumed to be API v2 simple because otherwise the KnarQL parser would have rejected it), convert it to an internal Iri
                    if (iriRef.iri.isKnoraApiV2EntityIri) {
                        IriRef(iriRef.iri.toOntologySchema(InternalSchema))
                    } else {
                        iriRef
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
        private def preprocessStatementPattern(statementPattern: StatementPattern): StatementPattern = {

            val subj = preprocessEntity(statementPattern.subj)
            val pred = preprocessEntity(statementPattern.pred)
            val obj = preprocessEntity(statementPattern.obj)

            StatementPattern.makeInferred(
                subj = subj,
                pred = pred,
                obj = obj
            ) // use inference for all user-provided statements in Where clause
        }
    }

    /**
      * An abstract base class providing shared methods to query transformers for extended search.
      */
    abstract class AbstractExtendedSearchTransformer(typeInspectionResult: TypeInspectionResult) extends WhereTransformer {

        // Contains the variable representing the main resource: knora-base:isMainResource
        protected var mainResourceVariable: Option[QueryVariable] = None

        // get method for public access
        def getMainResourceVariable: QueryVariable = mainResourceVariable.getOrElse(throw SparqlSearchException("Could not get main resource variable from transformer"))

        // a Set containing all `TypeableEntity` (keys of `typeInspectionResult`) that have already been processed
        // in order to prevent duplicates
        protected val processedTypeInformationKeysWhereClause = mutable.Set.empty[TypeableEntity]

        // Contains the variables of dependent resources
        protected var dependentResourceVariables = mutable.Set.empty[QueryVariable]

        // separator used by GroupConcat
        val groupConcatSeparator: Char = StringFormatter.INFORMATION_SEPARATOR_ONE

        // contains variables representing group concatenated dependent resource Iris
        protected var dependentResourceVariablesGroupConcat = Set.empty[QueryVariable]

        // get method for public access
        def getDependentResourceVariablesGroupConcat: Set[QueryVariable] = dependentResourceVariablesGroupConcat

        // contains the variables of value objects (including those for link values)
        protected var valueObjectVariables = mutable.Set.empty[QueryVariable]

        // contains variables representing group concatenated value objects Iris
        protected var valueObjectVarsGroupConcat = Set.empty[QueryVariable]

        // get method for public access
        def getValueObjectVarsGroupConcat: Set[QueryVariable] = valueObjectVarsGroupConcat

        // suffix appended to variables that are returned by a SPARQL aggregation function.
        val groupConcatVariableAppendix = "Concat"

        // variables that are created when processing filter statements
        // they represent the value of a literal pointed to by a value object
        val valueVariablesCreatedInFilters = mutable.Map.empty[QueryVariable, QueryVariable]

        /**
          * Convert an [[Entity]] to a [[TypeableEntity]] (key of type inspection results).
          * The entity is expected to be a variable or an Iri, otherwise `None` is returned.
          *
          * @param entity the entity to be converted to a [[TypeableEntity]].
          * @return an Option of a [[TypeableEntity]].
          */
        protected def toTypeableEntityKey(entity: Entity): Option[TypeableEntity] = {

            entity match {
                case queryVar: QueryVariable => Some(TypeableVariable(queryVar.variableName))

                case iriRef: IriRef =>
                    // type info keys are external api v2 simple Iris,
                    // so convert this internal Iri to an external api v2 simple if possible
                    val externalIri = if (iriRef.iri.isKnoraInternalEntityIri) {
                        IriRef(iriRef.iri.toOntologySchema(ApiV2Simple))
                    } else {
                        iriRef
                    }

                    Some(TypeableIri(externalIri.iri))

                case _ => None
            }

        }

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
                case IriRef(SmartIri(OntologyConstants.KnoraBase.IsMainResource), _) =>

                    statementPattern.obj match {
                        case XsdLiteral(value, SmartIri(OntologyConstants.Xsd.Boolean)) if value.toBoolean =>
                            statementPattern.subj match {
                                case queryVariable: QueryVariable => Some(queryVariable)
                                case _ => throw SparqlSearchException(s"The subject of ${OntologyConstants.KnoraApiV2Simple.IsMainResource} must be a variable")
                            }

                        case _ => None
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

            if (nonPropertyTypeInfo.typeIri == OntologyConstants.KnoraApiV2Simple.Resource.toSmartIri) {

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

                            case None =>

                        }

                    case _ =>
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

        protected def convertStatementForPropertyType(propertyTypeInfo: PropertyTypeInfo, statementPattern: StatementPattern): Seq[QueryPattern] = {

            propertyTypeInfo.objectTypeIri.toString match {
                case OntologyConstants.KnoraApiV2Simple.Resource => {
                    // linking property

                    // make sure that the object is either an Iri or a variable (cannot be a literal)
                    statementPattern.obj match {
                        case _: IriRef => ()
                        case _: QueryVariable => ()
                        case other => throw SparqlSearchException(s"Object of a linking statement must be an IRI or a variable, but $other given.")
                    }

                    // we are given a linking property
                    // a variable representing the corresponding link value has to be created

                    // create a variable representing the link value
                    val linkValueObjVar: QueryVariable = createUniqueVariableNameFromEntityAndProperty(statementPattern.obj, OntologyConstants.KnoraBase.LinkValue)

                    // add variable to collection representing value objects
                    valueObjectVariables += linkValueObjVar

                    // create an Entity that connects the subject of the linking property with the link value object
                    val linkValueProp: Entity = statementPattern.pred match {
                        case linkingPropQueryVar: QueryVariable =>
                            // create a variable representing the link value property
                            // in case FILTER patterns are given restricting the linking property's possible Iris, the same variable will recreated when processing FILTER patterns
                            createlinkValuePropertyVariableFromLinkingPropertyVariable(linkingPropQueryVar)
                        case propIri: IriRef =>
                            // convert the given linking property Iri to the corresponding link value property Iri
                            // only matches the linking property's link value
                            IriRef(propIri.iri.fromLinkPropToLinkValueProp)
                        case literal: XsdLiteral => throw SparqlSearchException(s"literal $literal cannot be used as a predicate")
                        case other => throw SparqlSearchException(s"$other cannot be used as a predicate")
                    }

                    // create statements that represent the link value's properties for the given linking property
                    // do not check for the predicate because inference would not work
                    // instead, linkValueProp restricts the link value objects to be returned
                    val linkValueStatements = Seq(
                        StatementPattern.makeInferred(subj = statementPattern.subj, pred = linkValueProp, obj = linkValueObjVar),
                        StatementPattern.makeExplicit(subj = linkValueObjVar, pred = IriRef(OntologyConstants.Rdf.Type.toSmartIri), obj = IriRef(OntologyConstants.KnoraBase.LinkValue.toSmartIri)),
                        StatementPattern.makeExplicit(subj = linkValueObjVar, pred = IriRef(OntologyConstants.KnoraBase.IsDeleted.toSmartIri), obj = XsdLiteral(value = "false", datatype = OntologyConstants.Xsd.Boolean.toSmartIri)),
                        StatementPattern.makeExplicit(subj = linkValueObjVar, pred = IriRef(OntologyConstants.Rdf.Subject.toSmartIri), obj = statementPattern.subj),
                        StatementPattern.makeExplicit(subj = linkValueObjVar, pred = IriRef(OntologyConstants.Rdf.Object.toSmartIri), obj = statementPattern.obj)
                    )

                    // linking property: just include the original statement relating the subject to the target of the link
                    statementPattern +: linkValueStatements
                }

                case _ => {
                    // value property

                    // make sure that the object is a query variable (literals are not supported yet)
                    statementPattern.obj match {
                        case queryVar: QueryVariable => valueObjectVariables += queryVar // add variable to collection representing value objects
                        case other => throw SparqlSearchException(s"Object of a value property statement must be a QueryVariable, but $other given.")
                    }

                    // check that value object is not marked as deleted
                    val valueObjectIsNotDeleted = StatementPattern.makeExplicit(subj = statementPattern.obj, pred = IriRef(OntologyConstants.KnoraBase.IsDeleted.toSmartIri), obj = XsdLiteral(value = "false", datatype = OntologyConstants.Xsd.Boolean.toSmartIri))

                    // the query variable stands for a value object
                    // if there is a filter statement, the literal of the value object has to be checked: e.g., valueHasInteger etc.
                    // include the original statement relating the subject to a value object
                    Seq(statementPattern, valueObjectIsNotDeleted)
                }
            }

        }

        protected def processStatementPatternFromWhereClause(statementPattern: StatementPattern): Seq[QueryPattern] = {

            // look at the statement's subject, predicate, and object and generate additional statements if needed based on the given type information.
            // transform the originally given statement if necessary when processing the predicate

            // check if there exists type information for the given statement's subject
            val additionalStatementsForSubj: Seq[QueryPattern] = checkForNonPropertyTypeInfoForEntity(statementPattern.subj, typeInspectionResult, processedTypeInformationKeysWhereClause, createAdditionalStatementsForNonPropertyType)

            // check if there exists type information for the given statement's object
            val additionalStatementsForObj: Seq[QueryPattern] = checkForNonPropertyTypeInfoForEntity(statementPattern.obj, typeInspectionResult, processedTypeInformationKeysWhereClause, createAdditionalStatementsForNonPropertyType)

            // Add additional statements based on the whole input statement, e.g. to deal with the value object or the link value, and transform the original statement.
            val additionalStatementsForWholeStatement: Seq[QueryPattern] = checkForPropertyTypeInfoForStatement(statementPattern, typeInspectionResult, convertStatementForPropertyType)

            additionalStatementsForSubj ++ additionalStatementsForWholeStatement ++ additionalStatementsForObj

        }

        /**
          * Creates additional statements for a given [[Entity]] based on type information using `conversionFuncForNonPropertyType`
          * for a non property type (e.g., a resource).
          *
          * @param entity                           the entity to be taken into consideration (a statement's subject or object).
          * @param typeInspection                   type information.
          * @param processedTypeInfo                the keys of type information that have already been looked at.
          * @param conversionFuncForNonPropertyType the function to use to create additional statements.
          * @return a sequence of [[QueryPattern]] representing the additional statements.
          */
        protected def checkForNonPropertyTypeInfoForEntity(entity: Entity, typeInspection: TypeInspectionResult, processedTypeInfo: mutable.Set[TypeableEntity], conversionFuncForNonPropertyType: (NonPropertyTypeInfo, Entity) => Seq[QueryPattern]): Seq[QueryPattern] = {

            val typeInfoKey = toTypeableEntityKey(entity)

            // make sure that type info has not been processed yet
            if (typeInfoKey.nonEmpty && (typeInspection.typedEntities -- processedTypeInfo contains typeInfoKey.get)) {

                val nonPropTypeInfo: NonPropertyTypeInfo = typeInspection.typedEntities(typeInfoKey.get) match {
                    case nonPropInfo: NonPropertyTypeInfo => nonPropInfo

                    case _ => throw AssertionException(s"NonPropertyTypeInfo expected for ${typeInfoKey.get}")
                }

                // add TypeableEntity (keys of `typeInspection`) for subject in order to prevent duplicates
                processedTypeInfo += typeInfoKey.get

                conversionFuncForNonPropertyType(
                    nonPropTypeInfo,
                    entity
                )

            } else {
                Seq.empty[QueryPattern]
            }

        }

        /**
          * Converts the given statement based on the given type information using `conversionFuncForPropertyType`.
          *
          * @param statementPattern              the statement to be converted.
          * @param typeInspection                type information.
          * @param conversionFuncForPropertyType the function to use for the conversion.
          * @return a sequence of [[QueryPattern]] representing the converted statement.
          */
        protected def checkForPropertyTypeInfoForStatement(statementPattern: StatementPattern, typeInspection: TypeInspectionResult, conversionFuncForPropertyType: (PropertyTypeInfo, StatementPattern) => Seq[QueryPattern]): Seq[QueryPattern] = {
            val predTypeInfoKey: Option[TypeableEntity] = toTypeableEntityKey(statementPattern.pred)

            if (predTypeInfoKey.nonEmpty && (typeInspection.typedEntities contains predTypeInfoKey.get)) {
                // process type information for the predicate into additional statements

                val propTypeInfo = typeInspection.typedEntities(predTypeInfoKey.get) match {
                    case propInfo: PropertyTypeInfo => propInfo

                    case _ => throw AssertionException(s"PropertyTypeInfo expected for ${predTypeInfoKey.get}")
                }

                conversionFuncForPropertyType(
                    propTypeInfo,
                    statementPattern
                )

            } else {
                // no type information given and thus no further processing needed, just return the originally given statement (e.g., rdf:type)
                Seq(statementPattern)
            }
        }

        // A Map of XSD types to the corresponding knora-base value predicates that point to literals.
        // This allows us to handle different types of values (value objects).
        protected val literalTypesToValueTypeIris: Map[IRI, IRI] = Map(
            OntologyConstants.Xsd.Uri -> OntologyConstants.KnoraBase.ValueHasUri,
            OntologyConstants.Xsd.Integer -> OntologyConstants.KnoraBase.ValueHasInteger,
            OntologyConstants.Xsd.Decimal -> OntologyConstants.KnoraBase.ValueHasDecimal,
            OntologyConstants.Xsd.Boolean -> OntologyConstants.KnoraBase.ValueHasBoolean,
            OntologyConstants.Xsd.String -> OntologyConstants.KnoraBase.ValueHasString,
            OntologyConstants.KnoraApiV2Simple.Date -> OntologyConstants.KnoraBase.ValueHasStartJDN
        )

        /**
          * Given a variable representing a linking property, creates a variable representing the corresponding link value property.
          *
          * @param linkingPropertyQueryVariable variable representing a linking property.
          * @return variable representing the corresponding link value property.
          */
        protected def createlinkValuePropertyVariableFromLinkingPropertyVariable(linkingPropertyQueryVariable: QueryVariable): QueryVariable = {
            createUniqueVariableNameFromEntityAndProperty(linkingPropertyQueryVariable, OntologyConstants.KnoraBase.HasLinkToValue)
        }

        /**
          * Represents a transformed Filter expression and additional statement patterns that possibly had to be created during transformation.
          *
          * @param expression           the transformed Filter expression. In some cases, a given FILTER expression is replaced by additional statements.
          * @param additionalStatements additionally created statement patterns.
          */
        protected case class TransformedFilterPattern(expression: Option[Expression], additionalStatements: Seq[StatementPattern] = Seq.empty[StatementPattern])

        /**
          * Handles query variables that represent properties in a [[FilterPattern]].
          *
          * @param queryVar the query variable to be handled.
          * @param comparisonOperator the comparison operator used in the filter pattern.
          * @param iriRef the Iri the property query variable is restricted to.
          * @param propInfo information about the query variable's type.
          * @return a [[TransformedFilterPattern]].
          */
        private def handlePropertyIriQueryVar(queryVar: QueryVariable, comparisonOperator: CompareExpressionOperator.Value, iriRef: IriRef, propInfo: PropertyTypeInfo): TransformedFilterPattern = {

            // make sure that the comparison operator is a `CompareExpressionOperator.EQUALS`
            if (comparisonOperator != CompareExpressionOperator.EQUALS)
                throw SparqlSearchException(s"Comparison operator in a CompareExpression for a property type is expected to be ${CompareExpressionOperator.EQUALS}, but ${comparisonOperator} given. For negations use 'FILTER NOT EXISTS' ")

            val userProvidedRestriction = CompareExpression(queryVar, comparisonOperator, iriRef)

            // check if the objectTypeIri of propInfo is knora-base:Resource
            // if so, it is a linking property and its link value property must be restricted too
            propInfo.objectTypeIri.toString match {
                case OntologyConstants.KnoraApiV2Simple.Resource =>

                    // it is a linking property, restrict the link value property
                    val restrictionForLinkValueProp = CompareExpression(
                        leftArg = createlinkValuePropertyVariableFromLinkingPropertyVariable(queryVar), // the same variable was created during statement processing in WHERE clause in `convertStatementForPropertyType`
                        operator = comparisonOperator,
                        rightArg = IriRef(iriRef.iri.fromLinkPropToLinkValueProp)) // create link value property from linking property

                    TransformedFilterPattern(
                        Some(
                            AndExpression(
                                leftArg = userProvidedRestriction,
                                rightArg = restrictionForLinkValueProp)
                        )
                    )

                case other =>
                    // not a linking property, just return the provided restriction
                    TransformedFilterPattern(Some(userProvidedRestriction))
            }

        }

        /**
          *
          * Handles query variables that represent literals in a [[FilterPattern]].
          *
          * @param queryVar the query variable to be handled.
          * @param comparisonOperator the comparison operator used in the filter pattern.
          * @param literalValueExpression the literal provided in the [[FilterPattern]] as an [[Expression]].
          * @param xsdType valid xsd types of the literal.
          * @param valueHasProperty the property of the value object pointing to the literal.
          * @param validComparisonOperators a set of valid comparison operators, if to be restricted.
          * @return a [[TransformedFilterPattern]].
          */
        private def handleLiteralQueryVar(queryVar: QueryVariable, comparisonOperator: CompareExpressionOperator.Value, literalValueExpression: Expression, xsdType: Set[IRI], valueHasProperty: IRI, validComparisonOperators: Set[CompareExpressionOperator.Value] = Set.empty[CompareExpressionOperator.Value]): TransformedFilterPattern = {

            // make sure that the expression is a literal of the expected type
            val literal: XsdLiteral = literalValueExpression match {
                case xsdLiteral: XsdLiteral if xsdType(xsdLiteral.datatype.toString) => xsdLiteral

                case other => throw SparqlSearchException(s"right argument in CompareExpression was expected to be a literal of type $xsdType, but $other is given.")
            }

            // check if comparison operator is supported for given type
            if(validComparisonOperators.nonEmpty && !validComparisonOperators(comparisonOperator))
                throw SparqlSearchException(s"Filter expressions for a literal of type ${xsdType.mkString(", ")} supports the following operators: ${validComparisonOperators.mkString(", ")}, but $comparisonOperator given")

            // create a variable representing the literal attached to the value object
            val valueObjectLiteralVar: QueryVariable = createUniqueVariableNameFromEntityAndProperty(queryVar, valueHasProperty)

            // add this variable to the collection of additionally created variables (needed for sorting in the prequery)
            valueVariablesCreatedInFilters.put(queryVar, valueObjectLiteralVar)

            TransformedFilterPattern(
                Some(CompareExpression(valueObjectLiteralVar, comparisonOperator, literal)), // compares the provided literal to the value object's literal value
                Seq(
                    // connects the query variable with the value object (internal structure: values are represented as objects)
                    StatementPattern.makeExplicit(subj = queryVar, pred = IriRef(valueHasProperty.toSmartIri), valueObjectLiteralVar)
                )
            )

        }

        /**
          * Handles query variables that represent a date in a [[FilterPattern]].
          *
          * @param queryVar the query variable to be handled.
          * @param comparisonOperator the comparison operator used in the filter pattern.
          * @param dateValueExpression the date literal provided in the [[FilterPattern]] as an [[Expression]].
          * @return a [[TransformedFilterPattern]].
          */
        private def handleDateQueryVar(queryVar: QueryVariable, comparisonOperator: CompareExpressionOperator.Value, dateValueExpression: Expression): TransformedFilterPattern = {

            // make sure that the right argument is a string literal (dates are represented as knora date strings in knora-api simple)
            val dateStringLiteral: XsdLiteral = dateValueExpression match {
                case dateStrLiteral: XsdLiteral if dateStrLiteral.datatype.toString == OntologyConstants.Xsd.String => dateStrLiteral

                case other => throw SparqlSearchException(s"right argument in CompareExpression for date property was expected to be a string literal representing a date, but $other is given.")
            }

            // validate Knora date string
            val dateStr: String = stringFormatter.validateDate(dateStringLiteral.value, throw BadRequestException(s"${dateStringLiteral.value} is not a valid date string"))

            val date: JulianDayNumberValueV1 = DateUtilV1.createJDNValueV1FromDateString(dateStr)

            // create a variable representing the period's start
            val dateValueHasStartVar = createUniqueVariableNameFromEntityAndProperty(queryVar, OntologyConstants.KnoraBase.ValueHasStartJDN)

            // sort dates by their period's start (in the prequery)
            valueVariablesCreatedInFilters.put(queryVar, dateValueHasStartVar)

            // create a variable representing the period's end
            val dateValueHasEndVar = createUniqueVariableNameFromEntityAndProperty(queryVar, OntologyConstants.KnoraBase.ValueHasEndJDN)

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

                    TransformedFilterPattern(
                        Some(filter),
                        Seq(
                            dateValStartStatement, dateValEndStatement
                        )
                    )

                case CompareExpressionOperator.NOT_EQUALS =>

                    // no overlap in considered as inequality (negation of equality)
                    val leftArgFilter = CompareExpression(XsdLiteral(date.dateval1.toString, OntologyConstants.Xsd.Integer.toSmartIri), CompareExpressionOperator.GREATER_THAN, dateValueHasEndVar)

                    val rightArgFilter = CompareExpression(XsdLiteral(date.dateval2.toString, OntologyConstants.Xsd.Integer.toSmartIri), CompareExpressionOperator.LESS_THAN, dateValueHasStartVar)

                    val filter = OrExpression(leftArgFilter, rightArgFilter)

                    TransformedFilterPattern(
                        Some(filter),
                        Seq(
                            dateValStartStatement, dateValEndStatement
                        )
                    )

                case CompareExpressionOperator.LESS_THAN =>

                    // period ends before indicated period
                    val filter = CompareExpression(dateValueHasEndVar, CompareExpressionOperator.LESS_THAN, XsdLiteral(date.dateval1.toString, OntologyConstants.Xsd.Integer.toSmartIri))

                    TransformedFilterPattern(
                        Some(filter),
                        Seq(dateValStartStatement, dateValEndStatement) // dateValStartStatement may be used as ORDER BY statement
                    )

                case CompareExpressionOperator.LESS_THAN_OR_EQUAL_TO =>

                    // period ends before indicated period or equals it (any overlap)
                    val filter = CompareExpression(dateValueHasStartVar, CompareExpressionOperator.LESS_THAN_OR_EQUAL_TO, XsdLiteral(date.dateval2.toString, OntologyConstants.Xsd.Integer.toSmartIri))

                    TransformedFilterPattern(
                        Some(filter),
                        Seq(dateValStartStatement)
                    )

                case CompareExpressionOperator.GREATER_THAN =>

                    // period starts after end of indicated period
                    val filter = CompareExpression(dateValueHasStartVar, CompareExpressionOperator.GREATER_THAN, XsdLiteral(date.dateval2.toString, OntologyConstants.Xsd.Integer.toSmartIri))

                    TransformedFilterPattern(
                        Some(filter),
                        Seq(dateValStartStatement)
                    )

                case CompareExpressionOperator.GREATER_THAN_OR_EQUAL_TO =>

                    // period starts after indicated period or equals it (any overlap)
                    val filter = CompareExpression(dateValueHasEndVar, CompareExpressionOperator.GREATER_THAN_OR_EQUAL_TO, XsdLiteral(date.dateval1.toString, OntologyConstants.Xsd.Integer.toSmartIri))

                    TransformedFilterPattern(
                        Some(filter),
                        Seq(dateValStartStatement, dateValEndStatement) // dateValStartStatement may be used as ORDER BY statement
                    )

                case other => throw SparqlSearchException(s"operator $other not supported in filter expressions for dates")

            }

        }

        /**
          * Handles a [[FilterPattern]] containing a query variable.
          *
          * @param queryVar the query variable.
          * @param compareExpression the filter pattern's compare expression.
          * @param typeInspection the type inspection results.
          * @return a [[TransformedFilterPattern]].
          */
        private def handleQueryVar(queryVar: QueryVariable, compareExpression: CompareExpression, typeInspection: TypeInspectionResult): TransformedFilterPattern = {

            // make a key to look up information in type inspection results
            val queryVarTypeInfoKey: Option[TypeableEntity] = toTypeableEntityKey(queryVar)

            // get information about the queryVar's type
            if (queryVarTypeInfoKey.nonEmpty && (typeInspection.typedEntities contains queryVarTypeInfoKey.get)) {

                // get type information for queryVar
                val typeInfo: SparqlEntityTypeInfo = typeInspection.typedEntities(queryVarTypeInfoKey.get)

                // check if queryVar represents a property or a value
                typeInfo match {

                    case propInfo: PropertyTypeInfo =>

                        // left arg queryVar is a variable representing a property
                        // therefore the right argument must be an Iri restricting the property variable to a certain property
                        compareExpression.rightArg match {
                            case iriRef: IriRef =>

                                handlePropertyIriQueryVar(
                                    queryVar = queryVar,
                                    comparisonOperator = compareExpression.operator,
                                    iriRef = iriRef,
                                    propInfo = propInfo
                                )

                            case other => throw SparqlSearchException(s"right argument of CompareExpression is expected to be an Iri representing a property, but $other is given")
                        }

                    case nonPropInfo: NonPropertyTypeInfo =>

                        // depending on the value type, transform the given Filter pattern.
                        // add an extra level by getting the value literal from the value object.
                        // queryVar refers to the value object, for the value literal an extra variable has to be created, taking its type into account.
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
                }

            } else {
                throw SparqlSearchException(s"type information about $queryVar is missing")
            }
        }

        /**
          *
          * Handles the use of the SPARQL lang function in a [[FilterPattern]].
          *
          * @param langFunctionCall  the lang function call to be handled.
          * @param compareExpression the filter pattern's compare expression.
          * @param typeInspection the type inspection results.
          * @return a [[TransformedFilterPattern]].
          */
        private def handleLangFunctionCall(langFunctionCall: LangFunction, compareExpression: CompareExpression, typeInspection: TypeInspectionResult): TransformedFilterPattern = {

            // make a key to look up information about the lang functions argument (query var) in type inspection results
            val queryVarTypeInfoKey: Option[TypeableEntity] = toTypeableEntityKey(langFunctionCall.textValueVar)

            // get information about the queryVar's type
            if (queryVarTypeInfoKey.nonEmpty && (typeInspection.typedEntities contains queryVarTypeInfoKey.get)) {

                // get type information for lang.textValueVar
                val typeInfo: SparqlEntityTypeInfo = typeInspection.typedEntities(queryVarTypeInfoKey.get)

                typeInfo match {

                    case nonPropInfo: NonPropertyTypeInfo =>

                        nonPropInfo.typeIri.toString match {

                            case OntologyConstants.Xsd.String => () // xsd:string is expected

                            case _ => throw SparqlSearchException(s"${langFunctionCall.textValueVar} is expected to be of type xsd:string")
                        }

                    case _ => throw SparqlSearchException(s"${langFunctionCall.textValueVar} is expected to be of type NonPropertyTypeInfo")
                }

            } else {
                throw SparqlSearchException(s"type information about ${langFunctionCall.textValueVar} is missing")
            }

            // comparison operator is expected to be '='
            if (compareExpression.operator != CompareExpressionOperator.EQUALS) throw SparqlSearchException(s"Comparison operator is expected to be '=' for the use with a 'lang' function call.")

            val langLiteral: XsdLiteral = compareExpression.rightArg match {
                case strLiteral: XsdLiteral if strLiteral.datatype == OntologyConstants.Xsd.String.toSmartIri => strLiteral

                case other => throw SparqlSearchException(s"Right argument of comparison statement is expected to be a string literal for the use with a 'lang' function call.")
            }

            TransformedFilterPattern(
                None,
                Seq(
                    // connects the value object with the value language code
                    StatementPattern.makeExplicit(subj = langFunctionCall.textValueVar, pred = IriRef(OntologyConstants.KnoraBase.ValueHasLanguage.toSmartIri), langLiteral)
                )
            )

        }

        /**
          * Handles the use of the SPARQL regex function in a [[FilterPattern]].
          *
          * @param regexFunctionCall the regex function call to be handled.
          * @param typeInspection the type inspection results.
          * @return a [[TransformedFilterPattern]].
          */
        private def handleRegexFunctionCall(regexFunctionCall: RegexFunction, typeInspection: TypeInspectionResult): TransformedFilterPattern = {

            // make sure that the query variable (first argument of regex function) represents a text value

            // make a key to look up information in type inspection results
            val queryVarTypeInfoKey: Option[TypeableEntity] = toTypeableEntityKey(regexFunctionCall.textValueVar)

            // get information about the queryVar's type
            if (queryVarTypeInfoKey.nonEmpty && (typeInspection.typedEntities contains queryVarTypeInfoKey.get)) {

                // get type information for regexFunction.textValueVar
                val typeInfo: SparqlEntityTypeInfo = typeInspection.typedEntities(queryVarTypeInfoKey.get)

                typeInfo match {

                    case nonPropInfo: NonPropertyTypeInfo =>

                        nonPropInfo.typeIri.toString match {

                            case OntologyConstants.Xsd.String => () // xsd:string is expected, TODO: should also xsd:anyUri be allowed?

                            case _ => throw SparqlSearchException(s"${regexFunctionCall.textValueVar} is expected to be of type xsd:string")
                        }

                    case _ => throw SparqlSearchException(s"${regexFunctionCall.textValueVar} is expected to be of type NonPropertyTypeInfo")
                }

            } else {
                throw SparqlSearchException(s"type information about ${regexFunctionCall.textValueVar} is missing")
            }

            // create a variable representing the string literal
            val textValHasString: QueryVariable = createUniqueVariableNameFromEntityAndProperty(regexFunctionCall.textValueVar, OntologyConstants.KnoraBase.ValueHasString)

            // add this variable to the collection of additionally created variables (needed for sorting in the prequery)
            valueVariablesCreatedInFilters.put(regexFunctionCall.textValueVar, textValHasString)

            TransformedFilterPattern(
                Some(RegexFunction(textValHasString, regexFunctionCall.pattern, regexFunctionCall.modifier)),
                Seq(
                    // connects the value object with the value literal
                    StatementPattern.makeExplicit(subj = regexFunctionCall.textValueVar, pred = IriRef(OntologyConstants.KnoraBase.ValueHasString.toSmartIri), textValHasString)
                )
            )

        }

        /**
          *
          * Handles a KnarQL specific function call in a [[FilterPattern]].
          *
          * @param functionCallExpression the function call to be handled.
          * @param typeInspection the type inspection results.
          * @return a [[TransformedFilterPattern]].
          */
        private def handleKnoraFunctionCall(functionCallExpression: FunctionCallExpression, typeInspection: TypeInspectionResult) = {

            val functionName: IriRef = functionCallExpression.functionIri.toInternalEntityIri

            functionName.iri match {

                case SmartIri(OntologyConstants.KnoraBase.MatchFunctionIri) =>

                    // two arguments are expected: the first is expected to be a variable representing a string value,
                    // the second is expected to be a string literal

                    if (functionCallExpression.args.size != 2) throw SparqlSearchException(s"Two arguments are expected for ${functionCallExpression.functionIri}")

                    // a QueryVariable expected to represent a text value
                    val textValueVar: QueryVariable = functionCallExpression.getArgAsQueryVar(pos = 0)

                    // make a key to look up information in type inspection results
                    val queryVarTypeInfoKey: Option[TypeableEntity] = toTypeableEntityKey(textValueVar)

                    // get information about the queryVar's type
                    if (queryVarTypeInfoKey.nonEmpty && (typeInspection.typedEntities contains queryVarTypeInfoKey.get)) {

                        // get type information for regexFunction.textValueVar
                        val typeInfo: SparqlEntityTypeInfo = typeInspection.typedEntities(queryVarTypeInfoKey.get)

                        typeInfo match {

                            case nonPropInfo: NonPropertyTypeInfo =>

                                nonPropInfo.typeIri.toString match {

                                    case OntologyConstants.Xsd.String => () // xsd:string is expected, TODO: should also xsd:anyUri be allowed?

                                    case _ => throw SparqlSearchException(s"$textValueVar is expected to be of type xsd:string")
                                }

                            case _ => throw SparqlSearchException(s"$textValueVar} is expected to be of type NonPropertyTypeInfo")
                        }

                    } else {
                        throw SparqlSearchException(s"type information about $textValueVar is missing")
                    }

                    // create a variable representing the string literal
                    val textValHasString: QueryVariable = createUniqueVariableNameFromEntityAndProperty(textValueVar, OntologyConstants.KnoraBase.ValueHasString)

                    // add this variable to the collection of additionally created variables (needed for sorting in the prequery)
                    valueVariablesCreatedInFilters.put(textValueVar, textValHasString)

                    val searchTerm: XsdLiteral = functionCallExpression.getArgAsLiteral(1, xsdDatatype = OntologyConstants.Xsd.String.toSmartIri)

                    // combine search terms with a logical AND (Lucene syntax)
                    val searchTerms: CombineSearchTerms = CombineSearchTerms(searchTerm.value)

                    TransformedFilterPattern(
                        None, // FILTER has been replaced by statements
                        Seq(
                            // connects the value object with the value literal
                            StatementPattern.makeExplicit(subj = textValueVar, pred = IriRef(OntologyConstants.KnoraBase.ValueHasString.toSmartIri), textValHasString),
                            StatementPattern.makeExplicit(subj = textValHasString, pred = IriRef(OntologyConstants.KnoraBase.MatchesTextIndex.toSmartIri), XsdLiteral(searchTerms.combineSearchTermsWithLogicalAnd, OntologyConstants.Xsd.String.toSmartIri))
                        )
                    )

                case _ => throw NotImplementedException(s"KnarQL function ${functionCallExpression.functionIri} not implemented")
            }

        }

        /**
          * Transforms a Filter expression provided in the input query (knora-api simple) into a knora-base compliant Filter expression.
          *
          * @param filterExpression the Filter expression to be transformed.
          * @param typeInspection   the results of type inspection.
          * @return a [[TransformedFilterPattern]].
          */
        protected def transformFilterPattern(filterExpression: Expression, typeInspection: TypeInspectionResult): TransformedFilterPattern = {

            filterExpression match {

                case filterCompare: CompareExpression =>

                    // left argument of a CompareExpression is expected to be a QueryVariable or a 'lang' function call
                    filterCompare.leftArg match {

                        case queryVar: QueryVariable =>

                            handleQueryVar(queryVar = queryVar, compareExpression = filterCompare, typeInspection = typeInspection)

                        case lang: LangFunction =>

                            handleLangFunctionCall(langFunctionCall = lang, compareExpression = filterCompare, typeInspection = typeInspection)

                        case other => throw SparqlSearchException(s"Left argument of a Filter CompareExpression is expected to be a QueryVariable or a 'lang' function call, but $other is given")
                    }


                case filterOr: OrExpression =>
                    // recursively call this method for both arguments
                    val filterExpressionLeft: TransformedFilterPattern = transformFilterPattern(filterOr.leftArg, typeInspection)
                    val filterExpressionRight: TransformedFilterPattern = transformFilterPattern(filterOr.rightArg, typeInspection)

                    // recreate Or expression and include additional statements
                    TransformedFilterPattern(
                        Some(OrExpression(filterExpressionLeft.expression.getOrElse(throw DataConversionException("Expression was expected from previous FILTER conversion, but None given")), filterExpressionRight.expression.getOrElse(throw DataConversionException("Expression was expected from previous FILTER conversion, but None given")))),
                        filterExpressionLeft.additionalStatements ++ filterExpressionRight.additionalStatements
                    )


                case filterAnd: AndExpression =>
                    // recursively call this method for both arguments
                    val filterExpressionLeft: TransformedFilterPattern = transformFilterPattern(filterAnd.leftArg, typeInspection)
                    val filterExpressionRight: TransformedFilterPattern = transformFilterPattern(filterAnd.rightArg, typeInspection)

                    // recreate And expression and include additional statements
                    TransformedFilterPattern(
                        Some(AndExpression(filterExpressionLeft.expression.getOrElse(throw DataConversionException("Expression was expected from previous FILTER conversion, but None given")), filterExpressionRight.expression.getOrElse(throw DataConversionException("Expression was expected from previous FILTER conversion, but None given")))),
                        filterExpressionLeft.additionalStatements ++ filterExpressionRight.additionalStatements
                    )

                case regexFunction: RegexFunction =>

                    handleRegexFunctionCall(regexFunctionCall = regexFunction, typeInspection = typeInspectionResult)

                case functionCall: FunctionCallExpression =>

                    handleKnoraFunctionCall(functionCallExpression = functionCall, typeInspectionResult)

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

        def transformStatementInWhere(statementPattern: StatementPattern): Seq[StatementPattern] = {
            transformKnoraExplicitToGraphDBExplicit(statementPattern)
        }

        def transformFilter(filterPattern: FilterPattern): Seq[QueryPattern] = Seq(filterPattern)

    }

    private class NoInferenceSelectToSelectTransformer extends SelectToSelectTransformer {
        def transformStatementInSelect(statementPattern: StatementPattern): Seq[StatementPattern] = Seq(statementPattern)

        def transformStatementInWhere(statementPattern: StatementPattern): Seq[StatementPattern] = {
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

        def transformStatementInWhere(statementPattern: StatementPattern): Seq[StatementPattern] = {
            transformKnoraExplicitToGraphDBExplicit(statementPattern)
        }

        def transformFilter(filterPattern: FilterPattern): Seq[QueryPattern] = Seq(filterPattern)
    }

    /**
      * Transforms non-triplestore-specific query patterns for a triplestore that does not have inference enabled.
      */
    private class NoInferenceConstructToConstructTransformer extends ConstructToConstructTransformer {
        def transformStatementInConstruct(statementPattern: StatementPattern): Seq[StatementPattern] = Seq(statementPattern)

        def transformStatementInWhere(statementPattern: StatementPattern): Seq[StatementPattern] = {
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
            case _ => throw SparqlSearchException(s"A unique variable could not be made for $entity")
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
      * Represents the Iris of resources and value objects.
      *
      * @param resourceIris    resource Iris.
      * @param valueObjectIris value object Iris.
      */
    private case class ResourceIrisAndValueObjectIris(resourceIris: Set[IRI], valueObjectIris: Set[IRI])

    /**
      * Traverses value property assertions and returns the Iris of the value objects and the dependent resources, recursively traversing their value properties as well.
      * This is method is needed in order to determine if the full query path is still present in the results after permissions checking handled in [[ConstructResponseUtilV2.splitMainResourcesAndValueRdfData]].
      * Due to insufficient permissions, some of the resources (both main and dependent resources) and/or values may have been filtered out.
      *
      * @param valuePropertyAssertions the assertions to be traversed.
      * @return a [[ResourceIrisAndValueObjectIris]] representing all resource and value object Iris that have been found in `valuePropertyAssertions`.
      */
    private def traverseValuePropertyAssertions(valuePropertyAssertions: Map[IRI, Seq[ConstructResponseUtilV2.ValueRdfData]]): ResourceIrisAndValueObjectIris = {

        // look at the value objects and ignore the property Iris (we are only interested in value instances)
        val resAndValObjIris: Seq[ResourceIrisAndValueObjectIris] = valuePropertyAssertions.values.flatten.foldLeft(Seq.empty[ResourceIrisAndValueObjectIris]) {
            (acc: Seq[ResourceIrisAndValueObjectIris], assertion) =>

                if (assertion.nestedResource.nonEmpty) {
                    // this is a link value
                    // recursively traverse the dependent resource's values

                    val dependentRes: ConstructResponseUtilV2.ResourceWithValueRdfData = assertion.nestedResource.get

                    // recursively traverse the link value's nested resource and its assertions
                    val resAndValObjIrisForDependentRes: ResourceIrisAndValueObjectIris = traverseValuePropertyAssertions(dependentRes.valuePropertyAssertions)
                    // get the dependent resource's Iri from the current link value's rdf:object or rdf:subject in case of an incoming link
                    val dependentResIri: IRI = if (!assertion.incomingLink) {
                        assertion.assertions.getOrElse(OntologyConstants.Rdf.Object, throw InconsistentTriplestoreDataException(s"expected ${OntologyConstants.Rdf.Object} for link value ${assertion.valueObjectIri}"))
                    } else {
                        assertion.assertions.getOrElse(OntologyConstants.Rdf.Subject, throw InconsistentTriplestoreDataException(s"expected ${OntologyConstants.Rdf.Subject} for link value ${assertion.valueObjectIri}"))
                    }
                    // append results from recursion and current value object
                    ResourceIrisAndValueObjectIris(resourceIris = resAndValObjIrisForDependentRes.resourceIris + dependentResIri, valueObjectIris = resAndValObjIrisForDependentRes.valueObjectIris + assertion.valueObjectIri) +: acc
                } else {
                    // not a link value or no dependent resource given (in order to avoid infinite recursion)
                    // no dependent resource present
                    // append results for current value object
                    ResourceIrisAndValueObjectIris(resourceIris = Set.empty[IRI], valueObjectIris = Set(assertion.valueObjectIri)) +: acc
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
    private def getForbiddenResource(requestingUser: UserADM) = {
        import SearchResponderV2Constants.forbiddenResourceIri

        for {
            forbiddenResSeq: ReadResourcesSequenceV2 <- (responderManager ? ResourcesGetRequestV2(resourceIris = Seq(forbiddenResourceIri), requestingUser = requestingUser)).mapTo[ReadResourcesSequenceV2]
            forbiddenRes = forbiddenResSeq.resources.headOption.getOrElse(throw InconsistentTriplestoreDataException(s"$forbiddenResourceIri was not returned"))
        } yield Some(forbiddenRes)
    }

    /**
      * Performs a fulltext search and returns the resources count (how man resources match the search criteria),
      * without taking into consideration permission checking.
      *
      * This method does not return the resources themselves.
      *
      * @param searchValue          the values to search for.
      * @param limitToProject       limit search to given project.
      * @param limitToResourceClass limit search to given resource class.
      * @param requestingUser          the the client making the request.
      * @return a [[ReadResourcesSequenceV2]] representing the amount of resources that have been found.
      */
    private def fulltextSearchCountV2(searchValue: String, limitToProject: Option[IRI], limitToResourceClass: Option[SmartIri], requestingUser: UserADM): Future[ReadResourcesSequenceV2] = {

        val searchTerms: CombineSearchTerms = CombineSearchTerms(searchValue)

        for {
            countSparql <- Future(queries.sparql.v2.txt.searchFulltext(
                triplestore = settings.triplestoreType,
                searchTerms = searchTerms,
                limitToProject = limitToProject,
                limitToResourceClass = limitToResourceClass.map(_.toString),
                separator = None, // no separator needed for count query
                limit = 1,
                offset = 0,
                countQuery = true // do  not get the resources themselves, but the sum of results
            ).toString())

            // _ = println(countSparql)

            countResponse: SparqlSelectResponse <- (storeManager ? SparqlSelectRequest(countSparql)).mapTo[SparqlSelectResponse]

            // query response should contain one result with one row with the name "count"
            _ = if (countResponse.results.bindings.length != 1) {
                throw SparqlSearchException(s"Fulltext count query is expected to return exactly one row, but ${countResponse.results.bindings.size} given")
            }

            count = countResponse.results.bindings.head.rowMap("count")

        } yield ReadResourcesSequenceV2(
            numberOfResources = count.toInt,
            resources = Seq.empty[ReadResourceV2] // no results for a count query
        )
    }

    /**
      * Performs a fulltext search (simple search).
      *
      * @param searchValue          the values to search for.
      * @param offset               the offset to be used for paging.
      * @param limitToProject       limit search to given project.
      * @param limitToResourceClass limit search to given resource class.
      * @param requestingUser          the the client making the request.
      * @return a [[ReadResourcesSequenceV2]] representing the resources that have been found.
      */
    private def fulltextSearchV2(searchValue: String, offset: Int, limitToProject: Option[IRI], limitToResourceClass: Option[SmartIri], requestingUser: UserADM): Future[ReadResourcesSequenceV2] = {

        import SearchResponderV2Constants.FullTextSearchConstants._

        val groupConcatSeparator = StringFormatter.INFORMATION_SEPARATOR_ONE

        /**
          * Creates a CONSTRUCT query for the given resource and value object Iris.
          *
          * @param resourceIris    the Iris of the resources to be queried.
          * @param valueObjectIris the Iris of the value objects to be queried.
          * @return a [[ConstructQuery]].
          */
        def createMainQuery(resourceIris: Set[IRI], valueObjectIris: Set[IRI]): ConstructQuery = {

            // WHERE patterns for the resources: check that the resource are a knora-base:Resource and that it is not marked as deleted
            val wherePatternsForResources = Seq(
                ValuesPattern(resourceVar, resourceIris.map(iri => IriRef(iri.toSmartIri))), // a ValuePattern that binds the resource Iris to the resource variable
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
                separator = Some(groupConcatSeparator),
                limit = settings.v2ResultsPerPage,
                offset = offset * settings.v2ResultsPerPage, // determine the actual offset
                countQuery = false
            ).toString())

            // _ = println(searchSparql)

            prequeryResponse: SparqlSelectResponse <- (storeManager ? SparqlSelectRequest(searchSparql)).mapTo[SparqlSelectResponse]

            // _ = println(prequeryResponse)

            // a sequence of resource Iris that match the search criteria
            // attention: no permission checking has been done so far
            resourceIris: Seq[IRI] = prequeryResponse.results.bindings.map {
                resultRow: VariableResultsRow => resultRow.rowMap(resourceVar.variableName)
            }

            // make sure that the prequery returned some results
            queryResultsSeparatedWithFullQueryPath: Map[IRI, ConstructResponseUtilV2.ResourceWithValueRdfData] <- if (resourceIris.nonEmpty) {

                // for each resource, create a Set of value object Iris
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

                // collect all value object Iris
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

                                    // check if the client has sufficient permissions on all value objects Iris present in the query path
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
      * Performs a count query for an extended search Sparql query provided by the user.
      *
      * @param inputQuery  Sparql construct query provided by the client.
      * @param requestingUser the the client making the request.
      * @return a [[ReadResourcesSequenceV2]] representing the resources that have been found.
      */
    private def extendedSearchCountV2(inputQuery: ConstructQuery, apiSchema: ApiV2Schema = ApiV2Simple, requestingUser: UserADM) = {

        if (apiSchema != ApiV2Simple) {
            throw SparqlSearchException("Only api v2 simple is supported in v2 extended search count query")
        }

        // make sure that OFFSET is 0
        if (inputQuery.offset != 0) throw SparqlSearchException(s"OFFSET is expected to be 0 for a count query, but ${inputQuery.offset} given")

        /**
          * Transforms a preprocessed CONSTRUCT query into a SELECT query that returns only the IRIs and sort order of the main resources that matched
          * the search criteria. This query will be used to get resource IRIs for a single page of results. These IRIs will be included in a CONSTRUCT
          * query to get the actual results for the page.
          *
          * @param typeInspectionResult the result of type inspection of the original query.
          */
        class NonTriplestoreSpecificConstructToSelectTransformer(typeInspectionResult: TypeInspectionResult) extends AbstractExtendedSearchTransformer(typeInspectionResult) with ConstructToSelectTransformer {

            def handleStatementInConstruct(statementPattern: StatementPattern): Unit = {
                // Just identify the main resource variable and put it in mainResourceVariable.

                isMainResourceVariable(statementPattern) match {
                    case Some(queryVariable: QueryVariable) => mainResourceVariable = Some(queryVariable)
                    case None => ()
                }
            }

            def transformStatementInWhere(statementPattern: StatementPattern): Seq[QueryPattern] = {

                // Include any statements needed to meet the user's search criteria, but not statements that would be needed for permission checking or
                // other information about the matching resources or values.

                processStatementPatternFromWhereClause(
                    statementPattern = statementPattern
                )

            }

            def transformFilter(filterPattern: FilterPattern): Seq[QueryPattern] = {
                val filterExpression: TransformedFilterPattern = transformFilterPattern(filterPattern.expression, typeInspection = typeInspectionResult)

                filterExpression.expression match {
                    case Some(expression: Expression) => filterExpression.additionalStatements :+ FilterPattern(expression)

                    case None => filterExpression.additionalStatements // no FILTER expression given
                }

            }

            def getSelectVariables: Seq[SelectQueryColumn] = {

                val mainResVar = mainResourceVariable match {
                    case Some(mainVar: QueryVariable) => mainVar

                    case None => throw SparqlSearchException(s"No ${OntologyConstants.KnoraBase.IsMainResource} found in CONSTRUCT query.")
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

            typeInspector <- FastFuture.successful(new ExplicitTypeInspectorV2())
            whereClauseWithoutAnnotations: WhereClause = typeInspector.removeTypeAnnotations(inputQuery.whereClause)
            typeInspectionResult: TypeInspectionResult = typeInspector.inspectTypes(inputQuery.whereClause)

            // Preprocess the query to convert API IRIs to internal IRIs and to set inference per statement.

            preprocessedQuery: ConstructQuery = QueryTraverser.transformConstructToConstruct(
                inputQuery = inputQuery.copy(whereClause = whereClauseWithoutAnnotations),
                transformer = new Preprocessor
            )

            nonTriplestoreSpecificConstructToSelectTransformer: NonTriplestoreSpecificConstructToSelectTransformer = new NonTriplestoreSpecificConstructToSelectTransformer(typeInspectionResult = typeInspectionResult)

            // Create a Select prequery
            nonTriplestoreSpecficPrequery: SelectQuery = QueryTraverser.transformConstructToSelect(
                inputQuery = preprocessedQuery,
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
                throw SparqlSearchException(s"Fulltext count query is expected to return exactly one row, but ${countResponse.results.bindings.size} given")
            }

            count: String = countResponse.results.bindings.head.rowMap("count")

        } yield ReadResourcesSequenceV2(
            numberOfResources = count.toInt,
            resources = Seq.empty[ReadResourceV2] // no results for a count query
        )

    }

    /**
      * Performs an extended search using a Sparql query provided by the user.
      *
      * @param inputQuery  Sparql construct query provided by the client.
      * @param requestingUser the the client making the request.
      * @return a [[ReadResourcesSequenceV2]] representing the resources that have been found.
      */
    private def extendedSearchV2(inputQuery: ConstructQuery, requestingUser: UserADM): Future[ReadResourcesSequenceV2] = {

        /**
          * Transforms a preprocessed CONSTRUCT query into a SELECT query that returns only the IRIs and sort order of the main resources that matched
          * the search criteria. This query will be used to get resource IRIs for a single page of results. These IRIs will be included in a CONSTRUCT
          * query to get the actual results for the page.
          *
          * @param typeInspectionResult the result of type inspection of the original query.
          */
        class NonTriplestoreSpecificConstructToSelectTransformer(typeInspectionResult: TypeInspectionResult) extends AbstractExtendedSearchTransformer(typeInspectionResult) with ConstructToSelectTransformer {

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
              * @return the result of the transformation.
              */
            override def transformStatementInWhere(statementPattern: StatementPattern): Seq[QueryPattern] = {
                // Include any statements needed to meet the user's search criteria, but not statements that would be needed for permission checking or
                // other information about the matching resources or values.

                processStatementPatternFromWhereClause(
                    statementPattern = statementPattern
                )
            }

            /**
              * Transforms a [[FilterPattern]] in a WHERE clause into zero or more statement patterns.
              *
              * @param filterPattern the filter to be transformed.
              * @return the result of the transformation.
              */
            override def transformFilter(filterPattern: FilterPattern): Seq[QueryPattern] = {

                val filterExpression: TransformedFilterPattern = transformFilterPattern(filterPattern.expression, typeInspection = typeInspectionResult)

                filterExpression.expression match {
                    case Some(expression: Expression) => filterExpression.additionalStatements :+ FilterPattern(expression)

                    case None => filterExpression.additionalStatements // no FILTER expression given
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
                    (dependentResVar: QueryVariable) =>
                        GroupConcat(inputVariable = dependentResVar,
                            separator = groupConcatSeparator,
                            outputVariableName = dependentResVar.variableName + groupConcatVariableAppendix)
                }.toSet

                dependentResourceVariablesGroupConcat = dependentResourceGroupConcat.map(_.outputVariable)

                val valueObjectGroupConcat = valueObjectVariables.map {
                    (valueObjVar: QueryVariable) =>
                        GroupConcat(inputVariable = valueObjVar,
                            separator = groupConcatSeparator,
                            outputVariableName = valueObjVar.variableName + groupConcatVariableAppendix)
                }.toSet

                valueObjectVarsGroupConcat = valueObjectGroupConcat.map(_.outputVariable)

                mainResourceVariable match {
                    case Some(mainVar: QueryVariable) => Seq(mainVar) ++ dependentResourceGroupConcat ++ valueObjectGroupConcat

                    case None => throw SparqlSearchException(s"No ${OntologyConstants.KnoraBase.IsMainResource} found in CONSTRUCT query.")
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

                        valueVariablesCreatedInFilters.get(criterion.queryVariable) match {
                            case Some(generatedVariable) =>
                                // Yes. Use the already generated variable in the ORDER BY.
                                acc.copy(
                                    orderBy = acc.orderBy :+ OrderCriterion(queryVariable = generatedVariable, isAscending = criterion.isAscending)
                                )

                            case None =>
                                // No. Generate such a variable and generate an additional statement to get its literal value in the WHERE clause.

                                // What is the type of the literal value?
                                val typeableEntity = TypeableVariable(criterion.queryVariable.variableName)
                                val typeInfo: SparqlEntityTypeInfo = typeInspectionResult.typedEntities.getOrElse(typeableEntity, throw SparqlSearchException(s"No type information found for ${criterion.queryVariable}"))

                                // Get the corresponding knora-base:valueHas* property so we can generate an appropriate variable name.
                                val propertyIri: SmartIri = typeInfo match {
                                    case nonPropertyTypeInfo: NonPropertyTypeInfo =>
                                        literalTypesToValueTypeIris.getOrElse(nonPropertyTypeInfo.typeIri.toString, throw SparqlSearchException(s"Type $nonPropertyTypeInfo.typeIri is not supported in ORDER BY")).toSmartIri

                                    case _: PropertyTypeInfo => throw SparqlSearchException(s"Variable ${criterion.queryVariable.variableName} represents a property, and therefore cannot be used in ORDER BY")
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
                    queryVariable = mainResourceVariable.getOrElse(throw SparqlSearchException("No ${OntologyConstants.KnoraBase.IsMainResource} found in CONSTRUCT query.")),
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
          * @param typeInspection       results of type inspection.
          * @param variableConcatSuffix the suffix appended to variable names in prequery results.
          * @return a Set of [[PropertyTypeInfo]] representing the value and link value properties to be returned to the client.
          */
        def collectValueVariablesForResource(constructClause: ConstructClause, resource: Entity, typeInspection: TypeInspectionResult, variableConcatSuffix: String): Set[QueryVariable] = {

            // make sure resource is a query variable or an Iri
            resource match {
                case queryVar: QueryVariable => ()
                case iri: IriRef => ()
                case literal: XsdLiteral => throw SparqlSearchException(s"literal $literal cannot represent a resource")
                case other => throw SparqlSearchException(s"$other cannot represent a resource")
            }

            // TODO: check in type information that resource represents a resource

            // get statements with the main resource as a subject
            val statementsWithResourceAsSubject: Seq[StatementPattern] = constructClause.statements.filter {
                (statementPattern: StatementPattern) =>
                    statementPattern.subj == resource
            }

            statementsWithResourceAsSubject.foldLeft(Set.empty[QueryVariable]) {
                (acc: Set[QueryVariable], statementPattern: StatementPattern) =>

                    // check if the predicate is a Knora value  or linking property

                    // create a key for the type annotations map
                    val typeableEntity: TypeableEntity = statementPattern.pred match {
                        case iriRef: IriRef =>
                            val externalIri = if (iriRef.iri.isKnoraInternalEntityIri) {
                                iriRef.iri.toOntologySchema(ApiV2Simple)
                            } else {
                                iriRef.iri
                            }

                            TypeableIri(externalIri)

                        case variable: QueryVariable => TypeableVariable(variable.variableName)

                        case other => throw SparqlSearchException(s"Expected an Iri or a variable as the predicate of a statement, but $other given")
                    }

                    // if the given key exists in the type annotations map, add it to the collection
                    if (typeInspection.typedEntities.contains(typeableEntity)) {

                        val propTypeInfo: PropertyTypeInfo = typeInspection.typedEntities(typeableEntity) match {
                            case propType: PropertyTypeInfo => propType

                            case _: NonPropertyTypeInfo =>
                                throw SparqlSearchException(s"PropertyTypeInfo was expected for predicate ${statementPattern.pred} in type annotations, but NonPropertyTypeInfo given.")

                        }

                        val valueObjectVariable: Set[QueryVariable] = propTypeInfo.objectTypeIri.toString match {

                            // linking prop: get value object var and information which values are requested for dependent resource
                            case OntologyConstants.KnoraApiV2Simple.Resource =>

                                // link value object variable
                                val valObjVar = createUniqueVariableNameFromEntityAndProperty(statementPattern.obj, OntologyConstants.KnoraBase.LinkValue)

                                // return link value object variable and value objects requested for the dependent resource
                                Set(QueryVariable(valObjVar.variableName + variableConcatSuffix))

                            case _ =>
                                statementPattern.obj match {
                                    case queryVar: QueryVariable => Set(QueryVariable(queryVar.variableName + variableConcatSuffix))
                                    case other => throw SparqlSearchException(s"object of a statement involving a non linking property is expected to be a query variable, but $other given.")
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
          * @param mainResourceIris      Iris of main resources to be queried.
          * @param dependentResourceIris Iris of dependent resources to be queried.
          * @param valueObjectIris       Iris of value objects to be queried (for both main and dependent resources)
          * @return the main [[ConstructQuery]] query to be executed.
          */
        def createMainQuery(mainResourceIris: Set[IriRef], dependentResourceIris: Set[IriRef], valueObjectIris: Set[IRI]): ConstructQuery = {

            import SearchResponderV2Constants.ExtendedSearchConstants._

            // WHERE patterns for the main resource variable: check that main resource is a knora-base:Resource and that it is not marked as deleted
            val wherePatternsForMainResource = Seq(
                ValuesPattern(mainResourceVar, mainResourceIris), // a ValuePattern that binds the main resources' Iris to the main resource variable
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
                ValuesPattern(mainAndDependentResourceVar, mainResourceIris ++ dependentResourceIris), // a ValuePattern that binds the main and dependent resources' Iris to a variable
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

            typeInspector <- FastFuture.successful(new ExplicitTypeInspectorV2())
            whereClauseWithoutAnnotations: WhereClause = typeInspector.removeTypeAnnotations(inputQuery.whereClause)
            typeInspectionResult: TypeInspectionResult = typeInspector.inspectTypes(inputQuery.whereClause)

            // Preprocess the query to convert API IRIs to internal IRIs and to set inference per statement.

            preprocessedQuery: ConstructQuery = QueryTraverser.transformConstructToConstruct(
                inputQuery = inputQuery.copy(whereClause = whereClauseWithoutAnnotations),
                transformer = new Preprocessor
            )

            nonTriplestoreSpecificConstructToSelectTransformer: NonTriplestoreSpecificConstructToSelectTransformer = new NonTriplestoreSpecificConstructToSelectTransformer(typeInspectionResult = typeInspectionResult)


            // TODO: if the ORDER BY criterion is a property whose occurrence is not 1, then the logic does not work correctly
            // TODO: the ORDER BY criterion has to be included in a GROUP BY statement, returning more than one row if property occurs more than once

            // Create a Select prequery
            nonTriplestoreSpecficPrequery: SelectQuery = QueryTraverser.transformConstructToSelect(
                inputQuery = preprocessedQuery.copy(orderBy = inputQuery.orderBy, offset = inputQuery.offset), // TODO: This is a workaround to get Order By and OFFSET into the transformer since the preprocessor does not know about it
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

            // a sequence of resource Iris that match the search criteria
            // attention: no permission checking has been done so far
            mainResourceIris: Seq[IRI] = prequeryResponse.results.bindings.map {
                resultRow: VariableResultsRow =>
                    resultRow.rowMap(mainResourceVar.variableName)
            }

            queryResultsSeparatedWithFullQueryPath <- if (mainResourceIris.nonEmpty) {
                // at least one resource matched the prequery

                // variables representing dependent resources
                val dependentResourceVariablesConcat: Set[QueryVariable] = nonTriplestoreSpecificConstructToSelectTransformer.getDependentResourceVariablesGroupConcat

                // get all the Iris for variables representing dependent resources per main resource
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

                                        // Iris are concatenated, split them
                                        depResIri.split(nonTriplestoreSpecificConstructToSelectTransformer.groupConcatSeparator).toSeq

                                    case None => Set.empty[IRI] // no value present
                                }

                        }

                        acc + (mainResIri -> dependentResIris)
                }

                // collect all variables representing resources
                val allResourceVariablesFromTypeInspection: Set[QueryVariable] = typeInspectionResult.typedEntities.collect {
                    case (queryVar: TypeableVariable, nonPropTypeInfo: NonPropertyTypeInfo) if nonPropTypeInfo.typeIri.toString == OntologyConstants.KnoraApiV2Simple.Resource => QueryVariable(queryVar.variableName)
                }.toSet

                // the user may have defined Iris of dependent resources in the input query (type annotations)
                // only add them if they are mentioned in a positive context (not negated like in a FILTER NOT EXISTS or MINUS)
                val dependentResourceIrisFromTypeInspection: Set[IRI] = typeInspectionResult.typedEntities.collect {
                    case (iri: TypeableIri, _: NonPropertyTypeInfo) if whereClauseWithoutAnnotations.positiveEntities.contains(IriRef(iri.iri)) =>
                        iri.iri.toString
                }.toSet

                // the Iris of all dependent resources for all main resources
                val allDependentResourceIris: Set[IRI] = dependentResourceIrisPerMainResource.values.flatten.toSet ++ dependentResourceIrisFromTypeInspection

                // value objects variables present in the prequery's WHERE clause
                val valueObjectVariablesConcat = nonTriplestoreSpecificConstructToSelectTransformer.getValueObjectVarsGroupConcat

                // for each main resource, create a Map of value object variables and their values
                val valueObjectIrisPerMainResource: Map[IRI, Map[QueryVariable, Set[IRI]]] = prequeryResponse.results.bindings.foldLeft(Map.empty[IRI, Map[QueryVariable, Set[IRI]]]) {
                    (acc: Map[IRI, Map[QueryVariable, Set[IRI]]], resultRow: VariableResultsRow) =>

                        val mainResIri: String = resultRow.rowMap(mainResourceVar.variableName)

                        val valueObjVarToIris: Map[QueryVariable, Set[IRI]] = valueObjectVariablesConcat.map {
                            (valueObjVarConcat: QueryVariable) =>

                                // check if key exists (the variable could be contained in an OPTIONAL or a UNION)
                                val valueObjIrisOption: Option[IRI] = resultRow.rowMap.get(valueObjVarConcat.variableName)

                                val valueObjIris: Set[IRI] = valueObjIrisOption match {

                                    case Some(valObjIris) =>

                                        // Iris are concatenated, split them
                                        valObjIris.split(nonTriplestoreSpecificConstructToSelectTransformer.groupConcatSeparator).toSet

                                    case None => Set.empty[IRI] // no value present

                                }

                                valueObjVarConcat -> valueObjIris
                        }.toMap

                        acc + (mainResIri -> valueObjVarToIris)
                }

                // collect all value objects Iris (for all main resources and for all value object variables)
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
                // println(triplestoreSpecificQuery.toSparql)

                for {
                    searchResponse: SparqlConstructResponse <- (storeManager ? SparqlConstructRequest(triplestoreSpecificSparql)).mapTo[SparqlConstructResponse]

                    // separate main resources and value objects (dependent resources are nested)
                    queryResultsSep: Map[IRI, ConstructResponseUtilV2.ResourceWithValueRdfData] = ConstructResponseUtilV2.splitMainResourcesAndValueRdfData(constructQueryResults = searchResponse, requestingUser = requestingUser)

                    // for each main resource check if all dependent resources and value objects are still present after permission checking
                    // this ensures that the user has sufficient permissions on the whole query path
                    queryResWithFullQueryPath = queryResultsSep.foldLeft(Map.empty[IRI, ConstructResponseUtilV2.ResourceWithValueRdfData]) {
                        case (acc: Map[IRI, ConstructResponseUtilV2.ResourceWithValueRdfData], (mainResIri: IRI, values: ConstructResponseUtilV2.ResourceWithValueRdfData)) =>

                            // check for presence of dependent resources:  dependentResourceIrisPerMainResource, dependentResourceIrisFromTypeInspection
                            val expectedDependenResources: Set[IRI] = dependentResourceIrisPerMainResource(mainResIri) ++ dependentResourceIrisFromTypeInspection

                            // check for presence of value objects: valueObjectIrisPerMainResource
                            val expectedValueObjects: Set[IRI] = valueObjectIrisPerMainResource(mainResIri).values.flatten.toSet

                            // value property assertions for the current main resource
                            val valuePropAssertions: Map[IRI, Seq[ConstructResponseUtilV2.ValueRdfData]] = values.valuePropertyAssertions

                            // all the Iris of dependent resources and value objects contained in `valuePropAssertions`
                            val resAndValueObjIris: ResourceIrisAndValueObjectIris = traverseValuePropertyAssertions(valuePropAssertions)

                            // check if the client has sufficient permissions on all dependent resources present in the query path
                            val allDependentResources: Boolean = resAndValueObjIris.resourceIris.intersect(expectedDependenResources) == expectedDependenResources

                            // check if the client has sufficient permissions on all value objects Iris present in the query path
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
                            collectValueVariablesForResource(preprocessedQuery.constructClause, depResVar, typeInspectionResult, nonTriplestoreSpecificConstructToSelectTransformer.groupConcatVariableAppendix)
                    }

                    valueObjectVariablesForDependentResIris: Set[QueryVariable] = dependentResourceIrisFromTypeInspection.flatMap {
                        depResIri =>
                            collectValueVariablesForResource(preprocessedQuery.constructClause, IriRef(iri = depResIri.toSmartIri), typeInspectionResult, nonTriplestoreSpecificConstructToSelectTransformer.groupConcatVariableAppendix)
                    }

                    allValueObjectVariables: Set[QueryVariable] = valueObjectVariablesForAllResVars ++ valueObjectVariablesForDependentResIris

                    // collect requested value object Iris for each resource
                    requestedValObjIrisPerResource: Map[IRI, Set[IRI]] = queryResWithFullQueryPath.map {
                        case (resIri: IRI, assertions: ConstructResponseUtilV2.ResourceWithValueRdfData) =>

                            val valueObjIrisForRes: Map[QueryVariable, Set[IRI]] = valueObjectIrisPerMainResource(resIri)

                            val valObjIrisRequestedForRes: Set[IRI] = allValueObjectVariables.flatMap {
                                (requestedQueryVar: QueryVariable) =>
                                    valueObjIrisForRes.getOrElse(requestedQueryVar, throw AssertionException(s"key $requestedQueryVar is absent in prequery's value object Iris collection for resource $resIri"))
                            }

                            resIri -> valObjIrisRequestedForRes
                    }

                    // filter out those value objects that the user does not want to be returned by the query
                    queryResWithFullQueryPathOnlyRequestedValues: Map[IRI, ConstructResponseUtilV2.ResourceWithValueRdfData] = queryResWithFullQueryPath.map {
                        case (resIri: IRI, assertions: ConstructResponseUtilV2.ResourceWithValueRdfData) =>

                            // get the Iris of all the value objects requested for this resource
                            val valueObjIrisRequestedForRes: Set[IRI] = requestedValObjIrisPerResource.getOrElse(resIri, throw AssertionException(s"key $resIri is absent in requested value object Iris collection for resource $resIri"))

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
                                            (valueObj: ConstructResponseUtilV2.ValueRdfData) =>
                                                // only return those value objects whose Iris are contained in valueObjIrisRequestedForRes
                                                valueObjIrisRequestedForRes(valueObj.valueObjectIri)
                                        }

                                        // if there are link values including a target resource, apply filter to their values too
                                        val valuesFilteredRecursively: Seq[ConstructResponseUtilV2.ValueRdfData] = valuesFiltered.map {
                                            (valObj: ConstructResponseUtilV2.ValueRdfData) =>
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
      * @param requestingUser          the the client making the request.
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
                throw SparqlSearchException(s"Fulltext count query is expected to return exactly one row, but ${countResponse.results.bindings.size} given")
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
      * @param requestingUser          the the client making the request.
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

            // collect the Iris of main resources returned
            mainResourceIris: Set[IRI] = searchResourceByLabelResponse.statements.foldLeft(Set.empty[IRI]) {
                case (acc: Set[IRI], (subjIri: IRI, assertions: Seq[(IRI, String)])) =>
                    //statement.pred == OntologyConstants.KnoraBase.IsMainResource && statement.obj.toBoolean

                    // check if the assertions represent a main resource and include its Iri if so
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