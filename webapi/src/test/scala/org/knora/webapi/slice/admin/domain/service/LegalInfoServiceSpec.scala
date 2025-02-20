/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.service

import zio.Exit
import zio.ZIO
import zio.test.*
import zio.test.Assertion.*

import org.knora.webapi.TestDataFactory
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.v2.responder.valuemessages.FileValueV2
import org.knora.webapi.slice.admin.domain.model.CopyrightHolder
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.admin.domain.model.License
import org.knora.webapi.slice.admin.domain.model.LicenseIri
import org.knora.webapi.slice.admin.domain.repo.KnoraProjectRepoInMemory
import org.knora.webapi.slice.admin.repo.LicenseRepo
import org.knora.webapi.slice.ontology.repo.service.OntologyRepoInMemory
import org.knora.webapi.slice.resourceinfo.domain.IriConverter
import org.knora.webapi.store.triplestore.api.TriplestoreServiceInMemory

object LegalInfoServiceSpec extends ZIOSpecDefault {

  private val service     = ZIO.serviceWithZIO[LegalInfoService]
  private val projectRepo = ZIO.serviceWithZIO[KnoraProjectRepo]

  private val validCopyrightHolder = CopyrightHolder.unsafeFrom("DaSCH")
  private val validLicense         = LicenseIri.AI_GENERATED
  private val fileValueValid =
    FileValueV2("unused", "unused", None, None, Some(validCopyrightHolder), None, Some(validLicense))
  private val setupProject = projectRepo(
    _.save(
      TestDataFactory.someProject
        .copy(allowedCopyrightHolders = Set(validCopyrightHolder)),
    ),
  )

  private val validateLegalInfoSuite = suite("validateLegalInfo for FileValue in Project")(
    test("A FileValue without LicenseIri and Copyright Holder should be valid") {
      for {
        prj           <- setupProject
        emptyFileValue = fileValueValid.copy(licenseIri = None, copyrightHolder = None)
        actual        <- service(_.validateLegalInfo(emptyFileValue, prj.shortcode))
      } yield assertTrue(actual == emptyFileValue)
    },
    test("A FileValue with valid LicenseIri and Copyright Holder should be valid") {
      for {
        prj    <- setupProject
        actual <- service(_.validateLegalInfo(fileValueValid, prj.shortcode))
      } yield assertTrue(actual == fileValueValid)
    },
    test("A FileValue with invalid LicenseIri should be invalid") {
      for {
        prj       <- setupProject
        invalidIri = LicenseIri.makeNew
        actual <-
          service(_.validateLegalInfo(fileValueValid.copy(licenseIri = Some(invalidIri)), prj.shortcode)).exit
      } yield assert(actual)(fails(equalTo(s"License $invalidIri is not allowed in project ${prj.shortcode}")))
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
    validateLegalInfoSuite,
    test("findLicenses should return all licenses for project") {
      for {
        actual <- service(_.findLicenses(Shortcode.unsafeFrom("0001")))
      } yield assert(actual)(hasSameElements(License.BUILT_IN))
    },
  ).provide(
    LegalInfoService.layer,
    LicenseRepo.layer,
    KnoraProjectService.layer,
    KnoraProjectRepoInMemory.layer,
    OntologyRepoInMemory.emptyLayer,
    IriConverter.layer,
    StringFormatter.test,
    TriplestoreServiceInMemory.emptyLayer,
  )
}
