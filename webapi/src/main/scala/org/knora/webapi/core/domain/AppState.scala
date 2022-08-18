/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.core.domain

sealed trait ApplicationRequest

/**
 * Start Application
 *
 * @param ignoreRepository    if `true`, don't read anything from the repository on startup.
 * @param requiresIIIFService if `true`, ensure that the IIIF service is started.
 * @param retryCnt            how many times was this command tried
 */
case class AppStart(ignoreRepository: Boolean, requiresIIIFService: Boolean, retryCnt: Int = 0)
    extends ApplicationRequest

/**
 * After a successful bind, the ApplicationActor will receive this message and
 * change his behaviour to ready.
 */
case class AppReady() extends ApplicationRequest

/**
 * Stop Application
 */
case class AppStop() extends ApplicationRequest

/**
 * Check if actor is ready.
 */
case class ActorReady() extends ApplicationRequest

/**
 * Response used to acknowledge that actor is ready.
 */
case class ActorReadyAck()

/**
 * Setter message for storing the LoadDemoData flag.
 */
case class SetLoadDemoDataState(value: Boolean) extends ApplicationRequest

/**
 * Getter message for retrieving the LoadDemoData flag value.
 */
case class GetLoadDemoDataState() extends ApplicationRequest

/**
 * Setter message for storing the llowReloadOverHTTP flag.
 */
case class SetAllowReloadOverHTTPState(value: Boolean) extends ApplicationRequest

/**
 * Getter message for retrieving the llowReloadOverHTTP flag value.
 */
case class GetAllowReloadOverHTTPState() extends ApplicationRequest

/**
 * Setter message for storing the rometheusReporter flag.
 */
case class SetPrometheusReporterState(value: Boolean) extends ApplicationRequest

/**
 * Getter message for retrieving the rometheusReporter flag value.
 */
case class GetPrometheusReporterState() extends ApplicationRequest

/**
 * Setter message for storing the ZipkinReporter flag.
 */
case class SetZipkinReporterState(value: Boolean) extends ApplicationRequest

/**
 * Getter message for retrieving the ZipkinReporter flag value.
 */
case class GetZipkinReporterState() extends ApplicationRequest

/**
 * Setter message for storing the JaegerReporter flag.
 */
case class SetJaegerReporterState(value: Boolean) extends ApplicationRequest

/**
 * Getter message for retrieving the JaegerReporter flag value.
 */
case class GetJaegerReporterState() extends ApplicationRequest

/**
 * Setter message for storing the PrintConfigExtended flag.
 */
case class SetPrintConfigExtendedState(value: Boolean) extends ApplicationRequest

/**
 * Getter message for retrieving the PrintConfigExtended flag value.
 */
case class GetPrintConfigExtendedState() extends ApplicationRequest

/**
 * Setter message for setting the current application state.
 */
case class SetAppState(value: AppState) extends ApplicationRequest

/**
 * Message for getting the current application state.
 */
case class GetAppState() extends ApplicationRequest

/**
 * Message for initiating the startup sequence.
 *
 * @param ignoreRepository    if `true`, don't read anything from the repository on startup.
 * @param requiresIIIFService if `true`, ensure that the IIIF service is started.
 */
case class InitStartUp(ignoreRepository: Boolean, requiresIIIFService: Boolean) extends ApplicationRequest

/**
 * Acknowledgment message for [[InitStartUp]].
 */
case class InitStartUpAck() extends ApplicationRequest

/**
 * Message for checking whether the triplestore is available. Used only inside the actor itself.
 */
case class CheckTriplestore() extends ApplicationRequest

/**
 * Message for updating the repository to work the current version of Knora. Used only inside the actor itself.
 */
case class UpdateRepository() extends ApplicationRequest

/**
 * Message for initiating cache creation. Used only inside the actor itself.
 */
case class CreateCaches() extends ApplicationRequest

/**
 * Message for updating the triplestore's full-text search index. Used only inside the actor itself.
 */
case class UpdateSearchIndex() extends ApplicationRequest

/**
 * Message for initiating loading of ontologies. Used only inside the actor itself.
 */
case class LoadOntologies() extends ApplicationRequest

/**
 * Message for initiating IIIF Service checking. Used only inside the actor itself.
 */
case object CheckIIIFService extends ApplicationRequest

/**
 * Message for initiating Cache Service checking. Used only inside the actor itself.
 */
case object CheckCacheService extends ApplicationRequest

/**
 * Application States at Startup
 */
sealed trait AppState
object AppState {

  case object Stopped extends AppState

  case object StartingUp extends AppState

  case object WaitingForTriplestore extends AppState

  case object TriplestoreReady extends AppState

  case object UpdatingRepository extends AppState

  case object RepositoryUpToDate extends AppState

  case object CreatingCaches extends AppState

  case object CachesReady extends AppState

  case object UpdatingSearchIndex extends AppState

  case object SearchIndexReady extends AppState

  case object LoadingOntologies extends AppState

  case object OntologiesReady extends AppState

  case object WaitingForIIIFService extends AppState

  case object IIIFServiceReady extends AppState

  case object WaitingForCacheService extends AppState

  case object CacheServiceReady extends AppState

  case object MaintenanceMode extends AppState

  case object Running extends AppState

}
