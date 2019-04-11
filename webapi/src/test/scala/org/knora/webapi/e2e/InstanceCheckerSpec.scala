/*
 * Copyright © 2015-2019 the contributors (see Contributors.md).
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

package org.knora.webapi.e2e

import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import akka.http.scaladsl.testkit.RouteTestTimeout
import com.typesafe.config.{Config, ConfigFactory}
import org.knora.webapi.util.IriConversions._
import org.knora.webapi.util.StringFormatter
import org.knora.webapi.{AssertionException, E2ESpec}

import scala.concurrent.ExecutionContextExecutor

/**
  * Tests [[InstanceChecker]].
  */
class InstanceCheckerSpec extends E2ESpec(InstanceCheckerSpec.config) {
    private implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    implicit def default(implicit system: ActorSystem): RouteTestTimeout = RouteTestTimeout(settings.defaultTimeout)

    implicit override lazy val log: LoggingAdapter = akka.event.Logging(system, this.getClass)

    implicit val ec: ExecutionContextExecutor = system.dispatcher

    private val jsonLDInstanceChecker: InstanceChecker = InstanceChecker.getJsonLDChecker(log)
    private val jsonInstanceChecker: InstanceChecker = InstanceChecker.getJsonChecker(log)

    "The InstanceChecker" should {
        "reject a JSON-LD instance of anything:Thing (in the complex schema) with an extra property" in {
            assertThrows[AssertionException] {
                jsonLDInstanceChecker.check(
                    instanceResponse = InstanceCheckerSpec.complexThingWithExtraProperty,
                    expectedClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
                    knoraRouteGet = doGetRequest
                )
            }
        }

        "reject a JSON-LD instance of anything:Thing (in the complex schema) with an extra property object" in {
            assertThrows[AssertionException] {
                jsonLDInstanceChecker.check(
                    instanceResponse = InstanceCheckerSpec.complexThingWithExtraPropertyObject,
                    expectedClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
                    knoraRouteGet = doGetRequest
                )
            }
        }

        "reject a JSON-LD instance of anything:Thing (in the complex schema) with an invalid literal type" in {
            assertThrows[AssertionException] {
                jsonLDInstanceChecker.check(
                    instanceResponse = InstanceCheckerSpec.complexThingWithInvalidLiteralType,
                    expectedClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
                    knoraRouteGet = doGetRequest
                )
            }
        }

        "reject a JSON-LD instance of anything:Thing (in the complex schema) with an invalid object type" in {
            assertThrows[AssertionException] {
                jsonLDInstanceChecker.check(
                    instanceResponse = InstanceCheckerSpec.complexThingWithInvalidObjectType,
                    expectedClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
                    knoraRouteGet = doGetRequest
                )
            }
        }

        "reject a JSON-LD instance of anything:Thing (in the complex schema) with object content where an IRI is required" in {
            assertThrows[AssertionException] {
                jsonLDInstanceChecker.check(
                    instanceResponse = InstanceCheckerSpec.complexThingWithInvalidUseOfObjectInsteadOfIri,
                    expectedClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
                    knoraRouteGet = doGetRequest
                )
            }
        }

        "reject a JSON-LD instance of anything:Thing (in the simple schema) with an invalid datatype" in {
            assertThrows[AssertionException] {
                jsonLDInstanceChecker.check(
                    instanceResponse = InstanceCheckerSpec.simpleThingWithInvalidDatatype,
                    expectedClassIri = "http://0.0.0.0:3333/ontology/0001/anything/simple/v2#Thing".toSmartIri,
                    knoraRouteGet = doGetRequest
                )
            }
        }

        "reject a JSON-LD instance of anything:Thing (in the simple schema) without an rdfs:label" in {
            assertThrows[AssertionException] {
                jsonLDInstanceChecker.check(
                    instanceResponse = InstanceCheckerSpec.simpleThingWithMissingLabel,
                    expectedClassIri = "http://0.0.0.0:3333/ontology/0001/anything/simple/v2#Thing".toSmartIri,
                    knoraRouteGet = doGetRequest
                )
            }
        }

        "accept a correct JSON instance of an admin:User" in {
            jsonInstanceChecker.check(
                instanceResponse = InstanceCheckerSpec.correctUser,
                expectedClassIri = "http://api.knora.org/ontology/knora-admin/v2#User".toSmartIri,
                knoraRouteGet = doGetRequest
            )
        }

        "reject a JSON instance of an admin:User with an extra property" in {
            assertThrows[AssertionException] {
                jsonInstanceChecker.check(
                    instanceResponse = InstanceCheckerSpec.userWithExtraProperty,
                    expectedClassIri = "http://api.knora.org/ontology/knora-admin/v2#User".toSmartIri,
                    knoraRouteGet = doGetRequest
                )
            }
        }

        "reject a JSON instance of an admin:User without a username" in {
            assertThrows[AssertionException] {
                jsonInstanceChecker.check(
                    instanceResponse = InstanceCheckerSpec.userWithMissingUsername,
                    expectedClassIri = "http://api.knora.org/ontology/knora-admin/v2#User".toSmartIri,
                    knoraRouteGet = doGetRequest
                )
            }
        }

        "reject a JSON instance of an admin:User with an invalid literal object type" in {
            assertThrows[AssertionException] {
                jsonInstanceChecker.check(
                    instanceResponse = InstanceCheckerSpec.userWithInvalidObjectType,
                    expectedClassIri = "http://api.knora.org/ontology/knora-admin/v2#User".toSmartIri,
                    knoraRouteGet = doGetRequest
                )
            }
        }
    }
}

object InstanceCheckerSpec {
    val config: Config = ConfigFactory.parseString(
        """
          akka.loglevel = "DEBUG"
          akka.stdout-loglevel = "DEBUG"
        """.stripMargin)

    val complexThingWithExtraProperty: String =
        """{
          |  "@id" : "http://rdfh.ch/0001/cUnhrC1DT821lwVWQSwEgg",
          |  "@type" : "anything:Thing",
          |  "anything:hasExtraProperty" : {
          |    "@id" : "http://rdfh.ch/0001/cUnhrC1DT821lwVWQSwEgg/values/o-j0jdxMQvanmAdpAIOcFA",
          |    "@type" : "knora-api:BooleanValue",
          |    "knora-api:attachedToUser" : {
          |      "@id" : "http://rdfh.ch/users/9XBCrDV3SRa7kS1WwynB4Q"
          |    },
          |    "knora-api:booleanValueAsBoolean" : true,
          |    "knora-api:hasPermissions" : "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
          |    "knora-api:userHasPermission" : "CR",
          |    "knora-api:valueCreationDate" : {
          |      "@type" : "xsd:dateTimeStamp",
          |      "@value" : "2019-04-10T08:41:45.353992Z"
          |    }
          |  },
          |  "knora-api:arkUrl" : {
          |    "@type" : "xsd:anyURI",
          |    "@value" : "http://0.0.0.0:3336/ark:/72163/1/0001/cUnhrC1DT821lwVWQSwEgg0"
          |  },
          |  "knora-api:attachedToProject" : {
          |    "@id" : "http://rdfh.ch/projects/0001"
          |  },
          |  "knora-api:attachedToUser" : {
          |    "@id" : "http://rdfh.ch/users/9XBCrDV3SRa7kS1WwynB4Q"
          |  },
          |  "knora-api:creationDate" : {
          |    "@type" : "xsd:dateTimeStamp",
          |    "@value" : "2019-04-10T08:41:45.353992Z"
          |  },
          |  "knora-api:hasPermissions" : "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
          |  "knora-api:userHasPermission" : "CR",
          |  "knora-api:versionArkUrl" : {
          |    "@type" : "xsd:anyURI",
          |    "@value" : "http://0.0.0.0:3336/ark:/72163/1/0001/cUnhrC1DT821lwVWQSwEgg0.20190410T084145353992Z"
          |  },
          |  "rdfs:label" : "test thing",
          |  "@context" : {
          |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
          |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
          |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
          |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
          |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
          |  }
          |}
        """.stripMargin

    val complexThingWithExtraPropertyObject: String =
        """{
          |  "@id" : "http://rdfh.ch/0001/cUnhrC1DT821lwVWQSwEgg",
          |  "@type" : "anything:Thing",
          |  "anything:hasBoolean" : [ {
          |    "@id" : "http://rdfh.ch/0001/cUnhrC1DT821lwVWQSwEgg/values/o-j0jdxMQvanmAdpAIOcFA",
          |    "@type" : "knora-api:BooleanValue",
          |    "knora-api:attachedToUser" : {
          |      "@id" : "http://rdfh.ch/users/9XBCrDV3SRa7kS1WwynB4Q"
          |    },
          |    "knora-api:booleanValueAsBoolean" : true,
          |    "knora-api:hasPermissions" : "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
          |    "knora-api:userHasPermission" : "CR",
          |    "knora-api:valueCreationDate" : {
          |      "@type" : "xsd:dateTimeStamp",
          |      "@value" : "2019-04-10T08:41:45.353992Z"
          |    }
          |  }, {
          |    "@id" : "http://rdfh.ch/0001/cUnhrC1DT821lwVWQSwEgg/values/o-j0jdxMQvanmAdpAIOcFA",
          |    "@type" : "knora-api:BooleanValue",
          |    "knora-api:attachedToUser" : {
          |      "@id" : "http://rdfh.ch/users/9XBCrDV3SRa7kS1WwynB4Q"
          |    },
          |    "knora-api:booleanValueAsBoolean" : false,
          |    "knora-api:hasPermissions" : "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
          |    "knora-api:userHasPermission" : "CR",
          |    "knora-api:valueCreationDate" : {
          |      "@type" : "xsd:dateTimeStamp",
          |      "@value" : "2019-04-10T08:41:45.353992Z"
          |    }
          |  } ],
          |  "knora-api:arkUrl" : {
          |    "@type" : "xsd:anyURI",
          |    "@value" : "http://0.0.0.0:3336/ark:/72163/1/0001/cUnhrC1DT821lwVWQSwEgg0"
          |  },
          |  "knora-api:attachedToProject" : {
          |    "@id" : "http://rdfh.ch/projects/0001"
          |  },
          |  "knora-api:attachedToUser" : {
          |    "@id" : "http://rdfh.ch/users/9XBCrDV3SRa7kS1WwynB4Q"
          |  },
          |  "knora-api:creationDate" : {
          |    "@type" : "xsd:dateTimeStamp",
          |    "@value" : "2019-04-10T08:41:45.353992Z"
          |  },
          |  "knora-api:hasPermissions" : "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
          |  "knora-api:userHasPermission" : "CR",
          |  "knora-api:versionArkUrl" : {
          |    "@type" : "xsd:anyURI",
          |    "@value" : "http://0.0.0.0:3336/ark:/72163/1/0001/cUnhrC1DT821lwVWQSwEgg0.20190410T084145353992Z"
          |  },
          |  "rdfs:label" : "test thing",
          |  "@context" : {
          |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
          |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
          |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
          |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
          |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
          |  }
          |}
        """.stripMargin

    val complexThingWithInvalidLiteralType: String =
        """{
          |  "@id" : "http://rdfh.ch/0001/cUnhrC1DT821lwVWQSwEgg",
          |  "@type" : "anything:Thing",
          |  "anything:hasBoolean" : {
          |    "@id" : "http://rdfh.ch/0001/cUnhrC1DT821lwVWQSwEgg/values/o-j0jdxMQvanmAdpAIOcFA",
          |    "@type" : "knora-api:BooleanValue",
          |    "knora-api:attachedToUser" : {
          |      "@id" : "http://rdfh.ch/users/9XBCrDV3SRa7kS1WwynB4Q"
          |    },
          |    "knora-api:booleanValueAsBoolean" : "invalid literal",
          |    "knora-api:hasPermissions" : "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
          |    "knora-api:userHasPermission" : "CR",
          |    "knora-api:valueCreationDate" : {
          |      "@type" : "xsd:dateTimeStamp",
          |      "@value" : "2019-04-10T08:41:45.353992Z"
          |    }
          |  },
          |  "knora-api:arkUrl" : {
          |    "@type" : "xsd:anyURI",
          |    "@value" : "http://0.0.0.0:3336/ark:/72163/1/0001/cUnhrC1DT821lwVWQSwEgg0"
          |  },
          |  "knora-api:attachedToProject" : {
          |    "@id" : "http://rdfh.ch/projects/0001"
          |  },
          |  "knora-api:attachedToUser" : {
          |    "@id" : "http://rdfh.ch/users/9XBCrDV3SRa7kS1WwynB4Q"
          |  },
          |  "knora-api:creationDate" : {
          |    "@type" : "xsd:dateTimeStamp",
          |    "@value" : "2019-04-10T08:41:45.353992Z"
          |  },
          |  "knora-api:hasPermissions" : "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
          |  "knora-api:userHasPermission" : "CR",
          |  "knora-api:versionArkUrl" : {
          |    "@type" : "xsd:anyURI",
          |    "@value" : "http://0.0.0.0:3336/ark:/72163/1/0001/cUnhrC1DT821lwVWQSwEgg0.20190410T084145353992Z"
          |  },
          |  "rdfs:label" : "test thing",
          |  "@context" : {
          |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
          |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
          |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
          |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
          |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
          |  }
          |}
        """.stripMargin

    val complexThingWithInvalidObjectType: String =
        """{
          |  "@id" : "http://rdfh.ch/0001/cUnhrC1DT821lwVWQSwEgg",
          |  "@type" : "anything:Thing",
          |  "anything:hasBoolean" : {
          |    "@id" : "http://rdfh.ch/0001/cUnhrC1DT821lwVWQSwEgg/values/lj35qx3vRUa6s1Q8s5Z5SA",
          |    "@type" : "knora-api:DateValue",
          |    "knora-api:attachedToUser" : {
          |      "@id" : "http://rdfh.ch/users/9XBCrDV3SRa7kS1WwynB4Q"
          |    },
          |    "knora-api:dateValueHasCalendar" : "GREGORIAN",
          |    "knora-api:dateValueHasEndEra" : "CE",
          |    "knora-api:dateValueHasEndYear" : 1489,
          |    "knora-api:dateValueHasStartEra" : "CE",
          |    "knora-api:dateValueHasStartYear" : 1489,
          |    "knora-api:hasPermissions" : "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
          |    "knora-api:userHasPermission" : "CR",
          |    "knora-api:valueAsString" : "GREGORIAN:1489 CE",
          |    "knora-api:valueCreationDate" : {
          |      "@type" : "xsd:dateTimeStamp",
          |      "@value" : "2019-04-10T08:41:45.353992Z"
          |    }
          |  },
          |  "knora-api:arkUrl" : {
          |    "@type" : "xsd:anyURI",
          |    "@value" : "http://0.0.0.0:3336/ark:/72163/1/0001/cUnhrC1DT821lwVWQSwEgg0"
          |  },
          |  "knora-api:attachedToProject" : {
          |    "@id" : "http://rdfh.ch/projects/0001"
          |  },
          |  "knora-api:attachedToUser" : {
          |    "@id" : "http://rdfh.ch/users/9XBCrDV3SRa7kS1WwynB4Q"
          |  },
          |  "knora-api:creationDate" : {
          |    "@type" : "xsd:dateTimeStamp",
          |    "@value" : "2019-04-10T08:41:45.353992Z"
          |  },
          |  "knora-api:hasPermissions" : "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
          |  "knora-api:userHasPermission" : "CR",
          |  "knora-api:versionArkUrl" : {
          |    "@type" : "xsd:anyURI",
          |    "@value" : "http://0.0.0.0:3336/ark:/72163/1/0001/cUnhrC1DT821lwVWQSwEgg0.20190410T084145353992Z"
          |  },
          |  "rdfs:label" : "test thing",
          |  "@context" : {
          |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
          |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
          |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
          |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
          |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
          |  }
          |}
        """.stripMargin

    val complexThingWithInvalidUseOfObjectInsteadOfIri: String =
        """{
          |  "@id" : "http://rdfh.ch/0001/cUnhrC1DT821lwVWQSwEgg",
          |  "@type" : "anything:Thing",
          |  "anything:hasRichtext" : {
          |    "@id" : "http://rdfh.ch/0001/cUnhrC1DT821lwVWQSwEgg/values/VY4XodOeSaOdttZ6rEkFPg",
          |    "@type" : "knora-api:TextValue",
          |    "knora-api:attachedToUser" : {
          |      "@id" : "http://rdfh.ch/users/9XBCrDV3SRa7kS1WwynB4Q"
          |    },
          |    "knora-api:hasPermissions" : "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
          |    "knora-api:textValueAsXml" : "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<text><p><strong>this is</strong> text</p> with standoff</text>",
          |    "knora-api:textValueHasMapping" : {
          |      "@id" : "http://rdfh.ch/standoff/mappings/StandardMapping",
          |      "@type" : "knora-api:XMLToStandoffMapping",
          |      "knora-api:hasMappingElement" : {
          |        "@type" : "knora-base:MappingElement",
          |	       "knora-api:mappingHasXMLTagname" : "p"
          |      }
          |    },
          |    "knora-api:userHasPermission" : "CR",
          |    "knora-api:valueCreationDate" : {
          |      "@type" : "xsd:dateTimeStamp",
          |      "@value" : "2019-04-10T08:41:45.353992Z"
          |    }
          |  },
          |  "knora-api:arkUrl" : {
          |    "@type" : "xsd:anyURI",
          |    "@value" : "http://0.0.0.0:3336/ark:/72163/1/0001/cUnhrC1DT821lwVWQSwEgg0"
          |  },
          |  "knora-api:attachedToProject" : {
          |    "@id" : "http://rdfh.ch/projects/0001"
          |  },
          |  "knora-api:attachedToUser" : {
          |    "@id" : "http://rdfh.ch/users/9XBCrDV3SRa7kS1WwynB4Q"
          |  },
          |  "knora-api:creationDate" : {
          |    "@type" : "xsd:dateTimeStamp",
          |    "@value" : "2019-04-10T08:41:45.353992Z"
          |  },
          |  "knora-api:hasPermissions" : "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
          |  "knora-api:userHasPermission" : "CR",
          |  "knora-api:versionArkUrl" : {
          |    "@type" : "xsd:anyURI",
          |    "@value" : "http://0.0.0.0:3336/ark:/72163/1/0001/cUnhrC1DT821lwVWQSwEgg0.20190410T084145353992Z"
          |  },
          |  "rdfs:label" : "test thing",
          |  "@context" : {
          |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
          |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
          |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
          |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
          |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
          |  }
          |}
        """.stripMargin

    val simpleThingWithInvalidDatatype: String =
        """
          |{
          |  "@id" : "http://rdfh.ch/0001/oGI65x9pQkK6JhsoqavTGA",
          |  "@type" : "anything:Thing",
          |  "anything:hasDecimal" : {
          |    "@type" : "knora-api:Date",
          |    "@value" : "GREGORIAN:1489 CE"
          |  },
          |  "knora-api:arkUrl" : {
          |    "@type" : "xsd:anyURI",
          |    "@value" : "http://0.0.0.0:3336/ark:/72163/1/0001/oGI65x9pQkK6JhsoqavTGAE"
          |  },
          |  "knora-api:versionArkUrl" : {
          |    "@type" : "xsd:anyURI",
          |    "@value" : "http://0.0.0.0:3336/ark:/72163/1/0001/oGI65x9pQkK6JhsoqavTGAE.20190410T124515840198Z"
          |  },
          |  "rdfs:label" : "test thing",
          |  "@context" : {
          |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
          |    "knora-api" : "http://api.knora.org/ontology/knora-api/simple/v2#",
          |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
          |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
          |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/simple/v2#"
          |  }
          |}
        """.stripMargin

    val simpleThingWithMissingLabel: String =
        """
          |{
          |  "@id" : "http://rdfh.ch/0001/oGI65x9pQkK6JhsoqavTGA",
          |  "@type" : "anything:Thing",
          |  "@context" : {
          |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
          |    "knora-api" : "http://api.knora.org/ontology/knora-api/simple/v2#",
          |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
          |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
          |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/simple/v2#"
          |  }
          |}
        """.stripMargin

    val correctUser: String =
        """
          |{
          |  "username" : "test",
          |  "email" : "test@example.org",
          |  "familyName" : "Tester",
          |  "givenName": "Test",
          |  "password" : "test",
          |  "preferredLanguage" : "en",
          |  "status" : true,
          |  "isInProject" : [ "http://rdfh.ch/projects/0001" ],
          |  "isInGroup" : [],
          |  "isInSystemAdminGroup" : false
          |}
        """.stripMargin

    val userWithExtraProperty: String =
        """
          |{
          |  "username" : "test",
          |  "extraProperty" : "test",
          |  "email" : "test@example.org",
          |  "familyName" : "Tester",
          |  "givenName": "Test",
          |  "password" : "test",
          |  "preferredLanguage" : "en",
          |  "status" : true,
          |  "isInProject" : [ "http://rdfh.ch/projects/0001" ],
          |  "isInGroup" : [],
          |  "isInSystemAdminGroup" : false
          |}
        """.stripMargin

    val userWithMissingUsername: String =
        """
          |{
          |  "email" : "test@example.org",
          |  "familyName" : "Tester",
          |  "givenName": "Test",
          |  "password" : "test",
          |  "preferredLanguage" : "en",
          |  "status" : true,
          |  "isInProject" : [ "http://rdfh.ch/projects/0001" ],
          |  "isInGroup" : [],
          |  "isInSystemAdminGroup" : false
          |}
        """.stripMargin


    val userWithInvalidObjectType: String =
        """
          |{
          |  "username" : "test",
          |  "email" : "test@example.org",
          |  "familyName" : "Tester",
          |  "givenName": "Test",
          |  "password" : "test",
          |  "preferredLanguage" : "en",
          |  "status" : "invalidValue",
          |  "isInProject" : [ "http://rdfh.ch/projects/0001" ],
          |  "isInGroup" : [],
          |  "isInSystemAdminGroup" : false
          |}
        """.stripMargin
}
