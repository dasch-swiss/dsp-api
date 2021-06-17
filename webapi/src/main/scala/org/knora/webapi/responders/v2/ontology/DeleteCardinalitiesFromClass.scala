/*
 * Copyright © 2015-2021 the contributors (see Contributors.md).
 *
 *  This file is part of the DaSCH Service Platform.
 *
 *  The DaSCH Service Platform  is free software: you can redistribute it
 *  and/or modify it under the terms of the GNU Affero General Public
 *  License as published by the Free Software Foundation, either version 3
 *  of the License, or (at your option) any later version.
 *
 *  The DaSCH Service Platform is distributed in the hope that it will be
 *  useful, but WITHOUT ANY WARRANTY; without even the implied warranty
 *  of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public
 *  License along with the DaSCH Service Platform.  If not, see
 *  <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.responders.v2.ontology

import org.knora.webapi.InternalSchema
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.v2.responder.ontologymessages.{
  ClassInfoContentV2,
  DeleteCardinalitiesFromClassRequestV2,
  ReadOntologyV2
}

import scala.concurrent.{ExecutionContext, Future}

/**
  * Contains methods used for deleting cardinalities from a class
  */
object DeleteCardinalitiesFromClass {

  /**
    *
    * @param internalClassIri
    * @param internalOntologyIri
    * @return a [[ReadOntologyV2]] in the internal schema, containing the new class definition.
    */
  def deleteCardinalitiesFromClassTaskFuture(
      deleteCardinalitiesFromClassRequest: DeleteCardinalitiesFromClassRequestV2,
      internalClassIri: SmartIri,
      internalOntologyIri: SmartIri
  )(implicit ec: ExecutionContext): Future[ReadOntologyV2] = {
    for {
      cacheData <- Cache.getCacheData
      internalClassDef: ClassInfoContentV2 = deleteCardinalitiesFromClassRequest.classInfoContent.toOntologySchema(
        InternalSchema)

    } yield ???
  }
}
