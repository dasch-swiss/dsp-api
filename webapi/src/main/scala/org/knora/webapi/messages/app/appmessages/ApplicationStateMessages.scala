/*
 * Copyright © 2015-2018 the contributors (see Contributors.md).
 *
 * This file is part of Knora.
 *
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.messages.app.appmessages

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

