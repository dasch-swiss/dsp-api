/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.common.jena

import zio.*
import zio.test.*

object ModelOpsSpec extends ZIOSpecDefault {

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
  val spec = suite("ModelOps")(
    suite("fromJsonLd")(
      test("should parse the json ld") {
        ModelOps.fromJsonLd(jsonLd).flatMap { model =>
          assertTrue(model.size() == 4)
        }
      },
      test("should produce isomorphic models") {
        for {
          model1 <- ModelOps.fromJsonLd(jsonLd)
          model2 <- ModelOps.fromJsonLd(jsonLd2)
        } yield assertTrue(model1.isIsomorphicWith(model2))
      },
    ),
  )
}
