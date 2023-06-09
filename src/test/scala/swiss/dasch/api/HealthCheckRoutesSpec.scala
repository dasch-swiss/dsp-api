package swiss.dasch.api

import swiss.dasch.api.healthcheck.HealthCheckServiceTest
import zio.http.*
import zio.test.*
import zio.test.Assertion.*

object HealthCheckRoutesSpec extends ZIOSpecDefault {

  override def spec: Spec[Any, Option[Nothing]] =
    suite("http")(
      suite("health check")(
        test("ok status") {
          val actual =
            HealthCheckRoutes.app.runZIO(Request.get(URL(!! / "health")))
          assertZIO(actual)(equalTo(Response(Status.Ok, Headers.empty, Body.empty)))
        }
      )
    ).provide(HealthCheckServiceTest.layer)
}
