/*
 * Copyright © 2015-2018 the contributors (see Contributors.md).
 *
 *  This file is part of Knora.
 *
 *  Knora is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published
 *  by the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Knora is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public
 *  License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.messages.util

import java.time.Instant

import org.apache.commons.text.StringEscapeUtils
import org.knora.webapi.OntologySchema
import org.knora.webapi.messages.v2.responder.ontologymessages.Cardinality
import org.knora.webapi.messages.SmartIri

import scala.reflect.runtime.{universe => ru}

/**
  * Utility functions for working with Akka messages.
  */
object MessageUtil {

    // Set of case class field names to skip.
    private val fieldsToSkip = Set("stringFormatter", "base64Decoder", "knoraIdUtil", "standoffLinkTagTargetResourceIris")

    /**
      * Recursively converts a Scala object to Scala source code for constructing the object (with named parameters). This is useful
      * for writing tests containing hard-coded Akka messages. It works with case classes, collections ([[Seq]], [[Set]],
      * and [[Map]]), [[Option]], enumerations (as long as the enumeration value's `toString` representation is the same
      * as its identifier), and primitive types. It doesn't work with classes defined inside methods.
      *
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
            case bigDecimal: BigDecimal => bigDecimal.toString
            case boolean: Boolean => boolean.toString
            case char: Char => char.toString
            case byte: Byte => byte.toString
            case s: String => "\"" + StringEscapeUtils.escapeJava(s) + "\""
            case Some(value) => "Some(" + toSource(value) + ")"
            case smartIri: SmartIri => "\"" + StringEscapeUtils.escapeJava(smartIri.toString) + "\".toSmartIri"
            case instant: Instant => "Instant.parse(\"" + instant.toString + "\")"
            case None => "None"

            // Handle enumerations.

            case cardinality: Cardinality.Value =>
                cardinality match {
                    case Cardinality.MayHaveOne => "Cardinality.MayHaveOne"
                    case Cardinality.MayHaveMany => "Cardinality.MayHaveMany"
                    case Cardinality.MustHaveOne => "Cardinality.MustHaveOne"
                    case Cardinality.MustHaveSome => "Cardinality.MustHaveSome"
                }

            case enumVal if enumVal.getClass.getName == "scala.Enumeration$Val" => enumVal.toString

            case ontologySchema: OntologySchema =>
                ontologySchema.getClass.getSimpleName.stripSuffix("$")

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

            // Handle case classes.
            case caseClass: Product =>
                val objClass = obj.getClass
                val objClassName = objClass.getSimpleName

                val fieldMap: Map[String, Any] = caseClass.getClass.getDeclaredFields.foldLeft(Map[String, Any]()) {
                    (acc, field) =>
                        val fieldName = field.getName.trim

                        if (fieldName.contains("$") || fieldName.endsWith("Format") || fieldsToSkip.contains(fieldName)) { // ridiculous hack
                            acc
                        } else {
                            field.setAccessible(true)
                            acc + (field.getName -> field.get(caseClass))
                        }
                }

                val members: Iterable[String] = fieldMap.map {
                    case (fieldName, fieldValue) =>
                        val fieldValueString = toSource(fieldValue)
                        s"$fieldName = $fieldValueString"
                }

                val maybeNewLine = maybeMakeNewLine(members.size)
                members.mkString(objClassName + "(" + maybeNewLine, ", " + maybeNewLine, maybeNewLine + ")")

            // Handle other classes.
            case _ =>
                // Generate a named parameter initializer for each of the class's non-method fields.

                val objClass = obj.getClass
                val objClassName = objClass.getSimpleName

                val runtimeMirror: ru.Mirror = ru.runtimeMirror(objClass.getClassLoader)
                val instanceMirror = runtimeMirror.reflect(obj)
                val objType: ru.Type = runtimeMirror.classSymbol(objClass).toType

                val members: Iterable[String] = objType.members.filter(member => !member.isMethod).flatMap {
                    member =>
                        val memberName = member.name.toString.trim

                        if (!(memberName.contains("$") || memberName.endsWith("Format"))) {
                            val fieldMirror = try {
                                instanceMirror.reflectField(member.asTerm)
                            } catch {
                                case e: Exception => throw new Exception(s"Can't format member $memberName in class $objClassName", e)
                            }

                            val memberValue = fieldMirror.get
                            val memberValueString = toSource(memberValue)
                            Some(s"$memberName = $memberValueString")
                        } else {
                            None
                        }
                }

                val maybeNewLine = maybeMakeNewLine(members.size)
                members.mkString(objClassName + "(" + maybeNewLine, ", " + maybeNewLine, maybeNewLine + ")")
        }
    }
}
