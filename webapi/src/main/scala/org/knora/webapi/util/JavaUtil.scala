/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and Sepideh Alassi.
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

package org.knora.webapi.util

import java.util.function.BiFunction

/**
  * Utility functions for working with Java libraries.
  */
object JavaUtil {

    /**
      * Converts a 2-argument Scala function into a Java [[BiFunction]].
      *
      * @param f the Scala function.
      * @return a [[BiFunction]] that calls the Scala function.
      */
    def biFunction[A, B, C](f: (A, B) => C): BiFunction[A, B, C] =
        (a: A, b: B) => f(a, b)

    /**
      * Recursively converts a Java collection into a Scala collection.
      *
      * Usage: val scalaObj = deepScalaToJava(javaObj).asInstanceOf[Map[String, Any]]
      *
      * @param javaObj the Java collection to be converted.
      * @return an equivalent Scala collection.
      */
    def deepJavatoScala(javaObj: Any): Any = {
        import collection.JavaConverters._
        javaObj match {
            case x: java.util.HashMap[_, _] => x.asScala.toMap.mapValues(deepJavatoScala)
            case x: java.util.ArrayList[_] => x.asScala.toList.map(deepJavatoScala)
            case _ => javaObj
        }
    }

    /**
      * Recursively converts a Scala collection into a Java collection.
      *
      * @param scalaCollection the Scala collection to be converted.
      * @return an equivalent Java collection.
      */
    def deepScalaToJava(scalaCollection: Any): Any = {
        import collection.JavaConverters._

        scalaCollection match {
            case x: List[_] => x.map(deepScalaToJava).asJava
            case x: Seq[_] => x.map(deepScalaToJava).asJava
            case x: Array[_] => x.map(deepScalaToJava)
            case x: collection.mutable.Map[_, _] => x.mapValues(deepScalaToJava).asJava
            case x: collection.immutable.Map[_, _] => x.mapValues(deepScalaToJava).asJava
            case x: collection.Map[_, _] => x.mapValues(deepScalaToJava).asJava
            case _ => scalaCollection
        }
    }
}
