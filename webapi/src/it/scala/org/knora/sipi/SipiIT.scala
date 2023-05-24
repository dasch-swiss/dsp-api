package org.knora.sipi

import zio.Scope
import zio.ZIO
import zio.http.Client
import zio.http.Request
import zio.test.Spec
import zio.test.TestEnvironment
import zio.test.ZIOSpecDefault
import zio.test.assertTrue

import org.knora.webapi.testcontainers.SipiTestContainer

object SipiIT extends ZIOSpecDefault {
  private def getSipiUrl = ZIO.serviceWith[SipiTestContainer](_.sipiBaseUrl)
  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("Sipi integration tests with mocked dsp-api")(
      test("health check works") {
        for {
          sipiBaseUrl <- getSipiUrl
          response    <- Client.request(Request.get(sipiBaseUrl.setPath("/server/test.html")))
        } yield assertTrue(response.status.isSuccess)
      }
    )
      .provideSomeLayerShared[Client](SipiTestContainer.layer)
      .provideLayer(Client.default)

}
