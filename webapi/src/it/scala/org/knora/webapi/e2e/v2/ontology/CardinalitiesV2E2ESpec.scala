package org.knora.webapi.e2e.v2.ontology

import org.knora.webapi.E2ESpec
import akka.http.scaladsl.model.HttpEntity
import org.knora.webapi.RdfMediaTypes
import akka.http.scaladsl.model.headers.BasicHttpCredentials
import org.knora.webapi.sharedtestdata.SharedTestDataADM
import akka.http.scaladsl.model.StatusCodes
import org.knora.webapi.util.AkkaHttpUtils
import spray.json.JsString
import org.knora.webapi.messages.util.rdf.JsonLDUtil
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.util.rdf.JsonLDKeywords
import akka.http.scaladsl.model.HttpResponse

class CardinalitiesV2E2ESpec extends E2ESpec {

  private def createProject(shortname: String, shortcode: String) = {
    val payload =
      s"""|{
          |    "shortname": "$shortname",
          |    "shortcode": "$shortcode",
          |    "longname": "project $shortname",
          |    "description": [
          |        {
          |            "value": "project $shortname",
          |            "language": "en"
          |        }
          |    ],
          |    "keywords": [
          |        "test project"
          |    ],
          |    "status": true,
          |    "selfjoin": false
          |}
          |""".stripMargin
    val request = Post(
      s"$baseApiUrl/admin/projects",
      HttpEntity(RdfMediaTypes.`application/json`, payload)
    ) ~> addCredentials(rootCredentials)
    val response = singleAwaitingRequest(request)
    assert(response.status == StatusCodes.OK, responseToString(response))
    AkkaHttpUtils
      .httpResponseToJson(response)
      .fields("project")
      .asJsObject
      .fields("id")
      .asInstanceOf[JsString]
      .value
  }

  private def createOntology(projectIri: String) = {
    val payload =
      s"""|{
          |  "knora-api:ontologyName" : "inherit",
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
    val request = Post(
      s"$baseApiUrl/v2/ontologies",
      HttpEntity(RdfMediaTypes.`application/ld+json`, payload)
    ) ~> addCredentials(rootCredentials)
    val response = singleAwaitingRequest(request)
    assert(response.status == StatusCodes.OK, responseToString(response))
    val lastModificationDate = getLastModificationDate(response)
    val ontologyIri =
      JsonLDUtil
        .parseJsonLD(responseToString(response))
        .body
        .requireString(JsonLDKeywords.ID)
    (ontologyIri, lastModificationDate)
  }

  private def createClass(
    ontologyIri: String,
    ontologyName: String,
    className: String,
    superClass: Option[String],
    lastModificationDate: String
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
          |      "@id" : "inherit:$className",
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
    val request = Post(
      s"$baseApiUrl/v2/ontologies/classes",
      HttpEntity(RdfMediaTypes.`application/ld+json`, payload)
    ) ~> addCredentials(rootCredentials)
    val response = singleAwaitingRequest(request)
    assert(response.status == StatusCodes.OK, responseToString(response))
    getLastModificationDate(response)
  }

  private def getLastModificationDate(response: HttpResponse): String =
    JsonLDUtil
      .parseJsonLD(responseToString(response))
      .body
      .requireObject(OntologyConstants.KnoraApiV2Complex.LastModificationDate)
      .requireString(JsonLDKeywords.VALUE)

  private val rootCredentials = BasicHttpCredentials(SharedTestDataADM.rootUser.email, SharedTestDataADM.testPass)

  "Ontologies endpoint" should {
    "be able to create resource instances when adding cardinalities on super class first" in {

      val projectIri = createProject("test1", "4441")
      println(projectIri)

      val (ontologyIri, lmd)   = createOntology(projectIri)
      var lastModificationDate = lmd

      val superName = "SuperClass"
      lastModificationDate = createClass(
        ontologyIri = ontologyIri,
        ontologyName = "inherit",
        className = superName,
        superClass = None,
        lastModificationDate = lastModificationDate
      )

      val subName = "SubClass"
      lastModificationDate = createClass(
        ontologyIri = ontologyIri,
        ontologyName = "inherit",
        className = subName,
        superClass = Some(superName),
        lastModificationDate = lastModificationDate
      )

      // create properties

      // create sub class

      // add cardinalities for sub class

      // add cardinalities for super class

      // val resStr = responseToString(createOntologyResponse)
      // println(resStr)
      // println(responseToString(createSuperClassResponse))

    }
  }
}
