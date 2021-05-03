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

package org.knora.webapi.store.eventstore

import org.knora.webapi.IRI

sealed trait ResourceEvent {
  val iri: IRI
}

case class ResourceCreated(iri: IRI, event: String) extends ResourceEvent

/*
  object ResourceCreated {
    implicit val decoder: JsonDecoder[ResourceCreated] = DeriveJsonDecoder.gen[ResourceCreated]
    implicit val encoder: JsonEncoder[ResourceCreated] = DeriveJsonEncoder.gen[ResourceCreated]
  }
 */
