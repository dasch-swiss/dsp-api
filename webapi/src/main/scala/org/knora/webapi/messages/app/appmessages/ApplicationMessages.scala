package org.knora.webapi.messages.app.appmessages

import org.knora.webapi.messages.app.appmessages.AppState.AppState

sealed trait ApplicationRequest

/**
 * Start Application
 *
 * @param ignoreRepository    if `true`, don't read anything from the repository on startup.
 * @param requiresIIIFService if `true`, ensure that the IIIF service is started.
 */
case class AppStart(ignoreRepository: Boolean, requiresIIIFService: Boolean) extends ApplicationRequest

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
object AppState extends Enumeration {
    type AppState = Value
    val Stopped, StartingUp,
    WaitingForTriplestore, TriplestoreReady,
    UpdatingRepository, RepositoryUpToDate,
    CreatingCaches, CachesReady,
    UpdatingSearchIndex, SearchIndexReady,
    LoadingOntologies, OntologiesReady,
    WaitingForIIIFService, IIIFServiceReady,
    WaitingForCacheService, CacheServiceReady,
    MaintenanceMode, Running = Value
}
