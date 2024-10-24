/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.common

import zio.Scope
import zio.json.DecoderOps
import zio.json.EncoderOps
import zio.json.ast.Json
import zio.test.*
import zio.test.Assertion.*

import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.slice.resourceinfo.domain.IriConverter

object KnoraApiValueModelSpec extends ZIOSpecDefault {

  private val createIntegerValue = """
    {
      "@id": "http://rdfh.ch/0001/a-thing",
      "@type": "anything:Thing",
      "anything:hasInteger": {
        "@type": "knora-api:IntValue",
        "knora-api:intValueAsInt": 4
      },
      "@context": {
        "knora-api": "http://api.knora.org/ontology/knora-api/v2#",
        "anything": "http://0.0.0.0:3333/ontology/0001/anything/v2#"
      }
    }
  """.fromJson[Json].getOrElse(throw new Exception("Invalid JSON"))

  val spec = suite("KnoraApiValueModel")(
    test("getResourceIri should get the id") {
      for {
        model <- KnoraApiValueModel.fromJsonLd(createIntegerValue.toJsonPretty())
        iri   <- model.getResourceIri
      } yield assertTrue(iri.toString == "http://rdfh.ch/0001/a-thing")
    },
    test("getResourceClass should get the rdfs:type") {
      for {
        model <- KnoraApiValueModel.fromJsonLd(createIntegerValue.toJsonPretty())
        iri   <- model.getResourceClassIri
      } yield assertTrue(iri.toString == "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing")
    },
  ).provideSome[Scope](IriConverter.layer, StringFormatter.test)
}
