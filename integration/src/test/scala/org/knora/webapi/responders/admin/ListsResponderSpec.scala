/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.admin

import org.apache.pekko.testkit.*

import java.util.UUID

import dsp.errors.BadRequestException
import dsp.errors.DuplicateValueException
import dsp.errors.UpdateNotPerformedException
import dsp.valueobjects.Iri
import dsp.valueobjects.V2
import org.knora.webapi.*
import org.knora.webapi.messages.admin.responder.listsmessages.*
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.routing.UnsafeZioRun
import org.knora.webapi.sharedtestdata.SharedListsTestDataADM
import org.knora.webapi.sharedtestdata.SharedTestDataADM
import org.knora.webapi.sharedtestdata.SharedTestDataADM2.*
import org.knora.webapi.slice.admin.api.Requests.ListChangePositionRequest
import org.knora.webapi.slice.admin.api.Requests.ListChangeRequest
import org.knora.webapi.slice.admin.api.Requests.ListCreateChildNodeRequest
import org.knora.webapi.slice.admin.api.Requests.ListCreateRootNodeRequest
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.ListProperties.*
import org.knora.webapi.util.MutableTestIri
import org.knora.webapi.util.ZioScalaTestUtil.assertFailsWithA

/**
 * Tests [[ListsResponder]].
 */
class ListsResponderSpec extends CoreSpec with ImplicitSender {

  override lazy val rdfDataObjects = List(
    RdfDataObject(
      path = "test_data/project_data/images-demo-data.ttl",
      name = "http://www.knora.org/data/00FF/images"
    ),
    RdfDataObject(
      path = "test_data/project_data/anything-data.ttl",
      name = "http://www.knora.org/data/0001/anything"
    )
  )

  private val treeListInfo: ListRootNodeInfoADM = SharedListsTestDataADM.treeListInfo

  private val summerNodeInfo: ListNodeInfoADM = SharedListsTestDataADM.summerNodeInfo

  private val otherTreeListInfo: ListRootNodeInfoADM = SharedListsTestDataADM.otherTreeListInfo

  private val treeListChildNodes: Seq[ListNodeADM] = SharedListsTestDataADM.treeListChildNodes

  "The Lists Responder" when {
    "used to query information about lists" should {
      "return all lists" in {
        val actual = UnsafeZioRun.runOrThrow(ListsResponder.getLists(None))
        actual.lists.size should be(9)
      }

      "return all lists belonging to the images project" in {
        val actual = UnsafeZioRun.runOrThrow(ListsResponder.getLists(Some(ProjectIri.unsafeFrom(imagesProjectIri))))
        actual.lists.size should be(4)
      }

      "return all lists belonging to the anything project" in {
        val actual = UnsafeZioRun.runOrThrow(ListsResponder.getLists(Some(ProjectIri.unsafeFrom(anythingProjectIri))))
        actual.lists.size should be(4)
      }

      "return basic list information (anything list)" in {
        val actual =
          UnsafeZioRun.runOrThrow(ListsResponder.listNodeInfoGetRequestADM("http://rdfh.ch/lists/0001/treeList"))
        actual match {
          case RootNodeInfoGetResponseADM(listInfo) => listInfo.sorted should be(treeListInfo.sorted)
          case _                                    => fail(s"Expecting RootNodeInfoGetResponseADM.")
        }
      }

      "return basic list information (anything other list)" in {
        val actual = UnsafeZioRun.runOrThrow(
          ListsResponder.listNodeInfoGetRequestADM("http://rdfh.ch/lists/0001/otherTreeList")
        )
        actual match {
          case RootNodeInfoGetResponseADM(listInfo) => listInfo.sorted should be(otherTreeListInfo.sorted)
          case _                                    => fail(s"Expecting RootNodeInfoGetResponseADM.")
        }
      }

      "return basic node information (images list - sommer)" in {
        val actual = UnsafeZioRun.runOrThrow(
          ListsResponder.listNodeInfoGetRequestADM("http://rdfh.ch/lists/00FF/526f26ed04")
        )
        actual match {
          case ChildNodeInfoGetResponseADM(childInfo) => childInfo.sorted should be(summerNodeInfo.sorted)
          case _                                      => fail(s"Expecting ChildNodeInfoGetResponseADM.")
        }
      }

      "return a full list response" in {
        val actual = UnsafeZioRun.runOrThrow(
          ListsResponder.listGetRequestADM("http://rdfh.ch/lists/0001/treeList")
        )
        actual match {
          case ListGetResponseADM(list) =>
            list.listinfo.sorted should be(treeListInfo.sorted)
            list.children.map(_.sorted) should be(treeListChildNodes.map(_.sorted))
          case _ => fail(s"Expecting ListGetResponseADM.")
        }
      }
    }

    val newListIri     = new MutableTestIri
    val firstChildIri  = new MutableTestIri
    val secondChildIri = new MutableTestIri
    val thirdChildIri  = new MutableTestIri

    "used to modify lists" should {
      "create a list" in {
        val received = UnsafeZioRun.runOrThrow(
          ListsResponder.listCreateRootNode(
            ListCreateRootNodeRequest(
              id = None,
              Comments.unsafeFrom(Seq(V2.StringLiteralV2(value = "Neuer Kommentar", language = Some("de")))),
              Labels.unsafeFrom(Seq(V2.StringLiteralV2(value = "Neue Liste", language = Some("de")))),
              Some(ListName.unsafeFrom("neuelistename")),
              ProjectIri.unsafeFrom(imagesProjectIri)
            ),
            apiRequestID = UUID.randomUUID
          )
        )

        val listInfo = received.list.listinfo
        listInfo.projectIri should be(imagesProjectIri)

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
        val received = UnsafeZioRun.runOrThrow(
          ListsResponder.listCreateRootNode(
            ListCreateRootNodeRequest(
              id = None,
              Comments.unsafeFrom(Seq(V2.StringLiteralV2(commentWithSpecialCharacter, language = Some("de")))),
              Labels.unsafeFrom(Seq(V2.StringLiteralV2(labelWithSpecialCharacter, language = Some("de")))),
              Some(ListName.unsafeFrom(nameWithSpecialCharacter)),
              ProjectIri.unsafeFrom(imagesProjectIri)
            ),
            apiRequestID = UUID.randomUUID
          )
        )

        val listInfo = received.list.listinfo
        listInfo.projectIri should be(imagesProjectIri)

        listInfo.name should be(Some(Iri.fromSparqlEncodedString(nameWithSpecialCharacter)))

        val labels: Seq[StringLiteralV2] = listInfo.labels.stringLiterals
        labels.size should be(1)
        val givenLabel = labels.head
        givenLabel.value shouldEqual Iri.fromSparqlEncodedString(labelWithSpecialCharacter)
        givenLabel.language shouldEqual Some("de")

        val comments     = received.list.listinfo.comments.stringLiterals
        val givenComment = comments.head
        givenComment.language shouldEqual Some("de")
        givenComment.value shouldEqual Iri.fromSparqlEncodedString(commentWithSpecialCharacter)

        val children = received.list.children
        children.size should be(0)
      }

      "update basic list information" in {
        val theChange: ListChangeRequest = ListChangeRequest(
          listIri = ListIri.unsafeFrom(newListIri.get),
          projectIri = ProjectIri.unsafeFrom(imagesProjectIri),
          name = Some(ListName.unsafeFrom("updated name")),
          labels = Some(
            Labels.unsafeFrom(
              Seq(
                V2.StringLiteralV2(value = "Neue geänderte Liste", language = Some("de")),
                V2.StringLiteralV2(value = "Changed List", language = Some("en"))
              )
            )
          ),
          comments = Some(
            Comments
              .unsafeFrom(
                Seq(
                  V2.StringLiteralV2(value = "Neuer Kommentar", language = Some("de")),
                  V2.StringLiteralV2(value = "New Comment", language = Some("en"))
                )
              )
          )
        )

        val received = UnsafeZioRun.runOrThrow(ListsResponder.nodeInfoChangeRequest(theChange, UUID.randomUUID()))
        val listInfo = received match {
          case RootNodeInfoGetResponseADM(info) => info
          case _                                => fail("RootNodeInfoGetResponseADM expected")
        }

        listInfo.projectIri should be(imagesProjectIri)
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
        val name       = Some(ListName.unsafeFrom("sommer"))
        val projectIRI = ProjectIri.unsafeFrom(imagesProjectIri)
        val theChange = ListChangeRequest(
          listIri = ListIri.unsafeFrom(newListIri.get),
          projectIri = projectIRI,
          name = name
        )
        val exit = UnsafeZioRun.run(ListsResponder.nodeInfoChangeRequest(theChange, UUID.randomUUID()))
        assertFailsWithA[DuplicateValueException](
          exit,
          s"The name ${name.value} is already used by a list inside the project ${projectIRI.value}."
        )
      }

      "add child to list - to the root node" in {
        val received = UnsafeZioRun.runOrThrow(
          ListsResponder.listCreateChildNode(
            ListCreateChildNodeRequest(
              id = None,
              Some(
                Comments.unsafeFrom(
                  Seq(V2.StringLiteralV2(value = "New First Child List Node Comment", language = Some("en")))
                )
              ),
              Labels.unsafeFrom(
                Seq(V2.StringLiteralV2(value = "New First Child List Node Value", language = Some("en")))
              ),
              Some(ListName.unsafeFrom("first")),
              ListIri.unsafeFrom(newListIri.get),
              None,
              ProjectIri.unsafeFrom(imagesProjectIri)
            ),
            UUID.randomUUID
          )
        )

        val nodeInfo = received.nodeinfo
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
        val received = UnsafeZioRun.runOrThrow(
          ListsResponder.listCreateChildNode(
            ListCreateChildNodeRequest(
              id = None,
              Some(
                Comments.unsafeFrom(
                  Seq(V2.StringLiteralV2(value = "New Second Child List Node Comment", language = Some("en")))
                )
              ),
              Labels.unsafeFrom(
                Seq(V2.StringLiteralV2(value = "New Second Child List Node Value", language = Some("en")))
              ),
              Some(ListName.unsafeFrom("second")),
              ListIri.unsafeFrom(newListIri.get),
              Some(Position.unsafeFrom(0)),
              ProjectIri.unsafeFrom(imagesProjectIri)
            ),
            UUID.randomUUID
          )
        )

        val nodeInfo = received.nodeinfo

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
        val received = UnsafeZioRun.runOrThrow(
          ListsResponder.listCreateChildNode(
            ListCreateChildNodeRequest(
              id = None,
              Some(
                Comments.unsafeFrom(
                  Seq(V2.StringLiteralV2(value = "New Third Child List Node Comment", language = Some("en")))
                )
              ),
              Labels.unsafeFrom(
                Seq(V2.StringLiteralV2(value = "New Third Child List Node Value", language = Some("en")))
              ),
              Some(ListName.unsafeFrom("third")),
              ListIri.unsafeFrom(secondChildIri.get),
              Some(Position.unsafeFrom(0)),
              ProjectIri.unsafeFrom(imagesProjectIri)
            ),
            UUID.randomUUID
          )
        )

        val nodeInfo = received.nodeinfo

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
        val givenPosition = Position.unsafeFrom(20)

        val exit = UnsafeZioRun.run(
          ListsResponder.listCreateChildNode(
            ListCreateChildNodeRequest(
              id = None,
              Some(
                Comments.unsafeFrom(
                  Seq(V2.StringLiteralV2(value = "New Fourth Child List Node Comment", language = Some("en")))
                )
              ),
              Labels.unsafeFrom(
                Seq(V2.StringLiteralV2(value = "New Fourth Child List Node Value", language = Some("en")))
              ),
              Some(ListName.unsafeFrom("fourth")),
              ListIri.unsafeFrom(newListIri.get),
              Some(givenPosition),
              ProjectIri.unsafeFrom(imagesProjectIri)
            ),
            UUID.randomUUID
          )
        )

        assertFailsWithA[BadRequestException](
          exit,
          s"Invalid position given ${givenPosition.value}, maximum allowed position is = 2."
        )
      }
    }

    "used to reposition nodes" should {
      "not reposition a node if new position is the same as old one" in {
        val nodeIri     = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/notUsedList014")
        val parentIri   = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/notUsedList01")
        val newPosition = Position.unsafeFrom(3)
        val exit = UnsafeZioRun.run(
          ListsResponder.nodePositionChangeRequestADM(
            nodeIri,
            ListChangePositionRequest(newPosition, parentIri),
            SharedTestDataADM.anythingAdminUser,
            UUID.randomUUID
          )
        )
        assertFailsWithA[UpdateNotPerformedException](
          exit,
          s"The given position is the same as node's current position."
        )
      }

      "not reposition a node if new position is out of range" in {
        val nodeIri     = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/notUsedList014")
        val parentIri   = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/notUsedList01")
        val newPosition = Position.unsafeFrom(30)

        val exit = UnsafeZioRun.run(
          ListsResponder.nodePositionChangeRequestADM(
            nodeIri,
            ListChangePositionRequest(newPosition, parentIri),
            SharedTestDataADM.anythingAdminUser,
            UUID.randomUUID
          )
        )
        assertFailsWithA[BadRequestException](
          exit,
          s"Invalid position given, maximum allowed position is = 4."
        )
      }

      "not reposition a node to another parent node if new position is out of range" in {
        val nodeIri     = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/notUsedList014")
        val parentIri   = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/notUsedList")
        val newPosition = Position.unsafeFrom(30)

        val exit = UnsafeZioRun.run(
          ListsResponder.nodePositionChangeRequestADM(
            nodeIri,
            ListChangePositionRequest(newPosition, parentIri),
            SharedTestDataADM.anythingAdminUser,
            UUID.randomUUID
          )
        )
        assertFailsWithA[BadRequestException](
          exit,
          s"Invalid position given, maximum allowed position is = 3."
        )
      }

      "reposition node List014 from position 3 to 1 (shift to right)" in {
        val nodeIri     = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/notUsedList014")
        val parentIri   = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/notUsedList01")
        val newPosition = Position.unsafeFrom(1)

        val received = UnsafeZioRun.runOrThrow(
          ListsResponder.nodePositionChangeRequestADM(
            nodeIri,
            ListChangePositionRequest(newPosition, parentIri),
            SharedTestDataADM.anythingAdminUser,
            UUID.randomUUID
          )
        )

        val parentNode = received.node
        parentNode.id should be(parentIri.value)

        val children      = parentNode.children
        val isNodeUpdated = children.exists(child => child.id == nodeIri.value && child.position == 1)
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
        val nodeIri     = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/notUsedList011")
        val parentIri   = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/notUsedList01")
        val newPosition = Position.unsafeFrom(-1)

        val received = UnsafeZioRun.runOrThrow(
          ListsResponder.nodePositionChangeRequestADM(
            nodeIri,
            ListChangePositionRequest(newPosition, parentIri),
            SharedTestDataADM.anythingAdminUser,
            UUID.randomUUID
          )
        )

        val parentNode = received.node

        /* check parent node */
        parentNode.id should be(parentIri.value)
        val children      = parentNode.children
        val isNodeUpdated = children.exists(child => child.id == nodeIri.value && child.position == 4)
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
        val nodeIri      = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/notUsedList013")
        val newParentIri = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/notUsedList")
        val newPosition  = Position.unsafeFrom(2)

        val received = UnsafeZioRun.runOrThrow(
          ListsResponder.nodePositionChangeRequestADM(
            nodeIri,
            ListChangePositionRequest(newPosition, newParentIri),
            SharedTestDataADM.anythingAdminUser,
            UUID.randomUUID
          )
        )

        val oldParentIri = "http://rdfh.ch/lists/0001/notUsedList01"
        val parentNode   = received.node
        parentNode.id should be(newParentIri.value)

        /* check children of new parent node */
        val childrenOfNewParent = parentNode.children

        // node must be in children of new parent
        childrenOfNewParent.size should be(4)
        val isNodeAdd = childrenOfNewParent.exists(child => child.id == nodeIri.value && child.position == 2)
        isNodeAdd should be(true)

        // last node of new parent must be shifted one place to right
        val isShifted = childrenOfNewParent.exists(child =>
          child.id == "http://rdfh.ch/lists/0001/notUsedList03" && child.position == 3
        )
        isShifted should be(true)

        /* check old parent node */
        val actual = UnsafeZioRun.runOrThrow(ListsResponder.listGetRequestADM(oldParentIri))
        val receivedNode: ListNodeGetResponseADM = actual match {
          case it: ListNodeGetResponseADM => it
          case _                          => fail(s"Expecting ListNodeGetResponseADM.")
        }

        // node must not be in children of old parent
        val oldParentChildren = receivedNode.node.children
        oldParentChildren.size should be(4)
        val isNodeUpdated = oldParentChildren.exists(child => child.id == nodeIri.value)
        isNodeUpdated should be(false)

        // nodes of old siblings must be shifted to the left.
        val lastNode = oldParentChildren.last
        lastNode.id should be("http://rdfh.ch/lists/0001/notUsedList011")
        lastNode.position should be(3)
      }

      "reposition node List015 to the end of another parent's children" in {
        val nodeIri      = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/notUsedList015")
        val newParentIri = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/notUsedList")
        val newPosition  = Position.unsafeFrom(-1)

        val received = UnsafeZioRun.runOrThrow(
          ListsResponder.nodePositionChangeRequestADM(
            nodeIri,
            ListChangePositionRequest(newPosition, newParentIri),
            SharedTestDataADM.anythingAdminUser,
            UUID.randomUUID
          )
        )

        val oldParentIri = "http://rdfh.ch/lists/0001/notUsedList01"
        val parentNode   = received.node
        parentNode.id should be(newParentIri.value)

        /* check children of new parent node */
        val childrenOfNewParent = parentNode.children

        // node must be in children of new parent
        childrenOfNewParent.size should be(5)
        val isNodeAdd = childrenOfNewParent.exists(child => child.id == nodeIri.value && child.position == 4)
        isNodeAdd should be(true)

        // last node of new parent must have remained in its current position
        val isShifted = childrenOfNewParent.exists(child =>
          child.id == "http://rdfh.ch/lists/0001/notUsedList03" && child.position == 3
        )
        isShifted should be(true)

        /* check old parent node */
        val actual = UnsafeZioRun.runOrThrow(ListsResponder.listGetRequestADM(oldParentIri))
        val receivedNode: ListNodeGetResponseADM = actual match {
          case node: ListNodeGetResponseADM => node
          case _                            => fail(s"Expecting ListNodeGetResponseADM.")
        }

        // node must not be in children of old parent
        val oldParentChildren = receivedNode.node.children
        oldParentChildren.size should be(3)
        val isNodeUpdated = oldParentChildren.exists(child => child.id == nodeIri.value)
        isNodeUpdated should be(false)
      }

      "put List015 back in end of its original parent node" in {
        val nodeIri      = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/notUsedList015")
        val newParentIri = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/notUsedList01")
        val newPosition  = Position.unsafeFrom(-1)

        val received = UnsafeZioRun.runOrThrow(
          ListsResponder.nodePositionChangeRequestADM(
            nodeIri,
            ListChangePositionRequest(newPosition, newParentIri),
            SharedTestDataADM.anythingAdminUser,
            UUID.randomUUID
          )
        )

        val parentNode = received.node
        parentNode.id should be(newParentIri.value)

        /* check children of new parent node */
        val childrenOfNewParent = parentNode.children
        childrenOfNewParent.size should be(4)
        val isNodeUpdated = childrenOfNewParent.exists(child => child.id == nodeIri.value && child.position == 3)
        isNodeUpdated should be(true)
      }

      "put List013 back in position 2 of its original parent node" in {
        val nodeIri      = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/notUsedList013")
        val newParentIri = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/notUsedList01")
        val newPosition  = Position.unsafeFrom(2)

        val received = UnsafeZioRun.runOrThrow(
          ListsResponder.nodePositionChangeRequestADM(
            nodeIri,
            ListChangePositionRequest(newPosition, newParentIri),
            SharedTestDataADM.anythingAdminUser,
            UUID.randomUUID
          )
        )

        val parentNode = received.node
        parentNode.id should be(newParentIri.value)

        /* check children of new parent node */
        val childrenOfNewParent = parentNode.children
        childrenOfNewParent.size should be(5)
        val isNodeUpdated = childrenOfNewParent.exists(child => child.id == nodeIri.value && child.position == 2)
        isNodeUpdated should be(true)
      }

      "put List011 back in its original place" in {
        val nodeIri      = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/notUsedList011")
        val newParentIri = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/notUsedList01")
        val newPosition  = Position.unsafeFrom(0)

        val received = UnsafeZioRun.runOrThrow(
          ListsResponder.nodePositionChangeRequestADM(
            nodeIri,
            ListChangePositionRequest(newPosition, newParentIri),
            SharedTestDataADM.anythingAdminUser,
            UUID.randomUUID
          )
        )

        val parentNode = received.node
        parentNode.id should be(newParentIri.value)
        val isNodeUpdated = parentNode.children.exists(child => child.id == nodeIri.value && child.position == 0)
        isNodeUpdated should be(true)
      }

      "put List014 back in its original position" in {
        val nodeIri      = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/notUsedList014")
        val newParentIri = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/notUsedList01")
        val newPosition  = Position.unsafeFrom(3)

        val received = UnsafeZioRun.runOrThrow(
          ListsResponder.nodePositionChangeRequestADM(
            nodeIri,
            ListChangePositionRequest(newPosition, newParentIri),
            SharedTestDataADM.anythingAdminUser,
            UUID.randomUUID
          )
        )

        val parentNode = received.node
        parentNode.id should be(newParentIri.value)
        val isNodeUpdated = parentNode.children.exists(child => child.id == nodeIri.value && child.position == 3)
        isNodeUpdated should be(true)
      }

      "reposition node in a position equal to length of new parents children" in {
        val nodeIri      = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/notUsedList03")
        val newParentIri = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/notUsedList01")
        val newPosition  = Position.unsafeFrom(5)

        val received = UnsafeZioRun.runOrThrow(
          ListsResponder.nodePositionChangeRequestADM(
            nodeIri,
            ListChangePositionRequest(newPosition, newParentIri),
            SharedTestDataADM.anythingAdminUser,
            UUID.randomUUID
          )
        )

        val parentNode = received.node
        parentNode.id should be(newParentIri.value)
        val isNodeUpdated = parentNode.children.exists(child => child.id == nodeIri.value && child.position == 5)
        isNodeUpdated should be(true)
      }

      "reposition List014 in position 0 of its sibling which does not have a child" in {
        val nodeIri      = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/notUsedList014")
        val newParentIri = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/notUsedList015")
        val newPosition  = Position.unsafeFrom(0)

        val received = UnsafeZioRun.runOrThrow(
          ListsResponder.nodePositionChangeRequestADM(
            nodeIri,
            ListChangePositionRequest(newPosition, newParentIri),
            SharedTestDataADM.anythingAdminUser,
            UUID.randomUUID
          )
        )

        val parentNode = received.node
        parentNode.id should be(newParentIri.value)
        val isNodeUpdated = parentNode.children.exists(child => child.id == nodeIri.value && child.position == 0)
        isNodeUpdated should be(true)
      }
    }

    "used to delete list items" should {
      "not delete a node that is in use" in {
        val nodeInUseIri = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/treeList01")
        val exit = UnsafeZioRun.run(
          ListsResponder.deleteListItemRequestADM(
            nodeInUseIri,
            SharedTestDataADM.anythingAdminUser,
            UUID.randomUUID
          )
        )
        assertFailsWithA[BadRequestException](
          exit,
          s"Node ${nodeInUseIri.value} cannot be deleted, because it is in use."
        )
      }

      "not delete a node that has a child which is used (node itself not in use, but its child is)" in {
        val nodeIri = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/treeList03")
        val exit = UnsafeZioRun.run(
          ListsResponder.deleteListItemRequestADM(
            nodeIri,
            SharedTestDataADM.anythingAdminUser,
            UUID.randomUUID
          )
        )
        val usedChild = "http://rdfh.ch/lists/0001/treeList10"
        assertFailsWithA[BadRequestException](
          exit,
          s"Node ${nodeIri.value} cannot be deleted, because its child $usedChild is in use."
        )
      }

      "not delete a node used as object of salsah-gui:guiAttribute (i.e. 'hlist=<nodeIri>') but not as object of knora-base:valueHasListNode" in {
        val nodeInUseInOntologyIri = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/treeList")
        val exit = UnsafeZioRun.run(
          ListsResponder.deleteListItemRequestADM(
            nodeInUseInOntologyIri,
            SharedTestDataADM.anythingAdminUser,
            UUID.randomUUID
          )
        )
        assertFailsWithA[BadRequestException](
          exit,
          s"Node ${nodeInUseInOntologyIri.value} cannot be deleted, because it is in use."
        )
      }

      "delete a middle child node that is not in use" in {
        val nodeIri = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/notUsedList012")
        val received = UnsafeZioRun.runOrThrow(
          ListsResponder.deleteListItemRequestADM(
            nodeIri,
            SharedTestDataADM.anythingAdminUser,
            UUID.randomUUID
          )
        )

        val parentNode = received match {
          case ChildNodeDeleteResponseADM(node) => node
          case _                                => fail("expecting ChildNodeDeleteResponseADM")
        }

        val remainingChildren = parentNode.children
        remainingChildren.size should be(4)
        // Tailing children should be shifted to left
        remainingChildren.last.position should be(3)

        // node List015 should still have its child
        val list015 = remainingChildren.filter(node => node.id == "http://rdfh.ch/lists/0001/notUsedList015").head
        list015.position should be(2)
        list015.children.size should be(1)
      }

      "delete a child node that is not in use" in {
        val nodeIri = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/notUsedList02")
        val received = UnsafeZioRun.runOrThrow(
          ListsResponder.deleteListItemRequestADM(
            nodeIri,
            SharedTestDataADM.anythingAdminUser,
            UUID.randomUUID
          )
        )
        val parentNode = received match {
          case ChildNodeDeleteResponseADM(node) => node
          case _                                => fail("expecting ChildNodeDeleteResponseADM")
        }

        val remainingChildren = parentNode.children
        remainingChildren.size should be(1)
        val firstChild = remainingChildren.head
        firstChild.id should be("http://rdfh.ch/lists/0001/notUsedList01")
        firstChild.position should be(0)
      }

      "delete a list (i.e. root node) that is not in use in ontology" in {
        val listIri = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/notUsedList")
        val actual = UnsafeZioRun.runOrThrow(
          ListsResponder.deleteListItemRequestADM(
            listIri,
            SharedTestDataADM.anythingAdminUser,
            UUID.randomUUID
          )
        )

        val received = actual match {
          case resp: ListDeleteResponseADM => resp
          case _                           => fail("expecting ListDeleteResponseADM")
        }
        received.iri should be(listIri.value)
        received.deleted should be(true)
      }
    }

    "used to query if list can be deleted" should {
      "return FALSE for a node that is in use" in {
        val nodeInUseIri = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/treeList01")
        val response     = UnsafeZioRun.runOrThrow(ListsResponder.canDeleteListRequestADM(nodeInUseIri))
        response.listIri should be(nodeInUseIri.value)
        response.canDeleteList should be(false)
      }

      "return FALSE for a node that is unused but has a child which is used" in {
        val nodeIri  = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/treeList03")
        val response = UnsafeZioRun.runOrThrow(ListsResponder.canDeleteListRequestADM(nodeIri))
        response.listIri should be(nodeIri.value)
        response.canDeleteList should be(false)
      }

      "return FALSE for a node used as object of salsah-gui:guiAttribute (i.e. 'hlist=<nodeIri>') but not as object of knora-base:valueHasListNode" in {
        val nodeInUseInOntologyIri = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/treeList03")
        val response               = UnsafeZioRun.runOrThrow(ListsResponder.canDeleteListRequestADM(nodeInUseInOntologyIri))
        response.listIri should be(nodeInUseInOntologyIri.value)
        response.canDeleteList should be(false)
      }

      "return TRUE for a middle child node that is not in use" in {
        val nodeIri  = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/notUsedList012")
        val response = UnsafeZioRun.runOrThrow(ListsResponder.canDeleteListRequestADM(nodeIri))
        response.listIri should be(nodeIri.value)
        response.canDeleteList should be(true)
      }

      "return TRUE for a child node that is not in use" in {
        val nodeIri  = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/notUsedList02")
        val response = UnsafeZioRun.runOrThrow(ListsResponder.canDeleteListRequestADM(nodeIri))
        response.listIri should be(nodeIri.value)
        response.canDeleteList should be(true)
      }

      "delete a list (i.e. root node) that is not in use in ontology" in {
        val listIri  = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/notUsedList")
        val response = UnsafeZioRun.runOrThrow(ListsResponder.canDeleteListRequestADM(listIri))
        response.listIri should be(listIri.value)
        response.canDeleteList should be(true)
      }
    }

    "used to delete list node comments" should {
      "do not delete a comment of root list node" in {
        val listIri = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/testList")
        val exit    = UnsafeZioRun.run(ListsResponder.deleteListNodeCommentsADM(listIri))
        assertFailsWithA[BadRequestException](
          exit,
          s"Root node comments cannot be deleted."
        )
      }

      "delete all comments of child node that contains just one comment" in {
        val listIri  = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/testList01")
        val response = UnsafeZioRun.runOrThrow(ListsResponder.deleteListNodeCommentsADM(listIri))
        response.nodeIri should be(listIri.value)
        response.commentsDeleted should be(true)
      }

      "delete all comments of child node that contains more than one comment" in {
        val listIri  = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/testList02")
        val response = UnsafeZioRun.runOrThrow(ListsResponder.deleteListNodeCommentsADM(listIri))
        response.nodeIri should be(listIri.value)
        response.commentsDeleted should be(true)
      }

      "if requested list does not have comments, inform there is no comments to delete" in {
        val listIri = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/testList03")
        val exit    = UnsafeZioRun.run(ListsResponder.deleteListNodeCommentsADM(listIri))
        assertFailsWithA[BadRequestException](
          exit,
          s"Nothing to delete. Node ${listIri.value} does not have comments."
        )
      }
    }
  }
}
