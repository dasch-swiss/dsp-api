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
  * A trait for enumerated values representing API serialisation formats.
  */
trait ApiSerialisationFormat

/**
  * Indicates that an API uses plain JSON as its serialisation format.
  */
case object Json extends ApiSerialisationFormat

/**
  * Indicates that an API uses JSON-LD as its serialisation format.
  */
case object JsonLD extends ApiSerialisationFormat

/**
  * Represents a client API.
  */
trait ClientApi {
    /**
      * The serialisation format used by the API.
      */
    val serialisationFormat: ApiSerialisationFormat

    /**
      * The machine-readable name of the API.
      */
    val name: String

    /**
     * The name of a directory in which the API code can be generated.
     */
    val directoryName: String

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
    val endpoints: Seq[ClientEndpoint]

    /**
      * The IRIs of the classes used by this API.
      */
    lazy val classIrisUsed: Set[SmartIri] = endpoints.flatMap(_.classIrisUsed).toSet
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
     * The name of a directory in which the endpoint code can be generated.
     */
    val directoryName: String

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
    import scala.language.implicitConversions

    /**
      * Constructs a [[ClientHttpRequest]] for a `GET` request.
      *
      * @param path   the URL path to be used in the request.
      * @param params the query parameters to be used in the request.
      * @return a [[ClientHttpRequest]].
      */
    def httpGet(path: Seq[UrlComponent], params: Seq[(String, Value)] = Seq.empty): ClientHttpRequest =
        http(httpMethod = GET, path = path, params = params, body = None)

    /**
      * Constructs a [[ClientHttpRequest]] for a `POST` request.
      *
      * @param path   the URL path to be used in the request.
      * @param params the query parameters to be used in the request.
      * @param body   the body of the request.
      * @return a [[ClientHttpRequest]].
      */
    def httpPost(path: Seq[UrlComponent], params: Seq[(String, Value)] = Seq.empty, body: Option[HttpRequestBody] = None): ClientHttpRequest =
        http(httpMethod = POST, path = path, params = params, body = body)

    /**
      * Constructs a [[ClientHttpRequest]] for a `PUT` request.
      *
      * @param path   the URL path to be used in the request.
      * @param params the query parameters to be used in the request.
      * @param body   the body of the request.
      * @return a [[ClientHttpRequest]].
      */
    def httpPut(path: Seq[UrlComponent], params: Seq[(String, Value)] = Seq.empty, body: Option[HttpRequestBody] = None): ClientHttpRequest =
        http(httpMethod = PUT, path = path, params = params, body = body)

    /**
      * Constructs a [[ClientHttpRequest]] for a `DELETE` request.
      *
      * @param path   the URL path to be used in the request.
      * @param params the query parameters to be used in the request.
      * @return a [[ClientHttpRequest]].
      */
    def httpDelete(path: Seq[UrlComponent], params: Seq[(String, Value)] = Seq.empty): ClientHttpRequest =
        http(httpMethod = DELETE, path = path, params = params, body = None)

    /**
      * Constructs a [[ClassRef]] for referring to a class in a function definition.
      *
      * @param classIri the IRI of the class.
      * @return a [[ClassRef]] that can be used for referring to the class.
      */
    def classRef(classIri: SmartIri) = ClassRef(className = classIri.getEntityName.capitalize, classIri = classIri)

    /**
      * Constructs a [[StringLiteralValue]].
      *
      * @param value the value of the string literal.
      * @return a [[StringLiteralValue]].
      */
    def str(value: String): StringLiteralValue = StringLiteralValue(value)

    /**
      * A [[BooleanLiteralValue]] with the value `true`.
      */
    val True: BooleanLiteralValue = BooleanLiteralValue(true)

    /**
      * A [[BooleanLiteralValue]] with the value `false`.
      */
    val False: BooleanLiteralValue = BooleanLiteralValue(false)

    /**
      * Constructs an [[EnumDatatype]].
      *
      * @param values the values of the enumeration.
      * @return an [[EnumDatatype]].
      */
    def enum(values: String*): EnumDatatype = EnumDatatype(values.toSet)

    /**
      * Constructs an [[ArgValue]] referring to a function argument.
      *
      * @param name the name of the argument.
      * @return an [[ArgValue]].
      */
    def arg(name: String) = ArgValue(name)

    /**
      * Constructs an [[ArgValue]] referring to a member of a function argument.
      *
      * @param name   the name of the argument.
      * @param member the name of the member of the argument.
      * @return an [[ArgValue]].
      */
    def argMember(name: String, member: String) = ArgValue(name = name, memberVariableName = Some(member))

    /**
      * A URL path representing the base path of an endpoint.
      */
    val BasePath: Seq[UrlComponent] = Seq.empty[UrlComponent]

    /**
      * Constructs a [[JsonRequestBody]].
      *
      * @param pairs the key-value pairs to be included in the JSON.
      * @return a [[JsonRequestBody]].
      */
    def json(pairs: (String, Value)*): JsonRequestBody = JsonRequestBody(pairs)

    private def http(httpMethod: ClientHttpMethod, path: Seq[UrlComponent], params: Seq[(String, Value)], body: Option[HttpRequestBody] = None): ClientHttpRequest = {
        // Collapse each run of strings into a single string.
        val collapsedPath: Seq[UrlComponent] = if (path.isEmpty) {
            path
        } else {
            (SlashUrlComponent +: path).foldLeft(Vector.empty[UrlComponent]) {
                case (acc, component) =>
                    acc.lastOption match {
                        case Some(last) =>
                            (last, component) match {
                                case (lastStr: StringLiteralValue, thisStr: StringLiteralValue) =>
                                    acc.dropRight(1) :+ StringLiteralValue(lastStr.value + thisStr.value)

                                case (SlashUrlComponent, thisStr: StringLiteralValue) =>
                                    acc.dropRight(1) :+ StringLiteralValue("/" + thisStr.value)

                                case (lastStr: StringLiteralValue, SlashUrlComponent) =>
                                    acc.dropRight(1) :+ StringLiteralValue(lastStr.value + "/")

                                case _ => acc :+ component
                            }

                        case None => acc :+ component
                    }
            }
        }

        ClientHttpRequest(httpMethod = httpMethod, urlPath = collapsedPath, params = params, requestBody = body)
    }

    implicit class Identifier(val name: String) extends AnyVal {
        def description(desc: String): NameWithDescription = NameWithDescription(name = name, description = desc)
    }

    implicit def urlComponentToSeq(urlComponent: UrlComponent): Seq[UrlComponent] = Seq(urlComponent)

    implicit class UrlComponentAsSeq(val urlComponent: UrlComponent) extends AnyVal {
        def /(nextComponent: UrlComponent): Seq[UrlComponent] = Seq(urlComponent, SlashUrlComponent, nextComponent)
    }

    implicit class UrlComponentSeq(val urlComponents: Seq[UrlComponent]) extends AnyVal {
        def /(nextComponent: UrlComponent): Seq[UrlComponent] = urlComponents :+ SlashUrlComponent :+ nextComponent
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
            isOptional = false,
            description = description
        )

        def paramOptionType(objectType: ClientObjectType): FunctionParam = FunctionParam(
            name = name,
            objectType = objectType,
            isOptional = true,
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
  * @param isOptional  `true` if the parameter is optional.
  * @param description a human-readable description of the parameter.
  */
case class FunctionParam(name: String,
                         objectType: ClientObjectType,
                         isOptional: Boolean,
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
  * @param params      the URL parameters to be included.
  * @param requestBody if provided, the body of the HTTP request.
  */
case class ClientHttpRequest(httpMethod: ClientHttpMethod,
                             urlPath: Seq[UrlComponent],
                             params: Seq[(String, Value)],
                             requestBody: Option[HttpRequestBody] = None) extends FunctionImplementation {
    /**
      * Represents all URL elements, including query parameters, as a sequence of [[Value]] objects
      * for concatenation.
      */
    lazy val urlElementsAsValues: Seq[Value] = urlPath.map {
        case SlashUrlComponent => StringLiteralValue("/")
        case value: Value => value
    } ++ paramsAsValues

    /**
      * Represents the URL query parameters as a sequence of [[Value]] objects for concatenation.
      */
    lazy val paramsAsValues: Seq[Value] = {
        if (params.isEmpty) {
            Seq.empty
        } else {
            val pairsAsValues = params.foldLeft(Seq.empty[Value]) {
                case (acc: Seq[Value], (paramName: String, paramValue: Value)) =>
                    val pairAsValues: Seq[Value] = Seq(StringLiteralValue(paramName), StringLiteralValue("="), paramValue)

                    if (acc.isEmpty) {
                        pairAsValues
                    } else {
                        (acc :+ StringLiteralValue("&")) ++ pairAsValues
                    }
            }

            (StringLiteralValue("?") +: pairsAsValues).foldLeft(Vector.empty[Value]) {
                case (acc, value) =>
                    acc.lastOption match {
                        case Some(last) =>
                            (last, value) match {
                                case (lastStr: StringLiteralValue, thisStr: StringLiteralValue) =>
                                    acc.dropRight(1) :+ StringLiteralValue(lastStr.value + thisStr.value)

                                case _ => acc :+ value
                            }

                        case None => acc :+ value
                    }
            }
        }
    }
}

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
  * Represents HTTP DELETE.
  */
case object DELETE extends ClientHttpMethod

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
  * Represents a literal value.
  */
trait LiteralValue extends Value

/**
  * Represents a string literal value.
  *
  * @param value the value of the string literal.
  */
case class StringLiteralValue(value: String) extends LiteralValue {
    override def toString: String = value
}

/**
  * Represents a boolean literal value.
  *
  * @param value the value of the boolean literal.
  */
case class BooleanLiteralValue(value: Boolean) extends LiteralValue {
    override def toString: String = value.toString
}

/**
  * Represents an integer literal value.
  *
  * @param value the value of the integer literal.
  */
case class IntegerLiteralValue(value: Int) extends LiteralValue {
    override def toString: String = value.toString
}

/**
  * Represents a function argument.
  *
  * @param name               the name of the parameter whose value is the argument.
  * @param memberVariableName if provided, the name of a member variable of the argument, to be used instead of
  *                           the argument itself.
  * @param convertTo          if provided, the type that the argument should be converted to.
  */
case class ArgValue(name: String, memberVariableName: Option[String] = None, convertTo: Option[ClientObjectType] = None) extends Value with HttpRequestBody {
    def as(convertTo: ClientObjectType):ArgValue = copy(convertTo = Some(convertTo))
}

/**
  * Represents the body of an HTTP request.
  */
trait HttpRequestBody

/**
  * Represents a JSON object to be sent as an HTTP request body.
  *
  * @param jsonObject the keys and values of the object.
  */
case class JsonRequestBody(jsonObject: Seq[(String, Value)]) extends HttpRequestBody

/**
  * A definition of a Knora API class, which can be used by a [[GeneratorBackEnd]] to generate client code.
  *
  * @param className        the name of the class.
  * @param classDescription a description of the class.
  * @param classIri         the IRI of the class in the Knora API.
  * @param properties       definitions of the properties used in the class.
  */
case class ClientClassDefinition(className: String,
                                 classDescription: Option[String],
                                 classIri: SmartIri,
                                 properties: Vector[ClientPropertyDefinition]) {
    /**
      * The classes used by this class.
      */
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
  * @param propertyName        the name of the property.
  * @param propertyDescription a description of the property.
  * @param propertyIri         the IRI of the property in the Knora API.
  * @param objectType          the type of object that the property points to.
  * @param cardinality         the cardinality of the property in the class.
  * @param isEditable          `true` if the property's value is editable via the API.
  */
case class ClientPropertyDefinition(propertyName: String,
                                    propertyDescription: Option[String],
                                    propertyIri: SmartIri,
                                    objectType: ClientObjectType,
                                    cardinality: Cardinality,
                                    isEditable: Boolean)

/**
  * A trait for types used in client API endpoints.
  */
sealed trait ClientObjectType

/**
  * A trait for datatypes.
  */
sealed trait ClientDatatype extends ClientObjectType

/**
  * The type of string datatype values.
  */
case object StringDatatype extends ClientDatatype

/**
  * The type of boolean datatype values.
  */
case object BooleanDatatype extends ClientDatatype

/**
  * The type of integer datatype values.
  */
case object IntegerDatatype extends ClientDatatype

/**
  * The type of decimal datatype values.
  */
case object DecimalDatatype extends ClientDatatype

/**
  * The type of URI datatype values.
  */
case object UriDatatype extends ClientDatatype

/**
  * The type of timestamp datatype values.
  */
case object DateTimeStampDatatype extends ClientDatatype

/**
  * The type of enums.
  *
  * @param values the values of the enum.
  */
case class EnumDatatype(values: Set[String]) extends ClientDatatype

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
