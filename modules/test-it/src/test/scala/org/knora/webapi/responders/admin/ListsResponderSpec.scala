/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.admin

import zio.*
import zio.test.*
import zio.test.Assertion.*

import java.util.UUID
import scala.reflect.ClassTag

import dsp.errors.BadRequestException
import dsp.errors.DuplicateValueException
import dsp.errors.NotFoundException
import dsp.errors.UpdateNotPerformedException
import dsp.valueobjects.Iri
import org.knora.webapi.*
import org.knora.webapi.messages.admin.responder.listsmessages.*
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.sharedtestdata.SharedListsTestDataADM
import org.knora.webapi.sharedtestdata.SharedTestDataADM.*
import org.knora.webapi.slice.admin.api.Requests.*
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.admin.domain.model.ListProperties.*
import org.knora.webapi.slice.common.domain.LanguageCode.*
import org.knora.webapi.util.MutableTestIri

object ListsResponderSpec extends E2EZSpec {

  override val rdfDataObjects = List(
    RdfDataObject(
      path = "test_data/project_data/images-demo-data.ttl",
      name = "http://www.knora.org/data/00FF/images",
    ),
    RdfDataObject(
      path = "test_data/project_data/anything-data.ttl",
      name = "http://www.knora.org/data/0001/anything",
    ),
  )
  private val listsResponder = ZIO.serviceWithZIO[ListsResponder]

  private val treeListInfo: ListRootNodeInfoADM      = SharedListsTestDataADM.treeListInfo
  private val summerNodeInfo: ListNodeInfoADM        = SharedListsTestDataADM.summerNodeInfo
  private val otherTreeListInfo: ListRootNodeInfoADM = SharedListsTestDataADM.otherTreeListInfo
  private val treeListChildNodes: Seq[ListNodeADM]   = SharedListsTestDataADM.treeListChildNodes

  private val newListIri     = new MutableTestIri
  private val firstChildIri  = new MutableTestIri
  private val secondChildIri = new MutableTestIri
  private val thirdChildIri  = new MutableTestIri

  private def expectType[A](implicit ct: ClassTag[A]): Any => Task[A] = {
    case a: A  => ZIO.succeed(a)
    case other => ZIO.fail(new Exception(s"Unexpected Type ${other.getClass}"))
  }

  override val e2eSpec = suite("The Lists Responder")(
    suite("used to query information about lists")(
      test("return all lists") {
        listsResponder(_.getLists(None)).map(actual => assertTrue(actual.lists.size == 9))
      },
      test("return all lists belonging to the images project by iri") {
        listsResponder(_.getLists(Some(Left(imagesProjectIri))))
          .map(actual => assertTrue(actual.lists.size == 4))
      },
      test("return all lists belonging to the images project by shortcode") {
        listsResponder(_.getLists(Some(Right(imagesProjectShortcode))))
          .map(actual => assertTrue(actual.lists.size == 4))
      },
      test("getLists should fail if project by iri was not found") {
        listsResponder(_.getLists(Some(Left(ProjectIri.unsafeFrom("http://rdfh.ch/projects/unknown"))))).exit
          .map(assert(_)(failsWithA[NotFoundException]))
      },
      test("getLists should fail if project by shortcode was not found") {
        listsResponder(_.getLists(Some(Right(Shortcode.unsafeFrom("9999"))))).exit
          .map(assert(_)(failsWithA[NotFoundException]))
      },
      test("return all lists belonging to the anything project") {
        listsResponder(_.getLists(Some(Left(anythingProjectIri))))
          .map(actual => assertTrue(actual.lists.size == 4))
      },
      test("return basic list information (anything list)") {
        listsResponder(_.listNodeInfoGetRequestADM("http://rdfh.ch/lists/0001/treeList"))
          .flatMap(expectType[RootNodeInfoGetResponseADM])
          .map(actual => assertTrue(actual.listinfo.sorted == treeListInfo.sorted))
      },
      test("return basic list information (anything other list)") {
        listsResponder(_.listNodeInfoGetRequestADM("http://rdfh.ch/lists/0001/otherTreeList"))
          .flatMap(expectType[RootNodeInfoGetResponseADM])
          .map(actual => assertTrue(actual.listinfo.sorted == otherTreeListInfo.sorted))
      },
      test("return basic node information (images list - sommer)") {
        listsResponder(_.listNodeInfoGetRequestADM("http://rdfh.ch/lists/00FF/526f26ed04"))
          .flatMap(expectType[ChildNodeInfoGetResponseADM])
          .map(actual => assertTrue(actual.nodeinfo.sorted == summerNodeInfo.sorted))
      },
      test("return a full list response") {
        listsResponder(_.listGetRequestADM(ListIri.unsafeFrom("http://rdfh.ch/lists/0001/treeList")))
          .flatMap(expectType[ListGetResponseADM])
          .map(actual =>
            assertTrue(
              actual.list.listinfo.sorted == treeListInfo.sorted,
              actual.list.children.map(_.sorted) == treeListChildNodes.map(_.sorted),
            ),
          )
      },
    ),
    suite("used to modify lists")(
      test("create a list") {
        val createRequest = ListCreateRootNodeRequest(
          id = None,
          Comments.unsafeFrom(Seq(StringLiteralV2.from("Neuer Kommentar", DE))),
          Labels.unsafeFrom(Seq(StringLiteralV2.from("Neue Liste", DE))),
          Some(ListName.unsafeFrom("neuelistename")),
          imagesProjectIri,
        )

        listsResponder(_.listCreateRootNode(createRequest, UUID.randomUUID))
          .tap(received => ZIO.succeed(received.list.listinfo.id))
          .map(_.list)
          .tap(list => ZIO.succeed(newListIri.set(list.listinfo.id)))
          .map { list =>
            assertTrue(
              list.children.isEmpty,
              list.listinfo.id == newListIri.get,
              list.listinfo.name == Some("neuelistename"),
              list.listinfo.projectIri == imagesProjectIri.value,
              list.listinfo.name == Some("neuelistename"),
              list.listinfo.labels.stringLiterals.size == 1,
              list.listinfo.labels.stringLiterals.head == StringLiteralV2
                .from(value = "Neue Liste", DE),
              !list.listinfo.comments.stringLiterals.isEmpty,
            )
          }
      },
      test("create a list with special characters in its labels") {
        val labelWithSpecialCharacter   = "Neue \\\"Liste\\\""
        val commentWithSpecialCharacter = "Neue \\\"Kommentar\\\""
        val nameWithSpecialCharacter    = "a new \\\"name\\\""
        val createReq = ListCreateRootNodeRequest(
          None,
          Comments.unsafeFrom(Seq(StringLiteralV2.from(commentWithSpecialCharacter, DE))),
          Labels.unsafeFrom(Seq(StringLiteralV2.from(labelWithSpecialCharacter, DE))),
          Some(ListName.unsafeFrom(nameWithSpecialCharacter)),
          imagesProjectIri,
        )
        listsResponder(_.listCreateRootNode(createReq, UUID.randomUUID)).map { actual =>
          assertTrue(
            actual.list.listinfo.projectIri == imagesProjectIri.value,
            actual.list.listinfo.name == Some(Iri.fromSparqlEncodedString(nameWithSpecialCharacter)),
            actual.list.listinfo.labels.stringLiterals.size == 1,
            actual.list.listinfo.labels.stringLiterals.head.value ==
              Iri.fromSparqlEncodedString(labelWithSpecialCharacter),
            actual.list.listinfo.labels.stringLiterals.head.languageOption.contains(DE),
            actual.list.listinfo.comments.stringLiterals.head.languageOption.contains(DE),
            actual.list.listinfo.comments.stringLiterals.head.value ==
              Iri.fromSparqlEncodedString(commentWithSpecialCharacter),
            actual.list.children.size == 0,
          )
        }
      },
      test("update basic list information") {
        val newLabelValues = Seq(
          StringLiteralV2.from("Neue geänderte Liste", DE),
          StringLiteralV2.from("Changed List", EN),
        )
        val newCommentValues = Seq(
          StringLiteralV2.from("Neuer Kommentar", DE),
          StringLiteralV2.from("New Comment", EN),
        )
        val changeReq = ListChangeRequest(
          newListIri.asListIri,
          imagesProjectIri,
          name = Some(ListName.unsafeFrom("updated name")),
          labels = Some(Labels.unsafeFrom(newLabelValues)),
          comments = Some(Comments.unsafeFrom(newCommentValues)),
        )
        listsResponder(_.nodeInfoChangeRequest(changeReq, UUID.randomUUID()))
          .flatMap(expectType[RootNodeInfoGetResponseADM])
          .map(_.listinfo)
          .map(listInfo =>
            assertTrue(
              listInfo.projectIri == imagesProjectIri.value,
              listInfo.name == Some("updated name"),
              listInfo.labels.stringLiterals.sorted == newLabelValues.sorted,
              listInfo.comments.stringLiterals.sorted == newCommentValues.sorted,
            ),
          )
      },
      test("not update basic list information if name is duplicate") {
        val changeReq = ListChangeRequest(
          newListIri.asListIri,
          imagesProjectIri,
          name = Some(ListName.unsafeFrom("sommer")),
        )
        listsResponder(_.nodeInfoChangeRequest(changeReq, UUID.randomUUID())).exit.map(
          assert(_)(
            E2EZSpec.failsWithMessageEqualTo[DuplicateValueException](
              s"The name ${Some(ListName.unsafeFrom("sommer")).value} is already used by a list inside the project ${imagesProjectIri}.",
            ),
          ),
        )
      },
      test("add child to list - to the root node") {
        val createReq = ListCreateChildNodeRequest(
          None,
          Some(Comments.unsafeFrom(Seq(StringLiteralV2.from("New First Child List Node Comment", EN)))),
          Labels.unsafeFrom(Seq(StringLiteralV2.from("New First Child List Node Value", EN))),
          Some(ListName.unsafeFrom("first")),
          newListIri.asListIri,
          None,
          imagesProjectIri,
        )
        listsResponder(_.listCreateChildNode(createReq, UUID.randomUUID))
          .map(_.nodeinfo)
          .tap(nodeInfo => ZIO.succeed(firstChildIri.set(nodeInfo.id)))
          .map(nodeInfo =>
            assertTrue(
              nodeInfo.labels.stringLiterals.sorted ==
                Seq(StringLiteralV2.from("New First Child List Node Value", EN)),
              nodeInfo.comments.stringLiterals.sorted ==
                Seq(StringLiteralV2.from("New First Child List Node Comment", EN)),
              nodeInfo.position == 0,
              nodeInfo.hasRootNode == newListIri.get,
            ),
          )
      },
      test("add second child to list in first position - to the root node") {
        val commentValues = Seq(StringLiteralV2.from("New Second Child List Node Comment", EN))
        val labelValues   = Seq(StringLiteralV2.from("New Second Child List Node Value", EN))
        val createReq = ListCreateChildNodeRequest(
          None,
          Some(Comments.unsafeFrom(commentValues)),
          Labels.unsafeFrom(labelValues),
          Some(ListName.unsafeFrom("second")),
          newListIri.asListIri,
          Some(Position.unsafeFrom(0)),
          imagesProjectIri,
        )
        listsResponder(_.listCreateChildNode(createReq, UUID.randomUUID))
          .map(_.nodeinfo)
          .tap(nodeInfo => ZIO.succeed(secondChildIri.set(nodeInfo.id)))
          .map(nodeInfo =>
            assertTrue(
              nodeInfo.labels.stringLiterals == labelValues,
              nodeInfo.comments.stringLiterals == commentValues,
              nodeInfo.position == 0,
              nodeInfo.hasRootNode == newListIri.get,
            ),
          )
      },
      test("add child to second child node") {
        val commentValues = Seq(StringLiteralV2.from("New Third Child List Node Comment", EN))
        val labelValues   = Seq(StringLiteralV2.from("New Third Child List Node Value", EN))
        val createReq = ListCreateChildNodeRequest(
          None,
          Some(Comments.unsafeFrom(commentValues)),
          Labels.unsafeFrom(labelValues),
          Some(ListName.unsafeFrom("third")),
          ListIri.unsafeFrom(secondChildIri.get),
          Some(Position.unsafeFrom(0)),
          imagesProjectIri,
        )
        listsResponder(_.listCreateChildNode(createReq, UUID.randomUUID))
          .map(_.nodeinfo)
          .tap(nodeInfo => ZIO.succeed(thirdChildIri.set(nodeInfo.id)))
          .map(nodeInfo =>
            assertTrue(
              nodeInfo.labels.stringLiterals.sorted == labelValues,
              nodeInfo.comments.stringLiterals == commentValues,
              nodeInfo.position == 0,
              nodeInfo.hasRootNode == newListIri.get,
            ),
          )
      },
      test("not create a node if given new position is out of range") {
        val givenPosition = Position.unsafeFrom(20)

        val createReq = ListCreateChildNodeRequest(
          None,
          None,
          Labels.unsafeFrom(Seq(StringLiteralV2.from("New Fourth Child List Node Value", EN))),
          None,
          ListIri.unsafeFrom(newListIri.get),
          Some(givenPosition),
          imagesProjectIri,
        )
        listsResponder(_.listCreateChildNode(createReq, UUID.randomUUID)).exit.map(
          assert(_)(
            E2EZSpec.failsWithMessageEqualTo[BadRequestException](
              s"Invalid position given ${givenPosition.value}, maximum allowed position is = 2.",
            ),
          ),
        )
      },
    ),
    suite("used to reposition nodes")(
      test("not reposition a node if new position is the same as old one") {
        val nodeIri     = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/notUsedList014")
        val parentIri   = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/notUsedList01")
        val newPosition = Position.unsafeFrom(3)
        listsResponder(
          _.nodePositionChangeRequest(
            nodeIri,
            ListChangePositionRequest(newPosition, parentIri),
            anythingAdminUser,
            UUID.randomUUID,
          ),
        ).exit.map(
          assert(_)(
            E2EZSpec.failsWithMessageEqualTo[UpdateNotPerformedException](
              s"The given position is the same as node's current position.",
            ),
          ),
        )
      },
      test("not reposition a node if new position is out of range") {
        val nodeIri     = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/notUsedList014")
        val parentIri   = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/notUsedList01")
        val newPosition = Position.unsafeFrom(30)
        listsResponder(
          _.nodePositionChangeRequest(
            nodeIri,
            ListChangePositionRequest(newPosition, parentIri),
            anythingAdminUser,
            UUID.randomUUID,
          ),
        ).exit.map(
          assert(_)(
            E2EZSpec.failsWithMessageEqualTo[BadRequestException](
              s"Invalid position given, maximum allowed position is = 4.",
            ),
          ),
        )
      },
      test("not reposition a node to another parent node if new position is out of range") {
        val nodeIri     = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/notUsedList014")
        val parentIri   = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/notUsedList")
        val newPosition = Position.unsafeFrom(30)
        listsResponder(
          _.nodePositionChangeRequest(
            nodeIri,
            ListChangePositionRequest(newPosition, parentIri),
            anythingAdminUser,
            UUID.randomUUID,
          ),
        ).exit.map(
          assert(_)(
            E2EZSpec.failsWithMessageEqualTo[BadRequestException](
              s"Invalid position given, maximum allowed position is = 3.",
            ),
          ),
        )
      },
      test("reposition node List014 from position 3 to 1 (shift to right)") {
        val nodeIri     = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/notUsedList014")
        val parentIri   = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/notUsedList01")
        val newPosition = Position.unsafeFrom(1)
        listsResponder(
          _.nodePositionChangeRequest(
            nodeIri,
            ListChangePositionRequest(newPosition, parentIri),
            anythingAdminUser,
            UUID.randomUUID,
          ),
        ).map(_.node).map { parentNode =>
          val children      = parentNode.children
          val isNodeUpdated = children.exists(child => child.id == nodeIri.value && child.position == 1)
          // node in position 1 must have been shifted to position 2
          val isShifted =
            children.exists(child => child.id == "http://rdfh.ch/lists/0001/notUsedList012" && child.position == 2)
          assertTrue(
            parentNode.id == parentIri.value,
            isNodeUpdated,
            // last node must not have changed
            children.last.position == 4,
            children.last.id == "http://rdfh.ch/lists/0001/notUsedList015",
            isShifted,
          )
        }
      },
      test("reposition node List011 from position 0 to end (shift to left)") {
        val nodeIri     = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/notUsedList011")
        val parentIri   = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/notUsedList01")
        val newPosition = Position.unsafeFrom(-1)
        listsResponder(
          _.nodePositionChangeRequest(
            nodeIri,
            ListChangePositionRequest(newPosition, parentIri),
            anythingAdminUser,
            UUID.randomUUID,
          ),
        ).map(_.node).map { parentNode =>
          val children      = parentNode.children
          val firstNode     = children.head
          val isNodeUpdated = children.exists(child => child.id == nodeIri.value && child.position == 4)
          assertTrue(
            parentNode.id == parentIri.value,
            isNodeUpdated,
            // node that was in position 1 must be in 0 now
            firstNode.id == "http://rdfh.ch/lists/0001/notUsedList014",
            firstNode.position == 0,
            // last node must be one before last now
            children.exists(child => child.id == "http://rdfh.ch/lists/0001/notUsedList015" && child.position == 3),
          )
        }
      },
      test("reposition node List013 in position 2 of another parent") {
        val nodeIri      = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/notUsedList013")
        val newParentIri = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/notUsedList")
        val newPosition  = Position.unsafeFrom(2)
        val oldParentIri = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/notUsedList01")
        val parentNode = listsResponder(
          _.nodePositionChangeRequest(
            nodeIri,
            ListChangePositionRequest(newPosition, newParentIri),
            anythingAdminUser,
            UUID.randomUUID,
          ),
        ).map(_.node)
        val actual = listsResponder(_.listGetRequestADM(oldParentIri)).flatMap(expectType[ListNodeGetResponseADM])

        (parentNode <*> actual).map { (parentNode: ListNodeADM, actual: ListNodeGetResponseADM) =>

          /* check children of new parent node */
          // node must be in children of new parent
          val isNodeAdd = parentNode.children.exists(child => child.id == nodeIri.value && child.position == 2)

          // last node of new parent must be shifted one place to right
          val isShifted = parentNode.children.exists(child =>
            child.id == "http://rdfh.ch/lists/0001/notUsedList03" && child.position == 3,
          )

          /* check old parent node */
          // node must not be in children of old parent
          val oldParentChildren = actual.node.children
          val isNodeUpdated     = oldParentChildren.exists(child => child.id == nodeIri.value)

          // nodes of old siblings must be shifted to the left.
          val lastNode = oldParentChildren.last

          assertTrue(
            parentNode.id == newParentIri.value,
            parentNode.children.size == 4,
            isNodeAdd,
            isShifted,
            oldParentChildren.size == 4,
            !isNodeUpdated,
            lastNode.id == "http://rdfh.ch/lists/0001/notUsedList011",
            lastNode.position == 3,
          )
        }
      },
      test("reposition node List015 to the end of another parent's children") {
        val nodeIri      = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/notUsedList015")
        val newParentIri = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/notUsedList")
        val oldParentIri = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/notUsedList01")
        val newPosition  = Position.unsafeFrom(-1)

        val parentNode = listsResponder(
          _.nodePositionChangeRequest(
            nodeIri,
            ListChangePositionRequest(newPosition, newParentIri),
            anythingAdminUser,
            UUID.randomUUID,
          ),
        ).map(_.node)

        val actual = listsResponder(_.listGetRequestADM(oldParentIri))
          .flatMap(expectType[ListNodeGetResponseADM])

        (parentNode <*> actual).map { (parentNode: ListNodeADM, actual: ListNodeGetResponseADM) =>
          /* check children of new parent node */
          val childrenOfNewParent = parentNode.children
          // node must be in children of new parent
          val isNodeAdd = childrenOfNewParent.exists(child => child.id == nodeIri.value && child.position == 4)
          // last node of new parent must have remained in its current position
          val isShifted = childrenOfNewParent
            .exists(child => child.id == "http://rdfh.ch/lists/0001/notUsedList03" && child.position == 3)
          // node must not be in children of old parent
          val oldParentChildren = actual.node.children
          val isNodeUpdated     = oldParentChildren.exists(child => child.id == nodeIri.value)
          assertTrue(
            parentNode.id == newParentIri.value,
            childrenOfNewParent.size == 5,
            isNodeAdd,
            isShifted,
            oldParentChildren.size == 3,
            !isNodeUpdated,
          )
        }
      },
      test("put List015 back in end of its original parent node") {
        val nodeIri      = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/notUsedList015")
        val newParentIri = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/notUsedList01")
        val newPosition  = Position.unsafeFrom(-1)

        listsResponder(
          _.nodePositionChangeRequest(
            nodeIri,
            ListChangePositionRequest(newPosition, newParentIri),
            anythingAdminUser,
            UUID.randomUUID,
          ),
        ).map(_.node).map { parentNode =>
          assertTrue(
            parentNode.id == newParentIri.value,
            parentNode.children.size == 4,
            parentNode.children.exists(child => child.id == nodeIri.value && child.position == 3),
          )
        }
      },
      test("put List013 back in position 2 of its original parent node") {
        val nodeIri      = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/notUsedList013")
        val newParentIri = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/notUsedList01")
        val newPosition  = Position.unsafeFrom(2)
        listsResponder(
          _.nodePositionChangeRequest(
            nodeIri,
            ListChangePositionRequest(newPosition, newParentIri),
            anythingAdminUser,
            UUID.randomUUID,
          ),
        ).map(_.node).map { parentNode =>
          assertTrue(
            parentNode.id == newParentIri.value,
            parentNode.children.size == 5,
            parentNode.children.exists(child => child.id == nodeIri.value && child.position == 2),
          )
        }
      },
      test("put List011 back in its original place") {
        val nodeIri      = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/notUsedList011")
        val newParentIri = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/notUsedList01")
        val newPosition  = Position.unsafeFrom(0)

        listsResponder(
          _.nodePositionChangeRequest(
            nodeIri,
            ListChangePositionRequest(newPosition, newParentIri),
            anythingAdminUser,
            UUID.randomUUID,
          ),
        ).map(_.node)
          .map(parentNode =>
            assertTrue(
              parentNode.id == newParentIri.value,
              parentNode.children.exists(child => child.id == nodeIri.value && child.position == 0),
            ),
          )
      },
      test("put List014 back in its original position") {
        val nodeIri      = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/notUsedList014")
        val newParentIri = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/notUsedList01")
        val newPosition  = Position.unsafeFrom(3)

        listsResponder(
          _.nodePositionChangeRequest(
            nodeIri,
            ListChangePositionRequest(newPosition, newParentIri),
            anythingAdminUser,
            UUID.randomUUID,
          ),
        ).map(_.node)
          .map(parentNode =>
            assertTrue(
              parentNode.id == newParentIri.value,
              parentNode.children.exists(child => child.id == nodeIri.value && child.position == 3),
            ),
          )
      },
      test("reposition node in a position equal to length of new parents children") {
        val nodeIri      = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/notUsedList03")
        val newParentIri = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/notUsedList01")
        val newPosition  = Position.unsafeFrom(5)
        listsResponder(
          _.nodePositionChangeRequest(
            nodeIri,
            ListChangePositionRequest(newPosition, newParentIri),
            anythingAdminUser,
            UUID.randomUUID,
          ),
        ).map(_.node)
          .map(received =>
            assertTrue(
              received.id == newParentIri.value,
              received.children.exists(child => child.id == nodeIri.value && child.position == 5),
            ),
          )
      },
      test("reposition List014 in position 0 of its sibling which does not have a child") {
        val nodeIri      = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/notUsedList014")
        val newParentIri = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/notUsedList015")
        val newPosition  = Position.unsafeFrom(0)
        listsResponder(
          _.nodePositionChangeRequest(
            nodeIri,
            ListChangePositionRequest(newPosition, newParentIri),
            anythingAdminUser,
            UUID.randomUUID,
          ),
        ).map(_.node).map { received =>
          assertTrue(
            received.id == newParentIri.value,
            received.children.exists(child => child.id == nodeIri.value && child.position == 0),
          )
        }
      },
    ),
    suite("used to delete list items")(
      test("not delete a node that is in use") {
        val nodeInUseIri = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/treeList01")
        listsResponder(_.deleteListItemRequestADM(nodeInUseIri, anythingAdminUser, UUID.randomUUID)).exit.map(
          assert(_)(
            E2EZSpec.failsWithMessageEqualTo[BadRequestException](
              s"Node $nodeInUseIri cannot be deleted, because it is in use.",
            ),
          ),
        )
      },
      test("not delete a node that has a child which is used (node itself not in use, but its child is)") {
        val nodeIri   = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/treeList03")
        val usedChild = "http://rdfh.ch/lists/0001/treeList10"
        listsResponder(_.deleteListItemRequestADM(nodeIri, anythingAdminUser, UUID.randomUUID)).exit.map(
          assert(_)(
            E2EZSpec.failsWithMessageEqualTo[BadRequestException](
              s"Node ${nodeIri.value} cannot be deleted, because its child $usedChild is in use.",
            ),
          ),
        )
      },
      test(
        "not delete a node used as object of salsah-gui:guiAttribute (i.e. 'hlist=<nodeIri>') but not as object of knora-base:valueHasListNode",
      ) {
        val nodeInUseInOntologyIri = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/treeList")
        listsResponder(_.deleteListItemRequestADM(nodeInUseInOntologyIri, anythingAdminUser, UUID.randomUUID)).exit.map(
          assert(_)(
            E2EZSpec.failsWithMessageEqualTo[BadRequestException](
              s"Node ${nodeInUseInOntologyIri.value} cannot be deleted, because it is in use.",
            ),
          ),
        )
      },
      test("delete a middle child node that is not in use") {
        val nodeIri = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/notUsedList012")
        listsResponder(_.deleteListItemRequestADM(nodeIri, anythingAdminUser, UUID.randomUUID))
          .flatMap(expectType[ChildNodeDeleteResponseADM])
          .map(_.node.children)
          .map { remainingChildren =>
            // node List015 should still have its child
            val list015 = remainingChildren.filter(node => node.id == "http://rdfh.ch/lists/0001/notUsedList015").head
            assertTrue(
              remainingChildren.size == 4,
              // Tailing children  ==  shifted to left
              remainingChildren.last.position == 3,
              list015.position == 2,
              list015.children.size == 1,
            )
          }
      },
      test("delete a child node that is not in use") {
        val nodeIri = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/notUsedList02")
        listsResponder(_.deleteListItemRequestADM(nodeIri, anythingAdminUser, UUID.randomUUID))
          .flatMap(expectType[ChildNodeDeleteResponseADM])
          .map(_.node.children)
          .map { remainingChildren =>
            val firstChild = remainingChildren.head
            assertTrue(
              remainingChildren.size == 1,
              firstChild.id == "http://rdfh.ch/lists/0001/notUsedList01",
              firstChild.position == 0,
            )
          }
      },
      test("delete a list (i.e. root node) that is not in use in ontology") {
        val listIri = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/notUsedList")
        listsResponder(_.deleteListItemRequestADM(listIri, anythingAdminUser, UUID.randomUUID))
          .flatMap(expectType[ListDeleteResponseADM])
          .map(actual => assertTrue(actual.iri == listIri.value, actual.deleted))
      },
    ),
    suite("used to query if list can be deleted")(
      test("return FALSE for a node that is in use") {
        val nodeInUseIri = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/treeList01")
        listsResponder(_.canDeleteListRequestADM(nodeInUseIri))
          .map(response => assertTrue(response.listIri == nodeInUseIri.value, !response.canDeleteList))
      },
      test("return FALSE for a node that is unused but has a child which is used") {
        val nodeIri = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/treeList03")
        listsResponder(_.canDeleteListRequestADM(nodeIri))
          .map(response => assertTrue(response.listIri == nodeIri.value, !response.canDeleteList))
      },
      test(
        "return FALSE for a node used as object of salsah-gui:guiAttribute (i.e. 'hlist=<nodeIri>') but not as object of knora-base:valueHasListNode",
      ) {
        val nodeInUseInOntologyIri = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/treeList03")
        listsResponder(_.canDeleteListRequestADM(nodeInUseInOntologyIri))
          .map(response => assertTrue(response.listIri == nodeInUseInOntologyIri.value, !response.canDeleteList))
      },
      test("return TRUE for a middle child node that is not in use") {
        val nodeIri = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/notUsedList012")
        listsResponder(_.canDeleteListRequestADM(nodeIri))
          .map(response => assertTrue(response.listIri == nodeIri.value, response.canDeleteList))
      },
      test("return TRUE for a child node that is not in use") {
        val nodeIri = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/notUsedList02")
        listsResponder(_.canDeleteListRequestADM(nodeIri))
          .map(response => assertTrue(response.listIri == nodeIri.value, response.canDeleteList))
      },
      test("delete a list (i.e. root node) that is not in use in ontology") {
        val listIri = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/notUsedList")
        listsResponder(_.canDeleteListRequestADM(listIri))
          .map(response => assertTrue(response.canDeleteList, response.listIri == listIri.value))
      },
    ),
    suite("used to delete list node comments")(
      test("do not delete a comment of root list node") {
        val listIri = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/testList")
        listsResponder(_.deleteListNodeCommentsADM(listIri)).exit.map(
          assert(_)(
            E2EZSpec.failsWithMessageEqualTo[BadRequestException](
              s"Root node comments cannot be deleted.",
            ),
          ),
        )
      },
      test("delete all comments of child node that contains just one comment") {
        val listIri = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/testList01")
        listsResponder(_.deleteListNodeCommentsADM(listIri)).map(response =>
          assertTrue(
            response.nodeIri == listIri.value,
            response.commentsDeleted,
          ),
        )
      },
      test("delete all comments of child node that contains more than one comment") {
        val listIri = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/testList02")
        listsResponder(_.deleteListNodeCommentsADM(listIri))
          .map(response => assertTrue(response.nodeIri == listIri.value, response.commentsDeleted))
      },
      test("if requested list does not have comments, inform there is no comments to delete") {
        val listIri = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/testList03")
        listsResponder(_.deleteListNodeCommentsADM(listIri)).exit.map(
          assert(_)(
            E2EZSpec.failsWithMessageEqualTo[BadRequestException](
              s"Nothing to delete. Node ${listIri.value} does not have comments.",
            ),
          ),
        )
      },
    ),
  )
}
