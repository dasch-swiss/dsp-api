/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders

import org.knora.webapi.messages.ResponderRequest
import org.knora.webapi.messages.admin.responder.groupsmessages.GroupsResponderRequestADM
import org.knora.webapi.messages.admin.responder.listsmessages.ListsResponderRequestADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionsResponderRequestADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectsResponderRequestADM
import org.knora.webapi.messages.admin.responder.sipimessages.SipiResponderRequestADM
import org.knora.webapi.messages.admin.responder.storesmessages.StoreResponderRequestADM
import org.knora.webapi.messages.admin.responder.usersmessages.UsersResponderRequestADM
import org.knora.webapi.messages.util.ResponderData
import org.knora.webapi.messages.v1.responder.ckanmessages.CkanResponderRequestV1
import org.knora.webapi.messages.v1.responder.listmessages.ListsResponderRequestV1
import org.knora.webapi.messages.v1.responder.ontologymessages.OntologyResponderRequestV1
import org.knora.webapi.messages.v1.responder.projectmessages.ProjectsResponderRequestV1
import org.knora.webapi.messages.v1.responder.resourcemessages.ResourcesResponderRequestV1
import org.knora.webapi.messages.v1.responder.searchmessages.SearchResponderRequestV1
import org.knora.webapi.messages.v1.responder.standoffmessages.StandoffResponderRequestV1
import org.knora.webapi.messages.v1.responder.usermessages.UsersResponderRequestV1
import org.knora.webapi.messages.v1.responder.valuemessages.ValuesResponderRequestV1
import org.knora.webapi.messages.v2.responder.listsmessages.ListsResponderRequestV2
import org.knora.webapi.messages.v2.responder.ontologymessages.OntologiesResponderRequestV2
import org.knora.webapi.messages.v2.responder.resourcemessages.ResourcesResponderRequestV2
import org.knora.webapi.messages.v2.responder.searchmessages.SearchResponderRequestV2
import org.knora.webapi.messages.v2.responder.standoffmessages.StandoffResponderRequestV2
import org.knora.webapi.messages.v2.responder.valuemessages.ValuesResponderRequestV2
import org.knora.webapi.responders.admin._
import org.knora.webapi.responders.v1._
import org.knora.webapi.responders.v2._

/**
 * This actor receives messages representing client requests, and forwards them to pools of specialised actors
 * that it supervises.
 *
 * @param appActor the main application actor.
 */
final case class ResponderManager(
  responderData: ResponderData
) { self =>

  // A subclass can replace the standard responders with custom responders, e.g. for testing. To do this, it must
  // override one or more of the protected val members below representing responder classes. To construct a default
  // responder, a subclass can call one of the protected methods below.

  /**
   * Constructs the [[CkanResponderV1]].
   */
  private val ckanResponderV1: CkanResponderV1 = new CkanResponderV1(responderData)

  /**
   * Constructs the [[ResourcesResponderV1]].
   */
  private val resourcesResponderV1: ResourcesResponderV1 = new ResourcesResponderV1(responderData)

  /**
   * Constructs the [[ValuesResponderV1]].
   */
  private val valuesResponderV1: ValuesResponderV1 = new ValuesResponderV1(responderData)

  /**
   * Constructs the [[StandoffResponderV1]].
   */
  private val standoffResponderV1: StandoffResponderV1 = new StandoffResponderV1(responderData)

  /**
   * Constructs the [[UsersResponderV1]].
   */
  private val usersResponderV1: UsersResponderV1 = new UsersResponderV1(responderData)

  /**
   * Constructs the [[ListsResponderV1]].
   */
  private val listsResponderV1: ListsResponderV1 = new ListsResponderV1(responderData)

  /**
   * Constructs the [[SearchResponderV1]].
   */
  private val searchResponderV1: SearchResponderV1 = new SearchResponderV1(responderData)

  /**
   * Constructs the [[OntologyResponderV1]].
   */
  private val ontologyResponderV1: OntologyResponderV1 = new OntologyResponderV1(responderData)

  /**
   * Constructs the [[ProjectsResponderV1]].
   */
  private val projectsResponderV1: ProjectsResponderV1 = new ProjectsResponderV1(responderData)

  //
  // V2 responders
  //

  /**
   * Constructs the [[OntologyResponderV2]].
   */
  private val ontologiesResponderV2: OntologyResponderV2 = new OntologyResponderV2(responderData)

  /**
   * Constructs the [[SearchResponderV2]].
   */
  private val searchResponderV2: SearchResponderV2 = new SearchResponderV2(responderData)

  /**
   * Constructs the [[ResourcesResponderV2]].
   */
  private val resourcesResponderV2: ResourcesResponderV2 = new ResourcesResponderV2(responderData)

  /**
   * Constructs the [[ValuesResponderV2]].
   */
  private val valuesResponderV2: ValuesResponderV2 = new ValuesResponderV2(responderData)

  /**
   * Constructs the [[StandoffResponderV2]].
   */
  private val standoffResponderV2: StandoffResponderV2 = new StandoffResponderV2(responderData)

  /**
   * Constructs the [[ListsResponderV2]].
   */
  private val listsResponderV2: ListsResponderV2 = new ListsResponderV2(responderData)

  //
  // Admin responders
  //

  /**
   * Constructs the [[GroupsResponderADM]].
   */
  private val groupsResponderADM: GroupsResponderADM = new GroupsResponderADM(responderData)

  /**
   * Constructs the [[ListsResponderADM]].
   */
  private val listsResponderADM: ListsResponderADM = new ListsResponderADM(responderData)

  /**
   * Constructs the [[PermissionsResponderADM]].
   */
  private val permissionsResponderADM: PermissionsResponderADM = new PermissionsResponderADM(responderData)

  /**
   * Constructs the [[ProjectsResponderADM]].
   */
  private val projectsResponderADM: ProjectsResponderADM = new ProjectsResponderADM(responderData)

  /**
   * Constructs the [[StoresResponderADM]].
   */
  private val storeResponderADM: StoresResponderADM = new StoresResponderADM(responderData)

  /**
   * Constructs the [[UsersResponderADM]].
   */
  private val usersResponderADM: UsersResponderADM = new UsersResponderADM(responderData)

  /**
   * Constructs the [[SipiResponderADM]].
   */
  private val sipiRouterADM: SipiResponderADM = new SipiResponderADM(responderData)

  /**
   * Each responder's receive method is called and only messages of the allowed type are supplied as the parameter.
   * If a serious error occurs (i.e. an error that isn't the client's fault), the future2Message method first
   * returns `Failure` to the sender, then throws an exception.
   */
  def receive(msg: ResponderRequest) = msg match {
    // Knora API V1 messages
    case ckanResponderRequestV1: CkanResponderRequestV1 =>
      ckanResponderV1.receive(ckanResponderRequestV1)
    case resourcesResponderRequestV1: ResourcesResponderRequestV1 =>
      resourcesResponderV1.receive(resourcesResponderRequestV1)
    case valuesResponderRequestV1: ValuesResponderRequestV1 =>
      valuesResponderV1.receive(valuesResponderRequestV1)
    case listsResponderRequestV1: ListsResponderRequestV1 =>
      listsResponderV1.receive(listsResponderRequestV1)
    case searchResponderRequestV1: SearchResponderRequestV1 =>
      searchResponderV1.receive(searchResponderRequestV1)
    case ontologyResponderRequestV1: OntologyResponderRequestV1 =>
      ontologyResponderV1.receive(ontologyResponderRequestV1)
    case standoffResponderRequestV1: StandoffResponderRequestV1 =>
      standoffResponderV1.receive(standoffResponderRequestV1)
    case usersResponderRequestV1: UsersResponderRequestV1 =>
      usersResponderV1.receive(usersResponderRequestV1)
    case projectsResponderRequestV1: ProjectsResponderRequestV1 =>
      projectsResponderV1.receive(projectsResponderRequestV1)

    // Knora API V2 messages
    case ontologiesResponderRequestV2: OntologiesResponderRequestV2 =>
      ontologiesResponderV2.receive(ontologiesResponderRequestV2)
    case searchResponderRequestV2: SearchResponderRequestV2 =>
      searchResponderV2.receive(searchResponderRequestV2)
    case resourcesResponderRequestV2: ResourcesResponderRequestV2 =>
      resourcesResponderV2.receive(resourcesResponderRequestV2)
    case valuesResponderRequestV2: ValuesResponderRequestV2 =>
      valuesResponderV2.receive(valuesResponderRequestV2)
    case standoffResponderRequestV2: StandoffResponderRequestV2 =>
      standoffResponderV2.receive(standoffResponderRequestV2)
    case listsResponderRequestV2: ListsResponderRequestV2 =>
      listsResponderV2.receive(listsResponderRequestV2)

    // Knora Admin message
    case groupsResponderRequestADM: GroupsResponderRequestADM =>
      groupsResponderADM.receive(groupsResponderRequestADM)
    case listsResponderRequest: ListsResponderRequestADM =>
      listsResponderADM.receive(listsResponderRequest)
    case permissionsResponderRequestADM: PermissionsResponderRequestADM =>
      permissionsResponderADM.receive(permissionsResponderRequestADM)
    case projectsResponderRequestADM: ProjectsResponderRequestADM =>
      projectsResponderADM.receive(projectsResponderRequestADM)
    case storeResponderRequestADM: StoreResponderRequestADM =>
      storeResponderADM.receive(storeResponderRequestADM)
    case usersResponderRequestADM: UsersResponderRequestADM =>
      usersResponderADM.receive(usersResponderRequestADM)
    case sipiResponderRequestADM: SipiResponderRequestADM =>
      sipiRouterADM.receive(sipiResponderRequestADM)
  }
}
