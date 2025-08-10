/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.e2e

import org.apache.pekko

import java.nio.file.Paths
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration.NANOSECONDS

import dsp.errors.AssertionException
import org.knora.webapi.E2ESpec
import org.knora.webapi.messages.IriConversions.*
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.util.FileUtil

import pekko.http.scaladsl.testkit.RouteTestTimeout

/**
 * Tests [[InstanceChecker]].
 */
class InstanceCheckerSpec extends E2ESpec {
  private implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

  implicit def default: RouteTestTimeout = RouteTestTimeout(
    FiniteDuration(appConfig.defaultTimeout.toNanos, NANOSECONDS),
  )

  private val jsonLDInstanceChecker: InstanceChecker = InstanceChecker.getJsonLDChecker

  "The InstanceChecker" should {
    "accept a JSON-LD instance of anything:Thing" in {
      val testDing =
        FileUtil.readTextFile(Paths.get("test_data/generated_test_data/resourcesR2RV2/Testding.jsonld"))

      jsonLDInstanceChecker.check(
        instanceResponse = testDing,
        expectedClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
        knoraRouteGet = doGetRequest,
      )
    }

    "reject a JSON-LD instance of anything:Thing (in the complex schema) with an extra property" in {
      val exception = intercept[AssertionException] {
        jsonLDInstanceChecker.check(
          instanceResponse = InstanceCheckerSpec.complexThingWithExtraProperty,
          expectedClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
          knoraRouteGet = doGetRequest,
        )
      }

      assert(
        exception.getMessage == "One or more instance properties are not allowed by cardinalities: http://0.0.0.0:3333/ontology/0001/anything/v2#hasExtraProperty",
      )
    }

    "reject a JSON-LD instance of anything:Thing (in the complex schema) with an extra property object" in {
      val exception = intercept[AssertionException] {
        jsonLDInstanceChecker.check(
          instanceResponse = InstanceCheckerSpec.complexThingWithExtraPropertyObject,
          expectedClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
          knoraRouteGet = doGetRequest,
        )
      }

      assert(
        exception.getMessage == "Property http://0.0.0.0:3333/ontology/0001/anything/v2#hasBoolean has 2 objects, but its cardinality is 0-1",
      )
    }

    "reject a JSON-LD instance of anything:Thing (in the complex schema) with an invalid literal type" in {
      val exception = intercept[AssertionException] {
        jsonLDInstanceChecker.check(
          instanceResponse = InstanceCheckerSpec.complexThingWithInvalidLiteralType,
          expectedClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
          knoraRouteGet = doGetRequest,
        )
      }

      assert(
        exception.getMessage == "Property http://api.knora.org/ontology/knora-api/v2#booleanValueAsBoolean has an object of type http://www.w3.org/2001/XMLSchema#string with literal content 'invalid literal', but type http://www.w3.org/2001/XMLSchema#boolean was expected",
      )
    }

    "reject a JSON-LD instance of anything:Thing (in the complex schema) with an invalid object type" in {
      val exception = intercept[AssertionException] {
        jsonLDInstanceChecker.check(
          instanceResponse = InstanceCheckerSpec.complexThingWithInvalidObjectType,
          expectedClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
          knoraRouteGet = doGetRequest,
        )
      }

      assert(
        exception.getMessage == "Instance type http://api.knora.org/ontology/knora-api/v2#DateValue is not compatible with expected class IRI http://api.knora.org/ontology/knora-api/v2#BooleanValue",
      )
    }

    "reject a JSON-LD instance of anything:Thing (in the complex schema) with object content where an IRI is required" in {
      val exception = intercept[AssertionException] {
        jsonLDInstanceChecker.check(
          instanceResponse = InstanceCheckerSpec.complexThingWithInvalidUseOfObjectInsteadOfIri,
          expectedClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
          knoraRouteGet = doGetRequest,
        )
      }

      assert(
        exception.getMessage == "Property http://api.knora.org/ontology/knora-api/v2#textValueHasMapping requires an IRI referring to an instance of http://api.knora.org/ontology/knora-api/v2#XMLToStandoffMapping, but object content was received instead",
      )
    }

    "reject a JSON-LD instance of anything:Thing (in the simple schema) with an invalid datatype" in {
      val exception = intercept[AssertionException] {
        jsonLDInstanceChecker.check(
          instanceResponse = InstanceCheckerSpec.simpleThingWithInvalidDatatype,
          expectedClassIri = "http://0.0.0.0:3333/ontology/0001/anything/simple/v2#Thing".toSmartIri,
          knoraRouteGet = doGetRequest,
        )
      }

      assert(
        exception.getMessage == "Property http://0.0.0.0:3333/ontology/0001/anything/simple/v2#hasDecimal has an object of type http://api.knora.org/ontology/knora-api/simple/v2#Date with literal content 'GREGORIAN:1489 CE', but type http://www.w3.org/2001/XMLSchema#decimal was expected",
      )
    }

    "reject a JSON-LD instance of anything:Thing (in the simple schema) without an rdfs:label" in {
      val exception = intercept[AssertionException] {
        jsonLDInstanceChecker.check(
          instanceResponse = InstanceCheckerSpec.simpleThingWithMissingLabel,
          expectedClassIri = "http://0.0.0.0:3333/ontology/0001/anything/simple/v2#Thing".toSmartIri,
          knoraRouteGet = doGetRequest,
        )
      }

      assert(
        exception.getMessage == "Property http://www.w3.org/2000/01/rdf-schema#label has 0 objects, but its cardinality is 1",
      )
    }
  }
}

object InstanceCheckerSpec {

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
      |    "knora-api:arkUrl" : {
      |      "@type" : "xsd:anyURI",
      |      "@value" : "http://0.0.0.0:3336/ark:/72163/1/0001/cUnhrC1DT821lwVWQSwEgg0/o=j0jdxMQvanmAdpAIOcFA"
      |    },
      |    "knora-api:versionArkUrl" : {
      |      "@type" : "xsd:anyURI",
      |      "@value" : "http://0.0.0.0:3336/ark:/72163/1/0001/cUnhrC1DT821lwVWQSwEgg0/o=j0jdxMQvanmAdpAIOcFA.20190410T084145353992Z"
      |    },
      |    "knora-api:valueHasUUID" : "o-j0jdxMQvanmAdpAIOcFA",
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
      |    "knora-api:arkUrl" : {
      |      "@type" : "xsd:anyURI",
      |      "@value" : "http://0.0.0.0:3336/ark:/72163/1/0001/cUnhrC1DT821lwVWQSwEgg0/VY4XodOeSaOdttZ6rEkFPg"
      |    },
      |    "knora-api:versionArkUrl" : {
      |      "@type" : "xsd:anyURI",
      |      "@value" : "http://0.0.0.0:3336/ark:/72163/1/0001/cUnhrC1DT821lwVWQSwEgg0/VY4XodOeSaOdttZ6rEkFPg.20190410T084145353992Z"
      |    },
      |    "knora-api:valueHasUUID" : "VY4XodOeSaOdttZ6rEkFPg",
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
    """{
      |    "email": "anything.user01@example.org",
      |    "familyName": "User01",
      |    "givenName": "Anything",
      |    "groups": [
      |      {
      |        "description": "A group for thing searchers.",
      |        "id": "http://rdfh.ch/groups/0001/thing-searcher",
      |        "name": "Thing searcher",
      |        "project": {
      |          "description": [
      |            {
      |              "value": "Anything Project"
      |            }
      |          ],
      |          "id": "http://rdfh.ch/projects/0001",
      |          "keywords": [],
      |          "logo": null,
      |          "longname": "Anything Project",
      |          "ontologies": [
      |            "http://www.knora.org/ontology/0001/anything",
      |            "http://www.knora.org/ontology/0001/something"
      |          ],
      |          "selfjoin": false,
      |          "shortcode": "0001",
      |          "shortname": "anything",
      |          "status": true
      |        },
      |        "selfjoin": true,
      |        "status": true
      |      }
      |    ],
      |    "id": "http://rdfh.ch/users/9XBCrDV3SRa7kS1WwynB4Q",
      |    "lang": "de",
      |    "password": null,
      |    "permissions": {
      |      "administrativePermissionsPerProject": {
      |        "http://rdfh.ch/projects/0001": [
      |          {
      |            "additionalInformation": null,
      |            "name": "ProjectResourceCreateAllPermission",
      |            "permissionCode": null
      |          }
      |        ]
      |      },
      |      "groupsPerProject": {
      |        "http://rdfh.ch/projects/0001": [
      |          "http://rdfh.ch/groups/0001/thing-searcher",
      |          "http://www.knora.org/ontology/knora-admin#ProjectMember"
      |        ]
      |      }
      |    },
      |    "projects": [
      |      {
      |        "description": [
      |          {
      |            "value": "Anything Project"
      |          }
      |        ],
      |        "id": "http://rdfh.ch/projects/0001",
      |        "keywords": [],
      |        "logo": null,
      |        "longname": "Anything Project",
      |        "ontologies": [
      |          "http://www.knora.org/ontology/0001/anything",
      |          "http://www.knora.org/ontology/0001/something"
      |        ],
      |        "selfjoin": false,
      |        "shortcode": "0001",
      |        "shortname": "anything",
      |        "status": true
      |      }
      |    ],
      |    "sessionId": null,
      |    "status": true,
      |    "token": null,
      |    "username": "anything.user01"
      |}""".stripMargin

  val userWithExtraProperty: String =
    """
      |{
      |  "username" : "test",
      |  "id" : "http://rdfh.ch/users/normaluser",
      |  "extraProperty" : "test",
      |  "email" : "test@example.org",
      |  "familyName" : "Tester",
      |  "givenName": "Test",
      |  "password" : "test",
      |  "lang" : "en",
      |  "status" : true,
      |  "permissions": {
      |    "administrativePermissionsPerProject": {
      |      "http://rdfh.ch/projects/0001": [
      |        {
      |          "additionalInformation": null,
      |          "name": "ProjectResourceCreateAllPermission",
      |          "permissionCode": null
      |        }
      |      ]
      |    },
      |    "groupsPerProject": {
      |      "http://rdfh.ch/projects/0001": [
      |        "http://rdfh.ch/groups/0001/thing-searcher",
      |        "http://www.knora.org/ontology/knora-admin#ProjectMember"
      |      ]
      |    }
      |  },
      |  "projects" : [ "http://rdfh.ch/projects/0001" ],
      |  "groups" : []
      |}
        """.stripMargin

  val userWithMissingUsername: String =
    """
      |{
      |  "id" : "http://rdfh.ch/users/normaluser",
      |  "email" : "test@example.org",
      |  "familyName" : "Tester",
      |  "givenName": "Test",
      |  "password" : "test",
      |  "lang" : "en",
      |  "status" : true,
      |  "permissions": {
      |    "administrativePermissionsPerProject": {
      |      "http://rdfh.ch/projects/0001": [
      |        {
      |          "additionalInformation": null,
      |          "name": "ProjectResourceCreateAllPermission",
      |          "permissionCode": null
      |        }
      |      ]
      |    },
      |    "groupsPerProject": {
      |      "http://rdfh.ch/projects/0001": [
      |        "http://rdfh.ch/groups/0001/thing-searcher",
      |        "http://www.knora.org/ontology/knora-admin#ProjectMember"
      |      ]
      |    }
      |  },
      |  "projects" : [ "http://rdfh.ch/projects/0001" ],
      |  "groups" : []
      |}
        """.stripMargin

  val userWithInvalidObjectType: String =
    """
      |{
      |  "id" : "http://rdfh.ch/users/normaluser",
      |  "username" : "test",
      |  "email" : "test@example.org",
      |  "familyName" : "Tester",
      |  "givenName": "Test",
      |  "password" : "test",
      |  "lang" : "en",
      |  "status" : "invalidValue",
      |  "permissions": {
      |    "administrativePermissionsPerProject": {
      |      "http://rdfh.ch/projects/0001": [
      |        {
      |          "additionalInformation": null,
      |          "name": "ProjectResourceCreateAllPermission",
      |          "permissionCode": null
      |        }
      |      ]
      |    },
      |    "groupsPerProject": {
      |      "http://rdfh.ch/projects/0001": [
      |        "http://rdfh.ch/groups/0001/thing-searcher",
      |        "http://www.knora.org/ontology/knora-admin#ProjectMember"
      |      ]
      |    }
      |  },
      |  "projects" : [ "http://rdfh.ch/projects/0001" ],
      |  "groups" : []
      |}
        """.stripMargin
}
