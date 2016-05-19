package org.knora.webapi.util

import org.apache.commons.lang3.StringEscapeUtils
import org.knora.webapi._
import org.knora.webapi.messages.v1respondermessages.resourcemessages.{LiteralValueType, ResourceCreateValueObjectResponseV1, ResourceCreateValueResponseV1}
import org.knora.webapi.messages.v1respondermessages.valuemessages._

import scala.reflect.runtime.{universe => ru}

/**
  * Utility functions for working with Akka messages.
  */
object MessageUtil {
    /**
      * Recursively converts a Scala object to Scala source code for constructing the object (with named parameters). This is useful
      * for writing tests containing hard-coded Akka messages. It works with case classes, collections ([[List]], [[Set]],
      * and [[Map]]), [[Option]], enumerations (as long as the enumeration value's `toString` representation is the same
      * as its identifier), and primitive types. It doesn't work with classes defined inside methods.
      * @param obj the object to convert.
      * @return a string that can be pasted into Scala source code to construct the object.
      */
    def toSource(obj: Any): String = {
        def maybeMakeNewLine(elemCount: Int): String = if (elemCount > 1) "\n" else ""

        obj match {
            // Handle primitive types.
            case null => "null"
            case short: Short => short.toString
            case int: Int => int.toString
            case long: Long => long.toString
            case float: Float => float.toString
            case double: Double => double.toString
            case boolean: Boolean => boolean.toString
            case char: Char => char.toString
            case byte: Byte => byte.toString
            case s: String => "\"" + StringEscapeUtils.escapeJava(s) + "\""
            case Some(value) => "Some(" + toSource(value) + ")"
            case None => "None"

            // Handle enumerations.
            case enumVal if enumVal.getClass.getName == "scala.Enumeration$Val" => enumVal.toString

            // Handle collections.

            case Nil => "Nil"

            case list: Seq[Any@unchecked] =>
                val maybeNewLine = maybeMakeNewLine(list.size)
                list.map(elem => toSource(elem)).mkString("Vector(" + maybeNewLine, ", " + maybeNewLine, maybeNewLine + ")")

            case set: Set[Any@unchecked] =>
                val maybeNewLine = maybeMakeNewLine(set.size)
                set.map(elem => toSource(elem)).mkString("Set(" + maybeNewLine, ", " + maybeNewLine, maybeNewLine + ")")

            case map: Map[Any@unchecked, Any@unchecked] =>
                val maybeNewLine = maybeMakeNewLine(map.size)

                map.map {
                    case (key, value) => toSource(key) + " -> " + toSource(value)
                }.mkString("Map(" + maybeNewLine, ", " + maybeNewLine, maybeNewLine + ")")

            // Handle other classes.
            case _ =>
                // Generate a named parameter initializer for each of the class's non-method fields.

                val objClass = obj.getClass
                val objClassName = objClass.getSimpleName
                val runtimeMirror: ru.Mirror = ru.runtimeMirror(objClass.getClassLoader)
                val instanceMirror = runtimeMirror.reflect(obj)
                val objType: ru.Type = runtimeMirror.classSymbol(objClass).toType

                val members: Iterable[String] = objType.members.filter(!_.isMethod).map {
                    member =>
                        val memberName = member.name.toString.trim
                        val fieldMirror = instanceMirror.reflectField(member.asTerm)
                        val memberValue = fieldMirror.get
                        val memberValueString = toSource(memberValue)
                        s"$memberName = $memberValueString"
                }

                val maybeNewLine = maybeMakeNewLine(members.size)
                members.mkString(objClassName + "(" + maybeNewLine, ", " + maybeNewLine, maybeNewLine + ")")
        }
    }

    /**
      * Converts a [[CreateValueResponseV1]] returned by the values responder on resource creation
      * to the expected format for the resources responder [[ResourceCreateValueResponseV1]]
      * @param resourceIri the Iri of the created resource.
      * @param ownerIri the owner of the resource.
      * @param propertyIri the property the valueResponse belongs to.
      * @param valueResponse the value that has been attached to the resource.
      * @return a [[ResourceCreateValueResponseV1]] representing the created value.
      */
    def convertCreateValueResponseV1ToResourceCreateValueResponseV1(resourceIri: IRI,
                                                                    ownerIri: IRI,
                                                                    propertyIri: IRI,
                                                                    valueResponse: CreateValueResponseV1): ResourceCreateValueResponseV1 = {

        // TODO: see resource responder's convertPropertyV1toPropertyGetV1 that also deals with valuetypes

        val basicObjectResponse = ResourceCreateValueObjectResponseV1(
            textval = Map(LiteralValueType.StringValue -> valueResponse.value.toString),
            resource_id = Map(LiteralValueType.StringValue -> resourceIri),
            property_id = Map(LiteralValueType.StringValue -> propertyIri),
            person_id = Map(LiteralValueType.StringValue -> ownerIri),
            order = Map(LiteralValueType.IntegerValue -> 1) // TODO: include correct order: valueHasOrder
        )

        val objectResponse = valueResponse.value match {
            case integerValue: IntegerValueV1 =>
                basicObjectResponse.copy(
                    ival = Some(Map(LiteralValueType.IntegerValue -> integerValue.ival))
                )

            case floatValue: FloatValueV1 =>
                basicObjectResponse.copy(
                    fval = Some(Map(LiteralValueType.FloatValue -> floatValue.fval))
                )

            case dateValue: DateValueV1 =>
                val julianDayCountValue = DateUtilV1.dateValueV1ToJulianDayCountValueV1(dateValue)
                basicObjectResponse.copy(
                    dateval1 = Some(Map(LiteralValueType.StringValue -> dateValue.dateval1)),
                    dateval2 = Some(Map(LiteralValueType.StringValue -> dateValue.dateval2)),
                    dateprecision1 = Some(Map(LiteralValueType.StringValue -> julianDayCountValue.dateprecision1)),
                    dateprecision2 = Some(Map(LiteralValueType.StringValue -> julianDayCountValue.dateprecision2)),
                    calendar = Some(Map(LiteralValueType.StringValue -> julianDayCountValue.calendar))
                )

            case textValue: TextValueV1 => basicObjectResponse

            case linkValue: LinkV1 => basicObjectResponse

            case stillImageFileValue: StillImageFileValueV1 => basicObjectResponse // TODO: implement this.

            case other => throw new Exception(s"Resource creation response format not implemented for value type ${other.valueTypeIri}") // TODO: implement remaining types.
        }

        ResourceCreateValueResponseV1(
            value = objectResponse,
            id = valueResponse.id
        )

    }

    /*

    import org.knora.rapier.messages.v1respondermessages.searchmessages.FulltextSearchGetRequestV1
    import org.knora.rapier.messages.v1respondermessages.usermessages.{UserDataV1, UserProfileV1}

    private val userData = UserDataV1(
        email = Some("test@test.ch"),
        lastname = Some("Test"),
        firstname = Some("User"),
        username = Some("testuser"),
        token = None,
        user_id = Some("http://data.knora.org/users/b83acc5f05"),
        lang = "de"
    )

    private val userProfile = UserProfileV1(
        projects = Vector("http://data.knora.org/projects/77275339"),
        groups = Nil,
        userData = userData
    )

    private val searchForTitleWord = FulltextSearchGetRequestV1(
        filterByRestype = Some("http://www.knora.org/ontology/incunabula#book"),
        filterByProject = None,
        searchValue = "Zeitglöcklein",
        startAt = 0,
        showNRows = 25,
        userProfile = userProfile
    )

    println(toSource(searchForTitleWord))

    private val generated = FulltextSearchGetRequestV1(
        userProfile = UserProfileV1(
            projects = Vector("http://data.knora.org/projects/77275339"),
            groups = Nil,
            userData = UserDataV1(
                email = Some("test@test.ch"),
                lastname = Some("Test"),
                firstname = Some("User"),
                username = Some("testuser"),
                token = None,
                user_id = Some("http://data.knora.org/users/b83acc5f05"),
                lang = "de"
            )
        ),
        showNRows = 25,
        startAt = 0,
        filterByProject = None,
        filterByRestype = Some("http://www.knora.org/ontology/incunabula#book"),
        searchValue = "Zeitglöcklein"
    )

    println(s"The generated source code equals the original: ${generated == searchForTitleWord}")
    */
}
