/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.`export`.fair

import org.apache.jena.rdf.model.Model
import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.rdf.model.Resource
import org.apache.jena.riot.Lang
import org.apache.jena.riot.RDFDataMgr
import org.junit.runner.RunWith
import zio.json.*
import zio.test.*

import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList
import scala.jdk.CollectionConverters.*

import org.knora.testrunner.DspZTestJUnitRunner

@RunWith(classOf[DspZTestJUnitRunner])
class SchemaOrgTurtleSpec extends ZIOSpecDefault {

  private val S     = "https://schema.org/"
  private val orcid = "https://orcid.org/0000-0002-1825-0097"

  private val openWithFile = ResourceFairGraph(
    ark = "https://ark.dasch.swiss/ark:/72163/1/0868/abc123",
    pageUrl = "https://app.dasch.swiss/resource/0868/abc123",
    title = "Table 1",
    creators = Seq(
      Creator("Jane Doe", CreatorKind.Person, Some(orcid)),
      Creator("John Roe", CreatorKind.Person, None),
    ),
    dateCreated = Instant.parse("2024-03-01T10:15:30Z"),
    dateModified = Some(Instant.parse("2025-01-02T03:04:05Z")),
    license = Some("https://creativecommons.org/licenses/by/4.0/"),
    copyrightHolder = Some("University of Basel"),
    generalType = "Dataset",
    accessLevel = AccessLevel.FullOpen,
    file = Some(
      FileFacts(
        "https://ingest.dasch.swiss/projects/0868/assets/xyz/original",
        Some("table1.csv"),
        Some("text/csv"),
        Some(1234L),
        Some("https://creativecommons.org/publicdomain/zero/1.0/"),
      ),
    ),
    projectArk = "https://ark.dasch.swiss/ark:/72163/1/0868",
    projectShortcode = "0868",
    projectName = "Example project",
    resourceClassIri = "http://api.dasch.swiss/ontology/0868/example/v2#Table",
  )

  private def parseTurtle(ttl: String): Model = {
    val m = ModelFactory.createDefaultModel()
    RDFDataMgr.read(m, ByteArrayInputStream(ttl.getBytes(StandardCharsets.UTF_8)), Lang.TURTLE)
    m
  }

  private def root(m: Model): Resource   = m.getResource(openWithFile.ark)
  private def p(m: Model, local: String) = m.createProperty(S + local)

  /** Records every URI the JVM's default proxy selector is asked about, i.e. every outgoing HTTP connection attempt. */
  private final class RecordingSelector extends ProxySelector {
    val requested                               = CopyOnWriteArrayList[URI]()
    def select(uri: URI): java.util.List[Proxy] = {
      requested.add(uri): Unit
      java.util.List.of(Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", 1)))
    }
    def connectFailed(uri: URI, sa: SocketAddress, ioe: IOException): Unit = ()
  }

  private def withRecordingSelector[A](f: => A): (A, Seq[URI]) = {
    val previous = ProxySelector.getDefault
    val selector = RecordingSelector()
    ProxySelector.setDefault(selector)
    try (f, selector.requested.asScala.toSeq)
    finally ProxySelector.setDefault(previous)
  }

  val spec: Spec[Any, Nothing] = suite("SchemaOrgTurtle")(
    test("key triples of the open resource with a file") {
      val m    = parseTurtle(SchemaOrgTurtle.render(openWithFile))
      val r    = root(m)
      val lic  = r.getProperty(p(m, "license")).getObject
      val dist = r.getProperty(p(m, "distribution")).getObject.asResource()
      assertTrue(
        r.hasProperty(org.apache.jena.vocabulary.RDF.`type`, m.getResource(S + "Dataset")),
        lic.isURIResource,
        lic.asResource().getURI == "https://creativecommons.org/licenses/by/4.0/",
        r.listProperties(p(m, "identifier")).asScala.size == 2,
        dist.getProperty(p(m, "contentUrl")).getObject.asResource().getURI == openWithFile.file.get.contentUrl,
        dist.getProperty(p(m, "contentSize")).getLiteral.getLong == 1234L,
        r.getProperty(p(m, "isAccessibleForFree")).getLiteral.getBoolean,
        r.hasProperty(m.createProperty("http://www.w3.org/ns/prov#wasAttributedTo"), m.getResource(orcid)),
      )
    },
    test("binds the schema and prov prefixes") {
      val ttl      = SchemaOrgTurtle.render(openWithFile)
      val prefixes = ttl.linesIterator.filter(_.toLowerCase.startsWith("prefix")).map(_.split("\\s+").toList).toSet
      assertTrue(
        prefixes.contains(List("PREFIX", "schema:", "<https://schema.org/>")),
        prefixes.contains(List("PREFIX", "prov:", "<http://www.w3.org/ns/prov#>")),
        prefixes.size == 2,
      )
    },
    test("the Turtle is isomorphic to the JSON-LD parsed with the same inline context") {
      val fromJsonLd = ModelFactory.createDefaultModel()
      RDFDataMgr.read(
        fromJsonLd,
        ByteArrayInputStream(SchemaOrgTurtle.inlineContextJsonLd(openWithFile).toJson.getBytes(StandardCharsets.UTF_8)),
        Lang.JSONLD,
      )
      assertTrue(
        fromJsonLd.size() > 10,
        parseTurtle(SchemaOrgTurtle.render(openWithFile)).isIsomorphicWith(fromJsonLd),
      )
    },
    test("rendering never reaches the network, while the remote context would") {
      val remote               = SchemaOrgJsonLd.toJsonString(openWithFile)
      val (_, controlRequests) = withRecordingSelector {
        val m = ModelFactory.createDefaultModel()
        scala.util.Try(RDFDataMgr.read(m, ByteArrayInputStream(remote.getBytes(StandardCharsets.UTF_8)), Lang.JSONLD))
      }
      val (ttl, renderRequests) = withRecordingSelector(SchemaOrgTurtle.render(openWithFile))
      assertTrue(
        controlRequests.exists(_.getHost == "schema.org"),
        renderRequests.isEmpty,
        ttl.nonEmpty,
      )
    },
  )
}
