/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.service

import zio.*
import zio.test.*
import zio.test.Assertion.*

import org.knora.webapi.TestDataFactory
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.v2.responder.valuemessages.FileValueV2
import org.knora.webapi.slice.admin.domain.model.CopyrightHolder
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.admin.domain.model.License
import org.knora.webapi.slice.admin.domain.model.LicenseIri
import org.knora.webapi.slice.admin.repo.LicenseRepo
import org.knora.webapi.slice.admin.repo.service.KnoraProjectRepoLive
import org.knora.webapi.slice.common.service.IriConverter
import org.knora.webapi.slice.infrastructure.CacheManager
import org.knora.webapi.slice.ontology.repo.service.OntologyRepoInMemory
import org.knora.webapi.store.triplestore.api.TriplestoreServiceInMemory

object LegalInfoServiceSpec extends ZIOSpecDefault {

  private val service     = ZIO.serviceWithZIO[LegalInfoService]
  private val projectRepo = ZIO.serviceWithZIO[KnoraProjectRepo]

  private val validCopyrightHolder = CopyrightHolder.unsafeFrom("DaSCH")
  private val enabledLicense       = LicenseIri.CC_BY_4_0
  private val disabledLicense      = LicenseIri.AI_GENERATED
  private val fileValueValid =
    FileValueV2("unused", "unused", None, None, Some(validCopyrightHolder), None, Some(enabledLicense))
  private val setupProject = projectRepo(
    _.save(
      TestDataFactory.someProject
        .copy(allowedCopyrightHolders = Set(validCopyrightHolder), enabledLicenses = Set(enabledLicense)),
    ),
  )

  private val findLicenseByIri = suite("findByIdAndProject")(
    test("should return the license for a project") {
      for {
        prj    <- setupProject
        actual <- service(_.findAvailableLicenseByIdAndShortcode(enabledLicense, prj.shortcode))
      } yield assertTrue(actual.map(_.id).contains(enabledLicense))
    },
    test("should return None if the license is not available in the project") {
      for {
        prj              <- setupProject
        unknownLicenseIri = LicenseIri.unsafeFrom("http://rdfh.ch/licenses/i6xBpZn4RVOdOIyTezEumw")
        actual           <- service(_.findAvailableLicenseByIdAndShortcode(unknownLicenseIri, prj.shortcode))
      } yield assertTrue(actual.isEmpty)
    },
  )

  private val licenseEnablingSuite = suite("License enabling and disabling")(
    test("enabling should work") {
      for {
        prj    <- setupProject
        actual <- service(_.enableLicense(disabledLicense, prj))
      } yield assertTrue(actual.enabledLicenses == Set(disabledLicense, enabledLicense))
    },
    test("disabling should work") {
      for {
        prj    <- setupProject
        actual <- service(_.disableLicense(enabledLicense, prj))
      } yield assertTrue(actual.enabledLicenses == Set.empty)
    },
    test("available licenses should return all built-in licenses, even if no license is enabled") {
      for {
        prj    <- setupProject
        _      <- service(_.disableLicense(enabledLicense, prj))
        actual <- service(_.findAvailableLicenses(prj.shortcode))
      } yield assertTrue(actual == License.BUILT_IN.toSet)
    },
  )

  private val validateLegalInfoSuite = suite("validateLegalInfo for FileValue in Project")(
    test("A FileValue without LicenseIri and Copyright Holder should be valid") {
      for {
        prj           <- setupProject
        emptyFileValue = fileValueValid.copy(licenseIri = None, copyrightHolder = None)
        actual        <- service(_.validateLegalInfo(emptyFileValue, prj.shortcode))
      } yield assertTrue(actual == emptyFileValue)
    },
    test("A FileValue with enabled LicenseIri and valid Copyright Holder should be valid") {
      for {
        prj    <- setupProject
        actual <- service(_.validateLegalInfo(fileValueValid, prj.shortcode))
      } yield assertTrue(actual == fileValueValid)
    },
    test("A FileValue with not enabled LicenseIri should be invalid") {
      for {
        prj <- setupProject
        actual <-
          service(_.validateLegalInfo(fileValueValid.copy(licenseIri = Some(disabledLicense)), prj.shortcode)).exit
      } yield assert(actual)(fails(equalTo(s"License $disabledLicense is not allowed in project ${prj.shortcode}")))
    },
    test("A FileValue with invalid CopyrightHolder should be invalid") {
      for {
        prj    <- setupProject
        holder  = CopyrightHolder.unsafeFrom("this-is-not-allowed")
        actual <- service(_.validateLegalInfo(fileValueValid.copy(copyrightHolder = Some(holder)), prj.shortcode)).exit
      } yield assert(actual)(fails(equalTo(s"Copyright holder $holder is not allowed in project ${prj.shortcode}")))
    },
    test("A FileValue with invalid LicenseIri and invalid Copyright Holder should be invalid") {
      for {
        prj          <- setupProject
        invalidHolder = CopyrightHolder.unsafeFrom("this-is-not-allowed")
        invalidIri    = LicenseIri.makeNew
        actual <-
          service(
            _.validateLegalInfo(
              fileValueValid.copy(copyrightHolder = Some(invalidHolder), licenseIri = Some(invalidIri)),
              prj.shortcode,
            ),
          ).exit
      } yield assert(actual)(
        fails(
          equalTo(
            s"License $invalidIri is not allowed in project ${prj.shortcode}, " +
              s"Copyright holder $invalidHolder is not allowed in project ${prj.shortcode}",
          ),
        ),
      )
    },
  )

  def spec = suite("LegalInfoService")(
    findLicenseByIri,
    licenseEnablingSuite,
    validateLegalInfoSuite,
    test("findAvailableLicenses should return all licenses for project") {
      for {
        actual <- service(_.findAvailableLicenses(Shortcode.unsafeFrom("0001")))
      } yield assert(actual)(hasSameElements(License.BUILT_IN))
    },
    test("given we have not enabled a license, then findEnabledLicenses should return no licenses for project") {
      for {
        actual <- service(_.findEnabledLicenses(Shortcode.unsafeFrom("0001")))
      } yield assertTrue(actual.isEmpty)
    },
  ).provide(
    LegalInfoService.layer,
    LicenseRepo.layer,
    KnoraProjectService.layer,
    KnoraProjectRepoLive.layer,
    OntologyRepoInMemory.emptyLayer,
    IriConverter.layer,
    StringFormatter.test,
    TriplestoreServiceInMemory.emptyLayer,
    CacheManager.layer,
    ZLayer.succeed(
      org.knora.webapi.config.Features(
        allowEraseProjects = false,
        disableLastModificationDateCheck = false,
        triggerCompactionAfterProjectErasure = false,
        enableFullLicenseCheck = true,
      ),
    ),
  )
}
