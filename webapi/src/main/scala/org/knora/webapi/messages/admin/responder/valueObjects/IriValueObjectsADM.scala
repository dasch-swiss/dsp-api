/*
 * Copyright © 2021 Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.admin.responder.valueObjects

import org.knora.webapi.exceptions.BadRequestException
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.listsmessages.ListsErrorMessagesADM.{
  PROJECT_IRI_INVALID_ERROR,
  PROJECT_IRI_MISSING_ERROR
}
import zio.prelude.Validation

/**
 * ProjectIRI value object.
 */
sealed abstract case class ProjectIRI private (value: String)
object ProjectIRI {
  val sf = StringFormatter.getGeneralInstance

  def make(value: String): Validation[Throwable, ProjectIRI] =
    if (value.isEmpty) {
      Validation.fail(BadRequestException(PROJECT_IRI_MISSING_ERROR))
    } else {
      if (value.nonEmpty && !sf.isKnoraProjectIriStr(value)) {
        Validation.fail(BadRequestException(PROJECT_IRI_INVALID_ERROR))
      } else {
        val validatedValue = Validation(
          sf.validateAndEscapeProjectIri(value, throw BadRequestException(PROJECT_IRI_INVALID_ERROR))
        )

        validatedValue.map(new ProjectIRI(_) {})
      }
    }
}
