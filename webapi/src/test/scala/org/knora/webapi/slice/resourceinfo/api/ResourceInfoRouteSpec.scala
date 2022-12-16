/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resourceinfo.api

import zhttp.http._
import zio.ZIO
import zio.test.ZIOSpecDefault
import zio.test._

import java.util.UUID.randomUUID

import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.slice.resourceinfo.api.LiveRestResourceInfoService.ASC
import org.knora.webapi.slice.resourceinfo.api.LiveRestResourceInfoService.DESC
import org.knora.webapi.slice.resourceinfo.api.LiveRestResourceInfoService.creationDate
import org.knora.webapi.slice.resourceinfo.api.LiveRestResourceInfoService.lastModificationDate
import org.knora.webapi.slice.resourceinfo.api.SpyLiveRestResourceInfoService.orderingKey
import org.knora.webapi.slice.resourceinfo.api.SpyLiveRestResourceInfoService.projectIriKey
import org.knora.webapi.slice.resourceinfo.api.SpyLiveRestResourceInfoService.resourceClassKey
import org.knora.webapi.slice.resourceinfo.domain.IriConverter
import org.knora.webapi.slice.resourceinfo.repo.ResourceInfoRepoFake

object ResourceInfoRouteSpec extends ZIOSpecDefault {

  private val testResourceClass = "http://test-resource-class/" + randomUUID
  private val testProjectIri    = "http://test-project/" + randomUUID
  private val baseUrl           = URL(!! / "v2" / "resources" / "info")
  private val projectHeader     = Headers("x-knora-accept-project", testProjectIri)

  private def sendRequest(req: Request) =
    for {
      route    <- ZIO.service[ResourceInfoRoute].map(_.route)
      response <- route(req)
    } yield response

  def spec =
    suite("ResourceInfoRoute /v2/resources/info")(
      test("given no required params/headers were passed should respond with BadRequest") {
        val request = Request(url = baseUrl)
        for {
          response <- sendRequest(request)
        } yield assertTrue(response.status == Status.BadRequest)
      },
      test("given more than one resource class should respond with BadRequest") {
        val url     = baseUrl.setQueryParams(Map("resourceClass" -> List(testResourceClass, "http://anotherResourceClass")))
        val request = Request(url = url, headers = projectHeader)
        for {
          response <- sendRequest(request)
        } yield assertTrue(response.status == Status.BadRequest)
      },
      test("given no projectIri should respond with BadRequest") {
        val url     = baseUrl.setQueryParams(Map("resourceClass" -> List(testResourceClass)))
        val request = Request(url = url)
        for {
          response <- sendRequest(request)
        } yield assertTrue(response.status == Status.BadRequest)
      },
      test("given all mandatory parameters should respond with OK") {
        val url     = baseUrl.setQueryParams(Map("resourceClass" -> List(testResourceClass)))
        val request = Request(url = url, headers = projectHeader)
        for {
          response <- sendRequest(request)
        } yield assertTrue(response.status == Status.Ok)
      },
      test("given all parameters rest service should be called with default order") {
        val url     = baseUrl.setQueryParams(Map("resourceClass" -> List(testResourceClass)))
        val request = Request(url = url, headers = projectHeader)
        for {
          expectedResourceClassIri <- IriConverter.asInternalIri(testResourceClass).map(_.value)
          expectedProjectIri       <- IriConverter.asInternalIri(testProjectIri).map(_.value)
          lastInvocation           <- sendRequest(request) *> SpyLiveRestResourceInfoService.lastInvocation
        } yield assertTrue(
          lastInvocation ==
            Map(
              projectIriKey    -> expectedProjectIri,
              resourceClassKey -> expectedResourceClassIri,
              orderingKey      -> (lastModificationDate, ASC)
            )
        )
      },
      test("given all parameters rest service should be called with correct parameters") {
        val url = baseUrl.setQueryParams(
          Map(
            "resourceClass" -> List(testResourceClass),
            "orderBy"       -> List("creationDate"),
            "order"         -> List("DESC")
          )
        )
        val request = Request(url = url, headers = projectHeader)
        for {
          expectedProjectIri       <- IriConverter.asInternalIri(testProjectIri).map(_.value)
          expectedResourceClassIri <- IriConverter.asInternalIri(testResourceClass).map(_.value)
          _                        <- sendRequest(request)
          lastInvocation           <- SpyLiveRestResourceInfoService.lastInvocation
        } yield assertTrue(
          lastInvocation ==
            Map(
              projectIriKey    -> expectedProjectIri,
              resourceClassKey -> expectedResourceClassIri,
              orderingKey      -> (creationDate, DESC)
            )
        )
      }
    ).provide(
      ResourceInfoRoute.layer,
      SpyLiveRestResourceInfoService.layer,
      )
    StringFormatter.test
      ,IriConverter.layer
      ,ResourceInfoRepoFake
        /** EndMarker */
        .layer.layer,
      IriConverter.layer,
      StringFormatter.test
    )
}
