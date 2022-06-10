/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.cache.serialization

import dsp.errors.CacheServiceException
import zio._

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

case class EmptyByteArray(message: String) extends CacheServiceException(message)

object CacheSerialization {

  /**
   * Serialize objects by using Apache commons.
   *
   * @param value the value we want to serialize as a array of bytes.
   * @tparam A the type parameter of our value.
   */
  def serialize[A](value: A): Task[Array[Byte]] =
    ZIO.attempt {
      val stream: ByteArrayOutputStream = new ByteArrayOutputStream()
      val oos                           = new ObjectOutputStream(stream)
      oos.writeObject(value)
      oos.close()
      stream.toByteArray
    }

  /**
   * Deserialize objects by using Apache commons.
   *
   * @tparam A the type parameter of our value.
   */
  def deserialize[A](bytes: Array[Byte]): Task[Option[A]] =
    ZIO.attempt {
      if (bytes.isEmpty) {
        None
      } else {
        val ois   = new ObjectInputStream(new ByteArrayInputStream(bytes))
        val value = ois.readObject
        ois.close()
        Some(value.asInstanceOf[A])
      }
    }
}
