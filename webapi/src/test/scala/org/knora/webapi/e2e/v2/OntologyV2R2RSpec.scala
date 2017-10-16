package org.knora.webapi.e2e.v2

import java.io.File
import java.net.URLEncoder

import akka.actor.{ActorSystem, Props}
import akka.http.scaladsl.testkit.RouteTestTimeout
import akka.pattern._
import akka.util.Timeout
import org.knora.webapi._
import org.knora.webapi.messages.store.triplestoremessages.ResetTriplestoreContent
import org.knora.webapi.messages.v1.responder.ontologymessages.LoadOntologiesRequest
import org.knora.webapi.responders._
import org.knora.webapi.routing.v2.OntologiesRouteV2
import org.knora.webapi.store._
import org.knora.webapi.util.{AkkaHttpUtils, FileUtil}
import spray.json._

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContextExecutor}

/**
  * End-to-end test specification for API v2 ontology routes.
  */
class OntologyV2R2RSpec extends R2RSpec {


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

    private val knoraApiOntologySimple: JsValue = JsonParser(FileUtil.readTextFile(new File("src/test/resources/test-data/knoraApiOntologySimple.json")))
    private val knoraApiWithValueObjects: JsValue = JsonParser(FileUtil.readTextFile(new File("src/test/resources/test-data/knoraApiWithValueObjects.json")))
    private val incunabulaOntologySimple: JsValue = JsonParser(FileUtil.readTextFile(new File("src/test/resources/test-data/incunabulaOntologySimple.json")))
    private val incunabulaOntologyWithValueObjects: JsValue = JsonParser(FileUtil.readTextFile(new File("src/test/resources/test-data/incunabulaOntologyWithValueObjects.json")))
    private val knoraApiDate: JsValue = JsonParser(FileUtil.readTextFile(new File("src/test/resources/test-data/knoraApiDate.json")))
    private val knoraApiDateValue: JsValue = JsonParser(FileUtil.readTextFile(new File("src/test/resources/test-data/knoraApiDateValue.json")))
    private val knoraApiSimpleHasColor: JsValue = JsonParser(FileUtil.readTextFile(new File("src/test/resources/test-data/knoraApiSimpleHasColor.json")))
    private val knoraApiWithValueObjectsHasColor: JsValue = JsonParser(FileUtil.readTextFile(new File("src/test/resources/test-data/knoraApiWithValueObjectsHasColor.json")))
    private val incunabulaSimplePubDate: JsValue = JsonParser(FileUtil.readTextFile(new File("src/test/resources/test-data/incunabulaSimplePubDate.json")))
    private val incunabulaWithValueObjectsPubDate: JsValue = JsonParser(FileUtil.readTextFile(new File("src/test/resources/test-data/incunabulaWithValueObjectsPubDate.json")))

    "Load test data" in {
        Await.result(storeManager ? ResetTriplestoreContent(rdfDataObjects), 360.seconds)
        Await.result(responderManager ? LoadOntologiesRequest(SharedAdminTestData.rootUser), 30.seconds)
    }

    "The Ontologies v2 Endpoint" should {
        "serve the knora-api simple ontology as JSON-LD via the namedgraphs route" in {
            val ontologyIri = URLEncoder.encode("http://api.knora.org/ontology/knora-api/simple/v2", "UTF-8")

            Get(s"/v2/ontologies/namedgraphs/$ontologyIri") ~> ontologiesPath ~> check {
                val responseJson = AkkaHttpUtils.httpResponseToJson(response)
                assert(responseJson == knoraApiOntologySimple)
            }
        }

        "serve the knora-api simple ontology as JSON-LD via the /ontology route" in {
            Get("/ontology/knora-api/simple/v2") ~> ontologiesPath ~> check {
                val responseJson = AkkaHttpUtils.httpResponseToJson(response)
                assert(responseJson == knoraApiOntologySimple)
            }
        }

        "serve the knora-api with value objects ontology as JSON-LD via the namedgraphs route" in {
            val ontologyIri = URLEncoder.encode("http://api.knora.org/ontology/knora-api/v2", "UTF-8")

            Get(s"/v2/ontologies/namedgraphs/$ontologyIri") ~> ontologiesPath ~> check {
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

        "serve a project-specific ontology as JSON-LD via the namedgraphs route using the simple schema" in {
            val ontologyIri = URLEncoder.encode("http://api.knora.org/ontology/incunabula/simple/v2", "UTF-8")

            Get(s"/v2/ontologies/namedgraphs/$ontologyIri") ~> ontologiesPath ~> check {
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

        "serve a project-specific ontology as JSON-LD via the namedgraphs route using the value object schema" in {
            val ontologyIri = URLEncoder.encode("http://api.knora.org/ontology/incunabula/v2", "UTF-8")

            Get(s"/v2/ontologies/namedgraphs/$ontologyIri") ~> ontologiesPath ~> check {
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
            val classIri = URLEncoder.encode("http://api.knora.org/ontology/incunabula/simple/v2#pubdate", "UTF-8")

            Get(s"/v2/ontologies/properties/$classIri") ~> ontologiesPath ~> check {
                val responseJson = AkkaHttpUtils.httpResponseToJson(response)
                assert(responseJson == incunabulaSimplePubDate)
            }
        }

        "serve a project-specific property as JSON-LD using the value object schema" in {
            val classIri = URLEncoder.encode("http://api.knora.org/ontology/incunabula/v2#pubdate", "UTF-8")

            Get(s"/v2/ontologies/properties/$classIri") ~> ontologiesPath ~> check {
                val responseJson = AkkaHttpUtils.httpResponseToJson(response)
                assert(responseJson == incunabulaWithValueObjectsPubDate)
            }
        }

    }
}
