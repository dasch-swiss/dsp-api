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

    private val instanceChecker: InstanceChecker = InstanceChecker.getJsonLDChecker(log)

    "The InstanceChecker" should {
        "reject a JSON-LD instance with an extra property" in {
            assertThrows[AssertionException] {
                instanceChecker.check(
                    instanceResponse = InstanceCheckerSpec.thingWithExtraProperty,
                    expectedClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
                    knoraRouteGet = doGetRequest
                )
            }
        }

        "reject a JSON-LD instance with an extra property object" in {
            assertThrows[AssertionException] {
                instanceChecker.check(
                    instanceResponse = InstanceCheckerSpec.thingWithExtraPropertyObject,
                    expectedClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
                    knoraRouteGet = doGetRequest
                )
            }
        }

        "reject a JSON-LD instance with an invalid literal type" in {
            assertThrows[AssertionException] {
                instanceChecker.check(
                    instanceResponse = InstanceCheckerSpec.thingWithInvalidLiteralType,
                    expectedClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
                    knoraRouteGet = doGetRequest
                )
            }
        }

        "reject a JSON-LD instance with an invalid object type" in {
            assertThrows[AssertionException] {
                instanceChecker.check(
                    instanceResponse = InstanceCheckerSpec.thingWithInvalidObjectType,
                    expectedClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
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

    val thingWithExtraProperty: String =
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

    val thingWithExtraPropertyObject: String =
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

    val thingWithInvalidLiteralType: String =
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

    val thingWithInvalidObjectType: String =
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

    val correctThingInstance: String =
        """{
          |  "@id" : "http://rdfh.ch/0001/cUnhrC1DT821lwVWQSwEgg",
          |  "@type" : "anything:Thing",
          |  "anything:hasBoolean" : {
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
          |  "anything:hasColor" : {
          |    "@id" : "http://rdfh.ch/0001/cUnhrC1DT821lwVWQSwEgg/values/mZzh9KauSMeA3J0jE3WW3w",
          |    "@type" : "knora-api:ColorValue",
          |    "knora-api:attachedToUser" : {
          |      "@id" : "http://rdfh.ch/users/9XBCrDV3SRa7kS1WwynB4Q"
          |    },
          |    "knora-api:colorValueAsColor" : "#ff3333",
          |    "knora-api:hasPermissions" : "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
          |    "knora-api:userHasPermission" : "CR",
          |    "knora-api:valueCreationDate" : {
          |      "@type" : "xsd:dateTimeStamp",
          |      "@value" : "2019-04-10T08:41:45.353992Z"
          |    }
          |  },
          |  "anything:hasDate" : {
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
          |  "anything:hasDecimal" : {
          |    "@id" : "http://rdfh.ch/0001/cUnhrC1DT821lwVWQSwEgg/values/Zp50VNGbTmKvgPBRykZeng",
          |    "@type" : "knora-api:DecimalValue",
          |    "knora-api:attachedToUser" : {
          |      "@id" : "http://rdfh.ch/users/9XBCrDV3SRa7kS1WwynB4Q"
          |    },
          |    "knora-api:decimalValueAsDecimal" : {
          |      "@type" : "xsd:decimal",
          |      "@value" : "100000000000000.000000000000001"
          |    },
          |    "knora-api:hasPermissions" : "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
          |    "knora-api:userHasPermission" : "CR",
          |    "knora-api:valueCreationDate" : {
          |      "@type" : "xsd:dateTimeStamp",
          |      "@value" : "2019-04-10T08:41:45.353992Z"
          |    }
          |  },
          |  "anything:hasGeometry" : {
          |    "@id" : "http://rdfh.ch/0001/cUnhrC1DT821lwVWQSwEgg/values/vaJ1MeVmRIWK3cTrc5wVEA",
          |    "@type" : "knora-api:GeomValue",
          |    "knora-api:attachedToUser" : {
          |      "@id" : "http://rdfh.ch/users/9XBCrDV3SRa7kS1WwynB4Q"
          |    },
          |    "knora-api:geometryValueAsGeometry" : "{\"status\":\"active\",\"lineColor\":\"#ff3333\",\"lineWidth\":2,\"points\":[{\"x\":0.08098591549295775,\"y\":0.16741071428571427},{\"x\":0.7394366197183099,\"y\":0.7299107142857143}],\"type\":\"rectangle\",\"original_index\":0}",
          |    "knora-api:hasPermissions" : "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
          |    "knora-api:userHasPermission" : "CR",
          |    "knora-api:valueCreationDate" : {
          |      "@type" : "xsd:dateTimeStamp",
          |      "@value" : "2019-04-10T08:41:45.353992Z"
          |    }
          |  },
          |  "anything:hasGeoname" : {
          |    "@id" : "http://rdfh.ch/0001/cUnhrC1DT821lwVWQSwEgg/values/U_Dw08FDSHWYOvqlvZi3WA",
          |    "@type" : "knora-api:GeonameValue",
          |    "knora-api:attachedToUser" : {
          |      "@id" : "http://rdfh.ch/users/9XBCrDV3SRa7kS1WwynB4Q"
          |    },
          |    "knora-api:geonameValueAsGeonameCode" : "2661604",
          |    "knora-api:hasPermissions" : "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
          |    "knora-api:userHasPermission" : "CR",
          |    "knora-api:valueCreationDate" : {
          |      "@type" : "xsd:dateTimeStamp",
          |      "@value" : "2019-04-10T08:41:45.353992Z"
          |    }
          |  },
          |  "anything:hasInteger" : [ {
          |    "@id" : "http://rdfh.ch/0001/cUnhrC1DT821lwVWQSwEgg/values/QGfE8jxJSqumzvYn37Rsng",
          |    "@type" : "knora-api:IntValue",
          |    "knora-api:attachedToUser" : {
          |      "@id" : "http://rdfh.ch/users/9XBCrDV3SRa7kS1WwynB4Q"
          |    },
          |    "knora-api:hasPermissions" : "CR knora-admin:Creator|V http://rdfh.ch/groups/0001/thing-searcher",
          |    "knora-api:intValueAsInt" : 5,
          |    "knora-api:userHasPermission" : "CR",
          |    "knora-api:valueCreationDate" : {
          |      "@type" : "xsd:dateTimeStamp",
          |      "@value" : "2019-04-10T08:41:45.353992Z"
          |    },
          |    "knora-api:valueHasComment" : "this is the number five"
          |  }, {
          |    "@id" : "http://rdfh.ch/0001/cUnhrC1DT821lwVWQSwEgg/values/e5YJ_1dlRiKsHD8ERNYP4A",
          |    "@type" : "knora-api:IntValue",
          |    "knora-api:attachedToUser" : {
          |      "@id" : "http://rdfh.ch/users/9XBCrDV3SRa7kS1WwynB4Q"
          |    },
          |    "knora-api:hasPermissions" : "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
          |    "knora-api:intValueAsInt" : 6,
          |    "knora-api:userHasPermission" : "CR",
          |    "knora-api:valueCreationDate" : {
          |      "@type" : "xsd:dateTimeStamp",
          |      "@value" : "2019-04-10T08:41:45.353992Z"
          |    }
          |  } ],
          |  "anything:hasInterval" : {
          |    "@id" : "http://rdfh.ch/0001/cUnhrC1DT821lwVWQSwEgg/values/ELr-bNGqRRCRJeSjfnNSuA",
          |    "@type" : "knora-api:IntervalValue",
          |    "knora-api:attachedToUser" : {
          |      "@id" : "http://rdfh.ch/users/9XBCrDV3SRa7kS1WwynB4Q"
          |    },
          |    "knora-api:hasPermissions" : "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
          |    "knora-api:intervalValueHasEnd" : {
          |      "@type" : "xsd:decimal",
          |      "@value" : "3.4"
          |    },
          |    "knora-api:intervalValueHasStart" : {
          |      "@type" : "xsd:decimal",
          |      "@value" : "1.2"
          |    },
          |    "knora-api:userHasPermission" : "CR",
          |    "knora-api:valueCreationDate" : {
          |      "@type" : "xsd:dateTimeStamp",
          |      "@value" : "2019-04-10T08:41:45.353992Z"
          |    }
          |  },
          |  "anything:hasListItem" : {
          |    "@id" : "http://rdfh.ch/0001/cUnhrC1DT821lwVWQSwEgg/values/uxIWvgVySRiVZOQUk8ggaA",
          |    "@type" : "knora-api:ListValue",
          |    "knora-api:attachedToUser" : {
          |      "@id" : "http://rdfh.ch/users/9XBCrDV3SRa7kS1WwynB4Q"
          |    },
          |    "knora-api:hasPermissions" : "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
          |    "knora-api:listValueAsListNode" : {
          |      "@id" : "http://rdfh.ch/lists/0001/treeList03"
          |    },
          |    "knora-api:listValueAsListNodeLabel" : "Tree list node 03",
          |    "knora-api:userHasPermission" : "CR",
          |    "knora-api:valueCreationDate" : {
          |      "@type" : "xsd:dateTimeStamp",
          |      "@value" : "2019-04-10T08:41:45.353992Z"
          |    }
          |  },
          |  "anything:hasOtherThingValue" : {
          |    "@id" : "http://rdfh.ch/0001/cUnhrC1DT821lwVWQSwEgg/values/fvrSWN1CQWWeTZTABM_NHQ",
          |    "@type" : "knora-api:LinkValue",
          |    "knora-api:attachedToUser" : {
          |      "@id" : "http://rdfh.ch/users/9XBCrDV3SRa7kS1WwynB4Q"
          |    },
          |    "knora-api:hasPermissions" : "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
          |    "knora-api:linkValueHasTarget" : {
          |      "@id" : "http://rdfh.ch/0001/a-thing",
          |      "@type" : "anything:Thing",
          |      "knora-api:arkUrl" : {
          |        "@type" : "xsd:anyURI",
          |        "@value" : "http://0.0.0.0:3336/ark:/72163/1/0001/a=thingO"
          |      },
          |      "knora-api:attachedToProject" : {
          |        "@id" : "http://rdfh.ch/projects/0001"
          |      },
          |      "knora-api:attachedToUser" : {
          |        "@id" : "http://rdfh.ch/users/9XBCrDV3SRa7kS1WwynB4Q"
          |      },
          |      "knora-api:creationDate" : {
          |        "@type" : "xsd:dateTimeStamp",
          |        "@value" : "2016-03-02T15:05:10Z"
          |      },
          |      "knora-api:hasPermissions" : "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:UnknownUser",
          |      "knora-api:userHasPermission" : "CR",
          |      "knora-api:versionArkUrl" : {
          |        "@type" : "xsd:anyURI",
          |        "@value" : "http://0.0.0.0:3336/ark:/72163/1/0001/a=thingO.20160302T150510Z"
          |      },
          |      "rdfs:label" : "A thing"
          |    },
          |    "knora-api:userHasPermission" : "CR",
          |    "knora-api:valueCreationDate" : {
          |      "@type" : "xsd:dateTimeStamp",
          |      "@value" : "2019-04-10T08:41:45.353992Z"
          |    }
          |  },
          |  "anything:hasRichtext" : {
          |    "@id" : "http://rdfh.ch/0001/cUnhrC1DT821lwVWQSwEgg/values/VY4XodOeSaOdttZ6rEkFPg",
          |    "@type" : "knora-api:TextValue",
          |    "knora-api:attachedToUser" : {
          |      "@id" : "http://rdfh.ch/users/9XBCrDV3SRa7kS1WwynB4Q"
          |    },
          |    "knora-api:hasPermissions" : "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
          |    "knora-api:textValueAsXml" : "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<text><p><strong>this is</strong> text</p> with standoff</text>",
          |    "knora-api:textValueHasMapping" : {
          |      "@id" : "http://rdfh.ch/standoff/mappings/StandardMapping"
          |    },
          |    "knora-api:userHasPermission" : "CR",
          |    "knora-api:valueCreationDate" : {
          |      "@type" : "xsd:dateTimeStamp",
          |      "@value" : "2019-04-10T08:41:45.353992Z"
          |    }
          |  },
          |  "anything:hasText" : {
          |    "@id" : "http://rdfh.ch/0001/cUnhrC1DT821lwVWQSwEgg/values/CeOBKM0qSX2KBOy7Yjx_TA",
          |    "@type" : "knora-api:TextValue",
          |    "knora-api:attachedToUser" : {
          |      "@id" : "http://rdfh.ch/users/9XBCrDV3SRa7kS1WwynB4Q"
          |    },
          |    "knora-api:hasPermissions" : "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
          |    "knora-api:userHasPermission" : "CR",
          |    "knora-api:valueAsString" : "this is text without standoff",
          |    "knora-api:valueCreationDate" : {
          |      "@type" : "xsd:dateTimeStamp",
          |      "@value" : "2019-04-10T08:41:45.353992Z"
          |    }
          |  },
          |  "anything:hasUri" : {
          |    "@id" : "http://rdfh.ch/0001/cUnhrC1DT821lwVWQSwEgg/values/8FT4QZnJQ2KNtdGpOaUgPw",
          |    "@type" : "knora-api:UriValue",
          |    "knora-api:attachedToUser" : {
          |      "@id" : "http://rdfh.ch/users/9XBCrDV3SRa7kS1WwynB4Q"
          |    },
          |    "knora-api:hasPermissions" : "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
          |    "knora-api:uriValueAsUri" : {
          |      "@type" : "xsd:anyURI",
          |      "@value" : "https://www.knora.org"
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
}