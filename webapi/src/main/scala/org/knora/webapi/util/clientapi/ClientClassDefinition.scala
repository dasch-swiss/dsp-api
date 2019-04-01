/*
 * Copyright © 2015-2019 the contributors (see Contributors.md).
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

package org.knora.webapi.util.clientapi

import org.knora.webapi.messages.v2.responder.ontologymessages.Cardinality.Cardinality
import org.knora.webapi.util.SmartIri

sealed trait ClientObjectType

sealed trait ClientLiteral extends ClientObjectType

case object ClientStringLiteral extends ClientLiteral

case object ClientBooleanLiteral extends ClientLiteral

case object ClientIntegerLiteral extends ClientLiteral

case object ClientDecimalLiteral extends ClientLiteral

case class ClientIriLiteral(classIri: SmartIri) extends ClientLiteral

case object ClientDateTimeStampLiteral extends ClientLiteral

sealed trait ClientKnoraValue extends ClientObjectType

case object ClientTextValue extends ClientKnoraValue

case object ClientIntValue extends ClientKnoraValue

case object ClientBooleanValue extends ClientKnoraValue

case object ClientUriValue extends ClientKnoraValue

case object ClientDecimalValue extends ClientKnoraValue

case object ClientDateValue extends ClientKnoraValue

case object ClientColorValue extends ClientKnoraValue

case object ClientGeomValue extends ClientKnoraValue

case object ClientListValue extends ClientKnoraValue

case object ClientIntervalValue extends ClientKnoraValue

case object ClientGeonameValue extends ClientKnoraValue

case object ClientAudioFileValue extends ClientKnoraValue

case object ClientDDDFileValue extends ClientKnoraValue

case object ClientDocumentFileValue extends ClientKnoraValue

case object ClientStillImageFileValue extends ClientKnoraValue

case object ClientMovingImageFileValue extends ClientKnoraValue

case object ClientTextFileValue extends ClientKnoraValue

case class ClientLinkValue(classIri: SmartIri) extends ClientKnoraValue

case class ClientPropertyDefinition(propertyIri: SmartIri, objectType: ClientObjectType, cardinality: Cardinality, isEditable: Boolean)

case class ClientClassDefinition(classIri: SmartIri, properties: Vector[ClientPropertyDefinition])
