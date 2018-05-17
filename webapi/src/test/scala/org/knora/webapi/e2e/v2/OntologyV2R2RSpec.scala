package org.knora.webapi.e2e.v2

import java.io.File
import java.net.URLEncoder
import java.time.Instant

import akka.actor.{ActorSystem, Props}
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{Accept, BasicHttpCredentials}
import akka.http.scaladsl.testkit.RouteTestTimeout
import akka.pattern._
import akka.util.Timeout
import org.eclipse.rdf4j.model.Model
import org.knora.webapi._
import org.knora.webapi.messages.store.triplestoremessages.ResetTriplestoreContent
import org.knora.webapi.messages.v2.responder.ontologymessages.{InputOntologyV2, LoadOntologiesRequestV2}
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

    private val allOntologyMetadataJsonLD: JsValue = JsonParser(FileUtil.readTextFile(new File("src/test/resources/test-data/ontologyR2RV2/allOntologyMetadata.json")))
    private val allOntologyMetadataTurtle: Model = parseTurtle(FileUtil.readTextFile(new File("src/test/resources/test-data/ontologyR2RV2/allOntologyMetadata.ttl")))

    private val imagesOntologyMetadataJsonLD: JsValue = JsonParser(FileUtil.readTextFile(new File("src/test/resources/test-data/ontologyR2RV2/imagesOntologyMetadata.json")))
    private val imagesOntologyMetadataTurtle: Model = parseTurtle(FileUtil.readTextFile(new File("src/test/resources/test-data/ontologyR2RV2/imagesOntologyMetadata.ttl")))

    private val knoraApiOntologySimpleJsonLD: JsValue = JsonParser(FileUtil.readTextFile(new File("src/test/resources/test-data/ontologyR2RV2/knoraApiOntologySimple.json")))
    private val knoraApiOntologySimpleTurtle: Model = parseTurtle(FileUtil.readTextFile(new File("src/test/resources/test-data/ontologyR2RV2/knoraApiOntologySimple.ttl")))

    private val knoraApiWithValueObjectsJsonLD: JsValue = JsonParser(FileUtil.readTextFile(new File("src/test/resources/test-data/ontologyR2RV2/knoraApiWithValueObjects.json")))
    private val knoraApiWithValueObjectsTurtle: Model = parseTurtle(FileUtil.readTextFile(new File("src/test/resources/test-data/ontologyR2RV2/knoraApiWithValueObjects.ttl")))

    private val salsahGuiJsonLD: JsValue = JsonParser(FileUtil.readTextFile(new File("src/test/resources/test-data/ontologyR2RV2/salsahGuiOntology.json")))
    private val salsahGuiTurtle: Model = parseTurtle(FileUtil.readTextFile(new File("src/test/resources/test-data/ontologyR2RV2/salsahGuiOntology.ttl")))

    private val standoffSimpleJsonLD: JsValue = JsonParser(FileUtil.readTextFile(new File("src/test/resources/test-data/ontologyR2RV2/standoffOntologySimple.json")))
    private val standoffSimpleTurtle: Model = parseTurtle(FileUtil.readTextFile(new File("src/test/resources/test-data/ontologyR2RV2/standoffOntologySimple.ttl")))

    private val standoffWithValueObjectsJsonLD: JsValue = JsonParser(FileUtil.readTextFile(new File("src/test/resources/test-data/ontologyR2RV2/standoffOntologyWithValueObjects.json")))
    private val standoffWithValueObjectsTurtle: Model = parseTurtle(FileUtil.readTextFile(new File("src/test/resources/test-data/ontologyR2RV2/standoffOntologyWithValueObjects.ttl")))

    private val incunabulaOntologySimpleJsonLD: JsValue = JsonParser(FileUtil.readTextFile(new File("src/test/resources/test-data/ontologyR2RV2/incunabulaOntologySimple.json")))
    private val incunabulaOntologySimpleTurtle: Model = parseTurtle(FileUtil.readTextFile(new File("src/test/resources/test-data/ontologyR2RV2/incunabulaOntologySimple.ttl")))

    private val incunabulaOntologyWithValueObjectsJsonLD: JsValue = JsonParser(FileUtil.readTextFile(new File("src/test/resources/test-data/ontologyR2RV2/incunabulaOntologyWithValueObjects.json")))
    private val incunabulaOntologyWithValueObjectsTurtle: Model = parseTurtle(FileUtil.readTextFile(new File("src/test/resources/test-data/ontologyR2RV2/incunabulaOntologyWithValueObjects.ttl")))

    private val knoraApiDateJsonLD: JsValue = JsonParser(FileUtil.readTextFile(new File("src/test/resources/test-data/ontologyR2RV2/knoraApiDate.json")))
    private val knoraApiDateTurtle: Model = parseTurtle(FileUtil.readTextFile(new File("src/test/resources/test-data/ontologyR2RV2/knoraApiDate.ttl")))

    private val knoraApiDateValueJsonLD: JsValue = JsonParser(FileUtil.readTextFile(new File("src/test/resources/test-data/ontologyR2RV2/knoraApiDateValue.json")))
    private val knoraApiDateValueTurtle: Model = parseTurtle(FileUtil.readTextFile(new File("src/test/resources/test-data/ontologyR2RV2/knoraApiDateValue.ttl")))

    private val knoraApiSimpleHasColorJsonLD: JsValue = JsonParser(FileUtil.readTextFile(new File("src/test/resources/test-data/ontologyR2RV2/knoraApiSimpleHasColor.json")))
    private val knoraApiSimpleHasColorTurtle: Model = parseTurtle(FileUtil.readTextFile(new File("src/test/resources/test-data/ontologyR2RV2/knoraApiSimpleHasColor.ttl")))

    private val knoraApiWithValueObjectsHasColorJsonLD: JsValue = JsonParser(FileUtil.readTextFile(new File("src/test/resources/test-data/ontologyR2RV2/knoraApiWithValueObjectsHasColor.json")))
    private val knoraApiWithValueObjectsHasColorTurtle: Model = parseTurtle(FileUtil.readTextFile(new File("src/test/resources/test-data/ontologyR2RV2/knoraApiWithValueObjectsHasColor.ttl")))

    private val incunabulaSimplePubDateJsonLD: JsValue = JsonParser(FileUtil.readTextFile(new File("src/test/resources/test-data/ontologyR2RV2/incunabulaSimplePubDate.json")))
    private val incunabulaSimplePubDateTurtle: Model = parseTurtle(FileUtil.readTextFile(new File("src/test/resources/test-data/ontologyR2RV2/incunabulaSimplePubDate.ttl")))

    private val incunabulaWithValueObjectsPubDateJsonLD: JsValue = JsonParser(FileUtil.readTextFile(new File("src/test/resources/test-data/ontologyR2RV2/incunabulaWithValueObjectsPubDate.json")))
    private val incunabulaWithValueObjectsPubDateTurtle: Model = parseTurtle(FileUtil.readTextFile(new File("src/test/resources/test-data/ontologyR2RV2/incunabulaWithValueObjectsPubDate.ttl")))

    private val incunabulaPageAndBookWithValueObjectsJsonLD: JsValue = JsonParser(FileUtil.readTextFile(new File("src/test/resources/test-data/ontologyR2RV2/incunabulaPageAndBookWithValueObjects.json")))
    private val incunabulaPageAndBookWithValueObjectsTurtle: Model = parseTurtle(FileUtil.readTextFile(new File("src/test/resources/test-data/ontologyR2RV2/incunabulaPageAndBookWithValueObjects.ttl")))

    private val fooIri = new MutableTestIri
    private var fooLastModDate: Instant = Instant.now

    private val AnythingOntologyIri = "http://0.0.0.0:3333/ontology/0001/anything/v2".toSmartIri
    private var anythingLastModDate: Instant = Instant.parse("2017-12-19T15:23:42.166Z")

    private def getPropertyIrisFromResourceClassResponse(responseJsonDoc: JsonLDDocument): Set[SmartIri] = {
        val classDef = responseJsonDoc.body.requireArray("@graph").value.head.asInstanceOf[JsonLDObject]

        classDef.value(OntologyConstants.Rdfs.SubClassOf).asInstanceOf[JsonLDArray].value.collect {
            case obj: JsonLDObject if !JsonLDUtil.isIriValue(obj) => obj.requireIriInObject(OntologyConstants.Owl.OnProperty, stringFormatter.toSmartIriWithErr)
        }.toSet
    }

    "Load test data" in {
        Await.result(storeManager ? ResetTriplestoreContent(rdfDataObjects), 360.seconds)
        Await.result(responderManager ? LoadOntologiesRequestV2(KnoraSystemInstances.Users.SystemUser), 30.seconds)
    }

    "The Ontologies v2 Endpoint" should {
        "serve metadata for all ontologies in JSON-LD" in {
            Get(s"/v2/ontologies/metadata") ~> ontologiesPath ~> check {
                val responseJson: JsValue = JsonParser(responseAs[String])
                assert(responseJson == allOntologyMetadataJsonLD)
            }
        }

        "serve metadata for all ontologies in Turtle" in {
            Get(s"/v2/ontologies/metadata").addHeader(Accept(RdfMediaTypes.`text/turtle`)) ~> ontologiesPath ~> check {
                assert(parseTurtle(responseAs[String]) == allOntologyMetadataTurtle)
            }
        }

        "serve metadata for the ontologies of one project in JSON-LD" in {
            val projectIri = URLEncoder.encode(imagesProjectIri, "UTF-8")

            Get(s"/v2/ontologies/metadata/$projectIri") ~> ontologiesPath ~> check {
                val responseJson: JsValue = JsonParser(responseAs[String])
                assert(responseJson == imagesOntologyMetadataJsonLD)
            }
        }

        "serve metadata for the ontologies of one project in Turtle" in {
            val projectIri = URLEncoder.encode(imagesProjectIri, "UTF-8")

            Get(s"/v2/ontologies/metadata/$projectIri").addHeader(Accept(RdfMediaTypes.`text/turtle`)) ~> ontologiesPath ~> check {
                assert(parseTurtle(responseAs[String]) == imagesOntologyMetadataTurtle)
            }
        }

        "serve the knora-api simple ontology via the allentities route in JSON-LD" in {
            val ontologyIri = URLEncoder.encode("http://api.knora.org/ontology/knora-api/simple/v2", "UTF-8")

            Get(s"/v2/ontologies/allentities/$ontologyIri") ~> ontologiesPath ~> check {
                val responseJson: JsValue = JsonParser(responseAs[String])
                assert(responseJson == knoraApiOntologySimpleJsonLD)
            }
        }

        "serve the knora-api simple ontology via the allentities route in Turtle" in {
            val ontologyIri = URLEncoder.encode("http://api.knora.org/ontology/knora-api/simple/v2", "UTF-8")

            Get(s"/v2/ontologies/allentities/$ontologyIri").addHeader(Accept(RdfMediaTypes.`text/turtle`)) ~> ontologiesPath ~> check {
                assert(parseTurtle(responseAs[String]) == knoraApiOntologySimpleTurtle)
            }
        }

        "serve the knora-api simple ontology via the /ontology route in JSON-LD" in {
            Get("/ontology/knora-api/simple/v2") ~> ontologiesPath ~> check {
                val responseJson = JsonParser(responseAs[String])
                assert(responseJson == knoraApiOntologySimpleJsonLD)
            }
        }

        "serve the knora-api simple ontology via the /ontology route in Turtle" in {
            Get("/ontology/knora-api/simple/v2").addHeader(Accept(RdfMediaTypes.`text/turtle`)) ~> ontologiesPath ~> check {
                assert(parseTurtle(responseAs[String]) == knoraApiOntologySimpleTurtle)
            }
        }

        "serve the knora-api with value objects ontology via the /ontologies/allentities route in JSON-LD" in {
            val ontologyIri = URLEncoder.encode("http://api.knora.org/ontology/knora-api/v2", "UTF-8")

            Get(s"/v2/ontologies/allentities/$ontologyIri") ~> ontologiesPath ~> check {
                val responseJson = JsonParser(responseAs[String])
                assert(responseJson == knoraApiWithValueObjectsJsonLD)
            }
        }

        "serve the knora-api with value objects ontology via the /ontologies/allentities route in Turtle" in {
            val ontologyIri = URLEncoder.encode("http://api.knora.org/ontology/knora-api/v2", "UTF-8")

            Get(s"/v2/ontologies/allentities/$ontologyIri").addHeader(Accept(RdfMediaTypes.`text/turtle`)) ~> ontologiesPath ~> check {
                assert(parseTurtle(responseAs[String]) == knoraApiWithValueObjectsTurtle)
            }
        }

        "serve the knora-api with value objects ontology via the /ontology route in JSON-LD" in {
            Get("/ontology/knora-api/v2") ~> ontologiesPath ~> check {
                val responseJson = JsonParser(responseAs[String])
                assert(responseJson == knoraApiWithValueObjectsJsonLD)
            }
        }

        "serve the knora-api with value objects ontology via the /ontology route in Turtle" in {
            Get("/ontology/knora-api/v2").addHeader(Accept(RdfMediaTypes.`text/turtle`)) ~> ontologiesPath ~> check {
                assert(parseTurtle(responseAs[String]) == knoraApiWithValueObjectsTurtle)
            }
        }

        "serve the salsah-gui ontology via the /ontology route in JSON-LD" in {
            Get("/ontology/salsah-gui/v2") ~> ontologiesPath ~> check {
                val responseJson = JsonParser(responseAs[String])
                assert(responseJson == salsahGuiJsonLD)
            }
        }

        "serve the salsah-gui ontology via the /ontology route in Turtle" in {
            Get("/ontology/salsah-gui/v2").addHeader(Accept(RdfMediaTypes.`text/turtle`)) ~> ontologiesPath ~> check {
                assert(parseTurtle(responseAs[String]) == salsahGuiTurtle)
            }
        }

        "serve the standoff ontology via the /ontology route using the simple schema in JSON-LD" in {
            Get("/ontology/standoff/simple/v2") ~> ontologiesPath ~> check {
                val responseJson = JsonParser(responseAs[String])
                assert(responseJson == standoffSimpleJsonLD)
            }
        }

        "serve the standoff ontology via the /ontology route using the simple schema in Turtle" in {
            Get("/ontology/standoff/simple/v2").addHeader(Accept(RdfMediaTypes.`text/turtle`)) ~> ontologiesPath ~> check {
                assert(parseTurtle(responseAs[String]) == standoffSimpleTurtle)
            }
        }

        "serve the standoff ontology via the /ontology route using the value object schema in JSON-LD" in {
            Get("/ontology/standoff/v2") ~> ontologiesPath ~> check {
                val responseJson = JsonParser(responseAs[String])
                assert(responseJson == standoffWithValueObjectsJsonLD)
            }
        }

        "serve the standoff ontology via the /ontology route using the value object schema in Turtle" in {
            Get("/ontology/standoff/v2").addHeader(Accept(RdfMediaTypes.`text/turtle`)) ~> ontologiesPath ~> check {
                assert(parseTurtle(responseAs[String]) == standoffWithValueObjectsTurtle)
            }
        }

        "serve a project-specific ontology via the /ontologies/allentities route using the simple schema in JSON-LD" in {
            val ontologyIri = URLEncoder.encode("http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2", "UTF-8")

            Get(s"/v2/ontologies/allentities/$ontologyIri") ~> ontologiesPath ~> check {
                val responseJson = JsonParser(responseAs[String])
                assert(responseJson == incunabulaOntologySimpleJsonLD)
            }
        }

        "serve a project-specific ontology via the /ontologies/allentities route using the simple schema in Turtle" in {
            val ontologyIri = URLEncoder.encode("http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2", "UTF-8")

            Get(s"/v2/ontologies/allentities/$ontologyIri").addHeader(Accept(RdfMediaTypes.`text/turtle`)) ~> ontologiesPath ~> check {
                assert(parseTurtle(responseAs[String]) == incunabulaOntologySimpleTurtle)
            }
        }

        "serve a project-specific ontology via the /ontology route using the simple schema in JSON-LD" in {
            Get("/ontology/0803/incunabula/simple/v2") ~> ontologiesPath ~> check {
                val responseJson = JsonParser(responseAs[String])
                assert(responseJson == incunabulaOntologySimpleJsonLD)
            }
        }

        "serve a project-specific ontology via the /ontology route using the simple schema in Turtle" in {
            Get("/ontology/0803/incunabula/simple/v2").addHeader(Accept(RdfMediaTypes.`text/turtle`)) ~> ontologiesPath ~> check {
                assert(parseTurtle(responseAs[String]) == incunabulaOntologySimpleTurtle)
            }
        }

        "serve a project-specific ontology via the /ontologies/allentities route using the value object schema in JSON-LD" in {
            val ontologyIri = URLEncoder.encode("http://0.0.0.0:3333/ontology/0803/incunabula/v2", "UTF-8")

            Get(s"/v2/ontologies/allentities/$ontologyIri") ~> ontologiesPath ~> check {
                val responseJson = JsonParser(responseAs[String])
                assert(responseJson == incunabulaOntologyWithValueObjectsJsonLD)
            }
        }

        "serve a project-specific ontology via the /ontologies/allentities route using the value object schema in Turtle" in {
            val ontologyIri = URLEncoder.encode("http://0.0.0.0:3333/ontology/0803/incunabula/v2", "UTF-8")

            Get(s"/v2/ontologies/allentities/$ontologyIri").addHeader(Accept(RdfMediaTypes.`text/turtle`)) ~> ontologiesPath ~> check {
                assert(parseTurtle(responseAs[String]) == incunabulaOntologyWithValueObjectsTurtle)
            }
        }

        "serve a project-specific ontology via the /ontology route using the value object schema in JSON-LD" in {
            Get("/ontology/0803/incunabula/v2") ~> ontologiesPath ~> check {
                val responseJson = JsonParser(responseAs[String])
                assert(responseJson == incunabulaOntologyWithValueObjectsJsonLD)
            }
        }

        "serve a project-specific ontology via the /ontology route using the value object schema in Turtle" in {
            Get("/ontology/0803/incunabula/v2").addHeader(Accept(RdfMediaTypes.`text/turtle`)) ~> ontologiesPath ~> check {
                assert(parseTurtle(responseAs[String]) == incunabulaOntologyWithValueObjectsTurtle)
            }
        }

        "serve a knora-api custom datatype from the simple schema in JSON-LD" in {
            val classIri = URLEncoder.encode("http://api.knora.org/ontology/knora-api/simple/v2#Date", "UTF-8")

            Get(s"/v2/ontologies/classes/$classIri") ~> ontologiesPath ~> check {
                val responseJson = JsonParser(responseAs[String])
                assert(responseJson == knoraApiDateJsonLD)
            }
        }

        "serve a knora-api custom datatype from the simple schema in Turtle" in {
            val classIri = URLEncoder.encode("http://api.knora.org/ontology/knora-api/simple/v2#Date", "UTF-8")

            Get(s"/v2/ontologies/classes/$classIri").addHeader(Accept(RdfMediaTypes.`text/turtle`)) ~> ontologiesPath ~> check {
                assert(parseTurtle(responseAs[String]) == knoraApiDateTurtle)
            }
        }

        "serve a knora-api value class from the value object schema in JSON-LD" in {
            val classIri = URLEncoder.encode("http://api.knora.org/ontology/knora-api/v2#DateValue", "UTF-8")

            Get(s"/v2/ontologies/classes/$classIri") ~> ontologiesPath ~> check {
                val responseJson = JsonParser(responseAs[String])
                assert(responseJson == knoraApiDateValueJsonLD)
            }
        }

        "serve a knora-api value class from the value object schema in Turtle" in {
            val classIri = URLEncoder.encode("http://api.knora.org/ontology/knora-api/v2#DateValue", "UTF-8")

            Get(s"/v2/ontologies/classes/$classIri").addHeader(Accept(RdfMediaTypes.`text/turtle`)) ~> ontologiesPath ~> check {
                assert(parseTurtle(responseAs[String]) == knoraApiDateValueTurtle)
            }
        }

        "serve a knora-api property from the simple schema in JSON-LD" in {
            val classIri = URLEncoder.encode("http://api.knora.org/ontology/knora-api/simple/v2#hasColor", "UTF-8")

            Get(s"/v2/ontologies/properties/$classIri") ~> ontologiesPath ~> check {
                val responseJson = JsonParser(responseAs[String])
                assert(responseJson == knoraApiSimpleHasColorJsonLD)
            }
        }

        "serve a knora-api property from the simple schema in Turtle" in {
            val classIri = URLEncoder.encode("http://api.knora.org/ontology/knora-api/simple/v2#hasColor", "UTF-8")

            Get(s"/v2/ontologies/properties/$classIri").addHeader(Accept(RdfMediaTypes.`text/turtle`)) ~> ontologiesPath ~> check {
                assert(parseTurtle(responseAs[String]) == knoraApiSimpleHasColorTurtle)
            }
        }

        "serve a knora-api property from the value object schema in JSON-LD" in {
            val classIri = URLEncoder.encode("http://api.knora.org/ontology/knora-api/v2#hasColor", "UTF-8")

            Get(s"/v2/ontologies/properties/$classIri") ~> ontologiesPath ~> check {
                val responseJson = JsonParser(responseAs[String])
                assert(responseJson == knoraApiWithValueObjectsHasColorJsonLD)
            }
        }

        "serve a knora-api property from the value object schema in Turtle" in {
            val classIri = URLEncoder.encode("http://api.knora.org/ontology/knora-api/v2#hasColor", "UTF-8")

            Get(s"/v2/ontologies/properties/$classIri").addHeader(Accept(RdfMediaTypes.`text/turtle`)) ~> ontologiesPath ~> check {
                assert(parseTurtle(responseAs[String]) == knoraApiWithValueObjectsHasColorTurtle)
            }
        }

        "serve a project-specific property using the simple schema in JSON-LD" in {
            val classIri = URLEncoder.encode("http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#pubdate", "UTF-8")

            Get(s"/v2/ontologies/properties/$classIri") ~> ontologiesPath ~> check {
                val responseJson = JsonParser(responseAs[String])
                assert(responseJson == incunabulaSimplePubDateJsonLD)
            }
        }

        "serve a project-specific property using the simple schema in Turtle" in {
            val classIri = URLEncoder.encode("http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#pubdate", "UTF-8")

            Get(s"/v2/ontologies/properties/$classIri").addHeader(Accept(RdfMediaTypes.`text/turtle`)) ~> ontologiesPath ~> check {
                assert(parseTurtle(responseAs[String]) == incunabulaSimplePubDateTurtle)
            }
        }

        "serve a project-specific property using the value object schema in JSON-LD" in {
            val classIri = URLEncoder.encode("http://0.0.0.0:3333/ontology/0803/incunabula/v2#pubdate", "UTF-8")

            Get(s"/v2/ontologies/properties/$classIri") ~> ontologiesPath ~> check {
                val responseJson = JsonParser(responseAs[String])
                assert(responseJson == incunabulaWithValueObjectsPubDateJsonLD)
            }
        }

        "serve a project-specific property using the value object schema in Turtle" in {
            val classIri = URLEncoder.encode("http://0.0.0.0:3333/ontology/0803/incunabula/v2#pubdate", "UTF-8")

            Get(s"/v2/ontologies/properties/$classIri").addHeader(Accept(RdfMediaTypes.`text/turtle`)) ~> ontologiesPath ~> check {
                assert(parseTurtle(responseAs[String]) == incunabulaWithValueObjectsPubDateTurtle)
            }
        }

        "serve two project-specific classes using the value object schema in JSON-LD" in {
            val pageIri = URLEncoder.encode("http://0.0.0.0:3333/ontology/0803/incunabula/v2#page", "UTF-8")
            val bookIri = URLEncoder.encode("http://0.0.0.0:3333/ontology/0803/incunabula/v2#book", "UTF-8")

            Get(s"/v2/ontologies/classes/$pageIri/$bookIri") ~> ontologiesPath ~> check {
                val responseJson = JsonParser(responseAs[String])
                assert(responseJson == incunabulaPageAndBookWithValueObjectsJsonLD)
            }
        }

        "serve two project-specific classes using the value object schema in Turtle" in {
            val pageIri = URLEncoder.encode("http://0.0.0.0:3333/ontology/0803/incunabula/v2#page", "UTF-8")
            val bookIri = URLEncoder.encode("http://0.0.0.0:3333/ontology/0803/incunabula/v2#book", "UTF-8")

            Get(s"/v2/ontologies/classes/$pageIri/$bookIri").addHeader(Accept(RdfMediaTypes.`text/turtle`)) ~> ontologiesPath ~> check {
                assert(parseTurtle(responseAs[String]) == incunabulaPageAndBookWithValueObjectsTurtle)
            }
        }

        "create an empty ontology called 'foo' with a project code" in {
            val label = "The foo ontology"

            val params =
                s"""
                   |{
                   |    "knora-api:ontologyName": "foo",
                   |    "knora-api:attachedToProject": {
                   |      "@id": "$imagesProjectIri"
                   |    },
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
                val metadata = responseJsonDoc.body
                val ontologyIri = metadata.value("@id").asInstanceOf[JsonLDString].value
                assert(ontologyIri == "http://0.0.0.0:3333/ontology/00FF/foo/v2")
                fooIri.set(ontologyIri)
                assert(metadata.value(OntologyConstants.Rdfs.Label) == JsonLDString(label))
                val lastModDate = Instant.parse(metadata.value(OntologyConstants.KnoraApiV2WithValueObjects.LastModificationDate).asInstanceOf[JsonLDString].value)
                fooLastModDate = lastModDate
            }
        }

        "change the metadata of 'foo'" in {
            val newLabel = "The modified foo ontology"

            val params =
                s"""
                   |{
                   |  "@id": "${fooIri.get}",
                   |  "rdfs:label": "$newLabel",
                   |  "knora-api:lastModificationDate": "$fooLastModDate",
                   |  "@context": {
                   |    "rdfs": "${OntologyConstants.Rdfs.RdfsPrefixExpansion}",
                   |    "knora-api": "${OntologyConstants.KnoraApiV2WithValueObjects.KnoraApiV2PrefixExpansion}"
                   |  }
                   |}
                """.stripMargin

            Put("/v2/ontologies/metadata", HttpEntity(ContentTypes.`application/json`, params)) ~> addCredentials(BasicHttpCredentials(imagesUsername, password)) ~> ontologiesPath ~> check {
                assert(status == StatusCodes.OK, response.toString)
                val responseJsonDoc = responseToJsonLDDocument(response)
                val metadata = responseJsonDoc.body
                val ontologyIri = metadata.value("@id").asInstanceOf[JsonLDString].value
                assert(ontologyIri == fooIri.get)
                assert(metadata.value(OntologyConstants.Rdfs.Label) == JsonLDString(newLabel))
                val lastModDate = Instant.parse(metadata.value(OntologyConstants.KnoraApiV2WithValueObjects.LastModificationDate).asInstanceOf[JsonLDString].value)
                assert(lastModDate.isAfter(fooLastModDate))
                fooLastModDate = lastModDate
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
                  |  "@id" : "http://0.0.0.0:3333/ontology/0001/anything/v2",
                  |  "@type" : "owl:Ontology",
                  |  "knora-api:lastModificationDate" : "2017-12-19T15:23:42.166Z",
                  |  "@graph" : [ {
                  |      "@id" : "anything:hasName",
                  |      "@type" : "owl:ObjectProperty",
                  |      "knora-api:subjectType" : {
                  |        "@id" : "anything:Thing"
                  |      },
                  |      "knora-api:objectType" : {
                  |        "@id" : "knora-api:TextValue"
                  |      },
                  |      "rdfs:comment" : [ {
                  |        "@language" : "en",
                  |        "@value" : "The name of a Thing"
                  |      }, {
                  |        "@language" : "de",
                  |        "@value" : "Der Name eines Dinges"
                  |      } ],
                  |      "rdfs:label" : [ {
                  |        "@language" : "en",
                  |        "@value" : "has name"
                  |      }, {
                  |        "@language" : "de",
                  |        "@value" : "hat Namen"
                  |      } ],
                  |      "rdfs:subPropertyOf" : [ {
                  |        "@id" : "knora-api:hasValue"
                  |      }, {
                  |        "@id" : "http://schema.org/name"
                  |      } ],
                  |      "salsah-gui:guiElement" : {
                  |        "@id" : "salsah-gui:SimpleText"
                  |      },
                  |      "salsah-gui:guiAttribute" : [ "size=80", "maxlength=100" ]
                  |  } ],
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

            // Convert the submitted JSON-LD to an InputOntologyV2, without SPARQL-escaping, so we can compare it to the response.
            val paramsAsInput: InputOntologyV2 = InputOntologyV2.fromJsonLD(JsonLDUtil.parseJsonLD(params)).unescape

            Post("/v2/ontologies/properties", HttpEntity(ContentTypes.`application/json`, params)) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> ontologiesPath ~> check {
                assert(status == StatusCodes.OK, response.toString)
                val responseJsonDoc = responseToJsonLDDocument(response)

                // Convert the response to an InputOntologyV2 and compare the relevant part of it to the request.
                val responseAsInput: InputOntologyV2 = InputOntologyV2.fromJsonLD(responseJsonDoc, ignoreExtraData = true).unescape
                responseAsInput.properties should ===(paramsAsInput.properties)

                // Check that the ontology's last modification date was updated.
                val newAnythingLastModDate = responseAsInput.ontologyMetadata.lastModificationDate.get
                assert(newAnythingLastModDate.isAfter(anythingLastModDate))
                anythingLastModDate = newAnythingLastModDate
            }
        }

        "change the labels of a property" in {
            val params =
                s"""
                   |{
                   |  "@id" : "$AnythingOntologyIri",
                   |  "@type" : "owl:Ontology",
                   |  "knora-api:lastModificationDate" : "$anythingLastModDate",
                   |  "@graph" : [ {
                   |    "@id" : "anything:hasName",
                   |    "@type" : "owl:ObjectProperty",
                   |    "rdfs:label" : [ {
                   |      "@language" : "en",
                   |      "@value" : "has name"
                   |    }, {
                   |      "@language" : "fr",
                   |      "@value" : "a nom"
                   |    }, {
                   |      "@language" : "de",
                   |      "@value" : "hat Namen"
                   |    } ]
                   |  } ],
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

            // Convert the submitted JSON-LD to an InputOntologyV2, without SPARQL-escaping, so we can compare it to the response.
            val paramsAsInput: InputOntologyV2 = InputOntologyV2.fromJsonLD(JsonLDUtil.parseJsonLD(params)).unescape

            Put("/v2/ontologies/properties", HttpEntity(ContentTypes.`application/json`, params)) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> ontologiesPath ~> check {
                assert(status == StatusCodes.OK, response.toString)
                val responseJsonDoc = responseToJsonLDDocument(response)

                // Comvert the response to an InputOntologyV2 and compare the relevant part of it to the request.
                val responseAsInput: InputOntologyV2 = InputOntologyV2.fromJsonLD(responseJsonDoc, ignoreExtraData = true).unescape
                responseAsInput.properties.head._2.predicates(OntologyConstants.Rdfs.Label.toSmartIri).objects should ===(paramsAsInput.properties.head._2.predicates.head._2.objects)

                // Check that the ontology's last modification date was updated.
                val newAnythingLastModDate = responseAsInput.ontologyMetadata.lastModificationDate.get
                assert(newAnythingLastModDate.isAfter(anythingLastModDate))
                anythingLastModDate = newAnythingLastModDate
            }
        }

        "change the comments of a property" in {
            val params =
                s"""
                   |{
                   |  "@id" : "$AnythingOntologyIri",
                   |  "@type" : "owl:Ontology",
                   |  "knora-api:lastModificationDate" : "$anythingLastModDate",
                   |  "@graph" : [ {
                   |    "@id" : "anything:hasName",
                   |    "@type" : "owl:ObjectProperty",
                   |    "rdfs:comment" : [ {
                   |      "@language" : "en",
                   |      "@value" : "The name of a Thing"
                   |    }, {
                   |      "@language" : "fr",
                   |      "@value" : "Le nom d'une chose"
                   |    }, {
                   |      "@language" : "de",
                   |      "@value" : "Der Name eines Dinges"
                   |    } ]
                   |  } ],
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

            // Convert the submitted JSON-LD to an InputOntologyV2, without SPARQL-escaping, so we can compare it to the response.
            val paramsAsInput: InputOntologyV2 = InputOntologyV2.fromJsonLD(JsonLDUtil.parseJsonLD(params)).unescape

            Put("/v2/ontologies/properties", HttpEntity(ContentTypes.`application/json`, params)) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> ontologiesPath ~> check {
                assert(status == StatusCodes.OK, response.toString)
                val responseJsonDoc = responseToJsonLDDocument(response)

                // Comvert the response to an InputOntologyV2 and compare the relevant part of it to the request.
                val responseAsInput: InputOntologyV2 = InputOntologyV2.fromJsonLD(responseJsonDoc, ignoreExtraData = true).unescape
                responseAsInput.properties.head._2.predicates(OntologyConstants.Rdfs.Comment.toSmartIri).objects should ===(paramsAsInput.properties.head._2.predicates.head._2.objects)

                // Check that the ontology's last modification date was updated.
                val newAnythingLastModDate = responseAsInput.ontologyMetadata.lastModificationDate.get
                assert(newAnythingLastModDate.isAfter(anythingLastModDate))
                anythingLastModDate = newAnythingLastModDate
            }
        }

        "create a class anything:WildThing that is a subclass of anything:Thing, with a direct cardinality for anything:hasName" in {
            val params =
                s"""
                   |{
                   |  "@id" : "http://0.0.0.0:3333/ontology/0001/anything/v2",
                   |  "@type" : "owl:Ontology",
                   |  "knora-api:lastModificationDate" : "$anythingLastModDate",
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
                   |    "rdfs:subClassOf" : [
                   |      {
                   |        "@id": "anything:Thing"
                   |      },
                   |      {
                   |        "@type": "http://www.w3.org/2002/07/owl#Restriction",
                   |        "owl:maxCardinality": 1,
                   |        "owl:onProperty": {
                   |          "@id": "anything:hasName"
                   |        },
                   |        "salsah-gui:guiOrder": 1
                   |      }
                   |    ]
                   |  } ],
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

            // Convert the submitted JSON-LD to an InputOntologyV2, without SPARQL-escaping, so we can compare it to the response.
            val paramsAsInput: InputOntologyV2 = InputOntologyV2.fromJsonLD(JsonLDUtil.parseJsonLD(params)).unescape

            Post("/v2/ontologies/classes", HttpEntity(ContentTypes.`application/json`, params)) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> ontologiesPath ~> check {
                assert(status == StatusCodes.OK, response.toString)
                val responseJsonDoc = responseToJsonLDDocument(response)

                // Convert the response to an InputOntologyV2 and compare the relevant part of it to the request.
                val responseAsInput: InputOntologyV2 = InputOntologyV2.fromJsonLD(responseJsonDoc, ignoreExtraData = true).unescape
                responseAsInput.classes should ===(paramsAsInput.classes)

                // Check that cardinalities were inherited from anything:Thing.
                getPropertyIrisFromResourceClassResponse(responseJsonDoc) should ===(expectedProperties)

                // Check that the ontology's last modification date was updated.
                val newAnythingLastModDate = responseAsInput.ontologyMetadata.lastModificationDate.get
                assert(newAnythingLastModDate.isAfter(anythingLastModDate))
                anythingLastModDate = newAnythingLastModDate
            }
        }

        "create a class anything:Nothing with no properties" in {
            val params =
                s"""
                   |{
                   |  "@id" : "http://0.0.0.0:3333/ontology/0001/anything/v2",
                   |  "@type" : "owl:Ontology",
                   |  "knora-api:lastModificationDate" : "$anythingLastModDate",
                   |  "@graph" : [ {
                   |    "@id" : "anything:Nothing",
                   |    "@type" : "owl:Class",
                   |    "rdfs:label" : {
                   |      "@language" : "en",
                   |      "@value" : "nothing"
                   |    },
                   |    "rdfs:comment" : {
                   |      "@language" : "en",
                   |      "@value" : "Represents nothing"
                   |    },
                   |    "rdfs:subClassOf" : {
                   |      "@id" : "knora-api:Resource"
                   |    }
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

            // Convert the submitted JSON-LD to an InputOntologyV2, without SPARQL-escaping, so we can compare it to the response.
            val paramsAsInput: InputOntologyV2 = InputOntologyV2.fromJsonLD(JsonLDUtil.parseJsonLD(params)).unescape

            Post("/v2/ontologies/classes", HttpEntity(ContentTypes.`application/json`, params)) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> ontologiesPath ~> check {
                assert(status == StatusCodes.OK, response.toString)
                val responseJsonDoc = responseToJsonLDDocument(response)

                // Convert the response to an InputOntologyV2 and compare the relevant part of it to the request.
                val responseAsInput: InputOntologyV2 = InputOntologyV2.fromJsonLD(responseJsonDoc, ignoreExtraData = true).unescape
                responseAsInput.classes should ===(paramsAsInput.classes)

                // Check that cardinalities were inherited from knora-api:Resource.
                getPropertyIrisFromResourceClassResponse(responseJsonDoc) should ===(expectedProperties)

                // Check that the ontology's last modification date was updated.
                val newAnythingLastModDate = responseAsInput.ontologyMetadata.lastModificationDate.get
                assert(newAnythingLastModDate.isAfter(anythingLastModDate))
                anythingLastModDate = newAnythingLastModDate
            }
        }

        "change the labels of a class" in {
            val params =
                s"""
                   |{
                   |  "@id" : "$AnythingOntologyIri",
                   |  "@type" : "owl:Ontology",
                   |  "knora-api:lastModificationDate" : "$anythingLastModDate",
                   |  "@graph" : [ {
                   |    "@id" : "anything:Nothing",
                   |    "@type" : "owl:Class",
                   |    "rdfs:label" : [ {
                   |      "@language" : "en",
                   |      "@value" : "nothing"
                   |    }, {
                   |      "@language" : "fr",
                   |      "@value" : "rien"
                   |    } ]
                   |  } ],
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

            // Convert the submitted JSON-LD to an InputOntologyV2, without SPARQL-escaping, so we can compare it to the response.
            val paramsAsInput: InputOntologyV2 = InputOntologyV2.fromJsonLD(JsonLDUtil.parseJsonLD(params)).unescape

            Put("/v2/ontologies/classes", HttpEntity(ContentTypes.`application/json`, params)) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> ontologiesPath ~> check {
                assert(status == StatusCodes.OK, response.toString)
                val responseJsonDoc = responseToJsonLDDocument(response)

                // Comvert the response to an InputOntologyV2 and compare the relevant part of it to the request.
                val responseAsInput: InputOntologyV2 = InputOntologyV2.fromJsonLD(responseJsonDoc, ignoreExtraData = true).unescape
                responseAsInput.classes.head._2.predicates(OntologyConstants.Rdfs.Label.toSmartIri).objects should ===(paramsAsInput.classes.head._2.predicates.head._2.objects)

                // Check that the ontology's last modification date was updated.
                val newAnythingLastModDate = responseAsInput.ontologyMetadata.lastModificationDate.get
                assert(newAnythingLastModDate.isAfter(anythingLastModDate))
                anythingLastModDate = newAnythingLastModDate
            }
        }

        "change the comments of a class" in {
            val params =
                s"""
                   |{
                   |  "@id" : "$AnythingOntologyIri",
                   |  "@type" : "owl:Ontology",
                   |  "knora-api:lastModificationDate" : "$anythingLastModDate",
                   |  "@graph" : [ {
                   |    "@id" : "anything:Nothing",
                   |    "@type" : "owl:Class",
                   |    "rdfs:comment" : [ {
                   |      "@language" : "en",
                   |      "@value" : "Represents nothing"
                   |    }, {
                   |      "@language" : "fr",
                   |      "@value" : "ne représente rien"
                   |    } ]
                   |  } ],
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

            // Convert the submitted JSON-LD to an InputOntologyV2, without SPARQL-escaping, so we can compare it to the response.
            val paramsAsInput: InputOntologyV2 = InputOntologyV2.fromJsonLD(JsonLDUtil.parseJsonLD(params)).unescape

            Put("/v2/ontologies/classes", HttpEntity(ContentTypes.`application/json`, params)) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> ontologiesPath ~> check {
                assert(status == StatusCodes.OK, response.toString)
                val responseJsonDoc = responseToJsonLDDocument(response)

                // Comvert the response to an InputOntologyV2 and compare the relevant part of it to the request.
                val responseAsInput: InputOntologyV2 = InputOntologyV2.fromJsonLD(responseJsonDoc, ignoreExtraData = true).unescape
                responseAsInput.classes.head._2.predicates(OntologyConstants.Rdfs.Comment.toSmartIri).objects should ===(paramsAsInput.classes.head._2.predicates.head._2.objects)

                // Check that the ontology's last modification date was updated.
                val newAnythingLastModDate = responseAsInput.ontologyMetadata.lastModificationDate.get
                assert(newAnythingLastModDate.isAfter(anythingLastModDate))
                anythingLastModDate = newAnythingLastModDate
            }
        }

        "create a property anything:hasNothingness with knora-api:subjectType anything:Nothing" in {
            val params =
                s"""
                   |{
                   |  "@id" : "http://0.0.0.0:3333/ontology/0001/anything/v2",
                   |  "@type" : "owl:Ontology",
                   |  "knora-api:lastModificationDate" : "$anythingLastModDate",
                   |  "@graph" : [ {
                   |    "@id" : "anything:hasNothingness",
                   |    "@type" : "owl:ObjectProperty",
                   |    "knora-api:subjectType" : {
                   |      "@id" : "anything:Nothing"
                   |    },
                   |    "knora-api:objectType" : {
                   |      "@id" : "knora-api:BooleanValue"
                   |    },
                   |    "rdfs:comment" : {
                   |      "@language" : "en",
                   |      "@value" : "Indicates whether a Nothing has nothingness"
                   |    },
                   |    "rdfs:label" : {
                   |      "@language" : "en",
                   |      "@value" : "has nothingness"
                   |    },
                   |    "rdfs:subPropertyOf" : {
                   |      "@id" : "knora-api:hasValue"
                   |    }
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

            // Convert the submitted JSON-LD to an InputOntologyV2, without SPARQL-escaping, so we can compare it to the response.
            val paramsAsInput: InputOntologyV2 = InputOntologyV2.fromJsonLD(JsonLDUtil.parseJsonLD(params)).unescape

            Post("/v2/ontologies/properties", HttpEntity(ContentTypes.`application/json`, params)) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> ontologiesPath ~> check {
                assert(status == StatusCodes.OK, response.toString)
                val responseJsonDoc = responseToJsonLDDocument(response)

                // Comvert the response to an InputOntologyV2 and compare the relevant part of it to the request.
                val responseAsInput: InputOntologyV2 = InputOntologyV2.fromJsonLD(responseJsonDoc, ignoreExtraData = true).unescape
                responseAsInput.properties should ===(paramsAsInput.properties)

                // Check that the ontology's last modification date was updated.
                val newAnythingLastModDate = responseAsInput.ontologyMetadata.lastModificationDate.get
                assert(newAnythingLastModDate.isAfter(anythingLastModDate))
                anythingLastModDate = newAnythingLastModDate
            }
        }

        "add a cardinality for the property anything:hasNothingness to the class anything:Nothing" in {
            val params =
                s"""
                   |{
                   |  "@id" : "http://0.0.0.0:3333/ontology/0001/anything/v2",
                   |  "@type" : "owl:Ontology",
                   |  "knora-api:lastModificationDate" : "$anythingLastModDate",
                   |  "@graph" : [ {
                   |    "@id" : "anything:Nothing",
                   |    "@type" : "owl:Class",
                   |    "rdfs:subClassOf" : {
                   |      "@type": "owl:Restriction",
                   |      "owl:maxCardinality" : 1,
                   |      "owl:onProperty" : {
                   |        "@id" : "anything:hasNothingness"
                   |      }
                   |    }
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

            // Convert the submitted JSON-LD to an InputOntologyV2, without SPARQL-escaping, so we can compare it to the response.
            val paramsAsInput: InputOntologyV2 = InputOntologyV2.fromJsonLD(JsonLDUtil.parseJsonLD(params)).unescape

            Post("/v2/ontologies/cardinalities", HttpEntity(ContentTypes.`application/json`, params)) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> ontologiesPath ~> check {
                assert(status == StatusCodes.OK, response.toString)
                val responseJsonDoc = responseToJsonLDDocument(response)

                // Convert the response to an InputOntologyV2 and compare the relevant part of it to the request.
                val responseAsInput: InputOntologyV2 = InputOntologyV2.fromJsonLD(responseJsonDoc, ignoreExtraData = true).unescape
                responseAsInput.classes.head._2.directCardinalities should ===(paramsAsInput.classes.head._2.directCardinalities)

                // Check that cardinalities were inherited from knora-api:Resource.
                getPropertyIrisFromResourceClassResponse(responseJsonDoc) should ===(expectedProperties)

                // Check that the ontology's last modification date was updated.
                val newAnythingLastModDate = responseAsInput.ontologyMetadata.lastModificationDate.get
                assert(newAnythingLastModDate.isAfter(anythingLastModDate))
                anythingLastModDate = newAnythingLastModDate
            }
        }

        "create a property anything:hasEmptiness with knora-api:subjectType anything:Nothing" in {
            val params =
                s"""
                   |{
                   |  "@id" : "http://0.0.0.0:3333/ontology/0001/anything/v2",
                   |  "@type" : "owl:Ontology",
                   |  "knora-api:lastModificationDate" : "$anythingLastModDate",
                   |  "@graph" : [ {
                   |    "@id" : "anything:hasEmptiness",
                   |    "@type" : "owl:ObjectProperty",
                   |    "knora-api:subjectType" : {
                   |      "@id" : "anything:Nothing"
                   |    },
                   |    "knora-api:objectType" : {
                   |      "@id" : "knora-api:BooleanValue"
                   |    },
                   |    "rdfs:comment" : {
                   |      "@language" : "en",
                   |      "@value" : "Indicates whether a Nothing has emptiness"
                   |    },
                   |    "rdfs:label" : {
                   |      "@language" : "en",
                   |      "@value" : "has emptiness"
                   |    },
                   |    "rdfs:subPropertyOf" : {
                   |      "@id" : "knora-api:hasValue"
                   |    }
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

            // Convert the submitted JSON-LD to an InputOntologyV2, without SPARQL-escaping, so we can compare it to the response.
            val paramsAsInput: InputOntologyV2 = InputOntologyV2.fromJsonLD(JsonLDUtil.parseJsonLD(params)).unescape

            Post("/v2/ontologies/properties", HttpEntity(ContentTypes.`application/json`, params)) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> ontologiesPath ~> check {
                assert(status == StatusCodes.OK, response.toString)
                val responseJsonDoc = responseToJsonLDDocument(response)

                // Comvert the response to an InputOntologyV2 and compare the relevant part of it to the request.
                val responseAsInput: InputOntologyV2 = InputOntologyV2.fromJsonLD(responseJsonDoc, ignoreExtraData = true).unescape
                responseAsInput.properties should ===(paramsAsInput.properties)

                // Check that the ontology's last modification date was updated.
                val newAnythingLastModDate = responseAsInput.ontologyMetadata.lastModificationDate.get
                assert(newAnythingLastModDate.isAfter(anythingLastModDate))
                anythingLastModDate = newAnythingLastModDate
            }
        }

        "change the cardinalities of the class anything:Nothing, replacing anything:hasNothingness with anything:hasEmptiness" in {
            val params =
                s"""
                   |{
                   |  "@id" : "http://0.0.0.0:3333/ontology/0001/anything/v2",
                   |  "@type" : "owl:Ontology",
                   |  "knora-api:lastModificationDate" : "$anythingLastModDate",
                   |  "@graph" : [ {
                   |    "@id" : "anything:Nothing",
                   |    "@type" : "owl:Class",
                   |    "rdfs:subClassOf" : {
                   |      "@type": "owl:Restriction",
                   |      "owl:maxCardinality": 1,
                   |      "owl:onProperty": {
                   |        "@id" : "anything:hasEmptiness"
                   |      }
                   |    }
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

            // Convert the submitted JSON-LD to an InputOntologyV2, without SPARQL-escaping, so we can compare it to the response.
            val paramsAsInput: InputOntologyV2 = InputOntologyV2.fromJsonLD(JsonLDUtil.parseJsonLD(params)).unescape

            Put("/v2/ontologies/cardinalities", HttpEntity(ContentTypes.`application/json`, params)) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> ontologiesPath ~> check {
                assert(status == StatusCodes.OK, response.toString)
                val responseJsonDoc = responseToJsonLDDocument(response)

                // Convert the response to an InputOntologyV2 and compare the relevant part of it to the request.
                val responseAsInput: InputOntologyV2 = InputOntologyV2.fromJsonLD(responseJsonDoc, ignoreExtraData = true).unescape
                responseAsInput.classes.head._2.directCardinalities should ===(paramsAsInput.classes.head._2.directCardinalities)

                // Check that cardinalities were inherited from knora-api:Resource.
                getPropertyIrisFromResourceClassResponse(responseJsonDoc) should ===(expectedProperties)

                // Check that the ontology's last modification date was updated.
                val newAnythingLastModDate = responseAsInput.ontologyMetadata.lastModificationDate.get
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
                responseJsonDoc.requireString("@id", stringFormatter.toSmartIriWithErr) should ===("http://0.0.0.0:3333/ontology/0001/anything/v2".toSmartIri)
                val newAnythingLastModDate = responseJsonDoc.requireString(OntologyConstants.KnoraApiV2WithValueObjects.LastModificationDate, stringFormatter.toInstant)
                assert(newAnythingLastModDate.isAfter(anythingLastModDate))
                anythingLastModDate = newAnythingLastModDate
            }
        }

        "remove all cardinalities from the class anything:Nothing" in {
            val params =
                s"""
                   |{
                   |  "@id" : "http://0.0.0.0:3333/ontology/0001/anything/v2",
                   |  "@type" : "owl:Ontology",
                   |  "knora-api:lastModificationDate" : "$anythingLastModDate" ,
                   |  "@graph" : [ {
                   |    "@id" : "anything:Nothing",
                   |    "@type" : "owl:Class"
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

                // Convert the response to an InputOntologyV2 and compare the relevant part of it to the request.
                val responseAsInput: InputOntologyV2 = InputOntologyV2.fromJsonLD(responseJsonDoc, ignoreExtraData = true).unescape
                responseAsInput.classes.head._2.directCardinalities.isEmpty should ===(true)

                // Check that cardinalities were inherited from knora-api:Resource.
                getPropertyIrisFromResourceClassResponse(responseJsonDoc) should ===(expectedProperties)

                // Check that the ontology's last modification date was updated.
                val newAnythingLastModDate = responseAsInput.ontologyMetadata.lastModificationDate.get
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
                responseJsonDoc.requireString("@id", stringFormatter.toSmartIriWithErr) should ===("http://0.0.0.0:3333/ontology/0001/anything/v2".toSmartIri)
                val newAnythingLastModDate = responseJsonDoc.requireString(OntologyConstants.KnoraApiV2WithValueObjects.LastModificationDate, stringFormatter.toInstant)
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
                responseJsonDoc.requireString("@id", stringFormatter.toSmartIriWithErr) should ===("http://0.0.0.0:3333/ontology/0001/anything/v2".toSmartIri)
                val newAnythingLastModDate = responseJsonDoc.requireString(OntologyConstants.KnoraApiV2WithValueObjects.LastModificationDate, stringFormatter.toInstant)
                assert(newAnythingLastModDate.isAfter(anythingLastModDate))
                anythingLastModDate = newAnythingLastModDate
            }
        }
    }
}