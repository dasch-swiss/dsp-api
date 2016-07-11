/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and André Fatton.
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

export module basicResponseComponents {

    /**
     * Basic members of the Knora API V1 response format.
     */
    export interface basicResponse {
        /**
         * Knora status code
         */
        status:number;

        /**
         * The current user's data
         */
        userdata:userdata;
    }

    /**
     * Represents a Knora project
     */
    interface projectItem {
        /**
         * Path to the project's files
         */
        basepath:string;

        /**
         * Project's short name
         */
        shortname:string;

        /**
         * Description of the project
         */
        description:string;

        /**
         * The project's logo
         */
        logo:string;

        /**
         * The project's IRI
         */
        id:string;

        /**
         * Keywords describing the project
         */
        keywords:string;

        /**
         * obsolete
         */
        rights:string;

        /**
         * Project's long name
         */
        longname:string;
    }

    /**
     * Represents the current user's data
     */
    interface userdata {
        /**
         * User's email address
         */
        email:string;

        /**
         * User's unique name
         */
        username:string;

        /**
         * User's first name
         */
        firstname:string;

        /**
         * User's last name
         */
        lastname:string;

        /**
         * List of project descriptions the user is member of
         */
        projects_info:Array<projectItem>;

        /**
         * User's IRI
         */
        user_id:string;

        /**
         * User's preferred language
         */
        lang:string;

        /**
         * User's active project
         */
        active_project:string;

        /**
         * Session token
         */
        token:string;

        /**
         * List of project IRIs the user is member of
         */
        projects:Array<string>;

        /**
         * obsolete
         */
        password:string;
    }

}