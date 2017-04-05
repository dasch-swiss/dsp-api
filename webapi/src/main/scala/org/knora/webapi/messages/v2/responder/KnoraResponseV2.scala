/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and Sepideh Alassi.
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

package org.knora.webapi.messages.v2.responder

import org.knora.webapi.Jsonable

/**
  * A trait for Knora API v2 external response messages, i.e. messages leaving the server. Any external response message
  * can be converted into JSON.
  */
trait KnoraExternalResponseV2 extends Jsonable

/**
  * A trait for Knora API v2 internal response messages, i.e. messages never leaving the server. A internal response
  * message does not need to be converted into JSON.
  */
trait KnoraInternalResponseV2