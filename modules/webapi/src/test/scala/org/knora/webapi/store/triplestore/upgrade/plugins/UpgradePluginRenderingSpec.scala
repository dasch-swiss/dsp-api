/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.triplestore.upgrade.plugins

import org.apache.jena.update.UpdateFactory
import org.junit.runner.RunWith
import zio.test.Spec
import zio.test.ZIOSpecDefault
import zio.test.assertTrue

import org.knora.testrunner.DspZTestJUnitRunner

/**
 * Pins the rendered SPARQL of the migrated upgrade plugins against the output of their RDF4J
 * SparqlBuilder predecessors. The `expected` strings below are the verbatim `getQueryString`
 * output of the previous implementation, compared after canonicalisation by Jena.
 */
@RunWith(classOf[DspZTestJUnitRunner])
class UpgradePluginRenderingSpec extends ZIOSpecDefault {

  private def canonical(update: String): String = {
    val parsed = UpdateFactory.create(update)
    parsed.getPrefixMapping.clearNsPrefixMap()
    parsed.toString
  }

  val spec: Spec[Any, Nothing] = suite("UpgradePluginRendering")(
    suite("UpgradePluginPR3110")(
      test("removeAllInstitutions") {
        val expected =
          """|PREFIX knora-admin: <http://www.knora.org/ontology/knora-admin#>
             |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
             |DELETE { GRAPH <http://www.knora.org/data/admin> { ?s ?p ?o . } }
             |WHERE { GRAPH <http://www.knora.org/data/admin> { ?s a knora-admin:Institution ;
             |    ?p ?o . } }""".stripMargin
        assertTrue(canonical(new UpgradePluginPR3110().removeAllInstitutions.sparql) == canonical(expected))
      },
      test("removeAllBelongsToInstitutionTriples") {
        val expected =
          """|PREFIX knora-admin: <http://www.knora.org/ontology/knora-admin#>
             |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
             |DELETE { GRAPH <http://www.knora.org/data/admin> { ?s knora-admin:belongsToInstitution ?o . } }
             |WHERE { GRAPH <http://www.knora.org/data/admin> { ?s knora-admin:belongsToInstitution ?o . } }""".stripMargin
        assertTrue(
          canonical(new UpgradePluginPR3110().removeAllBelongsToInstitutionTriples.sparql) == canonical(expected),
        )
      },
    ),
    suite("UpgradePluginPR3111")(
      test("removeInvalidRestrictedViewWatermarkTriples") {
        val expected =
          """|PREFIX knora-admin: <http://www.knora.org/ontology/knora-admin#>
             |DELETE { GRAPH <http://www.knora.org/data/admin> { ?s knora-admin:projectRestrictedViewWatermark "path_to_image" . } }
             |WHERE { GRAPH <http://www.knora.org/data/admin> { ?s knora-admin:projectRestrictedViewWatermark "path_to_image" . } }""".stripMargin
        assertTrue(
          canonical(new UpgradePluginPR3111().removeInvalidRestrictedViewWatermarkTriples.sparql) == canonical(expected),
        )
      },
    ),
    suite("UpgradePluginPR3112")(
      test("removeWatermarkIfBothSet") {
        val expected =
          """|PREFIX knora-admin: <http://www.knora.org/ontology/knora-admin#>
             |DELETE { GRAPH <http://www.knora.org/data/admin> { ?project knora-admin:projectRestrictedViewWatermark ?prevWatermark . } }
             |WHERE { GRAPH <http://www.knora.org/data/admin> { ?project a knora-admin:knoraProject ;
             |    knora-admin:projectRestrictedViewWatermark ?prevWatermark ;
             |    knora-admin:projectRestrictedViewSize ?prevSize . } }""".stripMargin
        assertTrue(canonical(new UpgradePluginPR3112().removeWatermarkIfBothSet.sparql) == canonical(expected))
      },
      test("addDefaultRestrictedViewSizeToProjectsWithout") {
        val expected =
          """|PREFIX knora-admin: <http://www.knora.org/ontology/knora-admin#>
             |WITH <http://www.knora.org/data/admin>
             |INSERT { ?project knora-admin:projectRestrictedViewSize "!128,128" . }
             |WHERE { GRAPH <http://www.knora.org/data/admin> { ?project a knora-admin:knoraProject .
             |FILTER NOT EXISTS { ?project knora-admin:projectRestrictedViewSize ?size . }
             |FILTER NOT EXISTS { ?project knora-admin:projectRestrictedViewWatermark ?watermark . } } }""".stripMargin
        assertTrue(
          canonical(new UpgradePluginPR3112().addDefaultRestrictedViewSizeToProjectsWithout.sparql) == canonical(
            expected,
          ),
        )
      },
      test("replaceWatermarkFalseWithDefaultRestrictedViewSize") {
        val expected =
          """|PREFIX knora-admin: <http://www.knora.org/ontology/knora-admin#>
             |WITH <http://www.knora.org/data/admin>
             |DELETE { GRAPH <http://www.knora.org/data/admin> { ?project knora-admin:projectRestrictedViewWatermark false . } }
             |INSERT { ?project knora-admin:projectRestrictedViewSize "!128,128" . }
             |WHERE { GRAPH <http://www.knora.org/data/admin> { ?project a knora-admin:knoraProject ;
             |    knora-admin:projectRestrictedViewWatermark false .
             |FILTER NOT EXISTS { ?project knora-admin:projectRestrictedViewSize ?size . } } }""".stripMargin
        assertTrue(
          canonical(new UpgradePluginPR3112().replaceWatermarkFalseWithDefaultRestrictedViewSize.sparql) == canonical(
            expected,
          ),
        )
      },
    ),
    suite("UpgradePluginPR3383")(
      test("removeSystemProjectDefaultObjectAccessPermissions") {
        val expected =
          """|WITH <http://www.knora.org/data/permissions>
             |DELETE { ?permissionIri a <http://www.knora.org/ontology/knora-admin#DefaultObjectAccessPermission> ;
             |    <http://www.knora.org/ontology/knora-admin#forProject> <http://www.knora.org/ontology/knora-admin#SystemProject> ;
             |    ?p ?o . }
             |WHERE { ?permissionIri a <http://www.knora.org/ontology/knora-admin#DefaultObjectAccessPermission> ;
             |    <http://www.knora.org/ontology/knora-admin#forProject> <http://www.knora.org/ontology/knora-admin#SystemProject> ;
             |    ?p ?o . }""".stripMargin
        assertTrue(
          canonical(new UpgradePluginPR3383().removeSystemProjectDefaultObjectAccessPermissions.sparql) == canonical(
            expected,
          ),
        )
      },
      test("removeKnownUserDefaultObjectAccessPermissions") {
        val expected =
          """|WITH <http://www.knora.org/data/permissions>
             |DELETE { ?permissionIri a <http://www.knora.org/ontology/knora-admin#DefaultObjectAccessPermission> ;
             |    <http://www.knora.org/ontology/knora-admin#forGroup> <http://www.knora.org/ontology/knora-admin#KnownUser> ;
             |    ?p ?o . }
             |WHERE { ?permissionIri a <http://www.knora.org/ontology/knora-admin#DefaultObjectAccessPermission> ;
             |    <http://www.knora.org/ontology/knora-admin#forGroup> <http://www.knora.org/ontology/knora-admin#KnownUser> ;
             |    ?p ?o . }""".stripMargin
        assertTrue(
          canonical(new UpgradePluginPR3383().removeKnownUserDefaultObjectAccessPermissions.sparql) == canonical(
            expected,
          ),
        )
      },
    ),
    suite("UpgradePluginPR3612")(
      test("addDefaultCopyrightHolder") {
        val expected =
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
        assertTrue(canonical(new UpgradePluginPR3612().addDefaultCopyrightHolder.sparql) == canonical(expected))
      },
      test("addDefaultEnabledLicenses") {
        val expected =
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
        assertTrue(canonical(new UpgradePluginPR3612().addDefaultEnabledLicenses.sparql) == canonical(expected))
      },
    ),
    suite("MigrateRemoveProjectStatus")(
      test("removeProjectStatus") {
        val expected =
          """|PREFIX knora-admin: <http://www.knora.org/ontology/knora-admin#>
             |WITH <http://www.knora.org/data/admin>
             |DELETE { ?project knora-admin:status ?status . }
             |WHERE { ?project a knora-admin:knoraProject ;
             |    knora-admin:status ?status . }""".stripMargin
        assertTrue(canonical(new MigrateRemoveProjectStatus().removeProjectStatus.sparql) == canonical(expected))
      },
    ),
  )
}
