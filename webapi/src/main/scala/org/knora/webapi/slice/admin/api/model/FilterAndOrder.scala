/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api.model

import sttp.tapir.*

case class FilterAndOrder(filter: Option[String], order: Order) { self =>

  def ordering[A](using o: Ordering[A]): Ordering[A] = self.order match {
    case Order.Asc  => o
    case Order.Desc => o.reverse
  }
}

object FilterAndOrder {
  private val filterQueryParam =
    query[Option[String]]("filter")
      .description("Filter the results.")
      .default(None)

  val queryParams: EndpointInput[FilterAndOrder] =
    filterQueryParam.and(Order.queryParam).mapTo[FilterAndOrder]
}

enum Order {
  case Asc  extends Order
  case Desc extends Order

  def toQueryString: String = this match {
    case Asc  => "ASC"
    case Desc => "DESC"
  }
}

object Order {
  given Codec[String, Order, CodecFormat.TextPlain] = Codec.string.mapEither(from)(_.toString)

  val queryParam: EndpointInput.Query[Order] = query[Order]("order")
    .description("Sort the results in ascending (asc) or descending (desc) order.")
    .default(Order.Asc)

  def from(str: String): Either[String, Order] =
    Order.values
      .find(_.toString.equalsIgnoreCase(str))
      .toRight(s"Invalid order: possible values are '${Order.values.map(_.toString.toLowerCase).mkString(", ")}'")
}

enum OrderBy {
  case CreationDate         extends OrderBy
  case LastModificationDate extends OrderBy
}

object OrderBy {
  given Codec[String, OrderBy, CodecFormat.TextPlain] = Codec.string.mapEither(from)(_.toString)

  val queryParam: EndpointInput.Query[OrderBy] = query[OrderBy]("orderBy")
    .description("Sort the results by the specified property.")

  def from(str: String): Either[String, OrderBy] =
    OrderBy.values
      .find(_.toString.equalsIgnoreCase(str))
      .toRight(
        s"Invalid order by: possible values are '${OrderBy.values.map(_.toString.toLowerCase).mkString(", ")}'",
      )
}
