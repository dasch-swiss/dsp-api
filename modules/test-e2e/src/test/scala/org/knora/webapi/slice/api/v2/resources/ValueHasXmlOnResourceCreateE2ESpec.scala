/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v2.resources

import org.apache.jena.query.Dataset
import org.apache.jena.riot.Lang
import org.junit.runner.RunWith
import zio.*
import zio.nio.file.Files as ZFiles
import zio.nio.file.Path as ZPath
import zio.test.*

import java.nio.file.Files
import java.nio.file.Path as JPath
import scala.jdk.CollectionConverters.*

import org.knora.bagit.BagIt
import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.E2EZSpec
import org.knora.webapi.e2ez.ProjectExportTestSupport.downloadExport
import org.knora.webapi.e2ez.ProjectExportTestSupport.withCompletedExport
import org.knora.webapi.e2ez.ValueHasXmlTestSupport.*
import org.knora.webapi.slice.common.StandoffMappingIri
import org.knora.webapi.slice.common.jena.DatasetOps

/**
 * A formatted text value written through `POST /v2/resources` stores `kb:valueHasXml`, byte-identical to the
 * `textValueAsXml` the API serves, and the raw project dump carries it.
 */
@RunWith(classOf[DspZTestJUnitRunner])
class ValueHasXmlOnResourceCreateE2ESpec extends E2EZSpec {

  private val standard = StandoffMappingIri.StandardMapping

  // --- Raw project dump (POST /v3/projects/{iri}/exports) ----------------------------------------

  /** All `valueHasXml` literals of `valueIri` in the `rdf/data.nq` of a fresh project dump. */
  private def dumpedValueHasXml(valueIri: String) =
    withCompletedExport(projectIri, skipAssets = true) { exportId =>
      for {
        bytes <- downloadExport(projectIri, exportId)
        xmls  <- ZIO.scoped {
                  for {
                    tempDir <- ZFiles.createTempDirectoryScoped(Some("value-has-xml-dump"), Seq.empty)
                    zipPath  = tempDir / "export.zip"
                    _       <- ZFiles.writeBytes(zipPath, Chunk.fromArray(bytes))
                    bagRoot <- BagIt
                                 .readAndValidateZip(zipPath)
                                 .map(_._2)
                                 .mapError(e => new RuntimeException(s"Invalid BagIt zip: $e"))
                    dataNq <- findDataNq(bagRoot.toFile.toPath)
                    ds     <- DatasetOps.from(ZPath.fromJava(dataNq), Lang.NQUADS)
                    xmls   <- ZIO.attempt(xmlLiterals(ds, valueIri))
                  } yield xmls
                }
      } yield xmls
    }

  private def findDataNq(bagRoot: JPath): Task[JPath] =
    ZIO
      .scoped(
        ZIO
          .fromAutoCloseable(ZIO.attemptBlocking(Files.walk(bagRoot)))
          .flatMap(files => ZIO.attempt(files.iterator().asScala.find(_.endsWith("rdf/data.nq")))),
      )
      .someOrFail(new RuntimeException(s"The project dump contains no rdf/data.nq below $bagRoot"))

  private def xmlLiterals(ds: Dataset, valueIri: String): List[String] = {
    val model = org.apache.jena.rdf.model.ModelFactory.createDefaultModel()
    model.add(ds.getDefaultModel)
    ds.listNames().asScala.foreach(name => model.add(ds.getNamedModel(name)))
    model
      .listObjectsOfProperty(model.createResource(valueIri), model.createProperty(s"${kb}valueHasXml"))
      .asScala
      .map(_.asLiteral().getLexicalForm)
      .toList
  }

  override val e2eSpec: Spec[env, Any] = suite("valueHasXml written by POST /v2/resources")(
    test("a formatted value stores valueHasXml byte-identical to the served textValueAsXml") {
      for {
        resource <-
          createResourceWithText(xmlDoc("<p>no. 1: <strong>two</strong>, (3); <em>4</em>?</p>"), standard)
        value  <- currentValueOf(resource)
        result <- storedByteIdenticalToServed(resource, value)
      } yield result
    },
    test("a custom mapping stores valueHasXml byte-identical to the served textValueAsXml") {
      for {
        suffix  <- Random.nextUUID.map(_.toString.take(8))
        mapping <- createCustomMapping(s"OnCreateCustom$suffix")
        xml      =
          xmlDoc(
            """<p>B <span class="event" data-description="a &quot;gala&quot;" data-date="GREGORIAN:2018-03-14 CE">soiree</span> on 14.3.</p>""",
          )
        resource <- createResourceWithText(xml, mapping)
        value    <- currentValueOf(resource)
        result   <- storedByteIdenticalToServed(resource, value)
      } yield result
    },
    test("exactly the formatted value of a resource carries valueHasXml, the plain sibling has none") {
      for {
        resource <- createThing(
                      plainTextJson("plain 7 + 8 = 15; no markup"),
                      textValueJson(xmlDoc("<p>marked <em>9</em> up, 10.</p>"), standard, None),
                    )
        values    <- textValuesOf(resource)
        xmls      <- ZIO.foreach(values)(storedXml)
        withXml   <- ZIO.filter(values)(v => storedXml(v).map(_.isDefined))
        plainIris <- ZIO.filter(values)(v => valueHasString(v).map(_.contains("plain 7 + 8 = 15; no markup")))
      } yield assertTrue(
        values.size == 2,
        withXml.size == 1,
        plainIris.size == 1,
        withXml.forall(!plainIris.contains(_)),
        xmls.flatten.forall(_.contains("marked")),
      )
    },
    test("the raw project dump carries valueHasXml of a value created with its resource") {
      for {
        resource <- createResourceWithText(xmlDoc("<p>dump 11: <strong>twelve</strong> (13)</p>"), standard)
        value    <- currentValueOf(resource)
        stored   <- storedXml(value).someOrFail(new RuntimeException("no valueHasXml stored"))
        dumped   <- dumpedValueHasXml(value)
      } yield assertTrue(stored.nonEmpty, dumped == List(stored))
    },
  )
}
