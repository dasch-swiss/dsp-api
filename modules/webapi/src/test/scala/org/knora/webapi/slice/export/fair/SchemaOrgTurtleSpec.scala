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
import zio.test.*

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import scala.jdk.CollectionConverters.*

import org.knora.testrunner.DspZTestJUnitRunner

@RunWith(classOf[DspZTestJUnitRunner])
class SchemaOrgTurtleSpec extends ZIOSpecDefault {

  import FairGraphFixtures.*

  private val S = "http://schema.org/"

  private def parseTurtle(ttl: String): Model = {
    val m = ModelFactory.createDefaultModel()
    RDFDataMgr.read(m, ByteArrayInputStream(ttl.getBytes(StandardCharsets.UTF_8)), Lang.TURTLE)
    m
  }

  private def root(m: Model, g: ResourceFairGraph): Resource = m.getResource(g.ark)
  private def p(m: Model, local: String)                     = m.createProperty(S + local)

  private val graphs = Seq("bare" -> bare, "restricted" -> restricted, "versioned" -> versioned, "open" -> openWithFile)

  val spec: Spec[Any, Nothing] = suite("SchemaOrgTurtle")(
    test("key triples of the open resource with a file, typed under http://schema.org/") {
      val m    = parseTurtle(SchemaOrgTurtle.render(openWithFile).toOption.get)
      val r    = root(m, openWithFile)
      val lic  = r.getProperty(p(m, "license")).getObject
      val dist = r.getProperty(p(m, "distribution")).getObject.asResource()
      assertTrue(
        r.hasProperty(org.apache.jena.vocabulary.RDF.`type`, m.getResource(S + "Dataset")),
        lic.isURIResource,
        lic.asResource().getURI == ccBy.uri,
        r.listProperties(p(m, "identifier")).asScala.size == 2,
        r.getProperty(p(m, "name")).getString == "Table 1",
        dist.hasProperty(org.apache.jena.vocabulary.RDF.`type`, m.getResource(S + "DataDownload")),
        dist.getProperty(p(m, "contentUrl")).getString == openWithFile.file.get.contentUrl,
        dist.getProperty(p(m, "contentSize")).getLiteral.getLong == 1234L,
        r.getProperty(p(m, "isAccessibleForFree")).getLiteral.getBoolean,
        r.hasProperty(m.createProperty("http://www.w3.org/ns/prov#wasAttributedTo"), m.getResource(orcid)),
      )
    },
    test("every graph renders") {
      assertTrue(graphs.forall { case (_, g) => SchemaOrgTurtle.render(g).isRight })
    },
    test("restricted, versioned and bare graphs carry no distribution; bare carries no license") {
      val models = Seq(restricted, versioned, bare).map(g => g -> parseTurtle(SchemaOrgTurtle.render(g).toOption.get))
      assertTrue(
        models.forall { case (g, m) => !root(m, g).hasProperty(p(m, "distribution")) },
        models.forall { case (g, m) => root(m, g).hasProperty(p(m, "isAccessibleForFree")) },
        !root(models.last._2, bare).hasProperty(p(models.last._2, "license")),
        !root(models.head._2, restricted).getProperty(p(models.head._2, "isAccessibleForFree")).getBoolean,
      )
    },
    test("binds only the schema and prov prefixes") {
      val ttl      = SchemaOrgTurtle.render(openWithFile).toOption.get
      val prefixes = ttl.linesIterator.filter(_.toLowerCase.startsWith("prefix")).map(_.split("\\s+").toList).toSet
      assertTrue(
        prefixes.contains(List("PREFIX", "schema:", "<http://schema.org/>")),
        prefixes.contains(List("PREFIX", "prov:", "<http://www.w3.org/ns/prov#>")),
        prefixes.size == 2,
      )
    },
    test("the JSON-LD parsed through the classpath context equals the Turtle model") {
      val results = graphs.map { case (_, g) =>
        for {
          fromJsonLd <- SchemaOrgTurtle.parseJsonLd(SchemaOrgJsonLd.toJsonString(g))
          ttl        <- SchemaOrgTurtle.render(g)
        } yield fromJsonLd.size() > 10 && parseTurtle(ttl).isIsomorphicWith(fromJsonLd)
      }
      assertTrue(results.forall(_ == Right(true)))
    },
    test("only schema.org's context is resolvable; any other remote context is refused without a fetch") {
      val foreign = """{"@context": "https://example.invalid/context.jsonld", "@id": "https://x.org/a", "name": "A"}"""
      assertTrue(
        SchemaOrgTurtle.parseJsonLd(foreign).isLeft,
        SchemaOrgTurtle
          .parseJsonLd("""{"@context": "https://schema.org", "@id": "https://x.org/a", "name": "A"}""")
          .isRight,
      )
    },
  )
}
