package org.knora.webapi.store.triplestore.templates

import zio.test.*
import org.knora.webapi.messages.twirl.queries.sparql
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.IriConversions.ConvertibleIri
import org.knora.webapi.config.AppConfig
import zio.*
import org.knora.webapi.core.LayersTest
import org.knora.webapi.core.LayersTest.DefaultTestEnvironmentWithoutSipi

object CreatePropertyTemplatesSpec extends ZIOSpecDefault {
  override def spec: Spec[Environment & (TestEnvironment & Scope), DefaultTestEnvironmentWithoutSipi] =
    suite("Create Property Templates")(
      suite("createProperty.scala.txt")(
      ),
      suite("generateInsertStatementsForCreateProperty.scala.txt")(
      ),
      suite("generateInsertStatementsForPredicates.scala.txt")(
        test("foo") {
          for {
            sf <- ZIO.service[StringFormatter]
            x   = sf.toSmartIri("http://www.knora.org/ontology/0001/anything#hasText")
          } yield assertTrue(true)
        },
      ),
    ).provide(LayersTest.integrationTestsWithFusekiTestcontainers())

}
