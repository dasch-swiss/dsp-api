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

package org.knora.webapi.messages.v2.responder.usermessages

import org.knora.webapi.messages.v1.responder.usermessages.{UserDataV1, UserProfileV1}
import org.knora.webapi.messages.v2.responder.{KnoraExternalRequestV2, KnoraExternalResponseV2, KnoraInternalRequestV2, KnoraInternalResponseV2}


////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Request Messages

/**
  * An abstract trait representing external messages that can be sent to `UsersResponderV2`.
  */
sealed trait UsersResponderExternalRequestV2 extends KnoraExternalRequestV2

/**
  * An abstract trait representing external messages that can be sent to `UsersResponderV2`.
  */
sealed trait UsersResponderInternalRequestV2 extends KnoraInternalRequestV2


/**
  * Get all information about all users in form of [[UsersGetExtRespV2]]. The UsersGetExtReqV2 returns either
  * something or a NotFound exception if there are no users found. Administration permission checking is performed.
  *
  * @param userProfileV2 the profile of the user that is making the request.
  */
case class UsersGetExtReqV2(userProfileV2: UserProfileV2) extends UsersResponderExternalRequestV2


/**
  * Get all information about all users in form of a sequence of [[UserDataV1]]. Returns an empty sequence if
  * no users are found. Administration permission checking is skipped.
  *
  */
case class UsersGetIntReqV2() extends UsersResponderInternalRequestV2


////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Response Responses

/**
  * Represents an answer to an external request for a list of all users.
  *
  * @param users a sequence of user profiles of the requested type.
  */
case class UsersGetExtRespV2(users: Seq[UserDataV2]) extends KnoraExternalResponseV2 {
    def toJsValue = ???
}

case class UsersGetIntRespV2(users: Seq[UserDataV2]) extends KnoraInternalResponseV2


////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Components of messages

case class UserDataV2()

case class UserProfileV2()