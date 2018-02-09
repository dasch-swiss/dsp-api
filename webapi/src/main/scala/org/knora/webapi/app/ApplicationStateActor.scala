package org.knora.webapi.app

import akka.actor.{Actor, ActorLogging}
import org.knora.webapi.messages.app.appmessages.{GetAllowReloadOverHTTPState, GetLoadDemoDataState, SetAllowReloadOverHTTPState, SetLoadDemoDataState}
import org.knora.webapi.messages.store.triplestoremessages.{Initialized, InitializedResponse}

class ApplicationStateActor extends Actor with ActorLogging {

    private var loadDemoDataState = false
    private var allowReloadOverHTTPState = false

    def receive: PartialFunction[Any, Unit] = {
        case Initialized() => sender ! InitializedResponse(true)
        case SetLoadDemoDataState(value) => {
            log.debug("ApplicationStateActor - SetLoadDemoDataState - value: {}", value)
            loadDemoDataState = value
        }
        case GetLoadDemoDataState() => {
            log.debug("ApplicationStateActor - GetLoadDemoDataState - value: {}", this.loadDemoDataState)
            sender ! loadDemoDataState
        }
        case SetAllowReloadOverHTTPState(value) => {
            log.debug("ApplicationStateActor - SetAllowReloadOverHTTPState - value: {}", value)
            allowReloadOverHTTPState = value
        }
        case GetAllowReloadOverHTTPState() => {
            log.debug("ApplicationStateActor - GetAllowReloadOverHTTPState - value: {}", this.allowReloadOverHTTPState)
            sender ! allowReloadOverHTTPState
        }
    }
}
