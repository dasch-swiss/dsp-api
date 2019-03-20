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

sealed trait ObjectType

sealed trait Literal extends ObjectType

case object StringLiteral extends Literal
case object BooleanLiteral extends Literal
case object IntegerLiteral extends Literal
case object DecimalLiteral extends Literal
case object IriLiteral extends Literal
case object DateTimeStampLiteral extends Literal

sealed trait KnoraValue extends ObjectType

case object TextValue extends KnoraValue
case object IntValue extends KnoraValue
case object BooleanValue extends KnoraValue
case object UriValue extends KnoraValue
case object DecimalValue extends KnoraValue
case object DateValue extends KnoraValue
case object ColorValue extends KnoraValue
case object GeomValue extends KnoraValue
case object ListValue extends KnoraValue
case object IntervalValue extends KnoraValue
case object LinkValue extends KnoraValue
case object GeonameValue extends KnoraValue
case object AudioFileValue extends KnoraValue
case object DDDFileValue extends KnoraValue
case object DocumentFileValue extends KnoraValue
case object StillImageFileValue extends KnoraValue
case object MovingImageFileValue extends KnoraValue
case object TextFileValue extends KnoraValue

case class PropertyDefinition(propertyName: String, objectType: ObjectType, cardinality: Cardinality)

case class ClassDefinition(className: String, properties: Set[PropertyDefinition])
