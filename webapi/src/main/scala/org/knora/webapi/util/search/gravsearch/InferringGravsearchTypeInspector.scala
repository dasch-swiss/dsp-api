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
import org.knora.webapi.util.search.WhereClause
import org.knora.webapi.util.search.gravsearch.GravsearchTypeInspectionUtil.IntermediateTypeInspectionResult

import scala.concurrent.{ExecutionContext, Future}

/**
  * A Gravsearch type inspector that infers types, relying on information from the relevant ontologies.
  *
  * @param nextInspector    the next type inspector in the pipeline.
  * @param responderManager the Knora API responder manager.
  */
class InferringGravsearchTypeInspector(nextInspector: Option[GravsearchTypeInspector],
                                       responderManager: ActorSelection)
                                      (implicit executionContext: ExecutionContext) extends GravsearchTypeInspector(nextInspector = nextInspector, responderManager = responderManager) {

    override def inspectTypes(previousResult: IntermediateTypeInspectionResult, whereClause: WhereClause): Future[IntermediateTypeInspectionResult] = {
        val intermediateResult = previousResult // TODO

        for {
            result <- runNextInspector(
                intermediateResult = intermediateResult,
                whereClause = whereClause
            )
        } yield result
    }

}
