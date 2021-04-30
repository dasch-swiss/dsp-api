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
import org.knora.webapi.messages.store.StoreRequest

sealed trait EventStoreRequest extends StoreRequest

/**
  * Message requesting to write a resource event.
  * @param event the event to be stored.
  */
case class EventStoreSaveResourceEventRequest(event: ResourceEvent) extends EventStoreRequest

/**
  * Message requesting to retrieve all events for a resource.
  * @param resourceIri the IRI of the resource.
  */
case class EventStoreGetResourceEventsRequest(resourceIri: IRI) extends EventStoreRequest
