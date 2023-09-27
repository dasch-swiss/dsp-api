/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.e2e.admin

import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.BasicHttpCredentials

import java.net.URLEncoder

import org.knora.webapi.E2ESpec
import org.knora.webapi.sharedtestdata.SharedTestDataADM

/**
 * End-to-End (E2E) test specification for testing groups endpoint.
 */
class RestrictedViewSizeE2ESpec extends E2ESpec {

  private val rootEmail = SharedTestDataADM.rootUser.email
  private val testPass  = SharedTestDataADM.testPass

  s"The Projects Route 'admin/projects'" when {

    "used to set RestrictedViewSize by project IRI" should {
      "return requested value to be set with 200 Response Status" in {
        val encodedIri = URLEncoder.encode(SharedTestDataADM.imagesProject.id, "utf-8")
        val payload    = """{"size":"pct:1"}"""
        val request =
          Post(s"http://0.0.0.0:5555/admin/projects/iri/$encodedIri/RestrictedViewSettings", payload) ~> addCredentials(
            BasicHttpCredentials(rootEmail, testPass)
          )
        val response: HttpResponse = singleAwaitingRequest(request)
        val result: String         = responseToString(response)
        assert(response.status === StatusCodes.OK)
        assert(payload === result)
      }

      "return the `BadRequest` if the size value is invalid" in {
        val encodedIri = URLEncoder.encode(SharedTestDataADM.imagesProject.id, "utf-8")
        val payload    = """{"size":"pct:0"}"""
        val request =
          Post(s"http://0.0.0.0:5555/admin/projects/iri/$encodedIri/RestrictedViewSettings", payload) ~> addCredentials(
            BasicHttpCredentials(rootEmail, testPass)
          )
        val response: HttpResponse = singleAwaitingRequest(request)
        val result: String         = responseToString(response)
        assert(response.status === StatusCodes.BadRequest)
        assert(result.contains("Invalid RestrictedViewSize: pct:0"))
      }

      "return `Forbidden` for the user who is not a system nor project admin" in {
        val encodedIri = URLEncoder.encode(SharedTestDataADM.imagesProject.id, "utf-8")
        val payload    = """{"size":"pct:1"}"""
        val request =
          Post(s"http://0.0.0.0:5555/admin/projects/iri/$encodedIri/RestrictedViewSettings", payload) ~> addCredentials(
            BasicHttpCredentials(SharedTestDataADM.imagesUser02.email, testPass)
          )
        val response: HttpResponse = singleAwaitingRequest(request)
        assert(response.status === StatusCodes.Forbidden)
      }
    }

    "used to set RestrictedViewSize by project Shortcode" should {
      "return requested value to be set with 200 Response Status" in {
        val shortcode = SharedTestDataADM.imagesProject.shortcode
        val payload   = """{"size":"pct:1"}"""
        val request =
          Post(
            s"http://0.0.0.0:5555/admin/projects/shortcode/$shortcode/RestrictedViewSettings",
            payload
          ) ~> addCredentials(
            BasicHttpCredentials(rootEmail, testPass)
          )
        val response: HttpResponse = singleAwaitingRequest(request)
        val result: String         = responseToString(response)
        assert(response.status === StatusCodes.OK)
        assert(payload === result)
      }

      "return the `BadRequest` if the size value is invalid" in {
        val shortcode = SharedTestDataADM.imagesProject.shortcode
        val payload   = """{"size":"pct:0"}"""
        val request =
          Post(
            s"http://0.0.0.0:5555/admin/projects/shortcode/$shortcode/RestrictedViewSettings",
            payload
          ) ~> addCredentials(
            BasicHttpCredentials(rootEmail, testPass)
          )
        val response: HttpResponse = singleAwaitingRequest(request)
        val result: String         = responseToString(response)
        assert(response.status === StatusCodes.BadRequest)
        assert(result.contains("Invalid RestrictedViewSize: pct:0"))
      }

      "return `Forbidden` for the user who is not a system nor project admin" in {
        val shortcode = SharedTestDataADM.imagesProject.shortcode
        val payload   = """{"size":"pct:1"}"""
        val request =
          Post(
            s"http://0.0.0.0:5555/admin/projects/shortcode/$shortcode/RestrictedViewSettings",
            payload
          ) ~> addCredentials(
            BasicHttpCredentials(SharedTestDataADM.imagesUser02.email, testPass)
          )
        val response: HttpResponse = singleAwaitingRequest(request)
        assert(response.status === StatusCodes.Forbidden)
      }
    }
  }
}
