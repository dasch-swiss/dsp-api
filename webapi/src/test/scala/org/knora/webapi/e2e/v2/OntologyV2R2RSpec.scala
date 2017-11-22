package org.knora.webapi.e2e.v2

import java.io.File
import java.net.URLEncoder

import akka.actor.{ActorSystem, Props}
import akka.http.scaladsl.model.headers.BasicHttpCredentials
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.testkit.RouteTestTimeout
import akka.pattern._
import akka.util.Timeout
import org.knora.webapi._
import org.knora.webapi.messages.store.triplestoremessages.ResetTriplestoreContent
import org.knora.webapi.messages.v1.responder.ontologymessages.LoadOntologiesRequest
import org.knora.webapi.responders._
import org.knora.webapi.routing.v2.OntologiesRouteV2
import org.knora.webapi.store._
import org.knora.webapi.util.jsonld.{JsonLDArray, JsonLDObject, JsonLDValue}
import org.knora.webapi.util.{AkkaHttpUtils, FileUtil}
import spray.json._

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContextExecutor}

object OntologyV2R2RSpec {
    private val userProfile = SharedAdminTestData.imagesUser01
    private val username = userProfile.userData.email.get
    private val password = "test"
    private val projectWithProjectID = SharedAdminTestData.IMAGES_PROJECT_IRI
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

    private val responderManager = system.actorOf(Props(new ResponderManager with LiveActorMaker), name = RESPONDER_MANAGER_ACTOR_NAME)
    private val storeManager = system.actorOf(Props(new StoreManager with LiveActorMaker), name = STORE_MANAGER_ACTOR_NAME)

    private val ontologiesPath = OntologiesRouteV2.knoraApiPath(system, settings, log)

    implicit private val timeout: Timeout = settings.defaultRestoreTimeout

    implicit def default(implicit system: ActorSystem): RouteTestTimeout = RouteTestTimeout(new DurationInt(360).second)

    implicit val ec: ExecutionContextExecutor = system.dispatcher

    private val rdfDataObjects = List()

    private val allOntologyMetadata: JsValue = JsonParser(FileUtil.readTextFile(new File("src/test/resources/test-data/ontologyR2RV2/allOntologyMetadata.json")))
    private val imagesOntologyMetadata: JsValue = JsonParser(FileUtil.readTextFile(new File("src/test/resources/test-data/ontologyR2RV2/imagesOntologyMetadata.json")))
    private val knoraApiOntologySimple: JsValue = JsonParser(FileUtil.readTextFile(new File("src/test/resources/test-data/ontologyR2RV2/knoraApiOntologySimple.json")))
    private val knoraApiWithValueObjects: JsValue = JsonParser(FileUtil.readTextFile(new File("src/test/resources/test-data/ontologyR2RV2/knoraApiWithValueObjects.json")))
    private val incunabulaOntologySimple: JsValue = JsonParser(FileUtil.readTextFile(new File("src/test/resources/test-data/ontologyR2RV2/incunabulaOntologySimple.json")))
    private val incunabulaOntologyWithValueObjects: JsValue = JsonParser(FileUtil.readTextFile(new File("src/test/resources/test-data/ontologyR2RV2/incunabulaOntologyWithValueObjects.json")))
    private val knoraApiDate: JsValue = JsonParser(FileUtil.readTextFile(new File("src/test/resources/test-data/ontologyR2RV2/knoraApiDate.json")))
    private val knoraApiDateValue: JsValue = JsonParser(FileUtil.readTextFile(new File("src/test/resources/test-data/ontologyR2RV2/knoraApiDateValue.json")))
    private val knoraApiSimpleHasColor: JsValue = JsonParser(FileUtil.readTextFile(new File("src/test/resources/test-data/ontologyR2RV2/knoraApiSimpleHasColor.json")))
    private val knoraApiWithValueObjectsHasColor: JsValue = JsonParser(FileUtil.readTextFile(new File("src/test/resources/test-data/ontologyR2RV2/knoraApiWithValueObjectsHasColor.json")))
    private val incunabulaSimplePubDate: JsValue = JsonParser(FileUtil.readTextFile(new File("src/test/resources/test-data/ontologyR2RV2/incunabulaSimplePubDate.json")))
    private val incunabulaWithValueObjectsPubDate: JsValue = JsonParser(FileUtil.readTextFile(new File("src/test/resources/test-data/ontologyR2RV2/incunabulaWithValueObjectsPubDate.json")))
    private val incunabulaPageAndBookWithValueObjects: JsValue = JsonParser(FileUtil.readTextFile(new File("src/test/resources/test-data/ontologyR2RV2/incunabulaPageAndBookWithValueObjects.json")))
    private val exampleOntologySimple: JsValue = JsonParser(FileUtil.readTextFile(new File("src/test/resources/test-data/ontologyR2RV2/p0001-example-simple.json")))
    private val exampleOntologyWithValueObjects: JsValue = JsonParser(FileUtil.readTextFile(new File("src/test/resources/test-data/ontologyR2RV2/p0001-example-withValueObjects.json")))
    private val exampleThingSimple: JsValue = JsonParser(FileUtil.readTextFile(new File("src/test/resources/test-data/ontologyR2RV2/p0001-example-ExampleThingSimple.json")))
    private val exampleThingWithValueObjects: JsValue = JsonParser(FileUtil.readTextFile(new File("src/test/resources/test-data/ontologyR2RV2/p0001-example-ExampleThingWithValueObjects.json")))

    "Load test data" in {
        Await.result(storeManager ? ResetTriplestoreContent(rdfDataObjects), 360.seconds)
        Await.result(responderManager ? LoadOntologiesRequest(SharedAdminTestData.rootUser), 30.seconds)
    }

    "The Ontologies v2 Endpoint" should {
        "serve metadata for all ontologies" in {
            Get(s"/v2/ontologies/metadata") ~> ontologiesPath ~> check {
                val responseJson: JsObject = AkkaHttpUtils.httpResponseToJson(response)
                assert(responseJson == allOntologyMetadata)
            }
        }

        "serve metadata for the ontologies of one project" in {
            val projectIri = URLEncoder.encode(projectWithProjectID, "UTF-8")

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

        "serve two project-specific classes and their properties as JSON-LD using the value object schema" in {
            val pageIri = URLEncoder.encode("http://0.0.0.0:3333/ontology/incunabula/v2#page", "UTF-8")
            val bookIri = URLEncoder.encode("http://0.0.0.0:3333/ontology/incunabula/v2#book", "UTF-8")

            Get(s"/v2/ontologies/classes/$pageIri/$bookIri") ~> ontologiesPath ~> check {
                val responseJson = AkkaHttpUtils.httpResponseToJson(response)
                assert(responseJson == incunabulaPageAndBookWithValueObjects)
            }
        }

        "serve a project-specific ontology whose IRI contains a project ID, as JSON-LD, using the simple schema" in {
            Get("/ontology/0001/example/simple/v2") ~> ontologiesPath ~> check {
                val responseJson = AkkaHttpUtils.httpResponseToJson(response)
                assert(responseJson == exampleOntologySimple)
            }
        }

        "serve a project-specific ontology whose IRI contains a project ID, as JSON-LD, using the value object schema" in {
            Get("/ontology/0001/example/v2") ~> ontologiesPath ~> check {
                val responseJson = AkkaHttpUtils.httpResponseToJson(response)
                assert(responseJson == exampleOntologyWithValueObjects)
            }
        }

        "serve a class from project-specific ontology whose IRI contains a project ID, as JSON-LD, using the simple schema" in {
            val exampleThingIri = URLEncoder.encode("http://0.0.0.0:3333/ontology/0001/example/simple/v2#ExampleThing", "UTF-8")

            Get(s"/v2/ontologies/classes/$exampleThingIri") ~> ontologiesPath ~> check {
                val responseJson = AkkaHttpUtils.httpResponseToJson(response)
                assert(responseJson == exampleThingSimple)
            }
        }

        "serve a class from project-specific ontology whose IRI contains a project ID, as JSON-LD, using the value object schema" in {
            val exampleThingIri = URLEncoder.encode("http://0.0.0.0:3333/ontology/0001/example/v2#ExampleThing", "UTF-8")

            Get(s"/v2/ontologies/classes/$exampleThingIri") ~> ontologiesPath ~> check {
                val responseJson = AkkaHttpUtils.httpResponseToJson(response)
                assert(responseJson == exampleThingWithValueObjects)
            }
        }

        "create an empty ontology called 'example' with a project code" in {
            val params =
                s"""
                   |{
                   |    "knora-api:ontologyName": "example",
                   |    "knora-api:projectIri": "$projectWithProjectID",
                   |    "@context": {
                   |        "knora-api": "${OntologyConstants.KnoraApiV2WithValueObjects.KnoraApiV2PrefixExpansion}"
                   |    }
                   |}
                """.stripMargin

            Post("/v2/ontologies", HttpEntity(ContentTypes.`application/json`, params)) ~> addCredentials(BasicHttpCredentials(username, password)) ~> ontologiesPath ~> check {
                assert(status == StatusCodes.OK, response.toString)
                val responseJsonDoc = responseToJsonLDDocument(response)

                responseJsonDoc.body.value(OntologyConstants.KnoraApiV2WithValueObjects.HasOntologiesWithClasses) match {
                    case ontologies: JsonLDObject =>
                        assert(ontologies.value("http://0.0.0.0:3333/ontology/00FF/example/v2") == JsonLDArray(Seq.empty[JsonLDValue]))
                    case _ => throw AssertionException(s"Unexpected response: $responseJsonDoc")
                }
            }
        }
    }
}
