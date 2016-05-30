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

package org.knora.webapi.routing

import org.knora.webapi.{KnoraService, R2RSpec, StartupFlags}
import spray.httpx.RequestBuilding


/**
  * End-to-End (E2E) test specification for testing authentication.
  *
  * This spec tests the 'v1/authentication' and 'v1/session' route.
  */
class AuthenticationV1E2ESpec extends R2RSpec with RequestBuilding {

    /* Set the startup flags and start the Knora Server */
    StartupFlags.loadDemoData send true
    KnoraService.start

    val rdfDataObjectsJsonList =
        """
            [
                {"path": "../knora-ontologies/knora-base.ttl", "name": "http://www.knora.org/ontology/knora-base"},
                {"path": "../knora-ontologies/knora-dc.ttl", "name": "http://www.knora.org/ontology/dc"},
                {"path": "../knora-ontologies/salsah-gui.ttl", "name": "http://www.knora.org/ontology/salsah-gui"},
                {"path": "_test_data/ontologies/incunabula-onto.ttl", "name": "http://www.knora.org/ontology/incunabula"},
                {"path": "_test_data/all_data/incunabula-data.ttl", "name": "http://www.knora.org/data/incunabula"},
                {"path": "_test_data/all_data/admin-data.ttl", "name": "http://www.knora.org/data/admin"}
            ]
        """

    "Load test data" in {
        // send POST to 'v1/store/ResetTriplestoreContent'
    }

    // TODO: Rewrite to only use HTTP requests
    /*
    "The Authentication Route ('v1/authenticate') when accessed with credentials supplied via URL parameters " should {
        "succeed with authentication and correct username / correct password " in {
            /* Correct username and password */
            Get("/v1/authenticate?username=root&password=test") ~> authenticatePath ~> check {
                //log.debug("==>> " + responseAs[String])
                assert(status === StatusCodes.OK)
            }
        }
        "fail with authentication and correct username / wrong password " in {
            /* Correct username / wrong password */
            Get("/v1/authenticate?username=root&password=wrong") ~> authenticatePath ~> check {
                //log.debug("==>> " + responseAs[String])
                assert(status === StatusCodes.Unauthorized)
            }
        }
        "fail with authentication if the user is set as 'not active' " in {
            /* User not active */
            Get("/v1/authenticate?username=inactiveuser&password=test") ~> authenticatePath ~> check {
                //log.debug("==>> " + responseAs[String])
                assert(status === StatusCodes.Unauthorized)
            }
        }
    }
    "The Authentication Route ('v1/authenticate') when accessed with credentials supplied via Basic Auth " should {
        "succeed with authentication and correct username / correct password " in {
            /* Correct username / correct password */
            Get("/v1/authenticate") ~> addCredentials(BasicHttpCredentials("root", "test")) ~> authenticatePath ~> check {
                //log.debug("==>> " + responseAs[String])
                assert(status === StatusCodes.OK)
            }
        }
        "fail with authentication and correct username / wrong password " in {
            /* Correct username / wrong password */
            Get("/v1/authenticate") ~> addCredentials(BasicHttpCredentials("root", "wrong")) ~> authenticatePath ~> check {
                //log.debug("==>> " + responseAs[String])
                assert(status === StatusCodes.Unauthorized)
            }
        }
    }
    "The Session Route ('v1/session') when accessed with credentials supplied via URL parameters " should {
        var sid = ""
        "succeed with 'login' and correct username / correct password " in {
            /* Correct username and correct password */
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
    "The Session Route ('v1/session') when accessed with credentials supplied via Basic Auth " should {
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
    "The Resources Route using the Authenticator trait when accessed " should {
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
