/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.v1.responder.projectmessages

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import org.knora.webapi.IRI
import org.knora.webapi.messages.v1.responder.KnoraRequestV1
import org.knora.webapi.messages.v1.responder.KnoraResponseV1
import org.knora.webapi.messages.v1.responder.usermessages.UserProfileV1
import spray.json.DefaultJsonProtocol
import spray.json.JsValue
import spray.json.JsonFormat
import spray.json.NullOptions
import spray.json.RootJsonFormat

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// API requests

// use 'admin' route for writing.

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Messages

/**
 * An abstract trait representing a request message that can be sent to [[org.knora.webapi.responders.v1.ProjectsResponderV1]].
 */
sealed trait ProjectsResponderRequestV1 extends KnoraRequestV1

// Requests
/**
 * Get all information about all projects in form of [[ProjectsResponseV1]]. The ProjectsGetRequestV1 returns either
 * something or a NotFound exception if there are no projects found. Administration permission checking is performed.
 *
 * @param userProfile          the profile of the user making the request.
 */
case class ProjectsGetRequestV1(userProfile: Option[UserProfileV1]) extends ProjectsResponderRequestV1

/**
 * Get all information about all projects in form of a sequence of [[ProjectInfoV1]]. Returns an empty sequence if
 * no projects are found. Administration permission checking is skipped.
 *
 * @param userProfile          the profile of the user making the request.
 */
case class ProjectsGetV1(userProfile: Option[UserProfileV1]) extends ProjectsResponderRequestV1

/**
 * Get info about a single project identified through its IRI. A successful response will be a [[ProjectInfoResponseV1]].
 *
 * @param iri                  the IRI of the project.
 * @param userProfileV1        the profile of the user making the request (optional).
 */
case class ProjectInfoByIRIGetRequestV1(
  iri: IRI,
  userProfileV1: Option[UserProfileV1]
) extends ProjectsResponderRequestV1

/**
 * Get info about a single project identified through its IRI. A successful response will be an [[Option[ProjectInfoV1] ]].
 *
 * @param iri                  the IRI of the project.
 * @param userProfileV1        the profile of the user making the request (optional).
 */
case class ProjectInfoByIRIGetV1(
  iri: IRI,
  userProfileV1: Option[UserProfileV1]
) extends ProjectsResponderRequestV1

/**
 * Find everything about a single project identified through its shortname.
 *
 * @param shortname            of the project.
 * @param userProfileV1        the profile of the user making the request.
 */
case class ProjectInfoByShortnameGetRequestV1(
  shortname: String,
  userProfileV1: Option[UserProfileV1]
) extends ProjectsResponderRequestV1

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
