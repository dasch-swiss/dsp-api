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
