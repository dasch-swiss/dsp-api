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

import akka.actor.ActorSelection
import org.knora.webapi.GravsearchException
import org.knora.webapi.util.search._
import org.knora.webapi.util.search.gravsearch.TypeInspectionUtil.IntermediateTypeInspectionResult

import scala.concurrent.{ExecutionContext, Future}

/**
  * Runs Gravsearch type inspection using one or more type inspector implementations.
  *
  * @param responderManager the Knora API responder manager.
  * @param inferTypes       if true, use type inference.
  */
class TypeInspectionRunner(val responderManager: ActorSelection,
                           inferTypes: Boolean = true)
                          (implicit val executionContext: ExecutionContext) {
    private val maybeInferringTypeInspector: Option[TypeInspector] = if (inferTypes) {
        Some(
            new InferringTypeInspector(
                nextInspector = None,
                responderManager = responderManager
            )
        )
    } else {
        None
    }

    private val typeInspectors = new ExplicitTypeInspector(
        nextInspector = maybeInferringTypeInspector,
        responderManager = responderManager
    )

    /**
      * Given the WHERE clause from a parsed Gravsearch query, returns information about the types found
      * in the query.
      *
      * @param whereClause the Gravsearch WHERE clause.
      * @return the result of the type inspection.
      */
    def inspectTypes(whereClause: WhereClause): Future[TypeInspectionResult] = {
        for {
            typeableEntities: collection.mutable.Set[TypeableEntity] <- Future(collection.mutable.Set(TypeInspectionUtil.getTypableEntitiesFromPatterns(whereClause.patterns).toSeq: _*))

            initialResult = IntermediateTypeInspectionResult(
                typedEntities = collection.mutable.Map.empty[TypeableEntity, GravsearchEntityTypeInfo],
                untypedEntities = typeableEntities
            )

            lastResult <- typeInspectors.inspectTypes(
                previousResult = initialResult,
                whereClause = whereClause
            )

            _ = if (lastResult.untypedEntities.nonEmpty) {
                throw GravsearchException(s"The types of one or more entities could not be determined: ${lastResult.untypedEntities.mkString(", ")}")
            }
        } yield TypeInspectionResult(lastResult.typedEntities.toMap)
    }


    /**
      * Removes type annotations from a Gravsearch WHERE clause.
      *
      * @param whereClause the WHERE clause to be filtered.
      * @return the same WHERE clause, minus any type annotations.
      */
    def removeTypeAnnotations(whereClause: WhereClause): WhereClause = {
        whereClause.copy(
            patterns = TypeInspectionUtil.removeTypeAnnotationsFromPatterns(whereClause.patterns)
        )
    }

}
