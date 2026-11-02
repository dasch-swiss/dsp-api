/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.fair

import org.junit.runner.RunWith
import sttp.client4.*
import sttp.model.StatusCode
import zio.*
import zio.http as zh
import zio.test.*

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.E2EZSpec
import org.knora.webapi.config.KnoraApi
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.sharedtestdata.SharedTestDataADM.*
import org.knora.webapi.testservices.TestApiClient

object FairEndpointsE2ESpec {
  private val started = new AtomicBoolean(false)
  private val scope   = new AtomicReference[Option[Scope.Closeable]](None)
}

@RunWith(classOf[DspZTestJUnitRunner])
class FairEndpointsE2ESpec extends E2EZSpec {

  override def rdfDataObjects: List[RdfDataObject] = incunabulaRdfOntologyAndData ++ anythingRdfOntologyAndData

  // Fixed because `app.fair.app-shell-url` in this module's test application.conf is static and must name this port.
  private val shellPort = 18742
  private val shell     = "<html><head><title>t</title></head><body></body></html>"

  // The landing page fetches dsp-app's shell lazily, so the stub only has to be up before the first landing request.
  // Started once per JVM and closed by afterAll, so a second spec instance neither fails to bind nor leaks the port.
  private val startShellStub: UIO[Unit] =
    ZIO.suspendSucceed {
      if (FairEndpointsE2ESpec.started.compareAndSet(false, true))
        (for {
          scope <- Scope.make
          _     <- ZIO.succeed(FairEndpointsE2ESpec.scope.set(Some(scope)))
          _     <- scope.extend[Any](
                 for {
                   env <- zh.Server.defaultWith(_.binding("localhost", shellPort)).build
                   _   <- zh.Server
                          .install(zh.Routes(zh.Method.GET / "index.html" -> zh.handler(zh.Response.text(shell))))
                          .provideEnvironment(env)
                 } yield (),
               )
        } yield ()).tapError(_ => stopShellStub).orDie
      else ZIO.unit
    }

  private val stopShellStub: UIO[Unit] =
    ZIO.suspendSucceed {
      FairEndpointsE2ESpec.scope.getAndSet(None) match {
        case Some(scope) => scope.close(Exit.unit) *> ZIO.succeed(FairEndpointsE2ESpec.started.set(false))
        case None        => ZIO.succeed(FairEndpointsE2ESpec.started.set(false))
      }
    }

  // The incunabula Sideband: anonymous users hold D on both the resource and its still image.
  private val visibleId = "0803/0b03b0a6e6be"
  // The incunabula page: anonymous users hold RV only.
  private val restrictedId = "0803/00014b43f902"
  // The anything Thing marked deleted.
  private val deletedId = "0001/PHbbrEsVR32q5D_ioKt6pA"
  private val missingId = "0803/ffffffffffff"

  private def landing(id: String, accept: Option[String] = None, followRedirects: Boolean = true) =
    ZIO.serviceWithZIO[TestApiClient](
      _.getAsString(
        uri"/internal/landing-pages/resources/${id.split('/').toList}",
        r => accept.fold(r)(a => r.header("Accept", a)).followRedirects(followRedirects),
      ),
    )

  private def representation(id: String, file: String) =
    ZIO.serviceWithZIO[TestApiClient](_.getAsString(uri"/fair/resources/${id.split('/').toList}/$file", identity))

  private def count(haystack: String, needle: String): Int = haystack.sliding(needle.length).count(_ == needle)

  private val ldScript = """<script type="application/ld+json">"""

  private val visibleSuite = suite("a resource the anonymous user may see")(
    test("the landing page carries one JSON-LD script, a cite-as Link and Vary: Accept") {
      landing(visibleId).map { r =>
        val body = r.body.merge
        assertTrue(
          r.code == StatusCode.Ok,
          count(body, ldScript) == 1,
          body.contains("<title>t</title>"),
          r.header("Link").exists(_.contains("""rel="cite-as"""")),
          r.header("Vary").contains("Accept"),
        )
      }
    },
    test("Accept: application/ld+json answers 303 to the JSON-LD representation") {
      landing(visibleId, Some("application/ld+json"), followRedirects = false).map { r =>
        assertTrue(
          r.code == StatusCode.SeeOther,
          r.header("Location").exists(_.endsWith("/fair/resources/0803/0b03b0a6e6be/metadata.jsonld")),
          r.header("Vary").contains("Accept"),
        )
      }
    },
    test("the JSON-LD representation lists the file with its original URL on the API host") {
      for {
        api <- ZIO.service[KnoraApi]
        r   <- representation(visibleId, "metadata.jsonld")
      } yield {
        val body = r.body.merge
        assertTrue(
          r.code == StatusCode.Ok,
          r.header("Content-Type").exists(_.startsWith("application/ld+json")),
          body.contains(""""@type":"DataDownload""""),
          body.contains(s"${api.externalKnoraApiBaseUrl}/ingest/projects/0803/assets/"),
          body.contains("/original"),
          body.contains(""""isAccessibleForFree":true"""),
        )
      }
    },
    test("the DataCite representation is served with its media type") {
      representation(visibleId, "metadata.datacite.json").map(r =>
        assertTrue(
          r.code == StatusCode.Ok,
          r.header("Content-Type").exists(_.startsWith("application/vnd.datacite.datacite+json")),
        ),
      )
    },
  )

  private val restrictedSuite = suite("a resource visible to the anonymous user as a preview only")(
    test("the page is visible, restricted, and offers no download") {
      for {
        page <- landing(restrictedId)
        ld   <- representation(restrictedId, "metadata.jsonld")
        body  = ld.body.merge
      } yield assertTrue(
        page.code == StatusCode.Ok,
        count(page.body.merge, ldScript) == 1,
        ld.code == StatusCode.Ok,
        body.contains(""""isAccessibleForFree":false"""),
        !body.contains("DataDownload"),
      )
    },
  )

  private val hiddenSuite = suite("a deleted and a missing resource")(
    test("the landing page is the unchanged shell without a Link header, for both") {
      for {
        deleted <- landing(deletedId)
        missing <- landing(missingId)
      } yield assertTrue(
        deleted.code == StatusCode.Ok,
        deleted.body.merge == shell,
        deleted.header("Link").isEmpty,
        missing.code == StatusCode.Ok,
        missing.body.merge == shell,
        missing.header("Link").isEmpty,
      )
    },
    test("the representations answer an identical 404") {
      ZIO
        .foreach(List("metadata.jsonld", "metadata.datacite.json")) { file =>
          for {
            deleted <- representation(deletedId, file)
            missing <- representation(missingId, file)
          } yield assertTrue(
            deleted.code == StatusCode.NotFound,
            missing.code == StatusCode.NotFound,
            deleted.body.merge == missing.body.merge,
            deleted.header("Content-Type") == missing.header("Content-Type"),
          )
        }
        .map(_.reduce(_ && _))
    },
  )

  override val e2eSpec =
    suite("FAIR landing page endpoints E2E")(visibleSuite, restrictedSuite, hiddenSuite) @@ TestAspect.beforeAll(
      startShellStub,
    ) @@ TestAspect.afterAll(stopShellStub)
}
