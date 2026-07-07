/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.common
import com.apicatalog.jsonld.JsonLd
import com.apicatalog.jsonld.api.CompactionApi
import com.apicatalog.jsonld.api.ExpansionApi
import com.apicatalog.jsonld.api.FlatteningApi
import com.apicatalog.jsonld.document.JsonDocument
import zio.test.Gen

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets.UTF_8

object JsonLdTestUtil {

  def jsonLdDocumentFrom(str: String): JsonDocument = JsonDocument.of(ByteArrayInputStream(str.getBytes(UTF_8)))

  object JsonLdTransformations {

    val expand: String => String = (jsonLd: String) => {
      val d: JsonDocument   = jsonLdDocumentFrom(jsonLd)
      val api: ExpansionApi = JsonLd.expand(d)
      api.get().toString
    }

    val flatten: String => String = (jsonLd: String) => {
      val d: JsonDocument    = jsonLdDocumentFrom(jsonLd)
      val api: FlatteningApi = JsonLd.flatten(d)
      api.get().toString
    }

    val compact: String => String = (jsonLd: String) => {
      val d: JsonDocument    = jsonLdDocumentFrom(jsonLd)
      val api: CompactionApi =
        JsonLd.compact(
          d,
          jsonLdDocumentFrom("""{
                               |  "api": "http://api.knora.org/ontology/knora-api/v2#",
                               |  "anything": "http://0.0.0.0:3333/ontology/0001/anything/v2#"
                               |}""".stripMargin),
        )
      api.get().toString
    }

    val noOp: String => String = (jsonLd: String) => jsonLd

    val all: Seq[String => String] = Seq(
      expand,
      compact,
      flatten,
      noOp,
    )

    val allGen: Gen[Any, String => String] = Gen.fromIterable(JsonLdTransformations.all)
  }
}
