/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api.model

import sttp.tapir.*

case class FilterAndOrder(filter: Option[String], order: Order)
object FilterAndOrder {
  private val filterQueryParam =
    query[Option[String]]("filter")
      .description("Filter the results by a string")
  private val orderQueryParam = query[Order]("order")
    .description("Order the results by ascending or descending")
    .default(Order.Asc)

  val queryParams: EndpointInput[FilterAndOrder] =
    filterQueryParam.and(orderQueryParam).mapTo[FilterAndOrder]
}

enum Order(override val toString: String) {
  case Asc  extends Order("ASC")
  case Desc extends Order("DESC")
}
object Order {
  given Codec[String, Order, CodecFormat.TextPlain] = Codec.string.mapEither(from)(_.toString)

  def from(str: String): Either[String, Order] =
    Order.values.find(_.toString.equalsIgnoreCase(str)).toRight("Invalid order")
}
