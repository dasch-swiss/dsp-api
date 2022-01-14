/*
 * Copyright © 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.admin.responder.listsmessages

object ListsErrorMessagesADM {
  val LIST_IRI_MISSING_ERROR = "List IRI cannot be empty."
  val LIST_IRI_INVALID_ERROR = "List IRI cannot be empty."
  val LIST_NODE_IRI_MISSING_ERROR = "List node IRI cannot be empty."
  val LIST_NODE_IRI_INVALID_ERROR = "List node IRI is invalid."
  val LIST_NAME_MISSING_ERROR = "List name cannot be empty."
  val LIST_NAME_INVALID_ERROR = "List name is invalid."
  val LABEL_MISSING_ERROR = "At least one label needs to be supplied."
  val LABEL_INVALID_ERROR = "Invalid label."
  val COMMENT_MISSING_ERROR = "At least one comment needs to be supplied."
  val COMMENT_INVALID_ERROR = "Invalid comment."
  val LIST_CREATE_PERMISSION_ERROR = "A list can only be created by the project or system administrator."
  val LIST_NODE_CREATE_PERMISSION_ERROR = "A list node can only be created by the project or system administrator."
  val LIST_CHANGE_PERMISSION_ERROR = "A list can only be changed by the project or system administrator."
  val UPDATE_REQUEST_EMPTY_LABEL_ERROR = "List labels cannot be empty."
  val INVALID_POSITION = "Invalid position value is given, position should be either a positive value or -1."
}
