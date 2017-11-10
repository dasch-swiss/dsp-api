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

import {basicMessageComponents} from "./basicMessageComponents";
import {userFormats} from "./userFormats";

/**
 * This module contains interfaces that represent requests and responses for user administration
 */
export module projectFormats {

    import UserDataV1 = userFormats.UserDataV1;

    /**
     * Represents an API request payload sent during a project creation request.
     *
     * HTTP POST request to http://host/v1/projects
     */
    export interface CreateProjectApiRequestV1 {

        /**
         * The shortname of the project to be created (unique).
         */
        shortname: string;

        /**
         * The shortcode of the project to be created (unique).
         */
        shortcode?: string;

        /**
         * The longname of the project to be created.
         */
        longname?: string;

        /**
         * The description of the project to be created.
         */
        description?: string;

        /**
         * The keywords of the project to be created.
         */
        keywords?: string;

        /**
         * The logo of the project to be created.
         */
        logo?: string;

        /**
         * The status of the project to be created (active = true, inactive = false).
         */
        status: boolean;

        /**
         * The status of self-join of the project to be created.
         */
        selfjoin: boolean;
    }


    /**
     * Represents an API request payload sent during a project change request.
     *
     * HTTP PUT request to http://host/v1/projects/projectIRI
     *
     * There are two distinct use cases / payload combination:
     *
     * 1. change ontology and data graph: ontologygraph, datagraph
     * 2. basic project information: shortname, longname, description, keywords, logo, institution, status, selfjoin
     */
    export interface ChangeProjectApiRequestV1 {

        /**
         * The new project's shortname.
         */
        shortname?: string;

        /**
         * The new project's longname.
         */
        longname?: string;

        /**
         * The new project's description.
         */
        description?: string;

        /**
         * The new project's keywords.
         */
        keywords?: string;

        /**
         * The new project's logo.
         */
        logo?: string;

        /**
         * The new project's institution.
         */
        institution?: string;

        /**
         * The new project's status.
         */
        status?: string;

        /**
         * The new project's self-join status.
         */
        selfjoin?: string;
    }

    /**
     * Represents a response to a request for information about all projects.
     *
     * HTTP GET request to http://host/v1/projects
     */
    export interface ProjectsResponseV1 {

        /**
         * Information about all existing projects.
         */
        projects: Array<ProjectInfoV1>;
    }

    /**
     * Represents a response to a request for information about a single project.
     *
     * HTTP GET request to http://host/v1/projects/projectIRI
     */
    export interface ProjectInfoResponseV1 {

        /**
         * All information about the project.
         */
        project_info: ProjectInfoV1;
    }

    /**
     * Represents a response to a request for a list of members inside a single project.
     *
     * HTTP GET request to http://host/v1/projects/members
     */
    export interface ProjectMembersGetResponseV1 {

        /**
         * A list of members.
         */
        members: Array<UserDataV1>;

        /**
         * Information about the user that made the request.
         */
        userDataV1: UserDataV1;
    }

    /**
     * Represents a response to a request for a list of admin members inside a single project.
     *
     * HTTP GET request to http://host/v1/projects/admin-members/projectIRI
     */
    export interface ProjectAdminMembersGetResponseV1 {

        /**
         * A list of admin members.
         */
        members: Array<UserDataV1>;

        /**
         * Information about the user that made the request.
         */
        userDataV1: UserDataV1;
    }

    /**
     * Represents an answer to a project creating/modifying operation.
     */
    export interface ProjectOperationResponseV1 {

        /**
         * The new project info of the created/modified project.
         */
        project_info: ProjectInfoV1;
    }

    /**
     * Describes a project that a user is a member of.
     */
    export interface ProjectInfoV1 {
        /**
         * The project's IRI.
         */
        id: basicMessageComponents.KnoraIRI;

        /**
         * The project's short name.
         */
        shortname: string;

        /**
         * The project's short code.
         */
        shortcode: string;

        /**
         * A description of the project.
         */
        description: string;

        /**
         * The IRI of the institution, if any, that the project belongs to.
         */
        institution: basicMessageComponents.KnoraIRI | null;

        /**
         * The filename of the project's logo, if any.
         */
        logo: string | null;

        /**
         * The IRIs of the project ontologies.
         */
        ontologies: Array<string>;

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
        selfjoin: boolean;

    }

}