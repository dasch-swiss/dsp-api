/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.admin

import akka.actor.Status.Failure
import akka.testkit._
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

import java.util.UUID
import scala.concurrent.duration._

import dsp.errors.BadRequestException
import dsp.errors.DuplicateValueException
import dsp.errors.UpdateNotPerformedException
import dsp.valueobjects.Iri._
import dsp.valueobjects.List._
import dsp.valueobjects.V2
import org.knora.webapi._
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.listsmessages.ListNodeCreatePayloadADM.ListChildNodeCreatePayloadADM
import org.knora.webapi.messages.admin.responder.listsmessages.ListNodeCreatePayloadADM.ListRootNodeCreatePayloadADM
import org.knora.webapi.messages.admin.responder.listsmessages._
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.sharedtestdata.SharedListsTestDataADM
import org.knora.webapi.sharedtestdata.SharedTestDataADM
import org.knora.webapi.sharedtestdata.SharedTestDataV1._
import org.knora.webapi.util.MutableTestIri

/**
 * Static data for testing [[ListsResponderADM]].
 */
object ListsResponderADMSpec {
  val config: Config = ConfigFactory.parseString("""
         akka.loglevel = "DEBUG"
         akka.stdout-loglevel = "DEBUG"
        """.stripMargin)
}

/**
 * Tests [[ListsResponderADM]].
 */
class ListsResponderADMSpec extends CoreSpec(ListsResponderADMSpec.config) with ImplicitSender {

  // The default timeout for receiving reply messages from actors.
  implicit private val timeout                          = 5.seconds
  private implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

  override lazy val rdfDataObjects = List(
    RdfDataObject(path = "test_data/demo_data/images-demo-data.ttl", name = "http://www.knora.org/data/00FF/images"),
    RdfDataObject(path = "test_data/all_data/anything-data.ttl", name = "http://www.knora.org/data/0001/anything")
  )

  private val treeListInfo: ListRootNodeInfoADM = SharedListsTestDataADM.treeListInfo

  private val summerNodeInfo: ListNodeInfoADM = SharedListsTestDataADM.summerNodeInfo

  private val otherTreeListInfo: ListRootNodeInfoADM = SharedListsTestDataADM.otherTreeListInfo

  private val treeListChildNodes: Seq[ListNodeADM] = SharedListsTestDataADM.treeListChildNodes

  "The Lists Responder" when {
    "used to query information about lists" should {
      "return all lists" in {
        appActor ! ListsGetRequestADM(
          requestingUser = SharedTestDataADM.imagesUser01
        )

        val received: ListsGetResponseADM = expectMsgType[ListsGetResponseADM](timeout)

        received.lists.size should be(9)
      }

      "return all lists belonging to the images project" in {
        appActor ! ListsGetRequestADM(
          projectIri = Some(IMAGES_PROJECT_IRI),
          requestingUser = SharedTestDataADM.imagesUser01
        )

        val received: ListsGetResponseADM = expectMsgType[ListsGetResponseADM](timeout)

        // log.debug("received: " + received)

        received.lists.size should be(4)
      }

      "return all lists belonging to the anything project" in {
        appActor ! ListsGetRequestADM(
          projectIri = Some(ANYTHING_PROJECT_IRI),
          requestingUser = SharedTestDataADM.imagesUser01
        )

        val received: ListsGetResponseADM = expectMsgType[ListsGetResponseADM](timeout)

        // log.debug("received: " + received)

        received.lists.size should be(4)
      }

      "return basic list information (anything list)" in {
        appActor ! ListNodeInfoGetRequestADM(
          iri = "http://rdfh.ch/lists/0001/treeList",
          requestingUser = SharedTestDataADM.anythingUser1
        )

        val received: RootNodeInfoGetResponseADM = expectMsgType[RootNodeInfoGetResponseADM](timeout)

        // log.debug("returned basic keyword list information: {}", MessageUtil.toSource(received.items.head))

        received.listinfo.sorted should be(treeListInfo.sorted)
      }

      "return basic list information (anything other list)" in {
        appActor ! ListNodeInfoGetRequestADM(
          iri = "http://rdfh.ch/lists/0001/otherTreeList",
          requestingUser = SharedTestDataADM.anythingUser1
        )

        val received: RootNodeInfoGetResponseADM = expectMsgType[RootNodeInfoGetResponseADM](timeout)

        // log.debug("returned basic keyword list information: {}", MessageUtil.toSource(received.items.head))

        received.listinfo.sorted should be(otherTreeListInfo.sorted)
      }

      "return basic node information (images list - sommer)" in {
        appActor ! ListNodeInfoGetRequestADM(
          iri = "http://rdfh.ch/lists/00FF/526f26ed04",
          requestingUser = SharedTestDataADM.imagesUser01
        )

        val received: ChildNodeInfoGetResponseADM = expectMsgType[ChildNodeInfoGetResponseADM](timeout)

        // log.debug("returned basic keyword list information: {}", MessageUtil.toSource(received.items.head))

        received.nodeinfo.sorted should be(summerNodeInfo.sorted)
      }

      "return a full list response" in {
        appActor ! ListGetRequestADM(
          iri = "http://rdfh.ch/lists/0001/treeList",
          requestingUser = SharedTestDataADM.anythingUser1
        )

        val received: ListGetResponseADM = expectMsgType[ListGetResponseADM](timeout)

        // log.debug("returned whole keyword list: {}", MessageUtil.toSource(received.items.head))

        received.list.listinfo.sorted should be(treeListInfo.sorted)

        received.list.children.map(_.sorted) should be(treeListChildNodes.map(_.sorted))
      }
    }
    val newListIri     = new MutableTestIri
    val firstChildIri  = new MutableTestIri
    val secondChildIri = new MutableTestIri
    val thirdChildIri  = new MutableTestIri

    "used to modify lists" should {
      "create a list" in {
        appActor ! ListRootNodeCreateRequestADM(
          createRootNode = ListRootNodeCreatePayloadADM(
            projectIri = ProjectIri.make(IMAGES_PROJECT_IRI).fold(e => throw e.head, v => v),
            name = Some(ListName.make("neuelistename").fold(e => throw e.head, v => v)),
            labels = Labels
              .make(Seq(V2.StringLiteralV2(value = "Neue Liste", language = Some("de"))))
              .fold(e => throw e.head, v => v),
            comments = Comments
              .make(Seq(V2.StringLiteralV2(value = "Neuer Kommentar", language = Some("de"))))
              .fold(e => throw e.head, v => v)
          ),
          requestingUser = SharedTestDataADM.imagesUser01,
          apiRequestID = UUID.randomUUID
        )

        val received: ListGetResponseADM = expectMsgType[ListGetResponseADM](timeout)

        val listInfo = received.list.listinfo
        listInfo.projectIri should be(IMAGES_PROJECT_IRI)

        listInfo.name should be(Some("neuelistename"))

        val labels: Seq[StringLiteralV2] = listInfo.labels.stringLiterals
        labels.size should be(1)
        labels.head should be(StringLiteralV2(value = "Neue Liste", language = Some("de")))

        val comments: Seq[StringLiteralV2] = listInfo.comments.stringLiterals
        comments.isEmpty should be(false)

        val children = received.list.children
        children.size should be(0)

        // store list IRI for next test
        newListIri.set(listInfo.id)
      }

      "create a list with special characters in its labels" in {
        val labelWithSpecialCharacter   = "Neue \\\"Liste\\\""
        val commentWithSpecialCharacter = "Neue \\\"Kommentar\\\""
        val nameWithSpecialCharacter    = "a new \\\"name\\\""
        appActor ! ListRootNodeCreateRequestADM(
          createRootNode = ListRootNodeCreatePayloadADM(
            projectIri = ProjectIri.make(IMAGES_PROJECT_IRI).fold(e => throw e.head, v => v),
            name = Some(ListName.make(nameWithSpecialCharacter).fold(e => throw e.head, v => v)),
            labels = Labels
              .make(Seq(V2.StringLiteralV2(value = labelWithSpecialCharacter, language = Some("de"))))
              .fold(e => throw e.head, v => v),
            comments = Comments
              .make(Seq(V2.StringLiteralV2(value = commentWithSpecialCharacter, language = Some("de"))))
              .fold(e => throw e.head, v => v)
          ),
          requestingUser = SharedTestDataADM.imagesUser01,
          apiRequestID = UUID.randomUUID
        )

        val received: ListGetResponseADM = expectMsgType[ListGetResponseADM](timeout)

        val listInfo = received.list.listinfo
        listInfo.projectIri should be(IMAGES_PROJECT_IRI)

        listInfo.name should be(Some(stringFormatter.fromSparqlEncodedString(nameWithSpecialCharacter)))

        val labels: Seq[StringLiteralV2] = listInfo.labels.stringLiterals
        labels.size should be(1)
        val givenLabel = labels.head
        givenLabel.value shouldEqual stringFormatter.fromSparqlEncodedString(labelWithSpecialCharacter)
        givenLabel.language shouldEqual Some("de")

        val comments     = received.list.listinfo.comments.stringLiterals
        val givenComment = comments.head
        givenComment.language shouldEqual Some("de")
        givenComment.value shouldEqual stringFormatter.fromSparqlEncodedString(commentWithSpecialCharacter)

        val children = received.list.children
        children.size should be(0)
      }

      "update basic list information" in {
        val changeNodeInfoRequest = NodeInfoChangeRequestADM(
          listIri = newListIri.get,
          changeNodeRequest = ListNodeChangePayloadADM(
            listIri = ListIri.make(newListIri.get).fold(e => throw e.head, v => v),
            projectIri = ProjectIri.make(IMAGES_PROJECT_IRI).fold(e => throw e.head, v => v),
            name = Some(ListName.make("updated name").fold(e => throw e.head, v => v)),
            labels = Some(
              Labels
                .make(
                  Seq(
                    V2.StringLiteralV2(value = "Neue geänderte Liste", language = Some("de")),
                    V2.StringLiteralV2(value = "Changed List", language = Some("en"))
                  )
                )
                .fold(e => throw e.head, v => v)
            ),
            comments = Some(
              Comments
                .make(
                  Seq(
                    V2.StringLiteralV2(value = "Neuer Kommentar", language = Some("de")),
                    V2.StringLiteralV2(value = "New Comment", language = Some("en"))
                  )
                )
                .fold(e => throw e.head, v => v)
            )
          ),
          requestingUser = SharedTestDataADM.imagesUser01,
          apiRequestID = UUID.randomUUID
        )
        appActor ! changeNodeInfoRequest

        val received: RootNodeInfoGetResponseADM = expectMsgType[RootNodeInfoGetResponseADM](timeout)

        val listInfo = received.listinfo
        listInfo.projectIri should be(IMAGES_PROJECT_IRI)
        listInfo.name should be(Some("updated name"))
        val labels: Seq[StringLiteralV2] = listInfo.labels.stringLiterals
        labels.size should be(2)
        labels.sorted should be(
          Seq(
            StringLiteralV2(value = "Neue geänderte Liste", language = Some("de")),
            StringLiteralV2(value = "Changed List", language = Some("en"))
          ).sorted
        )

        val comments = listInfo.comments.stringLiterals
        comments.size should be(2)
        comments.sorted should be(
          Seq(
            StringLiteralV2(value = "Neuer Kommentar", language = Some("de")),
            StringLiteralV2(value = "New Comment", language = Some("en"))
          ).sorted
        )
      }

      "not update basic list information if name is duplicate" in {
        val name       = Some(ListName.make("sommer").fold(e => throw e.head, v => v))
        val projectIRI = ProjectIri.make(IMAGES_PROJECT_IRI).fold(e => throw e.head, v => v)
        appActor ! NodeInfoChangeRequestADM(
          listIri = newListIri.get,
          changeNodeRequest = ListNodeChangePayloadADM(
            listIri = ListIri.make(newListIri.get).fold(e => throw e.head, v => v),
            projectIri = projectIRI,
            name = name
          ),
          requestingUser = SharedTestDataADM.imagesUser01,
          apiRequestID = UUID.randomUUID
        )
        expectMsg(
          Failure(
            DuplicateValueException(
              s"The name ${name.value} is already used by a list inside the project ${projectIRI.value}."
            )
          )
        )
      }

      "add child to list - to the root node" in {
        appActor ! ListChildNodeCreateRequestADM(
          createChildNodeRequest = ListChildNodeCreatePayloadADM(
            parentNodeIri = ListIri.make(newListIri.get).fold(e => throw e.head, v => v),
            projectIri = ProjectIri.make(IMAGES_PROJECT_IRI).fold(e => throw e.head, v => v),
            name = Some(ListName.make("first").fold(e => throw e.head, v => v)),
            labels = Labels
              .make(Seq(V2.StringLiteralV2(value = "New First Child List Node Value", language = Some("en"))))
              .fold(e => throw e.head, v => v),
            comments = Some(
              Comments
                .make(Seq(V2.StringLiteralV2(value = "New First Child List Node Comment", language = Some("en"))))
                .fold(e => throw e.head, v => v)
            )
          ),
          requestingUser = SharedTestDataADM.imagesUser01,
          apiRequestID = UUID.randomUUID
        )

        val received: ChildNodeInfoGetResponseADM = expectMsgType[ChildNodeInfoGetResponseADM](timeout)
        val nodeInfo                              = received.nodeinfo

        // check correct node info
        val childNodeInfo = nodeInfo match {
          case info: ListChildNodeInfoADM => info
          case something                  => fail(s"expecting ListChildNodeInfoADM but got ${something.getClass.toString} instead.")
        }

        // check labels
        val labels: Seq[StringLiteralV2] = childNodeInfo.labels.stringLiterals
        labels.size should be(1)
        labels.sorted should be(
          Seq(StringLiteralV2(value = "New First Child List Node Value", language = Some("en")))
        )

        // check comments
        val comments = childNodeInfo.comments.stringLiterals
        comments.size should be(1)
        comments.sorted should be(
          Seq(StringLiteralV2(value = "New First Child List Node Comment", language = Some("en")))
        )

        // check position
        val position = childNodeInfo.position
        position should be(0)

        // check has root node
        val rootNode = childNodeInfo.hasRootNode
        rootNode should be(newListIri.get)

        firstChildIri.set(childNodeInfo.id)
      }

      "add second child to list in first position - to the root node" in {
        appActor ! ListChildNodeCreateRequestADM(
          createChildNodeRequest = ListChildNodeCreatePayloadADM(
            parentNodeIri = ListIri.make(newListIri.get).fold(e => throw e.head, v => v),
            projectIri = ProjectIri.make(IMAGES_PROJECT_IRI).fold(e => throw e.head, v => v),
            name = Some(ListName.make("second").fold(e => throw e.head, v => v)),
            position = Some(Position.make(0).fold(e => throw e.head, v => v)),
            labels = Labels
              .make(Seq(V2.StringLiteralV2(value = "New Second Child List Node Value", language = Some("en"))))
              .fold(e => throw e.head, v => v),
            comments = Some(
              Comments
                .make(Seq(V2.StringLiteralV2(value = "New Second Child List Node Comment", language = Some("en"))))
                .fold(e => throw e.head, v => v)
            )
          ),
          requestingUser = SharedTestDataADM.imagesUser01,
          apiRequestID = UUID.randomUUID
        )

        val received: ChildNodeInfoGetResponseADM = expectMsgType[ChildNodeInfoGetResponseADM](timeout)
        val nodeInfo                              = received.nodeinfo

        // check correct node info
        val childNodeInfo = nodeInfo match {
          case info: ListChildNodeInfoADM => info
          case something                  => fail(s"expecting ListChildNodeInfoADM but got ${something.getClass.toString} instead.")
        }

        // check labels
        val labels: Seq[StringLiteralV2] = childNodeInfo.labels.stringLiterals
        labels.size should be(1)
        labels.sorted should be(
          Seq(StringLiteralV2(value = "New Second Child List Node Value", language = Some("en")))
        )

        // check comments
        val comments = childNodeInfo.comments.stringLiterals
        comments.size should be(1)
        comments.sorted should be(
          Seq(StringLiteralV2(value = "New Second Child List Node Comment", language = Some("en")))
        )

        // check position
        val position = childNodeInfo.position
        position should be(0)

        // check has root node
        val rootNode = childNodeInfo.hasRootNode
        rootNode should be(newListIri.get)

        secondChildIri.set(childNodeInfo.id)
      }

      "add child to second child node" in {
        appActor ! ListChildNodeCreateRequestADM(
          createChildNodeRequest = ListChildNodeCreatePayloadADM(
            parentNodeIri = ListIri.make(secondChildIri.get).fold(e => throw e.head, v => v),
            projectIri = ProjectIri.make(IMAGES_PROJECT_IRI).fold(e => throw e.head, v => v),
            name = Some(ListName.make("third").fold(e => throw e.head, v => v)),
            labels = Labels
              .make(Seq(V2.StringLiteralV2(value = "New Third Child List Node Value", language = Some("en"))))
              .fold(e => throw e.head, v => v),
            comments = Some(
              Comments
                .make(Seq(V2.StringLiteralV2(value = "New Third Child List Node Comment", language = Some("en"))))
                .fold(e => throw e.head, v => v)
            )
          ),
          requestingUser = SharedTestDataADM.imagesUser01,
          apiRequestID = UUID.randomUUID
        )

        val received: ChildNodeInfoGetResponseADM = expectMsgType[ChildNodeInfoGetResponseADM](timeout)
        val nodeInfo                              = received.nodeinfo

        // check correct node info
        val childNodeInfo = nodeInfo match {
          case info: ListChildNodeInfoADM => info
          case something                  => fail(s"expecting ListChildNodeInfoADM but got ${something.getClass.toString} instead.")
        }

        // check labels
        val labels: Seq[StringLiteralV2] = childNodeInfo.labels.stringLiterals
        labels.size should be(1)
        labels.sorted should be(
          Seq(StringLiteralV2(value = "New Third Child List Node Value", language = Some("en")))
        )

        // check comments
        val comments = childNodeInfo.comments.stringLiterals
        comments.size should be(1)
        comments.sorted should be(
          Seq(StringLiteralV2(value = "New Third Child List Node Comment", language = Some("en")))
        )

        // check position
        val position = childNodeInfo.position
        position should be(0)

        // check has root node
        val rootNode = childNodeInfo.hasRootNode
        rootNode should be(newListIri.get)

        thirdChildIri.set(childNodeInfo.id)
      }

      "not create a node if given new position is out of range" in {
        val givenPosition = Some(Position.make(20).fold(e => throw e.head, v => v))
        appActor ! ListChildNodeCreateRequestADM(
          createChildNodeRequest = ListChildNodeCreatePayloadADM(
            parentNodeIri = ListIri.make(newListIri.get).fold(e => throw e.head, v => v),
            projectIri = ProjectIri.make(IMAGES_PROJECT_IRI).fold(e => throw e.head, v => v),
            name = Some(ListName.make("fourth").fold(e => throw e.head, v => v)),
            position = givenPosition,
            labels = Labels
              .make(Seq(V2.StringLiteralV2(value = "New Fourth Child List Node Value", language = Some("en"))))
              .fold(e => throw e.head, v => v),
            comments = Some(
              Comments
                .make(Seq(V2.StringLiteralV2(value = "New Fourth Child List Node Comment", language = Some("en"))))
                .fold(e => throw e.head, v => v)
            )
          ),
          requestingUser = SharedTestDataADM.imagesUser01,
          apiRequestID = UUID.randomUUID
        )
        expectMsg(
          Failure(
            BadRequestException(
              s"Invalid position given ${givenPosition.map(_.value)}, maximum allowed position is = 2."
            )
          )
        )
      }
    }

    "used to reposition nodes" should {
      "not reposition a node if new position is the same as old one" in {
        val nodeIri = "http://rdfh.ch/lists/0001/notUsedList014"
        appActor ! NodePositionChangeRequestADM(
          nodeIri = nodeIri,
          changeNodePositionRequest = ChangeNodePositionApiRequestADM(
            position = 3,
            parentIri = "http://rdfh.ch/lists/0001/notUsedList01"
          ),
          requestingUser = SharedTestDataADM.anythingAdminUser,
          apiRequestID = UUID.randomUUID
        )
        expectMsg(Failure(UpdateNotPerformedException(s"The given position is the same as node's current position.")))
      }

      "not reposition a node if new position is out of range" in {
        val nodeIri = "http://rdfh.ch/lists/0001/notUsedList014"
        appActor ! NodePositionChangeRequestADM(
          nodeIri = nodeIri,
          changeNodePositionRequest = ChangeNodePositionApiRequestADM(
            position = 30,
            parentIri = "http://rdfh.ch/lists/0001/notUsedList01"
          ),
          requestingUser = SharedTestDataADM.anythingAdminUser,
          apiRequestID = UUID.randomUUID
        )
        expectMsg(Failure(BadRequestException(s"Invalid position given, maximum allowed position is = 4.")))
      }

      "not reposition a node to another parent node if new position is out of range" in {
        val nodeIri = "http://rdfh.ch/lists/0001/notUsedList014"
        appActor ! NodePositionChangeRequestADM(
          nodeIri = nodeIri,
          changeNodePositionRequest = ChangeNodePositionApiRequestADM(
            position = 30,
            parentIri = "http://rdfh.ch/lists/0001/notUsedList"
          ),
          requestingUser = SharedTestDataADM.anythingAdminUser,
          apiRequestID = UUID.randomUUID
        )
        expectMsg(Failure(BadRequestException(s"Invalid position given, maximum allowed position is = 3.")))
      }

      "reposition node List014 from position 3 to 1 (shift to right)" in {
        val nodeIri   = "http://rdfh.ch/lists/0001/notUsedList014"
        val parentIri = "http://rdfh.ch/lists/0001/notUsedList01"
        appActor ! NodePositionChangeRequestADM(
          nodeIri = nodeIri,
          changeNodePositionRequest = ChangeNodePositionApiRequestADM(
            position = 1,
            parentIri = parentIri
          ),
          requestingUser = SharedTestDataADM.anythingAdminUser,
          apiRequestID = UUID.randomUUID
        )

        val received: NodePositionChangeResponseADM = expectMsgType[NodePositionChangeResponseADM](timeout)
        val parentNode                              = received.node
        parentNode.getNodeId should be(parentIri)

        val children      = parentNode.getChildren
        val isNodeUpdated = children.exists(child => child.id == nodeIri && child.position == 1)
        isNodeUpdated should be(true)

        // node in position 4 must not have changed
        val staticNode = children.last
        staticNode.id should be("http://rdfh.ch/lists/0001/notUsedList015")
        staticNode.position should be(4)

        // node in position 1 must have been shifted to position 2
        val isShifted =
          children.exists(child => child.id == "http://rdfh.ch/lists/0001/notUsedList012" && child.position == 2)
        isShifted should be(true)
      }

      "reposition node List011 from position 0 to end (shift to left)" in {
        val nodeIri   = "http://rdfh.ch/lists/0001/notUsedList011"
        val parentIri = "http://rdfh.ch/lists/0001/notUsedList01"
        appActor ! NodePositionChangeRequestADM(
          nodeIri = nodeIri,
          changeNodePositionRequest = ChangeNodePositionApiRequestADM(
            position = -1,
            parentIri = parentIri
          ),
          requestingUser = SharedTestDataADM.anythingAdminUser,
          apiRequestID = UUID.randomUUID
        )
        val received: NodePositionChangeResponseADM = expectMsgType[NodePositionChangeResponseADM](timeout)
        val parentNode                              = received.node

        /* check parent node */
        parentNode.getNodeId should be(parentIri)
        val children      = parentNode.getChildren
        val isNodeUpdated = children.exists(child => child.id == nodeIri && child.position == 4)
        isNodeUpdated should be(true)

        // node that was in position 1 must be in 0 now
        val firstNode = children.head
        firstNode.id should be("http://rdfh.ch/lists/0001/notUsedList014")
        firstNode.position should be(0)

        // last node must be one before last now
        val isShifted =
          children.exists(child => child.id == "http://rdfh.ch/lists/0001/notUsedList015" && child.position == 3)
        isShifted should be(true)
      }

      "reposition node List013 in position 2 of another parent" in {
        val nodeIri      = "http://rdfh.ch/lists/0001/notUsedList013"
        val newParentIri = "http://rdfh.ch/lists/0001/notUsedList"
        val oldParentIri = "http://rdfh.ch/lists/0001/notUsedList01"
        appActor ! NodePositionChangeRequestADM(
          nodeIri = nodeIri,
          changeNodePositionRequest = ChangeNodePositionApiRequestADM(
            position = 2,
            parentIri = newParentIri
          ),
          requestingUser = SharedTestDataADM.anythingAdminUser,
          apiRequestID = UUID.randomUUID
        )
        val received: NodePositionChangeResponseADM = expectMsgType[NodePositionChangeResponseADM](timeout)
        val parentNode                              = received.node
        parentNode.getNodeId should be(newParentIri)

        /* check children of new parent node */
        val childrenOfNewParent = parentNode.getChildren

        // node must be in children of new parent
        childrenOfNewParent.size should be(4)
        val isNodeAdd = childrenOfNewParent.exists(child => child.id == nodeIri && child.position == 2)
        isNodeAdd should be(true)

        // last node of new parent must be shifted one place to right
        val isShifted = childrenOfNewParent.exists(child =>
          child.id == "http://rdfh.ch/lists/0001/notUsedList03" && child.position == 3
        )
        isShifted should be(true)

        /* check old parent node */
        appActor ! ListGetRequestADM(
          iri = oldParentIri,
          requestingUser = SharedTestDataADM.anythingAdminUser
        )
        val receivedNode: ListNodeGetResponseADM = expectMsgType[ListNodeGetResponseADM](timeout)
        // node must not be in children of old parent
        val oldParentChildren = receivedNode.node.children
        oldParentChildren.size should be(4)
        val isNodeUpdated = oldParentChildren.exists(child => child.id == nodeIri)
        isNodeUpdated should be(false)

        // nodes of old siblings must be shifted to the left.
        val lastNode = oldParentChildren.last
        lastNode.id should be("http://rdfh.ch/lists/0001/notUsedList011")
        lastNode.position should be(3)
      }

      "reposition node List015 to the end of another parent's children" in {
        val nodeIri      = "http://rdfh.ch/lists/0001/notUsedList015"
        val newParentIri = "http://rdfh.ch/lists/0001/notUsedList"
        val oldParentIri = "http://rdfh.ch/lists/0001/notUsedList01"
        appActor ! NodePositionChangeRequestADM(
          nodeIri = nodeIri,
          changeNodePositionRequest = ChangeNodePositionApiRequestADM(
            position = -1,
            parentIri = newParentIri
          ),
          requestingUser = SharedTestDataADM.anythingAdminUser,
          apiRequestID = UUID.randomUUID
        )
        val received: NodePositionChangeResponseADM = expectMsgType[NodePositionChangeResponseADM](timeout)
        val parentNode                              = received.node
        parentNode.getNodeId should be(newParentIri)

        /* check children of new parent node */
        val childrenOfNewParent = parentNode.getChildren

        // node must be in children of new parent
        childrenOfNewParent.size should be(5)
        val isNodeAdd = childrenOfNewParent.exists(child => child.id == nodeIri && child.position == 4)
        isNodeAdd should be(true)

        // last node of new parent must have remained in its current position
        val isShifted = childrenOfNewParent.exists(child =>
          child.id == "http://rdfh.ch/lists/0001/notUsedList03" && child.position == 3
        )
        isShifted should be(true)

        /* check old parent node */
        appActor ! ListGetRequestADM(
          iri = oldParentIri,
          requestingUser = SharedTestDataADM.anythingAdminUser
        )
        val receivedNode: ListNodeGetResponseADM = expectMsgType[ListNodeGetResponseADM](timeout)
        // node must not be in children of old parent
        val oldParentChildren = receivedNode.node.children
        oldParentChildren.size should be(3)
        val isNodeUpdated = oldParentChildren.exists(child => child.id == nodeIri)
        isNodeUpdated should be(false)
      }

      "put List015 back in end of its original parent node" in {
        val nodeIri      = "http://rdfh.ch/lists/0001/notUsedList015"
        val newParentIri = "http://rdfh.ch/lists/0001/notUsedList01"
        appActor ! NodePositionChangeRequestADM(
          nodeIri = nodeIri,
          changeNodePositionRequest = ChangeNodePositionApiRequestADM(
            position = -1,
            parentIri = newParentIri
          ),
          requestingUser = SharedTestDataADM.anythingAdminUser,
          apiRequestID = UUID.randomUUID
        )
        val received: NodePositionChangeResponseADM = expectMsgType[NodePositionChangeResponseADM](timeout)
        val parentNode                              = received.node
        parentNode.getNodeId should be(newParentIri)

        /* check children of new parent node */
        val childrenOfNewParent = parentNode.getChildren
        childrenOfNewParent.size should be(4)
        val isNodeUpdated = childrenOfNewParent.exists(child => child.id == nodeIri && child.position == 3)
        isNodeUpdated should be(true)

      }

      "put List013 back in position 2 of its original parent node" in {
        val nodeIri      = "http://rdfh.ch/lists/0001/notUsedList013"
        val newParentIri = "http://rdfh.ch/lists/0001/notUsedList01"
        appActor ! NodePositionChangeRequestADM(
          nodeIri = nodeIri,
          changeNodePositionRequest = ChangeNodePositionApiRequestADM(
            position = 2,
            parentIri = newParentIri
          ),
          requestingUser = SharedTestDataADM.anythingAdminUser,
          apiRequestID = UUID.randomUUID
        )
        val received: NodePositionChangeResponseADM = expectMsgType[NodePositionChangeResponseADM](timeout)
        val parentNode                              = received.node
        parentNode.getNodeId should be(newParentIri)

        /* check children of new parent node */
        val childrenOfNewParent = parentNode.getChildren
        childrenOfNewParent.size should be(5)
        val isNodeUpdated = childrenOfNewParent.exists(child => child.id == nodeIri && child.position == 2)
        isNodeUpdated should be(true)

      }

      "put List011 back in its original place" in {
        val nodeIri   = "http://rdfh.ch/lists/0001/notUsedList011"
        val parentIri = "http://rdfh.ch/lists/0001/notUsedList01"
        appActor ! NodePositionChangeRequestADM(
          nodeIri = nodeIri,
          changeNodePositionRequest = ChangeNodePositionApiRequestADM(
            position = 0,
            parentIri = parentIri
          ),
          requestingUser = SharedTestDataADM.anythingAdminUser,
          apiRequestID = UUID.randomUUID
        )
        val received: NodePositionChangeResponseADM = expectMsgType[NodePositionChangeResponseADM](timeout)
        val parentNode                              = received.node
        parentNode.getNodeId should be(parentIri)
        val isNodeUpdated = parentNode.getChildren.exists(child => child.id == nodeIri && child.position == 0)
        isNodeUpdated should be(true)
      }

      "put List014 back in its original position" in {
        val nodeIri   = "http://rdfh.ch/lists/0001/notUsedList014"
        val parentIri = "http://rdfh.ch/lists/0001/notUsedList01"
        appActor ! NodePositionChangeRequestADM(
          nodeIri = nodeIri,
          changeNodePositionRequest = ChangeNodePositionApiRequestADM(
            position = 3,
            parentIri = parentIri
          ),
          requestingUser = SharedTestDataADM.anythingAdminUser,
          apiRequestID = UUID.randomUUID
        )
        val received: NodePositionChangeResponseADM = expectMsgType[NodePositionChangeResponseADM](timeout)
        val parentNode                              = received.node
        parentNode.getNodeId should be(parentIri)
        val isNodeUpdated = parentNode.getChildren.exists(child => child.id == nodeIri && child.position == 3)
        isNodeUpdated should be(true)
      }

      "reposition node in a position equal to length of new parents children" in {
        val nodeIri   = "http://rdfh.ch/lists/0001/notUsedList03"
        val parentIri = "http://rdfh.ch/lists/0001/notUsedList01"
        appActor ! NodePositionChangeRequestADM(
          nodeIri = nodeIri,
          changeNodePositionRequest = ChangeNodePositionApiRequestADM(
            position = 5,
            parentIri = parentIri
          ),
          requestingUser = SharedTestDataADM.anythingAdminUser,
          apiRequestID = UUID.randomUUID
        )
        val received: NodePositionChangeResponseADM = expectMsgType[NodePositionChangeResponseADM](timeout)
        val parentNode                              = received.node
        parentNode.getNodeId should be(parentIri)
        val isNodeUpdated = parentNode.getChildren.exists(child => child.id == nodeIri && child.position == 5)
        isNodeUpdated should be(true)
      }

      "reposition List014 in position 0 of its sibling which does not have a child" in {
        val nodeIri   = "http://rdfh.ch/lists/0001/notUsedList014"
        val parentIri = "http://rdfh.ch/lists/0001/notUsedList015"
        appActor ! NodePositionChangeRequestADM(
          nodeIri = nodeIri,
          changeNodePositionRequest = ChangeNodePositionApiRequestADM(
            position = 0,
            parentIri = parentIri
          ),
          requestingUser = SharedTestDataADM.anythingAdminUser,
          apiRequestID = UUID.randomUUID
        )
        val received: NodePositionChangeResponseADM = expectMsgType[NodePositionChangeResponseADM](timeout)
        val parentNode                              = received.node
        parentNode.getNodeId should be(parentIri)
        val isNodeUpdated = parentNode.getChildren.exists(child => child.id == nodeIri && child.position == 0)
        isNodeUpdated should be(true)
      }
    }

    "used to delete list items" should {
      "not delete a node that is in use" in {
        val nodeInUseIri = "http://rdfh.ch/lists/0001/treeList01"
        appActor ! ListItemDeleteRequestADM(
          nodeIri = nodeInUseIri,
          requestingUser = SharedTestDataADM.anythingAdminUser,
          apiRequestID = UUID.randomUUID
        )
        expectMsg(Failure(BadRequestException(s"Node ${nodeInUseIri} cannot be deleted, because it is in use.")))

      }

      "not delete a node that has a child which is used (node itself not in use, but its child is)" in {
        val nodeIri = "http://rdfh.ch/lists/0001/treeList03"
        appActor ! ListItemDeleteRequestADM(
          nodeIri = nodeIri,
          requestingUser = SharedTestDataADM.anythingAdminUser,
          apiRequestID = UUID.randomUUID
        )
        val usedChild = "http://rdfh.ch/lists/0001/treeList10"
        expectMsg(
          Failure(BadRequestException(s"Node ${nodeIri} cannot be deleted, because its child ${usedChild} is in use."))
        )

      }

      "not delete a node used as object of salsah-gui:guiAttribute (i.e. 'hlist=<nodeIri>') but not as object of knora-base:valueHasListNode" in {
        val nodeInUseInOntologyIri = "http://rdfh.ch/lists/0001/treeList"
        appActor ! ListItemDeleteRequestADM(
          nodeIri = nodeInUseInOntologyIri,
          requestingUser = SharedTestDataADM.anythingAdminUser,
          apiRequestID = UUID.randomUUID
        )
        expectMsg(
          Failure(BadRequestException(s"Node ${nodeInUseInOntologyIri} cannot be deleted, because it is in use."))
        )

      }

      "delete a middle child node that is not in use" in {
        val nodeIri = "http://rdfh.ch/lists/0001/notUsedList012"
        appActor ! ListItemDeleteRequestADM(
          nodeIri = nodeIri,
          requestingUser = SharedTestDataADM.anythingAdminUser,
          apiRequestID = UUID.randomUUID
        )
        val received: ChildNodeDeleteResponseADM = expectMsgType[ChildNodeDeleteResponseADM](timeout)
        val parentNode                           = received.node
        val remainingChildren                    = parentNode.getChildren
        remainingChildren.size should be(4)
        // Tailing children should be shifted to left
        remainingChildren.last.position should be(3)

        // node List015 should still have its child
        val list015 = remainingChildren.filter(node => node.id == "http://rdfh.ch/lists/0001/notUsedList015").head
        list015.position should be(2)
        list015.children.size should be(1)
      }

      "delete a child node that is not in use" in {
        val nodeIri = "http://rdfh.ch/lists/0001/notUsedList02"
        appActor ! ListItemDeleteRequestADM(
          nodeIri = nodeIri,
          requestingUser = SharedTestDataADM.anythingAdminUser,
          apiRequestID = UUID.randomUUID
        )
        val received: ChildNodeDeleteResponseADM = expectMsgType[ChildNodeDeleteResponseADM](timeout)
        val parentNode                           = received.node
        val remainingChildren                    = parentNode.getChildren
        remainingChildren.size should be(1)
        val firstChild = remainingChildren.head
        firstChild.id should be("http://rdfh.ch/lists/0001/notUsedList01")
        firstChild.position should be(0)
      }

      "delete a list (i.e. root node) that is not in use in ontology" in {
        val listIri = "http://rdfh.ch/lists/0001/notUsedList"
        appActor ! ListItemDeleteRequestADM(
          nodeIri = listIri,
          requestingUser = SharedTestDataADM.anythingAdminUser,
          apiRequestID = UUID.randomUUID
        )
        val received: ListDeleteResponseADM = expectMsgType[ListDeleteResponseADM](timeout)
        received.iri should be(listIri)
        received.deleted should be(true)
      }
    }

    "used to query if list can be deleted" should {
      "return FALSE for a node that is in use" in {
        val nodeInUseIri = "http://rdfh.ch/lists/0001/treeList01"
        appActor ! CanDeleteListRequestADM(
          iri = nodeInUseIri,
          requestingUser = SharedTestDataADM.anythingAdminUser
        )
        val response: CanDeleteListResponseADM = expectMsgType[CanDeleteListResponseADM](timeout)
        response.listIri should be(nodeInUseIri)
        response.canDeleteList should be(false)
      }

      "return FALSE for a node that is unused but has a child which is used" in {
        val nodeIri = "http://rdfh.ch/lists/0001/treeList03"
        appActor ! CanDeleteListRequestADM(
          iri = nodeIri,
          requestingUser = SharedTestDataADM.anythingAdminUser
        )
        val response: CanDeleteListResponseADM = expectMsgType[CanDeleteListResponseADM](timeout)
        response.listIri should be(nodeIri)
        response.canDeleteList should be(false)
      }

      "return FALSE for a node used as object of salsah-gui:guiAttribute (i.e. 'hlist=<nodeIri>') but not as object of knora-base:valueHasListNode" in {
        val nodeInUseInOntologyIri = "http://rdfh.ch/lists/0001/treeList"
        appActor ! CanDeleteListRequestADM(
          iri = nodeInUseInOntologyIri,
          requestingUser = SharedTestDataADM.anythingAdminUser
        )
        val response: CanDeleteListResponseADM = expectMsgType[CanDeleteListResponseADM](timeout)
        response.listIri should be(nodeInUseInOntologyIri)
        response.canDeleteList should be(false)
      }

      "return TRUE for a middle child node that is not in use" in {
        val nodeIri = "http://rdfh.ch/lists/0001/notUsedList012"
        appActor ! CanDeleteListRequestADM(
          iri = nodeIri,
          requestingUser = SharedTestDataADM.anythingAdminUser
        )
        val response: CanDeleteListResponseADM = expectMsgType[CanDeleteListResponseADM](timeout)
        response.listIri should be(nodeIri)
        response.canDeleteList should be(true)
      }

      "retrun TRUE for a child node that is not in use" in {
        val nodeIri = "http://rdfh.ch/lists/0001/notUsedList02"
        appActor ! CanDeleteListRequestADM(
          iri = nodeIri,
          requestingUser = SharedTestDataADM.anythingAdminUser
        )
        val response: CanDeleteListResponseADM = expectMsgType[CanDeleteListResponseADM](timeout)
        response.listIri should be(nodeIri)
        response.canDeleteList should be(true)
      }

      "delete a list (i.e. root node) that is not in use in ontology" in {
        val listIri = "http://rdfh.ch/lists/0001/notUsedList"
        appActor ! CanDeleteListRequestADM(
          iri = listIri,
          requestingUser = SharedTestDataADM.anythingAdminUser
        )
        val response: CanDeleteListResponseADM = expectMsgType[CanDeleteListResponseADM](timeout)
        response.listIri should be(listIri)
        response.canDeleteList should be(true)
      }
    }

    "used to delete list node comments" should {
      "do not delete a comment of root list node" in {
        val nodeIri = "http://rdfh.ch/lists/0001/testList"
        appActor ! ListNodeCommentsDeleteRequestADM(
          iri = nodeIri,
          requestingUser = SharedTestDataADM.anythingAdminUser
        )
        expectMsg(
          Failure(BadRequestException("Root node comments cannot be deleted."))
        )
      }

      "delete all comments of child node that contains just one comment" in {
        val nodeIri = "http://rdfh.ch/lists/0001/testList01"
        appActor ! ListNodeCommentsDeleteRequestADM(
          iri = nodeIri,
          requestingUser = SharedTestDataADM.anythingAdminUser
        )
        val response: ListNodeCommentsDeleteResponseADM =
          expectMsgType[ListNodeCommentsDeleteResponseADM](timeout)
        response.nodeIri should be(nodeIri)
        response.commentsDeleted should be(true)
      }

      "delete all comments of child node that contains more than one comment" in {
        val nodeIri = "http://rdfh.ch/lists/0001/testList02"
        appActor ! ListNodeCommentsDeleteRequestADM(
          iri = nodeIri,
          requestingUser = SharedTestDataADM.anythingAdminUser
        )
        val response: ListNodeCommentsDeleteResponseADM =
          expectMsgType[ListNodeCommentsDeleteResponseADM](timeout)
        response.nodeIri should be(nodeIri)
        response.commentsDeleted should be(true)
      }

      "if reqested list does not have comments, inform there is no comments to delete" in {
        val nodeIri = "http://rdfh.ch/lists/0001/testList03"
        appActor ! ListNodeCommentsDeleteRequestADM(
          iri = nodeIri,
          requestingUser = SharedTestDataADM.anythingAdminUser
        )
        expectMsg(
          Failure(BadRequestException(s"Nothing to delete. Node $nodeIri does not have comments."))
        )
      }
    }
  }
}
