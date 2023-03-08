/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi

package object settings {

  val APPLICATION_MANAGER_ACTOR_NAME = "applicationManager"
  val APPLICATION_MANAGER_ACTOR_PATH = "/user/" + APPLICATION_MANAGER_ACTOR_NAME

  val RESPONDER_MANAGER_ACTOR_NAME: String = "responderManager"
  val RESPONDER_MANAGER_ACTOR_PATH: String = "/user/" + APPLICATION_MANAGER_ACTOR_NAME + RESPONDER_MANAGER_ACTOR_NAME

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

  val SIPI_ROUTER_V2_ACTOR_NAME: String = "sipiRouterV2"
  val SIPI_ROUTER_V2_ACTOR_PATH: String = RESPONDER_MANAGER_ACTOR_PATH + "/" + SIPI_ROUTER_V2_ACTOR_NAME

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

  val StoreManagerActorName: String = "storeManager"
  val StoreManagerActorPath: String = "/user/" + APPLICATION_MANAGER_ACTOR_NAME + StoreManagerActorName

  /* Triplestores */

  val TriplestoreManagerActorName: String = "triplestoreManager"
  val TriplestoreManagerActorPath: String = StoreManagerActorPath + "/" + TriplestoreManagerActorName

  val HttpTriplestoreActorName: String = "httpTriplestoreRouter"
  val FakeTriplestoreActorName: String = "fakeTriplestore"

  /* Sipi */

  val IIIFManagerActorName: String = "iiifManager"
  val IIIFManagerActorPath: String = StoreManagerActorPath + "/" + IIIFManagerActorName

  val SipiConnectorActorName: String = "sipiConnector"
}
