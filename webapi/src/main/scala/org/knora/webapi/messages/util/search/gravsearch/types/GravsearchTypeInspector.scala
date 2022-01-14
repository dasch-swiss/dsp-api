/*
 * Copyright © 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.util.search.gravsearch.types

import akka.actor.ActorSystem
import akka.util.Timeout
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.util.ResponderData
import org.knora.webapi.messages.util.search.WhereClause
import org.knora.webapi.settings.{KnoraDispatchers, KnoraSettings, KnoraSettingsImpl}

import scala.concurrent.{ExecutionContext, Future}

/**
 * An trait whose implementations can get type information from a parsed Gravsearch query in different ways.
 * Type inspectors are run in a pipeline. Each inspector tries to determine the types of all the typeable
 * entities in the WHERE clause of a Gravsearch query, then runs the next inspector in the pipeline.
 *
 * @param nextInspector the next type inspector in the pipeline, or `None` if this is the last one.
 * @param responderData the Knora responder data.
 */
abstract class GravsearchTypeInspector(
  protected val nextInspector: Option[GravsearchTypeInspector],
  responderData: ResponderData
) {

  protected val system: ActorSystem = responderData.system
  protected val settings: KnoraSettingsImpl = KnoraSettings(system)
  protected implicit val executionContext: ExecutionContext =
    system.dispatchers.lookup(KnoraDispatchers.KnoraActorDispatcher)
  protected implicit val timeout: Timeout = settings.defaultTimeout

  /**
   * Given the WHERE clause from a parsed Gravsearch query, returns information about the types found
   * in the query. Each implementation must end by calling `runNextInspector`.
   *
   * @param previousResult the result of previous type inspection.
   * @param whereClause    the Gravsearch WHERE clause.
   * @param requestingUser the requesting user.
   * @return the result returned by the pipeline.
   */
  def inspectTypes(
    previousResult: IntermediateTypeInspectionResult,
    whereClause: WhereClause,
    requestingUser: UserADM
  ): Future[IntermediateTypeInspectionResult]

  /**
   * Runs the next type inspector in the pipeline.
   *
   * @param intermediateResult the intermediate result produced by this type inspector.
   * @param whereClause        the Gravsearch WHERE clause.
   * @return the result returned by the pipeline.
   */
  protected def runNextInspector(
    intermediateResult: IntermediateTypeInspectionResult,
    whereClause: WhereClause,
    requestingUser: UserADM
  ): Future[IntermediateTypeInspectionResult] =
    // Is there another inspector in the pipeline?
    nextInspector match {
      case Some(next) =>
        // Yes. Run that inspector.
        next.inspectTypes(
          previousResult = intermediateResult,
          whereClause = whereClause,
          requestingUser = requestingUser
        )

      case None =>
        // There are no more inspectors. Return the result we have.
        Future(intermediateResult)
    }
}
