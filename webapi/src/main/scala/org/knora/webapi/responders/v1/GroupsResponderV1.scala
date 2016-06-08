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

package org.knora.webapi.responders.v1

import akka.actor.Status
import akka.pattern._
import org.knora.webapi.messages.v1.responder.projectmessages.ProjectsResponderRequestV1
import org.knora.webapi.messages.v1.responder.groupmessages._
import org.knora.webapi.messages.v1.responder.usermessages.UserProfileV1
import org.knora.webapi.messages.v1.store.triplestoremessages.{SparqlSelectRequest, SparqlSelectResponse, VariableResultsRow}
import org.knora.webapi.util.ActorUtil._
import org.knora.webapi.{IRI, NotFoundException, OntologyConstants, UnexpectedMessageException}

import scala.concurrent.Future


/**
  * Returns information about Knora projects.
  */
class GroupsResponderV1 extends ResponderV1 {

    /**
      * Receives a message extending [[ProjectsResponderRequestV1]], and returns an appropriate response message, or
      * [[Status.Failure]]. If a serious error occurs (i.e. an error that isn't the client's fault), this
      * method first returns `Failure` to the sender, then throws an exception.
      */
    def receive = {
        case GroupsGetRequestV1(userProfile) => future2Message(sender(), getGroupsResponseV1(userProfile), log)
        case GroupInfoByIRIGetRequest(iri, infoType, userProfile) => future2Message(sender(), getGroupInfoByIRIGetRequest(iri, infoType, userProfile), log)
        case GroupInfoByNameGetRequest(shortname, infoType, userProfile) => future2Message(sender(), getGroupInfoByNameGetRequest(shortname, infoType, userProfile), log)
        case other => sender ! Status.Failure(UnexpectedMessageException(s"Unexpected message $other of type ${other.getClass.getCanonicalName}"))
    }

    /**
      * Gets permissions for the current user on the given project.
      *
      * @param groupIri the Iri of the project.
      * @param propertiesForGroup assertions containing permissions on the project.
      * @param userProfile the user that is making the request.
      * @return permission level of the current user on the project.
      */
    private def getUserPermissionV1ForGroup(groupIri: IRI, propertiesForGroup: Map[IRI, String], userProfile: UserProfileV1): Option[Int] = {

        // propertiesForProject must contain an owner for the project (knora-base:attachedToUser).
        propertiesForGroup.get(OntologyConstants.KnoraBase.AttachedToUser) match {
            case Some(user) => // add statement that `PermissionUtil.getUserPermissionV1` requires but is not present in the data for projects.
                val assertionsForProject: Seq[(IRI, IRI)] = (OntologyConstants.KnoraBase.AttachedToProject, groupIri) +: propertiesForGroup.toVector
                PermissionUtilV1.getUserPermissionV1(groupIri, assertionsForProject, userProfile)
            case None => None // TODO: this is temporary to prevent PermissionUtil.getUserPermissionV1 from failing because owner id is missing in the data for project. See issue 1.
        }
    }

    /**
      * Gets all the groups and returns them as a [[GroupsResponseV1]].
      *
      * @param userProfile the profile of the user that is making the request.
      * @return all the groups as a [[GroupsResponseV1]].
      */
    private def getGroupsResponseV1(userProfile: Option[UserProfileV1]): Future[GroupsResponseV1] = {

        for {
            sparqlQuery <- Future(queries.sparql.v1.txt.getProjects(
                triplestore = settings.triplestoreType
            ).toString())
            groupsResponse <- (storeManager ? SparqlSelectRequest(sparqlQuery)).mapTo[SparqlSelectResponse]
            groupsResponseRows: Seq[VariableResultsRow] = groupsResponse.results.bindings

            groupsWithProperties: Map[String, Map[String, String]] = groupsResponseRows.groupBy(_.rowMap("s")).map {
                case (projIri: String, rows: Seq[VariableResultsRow]) => (projIri, rows.map {
                    case row => (row.rowMap("p"), row.rowMap("o"))
                }.toMap)
            }

            groups = groupsWithProperties.map {
                case (projIri: String, propsMap: Map[String, String]) =>

                    val rightsInProject = userProfile match {
                        case Some(profile) => getUserPermissionV1ForGroup(groupIri = projIri, propertiesForGroup = propsMap, profile)
                        case None => None
                    }

                    GroupInfoV1(
                        id = projIri,
                        name = propsMap.getOrElse(OntologyConstants.Foaf.Name, ""),
                        description = propsMap.get(OntologyConstants.KnoraBase.Description)
                    )
            }.toVector
        } yield GroupsResponseV1(
            groups = groups,
            userdata = userProfile match {
                case Some(profile) => Some(profile.userData)
                case None => None
            }
        )
    }

    /**
      * Gets the group with the given group Iri and returns the information as a [[GroupInfoResponseV1]].
      *
      * @param groupIri the Iri of the group requested.
      * @param requestType type request: either short or full.
      * @param userProfile the profile of user that is making the request.
      * @return information about the group as a [[GroupInfoResponseV1]].
      */
    private def getGroupInfoByIRIGetRequest(groupIri: IRI, requestType: GroupInfoType.Value, userProfile: Option[UserProfileV1] = None): Future[GroupInfoResponseV1] = {
        for {
            sparqlQuery <- Future(queries.sparql.v1.txt.getGroupByIri(
                triplestore = settings.triplestoreType,
                groupIri = groupIri
            ).toString())
            groupResponse <- (storeManager ? SparqlSelectRequest(sparqlQuery)).mapTo[SparqlSelectResponse]

            groupInfo = createGroupInfoV1FromGroupResponse(groupResponse = groupResponse.results.bindings, groupIri = groupIri, requestType = requestType, userProfile)

        } yield GroupInfoResponseV1(
            group_info = groupInfo,
            userdata = userProfile match {
                case Some(profile) => Some(profile.userData)
                case None => None
            }
        )
    }

    /**
      * Gets the group with the given name and returns the information as a [[GroupInfoResponseV1]].
      *
      * @param name the name of the project requested.
      * @param requestType type request: either short or full.
      * @param userProfile the profile of user that is making the request.
      * @return information about the project as a [[GroupInfoResponseV1]].
      */
    private def getGroupInfoByNameGetRequest(name: String, requestType: GroupInfoType.Value, userProfile: Option[UserProfileV1]): Future[GroupInfoResponseV1] = {
        for {
            sparqlQuery <- Future(queries.sparql.v1.txt.getGroupByName(
                triplestore = settings.triplestoreType,
                name = name
            ).toString())
            groupResponse <- (storeManager ? SparqlSelectRequest(sparqlQuery)).mapTo[SparqlSelectResponse]

            // get project Iri from results rows
            groupIri: IRI = if (groupResponse.results.bindings.nonEmpty) {
                groupResponse.results.bindings.head.rowMap("s")
            } else {
                throw NotFoundException(s"For the given group name $name no information was found")
            }

            projectInfo = createGroupInfoV1FromGroupResponse(groupResponse = groupResponse.results.bindings, groupIri = groupIri, requestType = requestType, userProfile)

        } yield GroupInfoResponseV1(
            group_info = projectInfo,
            userdata = userProfile match {
                case Some(profile) => Some(profile.userData)
                case None => None
            }
        )
    }


    /**
      * Helper method that turns SPARQL result rows into a [[GroupInfoV1]].
      *
      * @param groupResponse results from the SPARQL query representing information about the group.
      * @param groupIri the Iri of the group the querid information belong to.
      * @param requestType type request: either short or full.
      * @param userProfile the profile of the user that is making the request.
      * @return a [[GroupInfoV1]] representing information about the group.
      */
    private def createGroupInfoV1FromGroupResponse(groupResponse: Seq[VariableResultsRow], groupIri: IRI, requestType: GroupInfoType.Value, userProfile: Option[UserProfileV1]): GroupInfoV1 = {

        if (groupResponse.nonEmpty) {

            val groupProperties = groupResponse.foldLeft(Map.empty[IRI, String]) {
                case (acc, row: VariableResultsRow) =>
                    acc + (row.rowMap("p") -> row.rowMap("o"))
            }

            val rightsInProject = userProfile match {
                case Some(profile) => getUserPermissionV1ForGroup(groupIri = groupIri, propertiesForGroup = groupProperties, profile)
                case None => None
            }

            requestType match {
                //TODO: For now both cases return the same information. This should change in the future.
                case GroupInfoType.FULL =>
                    GroupInfoV1(
                        id = groupIri,
                        name = groupProperties.getOrElse(OntologyConstants.Foaf.Name, ""),
                        description = groupProperties.get(OntologyConstants.KnoraBase.Description)
                    )
                case GroupInfoType.SHORT | _ =>
                    GroupInfoV1(
                        id = groupIri,
                        name = groupProperties.getOrElse(OntologyConstants.Foaf.Name, ""),
                        description = groupProperties.get(OntologyConstants.KnoraBase.Description)
                    )
            }
        } else {
            // no information was found for the given project Iri
            throw NotFoundException(s"For the given group Iri $groupIri no information was found")

        }

    }


}
