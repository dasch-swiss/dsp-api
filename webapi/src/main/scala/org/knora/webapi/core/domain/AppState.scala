/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.core.domain

/**
 * Application States at Startup
 */
sealed trait AppState
object AppState {
  case object Stopped               extends AppState
  case object WaitingForTriplestore extends AppState
  case object TriplestoreReady      extends AppState
  case object UpdatingRepository    extends AppState
  case object RepositoryUpToDate    extends AppState
  case object LoadingOntologies     extends AppState
  case object OntologiesReady       extends AppState
  case object MaintenanceMode       extends AppState
  case object Running               extends AppState
}
