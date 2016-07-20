/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and André Fatton.
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

package org.knora.webapi.responders.v1

import akka.actor.Status
import org.knora.webapi._
import org.knora.webapi.messages.v1.responder.permissionmessages.{AdministrativePermissionV1, _}
import org.knora.webapi.messages.v1.responder.usermessages._
import org.knora.webapi.util.ActorUtil._
import org.knora.webapi.util.KnoraIriUtil

import scala.concurrent.Future


/**
  * Provides information about Knora users to other responders.
  */
class PermissionsResponderV1 extends ResponderV1 {

    // Creates IRIs for new Knora user objects.
    val knoraIriUtil = new KnoraIriUtil

    /**
      * Receives a message extending [[org.knora.webapi.messages.v1.responder.permissionmessages.PermissionsResponderRequestV1]].
      * If a serious error occurs (i.e. an error that isn't the client's fault), this
      * method first returns `Failure` to the sender, then throws an exception.
      */
    def receive = {
        case GetGroupAdministrativePermissionV1(projectIri, groupIri, userProfileV1) => future2Message(sender(), getGroupAdministrativePermissionV1(projectIri, groupIri, userProfileV1), log)
        case GetGroupDefaultObjectAccessPermissionsV1(projectIri, groupIri, userProfileV1) => future2Message(sender(), getGroupDefaultObjectAccessPermissionsV1(projectIri, groupIri, userProfileV1), log)
        case other => sender ! Status.Failure(UnexpectedMessageException(s"Unexpected message $other of type ${other.getClass.getCanonicalName}"))
    }


    /**
      * Gets the [[AdministrativePermissionV1]] object for a group inside a project.
      *
      * @param forProject the IRI of the project.
      * @param forGroup the IRI of the group.
      * @param userProfileV1 the [[UserProfileV1]] of the requesting user.
      *
      * @return a single [[AdministrativePermissionV1]] object.
      */
    private def getGroupAdministrativePermissionV1(forProject: IRI, forGroup: IRI, userProfileV1: UserProfileV1): Future[AdministrativePermissionV1] = {

        Future(AdministrativePermissionV1())
    }

    /**
      * Gets the [[DefaultObjectAccessPermissionV1]] objects for a group inside a project.
      *
      * @param forProject the IRI of the project.
      * @param forGroup the IRI of the group.
      * @param userProfileV1 the [[UserProfileV1]] of the requesting user.
      * @return a list of [[DefaultObjectAccessPermissionV1]] objects.
      */
    private def getGroupDefaultObjectAccessPermissionsV1(forProject: IRI, forGroup: IRI, userProfileV1: UserProfileV1): Future[List[DefaultObjectAccessPermissionV1]] = {

        Future(List(DefaultObjectAccessPermissionV1()))
    }
}
