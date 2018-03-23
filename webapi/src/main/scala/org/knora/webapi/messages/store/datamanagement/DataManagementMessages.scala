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


//********************************
// Requests
//********************************

/**
  * Checks to see if a data upgrade is necessary. Response is in the form of [[DataUpgradeCheckResult]]
  *
  * @param requestingUser the user making the request. Needs to be knora-admin:SystemUser.
  */
case class DataUpgradeCheck(requestingUser: UserADM) extends DataUpgradeRequest

/**
  * Initializes the data upgrade process. Response is in the form of [[DataUpgradeInitResult]].
  *
  * @param liveMode       denotes the mode in which to run. Live = false, no data will be changed. Live = true, change data.
  * @param requestingUser the user making the request. Needs to be knora-admin:SystemUser.
  */
case class DataUpgradeInit(liveMode: Boolean = false, requestingUser: UserADM) extends DataUpgradeRequest


//********************************
// Responses
//********************************

/**
  * Represents an response to [[DataUpgradeCheck]].
  * @param currentDataVersion
  * @param requiredDataVersion
  * @param dataUpgradeRequired
  */
case class DataUpgradeCheckResult(currentDataVersion: String, requiredDataVersion: String, dataUpgradeRequired: Boolean)

/**
  * Represents an response to [[DataUpgradeInit]].
  *
  * @param result
  */
case class DataUpgradeInitResult(result: Boolean)

