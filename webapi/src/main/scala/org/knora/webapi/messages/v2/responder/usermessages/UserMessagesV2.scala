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

import org.knora.webapi.IRI
import org.knora.webapi.messages.v1.responder.KnoraResponseV1
import org.knora.webapi.messages.v1.responder.usermessages.UserProfileTypeV1.UserProfileTypeV1
import org.knora.webapi.messages.v1.responder.usermessages._
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


/**
  * A message that requests a user's profile. A successful response will be a [[UserProfileResponseV1]].
  *
  * @param userIri         the IRI of the user to be queried.
  * @param userProfileType the extent of the information returned.
  */
case class UserByIRIGetExtReqV2(userIri: IRI,
                                userProfileType: UserProfileTypeV1,
                                userProfileV2: UserProfileV2) extends UsersResponderExternalRequestV2


/**
  * A message that requests a user's profile. A successful response will be a [[UserProfileV1]].
  *
  * @param userIri         the IRI of the user to be queried.
  * @param userProfileType the extent of the information returned.
  */
case class UserByIRIGetIntReqV1(userIri: IRI,
                                userProfileType: UserProfileTypeV1) extends UsersResponderInternalRequestV2

/**
  * A message that requests a user's profile. A successful response will be a [[UserProfileExtRespV2]].
  *
  * @param email           the email of the user to be queried.
  * @param userProfileType the extent of the information returned.
  * @param userProfileV2   the requesting user's profile.
  */
case class UserProfileByEmailGetExtReqV2(email: String,
                                         userProfileType: UserProfileTypeV1,
                                         userProfileV2: UserProfileV2) extends UsersResponderExternalRequestV2


/**
  * A message that requests a user's profile. A successful response will be a [[UserProfileIntRespV2]].
  *
  * @param email           the email of the user to be queried.
  * @param userProfileType the extent of the information returned.
  */
case class UserProfileByEmailGetIntReqV2(email: String,
                                         userProfileType: UserProfileTypeV1) extends UsersResponderInternalRequestV2



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


/**
  * Represents an answer to an external user profile request.
  *
  * @param userProfileV2 the user's profile of the requested type.
  */
case class UserProfileExtRespV2(userProfileV2: UserProfileV2) extends KnoraExternalResponseV2 {
    def toJsValue = ???
}

/**
  * Represents an answer to an internal user profile request.
  *
  * @param userProfileV2 the user's profile of the requested type.
  */
case class UserProfileIntRespV2(userProfileV2: Option[UserProfileV2]) extends KnoraInternalResponseV2

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Components of messages

case class UserDataV2()

case class UserProfileV2()