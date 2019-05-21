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


/**
  * A definition of a Knora API class, which can be used by a [[GeneratorBackEnd]] to generate client code.
  *
  * @param className  the name of the class.
  * @param classIri   the IRI of the class in the Knora API.
  * @param properties definitions of the properties used in the class.
  */
case class ClientClassDefinition(className: String, classIri: SmartIri, properties: Vector[ClientPropertyDefinition])

/**
  * A definition of a Knora property as used in a particular class.
  *
  * @param propertyName the name of the property.
  * @param propertyIri the IRI of the property in the Knora API.
  * @param objectType the type of object that the property points to.
  * @param cardinality the cardinality of the property in the class.
  * @param isEditable `true` if the property's value is editable via the API.
  */
case class ClientPropertyDefinition(propertyName: String, propertyIri: SmartIri, objectType: ClientObjectType, cardinality: Cardinality, isEditable: Boolean)

/**
  * A trait for types of objects of properties.
  */
sealed trait ClientObjectType

/**
  * A trait for literal types used as objects of properties.
  */
sealed trait ClientLiteral extends ClientObjectType

/**
  * The type of string literal property objects.
  */
case object ClientStringLiteral extends ClientLiteral

/**
  * The type of boolean literal property objects.
  */
case object ClientBooleanLiteral extends ClientLiteral

/**
  * The type of integer literal property objects.
  */
case object ClientIntegerLiteral extends ClientLiteral

/**
  * The type of decimal literal property objects.
  */
case object ClientDecimalLiteral extends ClientLiteral

/**
  * The type of timestamp literal property objects.
  */
case object ClientDateTimeStampLiteral extends ClientLiteral

/**
  * The type of property objects that are instances of classes.
  *
  * @param className the name of the class.
  * @param classIri the IRI of the class.
  */
case class ClientClassReference(className: String, classIri: SmartIri) extends ClientObjectType

/**
  * A trait for Knora value types used as objects of properties.
  */
sealed trait ClientKnoraValue extends ClientObjectType

/**
  * The type of text value property objects.
  */
case object ClientTextValue extends ClientKnoraValue

/**
  * The type of integer value property objects.
  */
case object ClientIntValue extends ClientKnoraValue

/**
  * The type of boolean value property objects.
  */
case object ClientBooleanValue extends ClientKnoraValue

/**
  * The type of URI value property objects.
  */
case object ClientUriValue extends ClientKnoraValue

/**
  * The type of decimal value property objects.
  */
case object ClientDecimalValue extends ClientKnoraValue

/**
  * The type of date value property objects.
  */
case object ClientDateValue extends ClientKnoraValue

/**
  * The type of color value property objects.
  */
case object ClientColorValue extends ClientKnoraValue

/**
  * The type of geometry value property objects.
  */
case object ClientGeomValue extends ClientKnoraValue

/**
  * The type of list value property objects.
  */
case object ClientListValue extends ClientKnoraValue

/**
  * The type of interval value property objects.
  */
case object ClientIntervalValue extends ClientKnoraValue

/**
  * The type of Geoname value property objects.
  */
case object ClientGeonameValue extends ClientKnoraValue

/**
  * The type of audio file value property objects.
  */
case object ClientAudioFileValue extends ClientKnoraValue

/**
  * The type of 3D file value property objects.
  */
case object ClientDDDFileValue extends ClientKnoraValue

/**
  * The type of document file value property objects.
  */
case object ClientDocumentFileValue extends ClientKnoraValue

/**
  * The type of still image file value property objects.
  */
case object ClientStillImageFileValue extends ClientKnoraValue

/**
  * The type of moving image value property objects.
  */
case object ClientMovingImageFileValue extends ClientKnoraValue

/**
  * The type of text file value property objects.
  */
case object ClientTextFileValue extends ClientKnoraValue

/**
  * The type of link value property objects.
  *
  * @param classIri the IRI of the class that is the target of the link.
  */
case class ClientLinkValue(classIri: SmartIri) extends ClientKnoraValue
