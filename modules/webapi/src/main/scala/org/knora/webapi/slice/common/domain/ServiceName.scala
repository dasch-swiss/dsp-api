/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.common.domain

import scala.util.matching.Regex

import org.knora.webapi.slice.common.StringValueCompanion
import org.knora.webapi.slice.common.Value.StringValue

/**
 * The identity of a trusted service caller, such as `dsp-repository`: lower-case letters, digits and hyphens.
 */
final case class ServiceName private (override val value: String) extends StringValue

object ServiceName extends StringValueCompanion[ServiceName] {

  private val Pattern: Regex = "^[a-z0-9-]+$".r

  def from(value: String): Either[String, ServiceName] =
    if (Pattern.matches(value)) Right(ServiceName(value))
    else Left("Service name must be non-empty and contain only lower-case letters, digits and hyphens.")
}
