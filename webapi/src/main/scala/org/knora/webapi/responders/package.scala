/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and Sepideh Alassi.
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

    val RESPONDER_MANAGER_ACTOR_NAME = "responderManager"
    val RESPONDER_MANAGER_ACTOR_PATH = "/user/" + RESPONDER_MANAGER_ACTOR_NAME

    val RESOURCES_ROUTER_ACTOR_NAME = "resourcesRouter"
    val RESOURCES_ROUTER_ACTOR_PATH = RESPONDER_MANAGER_ACTOR_PATH + "/" + RESOURCES_ROUTER_ACTOR_NAME

    val VALUES_ROUTER_ACTOR_NAME = "valuesRouter"
    val VALUES_ROUTER_ACTOR_PATH = RESPONDER_MANAGER_ACTOR_PATH + "/" + VALUES_ROUTER_ACTOR_NAME

    val SIPI_ROUTER_ACTOR_NAME = "sipiRouter"
    val SIPI_ROUTER_ACTOR_PATH = RESPONDER_MANAGER_ACTOR_PATH + "/" + SIPI_ROUTER_ACTOR_NAME

    val STANDOFF_ROUTER_ACTOR_NAME = "standoffRouter"
    val STANDOFF_ROUTER_ACTOR_PATH = RESPONDER_MANAGER_ACTOR_PATH + "/" + STANDOFF_ROUTER_ACTOR_NAME

    val USERS_ROUTER_ACTOR_NAME = "usersRouter"
    val USERS_ROUTER_ACTOR_PATH = RESPONDER_MANAGER_ACTOR_PATH + "/" + USERS_ROUTER_ACTOR_NAME

    val LISTS_ROUTER_ACTOR_NAME = "hierarchicalListsRouter"
    val LISTS_ROUTER_ACTOR_PATH = RESPONDER_MANAGER_ACTOR_PATH + "/" + LISTS_ROUTER_ACTOR_NAME

    val SEARCH_ROUTER_ACTOR_NAME = "searchRouter"
    val SEARCH_ROUTER_ACTOR_PATH = RESPONDER_MANAGER_ACTOR_PATH + "/" + SEARCH_ROUTER_ACTOR_NAME

    val ONTOLOGY_ROUTER_ACTOR_NAME = "ontologyRouter"
    val ONTOLOGY_ROUTER_ACTOR_PATH = RESPONDER_MANAGER_ACTOR_PATH + "/" + ONTOLOGY_ROUTER_ACTOR_NAME

    val PROJECTS_ROUTER_ACTOR_NAME = "projectsRouter"
    val PROJECTS_ROUTER_ACTOR_PATH = RESPONDER_MANAGER_ACTOR_PATH + "/" + PROJECTS_ROUTER_ACTOR_NAME

    val CKAN_ROUTER_ACTOR_NAME = "ckanRouter"
    val CKAN_ROUTER_ACTOR_PATH = RESPONDER_MANAGER_ACTOR_PATH + "/" + CKAN_ROUTER_ACTOR_NAME

    val STORE_ROUTER_ACTOR_NAME = "storeRouter"
    val STORE_ROUTER_ACTOR_PATH = RESPONDER_MANAGER_ACTOR_PATH + "/" + STORE_ROUTER_ACTOR_NAME

    val PERMISSIONS_ROUTER_ACTOR_NAME = "permissionsRouter"
    val PERMISSIONS_ROUTER_ACTOR_PATH = RESPONDER_MANAGER_ACTOR_PATH + "/" + PERMISSIONS_ROUTER_ACTOR_NAME

    val GROUPS_ROUTER_ACTOR_NAME = "groupsRouter"
    val GROUPS_ROUTER_ACTOR_PATH = RESPONDER_MANAGER_ACTOR_PATH + "/" + GROUPS_ROUTER_ACTOR_NAME

    // ------------------------------------------------------------------------------------------
    // --------------------------------------- V2 Routers ---------------------------------------
    // ------------------------------------------------------------------------------------------

    val ONTOLOGIES_ROUTER_ACTOR_NAME2 = "ontologiesRouter2"
    val ONTOLOGIES_ROUTER_ACTOR_PATH2 = RESPONDER_MANAGER_ACTOR_PATH + "/" + ONTOLOGIES_ROUTER_ACTOR_NAME2

    val SEARCH_ROUTER_ACTOR_NAME2 = "searchRouter2"
    val SEARCH_ROUTER_ACTOR_PATH2 = RESPONDER_MANAGER_ACTOR_PATH + "/" + SEARCH_ROUTER_ACTOR_NAME2

    val RESOURCES_ROUTER_ACTOR_NAME2 = "resourcesRouter2"
    val RESOURCES_ROUTER_ACTOR_PATH2 = RESPONDER_MANAGER_ACTOR_PATH + "/" + RESOURCES_ROUTER_ACTOR_NAME2

}
