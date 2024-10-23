package org.knora.webapi.slice.common

import org.knora.webapi.slice.common.JenaModelOps.toJsonLd
import zio.test.*
import zio.*
import zio.json.ast.Json
import zio.json.DecoderOps
import zio.json.EncoderOps

object JenaModelOpsSpec extends ZIOSpecDefault {

  private val jsonLd = """{
                     "@id" : "http://rdfh.ch/0001/a-thing",
                     "@type" : "anything:Thing",
                     "anything:hasInteger" : {
                       "@type" : "knora-api:IntValue",
                       "knora-api:intValueAsInt" : 4
                     },
                     "@context" : {
                       "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
                       "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
                     }
                   }
                   """.stripMargin
  private val jsonLd2 = """{
                     "@id" : "http://rdfh.ch/0001/a-thing",
                     "@type" : "anything:Thing",
                     "anything:hasInteger" : {
                       "@type" : "knora-api:IntValue",
                       "knora-api:intValueAsInt" : {
                          "@type" : "xsd",
                          "@value" : "4"
                        }
                     },
                     "@context" : {
                       "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
                       "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#",
                       "xsd" : "http://www.w3.org/2001/XMLSchema#integer"
                     }
                   }
                   """.stripMargin
  val spec = suite("JenaModelOps")(
    suite("fromJsonLd")(
      test("should parse the json ld") {
        JenaModelOps.fromJsonLd(jsonLd).flatMap { model =>
          assertTrue(model.size() == 4)
        }
      },
      test("should produce isomorphic models") {
        for {
          model1 <- JenaModelOps.fromJsonLd(jsonLd)
          model2 <- JenaModelOps.fromJsonLd(jsonLd2)
        } yield assertTrue(model1.isIsomorphicWith(model2))
      },
    ),
  )
}
