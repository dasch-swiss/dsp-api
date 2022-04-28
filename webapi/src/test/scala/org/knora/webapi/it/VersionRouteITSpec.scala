/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.it

import java.util.NoSuchElementException

import akka.http.scaladsl.model._
import com.typesafe.config.{Config, ConfigFactory}
import org.knora.webapi.ITKnoraLiveSpec
import spray.json._

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.languageFeature.postfixOps

object VersionRouteITSpec {
  val config: Config = ConfigFactory.parseString("""
                                                   |akka.loglevel = "DEBUG"
                                                   |akka.stdout-loglevel = "DEBUG"
  """.stripMargin)
}

/**
 * End-to-End (E2E) test specification for testing route rejections.
 */
class VersionRouteITSpec extends ITKnoraLiveSpec(VersionRouteITSpec.config) {

  private def getJsonResponse: JsObject = {
    val request                = Get(baseApiUrl + s"/version")
    val response: HttpResponse = singleAwaitingRequest(request)
    val responseBody: String =
      Await.result(response.entity.toStrict(10.seconds).map(_.data.decodeString("UTF-8")), 10.seconds)
    val responseBodyJson = responseBody.parseJson.asJsObject
    responseBodyJson
  }

  private def checkNonEmpty(field: String): Boolean = {
    val responseBodyJson = getJsonResponse
    var result           = false
    try {
      val value = responseBodyJson.fields(field).toString().replaceAll("\"", "")
      result = !value.equals("")
    } catch {
      case nse: NoSuchElementException => result = false
    }
    result
  }

  "The Version Route" should {

    "return 'OK'" in {
      val request                = Get(baseApiUrl + s"/version")
      val response: HttpResponse = singleAwaitingRequest(request)
      response.status should be(StatusCodes.OK)
    }

    "return 'version' as name" in {
      val responseBodyJson = getJsonResponse
      val value            = responseBodyJson.fields("name").toString().replaceAll("\"", "")
      assert(value.equals("version"))
    }

    "contain nonempty value for key 'webapi'" in {
      assert(checkNonEmpty("webapi"))
    }

    "contain nonempty value for key 'scala'" in {
      assert(checkNonEmpty("scala"))
    }

    "contain nonempty value for key 'akkaHttp'" in {
      assert(checkNonEmpty("akkaHttp"))
    }

    "contain nonempty value for key 'sipi'" in {
      assert(checkNonEmpty("sipi"))
    }

    "contain nonempty value for key 'fuseki'" in {
      assert(checkNonEmpty("fuseki"))
    }

    "fail for nonexisting key 'fail'" in {
      assert(!checkNonEmpty("fail"))
    }
  }
}
