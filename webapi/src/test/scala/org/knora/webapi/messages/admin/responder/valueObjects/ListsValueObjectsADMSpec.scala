/*
 * Copyright © 2015-2021 Data and Service Center for the Humanities (DaSCH)
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

package org.knora.webapi.messages.admin.responder.valueObjects

import org.knora.webapi.exceptions.BadRequestException
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.listsmessages.NodeCreatePayloadADM.{
  ChildNodeCreatePayloadADM,
  ListCreatePayloadADM
}
import org.knora.webapi.messages.admin.responder.listsmessages.{CreateNodeApiRequestADM, NodeCreatePayloadADM}
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.{IRI, UnitSpec}
import org.scalatest.enablers.Messaging.messagingNatureOfThrowable

/**
 * This spec is used to test the creation of value objects of the [[ListsValueObjectsADM]].
 */
class ListsValueObjectsADMSpec extends UnitSpec(ValueObjectsADMSpec.config) {

//  TODO: these test should be simplified - UNIT TESTS about value objects only

  private implicit val stringFormatter: StringFormatter = StringFormatter.getInstanceForConstantOntologies

  /**
   * Convenience method returning the NodeCreatePayloadADM from the [[CreateNodeApiRequestADM]] object
   *
   * @param createNodeApiRequestADM the [[CreateNodeApiRequestADM]] object
   * @return                        a [[NodeCreatePayloadADM]]
   */
  private def createRootNodeCreatePayloadADM(
    createNodeApiRequestADM: CreateNodeApiRequestADM
  ): ListCreatePayloadADM = {
    val maybeName: Option[ListName] = createNodeApiRequestADM.name match {
      case Some(value) => Some(ListName.create(value).fold(e => throw e, v => v))
      case None        => None
    }

    val maybePosition: Option[Position] = createNodeApiRequestADM.position match {
      case Some(value) => Some(Position.create(value).fold(e => throw e, v => v))
      case None        => None
    }

    ListCreatePayloadADM(
      id = stringFormatter.validateAndEscapeOptionalIri(
        createNodeApiRequestADM.id,
        throw BadRequestException(s"Invalid custom node IRI")
      ),
      projectIri = ProjectIRI.create(createNodeApiRequestADM.projectIri).fold(e => throw e, v => v),
      name = maybeName,
      labels = Labels.create(createNodeApiRequestADM.labels).fold(e => throw e, v => v),
      comments = Comments.create(createNodeApiRequestADM.comments).fold(e => throw e, v => v)
    )
  }

  /**
   * Convenience method returning the [[ListCreatePayloadADM]] object
   *
   * @param id            the optional custom IRI of the list node.
   * @param parentNodeIri the optional IRI of the parent node.
   * @param projectIri    the IRI of the project.
   * @param name          the optional name of the list node.
   * @param position      the optional position of the node.
   * @param labels        labels of the list node.
   * @param comments      comments of the list node.
   * @return            a [[ListCreatePayloadADM]]
   */
  private def createRootNodeApiRequestADM(
    id: Option[IRI] = None,
    parentNodeIri: Option[IRI] = None,
    projectIri: IRI = "http://rdfh.ch/projects/0001",
    name: Option[String] = None,
    position: Option[Int] = None,
    labels: Seq[StringLiteralV2] = Seq(StringLiteralV2(value = "New label", language = Some("en"))),
    comments: Seq[StringLiteralV2] = Seq(StringLiteralV2(value = "New comment", language = Some("en")))
  ): CreateNodeApiRequestADM = CreateNodeApiRequestADM(id, parentNodeIri, projectIri, name, position, labels, comments)

  "When the ListCreatePayloadADM case class is created it" should {
    "create a valid ListCreatePayloadADM" in {

      val request = createRootNodeApiRequestADM()

      val listCreatePayloadADM = createRootNodeCreatePayloadADM(request)

      listCreatePayloadADM.id should equal(request.id)
      listCreatePayloadADM.projectIri should equal(request.projectIri)
      listCreatePayloadADM.name.map(_.value) should equal(request.name)
      listCreatePayloadADM.labels.value should equal(request.labels)
      listCreatePayloadADM.comments.value should equal(request.comments)

      val otherRequest = createRootNodeApiRequestADM(
        id = Some("http://rdfh.ch/lists/otherlistcustomid"),
        parentNodeIri = None,
        projectIri = "http://rdfh.ch/projects/0002",
        name = Some("Uther Name"),
        position = None,
        labels = Seq(StringLiteralV2(value = "Other label", language = Some("en"))),
        comments = Seq(StringLiteralV2(value = "Other comment", language = Some("en")))
      )

      val otherRootNodeCreatePayloadADM = createRootNodeCreatePayloadADM(otherRequest)

      otherRootNodeCreatePayloadADM.id should equal(otherRequest.id)
      otherRootNodeCreatePayloadADM.projectIri should equal(otherRequest.projectIri)
      otherRootNodeCreatePayloadADM.name.map(_.value) should equal(otherRequest.name)
      otherRootNodeCreatePayloadADM.labels.value should equal(otherRequest.labels)
      otherRootNodeCreatePayloadADM.comments.value should equal(otherRequest.comments)

      otherRootNodeCreatePayloadADM.id should not equal request.id
      otherRootNodeCreatePayloadADM.projectIri should not equal request.projectIri
      otherRootNodeCreatePayloadADM.name.get.value should not equal request.name
      otherRootNodeCreatePayloadADM.labels.value should not equal request.labels
      otherRootNodeCreatePayloadADM.comments.value should not equal request.comments
    }
  }

  /**
   * Convenience method returning the NodeCreatePayloadADM from the [[CreateNodeApiRequestADM]] object
   *
   * @param createNodeApiRequestADM the [[CreateNodeApiRequestADM]] object
   * @return                        a [[NodeCreatePayloadADM]]
   */
  private def createChildNodeCreatePayloadADM(
    createNodeApiRequestADM: CreateNodeApiRequestADM
  ): ChildNodeCreatePayloadADM = {
    val maybeName: Option[ListName] = createNodeApiRequestADM.name match {
      case Some(value) => Some(ListName.create(value).fold(e => throw e, v => v))
      case None        => None
    }

    val maybePosition: Option[Position] = createNodeApiRequestADM.position match {
      case Some(value) => Some(Position.create(value).fold(e => throw e, v => v))
      case None        => None
    }

//    val maybeComments: Option[Comments] = createNodeApiRequestADM.comments match {
//      case Some(value) => Some(Comments.create(value).fold(e => throw e, v => v))
//      case None        => None
//    }

    ChildNodeCreatePayloadADM(
      id = stringFormatter.validateAndEscapeOptionalIri(
        createNodeApiRequestADM.id,
        throw BadRequestException(s"Invalid custom node IRI")
      ),
      parentNodeIri = stringFormatter.validateAndEscapeOptionalIri(
        createNodeApiRequestADM.parentNodeIri,
        throw BadRequestException(s"Invalid parent node IRI")
      ),
      projectIri = ProjectIRI.create(createNodeApiRequestADM.projectIri).fold(e => throw e, v => v),
      name = maybeName,
      position = maybePosition,
      labels = Labels.create(createNodeApiRequestADM.labels).fold(e => throw e, v => v),
      comments = Some(Comments.create(createNodeApiRequestADM.labels).fold(e => throw e, v => v))
    )
  }

  /**
   * Convenience method returning the [[ChildNodeCreatePayloadADM]] object
   *
   * @param id            the optional custom IRI of the list node.
   * @param parentNodeIri the optional IRI of the parent node.
   * @param projectIri    the IRI of the project.
   * @param name          the optional name of the list node.
   * @param position      the optional position of the node.
   * @param labels        labels of the list node.
   * @param comments      comments of the list node.
   * @return            a [[ChildNodeCreatePayloadADM]]
   */
  private def createChildNodeApiRequestADM(
    id: Option[IRI] = None,
    parentNodeIri: Option[IRI] = None,
    projectIri: IRI = "http://rdfh.ch/projects/0001",
    name: Option[String] = None,
    position: Option[Int] = None,
    labels: Seq[StringLiteralV2] = Seq(StringLiteralV2(value = "New label", language = Some("en"))),
    comments: Seq[StringLiteralV2] = Seq(StringLiteralV2(value = "", language = None))
  ): CreateNodeApiRequestADM =
    CreateNodeApiRequestADM(id, parentNodeIri, projectIri, name, position, labels, comments)

  "When the ChildNodeCreatePayloadADM case class is created it" should {
    "create a valid ChildNodeCreatePayloadADM" in {

      val request = createChildNodeApiRequestADM()

      val childNodeCreatePayloadADM = createChildNodeCreatePayloadADM(request)

      childNodeCreatePayloadADM.id should equal(request.id)
      childNodeCreatePayloadADM.parentNodeIri should equal(request.parentNodeIri)
      childNodeCreatePayloadADM.projectIri should equal(request.projectIri)
      childNodeCreatePayloadADM.name.map(_.value) should equal(request.name)
      childNodeCreatePayloadADM.position.map(_.value) should equal(request.position)
      childNodeCreatePayloadADM.labels.value should equal(request.labels)
//      TODO: bring below back after separating ChildNodeCreateApiRequestADM from CreateNodeApiRequestADM
//      childNodeCreatePayloadADM.comments.map(_.value) should equal(request.comments)

      val otherRequest = createChildNodeApiRequestADM(
        id = Some("http://rdfh.ch/lists/otherlistcustomid"),
        parentNodeIri = None,
        projectIri = "http://rdfh.ch/projects/0002",
        name = Some("Uther Name"),
        position = None,
        labels = Seq(StringLiteralV2(value = "Other label", language = Some("en"))),
        comments = Seq(StringLiteralV2(value = "Other comment", language = Some("en")))
      )

      val otherChildNodeCreatePayloadADM = createChildNodeCreatePayloadADM(otherRequest)

      otherChildNodeCreatePayloadADM.id should equal(otherRequest.id)
      otherChildNodeCreatePayloadADM.parentNodeIri should equal(otherRequest.parentNodeIri)
      otherChildNodeCreatePayloadADM.projectIri should equal(otherRequest.projectIri)
      otherChildNodeCreatePayloadADM.name.map(_.value) should equal(otherRequest.name)
      otherChildNodeCreatePayloadADM.position.map(_.value) should equal(otherRequest.position)
      otherChildNodeCreatePayloadADM.labels.value should equal(otherRequest.labels)
//      TODO: bring below back after separating ChildNodeCreateApiRequestADM from CreateNodeApiRequestADM
//      otherChildNodeCreatePayloadADM.comments.map(_.value) should equal(otherRequest.comments)

      otherChildNodeCreatePayloadADM.id should not equal request.id
//      otherChildNodeCreatePayloadADM.parentNodeIri should equal(otherRequest.parentNodeIri)
      otherChildNodeCreatePayloadADM.projectIri should not equal request.projectIri
      otherChildNodeCreatePayloadADM.name.get.value should not equal request.name
//      otherChildNodeCreatePayloadADM.position.map(_.value) should equal(otherRequest.position)
      otherChildNodeCreatePayloadADM.labels.value should not equal request.labels
      otherChildNodeCreatePayloadADM.comments.map(_.value) should not equal request.comments
    }
  }
}
