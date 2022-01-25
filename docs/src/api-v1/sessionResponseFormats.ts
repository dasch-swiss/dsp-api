/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

import {basicMessageComponents} from "./basicMessageComponents"
import {userFormats} from "./userFormats";

/**
 * This module contains interfaces that represent responses to requests to open and close
 * a user session.
 */
export module sessionResponseFormats {
    import UserProfileV1 = userFormats.UserProfileV1;
    /**
     * Represents the response to a successful login request.
     */
    export interface loginResponse extends basicMessageComponents.basicResponse {
        /**
         * The user's session ID.
         */
        sid: string;

        /**
         * A description of the result of the request.
         */
        message: string;

        /**
         * The user's profile.
         */
        userProfile: UserProfileV1;
    }
}
