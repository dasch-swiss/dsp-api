/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api.model

import dsp.errors.BadRequestException
import org.knora.webapi._
import org.knora.webapi.sharedtestdata.SharedTestDataADM
import org.knora.webapi.slice.admin.api.model.ProjectIdentifierADM._

/**
 * This spec is used to test subclasses of the [[ProjectsResponderRequestADM]] trait.
 */
class ProjectsMessagesADMSpec extends CoreSpec {
  "The ProjectIdentifierADM class" should {
    "return without throwing when the project IRI is valid" in {
      IriIdentifier
        .fromString(SharedTestDataADM.incunabulaProject.id)
        .getOrElseWith(e => throw BadRequestException(e.head.getMessage))
        .value
        .value shouldBe SharedTestDataADM.incunabulaProject.id
      IriIdentifier
        .fromString(SharedTestDataADM.defaultSharedOntologiesProject.id)
        .getOrElseWith(e => throw BadRequestException(e.head.getMessage))
        .value
        .value shouldBe SharedTestDataADM.defaultSharedOntologiesProject.id
      IriIdentifier
        .fromString(SharedTestDataADM.systemProject.id)
        .getOrElseWith(e => throw BadRequestException(e.head.getMessage))
        .value
        .value shouldBe SharedTestDataADM.systemProject.id
    }

    "return a 'BadRequestException' when the project IRI is invalid" in {
      assertThrows[BadRequestException] {
        IriIdentifier
          .fromString("http://not-valid.org")
          .getOrElseWith(e => throw BadRequestException(e.head.getMessage))
      }
    }
  }
}
