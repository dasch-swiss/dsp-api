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

package org.knora.webapi.messages.v2.responder.ontologymessages

import java.time.Instant

import org.knora.webapi.messages.store.triplestoremessages.{SmartIriLiteralV2, StringLiteralV2}
import org.knora.webapi.messages.v2.responder.ontologymessages.Cardinality.KnoraCardinalityInfo
import org.knora.webapi.util.IriConversions._
import org.knora.webapi.util.StringFormatter
import org.knora.webapi.util.jsonld.JsonLDUtil
import org.knora.webapi.{ApiV2WithValueObjects, BadRequestException, CoreSpec}

/**
  * Tests [[InputOntologyV2]].
  */
class InputOntologyV2Spec extends CoreSpec {

    import InputOntologyV2Spec._

    "InputOntologyV2" should {
        "parse a property definition" in {
            val params =
                """
                  |{
                  |  "@id" : "http://0.0.0.0:3333/ontology/0001/anything/v2",
                  |  "@type" : "owl:Ontology",
                  |  "knora-api:lastModificationDate" : "2017-12-19T15:23:42.166Z",
                  |  "@graph" : [ {
                  |    "@id" : "anything:hasName",
                  |    "@type" : "owl:ObjectProperty",
                  |    "knora-api:subjectType" : {
                  |      "@id" : "anything:Thing"
                  |    },
                  |    "knora-api:objectType" : {
                  |      "@id" : "knora-api:TextValue"
                  |    },
                  |    "rdfs:comment" : [ {
                  |      "@language" : "en",
                  |      "@value" : "The name of a 'Thing'"
                  |    }, {
                  |      "@language" : "de",
                  |      "@value" : "Der Name eines Dinges"
                  |    } ],
                  |    "rdfs:label" : [ {
                  |      "@language" : "en",
                  |      "@value" : "has name"
                  |    }, {
                  |      "@language" : "de",
                  |      "@value" : "hat Namen"
                  |    } ],
                  |    "rdfs:subPropertyOf" : [ {
                  |      "@id" : "knora-api:hasValue"
                  |    }, {
                  |      "@id" : "http://schema.org/name"
                  |    } ]
                  |  } ],
                  |  "@context" : {
                  |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
                  |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
                  |    "owl" : "http://www.w3.org/2002/07/owl#",
                  |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
                  |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
                  |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
                  |  }
                  |}
                """.stripMargin

            val paramsAsInput: InputOntologyV2 = InputOntologyV2.fromJsonLD(JsonLDUtil.parseJsonLD(params)).unescape
            paramsAsInput should ===(PropertyDef)
        }

        "parse a class definition" in {
            val params =
                s"""
                   |{
                   |  "@id" : "http://0.0.0.0:3333/ontology/0001/anything/v2",
                   |  "@type" : "owl:Ontology",
                   |  "knora-api:lastModificationDate" : "2017-12-19T15:23:42.166Z",
                   |  "@graph" : [ {
                   |    "@id" : "anything:WildThing",
                   |    "@type" : "owl:Class",
                   |    "rdfs:label" : {
                   |      "@language" : "en",
                   |      "@value" : "wild thing"
                   |    },
                   |    "rdfs:comment" : {
                   |      "@language" : "en",
                   |      "@value" : "A thing that is wild"
                   |    },
                   |    "rdfs:subClassOf" : [ {
                   |      "@id" : "anything:Thing"
                   |    }, {
                   |      "@type": "owl:Restriction",
                   |      "owl:maxCardinality": 1,
                   |      "owl:onProperty": {
                   |        "@id" : "anything:hasName"
                   |      }
                   |    } ]
                   |  } ],
                   |  "@context" : {
                   |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
                   |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
                   |    "owl" : "http://www.w3.org/2002/07/owl#",
                   |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
                   |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
                   |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
                   |  }
                   |}
            """.stripMargin

            val paramsAsInput: InputOntologyV2 = InputOntologyV2.fromJsonLD(JsonLDUtil.parseJsonLD(params)).unescape
            paramsAsInput should ===(ClassDef)
        }

        "reject an entity definition in the wrong ontology" in {
            val params =
                s"""
                   |{
                   |  "@id" : "http://0.0.0.0:3333/ontology/0001/incunabula/v2",
                   |  "@type" : "owl:Ontology",
                   |  "knora-api:lastModificationDate" : "2017-12-19T15:23:42.166Z",
                   |  "@graph" : [ {
                   |    "@id" : "anything:WildThing",
                   |    "@type" : "owl:Class",
                   |    "rdfs:label" : {
                   |      "@language" : "en",
                   |      "@value" : "wild thing"
                   |    },
                   |    "rdfs:comment" : {
                   |      "@language" : "en",
                   |      "@value" : "A thing that is wild"
                   |    },
                   |    "rdfs:subClassOf" : [ {
                   |      "@id" : "anything:Thing"
                   |    }, {
                   |      "@type": "owl:Restriction",
                   |      "owl:maxCardinality": 1,
                   |      "owl:onProperty": {
                   |        "@id" : "anything:hasName"
                   |      }
                   |    } ]
                   |  } ],
                   |  "@context" : {
                   |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
                   |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
                   |    "owl" : "http://www.w3.org/2002/07/owl#",
                   |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
                   |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
                   |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
                   |  }
                   |}
            """.stripMargin

            assertThrows[BadRequestException] {
                InputOntologyV2.fromJsonLD(JsonLDUtil.parseJsonLD(params))
            }
        }
    }
}

object InputOntologyV2Spec {
    private implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    val PropertyDef = InputOntologyV2(
        ontologyMetadata = OntologyMetadataV2(
            ontologyIri = "http://0.0.0.0:3333/ontology/0001/anything/v2".toSmartIri,
            label = None,
            lastModificationDate = Some(Instant.parse("2017-12-19T15:23:42.166Z"))
        ),
        properties = Map("http://0.0.0.0:3333/ontology/0001/anything/v2#hasName".toSmartIri -> PropertyInfoContentV2(
            propertyIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasName".toSmartIri,
            predicates = Map(
                "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri -> PredicateInfoV2(
                    predicateIri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
                    objects = Seq(SmartIriLiteralV2("http://www.w3.org/2002/07/owl#ObjectProperty".toSmartIri)),
                ),
                "http://api.knora.org/ontology/knora-api/v2#subjectType".toSmartIri -> PredicateInfoV2(
                    predicateIri = "http://api.knora.org/ontology/knora-api/v2#subjectType".toSmartIri,
                    objects = Seq(SmartIriLiteralV2("http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri)),
                ),
                "http://api.knora.org/ontology/knora-api/v2#objectType".toSmartIri -> PredicateInfoV2(
                    predicateIri = "http://api.knora.org/ontology/knora-api/v2#objectType".toSmartIri,
                    objects = Seq(SmartIriLiteralV2("http://api.knora.org/ontology/knora-api/v2#TextValue".toSmartIri)),
                ),
                "http://www.w3.org/2000/01/rdf-schema#label".toSmartIri -> PredicateInfoV2(
                    predicateIri = "http://www.w3.org/2000/01/rdf-schema#label".toSmartIri,
                    objects = Seq(
                        StringLiteralV2("has name", Some("en")),
                        StringLiteralV2("hat Namen", Some("de"))
                    )
                ),
                "http://www.w3.org/2000/01/rdf-schema#comment".toSmartIri -> PredicateInfoV2(
                    predicateIri = "http://www.w3.org/2000/01/rdf-schema#comment".toSmartIri,
                    objects = Seq(
                        StringLiteralV2("The name of a 'Thing'", Some("en")),
                        StringLiteralV2("Der Name eines Dinges", Some("de"))
                    )
                )
            ),
            subPropertyOf = Set(
                "http://api.knora.org/ontology/knora-api/v2#hasValue".toSmartIri,
                "http://schema.org/name".toSmartIri
            ),
            ontologySchema = ApiV2WithValueObjects
        ))
    )

    val ClassDef = InputOntologyV2(
        classes = Map("http://0.0.0.0:3333/ontology/0001/anything/v2#WildThing".toSmartIri -> ClassInfoContentV2(
            predicates = Map(
                "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri -> PredicateInfoV2(
                    predicateIri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
                    objects = Seq(SmartIriLiteralV2("http://www.w3.org/2002/07/owl#Class".toSmartIri))
                ),
                "http://www.w3.org/2000/01/rdf-schema#label".toSmartIri -> PredicateInfoV2(
                    predicateIri = "http://www.w3.org/2000/01/rdf-schema#label".toSmartIri,
                    objects = Seq(StringLiteralV2("wild thing", Some("en")))
                ),
                "http://www.w3.org/2000/01/rdf-schema#comment".toSmartIri -> PredicateInfoV2(
                    predicateIri = "http://www.w3.org/2000/01/rdf-schema#comment".toSmartIri,
                    objects = Seq(StringLiteralV2("A thing that is wild", Some("en")))
                )
            ),
            classIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#WildThing".toSmartIri,
            ontologySchema = ApiV2WithValueObjects,
            directCardinalities = Map("http://0.0.0.0:3333/ontology/0001/anything/v2#hasName".toSmartIri -> KnoraCardinalityInfo(Cardinality.MayHaveOne)),
            subClassOf = Set("http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri)
        )),
        ontologyMetadata = OntologyMetadataV2(
            ontologyIri = "http://0.0.0.0:3333/ontology/0001/anything/v2".toSmartIri,
            lastModificationDate = Some(Instant.parse("2017-12-19T15:23:42.166Z"))
        ),
        properties = Map()
    )
}
