/*
 * Copyright © 2015-2018 the contributors (see Contributors.md).
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

package org.knora.webapi.messages.admin.responder.projectsmessages

import java.util.UUID

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import org.knora.webapi.messages.admin.responder.ontologiesmessages.OntologyInfoShortADM
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.admin.responder.{KnoraRequestADM, KnoraResponseADM}
import org.knora.webapi.messages.store.triplestoremessages.{StringLiteralV2, TriplestoreJsonProtocol}
import org.knora.webapi.messages.v1.responder.projectmessages.ProjectInfoV1
import org.knora.webapi.responders.admin.ProjectsResponderADM
import org.knora.webapi.{BadRequestException, IRI}
import spray.json.{DefaultJsonProtocol, JsValue, JsonFormat, RootJsonFormat}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// API requests

/**
  * Represents an API request payload that asks the Knora API server to create a new project.
  *
  * @param shortname   the shortname of the project to be created (unique).
  * @param shortcode   the shortcode of the project to be creates (unique, optional)
  * @param longname    the longname of the project to be created.
  * @param description the description of the project to be created.
  * @param keywords    the keywords of the project to be created.
  * @param logo        the logo of the project to be created.
  * @param status      the status of the project to be created (active = true, inactive = false).
  * @param selfjoin    the status of self-join of the project to be created.
  */
case class CreateProjectApiRequestADM(shortname: String,
                                      shortcode: String,
                                      longname: Option[String],
                                      description: Seq[StringLiteralV2],
                                      keywords: Seq[String],
                                      logo: Option[String],
                                      status: Boolean,
                                      selfjoin: Boolean) extends ProjectsADMJsonProtocol {
    def toJsValue = createProjectApiRequestADMFormat.write(this)
}

/**
  * Represents an API request payload that asks the Knora API server to update an existing project.
  *
  * @param shortname     the new project's shortname.
  * @param longname      the new project's longname.
  * @param description   the new project's description.
  * @param keywords      the new project's keywords.
  * @param logo          the new project's logo.
  * @param status        the new project's status.
  * @param selfjoin      the new project's self-join status.
  */
case class ChangeProjectApiRequestADM(shortname: Option[String] = None,
                                     longname: Option[String] = None,
                                     description: Option[Seq[StringLiteralV2]] = None,
                                     keywords: Option[Seq[String]] = None,
                                     logo: Option[String] = None,
                                     status: Option[Boolean] = None,
                                     selfjoin: Option[Boolean] = None) extends ProjectsADMJsonProtocol {

    val parametersCount = List(
        shortname,
        longname,
        description,
        keywords,
        logo,
        status,
        selfjoin
    ).flatten.size

    // something needs to be sent, i.e. everything 'None' is not allowed
    if (parametersCount == 0) throw BadRequestException("No data sent in API request.")

    // change basic project information case
    if (parametersCount > 8) throw BadRequestException("To many parameters sent for changing basic project information.")

    def toJsValue = changeProjectApiRequestADMFormat.write(this)
}



//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Messages

/**
  * An abstract trait representing a request message that can be sent to [[ProjectsResponderADM]].
  */
sealed trait ProjectsResponderRequestADM extends KnoraRequestADM

// Requests
/**
  * Get all information about all projects in form of [[ProjectsGetResponseADM]]. The ProjectsGetRequestV1 returns either
  * something or a NotFound exception if there are no projects found. Administration permission checking is performed.
  *
  * @param requestingUser the user making the request.
  */
case class ProjectsGetRequestADM(requestingUser: UserADM) extends ProjectsResponderRequestADM

/**
  * Get all information about all projects in form of a sequence of [[ProjectADM]]. Returns an empty sequence if
  * no projects are found. Administration permission checking is skipped.
  *
  * @param requestingUser the user making the request.
  */
case class ProjectsGetADM(requestingUser: UserADM) extends ProjectsResponderRequestADM

/**
  * Get info about a single project identified either through its IRI, shortname or shortcode. The response is in form
  * of [[ProjectGetResponseADM]]. External use.
  *
  * @param maybeIri           the IRI of the project.
  * @param maybeShortname the project's short name.
  * @param maybeShortcode the project's shortcode.
  * @param requestingUser the user making the request.
  */
case class ProjectGetRequestADM(maybeIri: Option[IRI] = None,
                                maybeShortname: Option[String] = None,
                                maybeShortcode: Option[String] = None,
                                requestingUser: UserADM) extends ProjectsResponderRequestADM {
    val parametersCount = List(
        maybeIri,
        maybeShortname,
        maybeShortcode
    ).flatten.size

    // only one is allowed
    if (parametersCount == 0 || parametersCount > 1) throw BadRequestException("Need to provide either project IRI, shortname, or shortcode.")
}

/**
  * Get info about a single project identified either through its IRI, shortname or shortcode. The response is in form
  * of [[ProjectADM]]. Internal use only.
  *
  * @param maybeIri           the IRI of the project.
  * @param maybeShortname the project's short name.
  * @param maybeShortcode the project's shortcode.
  * @param requestingUser the user making the request.
  */
case class ProjectGetADM(maybeIri: Option[IRI],
                         maybeShortname: Option[String],
                         maybeShortcode: Option[String],
                         requestingUser: UserADM) extends ProjectsResponderRequestADM {

    val parametersCount = List(
        maybeIri,
        maybeShortname,
        maybeShortcode
    ).flatten.size

    // Only one is allowed
    if (parametersCount == 0 || parametersCount > 1) throw BadRequestException("Need to provide either project IRI, shortname, or shortcode.")
}

/**
  * Returns all users belonging to a project identified either through its IRI, shortname or shortcode.
  *
  * @param maybeIri           the IRI of the project.
  * @param maybeShortname the project's short name.
  * @param maybeShortcode the project's shortcode.
  * @param requestingUser the user making the request.
  */
case class ProjectMembersGetRequestADM(maybeIri: Option[IRI],
                                       maybeShortname: Option[String],
                                       maybeShortcode: Option[String],
                                       requestingUser: UserADM) extends ProjectsResponderRequestADM {

    val parametersCount = List(
        maybeIri,
        maybeShortname,
        maybeShortcode
    ).flatten.size

    // Only one is allowed
    if (parametersCount == 0 || parametersCount > 1) throw BadRequestException("Need to provide either project IRI, shortname, or shortcode.")
}


/**
  * Returns all admin users of a project identified either through its IRI, shortname or shortcode.
  *
  * @param maybeIri       the IRI of the project.
  * @param maybeShortname the project's short name.
  * @param maybeShortcode the project's shortcode.
  * @param requestingUser the user making the request.
  */
case class ProjectAdminMembersGetRequestADM(maybeIri: Option[IRI],
                                            maybeShortname: Option[String],
                                            maybeShortcode: Option[String],
                                            requestingUser: UserADM) extends ProjectsResponderRequestADM {

    val parametersCount = List(
        maybeIri,
        maybeShortname,
        maybeShortcode
    ).flatten.size

    // Only one is allowed
    if (parametersCount == 0 || parametersCount > 1) throw BadRequestException("Need to provide either project IRI, shortname, or shortcode.")
}

/**
  * Returns all unique keywords for all projects.
  *
  * @param requestingUser the user making the request.
  */
case class ProjectsKeywordsGetRequestADM(requestingUser: UserADM) extends ProjectsResponderRequestADM

/**
  * Returns all keywords for a project identified through IRI.
  *
  * @param projectIri the IRI of the project.
  * @param requestingUser the user making the request.
  */
case class ProjectKeywordsGetRequestADM(projectIri: IRI,
                                        requestingUser: UserADM) extends ProjectsResponderRequestADM


/**
  * Requests the creation of a new project.
  *
  * @param createRequest the [[CreateProjectApiRequestADM]] information for creation a new project.
  * @param requestingUser the user making the request.
  * @param apiRequestID  the ID of the API request.
  */
case class ProjectCreateRequestADM(createRequest: CreateProjectApiRequestADM,
                                   requestingUser: UserADM,
                                   apiRequestID: UUID) extends ProjectsResponderRequestADM

/**
  * Requests updating an existing project.
  *
  * @param projectIri           the IRI of the project to be updated.
  * @param changeProjectRequest the data which needs to be update.
  * @param requestingUser the user making the request.
  * @param apiRequestID         the ID of the API request.
  */
case class ProjectChangeRequestADM(projectIri: IRI,
                                   changeProjectRequest: ChangeProjectApiRequestADM,
                                   requestingUser: UserADM,
                                   apiRequestID: UUID) extends ProjectsResponderRequestADM

/**
  * Get all the project ontologies as a sequence of [[org.knora.webapi.messages.admin.responder.ontologiesmessages.OntologyInfoADM]].
  *
  * @param projectIri the IRI of the project.
  * @param requestingUser the user making the request.
  */
case class ProjectOntologyInfosGetADM(projectIri: IRI,
                                      requestingUser: UserADM) extends ProjectsADMJsonProtocol


/**
  * Requests adding an ontology to the project. This is an internal message, which should
  * only be sent by the ontology responder who is responsible for actually creating the
  * ontology.
  *
  * @param projectIri the IRI of the project to be updated.
  * @param ontologyIri the IRI of the ontology to be added.
  * @param requestingUser the user making the request.
  * @param apiRequestID the ID of the API request.
  */
case class ProjectOntologyAddADM(projectIri: IRI,
                                 ontologyIri: IRI,
                                 requestingUser: UserADM,
                                 apiRequestID: UUID) extends ProjectsResponderRequestADM


/**
  * Requests removing an ontology from the project. This is an internal message, which should
  * only be sent by the ontology responder who is responsible for actually removing the
  * ontology.
  *
  * @param projectIri the IRI of the project to be updated.
  * @param ontologyIri the IRI of the ontology to be removed.
  * @param requestingUser the user making the request.
  * @param apiRequestID the ID of the API request.
  */
case class ProjectOntologyRemoveADM(projectIri: IRI,
                                    ontologyIri: IRI,
                                    requestingUser: UserADM,
                                    apiRequestID: UUID) extends ProjectsResponderRequestADM

// Responses
/**
  * Represents the Knora API ADM JSON response to a request for information about all projects.
  *
  * @param projects information about all existing projects.
  */
case class ProjectsGetResponseADM(projects: Seq[ProjectADM]) extends KnoraResponseADM with ProjectsADMJsonProtocol {
    def toJsValue = projectsResponseADMFormat.write(this)
}

/**
  * Represents the Knora API ADM JSON response to a request for information about a single project.
  *
  * @param project all information about the project.
  */
case class ProjectGetResponseADM(project: ProjectADM) extends KnoraResponseADM with ProjectsADMJsonProtocol {
    def toJsValue = projectResponseADMFormat.write(this)
}

/**
  * Represents the Knora API ADM JSON response to a request for a list of members inside a single project.
  *
  * @param members    a list of members.
  */
case class ProjectMembersGetResponseADM(members: Seq[UserADM]) extends KnoraResponseADM with ProjectsADMJsonProtocol {

    def toJsValue = projectMembersGetResponseADMFormat.write(this)
}

/**
  * Represents the Knora API ADM JSON response to a request for a list of admin members inside a single project.
  *
  * @param members    a list of admin members.
  */
case class ProjectAdminMembersGetResponseADM(members: Seq[UserADM]) extends KnoraResponseADM with ProjectsADMJsonProtocol {

    def toJsValue = projectAdminMembersGetResponseADMFormat.write(this)
}

/**
  * Represents a response to a request for all keywords of all projects.
  *
  * @param keywords a list of keywords.
  */
case class ProjectsKeywordsGetResponseADM(keywords: Seq[String]) extends KnoraResponseADM with ProjectsADMJsonProtocol {
    def toJsValue: JsValue = projectsKeywordsGetResponseADMFormat.write(this)
}

/**
  * Represents a response to a request for all keywords of a single project.
  *
  * @param keywords a list of keywords.
  */
case class ProjectKeywordsGetResponseADM(keywords: Seq[String]) extends KnoraResponseADM with ProjectsADMJsonProtocol {
    def toJsValue: JsValue = projectKeywordsGetResponseADMFormat.write(this)
}

/**
  * Represents an answer to a project creating/modifying operation.
  *
  * @param project the new project info of the created/modified project.
  */
case class ProjectOperationResponseADM(project: ProjectADM) extends KnoraResponseADM with ProjectsADMJsonProtocol {
    def toJsValue = projectOperationResponseADMFormat.write(this)
}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Components of messages


/**
  * Represents basic information about a project.
  *
  * @param id                 The project's IRI.
  * @param shortname          The project's shortname. Needs to be system wide unique.
  * @param longname           The project's long name.
  * @param description        The project's description.
  * @param keywords           The project's keywords.
  * @param logo               The project's logo.
  * @param ontologies         The project's ontologies.
  * @param status             The project's status.
  * @param selfjoin           The project's self-join status.
  */
case class ProjectADM(id: IRI,
                      shortname: String,
                      shortcode: Option[String],
                      longname: Option[String],
                      description: Seq[StringLiteralV2],
                      keywords: Seq[String],
                      logo: Option[String],
                      ontologies: Seq[OntologyInfoShortADM],
                      status: Boolean,
                      selfjoin: Boolean) extends Ordered[ProjectADM] {

    /**
      * Allows to sort collections of ProjectADM. Sorting is done by the id.
      */
    def compare(that: ProjectADM): Int = this.id.compareTo(that.id)

    // ToDo: Refactor by using implicit conversions (when I manage to understand them)
    def asProjectInfoV1: ProjectInfoV1 = {

        val descriptionV1 = if (description.nonEmpty) {
            Some(description.head.value)
        } else {
            None
        }

        val keywordsV1 = if (keywords.nonEmpty) {
            Some(keywords.mkString(", "))
        } else {
            None
        }

        val ontologiesV1 = this.ontologies.map(_.ontologyIri.toString)

        ProjectInfoV1(
            id = id,
            shortname = shortname,
            shortcode = shortcode,
            longname = longname,
            description = descriptionV1,
            keywords = keywordsV1,
            logo = logo,
            institution = None,
            ontologies = ontologiesV1,
            status = status,
            selfjoin = selfjoin
        )
    }
}

/**
  * Payload used for updating of an existing project.
  *
  * @param shortname          The project's shortname. Needs to be system wide unique.
  * @param longname           The project's long name.
  * @param description        The project's description.
  * @param keywords           The project's keywords.
  * @param logo               The project's logo.
  * @param ontologies         The project's ontologies.
  * @param status             The project's status.
  * @param selfjoin           The project's self-join status.
  */
case class ProjectUpdatePayloadADM(shortname: Option[String] = None,
                                   longname: Option[String] = None,
                                   description: Option[Seq[StringLiteralV2]] = None,
                                   keywords: Option[Seq[String]] = None,
                                   logo: Option[String] = None,
                                   ontologies: Option[Seq[IRI]] = None,
                                   status: Option[Boolean] = None,
                                   selfjoin: Option[Boolean] = None)

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// JSON formating

/**
  * A spray-json protocol for generating Knora API v1 JSON providing data about projects.
  */
trait ProjectsADMJsonProtocol extends SprayJsonSupport with DefaultJsonProtocol with TriplestoreJsonProtocol {

    import org.knora.webapi.messages.admin.responder.ontologiesmessages.OntologiesADMJsonProtocol._
    import org.knora.webapi.messages.admin.responder.usersmessages.UsersADMJsonProtocol._

    implicit val projectADMFormat: JsonFormat[ProjectADM] = lazyFormat(jsonFormat10(ProjectADM))
    implicit val projectsResponseADMFormat: RootJsonFormat[ProjectsGetResponseADM] = rootFormat(lazyFormat(jsonFormat(ProjectsGetResponseADM, "projects")))
    implicit val projectResponseADMFormat: RootJsonFormat[ProjectGetResponseADM] = rootFormat(lazyFormat(jsonFormat(ProjectGetResponseADM, "project")))

    implicit val projectAdminMembersGetResponseADMFormat: RootJsonFormat[ProjectAdminMembersGetResponseADM] = rootFormat(lazyFormat(jsonFormat(ProjectAdminMembersGetResponseADM, "members")))
    implicit val projectMembersGetResponseADMFormat: RootJsonFormat[ProjectMembersGetResponseADM] = rootFormat(lazyFormat(jsonFormat(ProjectMembersGetResponseADM, "members")))
    implicit val createProjectApiRequestADMFormat: RootJsonFormat[CreateProjectApiRequestADM] = rootFormat(lazyFormat(jsonFormat(CreateProjectApiRequestADM, "shortname", "shortcode", "longname", "description", "keywords", "logo", "status", "selfjoin")))
    implicit val changeProjectApiRequestADMFormat: RootJsonFormat[ChangeProjectApiRequestADM] = rootFormat(lazyFormat(jsonFormat(ChangeProjectApiRequestADM, "shortname", "longname", "description", "keywords", "logo", "status", "selfjoin")))
    implicit val projectsKeywordsGetResponseADMFormat: RootJsonFormat[ProjectsKeywordsGetResponseADM] = jsonFormat(ProjectsKeywordsGetResponseADM, "keywords")
    implicit val projectKeywordsGetResponseADMFormat: RootJsonFormat[ProjectKeywordsGetResponseADM] = jsonFormat(ProjectKeywordsGetResponseADM, "keywords")
    implicit val projectOperationResponseADMFormat: RootJsonFormat[ProjectOperationResponseADM] = rootFormat(lazyFormat(jsonFormat(ProjectOperationResponseADM, "project")))

}