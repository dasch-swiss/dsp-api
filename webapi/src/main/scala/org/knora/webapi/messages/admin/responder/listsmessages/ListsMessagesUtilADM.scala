/*
 * Copyright © 2015-2018 the contributors (see Contributors.md).
 *
 *  This file is part of Knora.
 *
 *  Knora is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published
 *  by the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Knora is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public
 *  License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.messages.admin.responder.listsmessages

object ListsMessagesUtilADM {
  val LIST_IRI_MISSING_ERROR = "List IRI cannot be empty."
  val LIST_IRI_INVALID_ERROR = "List IRI cannot be empty."
  val LIST_NODE_IRI_MISSING_ERROR = "List node IRI cannot be empty."
  val LIST_NODE_IRI_INVALID_ERROR = "List node IRI is invalid."
  val PROJECT_IRI_MISSING_ERROR = "Project IRI cannot be empty."
  val PROJECT_IRI_INVALID_ERROR = "Project IRI is invalid."
  val LABEL_MISSING_ERROR = "At least one label needs to be supplied."
  val LIST_CREATE_PERMISSION_ERROR = "A list can only be created by the project or system administrator."
  val LIST_NODE_CREATE_PERMISSION_ERROR = "A list node can only be created by the project or system administrator."
  val LIST_CHANGE_PERMISSION_ERROR = "A list can only be changed by the project or system administrator."
  val UPDATE_REQUEST_EMPTY_LABEL_ERROR = "List labels cannot be empty."
  val INVALID_POSITION = "Invalid position value is given, position should be either a positive value or -1."
}
