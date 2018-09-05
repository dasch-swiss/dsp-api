/*
 * Copyright © 2015-2018 the contributors (see Contributors.md).
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

package org.knora.webapi

/**
  * Contains constants representing the Akka names and paths of the responder actors.
  */
package object responders {

    val RESPONDER_MANAGER_ACTOR_NAME: String = "responderManager"
    val RESPONDER_MANAGER_ACTOR_PATH: String = "/user/" + RESPONDER_MANAGER_ACTOR_NAME

    val RESOURCES_V1_ACTOR_NAME: String = "resourcesV1"
    val RESOURCES_V1_ACTOR_PATH: String = RESPONDER_MANAGER_ACTOR_PATH + "/" + RESOURCES_V1_ACTOR_NAME

    val VALUES_V1_ACTOR_NAME: String = "valuesV1"
    val VALUES_V1_ACTOR_PATH: String = RESPONDER_MANAGER_ACTOR_PATH + "/" + VALUES_V1_ACTOR_NAME

    val SIPI_ROUTER_V1_ACTOR_NAME: String = "sipiRouterV1"
    val SIPI_ROUTER_V1_ACTOR_PATH: String = RESPONDER_MANAGER_ACTOR_PATH + "/" + SIPI_ROUTER_V1_ACTOR_NAME

    val STANDOFF_V1_ACTOR_NAME: String = "standoffV1"
    val STANDOFF_V1_ACTOR_PATH: String = RESPONDER_MANAGER_ACTOR_PATH + "/" + STANDOFF_V1_ACTOR_NAME

    val USERS_V1_ACTOR_NAME: String = "usersV1"
    val USERS_V1_ACTOR_PATH: String = RESPONDER_MANAGER_ACTOR_PATH + "/" + USERS_V1_ACTOR_NAME

    val LISTS_V1_ACTOR_NAME: String = "listsV1"
    val LISTS_V1_ACTOR_PATH: String = RESPONDER_MANAGER_ACTOR_PATH + "/" + LISTS_V1_ACTOR_NAME

    val SEARCH_V1_ACTOR_NAME: String = "searchV1"
    val SEARCH_V1_ACTOR_PATH: String = RESPONDER_MANAGER_ACTOR_PATH + "/" + SEARCH_V1_ACTOR_NAME

    val ONTOLOGY_V1_ACTOR_NAME: String = "ontologyV1"
    val ONTOLOGY_V1_ACTOR_PATH: String = RESPONDER_MANAGER_ACTOR_PATH + "/" + ONTOLOGY_V1_ACTOR_NAME

    val PROJECTS_V1_ACTOR_NAME: String = "projectsV1"
    val PROJECTS_V1_ACTOR_PATH: String = RESPONDER_MANAGER_ACTOR_PATH + "/" + PROJECTS_V1_ACTOR_NAME

    val CKAN_V1_ACTOR_NAME: String = "ckanV1"
    val CKAN_V1_ACTOR_PATH: String = RESPONDER_MANAGER_ACTOR_PATH + "/" + CKAN_V1_ACTOR_NAME



    // ------------------------------------------------------------------------------------------
    // --------------------------------------- V2 Routers ---------------------------------------
    // ------------------------------------------------------------------------------------------

    val ONTOLOGY_V2_ACTOR_NAME: String = "ontologyV2"
    val ONTOLOGY_V2_ACTOR_PATH: String = RESPONDER_MANAGER_ACTOR_PATH + "/" + ONTOLOGY_V2_ACTOR_NAME

    val SEARCH_V2_ACTOR_NAME: String = "searchV2"
    val SEARCH_V2_ACTOR_PATH: String = RESPONDER_MANAGER_ACTOR_PATH + "/" + SEARCH_V2_ACTOR_NAME

    val RESOURCES_V2_ACTOR_NAME: String = "resourcesV2"
    val RESOURCES_V2_ACTOR_PATH: String = RESPONDER_MANAGER_ACTOR_PATH + "/" + RESOURCES_V2_ACTOR_NAME

    val VALUES_V2_ACTOR_NAME: String = "valuesV2"
    val VALUES_V2_ACTOR_PATH: String = RESPONDER_MANAGER_ACTOR_PATH + "/" + VALUES_V2_ACTOR_NAME

    val PERSISTENT_MAP_V2_ACTOR_NAME: String = "persistentMapV2"
    val PERSISTENT_MAP_V2_ACTOR_PATH: String = RESPONDER_MANAGER_ACTOR_PATH + "/" + PERSISTENT_MAP_V2_ACTOR_NAME

    val STANDOFF_V2_ACTOR_NAME: String = "standoffV2"
    val STANDOFF_V2_ACTOR_PATH: String = RESPONDER_MANAGER_ACTOR_PATH + "/" + STANDOFF_V2_ACTOR_NAME

    val LISTS_V2_ACTOR_NAME: String = "listsV2"
    val LISTS_V2_ACTOR_PATH: String = RESPONDER_MANAGER_ACTOR_PATH + "/" + LISTS_V2_ACTOR_NAME


    // ------------------------------------------------------------------------------------------
    // ------------------------------------- Admin Routers --------------------------------------
    // ------------------------------------------------------------------------------------------

    val GROUPS_ADM_ACTOR_NAME: String = "groupsADM"
    val GROUPS_ADM_ACTOR_PATH: String = RESPONDER_MANAGER_ACTOR_PATH + "/" + GROUPS_ADM_ACTOR_NAME

    val LISTS_ADM_ACTOR_NAME: String = "listsADM"
    val LISTS_ADM_ACTOR_PATH: String = RESPONDER_MANAGER_ACTOR_PATH + "/" + LISTS_ADM_ACTOR_NAME

    val ONTOLOGIES_ADM_ACTOR_NAME: String = "ontologiesADM"
    val ONTOLOGIES_ADM_ACTOR_PATH: String = RESPONDER_MANAGER_ACTOR_PATH + "/" + ONTOLOGIES_ADM_ACTOR_NAME

    val PERMISSIONS_ADM_ACTOR_NAME: String = "permissionsADM"
    val PERMISSIONS_ADM_ACTOR_PATH: String = RESPONDER_MANAGER_ACTOR_PATH + "/" + PERMISSIONS_ADM_ACTOR_NAME

    val PROJECTS_ADM_ACTOR_NAME: String = "projectsADM"
    val PROJECTS_ADM_ACTOR_PATH: String = RESPONDER_MANAGER_ACTOR_PATH + "/" + PROJECTS_ADM_ACTOR_NAME

    val STORE_ADM_ACTOR_NAME: String = "storeADM"
    val STORE_ADM_ACTOR_PATH: String = RESPONDER_MANAGER_ACTOR_PATH + "/" + STORE_ADM_ACTOR_NAME

    val USERS_ADM_ACTOR_NAME: String = "usersADM"
    val USERS_ADM_ACTOR_PATH: String = RESPONDER_MANAGER_ACTOR_PATH + "/" + USERS_ADM_ACTOR_NAME
}
