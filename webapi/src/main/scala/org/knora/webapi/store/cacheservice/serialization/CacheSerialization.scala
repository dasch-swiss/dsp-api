/*
 * Copyright © 2015-2021 the contributors (see Contributors.md).
 *
 *  This file is part of the DaSCH Service Platform.
 *
 *  The DaSCH Service Platform  is free software: you can redistribute it
 *  and/or modify it under the terms of the GNU Affero General Public
 *  License as published by the Free Software Foundation, either version 3
 *  of the License, or (at your option) any later version.
 *
 *  The DaSCH Service Platform is distributed in the hope that it will be
 *  useful, but WITHOUT ANY WARRANTY; without even the implied warranty
 *  of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public
 *  License along with the DaSCH Service Platform.  If not, see
 *  <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.store.cacheservice.serialization

import com.twitter.chill.MeatLocker
import org.knora.webapi.exceptions.CacheServiceException
import org.knora.webapi.instrumentation.InstrumentationSupport

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, ObjectInputStream, ObjectOutputStream}
import scala.concurrent.{ExecutionContext, Future}

case class EmptyByteArray(message: String) extends CacheServiceException(message)

object CacheSerialization extends InstrumentationSupport {

  /**
   * Serialize objects by using plain java serialization. Java serialization is not
   * capable to serialize all our objects (e.g., UserADM) and that is why we use the
   * [[MeatLocker]], which does some magic and allows our case classes to be
   * serializable.
   *
   * @param value the value we want to serialize as a array of bytes.
   * @tparam T the type parameter of our value.
   */
  def serialize[T](value: T)(implicit ec: ExecutionContext): Future[Array[Byte]] = tracedFuture("redis-serialize") {

    Future {
      val boxedItem: MeatLocker[T]      = MeatLocker[T](value)
      val stream: ByteArrayOutputStream = new ByteArrayOutputStream()
      val oos                           = new ObjectOutputStream(stream)
      oos.writeObject(boxedItem)
      oos.close()
      stream.toByteArray
    }
  }

  /**
   * Deserialize objects by using plain java serialization. Java serialization is not
   * capable to serialize all our objects (e.g., UserADM) and that is why we use the
   * [[MeatLocker]], which does some magic and allows our case classes to be
   * serializable.
   *
   * @tparam T the type parameter of our value.
   */
  def deserialize[T](bytes: Array[Byte])(implicit ec: ExecutionContext): Future[Option[T]] =
    tracedFuture("redis-deserialize") {

      Future {
        if (bytes.isEmpty) {
          None
        } else {
          val ois = new ObjectInputStream(new ByteArrayInputStream(bytes))
          val box = ois.readObject
          ois.close()
          Some(box.asInstanceOf[MeatLocker[T]].get)
        }
      }

    }
}
