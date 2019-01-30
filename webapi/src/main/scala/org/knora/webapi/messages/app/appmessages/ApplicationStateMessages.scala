package org.knora.webapi.messages.app.appmessages

import org.knora.webapi.messages.app.appmessages.AppState.AppState

sealed trait ApplicationStateRequest

/**
  * Check if actor is ready.
  */
case class ActorReady() extends ApplicationStateRequest

/**
  * Response used to acknowledge that actor is ready.
  */
case class ActorReadyAck()

/**
  * Setter message for storing the LoadDemoData flag.
  */
case class SetLoadDemoDataState(value: Boolean) extends ApplicationStateRequest

/**
  * Getter message for retrieving the LoadDemoData flag value.
  */
case class GetLoadDemoDataState() extends ApplicationStateRequest

/**
  * Setter message for storing the llowReloadOverHTTP flag.
  */
case class SetAllowReloadOverHTTPState(value: Boolean) extends ApplicationStateRequest

/**
  * Getter message for retrieving the llowReloadOverHTTP flag value.
  */
case class GetAllowReloadOverHTTPState() extends ApplicationStateRequest

/**
  * Setter message for storing the rometheusReporter flag.
  */
case class SetPrometheusReporterState(value: Boolean) extends ApplicationStateRequest

/**
  * Getter message for retrieving the rometheusReporter flag value.
  */
case class GetPrometheusReporterState() extends ApplicationStateRequest

/**
  * Setter message for storing the ZipkinReporter flag.
  */
case class SetZipkinReporterState(value: Boolean) extends ApplicationStateRequest

/**
  * Getter message for retrieving the ZipkinReporter flag value.
  */
case class GetZipkinReporterState() extends ApplicationStateRequest

/**
  * Setter message for storing the JaegerReporter flag.
  */
case class SetJaegerReporterState(value: Boolean) extends ApplicationStateRequest

/**
  * Getter message for retrieving the JaegerReporter flag value.
  */
case class GetJaegerReporterState() extends ApplicationStateRequest

/**
  * Setter message for storing the PrintConfigExtended flag.
  */
case class SetPrintConfigExtendedState(value: Boolean) extends ApplicationStateRequest

/**
  * Getter message for retrieving the PrintConfigExtended flag value.
  */
case class GetPrintConfigExtendedState() extends ApplicationStateRequest

/**
  * Setter message for setting the current application state.
  */
case class SetAppState(value: AppState) extends ApplicationStateRequest

/**
  * Message for getting the current application state.
  */
case class GetAppState() extends ApplicationStateRequest

/**
  * Message for initiating the startup sequence.
  *
  * @param withOntologies a boolean value denoting if loading of ontologies should be skipped or not.
  */
case class InitStartUp(withOntologies: Boolean = false) extends ApplicationStateRequest

/**
  * Acknowledgment message for [[InitStartUp]].
  */
case class InitStartUpAck() extends ApplicationStateRequest

/**
  * Message for initiating repository checking. Used only inside the actor itself.
  */
case class CheckRepository() extends ApplicationStateRequest

/**
  * Message for initiating cache creation. Used only inside the actor itself.
  */
case class CreateCaches() extends ApplicationStateRequest

/**
  * Message for initiating loading of ontologies. Used only inside the actor itself.
  */
case class LoadOntologies() extends ApplicationStateRequest

/**
  * Application States at Startup
  */
object AppState extends Enumeration {
    type AppState = Value
    val Stopped, StartingUp, WaitingForRepository, RepositoryReady, CreatingCaches, CachesReady, LoadingOntologies, OntologiesReady, MaintenanceMode, Running = Value
}

