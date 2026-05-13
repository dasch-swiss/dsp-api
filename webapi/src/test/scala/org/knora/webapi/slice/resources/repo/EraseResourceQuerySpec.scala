/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.repo

import zio.test.*

import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.slice.admin.domain.model.KnoraProject.*
import org.knora.webapi.slice.api.admin.model.Project
import org.knora.webapi.slice.common.ResourceIri

object EraseResourceQuerySpec extends ZIOSpecDefault {

  private val testProject = Project(
    ProjectIri.unsafeFrom("http://rdfh.ch/projects/0001"),
    Shortname.unsafeFrom("anything"),
    Shortcode.unsafeFrom("0001"),
    None,
    Seq(StringLiteralV2.from("Test project")),
    List.empty,
    None,
    Seq.empty,
    Status.Active,
    SelfJoin.CannotJoin,
    Set.empty,
    Set.empty,
  )

  private val resourceIri = ResourceIri.unsafeFrom("http://rdfh.ch/0001/thing_with_history")

  override def spec: Spec[TestEnvironment, Any] = suite("EraseResourceQuery")(
    test("build should produce the expected SPARQL query") {
      val actual = EraseResourceQuery.build(testProject, resourceIri).getQueryString
      assertTrue(
        actual ==
          """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
            |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
            |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
            |DELETE { GRAPH <http://www.knora.org/data/0001/anything> { <http://rdfh.ch/0001/thing_with_history> ?resourcePred ?resourceObj .
            |?value ?valuePred ?valueObj .
            |?standoff ?standoffPred ?standoffObj . } }
            |WHERE { <http://rdfh.ch/0001/thing_with_history> a ?resourceClass .
            |?resourceClass rdfs:subClassOf* knora-base:Resource .
            |{ <http://rdfh.ch/0001/thing_with_history> ?resourcePred ?resourceObj . } UNION { <http://rdfh.ch/0001/thing_with_history> ?valueProp ?currentValue .
            |?currentValue a ?currentValueClass .
            |?currentValueClass rdfs:subClassOf* knora-base:Value .
            |?currentValue knora-base:previousValue* ?value .
            |?value ?valuePred ?valueObj . } UNION { <http://rdfh.ch/0001/thing_with_history> ?valueProp ?currentTextValue .
            |?currentTextValue a knora-base:TextValue ;
            |    knora-base:previousValue* ?textValue .
            |?textValue knora-base:valueHasStandoff ?standoff .
            |?standoff ?standoffPred ?standoffObj . } }""".stripMargin,
      )
    },
  )
}
