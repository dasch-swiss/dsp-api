package org.knora.webapi.e2e.v2

import java.io.File
import java.net.URLEncoder
import java.time.Instant

import akka.actor.{ActorSystem, Props}
import akka.http.scaladsl.model.headers.BasicHttpCredentials
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.testkit.RouteTestTimeout
import akka.pattern._
import akka.util.Timeout
import org.knora.webapi._
import org.knora.webapi.messages.store.triplestoremessages.ResetTriplestoreContent
import org.knora.webapi.messages.v2.responder.ontologymessages.{InputOntologiesV2, LoadOntologiesRequestV2}
import org.knora.webapi.responders._
import org.knora.webapi.routing.v2.OntologiesRouteV2
import org.knora.webapi.store._
import org.knora.webapi.util.IriConversions._
import org.knora.webapi.util._
import org.knora.webapi.util.jsonld._
import spray.json._

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContextExecutor}

object OntologyV2R2RSpec {
    private val imagesUserProfile = SharedTestDataADM.imagesUser01
    private val imagesUsername = imagesUserProfile.email
    private val imagesProjectIri = SharedTestDataADM.IMAGES_PROJECT_IRI

    private val anythingUserProfile = SharedTestDataADM.anythingAdminUser
    private val anythingUsername = anythingUserProfile.email

    private val password = "test"
}

/**
  * End-to-end test specification for API v2 ontology routes.
  */
class OntologyV2R2RSpec extends R2RSpec {

    import OntologyV2R2RSpec._

    override def testConfigSource: String =
        """
          |# akka.loglevel = "DEBUG"
          |# akka.stdout-loglevel = "DEBUG"
        """.stripMargin

    private implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    private val responderManager = system.actorOf(Props(new ResponderManager with LiveActorMaker), name = RESPONDER_MANAGER_ACTOR_NAME)
    private val storeManager = system.actorOf(Props(new StoreManager with LiveActorMaker), name = STORE_MANAGER_ACTOR_NAME)

    private val ontologiesPath = OntologiesRouteV2.knoraApiPath(system, settings, log)

    implicit private val timeout: Timeout = settings.defaultRestoreTimeout

    implicit def default(implicit system: ActorSystem): RouteTestTimeout = RouteTestTimeout(new DurationInt(360).second)

    implicit val ec: ExecutionContextExecutor = system.dispatcher

    private val rdfDataObjects = List()

    private val allOntologyMetadata: JsValue = JsonParser(FileUtil.readTextFile(new File("src/test/resources/test-data/ontologyR2RV2/allOntologyMetadata.json")))
    private val imagesOntologyMetadata: JsValue = JsonParser(FileUtil.readTextFile(new File("src/test/resources/test-data/ontologyR2RV2/imagesOntologyMetadata.json")))
    private val imagesOntologySimple: JsValue = JsonParser(FileUtil.readTextFile(new File("src/test/resources/test-data/ontologyR2RV2/imagesOntologySimple.json")))
    private val imagesOntologyWithValueObjects: JsValue = JsonParser(FileUtil.readTextFile(new File("src/test/resources/test-data/ontologyR2RV2/imagesOntologyWithValueObjects.json")))
    private val bildSimple: JsValue = JsonParser(FileUtil.readTextFile(new File("src/test/resources/test-data/ontologyR2RV2/bildSimple.json")))
    private val bildWithValueObjects: JsValue = JsonParser(FileUtil.readTextFile(new File("src/test/resources/test-data/ontologyR2RV2/bildWithValueObjects.json")))
    private val knoraApiOntologySimple: JsValue = JsonParser(FileUtil.readTextFile(new File("src/test/resources/test-data/ontologyR2RV2/knoraApiOntologySimple.json")))
    private val knoraApiWithValueObjects: JsValue = JsonParser(FileUtil.readTextFile(new File("src/test/resources/test-data/ontologyR2RV2/knoraApiWithValueObjects.json")))
    private val salsahGui: JsValue = JsonParser(FileUtil.readTextFile(new File("src/test/resources/test-data/ontologyR2RV2/salsahGuiOntology.json")))
    private val standoffSimple: JsValue = JsonParser(FileUtil.readTextFile(new File("src/test/resources/test-data/ontologyR2RV2/standoffOntologySimple.json")))
    private val standoffWithValueObjects: JsValue = JsonParser(FileUtil.readTextFile(new File("src/test/resources/test-data/ontologyR2RV2/standoffOntologyWithValueObjects.json")))
    private val incunabulaOntologySimple: JsValue = JsonParser(FileUtil.readTextFile(new File("src/test/resources/test-data/ontologyR2RV2/incunabulaOntologySimple.json")))
    private val incunabulaOntologyWithValueObjects: JsValue = JsonParser(FileUtil.readTextFile(new File("src/test/resources/test-data/ontologyR2RV2/incunabulaOntologyWithValueObjects.json")))
    private val knoraApiDate: JsValue = JsonParser(FileUtil.readTextFile(new File("src/test/resources/test-data/ontologyR2RV2/knoraApiDate.json")))
    private val knoraApiDateValue: JsValue = JsonParser(FileUtil.readTextFile(new File("src/test/resources/test-data/ontologyR2RV2/knoraApiDateValue.json")))
    private val knoraApiSimpleHasColor: JsValue = JsonParser(FileUtil.readTextFile(new File("src/test/resources/test-data/ontologyR2RV2/knoraApiSimpleHasColor.json")))
    private val knoraApiWithValueObjectsHasColor: JsValue = JsonParser(FileUtil.readTextFile(new File("src/test/resources/test-data/ontologyR2RV2/knoraApiWithValueObjectsHasColor.json")))
    private val incunabulaSimplePubDate: JsValue = JsonParser(FileUtil.readTextFile(new File("src/test/resources/test-data/ontologyR2RV2/incunabulaSimplePubDate.json")))
    private val incunabulaWithValueObjectsPubDate: JsValue = JsonParser(FileUtil.readTextFile(new File("src/test/resources/test-data/ontologyR2RV2/incunabulaWithValueObjectsPubDate.json")))
    private val incunabulaPageAndBookWithValueObjects: JsValue = JsonParser(FileUtil.readTextFile(new File("src/test/resources/test-data/ontologyR2RV2/incunabulaPageAndBookWithValueObjects.json")))

    private val fooIri = new MutableTestIri
    private var fooLastModDate: Instant = Instant.now

    private val AnythingOntologyIri = "http://0.0.0.0:3333/ontology/0001/anything/v2".toSmartIri
    private var anythingLastModDate: Instant = Instant.parse("2017-12-19T15:23:42.166Z")

    private def getPropertyIrisFromResourceClassResponse(responseJsonDoc: JsonLDDocument): Set[SmartIri] = {
        val ontology = responseJsonDoc.body.value(OntologyConstants.KnoraApiV2WithValueObjects.HasOntologies).asInstanceOf[JsonLDObject]
        val classDef = ontology.value(OntologyConstants.KnoraApiV2WithValueObjects.HasClasses).asInstanceOf[JsonLDObject].value.values.head.asInstanceOf[JsonLDObject]

        classDef.value(OntologyConstants.Rdfs.SubClassOf).asInstanceOf[JsonLDArray].value.collect {
            case obj: JsonLDObject => obj.value(OntologyConstants.Owl.OnProperty).asInstanceOf[JsonLDString].value.toSmartIri
        }.toSet
    }

    "Load test data" in {
        Await.result(storeManager ? ResetTriplestoreContent(rdfDataObjects), 360.seconds)
        Await.result(responderManager ? LoadOntologiesRequestV2(KnoraSystemInstances.Users.SystemUser), 30.seconds)
    }

    "The Ontologies v2 Endpoint" should {
        "serve metadata for all ontologies" in {
            Get(s"/v2/ontologies/metadata") ~> ontologiesPath ~> check {
                val responseJson: JsObject = AkkaHttpUtils.httpResponseToJson(response)
                assert(responseJson == allOntologyMetadata)
            }
        }

        "serve metadata for the ontologies of one project" in {
            val projectIri = URLEncoder.encode(imagesProjectIri, "UTF-8")

            Get(s"/v2/ontologies/metadata/$projectIri") ~> ontologiesPath ~> check {
                val responseJson: JsObject = AkkaHttpUtils.httpResponseToJson(response)
                assert(responseJson == imagesOntologyMetadata)
            }
        }

        "serve the knora-api simple ontology as JSON-LD via the allentities route" in {
            val ontologyIri = URLEncoder.encode("http://api.knora.org/ontology/knora-api/simple/v2", "UTF-8")

            Get(s"/v2/ontologies/allentities/$ontologyIri") ~> ontologiesPath ~> check {
                val responseJson: JsObject = AkkaHttpUtils.httpResponseToJson(response)
                assert(responseJson == knoraApiOntologySimple)
            }
        }

        "serve the knora-api simple ontology as JSON-LD via the /ontology route" in {
            Get("/ontology/knora-api/simple/v2") ~> ontologiesPath ~> check {
                val responseJson = AkkaHttpUtils.httpResponseToJson(response)
                assert(responseJson == knoraApiOntologySimple)
            }
        }

        "serve the knora-api with value objects ontology as JSON-LD via the /ontologies/allentities route" in {
            val ontologyIri = URLEncoder.encode("http://api.knora.org/ontology/knora-api/v2", "UTF-8")

            Get(s"/v2/ontologies/allentities/$ontologyIri") ~> ontologiesPath ~> check {
                val responseJson = AkkaHttpUtils.httpResponseToJson(response)
                assert(responseJson == knoraApiWithValueObjects)
            }
        }

        "serve the knora-api with value objects ontology as JSON-LD via the /ontology route" in {
            Get("/ontology/knora-api/v2") ~> ontologiesPath ~> check {
                val responseJson = AkkaHttpUtils.httpResponseToJson(response)
                assert(responseJson == knoraApiWithValueObjects)
            }
        }

        "serve the salsah-gui ontology as JSON-LD via the /ontology route" in {
            Get("/ontology/salsah-gui/v2") ~> ontologiesPath ~> check {
                val responseJson = AkkaHttpUtils.httpResponseToJson(response)
                assert(responseJson == salsahGui)
            }
        }

        "serve the standoff ontology as JSON-LD via the /ontology route using the simple schema" in {
            Get("/ontology/standoff/simple/v2") ~> ontologiesPath ~> check {
                val responseJson = AkkaHttpUtils.httpResponseToJson(response)
                assert(responseJson == standoffSimple)
            }
        }

        "serve the standoff ontology as JSON-LD via the /ontology route using the value object schema" in {
            Get("/ontology/standoff/v2") ~> ontologiesPath ~> check {
                val responseJson = AkkaHttpUtils.httpResponseToJson(response)
                assert(responseJson == standoffWithValueObjects)
            }
        }

        "serve a project-specific ontology as JSON-LD via the /ontologies/allentities route using the simple schema" in {
            val ontologyIri = URLEncoder.encode("http://0.0.0.0:3333/ontology/incunabula/simple/v2", "UTF-8")

            Get(s"/v2/ontologies/allentities/$ontologyIri") ~> ontologiesPath ~> check {
                val responseJson = AkkaHttpUtils.httpResponseToJson(response)
                assert(responseJson == incunabulaOntologySimple)
            }
        }

        "serve a project-specific ontology as JSON-LD via the /ontology route using the simple schema" in {
            Get("/ontology/incunabula/simple/v2") ~> ontologiesPath ~> check {
                val responseJson = AkkaHttpUtils.httpResponseToJson(response)
                assert(responseJson == incunabulaOntologySimple)
            }
        }

        "serve a project-specific ontology as JSON-LD via the /ontologies/allentities route using the value object schema" in {
            val ontologyIri = URLEncoder.encode("http://0.0.0.0:3333/ontology/incunabula/v2", "UTF-8")

            Get(s"/v2/ontologies/allentities/$ontologyIri") ~> ontologiesPath ~> check {
                val responseJson = AkkaHttpUtils.httpResponseToJson(response)
                assert(responseJson == incunabulaOntologyWithValueObjects)
            }
        }

        "serve a project-specific ontology as JSON-LD via the /ontology route using the value object schema" in {
            Get("/ontology/incunabula/v2") ~> ontologiesPath ~> check {
                val responseJson = AkkaHttpUtils.httpResponseToJson(response)
                assert(responseJson == incunabulaOntologyWithValueObjects)
            }
        }

        "serve a knora-api custom datatype as JSON-LD from the simple schema" in {
            val classIri = URLEncoder.encode("http://api.knora.org/ontology/knora-api/simple/v2#Date", "UTF-8")

            Get(s"/v2/ontologies/classes/$classIri") ~> ontologiesPath ~> check {
                val responseJson = AkkaHttpUtils.httpResponseToJson(response)
                assert(responseJson == knoraApiDate)
            }
        }

        "serve a knora-api value class as JSON-LD from the value object schema" in {
            val classIri = URLEncoder.encode("http://api.knora.org/ontology/knora-api/v2#DateValue", "UTF-8")

            Get(s"/v2/ontologies/classes/$classIri") ~> ontologiesPath ~> check {
                val responseJson = AkkaHttpUtils.httpResponseToJson(response)
                assert(responseJson == knoraApiDateValue)
            }
        }

        "serve a knora-api property as JSON-LD from the simple schema" in {
            val classIri = URLEncoder.encode("http://api.knora.org/ontology/knora-api/simple/v2#hasColor", "UTF-8")

            Get(s"/v2/ontologies/properties/$classIri") ~> ontologiesPath ~> check {
                val responseJson = AkkaHttpUtils.httpResponseToJson(response)
                assert(responseJson == knoraApiSimpleHasColor)
            }
        }

        "serve a knora-api property as JSON-LD from the value object schema" in {
            val classIri = URLEncoder.encode("http://api.knora.org/ontology/knora-api/v2#hasColor", "UTF-8")

            Get(s"/v2/ontologies/properties/$classIri") ~> ontologiesPath ~> check {
                val responseJson = AkkaHttpUtils.httpResponseToJson(response)
                assert(responseJson == knoraApiWithValueObjectsHasColor)
            }
        }

        "serve a project-specific property as JSON-LD using the simple schema" in {
            val classIri = URLEncoder.encode("http://0.0.0.0:3333/ontology/incunabula/simple/v2#pubdate", "UTF-8")

            Get(s"/v2/ontologies/properties/$classIri") ~> ontologiesPath ~> check {
                val responseJson = AkkaHttpUtils.httpResponseToJson(response)
                assert(responseJson == incunabulaSimplePubDate)
            }
        }

        "serve a project-specific property as JSON-LD using the value object schema" in {
            val classIri = URLEncoder.encode("http://0.0.0.0:3333/ontology/incunabula/v2#pubdate", "UTF-8")

            Get(s"/v2/ontologies/properties/$classIri") ~> ontologiesPath ~> check {
                val responseJson = AkkaHttpUtils.httpResponseToJson(response)
                assert(responseJson == incunabulaWithValueObjectsPubDate)
            }
        }

        "serve two project-specific classes as JSON-LD using the value object schema" in {
            val pageIri = URLEncoder.encode("http://0.0.0.0:3333/ontology/incunabula/v2#page", "UTF-8")
            val bookIri = URLEncoder.encode("http://0.0.0.0:3333/ontology/incunabula/v2#book", "UTF-8")

            Get(s"/v2/ontologies/classes/$pageIri/$bookIri") ~> ontologiesPath ~> check {
                val responseJson = AkkaHttpUtils.httpResponseToJson(response)
                assert(responseJson == incunabulaPageAndBookWithValueObjects)
            }
        }

        "serve a project-specific ontology whose IRI contains a project ID, as JSON-LD, using the simple schema" in {
            Get("/ontology/00FF/images/simple/v2") ~> ontologiesPath ~> check {
                val responseJson = AkkaHttpUtils.httpResponseToJson(response)
                assert(responseJson == imagesOntologySimple)
            }
        }

        "serve a project-specific ontology whose IRI contains a project ID, as JSON-LD, using the value object schema" in {
            Get("/ontology/00FF/images/v2") ~> ontologiesPath ~> check {
                val responseJson = AkkaHttpUtils.httpResponseToJson(response)
                assert(responseJson == imagesOntologyWithValueObjects)
            }
        }

        "serve a class from project-specific ontology whose IRI contains a project ID, as JSON-LD, using the simple schema" in {
            val bildIri = URLEncoder.encode("http://0.0.0.0:3333/ontology/00FF/images/simple/v2#bild", "UTF-8")

            Get(s"/v2/ontologies/classes/$bildIri") ~> ontologiesPath ~> check {
                val responseJson = AkkaHttpUtils.httpResponseToJson(response)
                assert(responseJson == bildSimple)
            }
        }

        "serve a class from project-specific ontology whose IRI contains a project ID, as JSON-LD, using the value object schema" in {
            val bildIri = URLEncoder.encode("http://0.0.0.0:3333/ontology/00FF/images/v2#bild", "UTF-8")

            Get(s"/v2/ontologies/classes/$bildIri") ~> ontologiesPath ~> check {
                val responseJson = AkkaHttpUtils.httpResponseToJson(response)
                assert(responseJson == bildWithValueObjects)
            }
        }

        "create an empty ontology called 'foo' with a project code" in {
            val label = "The foo ontology"

            val params =
                s"""
                   |{
                   |    "knora-api:ontologyName": "foo",
                   |    "knora-api:projectIri": "$imagesProjectIri",
                   |    "rdfs:label": "$label",
                   |    "@context": {
                   |        "rdfs": "${OntologyConstants.Rdfs.RdfsPrefixExpansion}",
                   |        "knora-api": "${OntologyConstants.KnoraApiV2WithValueObjects.KnoraApiV2PrefixExpansion}"
                   |    }
                   |}
                """.stripMargin

            Post("/v2/ontologies", HttpEntity(ContentTypes.`application/json`, params)) ~> addCredentials(BasicHttpCredentials(imagesUsername, password)) ~> ontologiesPath ~> check {
                assert(status == StatusCodes.OK, response.toString)
                val responseJsonDoc = responseToJsonLDDocument(response)

                responseJsonDoc.body.value(OntologyConstants.KnoraApiV2WithValueObjects.HasOntologies) match {
                    case metadata: JsonLDObject =>
                        val ontologyIri = metadata.value("@id").asInstanceOf[JsonLDString].value
                        assert(ontologyIri == "http://0.0.0.0:3333/ontology/00FF/foo/v2")
                        fooIri.set(ontologyIri)

                        assert(metadata.value(OntologyConstants.Rdfs.Label) == JsonLDString(label))

                        val lastModDate = Instant.parse(metadata.value(OntologyConstants.KnoraApiV2WithValueObjects.LastModificationDate).asInstanceOf[JsonLDString].value)
                        fooLastModDate = lastModDate

                    case _ => throw AssertionException(s"Unexpected response: $responseJsonDoc")
                }
            }
        }

        "change the metadata of 'foo'" in {
            val newLabel = "The modified foo ontology"

            val params =
                s"""
                   |{
                   |  "knora-api:hasOntologies": {
                   |    "@id": "${fooIri.get}",
                   |    "rdfs:label": "$newLabel",
                   |    "knora-api:lastModificationDate": "$fooLastModDate"
                   |  },
                   |  "@context": {
                   |    "rdfs": "${OntologyConstants.Rdfs.RdfsPrefixExpansion}",
                   |    "knora-api": "${OntologyConstants.KnoraApiV2WithValueObjects.KnoraApiV2PrefixExpansion}"
                   |  }
                   |}
                """.stripMargin

            Put("/v2/ontologies/metadata", HttpEntity(ContentTypes.`application/json`, params)) ~> addCredentials(BasicHttpCredentials(imagesUsername, password)) ~> ontologiesPath ~> check {
                assert(status == StatusCodes.OK, response.toString)
                val responseJsonDoc = responseToJsonLDDocument(response)

                responseJsonDoc.body.value(OntologyConstants.KnoraApiV2WithValueObjects.HasOntologies) match {
                    case metadata: JsonLDObject =>
                        val ontologyIri = metadata.value("@id").asInstanceOf[JsonLDString].value
                        assert(ontologyIri == fooIri.get)

                        assert(metadata.value(OntologyConstants.Rdfs.Label) == JsonLDString(newLabel))

                        val lastModDate = Instant.parse(metadata.value(OntologyConstants.KnoraApiV2WithValueObjects.LastModificationDate).asInstanceOf[JsonLDString].value)
                        assert(lastModDate.isAfter(fooLastModDate))
                        fooLastModDate = lastModDate

                    case _ => throw AssertionException(s"Unexpected response: $responseJsonDoc")
                }
            }
        }

        "delete the 'foo' ontology" in {
            val fooIriEncoded = URLEncoder.encode(fooIri.get, "UTF-8")
            val lastModificationDate = URLEncoder.encode(fooLastModDate.toString, "UTF-8")

            Delete(s"/v2/ontologies/$fooIriEncoded?lastModificationDate=$lastModificationDate") ~> addCredentials(BasicHttpCredentials(imagesUsername, password)) ~> ontologiesPath ~> check {
                assert(status == StatusCodes.OK, response.toString)
            }
        }

        "create a property anything:hasName as a subproperty of knora-api:hasValue and schema:name" in {
            val params =
                """
                  |{
                  |  "knora-api:hasOntologies" : {
                  |    "@id" : "http://0.0.0.0:3333/ontology/0001/anything/v2",
                  |    "@type" : "owl:Ontology",
                  |    "knora-api:hasProperties" : {
                  |      "anything:hasName" : {
                  |        "@id" : "anything:hasName",
                  |        "@type" : "owl:ObjectProperty",
                  |        "knora-api:subjectType" : "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing",
                  |        "knora-api:objectType" : "http://api.knora.org/ontology/knora-api/v2#TextValue",
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
                  |        "rdfs:subPropertyOf" : [ "http://api.knora.org/ontology/knora-api/v2#hasValue", "http://schema.org/name" ],
                  |        "salsah-gui:guiElement" : "http://api.knora.org/ontology/salsah-gui/v2#SimpleText",
                  |        "salsah-gui:guiAttribute" : [ "size=80", "maxlength=100" ]
                  |      }
                  |    },
                  |    "knora-api:lastModificationDate" : "2017-12-19T15:23:42.166Z"
                  |  },
                  |  "@context" : {
                  |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
                  |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
                  |    "salsah-gui" : "http://api.knora.org/ontology/salsah-gui/v2#",
                  |    "owl" : "http://www.w3.org/2002/07/owl#",
                  |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
                  |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
                  |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
                  |  }
                  |}
                """.stripMargin

            // Convert the submitted JSON-LD to an InputOntologiesV2, without SPARQL-escaping, so we can compare it to the response.
            val paramsAsInput: InputOntologiesV2 = InputOntologiesV2.fromJsonLD(JsonLDUtil.parseJsonLD(params)).unescape

            Post("/v2/ontologies/properties", HttpEntity(ContentTypes.`application/json`, params)) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> ontologiesPath ~> check {
                assert(status == StatusCodes.OK, response.toString)
                val responseJsonDoc = responseToJsonLDDocument(response)

                // Convert the response to an InputOntologiesV2 and compare the relevant part of it to the request.
                val responseAsInput: InputOntologiesV2 = InputOntologiesV2.fromJsonLD(responseJsonDoc, ignoreExtraData = true).unescape
                assert(responseAsInput.ontologies.size == 1)
                responseAsInput.ontologies.head.properties should ===(paramsAsInput.ontologies.head.properties)

                // Check that the ontology's last modification date was updated.
                val newAnythingLastModDate = responseAsInput.ontologies.head.ontologyMetadata.lastModificationDate.get
                assert(newAnythingLastModDate.isAfter(anythingLastModDate))
                anythingLastModDate = newAnythingLastModDate
            }
        }

        "change the labels of a property" in {
            val params =
                s"""
                   |{
                   |  "knora-api:hasOntologies" : {
                   |    "@id" : "$AnythingOntologyIri",
                   |    "@type" : "owl:Ontology",
                   |    "knora-api:hasProperties" : {
                   |      "anything:hasName" : {
                   |        "@id" : "anything:hasName",
                   |        "@type" : "owl:ObjectProperty",
                   |        "rdfs:label" : [ {
                   |          "@language" : "en",
                   |          "@value" : "has name"
                   |        }, {
                   |          "@language" : "fr",
                   |          "@value" : "a nom"
                   |        }, {
                   |          "@language" : "de",
                   |          "@value" : "hat Namen"
                   |        } ]
                   |      }
                   |    },
                   |    "knora-api:lastModificationDate" : "$anythingLastModDate"
                   |  },
                   |  "@context" : {
                   |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
                   |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
                   |    "owl" : "http://www.w3.org/2002/07/owl#",
                   |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
                   |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
                   |    "anything" : "$AnythingOntologyIri#"
                   |  }
                   |}
                """.stripMargin

            // Convert the submitted JSON-LD to an InputOntologiesV2, without SPARQL-escaping, so we can compare it to the response.
            val paramsAsInput: InputOntologiesV2 = InputOntologiesV2.fromJsonLD(JsonLDUtil.parseJsonLD(params)).unescape

            Put("/v2/ontologies/properties", HttpEntity(ContentTypes.`application/json`, params)) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> ontologiesPath ~> check {
                assert(status == StatusCodes.OK, response.toString)
                val responseJsonDoc = responseToJsonLDDocument(response)

                // Comvert the response to an InputOntologiesV2 and compare the relevant part of it to the request.
                val responseAsInput: InputOntologiesV2 = InputOntologiesV2.fromJsonLD(responseJsonDoc, ignoreExtraData = true).unescape
                assert(responseAsInput.ontologies.size == 1)
                responseAsInput.ontologies.head.properties.head._2.predicates(OntologyConstants.Rdfs.Label.toSmartIri).objects should ===(paramsAsInput.ontologies.head.properties.head._2.predicates.head._2.objects)

                // Check that the ontology's last modification date was updated.
                val newAnythingLastModDate = responseAsInput.ontologies.head.ontologyMetadata.lastModificationDate.get
                assert(newAnythingLastModDate.isAfter(anythingLastModDate))
                anythingLastModDate = newAnythingLastModDate
            }
        }

        "change the comments of a property" in {
            val params =
                s"""
                   |{
                   |  "knora-api:hasOntologies" : {
                   |    "@id" : "$AnythingOntologyIri",
                   |    "@type" : "owl:Ontology",
                   |    "knora-api:hasProperties" : {
                   |      "anything:hasName" : {
                   |        "@id" : "anything:hasName",
                   |        "@type" : "owl:ObjectProperty",
                   |        "rdfs:comment" : [ {
                   |          "@language" : "en",
                   |          "@value" : "The name of a Thing"
                   |        }, {
                   |          "@language" : "fr",
                   |          "@value" : "Le nom d'une chose"
                   |        }, {
                   |          "@language" : "de",
                   |          "@value" : "Der Name eines Dinges"
                   |        } ]
                   |      }
                   |    },
                   |    "knora-api:lastModificationDate" : "$anythingLastModDate"
                   |  },
                   |  "@context" : {
                   |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
                   |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
                   |    "owl" : "http://www.w3.org/2002/07/owl#",
                   |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
                   |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
                   |    "anything" : "$AnythingOntologyIri#"
                   |  }
                   |}
                """.stripMargin

            // Convert the submitted JSON-LD to an InputOntologiesV2, without SPARQL-escaping, so we can compare it to the response.
            val paramsAsInput: InputOntologiesV2 = InputOntologiesV2.fromJsonLD(JsonLDUtil.parseJsonLD(params)).unescape

            Put("/v2/ontologies/properties", HttpEntity(ContentTypes.`application/json`, params)) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> ontologiesPath ~> check {
                assert(status == StatusCodes.OK, response.toString)
                val responseJsonDoc = responseToJsonLDDocument(response)

                // Comvert the response to an InputOntologiesV2 and compare the relevant part of it to the request.
                val responseAsInput: InputOntologiesV2 = InputOntologiesV2.fromJsonLD(responseJsonDoc, ignoreExtraData = true).unescape
                assert(responseAsInput.ontologies.size == 1)
                responseAsInput.ontologies.head.properties.head._2.predicates(OntologyConstants.Rdfs.Comment.toSmartIri).objects should ===(paramsAsInput.ontologies.head.properties.head._2.predicates.head._2.objects)

                // Check that the ontology's last modification date was updated.
                val newAnythingLastModDate = responseAsInput.ontologies.head.ontologyMetadata.lastModificationDate.get
                assert(newAnythingLastModDate.isAfter(anythingLastModDate))
                anythingLastModDate = newAnythingLastModDate
            }
        }

        "create a class anything:WildThing that is a subclass of anything:Thing, with a direct cardinality for anything:hasName" in {
            val params =
                s"""
                   |{
                   |  "knora-api:hasOntologies" : {
                   |    "@id" : "http://0.0.0.0:3333/ontology/0001/anything/v2",
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
                   |            "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing",
                   |            {
                   |                "@type": "http://www.w3.org/2002/07/owl#Restriction",
                   |                "owl:maxCardinality": 1,
                   |                "owl:onProperty": "http://0.0.0.0:3333/ontology/0001/anything/v2#hasName",
                   |                "salsah-gui:guiOrder": 1
                   |            }
                   |        ]
                   |      }
                   |    },
                   |    "knora-api:lastModificationDate" : "$anythingLastModDate"
                   |  },
                   |  "@context" : {
                   |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
                   |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
                   |    "salsah-gui" : "http://api.knora.org/ontology/salsah-gui/v2#",
                   |    "owl" : "http://www.w3.org/2002/07/owl#",
                   |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
                   |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
                   |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
                   |  }
                   |}
            """.stripMargin

            val expectedProperties: Set[SmartIri] = Set(
                "http://0.0.0.0:3333/ontology/0001/anything/v2#hasBlueThing".toSmartIri,
                "http://0.0.0.0:3333/ontology/0001/anything/v2#hasName".toSmartIri,
                "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInterval".toSmartIri,
                "http://0.0.0.0:3333/ontology/0001/anything/v2#hasOtherThingValue".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#attachedToUser".toSmartIri,
                "http://0.0.0.0:3333/ontology/0001/anything/v2#hasThingPicture".toSmartIri,
                "http://0.0.0.0:3333/ontology/0001/anything/v2#hasOtherListItem".toSmartIri,
                "http://0.0.0.0:3333/ontology/0001/anything/v2#hasListItem".toSmartIri,
                "http://0.0.0.0:3333/ontology/0001/anything/v2#hasOtherThing".toSmartIri,
                "http://0.0.0.0:3333/ontology/0001/anything/v2#hasThingPictureValue".toSmartIri,
                "http://0.0.0.0:3333/ontology/0001/anything/v2#isPartOfOtherThingValue".toSmartIri,
                "http://0.0.0.0:3333/ontology/0001/anything/v2#hasDate".toSmartIri,
                "http://www.w3.org/2000/01/rdf-schema#label".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#lastModificationDate".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#creationDate".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#hasStandoffLinkTo".toSmartIri,
                "http://0.0.0.0:3333/ontology/0001/anything/v2#isPartOfOtherThing".toSmartIri,
                "http://0.0.0.0:3333/ontology/0001/anything/v2#hasText".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#hasStandoffLinkToValue".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#hasPermissions".toSmartIri,
                "http://0.0.0.0:3333/ontology/0001/anything/v2#hasDecimal".toSmartIri,
                "http://0.0.0.0:3333/ontology/0001/anything/v2#hasBoolean".toSmartIri,
                "http://0.0.0.0:3333/ontology/0001/anything/v2#hasColor".toSmartIri,
                "http://0.0.0.0:3333/ontology/0001/anything/v2#hasBlueThingValue".toSmartIri,
                "http://0.0.0.0:3333/ontology/0001/anything/v2#hasRichtext".toSmartIri,
                "http://0.0.0.0:3333/ontology/0001/anything/v2#hasUri".toSmartIri,
                "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#attachedToProject".toSmartIri
            )

            // Convert the submitted JSON-LD to an InputOntologiesV2, without SPARQL-escaping, so we can compare it to the response.
            val paramsAsInput: InputOntologiesV2 = InputOntologiesV2.fromJsonLD(JsonLDUtil.parseJsonLD(params)).unescape

            Post("/v2/ontologies/classes", HttpEntity(ContentTypes.`application/json`, params)) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> ontologiesPath ~> check {
                assert(status == StatusCodes.OK, response.toString)
                val responseJsonDoc = responseToJsonLDDocument(response)

                // Convert the response to an InputOntologiesV2 and compare the relevant part of it to the request.
                val responseAsInput: InputOntologiesV2 = InputOntologiesV2.fromJsonLD(responseJsonDoc, ignoreExtraData = true).unescape
                assert(responseAsInput.ontologies.size == 1)
                responseAsInput.ontologies.head.classes should ===(paramsAsInput.ontologies.head.classes)

                // Check that cardinalities were inherited from anything:Thing.
                getPropertyIrisFromResourceClassResponse(responseJsonDoc) should ===(expectedProperties)

                // Check that the ontology's last modification date was updated.
                val newAnythingLastModDate = responseAsInput.ontologies.head.ontologyMetadata.lastModificationDate.get
                assert(newAnythingLastModDate.isAfter(anythingLastModDate))
                anythingLastModDate = newAnythingLastModDate
            }
        }

        "create a class anything:Nothing with no properties" in {
            val params =
                s"""
                   |{
                   |  "knora-api:hasOntologies" : {
                   |    "@id" : "http://0.0.0.0:3333/ontology/0001/anything/v2",
                   |    "@type" : "owl:Ontology",
                   |    "knora-api:hasClasses" : {
                   |      "anything:Nothing" : {
                   |        "@id" : "anything:Nothing",
                   |        "@type" : "owl:Class",
                   |        "rdfs:label" : {
                   |          "@language" : "en",
                   |          "@value" : "nothing"
                   |        },
                   |        "rdfs:comment" : {
                   |          "@language" : "en",
                   |          "@value" : "Represents nothing"
                   |        },
                   |        "rdfs:subClassOf" : "http://api.knora.org/ontology/knora-api/v2#Resource"
                   |      }
                   |    },
                   |    "knora-api:lastModificationDate" : "$anythingLastModDate"
                   |  },
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

            val expectedProperties: Set[SmartIri] = Set(
                "http://api.knora.org/ontology/knora-api/v2#attachedToUser".toSmartIri,
                "http://www.w3.org/2000/01/rdf-schema#label".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#lastModificationDate".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#creationDate".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#hasStandoffLinkTo".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#hasStandoffLinkToValue".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#hasPermissions".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#attachedToProject".toSmartIri
            )

            // Convert the submitted JSON-LD to an InputOntologiesV2, without SPARQL-escaping, so we can compare it to the response.
            val paramsAsInput: InputOntologiesV2 = InputOntologiesV2.fromJsonLD(JsonLDUtil.parseJsonLD(params)).unescape

            Post("/v2/ontologies/classes", HttpEntity(ContentTypes.`application/json`, params)) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> ontologiesPath ~> check {
                assert(status == StatusCodes.OK, response.toString)
                val responseJsonDoc = responseToJsonLDDocument(response)

                // Convert the response to an InputOntologiesV2 and compare the relevant part of it to the request.
                val responseAsInput: InputOntologiesV2 = InputOntologiesV2.fromJsonLD(responseJsonDoc, ignoreExtraData = true).unescape
                assert(responseAsInput.ontologies.size == 1)
                responseAsInput.ontologies.head.classes should ===(paramsAsInput.ontologies.head.classes)

                // Check that cardinalities were inherited from knora-api:Resource.
                getPropertyIrisFromResourceClassResponse(responseJsonDoc) should ===(expectedProperties)

                // Check that the ontology's last modification date was updated.
                val newAnythingLastModDate = responseAsInput.ontologies.head.ontologyMetadata.lastModificationDate.get
                assert(newAnythingLastModDate.isAfter(anythingLastModDate))
                anythingLastModDate = newAnythingLastModDate
            }
        }

        "change the labels of a class" in {
            val params =
                s"""
                   |{
                   |  "knora-api:hasOntologies" : {
                   |    "@id" : "$AnythingOntologyIri",
                   |    "@type" : "owl:Ontology",
                   |    "knora-api:hasClasses" : {
                   |      "anything:Nothing" : {
                   |        "@id" : "anything:Nothing",
                   |        "@type" : "owl:Class",
                   |        "rdfs:label" : [ {
                   |          "@language" : "en",
                   |          "@value" : "nothing"
                   |        }, {
                   |          "@language" : "fr",
                   |          "@value" : "rien"
                   |        } ]
                   |      }
                   |    },
                   |    "knora-api:lastModificationDate" : "$anythingLastModDate"
                   |  },
                   |  "@context" : {
                   |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
                   |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
                   |    "owl" : "http://www.w3.org/2002/07/owl#",
                   |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
                   |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
                   |    "anything" : "$AnythingOntologyIri#"
                   |  }
                   |}
                """.stripMargin

            // Convert the submitted JSON-LD to an InputOntologiesV2, without SPARQL-escaping, so we can compare it to the response.
            val paramsAsInput: InputOntologiesV2 = InputOntologiesV2.fromJsonLD(JsonLDUtil.parseJsonLD(params)).unescape

            Put("/v2/ontologies/classes", HttpEntity(ContentTypes.`application/json`, params)) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> ontologiesPath ~> check {
                assert(status == StatusCodes.OK, response.toString)
                val responseJsonDoc = responseToJsonLDDocument(response)

                // Comvert the response to an InputOntologiesV2 and compare the relevant part of it to the request.
                val responseAsInput: InputOntologiesV2 = InputOntologiesV2.fromJsonLD(responseJsonDoc, ignoreExtraData = true).unescape
                assert(responseAsInput.ontologies.size == 1)
                responseAsInput.ontologies.head.classes.head._2.predicates(OntologyConstants.Rdfs.Label.toSmartIri).objects should ===(paramsAsInput.ontologies.head.classes.head._2.predicates.head._2.objects)

                // Check that the ontology's last modification date was updated.
                val newAnythingLastModDate = responseAsInput.ontologies.head.ontologyMetadata.lastModificationDate.get
                assert(newAnythingLastModDate.isAfter(anythingLastModDate))
                anythingLastModDate = newAnythingLastModDate
            }
        }

        "change the comments of a class" in {
            val params =
                s"""
                   |{
                   |  "knora-api:hasOntologies" : {
                   |    "@id" : "$AnythingOntologyIri",
                   |    "@type" : "owl:Ontology",
                   |    "knora-api:hasClasses" : {
                   |      "anything:Nothing" : {
                   |        "@id" : "anything:Nothing",
                   |        "@type" : "owl:Class",
                   |        "rdfs:comment" : [ {
                   |          "@language" : "en",
                   |          "@value" : "Represents nothing"
                   |        }, {
                   |          "@language" : "fr",
                   |          "@value" : "ne représente rien"
                   |        } ]
                   |      }
                   |    },
                   |    "knora-api:lastModificationDate" : "$anythingLastModDate"
                   |  },
                   |  "@context" : {
                   |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
                   |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
                   |    "owl" : "http://www.w3.org/2002/07/owl#",
                   |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
                   |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
                   |    "anything" : "$AnythingOntologyIri#"
                   |  }
                   |}
                """.stripMargin

            // Convert the submitted JSON-LD to an InputOntologiesV2, without SPARQL-escaping, so we can compare it to the response.
            val paramsAsInput: InputOntologiesV2 = InputOntologiesV2.fromJsonLD(JsonLDUtil.parseJsonLD(params)).unescape

            Put("/v2/ontologies/classes", HttpEntity(ContentTypes.`application/json`, params)) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> ontologiesPath ~> check {
                assert(status == StatusCodes.OK, response.toString)
                val responseJsonDoc = responseToJsonLDDocument(response)

                // Comvert the response to an InputOntologiesV2 and compare the relevant part of it to the request.
                val responseAsInput: InputOntologiesV2 = InputOntologiesV2.fromJsonLD(responseJsonDoc, ignoreExtraData = true).unescape
                assert(responseAsInput.ontologies.size == 1)
                responseAsInput.ontologies.head.classes.head._2.predicates(OntologyConstants.Rdfs.Comment.toSmartIri).objects should ===(paramsAsInput.ontologies.head.classes.head._2.predicates.head._2.objects)

                // Check that the ontology's last modification date was updated.
                val newAnythingLastModDate = responseAsInput.ontologies.head.ontologyMetadata.lastModificationDate.get
                assert(newAnythingLastModDate.isAfter(anythingLastModDate))
                anythingLastModDate = newAnythingLastModDate
            }
        }

        "create a property anything:hasNothingness with knora-api:subjectType anything:Nothing" in {
            val params =
                s"""
                   |{
                   |  "knora-api:hasOntologies" : {
                   |    "@id" : "http://0.0.0.0:3333/ontology/0001/anything/v2",
                   |    "@type" : "owl:Ontology",
                   |    "knora-api:hasProperties" : {
                   |      "anything:hasNothingness" : {
                   |        "@id" : "anything:hasNothingness",
                   |        "@type" : "owl:ObjectProperty",
                   |        "knora-api:subjectType" : "http://0.0.0.0:3333/ontology/0001/anything/v2#Nothing",
                   |        "knora-api:objectType" : "http://api.knora.org/ontology/knora-api/v2#BooleanValue",
                   |        "rdfs:comment" : {
                   |          "@language" : "en",
                   |          "@value" : "Indicates whether a Nothing has nothingness"
                   |        },
                   |        "rdfs:label" : {
                   |          "@language" : "en",
                   |          "@value" : "has nothingness"
                   |        },
                   |        "rdfs:subPropertyOf" : "http://api.knora.org/ontology/knora-api/v2#hasValue"
                   |      }
                   |    },
                   |    "knora-api:lastModificationDate" : "$anythingLastModDate"
                   |  },
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

            // Convert the submitted JSON-LD to an InputOntologiesV2, without SPARQL-escaping, so we can compare it to the response.
            val paramsAsInput: InputOntologiesV2 = InputOntologiesV2.fromJsonLD(JsonLDUtil.parseJsonLD(params)).unescape

            Post("/v2/ontologies/properties", HttpEntity(ContentTypes.`application/json`, params)) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> ontologiesPath ~> check {
                assert(status == StatusCodes.OK, response.toString)
                val responseJsonDoc = responseToJsonLDDocument(response)

                // Comvert the response to an InputOntologiesV2 and compare the relevant part of it to the request.
                val responseAsInput: InputOntologiesV2 = InputOntologiesV2.fromJsonLD(responseJsonDoc, ignoreExtraData = true).unescape
                assert(responseAsInput.ontologies.size == 1)
                responseAsInput.ontologies.head.properties should ===(paramsAsInput.ontologies.head.properties)

                // Check that the ontology's last modification date was updated.
                val newAnythingLastModDate = responseAsInput.ontologies.head.ontologyMetadata.lastModificationDate.get
                assert(newAnythingLastModDate.isAfter(anythingLastModDate))
                anythingLastModDate = newAnythingLastModDate
            }
        }

        "add a cardinality for the property anything:hasNothingness to the class anything:Nothing" in {
            val params =
                s"""
                   |{
                   |  "knora-api:hasOntologies" : {
                   |    "@id" : "http://0.0.0.0:3333/ontology/0001/anything/v2",
                   |    "@type" : "owl:Ontology",
                   |    "knora-api:hasClasses" : {
                   |      "anything:Nothing" : {
                   |        "@id" : "anything:Nothing",
                   |        "@type" : "owl:Class",
                   |        "rdfs:subClassOf" : [
                   |            {
                   |                "@type": "http://www.w3.org/2002/07/owl#Restriction",
                   |                "owl:maxCardinality": 1,
                   |                "owl:onProperty": "http://0.0.0.0:3333/ontology/0001/anything/v2#hasNothingness"
                   |            }
                   |        ]
                   |      }
                   |    },
                   |    "knora-api:lastModificationDate" : "$anythingLastModDate"
                   |  },
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

            val expectedProperties: Set[SmartIri] = Set(
                "http://api.knora.org/ontology/knora-api/v2#attachedToUser".toSmartIri,
                "http://0.0.0.0:3333/ontology/0001/anything/v2#hasNothingness".toSmartIri,
                "http://www.w3.org/2000/01/rdf-schema#label".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#lastModificationDate".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#creationDate".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#hasStandoffLinkTo".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#hasStandoffLinkToValue".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#hasPermissions".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#attachedToProject".toSmartIri
            )

            // Convert the submitted JSON-LD to an InputOntologiesV2, without SPARQL-escaping, so we can compare it to the response.
            val paramsAsInput: InputOntologiesV2 = InputOntologiesV2.fromJsonLD(JsonLDUtil.parseJsonLD(params)).unescape

            Post("/v2/ontologies/cardinalities", HttpEntity(ContentTypes.`application/json`, params)) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> ontologiesPath ~> check {
                assert(status == StatusCodes.OK, response.toString)
                val responseJsonDoc = responseToJsonLDDocument(response)

                // Convert the response to an InputOntologiesV2 and compare the relevant part of it to the request.
                val responseAsInput: InputOntologiesV2 = InputOntologiesV2.fromJsonLD(responseJsonDoc, ignoreExtraData = true).unescape
                assert(responseAsInput.ontologies.size == 1)
                responseAsInput.ontologies.head.classes.head._2.directCardinalities should ===(paramsAsInput.ontologies.head.classes.head._2.directCardinalities)

                // Check that cardinalities were inherited from knora-api:Resource.
                getPropertyIrisFromResourceClassResponse(responseJsonDoc) should ===(expectedProperties)

                // Check that the ontology's last modification date was updated.
                val newAnythingLastModDate = responseAsInput.ontologies.head.ontologyMetadata.lastModificationDate.get
                assert(newAnythingLastModDate.isAfter(anythingLastModDate))
                anythingLastModDate = newAnythingLastModDate
            }
        }

        "create a property anything:hasEmptiness with knora-api:subjectType anything:Nothing" in {
            val params =
                s"""
                   |{
                   |  "knora-api:hasOntologies" : {
                   |    "@id" : "http://0.0.0.0:3333/ontology/0001/anything/v2",
                   |    "@type" : "owl:Ontology",
                   |    "knora-api:hasProperties" : {
                   |      "anything:hasEmptiness" : {
                   |        "@id" : "anything:hasEmptiness",
                   |        "@type" : "owl:ObjectProperty",
                   |        "knora-api:subjectType" : "http://0.0.0.0:3333/ontology/0001/anything/v2#Nothing",
                   |        "knora-api:objectType" : "http://api.knora.org/ontology/knora-api/v2#BooleanValue",
                   |        "rdfs:comment" : {
                   |          "@language" : "en",
                   |          "@value" : "Indicates whether a Nothing has emptiness"
                   |        },
                   |        "rdfs:label" : {
                   |          "@language" : "en",
                   |          "@value" : "has emptiness"
                   |        },
                   |        "rdfs:subPropertyOf" : "http://api.knora.org/ontology/knora-api/v2#hasValue"
                   |      }
                   |    },
                   |    "knora-api:lastModificationDate" : "$anythingLastModDate"
                   |  },
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

            // Convert the submitted JSON-LD to an InputOntologiesV2, without SPARQL-escaping, so we can compare it to the response.
            val paramsAsInput: InputOntologiesV2 = InputOntologiesV2.fromJsonLD(JsonLDUtil.parseJsonLD(params)).unescape

            Post("/v2/ontologies/properties", HttpEntity(ContentTypes.`application/json`, params)) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> ontologiesPath ~> check {
                assert(status == StatusCodes.OK, response.toString)
                val responseJsonDoc = responseToJsonLDDocument(response)

                // Comvert the response to an InputOntologiesV2 and compare the relevant part of it to the request.
                val responseAsInput: InputOntologiesV2 = InputOntologiesV2.fromJsonLD(responseJsonDoc, ignoreExtraData = true).unescape
                assert(responseAsInput.ontologies.size == 1)
                responseAsInput.ontologies.head.properties should ===(paramsAsInput.ontologies.head.properties)

                // Check that the ontology's last modification date was updated.
                val newAnythingLastModDate = responseAsInput.ontologies.head.ontologyMetadata.lastModificationDate.get
                assert(newAnythingLastModDate.isAfter(anythingLastModDate))
                anythingLastModDate = newAnythingLastModDate
            }
        }

        "change the cardinalities of the class anything:Nothing, replacing anything:hasNothingness with anything:hasEmptiness" in {
            val params =
                s"""
                   |{
                   |  "knora-api:hasOntologies" : {
                   |    "@id" : "http://0.0.0.0:3333/ontology/0001/anything/v2",
                   |    "@type" : "owl:Ontology",
                   |    "knora-api:hasClasses" : {
                   |      "anything:Nothing" : {
                   |        "@id" : "anything:Nothing",
                   |        "@type" : "owl:Class",
                   |        "rdfs:subClassOf" : [
                   |            {
                   |                "@type": "http://www.w3.org/2002/07/owl#Restriction",
                   |                "owl:maxCardinality": 1,
                   |                "owl:onProperty": "http://0.0.0.0:3333/ontology/0001/anything/v2#hasEmptiness"
                   |            }
                   |        ]
                   |      }
                   |    },
                   |    "knora-api:lastModificationDate" : "$anythingLastModDate"
                   |  },
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

            val expectedProperties: Set[SmartIri] = Set(
                "http://api.knora.org/ontology/knora-api/v2#attachedToUser".toSmartIri,
                "http://www.w3.org/2000/01/rdf-schema#label".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#lastModificationDate".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#creationDate".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#hasStandoffLinkTo".toSmartIri,
                "http://0.0.0.0:3333/ontology/0001/anything/v2#hasEmptiness".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#hasStandoffLinkToValue".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#hasPermissions".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#attachedToProject".toSmartIri
            )

            // Convert the submitted JSON-LD to an InputOntologiesV2, without SPARQL-escaping, so we can compare it to the response.
            val paramsAsInput: InputOntologiesV2 = InputOntologiesV2.fromJsonLD(JsonLDUtil.parseJsonLD(params)).unescape

            Put("/v2/ontologies/cardinalities", HttpEntity(ContentTypes.`application/json`, params)) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> ontologiesPath ~> check {
                assert(status == StatusCodes.OK, response.toString)
                val responseJsonDoc = responseToJsonLDDocument(response)

                // Convert the response to an InputOntologiesV2 and compare the relevant part of it to the request.
                val responseAsInput: InputOntologiesV2 = InputOntologiesV2.fromJsonLD(responseJsonDoc, ignoreExtraData = true).unescape
                assert(responseAsInput.ontologies.size == 1)
                responseAsInput.ontologies.head.classes.head._2.directCardinalities should ===(paramsAsInput.ontologies.head.classes.head._2.directCardinalities)

                // Check that cardinalities were inherited from knora-api:Resource.
                getPropertyIrisFromResourceClassResponse(responseJsonDoc) should ===(expectedProperties)

                // Check that the ontology's last modification date was updated.
                val newAnythingLastModDate = responseAsInput.ontologies.head.ontologyMetadata.lastModificationDate.get
                assert(newAnythingLastModDate.isAfter(anythingLastModDate))
                anythingLastModDate = newAnythingLastModDate
            }
        }

        "delete the property anything:hasNothingness" in {
            val propertyIri = URLEncoder.encode("http://0.0.0.0:3333/ontology/0001/anything/v2#hasNothingness", "UTF-8")
            val lastModificationDate = URLEncoder.encode(anythingLastModDate.toString, "UTF-8")

            Delete(s"/v2/ontologies/properties/$propertyIri?lastModificationDate=$lastModificationDate") ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> ontologiesPath ~> check {
                assert(status == StatusCodes.OK, response.toString)
                val responseJsonDoc = responseToJsonLDDocument(response)
                val ontology = responseJsonDoc.requireObject(OntologyConstants.KnoraApiV2WithValueObjects.HasOntologies)
                ontology.value("@id").asInstanceOf[JsonLDString].value should ===("http://0.0.0.0:3333/ontology/0001/anything/v2")
                val newAnythingLastModDate = Instant.parse(ontology.value(OntologyConstants.KnoraApiV2WithValueObjects.LastModificationDate).asInstanceOf[JsonLDString].value)
                assert(newAnythingLastModDate.isAfter(anythingLastModDate))
                anythingLastModDate = newAnythingLastModDate
            }
        }

        "remove all cardinalities from the class anything:Nothing" in {
            val params =
                s"""
                   |{
                   |  "knora-api:hasOntologies" : {
                   |    "@id" : "http://0.0.0.0:3333/ontology/0001/anything/v2",
                   |    "@type" : "owl:Ontology",
                   |    "knora-api:hasClasses" : {
                   |      "anything:Nothing" : {
                   |        "@id" : "anything:Nothing",
                   |        "@type" : "owl:Class"
                   |      }
                   |    },
                   |    "knora-api:lastModificationDate" : "$anythingLastModDate"
                   |  },
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

            val expectedProperties: Set[SmartIri] = Set(
                "http://api.knora.org/ontology/knora-api/v2#attachedToUser".toSmartIri,
                "http://www.w3.org/2000/01/rdf-schema#label".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#lastModificationDate".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#creationDate".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#hasStandoffLinkTo".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#hasStandoffLinkToValue".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#hasPermissions".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#attachedToProject".toSmartIri
            )

            Put("/v2/ontologies/cardinalities", HttpEntity(ContentTypes.`application/json`, params)) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> ontologiesPath ~> check {
                assert(status == StatusCodes.OK, response.toString)
                val responseJsonDoc = responseToJsonLDDocument(response)

                // Convert the response to an InputOntologiesV2 and compare the relevant part of it to the request.
                val responseAsInput: InputOntologiesV2 = InputOntologiesV2.fromJsonLD(responseJsonDoc, ignoreExtraData = true).unescape
                assert(responseAsInput.ontologies.size == 1)
                responseAsInput.ontologies.head.classes.head._2.directCardinalities.isEmpty should ===(true)

                // Check that cardinalities were inherited from knora-api:Resource.
                getPropertyIrisFromResourceClassResponse(responseJsonDoc) should ===(expectedProperties)

                // Check that the ontology's last modification date was updated.
                val newAnythingLastModDate = responseAsInput.ontologies.head.ontologyMetadata.lastModificationDate.get
                assert(newAnythingLastModDate.isAfter(anythingLastModDate))
                anythingLastModDate = newAnythingLastModDate
            }
        }

        "delete the property anything:hasEmptiness" in {
            val propertyIri = URLEncoder.encode("http://0.0.0.0:3333/ontology/0001/anything/v2#hasEmptiness", "UTF-8")
            val lastModificationDate = URLEncoder.encode(anythingLastModDate.toString, "UTF-8")

            Delete(s"/v2/ontologies/properties/$propertyIri?lastModificationDate=$lastModificationDate") ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> ontologiesPath ~> check {
                assert(status == StatusCodes.OK, response.toString)
                val responseJsonDoc = responseToJsonLDDocument(response)
                val ontology = responseJsonDoc.requireObject(OntologyConstants.KnoraApiV2WithValueObjects.HasOntologies)
                ontology.value("@id").asInstanceOf[JsonLDString].value should ===("http://0.0.0.0:3333/0001/ontology/anything/v2")
                val newAnythingLastModDate = Instant.parse(ontology.value(OntologyConstants.KnoraApiV2WithValueObjects.LastModificationDate).asInstanceOf[JsonLDString].value)
                assert(newAnythingLastModDate.isAfter(anythingLastModDate))
                anythingLastModDate = newAnythingLastModDate
            }
        }

        "delete the class anything:Nothing" in {
            val classIri = URLEncoder.encode("http://0.0.0.0:3333/ontology/0001/anything/v2#Nothing", "UTF-8")
            val lastModificationDate = URLEncoder.encode(anythingLastModDate.toString, "UTF-8")

            Delete(s"/v2/ontologies/classes/$classIri?lastModificationDate=$lastModificationDate") ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> ontologiesPath ~> check {
                assert(status == StatusCodes.OK, response.toString)
                val responseJsonDoc = responseToJsonLDDocument(response)
                val ontology = responseJsonDoc.requireObject(OntologyConstants.KnoraApiV2WithValueObjects.HasOntologies)
                ontology.value("@id").asInstanceOf[JsonLDString].value should ===("http://0.0.0.0:3333/ontology/0001/anything/v2")
                val newAnythingLastModDate = Instant.parse(ontology.value(OntologyConstants.KnoraApiV2WithValueObjects.LastModificationDate).asInstanceOf[JsonLDString].value)
                assert(newAnythingLastModDate.isAfter(anythingLastModDate))
                anythingLastModDate = newAnythingLastModDate
            }
        }
    }
}