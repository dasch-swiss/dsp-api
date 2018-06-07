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
import org.knora.webapi.util.search._
import org.knora.webapi.util.search.gravsearch.TypeInspectionUtil.IntermediateTypeInspectionResult

import scala.concurrent.{ExecutionContext, Future}

/**
  * An trait whose implementations can get type information from a parsed Gravsearch query in different ways.
  * Type inspectors are run in a pipeline.
  *
  * @param nextInspector    the next type inspector in the pipeline.
  * @param responderManager the Knora API responder manager.
  */
abstract class TypeInspector(protected val nextInspector: Option[TypeInspector],
                             protected val responderManager: ActorSelection)
                            (implicit protected val executionContext: ExecutionContext) {
    /**
      * Given the WHERE clause from a parsed Gravsearch query, returns information about the types found
      * in the query. Each implementation must end by calling `runNextInspector`.
      *
      * @param whereClause the Gravsearch WHERE clause.
      * @return the result of the type inspection.
      */
    def inspectTypes(previousResult: IntermediateTypeInspectionResult,
                     whereClause: WhereClause): Future[IntermediateTypeInspectionResult]

    /**
      * Runs the next type inspector, if any, in a pipeline of type inspectors.
      *
      * @param intermediateResult the intermediate result produced by this type inspector.
      * @param whereClause        the Gravsearch WHERE clause.
      * @return the result returned by the next type inspector.
      */
    protected def runNextInspector(intermediateResult: IntermediateTypeInspectionResult,
                                   whereClause: WhereClause): Future[IntermediateTypeInspectionResult] = {
        nextInspector match {
            case Some(next) =>
                next.inspectTypes(
                    previousResult = intermediateResult,
                    whereClause = whereClause
                )

            case None => Future(intermediateResult)
        }
    }
}
