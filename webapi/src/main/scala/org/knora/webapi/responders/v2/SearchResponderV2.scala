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
import org.knora.webapi.messages.store.triplestoremessages._
import org.knora.webapi.messages.v1.responder.usermessages.UserProfileV1
import org.knora.webapi.messages.v1.responder.valuemessages.JulianDayNumberValueV1
import org.knora.webapi.messages.v2.responder._
import org.knora.webapi.messages.v2.responder.resourcemessages.ResourcesGetRequestV2
import org.knora.webapi.messages.v2.responder.searchmessages._
import org.knora.webapi.responders.ResponderWithStandoffV2
import org.knora.webapi.util.ActorUtil._
import org.knora.webapi.util._
import org.knora.webapi.util.search.ApacheLuceneSupport.{CombineSearchTerms, MatchStringWhileTyping}
import org.knora.webapi.util.search._
import org.knora.webapi.util.search.v2._
import org.knora.webapi.util.{ConstructResponseUtilV2, DateUtilV1, StringFormatter}

import scala.collection.mutable
import scala.concurrent.Future

/**
  * Constants used in [[SearchResponderV2]].
  */
object SearchResponderV2Constants {

    /**
      * Constants for fulltext query.
      */
    object FullTextSearchConstants {
        val resourceVar = QueryVariable("resource")
        val resourcePropVar = QueryVariable("resourceProp")
        val resourceObjectVar = QueryVariable("resourceObj")
        val resourceValueObject = QueryVariable("resourceValueObject")
        val resourceValueProp = QueryVariable("resourceValueProp")
        val resourceValueObjectProp = QueryVariable("resourceValueObjectProp")
        val resourceValueObjectObj = QueryVariable("resourceValueObjectObj")

        val standoffNodeVar = QueryVariable("standoffNode")
        val standoffPropVar = QueryVariable("standoffProp")
        val standoffValueVar = QueryVariable("standoffValue")

        val valueObjectConcatVar = QueryVariable("valueObjectConcat")
    }

    /**
      * Constants for extended search.
      */
    object ExtendedSearchConstants {

        // variables representing the main resource and its properties
        val mainResourceVar = QueryVariable("mainResourceVar")

        // variables representing main and dependent resources. direct assertions about them as well as their values
        val mainAndDependentResourceVar = QueryVariable("mainAndDependentResource")
        val mainAndDependentResourcePropVar = QueryVariable("mainAndDependentResourceProp")
        val mainAndDependentResourceObjectVar = QueryVariable("mainAndDependentResourceObj")
        val mainAndDependentResourceValueObject = QueryVariable("mainAndDependentResourceValueObject")
        val mainAndDependentResourceValueProp = QueryVariable("mainAndDependentResourceValueProp")
        val mainAndDependentResourceValueObjectProp = QueryVariable("mainAndDependentResourceValueObjectProp")
        val mainAndDependentResourceValueObjectObj = QueryVariable("mainAndDependentResourceValueObjectObj")

        val standoffNodeVar = QueryVariable("standoffNode")
        val standoffPropVar = QueryVariable("standoffProp")
        val standoffValueVar = QueryVariable("standoffValue")

        val forbiddenResourceIri = "http://data.knora.org/permissions/forbiddenResource"

    }

}

class SearchResponderV2 extends ResponderWithStandoffV2 {

    val knoraIdUtil = new KnoraIdUtil

    def receive = {
        case FullTextSearchCountGetRequestV2(searchValue, limitToProject, limitToResourceClass, userProfile) => future2Message(sender(), fulltextSearchCountV2(searchValue, limitToProject, limitToResourceClass, userProfile), log)
        case FulltextSearchGetRequestV2(searchValue, offset, limitToProject, limitToResourceClass, userProfile) => future2Message(sender(), fulltextSearchV2(searchValue, offset, limitToProject, limitToResourceClass, userProfile), log)
        case ExtendedSearchCountGetRequestV2(query, userProfile) => future2Message(sender(), extendedSearchCountV2(inputQuery = query, userProfile = userProfile), log)
        case ExtendedSearchGetRequestV2(query, userProfile) => future2Message(sender(), extendedSearchV2(inputQuery = query, userProfile = userProfile), log)
        case SearchResourceByLabelCountGetRequestV2(searchValue, limitToProject, limitToResourceClass, userProfile) => future2Message(sender(), searchResourcesByLabelCountV2(searchValue, limitToProject, limitToResourceClass, userProfile), log)
        case SearchResourceByLabelGetRequestV2(searchValue, offset, limitToProject, limitToResourceClass, userProfile) => future2Message(sender(), searchResourcesByLabelV2(searchValue, offset, limitToProject, limitToResourceClass, userProfile), log)
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
                case iriRef: IriRef => // if an Iri is an external knora-api entity (with value object or simple), convert it to an internal Iri
                    if (stringFormatter.isExternalEntityIri(iriRef.iri)) {
                        IriRef(stringFormatter.externalToInternalEntityIri(iriRef.iri, () => throw BadRequestException(s"${iriRef.iri} is not a valid external knora-api entity Iri")))
                    } else {
                        IriRef(stringFormatter.toIri(iriRef.iri, () => throw BadRequestException(s"$iriRef is not a valid IRI")))
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
                    val externalIri = if (stringFormatter.isInternalEntityIri(iriRef.iri)) {
                        stringFormatter.internalEntityIriToApiV2SimpleEntityIri(iriRef.iri, () => throw BadRequestException(s"${iriRef.iri} is not a valid internal knora-api entity Iri"))
                    } else {
                        iriRef.iri
                    }

                    Some(TypeableIri(externalIri))

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
                case IriRef(OntologyConstants.KnoraBase.IsMainResource, _) =>
                    statementPattern.obj match {
                        case XsdLiteral("true", OntologyConstants.Xsd.Boolean) =>
                            statementPattern.subj match {
                                case queryVariable: QueryVariable => Some(queryVariable)
                                case _ => throw SparqlSearchException(s"The subject of ${OntologyConstants.KnoraBase.IsMainResource} must be a variable") // TODO: use the knora-api predicate in the error message?
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

            val typeIriInternal = if (stringFormatter.isExternalEntityIri(nonPropertyTypeInfo.typeIri)) {
                stringFormatter.externalToInternalEntityIri(nonPropertyTypeInfo.typeIri, () => throw BadRequestException(s"${nonPropertyTypeInfo.typeIri} is not a valid external knora-api entity Iri"))
            } else {
                nonPropertyTypeInfo.typeIri
            }

            if (typeIriInternal == OntologyConstants.KnoraBase.Resource) {

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
                    StatementPattern.makeInferred(subj = inputEntity, pred = IriRef(OntologyConstants.Rdf.Type), obj = IriRef(OntologyConstants.KnoraBase.Resource)),
                    StatementPattern.makeExplicit(subj = inputEntity, pred = IriRef(OntologyConstants.KnoraBase.IsDeleted), obj = XsdLiteral(value = "false", datatype = OntologyConstants.Xsd.Boolean))
                )
            } else {
                // inputEntity is target of a value property
                // properties are handled by `convertStatementForPropertyType`, no processing needed here

                Seq.empty[QueryPattern]
            }
        }

        protected def convertStatementForPropertyType(propertyTypeInfo: PropertyTypeInfo, statementPattern: StatementPattern): Seq[QueryPattern] = {

            // convert the type information into an internal Knora Iri if possible
            val objectIri = if (stringFormatter.isExternalEntityIri(propertyTypeInfo.objectTypeIri)) {
                stringFormatter.externalToInternalEntityIri(propertyTypeInfo.objectTypeIri, () => throw BadRequestException(s"${propertyTypeInfo.objectTypeIri} is not a valid external knora-api entity Iri"))
            } else {
                propertyTypeInfo.objectTypeIri
            }

            objectIri match {
                case OntologyConstants.KnoraBase.Resource => {
                    // linking property

                    // make sure that the object is either an Iri or a variable (cannot be a literal)
                    statementPattern.obj match {
                        case iriRef: IriRef => ()
                        case queryVar: QueryVariable => ()
                        case other => throw SparqlSearchException(s"Object of a linking statement must be an Iri or a QueryVariable, but $other given.")
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
                            IriRef(knoraIdUtil.linkPropertyIriToLinkValuePropertyIri(propIri.iri))
                        case literal: XsdLiteral => throw SparqlSearchException(s"literal $literal cannot be used as a predicate")
                        case other => throw SparqlSearchException(s"$other cannot be used as a predicate")
                    }

                    // create statements that represent the link value's properties for the given linking property
                    // do not check for the predicate because inference would not work
                    // instead, linkValueProp restricts the link value objects to be returned
                    val linkValueStatements = Seq(
                        StatementPattern.makeInferred(subj = statementPattern.subj, pred = linkValueProp, obj = linkValueObjVar),
                        StatementPattern.makeExplicit(subj = linkValueObjVar, pred = IriRef(OntologyConstants.Rdf.Type), obj = IriRef(OntologyConstants.KnoraBase.LinkValue)),
                        StatementPattern.makeExplicit(subj = linkValueObjVar, pred = IriRef(OntologyConstants.KnoraBase.IsDeleted), obj = XsdLiteral(value = "false", datatype = OntologyConstants.Xsd.Boolean)),
                        StatementPattern.makeExplicit(subj = linkValueObjVar, pred = IriRef(OntologyConstants.Rdf.Subject), obj = statementPattern.subj),
                        StatementPattern.makeExplicit(subj = linkValueObjVar, pred = IriRef(OntologyConstants.Rdf.Object), obj = statementPattern.obj)
                    )

                    // linking property: just include the original statement relating the subject to the target of the link
                    statementPattern +: linkValueStatements
                }

                case literalType: IRI => {
                    // value property

                    // make sure that the object is a query variable (literals are not supported yet)
                    statementPattern.obj match {
                        case queryVar: QueryVariable => valueObjectVariables += queryVar // add variable to collection representing value objects
                        case other => throw SparqlSearchException(s"Object of a value property statement must be a QueryVariable, but $other given.")
                    }

                    // check that value object is not marked as deleted
                    val valueObjectIsNotDeleted = StatementPattern.makeExplicit(subj = statementPattern.obj, pred = IriRef(OntologyConstants.KnoraBase.IsDeleted), obj = XsdLiteral(value = "false", datatype = OntologyConstants.Xsd.Boolean))

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
            OntologyConstants.Xsd.Integer -> OntologyConstants.KnoraBase.ValueHasInteger,
            OntologyConstants.Xsd.Decimal -> OntologyConstants.KnoraBase.ValueHasDecimal,
            OntologyConstants.Xsd.Boolean -> OntologyConstants.KnoraBase.ValueHasBoolean,
            OntologyConstants.Xsd.String -> OntologyConstants.KnoraBase.ValueHasString,
            OntologyConstants.KnoraBase.Date -> OntologyConstants.KnoraBase.ValueHasStartJDN
        )

        /**
          * Given a variable representing a linking property, creates a variable respresenting the corresponding link value property.
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
          * @param expression           the transformed Filter expression.
          * @param additionalStatements additionally created statement patterns.
          */
        protected case class TransformedFilterExpression(expression: Expression, additionalStatements: Seq[StatementPattern] = Seq.empty[StatementPattern])

        /**
          * Transforms a Filter expression provided in the input query (knora-api simple) into a knora-base compliant Filter expression.
          *
          * @param filterExpression the Filter expression to be transformed.
          * @param typeInspection   the results of type inspection.
          * @return a [[TransformedFilterExpression]].
          */
        protected def transformFilterExpression(filterExpression: Expression, typeInspection: TypeInspectionResult): TransformedFilterExpression = {

            filterExpression match {

                case filterCompare: CompareExpression =>

                    // left argument of a CompareExpression is expected to be a QueryVariable
                    val queryVar: QueryVariable = filterCompare.leftArg match {

                        case queryVar: QueryVariable => queryVar

                        case other => throw SparqlSearchException(s"Left argument of a Filter CompareExpression is expected to be a QueryVariable, but $other is given")
                    }

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
                                filterCompare.rightArg match {
                                    case iriRef: IriRef =>

                                        // make sure that the comparison operator is a `CompareExpressionOperator.EQUALS`
                                        if (filterCompare.operator != CompareExpressionOperator.EQUALS) throw SparqlSearchException(s"Comparison operator in a CompareExpression for a property type is expected to be ${CompareExpressionOperator.EQUALS}, but ${filterCompare.operator} given. For negations use 'FILTER NOT EXISTS' ")

                                        val objectTypeIriInternal = if (stringFormatter.isExternalEntityIri(propInfo.objectTypeIri)) {
                                            stringFormatter.externalToInternalEntityIri(propInfo.objectTypeIri, () => throw BadRequestException(s"${propInfo.objectTypeIri} is not a valid external knora-api entity Iri"))
                                        } else {
                                            propInfo.objectTypeIri
                                        }

                                        val userProvidedRestriction = CompareExpression(queryVar, filterCompare.operator, iriRef)

                                        // check if the objectTypeIri of propInfo is knora-base:Resource
                                        // if so, it is a linking property and its link value property must be restricted too
                                        objectTypeIriInternal match {
                                            case OntologyConstants.KnoraBase.Resource =>

                                                // it is a linking property, restrict the link value property

                                                val restrictionForLinkValueProp = CompareExpression(
                                                    leftArg = createlinkValuePropertyVariableFromLinkingPropertyVariable(queryVar), // the same variable was created during statement processing in WHERE clause in `convertStatementForPropertyType`
                                                    operator = filterCompare.operator,
                                                    rightArg = IriRef(knoraIdUtil.linkPropertyIriToLinkValuePropertyIri(iriRef.iri))) // create link value property from linking property

                                                TransformedFilterExpression(AndExpression(
                                                    leftArg = userProvidedRestriction,
                                                    rightArg = restrictionForLinkValueProp)
                                                )

                                            case other =>
                                                // not a linking property, just return the provided restriction
                                                TransformedFilterExpression(userProvidedRestriction)
                                        }


                                    case other => throw SparqlSearchException(s"right argument of CompareExpression is expected to be an Iri representing a property, but $other is given")
                                }

                            case nonPropInfo: NonPropertyTypeInfo =>

                                // the left arg queryVar is a variable representing a value
                                // get the internal Iri of the value type, if possible (xsd types are not internal types).
                                val typeIriInternal = if (stringFormatter.isExternalEntityIri(nonPropInfo.typeIri)) {
                                    stringFormatter.externalToInternalEntityIri(nonPropInfo.typeIri, () => throw BadRequestException(s"${nonPropInfo.typeIri} is not a valid external knora-api entity Iri"))
                                } else {
                                    nonPropInfo.typeIri
                                }

                                // depending on the value type, transform the given Filter expression.
                                // add an extra level by getting the value literal from the value object.
                                // queryVar refers to the value object, for the value literal an extra variable has to be created, taking its type into account.
                                typeIriInternal match {

                                    case OntologyConstants.Xsd.Integer =>

                                        // make sure that the right argument is an integer literal
                                        val integerLiteral: XsdLiteral = filterCompare.rightArg match {
                                            case intLiteral: XsdLiteral if intLiteral.datatype == OntologyConstants.Xsd.Integer => intLiteral

                                            case other => throw SparqlSearchException(s"right argument in CompareExpression for integer property was expected to be an integer literal, but $other is given.")
                                        }

                                        // create a variable representing the integer literal
                                        val intValHasInteger: QueryVariable = createUniqueVariableNameFromEntityAndProperty(queryVar, OntologyConstants.KnoraBase.ValueHasInteger)

                                        // add this variable to the collection of additionally created variables (needed for sorting in the prequery)
                                        valueVariablesCreatedInFilters.put(queryVar, intValHasInteger)

                                        TransformedFilterExpression(
                                            CompareExpression(intValHasInteger, filterCompare.operator, integerLiteral),
                                            Seq(
                                                // connects the value object with the value literal
                                                StatementPattern.makeExplicit(subj = queryVar, pred = IriRef(OntologyConstants.KnoraBase.ValueHasInteger), intValHasInteger)
                                            )
                                        )

                                    case OntologyConstants.Xsd.Decimal =>

                                        // make sure that the right argument is a decimal or integer literal
                                        val decimalLiteral: XsdLiteral = filterCompare.rightArg match {
                                            case decimalLiteral: XsdLiteral if decimalLiteral.datatype == OntologyConstants.Xsd.Decimal || decimalLiteral.datatype == OntologyConstants.Xsd.Integer => decimalLiteral

                                            case other => throw SparqlSearchException(s"right argument in CompareExpression for decimal property was expected to be a decimal or an integer literal, but $other is given.")
                                        }

                                        // create a variable representing the decimal literal
                                        val decimalValHasDecimal = createUniqueVariableNameFromEntityAndProperty(queryVar, OntologyConstants.KnoraBase.ValueHasDecimal)

                                        // add this variable to the collection of additionally created variables (needed for sorting in the prequery)
                                        valueVariablesCreatedInFilters.put(queryVar, decimalValHasDecimal)

                                        TransformedFilterExpression(
                                            CompareExpression(decimalValHasDecimal, filterCompare.operator, decimalLiteral),
                                            Seq(
                                                // connects the value object with the value literal
                                                StatementPattern.makeExplicit(subj = queryVar, pred = IriRef(OntologyConstants.KnoraBase.ValueHasDecimal), decimalValHasDecimal)
                                            )
                                        )

                                    case OntologyConstants.Xsd.Boolean =>

                                        // make sure that the right argument is a boolean literal
                                        val booleanLiteral: XsdLiteral = filterCompare.rightArg match {
                                            case booleanLiteral: XsdLiteral if booleanLiteral.datatype == OntologyConstants.Xsd.Boolean => booleanLiteral

                                            case other => throw SparqlSearchException(s"right argument in CompareExpression for boolean property was expected to be a boolean literal, but $other is given.")
                                        }

                                        // create a variable representing the boolean literal
                                        val booleanValHasBoolean = createUniqueVariableNameFromEntityAndProperty(queryVar, OntologyConstants.KnoraBase.ValueHasBoolean)

                                        // add this variable to the collection of additionally created variables (needed for sorting in the prequery)
                                        valueVariablesCreatedInFilters.put(queryVar, booleanValHasBoolean)

                                        // check if operator is supported for boolean
                                        if (!(filterCompare.operator.equals(CompareExpressionOperator.EQUALS) || filterCompare.operator.equals(CompareExpressionOperator.NOT_EQUALS))) {
                                            throw SparqlSearchException(s"Filter expressions for a boolean value supports the following operators: ${CompareExpressionOperator.EQUALS}, ${CompareExpressionOperator.NOT_EQUALS}, but ${filterCompare.operator} given")
                                        }

                                        TransformedFilterExpression(
                                            CompareExpression(booleanValHasBoolean, filterCompare.operator, booleanLiteral),
                                            Seq(
                                                // connects the value object with the value literal
                                                StatementPattern.makeExplicit(subj = queryVar, pred = IriRef(OntologyConstants.KnoraBase.ValueHasBoolean), booleanValHasBoolean)
                                            )
                                        )

                                    case OntologyConstants.Xsd.String =>

                                        // make sure that the right argument is a string literal
                                        val stringLiteral: XsdLiteral = filterCompare.rightArg match {
                                            case strLiteral: XsdLiteral if strLiteral.datatype == OntologyConstants.Xsd.String => strLiteral

                                            case other => throw SparqlSearchException(s"right argument in CompareExpression for string property was expected to be a string literal, but $other is given.")
                                        }

                                        // create a variable representing the string literal
                                        val textValHasString = createUniqueVariableNameFromEntityAndProperty(queryVar, OntologyConstants.KnoraBase.ValueHasString)

                                        // add this variable to the collection of additionally created variables (needed for sorting in the prequery)
                                        valueVariablesCreatedInFilters.put(queryVar, textValHasString)

                                        // check if operator is supported for string operations
                                        if (!(filterCompare.operator.equals(CompareExpressionOperator.EQUALS) || filterCompare.operator.equals(CompareExpressionOperator.NOT_EQUALS))) {
                                            throw SparqlSearchException(s"Filter expressions for a string value supports the following operators: ${CompareExpressionOperator.EQUALS}, ${CompareExpressionOperator.NOT_EQUALS}, but ${filterCompare.operator} given")
                                        }

                                        TransformedFilterExpression(
                                            CompareExpression(textValHasString, filterCompare.operator, stringLiteral),
                                            Seq(
                                                // connects the value object with the value literal
                                                StatementPattern.makeExplicit(subj = queryVar, pred = IriRef(OntologyConstants.KnoraBase.ValueHasString), textValHasString)
                                            )
                                        )

                                    case OntologyConstants.KnoraBase.Date =>

                                        // make sure that the right argument is a string literal (dates are represented as knora date strings in knora-api simple)
                                        val dateStringLiteral: _root_.org.knora.webapi.util.search.XsdLiteral = filterCompare.rightArg match {
                                            case dateStrLiteral: XsdLiteral if dateStrLiteral.datatype == OntologyConstants.Xsd.String => dateStrLiteral

                                            case other => throw SparqlSearchException(s"right argument in CompareExpression for date property was expected to be a string literal representing a date, but $other is given.")
                                        }

                                        // validate Knora  date string
                                        val dateStr: String = stringFormatter.toDate(dateStringLiteral.value, () => throw BadRequestException(s"${dateStringLiteral.value} is not a valid date string"))

                                        val date: JulianDayNumberValueV1 = DateUtilV1.createJDNValueV1FromDateString(dateStr)

                                        // create a variable representing the period's start
                                        val dateValueHasStartVar = createUniqueVariableNameFromEntityAndProperty(queryVar, OntologyConstants.KnoraBase.ValueHasStartJDN)

                                        // sort dates by their period's start (in the prequery)
                                        valueVariablesCreatedInFilters.put(queryVar, dateValueHasStartVar)

                                        // create a variable representing the period's end
                                        val dateValueHasEndVar = createUniqueVariableNameFromEntityAndProperty(queryVar, OntologyConstants.KnoraBase.ValueHasEndJDN)

                                        // connects the value object with the periods start variable
                                        val dateValStartStatement = StatementPattern.makeExplicit(subj = queryVar, pred = IriRef(OntologyConstants.KnoraBase.ValueHasStartJDN), obj = dateValueHasStartVar)

                                        // connects the value object with the periods end variable
                                        val dateValEndStatement = StatementPattern.makeExplicit(subj = queryVar, pred = IriRef(OntologyConstants.KnoraBase.ValueHasEndJDN), obj = dateValueHasEndVar)

                                        // process filter expression based on given comparison operator
                                        filterCompare.operator match {

                                            case CompareExpressionOperator.EQUALS =>

                                                // any overlap in considered as equality
                                                val leftArgFilter = CompareExpression(XsdLiteral(date.dateval1.toString, OntologyConstants.Xsd.Integer), CompareExpressionOperator.LESS_THAN_OR_EQUAL_TO, dateValueHasEndVar)

                                                val rightArgFilter = CompareExpression(XsdLiteral(date.dateval2.toString, OntologyConstants.Xsd.Integer), CompareExpressionOperator.GREATER_THAN_OR_EQUAL_TO, dateValueHasStartVar)

                                                val filter = AndExpression(leftArgFilter, rightArgFilter)

                                                TransformedFilterExpression(
                                                    filter,
                                                    Seq(
                                                        dateValStartStatement, dateValEndStatement
                                                    )
                                                )

                                            case CompareExpressionOperator.NOT_EQUALS =>

                                                // no overlap in considered as inequality (negation of equality)
                                                val leftArgFilter = CompareExpression(XsdLiteral(date.dateval1.toString, OntologyConstants.Xsd.Integer), CompareExpressionOperator.GREATER_THAN, dateValueHasEndVar)

                                                val rightArgFilter = CompareExpression(XsdLiteral(date.dateval2.toString, OntologyConstants.Xsd.Integer), CompareExpressionOperator.LESS_THAN, dateValueHasStartVar)

                                                val filter = OrExpression(leftArgFilter, rightArgFilter)

                                                TransformedFilterExpression(
                                                    filter,
                                                    Seq(
                                                        dateValStartStatement, dateValEndStatement
                                                    )
                                                )

                                            case CompareExpressionOperator.LESS_THAN =>

                                                // period ends before indicated period
                                                val filter = CompareExpression(dateValueHasEndVar, CompareExpressionOperator.LESS_THAN, XsdLiteral(date.dateval1.toString, OntologyConstants.Xsd.Integer))

                                                TransformedFilterExpression(
                                                    filter,
                                                    Seq(dateValEndStatement)
                                                )

                                            case CompareExpressionOperator.LESS_THAN_OR_EQUAL_TO =>

                                                // period ends before indicated period or equals it (any overlap)
                                                val filter = CompareExpression(dateValueHasStartVar, CompareExpressionOperator.LESS_THAN_OR_EQUAL_TO, XsdLiteral(date.dateval2.toString, OntologyConstants.Xsd.Integer))

                                                TransformedFilterExpression(
                                                    filter,
                                                    Seq(dateValStartStatement)
                                                )

                                            case CompareExpressionOperator.GREATER_THAN =>

                                                // period starts after end of indicated period
                                                val filter = CompareExpression(dateValueHasStartVar, CompareExpressionOperator.GREATER_THAN, XsdLiteral(date.dateval2.toString, OntologyConstants.Xsd.Integer))

                                                TransformedFilterExpression(
                                                    filter,
                                                    Seq(dateValStartStatement)
                                                )

                                            case CompareExpressionOperator.GREATER_THAN_OR_EQUAL_TO =>

                                                // period starts after indicated period or equals it (any overlap)
                                                val filter = CompareExpression(dateValueHasEndVar, CompareExpressionOperator.GREATER_THAN_OR_EQUAL_TO, XsdLiteral(date.dateval1.toString, OntologyConstants.Xsd.Integer))

                                                TransformedFilterExpression(
                                                    filter,
                                                    Seq(dateValEndStatement)
                                                )


                                            case other => throw SparqlSearchException(s"operator $other not supported in filter expressions for dates")


                                        }

                                    case other => throw NotImplementedException(s"Value type $other not supported in FilterExpression")

                                }

                        }


                    } else {
                        throw SparqlSearchException(s"type information about $queryVar is missing")
                    }


                case filterOr: OrExpression =>
                    // recursively call this method for both arguments
                    val filterExpressionLeft = transformFilterExpression(filterOr.leftArg, typeInspection)
                    val filterExpressionRight = transformFilterExpression(filterOr.rightArg, typeInspection)

                    // recreate Or expression and include additional statements
                    TransformedFilterExpression(
                        OrExpression(filterExpressionLeft.expression, filterExpressionRight.expression),
                        filterExpressionLeft.additionalStatements ++ filterExpressionRight.additionalStatements
                    )


                case filterAnd: AndExpression =>
                    // recursively call this method for both arguments
                    val filterExpressionLeft = transformFilterExpression(filterAnd.leftArg, typeInspection)
                    val filterExpressionRight = transformFilterExpression(filterAnd.rightArg, typeInspection)

                    // recreate And expression and include additional statements
                    TransformedFilterExpression(
                        AndExpression(filterExpressionLeft.expression, filterExpressionRight.expression),
                        filterExpressionLeft.additionalStatements ++ filterExpressionRight.additionalStatements
                    )

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
            namedGraph = statement.namedGraph match {
                case Some(IriRef(OntologyConstants.NamedGraphs.KnoraExplicitNamedGraph, _)) => Some(IriRef(OntologyConstants.NamedGraphs.GraphDBExplicitNamedGraph))
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
            case IriRef(iriLiteral, _) => iriLiteral
            case XsdLiteral(stringLiteral, _) => stringLiteral
            case _ => throw SparqlSearchException(s"A unique variable could not be made for $entity")
        }

        entityStr.replaceAll("[:/.#-]", "").replaceAll("\\s", "") // TODO: check if this is complete and if it could lea to collision of variable names
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

                if (assertion.targetResource.nonEmpty) {
                    // this is a link value
                    // recursively traverse the dependent resource's values

                    val dependentRes: ConstructResponseUtilV2.ResourceWithValueRdfData = assertion.targetResource.get

                    // recursively traverse the link value's nested resource and its assertions
                    val resAndValObjIrisForDependentRes: ResourceIrisAndValueObjectIris = traverseValuePropertyAssertions(dependentRes.valuePropertyAssertions)
                    // get the dependent resource's Iri from the current link value's rdf:object
                    val dependentResIri: IRI = assertion.assertions.getOrElse(OntologyConstants.Rdf.Object, throw InconsistentTriplestoreDataException(s"expected ${OntologyConstants.Rdf.Object} for link value ${assertion.valueObjectIri}"))

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
      * @param userProfile the user making the request.
      * @return the forbidden resource.
      */
    private def getForbiddenResource(userProfile: UserProfileV1) = {
        import SearchResponderV2Constants.ExtendedSearchConstants.forbiddenResourceIri

        for {

            forbiddenResSeq: ReadResourcesSequenceV2 <- (responderManager ? ResourcesGetRequestV2(resourceIris = Seq(forbiddenResourceIri), userProfile = userProfile)).mapTo[ReadResourcesSequenceV2]
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
      * @param userProfile          the profile of the client making the request.
      * @return a [[ReadResourcesSequenceV2]] representing the amount of resources that have been found.
      */
    private def fulltextSearchCountV2(searchValue: String, limitToProject: Option[IRI], limitToResourceClass: Option[IRI], userProfile: UserProfileV1): Future[ReadResourcesSequenceV2] = {

        val searchTerms: CombineSearchTerms = CombineSearchTerms(searchValue)

        for {
            countSparql <- Future(queries.sparql.v2.txt.searchFulltext(
                triplestore = settings.triplestoreType,
                searchTerms = searchTerms,
                limitToProject = limitToProject,
                limitToResourceClass = limitToResourceClass,
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
      * @param userProfile          the profile of the client making the request.
      * @return a [[ReadResourcesSequenceV2]] representing the resources that have been found.
      */
    private def fulltextSearchV2(searchValue: String, offset: Int, limitToProject: Option[IRI], limitToResourceClass: Option[IRI], userProfile: UserProfileV1): Future[ReadResourcesSequenceV2] = {

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
                ValuesPattern(resourceVar, resourceIris.map(IriRef(_))), // a ValuePattern that binds the resource Iris to the resource variable
                StatementPattern.makeInferred(subj = resourceVar, pred = IriRef(OntologyConstants.Rdf.Type), obj = IriRef(OntologyConstants.KnoraBase.Resource)),
                StatementPattern.makeExplicit(subj = resourceVar, pred = IriRef(OntologyConstants.KnoraBase.IsDeleted), obj = XsdLiteral(value = "false", datatype = OntologyConstants.Xsd.Boolean)),
                StatementPattern.makeExplicit(subj = resourceVar, pred = resourcePropVar, obj = resourceObjectVar)
            )

            //  mark resources as the main resource and a knora-base:Resource in CONSTRUCT clause and return direct assertions about resources
            val constructPatternsForResources = Seq(
                StatementPattern(subj = resourceVar, pred = IriRef(OntologyConstants.KnoraBase.IsMainResource), obj = XsdLiteral(value = "true", datatype = OntologyConstants.Xsd.Boolean)),
                StatementPattern(subj = resourceVar, pred = IriRef(OntologyConstants.Rdf.Type), obj = IriRef(OntologyConstants.KnoraBase.Resource)),
                StatementPattern(subj = resourceVar, pred = resourcePropVar, obj = resourceObjectVar)
            )

            if (valueObjectIris.nonEmpty) {
                // value objects are to be queried

                // WHERE patterns for statements about the resources' values
                val wherePatternsForValueObjects = Seq(
                    ValuesPattern(resourceValueObject, valueObjectIris.map(iri => IriRef(iri))),
                    StatementPattern.makeInferred(subj = resourceVar, pred = IriRef(OntologyConstants.KnoraBase.HasValue), obj = resourceValueObject),
                    StatementPattern.makeExplicit(subj = resourceVar, pred = resourceValueProp, obj = resourceValueObject),
                    StatementPattern.makeExplicit(subj = resourceValueObject, pred = IriRef(OntologyConstants.KnoraBase.IsDeleted), obj = XsdLiteral(value = "false", datatype = OntologyConstants.Xsd.Boolean)),
                    StatementPattern.makeExplicit(subj = resourceValueObject, pred = resourceValueObjectProp, obj = resourceValueObjectObj)
                )

                // return assertions about value objects
                val constructPatternsForValueObjects = Seq(
                    StatementPattern(subj = resourceVar, pred = IriRef(OntologyConstants.KnoraBase.HasValue), obj = resourceValueObject),
                    StatementPattern(subj = resourceVar, pred = resourceValueProp, obj = resourceValueObject),
                    StatementPattern(subj = resourceValueObject, pred = resourceValueObjectProp, obj = resourceValueObjectObj)
                )

                // WHERE patterns for standoff belonging to value objects (if any)
                val wherePatternsForStandoff = Seq(
                    ValuesPattern(resourceValueObject, valueObjectIris.map(iri => IriRef(iri))),
                    StatementPattern.makeExplicit(subj = resourceValueObject, pred = IriRef(OntologyConstants.KnoraBase.ValueHasStandoff), obj = standoffNodeVar),
                    StatementPattern.makeExplicit(subj = standoffNodeVar, pred = standoffPropVar, obj = standoffValueVar)
                )

                // return standoff
                val constructPatternsForStandoff = Seq(
                    StatementPattern(subj = resourceValueObject, pred = IriRef(OntologyConstants.KnoraBase.ValueHasStandoff), obj = standoffNodeVar),
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
                limitToResourceClass = limitToResourceClass,
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
                case resultRow: VariableResultsRow =>
                    resultRow.rowMap(resourceVar.variableName)
            }

            // make sure that the prequery returned some results
            queryResultsSeparatedWithFullQueryPath: Map[IRI, ConstructResponseUtilV2.ResourceWithValueRdfData] <- if (resourceIris.nonEmpty) {

                // for each resource, create a Set of value object Iris
                val valueObjectIrisPerResource: Map[IRI, Set[IRI]] = prequeryResponse.results.bindings.foldLeft(Map.empty[IRI, Set[IRI]]) {
                    (acc: Map[IRI, Set[IRI]], resultRow: VariableResultsRow) =>

                        val mainResIri: String = resultRow.rowMap(resourceVar.variableName)

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
                    queryResultsSep = ConstructResponseUtilV2.splitMainResourcesAndValueRdfData(constructQueryResults = searchResponse, userProfile = userProfile)

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

                getForbiddenResource(userProfile)
            } else {
                // all resources visible, no need for the forbidden resource
                Future(None)
            }

            // get the mappings
            mappingsAsMap <- getMappingsFromQueryResultsSeparated(queryResultsSeparatedWithFullQueryPath, userProfile)

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
      * @param userProfile the profile of the client making the request.
      * @return a [[ReadResourcesSequenceV2]] representing the resources that have been found.
      */
    private def extendedSearchCountV2(inputQuery: ConstructQuery, apiSchema: ApiV2Schema = ApiV2Simple, userProfile: UserProfileV1) = {

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
                val filterExpression: TransformedFilterExpression = transformFilterExpression(filterPattern.expression, typeInspection = typeInspectionResult)

                filterExpression.additionalStatements :+ FilterPattern(filterExpression.expression)
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

            typeInspector <- FastFuture.successful(new ExplicitTypeInspectorV2(apiSchema))
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
      * @param userProfile the profile of the client making the request.
      * @return a [[ReadResourcesSequenceV2]] representing the resources that have been found.
      */
    private def extendedSearchV2(inputQuery: ConstructQuery, apiSchema: ApiV2Schema = ApiV2Simple, userProfile: UserProfileV1): Future[ReadResourcesSequenceV2] = {

        if (apiSchema != ApiV2Simple) {
            throw SparqlSearchException("Only api v2 simple is supported in v2 extended search")
        }

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

                val filterExpression: TransformedFilterExpression = transformFilterExpression(filterPattern.expression, typeInspection = typeInspectionResult)

                filterExpression.additionalStatements :+ FilterPattern(filterExpression.expression)

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
                                val propertyIri: IRI = typeInfo match {
                                    case nonPropertyTypeInfo: NonPropertyTypeInfo =>
                                        val internalTypeIri = if (stringFormatter.isExternalEntityIri(nonPropertyTypeInfo.typeIri)) {
                                            IriRef(stringFormatter.externalToInternalEntityIri(nonPropertyTypeInfo.typeIri, () => throw BadRequestException(s"${nonPropertyTypeInfo.typeIri} is not a valid external knora-api entity Iri")))
                                        } else {
                                            IriRef(stringFormatter.toIri(nonPropertyTypeInfo.typeIri, () => throw BadRequestException(s"${nonPropertyTypeInfo.typeIri} is not a valid IRI")))
                                        }
                                        literalTypesToValueTypeIris.getOrElse(internalTypeIri.iri, throw SparqlSearchException(s"Type ${internalTypeIri.iri} is not supported in ORDER BY"))

                                    case _: PropertyTypeInfo => throw SparqlSearchException(s"Variable ${criterion.queryVariable.variableName} represents a property, and therefore cannot be used in ORDER BY")
                                }

                                // Generate the variable name.
                                val variableForLiteral: QueryVariable = createUniqueVariableNameFromEntityAndProperty(criterion.queryVariable, propertyIri)

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

                // dependent resource variables as order by criteria
                val orderByDependentResVars: Seq[OrderCriterion] = dependentResourceVariables.map {
                    (resVar: QueryVariable) =>
                        OrderCriterion(
                            queryVariable = resVar,
                            isAscending = true
                        )
                }.toSeq

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
          * Recursively collects variables representing values that are present in the CONSTRUCT clause of the input query for the given [[Entity]] representing a resource.
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
                        case iri: IriRef =>
                            val externalIri = if (stringFormatter.isInternalEntityIri(iri.iri)) {
                                stringFormatter.internalEntityIriToApiV2SimpleEntityIri(iri.iri, () => throw BadRequestException(s"${iri.iri} is not a valid internal knora-api entity Iri"))
                            } else {
                                iri.iri
                            }

                            TypeableIri(externalIri)

                        case variable: QueryVariable => TypeableVariable(variable.variableName)

                        case other => throw SparqlSearchException(s"Expected an Iri or a variable as the predicate of a statement, but $other given")
                    }

                    // if the given key exists in the type annotations map, add it to the collection
                    if (typeInspection.typedEntities.contains(typeableEntity)) {

                        val propTypeInfo: PropertyTypeInfo = typeInspection.typedEntities(typeableEntity) match {
                            case propType: PropertyTypeInfo => propType

                            case nonPropType: NonPropertyTypeInfo =>
                                throw SparqlSearchException(s"PropertyTypeInfo was expected for predicate ${statementPattern.pred} in type annotations, but NonPropertyTypeInfo given.")

                        }

                        // convert the type information into an internal Knora Iri if possible
                        val objectIri = if (stringFormatter.isExternalEntityIri(propTypeInfo.objectTypeIri)) {
                            stringFormatter.externalToInternalEntityIri(propTypeInfo.objectTypeIri, () => throw BadRequestException(s"${propTypeInfo.objectTypeIri} is not a valid external knora-api entity Iri"))
                        } else {
                            propTypeInfo.objectTypeIri
                        }

                        val valueObjectVariable: Set[QueryVariable] = objectIri match {

                            // linking prop: get value object var and information which values are requested for dependent resource
                            case OntologyConstants.KnoraBase.Resource =>

                                // recursively get value objects requested for dependent resource: statement.obj represents a dependent resource (query variable or IRI)
                                val valObjVarsForDependentRes: Set[QueryVariable] = collectValueVariablesForResource(constructClause, statementPattern.obj, typeInspection, variableConcatSuffix)

                                // link value object variable
                                val valObjVar = createUniqueVariableNameFromEntityAndProperty(statementPattern.obj, OntologyConstants.KnoraBase.LinkValue)

                                // return link value object variable and value objects requested for the dependent resource
                                valObjVarsForDependentRes + QueryVariable(valObjVar.variableName + variableConcatSuffix)

                            case nonLinkingProp =>
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
                StatementPattern.makeInferred(subj = mainResourceVar, pred = IriRef(OntologyConstants.Rdf.Type), obj = IriRef(OntologyConstants.KnoraBase.Resource)),
                StatementPattern.makeExplicit(subj = mainResourceVar, pred = IriRef(OntologyConstants.KnoraBase.IsDeleted), obj = XsdLiteral(value = "false", datatype = OntologyConstants.Xsd.Boolean))
            )

            // mark main resource variable in CONSTRUCT clause
            val constructPatternsForMainResource = Seq(
                StatementPattern(subj = mainResourceVar, pred = IriRef(OntologyConstants.KnoraBase.IsMainResource), obj = XsdLiteral(value = "true", datatype = OntologyConstants.Xsd.Boolean))
            )

            // since a CONSTRUCT query returns a flat list of triples, we can handle main and dependent resources in the same way

            // WHERE patterns for direct statements about the main resource and dependent resources
            val wherePatternsForMainAndDependentResources = Seq(
                ValuesPattern(mainAndDependentResourceVar, mainResourceIris ++ dependentResourceIris), // a ValuePattern that binds the main and dependent resources' Iris to a variable
                StatementPattern.makeInferred(subj = mainAndDependentResourceVar, pred = IriRef(OntologyConstants.Rdf.Type), obj = IriRef(OntologyConstants.KnoraBase.Resource)),
                StatementPattern.makeExplicit(subj = mainAndDependentResourceVar, pred = IriRef(OntologyConstants.KnoraBase.IsDeleted), obj = XsdLiteral(value = "false", datatype = OntologyConstants.Xsd.Boolean)),
                StatementPattern.makeExplicit(subj = mainAndDependentResourceVar, pred = mainAndDependentResourcePropVar, obj = mainAndDependentResourceObjectVar)
            )

            // mark main and dependent resources as a knora-base:Resource in CONSTRUCT clause and return direct assertions about all resources
            val constructPatternsForMainAndDependentResources = Seq(
                StatementPattern(subj = mainAndDependentResourceVar, pred = IriRef(OntologyConstants.Rdf.Type), obj = IriRef(OntologyConstants.KnoraBase.Resource)),
                StatementPattern(subj = mainAndDependentResourceVar, pred = mainAndDependentResourcePropVar, obj = mainAndDependentResourceObjectVar)
            )

            if (valueObjectIris.nonEmpty) {
                // value objects are to be queried

                // WHERE patterns for statements about the main and dependent resources' values
                val wherePatternsForMainAndDependentResourcesValues = Seq(
                    ValuesPattern(mainAndDependentResourceValueObject, valueObjectIris.map(iri => IriRef(iri))),
                    StatementPattern.makeInferred(subj = mainAndDependentResourceVar, pred = IriRef(OntologyConstants.KnoraBase.HasValue), obj = mainAndDependentResourceValueObject),
                    StatementPattern.makeExplicit(subj = mainAndDependentResourceVar, pred = mainAndDependentResourceValueProp, obj = mainAndDependentResourceValueObject),
                    StatementPattern.makeExplicit(subj = mainAndDependentResourceValueObject, pred = IriRef(OntologyConstants.KnoraBase.IsDeleted), obj = XsdLiteral(value = "false", datatype = OntologyConstants.Xsd.Boolean)),
                    StatementPattern.makeExplicit(subj = mainAndDependentResourceValueObject, pred = mainAndDependentResourceValueObjectProp, obj = mainAndDependentResourceValueObjectObj)
                )

                // return assertions about the main and dependent resources' values in CONSTRUCT clause
                val constructPatternsForMainAndDependentResourcesValues = Seq(
                    StatementPattern(subj = mainAndDependentResourceVar, pred = IriRef(OntologyConstants.KnoraBase.HasValue), obj = mainAndDependentResourceValueObject),
                    StatementPattern(subj = mainAndDependentResourceVar, pred = mainAndDependentResourceValueProp, obj = mainAndDependentResourceValueObject),
                    StatementPattern(subj = mainAndDependentResourceValueObject, pred = mainAndDependentResourceValueObjectProp, obj = mainAndDependentResourceValueObjectObj)
                )

                // WHERE patterns for standoff belonging to value objects (if any)
                val wherePatternsForStandoff = Seq(
                    ValuesPattern(mainAndDependentResourceValueObject, valueObjectIris.map(iri => IriRef(iri))),
                    StatementPattern.makeExplicit(subj = mainAndDependentResourceValueObject, pred = IriRef(OntologyConstants.KnoraBase.ValueHasStandoff), obj = standoffNodeVar),
                    StatementPattern.makeExplicit(subj = standoffNodeVar, pred = standoffPropVar, obj = standoffValueVar)
                )

                // return standoff assertions
                val constructPatternsForStandoff = Seq(
                    StatementPattern(subj = mainAndDependentResourceValueObject, pred = IriRef(OntologyConstants.KnoraBase.ValueHasStandoff), obj = standoffNodeVar),
                    StatementPattern(subj = standoffNodeVar, pred = standoffPropVar, obj = standoffValueVar)
                )

                ConstructQuery(
                    constructClause = ConstructClause(
                        statements = constructPatternsForMainResource ++ constructPatternsForMainAndDependentResources ++ constructPatternsForMainAndDependentResourcesValues ++ constructPatternsForStandoff
                    ),
                    whereClause = WhereClause(
                        Seq(
                            UnionPattern(
                                Seq(wherePatternsForMainResource, wherePatternsForMainAndDependentResources, wherePatternsForMainAndDependentResourcesValues, wherePatternsForStandoff)
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

            typeInspector <- FastFuture.successful(new ExplicitTypeInspectorV2(apiSchema))
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
                case resultRow: VariableResultsRow =>
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
                            case dependentResVar: QueryVariable =>
                                // Iris are concatenated, split them
                                resultRow.rowMap(dependentResVar.variableName).split(nonTriplestoreSpecificConstructToSelectTransformer.groupConcatSeparator).toSeq
                        }

                        acc + (mainResIri -> dependentResIris)
                }

                // the user may have defined Iris of dependent resources in the input query (type annotations)
                val dependentResourceIrisFromTypeInspection: Set[IRI] = typeInspectionResult.typedEntities.collect {
                    case (iri: TypeableIri, nonPropTypeInfo: NonPropertyTypeInfo) => iri.iri
                }.toSet

                // the Iris of all dependent resources for all main resources
                val allDependentResourceIris: Set[IRI] = dependentResourceIrisPerMainResource.values.flatten.toSet ++ dependentResourceIrisFromTypeInspection

                // value objects variables present in the preequery's WHERE clause
                val valueObjectVariablesConcat = nonTriplestoreSpecificConstructToSelectTransformer.getValueObjectVarsGroupConcat

                // for each main resource, create a Map of value object variables and their values
                val valueObjectIrisPerMainResource: Map[IRI, Map[QueryVariable, Set[IRI]]] = prequeryResponse.results.bindings.foldLeft(Map.empty[IRI, Map[QueryVariable, Set[IRI]]]) {
                    (acc: Map[IRI, Map[QueryVariable, Set[IRI]]], resultRow: VariableResultsRow) =>

                        val mainResIri: String = resultRow.rowMap(mainResourceVar.variableName)

                        val valueObjVarToIris: Map[QueryVariable, Set[IRI]] = valueObjectVariablesConcat.map {
                            (valueObjVarConcat: QueryVariable) =>
                                valueObjVarConcat -> resultRow.rowMap(valueObjVarConcat.variableName).split(nonTriplestoreSpecificConstructToSelectTransformer.groupConcatSeparator).toSet
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
                val mainQuery = createMainQuery(
                    mainResourceIris = mainResourceIris.map(iri => IriRef(iri)).toSet,
                    dependentResourceIris = allDependentResourceIris.map(iri => IriRef(iri)),
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

                //println("++++++++")
                //println(triplestoreSpecificQuery.toSparql)

                for {
                    searchResponse: SparqlConstructResponse <- (storeManager ? SparqlConstructRequest(triplestoreSpecificSparql)).mapTo[SparqlConstructResponse]

                    // separate main resources and value objects (dependent resources are nested)
                    queryResultsSep: Map[IRI, ConstructResponseUtilV2.ResourceWithValueRdfData] = ConstructResponseUtilV2.splitMainResourcesAndValueRdfData(constructQueryResults = searchResponse, userProfile = userProfile)

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

                    // get all the requested value object vars (for main and dependent resources)
                    valueObjectVariablesForAllResources: Set[QueryVariable] = collectValueVariablesForResource(preprocessedQuery.constructClause, mainResourceVar, typeInspectionResult, nonTriplestoreSpecificConstructToSelectTransformer.groupConcatVariableAppendix)

                    // collect requested valu object Iris for each resource
                    requestedValObjIrisPerResource: Map[IRI, Set[IRI]] = queryResWithFullQueryPath.map {
                        case (resIri: IRI, assertions: ConstructResponseUtilV2.ResourceWithValueRdfData) =>

                            val valueObjIrisForRes: Map[QueryVariable, Set[IRI]] = valueObjectIrisPerMainResource(resIri)

                            val valObjIrisRequestedForRes: Set[IRI] = valueObjectVariablesForAllResources.flatMap {
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
                                                if (valObj.targetResource.nonEmpty) {

                                                    val targetResourceAssertions: ConstructResponseUtilV2.ResourceWithValueRdfData = valObj.targetResource.get

                                                    // apply filter to the target resource's values
                                                    val targetResourceAssertionsFiltered: Map[IRI, Seq[ConstructResponseUtilV2.ValueRdfData]] = traverseAndFilterValues(targetResourceAssertions)

                                                    valObj.copy(
                                                        targetResource = Some(targetResourceAssertions.copy(
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

                getForbiddenResource(userProfile)
            } else {
                // all resources visible, no need for the forbidden resource
                Future(None)
            }

            // get the mappings
            mappingsAsMap <- getMappingsFromQueryResultsSeparated(queryResultsSeparatedWithFullQueryPath, userProfile)


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
      * @param userProfile          the profile of the client making the request.
      * @return a [[ReadResourcesSequenceV2]] representing the resources that have been found.
      */
    private def searchResourcesByLabelCountV2(searchValue: String, limitToProject: Option[IRI], limitToResourceClass: Option[IRI], userProfile: UserProfileV1) = {

        val searchPhrase: MatchStringWhileTyping = MatchStringWhileTyping(searchValue)

        for {
            countSparql <- Future(queries.sparql.v2.txt.searchResourceByLabel(
                triplestore = settings.triplestoreType,
                searchTerm = searchPhrase,
                limitToProject = limitToProject,
                limitToResourceClass = limitToResourceClass,
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
      * @param offset the offset to be used for paging.
      * @param limitToProject       limit search to given project.
      * @param limitToResourceClass limit search to given resource class.
      * @param userProfile          the profile of the client making the request.
      * @return a [[ReadResourcesSequenceV2]] representing the resources that have been found.
      */
    private def searchResourcesByLabelV2(searchValue: String, offset: Int, limitToProject: Option[IRI], limitToResourceClass: Option[IRI], userProfile: UserProfileV1): Future[ReadResourcesSequenceV2] = {

        val searchPhrase: MatchStringWhileTyping = MatchStringWhileTyping(searchValue)

        for {
            searchResourceByLabelSparql <- Future(queries.sparql.v2.txt.searchResourceByLabel(
                triplestore = settings.triplestoreType,
                searchTerm = searchPhrase,
                limitToProject = limitToProject,
                limitToResourceClass = limitToResourceClass,
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
            queryResultsSeparated = ConstructResponseUtilV2.splitMainResourcesAndValueRdfData(constructQueryResults = searchResourceByLabelResponse, userProfile = userProfile)

            // check if there are resources the user does not have sufficient permissions to see
            forbiddenResourceOption: Option[ReadResourceV2] <- if (mainResourceIris.size > queryResultsSeparated.size) {
                // some of the main resources have been suppressed, represent them using the forbidden resource
                getForbiddenResource(userProfile)
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