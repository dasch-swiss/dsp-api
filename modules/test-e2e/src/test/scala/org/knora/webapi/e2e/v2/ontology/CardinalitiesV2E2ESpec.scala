/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.e2e.v2.ontology

import sttp.client4.*
import sttp.model.StatusCode
import zio.*
import zio.test.*

import dsp.errors.BadRequestException
import org.knora.webapi.E2EZSpec
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.util.rdf.JsonLDDocument
import org.knora.webapi.messages.util.rdf.JsonLDKeywords
import org.knora.webapi.messages.util.rdf.JsonLDUtil
import org.knora.webapi.sharedtestdata.SharedTestDataADM.rootUser
import org.knora.webapi.slice.admin.domain.model.KnoraProject.*
import org.knora.webapi.slice.admin.domain.service.KnoraProjectService
import org.knora.webapi.slice.api.admin.model.ProjectsEndpointsRequestsAndResponses.ProjectCreateRequest
import org.knora.webapi.slice.common.KnoraIris.OntologyIri
import org.knora.webapi.testservices.ResponseOps.assert200
import org.knora.webapi.testservices.TestApiClient

object CardinalitiesV2E2ESpec extends E2EZSpec {

  private val ontologyName = "inherit"

  private val superClassName = "SuperClass"
  private val subClassName   = "SubClass"

  private val superClassProperty1 = "superClassProperty1"
  private val superClassProperty2 = "superClassProperty2"
  private val subClassProperty1   = "subClassProperty1"
  private val subClassProperty2   = "subClassProperty2"

  private def createTestOntologyInNewProject(shortname: String, shortcode: String) =
    for {
      projectIri        <- createProject(shortname, shortcode)
      iriAndLmd         <- createOntology(projectIri)
      (ontologyIri, lmd) = iriAndLmd

      lmd <- createClass(ontologyIri, superClassName, None, lmd)
      lmd <- createClass(ontologyIri, subClassName, Some(superClassName), lmd)

      lmd <- createProperty(ontologyIri, superClassProperty1, lmd)
      lmd <- createProperty(ontologyIri, superClassProperty2, lmd)
      lmd <- createProperty(ontologyIri, subClassProperty1, lmd)
      lmd <- createProperty(ontologyIri, subClassProperty2, lmd)
    } yield (projectIri, ontologyIri, lmd)

  private def createProject(shortname: String, shortcode: String): ZIO[KnoraProjectService, Throwable, ProjectIri] = {
    val createRequest = ProjectCreateRequest(
      shortname = Shortname.unsafeFrom(shortname),
      shortcode = Shortcode.unsafeFrom(shortcode),
      description = List(Description.unsafeFrom("test project", Some("en"))),
    )
    ZIO.serviceWithZIO[KnoraProjectService](_.createProject(createRequest)).map(_.id)
  }

  private def createOntology(projectIri: ProjectIri): ZIO[TestApiClient, Throwable, (OntologyIri, String)] = {
    val payload =
      s"""|{
          |  "knora-api:ontologyName" : "$ontologyName",
          |  "knora-api:attachedToProject" : {
          |    "@id" : "$projectIri"
          |  },
          |  "rdfs:label" : "inheritance ontology",
          |  "@context" : {
          |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
          |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#"
          |  }
          |}
          |""".stripMargin
    for {
      document <- TestApiClient
                    .postJsonLd(uri"/v2/ontologies", payload, rootUser)
                    .flatMap(_.assert200)
                    .mapAttempt(JsonLDUtil.parseJsonLD)
      ontologyIri <- ZIO
                       .fromEither(document.body.getRequiredString(JsonLDKeywords.ID))
                       .mapError(new AssertionError(_))
                       .mapAttempt(str => OntologyIri.unsafeFrom(sf.toSmartIri(str)))
    } yield (ontologyIri, getLastModificationDate(document))
  }

  private def createClass(
    ontologyIri: OntologyIri,
    className: String,
    superClass: Option[String],
    lastModificationDate: String,
  ) = {
    val sp = superClass match {
      case None        => "knora-api:Resource"
      case Some(value) => s"$ontologyName:$value"
    }
    val payload =
      f"""|{
          |  "@id" : "$ontologyIri",
          |  "@type" : "owl:Ontology",
          |  "knora-api:lastModificationDate" : {
          |    "@type" : "xsd:dateTimeStamp",
          |    "@value" : "$lastModificationDate"
          |  },
          |  "@graph" : [
          |    {
          |      "@id" : "$ontologyName:$className",
          |      "@type" : "owl:Class",
          |      "rdfs:label" : {
          |        "@language" : "en",
          |        "@value" : "$className"
          |      },
          |      "rdfs:subClassOf" : {
          |        "@id" : "$sp"
          |      }
          |    }
          |  ],
          |  "@context" : {
          |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
          |    "$ontologyName" : "$ontologyIri#",
          |    "owl" : "http://www.w3.org/2002/07/owl#",
          |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
          |    "xsd" : "http://www.w3.org/2001/XMLSchema#"
          |  }
          |}
          |""".stripMargin
    TestApiClient
      .postJsonLd(uri"/v2/ontologies/classes", payload, rootUser)
      .flatMap(_.assert200)
      .mapAttempt(JsonLDUtil.parseJsonLD)
      .map(getLastModificationDate)
  }

  private def createProperty(ontologyIri: OntologyIri, propertyName: String, lastModificationDate: String) = {
    val payload =
      s"""|{
          |  "@id" : "$ontologyIri",
          |  "@type" : "owl:Ontology",
          |  "knora-api:lastModificationDate" : {
          |    "@type" : "xsd:dateTimeStamp",
          |    "@value" : "$lastModificationDate"
          |  },
          |  "@graph" : [
          |    {
          |      "@id" : "$ontologyName:$propertyName",
          |      "@type" : "owl:ObjectProperty",
          |      "knora-api:objectType" : {
          |        "@id" : "knora-api:IntValue"
          |      },
          |      "rdfs:label" : {
          |        "@language" : "en",
          |        "@value" : "property $propertyName"
          |      },
          |      "rdfs:subPropertyOf" : {
          |        "@id" : "knora-api:hasValue"
          |      }
          |    }
          |  ],
          |  "@context" : {
          |    "$ontologyName" : "$ontologyIri#",
          |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
          |    "salsah-gui" : "http://api.knora.org/ontology/salsah-gui/v2#",
          |    "owl" : "http://www.w3.org/2002/07/owl#",
          |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
          |    "xsd" : "http://www.w3.org/2001/XMLSchema#"
          |  }
          |}
          |""".stripMargin
    TestApiClient
      .postJsonLd(uri"/v2/ontologies/properties", payload, rootUser)
      .flatMap(_.assert200)
      .mapAttempt(JsonLDUtil.parseJsonLD)
      .map(getLastModificationDate)
  }

  private def addRequiredCardinalityToClass(
    ontologyIri: OntologyIri,
    className: String,
    propertyName: String,
    lastModificationDate: String,
  ) = {
    val payload =
      s"""|{
          |  "@id" : "$ontologyIri",
          |  "@type" : "owl:Ontology",
          |  "knora-api:lastModificationDate" : {
          |    "@type" : "xsd:dateTimeStamp",
          |    "@value" : "$lastModificationDate"
          |  },
          |  "@graph" : [
          |    {
          |      "@id" : "$ontologyName:$className",
          |      "@type" : "owl:Class",
          |      "rdfs:subClassOf" : {
          |        "@type": "owl:Restriction",
          |        "owl:cardinality": 1,
          |        "owl:onProperty": {
          |          "@id" : "$ontologyName:$propertyName"
          |        }
          |      }
          |    }
          |  ],
          |  "@context" : {
          |    "$ontologyName" : "$ontologyIri#",
          |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
          |    "owl" : "http://www.w3.org/2002/07/owl#",
          |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
          |    "xsd" : "http://www.w3.org/2001/XMLSchema#"
          |  }
          |}
          |""".stripMargin
    TestApiClient
      .postJsonLd(uri"/v2/ontologies/cardinalities", payload, rootUser)
      .flatMap(_.assert200)
      .mapAttempt(JsonLDUtil.parseJsonLD)
      .map(getLastModificationDate)
  }

  private def createValue(
    projectIri: ProjectIri,
    ontologyIri: OntologyIri,
    className: String,
    propertyNames: List[String],
  ) = {
    val propDefinitions =
      propertyNames
        .map(prop => s"""|  "$ontologyName:$prop" : {
                         |    "@type" : "knora-api:IntValue",
                         |    "knora-api:intValueAsInt" : 42
                         |  },""".stripMargin)
        .mkString("\n")
    val payload =
      s"""|{
          |  "@type" : "$ontologyName:$className",
          |  "rdfs:label": "Instance of $className",
          |  "knora-api:attachedToProject" : {
          |    "@id" : "$projectIri"
          |  },
          |$propDefinitions
          |  "@context" : {
          |    "$ontologyName" : "$ontologyIri#",
          |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
          |    "owl" : "http://www.w3.org/2002/07/owl#",
          |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
          |    "xsd" : "http://www.w3.org/2001/XMLSchema#"
          |  }
          |}
          |""".stripMargin
    TestApiClient.postJsonLd(uri"/v2/resources", payload, rootUser).map(_.code)
  }

  private def getLastModificationDate(document: JsonLDDocument): String =
    document.body
      .getRequiredObject(OntologyConstants.KnoraApiV2Complex.LastModificationDate)
      .fold(e => throw BadRequestException(e), identity)
      .getRequiredString(JsonLDKeywords.VALUE)
      .fold(msg => throw BadRequestException(msg), identity)

  override val e2eSpec = suite("Ontologies endpoint")(
    test("be able to create resource instances with all properties, when adding cardinalities on super class first") {
      for {
        created                       <- createTestOntologyInNewProject("test1", "4441")
        (projectIri, ontologyIri, lmd) = created

        // first adding the cardinalities to the *super*, then to the *sub* class
        lmd <- addRequiredCardinalityToClass(ontologyIri, superClassName, superClassProperty1, lmd)
        lmd <- addRequiredCardinalityToClass(ontologyIri, superClassName, superClassProperty2, lmd)
        lmd <- addRequiredCardinalityToClass(ontologyIri, subClassName, subClassProperty1, lmd)
        _   <- addRequiredCardinalityToClass(ontologyIri, subClassName, subClassProperty2, lmd)

        superStatus <-
          createValue(projectIri, ontologyIri, superClassName, List(superClassProperty1, superClassProperty2))
        subStatus <- createValue(
                       projectIri,
                       ontologyIri,
                       subClassName,
                       List(superClassProperty1, superClassProperty2, subClassProperty1, subClassProperty2),
                     )
      } yield assertTrue(superStatus == StatusCode.Ok, subStatus == StatusCode.Ok)
    },
    test("be able to create resource instances with all properties, when adding cardinalities on sub class first") {
      for {
        created                       <- createTestOntologyInNewProject("test2", "4442")
        (projectIri, ontologyIri, lmd) = created

        // first adding the cardinalities to the *sub*, then to the *super* class
        lmd <- addRequiredCardinalityToClass(ontologyIri, subClassName, subClassProperty1, lmd)
        lmd <- addRequiredCardinalityToClass(ontologyIri, subClassName, subClassProperty2, lmd)
        lmd <- addRequiredCardinalityToClass(ontologyIri, superClassName, superClassProperty1, lmd)
        _   <- addRequiredCardinalityToClass(ontologyIri, superClassName, superClassProperty2, lmd)

        superStatus <-
          createValue(projectIri, ontologyIri, superClassName, List(superClassProperty1, superClassProperty2))
        subStatus <- createValue(
                       projectIri,
                       ontologyIri,
                       subClassName,
                       List(superClassProperty1, superClassProperty2, subClassProperty1, subClassProperty2),
                     )
      } yield assertTrue(
        superStatus == StatusCode.Ok,
        subStatus == StatusCode.Ok,
      )
    },
    test(
      "not be able to create subclass instances with missing required properties defined on superclass, when adding cardinalities on super class first",
    ) {
      for {
        created                       <- createTestOntologyInNewProject("test3", "4443")
        (projectIri, ontologyIri, lmd) = created

        // first adding the cardinalities to the *super*, then to the *sub* class
        lmd <- addRequiredCardinalityToClass(ontologyIri, superClassName, superClassProperty1, lmd)
        lmd <- addRequiredCardinalityToClass(ontologyIri, superClassName, superClassProperty2, lmd)
        lmd <- addRequiredCardinalityToClass(ontologyIri, subClassName, subClassProperty1, lmd)
        _   <- addRequiredCardinalityToClass(ontologyIri, subClassName, subClassProperty2, lmd)

        // trying to create an instance of the sub class, but missing mandatory props defined on super class
        status <- createValue(projectIri, ontologyIri, subClassName, List(subClassProperty1, subClassProperty2))
      } yield assertTrue(status == StatusCode.BadRequest)
    },
    test(
      "not be able to create subclass instances with missing properties defined on superclass, when adding cardinalities on sub class first",
    ) {
      for {
        created                       <- createTestOntologyInNewProject("test4", "4444")
        (projectIri, ontologyIri, lmd) = created

        // first adding the cardinalities to the *sub*, then to the *super* class
        lmd <- addRequiredCardinalityToClass(ontologyIri, subClassName, subClassProperty1, lmd)
        lmd <- addRequiredCardinalityToClass(ontologyIri, subClassName, subClassProperty2, lmd)
        lmd <- addRequiredCardinalityToClass(ontologyIri, superClassName, superClassProperty1, lmd)
        _   <- addRequiredCardinalityToClass(ontologyIri, superClassName, superClassProperty2, lmd)

        status <- createValue(projectIri, ontologyIri, subClassName, List(subClassProperty1, subClassProperty2))
      } yield assertTrue(status == StatusCode.BadRequest)
    },
  )
}
