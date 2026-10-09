/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.fair.domain

import org.junit.runner.RunWith
import zio.test.*

import java.time.Instant

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.admin.domain.model.Permission.ObjectAccess
import org.knora.webapi.slice.common.ResourceIri
import org.knora.webapi.slice.fair.ports.*

@RunWith(classOf[DspZTestJUnitRunner])
class ResourceFactsSpec extends ZIOSpecDefault {

  private val iri = ResourceIri.unsafeFrom("http://rdfh.ch/0803/0b03b0a6e6be")

  private val storedFile = FairFileValue(
    kind = FairFileKind.Stored,
    userPermission = ObjectAccess.View,
    typeOfData = Some("Image"),
    assetId = Some("asset-1"),
    originalUrl = Some("https://ingest.example.org/projects/0803/assets/asset-1/original"),
    licenseIri = None,
    authorship = List.empty,
  )

  private val resource = FairResource(
    iri = iri,
    label = "A resource",
    classIri = "http://api.example.org/ontology/0803/x/v2#Thing",
    userPermission = ObjectAccess.View,
    creationDate = Instant.parse("2024-01-02T03:04:05Z"),
    lastModificationDate = Some(Instant.parse("2025-01-02T03:04:05Z")),
    authorship = List.empty,
    description = Some("A description"),
    file = None,
  )

  private val project = FairProject(
    shortcode = Shortcode.unsafeFrom("0803"),
    shortname = "short",
    longname = Some("A long name"),
    dataLicenseIri = None,
    defaultDataAuthorship = List.empty,
  )

  private val base = FactsInputs(
    resource = resource,
    project = project,
    fileLicence = LicenceLookup.NotRecorded,
    projectLicence = LicenceLookup.NotRecorded,
    classLabels = Map("en" -> "Thing"),
    sidecar = None,
    ark = "https://ark.example.org/ark:/72163/1/0803/0b03b0a6e6bX",
    projectArk = "https://ark.example.org/ark:/72163/1/0803",
    appExternalBaseUrl = "https://app.example.org",
  )

  private val sidecar = FairAsset(Some("image/jp2"), Some("a.jp2"), Some(42L))

  private val ccBy: FairLicence.Licence =
    FairLicence.Licence("https://creativecommons.org/licenses/by/4.0/", "CC BY 4.0")
  private val cc0: FairLicence.Licence =
    FairLicence.Licence("https://creativecommons.org/publicdomain/zero/1.0/", "CC0 1.0")
  private val projectCc = LicenceLookup.Resolved(cc0)

  private def withFile(f: FairFileValue => FairFileValue = identity): FactsInputs =
    base.copy(resource = resource.copy(file = Some(f(storedFile))))

  private def licenceOf(in: FactsInputs): Option[ResourceLicence] = ResourceFacts.derive(in).flatMap(_.licence)
  private def creatorsOf(in: FactsInputs): Option[Creators]       = ResourceFacts.derive(in).map(_.creators)

  val spec = suite("ResourceFacts.derive")(
    suite("page URL and ARK")(
      test("builds the page URL from the canonical IRI") {
        val facts = ResourceFacts.derive(base)
        assertTrue(
          facts.map(_.pageUrl).contains("https://app.example.org/resource/0803/0b03b0a6e6be"),
          facts.map(_.shortcode).contains(Shortcode.unsafeFrom("0803")),
          facts.map(_.ark).contains(base.ark),
          facts.map(_.projectArk).contains(base.projectArk),
        )
      },
      test("tolerates a trailing slash on the base URL") {
        val facts = ResourceFacts.derive(base.copy(appExternalBaseUrl = "https://app.example.org/"))
        assertTrue(facts.map(_.pageUrl).contains("https://app.example.org/resource/0803/0b03b0a6e6be"))
      },
    ),
    suite("file and access")(
      test("an RV-only resource without a file is visible with restrictions") {
        val facts =
          ResourceFacts.derive(base.copy(resource = resource.copy(userPermission = ObjectAccess.RestrictedView)))
        assertTrue(
          facts.map(_.accessLevel).contains(AccessLevel.OpenAccessWithRestrictions),
          facts.flatMap(_.file).isEmpty,
        )
      },
      test("a V resource with an RV-only stored file has no file facts") {
        val facts = ResourceFacts.derive(withFile(_.copy(userPermission = ObjectAccess.RestrictedView)))
        assertTrue(
          facts.flatMap(_.file).isEmpty,
          facts.map(_.accessLevel).contains(AccessLevel.OpenAccessWithRestrictions),
          facts.flatMap(_.typeOfData).contains("Image"),
        )
      },
      test("a V resource with a V file has file facts from the sidecar") {
        val facts = ResourceFacts.derive(withFile().copy(sidecar = Some(sidecar)))
        assertTrue(
          facts
            .flatMap(_.file)
            .contains(
              FileFacts(
                "https://ingest.example.org/projects/0803/assets/asset-1/original",
                Some("image/jp2"),
                Some("a.jp2"),
                Some(42L),
              ),
            ),
          facts.map(_.accessLevel).contains(AccessLevel.FullOpenAccess),
        )
      },
      test("without a sidecar only the content URL is set") {
        val facts = ResourceFacts.derive(withFile())
        assertTrue(
          facts
            .flatMap(_.file)
            .contains(
              FileFacts("https://ingest.example.org/projects/0803/assets/asset-1/original", None, None, None),
            ),
        )
      },
      test("a resource without a file has full access and no type of data") {
        val facts = ResourceFacts.derive(base)
        assertTrue(
          facts.map(_.accessLevel).contains(AccessLevel.FullOpenAccess),
          facts.exists(_.typeOfData.isEmpty),
        )
      },
      test("an external IIIF file gives the type of data but no file facts") {
        val facts =
          ResourceFacts.derive(withFile(_.copy(kind = FairFileKind.ExternalIiif, assetId = None, originalUrl = None)))
        assertTrue(
          facts.flatMap(_.typeOfData).contains("Image"),
          facts.flatMap(_.file).isEmpty,
        )
      },
    ),
    suite("licence")(
      test("a built-in CC BY file licence carries the SPDX id and the DPE label") {
        val in = withFile(_.copy(licenseIri = Some(ccBy.uri))).copy(fileLicence = LicenceLookup.Resolved(ccBy))
        assertTrue(
          licenceOf(in).contains(
            ResourceLicence(Some(ccBy.uri), "Creative Commons Attribution 4.0 International", Some("CC-BY-4.0")),
          ),
        )
      },
      test("a placeholder file licence without a project licence gives none") {
        val in = withFile().copy(fileLicence = LicenceLookup.Resolved(FairLicence.Placeholder))
        assertTrue(licenceOf(in).isEmpty)
      },
      test("a placeholder file licence falls back to the project licence") {
        val in =
          withFile().copy(fileLicence = LicenceLookup.Resolved(FairLicence.Placeholder), projectLicence = projectCc)
        assertTrue(
          licenceOf(in).contains(
            ResourceLicence(Some(cc0.uri), "Creative Commons Public Domain Dedication", Some("CC0-1.0")),
          ),
        )
      },
      test("a declaration gives a label only and does not fall back") {
        val labels  = List("Unknown", "AI-generated", "Public domain")
        val results = labels.map { l =>
          licenceOf(
            withFile()
              .copy(fileLicence = LicenceLookup.Resolved(FairLicence.Declaration(l)), projectLicence = projectCc),
          )
        }
        assertTrue(results == labels.map(l => Some(ResourceLicence(None, l, None))))
      },
      test("a not-recorded file licence falls back to the project licence") {
        val in = withFile().copy(projectLicence = projectCc)
        assertTrue(licenceOf(in).map(_.spdxId).contains(Some("CC0-1.0")))
      },
      test("an unresolved file licence gives none and does not fall back") {
        val in = withFile(_.copy(licenseIri = Some("http://example.org/custom"))).copy(
          fileLicence = LicenceLookup.Unresolved,
          projectLicence = projectCc,
        )
        assertTrue(licenceOf(in).isEmpty)
      },
      test("no licence anywhere gives none") {
        assertTrue(licenceOf(base).isEmpty)
      },
      test("a non-CC licence keeps its URI and label without an SPDX id") {
        val l: FairLicence.Licence = FairLicence.Licence("http://rightsstatements.org/vocab/NoC-NC/1.0/", "NoC-NC")
        val in                     = withFile().copy(fileLicence = LicenceLookup.Resolved(l))
        assertTrue(licenceOf(in).contains(ResourceLicence(Some(l.uri), "NoC-NC", None)))
      },
    ),
    suite("creators")(
      test("uses the resource authorship") {
        val in = base.copy(resource = resource.copy(authorship = List("Ann", "Bob")))
        assertTrue(creatorsOf(in).contains(Creators(List("Ann", "Bob"), CreatorSource.Authorship)))
      },
      test("appends the file authorship") {
        val in  = withFile(_.copy(authorship = List("Cy")))
        val in2 = in.copy(resource = in.resource.copy(authorship = List("Ann")))
        assertTrue(creatorsOf(in2).contains(Creators(List("Ann", "Cy"), CreatorSource.Authorship)))
      },
      test("deduplicates across resource and file keeping order") {
        val in  = withFile(_.copy(authorship = List("Bob", "Cy", "Cy")))
        val in2 = in.copy(resource = in.resource.copy(authorship = List("Ann", "Bob")))
        assertTrue(creatorsOf(in2).contains(Creators(List("Ann", "Bob", "Cy"), CreatorSource.Authorship)))
      },
      test("credits the authorship of an RV-only file") {
        val in = withFile(_.copy(userPermission = ObjectAccess.RestrictedView, authorship = List("Cy")))
        assertTrue(creatorsOf(in).contains(Creators(List("Cy"), CreatorSource.Authorship)))
      },
      test("falls back to the project default authorship") {
        val in = base.copy(project = project.copy(defaultDataAuthorship = List("Dee", "Dee")))
        assertTrue(creatorsOf(in).contains(Creators(List("Dee"), CreatorSource.ProjectDefault)))
      },
      test("falls back to the project longname as organisation") {
        assertTrue(creatorsOf(base).contains(Creators(List("A long name"), CreatorSource.ProjectOrganisation)))
      },
      test("falls back to the project shortname when there is no longname") {
        val in = base.copy(project = project.copy(longname = None))
        assertTrue(creatorsOf(in).contains(Creators(List("short"), CreatorSource.ProjectOrganisation)))
      },
    ),
    suite("class label")(
      test("prefers en") {
        val in = base.copy(classLabels = Map("de" -> "Ding", "en" -> "Thing"))
        assertTrue(ResourceFacts.derive(in).map(_.classLabel).contains("Thing"))
      },
      test("uses the smallest language tag without en") {
        val in = base.copy(classLabels = Map("fr" -> "Chose", "de" -> "Ding"))
        assertTrue(ResourceFacts.derive(in).map(_.classLabel).contains("Ding"))
      },
      test("falls back to the class IRI") {
        val in = base.copy(classLabels = Map.empty)
        assertTrue(ResourceFacts.derive(in).map(_.classLabel).contains(resource.classIri))
      },
    ),
  )
}
