/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and Sepideh Alassi.
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

import org.knora.webapi.util.IriConversions._
import org.knora.webapi.util.StringFormatter
import org.knora.webapi.util.jsonld.JsonLDUtil
import org.knora.webapi.{ApiV2WithValueObjects, CoreSpec}

/**
  * Tests [[InputOntologiesV2]].
  */
class InputOntologiesV2Spec extends CoreSpec {
    import InputOntologiesV2Spec._

    "InputOntologiesV2" should {
        "parse a property definition" in {
            val params =
                """
                  |{
                  |  "knora-api:hasOntologies" : {
                  |    "@id" : "http://0.0.0.0:3333/ontology/anything/v2",
                  |    "@type" : "owl:Ontology",
                  |    "knora-api:hasProperties" : {
                  |      "anything:hasName" : {
                  |        "@id" : "anything:hasName",
                  |        "@type" : "owl:ObjectProperty",
                  |        "knora-api:subjectType" : "http://0.0.0.0:3333/ontology/anything/v2#Thing",
                  |        "knora-api:objectType" : "http://api.knora.org/ontology/knora-api/v2#TextValue",
                  |        "rdfs:comment" : [ {
                  |          "@language" : "en",
                  |          "@value" : "The name of a 'Thing'"
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

            val paramsAsInput: InputOntologiesV2 = InputOntologiesV2.fromJsonLD(JsonLDUtil.parseJsonLD(params)).unescape
            paramsAsInput should ===(PropertyDef)
        }

        "parse a class definition" in {
            val params =
                s"""
                   |{
                   |  "knora-api:hasOntologies" : {
                   |    "@id" : "http://0.0.0.0:3333/ontology/anything/v2",
                   |    "@type" : "owl:Ontology",
                   |    "knora-api:hasClasses" : {
                   |      "anything:WildThing" : {
                   |        "@id" : "anything:WildThing",
                   |        "@type" : "owl:Class",
                   |        "rdfs:label" : {
                   |          "@language" : "en",
                   |          "@value" : "wild thing"
                   |        },
                   |        "rdfs:comment" : {
                   |          "@language" : "en",
                   |          "@value" : "A thing that is wild"
                   |        },
                   |        "rdfs:subClassOf" : [
                   |            "http://0.0.0.0:3333/ontology/anything/v2#Thing",
                   |            {
                   |                "@type": "http://www.w3.org/2002/07/owl#Restriction",
                   |                "owl:maxCardinality": 1,
                   |                "owl:onProperty": "http://0.0.0.0:3333/ontology/anything/v2#hasName"
                   |            }
                   |        ]
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

            val paramsAsInput: InputOntologiesV2 = InputOntologiesV2.fromJsonLD(JsonLDUtil.parseJsonLD(params)).unescape
            paramsAsInput should ===(ClassDef)
        }
    }
}

object InputOntologiesV2Spec {
    private implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    val PropertyDef = InputOntologiesV2(ontologies = Vector(InputOntologyV2(
        ontologyMetadata = OntologyMetadataV2(
            ontologyIri = "http://0.0.0.0:3333/ontology/anything/v2".toSmartIri,
            label = None,
            lastModificationDate = Some(Instant.parse("2017-12-19T15:23:42.166Z"))
        ),
        properties = Map("http://0.0.0.0:3333/ontology/anything/v2#hasName".toSmartIri -> PropertyInfoContentV2(
            propertyIri = "http://0.0.0.0:3333/ontology/anything/v2#hasName".toSmartIri,
            predicates = Map(
                "http://www.w3.org/2000/01/rdf-schema#label".toSmartIri -> PredicateInfoV2(
                    predicateIri = "http://www.w3.org/2000/01/rdf-schema#label".toSmartIri,
                    objects = Set(),
                    objectsWithLang = Map(
                        "en" -> "has name",
                        "de" -> "hat Namen"
                    )
                ),
                "http://api.knora.org/ontology/knora-api/v2#subjectType".toSmartIri -> PredicateInfoV2(
                    predicateIri = "http://api.knora.org/ontology/knora-api/v2#subjectType".toSmartIri,
                    objects = Set("http://0.0.0.0:3333/ontology/anything/v2#Thing"),
                ),
                "http://www.w3.org/2000/01/rdf-schema#comment".toSmartIri -> PredicateInfoV2(
                    predicateIri = "http://www.w3.org/2000/01/rdf-schema#comment".toSmartIri,
                    objectsWithLang = Map(
                        "en" -> "The name of a 'Thing'",
                        "de" -> "Der Name eines Dinges"
                    )
                ),
                "http://api.knora.org/ontology/knora-api/v2#objectType".toSmartIri -> PredicateInfoV2(
                    predicateIri = "http://api.knora.org/ontology/knora-api/v2#objectType".toSmartIri,
                    objects = Set("http://api.knora.org/ontology/knora-api/v2#TextValue"),
                ),
                "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri -> PredicateInfoV2(
                    predicateIri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
                    objects = Set("http://www.w3.org/2002/07/owl#ObjectProperty"),
                )
            ),
            subPropertyOf = Set(
                "http://api.knora.org/ontology/knora-api/v2#hasValue".toSmartIri,
                "http://schema.org/name".toSmartIri
            ),
            ontologySchema = ApiV2WithValueObjects
        ))
    )))

    val ClassDef = InputOntologiesV2(ontologies = Vector(InputOntologyV2(
        standoffProperties = Map(),
        classes = Map("http://0.0.0.0:3333/ontology/anything/v2#WildThing".toSmartIri -> ClassInfoContentV2(
            predicates = Map(
                "http://www.w3.org/2000/01/rdf-schema#label".toSmartIri -> PredicateInfoV2(
                    predicateIri = "http://www.w3.org/2000/01/rdf-schema#label".toSmartIri,
                    objectsWithLang = Map("en" -> "wild thing")
                ),
                "http://www.w3.org/2000/01/rdf-schema#comment".toSmartIri -> PredicateInfoV2(
                    predicateIri = "http://www.w3.org/2000/01/rdf-schema#comment".toSmartIri,
                    objectsWithLang = Map("en" -> "A thing that is wild")
                ),
                "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri -> PredicateInfoV2(
                    predicateIri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
                    objects = Set("http://www.w3.org/2002/07/owl#Class")
                )
            ),
            classIri = "http://0.0.0.0:3333/ontology/anything/v2#WildThing".toSmartIri,
            ontologySchema = ApiV2WithValueObjects,
            directCardinalities = Map("http://0.0.0.0:3333/ontology/anything/v2#hasName".toSmartIri -> Cardinality.MayHaveOne),
            subClassOf = Set("http://0.0.0.0:3333/ontology/anything/v2#Thing".toSmartIri)
        )),
        ontologyMetadata = OntologyMetadataV2(
            ontologyIri = "http://0.0.0.0:3333/ontology/anything/v2".toSmartIri,
            lastModificationDate = Some(Instant.parse("2017-12-19T15:23:42.166Z"))
        ),
        standoffClasses = Map(),
        properties = Map()
    )))
}