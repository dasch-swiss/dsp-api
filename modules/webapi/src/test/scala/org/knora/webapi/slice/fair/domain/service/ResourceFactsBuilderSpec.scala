/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.fair.domain.service

import org.junit.runner.RunWith
import zio.*
import zio.test.*

import java.time.Instant

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.config.FairConfig
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.admin.domain.model.Permission.ObjectAccess
import org.knora.webapi.slice.common.ResourceIri
import org.knora.webapi.slice.fair.domain.ResourceFacts
import org.knora.webapi.slice.fair.ports.*

@RunWith(classOf[DspZTestJUnitRunner])
class ResourceFactsBuilderSpec extends ZIOSpecDefault {

  private val config =
    FairConfig("https://app.example.org", "https://app.example.org/index.html", "https://example.org/oai", "")

  private val id    = "0b03b0a6e6be"
  private val iri   = ResourceIri.unsafeFrom(s"http://rdfh.ch/081C/$id")
  private val label = "Thing"

  private val project = FairProject(Shortcode.unsafeFrom("081C"), "short", Some("Long"), None, List.empty)

  private val ccBy = FairLicence.Licence("https://creativecommons.org/licenses/by/4.0/", "CC BY 4.0")
  private val cc0  = FairLicence.Licence("https://creativecommons.org/publicdomain/zero/1.0/", "CC0 1.0")

  private val resource = FairResource(
    iri = iri,
    label = "A resource",
    classIri = "http://api.example.org/ontology/081C/x/v2#Thing",
    userPermission = ObjectAccess.View,
    creationDate = Instant.parse("2024-01-02T03:04:05Z"),
    lastModificationDate = None,
    authorship = List.empty,
    description = None,
    file = None,
  )

  private def file(permission: ObjectAccess, licence: Option[String] = None) = FairFileValue(
    kind = FairFileKind.Stored,
    userPermission = permission,
    typeOfData = Some("Image"),
    assetId = Some("asset-1"),
    originalUrl = Some("https://ingest.example.org/projects/081C/assets/asset-1/original"),
    licenseIri = licence,
    authorship = List.empty,
  )

  private val sidecar = FairAsset(Some("image/jp2"), Some("a.jp2"), Some(42L))

  private def run(
    resources: FairResourcesInMemory,
    shortcode: String = "081C",
    resourceId: String = id,
    projects: FairProjectsInMemory = FairProjectsInMemory(Map(project.shortcode -> project)),
    assets: FairAssetsInMemory,
  ): Task[Option[ResourceFacts]] =
    ZIO
      .service[StringFormatter]
      .flatMap(sf =>
        ResourceFactsBuilder(
          resources,
          projects,
          FairClassLabelsInMemory(Map(resource.classIri -> Map("en" -> label))),
          assets,
          config,
          sf,
        )
          .build(shortcode, resourceId),
      )
      .provide(StringFormatter.test)

  private def runPlain(
    resources: FairResourcesInMemory,
    shortcode: String = "081C",
    resourceId: String = id,
    projects: FairProjectsInMemory = FairProjectsInMemory(Map(project.shortcode -> project)),
  ) = FairAssetsInMemory.make().flatMap(a => run(resources, shortcode, resourceId, projects, a))

  private def malformed(name: String, shortcode: String, resourceId: String) =
    test(s"returns None without reading for $name") {
      for {
        resources <- FairResourcesInMemory.make(resource)
        result    <- runPlain(resources, shortcode, resourceId)
        calls     <- resources.calls.get
      } yield assertTrue(result.isEmpty, calls == 0)
    }

  val spec = suite("ResourceFactsBuilder")(
    suite("absent")(
      test("returns None for a missing resource") {
        for {
          resources <- FairResourcesInMemory.make()
          result    <- runPlain(resources)
        } yield assertTrue(result.isEmpty)
      },
      test("returns None for a deleted resource, which the port reports as absent") {
        for {
          resources <- FairResourcesInMemory.make()
          result    <- runPlain(resources)
        } yield assertTrue(result.isEmpty)
      },
      test("returns None for a forbidden resource, which the port reports as absent") {
        for {
          resources <- FairResourcesInMemory.make()
          result    <- runPlain(resources)
        } yield assertTrue(result.isEmpty)
      },
    ),
    suite("malformed ids")(
      malformed("an id with illegal characters", "081C", "bad!id"),
      malformed("an empty id", "081C", ""),
      malformed("a non-hex shortcode", "08X3", id),
      malformed("a short shortcode", "080", id),
    ),
    suite("failures")(
      test("fails when the resources port fails") {
        for {
          resources <- FairResourcesInMemory.failing(new RuntimeException("triplestore down"))
          result    <- runPlain(resources).exit
        } yield assertTrue(result.isFailure)
      },
      test("fails when an existing resource has no project") {
        for {
          resources <- FairResourcesInMemory.make(resource)
          result    <- runPlain(resources, projects = FairProjectsInMemory()).exit
        } yield assertTrue(result.isFailure)
      },
    ),
    suite("URLs")(
      test("a lowercase request shortcode yields the canonical page URL and ARK") {
        for {
          resources <- FairResourcesInMemory.make(resource)
          facts     <- runPlain(resources, shortcode = "081c")
        } yield assertTrue(
          facts.map(_.pageUrl).contains(s"https://app.example.org/resource/081C/$id"),
          facts.map(_.iri).contains(iri),
          facts.exists(_.ark.contains("/081C/")),
        )
      },
      test("the project ARK is the resource ARK without its last path segment") {
        for {
          resources <- FairResourcesInMemory.make(resource)
          facts     <- runPlain(resources)
        } yield assertTrue(
          facts.exists(f =>
            f.ark.startsWith(f.projectArk + "/") && !f.ark.stripPrefix(f.projectArk + "/").contains("/"),
          ),
          facts.exists(_.projectArk.endsWith("/081C")),
          facts.map(_.classLabel).contains(label),
        )
      },
    ),
    suite("sidecar")(
      test("is looked up for a stored file readable with V") {
        for {
          resources <- FairResourcesInMemory.make(resource.copy(file = Some(file(ObjectAccess.View))))
          assets    <- FairAssetsInMemory.make(Map("asset-1" -> sidecar))
          facts     <- run(resources, assets = assets)
          calls     <- assets.calls.get
        } yield assertTrue(calls == List("asset-1"), facts.flatMap(_.file).flatMap(_.mimeType).contains("image/jp2"))
      },
      test("is not looked up for a file visible with RV only") {
        for {
          resources <- FairResourcesInMemory.make(resource.copy(file = Some(file(ObjectAccess.RestrictedView))))
          assets    <- FairAssetsInMemory.make(Map("asset-1" -> sidecar))
          facts     <- run(resources, assets = assets)
          calls     <- assets.calls.get
        } yield assertTrue(calls.isEmpty, facts.isDefined, facts.exists(_.file.isEmpty))
      },
    ),
    suite("licence")(
      test("an unknown file licence IRI hides the licence even when the project has one") {
        val projects = FairProjectsInMemory(
          Map(project.shortcode -> project.copy(dataLicenseIri = Some("urn:project"))),
          Map("urn:project"     -> cc0),
        )
        for {
          resources <-
            FairResourcesInMemory.make(resource.copy(file = Some(file(ObjectAccess.View, Some("urn:unknown")))))
          facts <- runPlain(resources, projects = projects)
        } yield assertTrue(facts.isDefined, facts.exists(_.licence.isEmpty))
      },
      test("a known file licence IRI is used") {
        val projects = FairProjectsInMemory(Map(project.shortcode -> project), Map("urn:file" -> ccBy))
        for {
          resources <- FairResourcesInMemory.make(resource.copy(file = Some(file(ObjectAccess.View, Some("urn:file")))))
          facts     <- runPlain(resources, projects = projects)
        } yield assertTrue(facts.flatMap(_.licence).flatMap(_.spdxId).contains("CC-BY-4.0"))
      },
      test("without a file licence IRI the project licence is used") {
        val projects = FairProjectsInMemory(
          Map(project.shortcode -> project.copy(dataLicenseIri = Some("urn:project"))),
          Map("urn:project"     -> cc0),
        )
        for {
          resources <- FairResourcesInMemory.make(resource.copy(file = Some(file(ObjectAccess.View))))
          facts     <- runPlain(resources, projects = projects)
        } yield assertTrue(facts.flatMap(_.licence).flatMap(_.spdxId).contains("CC0-1.0"))
      },
    ),
  )
}
