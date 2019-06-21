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
  * Represents a client API.
  */
trait ClientApi {
    /**
      * The machine-readable name of the API.
      */
    val name: String

    /**
      * The URL path of the API.
      */
    val urlPath: String

    /**
      * A human-readable description of the API.
      */
    val description: String

    /**
      * The endpoints available in the API.
      */
    val endpoints: Set[ClientEndpoint]

    /**
      * The IRIs of the classes used by this API.
      */
    lazy val classIrisUsed: Set[SmartIri] = endpoints.flatMap(_.classIrisUsed)
}

/**
  * Represents a client endpoint.
  */
trait ClientEndpoint {
    /**
      * The machine-readable name of the endpoint.
      */
    val name: String

    /**
      * The URL path of the endpoint, relative to its API path.
      */
    val urlPath: String

    /**
      * A human-readable description of the endpoint.
      */
    val description: String

    /**
      * The functions provided by the endpoint.
      */
    val functions: Seq[ClientFunction]

    /**
      * The IRIs of the classes used by this endpoint.
      */
    lazy val classIrisUsed: Set[SmartIri] = functions.flatMap {
        function =>
            val maybeReturnedClass: Option[SmartIri] = function.returnType match {
                case classRef: ClassRef => Some(classRef.classIri)
                case _ => None
            }

            val paramClasses: Set[SmartIri] = function.params.map {
                param => param.objectType
            }.collect {
                case classRef: ClassRef => classRef.classIri
            }.toSet

            paramClasses ++ maybeReturnedClass
    }.toSet
}

/**
  * A DSL for defining functions in client endpoints.
  */
object EndpointFunctionDSL {
    def function(clientFunction: ClientFunction): ClientFunction = clientFunction

    def httpGet(path: Seq[UrlComponent]): ClientHttpRequest =
        http(httpMethod = GET, path = path, body = None)

    def httpPost(path: Seq[UrlComponent], body: HttpRequestBody): ClientHttpRequest =
        http(httpMethod = POST, path = path, body = Some(body))

    def httpPut(path: Seq[UrlComponent], body: HttpRequestBody): ClientHttpRequest =
        http(httpMethod = PUT, path = path, body = Some(body))

    def classRef(classIri: SmartIri) = ClassRef(className = classIri.getEntityName.capitalize, classIri = classIri)

    def str(value: String): StringLiteralValue = StringLiteralValue(value)

    val True: BooleanLiteralValue = BooleanLiteralValue(true)

    val False: BooleanLiteralValue = BooleanLiteralValue(false)

    def enum(possibleValues: String*): EnumLiteral = EnumLiteral(possibleValues.toSet)

    def arg(name: String) = ArgValue(name)

    def argMember(name: String, member: String) = ArgValue(name = name, memberVariableName = Some(member))

    val emptyPath = Seq.empty[UrlComponent]

    def json(pairs: (String, Value)*): JsonRequestBody = JsonRequestBody(pairs.toMap)

    private def http(httpMethod: ClientHttpMethod, path: Seq[UrlComponent], body: Option[HttpRequestBody] = None): ClientHttpRequest =
        ClientHttpRequest(httpMethod = httpMethod, urlPath = path, requestBody = body)

    implicit class Identifier(val name: String) extends AnyVal {
        def description(desc: String): NameWithDescription = NameWithDescription(name = name, description = desc)
    }

    implicit class UrlComponentAsSeq(val urlComponent: UrlComponent) extends AnyVal {
        def /(nextComponent: UrlComponent): Seq[UrlComponent] = Seq(urlComponent, SlashUrlComponent, nextComponent)
    }

    implicit class UrlComponentSeq(val urlComponents: Seq[UrlComponent]) extends AnyVal {
        def /(nextComponent: UrlComponent): Seq[UrlComponent] = urlComponents :+ nextComponent
    }

    case class NameWithDescription(name: String, description: String) {
        def params(paramList: FunctionParam*): NameWithDescriptionAndParams = NameWithDescriptionAndParams(
            name = name,
            description = description,
            paramList = paramList
        )

        def paramType(objectType: ClientObjectType): FunctionParam = FunctionParam(
            name = name,
            objectType = objectType,
            description = description
        )
    }

    case class NameWithDescriptionAndParams(name: String, description: String, paramList: Seq[FunctionParam]) {
        def doThis(implementation: FunctionImplementation): NameWithDescriptionParamsAndImplementation = NameWithDescriptionParamsAndImplementation(
            name = name,
            description = description,
            paramList = paramList,
            implementation = implementation
        )
    }

    case class NameWithDescriptionParamsAndImplementation(name: String, description: String, paramList: Seq[FunctionParam], implementation: FunctionImplementation) {
        def returns(clientObjectType: ClientObjectType): ClientFunction = ClientFunction(
            name = name,
            params = paramList,
            returnType = clientObjectType,
            implementation = implementation,
            description = description
        )
    }
}

/**
  * Represents a client endpoint function.
  *
  * @param name           the name of the function.
  * @param params         the parameters of the function.
  * @param returnType     the function's return type.
  * @param implementation the implementation of the function.
  * @param description    a human-readable description of the function.
  */
case class ClientFunction(name: String,
                          params: Seq[FunctionParam],
                          returnType: ClientObjectType,
                          implementation: FunctionImplementation,
                          description: String) {
    def withArgs(args: Value*): FunctionCall = FunctionCall(name = name, args = args)
}

/**
  * Represents a function parameter.
  *
  * @param name        the name of the parameter.
  * @param objectType  the type of the parameter.
  * @param description a human-readable description of the parameter.
  */
case class FunctionParam(name: String,
                         objectType: ClientObjectType,
                         description: String)

/**
  * Represents the implementation of a client endpoint function.
  */
trait FunctionImplementation

/**
  * Represents an HTTP request to be used as the implementation of a client function.
  *
  * @param httpMethod  the HTTP method to be used.
  * @param urlPath     the URL path to be used.
  * @param requestBody if provided, the body of the HTTP request.
  */
case class ClientHttpRequest(httpMethod: ClientHttpMethod, urlPath: Seq[UrlComponent], requestBody: Option[HttpRequestBody] = None) extends FunctionImplementation

/**
  * Represents a function call to be used as the implementation of a client endpoint function.
  *
  * @param name the name of the function to be called.
  * @param args the arguments to be passed to the function call.
  */
case class FunctionCall(name: String, args: Seq[Value]) extends FunctionImplementation

/**
  * Indicates the HTTP method used in a client endpoint function.
  */
trait ClientHttpMethod

/**
  * Represents HTTP GET.
  */
case object GET extends ClientHttpMethod

/**
  * Represents HTTP POST.
  */
case object POST extends ClientHttpMethod

/**
  * Represents HTTP PUT.
  */
case object PUT extends ClientHttpMethod

/**
  * Represents part of a URL path to be constructed for a client endpoint function.
  */
trait UrlComponent

/**
  * Represents a `/` character in a URL path.
  */
case object SlashUrlComponent extends UrlComponent

/**
  * Represents a value used in a function.
  */
trait Value extends UrlComponent

/**
  * Represents a string literal value.
  *
  * @param value the value of the string literal.
  */
case class StringLiteralValue(value: String) extends Value

/**
  * Represents a boolean literal value.
  *
  * @param value the value of the boolean literal.
  */
case class BooleanLiteralValue(value: Boolean) extends Value

/**
  * Represents a function argument.
  *
  * @param name               the name of the parameter whose value is the argument.
  * @param memberVariableName if provided, the name of a member variable of the argument, to be used instead of
  *                           the argument itself.
  */
case class ArgValue(name: String, memberVariableName: Option[String] = None) extends Value with HttpRequestBody

/**
  * Represents the body of an HTTP request.
  */
trait HttpRequestBody

/**
  * Represents a JSON object to be sent as an HTTP request body.
  *
  * @param jsonObject the keys and values of the object.
  */
case class JsonRequestBody(jsonObject: Map[String, Value]) extends HttpRequestBody

/**
  * A definition of a Knora API class, which can be used by a [[GeneratorBackEnd]] to generate client code.
  *
  * @param className  the name of the class.
  * @param classIri   the IRI of the class in the Knora API.
  * @param properties definitions of the properties used in the class.
  */
case class ClientClassDefinition(className: String,
                                 classIri: SmartIri,
                                 properties: Vector[ClientPropertyDefinition]) {
    lazy val classObjectTypesUsed: Set[ClassRef] = properties.foldLeft(Set.empty[ClassRef]) {
        (acc, property) =>
            property.objectType match {
                case classRef: ClassRef => acc + classRef
                case _ => acc
            }
    }
}

/**
  * A definition of a Knora property as used in a particular class.
  *
  * @param propertyName the name of the property.
  * @param propertyIri  the IRI of the property in the Knora API.
  * @param objectType   the type of object that the property points to.
  * @param cardinality  the cardinality of the property in the class.
  * @param isEditable   `true` if the property's value is editable via the API.
  */
case class ClientPropertyDefinition(propertyName: String,
                                    propertyIri: SmartIri,
                                    objectType: ClientObjectType,
                                    cardinality: Cardinality,
                                    isEditable: Boolean)

/**
  * A trait for types used in client API endpoints.
  */
sealed trait ClientObjectType

/**
  * A trait for literal types.
  */
sealed trait ClientLiteral extends ClientObjectType

/**
  * The type of string literals.
  */
case object StringLiteral extends ClientLiteral

/**
  * The type of boolean literals.
  */
case object BooleanLiteral extends ClientLiteral

/**
  * The type of integer literals.
  */
case object IntegerLiteral extends ClientLiteral

/**
  * The type of decimal literals.
  */
case object DecimalLiteral extends ClientLiteral

/**
  * The type of URI literals.
  */
case object UriLiteral extends ClientLiteral

/**
  * The type of timestamp literals.
  */
case object DateTimeStampLiteral extends ClientLiteral

/**
  * The type of enums.
  *
  * @param possibleValues the possible values of the enum.
  */
case class EnumLiteral(possibleValues: Set[String]) extends ClientLiteral

/**
  * The type of instances of classes.
  *
  * @param className the name of the class.
  * @param classIri  the IRI of the class.
  */
case class ClassRef(className: String, classIri: SmartIri) extends ClientObjectType

/**
  * A trait for Knora value types.
  */
sealed trait KnoraVal extends ClientObjectType

/**
  * The type of abstract Knora values.
  */
case object AbstractKnoraVal extends KnoraVal

/**
  * The type of text values.
  */
case object TextVal extends KnoraVal

/**
  * The type of integer values.
  */
case object IntVal extends KnoraVal

/**
  * The type of boolean values.
  */
case object BooleanVal extends KnoraVal

/**
  * The type of URI values.
  */
case object UriVal extends KnoraVal

/**
  * The type of decimal values.
  */
case object DecimalVal extends KnoraVal

/**
  * The type of date values.
  */
case object DateVal extends KnoraVal

/**
  * The type of color values.
  */
case object ColorVal extends KnoraVal

/**
  * The type of geometry values.
  */
case object GeomVal extends KnoraVal

/**
  * The type of list values.
  */
case object ListVal extends KnoraVal

/**
  * The type of interval values.
  */
case object IntervalVal extends KnoraVal

/**
  * The type of Geoname values.
  */
case object GeonameVal extends KnoraVal

/**
  * The type of audio file values.
  */
case object AudioFileVal extends KnoraVal

/**
  * The type of 3D file values.
  */
case object DDDFileVal extends KnoraVal

/**
  * The type of document file values.
  */
case object DocumentFileVal extends KnoraVal

/**
  * The type of still image file values.
  */
case object StillImageFileVal extends KnoraVal

/**
  * The type of moving image values.
  */
case object MovingImageFileVal extends KnoraVal

/**
  * The type of text file values.
  */
case object TextFileVal extends KnoraVal

/**
  * The type of link values.
  *
  * @param classIri the IRI of the class that is the target of the link.
  */
case class LinkVal(classIri: SmartIri) extends KnoraVal
