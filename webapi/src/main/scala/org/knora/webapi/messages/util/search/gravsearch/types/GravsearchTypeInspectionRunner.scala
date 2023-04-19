/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.util.search.gravsearch.types

import zio.Task
import zio.ZIO

import dsp.errors.GravsearchException
import org.knora.webapi.core.MessageRelay
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.util.search._

/**
 * Runs Gravsearch type inspection using one or more type inspector implementations.
 */
final case class GravsearchTypeInspectionRunner(
  private val queryTraverser: QueryTraverser,
  private val messageRelay: MessageRelay,
  implicit private val stringFormatter: StringFormatter
) {

  // If inference was requested, construct an inferring type inspector.
  private val maybeInferringTypeInspector: Option[GravsearchTypeInspector] =
    Some(new InferringGravsearchTypeInspector(nextInspector = None, messageRelay, queryTraverser))

  // The pipeline of type inspectors.
  private val typeInspectionPipeline = new AnnotationReadingGravsearchTypeInspector(
    nextInspector = maybeInferringTypeInspector,
    queryTraverser
  )

  /**
   * Given the WHERE clause from a parsed Gravsearch query, returns information about the types found
   * in the query.
   *
   * @param whereClause    the Gravsearch WHERE clause.
   * @param requestingUser the requesting user.
   * @return the result of the type inspection.
   */
  def inspectTypes(whereClause: WhereClause, requestingUser: UserADM): Task[GravsearchTypeInspectionResult] =
    for {
      // Get the set of typeable entities in the Gravsearch query.
      typeableEntities <- ZIO.attempt {
                            queryTraverser.visitWherePatterns(
                              patterns = whereClause.patterns,
                              whereVisitor = new TypeableEntityCollectingWhereVisitor,
                              initialAcc = Set.empty[TypeableEntity]
                            )
                          }

      // In the initial intermediate result, none of the entities have types yet.
      initialResult: IntermediateTypeInspectionResult = IntermediateTypeInspectionResult(typeableEntities)

      // Run the pipeline and get its result.
      lastResult <- typeInspectionPipeline.inspectTypes(
                      previousResult = initialResult,
                      whereClause = whereClause,
                      requestingUser = requestingUser
                    )

      untypedEntities: Set[TypeableEntity] = lastResult.untypedEntities
      _ <- // Are any entities still untyped?
        if (untypedEntities.nonEmpty) {
          ZIO.fail(
            //  Yes. Return an error.
            GravsearchException(
              s"Types could not be determined for one or more entities: ${untypedEntities.mkString(", ")}"
            )
          )
        } else {
          // No. Are there any entities with multiple types?
          val inconsistentEntities: Map[TypeableEntity, Set[GravsearchEntityTypeInfo]] =
            lastResult.entitiesWithInconsistentTypes
          ZIO.fail {
            // Yes. Return an error.
            val inconsistentStr = inconsistentEntities.map { case (entity, entityTypes) =>
              s"$entity ${entityTypes.mkString(" ; ")} ."
            }.mkString(" ")
            GravsearchException(s"One or more entities have inconsistent types: $inconsistentStr")
          }.when(inconsistentEntities.nonEmpty)
        }
    } yield lastResult.toFinalResult

  /**
   * A [[WhereVisitor]] that collects typeable entities from a Gravsearch WHERE clause.
   */
  private class TypeableEntityCollectingWhereVisitor extends WhereVisitor[Set[TypeableEntity]] {

    /**
     * Collects typeable entities from a statement.
     *
     * @param statementPattern the pattern to be visited.
     * @param acc              the accumulator.
     * @return the accumulator.
     */
    override def visitStatementInWhere(
      statementPattern: StatementPattern,
      acc: Set[TypeableEntity]
    ): Set[TypeableEntity] =
      statementPattern.pred match {
        case iriRef: IriRef if iriRef.iri.toString == OntologyConstants.Rdf.Type =>
          // If the predicate is rdf:type, only the subject can be typeable.
          acc ++ GravsearchTypeInspectionUtil.toTypeableEntities(Seq(statementPattern.subj))

        case _ =>
          // Otherwise, the subject, the predicate, and the object could all be typeable.
          acc ++ GravsearchTypeInspectionUtil.toTypeableEntities(
            Seq(statementPattern.subj, statementPattern.pred, statementPattern.obj)
          )
      }

    /**
     * Collects typeable entities from a `FILTER`.
     *
     * @param filterPattern the pattern to be visited.
     * @param acc           the accumulator.
     * @return the accumulator.
     */
    override def visitFilter(filterPattern: FilterPattern, acc: Set[TypeableEntity]): Set[TypeableEntity] =
      visitFilterExpression(filterPattern.expression, acc)

    /**
     * Collects typeable entities from a filter expression.
     *
     * @param filterExpression the filter expression to be visited.
     * @param acc              the accumulator.
     * @return the accumulator.
     */
    private def visitFilterExpression(filterExpression: Expression, acc: Set[TypeableEntity]): Set[TypeableEntity] =
      filterExpression match {
        case compareExpr: CompareExpression =>
          compareExpr match {
            case CompareExpression(queryVariable: QueryVariable, _: CompareExpressionOperator.Value, iriRef: IriRef) =>
              // A variable is compared to an IRI. The variable and the IRI are typeable.
              acc + TypeableVariable(queryVariable.variableName) + TypeableIri(iriRef.iri)

            case CompareExpression(queryVariable: QueryVariable, _, _: XsdLiteral) =>
              // A variable is compared to an XSD literal. The variable is typeable.
              acc + TypeableVariable(queryVariable.variableName)

            case _ =>
              val accFromLeft = visitFilterExpression(compareExpr.leftArg, acc)
              visitFilterExpression(compareExpr.rightArg, accFromLeft)
          }

        case functionCallExpression: FunctionCallExpression =>
          // Function arguments that are variables or IRIs are typeable.

          val variableArguments: Seq[TypeableEntity] = functionCallExpression.args.collect {
            case queryVariable: QueryVariable => TypeableVariable(queryVariable.variableName)
            case iriRef: IriRef               => TypeableIri(iriRef.iri)
          }

          acc ++ variableArguments

        case andExpr: AndExpression =>
          val accFromLeft = visitFilterExpression(andExpr.leftArg, acc)
          visitFilterExpression(andExpr.rightArg, accFromLeft)

        case orExpr: OrExpression =>
          val accFromLeft = visitFilterExpression(orExpr.leftArg, acc)
          visitFilterExpression(orExpr.rightArg, accFromLeft)

        case _ => acc
      }
  }

}
