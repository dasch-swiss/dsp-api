/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and André Fatton.
 * This file is part of Knora.
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.e2e.v1

import com.typesafe.config.ConfigFactory
import org.knora.webapi.E2ESpec
import org.knora.webapi.messages.v1.store.triplestoremessages.RdfDataObject
import spray.client.pipelining._
import spray.http.{BasicHttpCredentials, HttpResponse, StatusCodes}
import spray.httpx.SprayJsonSupport._

import scala.concurrent.Await
import scala.concurrent.duration._

object AuthenticationV1E2ESpec {
    val config = ConfigFactory.parseString(
        """
          akka.loglevel = "DEBUG"
          akka.stdout-loglevel = "DEBUG"
        """.stripMargin)
}

/**
  * End-to-End (E2E) test specification for testing authentication.
  *
  * This spec tests the 'v1/authentication' and 'v1/session' route.
  */
class AuthenticationV1E2ESpec extends E2ESpec(AuthenticationV1E2ESpec.config) {

    import org.knora.webapi.messages.v1.store.triplestoremessages.TriplestoreJsonProtocol._

    val rdfDataObjects = List(
        RdfDataObject(path = "../knora-ontologies/knora-base.ttl", name = "http://www.knora.org/ontology/knora-base"),
        RdfDataObject(path = "../knora-ontologies/knora-dc.ttl", name = "http://www.knora.org/ontology/dc"),
        RdfDataObject(path = "../knora-ontologies/salsah-gui.ttl", name = "http://www.knora.org/ontology/salsah-gui"),
        RdfDataObject(path = "_test_data/ontologies/incunabula-onto.ttl", name = "http://www.knora.org/ontology/incunabula"),
        RdfDataObject(path = "_test_data/all_data/incunabula-data.ttl", name = "http://www.knora.org/data/incunabula"),
        RdfDataObject(path = "_test_data/ontologies/images-demo-onto.ttl", name = "http://www.knora.org/ontology/images"),
        RdfDataObject(path = "_test_data/demo_data/images-demo-data.ttl", name = "http://www.knora.org/data/images")
    )

    "Load test data" ignore {
        // send POST to 'v1/store/ResetTriplestoreContent'
        Await.result(pipe(Post(s"${baseApiUrl}v1/store/ResetTriplestoreContent", rdfDataObjects)), 300 seconds)
    }

    "The Authentication Route ('v1/authenticate') with credentials supplied via URL parameters " should {
        "succeed with authentication and correct username / correct password " ignore {
            /* Correct username and password */
            val response: HttpResponse = Await.result(pipe(Get(s"${baseApiUrl}v1/authenticate?username=root&password=test")), 3 seconds)
            log.debug(s"response: ${response.toString}")
            assert(response.status === StatusCodes.OK)
        }
        "fail with authentication and correct username / wrong password " ignore {
            /* Correct username / wrong password */
            val response: HttpResponse = Await.result(pipe(Get(s"${baseApiUrl}v1/authenticate?username=root&password=wrong")), 3 seconds)
            log.debug(s"response: ${response.toString}")
            assert(response.status === StatusCodes.Unauthorized)
        }
        "fail with authentication if the user is set as 'not active' " ignore {
            /* User not active */
            val response: HttpResponse = Await.result(pipe(Get(s"${baseApiUrl}v1/authenticate?username=inactiveuser&password=test")), 3 seconds)
            log.debug(s"response: ${response.toString}")
            assert(response.status === StatusCodes.Unauthorized)
        }
    }

    "The Authentication Route ('v1/authenticate') with credentials supplied via Basic Auth " should {
        "succeed with authentication and correct username / correct password " ignore {
            /* Correct username / correct password */
            val request = Get(s"${baseApiUrl}v1/authenticate") ~> addCredentials(BasicHttpCredentials("root", "test"))
            val response = Await.result(pipe(request), 3 seconds)
            assert(response.status == StatusCodes.OK)
        }
        "fail with authentication and correct username / wrong password " ignore {
            /* Correct username / wrong password */
            val request = Get(s"${baseApiUrl}v1/authenticate") ~> addCredentials(BasicHttpCredentials("root", "wrong"))
            val response = Await.result(pipe(request), 3 seconds)
            assert(response.status == StatusCodes.Unauthorized)
        }
    }

    // TODO: Rewrite to only use HTTP requests
    /*
    "The Session Route ('v1/session') with credentials supplied via URL parameters " should {
        var sid = ""
        "succeed with 'login' and correct username / correct password " in {
            /* Correct username and correct password */
            val request = Get(s"${baseApiUrl}v1/session?login&username=root&password=test"))
            val response = Await.result(pipe(request), 3 seconds)
            assert(response.status == StatusCodes.OK)

            /* store session */
            sid = response. responseAs[SessionResponse].sid
            assert(header[`Set-Cookie`] === Some(`Set-Cookie`(HttpCookie(KNORA_AUTHENTICATION_COOKIE_NAME, content = sid))))

            Get("/v1/session?login&username=root&password=test") ~> authenticatePath ~> check {
                //log.debug("==>> " + responseAs[String])
                assert(status === StatusCodes.OK)
                /* store session */
                sid = responseAs[SessionResponse].sid
                assert(header[`Set-Cookie`] === Some(`Set-Cookie`(HttpCookie(KNORA_AUTHENTICATION_COOKIE_NAME, content = sid))))
            }
        }

        "succeed with authentication when using correct session id in cookie" in {
            // authenticate by calling '/v2/session' without parameters but by providing session id in cookie from earlier login
            Get("/v1/session") ~> Cookie(HttpCookie(KNORA_AUTHENTICATION_COOKIE_NAME, sid)) ~> authenticatePath ~> check {
                //log.debug("==>> " + responseAs[String])
                assert(status === StatusCodes.OK)
            }
        }

        "succeed with 'logout' when providing the session cookie " in {
            // do logout with stored session id
            Get("/v1/session?logout") ~> Cookie(HttpCookie(KNORA_AUTHENTICATION_COOKIE_NAME, sid)) ~> authenticatePath ~> check {
                //log.debug("==>> " + responseAs[String])
                assert(status === StatusCodes.OK)
                assert(header[`Set-Cookie`] === Some(`Set-Cookie`(HttpCookie(KNORA_AUTHENTICATION_COOKIE_NAME, "deleted", expires = Some(DateTime(1970, 1, 1, 0, 0, 0))))))
            }
        }

        "fail with authentication when providing the session cookie after logout" in {
            Get("/v1/session") ~> Cookie(HttpCookie(KNORA_AUTHENTICATION_COOKIE_NAME, sid)) ~> authenticatePath ~> check {
                //log.debug("==>> " + responseAs[String])
                assert(status === StatusCodes.Unauthorized)
            }
        }

        "fail with 'login' and correct username / wrong password " in {
            /* Correct username and wrong password */
            Get("/v1/session?login&username=root&password=wrong") ~> authenticatePath ~> check {
                //log.debug("==>> " + responseAs[String])
                assert(status === StatusCodes.Unauthorized)
            }
        }

        "fail with 'login' and wrong username " in {
            /* wrong username */
            Get("/v1/session?login&username=wrong&password=test") ~> authenticatePath ~> check {
                //log.debug("==>> " + responseAs[String])
                assert(status === StatusCodes.Unauthorized)
            }
        }

        "fail with authentication when using wrong session id in cookie " in {
            Get("/v1/session") ~> Cookie(HttpCookie(KNORA_AUTHENTICATION_COOKIE_NAME, "123456")) ~> authenticatePath ~> check {
                //log.debug("==>> " + responseAs[String])
                assert(status === StatusCodes.Unauthorized)
            }
        }
    }

    "The Session Route ('v1/session') with credentials supplied via Basic Auth " should {
        "succeed with 'login' and correct username / correct password " in {
            /* Correct username and correct password */
            Get("/v1/session?login") ~> addCredentials(BasicHttpCredentials("root", "test")) ~> authenticatePath ~> check {
                //log.debug("==>> " + responseAs[String])
                assert(status === StatusCodes.OK)
            }
        }

        "fail with 'login' and correct username / wrong password " in {
            /* Correct username and wrong password */
            Get("/v1/session?login") ~> addCredentials(BasicHttpCredentials("root", "wrong")) ~> authenticatePath ~> check {
                //log.debug("==>> " + responseAs[String])
                assert(status === StatusCodes.Unauthorized)
            }
        }

        "fail with 'login' and wrong username " in {
            /* wrong username */
            Get("/v1/session?logint") ~> addCredentials(BasicHttpCredentials("root", "test")) ~> authenticatePath ~> check {
                //log.debug("==>> " + responseAs[String])
                assert(status === StatusCodes.Unauthorized)
            }
        }
    }

    "The Resources Route using the Authenticator trait " should {
        "succeed with authentication using URL parameters and correct username / correct password " in {
            /* Correct username / correct password */
            Get("/v1/resources/http%3A%2F%2Fdata.knora.org%2Fc5058f3a?username=root&password=test") ~> resourcesPath ~> check {
                //log.debug("==>> " + responseAs[String])
                assert(status === StatusCodes.OK)
            }
        }

        "fail with authentication using URL parameters and correct username / wrong password " in {
            /* Correct username / wrong password */
            Get("/v1/resources/http%3A%2F%2Fdata.knora.org%2Fc5058f3a?username=root&password=wrong") ~> resourcesPath ~> check {
                //log.debug("==>> " + responseAs[String])
                assert(status === StatusCodes.Unauthorized)
            }
        }

        "succeed with authentication using HTTP Basic Auth headers and correct username / correct password " in {
            /* Correct username / correct password */
            Get("/v1/resources/http%3A%2F%2Fdata.knora.org%2Fc5058f3a") ~> addCredentials(BasicHttpCredentials("root", "test")) ~> resourcesPath ~> check {
                //log.debug("==>> " + responseAs[String])
                assert(status === StatusCodes.OK)
            }
        }

        "fail with authentication using HTTP Basic Auth headers and correct username / wrong password " in {
            /* Correct username / wrong password */
            Get("/v1/resources/http%3A%2F%2Fdata.knora.org%2Fc5058f3a") ~> addCredentials(BasicHttpCredentials("root", "wrong")) ~> resourcesPath ~> check {
                //log.debug("==>> " + responseAs[String])
                assert(status === StatusCodes.Unauthorized)
            }
        }

        "not return sensitive information (token, password) in the response " in {
            Get("/v1/resources/http%3A%2F%2Fdata.knora.org%2Fc5058f3a?username=root&password=test") ~> resourcesPath ~> check {
                //log.debug("==>> " + responseAs[String])
                // assert(status === StatusCodes.OK)
                assert(responseAs[String] contains "\"password\":null")
                assert(responseAs[String] contains "\"token\":null")
            }
        }
    }
    */
}
