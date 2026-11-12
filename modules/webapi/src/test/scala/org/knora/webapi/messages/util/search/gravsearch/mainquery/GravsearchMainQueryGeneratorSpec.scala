/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.util.search.gravsearch.mainquery

import org.apache.jena.query.QueryFactory
import org.junit.runner.RunWith
import zio.test.Spec
import zio.test.ZIOSpecDefault
import zio.test.assertTrue

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.ApiV2Complex
import org.knora.webapi.MarkupRendering
import org.knora.webapi.Rendering
import org.knora.webapi.ServeStoredXml
import org.knora.webapi.StandoffSelection
import org.knora.webapi.messages.IriConversions.*
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.util.rdf.SparqlSelectResult
import org.knora.webapi.messages.util.rdf.SparqlSelectResultBody
import org.knora.webapi.messages.util.rdf.SparqlSelectResultHeader
import org.knora.webapi.messages.util.rdf.VariableResultsRow
import org.knora.webapi.messages.util.search.IriRef
import org.knora.webapi.messages.util.search.QueryVariable
import org.knora.webapi.slice.common.ResourceIri

@RunWith(classOf[DspZTestJUnitRunner])
class GravsearchMainQueryGeneratorSpec extends ZIOSpecDefault {

  private val mainResourceVar = QueryVariable("mainRes")

  private def prequeryResultBindingMainResTo(values: String*): SparqlSelectResult =
    SparqlSelectResult(
      SparqlSelectResultHeader(Seq(mainResourceVar.variableName)),
      SparqlSelectResultBody(values.map(v => VariableResultsRow(Map(mainResourceVar.variableName -> v)))),
    )

  private given StringFormatter = StringFormatter.getInitializedTestInstance

  private def canonical(query: String): String = {
    val parsed = QueryFactory.create(query)
    parsed.getPrefixMapping.clearNsPrefixMap()
    parsed.toString
  }

  private val mainResourceIri = "http://rdfh.ch/0001/resource1"
  private val valueIri        = "http://rdfh.ch/0001/resource1/values/value1"

  private def renderMainQuery(schemaOptions: Set[Rendering]): String =
    GravsearchMainQueryGenerator
      .createMainQuery(
        Set(IriRef(mainResourceIri.toSmartIri)),
        Set.empty,
        Set(valueIri),
        StandoffSelection.from(ApiV2Complex, schemaOptions),
      )
      .toSparql

  // @formatter:off
  private val expectedMainQueryNoStandoff =
    """|CONSTRUCT {
       |?mainResourceVar <http://www.knora.org/ontology/knora-base#isMainResource> "true"^^<http://www.w3.org/2001/XMLSchema#boolean> .
       |?mainAndDependentResource <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://www.knora.org/ontology/knora-base#Resource> .
       |?mainAndDependentResource ?mainAndDependentResourceProp ?mainAndDependentResourceObj .
       |?mainAndDependentResource <http://www.knora.org/ontology/knora-base#hasValue> ?mainAndDependentResourceValueObject .
       |?mainAndDependentResource ?mainAndDependentResourceValueProp ?mainAndDependentResourceValueObject .
       |?mainAndDependentResourceValueObject ?mainAndDependentResourceValueObjectProp ?mainAndDependentResourceValueObjectObj .
       |} WHERE {
       |{
       |VALUES ?mainResourceVar { <http://rdfh.ch/0001/resource1> }
       |?mainResourceVar <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://www.knora.org/ontology/knora-base#Resource> .
       |?mainResourceVar <http://www.knora.org/ontology/knora-base#isDeleted> "false"^^<http://www.w3.org/2001/XMLSchema#boolean> .
       |} UNION {
       |VALUES ?mainAndDependentResource { <http://rdfh.ch/0001/resource1> }
       |?mainAndDependentResource <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://www.knora.org/ontology/knora-base#Resource> .
       |?mainAndDependentResource <http://www.knora.org/ontology/knora-base#isDeleted> "false"^^<http://www.w3.org/2001/XMLSchema#boolean> .
       |?mainAndDependentResource ?mainAndDependentResourceProp ?mainAndDependentResourceObj .
       |} UNION {
       |VALUES ?mainAndDependentResourceValueObject { <http://rdfh.ch/0001/resource1/values/value1> }
       |?mainAndDependentResource <http://www.knora.org/ontology/knora-base#hasValue> ?mainAndDependentResourceValueObject .
       |?mainAndDependentResource ?mainAndDependentResourceValueProp ?mainAndDependentResourceValueObject .
       |?mainAndDependentResourceValueObject <http://www.knora.org/ontology/knora-base#isDeleted> "false"^^<http://www.w3.org/2001/XMLSchema#boolean> .
       |?mainAndDependentResourceValueObject ?mainAndDependentResourceValueObjectProp ?mainAndDependentResourceValueObjectObj .
       |FILTER((?mainAndDependentResourceValueObjectProp != <http://www.knora.org/ontology/knora-base#valueHasStandoff>))
       |}
       |}""".stripMargin

  private val expectedMainQueryAllStandoff =
    """|CONSTRUCT {
       |?mainResourceVar <http://www.knora.org/ontology/knora-base#isMainResource> "true"^^<http://www.w3.org/2001/XMLSchema#boolean> .
       |?mainAndDependentResource <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://www.knora.org/ontology/knora-base#Resource> .
       |?mainAndDependentResource ?mainAndDependentResourceProp ?mainAndDependentResourceObj .
       |?mainAndDependentResource <http://www.knora.org/ontology/knora-base#hasValue> ?mainAndDependentResourceValueObject .
       |?mainAndDependentResource ?mainAndDependentResourceValueProp ?mainAndDependentResourceValueObject .
       |?mainAndDependentResourceValueObject ?mainAndDependentResourceValueObjectProp ?mainAndDependentResourceValueObjectObj .
       |?mainAndDependentResourceValueObject <http://www.knora.org/ontology/knora-base#valueHasStandoff> ?standoffNode .
       |?standoffNode ?standoffProp ?standoffValue .
       |?standoffNode <http://www.knora.org/ontology/knora-base#targetHasOriginalXMLID> ?targetOriginalXMLID .
       |} WHERE {
       |{
       |VALUES ?mainResourceVar { <http://rdfh.ch/0001/resource1> }
       |?mainResourceVar <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://www.knora.org/ontology/knora-base#Resource> .
       |?mainResourceVar <http://www.knora.org/ontology/knora-base#isDeleted> "false"^^<http://www.w3.org/2001/XMLSchema#boolean> .
       |} UNION {
       |VALUES ?mainAndDependentResource { <http://rdfh.ch/0001/resource1> }
       |?mainAndDependentResource <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://www.knora.org/ontology/knora-base#Resource> .
       |?mainAndDependentResource <http://www.knora.org/ontology/knora-base#isDeleted> "false"^^<http://www.w3.org/2001/XMLSchema#boolean> .
       |?mainAndDependentResource ?mainAndDependentResourceProp ?mainAndDependentResourceObj .
       |} UNION {
       |VALUES ?mainAndDependentResourceValueObject { <http://rdfh.ch/0001/resource1/values/value1> }
       |?mainAndDependentResource <http://www.knora.org/ontology/knora-base#hasValue> ?mainAndDependentResourceValueObject .
       |?mainAndDependentResource ?mainAndDependentResourceValueProp ?mainAndDependentResourceValueObject .
       |?mainAndDependentResourceValueObject <http://www.knora.org/ontology/knora-base#isDeleted> "false"^^<http://www.w3.org/2001/XMLSchema#boolean> .
       |?mainAndDependentResourceValueObject ?mainAndDependentResourceValueObjectProp ?mainAndDependentResourceValueObjectObj .
       |FILTER((?mainAndDependentResourceValueObjectProp != <http://www.knora.org/ontology/knora-base#valueHasStandoff>))
       |} UNION {
       |VALUES ?mainAndDependentResourceValueObject { <http://rdfh.ch/0001/resource1/values/value1> }
       |?mainAndDependentResourceValueObject <http://www.knora.org/ontology/knora-base#valueHasStandoff> ?standoffNode .
       |?standoffNode ?standoffProp ?standoffValue .
       |?standoffNode <http://www.knora.org/ontology/knora-base#standoffTagHasStartIndex> ?startIndex .
       |OPTIONAL {
       |?standoffNode <http://www.knora.org/ontology/knora-base#standoffTagHasInternalReference> ?targetStandoffTag .
       |?targetStandoffTag <http://www.knora.org/ontology/knora-base#standoffTagHasOriginalXMLID> ?targetOriginalXMLID .
       |}
       |FILTER((?startIndex >= "0"^^<http://www.w3.org/2001/XMLSchema#integer>))
       |}
       |}""".stripMargin

  private val expectedMainQueryOnlyWithoutStoredXml =
    """|CONSTRUCT {
       |?mainResourceVar <http://www.knora.org/ontology/knora-base#isMainResource> "true"^^<http://www.w3.org/2001/XMLSchema#boolean> .
       |?mainAndDependentResource <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://www.knora.org/ontology/knora-base#Resource> .
       |?mainAndDependentResource ?mainAndDependentResourceProp ?mainAndDependentResourceObj .
       |?mainAndDependentResource <http://www.knora.org/ontology/knora-base#hasValue> ?mainAndDependentResourceValueObject .
       |?mainAndDependentResource ?mainAndDependentResourceValueProp ?mainAndDependentResourceValueObject .
       |?mainAndDependentResourceValueObject ?mainAndDependentResourceValueObjectProp ?mainAndDependentResourceValueObjectObj .
       |?mainAndDependentResourceValueObject <http://www.knora.org/ontology/knora-base#valueHasStandoff> ?standoffNode .
       |?standoffNode ?standoffProp ?standoffValue .
       |?standoffNode <http://www.knora.org/ontology/knora-base#targetHasOriginalXMLID> ?targetOriginalXMLID .
       |} WHERE {
       |{
       |VALUES ?mainResourceVar { <http://rdfh.ch/0001/resource1> }
       |?mainResourceVar <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://www.knora.org/ontology/knora-base#Resource> .
       |?mainResourceVar <http://www.knora.org/ontology/knora-base#isDeleted> "false"^^<http://www.w3.org/2001/XMLSchema#boolean> .
       |} UNION {
       |VALUES ?mainAndDependentResource { <http://rdfh.ch/0001/resource1> }
       |?mainAndDependentResource <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://www.knora.org/ontology/knora-base#Resource> .
       |?mainAndDependentResource <http://www.knora.org/ontology/knora-base#isDeleted> "false"^^<http://www.w3.org/2001/XMLSchema#boolean> .
       |?mainAndDependentResource ?mainAndDependentResourceProp ?mainAndDependentResourceObj .
       |} UNION {
       |VALUES ?mainAndDependentResourceValueObject { <http://rdfh.ch/0001/resource1/values/value1> }
       |?mainAndDependentResource <http://www.knora.org/ontology/knora-base#hasValue> ?mainAndDependentResourceValueObject .
       |?mainAndDependentResource ?mainAndDependentResourceValueProp ?mainAndDependentResourceValueObject .
       |?mainAndDependentResourceValueObject <http://www.knora.org/ontology/knora-base#isDeleted> "false"^^<http://www.w3.org/2001/XMLSchema#boolean> .
       |?mainAndDependentResourceValueObject ?mainAndDependentResourceValueObjectProp ?mainAndDependentResourceValueObjectObj .
       |FILTER((?mainAndDependentResourceValueObjectProp != <http://www.knora.org/ontology/knora-base#valueHasStandoff>))
       |} UNION {
       |VALUES ?mainAndDependentResourceValueObject { <http://rdfh.ch/0001/resource1/values/value1> }
       |{
       |?mainAndDependentResourceValueObject <http://www.knora.org/ontology/knora-base#valueHasStandoff> ?standoffNode .
       |FILTER NOT EXISTS {
       | ?mainAndDependentResourceValueObject <http://www.knora.org/ontology/knora-base#valueHasXml> ?storedXml .
       |
       |}
       |}
       |?standoffNode ?standoffProp ?standoffValue .
       |?standoffNode <http://www.knora.org/ontology/knora-base#standoffTagHasStartIndex> ?startIndex .
       |OPTIONAL {
       |?standoffNode <http://www.knora.org/ontology/knora-base#standoffTagHasInternalReference> ?targetStandoffTag .
       |?targetStandoffTag <http://www.knora.org/ontology/knora-base#standoffTagHasOriginalXMLID> ?targetOriginalXMLID .
       |}
       |FILTER((?startIndex >= "0"^^<http://www.w3.org/2001/XMLSchema#integer>))
       |}
       |}""".stripMargin
  // @formatter:on

  val spec: Spec[Any, Any] =
    suite("GravsearchMainQueryGenerator.getValueObjectVarsAndIrisPerMainResource")(
      test(
        "skip rows whose main-resource variable is bound to a non-resource IRI (e.g. a LinkValue) rather than throwing (DEV-6604)",
      ) {
        // For variable-predicate incoming-link searches the prequery can bind the main-resource variable to a
        // LinkValue node (`.../values/...`); the spurious row must be dropped rather than reaching
        // `ResourceIri.unsafeFrom`.
        val resourceIri = "http://rdfh.ch/0828/eawbn9g4QVKouqfB8n0WiA"
        val valueIri    = "http://rdfh.ch/0828/qMBgsLpPTMSHv9FYfG1Zcg/values/9KeFJfrLTjmPhB3158J65Q"

        val result = GravsearchMainQueryGenerator.getValueObjectVarsAndIrisPerMainResource(
          prequeryResponse = prequeryResultBindingMainResTo(resourceIri, valueIri),
          valueObjectVariablesConcat = Set.empty,
          mainResourceVar = mainResourceVar,
        )

        assertTrue(
          result.valueObjectVariablesAndValueObjectIris.keySet == Set(ResourceIri.unsafeFrom(resourceIri)),
        )
      },
      test("keep all rows when every main-resource binding is a valid resource IRI") {
        val resourceIri1 = "http://rdfh.ch/0828/eawbn9g4QVKouqfB8n0WiA"
        val resourceIri2 = "http://rdfh.ch/0810/0Y_87NRETpiNRp_Ujx-NHw"

        val result = GravsearchMainQueryGenerator.getValueObjectVarsAndIrisPerMainResource(
          prequeryResponse = prequeryResultBindingMainResTo(resourceIri1, resourceIri2),
          valueObjectVariablesConcat = Set.empty,
          mainResourceVar = mainResourceVar,
        )

        assertTrue(
          result.valueObjectVariablesAndValueObjectIris.keySet ==
            Set(ResourceIri.unsafeFrom(resourceIri1), ResourceIri.unsafeFrom(resourceIri2)),
        )
      },
      test("createMainQuery - NoStandoff") {
        assertTrue(
          canonical(renderMainQuery(Set(MarkupRendering.Standoff))) == canonical(expectedMainQueryNoStandoff),
        )
      },
      test("createMainQuery - AllStandoff") {
        assertTrue(canonical(renderMainQuery(Set.empty)) == canonical(expectedMainQueryAllStandoff))
      },
      test("createMainQuery - OnlyWithoutStoredXml") {
        assertTrue(
          canonical(renderMainQuery(Set(ServeStoredXml))) == canonical(expectedMainQueryOnlyWithoutStoredXml),
        )
      },
    )
}
