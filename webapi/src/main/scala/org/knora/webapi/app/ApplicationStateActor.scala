package org.knora.webapi.app

import akka.actor.{Actor, ActorLogging}
import org.knora.webapi.messages.app.appmessages.AppState.AppState
import org.knora.webapi.messages.app.appmessages._
import org.knora.webapi.{Settings, SettingsImpl}

class ApplicationStateActor extends Actor with ActorLogging {

    log.debug("entered the ApplicationStateActor constructor")

    // the prometheus, zipkin, jaeger, and printConfig flags can be set via application.conf and via command line parameter
    val settings: SettingsImpl = Settings(context.system)

    private var appState: AppState = AppState.Stopped
    private var allowReloadOverHTTPState = false
    private var prometheusReporterState = false
    private var zipkinReporterState = false
    private var jaegerReporterState = false
    private var printConfigState = false

    def receive: PartialFunction[Any, Unit] = {
        case SetAllowReloadOverHTTPState(value) => {
            log.debug("ApplicationStateActor - SetAllowReloadOverHTTPState - value: {}", value)
            allowReloadOverHTTPState = value
        }
        case GetAllowReloadOverHTTPState() => {
            log.debug("ApplicationStateActor - GetAllowReloadOverHTTPState - value: {}", allowReloadOverHTTPState)
            sender ! allowReloadOverHTTPState
        }
        case SetPrometheusReporterState(value) => {
            log.debug("ApplicationStateActor - SetPrometheusReporterState - value: {}", value)
            prometheusReporterState = value
        }
        case GetPrometheusReporterState() => {
            log.debug("ApplicationStateActor - GetPrometheusReporterState - value: {}", prometheusReporterState)
            sender ! (prometheusReporterState | settings.prometheusReporter)
        }
        case SetZipkinReporterState(value) => {
            log.debug("ApplicationStateActor - SetZipkinReporterState - value: {}", value)
            zipkinReporterState = value
        }
        case GetZipkinReporterState() => {
            log.debug("ApplicationStateActor - GetZipkinReporterState - value: {}", zipkinReporterState)
            sender ! (zipkinReporterState | settings.zipkinReporter)
        }
        case SetJaegerReporterState(value) => {
            log.debug("ApplicationStateActor - SetJaegerReporterState - value: {}", value)
            jaegerReporterState = value
        }
        case GetJaegerReporterState() => {
            log.debug("ApplicationStateActor - GetJaegerReporterState - value: {}", jaegerReporterState)
            sender ! (jaegerReporterState | settings.jaegerReporter)
        }
        case SetPrintConfigState(value) => {
            log.debug("ApplicationStateActor - SetPrintConfigState - value: {}", value)
            printConfigState = value
        }
        case GetPrintConfigState() => {
            log.debug("ApplicationStateActor - GetPrintConfigState - value: {}", printConfigState)
            sender ! (printConfigState | settings.printConfig)
        }
        case SetAppState(value: AppState) => {
            log.debug("ApplicationStateActor - SetAppState - value {}", value)
            appState = value
        }
        case GetAppState() => {
            log.debug("ApplicationStateActor - GetAppState - value: {}", appState)
            sender ! appState
        }
    }

    override def postStop(): Unit = {
        super.postStop()
        log.debug("ApplicationStateActor - postStop called")
    }


}
