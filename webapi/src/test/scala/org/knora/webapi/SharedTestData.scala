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

package org.knora.webapi

import org.knora.webapi.messages.v1respondermessages.usermessages.{UserDataV1, UserProfileV1}

/**
  * Created by subotic on 01.04.16.
  */
object SharedTestData {

    /* represents the user profile of 'root' as found in admin-data.ttl */
    val rootUserProfileV1 = UserProfileV1(
        UserDataV1(
            user_id = Some("http://data.knora.org/users/root"),
            username = Some("root"),
            firstname = Some("System"),
            lastname = Some("Administrator"),
            email = Some("root@example.com"),
            hashedpassword = Some("$2a$10$fTEr/xVjPq7UBAy1O6KWKOM1scLhKGeRQdR4GTA997QPqHzXv0MnW"), // -> "test"
            token = None,
            isActiveUser = Some(true),
            isSystemAdmin = Some(true),
            lang = "de"
        ),
        Vector.empty[IRI],
        Vector.empty[IRI],
        Vector.empty[IRI],
        Vector.empty[IRI]
    )

    /* represents the user profile of 'superuser' as found in admin-data.ttl */
    val superuserUserProfileV1 = UserProfileV1(
        UserDataV1(
            user_id = Some("http://data.knora.org/users/superuser"),
            username = Some("superuser"),
            firstname = Some("Super"),
            lastname = Some("User"),
            email = Some("super.user@example.com"),
            hashedpassword = Some("$2a$10$fTEr/xVjPq7UBAy1O6KWKOM1scLhKGeRQdR4GTA997QPqHzXv0MnW"), // -> "test"
            token = None,
            isActiveUser = Some(true),
            isSystemAdmin = Some(true),
            lang = "de"
        ),
        Vector.empty[IRI],
        Vector.empty[IRI],
        Vector.empty[IRI],
        Vector.empty[IRI]
    )

    /* represents the user profile of 'superuser' as found in admin-data.ttl */
    val normaluserUserProfileV1 = UserProfileV1(
        UserDataV1(
            user_id = Some("http://data.knora.org/users/normaluser"),
            username = Some("normaluser"),
            firstname = Some("Normal"),
            lastname = Some("User"),
            email = Some("normal.user@example.com"),
            hashedpassword = Some("$2a$10$fTEr/xVjPq7UBAy1O6KWKOM1scLhKGeRQdR4GTA997QPqHzXv0MnW"), // -> "test"
            token = None,
            isActiveUser = Some(true),
            isSystemAdmin = Some(false),
            lang = "de"
        ),
        Vector.empty[IRI],
        Vector.empty[IRI],
        Vector.empty[IRI],
        Vector.empty[IRI]
    )

    /* represents an anonymous user */
    val anonymousUserProfileV1 = UserProfileV1(
        UserDataV1(
            lang = "de"
        ),
        Vector.empty[IRI],
        Vector.empty[IRI],
        Vector.empty[IRI],
        Vector.empty[IRI]
    )


}
