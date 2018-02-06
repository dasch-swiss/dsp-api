package org.knora.webapi.messages.app.appmessages

sealed trait ApplicationStateRequest

case class SetLoadDemoDataState(value: Boolean) extends ApplicationStateRequest
case class GetLoadDemoDataState() extends ApplicationStateRequest

case class SetAllowReloadOverHTTPState(value: Boolean) extends ApplicationStateRequest
case class GetAllowReloadOverHTTPState() extends ApplicationStateRequest


