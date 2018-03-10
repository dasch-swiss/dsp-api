/*
 * Copyright © 2015-2018 the contributors (see Contributors.md).
 *
 * This file is part of Knora.
 *
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.util.jsonld

import org.knora.webapi.CoreSpec
import spray.json.{JsValue, JsonParser}

/**
  * Tests [[JsonLDUtil]].
  */
class JsonLDUtilSpec extends CoreSpec {

    "The JSON-LD utility" should {
        "parse JSON-LD text, compact it with an empty context, convert the result to a JsonLDDocument, and convert that back to text" in {
            val inputStr =
                """
                  |{
                  |  "knora-api:hasOntologies" : {
                  |    "@id" : "http://0.0.0.0:3333/ontology/anything/v2",
                  |    "@type" : "owl:Ontology",
                  |    "knora-api:hasProperties" : {
                  |      "anything:hasName" : {
                  |        "@id" : "anything:hasName",
                  |        "@type" : "owl:ObjectProperty",
                  |        "knora-api:objectType" : "http://api.knora.org/ontology/knora-api/v2#TextValue",
                  |        "knora-api:subjectType" : "http://0.0.0.0:3333/ontology/anything/v2#Thing",
                  |        "rdfs:comment" : [ {
                  |          "@language" : "en",
                  |          "@value" : "The name of a Thing"
                  |        }, {
                  |          "@language" : "de",
                  |          "@value" : "Der Name eines Dinges"
                  |        } ],
                  |        "rdfs:label" : [ {
                  |          "@language" : "en",
                  |          "@value" : "has name"
                  |        }, {
                  |          "@language" : "de",
                  |          "@value" : "hat Namen"
                  |        } ],
                  |        "rdfs:subPropertyOf" : [ "http://api.knora.org/ontology/knora-api/v2#hasValue", "http://schema.org/name" ]
                  |      }
                  |    },
                  |    "knora-api:lastModificationDate" : "2017-12-19T15:23:42.166Z"
                  |  },
                  |  "@context" : {
                  |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
                  |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
                  |    "owl" : "http://www.w3.org/2002/07/owl#",
                  |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
                  |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
                  |    "anything" : "http://0.0.0.0:3333/ontology/anything/v2#"
                  |  }
                  |}
                """.stripMargin

            val expectedOutputStr =
                """
                  |{
                  |  "http://api.knora.org/ontology/knora-api/v2#hasOntologies" : {
                  |    "@id" : "http://0.0.0.0:3333/ontology/anything/v2",
                  |    "@type" : "http://www.w3.org/2002/07/owl#Ontology",
                  |    "http://api.knora.org/ontology/knora-api/v2#hasProperties" : {
                  |      "http://0.0.0.0:3333/ontology/anything/v2#hasName" : {
                  |        "@id" : "http://0.0.0.0:3333/ontology/anything/v2#hasName",
                  |        "@type" : "http://www.w3.org/2002/07/owl#ObjectProperty",
                  |        "http://api.knora.org/ontology/knora-api/v2#objectType" : "http://api.knora.org/ontology/knora-api/v2#TextValue",
                  |        "http://api.knora.org/ontology/knora-api/v2#subjectType" : "http://0.0.0.0:3333/ontology/anything/v2#Thing",
                  |        "http://www.w3.org/2000/01/rdf-schema#comment" : [ {
                  |          "@language" : "en",
                  |          "@value" : "The name of a Thing"
                  |        }, {
                  |          "@language" : "de",
                  |          "@value" : "Der Name eines Dinges"
                  |        } ],
                  |        "http://www.w3.org/2000/01/rdf-schema#label" : [ {
                  |          "@language" : "en",
                  |          "@value" : "has name"
                  |        }, {
                  |          "@language" : "de",
                  |          "@value" : "hat Namen"
                  |        } ],
                  |        "http://www.w3.org/2000/01/rdf-schema#subPropertyOf" : [ "http://api.knora.org/ontology/knora-api/v2#hasValue", "http://schema.org/name" ]
                  |      }
                  |    },
                  |    "http://api.knora.org/ontology/knora-api/v2#lastModificationDate" : "2017-12-19T15:23:42.166Z"
                  |  }
                  |}
                """.stripMargin

            val compactedJsonLDDoc: JsonLDDocument = JsonLDUtil.parseJsonLD(inputStr)
            val formattedCompactedDoc = compactedJsonLDDoc.toPrettyString
            val receivedOutputAsJsValue: JsValue = JsonParser(formattedCompactedDoc)
            val expectedOutputAsJsValue: JsValue = JsonParser(expectedOutputStr)
            receivedOutputAsJsValue should ===(expectedOutputAsJsValue)
        }
    }
}
