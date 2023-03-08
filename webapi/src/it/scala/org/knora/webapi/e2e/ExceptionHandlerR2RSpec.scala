/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.e2e

import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Directives.get
import akka.http.scaladsl.server.Directives.path
import akka.http.scaladsl.server.Route

import dsp.errors._
import org.knora.webapi._
import org.knora.webapi.http.handler

/**
 * Route (R2R) test specification for testing exception handling.
 */
class ExceptionHandlerR2RSpec extends R2RSpec {

  private val nfe = path(("v1" | "v2" | "admin") / "nfe") {
    get {
      throw NotFoundException("not found")
    }
  }

  private val fe = path((("v1" | "v2") | "admin") / "fe") {
    get {
      throw ForbiddenException("forbidden")
    }
  }

  private val bce = path(("v1" | "v2" | "admin") / "bce") {
    get {
      throw BadCredentialsException("bad credentials")
    }
  }

  private val dve = path(("v1" | "v2" | "admin") / "dve") {
    get {
      throw DuplicateValueException("duplicate value")
    }
  }

  private val oce = path(("v1" | "v2" | "admin") / "oce") {
    get {
      throw OntologyConstraintException("ontology constraint")
    }
  }

  private val ece = path(("v1" | "v2" | "admin") / "ece") {
    get {
      throw EditConflictException("edit conflict")
    }
  }

  private val sse = path(("v1" | "v2" | "admin") / "sse") {
    get {
      throw GravsearchException("sparql search")
    }
  }

  private val unpe = path(("v1" | "v2" | "admin") / "unpe") {
    get {
      throw UpdateNotPerformedException("update not performed")
    }
  }

  private val ae = path(("v1" | "v2" | "admin") / "ae") {
    get {
      throw AuthenticationException("authentication exception")
    }
  }

  private val route: Route = Route.seal(handleExceptions(handler.KnoraExceptionHandler(appConfig)) {
    nfe ~ fe ~ bce ~ dve ~ oce ~ ece ~ sse ~ unpe ~ ae
  })

  "The Exception Handler" should {

    "return correct status code and response for 'NotFoundException'" in {
      Get("/v1/nfe") ~> route ~> check {

        status should be(StatusCodes.NotFound)

        responseAs[String] should be(
          "{\"status\":9,\"error\":\"dsp.errors.NotFoundException: not found\"}"
        )
      }

      Get("/v2/nfe") ~> route ~> check {

        status should be(StatusCodes.NotFound)

        responseAs[String] should be(
          "{\"knora-api:error\":\"dsp.errors.NotFoundException: not found\",\"@context\":{\"knora-api\":\"http://api.knora.org/ontology/knora-api/v2#\"}}"
        )
      }

      Get("/admin/nfe") ~> route ~> check {

        status should be(StatusCodes.NotFound)

        responseAs[String] should be("{\"error\":\"dsp.errors.NotFoundException: not found\"}")
      }
    }

    "return correct status code and response for 'ForbiddenException'" in {
      Get("/v1/fe") ~> route ~> check {

        status should be(StatusCodes.Forbidden)

        responseAs[String] should be(
          "{\"status\":3,\"error\":\"dsp.errors.ForbiddenException: forbidden\",\"access\":\"NO_ACCESS\"}"
        )
      }

      Get("/v2/fe") ~> route ~> check {

        status should be(StatusCodes.Forbidden)

        responseAs[String] should be(
          "{\"knora-api:error\":\"dsp.errors.ForbiddenException: forbidden\",\"@context\":{\"knora-api\":\"http://api.knora.org/ontology/knora-api/v2#\"}}"
        )
      }

      Get("/admin/fe") ~> route ~> check {

        status should be(StatusCodes.Forbidden)

        responseAs[String] should be("{\"error\":\"dsp.errors.ForbiddenException: forbidden\"}")
      }
    }

    "return correct status code and response for 'DuplicateValueException'" in {
      Get("/v1/dve") ~> route ~> check {

        status should be(StatusCodes.BadRequest)

        responseAs[String] should be(
          "{\"status\":28,\"error\":\"dsp.errors.DuplicateValueException: duplicate value\"}"
        )
      }

      Get("/v2/dve") ~> route ~> check {

        status should be(StatusCodes.BadRequest)

        responseAs[String] should be(
          "{\"knora-api:error\":\"dsp.errors.DuplicateValueException: duplicate value\",\"@context\":{\"knora-api\":\"http://api.knora.org/ontology/knora-api/v2#\"}}"
        )
      }

      Get("/admin/dve") ~> route ~> check {

        status should be(StatusCodes.BadRequest)

        responseAs[String] should be(
          "{\"error\":\"dsp.errors.DuplicateValueException: duplicate value\"}"
        )
      }
    }

    "return correct status code and response for 'OntologyConstraintException'" in {
      Get("/v1/oce") ~> route ~> check {

        status should be(StatusCodes.BadRequest)

        responseAs[String] should be(
          "{\"status\":29,\"error\":\"dsp.errors.OntologyConstraintException: ontology constraint\"}"
        )
      }

      Get("/v2/oce") ~> route ~> check {

        status should be(StatusCodes.BadRequest)

        responseAs[String] should be(
          "{\"knora-api:error\":\"dsp.errors.OntologyConstraintException: ontology constraint\",\"@context\":{\"knora-api\":\"http://api.knora.org/ontology/knora-api/v2#\"}}"
        )
      }

      Get("/admin/oce") ~> route ~> check {

        status should be(StatusCodes.BadRequest)

        responseAs[String] should be(
          "{\"error\":\"dsp.errors.OntologyConstraintException: ontology constraint\"}"
        )
      }
    }

    "return correct status code and response for 'EditConflictException'" in {
      Get("/v1/ece") ~> route ~> check {

        status should be(StatusCodes.Conflict)

        responseAs[String] should be(
          "{\"status\":27,\"error\":\"dsp.errors.EditConflictException: edit conflict\"}"
        )
      }

      Get("/v2/ece") ~> route ~> check {

        status should be(StatusCodes.Conflict)

        responseAs[String] should be(
          "{\"knora-api:error\":\"dsp.errors.EditConflictException: edit conflict\",\"@context\":{\"knora-api\":\"http://api.knora.org/ontology/knora-api/v2#\"}}"
        )
      }

      Get("/admin/ece") ~> route ~> check {

        status should be(StatusCodes.Conflict)

        responseAs[String] should be("{\"error\":\"dsp.errors.EditConflictException: edit conflict\"}")
      }
    }

    "return correct status code and response for 'GravsearchException'" in {
      Get("/v1/sse") ~> route ~> check {

        status should be(StatusCodes.BadRequest)

        responseAs[String] should be(
          "{\"status\":11,\"error\":\"dsp.errors.GravsearchException: sparql search\"}"
        )
      }

      Get("/v2/sse") ~> route ~> check {

        status should be(StatusCodes.BadRequest)

        responseAs[String] should be(
          "{\"knora-api:error\":\"dsp.errors.GravsearchException: sparql search\",\"@context\":{\"knora-api\":\"http://api.knora.org/ontology/knora-api/v2#\"}}"
        )
      }

      Get("/admin/sse") ~> route ~> check {

        status should be(StatusCodes.BadRequest)

        responseAs[String] should be("{\"error\":\"dsp.errors.GravsearchException: sparql search\"}")
      }
    }

    "return correct status code and response for 'UpdateNotPerformedException'" in {
      Get("/v1/unpe") ~> route ~> check {

        status should be(StatusCodes.Conflict)

        responseAs[String] should be(
          "{\"status\":27,\"error\":\"dsp.errors.UpdateNotPerformedException: update not performed\"}"
        )
      }

      Get("/v2/unpe") ~> route ~> check {

        status should be(StatusCodes.Conflict)

        responseAs[String] should be(
          "{\"knora-api:error\":\"dsp.errors.UpdateNotPerformedException: update not performed\",\"@context\":{\"knora-api\":\"http://api.knora.org/ontology/knora-api/v2#\"}}"
        )
      }

      Get("/admin/unpe") ~> route ~> check {

        status should be(StatusCodes.Conflict)

        responseAs[String] should be(
          "{\"error\":\"dsp.errors.UpdateNotPerformedException: update not performed\"}"
        )
      }
    }

    "return correct status code and response for 'AuthenticationException'" in {
      Get("/v1/ae") ~> route ~> check {

        status should be(StatusCodes.InternalServerError)

        responseAs[String] should be(
          "{\"status\":4,\"error\":\"dsp.errors.AuthenticationException: authentication exception\"}"
        )
      }

      Get("/v2/ae") ~> route ~> check {

        status should be(StatusCodes.InternalServerError)

        responseAs[String] should be(
          "{\"knora-api:error\":\"dsp.errors.AuthenticationException: authentication exception\",\"@context\":{\"knora-api\":\"http://api.knora.org/ontology/knora-api/v2#\"}}"
        )
      }

      Get("/admin/ae") ~> route ~> check {

        status should be(StatusCodes.InternalServerError)

        responseAs[String] should be(
          "{\"error\":\"dsp.errors.AuthenticationException: authentication exception\"}"
        )
      }
    }
  }
}
