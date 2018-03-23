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

package org.knora.webapi.messages.store.datamanagement

import org.knora.webapi.messages.admin.responder.usersmessages.UserADM

sealed trait DataManagementRequest

/**
  * Groups message that deal with initial creation of a repository
  */
sealed trait RepositoryInitRequest extends DataManagementRequest

/**
  * Groups messages that deal with backup
  */
sealed trait DataBackupRequest extends DataManagementRequest

/**
  * Groups messages that deal with data upgrading
  */
sealed trait DataUpgradeRequest extends DataManagementRequest

/**
  * Initializes the data upgrade process
  */
case class DataUpgradeInit(requestingUser: UserADM) extends DataUpgradeRequest

/**
  * Represents the data upgrade process result
  * @param result
  */
case class DataUpgradeResult(result: Boolean)

