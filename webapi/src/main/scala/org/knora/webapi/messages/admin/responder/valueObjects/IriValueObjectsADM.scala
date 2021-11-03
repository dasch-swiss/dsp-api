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

import scala.util.{Failure, Success, Try}

/**
 * ProjectIRI value object.
 */
sealed abstract case class ProjectIRI private (value: String)
object ProjectIRI {
  val stringFormatter = StringFormatter.getGeneralInstance

  def create(value: String): Either[Throwable, ProjectIRI] =
    if (value.isEmpty) {
      Left(BadRequestException(PROJECT_IRI_MISSING_ERROR))
    } else {
      if (value.nonEmpty && !stringFormatter.isKnoraProjectIriStr(value)) {
        Left(BadRequestException(PROJECT_IRI_INVALID_ERROR))
      } else {
        val validatedValue = Try(
          stringFormatter.validateAndEscapeProjectIri(value, throw BadRequestException(PROJECT_IRI_INVALID_ERROR))
        )

        validatedValue match {
          case Success(iri) => Right(new ProjectIRI(iri) {})
          case Failure(_)   => Left(BadRequestException(PROJECT_IRI_INVALID_ERROR))
        }
      }
    }
}
