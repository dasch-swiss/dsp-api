/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.`export`.domain

import sttp.tapir.Schema
import zio.json.JsonCodec

import org.knora.webapi.slice.admin.domain.model.UserIri
import org.knora.webapi.slice.common.domain.ServiceName

/**
 * Who created a data task: a user, or a trusted service, which is not a user.
 *
 * Serialised as one string in task files and API responses: the user IRI, or `urn:dsp:service:<name>`. Task files
 * written before services existed hold a bare user IRI and must keep decoding.
 */
enum TaskCreator {
  case ByUser(iri: UserIri)
  case ByService(name: ServiceName)

  def value: String = this match
    case ByUser(iri)     => iri.value
    case ByService(name) => TaskCreator.ServicePrefix + name.value
}

object TaskCreator {
  private val ServicePrefix: String = "urn:dsp:service:"

  def from(str: String): Either[String, TaskCreator] =
    if (str.startsWith(ServicePrefix)) ServiceName.from(str.stripPrefix(ServicePrefix)).map(ByService(_))
    else UserIri.from(str).map(ByUser(_))

  // Hand-written: a derived codec would encode the enum as a JSON object.
  given JsonCodec[TaskCreator] = JsonCodec.string.transformOrFail(from, _.value)
  given Schema[TaskCreator]    = Schema.string.description("IRI of the user or service that created the task.")
}
