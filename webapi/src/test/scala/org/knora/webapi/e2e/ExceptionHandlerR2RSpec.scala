/*
 * Copyright © 2015-2018 the contributors (see Contributors.md).
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

    private val nfe = path( ("v1" | "v2" | "admin") / "nfe") {
        get {
            throw NotFoundException("not found")
        }
    }

    private val fe = path( (("v1" | "v2") | "admin") / "fe") {
        get {
            throw ForbiddenException("forbidden")
        }
    }

    private val bce = path( ("v1" | "v2" | "admin") / "bce") {
        get {
            throw BadCredentialsException("bad credentials")
        }
    }

    private val dve = path( ("v1" | "v2" | "admin") / "dve") {
        get {
            throw DuplicateValueException("duplicate value")
        }
    }

    private val oce = path( ("v1" | "v2" | "admin") / "oce") {
        get {
            throw OntologyConstraintException("ontology constraint")
        }
    }

    private val ece = path( ("v1" | "v2" | "admin") / "ece") {
        get {
            throw EditConflictException("edit conflict")
        }
    }

    private val sse = path( ("v1" | "v2" | "admin") / "sse") {
        get {
            throw GravsearchException("sparql search")
        }
    }

    private val unpe = path( ("v1" | "v2" | "admin") / "unpe") {
        get {
            throw UpdateNotPerformedException("update not performed")
        }
    }

    private val ae = path( ("v1" | "v2" | "admin") / "ae") {
        get {
            throw AuthenticationException("authentication exception")
        }
    }

    private val route: Route = Route.seal(handleExceptions(KnoraExceptionHandler(settings, log)) {
        nfe ~ fe ~ bce ~ dve ~ oce ~ ece ~ sse ~ unpe ~ ae
    })

    "The Exception Handler" should {

        "return correct status code and response for 'NotFoundException'" in {
            Get("/v1/nfe") ~> route ~> check {

                //println(responseAs[String])

                status should be(StatusCodes.NotFound)

                responseAs[String] should be("{\"status\":9,\"error\":\"org.knora.webapi.NotFoundException: not found\"}")
            }

            Get("/v2/nfe") ~> route ~> check {

                //println(responseAs[String])

                status should be(StatusCodes.NotFound)

                responseAs[String] should be("{\"error\":\"org.knora.webapi.NotFoundException: not found\"}")
            }

            Get("/admin/nfe") ~> route ~> check {

                //println(responseAs[String])

                status should be(StatusCodes.NotFound)

                responseAs[String] should be("{\"error\":\"org.knora.webapi.NotFoundException: not found\"}")
            }
        }


        "return correct status code and response for 'ForbiddenException'" in {
            Get("/v1/fe") ~> route ~> check {

                status should be(StatusCodes.Forbidden)

                responseAs[String] should be("{\"status\":3,\"error\":\"org.knora.webapi.ForbiddenException: forbidden\",\"access\":\"NO_ACCESS\"}")
            }

            Get("/v2/fe") ~> route ~> check {

                status should be(StatusCodes.Forbidden)

                responseAs[String] should be("{\"error\":\"org.knora.webapi.ForbiddenException: forbidden\"}")
            }

            Get("/admin/fe") ~> route ~> check {

                status should be(StatusCodes.Forbidden)

                responseAs[String] should be("{\"error\":\"org.knora.webapi.ForbiddenException: forbidden\"}")
            }
        }

        "return correct status code and response for 'DuplicateValueException'" in {
            Get("/v1/dve") ~> route ~> check {

                status should be(StatusCodes.BadRequest)

                responseAs[String] should be("{\"status\":28,\"error\":\"org.knora.webapi.DuplicateValueException: duplicate value\"}")
            }

            Get("/v2/dve") ~> route ~> check {

                status should be(StatusCodes.BadRequest)

                responseAs[String] should be("{\"error\":\"org.knora.webapi.DuplicateValueException: duplicate value\"}")
            }

            Get("/admin/dve") ~> route ~> check {

                status should be(StatusCodes.BadRequest)

                responseAs[String] should be("{\"error\":\"org.knora.webapi.DuplicateValueException: duplicate value\"}")
            }
        }

        "return correct status code and response for 'OntologyConstraintException'" in {
            Get("/v1/oce") ~> route ~> check {

                status should be(StatusCodes.BadRequest)

                responseAs[String] should be("{\"status\":29,\"error\":\"org.knora.webapi.OntologyConstraintException: ontology constraint\"}")
            }

            Get("/v2/oce") ~> route ~> check {

                status should be(StatusCodes.BadRequest)

                responseAs[String] should be("{\"error\":\"org.knora.webapi.OntologyConstraintException: ontology constraint\"}")
            }

            Get("/admin/oce") ~> route ~> check {

                status should be(StatusCodes.BadRequest)

                responseAs[String] should be("{\"error\":\"org.knora.webapi.OntologyConstraintException: ontology constraint\"}")
            }
        }

        "return correct status code and response for 'EditConflictException'" in {
            Get("/v1/ece") ~> route ~> check {

                status should be(StatusCodes.Conflict)

                responseAs[String] should be("{\"status\":27,\"error\":\"org.knora.webapi.EditConflictException: edit conflict\"}")
            }

            Get("/v2/ece") ~> route ~> check {

                status should be(StatusCodes.Conflict)

                responseAs[String] should be("{\"error\":\"org.knora.webapi.EditConflictException: edit conflict\"}")
            }

            Get("/admin/ece") ~> route ~> check {

                status should be(StatusCodes.Conflict)

                responseAs[String] should be("{\"error\":\"org.knora.webapi.EditConflictException: edit conflict\"}")
            }
        }

        "return correct status code and response for 'GravsearchException'" in {
            Get("/v1/sse") ~> route ~> check {

                status should be(StatusCodes.BadRequest)

                responseAs[String] should be("{\"status\":11,\"error\":\"org.knora.webapi.GravsearchException: sparql search\"}")
            }

            Get("/v2/sse") ~> route ~> check {

                status should be(StatusCodes.BadRequest)

                responseAs[String] should be("{\"error\":\"org.knora.webapi.GravsearchException: sparql search\"}")
            }

            Get("/admin/sse") ~> route ~> check {

                status should be(StatusCodes.BadRequest)

                responseAs[String] should be("{\"error\":\"org.knora.webapi.GravsearchException: sparql search\"}")
            }
        }

        "return correct status code and response for 'UpdateNotPerformedException'" in {
            Get("/v1/unpe") ~> route ~> check {

                status should be(StatusCodes.Conflict)

                responseAs[String] should be("{\"status\":27,\"error\":\"org.knora.webapi.UpdateNotPerformedException: update not performed\"}")
            }

            Get("/v2/unpe") ~> route ~> check {

                status should be(StatusCodes.Conflict)

                responseAs[String] should be("{\"error\":\"org.knora.webapi.UpdateNotPerformedException: update not performed\"}")
            }

            Get("/admin/unpe") ~> route ~> check {

                status should be(StatusCodes.Conflict)

                responseAs[String] should be("{\"error\":\"org.knora.webapi.UpdateNotPerformedException: update not performed\"}")
            }
        }

        "return correct status code and response for 'AuthenticationException'" in {
            Get("/v1/ae") ~> route ~> check {

                status should be(StatusCodes.InternalServerError)

                responseAs[String] should be("{\"status\":4,\"error\":\"org.knora.webapi.AuthenticationException: authentication exception\"}")
            }

            Get("/v2/ae") ~> route ~> check {

                status should be(StatusCodes.InternalServerError)

                responseAs[String] should be("{\"error\":\"org.knora.webapi.AuthenticationException: authentication exception\"}")
            }

            Get("/admin/ae") ~> route ~> check {

                status should be(StatusCodes.InternalServerError)

                responseAs[String] should be("{\"error\":\"org.knora.webapi.AuthenticationException: authentication exception\"}")
            }
        }
    }
}
