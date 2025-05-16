/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.it

import org.apache.pekko
import spray.json.*

import scala.concurrent.Await
import scala.concurrent.duration.*

import org.knora.webapi.ITKnoraLiveSpec

import pekko.http.scaladsl.model.*

/**
 * End-to-End (E2E) test specification for testing route rejections.
 */
class VersionRouteITSpec extends ITKnoraLiveSpec {

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
      val value = responseBodyJson.fields(field).compactPrint.replaceAll("\"", "")
      result = !value.equals("")
    } catch {
      case _: NoSuchElementException => result = false
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
      val value            = responseBodyJson.fields("name").compactPrint.replaceAll("\"", "")
      assert(value.equals("version"))
    }

    "contain nonempty value for key 'webapi'" in {
      assert(checkNonEmpty("webapi"))
    }

    "contain nonempty value for key 'scala'" in {
      assert(checkNonEmpty("scala"))
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
