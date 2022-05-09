/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.v2.responder.listsmessages

import org.knora.webapi._
import org.knora.webapi.feature.FeatureFactoryConfig
import org.knora.webapi.messages.IriConversions._
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.listsmessages._
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralSequenceV2
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.messages.util.rdf
import org.knora.webapi.messages.util.rdf._
import org.knora.webapi.messages.v2.responder.KnoraJsonLDResponseV2
import org.knora.webapi.messages.v2.responder.KnoraRequestV2
import org.knora.webapi.settings.KnoraSettingsImpl

/**
 * An abstract trait representing a Knora v2 API request message that can be sent to `ListsResponderV2`.
 */
sealed trait ListsResponderRequestV2 extends KnoraRequestV2

/**
 * Requests a list. A successful response will be a [[ListGetResponseV2]]
 *
 * @param listIri              the IRI of the list (Iri of the list's root node).
 * @param featureFactoryConfig the feature factory configuration.
 * @param requestingUser       the user making the request.
 */
case class ListGetRequestV2(listIri: IRI, featureFactoryConfig: FeatureFactoryConfig, requestingUser: UserADM)
    extends ListsResponderRequestV2

/**
 * An abstract trait providing a convenience method for language handling.
 */
trait ListResponderResponseV2 {

  /**
   * Given an Iri and a [[StringLiteralSequenceV2]], gets he string value in the user's preferred language.
   *
   * @param iri          the Iri pointing to the string value.
   * @param stringVals   the string values to choose from.
   * @param userLang     the user's preferred language.
   * @param fallbackLang the fallback language if the preferred language is not available.
   * @return a [[Map[IRI, JsonLDString]] (empty in case no string value is available).
   */
  def makeMapIriToJSONLDString(
    iri: IRI,
    stringVals: StringLiteralSequenceV2,
    userLang: String,
    fallbackLang: String
  ): Map[IRI, JsonLDString] =
    Map(
      iri -> stringVals.getPreferredLanguage(userLang, fallbackLang)
    ).collect { case (iri: IRI, Some(strVal: String)) =>
      iri -> JsonLDString(strVal)
    }

}

/**
 * Represents a response to a [[ListGetRequestV2]].
 *
 * @param list         the list to be returned.
 * @param userLang     the user's preferred language.
 * @param fallbackLang the fallback language if the preferred language is not available.
 */
case class ListGetResponseV2(list: ListADM, userLang: String, fallbackLang: String)
    extends KnoraJsonLDResponseV2
    with ListResponderResponseV2 {

  def toJsonLDDocument(
    targetSchema: ApiV2Schema,
    settings: KnoraSettingsImpl,
    schemaOptions: Set[SchemaOption]
  ): JsonLDDocument = {

    implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    /**
     * Given a [[ListNodeADM]], constructs a [[JsonLDObject]].
     *
     * @param node the node to be turned into JSON-LD.
     * @return a [[JsonLDObject]] representing the node.
     */
    def makeNode(node: ListChildNodeADM): JsonLDObject = {
      val label: Map[IRI, JsonLDString] =
        makeMapIriToJSONLDString(OntologyConstants.Rdfs.Label, node.labels, userLang, fallbackLang)
      val comment: Map[IRI, JsonLDString] = {
        makeMapIriToJSONLDString(
          OntologyConstants.Rdfs.Comment,
          node.comments.getOrElse(StringLiteralSequenceV2(Vector.empty[StringLiteralV2])),
          userLang,
          fallbackLang
        )
      }

      val position: Map[IRI, JsonLDInt] = Map(
        OntologyConstants.KnoraBase.ListNodePosition.toSmartIri.toOntologySchema(ApiV2Complex).toString -> JsonLDInt(
          node.position
        )
      )

      val children: Map[IRI, JsonLDArray] = if (node.children.nonEmpty) {
        Map(
          OntologyConstants.KnoraBase.HasSubListNode.toSmartIri.toOntologySchema(ApiV2Complex).toString -> JsonLDArray(
            node.children.map { childNode: ListChildNodeADM =>
              makeNode(childNode) // recursion
            }
          )
        )
      } else {
        // no children (abort condition for recursion)
        Map.empty[IRI, JsonLDArray]
      }

      val nodeHasRootNode: Map[IRI, JsonLDObject] = Map(
        OntologyConstants.KnoraBase.HasRootNode.toSmartIri.toOntologySchema(ApiV2Complex).toString -> JsonLDUtil
          .iriToJsonLDObject(node.hasRootNode)
      )

      JsonLDObject(
        Map(
          "@id" -> JsonLDString(node.id),
          "@type" -> JsonLDString(
            OntologyConstants.KnoraBase.ListNode.toSmartIri.toOntologySchema(ApiV2Complex).toString
          )
        ) ++ position ++ nodeHasRootNode ++ children ++ label ++ comment
      )
    }

    val listinfo = list.listinfo

    val label: Map[IRI, JsonLDString] =
      makeMapIriToJSONLDString(OntologyConstants.Rdfs.Label, listinfo.labels, userLang, fallbackLang)

    val comment: Map[IRI, JsonLDString] =
      makeMapIriToJSONLDString(OntologyConstants.Rdfs.Comment, listinfo.comments, userLang, fallbackLang)

    val children: Map[IRI, JsonLDArray] = if (list.children.nonEmpty) {
      Map(
        OntologyConstants.KnoraBase.HasSubListNode.toSmartIri.toOntologySchema(ApiV2Complex).toString -> JsonLDArray(
          list.children.map { childNode: ListNodeADM =>
            makeNode(childNode.asInstanceOf[ListChildNodeADM])
          }
        )
      )
    } else {
      Map.empty[IRI, JsonLDArray]
    }

    val project: Map[IRI, JsonLDObject] = Map(
      OntologyConstants.KnoraBase.AttachedToProject.toSmartIri.toOntologySchema(ApiV2Complex).toString -> JsonLDUtil
        .iriToJsonLDObject(listinfo.projectIri)
    )

    val body = rdf.JsonLDObject(
      Map(
        "@id" -> JsonLDString(listinfo.id),
        "@type" -> JsonLDString(
          OntologyConstants.KnoraBase.ListNode.toSmartIri.toOntologySchema(ApiV2Complex).toString
        ),
        OntologyConstants.KnoraBase.IsRootNode.toSmartIri.toOntologySchema(ApiV2Complex).toString -> JsonLDBoolean(true)
      ) ++ project ++ children ++ label ++ comment
    )

    val context = JsonLDObject(
      Map(
        OntologyConstants.KnoraApi.KnoraApiOntologyLabel -> JsonLDString(
          OntologyConstants.KnoraApiV2Complex.KnoraApiV2PrefixExpansion
        ),
        "rdfs" -> JsonLDString("http://www.w3.org/2000/01/rdf-schema#"),
        "rdf"  -> JsonLDString("http://www.w3.org/1999/02/22-rdf-syntax-ns#"),
        "owl"  -> JsonLDString("http://www.w3.org/2002/07/owl#"),
        "xsd"  -> JsonLDString("http://www.w3.org/2001/XMLSchema#")
      )
    )

    JsonLDDocument(body, context)
  }
}

/**
 * Requests a list node. A successful response will be a [[NodeGetResponseV2]]
 *
 * @param nodeIri              the IRI of the node to retrieve.
 * @param featureFactoryConfig the feature factory configuration.
 */
case class NodeGetRequestV2(nodeIri: IRI, featureFactoryConfig: FeatureFactoryConfig, requestingUser: UserADM)
    extends ListsResponderRequestV2

/**
 * Represents a response to a [[NodeGetRequestV2]].
 *
 * @param node         the node to be returned.
 * @param userLang     the user's preferred language.
 * @param fallbackLang the fallback language if the preferred language is not available.
 */
case class NodeGetResponseV2(node: ListNodeInfoADM, userLang: String, fallbackLang: String)
    extends KnoraJsonLDResponseV2
    with ListResponderResponseV2 {

  def toJsonLDDocument(
    targetSchema: ApiV2Schema,
    settings: KnoraSettingsImpl,
    schemaOptions: Set[SchemaOption]
  ): JsonLDDocument = {

    implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    val body: JsonLDObject = node match {

      case root: ListRootNodeInfoADM => {

        val label: Map[IRI, JsonLDString] =
          makeMapIriToJSONLDString(OntologyConstants.Rdfs.Label, root.labels, userLang, fallbackLang)

        val comment: Map[IRI, JsonLDString] =
          makeMapIriToJSONLDString(OntologyConstants.Rdfs.Comment, root.comments, userLang, fallbackLang)

        val position: Map[IRI, JsonLDInt] = Map.empty[IRI, JsonLDInt]

        val rootNode = Map.empty[IRI, JsonLDString]

        JsonLDObject(
          Map(
            "@id" -> JsonLDString(root.id),
            "@type" -> JsonLDString(
              OntologyConstants.KnoraBase.ListNode.toSmartIri.toOntologySchema(ApiV2Complex).toString
            )
          ) ++ rootNode ++ label ++ comment ++ position
        )
      }

      case child: ListChildNodeInfoADM => {

        val label: Map[IRI, JsonLDString] =
          makeMapIriToJSONLDString(OntologyConstants.Rdfs.Label, child.labels, userLang, fallbackLang)

        val comment: Map[IRI, JsonLDString] =
          makeMapIriToJSONLDString(OntologyConstants.Rdfs.Comment, child.comments, userLang, fallbackLang)

        val position: Map[IRI, JsonLDInt] = Map(
          OntologyConstants.KnoraBase.ListNodePosition.toSmartIri.toOntologySchema(ApiV2Complex).toString -> JsonLDInt(
            child.position
          )
        )

        val rootNode = Map(
          OntologyConstants.KnoraBase.HasRootNode.toSmartIri.toOntologySchema(ApiV2Complex).toString -> JsonLDUtil
            .iriToJsonLDObject(child.hasRootNode)
        )

        JsonLDObject(
          Map(
            "@id" -> JsonLDString(child.id),
            "@type" -> JsonLDString(
              OntologyConstants.KnoraBase.ListNode.toSmartIri.toOntologySchema(ApiV2Complex).toString
            )
          ) ++ rootNode ++ label ++ comment ++ position
        )
      }
    }

    val context = JsonLDObject(
      Map(
        OntologyConstants.KnoraApi.KnoraApiOntologyLabel -> JsonLDString(
          OntologyConstants.KnoraApiV2Complex.KnoraApiV2PrefixExpansion
        ),
        "rdfs" -> JsonLDString("http://www.w3.org/2000/01/rdf-schema#"),
        "rdf"  -> JsonLDString("http://www.w3.org/1999/02/22-rdf-syntax-ns#"),
        "owl"  -> JsonLDString("http://www.w3.org/2002/07/owl#"),
        "xsd"  -> JsonLDString("http://www.w3.org/2001/XMLSchema#")
      )
    )

    JsonLDDocument(body, context)
  }

}
