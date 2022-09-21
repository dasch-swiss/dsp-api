/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.util.search.gravsearch.types

import akka.actor.ActorRef

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import dsp.errors.GravsearchException
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.util.ResponderData
import org.knora.webapi.messages.util.search._
import org.knora.webapi.settings.KnoraDispatchers

/**
 * Runs Gravsearch type inspection using one or more type inspector implementations.
 *
 * @param appActor      a reference to the application actor
 * @param responderData the Knora [[ResponderData]].
 * @param inferTypes    if true, use type inference.
 */
class GravsearchTypeInspectionRunner(
  appActor: ActorRef,
  responderData: ResponderData,
  inferTypes: Boolean = true
) {
  private implicit val executionContext: ExecutionContext =
    responderData.system.dispatchers.lookup(KnoraDispatchers.KnoraActorDispatcher)

  // If inference was requested, construct an inferring type inspector.
  private val maybeInferringTypeInspector: Option[GravsearchTypeInspector] = if (inferTypes) {
    Some(
      new InferringGravsearchTypeInspector(
        nextInspector = None,
        appActor = appActor,
        responderData = responderData
      )
    )
  } else {
    None
  }

  // The pipeline of type inspectors.
  private val typeInspectionPipeline = new AnnotationReadingGravsearchTypeInspector(
    nextInspector = maybeInferringTypeInspector,
    responderData = responderData
  )

  /**
   * Given the WHERE clause from a parsed Gravsearch query, returns information about the types found
   * in the query.
   *
   * @param whereClause    the Gravsearch WHERE clause.
   * @param requestingUser the requesting user.
   * @return the result of the type inspection.
   */
  def inspectTypes(whereClause: WhereClause, requestingUser: UserADM): Future[GravsearchTypeInspectionResult] = {
    implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    for {
      // Get the set of typeable entities in the Gravsearch query.
      typeableEntities: Set[TypeableEntity] <- Future {
                                                 QueryTraverser.visitWherePatterns(
                                                   patterns = whereClause.patterns,
                                                   whereVisitor = new TypeableEntityCollectingWhereVisitor,
                                                   initialAcc = Set.empty[TypeableEntity]
                                                 )
                                               }

      // In the initial intermediate result, none of the entities have types yet.
      initialResult: IntermediateTypeInspectionResult = IntermediateTypeInspectionResult(typeableEntities)

      // Run the pipeline and get its result.
      lastResult: IntermediateTypeInspectionResult <- typeInspectionPipeline.inspectTypes(
                                                        previousResult = initialResult,
                                                        whereClause = whereClause,
                                                        requestingUser = requestingUser
                                                      )

      // Are any entities still untyped?
      untypedEntities: Set[TypeableEntity] = lastResult.untypedEntities

      _ =
        if (untypedEntities.nonEmpty) {
          //  Yes. Return an error.
          throw GravsearchException(
            s"Types could not be determined for one or more entities: ${untypedEntities.mkString(", ")}"
          )
        } else {
          // No. Are there any entities with multiple types?
          val inconsistentEntities: Map[TypeableEntity, Set[GravsearchEntityTypeInfo]] =
            lastResult.entitiesWithInconsistentTypes

          if (inconsistentEntities.nonEmpty) {
            // Yes. Return an error.

            val inconsistentStr: String = inconsistentEntities.map { case (entity, entityTypes) =>
              s"$entity ${entityTypes.mkString(" ; ")} ."
            }
              .mkString(" ")

            throw GravsearchException(s"One or more entities have inconsistent types: $inconsistentStr")
          }
        }
    } yield lastResult.toFinalResult
  }

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
