/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.v1.responder.projectmessages

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.DefaultJsonProtocol
import spray.json.JsValue
import spray.json.JsonFormat
import spray.json.NullOptions
import spray.json.RootJsonFormat

import org.knora.webapi.IRI
import org.knora.webapi.messages.v1.responder.KnoraResponseV1

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Messages

// Responses
/**
 * Represents the Knora API v1 JSON response to a request for information about all projects.
 *
 * @param projects information about all existing projects.
 */
case class ProjectsResponseV1(projects: Seq[ProjectInfoV1]) extends KnoraResponseV1 with ProjectV1JsonProtocol {
  def toJsValue: JsValue = projectsResponseV1Format.write(this)
}

/**
 * Represents the Knora API v1 JSON response to a request for information about a single project.
 *
 * @param project_info all information about the project.
 */
case class ProjectInfoResponseV1(project_info: ProjectInfoV1) extends KnoraResponseV1 with ProjectV1JsonProtocol {
  def toJsValue: JsValue = projectInfoResponseV1Format.write(this)
}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Components of messages

/**
 * Represents basic information about a project.
 *
 * @param id          The project's IRI.
 * @param shortname   The project's shortname. Needs to be system wide unique.
 * @param longname    The project's long name. Needs to be system wide unique.
 * @param description The project's description.
 * @param keywords    The project's keywords.
 * @param logo        The project's logo.
 * @param institution The project's institution.
 * @param ontologies  The project's ontologies.
 * @param status      The project's status.
 * @param selfjoin    The project's self-join status.
 */
case class ProjectInfoV1(
  id: IRI,
  shortname: String,
  shortcode: String,
  longname: Option[String],
  description: Option[String],
  keywords: Option[String],
  logo: Option[String],
  institution: Option[IRI],
  ontologies: Seq[IRI],
  status: Boolean,
  selfjoin: Boolean
)

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// JSON formating

/**
 * A spray-json protocol for generating Knora API v1 JSON providing data about projects.
 */
trait ProjectV1JsonProtocol extends SprayJsonSupport with DefaultJsonProtocol with NullOptions {

  // Some of these formatters have to use lazyFormat because there is a recursive dependency between this
  // protocol and UserV1JsonProtocol. See ttps://github.com/spray/spray-json#jsonformats-for-recursive-types.
  // rootFormat makes it return the expected type again.
  // https://github.com/spray/spray-json#jsonformats-for-recursive-types
  implicit val projectInfoV1Format: JsonFormat[ProjectInfoV1] = jsonFormat11(ProjectInfoV1)
  implicit val projectsResponseV1Format: RootJsonFormat[ProjectsResponseV1] = rootFormat(
    lazyFormat(jsonFormat(ProjectsResponseV1, "projects"))
  )
  implicit val projectInfoResponseV1Format: RootJsonFormat[ProjectInfoResponseV1] = rootFormat(
    lazyFormat(jsonFormat(ProjectInfoResponseV1, "project_info"))
  )
}
