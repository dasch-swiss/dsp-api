/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.`export`.domain

import org.junit.runner.RunWith
import zio.*
import zio.test.*
import zio.test.Assertion.*

import org.knora.shacl.RdfData
import org.knora.shacl.RdfGraphs
import org.knora.shacl.ShaclShapes
import org.knora.shacl.ShaclValidator
import org.knora.testrunner.DspZTestJUnitRunner

/** The real knora-base ontology and data-shapes fixtures, read once from the classpath and shared across tests. */
final case class RealShaclFixtures(knoraBaseTtl: String, dataShapesTtl: String)

/**
 * DEV-7325 regression: knora-base:valueHasXml must not trip up the production SHACL shapes.
 * data-shapes.ttl has no TextValue-specific sh:property shape for it and is not sh:closed, so a
 * TextValue instance that is otherwise valid must validate clean whether or not it carries the
 * predicate. Runs the same knora-base.ttl (v58) + shacl/data-shapes.ttl fixtures, through the same
 * in-JVM TopBraid/Jena engine, that ProjectMigrationImportValidator uses for project imports.
 */
@RunWith(classOf[DspZTestJUnitRunner])
class ValueHasXmlShaclRegressionSpec extends ZIOSpecDefault {

  private val KnoraBase  = "http://www.knora.org/ontology/knora-base#"
  private val KnoraAdmin = "http://www.knora.org/ontology/knora-admin#"
  private val Xsd        = "http://www.w3.org/2001/XMLSchema#"
  private val RdfType    = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type"

  private val UserIri  = "http://rdfh.ch/users/test001"
  private val ValueIri = "http://rdfh.ch/9999/thing001/values/val001"

  private val userTtl =
    s"""<$UserIri> <$RdfType> <${KnoraAdmin}User> .
       |""".stripMargin

  // Every predicate knora-base:Value-Shape requires (valueCreationDate, attachedToUser,
  // hasPermissions, isDeleted, valueHasString), so a validation failure can only be
  // attributed to the extra triples appended by the caller.
  private def textValueTtl(extraTriples: String): String =
    s"""<$ValueIri> <$RdfType> <${KnoraBase}TextValue> .
       |<$ValueIri> <${KnoraBase}valueCreationDate> "2024-01-01T00:00:00Z"^^<${Xsd}dateTime> .
       |<$ValueIri> <${KnoraBase}attachedToUser> <$UserIri> .
       |<$ValueIri> <${KnoraBase}isDeleted> "false"^^<${Xsd}boolean> .
       |<$ValueIri> <${KnoraBase}hasPermissions> "CR knora-admin:ProjectAdmin"^^<${Xsd}string> .
       |<$ValueIri> <${KnoraBase}valueHasString> "value string"^^<${Xsd}string> .
       |$extraTriples""".stripMargin

  private val valueHasXmlTriple =
    s"""<$ValueIri> <${KnoraBase}valueHasXml> "<text>hello <strong>world</strong></text>"^^<${Xsd}string> .
       |""".stripMargin

  private val FormattedText       = s"${KnoraBase}FormattedText"
  private val CustomFormattedText = s"${KnoraBase}CustomFormattedText"
  private val UnformattedText     = s"${KnoraBase}UnformattedText"

  private def textValueTypeTriple(typeIri: String): String =
    s"""<$ValueIri> <${KnoraBase}hasTextValueType> <$typeIri> .
       |""".stripMargin

  private def readClasspathResource(path: String): Task[String] =
    ZIO.attemptBlocking {
      val is = getClass.getClassLoader.getResourceAsStream(path)
      if (is == null) throw new RuntimeException(s"Classpath resource '$path' not found")
      try new String(is.readAllBytes(), "UTF-8")
      finally is.close()
    }

  private val fixturesLayer: TaskLayer[RealShaclFixtures] = ZLayer.fromZIO {
    for {
      knoraBaseTtl  <- readClasspathResource("knora-ontologies/knora-base.ttl")
      dataShapesTtl <- readClasspathResource("shacl/data-shapes.ttl")
    } yield RealShaclFixtures(knoraBaseTtl, dataShapesTtl)
  }

  private val noOpShapes = RdfData.InMemoryTurtle("", "urn:no-op-shapes")

  private val knoraBaseGraphIri = "http://www.knora.org/ontology/knora-base"

  private def validate(dataTtl: String) =
    ZIO.serviceWithZIO[RealShaclFixtures] { fixtures =>
      ShaclValidator
        .validate(
          graphs = RdfGraphs(
            ontologies = NonEmptyChunk(RdfData.InMemoryTurtle(fixtures.knoraBaseTtl, knoraBaseGraphIri)),
            data = NonEmptyChunk(RdfData.InMemoryTurtle(userTtl + dataTtl, "urn:test-data")),
          ),
          shapes = ShaclShapes(
            ontologyShapes = NonEmptyChunk(noOpShapes),
            dataShapes = NonEmptyChunk(RdfData.InMemoryTurtle(fixtures.dataShapesTtl, "urn:data-shapes")),
          ),
        )
        .either
    }

  override def spec: Spec[Any, Any] = suite("ValueHasXmlShaclRegressionSpec")(
    test("a FormattedText value carrying knora-base:valueHasXml validates clean") {
      validate(textValueTtl(textValueTypeTriple(FormattedText) + valueHasXmlTriple))
        .map(result => assert(result)(isRight))
    },
    test("a CustomFormattedText value carrying knora-base:valueHasXml validates clean") {
      validate(textValueTtl(textValueTypeTriple(CustomFormattedText) + valueHasXmlTriple))
        .map(result => assert(result)(isRight))
    },
    test("an UnformattedText value carrying knora-base:valueHasXml fails validation") {
      validate(textValueTtl(textValueTypeTriple(UnformattedText) + valueHasXmlTriple))
        .map(result => assert(result)(isLeft))
    },
    test("a value carrying knora-base:valueHasXml with no hasTextValueType validates clean") {
      validate(textValueTtl(valueHasXmlTriple)).map(result => assert(result)(isRight))
    },
    test("an UnformattedText value without knora-base:valueHasXml validates clean") {
      validate(textValueTtl(textValueTypeTriple(UnformattedText))).map(result => assert(result)(isRight))
    },
    test("a value without hasTextValueType and without knora-base:valueHasXml validates clean") {
      validate(textValueTtl("")).map(result => assert(result)(isRight))
    },
    test("a value missing a required Value predicate fails (harness sanity check)") {
      val incomplete =
        s"""<$ValueIri> <$RdfType> <${KnoraBase}TextValue> .
           |<$ValueIri> <${KnoraBase}valueCreationDate> "2024-01-01T00:00:00Z"^^<${Xsd}dateTime> .
           |<$ValueIri> <${KnoraBase}attachedToUser> <$UserIri> .
           |<$ValueIri> <${KnoraBase}isDeleted> "false"^^<${Xsd}boolean> .
           |<$ValueIri> <${KnoraBase}valueHasString> "value string"^^<${Xsd}string> .
           |""".stripMargin // knora-base:hasPermissions omitted on purpose; unformatted, no valueHasXml
      validate(incomplete).map(result => assert(result)(isLeft))
    },
  ).provideLayerShared(fixturesLayer)
}
