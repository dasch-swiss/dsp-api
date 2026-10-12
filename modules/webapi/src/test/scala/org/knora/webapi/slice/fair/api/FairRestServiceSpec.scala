/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.fair.api

import org.junit.runner.RunWith
import sttp.model.HeaderNames
import sttp.model.StatusCode
import zio.*
import zio.test.*

import java.time.Instant

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.config.FairConfig
import org.knora.webapi.config.KnoraApi
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.admin.domain.model.Permission.ObjectAccess
import org.knora.webapi.slice.common.ResourceIri
import org.knora.webapi.slice.fair.domain.service.ResourceFactsBuilder
import org.knora.webapi.slice.fair.infrastructure.AppShell
import org.knora.webapi.slice.fair.ports.*

@RunWith(classOf[DspZTestJUnitRunner])
class FairRestServiceSpec extends ZIOSpecDefault {

  private val fairConfig =
    FairConfig("https://app.example.org", "https://app.example.org/index.html", "https://example.org/oai", "")
  private val api = KnoraApi("0.0.0.0", 3333, "https", "api.example.org", 443, 5555)

  private val id  = "0b03b0a6e6be"
  private val iri = ResourceIri.unsafeFrom(s"http://rdfh.ch/081C/$id")

  private val project  = FairProject(Shortcode.unsafeFrom("081C"), "short", Some("Long"), None, List.empty)
  private val resource = FairResource(
    iri = iri,
    label = "A resource",
    classIri = "http://api.example.org/ontology/081C/x/v2#Thing",
    userPermission = ObjectAccess.View,
    creationDate = Instant.parse("2024-01-02T03:04:05Z"),
    lastModificationDate = None,
    authorship = List.empty,
    description = None,
    file = None,
  )

  private val shellHtml = "<html><head><title>x</title></head><body>app</body></html>"
  private val twoHeads  = "<html><head></head><body><!-- </head> --></body></html>"

  private val jsonLdUrl = s"https://api.example.org:443/fair/resources/081C/$id/metadata.jsonld"

  private final class CountingShell(value: Option[String], calls: Ref[Int]) extends AppShell {
    override def get: UIO[Option[String]] = calls.update(_ + 1).as(value)
  }

  private def service(resources: FairResourcesInMemory, shell: Option[String]) =
    for {
      calls  <- Ref.make(0)
      assets <- FairAssetsInMemory.make()
      sf     <- ZIO.service[StringFormatter]
      builder = ResourceFactsBuilder(
                  resources,
                  FairProjectsInMemory(Map(project.shortcode -> project)),
                  FairClassesInMemory(Map(resource.classIri -> FairClass(Map("en" -> "Thing"), requiresFile = false))),
                  assets,
                  fairConfig,
                  sf,
                )
    } yield (FairRestService(builder, CountingShell(shell, calls), fairConfig, api), calls)

  private def withService[A](shell: Option[String] = Some(shellHtml), present: Boolean = true)(
    f: (FairRestService, Ref[Int]) => ZIO[Any, FairInternalError.type, A],
  ): ZIO[Any, Throwable, A] =
    (for {
      resources <- if (present) FairResourcesInMemory.make(resource) else FairResourcesInMemory.make()
      sc        <- service(resources, shell)
      a         <- f(sc._1, sc._2).mapError(e => new RuntimeException(e.toString))
    } yield a).provide(StringFormatter.test)

  private def header(r: FairResponse, name: String): List[String] =
    r.headers.filter(_.is(name)).map(_.value)

  private def count(haystack: String, needle: String): Int = haystack.sliding(needle.length).count(_ == needle)

  private val html = "text/html; charset=UTF-8"

  val spec = suite("FairRestService")(
    suite("landing, HTML")(
      test("a visible resource gets the head block before the first </head>") {
        withService() { (s, _) =>
          s.landing("081C", id, Nil).map { r =>
            val body = r.body.getOrElse("")
            assertTrue(
              r.status == StatusCode.Ok,
              body.indexOf("<script type=\"application/ld+json\">") < body.indexOf("</head>"),
              body.startsWith("<html><head><title>x</title>"),
              count(body, "<script type=\"application/ld+json\">") == 1,
              header(r, HeaderNames.Link).map(count(_, "rel=\"cite-as\"")) == List(1),
              header(r, HeaderNames.Vary) == List("Accept"),
              header(r, HeaderNames.CacheControl) == List("no-store"),
              header(r, HeaderNames.ContentType) == List(html),
            )
          }
        }
      },
      test("removing the block gives the shell back") {
        withService() { (s, _) =>
          s.landing("081C", id, Nil).map { r =>
            val body  = r.body.getOrElse("")
            val start = body.indexOf("</head>")
            val block = body.substring(body.indexOf("</title>") + "</title>".length, start)
            assertTrue(block.nonEmpty, body.replace(block, "") == shellHtml)
          }
        }
      },
      test("a shell with two </head> gets the block only before the first") {
        withService(Some(twoHeads)) { (s, _) =>
          s.landing("081C", id, Nil).map { r =>
            val body = r.body.getOrElse("")
            assertTrue(
              count(body, "<script type=\"application/ld+json\">") == 1,
              body.indexOf("<script") < body.indexOf("</head>"),
              body.endsWith("<body><!-- </head> --></body></html>"),
            )
          }
        }
      },
      test("Accept absent, */* and text/html give identical bodies") {
        withService() { (s, _) =>
          for {
            none <- s.landing("081C", id, Nil)
            star <- s.landing("081C", id, List("*/*"))
            h    <- s.landing("081C", id, List("text/html"))
          } yield assertTrue(none.body == star.body, none.body == h.body, none.status == StatusCode.Ok)
        }
      },
      test("two Accept lines are joined: text/html beats application/ld+json;q=0.1") {
        withService() { (s, _) =>
          s.landing("081C", id, List("text/html", "application/ld+json;q=0.1"))
            .map(r => assertTrue(r.status == StatusCode.Ok, header(r, HeaderNames.ContentType) == List(html)))
        }
      },
      test("a shell that is unavailable answers 502 with Vary and Cache-Control") {
        withService(None) { (s, _) =>
          s.landing("081C", id, Nil).map { r =>
            assertTrue(
              r.status == StatusCode.BadGateway,
              r.body.isEmpty,
              header(r, HeaderNames.Vary) == List("Accept"),
              header(r, HeaderNames.CacheControl) == List("no-store"),
            )
          }
        }
      },
      test("a shell without </head> answers 502 for a visible resource") {
        withService(Some("<html><body>app</body></html>")) { (s, _) =>
          s.landing("081C", id, Nil).map { r =>
            assertTrue(
              r.status == StatusCode.BadGateway,
              r.body.isEmpty,
              header(r, HeaderNames.Vary) == List("Accept"),
              header(r, HeaderNames.CacheControl) == List("no-store"),
            )
          }
        }
      },
    ),
    suite("landing, redirect")(
      test("Accept application/ld+json redirects to the JSON-LD URL without consulting the shell") {
        withService() { (s, calls) =>
          for {
            r <- s.landing("081C", id, List("application/ld+json"))
            n <- calls.get
          } yield assertTrue(
            r.status == StatusCode.SeeOther,
            header(r, HeaderNames.Location) == List(jsonLdUrl),
            r.body.isEmpty,
            header(r, HeaderNames.Vary) == List("Accept"),
            header(r, HeaderNames.CacheControl) == List("no-store"),
            n == 0,
          )
        }
      },
      test("a redirect is served while the shell is unavailable") {
        withService(None) { (s, _) =>
          s.landing("081C", id, List("application/ld+json")).map(r => assertTrue(r.status == StatusCode.SeeOther))
        }
      },
    ),
    suite("landing, not visible")(
      test("the shell is returned byte for byte, without Link") {
        withService(present = false) { (s, _) =>
          s.landing("081C", id, Nil).map { r =>
            assertTrue(
              r.status == StatusCode.Ok,
              r.body.contains(shellHtml),
              header(r, HeaderNames.Link).isEmpty,
              header(r, HeaderNames.Vary) == List("Accept"),
              header(r, HeaderNames.CacheControl) == List("no-store"),
              header(r, HeaderNames.ContentType) == List(html),
            )
          }
        }
      },
      test("a malformed id is answered like a missing resource") {
        withService(present = false) { (s, _) =>
          for {
            missing   <- s.landing("081C", id, Nil)
            malformed <- s.landing("081C", "bad!id", Nil)
          } yield assertTrue(missing == malformed)
        }
      },
      test("an Accept for a representation of an invisible resource still answers the shell") {
        withService(present = false) { (s, _) =>
          s.landing("081C", id, List("application/ld+json")).map(r => assertTrue(r.body.contains(shellHtml)))
        }
      },
    ),
    suite("representations")(
      test("JSON-LD: 200, its media type and a describes Link") {
        withService() { (s, calls) =>
          for {
            r <- s.jsonLd("081C", id)
            n <- calls.get
          } yield assertTrue(
            r.status == StatusCode.Ok,
            header(r, HeaderNames.ContentType) == List("application/ld+json"),
            header(r, "X-Content-Type-Options") == List("nosniff"),
            header(r, HeaderNames.Link).exists(_.contains("rel=\"describes\"")),
            r.body.exists(_.startsWith("{")),
            n == 0,
          )
        }
      },
      test("DataCite: 200, its media type and a describes Link") {
        withService() { (s, _) =>
          s.dataCite("081C", id).map { r =>
            assertTrue(
              r.status == StatusCode.Ok,
              header(r, HeaderNames.ContentType) == List("application/vnd.datacite.datacite+json"),
              header(r, HeaderNames.Link).exists(_.contains("rel=\"describes\"")),
            )
          }
        }
      },
      test("missing, malformed id, malformed shortcode and absent resources answer identically") {
        withService(present = false) { (s, _) =>
          for {
            missing  <- s.jsonLd("081C", id)
            badId    <- s.jsonLd("081C", "bad!id")
            badShort <- s.jsonLd("08X3", id)
            dc       <- s.dataCite("081C", id)
          } yield assertTrue(
            missing.status == StatusCode.NotFound,
            header(missing, HeaderNames.ContentType) == List("text/plain"),
            missing.body.isEmpty,
            missing == badId,
            missing == badShort,
            missing == dc,
          )
        }
      },
    ),
    suite("failures")(
      test("a failing resources port fails with FairInternalError") {
        (for {
          resources <- FairResourcesInMemory.failing(new RuntimeException("triplestore down"))
          sc        <- service(resources, Some(shellHtml))
          landing   <- sc._1.landing("081C", id, Nil).either
          json      <- sc._1.jsonLd("081C", id).either
        } yield assertTrue(
          landing.left.exists(_ == FairInternalError),
          json.left.exists(_ == FairInternalError),
        )).provide(StringFormatter.test)
      },
    ),
  )
}
