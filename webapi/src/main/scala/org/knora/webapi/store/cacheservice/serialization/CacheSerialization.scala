/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.cacheservice.serialization

import org.knora.webapi.exceptions.CacheServiceException
import org.knora.webapi.instrumentation.InstrumentationSupport

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, ObjectInputStream, ObjectOutputStream}
import scala.concurrent.{ExecutionContext, Future}

import zio._
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.admin.responder.groupsmessages.GroupADM
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.springframework.util.SerializationUtils

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
      SerializationUtils.serialize(value.isInstanceOf[Serializable])
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
        Some(SerializationUtils.deserialize(bytes).asInstanceOf[A])
      }
    }
}
