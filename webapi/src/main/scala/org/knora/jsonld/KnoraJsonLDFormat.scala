/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and Sepideh Alassi.
 * This file is part of Knora.
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.jsonld

import java.util

import scala.annotation.implicitNotFound

/**
  * Provides the JSON-LD deserialization for type T.
  */
@implicitNotFound(msg = "Cannot find KnoraJsonLDReader or KnoraJsonLDFormat type class for ${T}")
trait KnoraJsonLDReader[T] {
    def read(expanded: util.Map[String, Object]): T
}

object KnoraJsonLDReader {
    implicit def func2Reader[T](f: util.Map[String, Object] => T): KnoraJsonLDReader[T] = new KnoraJsonLDReader[T] {
        def read(expanded: util.Map[String, Object]) = f(expanded)
    }
}

/**
  * Provides the JSON-LD serialization for type T.
  */
@implicitNotFound(msg = "Cannot find KnoraJsonLDWriter or KnoraJsonLDFormat type class for ${T}")
trait KnoraJsonLDWriter[T] {
    def write(obj: T): String
}

object KnoraJsonLDWriter {
    implicit def func2Writer[T](f: T => String): KnoraJsonLDWriter[T] = new KnoraJsonLDWriter[T] {
        def write(obj: T) = f(obj)
    }
}

/**
  * Provides the JSON deserialization and serialization for type T.
  */
trait KnoraJsonLDFormat[T] extends KnoraJsonLDReader[T] with KnoraJsonLDWriter[T]

/**
  * A special KnoraJsonLDReader capable of reading a legal JSON-LD root object, i.e. either a JSON array or a JSON object.
  */
@implicitNotFound(msg = "Cannot find RootJsonReader or RootJsonFormat type class for ${T}")
trait KnoraRootJsonLDReader[T] extends KnoraJsonLDReader[T]

/**
  * A special KnoraJsonLDWriter capable of writing a legal JSON root object, i.e. either a JSON array or a JSON object.
  */
@implicitNotFound(msg = "Cannot find RootJsonWriter or RootJsonFormat type class for ${T}")
trait KnoraRootJsonLDWriter[T] extends KnoraJsonLDWriter[T]

/**
  * A special KnoraJsonLDFormat signaling that the format produces a legal JSON root object, i.e. either a JSON array
  * or a JSON object.
  */
trait KnoraRootJsonLDFormat[T] extends KnoraJsonLDFormat[T] with KnoraRootJsonLDReader[T] with KnoraRootJsonLDWriter[T]