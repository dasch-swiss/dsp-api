/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.e2e.v1

import akka.actor._
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.BasicHttpCredentials
import akka.http.scaladsl.testkit.RouteTestTimeout

import java.net.URLEncoder
import java.nio.file.Files
import java.nio.file.Paths

import dsp.errors.FileWriteException
import org.knora.webapi._
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.messages.v1.responder.resourcemessages.CreateResourceApiRequestV1
import org.knora.webapi.messages.v1.responder.resourcemessages.CreateResourceValueV1
import org.knora.webapi.messages.v1.responder.valuemessages.ChangeFileValueApiRequestV1
import org.knora.webapi.messages.v1.responder.valuemessages.CreateRichtextV1
import org.knora.webapi.routing.v1.ResourcesRouteV1
import org.knora.webapi.routing.v1.ValuesRouteV1
import org.knora.webapi.sharedtestdata.SharedTestDataV1

/**
 * End-to-end test specification for the resources endpoint. This specification uses the Spray Testkit as documented
 * here: http://spray.io/documentation/1.2.2/spray-testkit/
 */
class SipiV1R2RSpec extends R2RSpec {

  private val resourcesPath = new ResourcesRouteV1(routeData).makeRoute
  private val valuesPath    = new ValuesRouteV1(routeData).makeRoute

  implicit def default(implicit system: ActorSystem): RouteTestTimeout = RouteTestTimeout(
    appConfig.defaultTimeoutAsDuration
  )

  private val incunabulaProjectAdminEmail = SharedTestDataV1.incunabulaProjectAdminUser.userData.email.get
  private val testPass                    = "test"

  override lazy val rdfDataObjects = List(
    RdfDataObject(path = "test_data/all_data/incunabula-data.ttl", name = "http://www.knora.org/data/0803/incunabula"),
    RdfDataObject(path = "test_data/demo_data/images-demo-data.ttl", name = "http://www.knora.org/data/00FF/images")
  )

  /* we need to run our app with the mocked sipi implementation */
  override type Environment = core.LayersTest.DefaultTestEnvironmentWithoutSipi
  override lazy val effectLayers = core.LayersTest.defaultLayersTestWithMockedSipi(system)

  object RequestParams {

    val createResourceParams: CreateResourceApiRequestV1 = CreateResourceApiRequestV1(
      restype_id = "http://www.knora.org/ontology/0803/incunabula#page",
      properties = Map(
        "http://www.knora.org/ontology/0803/incunabula#pagenum" -> Seq(
          CreateResourceValueV1(
            richtext_value = Some(
              CreateRichtextV1(
                utf8str = Some("test_page")
              )
            )
          )
        ),
        "http://www.knora.org/ontology/0803/incunabula#origname" -> Seq(
          CreateResourceValueV1(
            richtext_value = Some(
              CreateRichtextV1(
                utf8str = Some("test")
              )
            )
          )
        ),
        "http://www.knora.org/ontology/0803/incunabula#partOf" -> Seq(
          CreateResourceValueV1(
            link_value = Some("http://rdfh.ch/0803/5e77e98d2603")
          )
        ),
        "http://www.knora.org/ontology/0803/incunabula#seqnum" -> Seq(
          CreateResourceValueV1(
            int_value = Some(999)
          )
        )
      ),
      label = "test",
      project_id = "http://rdfh.ch/projects/0803"
    )

    val pathToFile = "test_data/test_route/images/Chlaus.jpg"

    def createTmpFileDir(): Unit = {
      // check if tmp datadir exists and create it if not
      val tmpFileDir = Paths.get(appConfig.tmpDatadir)

      if (!Files.exists(tmpFileDir)) {
        try {
          Files.createDirectories(tmpFileDir)
        } catch {
          case e: Throwable =>
            throw FileWriteException(
              s"Tmp data directory ${appConfig.tmpDatadir} could not be created: ${e.getMessage}"
            )
        }
      }
    }
  }

  "The Resources Endpoint" should {

    "create a resource with a digital representation" in {
      val internalFilename = "IQUO3t1AABm-FSLC0vNvVpr.jp2"

      val params = RequestParams.createResourceParams.copy(
        file = Some(internalFilename)
      )

      Post("/v1/resources", HttpEntity(MediaTypes.`application/json`, params.toJsValue.compactPrint)) ~> addCredentials(
        BasicHttpCredentials(incunabulaProjectAdminEmail, testPass)
      ) ~> resourcesPath ~> check {
        assert(status == StatusCodes.OK, "Status code is not set to OK, Knora says:\n" + responseAs[String])
      }
    }

  }

  "The Values endpoint" should {

    "change the file value of an existing page" in {
      val internalFilename = "FSLC0vNvVpr-IQUO3t1AABm.jp2"

      val params = ChangeFileValueApiRequestV1(
        file = internalFilename
      )

      val resIri = URLEncoder.encode("http://rdfh.ch/0803/8a0b1e75", "UTF-8")

      Put(
        "/v1/filevalue/" + resIri,
        HttpEntity(MediaTypes.`application/json`, params.toJsValue.compactPrint)
      ) ~> addCredentials(BasicHttpCredentials(incunabulaProjectAdminEmail, testPass)) ~> valuesPath ~> check {
        assert(status == StatusCodes.OK, "Status code is not set to OK, Knora says:\n" + responseAs[String])
      }

    }
  }
}
