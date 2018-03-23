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

package org.knora.webapi.store.datamanagement

import akka.actor.{Actor, ActorLogging, Props}
import akka.event.LoggingReceive
import org.knora.webapi.messages.store.datamanagement.{DataBackupRequest, DataUpgradeRequest, RepositoryInitRequest}
import org.knora.webapi.store._
import org.knora.webapi.{ActorMaker, Settings}

/**
  * This actor receives messages representing SPARQL requests, and forwards them to instances of one of the configured triple stores (embedded or remote).
  */
class DataManager extends Actor with ActorLogging {
    this: ActorMaker =>

    private val settings = Settings(context.system)

    implicit val timeout = settings.defaultRestoreTimeout

    val repositoryInitActor = makeActor(Props[RepositoryInitializationActor], name = REPOSITORY_INIT_ACTOR_NAME)
    val dataBackupActor = makeActor(Props[DataBackupActor], name = DATA_BACKUP_ACTOR_NAME)
    val dataUpgradeActor = makeActor(Props[DataUpgradeActor], name = DATA_UPGRADE_ACTOR_NAME)

    def receive = LoggingReceive {
        case msg: RepositoryInitRequest => repositoryInitActor forward msg
        case msg: DataBackupRequest => dataBackupActor forward msg
        case msg: DataUpgradeRequest ⇒ dataUpgradeActor forward msg
    }
}
