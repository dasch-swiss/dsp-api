/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.core.domain

/**
 * Application States at Startup
 */
sealed trait AppState
object AppState {
  case object Stopped                extends AppState
  case object StartingUp             extends AppState
  case object WaitingForTriplestore  extends AppState
  case object TriplestoreReady       extends AppState
  case object UpdatingRepository     extends AppState
  case object RepositoryUpToDate     extends AppState
  case object CreatingCaches         extends AppState
  case object CachesReady            extends AppState
  case object UpdatingSearchIndex    extends AppState
  case object SearchIndexReady       extends AppState
  case object LoadingOntologies      extends AppState
  case object OntologiesReady        extends AppState
  case object WaitingForIIIFService  extends AppState
  case object IIIFServiceReady       extends AppState
  case object WaitingForCacheService extends AppState
  case object CacheServiceReady      extends AppState
  case object MaintenanceMode        extends AppState
  case object Running                extends AppState
}
