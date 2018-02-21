package org.knora.webapi.app

import akka.actor.{Actor, ActorLogging}
import org.knora.webapi.{Settings, SettingsImpl}
import org.knora.webapi.messages.app.appmessages._
import org.knora.webapi.messages.store.triplestoremessages.{Initialized, InitializedResponse}

class ApplicationStateActor extends Actor with ActorLogging {

    // the prometheus, zipkin, and jaeger flags can be set via application.conf and via command line parameter
    val settings: SettingsImpl = Settings(context.system)

    private var loadDemoDataState = false
    private var allowReloadOverHTTPState = false
    private var prometheusReporterState = false
    private var zipkinReporterState = false
    private var jaegerReporterState = false

    def receive: PartialFunction[Any, Unit] = {
        case Initialized() => sender ! InitializedResponse(true)
        case SetLoadDemoDataState(value) => {
            log.debug("ApplicationStateActor - SetLoadDemoDataState - value: {}", value)
            loadDemoDataState = value
        }
        case GetLoadDemoDataState() => {
            log.debug("ApplicationStateActor - GetLoadDemoDataState - value: {}", loadDemoDataState)
            sender ! loadDemoDataState
        }
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
    }
}
