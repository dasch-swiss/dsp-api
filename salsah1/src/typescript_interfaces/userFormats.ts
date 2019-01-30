/*
 * Copyright © 2015-2019 the contributors (see Contributors.md).
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

import {basicMessageComponents} from "./basicMessageComponents"
import {permissionFormats} from "./permissionFormats"
import {projectFormats} from "./projectFormats"


/**
 * This module contains interfaces that represent requests and responses for user administration
 */
export module userFormats {

    import PermissionDataV1 = permissionFormats.PermissionDataV1;
    import ProjectInfoV1 = projectFormats.ProjectInfoV1

    /**
     * Represents an API request payload sent during a user creation request.
     *
     * HTTP POST request to http://host/v1/users
     */
    export interface CreateUserApiRequestV1 {

        /**
         * The email of the user to be created. Needs to be unique, i.e. not possible to create two users with the same
         * email address.
         */
        email: string;

        /**
         * The given name of the user to be created.
         */
        givenName: string;

        /**
         * The family name of the user to be created.
         */
        familyName: string;

        /**
         * The password of the user to be created.
         */
        password: string;

        /**
         * The status of the user to be created (active = true, inactive = false).
         */
        status: boolean;

        /**
         * The default language of the user to be created (e.g., "en").
         */
        lang: string;

        /**
         * The system admin membership.
         */
        systemAdmin: boolean;
    }

    /**
     * Represents an API request payload sent during a user change request.
     *
     * HTTP PUT request to http://host/v1/users/userIRI
     *
     * There are four distinct use case / payload combination:
     *
     * 1. change password: oldPassword, newPassword
     * 2. change status: newUserStatus
     * 3. change system admin membership: newSystemAdminMembershipStatus
     * 4. change basic user information: email, givenName, familyName, lang
     *
     * It is not possible to mix cases, e.g., sending newUserStatus and basic user information at the same time will
     * result in an error.
     */
    export interface ChangeUserApiRequestV1 {

        /**
         * The new email address. Needs to be unique on the server.
         */
        email?: string;

        /**
         * The new given name.
         */
        givenName?: string;

        /**
         * The new family name.
         */
        familyName?: string;

        /**
         * The new ISO 639-1 code of the new preferred language.
         */
        lang?: string;

        /**
         * The old password.
         */
        oldPassword?: string;

        /**
         * The new password.
         */
        newPassword?: string;

        /**
         * The new user status (active = true, inactive = false).
         */
        status?: boolean;

        /**
         * The new system admin membership status.
         */
        systemAdmin?:boolean;
    }

    /**
     * Represents a response to a request for information about all users.
     *
     * HTTP GET request to http://host/v1/users
     *
     */
    export interface UsersGetResponseV1 extends basicMessageComponents.basicResponse {
        /**
         * The userProfiles.
         */
        users:Array<UserDataV1>;
    }

    /**
     * Represents a response to a request for information about a single user.
     *
     * HTTP GET request to http://host/v1/users/userIRI
     */
    export interface UserProfileResponseV1 {
        userProfile: UserProfileV1
    }

    /**
     * Represents an answer to a request for a list of all projects the user is member of.
     *
     * HTTP GET request to http://host/v1/users/projects
     */
    export interface UserProjectMembershipsGetResponseV1 {

        projects: Array<basicMessageComponents.KnoraIRI>;
    }

    /**
     * Represents an answer to a request for a list of all projects the user is member of the project admin group.
     *
     * HTTP GET request to http://host/v1/users/projects-admin
     */
    export interface UserProjectAdminMembershipsGetResponseV1 {

        projects: Array<basicMessageComponents.KnoraIRI>;
    }

    /**
     * Represents an answer to a request for a list of all groups the user is member of.
     *
     * HTTP GET request to http://host/v1/users/groups
     */
    export interface UserGroupMembershipsGetResponseV1 {

        groups: Array<basicMessageComponents.KnoraIRI>;
    }


    /**
     * Represents an answer to a user creation / change operation
     *
     */
    export interface UserOperationResponseV1 {

        userProfile: UserProfileV1;
    }

    /**
     * Represents a user's profile.
     */
    export interface UserProfileV1 {
        /**
         * Basic data about the user.
         */
        userData: UserDataV1;

        /**
         * The IRIs of the groups that the user belongs to.
         */
        groups: Array<basicMessageComponents.KnoraIRI>;

        /**
         * Projects the user is a member of. The keys are project IRIs.
         */
        projects_info: {
            [index:string]: ProjectInfoV1;
        }

        /**
         * Not used.
         */
        sessionId: null;

        /**
         * True if the user is the system user.
         */
        isSystemUser: boolean;

        /**
         * The user's permission data.
         */
        permissionData: PermissionDataV1;
    }

    /**
     * Represents the current user's data
     */
    export interface UserDataV1 {
        /**
         * User's email address
         */
        email: string | null;

        /**
         * User's first name
         */
        firstname: string | null;

        /**
         * User's last name
         */
        lastname: string | null;

        /**
         * User's IRI
         */
        user_id: basicMessageComponents.KnoraIRI | null;

        /**
         * User's preferred language
         */
        lang: string | null;

        /**
         * True if the user is active.
         */
        status: boolean | null;

        /**
         * Session token is never exposed.
         */
        token: string | null;

        /**
         * Password is never exposed.
         */
        password: string | null;
    }
}