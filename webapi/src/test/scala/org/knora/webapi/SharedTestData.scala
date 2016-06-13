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

import org.knora.webapi.messages.v1.responder.groupmessages.GroupInfoV1
import org.knora.webapi.messages.v1.responder.usermessages.{UserDataV1, UserProfileV1}

/**
  * This object holds the same user which are loaded with '_test_data/all_data/admin-data.ttl'. Using this object
  * in tests, allows easier updating of user details as they change over time.
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
        groups = Vector.empty[IRI],
        projects = List("http://data.knora.org/projects/77275339", "http://data.knora.org/projects/images"),
        isGroupAdminFor = Vector.empty[IRI],
        isProjectAdminFor = Vector.empty[IRI],
        sessionId = None
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
        groups = Vector.empty[IRI],
        projects = Vector.empty[IRI],
        isGroupAdminFor = Vector.empty[IRI],
        isProjectAdminFor = Vector.empty[IRI],
        sessionId = None
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
        groups = Vector.empty[IRI],
        projects = Vector.empty[IRI],
        isGroupAdminFor = Vector.empty[IRI],
        isProjectAdminFor = Vector.empty[IRI],
        sessionId = None
    )

    /* represents an anonymous user */
    val anonymousUserProfileV1 = UserProfileV1(
        UserDataV1(
            lang = "de"
        ),
        groups = Vector.empty[IRI],
        projects = Vector.empty[IRI],
        isGroupAdminFor = Vector.empty[IRI],
        isProjectAdminFor = Vector.empty[IRI],
        sessionId = None
    )

    /* represents 'user01' as found in admin-data.ttl  */
    val user01UserProfileV1 = UserProfileV1(
        userData = UserDataV1(
            user_id = Some("http://data.knora.org/users/c266a56709"),
            username = Some("user01"),
            firstname = Some("User01"),
            lastname = Some("User"),
            email = Some("user01.user1@example.com"),
            hashedpassword = Some("$2a$10$fTEr/xVjPq7UBAy1O6KWKOM1scLhKGeRQdR4GTA997QPqHzXv0MnW"), // -> "test"
            token = None,
            isActiveUser = Some(true),
            isSystemAdmin = Some(false),
            lang = "de"
        ),
        groups = List("http://data.knora.org/groups/imgcontri"),
        projects = List("http://data.knora.org/projects/images"),
        isGroupAdminFor = List("http://data.knora.org/groups/imgcontri"),
        isProjectAdminFor = List("http://data.knora.org/projects/images"),
        sessionId = None
    )

    /* represents 'user02' as found in admin-data.ttl  */
    val user02UserProfileV1 = UserProfileV1(
        userData = UserDataV1(
            user_id = Some("http://data.knora.org/users/97cec4000f"),
            username = Some("user02"),
            firstname = Some("User02"),
            lastname = Some("User"),
            email = Some("user02.user@example.com"),
            hashedpassword = Some("$2a$10$fTEr/xVjPq7UBAy1O6KWKOM1scLhKGeRQdR4GTA997QPqHzXv0MnW"), // -> "test"
            token = None,
            isActiveUser = Some(true),
            isSystemAdmin = Some(false),
            lang = "de"
        ),
        groups = List("http://data.knora.org/groups/imgcontri"),
        projects = List("http://data.knora.org/projects/images"),
        isGroupAdminFor = Vector.empty[IRI],
        isProjectAdminFor = Vector.empty[IRI],
        sessionId = None
    )

    /* represents 'testuser' as found in admin-data.ttl  */
    val testuserUserProfileV1 = UserProfileV1(
        userData = UserDataV1(
            user_id = Some("http://data.knora.org/users/b83acc5f05")    ,
            username = Some("testuser"),
            firstname = Some("User"),
            lastname = Some("Test"),
            email = Some("user.test@example.com"),
            hashedpassword = Some("$2a$10$fTEr/xVjPq7UBAy1O6KWKOM1scLhKGeRQdR4GTA997QPqHzXv0MnW"), // -> "test"
            token = None,
            isActiveUser = Some(true),
            isSystemAdmin = Some(false),
            lang = "de"
        ),
        groups = Vector.empty[IRI],
        projects = List("http://data.knora.org/projects/77275339"),
        isGroupAdminFor = Vector.empty[IRI],
        isProjectAdminFor = Vector.empty[IRI],
        sessionId = None

    )

    val imgcontriGroupInfoV1 = GroupInfoV1(
        id = "http://data.knora.org/groups/imgcontri",
        name = "Images-Demo-Project External Contributors",
        description = "This group contains contributing users external to the Image-Collection-Demo project",
        isActiveGroup = true
    )
}
