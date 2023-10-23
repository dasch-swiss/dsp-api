/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resourceinfo.api.model

import sttp.tapir.Codec
import sttp.tapir.CodecFormat.TextPlain
import sttp.tapir.DecodeResult

import dsp.errors.BadRequestException

object QueryParams {

  sealed trait WithUrlParam { self =>
    def urlParam: String = self.getClass.getSimpleName.stripSuffix("$")
  }

  private def decode[A <: WithUrlParam](value: String, allValues: List[A]): DecodeResult[A] =
    allValues
      .find(_.urlParam.equalsIgnoreCase(value))
      .fold[DecodeResult[A]](
        DecodeResult.Error(value, BadRequestException(s"Expected one of ${allValues.map(_.urlParam.mkString)}"))
      )(DecodeResult.Value(_))

  sealed trait OrderBy             extends WithUrlParam
  case object CreationDate         extends OrderBy
  case object LastModificationDate extends OrderBy

  object OrderBy {

    val queryParamKey = "orderBy"

    private val allValues = List(CreationDate, LastModificationDate)

    implicit val tapirCodec: Codec[String, OrderBy, TextPlain] =
      Codec.string.mapDecode(decode[OrderBy](_, allValues))(_.urlParam)
  }

  sealed trait Order extends WithUrlParam
  case object Asc    extends Order
  case object Desc   extends Order

  object Order {

    val queryParamKey = "order"

    private val allValues = List(Asc, Desc)

    implicit val tapirCodec: Codec[String, Order, TextPlain] =
      Codec.string.mapDecode(decode[Order](_, allValues))(_.urlParam)
  }
}
