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

import {basicMessageComponents} from "./basicMessageComponents"

/**
 * This module contains interfaces that represent responses to requests to open and close
 * a user session.
 */
export module sessionResponseFormats {
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
        userProfile: userProfile;
    }

	export interface userProfile {
		/**
		 * The IRIs of the groups that the user belongs to.
		 */
        groups: Array<basicMessageComponents.KnoraIRI>;

        /**
         * Projects the user is a member of. The keys are project IRIs.
         */
        projects_info: {
            [index:string]: project;
        }

        /**
         * True if the user is the system user.
         */
        isSystemUser: boolean;

        /**
         * The user's permission data.
         */
        permissionData: permissionData;

	    /**
	     * Additional data about the user.
	     */
        userData: userdata;

        /**
         * Not used.
         */
        sessionId: null;
    }

    /**
     * Represents the current user's data
     */
    interface userdata {
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
        lang: string;

        /**
         * True if the user is active.
         */
        isActiveUser: boolean;

        /**
         * Session token (currently not used).
         */
        token: string | null;

        /**
         * Not used.
         */
        password: null;
    }

    /**
     * Describes a project that a user is a member of.
     */
    export interface project {
        /**
         * The project's IRI.
         */
        id: basicMessageComponents.KnoraIRI;

    	/**
    	 * The project's short name.
    	 */
        shortname: string;

        /**
         * A description of the project.
         */
        description: string;

        /**
         * The IRI of the institution, if any, that the project belongs to.
         */
        belongsToInstitution: basicMessageComponents.KnoraIRI | null;

        /**
         * The filename of the project's logo, if any.
         */
        logo: string | null;

        /**
         * The name of the RDF graph containing the project's ontology.
         */
        ontologyNamedGraph: string;

        /**
         * The name of the RDF graph containing the project's data.
         */
        dataNamedGraph: string;

        /**
         * True if the project is active.
         */
        status: boolean;

        /**
         * Keywords describing the project.
         */
        keywords: string;

        /**
         * The project's full name.
         */
        longname: string;

        /**
         * True if users can add themselves to the project.
         */
        hasSelfJoinEnabled: boolean;
    }

    /**
     * Represents a permission.
     */
    export interface permission {
    	/**
    	 * The name of the permission.
    	 */
        name: string;

        /**
         * An optional IRI (e.g., group IRI, resource class IRI).
         */
        additionalInformation: basicMessageComponents.KnoraIRI | null;

        /**
         * A number representing the operations that the user has permission to carry out.
         */
        v1Code: number | null;
    }

    /**
     * Represents a user's permission data.
     */
    export interface permissionData {
    	/**
    	 * The groups the user belongs to for each project. The keys are project IRIs.
    	 */
        groupsPerProject: {
            [index:string]: Array<basicMessageComponents.KnoraIRI>;
        }

        /**
         * The user's administrative permissions for each project. The keys are project IRIs.
         */
        administrativePermissionsPerProject: {
            [index:string]: Array<permission>;
        }

        /**
         * The user's default object access permissions for each project. The keys are project IRIs.
         */
        defaultObjectAccessPermissionsPerProject: {
            [index:string]: Array<permission>;
        }
    }

}