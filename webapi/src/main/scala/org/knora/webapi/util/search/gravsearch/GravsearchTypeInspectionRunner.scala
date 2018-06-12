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

package org.knora.webapi.util.search.gravsearch

import akka.actor.ActorSystem
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.util.search._
import org.knora.webapi.{GravsearchException, OntologyConstants}

import scala.concurrent.{ExecutionContextExecutor, Future}

/**
  * Runs Gravsearch type inspection using one or more type inspector implementations.
  *
  * @param system     the Akka actor system.
  * @param inferTypes if true, use type inference.
  */
class GravsearchTypeInspectionRunner(val system: ActorSystem,
                                     inferTypes: Boolean = true) {
    private implicit val executionContext: ExecutionContextExecutor = system.dispatcher

    // If inference was requested, construct an inferring type inspector.
    private val maybeInferringTypeInspector: Option[GravsearchTypeInspector] = if (inferTypes) {
        Some(
            new InferringGravsearchTypeInspector(
                nextInspector = None,
                system = system
            )
        )
    } else {
        None
    }

    // The pipeline of type inspectors.
    private val typeInspectionPipeline = new AnnotationReadingGravsearchTypeInspector(
        nextInspector = maybeInferringTypeInspector,
        system = system
    )

    /**
      * Given the WHERE clause from a parsed Gravsearch query, returns information about the types found
      * in the query.
      *
      * @param whereClause    the Gravsearch WHERE clause.
      * @param requestingUser the requesting user.
      * @return the result of the type inspection.
      */
    def inspectTypes(whereClause: WhereClause,
                     requestingUser: UserADM): Future[GravsearchTypeInspectionResult] = {
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

            _ = if (untypedEntities.nonEmpty) {
                //  Yes. Return an error.
                throw GravsearchException(s"Types could not be determined for one or more entities: ${untypedEntities.mkString(", ")}")
            } else {
                // No. Are there any entities with multiple types?
                val inconsistentEntities: Map[TypeableEntity, Set[GravsearchEntityTypeInfo]] = lastResult.entitiesWithInconsistentTypes

                if (inconsistentEntities.nonEmpty) {
                    // Yes. Return an error.

                    val inconsistentStr: String = inconsistentEntities.map {
                        case (entity, entityTypes) =>
                            s"$entity ${entityTypes.mkString(" ; ")} ."
                    }.mkString(" ")

                    throw GravsearchException(s"One or more entities have inconsistent types: $inconsistentStr")
                }
            }
        } yield lastResult.toFinalResult
    }


    /**
      * A [[WhereVisitor]] that collects typeable entities from a Gravsearch WHERE clause.
      */
    private class TypeableEntityCollectingWhereVisitor extends WhereVisitor[Set[TypeableEntity]] {
        override def visitStatementInWhere(statementPattern: StatementPattern, acc: Set[TypeableEntity]): Set[TypeableEntity] = {
            statementPattern.pred match {
                case iriRef: IriRef if iriRef.iri.toString == OntologyConstants.Rdf.Type =>
                    // If the predicate is rdf:type, only the subject can be typeable.
                    acc ++ GravsearchTypeInspectionUtil.toTypeableEntities(Seq(statementPattern.subj))

                case _ =>
                    // Otherwise, the subject, the predicate, and the object could all be typeable.
                    acc ++ GravsearchTypeInspectionUtil.toTypeableEntities(Seq(statementPattern.subj, statementPattern.pred, statementPattern.obj))
            }
        }

        override def visitFilter(filterPattern: FilterPattern, acc: Set[TypeableEntity]): Set[TypeableEntity] = acc
    }

}
