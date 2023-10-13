package org.knora.webapi.slice.resourceinfo.api.model

import sttp.tapir.Codec
import sttp.tapir.CodecFormat.TextPlain
import sttp.tapir.DecodeResult

import dsp.errors.BadRequestException

object QueryParams {

  sealed trait OrderBy {
    def urlParam: String
  }

  case object creationDate extends OrderBy {
    override val urlParam: String = "creationDate"
  }

  case object lastModificationDate extends OrderBy {
    override val urlParam: String = "lastModificationDate"
  }

  object OrderBy {
    val queryParamKey = "orderBy"

    def fromString(str: String): Either[String, OrderBy] = str match {
      case creationDate.urlParam         => Right(creationDate)
      case lastModificationDate.urlParam => Right(lastModificationDate)
      case _                             => Left(s"Unknown $queryParamKey parameter: $str")
    }

    implicit val tapirCodec: Codec[String, OrderBy, TextPlain] =
      Codec.string.mapDecode(str =>
        OrderBy
          .fromString(str)
          .fold(err => DecodeResult.Error(err, BadRequestException(err)), DecodeResult.Value(_))
      )(_.urlParam)
  }

  sealed trait Order {
    def urlParam: String
  }

  case object ASC extends Order {
    override val urlParam: String = "ASC"
  }

  case object DESC extends Order {
    override val urlParam: String = "DESC"
  }

  object Order {

    val queryParamKey = "order"

    def fromString(str: String): Either[String, Order] = str match {
      case ASC.urlParam  => Right(ASC)
      case DESC.urlParam => Right(DESC)
      case _             => Left(s"Unknown $queryParamKey parameter: $str")
    }

    implicit val tapirCodec: Codec[String, Order, TextPlain] =
      Codec.string.mapDecode(str =>
        Order
          .fromString(str)
          .fold(err => DecodeResult.Error(err, BadRequestException(err)), DecodeResult.Value(_))
      )(_.urlParam)
  }
}
