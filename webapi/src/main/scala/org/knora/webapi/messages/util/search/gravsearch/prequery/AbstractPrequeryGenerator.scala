/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.util.search.gravsearch.prequery

import zio.Task
import zio.ZIO

import scala.collection.mutable

import dsp.errors._
import org.knora.webapi._
import org.knora.webapi.messages.IriConversions._
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.ValuesValidator
import org.knora.webapi.messages.util.search._
import org.knora.webapi.messages.util.search.gravsearch.GravsearchQueryChecker
import org.knora.webapi.messages.util.search.gravsearch.transformers.SparqlTransformer
import org.knora.webapi.messages.util.search.gravsearch.types._
import org.knora.webapi.messages.v2.responder.valuemessages.DateValueContentV2
import org.knora.webapi.util.ApacheLuceneSupport.LuceneQueryString

object AbstractPrequeryGenerator {
  // separator used by GroupConcat
  val groupConcatSeparator: Char = StringFormatter.INFORMATION_SEPARATOR_ONE
}

/**
 * An abstract base class for [[WhereTransformer]] instances that generate SPARQL prequeries from Gravsearch input.
 *
 * @param typeInspectionResult the result of running type inspection on the Gravsearch input.
 * @param querySchema          the ontology schema used in the input Gravsearch query.
 */
abstract class AbstractPrequeryGenerator(
  constructClause: ConstructClause,
  typeInspectionResult: GravsearchTypeInspectionResult,
  querySchema: ApiV2Schema
) extends WhereTransformer {

  /**
   * Returns the columns to be specified in the SELECT query.
   */
  def getSelectColumns: Task[Seq[SelectQueryColumn]]

  /**
   * Returns the variables that the query result rows are grouped by (aggregating rows into one).
   * Variables returned by the SELECT query must either be present in the GROUP BY statement
   * or be transformed by an aggregation function in SPARQL.
   * This method will be called by [[QueryTraverser]] after the whole input query has been traversed.
   *
   * @param orderByCriteria the criteria used to sort the query results. They have to be included in the GROUP BY statement, otherwise they are unbound.
   * @return a list of variables that the result rows are grouped by.
   */
  def getGroupBy(orderByCriteria: TransformedOrderBy): Task[Seq[QueryVariable]]

  /**
   * Returns the criteria, if any, that should be used in the ORDER BY clause of the SELECT query. This method will be called
   * by [[QueryTraverser]] after the whole input query has been traversed.
   *
   * @param inputOrderBy the ORDER BY criteria in the input query.
   * @return the ORDER BY criteria, if any.
   */
  def getOrderBy(inputOrderBy: Seq[OrderCriterion]): Task[TransformedOrderBy]

  /**
   * Returns the limit representing the maximum amount of result rows returned by the SELECT query.
   *
   * @return the LIMIT, if any.
   */
  def getLimit: Task[Int]

  /**
   * Returns the OFFSET to be used in the SELECT query.
   * Provided the OFFSET submitted in the input query, calculates the actual offset in result rows depending on LIMIT.
   *
   * @param inputQueryOffset the OFFSET provided in the input query.
   * @return the OFFSET.
   */
  def getOffset(inputQueryOffset: Long, limit: Int): Task[Long]

  protected implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

  // a Set containing all `TypeableEntity` (keys of `typeInspectionResult`) that have already been processed
  // in order to prevent duplicates
  private val processedTypeInformationKeysWhereClause = mutable.Set.empty[TypeableEntity]

  // suffix appended to variables that are returned by a SPARQL aggregation function.
  protected val groupConcatVariableSuffix = "__Concat"

  /**
   * A container for a generated variable representing a value literal.
   *
   * @param variable     the generated variable.
   * @param useInOrderBy if `true`, the generated variable can be used in ORDER BY.
   */
  private case class GeneratedQueryVariable(variable: QueryVariable, useInOrderBy: Boolean)

  // Variables that are created when processing filter statements or for a value object var used as a sort criterion.
  // They represent the value of a literal pointed to by a value object. There is a stack of collections of these
  // variables, with an element for the top level of the WHERE clause, and an element for each level of UNION blocks,
  // because we can't assume that variables at the top level will be bound in a UNION block.
  private var valueVariablesAutomaticallyGenerated: List[Map[QueryVariable, Set[GeneratedQueryVariable]]] =
    List(Map.empty[QueryVariable, Set[GeneratedQueryVariable]])

  // Variables mentioned in the UNION block that is currently being processed, so we can ensure that a variable
  // is bound before it is used in a FILTER. This is a stack of sets, with one element per level of union blocks.
  private var variablesInUnionBlocks: List[Set[QueryVariable]] = List.empty

  // variables the represent resource metadata
  private val resourceMetadataVariables = mutable.Set.empty[QueryVariable]

  // The query can set this to false to disable inference.
  var useInference = true

  /**
   * Transforms a [[org.knora.webapi.messages.util.search.StatementPattern]] in a WHERE clause into zero or more query patterns.
   *
   * @param statementPattern           the statement to be transformed.
   * @param inputOrderBy               the ORDER BY clause in the input query.
   * @param limitInferenceToOntologies a set of ontology IRIs, to which the simulated inference will be limited. If `None`, all possible inference will be done.
   * @return the result of the transformation.
   */
  override def transformStatementInWhere(
    statementPattern: StatementPattern,
    inputOrderBy: Seq[OrderCriterion],
    limitInferenceToOntologies: Option[Set[SmartIri]] = None
  ): Task[Seq[QueryPattern]] =
    // Include any statements needed to meet the user's search criteria, but not statements that would be needed for permission checking or
    // other information about the matching resources or values.
    processStatementPatternFromWhereClause(
      statementPattern = statementPattern,
      inputOrderBy = inputOrderBy,
      limitInferenceToOntologies = limitInferenceToOntologies
    )

  /**
   * Runs optimisations that take a Gravsearch query as input. An optimisation needs to be run here if
   * it uses the type inspection result that refers to the Gravsearch query.
   *
   * @param patterns the query patterns to be optimised.
   * @return the optimised query patterns.
   */
  override def optimiseQueryPatterns(patterns: Seq[QueryPattern]): Task[Seq[QueryPattern]] =
    ZIO.attempt(
      GravsearchQueryOptimisationFactory
        .getGravsearchQueryOptimisationFeature(
          typeInspectionResult = typeInspectionResult,
          querySchema = querySchema
        )
        .optimiseQueryPatterns(patterns)
    )

  override def transformLuceneQueryPattern(luceneQueryPattern: LuceneQueryPattern): Task[Seq[QueryPattern]] =
    ZIO.succeed(Seq(luceneQueryPattern))

  /**
   * Transforms a [[org.knora.webapi.messages.util.search.FilterPattern]] in a WHERE clause into zero or more statement patterns.
   *
   * @param filterPattern the filter to be transformed.
   * @return the result of the transformation.
   */
  override def transformFilter(filterPattern: FilterPattern): Task[Seq[QueryPattern]] = ZIO.attempt {
    val filterExpression: TransformedFilterPattern =
      transformFilterPattern(filterPattern.expression, typeInspectionResult = typeInspectionResult, isTopLevel = true)

    filterExpression.expression match {
      case Some(expression: Expression) => filterExpression.additionalPatterns :+ FilterPattern(expression)

      case None => filterExpression.additionalPatterns // no FILTER expression given
    }

  }

  /**
   * When we enter a UNION block, pushes an empty collection of generated variables on to the stack
   * valueVariablesAutomaticallyGenerated.
   */
  override def enteringUnionBlock(): Task[Unit] = ZIO.attempt {
    valueVariablesAutomaticallyGenerated = Map
      .empty[QueryVariable, Set[GeneratedQueryVariable]] :: valueVariablesAutomaticallyGenerated

    variablesInUnionBlocks = Set.empty[QueryVariable] :: variablesInUnionBlocks
  }

  /**
   * When we leave a UNION block, pops that block's collection of generated variables off the
   * stack valueVariablesAutomaticallyGenerated.
   */
  override def leavingUnionBlock(): Task[Unit] = ZIO.attempt {
    valueVariablesAutomaticallyGenerated = valueVariablesAutomaticallyGenerated.tail

    variablesInUnionBlocks = variablesInUnionBlocks.tail
  }

  private def inUnionBlock: Boolean =
    variablesInUnionBlocks.nonEmpty

  /**
   * Saves a generated variable representing a value literal, if it hasn't been saved already.
   *
   * @param valueVar     the variable representing the value.
   * @param generatedVar the generated variable representing the value literal.
   * @param useInOrderBy if `true`, the generated variable can be used in ORDER BY.
   * @return `true` if the generated variable was saved, `false` if it had already been saved.
   */
  private def addGeneratedVariableForValueLiteral(
    valueVar: QueryVariable,
    generatedVar: QueryVariable,
    useInOrderBy: Boolean = true
  ): Boolean = {
    val currentGeneratedVarsForBlock: Map[QueryVariable, Set[GeneratedQueryVariable]] =
      valueVariablesAutomaticallyGenerated.head

    val currentGeneratedVarsForValueVar: Set[GeneratedQueryVariable] =
      currentGeneratedVarsForBlock.getOrElse(valueVar, Set.empty[GeneratedQueryVariable])

    val newGeneratedVarsForBlock =
      if (
        !currentGeneratedVarsForValueVar.exists(currentGeneratedVar => currentGeneratedVar.variable == generatedVar)
      ) {
        currentGeneratedVarsForBlock + (valueVar -> (currentGeneratedVarsForValueVar + GeneratedQueryVariable(
          generatedVar,
          useInOrderBy
        )))
      } else {
        currentGeneratedVarsForBlock
      }

    valueVariablesAutomaticallyGenerated = newGeneratedVarsForBlock :: valueVariablesAutomaticallyGenerated.tail

    newGeneratedVarsForBlock != currentGeneratedVarsForBlock
  }

  /**
   * Gets a saved generated variable representing a value literal, for use in ORDER BY.
   *
   * @param valueVar the variable representing the value.
   * @return a generated variable that represents a value literal and can be used in ORDER BY, or `None` if no such variable has been saved.
   */
  protected def getGeneratedVariableForValueLiteralInOrderBy(valueVar: QueryVariable): Option[QueryVariable] =
    valueVariablesAutomaticallyGenerated.head.get(valueVar) match {
      case Some(generatedVars: Set[GeneratedQueryVariable]) =>
        val generatedVarsForOrderBy: Set[QueryVariable] = generatedVars.filter(_.useInOrderBy).map(_.variable)

        if (generatedVarsForOrderBy.size > 1) {
          throw AssertionException(
            s"More than one variable was generated for the literal values of ${valueVar.toSparql} and marked for use in ORDER BY: ${generatedVarsForOrderBy.map(_.toSparql).mkString(", ")}"
          )
        }

        generatedVarsForOrderBy.headOption

      case None => None
    }

  // Generated statements for date literals, so we don't generate the same statements twice.
  private val generatedDateStatements = mutable.Set.empty[StatementPattern]

  // Variables generated to represent marked-up text in standoff, so we don't generate the same variables twice.
  private val standoffMarkedUpVariables = mutable.Set.empty[QueryVariable]

  /**
   * The variable in the CONSTRUCT clause that represents the main resource.
   */
  val mainResourceVariable: QueryVariable = {
    val mainResourceQueryVariables = constructClause.statements.foldLeft(Set.empty[QueryVariable]) {
      case (acc: Set[QueryVariable], statementPattern) =>
        statementPattern.pred match {
          case IriRef(iri, _) =>
            val iriStr = iri.toString

            if (
              iriStr == OntologyConstants.KnoraApiV2Simple.IsMainResource || iriStr == OntologyConstants.KnoraApiV2Complex.IsMainResource
            ) {
              statementPattern.obj match {
                case XsdLiteral(value, SmartIri(OntologyConstants.Xsd.Boolean)) if value.toBoolean =>
                  statementPattern.subj match {
                    case queryVariable: QueryVariable => acc + queryVariable
                    case _                            => throw GravsearchException(s"The subject of knora-api:isMainResource must be a variable")
                  }

                case _ => acc
              }
            } else {
              acc
            }

          case _ => acc
        }
    }

    if (mainResourceQueryVariables.isEmpty) {
      throw GravsearchException("CONSTRUCT clause contains no knora-api:isMainResource")
    }

    if (mainResourceQueryVariables.size > 1) {
      throw GravsearchException("CONSTRUCT clause contains more than one knora-api:isMainResource")
    }

    mainResourceQueryVariables.head
  }

  /**
   * Creates additional statements for a non property type (e.g., a resource).
   *
   * @param nonPropertyTypeInfo type information about non property type.
   * @param inputEntity         the [[Entity]] to make the statements about.
   * @return a sequence of [[QueryPattern]] representing the additional statements.
   */
  private def createAdditionalStatementsForNonPropertyType(
    nonPropertyTypeInfo: NonPropertyTypeInfo,
    inputEntity: Entity
  ): Seq[QueryPattern] =
    if (nonPropertyTypeInfo.isResourceType) {

      // inputEntity is either source or target of a linking property
      // create additional statements in order to query permissions and other information for a resource

      Seq(
        StatementPattern.makeExplicit(
          subj = inputEntity,
          pred = IriRef(OntologyConstants.KnoraBase.IsDeleted.toSmartIri),
          obj = XsdLiteral(value = "false", datatype = OntologyConstants.Xsd.Boolean.toSmartIri)
        )
      )
    } else {
      // inputEntity is target of a value property
      // properties are handled by `convertStatementForPropertyType`, no processing needed here

      Seq.empty[QueryPattern]
    }

  /**
   * Generates statements matching a `knora-base:LinkValue`.
   *
   * @param linkSource the resource that is the source of the link.
   * @param linkPred   the link predicate.
   * @param linkTarget the resource that is the target of the link.
   * @return statements matching the `knora-base:LinkValue` that describes the link.
   */
  private def generateStatementsForLinkValue(
    linkSource: Entity,
    linkPred: Entity,
    linkTarget: Entity
  ): Seq[StatementPattern] = {
    // Generate a variable name representing the link value
    val linkValueObjVar: QueryVariable = SparqlTransformer.createUniqueVariableFromStatementForLinkValue(
      baseStatement = StatementPattern(
        subj = linkSource,
        pred = linkPred,
        obj = linkTarget
      )
    )

    // create an Entity that connects the subject of the linking property with the link value object
    val linkValueProp: Entity = linkPred match {
      case linkingPropQueryVar: QueryVariable =>
        // Generate a variable name representing the link value property
        // in case FILTER patterns are given restricting the linking property's possible IRIs, the same variable will recreated when processing FILTER patterns
        createLinkValuePropertyVariableFromLinkingPropertyVariable(linkingPropQueryVar)

      case propIri: IriRef =>
        // convert the given linking property IRI to the corresponding link value property IRI
        // only matches the linking property's link value
        IriRef(propIri.iri.toOntologySchema(InternalSchema).fromLinkPropToLinkValueProp)

      case literal: XsdLiteral =>
        throw GravsearchException(s"literal ${literal.toSparql} cannot be used as a predicate")

      case other => throw GravsearchException(s"${other.toSparql} cannot be used as a predicate")
    }

    // Add statements that represent the link value's properties for the given linking property.
    // Do not check for the predicate, because inference would not work.
    // Instead, linkValueProp restricts the link value objects to be returned.
    // No need to check rdf:subject, because it has to be linkSource. But we have to check
    // rdf:object, because there could be different link values representing links from the
    // same source with the same property but with different targets.
    Seq(
      StatementPattern.makeInferred(subj = linkSource, pred = linkValueProp, obj = linkValueObjVar),
      StatementPattern.makeExplicit(
        subj = linkValueObjVar,
        pred = IriRef(OntologyConstants.Rdf.Type.toSmartIri),
        obj = IriRef(OntologyConstants.KnoraBase.LinkValue.toSmartIri)
      ),
      StatementPattern.makeExplicit(
        subj = linkValueObjVar,
        pred = IriRef(OntologyConstants.KnoraBase.IsDeleted.toSmartIri),
        obj = XsdLiteral(value = "false", datatype = OntologyConstants.Xsd.Boolean.toSmartIri)
      ),
      StatementPattern.makeExplicit(
        subj = linkValueObjVar,
        pred = IriRef(OntologyConstants.Rdf.Object.toSmartIri),
        obj = linkTarget
      )
    )
  }

  private def convertStatementForPropertyType(inputOrderBy: Seq[OrderCriterion])(
    propertyTypeInfo: PropertyTypeInfo,
    statementPattern: StatementPattern,
    typeInspectionResult: GravsearchTypeInspectionResult
  ): Seq[QueryPattern] = {

    /**
     * Ensures that if the object of a statement is a variable, and is used in the ORDER BY clause of the input query, the subject of the statement
     * is the main resource. Throws an exception otherwise.
     *
     * @param objectVar the variable that is the object of the statement.
     */
    def checkSubjectInOrderBy(objectVar: QueryVariable): Unit =
      statementPattern.subj match {
        case subjectVar: QueryVariable =>
          if (
            mainResourceVariable != subjectVar && inputOrderBy.exists(criterion => criterion.queryVariable == objectVar)
          ) {
            throw GravsearchException(
              s"Variable ${objectVar.toSparql} is used in ORDER BY, but does not represent a value of the main resource"
            )
          }

        case _ => ()
      }

    /**
     * Transforms a statement pointing to a list node so it matches also any of its subnodes.
     *
     * @return transformed statements.
     */
    def handleListNode(): Seq[StatementPattern] = {

      if (querySchema == ApiV2Simple) {
        throw GravsearchException("the method 'handleListNode' only works for the complex schema")
      }

      // the list node to match for provided in the input query
      val listNode: Entity = statementPattern.obj

      // variable representing the list node to match for
      val listNodeVar: QueryVariable = SparqlTransformer.createUniqueVariableFromStatement(
        baseStatement = statementPattern,
        suffix = "listNodeVar"
      )

      // transforms the statement given in the input query so the list node and any of its subnodes are matched
      Seq(
        statementPatternToInternalSchema(statementPattern, typeInspectionResult).copy(obj = listNodeVar),
        StatementPattern.makeExplicit(
          subj = listNode,
          pred = IriRef(iri = OntologyConstants.KnoraBase.HasSubListNode.toSmartIri, propertyPathOperator = Some('*')),
          obj = listNodeVar
        )
      )

    }

    val maybeSubjectType: Option[NonPropertyTypeInfo] =
      typeInspectionResult.getTypeOfEntity(statementPattern.subj) match {
        case Some(nonPropertyTypeInfo: NonPropertyTypeInfo) => Some(nonPropertyTypeInfo)
        case _                                              => None
      }

    // Is the subject of the statement a resource?
    if (maybeSubjectType.exists(_.isResourceType)) {
      // Yes. Is the object of the statement also a resource?
      if (propertyTypeInfo.objectIsResourceType) {
        // Yes. This is a link property. Make sure that the object is either an IRI or a variable (cannot be a literal).
        statementPattern.obj match {
          case _: IriRef                => ()
          case objectVar: QueryVariable => checkSubjectInOrderBy(objectVar)
          case other =>
            throw GravsearchException(
              s"Object of a linking statement must be an IRI or a variable, but ${other.toSparql} given."
            )
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
        // The subject is a resource, but the object isn't, so this isn't a link property.
        // Is the property a resource metadata property?
        statementPattern.pred match {
          case iriRef: IriRef if OntologyConstants.ResourceMetadataPropertyAxioms.contains(iriRef.iri.toString) =>
            // Yes. Store the variable if provided.
            val maybeObjectVar: Option[QueryVariable] = statementPattern.obj match {
              case queryVar: QueryVariable =>
                checkSubjectInOrderBy(queryVar)
                Some(queryVar)

              case _ => None
            }

            resourceMetadataVariables ++= maybeObjectVar

            // Just convert the statement pattern to the internal schema
            Seq(statementPatternToInternalSchema(statementPattern, typeInspectionResult))

          case _ =>
            //  The property is not a resource metadata property. Make sure the object is a variable.
            val objectVar: QueryVariable = statementPattern.obj match {
              case queryVar: QueryVariable =>
                checkSubjectInOrderBy(queryVar)
                queryVar

              case other =>
                throw GravsearchException(
                  s"Object of a value property statement must be a QueryVariable, but ${other.toSparql} given."
                )
            }

            // Does the variable refer to a Knora value object? We assume it does if the query just uses the
            // simple schema. If the query uses the complex schema, check whether the property's object type is a
            // Knora API v2 value class.

            val objectVarIsValueObject = querySchema == ApiV2Simple ||
              OntologyConstants.KnoraApiV2Complex.ValueClasses.contains(propertyTypeInfo.objectTypeIri.toString)

            if (objectVarIsValueObject) {
              // The variable refers to a value object.

              // Convert the statement to the internal schema, and add a statement to check that the value object is not marked as deleted.
              val valueObjectIsNotDeleted = StatementPattern.makeExplicit(
                subj = statementPattern.obj,
                pred = IriRef(OntologyConstants.KnoraBase.IsDeleted.toSmartIri),
                obj = XsdLiteral(value = "false", datatype = OntologyConstants.Xsd.Boolean.toSmartIri)
              )

              // check if the object var is used as a sort criterion
              val objectVarAsSortCriterionMaybe = inputOrderBy.find(criterion => criterion.queryVariable == objectVar)

              val orderByStatement: Option[QueryPattern] = if (objectVarAsSortCriterionMaybe.nonEmpty) {
                // it is used as a sort criterion, create an additional statement to get the literal value

                val criterion = objectVarAsSortCriterionMaybe.get

                val propertyIri: SmartIri = typeInspectionResult.getTypeOfEntity(criterion.queryVariable) match {
                  case Some(nonPropertyTypeInfo: NonPropertyTypeInfo) =>
                    valueTypesToValuePredsForOrderBy
                      .getOrElse(
                        nonPropertyTypeInfo.typeIri.toString,
                        throw GravsearchException(s"${criterion.queryVariable.toSparql} cannot be used in ORDER BY")
                      )
                      .toSmartIri

                  case Some(_) =>
                    throw GravsearchException(
                      s"Variable ${criterion.queryVariable.toSparql} represents a property, and therefore cannot be used in ORDER BY"
                    )

                  case None =>
                    throw GravsearchException(s"No type information found for ${criterion.queryVariable.toSparql}")
                }

                // Generate the variable name.
                val variableForLiteral: QueryVariable = SparqlTransformer.createUniqueVariableNameFromEntityAndProperty(
                  criterion.queryVariable,
                  propertyIri.toString
                )

                // put the generated variable into a collection so it can be reused in `NonTriplestoreSpecificGravsearchToPrequeryGenerator.getOrderBy`
                // set to true when the variable already exists
                val variableForLiteralExists =
                  !addGeneratedVariableForValueLiteral(criterion.queryVariable, variableForLiteral)

                if (!variableForLiteralExists) {
                  // Generate a statement to get the literal value
                  val statementPatternForSortCriterion = StatementPattern.makeExplicit(
                    subj = criterion.queryVariable,
                    pred = IriRef(propertyIri),
                    obj = variableForLiteral
                  )
                  Some(statementPatternForSortCriterion)
                } else {
                  // statement has already been created
                  None
                }
              } else {
                // it is not a sort criterion
                None
              }

              Seq(
                statementPatternToInternalSchema(statementPattern, typeInspectionResult),
                valueObjectIsNotDeleted
              ) ++ orderByStatement
            } else {
              // The variable doesn't refer to a value object. Just convert the statement pattern to the internal schema.
              Seq(statementPatternToInternalSchema(statementPattern, typeInspectionResult))
            }
        }
      }
    } else {
      // The subject isn't a resource, so it must be a value object or standoff node. Is the query in the complex schema?
      if (querySchema == ApiV2Complex) {
        // Yes. If the subject is a standoff tag and the object is a resource, that's an error, because the client
        // has to use the knora-api:standoffLink function instead.
        if (maybeSubjectType.exists(_.isStandoffTagType) && propertyTypeInfo.objectIsResourceType) {
          throw GravsearchException(
            s"Invalid statement pattern (use the knora-api:standoffLink function instead): ${statementPattern.toSparql.trim}"
          )
        } else {
          // Is the object of the statement a list node?
          propertyTypeInfo.objectTypeIri match {
            case SmartIri(OntologyConstants.KnoraApiV2Complex.ListNode) =>
              // Yes, transform statement so it also matches any of the subnodes of the given node
              handleListNode()
            case _ =>
              // No, just convert the statement pattern to the internal schema.
              Seq(statementPatternToInternalSchema(statementPattern, typeInspectionResult))
          }

        }
      } else {
        // The query is in the simple schema, so the statement is invalid.
        throw GravsearchException(s"Invalid statement pattern: ${statementPattern.toSparql.trim}")
      }
    }
  }

  /**
   * Processes Gravsearch options.
   *
   * @param statementPattern the statement specifying the option to be set.
   */
  private def processGravsearchOption(statementPattern: StatementPattern): Unit =
    statementPattern.pred match {
      case iriRef: IriRef if OntologyConstants.KnoraApi.UseInferenceIris.contains(iriRef.iri.toString) =>
        useInference = statementPattern.obj match {
          case xsdLiteral: XsdLiteral if xsdLiteral.datatype.toString == OntologyConstants.Xsd.Boolean =>
            xsdLiteral.value.toBoolean

          case other => throw GravsearchException(s"Invalid object for knora-api:useInference: ${other.toSparql}")
        }

      case other => throw GravsearchException(s"Invalid predicate for knora-api:GravsearchOptions: ${other.toSparql}")
    }

  /**
   * If we're in a UNION block, records any variables that are used in the specified statement,
   * so we can make sure that they're defined before they're used in a FILTER pattern.
   *
   * @param statementPattern the statement pattern being processed.
   */
  private def recordVariablesInUnionBlock(statementPattern: StatementPattern): Unit = {
    def entityAsVariable(entity: Entity): Option[QueryVariable] =
      entity match {
        case queryVariable: QueryVariable => Some(queryVariable)
        case _                            => None
      }

    // Are we in a UNION block?
    variablesInUnionBlocks match {
      case variablesInCurrentBlock :: tail =>
        // Yes. Collect any variables in the statement.
        val newVariablesInCurrentBlock: Set[QueryVariable] =
          variablesInCurrentBlock ++ entityAsVariable(statementPattern.subj) ++
            entityAsVariable(statementPattern.pred) ++
            entityAsVariable(statementPattern.obj)

        // Record them.
        variablesInUnionBlocks = newVariablesInCurrentBlock :: tail

      case Nil =>
        // No. Nothing to do here.
        ()
    }
  }

  protected def processStatementPatternFromWhereClause(
    statementPattern: StatementPattern,
    inputOrderBy: Seq[OrderCriterion],
    limitInferenceToOntologies: Option[Set[SmartIri]] = None
  ): Task[Seq[QueryPattern]] = ZIO.attempt(
    // Does this statement set a Gravsearch option?
    statementPattern.subj match {
      case iriRef: IriRef if OntologyConstants.KnoraApi.GravsearchOptionsIris.contains(iriRef.iri.toString) =>
        // Yes. Process the option.
        processGravsearchOption(statementPattern)
        Seq.empty[QueryPattern]

      case _ =>
        // No. look at the statement's subject, predicate, and object and generate additional statements if needed based on the given type information.
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

        // If we're in a UNION block, record any variables that are used in the statement,
        // so we can make sure that they're defined before they're used in a FILTER pattern.
        recordVariablesInUnionBlock(statementPattern)

        additionalStatementsForSubj ++ additionalStatementsForWholeStatement ++ additionalStatementsForObj
    }
  )

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
  private def checkForNonPropertyTypeInfoForEntity(
    entity: Entity,
    typeInspectionResult: GravsearchTypeInspectionResult,
    processedTypeInfo: mutable.Set[TypeableEntity],
    conversionFuncForNonPropertyType: (NonPropertyTypeInfo, Entity) => Seq[QueryPattern]
  ): Seq[QueryPattern] = {
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
  private def checkForPropertyTypeInfoForStatement(
    statementPattern: StatementPattern,
    typeInspectionResult: GravsearchTypeInspectionResult,
    conversionFuncForPropertyType: (
      PropertyTypeInfo,
      StatementPattern,
      GravsearchTypeInspectionResult
    ) => Seq[QueryPattern]
  ): Seq[QueryPattern] =
    typeInspectionResult.getTypeOfEntity(statementPattern.pred) match {
      case Some(propInfo: PropertyTypeInfo) =>
        // process type information for the predicate into additional statements
        conversionFuncForPropertyType(propInfo, statementPattern, typeInspectionResult)

      case Some(other) =>
        throw AssertionException(s"PropertyTypeInfo expected for ${statementPattern.pred}, got $other")

      case None =>
        // no type information given and thus no further processing needed, just return the originally given statement (e.g., rdf:type), converted to the internal schema.
        Seq(statementPatternToInternalSchema(statementPattern, typeInspectionResult))
    }

  // A Map of knora-api value types (both complex and simple) to the corresponding knora-base value predicates
  // that point to literals. This is used only for generating additional statements for ORDER BY clauses, so it only needs to include
  // types that have a meaningful order.
  private val valueTypesToValuePredsForOrderBy: Map[IRI, IRI] = Map(
    OntologyConstants.Xsd.Integer                    -> OntologyConstants.KnoraBase.ValueHasInteger,
    OntologyConstants.Xsd.Decimal                    -> OntologyConstants.KnoraBase.ValueHasDecimal,
    OntologyConstants.Xsd.Boolean                    -> OntologyConstants.KnoraBase.ValueHasBoolean,
    OntologyConstants.Xsd.String                     -> OntologyConstants.KnoraBase.ValueHasString,
    OntologyConstants.KnoraApiV2Simple.Date          -> OntologyConstants.KnoraBase.ValueHasStartJDN,
    OntologyConstants.KnoraApiV2Simple.Color         -> OntologyConstants.KnoraBase.ValueHasColor,
    OntologyConstants.KnoraApiV2Simple.Geoname       -> OntologyConstants.KnoraBase.ValueHasGeonameCode,
    OntologyConstants.KnoraApiV2Complex.TextValue    -> OntologyConstants.KnoraBase.ValueHasString,
    OntologyConstants.KnoraApiV2Complex.IntValue     -> OntologyConstants.KnoraBase.ValueHasInteger,
    OntologyConstants.KnoraApiV2Complex.DecimalValue -> OntologyConstants.KnoraBase.ValueHasDecimal,
    OntologyConstants.KnoraApiV2Complex.TimeValue    -> OntologyConstants.KnoraBase.ValueHasTimeStamp,
    OntologyConstants.KnoraApiV2Complex.BooleanValue -> OntologyConstants.KnoraBase.ValueHasBoolean,
    OntologyConstants.KnoraApiV2Complex.DateValue    -> OntologyConstants.KnoraBase.ValueHasStartJDN,
    OntologyConstants.KnoraApiV2Complex.ColorValue   -> OntologyConstants.KnoraBase.ValueHasColor,
    OntologyConstants.KnoraApiV2Complex.GeonameValue -> OntologyConstants.KnoraBase.ValueHasGeonameCode
  )

  /**
   * Calls [[GravsearchQueryChecker.checkStatement]], then converts the specified statement pattern to the internal schema.
   *
   * @param statementPattern     the statement pattern to be converted.
   * @param typeInspectionResult the type inspection result.
   * @return the converted statement pattern.
   */
  private def statementPatternToInternalSchema(
    statementPattern: StatementPattern,
    typeInspectionResult: GravsearchTypeInspectionResult
  ): StatementPattern = {
    GravsearchQueryChecker.checkStatement(
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
  private def createLinkValuePropertyVariableFromLinkingPropertyVariable(
    linkingPropertyQueryVariable: QueryVariable
  ): QueryVariable =
    SparqlTransformer.createUniqueVariableNameFromEntityAndProperty(
      base = linkingPropertyQueryVariable,
      propertyIri = OntologyConstants.KnoraBase.HasLinkToValue
    )

  /**
   * Represents a transformed Filter expression and additional statement patterns that possibly had to be created during transformation.
   *
   * @param expression         the transformed FILTER expression. In some cases, a given FILTER expression is replaced by additional statements, but
   *                           only if it is the top-level expression in the FILTER.
   * @param additionalPatterns additionally created query patterns.
   */
  protected case class TransformedFilterPattern(
    expression: Option[Expression],
    additionalPatterns: Seq[QueryPattern] = Seq.empty[QueryPattern]
  )

  /**
   * Handles query variables that represent properties in a [[FilterPattern]].
   *
   * @param queryVar           the query variable to be handled.
   * @param comparisonOperator the comparison operator used in the filter pattern.
   * @param iriRef             the IRI the property query variable is restricted to.
   * @param propInfo           information about the query variable's type.
   * @return a [[TransformedFilterPattern]].
   */
  private def handlePropertyIriQueryVar(
    queryVar: QueryVariable,
    comparisonOperator: CompareExpressionOperator.Value,
    iriRef: IriRef,
    propInfo: PropertyTypeInfo
  ): TransformedFilterPattern = {
    if (!iriRef.iri.isOntologySchema(querySchema))
      throw GravsearchException(s"Invalid schema for IRI: ${iriRef.toSparql}")

    // make sure that the comparison operator is a CompareExpressionOperator.EQUALS
    if (comparisonOperator != CompareExpressionOperator.EQUALS)
      throw GravsearchException(
        s"Comparison operator in a CompareExpression for a property type must be ${CompareExpressionOperator.EQUALS}, but '$comparisonOperator' given (for negations use 'FILTER NOT EXISTS')"
      )

    TransformedFilterPattern(
      Some(CompareExpression(queryVar, comparisonOperator, iriRef.toOntologySchema(InternalSchema)))
    )
  }

  /**
   * Handles query variables that represent a list node label in a [[FilterPattern]].
   *
   * @param queryVar               the query variable to be handled.
   * @param comparisonOperator     the comparison operator used in the filter pattern.
   * @param literalValueExpression the label to match against.
   */
  private def handleListQueryVar(
    queryVar: QueryVariable,
    comparisonOperator: CompareExpressionOperator.Value,
    literalValueExpression: Expression
  ): TransformedFilterPattern = {

    // make sure that the expression is a literal of the expected type
    val nodeLabel: String = literalValueExpression match {
      case xsdLiteral: XsdLiteral if xsdLiteral.datatype.toString == OntologyConstants.KnoraApiV2Simple.ListNode =>
        xsdLiteral.value

      case _ =>
        throw GravsearchException(s"Invalid type for literal ${OntologyConstants.KnoraApiV2Simple.ListNode}")
    }

    val validComparisonOperators = Set(CompareExpressionOperator.EQUALS)

    // check if comparison operator is supported
    if (!validComparisonOperators.contains(comparisonOperator))
      throw GravsearchException(
        s"Invalid operator '$comparisonOperator' in expression (allowed operators in this context are ${validComparisonOperators
            .map(op => "'" + op + "'")
            .mkString(", ")})"
      )

    // Generate a variable name representing the list node pointed to by the list value object
    val listNodeVar: QueryVariable = SparqlTransformer.createUniqueVariableNameFromEntityAndProperty(
      base = queryVar,
      propertyIri = OntologyConstants.KnoraBase.ValueHasListNode
    )

    // Generate variable name representing the label of the list node pointed to
    val listNodeLabel: QueryVariable = SparqlTransformer.createUniqueVariableNameFromEntityAndProperty(
      base = queryVar,
      propertyIri = OntologyConstants.Rdfs.Label
    )

    TransformedFilterPattern(
      // use the SPARQL-STR function because the list node label has a language tag
      Some(
        CompareExpression(
          StrFunction(listNodeLabel),
          comparisonOperator,
          XsdLiteral(nodeLabel, OntologyConstants.Xsd.String.toSmartIri)
        )
      ), // compares the provided literal to the value object's literal value
      Seq(
        // connects the query variable with the list node label
        StatementPattern.makeExplicit(
          subj = queryVar,
          pred = IriRef(OntologyConstants.KnoraBase.ValueHasListNode.toSmartIri),
          listNodeVar
        ),
        StatementPattern.makeExplicit(
          subj = listNodeVar,
          pred = IriRef(OntologyConstants.Rdfs.Label.toSmartIri),
          obj = listNodeLabel
        )
      )
    )
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
  private def handleLiteralQueryVar(
    queryVar: QueryVariable,
    comparisonOperator: CompareExpressionOperator.Value,
    literalValueExpression: Expression,
    xsdType: Set[IRI],
    valueHasProperty: IRI,
    validComparisonOperators: Set[CompareExpressionOperator.Value] = Set.empty[CompareExpressionOperator.Value]
  ): TransformedFilterPattern = {

    // make sure that the expression is a literal of the expected type
    val literal: XsdLiteral = literalValueExpression match {
      case xsdLiteral: XsdLiteral if xsdType(xsdLiteral.datatype.toString) => xsdLiteral

      case other =>
        throw GravsearchException(
          s"Invalid right argument ${other.toSparql} in comparison (allowed types in this context are ${xsdType.map(_.toSmartIri.toSparql).mkString(", ")})"
        )
    }

    // check if comparison operator is supported for given type
    if (validComparisonOperators.nonEmpty && !validComparisonOperators(comparisonOperator))
      throw GravsearchException(
        s"Invalid operator '$comparisonOperator' in expression (allowed operators in this context are ${validComparisonOperators
            .map(op => "'" + op + "'")
            .mkString(", ")})"
      )

    // Does the variable refer to resource metadata?
    if (resourceMetadataVariables.contains(queryVar)) {
      // Yes. Leave the expression as is.
      TransformedFilterPattern(
        Some(CompareExpression(queryVar, comparisonOperator, literal)),
        Seq.empty
      )
    } else {
      // The variable does not refer to resource metadata.
      // Generate a variable name representing the literal attached to the value object.
      val valueObjectLiteralVar: QueryVariable = SparqlTransformer.createUniqueVariableNameFromEntityAndProperty(
        base = queryVar,
        propertyIri = valueHasProperty
      )

      // Add a statement to assign the literal to a variable, which we'll use in the transformed FILTER expression,
      // if that statement hasn't been added already.

      val statementToAddForValueHas: Seq[StatementPattern] =
        if (addGeneratedVariableForValueLiteral(queryVar, valueObjectLiteralVar)) {
          Seq(
            // connects the query variable with the value object (internal structure: values are represented as objects)
            StatementPattern.makeExplicit(
              subj = queryVar,
              pred = IriRef(valueHasProperty.toSmartIri),
              valueObjectLiteralVar
            )
          )
        } else {
          Seq.empty[StatementPattern]
        }

      TransformedFilterPattern(
        Some(
          CompareExpression(valueObjectLiteralVar, comparisonOperator, literal)
        ), // compares the provided literal to the value object's literal value
        statementToAddForValueHas
      )
    }
  }

  /**
   * Handles query variables that represent a date in a [[FilterPattern]].
   *
   * @param queryVar            the query variable to be handled.
   * @param comparisonOperator  the comparison operator used in the filter pattern.
   * @param dateValueExpression the date literal provided in the [[FilterPattern]] as an [[Expression]].
   * @return a [[TransformedFilterPattern]].
   */
  private def handleDateQueryVar(
    queryVar: QueryVariable,
    comparisonOperator: CompareExpressionOperator.Value,
    dateValueExpression: Expression
  ): TransformedFilterPattern = {

    // make sure that the right argument is a string literal (dates are represented as knora date strings in knora-api simple)
    val dateStringLiteral: XsdLiteral = dateValueExpression match {
      case dateStrLiteral: XsdLiteral if dateStrLiteral.datatype.toString == OntologyConstants.KnoraApiV2Simple.Date =>
        dateStrLiteral

      case other => throw GravsearchException(s"Invalid right argument ${other.toSparql} in date comparison")
    }

    // validate Knora date string
    val dateStr: String = ValuesValidator
      .validateDate(dateStringLiteral.value)
      .getOrElse(
        throw BadRequestException(s"${dateStringLiteral.value} is not a valid date string")
      )

    // Convert it to Julian Day Numbers.
    val dateValueContent = DateValueContentV2.parse(dateStr)

    // Generate a variable name representing the period's start
    val dateValueHasStartVar = SparqlTransformer.createUniqueVariableNameFromEntityAndProperty(
      base = queryVar,
      propertyIri = OntologyConstants.KnoraBase.ValueHasStartJDN
    )

    // sort dates by their period's start (in the prequery)
    // is set to `true` if the date value object var is a sort criterion and has been handled already
    val dateValVarExists: Boolean = !addGeneratedVariableForValueLiteral(queryVar, dateValueHasStartVar)

    // Generate a variable name representing the period's end
    val dateValueHasEndVar = SparqlTransformer.createUniqueVariableNameFromEntityAndProperty(
      base = queryVar,
      propertyIri = OntologyConstants.KnoraBase.ValueHasEndJDN
    )

    // connects the value object with the periods start variable
    // only generate a new statement if it has not already been created when handling the sort criteria
    val dateValStartStatementOption: Option[StatementPattern] = if (!dateValVarExists) {
      Some(
        StatementPattern.makeExplicit(
          subj = queryVar,
          pred = IriRef(OntologyConstants.KnoraBase.ValueHasStartJDN.toSmartIri),
          obj = dateValueHasStartVar
        )
      )
    } else {
      None
    }

    // connects the value object with the periods end variable
    val dateValEndStatement = StatementPattern.makeExplicit(
      subj = queryVar,
      pred = IriRef(OntologyConstants.KnoraBase.ValueHasEndJDN.toSmartIri),
      obj = dateValueHasEndVar
    )

    // process filter expression based on given comparison operator
    comparisonOperator match {

      case CompareExpressionOperator.EQUALS =>
        // any overlap in considered as equality
        val leftArgFilter = CompareExpression(
          XsdLiteral(dateValueContent.valueHasStartJDN.toString, OntologyConstants.Xsd.Integer.toSmartIri),
          CompareExpressionOperator.LESS_THAN_OR_EQUAL_TO,
          dateValueHasEndVar
        )

        val rightArgFilter = CompareExpression(
          XsdLiteral(dateValueContent.valueHasEndJDN.toString, OntologyConstants.Xsd.Integer.toSmartIri),
          CompareExpressionOperator.GREATER_THAN_OR_EQUAL_TO,
          dateValueHasStartVar
        )

        val filter = AndExpression(leftArgFilter, rightArgFilter)

        val statementsToAdd = (dateValStartStatementOption.toSeq :+ dateValEndStatement).filterNot(statement =>
          generatedDateStatements.contains(statement)
        )
        generatedDateStatements ++= statementsToAdd

        TransformedFilterPattern(
          Some(filter),
          statementsToAdd
        )

      case CompareExpressionOperator.NOT_EQUALS =>
        // no overlap in considered as inequality (negation of equality)
        val leftArgFilter = CompareExpression(
          XsdLiteral(dateValueContent.valueHasStartJDN.toString, OntologyConstants.Xsd.Integer.toSmartIri),
          CompareExpressionOperator.GREATER_THAN,
          dateValueHasEndVar
        )

        val rightArgFilter = CompareExpression(
          XsdLiteral(dateValueContent.valueHasEndJDN.toString, OntologyConstants.Xsd.Integer.toSmartIri),
          CompareExpressionOperator.LESS_THAN,
          dateValueHasStartVar
        )

        val filter = OrExpression(leftArgFilter, rightArgFilter)

        val statementsToAdd = (dateValStartStatementOption.toSeq :+ dateValEndStatement).filterNot(statement =>
          generatedDateStatements.contains(statement)
        )
        generatedDateStatements ++= statementsToAdd

        TransformedFilterPattern(
          Some(filter),
          statementsToAdd
        )

      case CompareExpressionOperator.LESS_THAN =>
        // period ends before indicated period
        val filter = CompareExpression(
          dateValueHasEndVar,
          CompareExpressionOperator.LESS_THAN,
          XsdLiteral(dateValueContent.valueHasStartJDN.toString, OntologyConstants.Xsd.Integer.toSmartIri)
        )

        val statementsToAdd = (dateValStartStatementOption.toSeq :+ dateValEndStatement).filterNot(statement =>
          generatedDateStatements.contains(statement)
        ) // dateValStartStatement may be used as ORDER BY statement
        generatedDateStatements ++= statementsToAdd

        TransformedFilterPattern(
          Some(filter),
          statementsToAdd
        )

      case CompareExpressionOperator.LESS_THAN_OR_EQUAL_TO =>
        // period ends before indicated period or equals it (any overlap)
        val filter = CompareExpression(
          dateValueHasStartVar,
          CompareExpressionOperator.LESS_THAN_OR_EQUAL_TO,
          XsdLiteral(dateValueContent.valueHasEndJDN.toString, OntologyConstants.Xsd.Integer.toSmartIri)
        )

        val statementToAdd =
          if (
            dateValStartStatementOption.nonEmpty && !generatedDateStatements.contains(dateValStartStatementOption.get)
          ) {
            generatedDateStatements += dateValStartStatementOption.get
            Seq(dateValStartStatementOption.get)
          } else {
            Seq.empty[StatementPattern]
          }

        TransformedFilterPattern(
          Some(filter),
          statementToAdd
        )

      case CompareExpressionOperator.GREATER_THAN =>
        // period starts after end of indicated period
        val filter = CompareExpression(
          dateValueHasStartVar,
          CompareExpressionOperator.GREATER_THAN,
          XsdLiteral(dateValueContent.valueHasEndJDN.toString, OntologyConstants.Xsd.Integer.toSmartIri)
        )

        val statementToAdd =
          if (
            dateValStartStatementOption.nonEmpty && !generatedDateStatements.contains(dateValStartStatementOption.get)
          ) {
            generatedDateStatements += dateValStartStatementOption.get
            Seq(dateValStartStatementOption.get)
          } else {
            Seq.empty[StatementPattern]
          }

        TransformedFilterPattern(
          Some(filter),
          statementToAdd
        )

      case CompareExpressionOperator.GREATER_THAN_OR_EQUAL_TO =>
        // period starts after indicated period or equals it (any overlap)
        val filter = CompareExpression(
          dateValueHasEndVar,
          CompareExpressionOperator.GREATER_THAN_OR_EQUAL_TO,
          XsdLiteral(dateValueContent.valueHasStartJDN.toString, OntologyConstants.Xsd.Integer.toSmartIri)
        )

        val statementsToAdd = (dateValStartStatementOption.toSeq :+ dateValEndStatement).filterNot(statement =>
          generatedDateStatements.contains(statement)
        ) // dateValStartStatement may be used as ORDER BY statement
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
  private def handleQueryVar(
    queryVar: QueryVariable,
    compareExpression: CompareExpression,
    typeInspectionResult: GravsearchTypeInspectionResult
  ): TransformedFilterPattern = {

    typeInspectionResult.getTypeOfEntity(queryVar) match {
      case Some(typeInfo) =>
        // Does queryVar represent a property?
        typeInfo match {
          case propInfo: PropertyTypeInfo =>
            // Yes. The right argument must be an IRI restricting the property variable to a certain property.
            compareExpression.rightArg match {
              case iriRef: IriRef =>
                handlePropertyIriQueryVar(
                  queryVar = queryVar,
                  comparisonOperator = compareExpression.operator,
                  iriRef = iriRef,
                  propInfo = propInfo
                )

              case other =>
                throw GravsearchException(
                  s"Invalid right argument ${other.toSparql} in comparison (expected a property IRI)"
                )
            }

          case nonPropInfo: NonPropertyTypeInfo =>
            // queryVar doesn't represent a property. Does it represent a resource?
            if (nonPropInfo.isResourceType) {
              // Yes. If the right argument is a variable or IRI, keep the expression as is. We know that the types of the
              // arguments are consistent, because this already been checked during type inspection.
              compareExpression.rightArg match {
                case _: QueryVariable | _: IriRef => TransformedFilterPattern(Some(compareExpression))
                case other =>
                  throw GravsearchException(
                    s"Invalid right argument ${other.toSparql} in comparison (expected a variable or IRI representing a resource)"
                  )
              }
            } else if (querySchema == ApiV2Simple) { // The left operand doesn't represent a resource. Is the query using the API v2 simple schema?
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
                    xsdType = Set(
                      OntologyConstants.Xsd.Decimal,
                      OntologyConstants.Xsd.Integer
                    ), // an integer literal is also valid
                    valueHasProperty = OntologyConstants.KnoraBase.ValueHasDecimal
                  )

                case OntologyConstants.Xsd.DateTimeStamp =>
                  handleLiteralQueryVar(
                    queryVar = queryVar,
                    comparisonOperator = compareExpression.operator,
                    literalValueExpression = compareExpression.rightArg,
                    xsdType = Set(OntologyConstants.Xsd.DateTimeStamp),
                    valueHasProperty = OntologyConstants.KnoraBase.ValueHasTimeStamp
                  )

                case OntologyConstants.Xsd.Boolean =>
                  handleLiteralQueryVar(
                    queryVar = queryVar,
                    comparisonOperator = compareExpression.operator,
                    literalValueExpression = compareExpression.rightArg,
                    xsdType = Set(OntologyConstants.Xsd.Boolean),
                    valueHasProperty = OntologyConstants.KnoraBase.ValueHasBoolean,
                    validComparisonOperators =
                      Set(CompareExpressionOperator.EQUALS, CompareExpressionOperator.NOT_EQUALS)
                  )

                case OntologyConstants.Xsd.String =>
                  handleLiteralQueryVar(
                    queryVar = queryVar,
                    comparisonOperator = compareExpression.operator,
                    literalValueExpression = compareExpression.rightArg,
                    xsdType = Set(OntologyConstants.Xsd.String),
                    valueHasProperty = OntologyConstants.KnoraBase.ValueHasString,
                    validComparisonOperators =
                      Set(CompareExpressionOperator.EQUALS, CompareExpressionOperator.NOT_EQUALS)
                  )

                case OntologyConstants.Xsd.Uri =>
                  handleLiteralQueryVar(
                    queryVar = queryVar,
                    comparisonOperator = compareExpression.operator,
                    literalValueExpression = compareExpression.rightArg,
                    xsdType = Set(OntologyConstants.Xsd.Uri),
                    valueHasProperty = OntologyConstants.KnoraBase.ValueHasUri,
                    validComparisonOperators =
                      Set(CompareExpressionOperator.EQUALS, CompareExpressionOperator.NOT_EQUALS)
                  )

                case OntologyConstants.KnoraApiV2Simple.Date =>
                  handleDateQueryVar(
                    queryVar = queryVar,
                    comparisonOperator = compareExpression.operator,
                    dateValueExpression = compareExpression.rightArg
                  )

                case OntologyConstants.KnoraApiV2Simple.ListNode =>
                  handleListQueryVar(
                    queryVar = queryVar,
                    comparisonOperator = compareExpression.operator,
                    literalValueExpression = compareExpression.rightArg
                  )

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
   * Handles the use of the SPARQL lang function in a [[FilterPattern]].
   *
   * @param langFunctionCall     the lang function call to be handled.
   * @param compareExpression    the filter pattern's compare expression.
   * @param typeInspectionResult the type inspection results.
   * @return a [[TransformedFilterPattern]].
   */
  private def handleLangFunctionCall(
    langFunctionCall: LangFunction,
    compareExpression: CompareExpression,
    typeInspectionResult: GravsearchTypeInspectionResult
  ): TransformedFilterPattern = {

    if (querySchema == ApiV2Complex) {
      throw GravsearchException(
        s"The lang function is not allowed in a Gravsearch query that uses the API v2 complex schema"
      )
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
    if (
      !Set(CompareExpressionOperator.EQUALS, CompareExpressionOperator.NOT_EQUALS).contains(compareExpression.operator)
    )
      throw GravsearchException(s"Comparison operator must be '=' or '!=' for use with a 'lang' function call")

    val langLiteral: XsdLiteral = compareExpression.rightArg match {
      case strLiteral: XsdLiteral if strLiteral.datatype == OntologyConstants.Xsd.String.toSmartIri => strLiteral

      case _ =>
        throw GravsearchException(
          s"Right argument of comparison statement must be a string literal for use with 'lang' function call"
        )
    }

    // Generate a variable name representing the language of the text value
    val textValHasLanguage: QueryVariable = SparqlTransformer.createUniqueVariableNameFromEntityAndProperty(
      langFunctionCall.textValueVar,
      OntologyConstants.KnoraBase.ValueHasLanguage
    )

    // Add a statement to assign the literal to a variable, which we'll use in the transformed FILTER expression,
    // if that statement hasn't been added already.
    val statementToAddForValueHasLanguage =
      if (
        addGeneratedVariableForValueLiteral(
          valueVar = langFunctionCall.textValueVar,
          generatedVar = textValHasLanguage,
          useInOrderBy = false
        )
      ) {
        Seq(
          // connects the value object with the value language code
          StatementPattern.makeExplicit(
            subj = langFunctionCall.textValueVar,
            pred = IriRef(OntologyConstants.KnoraBase.ValueHasLanguage.toSmartIri),
            textValHasLanguage
          )
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
  private def handleRegexFunctionCall(
    regexFunctionCall: RegexFunction,
    typeInspectionResult: GravsearchTypeInspectionResult
  ): TransformedFilterPattern =
    // If the query uses the API v2 complex schema, leave the function call as it is.
    if (querySchema == ApiV2Complex) {
      TransformedFilterPattern(Some(regexFunctionCall))
    } else {
      // If the query uses only the simple schema, transform the function call.

      // Make sure that the first argument of the regex function is a query variable.
      val regexQueryVar: QueryVariable = regexFunctionCall.textExpr match {
        case queryVar: QueryVariable => queryVar
        case _                       => throw GravsearchException(s"First argument of regex function must be a variable")
      }

      // make sure that the query variable (first argument of regex function) represents string literal
      typeInspectionResult.getTypeOfEntity(regexQueryVar) match {
        case Some(typeInfo) =>
          typeInfo match {

            case nonPropInfo: NonPropertyTypeInfo =>
              nonPropInfo.typeIri.toString match {

                case OntologyConstants.Xsd.String =>
                  () // xsd:string is expected, TODO: should also xsd:anyUri be allowed?

                case _ => throw GravsearchException(s"${regexQueryVar.toSparql} must be of type xsd:string")
              }

            case _ => throw GravsearchException(s"${regexQueryVar.toSparql} must be of type NonPropertyTypeInfo")
          }

        case None =>
          throw GravsearchException(s"No type information found about ${regexQueryVar.toSparql}")
      }

      // Does the variable refer to resource metadata?
      if (resourceMetadataVariables.contains(regexQueryVar)) {
        // Yes. Leave the expression as is.
        TransformedFilterPattern(
          Some(RegexFunction(regexQueryVar, regexFunctionCall.pattern, regexFunctionCall.modifier)),
          Seq.empty
        )
      } else {
        // No, it refers to a TextValue. Generate a variable name representing the string literal.
        val textValHasString: QueryVariable = SparqlTransformer.createUniqueVariableNameFromEntityAndProperty(
          base = regexQueryVar,
          propertyIri = OntologyConstants.KnoraBase.ValueHasString
        )

        // Add a statement to assign the literal to a variable, which we'll use in the transformed FILTER expression,
        // if that statement hasn't been added already.
        val statementToAddForValueHasString: Seq[StatementPattern] =
          if (addGeneratedVariableForValueLiteral(regexQueryVar, textValHasString)) {
            Seq(
              // connects the value object with the value literal
              StatementPattern.makeExplicit(
                subj = regexQueryVar,
                pred = IriRef(OntologyConstants.KnoraBase.ValueHasString.toSmartIri),
                textValHasString
              )
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
   * Handles the function `knora-api:matchText` in the simple schema.
   *
   * @param functionCallExpression the function call to be handled.
   * @param typeInspectionResult   the type inspection results.
   * @param isTopLevel             if `true`, this is the top-level expression in the `FILTER`.
   * @return a [[TransformedFilterPattern]].
   */
  private def handleMatchTextFunctionInSimpleSchema(
    functionCallExpression: FunctionCallExpression,
    typeInspectionResult: GravsearchTypeInspectionResult,
    isTopLevel: Boolean
  ): TransformedFilterPattern = {
    val functionIri: SmartIri = functionCallExpression.functionIri.iri

    if (querySchema == ApiV2Complex) {
      throw GravsearchException(
        s"Function ${functionIri.toSparql} cannot be used in a Gravsearch query written in the complex schema; use ${OntologyConstants.KnoraApiV2Complex.MatchTextFunction.toSmartIri.toSparql} instead"
      )
    }

    // The match function must be the top-level expression, otherwise boolean logic won't work properly.
    if (!isTopLevel) {
      throw GravsearchException(s"Function ${functionIri.toSparql} must be the top-level expression in a FILTER")
    }

    // two arguments are expected: the first must be a variable representing a string value,
    // the second must be a string literal

    if (functionCallExpression.args.size != 2)
      throw GravsearchException(s"Two arguments are expected for ${functionIri.toSparql}")

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

    val textValHasString: QueryVariable = SparqlTransformer.createUniqueVariableNameFromEntityAndProperty(
      base = textValueVar,
      propertyIri = OntologyConstants.KnoraBase.ValueHasString
    )

    // Generate an optional statement to assign the literal to a variable, which we can pass to LuceneQueryPattern,
    // if that statement hasn't been added already.
    val valueHasStringStatement = if (addGeneratedVariableForValueLiteral(textValueVar, textValHasString)) {
      Some(
        StatementPattern.makeExplicit(
          subj = textValueVar,
          pred = IriRef(OntologyConstants.KnoraBase.ValueHasString.toSmartIri),
          textValHasString
        )
      )
    } else {
      None
    }

    val searchTerm: XsdLiteral =
      functionCallExpression.getArgAsLiteral(1, xsdDatatype = OntologyConstants.Xsd.String.toSmartIri)

    val searchTerms: LuceneQueryString = LuceneQueryString(searchTerm.value)

    // Replace the filter with a LuceneQueryPattern.
    TransformedFilterPattern(
      None, // FILTER has been replaced by statements
      Seq(
        LuceneQueryPattern(
          subj = textValueVar,
          obj = textValHasString,
          queryString = searchTerms,
          literalStatement = valueHasStringStatement
        )
      )
    )
  }

  /**
   * Handles the function `knora-api:matchText` in the complex schema.
   *
   * @param functionCallExpression the function call to be handled.
   * @param typeInspectionResult   the type inspection results.
   * @param isTopLevel             if `true`, this is the top-level expression in the `FILTER`.
   * @return a [[TransformedFilterPattern]].
   */
  private def handleMatchTextFunctionInComplexSchema(
    functionCallExpression: FunctionCallExpression,
    typeInspectionResult: GravsearchTypeInspectionResult,
    isTopLevel: Boolean
  ): TransformedFilterPattern = {
    val functionIri: SmartIri = functionCallExpression.functionIri.iri

    if (querySchema == ApiV2Simple) {
      throw GravsearchException(
        s"Function ${functionIri.toSparql} cannot be used in a Gravsearch query written in the simple schema; use ${OntologyConstants.KnoraApiV2Simple.MatchTextFunction.toSmartIri.toSparql} instead"
      )
    }

    // The match function must be the top-level expression, otherwise boolean logic won't work properly.
    if (!isTopLevel) {
      throw GravsearchException(s"Function ${functionIri.toSparql} must be the top-level expression in a FILTER")
    }

    // two arguments are expected: the first must be a variable representing a text value,
    // the second must be a string literal

    if (functionCallExpression.args.size != 2)
      throw GravsearchException(s"Two arguments are expected for ${functionIri.toSparql}")

    // a QueryVariable expected to represent a text value
    val textValueVar: QueryVariable = functionCallExpression.getArgAsQueryVar(pos = 0)

    typeInspectionResult.getTypeOfEntity(textValueVar) match {
      case Some(nonPropInfo: NonPropertyTypeInfo) =>
        nonPropInfo.typeIri.toString match {

          case OntologyConstants.KnoraApiV2Complex.TextValue => ()

          case _ => throw GravsearchException(s"${textValueVar.toSparql} must be a knora-api:TextValue")
        }

      case _ => throw GravsearchException(s"${textValueVar.toSparql} must be a knora-api:TextValue")
    }

    val textValHasString: QueryVariable = SparqlTransformer.createUniqueVariableNameFromEntityAndProperty(
      base = textValueVar,
      propertyIri = OntologyConstants.KnoraBase.ValueHasString
    )

    // Generate an optional statement to assign the literal to a variable, which we can pass to LuceneQueryPattern,
    // if that statement hasn't been added already.
    val valueHasStringStatement = if (addGeneratedVariableForValueLiteral(textValueVar, textValHasString)) {
      Some(
        StatementPattern.makeExplicit(
          subj = textValueVar,
          pred = IriRef(OntologyConstants.KnoraBase.ValueHasString.toSmartIri),
          textValHasString
        )
      )
    } else {
      None
    }

    val searchTerm: XsdLiteral =
      functionCallExpression.getArgAsLiteral(1, xsdDatatype = OntologyConstants.Xsd.String.toSmartIri)

    val searchTerms: LuceneQueryString = LuceneQueryString(searchTerm.value)

    // Replace the filter with a LuceneQueryPattern.
    TransformedFilterPattern(
      None, // FILTER has been replaced by statements
      Seq(
        LuceneQueryPattern(
          subj = textValueVar,
          obj = textValHasString,
          queryString = searchTerms,
          literalStatement = valueHasStringStatement
        )
      )
    )
  }

  /**
   * Handles the function `knora-api:matchTextInStandoff`.
   *
   * @param functionCallExpression the function call to be handled.
   * @param typeInspectionResult   the type inspection results.
   * @param isTopLevel             if `true`, this is the top-level expression in the `FILTER`.
   * @return a [[TransformedFilterPattern]].
   */
  private def handleMatchTextInStandoffFunction(
    functionCallExpression: FunctionCallExpression,
    typeInspectionResult: GravsearchTypeInspectionResult,
    isTopLevel: Boolean
  ): TransformedFilterPattern = {
    val functionIri: SmartIri = functionCallExpression.functionIri.iri

    if (querySchema == ApiV2Simple) {
      throw GravsearchException(
        s"Function ${functionIri.toSparql} cannot be used in a Gravsearch query written in the simple schema"
      )
    }

    if (!isTopLevel) {
      throw GravsearchException(s"Function ${functionIri.toSparql} must be the top-level expression in a FILTER")
    }

    // Three arguments are expected:
    // 1. a variable representing the text value
    // 2. a variable representing the standoff tag
    // 3. a string literal containing space-separated search terms

    if (functionCallExpression.args.size != 3)
      throw GravsearchException(s"Three arguments are expected for ${functionIri.toSparql}")

    // a QueryVariable expected to represent a text value
    val textValueVar: QueryVariable = functionCallExpression.getArgAsQueryVar(pos = 0)

    typeInspectionResult.getTypeOfEntity(textValueVar) match {
      case Some(nonPropInfo: NonPropertyTypeInfo) =>
        nonPropInfo.typeIri.toString match {

          case OntologyConstants.KnoraApiV2Complex.TextValue => ()

          case _ => throw GravsearchException(s"${textValueVar.toSparql} must be a knora-api:TextValue")
        }

      case _ => throw GravsearchException(s"${textValueVar.toSparql} must be a knora-api:TextValue")
    }

    val textValHasString: QueryVariable = SparqlTransformer.createUniqueVariableNameFromEntityAndProperty(
      base = textValueVar,
      propertyIri = OntologyConstants.KnoraBase.ValueHasString
    )

    // Generate a statement to assign the literal to a variable, if that statement hasn't been added already.
    val valueHasStringStatement = if (addGeneratedVariableForValueLiteral(textValueVar, textValHasString)) {
      Some(
        StatementPattern.makeExplicit(
          subj = textValueVar,
          pred = IriRef(OntologyConstants.KnoraBase.ValueHasString.toSmartIri),
          textValHasString
        )
      )
    } else {
      None
    }

    // A string literal representing the search terms.
    val searchTermStr: XsdLiteral =
      functionCallExpression.getArgAsLiteral(pos = 2, xsdDatatype = OntologyConstants.Xsd.String.toSmartIri)

    val searchTerms: LuceneQueryString = LuceneQueryString(searchTermStr.value)

    // Generate a LuceneQueryPattern to search the full-text search index, to assert that text value contains
    // the search terms.
    val luceneQueryPattern: Seq[LuceneQueryPattern] = Seq(
      LuceneQueryPattern(
        subj = textValueVar,
        obj = textValHasString,
        queryString = searchTerms,
        literalStatement = None // We have to add this statement ourselves, so LuceneQueryPattern doesn't need to.
      )
    )

    // Generate query patterns to assign the text in the standoff tag to a variable, if we
    // haven't done so already.

    // A variable representing the standoff tag.
    val standoffTagVar: QueryVariable = functionCallExpression.getArgAsQueryVar(pos = 1)

    val startVariable = QueryVariable(standoffTagVar.variableName + "__start")
    val endVariable   = QueryVariable(standoffTagVar.variableName + "__end")

    val markedUpPatternsToAdd: Seq[QueryPattern] = if (!standoffMarkedUpVariables.contains(startVariable)) {
      standoffMarkedUpVariables += startVariable

      Seq(
        // ?standoffTag knora-base:standoffTagHasStart ?standoffTag__start .
        StatementPattern.makeExplicit(
          standoffTagVar,
          IriRef(OntologyConstants.KnoraBase.StandoffTagHasStart.toSmartIri),
          startVariable
        ),
        // ?standoffTag knora-base:standoffTagHasEnd ?standoffTag__end .
        StatementPattern.makeExplicit(
          standoffTagVar,
          IriRef(OntologyConstants.KnoraBase.StandoffTagHasEnd.toSmartIri),
          endVariable
        )
      )
    } else {
      Seq.empty[QueryPattern]
    }

    // Generate a FILTER pattern for each search term, using the regex function to assert that the text in the
    // standoff tag contains the term:
    // FILTER REGEX(SUBSTR(?textValueStr, ?standoffTag__start + 1, ?standoffTag__end - ?standoffTag__start), 'term', "i")
    // TODO: handle the differences between regex syntax and Lucene syntax.
    val regexFilters: Seq[FilterPattern] = searchTerms.getSingleTerms.map { term: String =>
      FilterPattern(
        expression = RegexFunction(
          textExpr = SubStrFunction(
            textLiteralVar = textValHasString,
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
          ),
          pattern = term, // TODO: Ignore Lucene operators
          modifier = Some("i")
        )
      )
    }

    TransformedFilterPattern(
      expression = None, // The expression has been replaced by additional patterns.
      additionalPatterns = valueHasStringStatement.toSeq ++ luceneQueryPattern ++ markedUpPatternsToAdd ++ regexFilters
    )
  }

  /**
   * Checks that the query is in the simple schema, then calls `handleMatchLabelFunction`.
   *
   * @param functionCallExpression the function call to be handled.
   * @param typeInspectionResult   the type inspection results.
   * @param isTopLevel             if `true`, this is the top-level expression in the `FILTER`.
   * @return a [[TransformedFilterPattern]].
   */
  private def handleMatchLabelFunctionInSimpleSchema(
    functionCallExpression: FunctionCallExpression,
    typeInspectionResult: GravsearchTypeInspectionResult,
    isTopLevel: Boolean
  ): TransformedFilterPattern = {
    val functionIri: SmartIri = functionCallExpression.functionIri.iri

    if (querySchema == ApiV2Complex) {
      throw GravsearchException(
        s"Function ${functionIri.toSparql} cannot be used in a Gravsearch query written in the complex schema; use ${OntologyConstants.KnoraApiV2Complex.MatchLabelFunction.toSmartIri.toSparql} instead"
      )
    }

    handleMatchLabelFunction(
      functionCallExpression = functionCallExpression,
      typeInspectionResult = typeInspectionResult,
      isTopLevel = isTopLevel
    )
  }

  /**
   * Checks that the query is in the complex schema, then calls `handleMatchLabelFunction`.
   *
   * @param functionCallExpression the function call to be handled.
   * @param typeInspectionResult   the type inspection results.
   * @param isTopLevel             if `true`, this is the top-level expression in the `FILTER`.
   * @return a [[TransformedFilterPattern]].
   */
  private def handleMatchLabelFunctionInComplexSchema(
    functionCallExpression: FunctionCallExpression,
    typeInspectionResult: GravsearchTypeInspectionResult,
    isTopLevel: Boolean
  ): TransformedFilterPattern = {
    val functionIri: SmartIri = functionCallExpression.functionIri.iri

    if (querySchema == ApiV2Simple) {
      throw GravsearchException(
        s"Function ${functionIri.toSparql} cannot be used in a Gravsearch query written in the simple schema; use ${OntologyConstants.KnoraApiV2Simple.MatchLabelFunction.toSmartIri.toSparql} instead"
      )
    }

    handleMatchLabelFunction(
      functionCallExpression = functionCallExpression,
      typeInspectionResult = typeInspectionResult,
      isTopLevel = isTopLevel
    )
  }

  /**
   * Handles the function `knora-api:matchLabel` in either schema.
   *
   * @param functionCallExpression the function call to be handled.
   * @param typeInspectionResult   the type inspection results.
   * @param isTopLevel             if `true`, this is the top-level expression in the `FILTER`.
   * @return a [[TransformedFilterPattern]].
   */
  private def handleMatchLabelFunction(
    functionCallExpression: FunctionCallExpression,
    typeInspectionResult: GravsearchTypeInspectionResult,
    isTopLevel: Boolean
  ): TransformedFilterPattern = {
    val functionIri: SmartIri = functionCallExpression.functionIri.iri

    // The matchLabel function must be the top-level expression, otherwise boolean logic won't work properly.
    if (!isTopLevel) {
      throw GravsearchException(s"Function ${functionIri.toSparql} must be the top-level expression in a FILTER")
    }

    // Two arguments are expected:
    // 1. a variable representing a resource
    // 2. a string literal

    if (functionCallExpression.args.size != 2)
      throw GravsearchException(s"Two arguments are expected for ${functionIri.toSparql}")

    // A QueryVariable expected to represent a resource.
    val resourceVar: QueryVariable = functionCallExpression.getArgAsQueryVar(pos = 0)

    typeInspectionResult.getTypeOfEntity(resourceVar) match {
      case Some(nonPropInfo: NonPropertyTypeInfo) if nonPropInfo.isResourceType => ()
      case _                                                                    => throw GravsearchException(s"${resourceVar.toSparql} must be a knora-api:Resource")
    }

    // Add an optional statement to assign the literal to a variable, which we can pass to LuceneQueryPattern,
    // if that statement hasn't been added already.

    val rdfsLabelVar: QueryVariable = SparqlTransformer.createUniqueVariableNameFromEntityAndProperty(
      base = resourceVar,
      propertyIri = OntologyConstants.Rdfs.Label
    )

    val rdfsLabelStatement = if (addGeneratedVariableForValueLiteral(resourceVar, rdfsLabelVar)) {
      Some(
        StatementPattern
          .makeExplicit(subj = resourceVar, pred = IriRef(OntologyConstants.Rdfs.Label.toSmartIri), rdfsLabelVar)
      )
    } else {
      None
    }

    val searchTerm: XsdLiteral =
      functionCallExpression.getArgAsLiteral(1, xsdDatatype = OntologyConstants.Xsd.String.toSmartIri)
    val luceneQueryString: LuceneQueryString = LuceneQueryString(searchTerm.value)

    // Replace the filter with a LuceneQueryPattern.
    TransformedFilterPattern(
      None, // The FILTER has been replaced by statements.
      Seq(
        LuceneQueryPattern(
          subj = resourceVar,
          obj = rdfsLabelVar,
          queryString = luceneQueryString,
          literalStatement = rdfsLabelStatement
        )
      )
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
  private def handleStandoffLinkFunction(
    functionCallExpression: FunctionCallExpression,
    typeInspectionResult: GravsearchTypeInspectionResult,
    isTopLevel: Boolean
  ): TransformedFilterPattern = {
    val functionIri: SmartIri = functionCallExpression.functionIri.iri

    if (querySchema == ApiV2Simple) {
      throw GravsearchException(
        s"Function ${functionIri.toSparql} cannot be used in a Gravsearch query written in the simple schema"
      )
    }

    if (!isTopLevel) {
      throw GravsearchException(s"Function ${functionIri.toSparql} must be the top-level expression in a FILTER")
    }

    // Three arguments are expected:
    // 1. a variable or IRI representing the resource that is the source of the link
    // 2. a variable representing the standoff link tag
    // 3. a variable or IRI representing the resource that is the target of the link

    if (functionCallExpression.args.size != 3)
      throw GravsearchException(s"Three arguments are expected for ${functionIri.toSparql}")

    // A variable or IRI representing the resource that is the source of the link.
    val linkSourceEntity = functionCallExpression.args.head match {
      case queryVar: QueryVariable => queryVar
      case iriRef: IriRef          => iriRef
      case _                       => throw GravsearchException(s"The first argument of ${functionIri.toSparql} must be a variable or IRI")
    }

    typeInspectionResult.getTypeOfEntity(linkSourceEntity) match {
      case Some(nonPropertyTypeInfo: NonPropertyTypeInfo) if nonPropertyTypeInfo.isResourceType => ()
      case _ =>
        throw GravsearchException(s"The first argument of ${functionIri.toSparql} must represent a knora-api:Resource")
    }

    // A variable representing the standoff link tag.
    val standoffTagVar = functionCallExpression.getArgAsQueryVar(pos = 1)

    typeInspectionResult.getTypeOfEntity(standoffTagVar) match {
      case Some(nonPropertyTypeInfo: NonPropertyTypeInfo) if nonPropertyTypeInfo.isStandoffTagType => ()

      case _ =>
        throw GravsearchException(
          s"The second argument of ${functionIri.toSparql} must represent a knora-api:StandoffTag"
        )
    }

    val linkTargetEntity = functionCallExpression.args(2) match {
      case queryVar: QueryVariable => queryVar
      case iriRef: IriRef          => iriRef
      case _                       => throw GravsearchException(s"The third argument of ${functionIri.toSparql} must be a variable or IRI")
    }

    val statementsForTargetResource: Seq[QueryPattern] = typeInspectionResult.getTypeOfEntity(linkTargetEntity) match {
      case Some(nonPropertyTpeInfo: NonPropertyTypeInfo) if nonPropertyTpeInfo.isResourceType =>
        // process the entity representing the target of the link
        createAdditionalStatementsForNonPropertyType(nonPropertyTpeInfo, linkTargetEntity)

      case _ =>
        throw GravsearchException(s"The third argument of ${functionIri.toSparql} must represent a knora-api:Resource")
    }

    val hasStandoffLinkToIriRef = IriRef(OntologyConstants.KnoraBase.HasStandoffLinkTo.toSmartIri)

    // Generate statements linking the source resource and the standoff tag to the target resource.
    val linkStatements = Seq(
      StatementPattern.makeExplicit(subj = linkSourceEntity, pred = hasStandoffLinkToIriRef, obj = linkTargetEntity),
      StatementPattern.makeInferred(
        subj = standoffTagVar,
        pred = IriRef(OntologyConstants.KnoraBase.StandoffTagHasLink.toSmartIri),
        obj = linkTargetEntity
      )
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
   * Handles a Gravsearch-specific function call in a [[FilterPattern]].
   *
   * @param functionCallExpression the function call to be handled.
   * @param typeInspectionResult   the type inspection results.
   * @param isTopLevel             if `true`, this is the top-level expression in the `FILTER`.
   * @return a [[TransformedFilterPattern]].
   */
  private def handleKnoraFunctionCall(
    functionCallExpression: FunctionCallExpression,
    typeInspectionResult: GravsearchTypeInspectionResult,
    isTopLevel: Boolean
  ): TransformedFilterPattern = {
    val functionIri: SmartIri = functionCallExpression.functionIri.iri

    // Get a Scala function that implements the Gravsearch function.
    val functionFunction
      : (FunctionCallExpression, GravsearchTypeInspectionResult, Boolean) => TransformedFilterPattern =
      functionIri.toString match {
        case OntologyConstants.KnoraApiV2Simple.MatchTextFunction            => handleMatchTextFunctionInSimpleSchema
        case OntologyConstants.KnoraApiV2Complex.MatchTextFunction           => handleMatchTextFunctionInComplexSchema
        case OntologyConstants.KnoraApiV2Simple.MatchLabelFunction           => handleMatchLabelFunctionInSimpleSchema
        case OntologyConstants.KnoraApiV2Complex.MatchLabelFunction          => handleMatchLabelFunctionInComplexSchema
        case OntologyConstants.KnoraApiV2Complex.MatchTextInStandoffFunction => handleMatchTextInStandoffFunction
        case OntologyConstants.KnoraApiV2Complex.StandoffLinkFunction        => handleStandoffLinkFunction
        case OntologyConstants.KnoraApiV2Complex.ToSimpleDateFunction =>
          throw GravsearchException(s"Function ${functionIri.toSparql} must be used in a comparison expression")

        case _ => throw NotImplementedException(s"Function ${functionCallExpression.functionIri} not found")
      }

    // Call the Scala function.
    functionFunction(
      functionCallExpression,
      typeInspectionResult,
      isTopLevel
    )
  }

  /**
   * Handles the `knora-api:toSimpleDate` function in a comparison.
   *
   * @param filterCompare        the comparison expression.
   * @param functionCallExpr     the function call expression.
   * @param typeInspectionResult the type inspection result.
   * @return a [[TransformedFilterPattern]].
   */
  private def handleToSimpleDateFunction(
    filterCompare: CompareExpression,
    functionCallExpr: FunctionCallExpression,
    typeInspectionResult: GravsearchTypeInspectionResult
  ): TransformedFilterPattern = {
    if (querySchema == ApiV2Simple) {
      throw GravsearchException(
        s"Function ${functionCallExpr.functionIri.toSparql} cannot be used in a query written in the simple schema"
      )
    }

    if (functionCallExpr.args.size != 1)
      throw GravsearchException(s"One argument is expected for ${functionCallExpr.functionIri.toSparql}")

    // One argument is expected: a QueryVariable representing something that belongs to a subclass of knora-api:DateBase.
    val dateBaseVar: QueryVariable = functionCallExpr.getArgAsQueryVar(pos = 0)

    typeInspectionResult.getTypeOfEntity(dateBaseVar) match {
      case Some(nonPropInfo: NonPropertyTypeInfo) =>
        if (
          !(nonPropInfo.isStandoffTagType || nonPropInfo.typeIri.toString == OntologyConstants.KnoraApiV2Complex.DateValue)
        ) {
          throw GravsearchException(
            s"${dateBaseVar.toSparql} must represent a knora-api:DateValue or a knora-api:StandoffDateTag"
          )
        }

      case _ =>
        throw GravsearchException(
          s"${dateBaseVar.toSparql} must represent a knora-api:DateValue or a knora-api:StandoffDateTag"
        )
    }

    handleDateQueryVar(
      queryVar = dateBaseVar,
      comparisonOperator = filterCompare.operator,
      dateValueExpression = filterCompare.rightArg
    )
  }

  /**
   * Transforms a Filter expression provided in the input query (knora-api simple) into a knora-base compliant Filter expression.
   *
   * @param filterExpression     the `FILTER` expression to be transformed.
   * @param typeInspectionResult the results of type inspection.
   * @param isTopLevel `true` if this is the top-level expression in the filter.
   * @return a [[TransformedFilterPattern]].
   */
  protected def transformFilterPattern(
    filterExpression: Expression,
    typeInspectionResult: GravsearchTypeInspectionResult,
    isTopLevel: Boolean
  ): TransformedFilterPattern = {
    // Are we looking at a top-level filter expression in a UNION block?
    if (isTopLevel && inUnionBlock) {
      // Yes. Make sure that all the variables used in the FILTER have already been bound in the same block.
      val unboundVariables: Set[QueryVariable] = filterExpression.getVariables -- variablesInUnionBlocks.head

      if (unboundVariables.nonEmpty) {
        throw GravsearchException(
          s"One or more variables used in a filter have not been bound in the same UNION block: ${unboundVariables.map(_.toSparql).mkString(", ")}"
        )
      }
    }

    filterExpression match {

      case filterCompare: CompareExpression =>
        // left argument of a CompareExpression must be a QueryVariable or a function call
        filterCompare.leftArg match {

          case queryVar: QueryVariable =>
            handleQueryVar(
              queryVar = queryVar,
              compareExpression = filterCompare,
              typeInspectionResult = typeInspectionResult
            )

          case functionCallExpr: FunctionCallExpression
              if functionCallExpr.functionIri.iri.toString == OntologyConstants.KnoraApiV2Complex.ToSimpleDateFunction =>
            handleToSimpleDateFunction(
              filterCompare = filterCompare,
              functionCallExpr = functionCallExpr,
              typeInspectionResult = typeInspectionResult
            )

          case lang: LangFunction =>
            handleLangFunctionCall(
              langFunctionCall = lang,
              compareExpression = filterCompare,
              typeInspectionResult = typeInspectionResult
            )

          case other => throw GravsearchException(s"Invalid left argument ${other.toSparql} in comparison")
        }

      case filterOr: OrExpression =>
        // recursively call this method for both arguments
        val filterExpressionLeft: TransformedFilterPattern =
          transformFilterPattern(filterOr.leftArg, typeInspectionResult, isTopLevel = false)
        val filterExpressionRight: TransformedFilterPattern =
          transformFilterPattern(filterOr.rightArg, typeInspectionResult, isTopLevel = false)

        // recreate Or expression and include additional statements
        TransformedFilterPattern(
          Some(
            OrExpression(
              filterExpressionLeft.expression.getOrElse(
                throw DataConversionException("Expression was expected from previous FILTER conversion, but None given")
              ),
              filterExpressionRight.expression.getOrElse(
                throw DataConversionException("Expression was expected from previous FILTER conversion, but None given")
              )
            )
          ),
          filterExpressionLeft.additionalPatterns ++ filterExpressionRight.additionalPatterns
        )

      case filterAnd: AndExpression =>
        // recursively call this method for both arguments
        val filterExpressionLeft: TransformedFilterPattern =
          transformFilterPattern(filterAnd.leftArg, typeInspectionResult, isTopLevel = false)
        val filterExpressionRight: TransformedFilterPattern =
          transformFilterPattern(filterAnd.rightArg, typeInspectionResult, isTopLevel = false)

        // recreate And expression and include additional statements
        TransformedFilterPattern(
          Some(
            AndExpression(
              filterExpressionLeft.expression.getOrElse(
                throw DataConversionException("Expression was expected from previous FILTER conversion, but None given")
              ),
              filterExpressionRight.expression.getOrElse(
                throw DataConversionException("Expression was expected from previous FILTER conversion, but None given")
              )
            )
          ),
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
