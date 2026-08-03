/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api

import sttp.tapir.EndpointInput
import sttp.tapir.Schema
import sttp.tapir.Validator
import sttp.tapir.query
import zio.json.DeriveJsonCodec
import zio.json.JsonCodec

final case class Pagination(
  pageSize: Int,
  totalItems: Int,
  totalPages: Int,
  currentPage: Int,
  // True when the underlying scan hit its cap: totalItems/totalPages are a lower bound and later pages may
  // be incomplete. Defaults to false; only endpoints with a scan guardrail (e.g. view-restrictions) set it.
  approximate: Boolean = false,
)
object Pagination {
  given JsonCodec[Pagination] = DeriveJsonCodec.gen[Pagination]
  given Schema[Pagination]    = Schema.derived[Pagination]

  def from(totalItems: Int, pageAndSize: PageAndSize, approximate: Boolean = false): Pagination =
    val totalPages = Math.ceil(totalItems.toDouble / pageAndSize.size).toInt
    Pagination(pageAndSize.size, totalItems, totalPages, pageAndSize.page, approximate)
}

final case class PagedResponse[A] private (data: Seq[A], pagination: Pagination)
object PagedResponse {
  given [A: JsonCodec]: JsonCodec[PagedResponse[A]] = DeriveJsonCodec.gen[PagedResponse[A]]
  inline given [A]: Schema[PagedResponse[A]]        = Schema
    .derived[PagedResponse[A]]
    .modify(_.data)(_.copy(isOptional = false))

  def from[A](
    data: Seq[A],
    totalItems: Int,
    pageAndSize: PageAndSize,
    approximate: Boolean = false,
  ): PagedResponse[A] =
    PagedResponse(data, Pagination.from(totalItems, pageAndSize, approximate))
}

case class PageAndSize(page: Int, size: Int)
object PageAndSize {

  val DefaultPageSize: Int = 25
  val Default: PageAndSize = PageAndSize(1, DefaultPageSize)

  private val pageQuery = query[Int]("page")
    .description("The number of the desired page to be returned.")
    .default(1)
    .validate(Validator.min(1))

  private def sizeQuery(maxSize: Int) = query[Int]("page-size")
    .description("The number of items per page to be returned.")
    .default(DefaultPageSize)
    .validate(Validator.min(1))
    .validate(Validator.max(maxSize))

  def queryParams(maxSize: Int = 100): EndpointInput[PageAndSize] = pageQuery.and(sizeQuery(maxSize)).mapTo[PageAndSize]
}
