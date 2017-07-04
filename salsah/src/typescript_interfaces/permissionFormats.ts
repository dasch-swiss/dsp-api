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

/**
 * This module contains interfaces that represent requests and responses for user administration
 */
export module permissionFormats {

    
    /* ADMINISTRATIVE PERMISSIONS */
    
    /**
     * Represents a response to a request for all administrative permissions inside a project.
     */
    export interface AdministrativePermissionsForProjectGetResponseV1 {
        administrativePermissions: Array<AdministrativePermissionV1>;
    }

    /**
     * Represents a response to a request for a single administrative permission.
     */
    export interface AdministrativePermissionForIriGetResponseV1 {
        sdministrativePermission: AdministrativePermissionV1;
    }

    /**
     * Represents a response to a request for administrative permissions on a specific group.
     */
    export interface AdministrativePermissionForProjectGroupGetResponseV1 {
        sdministrativePermission: AdministrativePermissionV1;
    }

    /**
     * Represents a response to an administrative permission create request
     */
    export interface AdministrativePermissionCreateResponseV1 {
        administrativePermission: AdministrativePermissionV1;
    }

    /* DEFAULT OBJECT ACCESS PERMISSION */

    /**
     * Represents a response to a request for all default object access permissions inside a project.
     */
    export interface DefaultObjectAccessPermissionsForProjectGetResponseV1 {
        defaultObjectAccessPermissions: Array<DefaultObjectAccessPermissionV1>;
    }

    /**
     * Represents a response to a request for a single default object access permission.
     */
    export interface DefaultObjectAccessPermissionGetResponseV1 {
        defaultObjectAccessPermission: DefaultObjectAccessPermissionV1;
    }

    /**
     * Represents a response to a request for a single default object access permission.
     */
    export interface DefaultObjectAccessPermissionForIriGetResponseV1 {
        defaultObjectAccessPermission:DefaultObjectAccessPermissionV1;
    }


    /* COMPONENTS */

    /**
     * Represents 'knora-base:AdministrativePermission'
     */
    export interface AdministrativePermissionV1 {

        /**
         * The IRI of the administrative permission.
         */
        iri: basicMessageComponents.KnoraIRI;

        /**
         * The project this permission applies to.
         */
        forProject: basicMessageComponents.KnoraIRI;

        /**
         * The group this permission applies to.
         */
        forGroup: basicMessageComponents.KnoraIRI;

        /**
         * The administrative permissions.
         */
        hasPermissions: Array<PermissionV1>;
    }

    /**
     * Represents 'knora-base:DefaultObjectAccessPermission'
     */
    export interface DefaultObjectAccessPermissionV1 {

        iri: basicMessageComponents.KnoraIRI;

        forProject: basicMessageComponents.KnoraIRI;

        forGroup?: basicMessageComponents.KnoraIRI;

        forResourceClass: basicMessageComponents.KnoraIRI;

        forProperty?: basicMessageComponents.KnoraIRI;

        hasPermissions: Array<PermissionV1>;
    }


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