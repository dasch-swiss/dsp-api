/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.testservices
import sttp.client4.*
import sttp.model.*
import zio.*

import dsp.errors.AssertionException

object ResponseOps {
  extension [A](response: Response[Either[String, A]]) {

    def assert200: IO[AssertionException, A] =
      (if response.code == StatusCode.Ok then ZIO.fromEither(response.body)
       else ZIO.fail(s"Response code is not 200 OK, got ${response.code}")).mapError(AssertionException(_))

  }
}
