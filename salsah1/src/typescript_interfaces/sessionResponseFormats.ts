/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and Sepideh Alassi.
 * This file is part of Knora.
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
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