package org.knora.webapi.messages.app.appmessages

import org.knora.webapi.messages.app.appmessages.AppState.AppState

sealed trait ApplicationStateRequest

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

case class SetPrintConfigState(value: Boolean) extends ApplicationStateRequest
case class GetPrintConfigState() extends ApplicationStateRequest

case class SetAppState(value: AppState) extends ApplicationStateRequest
case class GetAppState() extends ApplicationStateRequest

/**
  * Application States at Startup
  */
object AppState extends Enumeration {
    type AppState = Value
    val Stopped, StartingUp, WaitingForRepository, RepositoryReady, CreatingCaches, CachesReady, LoadingOntologies, OntologiesReady, MaintenanceMode, Running = Value
}

