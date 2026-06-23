/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.v2.responder.listsmessages

import org.knora.webapi.*
import org.knora.webapi.config.AppConfig
import org.knora.webapi.messages.IriConversions.*
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.listsmessages.*
import org.knora.webapi.messages.store.triplestoremessages.LanguageTaggedStringLiteralV2
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralSequenceV2
import org.knora.webapi.messages.util.rdf
import org.knora.webapi.messages.util.rdf.*
import org.knora.webapi.messages.v2.responder.KnoraJsonLDResponseV2

/**
 * An abstract trait providing a convenience method for language handling.
 */
trait ListResponderResponseV2 {

  /**
   * Collapses a [[StringLiteralSequenceV2]] into a map from BCP-47 language tag to value.
   * Untagged literals (`PlainStringLiteralV2`) are skipped (REQ-1.8, D5). When multiple
   * literals share the same language tag, the last one wins via `.toMap` (REQ-1.9, D6).
   */
  private[listsmessages] def stringLiteralsToLangMap(seq: StringLiteralSequenceV2): Map[String, String] =
    seq.stringLiterals.collect { case LanguageTaggedStringLiteralV2(value, language) =>
      language.value -> value
    }.toMap

  /**
   * Builds the JSON-LD value for an `rdfs:label` or `rdfs:comment` field, dispatching on
   * the `allLanguages` flag.
   *
   *   - When `allLanguages` is `true`, returns a [[JsonLDArray]] of language-tagged objects
   *     (sort by language tag is delegated to `JsonLDUtil.objectsWithLangsToJsonLDArray` —
   *     REQ-1.7, D3) or `None` when the underlying sequence has no language-tagged literals
   *     (REQ-1.6, D2 — field is then omitted from the surrounding `JsonLDObject` via `++ Option`).
   *   - When `allLanguages` is `false`, returns the legacy single [[JsonLDString]] picked by
   *     the user's preferred language with the configured fallback (REQ-1.10, D8 unchanged).
   */
  private[listsmessages] def labelOrCommentJson(
    seq: StringLiteralSequenceV2,
    allLanguages: Boolean,
    userLang: String,
    fallbackLang: String,
  ): Option[JsonLDValue] =
    if (allLanguages) {
      val tagged = stringLiteralsToLangMap(seq)
      Option.when(tagged.nonEmpty)(JsonLDUtil.objectsWithLangsToJsonLDArray(tagged))
    } else {
      seq.getPreferredLanguage(userLang, fallbackLang).map(JsonLDString.apply)
    }

}

/**
 * @param list         the list to be returned.
 * @param userLang     the user's preferred language.
 * @param fallbackLang the fallback language if the preferred language is not available.
 */
case class ListGetResponseV2(
  list: ListADM,
  userLang: String,
  fallbackLang: String,
  allLanguages: Boolean = false,
) extends KnoraJsonLDResponseV2
    with ListResponderResponseV2 {

  def toJsonLDDocument(
    targetSchema: ApiV2Schema,
    appConfig: AppConfig,
    schemaOptions: Set[Rendering],
  ): JsonLDDocument = {

    implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    /**
     * Given a [[ListNodeADM]], constructs a [[JsonLDObject]].
     *
     * @param node the node to be turned into JSON-LD.
     * @return a [[JsonLDObject]] representing the node.
     */
    def makeNode(node: ListChildNodeADM): JsonLDObject = {
      val label: Option[(IRI, JsonLDValue)] =
        labelOrCommentJson(node.labels, allLanguages, userLang, fallbackLang)
          .map(OntologyConstants.Rdfs.Label -> _)
      val comment: Option[(IRI, JsonLDValue)] =
        labelOrCommentJson(node.comments, allLanguages, userLang, fallbackLang)
          .map(OntologyConstants.Rdfs.Comment -> _)

      val position: Map[IRI, JsonLDInt] = Map(
        OntologyConstants.KnoraBase.ListNodePosition.toSmartIri.toOntologySchema(ApiV2Complex).toString -> JsonLDInt(
          node.position,
        ),
      )

      val children: Map[IRI, JsonLDArray] = if (node.children.nonEmpty) {
        Map(
          OntologyConstants.KnoraBase.HasSubListNode.toSmartIri.toOntologySchema(ApiV2Complex).toString -> JsonLDArray(
            node.children.map { (childNode: ListChildNodeADM) =>
              makeNode(childNode) // recursion
            },
          ),
        )
      } else {
        // no children (abort condition for recursion)
        Map.empty[IRI, JsonLDArray]
      }

      val nodeHasRootNode: Map[IRI, JsonLDObject] = Map(
        OntologyConstants.KnoraBase.HasRootNode.toSmartIri.toOntologySchema(ApiV2Complex).toString -> JsonLDUtil
          .iriToJsonLDObject(node.hasRootNode),
      )

      JsonLDObject(
        Map[IRI, JsonLDValue](
          "@id"   -> JsonLDString(node.id),
          "@type" -> JsonLDString(
            OntologyConstants.KnoraBase.ListNode.toSmartIri.toOntologySchema(ApiV2Complex).toString,
          ),
        ) ++ position ++ nodeHasRootNode ++ children ++ label ++ comment,
      )
    }

    val listinfo = list.listinfo

    val label: Option[(IRI, JsonLDValue)] =
      labelOrCommentJson(listinfo.labels, allLanguages, userLang, fallbackLang)
        .map(OntologyConstants.Rdfs.Label -> _)

    val comment: Option[(IRI, JsonLDValue)] =
      labelOrCommentJson(listinfo.comments, allLanguages, userLang, fallbackLang)
        .map(OntologyConstants.Rdfs.Comment -> _)

    val children: Map[IRI, JsonLDArray] = if (list.children.nonEmpty) {
      Map(
        OntologyConstants.KnoraBase.HasSubListNode.toSmartIri.toOntologySchema(ApiV2Complex).toString -> JsonLDArray(
          list.children.map { (childNode: ListNodeADM) =>
            makeNode(childNode.asInstanceOf[ListChildNodeADM])
          },
        ),
      )
    } else {
      Map.empty[IRI, JsonLDArray]
    }

    val project: Map[IRI, JsonLDObject] = Map(
      OntologyConstants.KnoraBase.AttachedToProject.toSmartIri.toOntologySchema(ApiV2Complex).toString -> JsonLDUtil
        .iriToJsonLDObject(listinfo.projectIri),
    )

    val body = rdf.JsonLDObject(
      Map[IRI, JsonLDValue](
        "@id"   -> JsonLDString(listinfo.id),
        "@type" -> JsonLDString(
          OntologyConstants.KnoraBase.ListNode.toSmartIri.toOntologySchema(ApiV2Complex).toString,
        ),
        OntologyConstants.KnoraBase.IsRootNode.toSmartIri.toOntologySchema(ApiV2Complex).toString -> JsonLDBoolean(true),
      ) ++ project ++ children ++ label ++ comment,
    )

    val context = JsonLDObject(
      Map(
        OntologyConstants.KnoraApi.KnoraApiOntologyLabel -> JsonLDString(
          OntologyConstants.KnoraApiV2Complex.KnoraApiV2PrefixExpansion,
        ),
        "rdfs" -> JsonLDString("http://www.w3.org/2000/01/rdf-schema#"),
        "rdf"  -> JsonLDString("http://www.w3.org/1999/02/22-rdf-syntax-ns#"),
        "owl"  -> JsonLDString("http://www.w3.org/2002/07/owl#"),
        "xsd"  -> JsonLDString("http://www.w3.org/2001/XMLSchema#"),
      ),
    )

    JsonLDDocument(body, context)
  }
}

/**
 * @param node         the node to be returned.
 * @param userLang     the user's preferred language.
 * @param fallbackLang the fallback language if the preferred language is not available.
 */
case class NodeGetResponseV2(
  node: ListNodeInfoADM,
  userLang: String,
  fallbackLang: String,
  allLanguages: Boolean = false,
) extends KnoraJsonLDResponseV2
    with ListResponderResponseV2 {

  def toJsonLDDocument(
    targetSchema: ApiV2Schema,
    appConfig: AppConfig,
    schemaOptions: Set[Rendering],
  ): JsonLDDocument = {

    implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    val body: JsonLDObject = node match {

      case root: ListRootNodeInfoADM => {

        val label: Option[(IRI, JsonLDValue)] =
          labelOrCommentJson(root.labels, allLanguages, userLang, fallbackLang)
            .map(OntologyConstants.Rdfs.Label -> _)

        val comment: Option[(IRI, JsonLDValue)] =
          labelOrCommentJson(root.comments, allLanguages, userLang, fallbackLang)
            .map(OntologyConstants.Rdfs.Comment -> _)

        val position: Map[IRI, JsonLDInt] = Map.empty[IRI, JsonLDInt]

        val rootNode = Map.empty[IRI, JsonLDValue]

        JsonLDObject(
          Map[IRI, JsonLDValue](
            "@id"   -> JsonLDString(root.id),
            "@type" -> JsonLDString(
              OntologyConstants.KnoraBase.ListNode.toSmartIri.toOntologySchema(ApiV2Complex).toString,
            ),
          ) ++ rootNode ++ label ++ comment ++ position,
        )
      }

      case child: ListChildNodeInfoADM => {

        val label: Option[(IRI, JsonLDValue)] =
          labelOrCommentJson(child.labels, allLanguages, userLang, fallbackLang)
            .map(OntologyConstants.Rdfs.Label -> _)

        val comment: Option[(IRI, JsonLDValue)] =
          labelOrCommentJson(child.comments, allLanguages, userLang, fallbackLang)
            .map(OntologyConstants.Rdfs.Comment -> _)

        val position: Map[IRI, JsonLDInt] = Map(
          OntologyConstants.KnoraBase.ListNodePosition.toSmartIri.toOntologySchema(ApiV2Complex).toString -> JsonLDInt(
            child.position,
          ),
        )

        val rootNode = Map(
          OntologyConstants.KnoraBase.HasRootNode.toSmartIri.toOntologySchema(ApiV2Complex).toString -> JsonLDUtil
            .iriToJsonLDObject(child.hasRootNode),
        )

        JsonLDObject(
          Map[IRI, JsonLDValue](
            "@id"   -> JsonLDString(child.id),
            "@type" -> JsonLDString(
              OntologyConstants.KnoraBase.ListNode.toSmartIri.toOntologySchema(ApiV2Complex).toString,
            ),
          ) ++ rootNode ++ label ++ comment ++ position,
        )
      }
    }

    val context = JsonLDObject(
      Map(
        OntologyConstants.KnoraApi.KnoraApiOntologyLabel -> JsonLDString(
          OntologyConstants.KnoraApiV2Complex.KnoraApiV2PrefixExpansion,
        ),
        "rdfs" -> JsonLDString("http://www.w3.org/2000/01/rdf-schema#"),
        "rdf"  -> JsonLDString("http://www.w3.org/1999/02/22-rdf-syntax-ns#"),
        "owl"  -> JsonLDString("http://www.w3.org/2002/07/owl#"),
        "xsd"  -> JsonLDString("http://www.w3.org/2001/XMLSchema#"),
      ),
    )

    JsonLDDocument(body, context)
  }

}
