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
import org.knora.webapi.GravsearchException
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.util.search._
import org.knora.webapi.util.search.gravsearch.GravsearchTypeInspectionUtil.IntermediateTypeInspectionResult

import scala.concurrent.{ExecutionContext, Future}

/**
  * Runs Gravsearch type inspection using one or more type inspector implementations.
  *
  * @param system     the Akka actor system.
  * @param inferTypes if true, use type inference.
  */
class GravsearchTypeInspectionRunner(val system: ActorSystem,
                                     inferTypes: Boolean = true)
                                    (implicit val executionContext: ExecutionContext) {
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
    private val typeInspectionPipeline = new ExplicitGravsearchTypeInspector(
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
            typeableEntities: Set[TypeableEntity] <- Future(GravsearchTypeInspectionUtil.getTypableEntitiesFromPatterns(GravsearchTypeInspectionUtil.flattenPatterns(whereClause)))

            // In the initial intermediate result, none of the entities have types yet.
            initialResult = IntermediateTypeInspectionResult(typeableEntities)

            // Run the pipeline and get its result.
            lastResult <- typeInspectionPipeline.inspectTypes(
                previousResult = initialResult,
                whereClause = whereClause,
                requestingUser = requestingUser
            )

            // Are any entities still untyped?
            untypedEntities = lastResult.untypedEntities

            _ = if (untypedEntities.nonEmpty) {
                //  Yes. Return an error.
                throw GravsearchException(s"Types could not be determined for one or more entities: ${untypedEntities.mkString(", ")}")
            } else {
                // No. Are there any entities with multiple types?
                val inconsistentEntities = lastResult.entitiesWithInconsistentTypes

                if (inconsistentEntities.nonEmpty) {
                    // Yes. Return an error.

                    val inconsistentStr = inconsistentEntities.map {
                        case (entity, entityTypes) =>
                            s"$entity ${entityTypes.mkString(" ; ")} ."
                    }.mkString(" ")

                    throw GravsearchException(s"One or more entities have inconsistent types: $inconsistentStr")
                }
            }
        } yield lastResult.toFinalResult
    }
}
