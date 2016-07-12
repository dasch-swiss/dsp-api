package org.knora.webapi.e2e.v1

import java.net.URLEncoder

import akka.actor.{ActorSystem, Props}
import akka.pattern._
import akka.util.Timeout
import org.knora.webapi.IRI
import org.knora.webapi.LiveActorMaker
import org.knora.webapi.e2e.E2ESpec
import org.knora.webapi.messages.v1.store.triplestoremessages.{RdfDataObject, ResetTriplestoreContent}
import org.knora.webapi.responders._
import org.knora.webapi.responders.v1.ResponderManagerV1
import org.knora.webapi.routing.v1.ValuesRouteV1
import org.knora.webapi.store._
import org.knora.webapi.util.MutableTestIri
import spray.http.MediaTypes._
import spray.http._
import spray.json._

import scala.concurrent.Await
import scala.concurrent.duration._

/**
  * Created by benjamingeer on 12/07/16.
  */
class ValuesV1E2ESpec extends E2ESpec {

    override def testConfigSource =
        """
         # akka.loglevel = "DEBUG"
         # akka.stdout-loglevel = "DEBUG"
        """.stripMargin

    val responderManager = system.actorOf(Props(new ResponderManagerV1 with LiveActorMaker), name = RESPONDER_MANAGER_ACTOR_NAME)
    val storeManager = system.actorOf(Props(new StoreManager with LiveActorMaker), name = STORE_MANAGER_ACTOR_NAME)

    val valuesPath = ValuesRouteV1.knoraApiPath(system, settings, log)

    implicit val timeout: Timeout = 300.seconds

    implicit def default(implicit system: ActorSystem) = RouteTestTimeout(new DurationInt(15).second)

    private val integerValueIri = new MutableTestIri

    val rdfDataObjects = List(
        RdfDataObject(path = "../knora-ontologies/knora-base.ttl", name = "http://www.knora.org/ontology/knora-base"),
        RdfDataObject(path = "../knora-ontologies/knora-dc.ttl", name = "http://www.knora.org/ontology/dc"),
        RdfDataObject(path = "../knora-ontologies/salsah-gui.ttl", name = "http://www.knora.org/ontology/salsah-gui"),
        RdfDataObject(path = "_test_data/ontologies/anything-onto.ttl", name = "http://www.knora.org/ontology/anything"),
        RdfDataObject(path = "_test_data/all_data/anything-data.ttl", name = "http://www.knora.org/data/anything")
    )

    "Load test data" in {
        Await.result(storeManager ? ResetTriplestoreContent(rdfDataObjects), 300.seconds)
    }

    "The Values Endpoint" should {
        "add a value to a resource" in {
            val params =
                """
                  |{
                  |    "project_id": "http://data.knora.org/projects/anything",
                  |    "res_id": "http://data.knora.org/a-thing",
                  |    "prop": "http://www.knora.org/ontology/anything#hasInteger",
                  |    "int_value": 1234
                  |}
                """.stripMargin

            Post("/v1/values", HttpEntity(`application/json`, params)) ~> addCredentials(BasicHttpCredentials("anything-user", "test")) ~> valuesPath ~> check {
                assert(status == StatusCodes.OK, response.toString)
                val responseJson: Map[String, JsValue] = responseAs[String].parseJson.asJsObject.fields
                val valueIri: IRI = responseJson("id").asInstanceOf[JsString].value
                integerValueIri.set(valueIri)
            }
        }

        "mark a value as deleted" in {
            Delete(s"/v1/values/${URLEncoder.encode(integerValueIri.get, "UTF-8")}?deleteComment=deleted%20for%20testing") ~> addCredentials(BasicHttpCredentials("anything-user", "test")) ~> valuesPath ~> check {
                assert(status == StatusCodes.OK, response.toString)
            }
        }
    }
}
