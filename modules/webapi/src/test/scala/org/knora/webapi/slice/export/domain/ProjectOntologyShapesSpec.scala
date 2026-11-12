/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.`export`.domain

import org.apache.jena.rdf.model.Model
import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.vocabulary.RDF
import org.junit.runner.RunWith
import zio.*
import zio.test.*

import java.io.StringReader
import scala.io.Source
import scala.jdk.CollectionConverters.*

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.InternalSchema
import org.knora.webapi.messages.v2.responder.ontologymessages.*
import org.knora.webapi.slice.`export`.domain.ProjectOntologyFixtures.*
import org.knora.webapi.slice.ontology.domain.model.Cardinality.*

@RunWith(classOf[DspZTestJUnitRunner])
class ProjectOntologyShapesSpec extends ZIOSpecDefault {

  private val kb     = "http://www.knora.org/ontology/knora-base#"
  private val anyOnt = "http://www.knora.org/ontology/0001/anything#"

  private val ShNs = "http://www.w3.org/ns/shacl#"

  private val text = kb + "TextValue"

  private val thing = resourceClass(
    anyOnt + "Thing",
    direct = Map(
      anyOnt + "hasExactlyOne"      -> ExactlyOne,
      anyOnt + "hasZeroOrOne"       -> ZeroOrOne,
      anyOnt + "hasAtLeastOne"      -> AtLeastOne,
      anyOnt + "hasUnbounded"       -> Unbounded,
      anyOnt + "hasSubProperty"     -> ZeroOrOne,
      anyOnt + "hasOtherThing"      -> ZeroOrOne,
      anyOnt + "hasOtherThingValue" -> ZeroOrOne,
      kb + "hasComment"             -> Unbounded,
      anyOnt + "hasUndefined"       -> AtLeastOne,
      anyOnt + "hasList"            -> ZeroOrOne,
      anyOnt + "hasPlainList"       -> ZeroOrOne,
    ),
    inherited = Map(anyOnt + "hasInherited" -> ZeroOrOne),
    resourceProps = Set(
      "hasExactlyOne",
      "hasZeroOrOne",
      "hasAtLeastOne",
      "hasUnbounded",
      "hasSubProperty",
      "hasOtherThing",
      "hasOtherThingValue",
      "hasInherited",
      "hasUndefined",
      "hasList",
      "hasPlainList",
    ).map(anyOnt + _) + (kb + "hasComment"),
  )

  private val textValueClass = ReadClassInfoV2(
    ClassInfoContentV2(iri(anyOnt + "SomeValue"), ontologySchema = InternalSchema),
    allBaseClasses = Seq(iri(anyOnt + "SomeValue")),
    isValueClass = true,
  )

  private val projectOntology = ontology(
    anyOnt,
    Seq(thing, textValueClass),
    Seq(
      property(anyOnt + "hasExactlyOne", Some(kb + "IntValue")),
      property(anyOnt + "hasZeroOrOne", Some(text)),
      property(anyOnt + "hasAtLeastOne", Some(text)),
      property(anyOnt + "hasUnbounded", Some(text)),
      property(anyOnt + "hasInherited", Some(text)),
      property(anyOnt + "hasSubProperty", Some(text)),
      property(anyOnt + "hasOtherThing", Some(anyOnt + "Thing")),
      property(anyOnt + "hasOtherThingValue", Some(kb + "LinkValue"), isLinkValueProp = true),
      property(
        anyOnt + "hasList",
        Some(kb + "ListValue"),
        guiAttributes = Seq("hlist=<http://rdfh.ch/lists/0001/treeList>"),
      ),
      property(anyOnt + "hasPlainList", Some(kb + "ListValue")),
      // hasUndefined has no definition in the input
    ),
  )

  private val knoraBase = ontology(
    kb,
    Seq(resourceClass(kb + "Resource"), resourceClass(kb + "Region")),
    Seq(property(kb + "hasComment", Some(text))),
  )

  private def parse(ttl: String): Model = {
    val m = ModelFactory.createDefaultModel()
    m.read(new StringReader(ttl), null, "TURTLE")
    m
  }

  private val expected = parse(
    Source.fromResource("org/knora/webapi/slice/export/domain/ProjectOntologyShapesSpec-expected.ttl").mkString,
  )

  override def spec: Spec[Any, Any] = suite("ProjectOntologyShapes")(
    test("generates one node shape per project resource class, none for built-in ones, and the resource class shape") {
      val actual = parse(ProjectOntologyShapes.generate(List(knoraBase, projectOntology)))
      assertTrue(actual.isIsomorphicWith(expected))
    },
    test("no shape for link value properties") {
      val actual    = parse(ProjectOntologyShapes.generate(List(projectOntology)))
      val valueProp = actual.createResource(anyOnt + "hasOtherThingValue")
      val path      = actual.createProperty(ShNs + "path")
      assertTrue(!actual.contains(null, path, valueProp))
    },
    test("a project without a custom ontology gets only the resource class shape") {
      val actual     = parse(ProjectOntologyShapes.generate(List(knoraBase)))
      val nodeShapes = actual.listSubjectsWithProperty(RDF.`type`, actual.createResource(ShNs + "NodeShape")).asScala
      assertTrue(nodeShapes.map(_.getURI).toList == List(ProjectOntologyShapes.ResourceClassShapeIri))
    },
  )
}
