package org.knora.webapi.slice.ontology

import org.apache.jena.riot.Lang
import org.knora.webapi.core.TestAppConfig
import org.knora.webapi.slice.common.jena.ModelOps
import org.knora.webapi.slice.common.jena.ModelOps.*
import zio.test.*
import zio.ZIO

object OntologyTransformerSpec extends ZIOSpecDefault {

  private val transformer = ZIO.serviceWithZIO[OntologyTransformer]

  override def spec = suite("OntologyTransformerSpec")(
    test("OntologyTransformer should be created") {
      val jsonLd = """
                     |[{
                     |    "rdfs:label": "test",
                     |    "@id": "http://rdfh.ch/9999/Td9Y3a-cSjueXwn9pK1KYQ",
                     |    "@context": {
                     |        "rdfs": "http://www.w3.org/2000/01/rdf-schema#"
                     |    }
                     |}]""".stripMargin

      val expectedStr = """
                    | PREFIX knora-admin: <http://www.knora.org/ontology/knora-admin#>
                    | PREFIX knora-base:  <http://www.knora.org/ontology/knora-base#>
                    | PREFIX rdf:         <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                    | PREFIX rdfs:        <http://www.w3.org/2000/01/rdf-schema#>
                    |
                    | <http://rdfh.ch/9999/Td9Y3a-cSjueXwn9pK1KYQ>
                    |         rdfs:label                    "test" .
                    |""".stripMargin

      ZIO.scoped {
        for {
          actual   <- transformer(_.toKnoraBase(jsonLd)).flatMap( ModelOps.fromJsonLd)
          expected <- ModelOps.fromTurtle(expectedStr)
          actualNQ <- actual.as(Lang.NQUADS)
          expectedNQ <- expected.as(Lang.NQUADS)
        } yield assertTrue(actualNQ == expectedNQ)
      }
    },
  ).provide(OntologyTransformer.layer, TestAppConfig.layer())

}
