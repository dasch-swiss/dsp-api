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

package org.knora.webapi.e2e

import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives.{get, path, _}
import akka.http.scaladsl.server.Route
import org.knora.webapi._


/**
  * Route (R2R) test specification for testing exception handling.
  */
class ExceptionHandlerR2RSpec extends R2RSpec {



    // NotFoundException(_)
    // ForbiddenException(_)
    // BadCredentialsException(_)
    // DuplicateValueException(_)
    // OntologyConstraintException(_)
    // EditConflictException(_)
    // RequestRejectedException(_)
    // UpdateNotPerformedException(_)
    // InternalServerException(_)



    val nfe = path("v1" / "nfe") {
        get {
            throw NotFoundException("not found")
        }
    }

    val fe = path("v1" | "v2" | "admin" / "fe") {
        get {
            throw ForbiddenException("forbidden")
        }
    }

    val bce = path("v1" | "v2" | "admin" / "bce") {
        get {
            throw BadCredentialsException("bad credentials")
        }
    }

    val dve = path("v1" | "v2" | "admin" / "dve") {
        get {
            throw DuplicateValueException("duplicate value")
        }
    }

    val oce = path("v1" | "v2" | "admin" / "oce") {
        get {
            throw OntologyConstraintException("ontology constraint")
        }
    }

    val ece = path("v1" | "v2" | "admin" / "ece") {
        get {
            throw EditConflictException("edit conflict")
        }
    }

    val sse = path("v1" | "v2" | "admin" / "sse") {
        get {
            throw SparqlSearchException("sparql search")
        }
    }

    val unpe = path("v1" | "v2" | "admin" / "unpe") {
        get {
            throw UpdateNotPerformedException("update not performed")
        }
    }

    val ae = path("v1" | "v2" | "admin" / "ae") {
        get {
            throw AuthenticationException("authentication exception")
        }
    }

    val route: Route = Route.seal(handleExceptions(KnoraExceptionHandler(settings, log)) {
        nfe ~ fe ~ bce ~ dve ~ oce ~ ece ~ sse ~ unpe ~ ae
    })

    "The Exception Handler" should {

        "return an nfe exception for v1" in {
            Get("/v1/nfe") ~> route ~> check {

                println(responseAs[String])

                status should be(StatusCodes.NotFound)

                responseAs[String] should be("{\"status\":9,\"error\":\"org.knora.webapi.NotFoundException: not found\"}")
            }
        }

        "return an nfe exception for v2" in {
            Get("/v2/nfe") ~> route ~> check {

                println(responseAs[String])

                status should be(StatusCodes.NotFound)

                responseAs[String] should be("{\"error\":\"org.knora.webapi.NotFoundException: not found\"}")
            }
        }

        "akka path matcher pipe combinator bug (1)" in {

            val works = path("v1" / "fu") {
                complete("fu")
            } ~ path("v2" / "fu") {
                complete("fu")
            }

            Get("/v1/fu") ~> works ~> check {
                responseAs[String] should be("fu")
            }

            Get("/v2/fu") ~> works ~> check {
                responseAs[String] should be("fu")
            }

        }

        "akka path matcher pipe combinator bug (2)" in {

            val nope = path("v1" | "v2" / "fu") {
                complete("fu")
            }

            Get ("/v1/fu") ~> nope ~> check {
                println(responseAs[String])
                responseAs[String] should be ("fu")
            }

            Get ("/v2/fu") ~> nope ~> check {
                println(responseAs[String])
                responseAs[String] should be ("fu")
            }

        }
    }
}
