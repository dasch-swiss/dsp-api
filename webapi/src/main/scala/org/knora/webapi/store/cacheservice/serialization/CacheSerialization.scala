/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.cacheservice.serialization

import com.twitter.chill.MeatLocker
import org.knora.webapi.exceptions.CacheServiceException
import org.knora.webapi.instrumentation.InstrumentationSupport

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, ObjectInputStream, ObjectOutputStream}
import scala.concurrent.{ExecutionContext, Future}

import zio._

case class EmptyByteArray(message: String) extends CacheServiceException(message)

object CacheSerialization {

  /**
   * Serialize objects by using plain java serialization. Java serialization is not
   * capable to serialize all our objects (e.g., UserADM) and that is why we use the
   * [[MeatLocker]], which does some magic and allows our case classes to be
   * serializable.
   *
   * @param value the value we want to serialize as a array of bytes.
   * @tparam A the type parameter of our value.
   */
  def serialize[A](value: A): Task[Array[Byte]] =
    ZIO.attempt {
      val boxedItem: MeatLocker[A]      = MeatLocker[A](value)
      val stream: ByteArrayOutputStream = new ByteArrayOutputStream()
      val oos                           = new ObjectOutputStream(stream)
      oos.writeObject(boxedItem)
      oos.close()
      stream.toByteArray
    }

  /**
   * Deserialize objects by using plain java serialization. Java serialization is not
   * capable to serialize all our objects (e.g., UserADM) and that is why we use the
   * [[MeatLocker]], which does some magic and allows our case classes to be
   * serializable.
   *
   * @tparam A the type parameter of our value.
   */
  def deserialize[A](bytes: Array[Byte]): Task[Option[A]] =
    ZIO.attempt {
      if (bytes.isEmpty) {
        None
      } else {
        val ois = new ObjectInputStream(new ByteArrayInputStream(bytes))
        val box = ois.readObject
        ois.close()
        Some(box.asInstanceOf[MeatLocker[A]].get)
      }
    }
}
