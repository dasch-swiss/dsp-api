/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ingest.api

import org.junit.runner.RunWith
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.infrastructure.Scope
import org.knora.webapi.slice.ingest.api.IngestAuthorization.*
import org.knora.webapi.slice.ingest.domain.ProjectShortcode

@RunWith(classOf[DspZTestJUnitRunner])
class IngestAuthorizationSpec extends ZIOSpecDefault {
  private val project1                    = ProjectShortcode.unsafeFrom("1234")
  private val project2                    = ProjectShortcode.unsafeFrom("5678")
  private val shortcode1                  = Shortcode.unsafeFrom("1234")
  private def principalWith(scope: Scope) = IngestPrincipal("blank subject", scope, "")
  private val sessionEmpty                = principalWith(Scope.empty)
  private val sessionRead                 = principalWith(Scope.read(shortcode1))
  private val sessionWrite                = principalWith(Scope.write(shortcode1))
  private val sessionAdmin                = principalWith(Scope.admin)

  private val adminRequired = Left(ApiProblem.Unauthorized("Admin permissions required."))
  private val noAccess      = Left(ApiProblem.Unauthorized("No project access."))

  val spec = suite("IngestAuthorization")(
    test("should require admin") {
      for {
        empty <- ensureAdminScope(sessionEmpty).either
        read  <- ensureAdminScope(sessionRead).either
        write <- ensureAdminScope(sessionWrite).either
        admin <- ensureAdminScope(sessionAdmin).either
      } yield assertTrue(empty == adminRequired, read == adminRequired, write == adminRequired, admin == Right(()))
    },
    test("should authorize project read with read, write or admin for that project") {
      for {
        empty      <- ensureProjectReadable(sessionEmpty, project1).either
        readOther  <- ensureProjectReadable(sessionRead, project2).either
        writeOther <- ensureProjectReadable(sessionWrite, project2).either
        read       <- ensureProjectReadable(sessionRead, project1).either
        write      <- ensureProjectReadable(sessionWrite, project1).either
        admin      <- ensureProjectReadable(sessionAdmin, project1).either
      } yield assertTrue(
        empty == noAccess,
        readOther == noAccess,
        writeOther == noAccess,
        read == Right(()),
        write == Right(()),
        admin == Right(()),
      )
    },
    test("should authorize project write with write or admin for that project") {
      for {
        empty      <- ensureProjectWritable(sessionEmpty, project1).either
        read       <- ensureProjectWritable(sessionRead, project1).either
        writeOther <- ensureProjectWritable(sessionWrite, project2).either
        write      <- ensureProjectWritable(sessionWrite, project1).either
        admin      <- ensureProjectWritable(sessionAdmin, project1).either
      } yield assertTrue(
        empty == noAccess,
        read == noAccess,
        writeOther == noAccess,
        write == Right(()),
        admin == Right(()),
      )
    },
  )
}
