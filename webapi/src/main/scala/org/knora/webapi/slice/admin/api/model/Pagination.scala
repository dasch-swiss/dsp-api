/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api.model

import sttp.tapir.Validator
import sttp.tapir.query
import zio.json.DeriveJsonCodec
import zio.json.JsonCodec

final case class Pagination(`page-size`: Int, `total-items`: Int, `total-pages`: Int, `current-page`: Int)
object Pagination {
  given JsonCodec[Pagination] = DeriveJsonCodec.gen[Pagination]

  def from(totalItems: Int, pageAndSize: PageAndSize): Pagination =
    val totalPages = Math.ceil(totalItems.toDouble / pageAndSize.size).toInt
    Pagination(pageAndSize.size, totalItems, totalPages, pageAndSize.page)
}

final case class PagedResponse[A](data: Seq[A], pagination: Pagination)
object PagedResponse {
  given [A: JsonCodec]: JsonCodec[PagedResponse[A]] = DeriveJsonCodec.gen[PagedResponse[A]]
}

val defaultPageSize = 25

case class PageAndSize(page: Int, size: Int = defaultPageSize)

def pageAndSizeQuery(maxSize: Int = 100) = pageQuery.and(sizeQuery(maxSize)).mapTo[PageAndSize]

private val pageQuery = query[Int]("page")
  .description("The page number to retrieve.")
  .default(1)
  .validate(Validator.min(1))

private def sizeQuery(maxSize: Int) = query[Int]("page-size")
  .description("The number of items to retrieve.")
  .default(defaultPageSize)
  .validate(Validator.min(1))
  .validate(Validator.max(maxSize))
