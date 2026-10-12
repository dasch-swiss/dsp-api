/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.core

import org.junit.runner.RunWith
import sttp.tapir.server.ziohttp.ZioHttpInterpreter
import zio.*
import zio.http.Header
import zio.http.Method as HttpMethod
import zio.http.Request
import zio.http.URL
import zio.json.*
import zio.json.ast.Json
import zio.telemetry.opentelemetry.OpenTelemetry
import zio.telemetry.opentelemetry.context.ContextStorage
import zio.test.*

import java.time.Instant

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.config.FairConfig
import org.knora.webapi.config.KnoraApi
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.admin.domain.model.Permission.ObjectAccess
import org.knora.webapi.slice.common.ResourceIri
import org.knora.webapi.slice.fair.api.FairEndpoints
import org.knora.webapi.slice.fair.api.FairRestService
import org.knora.webapi.slice.fair.api.FairServerEndpoints
import org.knora.webapi.slice.fair.domain.service.ResourceFactsBuilder
import org.knora.webapi.slice.fair.infrastructure.AppShell
import org.knora.webapi.slice.fair.ports.*

/**
 * The FAIR routes served through [[DspApiServer.serverOptions]], the interceptor chain the running server installs,
 * with assertions on what goes over the wire: status, header lines and body bytes.
 */
@RunWith(classOf[DspZTestJUnitRunner])
class FairEndpointsHttpSpec extends ZIOSpecDefault {

  /** One response as a client sees it. Header names are lower-cased. */
  private final case class Wire(status: Int, headers: List[(String, String)], body: String) {
    def header(name: String): List[String] = headers.collect { case (n, v) if n == name.toLowerCase => v }
    def contentType: List[String]          = header("content-type")
    def comparable: Wire                   = copy(headers = headers.sorted)
  }

  private final class Harness(val run: Request => Task[Wire], val shellCalls: UIO[Int], val shellUp: Ref[Boolean])

  private val fairConfig =
    FairConfig("https://app.example.org", "https://app.example.org/index.html", "https://example.org/oai", "")
  private val api = KnoraApi("0.0.0.0", 3333, "https", "api.example.org", 443, 5555)

  private val shell = "<html><head><title>x</title></head><body>app</body></html>"

  private val sc = Shortcode.unsafeFrom("081C")

  private val visibleId   = "0b03b0a6e6be"
  private val rvFileId    = "00014b43f902"
  private val noFileId    = "aaaa00000001"
  private val missingId   = "ffff00000000"
  private val hiddenValue = s"http://rdfh.ch/081C/$visibleId/values/hidden-value"

  private val ingestUrl  = "https://ingest.example.org/081C/asset-rv.jp2"
  private val licenceIri = "http://rdfh.ch/lists/081C/file-licence"

  private val thing = "http://api.example.org/ontology/081C/x/v2#Thing"
  private val image = "http://api.example.org/ontology/081C/x/v2#Image"

  private def resource(id: String, classIri: String, file: Option[FairFileValue]) =
    FairResource(
      iri = ResourceIri.unsafeFrom(s"http://rdfh.ch/081C/$id"),
      label = "A resource",
      classIri = classIri,
      userPermission = ObjectAccess.View,
      creationDate = Instant.parse("2024-01-02T03:04:05Z"),
      lastModificationDate = None,
      authorship = List("Visible Author"),
      description = Some("A description"),
      file = file,
    )

  private val visible = resource(visibleId, thing, None)

  /** A file value anonymous may only see restricted; its ingest URL, type, name and size must reach no answer. */
  private val rvOnly = resource(
    rvFileId,
    image,
    Some(
      FairFileValue(
        FairFileKind.Stored,
        ObjectAccess.RestrictedView,
        Some("image"),
        Some("asset-rv"),
        Some(ingestUrl),
        Some(licenceIri),
        List("File Author"),
      ),
    ),
  )

  /** The class requires a file, and anonymous cannot see that file value at all. */
  private val fileHidden = resource(noFileId, image, None)

  private val rvSidecar = FairAsset(Some("image/jp2"), Some("secret-file.jp2"), Some(123456L))

  private val project = FairProject(sc, "short", Some("Long"), None, List.empty)

  private def harness(shellText: Option[String]): URIO[ContextStorage & StringFormatter, Harness] =
    for {
      ctxStore  <- ZIO.service[ContextStorage]
      sf        <- ZIO.service[StringFormatter]
      calls     <- Ref.make(0)
      up        <- Ref.make(shellText.isDefined)
      assets    <- FairAssetsInMemory.make(Map("asset-rv" -> rvSidecar))
      resources <- FairResourcesInMemory.make(visible, rvOnly, fileHidden)
      builder    = ResourceFactsBuilder(
                  resources,
                  FairProjectsInMemory(
                    Map(sc         -> project),
                    Map(licenceIri -> FairLicence.Licence("https://creativecommons.org/licenses/by/4.0/", "CC BY")),
                  ),
                  FairClassesInMemory(
                    Map(
                      thing -> FairClass(Map("en" -> "Thing"), requiresFile = false),
                      image -> FairClass(Map("en" -> "Image"), requiresFile = true),
                    ),
                  ),
                  assets,
                  fairConfig,
                  sf,
                )
      stub = new AppShell {
               override def get: UIO[Option[String]] =
                 calls.update(_ + 1) *> up.get.map(isUp => if (isUp) shellText.orElse(Some(shell)) else None)
             }
      endpoints = FairServerEndpoints(FairEndpoints(), FairRestService(builder, stub, fairConfig, api))
      app       = ZioHttpInterpreter(DspApiServer.serverOptions(ctxStore)).toHttp(endpoints.serverEndpoints)
    } yield Harness(
      request =>
        ZIO.scoped(
          for {
            response <- app.runZIO(request)
            body     <- response.body.asString
          } yield Wire(
            response.status.code,
            response.headers.toList.map(h => h.headerName.toLowerCase -> h.renderedValue),
            body,
          ),
        ),
      calls.get,
      up,
    )

  private def withHarness[A](shellText: Option[String] = Some(shell))(f: Harness => Task[A]): Task[A] =
    ZIO
      .scoped(harness(shellText).flatMap(f))
      .provide(OpenTelemetry.contextZIO, StringFormatter.test)

  private def url(p: String): URL = URL.decode(p).getOrElse(throw new IllegalArgumentException(p))

  private def request(
    method: HttpMethod,
    path: String,
    accept: List[String],
    extra: List[Header],
  ): Request =
    (accept.map(a => Header.Custom("Accept", a)) ::: extra)
      .foldLeft(Request(method = method, url = url(path)))(_.addHeader(_))

  private def landingPath(id: String)                                 = s"/fair/resources/081C/$id"
  private def jsonLdPath(id: String)                                  = s"${landingPath(id)}/metadata.jsonld"
  private def dataCitePath(id: String)                                = s"${landingPath(id)}/metadata.datacite.json"
  private def get(h: Harness, p: String, accept: List[String] = Nil)  = h.run(request(HttpMethod.GET, p, accept, Nil))
  private def head(h: Harness, p: String, accept: List[String] = Nil) = h.run(request(HttpMethod.HEAD, p, accept, Nil))

  private val html        = "text/html; charset=UTF-8"
  private val apiBase     = "https://api.example.org:443"
  private val pageUrl     = s"https://app.example.org/resource/081C/$visibleId"
  private val jsonLdUrl   = s"$apiBase${jsonLdPath(visibleId)}"
  private val dataCiteUrl = s"$apiBase${dataCitePath(visibleId)}"

  private val DataCiteType = "application/vnd.datacite.datacite+json"
  private val ScriptOpen   = "<script type=\"application/ld+json\">"

  private def count(haystack: String, needle: String): Int = haystack.sliding(needle.length).count(_ == needle)

  /** The block spliced into the page: everything between the shell's title and its `</head>`. */
  private def splicedBlock(body: String): String =
    body.substring(body.indexOf("</title>") + "</title>".length, body.indexOf("</head>"))

  private def embeddedJson(body: String): Json = {
    val start = body.indexOf(ScriptOpen) + ScriptOpen.length
    body.substring(start, body.indexOf("</script>", start)).fromJson[Json].getOrElse(Json.Null)
  }

  /** `(href, rel, type)` of each value of a `Link` header; hrefs here carry no comma, so ", <" splits the values. */
  private def linkHeaderEntries(value: String): Set[(String, String, String)] = {
    val entry = """<([^>]*)>; rel="([^"]*)"(?:; type="([^"]*)")?""".r
    value
      .split(", (?=<)")
      .toList
      .flatMap {
        case entry(href, rel, tpe) => Some((href, rel, Option(tpe).getOrElse("")))
        case _                     => None
      }
      .toSet
  }

  private def linkElementEntries(body: String): Set[(String, String, String)] = {
    val element = """<link rel="([^"]*)" href="([^"]*)"(?: type="([^"]*)")?>""".r
    element.findAllMatchIn(body).map(m => (m.group(2), m.group(1), Option(m.group(3)).getOrElse(""))).toSet
  }

  private val notVisibleRepresentation = "the representation answer for a resource that is not visible"

  val spec = suite("the FAIR routes over HTTP")(
    suite("4.10 negotiation")(
      test("Accept values that answer the HTML page") {
        val accepts = List(
          Nil,
          List("*/*"),
          List("text/html,*/*;q=0.8"),
          List("text/turtle"),
          List("application/json"),
          List("application/xml"),
          List("application/*"),
          List("text/html;q=1, application/ld+json;q=1"),
          List("*/*;q=0"),
          List(";;;,,,"),
          List("application/ld+json;q=abc"),
        )
        withHarness() { h =>
          ZIO.foreach(accepts)(a => get(h, landingPath(visibleId), a).map(a -> _)).map { answers =>
            assertTrue(
              answers.forall((_, w) => w.status == 200),
              answers.forall((_, w) => w.contentType == List(html)),
              answers.forall((_, w) => w.header("location").isEmpty),
            )
          }
        }
      },
      test("application/ld+json redirects to metadata.jsonld, without a Content-Type") {
        withHarness() { h =>
          get(h, landingPath(visibleId), List("application/ld+json")).map { w =>
            assertTrue(
              w.status == 303,
              w.header("location") == List(jsonLdUrl),
              w.contentType.isEmpty,
              w.body.isEmpty,
            )
          }
        }
      },
      test("the DataCite type redirects to metadata.datacite.json") {
        withHarness() { h =>
          get(h, landingPath(visibleId), List(DataCiteType)).map { w =>
            assertTrue(w.status == 303, w.header("location") == List(dataCiteUrl), w.contentType.isEmpty)
          }
        }
      },
      test("a resource that is not visible answers the unchanged shell to every Accept, never a redirect or a 406") {
        val accepts = List(Nil, List("application/ld+json"), List(DataCiteType), List("text/turtle"), List("*/*"))
        withHarness() { h =>
          ZIO.foreach(accepts)(get(h, landingPath(missingId), _)).map { answers =>
            assertTrue(
              answers.forall(_.status == 200),
              answers.forall(_.body == shell),
              answers.forall(_.contentType == List(html)),
              answers.forall(_.header("location").isEmpty),
              answers.forall(_.header("link").isEmpty),
            )
          }
        }
      },
      test("two Accept lines are combined, not refused as a bad request") {
        withHarness() { h =>
          get(h, landingPath(visibleId), List("text/html", "application/ld+json;q=0.1")).map(w =>
            assertTrue(w.status == 200, w.contentType == List(html)),
          )
        }
      },
      test("the 200 and 303 answers carry Vary: Accept and Cache-Control: no-store, for GET and HEAD") {
        withHarness() { h =>
          for {
            ok        <- get(h, landingPath(visibleId))
            see       <- get(h, landingPath(visibleId), List("application/ld+json"))
            okHead    <- head(h, landingPath(visibleId))
            seeHead   <- head(h, landingPath(visibleId), List("application/ld+json"))
            notShown  <- get(h, landingPath(missingId))
            notShownH <- head(h, landingPath(missingId))
          } yield assertTrue(
            List(ok, see, okHead, seeHead, notShown, notShownH).forall(w =>
              w.header("vary") == List("Accept") && w.header("cache-control") == List("no-store"),
            ),
          )
        }
      },
      test("HEAD carries the same Link, Vary, Cache-Control, Content-Type and Location as GET, with an empty body") {
        withHarness() { h =>
          def same(accept: List[String]) =
            for {
              g  <- get(h, landingPath(visibleId), accept)
              hd <- head(h, landingPath(visibleId), accept)
            } yield List("link", "vary", "cache-control", "content-type", "location").forall(n =>
              g.header(n) == hd.header(n),
            ) && g.status == hd.status && hd.body.isEmpty
          for {
            page     <- same(Nil)
            redirect <- same(List("application/ld+json"))
          } yield assertTrue(page, redirect)
        }
      },
      test("the 200 HTML answer has exactly one Content-Type, text/html; charset=UTF-8") {
        withHarness() { h =>
          get(h, landingPath(visibleId)).map(w => assertTrue(w.contentType == List(html)))
        }
      },
      test("exactly one JSON-LD script and one cite-as, and the DC tags with DC.accessRights") {
        withHarness() { h =>
          get(h, landingPath(visibleId)).map { w =>
            assertTrue(
              count(w.body, ScriptOpen) == 1,
              w.header("link").map(count(_, "rel=\"cite-as\"")) == List(1),
              count(w.body, "rel=\"cite-as\"") == 1,
              w.body.contains("<meta name=\"DC.title\" content=\"A resource\">"),
              w.body.contains("<meta name=\"DC.creator\""),
              w.body.contains("<meta name=\"DC.identifier\""),
              w.body.contains(
                "<meta name=\"DC.accessRights\" content=\"http://purl.org/coar/access_right/c_abf2\">",
              ),
            )
          }
        }
      },
      test("the <link> elements match the Link header relations") {
        withHarness() { h =>
          get(h, landingPath(visibleId)).map { w =>
            val fromHeader = w.header("link").flatMap(linkHeaderEntries).toSet
            assertTrue(fromHeader.nonEmpty, fromHeader == linkElementEntries(w.body))
          }
        }
      },
      test("the body minus the spliced block is the shell") {
        withHarness() { h =>
          get(h, landingPath(visibleId)).map { w =>
            val block = splicedBlock(w.body)
            assertTrue(block.nonEmpty, w.body.replace(block, "") == shell)
          }
        }
      },
      test("Accept absent, */* and text/html give byte-identical answers") {
        withHarness() { h =>
          for {
            none <- get(h, landingPath(visibleId))
            star <- get(h, landingPath(visibleId), List("*/*"))
            text <- get(h, landingPath(visibleId), List("text/html"))
          } yield assertTrue(none.body == star.body, none.body == text.body, none.comparable == text.comparable)
        }
      },
    ),
    suite("4.11 what anonymous cannot see")(
      test("a landing answer for a resource that is not visible is the shell, byte for byte, without Link") {
        withHarness() { h =>
          get(h, landingPath(missingId)).map(w => assertTrue(w.body == shell, w.header("link").isEmpty))
        }
      },
      test(s"$notVisibleRepresentation is the same empty 404 text/plain for missing, malformed id and shortcode") {
        withHarness() { h =>
          for {
            missing  <- get(h, jsonLdPath(missingId))
            badId    <- get(h, jsonLdPath("bad!id"))
            badShort <- get(h, s"/fair/resources/08X3/$visibleId/metadata.jsonld")
            dc       <- get(h, dataCitePath(missingId))
            absent   <- get(h, s"/fair/resources/0001/PHbbrEsVR32q5D_ioKt6pA/metadata.datacite.json")
          } yield assertTrue(
            missing.status == 404,
            missing.contentType == List("text/plain"),
            missing.body.isEmpty,
            List(badId, badShort, dc, absent).forall(_.comparable == missing.comparable),
          )
        }
      },
      test("a V resource with an RV-only file shows no file pointer in any answer") {
        val leaks = List(ingestUrl, "asset-rv", "image/jp2", "secret-file.jp2", "123456")
        withHarness() { h =>
          for {
            page <- get(h, landingPath(rvFileId))
            ld   <- get(h, jsonLdPath(rvFileId))
            dc   <- get(h, dataCitePath(rvFileId))
            all   = List(page, ld, dc)
          } yield assertTrue(
            all.forall(_.status == 200),
            all.forall(w => leaks.forall(l => !w.body.contains(l) && !w.headers.exists(_._2.contains(l)))),
            !ld.body.contains("\"distribution\""),
            !dc.body.contains("\"formats\""),
            !dc.body.contains("\"sizes\""),
            !page.body.contains("DC.format"),
          )
        }
      },
      test("a file anonymous cannot see at all adds no authorship or licence, and the access level is restricted") {
        withHarness() { h =>
          for {
            page <- get(h, landingPath(noFileId))
            ld   <- get(h, jsonLdPath(noFileId))
            dc   <- get(h, dataCitePath(noFileId))
          } yield assertTrue(
            List(page, ld, dc).forall(w => !w.body.contains("File Author") && !w.body.contains("file-licence")),
            List(page, ld, dc).forall(w => !w.body.contains("CC BY")),
            ld.body.contains("\"conditionsOfAccess\":\"Open Access with Restrictions\""),
            page.body.contains("<meta name=\"DC.accessRights\" content=\"http://purl.org/coar/access_right/c_16ec\">"),
          )
        }
      },
    ),
    suite("4.12 credentials")(
      test("a Bearer token and a cookie change nothing on a visible resource, on any route") {
        val credentials = List(
          Header.Custom("Authorization", "Bearer anything-at-all"),
          Header.Custom("Cookie", "KnoraAuthenticationMFYGSLTEMFZQ=abc; other=1"),
        )
        withHarness() { h =>
          def same(method: HttpMethod, path: String, accept: List[String] = Nil) =
            for {
              anonymous <- h.run(request(method, path, accept, Nil))
              known     <- h.run(request(method, path, accept, credentials))
            } yield anonymous.comparable == known.comparable && anonymous.status != 401
          for {
            page   <- same(HttpMethod.GET, landingPath(visibleId))
            see    <- same(HttpMethod.GET, landingPath(visibleId), List("application/ld+json"))
            ld     <- same(HttpMethod.GET, jsonLdPath(visibleId))
            dc     <- same(HttpMethod.GET, dataCitePath(visibleId))
            hidden <- same(HttpMethod.GET, landingPath(missingId))
            ldMiss <- same(HttpMethod.GET, jsonLdPath(missingId))
            shellA <- get(h, landingPath(missingId))
            shellB <- h.run(request(HttpMethod.GET, landingPath(missingId), Nil, credentials))
          } yield assertTrue(
            page,
            see,
            ld,
            dc,
            hidden,
            ldMiss,
            shellB.body == shell,
            shellA.comparable == shellB.comparable,
          )
        }
      },
    ),
    suite("4.13 the shell is unavailable")(
      test("an HTML answer is 502 with Vary and Cache-Control; the representations and a 303 are served") {
        withHarness() { h =>
          for {
            _      <- h.shellUp.set(false)
            page   <- get(h, landingPath(visibleId))
            hidden <- get(h, landingPath(missingId))
            see    <- get(h, landingPath(visibleId), List("application/ld+json"))
            ld     <- get(h, jsonLdPath(visibleId))
            dc     <- get(h, dataCitePath(visibleId))
          } yield assertTrue(
            page.status == 502,
            hidden.status == 502,
            page.header("vary") == List("Accept"),
            page.header("cache-control") == List("no-store"),
            page.body.isEmpty,
            see.status == 303,
            see.header("location") == List(jsonLdUrl),
            ld.status == 200,
            dc.status == 200,
          )
        }
      },
      test("the shell is not asked for a 303 or a representation") {
        withHarness() { h =>
          for {
            _     <- get(h, landingPath(visibleId), List("application/ld+json"))
            _     <- get(h, landingPath(visibleId), List(DataCiteType))
            _     <- get(h, jsonLdPath(visibleId))
            _     <- get(h, dataCitePath(visibleId))
            _     <- get(h, jsonLdPath(missingId))
            calls <- h.shellCalls
            _     <- get(h, landingPath(visibleId))
            after <- h.shellCalls
          } yield assertTrue(calls == 0, after == 1)
        }
      },
    ),
    suite("4.14 representations")(
      test("the .jsonld body is the graph embedded in the page") {
        withHarness() { h =>
          for {
            page <- get(h, landingPath(visibleId))
            ld   <- get(h, jsonLdPath(visibleId))
          } yield assertTrue(
            ld.status == 200,
            ld.contentType == List("application/ld+json"),
            ld.body.fromJson[Json].isRight,
            ld.body.fromJson[Json] == Right(embeddedJson(page.body)),
          )
        }
      },
      test("the DataCite answer is 200 application/vnd.datacite.datacite+json") {
        withHarness() { h =>
          get(h, dataCitePath(visibleId)).map(w =>
            assertTrue(w.status == 200, w.contentType == List(DataCiteType), w.body.fromJson[Json].isRight),
          )
        }
      },
      test("both representations describe the page in a Link header") {
        withHarness() { h =>
          for {
            ld <- get(h, jsonLdPath(visibleId))
            dc <- get(h, dataCitePath(visibleId))
          } yield assertTrue(
            ld.header("link") == List(s"<$pageUrl>; rel=\"describes\""),
            ld.header("link") == dc.header("link"),
          )
        }
      },
    ),
    suite("4.15 query parameters")(
      test("?version= and ?highlightValue= change nothing, also for a value anonymous cannot see") {
        val encoded = java.net.URLEncoder.encode(hiddenValue, "UTF-8")
        val queries = List("?version=20240101T000000Z", "?highlightValue=" + encoded, "?version=2&highlightValue=x")
        withHarness() { h =>
          def unchanged(path: String, accept: List[String] = Nil) =
            for {
              plain <- get(h, path, accept)
              other <- ZIO.foreach(queries)(q => get(h, path + q, accept))
            } yield other.forall(_.comparable == plain.comparable)
          for {
            page    <- unchanged(landingPath(visibleId))
            see     <- unchanged(landingPath(visibleId), List("application/ld+json"))
            ld      <- unchanged(jsonLdPath(visibleId))
            missing <- unchanged(landingPath(missingId))
          } yield assertTrue(page, see, ld, missing)
        }
      },
    ),
  ) @@ TestAspect.sequential
}
