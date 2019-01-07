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

case class SetLoadDemoDataState(value: Boolean) extends ApplicationStateRequest
case class GetLoadDemoDataState() extends ApplicationStateRequest

case class SetAllowReloadOverHTTPState(value: Boolean) extends ApplicationStateRequest
case class GetAllowReloadOverHTTPState() extends ApplicationStateRequest

case class SetPrometheusReporterState(value: Boolean) extends ApplicationStateRequest
case class GetPrometheusReporterState() extends ApplicationStateRequest

case class SetZipkinReporterState(value: Boolean) extends ApplicationStateRequest
case class GetZipkinReporterState() extends ApplicationStateRequest

case class SetJaegerReporterState(value: Boolean) extends ApplicationStateRequest
case class GetJaegerReporterState() extends ApplicationStateRequest

case class SetPrintConfigExtendedState(value: Boolean) extends ApplicationStateRequest
case class GetPrintConfigExtendedState() extends ApplicationStateRequest

case class SetAppState(value: AppState) extends ApplicationStateRequest
case class GetAppState() extends ApplicationStateRequest

case class InitStartUp(withOntologies: Boolean = false) extends ApplicationStateRequest
case class InitStartUpAck() extends ApplicationStateRequest

case class CheckRepository() extends ApplicationStateRequest

case class CreateCaches() extends ApplicationStateRequest

case class LoadOntologies() extends ApplicationStateRequest

/**
  * Application States at Startup
  */
object AppState extends Enumeration {
    type AppState = Value
    val Stopped, StartingUp, WaitingForRepository, RepositoryReady, CreatingCaches, CachesReady, LoadingOntologies, OntologiesReady, MaintenanceMode, Running = Value
}

