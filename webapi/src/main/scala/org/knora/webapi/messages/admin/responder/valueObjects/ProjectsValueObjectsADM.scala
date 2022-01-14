/*
 * Copyright © 2021 Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.admin.responder.valueObjects

import org.knora.webapi.exceptions.BadRequestException
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectsErrorMessagesADM._
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import zio.prelude.Validation

/**
 * ProjectIRI value object.
 */
sealed abstract case class ProjectIRI private (value: String)
object ProjectIRI { self =>
  val sf: StringFormatter = StringFormatter.getGeneralInstance

  def make(value: String): Validation[Throwable, ProjectIRI] =
    if (value.isEmpty) {
      Validation.fail(BadRequestException(PROJECT_IRI_MISSING_ERROR))
    } else {
      if (!sf.isKnoraProjectIriStr(value)) {
        Validation.fail(BadRequestException(PROJECT_IRI_INVALID_ERROR))
      } else {
        val validatedValue = Validation(
          sf.validateAndEscapeProjectIri(value, throw BadRequestException(PROJECT_IRI_INVALID_ERROR))
        )

        validatedValue.map(new ProjectIRI(_) {})
      }
    }

  def make(value: Option[String]): Validation[Throwable, Option[ProjectIRI]] =
    value match {
      case Some(v) => self.make(v).map(Some(_))
      case None    => Validation.succeed(None)
    }
}

/**
 * Project Shortcode value object.
 */
sealed abstract case class Shortcode private (value: String)
object Shortcode { self =>
  val sf: StringFormatter = StringFormatter.getGeneralInstance

  def make(value: String): Validation[Throwable, Shortcode] =
    if (value.isEmpty) {
      Validation.fail(BadRequestException(SHORTCODE_MISSING_ERROR))
    } else {
      val validatedValue: Validation[Throwable, String] = Validation(
        sf.validateProjectShortcode(value, throw BadRequestException(SHORTCODE_INVALID_ERROR))
      )
      validatedValue.map(new Shortcode(_) {})
    }

  def make(value: Option[String]): Validation[Throwable, Option[Shortcode]] =
    value match {
      case Some(v) => self.make(v).map(Some(_))
      case None    => Validation.succeed(None)
    }
}

/**
 * Project Shortname value object.
 */
sealed abstract case class Shortname private (value: String)
object Shortname { self =>
  val sf: StringFormatter = StringFormatter.getGeneralInstance

  def make(value: String): Validation[Throwable, Shortname] =
    if (value.isEmpty) {
      Validation.fail(BadRequestException(SHORTNAME_MISSING_ERROR))
    } else {
      val validatedValue = Validation(
        sf.validateAndEscapeProjectShortname(value, throw BadRequestException(SHORTNAME_INVALID_ERROR))
      )
      validatedValue.map(new Shortname(_) {})
    }

  def make(value: Option[String]): Validation[Throwable, Option[Shortname]] =
    value match {
      case Some(v) => self.make(v).map(Some(_))
      case None    => Validation.succeed(None)
    }
}

/**
 * Project Longname value object.
 */
sealed abstract case class Longname private (value: String)
object Longname { self =>
  def make(value: String): Validation[Throwable, Longname] =
    if (value.isEmpty) {
      Validation.fail(BadRequestException(LONGNAME_MISSING_ERROR))
    } else {
      Validation.succeed(new Longname(value) {})
    }

  def make(value: Option[String]): Validation[Throwable, Option[Longname]] =
    value match {
      case None    => Validation.succeed(None)
      case Some(v) => self.make(v).map(Some(_))
    }
}

/**
 * ProjectDescription value object.
 */
sealed abstract case class ProjectDescription private (value: Seq[StringLiteralV2])
object ProjectDescription { self =>
  def make(value: Seq[StringLiteralV2]): Validation[Throwable, ProjectDescription] =
    if (value.isEmpty) {
      Validation.fail(BadRequestException(PROJECT_DESCRIPTION_MISSING_ERROR))
    } else {
      Validation.succeed(new ProjectDescription(value) {})
    }

  def make(value: Option[Seq[StringLiteralV2]]): Validation[Throwable, Option[ProjectDescription]] =
    value match {
      case Some(v) => self.make(v).map(Some(_))
      case None    => Validation.succeed(None)
    }
}

/**
 * Project Keywords value object.
 */
sealed abstract case class Keywords private (value: Seq[String])
object Keywords { self =>
  def make(value: Seq[String]): Validation[Throwable, Keywords] =
    if (value.isEmpty) {
      Validation.fail(BadRequestException(KEYWORDS_MISSING_ERROR))
    } else {
      Validation.succeed(new Keywords(value) {})
    }

  def make(value: Option[Seq[String]]): Validation[Throwable, Option[Keywords]] =
    value match {
      case Some(v) => self.make(v).map(Some(_))
      case None    => Validation.succeed(None)
    }
}

/**
 * Project Logo value object.
 */
sealed abstract case class Logo private (value: String)
object Logo { self =>
  def make(value: String): Validation[Throwable, Logo] =
    if (value.isEmpty) {
      Validation.fail(BadRequestException(LOGO_MISSING_ERROR))
    } else {
      Validation.succeed(new Logo(value) {})
    }
  def make(value: Option[String]): Validation[Throwable, Option[Logo]] =
    value match {
      case Some(v) => self.make(v).map(Some(_))
      case None    => Validation.succeed(None)
    }
}

/**
 * ProjectSelfjoin value object.
 */
sealed abstract case class ProjectSelfJoin private (value: Boolean)
object ProjectSelfJoin { self =>
  def make(value: Boolean): Validation[Throwable, ProjectSelfJoin] =
    Validation.succeed(new ProjectSelfJoin(value) {})

  def make(value: Option[Boolean]): Validation[Throwable, Option[ProjectSelfJoin]] =
    value match {
      case Some(v) => self.make(v).map(Some(_))
      case None    => Validation.succeed(None)
    }
}

/**
 * ProjectStatus value object.
 */
sealed abstract case class ProjectStatus private (value: Boolean)
object ProjectStatus { self =>
  def make(value: Boolean): Validation[Throwable, ProjectStatus] =
    Validation.succeed(new ProjectStatus(value) {})

  def make(value: Option[Boolean]): Validation[Throwable, Option[ProjectStatus]] =
    value match {
      case Some(v) => self.make(v).map(Some(_))
      case None    => Validation.succeed(None)
    }
}
