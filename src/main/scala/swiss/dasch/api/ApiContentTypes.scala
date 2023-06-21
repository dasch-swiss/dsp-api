/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.api

import zio.{ IO, ZIO }
import zio.http.Header.ContentType
import zio.http.MediaType

object ApiContentTypes {

  val applicationZip: ContentType = ContentType(MediaType.application.zip)

  def verifyContentType(actual: ContentType, expected: ContentType): IO[IllegalArguments, Unit] =
    ZIO.fail(ApiProblem.invalidHeaderContentType(actual, expected)).when(actual != expected).unit
}
