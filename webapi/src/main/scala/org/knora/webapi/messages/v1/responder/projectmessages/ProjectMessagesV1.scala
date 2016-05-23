/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and André Fatton.
 *
 * This file is part of Knora.
 *
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.messages.v1.responder.projectmessages

import org.knora.webapi.messages.v1.responder.usermessages.{UserDataV1, UserProfileV1}
import org.knora.webapi.{IRI, InconsistentTriplestoreDataException}
import org.knora.webapi.messages.v1.responder.{KnoraRequestV1, KnoraResponseV1}
import org.knora.webapi.responders.v1.ProjectsResponderV1
import spray.json.{DefaultJsonProtocol, JsonFormat, NullOptions, RootJsonFormat}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Messages

/**
  * An abstract trait representing a request message that can be sent to [[ProjectsResponderV1]].
  */
sealed trait ProjectsResponderRequestV1 extends KnoraRequestV1

// Requests
/**
  * Get all information about all projects.
  *
  * @param userProfile the profile of the user making the request.
  */
case class ProjectsGetRequestV1(userProfile: Option[UserProfileV1]) extends ProjectsResponderRequestV1


/**
  * Get everything about a single project identified through it's IRI.
  *
  * @param iri Iri of the project.
  * @param requestType is the type of the project information: full or short.
  * @param userProfileV1 the profile of the user making the request.
  */
case class ProjectInfoByIRIGetRequest(iri: IRI, requestType: ProjectInfoType.Value, userProfileV1: Option[UserProfileV1]) extends ProjectsResponderRequestV1


/**
  * Find everything about a single project identified through it's shortname.
  *
  * @param shortname of the project.
  * @param requestType is the type of the project information.
  * @param userProfileV1 the profile of the user making the request.
  */
case class ProjectInfoByShortnameGetRequest(shortname: String, requestType: ProjectInfoType.Value, userProfileV1: Option[UserProfileV1]) extends ProjectsResponderRequestV1


// Responses
/**
  * Represents the Knora API v1 JSON response to a request for information about all projects.
  *
  * @param projects information about all existing projects.
  * @param userdata information about the user that made the request.
  */
case class ProjectsResponseV1(projects: Seq[ProjectInfoV1], userdata: Option[UserDataV1]) extends KnoraResponseV1 {
    def toJsValue = ProjectV1JsonProtocol.projectsResponseV1Format.write(this)
}

/**
  * Represents the Knora API v1 JSON response to a request for information about a single project.
  *
  * @param project_info all information about the project.
  * @param userdata information about the user that made the request.
  */
case class ProjectInfoResponseV1(project_info: ProjectInfoV1, userdata: Option[UserDataV1]) extends KnoraResponseV1 {
    def toJsValue = ProjectV1JsonProtocol.projectInfoResponseV1Format.write(this)
}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Components of messages

case class ProjectInfoV1(id: IRI,
                         shortname: String,
                         longname: Option[String] = None,
                         description: Option[String] = None,
                         keywords: Option[String] = None,
                         logo: Option[String] = None,
                         basepath: Option[String] = None,
                         rights: Option[Int] = None)

object ProjectInfoType extends Enumeration {
    val SHORT = Value(0, "short")
    val FULL = Value(1, "full")

    val valueMap: Map[String, Value] = values.map(v => (v.toString, v)).toMap

    /**
      * Given the name of a value in this enumeration, returns the value. If the value is not found, throws an
      * [[InconsistentTriplestoreDataException]].
      *
      * @param name the name of the value.
      * @return the requested value.
      */
    def lookup(name: String): Value = {
        valueMap.get(name) match {
            case Some(value) => value
            case None => throw InconsistentTriplestoreDataException(s"Project info type not supported: $name")
        }
    }
}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// JSON formating

/**
  * A spray-json protocol for generating Knora API v1 JSON providing data about projects.
  */
object ProjectV1JsonProtocol extends DefaultJsonProtocol with NullOptions {

    implicit val projectInfoV1Format: JsonFormat[ProjectInfoV1] = jsonFormat8(ProjectInfoV1)
    // we have to use lazyFormat here because `UserV1JsonProtocol` contains an import statement for this object.
    // this results in recursive import statements
    // rootFormat makes it return the expected type again.
    // https://github.com/spray/spray-json#jsonformats-for-recursive-types
    implicit val projectsResponseV1Format: RootJsonFormat[ProjectsResponseV1] = rootFormat(lazyFormat(jsonFormat2(ProjectsResponseV1)))
    implicit val projectInfoResponseV1Format: RootJsonFormat[ProjectInfoResponseV1] = rootFormat(lazyFormat(jsonFormat2(ProjectInfoResponseV1)))
}