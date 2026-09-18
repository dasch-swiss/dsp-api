/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.triplestore.upgrade.plugins

import org.apache.jena.query.DatasetFactory
import org.apache.jena.rdf.model.Model
import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.rdf.model.RDFNode
import org.apache.jena.rdf.model.ResourceFactory
import org.apache.jena.update.UpdateAction
import org.apache.jena.update.UpdateFactory
import org.apache.jena.vocabulary.RDF
import org.junit.runner.RunWith
import zio.test.Spec
import zio.test.ZIOSpecDefault
import zio.test.assertTrue

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.slice.admin.domain.model.CopyrightHolder
import org.knora.webapi.slice.admin.domain.model.IsDaschRecommended.Yes
import org.knora.webapi.slice.admin.domain.model.License
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Update

@RunWith(classOf[DspZTestJUnitRunner])
class UpgradePluginPR3612Spec extends ZIOSpecDefault {

  private val adminGraph = "http://www.knora.org/data/admin"
  private val ka         = "http://www.knora.org/ontology/knora-admin#"

  private val legacyCopyrightHolder =
    """|PREFIX knora-admin: <http://www.knora.org/ontology/knora-admin#>
       |WITH <http://www.knora.org/data/admin>
       |DELETE { ?projectIri a knora-admin:knoraProject ;
       |    knora-admin:hasAllowedCopyrightHolder "AI-Generated Content - Not Protected by Copyright" ;
       |    knora-admin:hasAllowedCopyrightHolder "Public Domain - Not Protected by Copyright" . }
       |INSERT { ?projectIri a knora-admin:knoraProject ;
       |    knora-admin:hasAllowedCopyrightHolder "AI-Generated Content - Not Protected by Copyright" ;
       |    knora-admin:hasAllowedCopyrightHolder "Public Domain - Not Protected by Copyright" . }
       |WHERE { ?projectIri a knora-admin:knoraProject .
       |FILTER NOT EXISTS { ?projectIri knora-admin:hasAllowedCopyrightHolder "AI-Generated Content - Not Protected by Copyright" . }
       |FILTER NOT EXISTS { ?projectIri knora-admin:hasAllowedCopyrightHolder "Public Domain - Not Protected by Copyright" . } }""".stripMargin

  private val legacyEnabledLicenses =
    """|PREFIX knora-admin: <http://www.knora.org/ontology/knora-admin#>
       |WITH <http://www.knora.org/data/admin>
       |DELETE { ?projectIri a knora-admin:knoraProject ;
       |    knora-admin:hasEnabledLicense <http://rdfh.ch/licenses/cc-by-4.0> ;
       |    knora-admin:hasEnabledLicense <http://rdfh.ch/licenses/cc-by-sa-4.0> ;
       |    knora-admin:hasEnabledLicense <http://rdfh.ch/licenses/cc-by-nc-4.0> ;
       |    knora-admin:hasEnabledLicense <http://rdfh.ch/licenses/cc-by-nc-sa-4.0> ;
       |    knora-admin:hasEnabledLicense <http://rdfh.ch/licenses/cc-by-nd-4.0> ;
       |    knora-admin:hasEnabledLicense <http://rdfh.ch/licenses/cc-by-nc-nd-4.0> ;
       |    knora-admin:hasEnabledLicense <http://rdfh.ch/licenses/ai-generated> ;
       |    knora-admin:hasEnabledLicense <http://rdfh.ch/licenses/unknown> ;
       |    knora-admin:hasEnabledLicense <http://rdfh.ch/licenses/public-domain> . }
       |INSERT { ?projectIri a knora-admin:knoraProject ;
       |    knora-admin:hasEnabledLicense <http://rdfh.ch/licenses/cc-by-4.0> ;
       |    knora-admin:hasEnabledLicense <http://rdfh.ch/licenses/cc-by-sa-4.0> ;
       |    knora-admin:hasEnabledLicense <http://rdfh.ch/licenses/cc-by-nc-4.0> ;
       |    knora-admin:hasEnabledLicense <http://rdfh.ch/licenses/cc-by-nc-sa-4.0> ;
       |    knora-admin:hasEnabledLicense <http://rdfh.ch/licenses/cc-by-nd-4.0> ;
       |    knora-admin:hasEnabledLicense <http://rdfh.ch/licenses/cc-by-nc-nd-4.0> ;
       |    knora-admin:hasEnabledLicense <http://rdfh.ch/licenses/ai-generated> ;
       |    knora-admin:hasEnabledLicense <http://rdfh.ch/licenses/unknown> ;
       |    knora-admin:hasEnabledLicense <http://rdfh.ch/licenses/public-domain> . }
       |WHERE { ?projectIri a knora-admin:knoraProject .
       |FILTER NOT EXISTS { ?projectIri knora-admin:hasEnabledLicense <http://rdfh.ch/licenses/cc-by-4.0> . }
       |FILTER NOT EXISTS { ?projectIri knora-admin:hasEnabledLicense <http://rdfh.ch/licenses/cc-by-sa-4.0> . }
       |FILTER NOT EXISTS { ?projectIri knora-admin:hasEnabledLicense <http://rdfh.ch/licenses/cc-by-nc-4.0> . }
       |FILTER NOT EXISTS { ?projectIri knora-admin:hasEnabledLicense <http://rdfh.ch/licenses/cc-by-nc-sa-4.0> . }
       |FILTER NOT EXISTS { ?projectIri knora-admin:hasEnabledLicense <http://rdfh.ch/licenses/cc-by-nd-4.0> . }
       |FILTER NOT EXISTS { ?projectIri knora-admin:hasEnabledLicense <http://rdfh.ch/licenses/cc-by-nc-nd-4.0> . }
       |FILTER NOT EXISTS { ?projectIri knora-admin:hasEnabledLicense <http://rdfh.ch/licenses/ai-generated> . }
       |FILTER NOT EXISTS { ?projectIri knora-admin:hasEnabledLicense <http://rdfh.ch/licenses/unknown> . }
       |FILTER NOT EXISTS { ?projectIri knora-admin:hasEnabledLicense <http://rdfh.ch/licenses/public-domain> . } }""".stripMargin

  private def fixture(predicate: String, defaults: Seq[RDFNode], custom: RDFNode): Model = {
    val model        = ModelFactory.createDefaultModel()
    val projectClass = ResourceFactory.createResource(s"${ka}knoraProject")
    val property     = ResourceFactory.createProperty(s"$ka$predicate")

    def project(name: String) =
      model.createResource(s"http://example.org/projects/$name").addProperty(RDF.`type`, projectClass)

    project("without-defaults")
    project("one-default").addProperty(property, defaults.head)
    defaults.foreach(project("all-defaults").addProperty(property, _))
    project("custom-only").addProperty(property, custom)
    model.createResource("http://example.org/not-a-project")

    model
  }

  private def execute(update: String, initial: Model): Model = {
    val dataset = DatasetFactory.create()
    dataset.getNamedModel(adminGraph).add(initial)
    UpdateAction.parseExecute(update, dataset)
    ModelFactory.createDefaultModel().add(dataset.getNamedModel(adminGraph))
  }

  private def assertSameData(legacy: String, actual: Update, initial: Model) = {
    val legacyResult = execute(legacy, initial)
    val actualResult = execute(actual.sparql, initial)
    assertTrue(
      actualResult.isIsomorphicWith(legacyResult),
      UpdateFactory.create(actual.sparql).getOperations.size == 1,
    )
  }

  override val spec: Spec[Any, Nothing] = suite("UpgradePluginPR3612")(
    test("copyright-holder update preserves the legacy all-or-nothing behaviour") {
      val defaults: Seq[RDFNode] =
        CopyrightHolder.default.toSeq.map(value => ResourceFactory.createPlainLiteral(value.value))
      val initial = fixture(
        "hasAllowedCopyrightHolder",
        defaults,
        ResourceFactory.createPlainLiteral("Custom copyright holder"),
      )

      assertSameData(legacyCopyrightHolder, new UpgradePluginPR3612().addDefaultCopyrightHolder, initial)
    },
    test("enabled-license update preserves the legacy all-or-nothing behaviour") {
      val defaults: Seq[RDFNode] = License.BUILT_IN
        .filter(_.isRecommended == Yes)
        .map(license => ResourceFactory.createResource(license.id.value))
      val initial = fixture(
        "hasEnabledLicense",
        defaults,
        ResourceFactory.createResource("http://example.org/licenses/custom"),
      )

      assertSameData(legacyEnabledLicenses, new UpgradePluginPR3612().addDefaultEnabledLicenses, initial)
    },
  )
}
