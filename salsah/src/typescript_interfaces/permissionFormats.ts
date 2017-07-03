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
 * This module contains interfaces that represent requests and responses for user administration
 */
export module permissionFormats {

    /**
     * Represents a user's permission data.
     */
    export interface PermissionDataV1 {
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
            [index:string]: Array<PermissionV1>;
        }

        /**
         * The user's type. Only 'true' if user is anonymous.
         */
        anonymousUser: boolean;
    }

    /**
     * Represents a permission.
     */
    export interface PermissionV1 {
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



}