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

/**
 * This module contains interfaces that represent responses to a resource GET request.
 */
export module resourceResponseFormats {

    /**
     * Represents a property value (no parallel arrays)
     */
    interface propval {
        /**
         * Textual representation of the value.
         */
        textval:string;

        /**
         * Owner of the value.
         */
        person_id:string | null;

        /**
         * Date of last modification of the value.
         */
        lastmod:string | null;

        /**
         * IRI of the value.
         */
        id:basicMessageComponents.KnoraIRI;

        /**
         * Comment on the value.
         */
        comment:string | null;

        /**
         * date of last modification of the value as UTC.
         */
        lastmod_utc:string | null;

        /**
         * typed representation of the value.
         */
        value:basicMessageComponents.knoraValue;
    }

    /**
     * Represents a property (no parallel arrays)
     */
    interface prop {
        /**
         * Type of the value as a string
         */
        valuetype:string;

        /**
         * obsolete
         */
        is_annotation:string;

        /**
         * IRI of the value type.
         */
        valuetype_id:basicMessageComponents.KnoraIRI;

        /**
         * Label of the property type
         */
        label:string;

        /**
         * GUI element of the property
         */
        guielement:string | null;

        /**
         * HTML attributes for the GUI element used to render this property
         */
        attributes:string | null;

        /**
         * IRI of the property type
         */
        pid:basicMessageComponents.KnoraIRI;

        /**
         * The property's values if given.
         * If an instance of this property type does not exists for the requested resource,
         * only the information about the property type is returned.
         */
        values?:Array<propval>;
    }

    /**
     * Represents a property (parallel arrays)
     */
    interface property {
        /**
         * Obsolete
         */
        regular_property:number;

        /**
         * If the property's value is another resource, contains the `rdfs:label` of the OWL class
         * of each resource referred to.
         */
        value_restype?:Array<string | null>;

        /**
         * Order of property type in GUI
         */
        guiorder:number | null;

        /**
         * If the property's value is another resource, contains the `rdfs:label` of each resource
         * referred to.
         */
        value_firstprops?:Array<string | null>;

        /**
         * Obsolete
         */
        is_annotation:string;

        /**
         * The type of this property's values
         */
        valuetype_id:string;

        /**
         * The label of this property type (null if the property is __locations__)
         */
        label:string | null;

        /**
         * if the property's value is another resource, contains the icon representing the OWL
         * class of each resource referred to.
         */
        value_iconsrcs?:Array<string | null>;

        /**
         * the type of GUI element used to render this property.
         */
        guielement:string | null;

        /**
         * HTML attributes for the GUI element used to render this property
         */
        attributes:string | null;

        /**
         * The cardinality of this property type for the given resource class (null if the property is __locations__)
         */
        occurrence:string | null;

        /**
         * The IRIs of the value objects representing the property's values for this resource
         */
        value_ids?:Array<basicMessageComponents.KnoraIRI>;

        /**
         * The given user's permissions on the value objects.
         */
        value_rights?:Array<basicMessageComponents.KnoraRights>;

        /**
         * The IRI of the property type
         */
        pid:basicMessageComponents.KnoraIRI;

        /**
         * The property's values
         */
        values?:Array<basicMessageComponents.knoraValue>;

        /**
         * Comments on the property's values
         */
        comments?:Array<string | null>;

        /**
         * List of binary representations attached to the requested resource (when doing a full resource request)
         */
        locations?: Array<basicMessageComponents.locationItem>;
    }

    /**
     * Represents a permission assertion for the current user.
     */
    interface permissionItem {
        /**
         * Permission level
         */
        permission:string;

        /**
         * User group that the permission level is granted to
         */
        granted_to:string;
    }

    /**
     * Represents the regions attached to a resource.
     */
    interface region {
        /**
         * A map of property types to property values and res_id and iconsrc
         */
        [index:string]:prop|string;
    }

    /**
     * Represents information about a resource and its class.
     */
    interface resinfo {
        /**
         * Digital representations of the resource
         */
        locations:Array<basicMessageComponents.locationItem> | null;

        /**
         * Label of the resource's class
         */
        restype_label:string;

        /**
         * Indicates if there is a location (digital representation) attached
         */
        resclass_has_location:boolean;

        /**
         * Preview representation of the resource: Thumbnail or Icon
         */
        preview:basicMessageComponents.locationItem | null;

        /**
         * The owner of the resource
         */
        person_id:string;

        /**
         * Points to the parent resource in case the resource depends on it
         */
        value_of:string|number;

        /**
         * Date of last modification
         */
        lastmod:string;

        /**
         * The resource class's name
         */
        resclass_name:string;

        /**
         * Regions if there are any
         */
        regions:Array<region> | null;

        /**
         * Description of the resource type
         */
        restype_description:string;

        /**
         * The project IRI the resource belongs to
         */
        project_id:basicMessageComponents.KnoraIRI;

        /**
         * Full quality representation of the resource
         */
        locdata:basicMessageComponents.locationItem | null;

        /**
         * The Knora IRI identifying the resource's class
         */
        restype_id:basicMessageComponents.KnoraIRI;

        /**
         * The resource's label
         */
        firstproperty:string;

        /**
         * The URL of an icon for the resource class
         */
        restype_iconsrc:string;

        /**
         * The Knora IRI identifying the resource's class
         */
        restype_name:basicMessageComponents.KnoraIRI;
    }

    /**
     * Represents information about a resource.
     */
    interface resdata {
        /**
         * IRI of the resource
         */
        res_id:basicMessageComponents.KnoraIRI;

        /**
         * IRI of the resource's class.
         */
        restype_name:basicMessageComponents.KnoraIRI;

        /**
         * Label of the resource's class
         */
        restype_label:string;

        /**
         * Icon of the resource's class.
         */
        iconsrc:string;

        /**
         * The given user's permissions on the resource
         */
        rights:basicMessageComponents.KnoraRights;
    }

    /**
     * Represents a resource referring to the requested resource.
     */
    interface incomingItem {
        /**
         * Representation of the referring resource
         */
        ext_res_id:{
            /**
             * The IRI of the referring resource
             */
            id:basicMessageComponents.KnoraIRI;

            /**
             * The IRI of the referring property type
             */
            pid:basicMessageComponents.KnoraIRI;
        };

        /**
         * Resinfo of the referring resource
         */
        resinfo:resinfo;

        /**
         * First property of the referring resource
         */
        value:string;
    }

    /**
     * Represents the context of a resource.
     */
    interface context {
        /**
         * Context code: 0 for none, 1 for is partOf (e.g. a page of a book), 2 for isCompound (e.g. a book that has pages)
         */
        context:number;

        /**
         * The IRI of the resource
         */
        canonical_res_id:basicMessageComponents.KnoraIRI;

        /**
         * IRI of the parent resource
         */
        parent_res_id?:basicMessageComponents.KnoraIRI;

        /**
         * Resinfo of the parent resource (if the requested resource is a dependent resource like a page that belongs to a book)
         */
        parent_resinfo?:resinfo;

        /**
         * Resinfo of the requested resource (if requested: resinfo=true)
         */
        resinfo?:resinfo;

        /**
         * Locations of depending resources (e.g. representation of pages of a book)
         */
        locations?:Array<Array<basicMessageComponents.locationItem>>;

        /**
         * Preview locations of depending resources (e.g. representation of pages of a book)
         */
        preview?:Array<basicMessageComponents.locationItem>

        /**
         * First properties of depending resources (e.g. of pages of a book)
         */
        firstprop?:Array<string>;

        /**
         * obsolete
         */
        region?:Array<string | null>;

        /**
         * obsolete
         */
        resclass_name?:string;

        /**
         * IRIs of dependent resources (e.g. pages of a book)
         */
        res_id?:Array<basicMessageComponents.KnoraIRI>;
    }

    /**
     * Represents information about a property type.
     */
    interface propertyDefinition {
        /**
         * IRI of the property type
         */
        name:basicMessageComponents.KnoraIRI;

        /**
         * Description of the property type
         */
        description:string;

        /**
         * IRI of the property type's value
         */
        valuetype_id:basicMessageComponents.KnoraIRI;

        /**
         * Label of the property type
         */
        label:string;

        /**
         * IRI of the vocabulary the property type belongs to
         */
        vocabulary:basicMessageComponents.KnoraIRI;

        /**
         * GUI attributes (HTML) of the property type
         */
        attributes:string | null;

        /**
         * Cardinality of the property type for the requested resource class (not given if property type is requested for a vocabulary)
         */
        occurrence?:string;

        /**
         * IRI of the property type
         */
        id:basicMessageComponents.KnoraIRI;

        /**
         * Name of the GUI element used for the property type
         */
        gui_name:string | null;

    }

    /**
     * Represents information about the requested resource class.
     */
    interface restype {

        /**
         * IRI of the resource class
         */
        name:basicMessageComponents.KnoraIRI;

        /**
         * Description of the resource class
         */
        description:string;

        /**
         * Label of the resource class
         */
        label:string;

        /**
         * Property types that the resource class may have
         */
        properties:Array<propertyDefinition>;

        /**
         * Path to the resource class icon
         */
        iconsrc:string;

    }

    /**
     * Represents a property type attached to a resource class.
     */
    interface propItemForResType {

        id:string;

        label:string;
    }

    /**
     * Represents a resource class.
     */
    interface resTypeItem {
        /**
         * IRI of the resource class
         */
        id:basicMessageComponents.KnoraIRI;

        /**
         * Label of the resource class
         */
        label:string;

        /**
         * Property Types that this resource class may have
         */
        properties:Array<propItemForResType>

    }

    /**
     * Represents a vocabulary.
     */
    interface vocabularyItem {
        /**
         * The vocabulary's project's short name
         */
        shortname: string;

        /**
         * Description of the vocabulary's project
         */
        description: string;

        /**
         * The vocabulary's IRI
         */
        uri: basicMessageComponents.KnoraIRI;

        /**
         * The vocabulary's IRI
         */
        id: basicMessageComponents.KnoraIRI;

        /**
         * The project the vocabulary belongs to
         */
        project_id:basicMessageComponents.KnoraIRI;

        /**
         * The vocabulary's project's long name
         */
        longname: string;

        /**
         * Indicates if this is the vocabulary the user's project belongs to
         */
        active: Boolean;

    }

    /**
     * Represents a retrieved resource when doing a label search.
     */
    interface resourceLabelSearchItem {

        /**
         * The IRI of the retrieved resource
         */
        id: basicMessageComponents.KnoraIRI;

        /**
         * Values representing the retrieved resource
         */
        value: Array<string>;

        /**
         * The user's permissions on the retrieved resource
         */
        rights: basicMessageComponents.KnoraRights;

    }

    /**
     * Represents a list of property types for the requested resource class or vocabulary.
     *
     * HTTP GET to http://host/v1/propertylists?restype=resourceClassIRI
     *
     */
    export interface propertyTypesInResourceClassResponse extends basicMessageComponents.basicResponse {
        /**
         * Lists the property types the indicated resource class or vocabulary may have.
         */
        properties:Array<propertyDefinition>;

    }

    /**
     * Represents the Knora API V1 response to a resource type request for a vocabulary.
     *
     * HTTP GET to http://host/v1/resourcetypes?vocabulary=vocabularyIRI
     */
    export interface resourceTypesInVocabularyResponse extends basicMessageComponents.basicResponse {
        /**
         * Lists the resource classes that are defined for the given vocabulary.
         */
        resourcetypes:Array<resTypeItem>;

    }

    /**
     * Represents the Knora API V1 response to a resource type request.
     *
     * HTTP GET to http://host/v1/resourcetypes/resourceClassIRI
     */
    export interface resourceTypeResponse extends basicMessageComponents.basicResponse {
        /**
         * Represents information about the requested resource class
         */
        restype_info:restype;

    }

    /**
     * Represents the Knora API V1 response to a properties request for a resource.
     * This response just returns a resource's properties.
     *
     * HTTP GET to http://host/v1/properties/resourceIRI
     */
    export interface resourcePropertiesResponse extends basicMessageComponents.basicResponse {

        /**
         * A map of property type IRIs to property instances
         */
        properties:{
            [index:string]:prop;
        }

    }

    /**
     * Represents the Knora API V1 response to a full resource request.
     *
     * HTTP GET to http://host/v1/resources/resourceIRI
     */
    export interface resourceFullResponse extends basicMessageComponents.basicResponse {
        /**
         * Description of the resource and its class
         */
        resinfo:resinfo;

        /**
         * Additional information about the requested resource (no parameters)
         */
        resdata:resdata;

        /**
         * The resource's properties
         */
        props:{
            [index:string]:property;
        }

        /**
         * Resources referring to the requested resource
         */
        incoming:Array<incomingItem>

        /**
         * The given user's permissions on the resource (obsolete)
         */
        access:basicMessageComponents.KnoraAccess;

    }

    /**
     * Represents the Knora API V1 response to a resource info request (reqtype=info).
     *
     * HTTP GET to http://host/v1/resources/resourceIRI?reqtype=info
     */
    export interface resourceInfoResponse extends basicMessageComponents.basicResponse {
        /**
         * The current user's permissions on the resource
         */
        rights:basicMessageComponents.KnoraRights;

        /**
         * Description of the resource and its class
         */
        resource_info:resinfo;

    }

    /**
     * Represents the Knora API V1 response to a resource rights request (reqtype=rights).
     *
     * HTTP GET to http://host/v1/resources/resourceIRI?reqtype=rights
     */
    export interface resourceRightsResponse extends basicMessageComponents.basicResponse {
        /**
         * The current user's permissions on the resource
         */
        rights:number;

    }

    /**
     * Represents the Knora API V1 response to a context request (reqtype=context) with or without resinfo (resinfo=true).
     *
     * HTTP GET to http://host/v1/resources/resourceIRI?reqtype=context[&resinfo=true]
     */
    export interface resourceContextResponse extends basicMessageComponents.basicResponse {
        /**
         * Context of the requested resource
         */
        resource_context:context;

    }

    /**
     * Represents the available vocabularies
     *
     * HTTP GET to http://host/v1/vocabularies
     */
    export interface vocabularyResponse extends basicMessageComponents.basicResponse {

        vocabularies: Array<vocabularyItem>;

    }

    /**
     * Represents resources that matched the search term in their label.
     * The search can be restricted to resource classes and a limit can be defined for the results to be returned.
     * The amount of values values to be returned for each retrieved resource can also be defined.
     *
     * HTTP GET to http://host/v1/resources?searchstr=searchValue[&restype_id=resourceClassIRI][&numprops=Integer][&limit=Integer]
     */
    export interface resourceLabelSearchResponse extends basicMessageComponents.basicResponse {

        resources: Array<resourceLabelSearchItem>;

    }

}
