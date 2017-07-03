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

/**
 * This module contains interfaces that represent requests and responses for group administration
 */
export module groupFormats {

    /**
     * Represents an API request payload sent during a group creation request.
     */
    export interface CreateGroupApiRequestV1 {

        /**
         * The name of the group to be created (unique).
         */
        name: string;

        /**
         * The description of the group to be created.
         */
        description?: string;

        /**
         * The project inside which the group will be created.
         */
        project: basicMessageComponents.KnoraIRI;

        /**
         * The status of the group to be created (default = true).
         */
        status?: boolean;

        /**
         * The status of self-join of the group to be created (default = false).
         */
        selfjoin?: boolean;
    }

    /**
     * Represents an API request payload sent during a group change request.
     */
    export interface ChangeGroupApiRequestV1 {

        /**
         * The new group's name.
         */
        name?: string;

        /**
         * The new group's description.
         */
        description?: string;

        /**
         * The new group's status.
         */
        status?: boolean;

        /**
         * The new group's self-join status.
         */
        selfjoin?: boolean;
    }

    /**
     * Represents a response to a request for information about all groups.
     */
    export interface GroupsResponseV1 {

        /**
         * Information about all existing groups.
         */
        groups: Array<GroupInfoV1>;
    }

    /**
     * Represents a response to a request for information about a single group.
     */
    export interface GroupInfoResponseV1 {

        /**
         * All information about the group.
         */
        group_info: GroupInfoV1;
    }

    /**
     * Represents a response to a request for a list of members inside a single group.
     */
    export interface GroupMembersResponseV1 {

        /**
         * The group's members.
         */
        members: basicMessageComponents.KnoraIRI;
    }

    /**
     * Represents an answer to a group creating/modifying operation.
     */
    export interface GroupOperationResponseV1 {

        /**
         * The new group info of the created/modified group.
         */
        group_info: GroupInfoV1
    }

    /**
     * Represents group information.
     */
    export interface GroupInfoV1 {
        /**
         * The IRI of the group.
         */
        id: basicMessageComponents.KnoraIRI;

        /**
         * The name of the group. Needs to be unique on project level.
         */
        name: string;

        /**
         * The description of the group.
         */
        description: string;

        /**
         * The IRI of the project this group belongs to.
         */
        project: basicMessageComponents.KnoraIRI;

        /**
         * The group's status. `false` means the group is deleted.
         */
        status: boolean;

        /**
         * The group's self-join status. 'true' means everybody can join by themselves.
         */
        selfjoin: boolean;
    }
}