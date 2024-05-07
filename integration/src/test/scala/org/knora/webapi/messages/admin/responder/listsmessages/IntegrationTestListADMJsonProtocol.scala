package org.knora.webapi.messages.admin.responder.listsmessages

import org.apache.pekko.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.*
import spray.json.DefaultJsonProtocol
import spray.json.DeserializationException
import spray.json.JsArray
import spray.json.JsObject
import spray.json.JsValue
import spray.json.JsonFormat
import spray.json.RootJsonFormat

import scala.util.Try

import org.knora.webapi.IRI
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralSequenceV2
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.messages.store.triplestoremessages.TriplestoreJsonProtocol

/**
 * A spray-json protocol for generating Knora API V2 JSON providing data about lists.
 */
trait IntegrationTestListADMJsonProtocol
    extends SprayJsonSupport
    with DefaultJsonProtocol
    with TriplestoreJsonProtocol {

  implicit object ListRootNodeInfoFormat extends JsonFormat[ListRootNodeInfoADM] {

    def write(node: ListRootNodeInfoADM): JsValue =
      ListNodeInfoFormat.write(node)

    def read(value: JsValue): ListRootNodeInfoADM =
      ListNodeInfoFormat.read(value).asInstanceOf[ListRootNodeInfoADM]

  }

  implicit object ListChildNodeInfoFormat extends JsonFormat[ListChildNodeInfoADM] {

    def write(node: ListChildNodeInfoADM): JsValue =
      ListNodeInfoFormat.write(node)

    def read(value: JsValue): ListChildNodeInfoADM =
      ListNodeInfoFormat.read(value).asInstanceOf[ListChildNodeInfoADM]

  }

  implicit object ListNodeInfoFormat extends JsonFormat[ListNodeInfoADM] {

    /**
     * Converts a [[ListNodeInfoADM]] to a [[JsValue]].
     *
     * @param nodeInfo a [[ListNodeInfoADM]].
     * @return a [[JsValue]].
     */
    def write(nodeInfo: ListNodeInfoADM): JsValue =
      nodeInfo match {
        case root: ListRootNodeInfoADM =>
          if (root.name.nonEmpty) {
            JsObject(
              "id"         -> root.id.toJson,
              "projectIri" -> root.projectIri.toJson,
              "name"       -> root.name.toJson,
              "labels"     -> JsArray(root.labels.stringLiterals.map(_.toJson)),
              "comments"   -> JsArray(root.comments.stringLiterals.map(_.toJson)),
              "isRootNode" -> true.toJson,
            )
          } else {
            JsObject(
              "id"         -> root.id.toJson,
              "projectIri" -> root.projectIri.toJson,
              "labels"     -> JsArray(root.labels.stringLiterals.map(_.toJson)),
              "comments"   -> JsArray(root.comments.stringLiterals.map(_.toJson)),
              "isRootNode" -> true.toJson,
            )
          }

        case child: ListChildNodeInfoADM =>
          if (child.name.nonEmpty) {
            JsObject(
              "id"          -> child.id.toJson,
              "name"        -> child.name.toJson,
              "labels"      -> JsArray(child.labels.stringLiterals.map(_.toJson)),
              "comments"    -> JsArray(child.comments.stringLiterals.map(_.toJson)),
              "position"    -> child.position.toJson,
              "hasRootNode" -> child.hasRootNode.toJson,
            )
          } else {
            JsObject(
              "id"          -> child.id.toJson,
              "labels"      -> JsArray(child.labels.stringLiterals.map(_.toJson)),
              "comments"    -> JsArray(child.comments.stringLiterals.map(_.toJson)),
              "position"    -> child.position.toJson,
              "hasRootNode" -> child.hasRootNode.toJson,
            )
          }
      }

    /**
     * Converts a [[JsValue]] to a [[ListNodeInfoADM]].
     *
     * @param value a [[JsValue]].
     * @return a [[ListNodeInfoADM]].
     */
    def read(value: JsValue): ListNodeInfoADM = {

      val fields = value.asJsObject.fields

      val id =
        fields.getOrElse("id", throw DeserializationException("The expected field 'id' is missing.")).convertTo[String]
      val name = fields.get("name").map(_.convertTo[String])
      val labels = fields.get("labels") match {
        case Some(JsArray(values)) => values.map(_.convertTo[StringLiteralV2])
        case None                  => Seq.empty[StringLiteralV2]
        case _                     => throw DeserializationException("The expected field 'labels' is in the wrong format.")
      }

      val comments = fields.get("comments") match {
        case Some(JsArray(values)) => values.map(_.convertTo[StringLiteralV2])
        case None                  => Seq.empty[StringLiteralV2]
        case _                     => throw DeserializationException("The expected field 'comments' is in the wrong format.")
      }

      val maybePosition: Option[Int] = fields.get("position").map(_.convertTo[Int])

      val maybeHasRootNode: Option[IRI] = fields.get("hasRootNode").map(_.convertTo[String])

      val maybeIsRootNode: Option[Boolean] = fields.get("isRootNode").map(_.convertTo[Boolean])

      val isRootNode = maybeIsRootNode.getOrElse(false)

      val maybeProjectIri: Option[IRI] = fields.get("projectIri").map(_.convertTo[IRI])

      if (isRootNode) {
        ListRootNodeInfoADM(
          id = id,
          projectIri = maybeProjectIri.getOrElse(throw DeserializationException("The project IRI is not defined.")),
          name = name,
          labels = StringLiteralSequenceV2(labels.toVector),
          comments = StringLiteralSequenceV2(comments.toVector),
        )
      } else {
        ListChildNodeInfoADM(
          id = id,
          name = name,
          labels = StringLiteralSequenceV2(labels.toVector),
          comments = StringLiteralSequenceV2(comments.toVector),
          position = maybePosition.getOrElse(throw DeserializationException("The position is not defined.")),
          hasRootNode = maybeHasRootNode.getOrElse(throw DeserializationException("The root node is not defined.")),
        )
      }
    }
  }

  implicit object ListRootNodeFormat extends JsonFormat[ListRootNodeADM] {
    def write(node: ListRootNodeADM): JsValue = listNodeADMFormat.write(node)
    def read(value: JsValue): ListRootNodeADM = listNodeADMFormat.read(value).asInstanceOf[ListRootNodeADM]
  }
  implicit object ListChildNodeFormat extends JsonFormat[ListChildNodeADM] {
    def write(node: ListChildNodeADM): JsValue = listNodeADMFormat.write(node)
    def read(value: JsValue): ListChildNodeADM = listNodeADMFormat.read(value).asInstanceOf[ListChildNodeADM]
  }

  implicit val listNodeADMFormat: JsonFormat[ListNodeADM] = new RootJsonFormat[ListNodeADM] {

    /**
     * Converts a [[ListNodeADM]] to a [[JsValue]].
     *
     * @param node a [[ListNodeADM]].
     * @return a [[JsValue]].
     */
    override def write(node: ListNodeADM): JsValue =
      node match {
        case root: ListRootNodeADM =>
          JsObject(
            "id"         -> root.id.toJson,
            "projectIri" -> root.projectIri.toJson,
            "name"       -> root.name.toJson,
            "labels"     -> JsArray(root.labels.stringLiterals.map(_.toJson)),
            "comments"   -> JsArray(root.comments.stringLiterals.map(_.toJson)),
            "isRootNode" -> true.toJson,
            "children"   -> JsArray(root.children.map(write).toVector),
          )

        case child: ListChildNodeADM =>
          JsObject(
            "id"          -> child.id.toJson,
            "name"        -> child.name.toJson,
            "labels"      -> JsArray(child.labels.stringLiterals.map(_.toJson)),
            "comments"    -> JsArray(child.comments.stringLiterals.map(_.toJson)),
            "position"    -> child.position.toJson,
            "hasRootNode" -> child.hasRootNode.toJson,
            "children"    -> JsArray(child.children.map(write).toVector),
          )
      }

    /**
     * Converts a [[JsValue]] to a [[ListNodeADM]].
     *
     * @param value a [[JsValue]].
     * @return a [[ListNodeADM]].
     */
    override def read(value: JsValue): ListNodeADM = {

      val fields = value.asJsObject.fields

      val id =
        fields.getOrElse("id", throw DeserializationException("The expected field 'id' is missing.")).convertTo[String]
      val name = fields.get("name").map(_.convertTo[String])
      val labels = fields.get("labels") match {
        case Some(JsArray(values)) => values.map(_.convertTo[StringLiteralV2])
        case None                  => Seq.empty[StringLiteralV2]
        case _                     => throw DeserializationException("The expected field 'labels' is in the wrong format.")
      }

      val comments = fields.get("comments") match {
        case Some(JsArray(values)) => values.map(_.convertTo[StringLiteralV2])
        case None                  => Seq.empty[StringLiteralV2]
        case _                     => throw DeserializationException("The expected field 'comments' is in the wrong format.")
      }

      val children: Seq[ListChildNodeADM] = fields.get("children") match {
        case Some(JsArray(values)) => values.map(read).map(_.asInstanceOf[ListChildNodeADM])
        case None                  => Seq.empty[ListChildNodeADM]
        case _                     => throw DeserializationException("The expected field 'children' is in the wrong format.")
      }

      val maybePosition: Option[Int] = fields.get("position").map(_.convertTo[Int])

      val maybeHasRootNode: Option[IRI] = fields.get("hasRootNode").map(_.convertTo[String])

      val maybeIsRootNode: Option[Boolean] = fields.get("isRootNode").map(_.convertTo[Boolean])

      val isRootNode = maybeIsRootNode.getOrElse(false)

      val maybeProjectIri: Option[IRI] = fields.get("projectIri").map(_.convertTo[IRI])

      if (isRootNode) {
        ListRootNodeADM(
          id = id,
          projectIri = maybeProjectIri.getOrElse(throw DeserializationException("The project IRI is not defined.")),
          name = name,
          labels = StringLiteralSequenceV2(labels.toVector),
          comments = StringLiteralSequenceV2(comments.toVector),
          children = children,
        )
      } else {
        ListChildNodeADM(
          id = id,
          name = name,
          labels = StringLiteralSequenceV2(labels.toVector),
          comments = StringLiteralSequenceV2(comments.toVector),
          position = maybePosition.getOrElse(throw DeserializationException("The position is not defined.")),
          hasRootNode = maybeHasRootNode.getOrElse(throw DeserializationException("The root node is not defined.")),
          children = children,
        )
      }
    }
  }

  implicit object ListFormat extends JsonFormat[ListADM] {

    /**
     * Converts a [[ListADM]] to a [[JsValue]].
     *
     * @param list a [[ListADM]].
     * @return a [[JsValue]].
     */
    def write(list: ListADM): JsValue =
      JsObject(
        "listinfo" -> list.listinfo.toJson,
        "children" -> JsArray(list.children.map(_.toJson).toVector),
      )

    /**
     * Converts a [[JsValue]] to a [[List]].
     *
     * @param value a [[JsValue]].
     * @return a [[List]].
     */
    def read(value: JsValue): ListADM = {

      val fields = value.asJsObject.fields

      val listinfo: ListRootNodeInfoADM = fields
        .getOrElse("listinfo", throw DeserializationException("The expected field 'listinfo' is missing."))
        .convertTo[ListRootNodeInfoADM]
      val children: Seq[ListChildNodeADM] = fields.get("children") match {
        case Some(JsArray(values)) => values.map(it => ListChildNodeFormat.read(it))
        case None                  => Seq.empty[ListChildNodeADM]
        case _                     => throw DeserializationException("The expected field 'children' is in the wrong format.")
      }

      ListADM(
        listinfo = listinfo,
        children = children,
      )
    }
  }

  implicit object SublistFormat extends JsonFormat[NodeADM] {

    /**
     * Converts a [[NodeADM]] to a [[JsValue]].
     *
     * @param node a [[NodeADM]].
     * @return a [[JsValue]].
     */
    def write(node: NodeADM): JsValue =
      JsObject(
        "nodeinfo" -> node.nodeinfo.toJson,
        "children" -> JsArray(node.children.map(_.toJson).toVector),
      )

    /**
     * Converts a [[JsValue]] to a [[NodeADM]].
     *
     * @param value a [[JsValue]].
     * @return a [[NodeADM]].
     */
    def read(value: JsValue): NodeADM = {

      val fields = value.asJsObject.fields

      val nodeinfo: ListChildNodeInfoADM = fields
        .getOrElse("nodeinfo", throw DeserializationException("The expected field 'nodeinfo' is missing."))
        .convertTo[ListChildNodeInfoADM]
      val children: Seq[ListChildNodeADM] = fields.get("children") match {
        case Some(JsArray(values)) => values.map(ListChildNodeFormat.read(_))
        case None                  => Seq.empty[ListChildNodeADM]
        case _                     => throw DeserializationException("The expected field 'children' is in the wrong format.")
      }

      NodeADM(
        nodeinfo = nodeinfo,
        children = children,
      )
    }
  }

  implicit val listsGetResponseADMFormat: RootJsonFormat[ListsGetResponseADM] =
    jsonFormat(ListsGetResponseADM.apply, "lists")

  implicit val listItemGetResponseADMJsonFormat: RootJsonFormat[ListItemGetResponseADM] =
    new RootJsonFormat[ListItemGetResponseADM] {
      override def write(obj: ListItemGetResponseADM): JsValue = obj match {
        case list: ListGetResponseADM     => listGetResponseADMFormat.write(list)
        case node: ListNodeGetResponseADM => listNodeGetResponseADMFormat.write(node)
      }
      override def read(json: JsValue): ListItemGetResponseADM =
        Try(listGetResponseADMFormat.read(json))
          .getOrElse(listNodeGetResponseADMFormat.read(json))
    }
  implicit val listGetResponseADMFormat: RootJsonFormat[ListGetResponseADM] =
    jsonFormat(ListGetResponseADM.apply, "list")
  implicit val listNodeGetResponseADMFormat: RootJsonFormat[ListNodeGetResponseADM] =
    jsonFormat(ListNodeGetResponseADM.apply, "node")

  implicit val nodeInfoGetResponseADMJsonFormat: RootJsonFormat[NodeInfoGetResponseADM] =
    new RootJsonFormat[NodeInfoGetResponseADM] {
      override def write(obj: NodeInfoGetResponseADM): JsValue = obj match {
        case root: RootNodeInfoGetResponseADM  => listInfoGetResponseADMFormat.write(root)
        case node: ChildNodeInfoGetResponseADM => listNodeInfoGetResponseADMFormat.write(node)
      }
      override def read(json: JsValue): NodeInfoGetResponseADM =
        Try(listInfoGetResponseADMFormat.read(json))
          .getOrElse(listNodeInfoGetResponseADMFormat.read(json))
    }
  implicit val listInfoGetResponseADMFormat: RootJsonFormat[RootNodeInfoGetResponseADM] =
    jsonFormat(RootNodeInfoGetResponseADM.apply, "listinfo")
  implicit val listNodeInfoGetResponseADMFormat: RootJsonFormat[ChildNodeInfoGetResponseADM] =
    jsonFormat(ChildNodeInfoGetResponseADM.apply, "nodeinfo")

  implicit val changeNodePositionApiResponseADMFormat: RootJsonFormat[NodePositionChangeResponseADM] =
    jsonFormat(NodePositionChangeResponseADM.apply, "node")

  implicit val listItemDeleteResponseADMFormat: RootJsonFormat[ListItemDeleteResponseADM] =
    new RootJsonFormat[ListItemDeleteResponseADM] {
      override def write(obj: ListItemDeleteResponseADM): JsValue = obj match {
        case list: ListDeleteResponseADM      => listDeleteResponseADMFormat.write(list)
        case node: ChildNodeDeleteResponseADM => listNodeDeleteResponseADMFormat.write(node)
      }
      override def read(json: JsValue): ListItemDeleteResponseADM =
        Try(listDeleteResponseADMFormat.read(json))
          .getOrElse(listNodeDeleteResponseADMFormat.read(json))
    }
  implicit val listNodeDeleteResponseADMFormat: RootJsonFormat[ChildNodeDeleteResponseADM] =
    jsonFormat(ChildNodeDeleteResponseADM.apply, "node")
  implicit val listDeleteResponseADMFormat: RootJsonFormat[ListDeleteResponseADM] =
    jsonFormat(ListDeleteResponseADM.apply, "iri", "deleted")

  implicit val canDeleteListResponseADMFormat: RootJsonFormat[CanDeleteListResponseADM] =
    jsonFormat(CanDeleteListResponseADM.apply, "listIri", "canDeleteList")
  implicit val ListNodeCommentsDeleteResponseADMFormat: RootJsonFormat[ListNodeCommentsDeleteResponseADM] =
    jsonFormat(ListNodeCommentsDeleteResponseADM.apply, "nodeIri", "commentsDeleted")
}
