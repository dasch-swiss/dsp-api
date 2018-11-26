package org.knora.webapi.util.search.sparql.gravsearch

import org.knora.webapi._
import org.knora.webapi.messages.v2.responder.valuemessages.DateValueContentV2
import org.knora.webapi.util.{SmartIri, StringFormatter}
import org.knora.webapi.util.search.ApacheLuceneSupport.CombineSearchTerms
import org.knora.webapi.util.search._
import org.knora.webapi.util.search.sparql.gravsearch.GravsearchUtilV2.Gravsearch.GravsearchConstants
import org.knora.webapi.util.search.sparql.gravsearch.GravsearchUtilV2.SparqlTransformation._
import org.knora.webapi.util.IriConversions._

import scala.collection.mutable

/**
  * An abstract base class providing shared methods for [[WhereTransformer]] instances.
  */
abstract class AbstractSparqlTransformer(typeInspectionResult: GravsearchTypeInspectionResult, querySchema: ApiV2Schema) extends WhereTransformer {

    protected implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    // Contains the variable representing the input query's main resource: knora-base:isMainResource
    protected var mainResourceVariable: Option[QueryVariable] = None

    // getter method for public access
    def getMainResourceVariable: QueryVariable = mainResourceVariable.getOrElse(throw GravsearchException("Could not get main resource variable from transformer"))

    // a Set containing all `TypeableEntity` (keys of `typeInspectionResult`) that have already been processed
    // in order to prevent duplicates
    protected val processedTypeInformationKeysWhereClause = mutable.Set.empty[TypeableEntity]

    // variables representing dependent resources
    protected var dependentResourceVariables: mutable.Set[QueryVariable] = mutable.Set.empty

    // separator used by GroupConcat
    val groupConcatSeparator: Char = StringFormatter.INFORMATION_SEPARATOR_ONE

    // variables representing aggregated dependent resources
    protected var dependentResourceVariablesGroupConcat: Set[QueryVariable] = Set.empty

    // getter method for public access
    def getDependentResourceVariablesGroupConcat: Set[QueryVariable] = dependentResourceVariablesGroupConcat

    // variables representing value objects (including those for link values)
    protected var valueObjectVariables: mutable.Set[QueryVariable] = mutable.Set.empty

    // variables representing aggregated value objects
    protected var valueObjectVarsGroupConcat: Set[QueryVariable] = Set.empty

    // getter method for public access
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

                        case None => () // TODO: What happens if the main resource variable has not been processed yet (Option would be None)? Shall rather an error be thrown here?

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

        // Convert it to Julian Day Numbers.
        val dateValueContent = DateValueContentV2.parse(dateStr)

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
                val leftArgFilter = CompareExpression(XsdLiteral(dateValueContent.valueHasStartJDN.toString, OntologyConstants.Xsd.Integer.toSmartIri), CompareExpressionOperator.LESS_THAN_OR_EQUAL_TO, dateValueHasEndVar)

                val rightArgFilter = CompareExpression(XsdLiteral(dateValueContent.valueHasEndJDN.toString, OntologyConstants.Xsd.Integer.toSmartIri), CompareExpressionOperator.GREATER_THAN_OR_EQUAL_TO, dateValueHasStartVar)

                val filter = AndExpression(leftArgFilter, rightArgFilter)

                val statementsToAdd = Seq(dateValStartStatement, dateValEndStatement).filterNot(statement => generatedDateStatements.contains(statement))
                generatedDateStatements ++= statementsToAdd

                TransformedFilterPattern(
                    Some(filter),
                    statementsToAdd
                )

            case CompareExpressionOperator.NOT_EQUALS =>

                // no overlap in considered as inequality (negation of equality)
                val leftArgFilter = CompareExpression(XsdLiteral(dateValueContent.valueHasStartJDN.toString, OntologyConstants.Xsd.Integer.toSmartIri), CompareExpressionOperator.GREATER_THAN, dateValueHasEndVar)

                val rightArgFilter = CompareExpression(XsdLiteral(dateValueContent.valueHasEndJDN.toString, OntologyConstants.Xsd.Integer.toSmartIri), CompareExpressionOperator.LESS_THAN, dateValueHasStartVar)

                val filter = OrExpression(leftArgFilter, rightArgFilter)

                val statementsToAdd = Seq(dateValStartStatement, dateValEndStatement).filterNot(statement => generatedDateStatements.contains(statement))
                generatedDateStatements ++= statementsToAdd

                TransformedFilterPattern(
                    Some(filter),
                    statementsToAdd
                )

            case CompareExpressionOperator.LESS_THAN =>

                // period ends before indicated period
                val filter = CompareExpression(dateValueHasEndVar, CompareExpressionOperator.LESS_THAN, XsdLiteral(dateValueContent.valueHasStartJDN.toString, OntologyConstants.Xsd.Integer.toSmartIri))

                val statementsToAdd = Seq(dateValStartStatement, dateValEndStatement).filterNot(statement => generatedDateStatements.contains(statement)) // dateValStartStatement may be used as ORDER BY statement
                generatedDateStatements ++= statementsToAdd

                TransformedFilterPattern(
                    Some(filter),
                    statementsToAdd
                )

            case CompareExpressionOperator.LESS_THAN_OR_EQUAL_TO =>

                // period ends before indicated period or equals it (any overlap)
                val filter = CompareExpression(dateValueHasStartVar, CompareExpressionOperator.LESS_THAN_OR_EQUAL_TO, XsdLiteral(dateValueContent.valueHasEndJDN.toString, OntologyConstants.Xsd.Integer.toSmartIri))

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
                val filter = CompareExpression(dateValueHasStartVar, CompareExpressionOperator.GREATER_THAN, XsdLiteral(dateValueContent.valueHasEndJDN.toString, OntologyConstants.Xsd.Integer.toSmartIri))

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
                val filter = CompareExpression(dateValueHasEndVar, CompareExpressionOperator.GREATER_THAN_OR_EQUAL_TO, XsdLiteral(dateValueContent.valueHasStartJDN.toString, OntologyConstants.Xsd.Integer.toSmartIri))

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
            throw GravsearchException(s"Function ${functionIri.toSparql} cannot be used in a Gravsearch query written in the simple schema; use ${OntologyConstants.KnoraApiV2Simple.MatchFunction.toSmartIri.toSparql} instead")
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
        // 3. a variable or IRI representing the resource that is the target of the link

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

        val statementsForTargetResource: Seq[QueryPattern] = typeInspectionResult.getTypeOfEntity(linkTargetEntity) match {
            case Some(nonPropertyTpeInfo: NonPropertyTypeInfo) if OntologyConstants.KnoraApi.isKnoraApiV2Resource(nonPropertyTpeInfo.typeIri) =>

                // process the entity representing the target of the link
                createAdditionalStatementsForNonPropertyType(nonPropertyTpeInfo, linkTargetEntity)

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
            linkStatements ++ statementsForLinkValue ++ statementsForTargetResource
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

            case OntologyConstants.KnoraApiV2Simple.MatchFunction =>
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

            case OntologyConstants.KnoraApiV2WithValueObjects.ToSimpleDateFunction =>
                throw GravsearchException(s"Function ${functionIri.toSparql} must be used in a comparison expression")

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

        // One argument is expected: a QueryVariable representing something that belongs to a subclass of knora-api:DateBase.
        val dateBaseVar: QueryVariable = functionCallExpr.getArgAsQueryVar(pos = 0)

        typeInspectionResult.getTypeOfEntity(dateBaseVar) match {
            case Some(nonPropInfo: NonPropertyTypeInfo) =>
                if (!GravsearchConstants.dateTypes.contains(nonPropInfo.typeIri.toString)) {
                    throw GravsearchException(s"${dateBaseVar.toSparql} must represent a knora-api:DateValue or a knora-api:StandoffDateTag")
                }

            case _ => throw GravsearchException(s"${dateBaseVar.toSparql} must represent a knora-api:DateValue or a knora-api:StandoffDateTag")
        }

        handleDateQueryVar(queryVar = dateBaseVar, comparisonOperator = filterCompare.operator, dateValueExpression = filterCompare.rightArg)
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
