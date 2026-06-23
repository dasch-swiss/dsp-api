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
import org.knora.webapi.messages.util.rdf.*
import org.knora.webapi.messages.v2.responder.KnoraJsonLDResponseV2

/**
 * Cached `ApiV2Complex` IRI strings and the shared JSON-LD context object used by both
 * list responders. Lifted out of the `toJsonLDDocument` bodies so the SmartIri
 * conversions and the context object are constructed once per JVM.
 */
private[listsmessages] object ListV2Iris {
  private implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

  val listNodeType: IRI     = OntologyConstants.KnoraBase.ListNode.toSmartIri.toOntologySchema(ApiV2Complex).toString
  val listNodePosition: IRI =
    OntologyConstants.KnoraBase.ListNodePosition.toSmartIri.toOntologySchema(ApiV2Complex).toString
  val hasSubListNode: IRI =
    OntologyConstants.KnoraBase.HasSubListNode.toSmartIri.toOntologySchema(ApiV2Complex).toString
  val hasRootNode: IRI       = OntologyConstants.KnoraBase.HasRootNode.toSmartIri.toOntologySchema(ApiV2Complex).toString
  val attachedToProject: IRI =
    OntologyConstants.KnoraBase.AttachedToProject.toSmartIri.toOntologySchema(ApiV2Complex).toString
  val isRootNode: IRI = OntologyConstants.KnoraBase.IsRootNode.toSmartIri.toOntologySchema(ApiV2Complex).toString

  val sharedContext: JsonLDObject = JsonLDObject(
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
}

/**
 * An abstract trait providing convenience methods for the language-handling shape of
 * `rdfs:label` and `rdfs:comment` in list/node responses.
 */
trait ListResponderResponseV2 {

  /**
   * Builds the optional `(rdfs:label, JsonLDValue)` entry for a node, dispatching on
   * `allLanguages`. Returns `None` when there is no label to emit (caller drops the
   * field via `++ Option`).
   */
  private[listsmessages] def labelEntry(
    seq: StringLiteralSequenceV2,
    allLanguages: Boolean,
    userLang: String,
    fallbackLang: String,
  ): Option[(IRI, JsonLDValue)] =
    labelOrCommentJson(seq, allLanguages, userLang, fallbackLang).map(OntologyConstants.Rdfs.Label -> _)

  /**
   * Builds the optional `(rdfs:comment, JsonLDValue)` entry for a node — same dispatch
   * rules as [[labelEntry]].
   */
  private[listsmessages] def commentEntry(
    seq: StringLiteralSequenceV2,
    allLanguages: Boolean,
    userLang: String,
    fallbackLang: String,
  ): Option[(IRI, JsonLDValue)] =
    labelOrCommentJson(seq, allLanguages, userLang, fallbackLang).map(OntologyConstants.Rdfs.Comment -> _)

  /**
   * Builds the JSON-LD value for an `rdfs:label` or `rdfs:comment` field:
   *
   *   - `allLanguages=true`: returns a [[JsonLDArray]] of language-tagged objects (sort
   *     is delegated to `JsonLDUtil.objectsWithLangsToJsonLDArray`). Untagged literals
   *     are skipped; on repeated tags last-wins via `.toMap`. Returns `None` when no
   *     language-tagged literals are present so the field can be omitted entirely.
   *   - `allLanguages=false`: returns the legacy single [[JsonLDString]] picked by the
   *     user's preferred language with the configured fallback (unchanged behaviour).
   */
  private[listsmessages] def labelOrCommentJson(
    seq: StringLiteralSequenceV2,
    allLanguages: Boolean,
    userLang: String,
    fallbackLang: String,
  ): Option[JsonLDValue] =
    if (allLanguages) {
      val tagged = seq.stringLiterals.collect { case LanguageTaggedStringLiteralV2(value, language) =>
        language.value -> value
      }.toMap
      Option.when(tagged.nonEmpty)(JsonLDUtil.objectsWithLangsToJsonLDArray(tagged))
    } else {
      seq.getPreferredLanguage(userLang, fallbackLang).map(JsonLDString.apply)
    }

}

/**
 * @param list         the list to be returned.
 * @param userLang     the user's preferred language.
 * @param fallbackLang the fallback language if the preferred language is not available.
 * @param allLanguages if `true`, emit `rdfs:label` / `rdfs:comment` as JSON-LD arrays of
 *                     language-tagged objects sorted by BCP-47 tag; if `false` (default)
 *                     emit the legacy single-string shape in the user's preferred language.
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
    import ListV2Iris.*

    def makeNode(node: ListChildNodeADM): JsonLDObject = {
      val label    = labelEntry(node.labels, allLanguages, userLang, fallbackLang)
      val comment  = commentEntry(node.comments, allLanguages, userLang, fallbackLang)
      val children = Option.when(node.children.nonEmpty)(
        hasSubListNode -> JsonLDArray(node.children.map(makeNode)),
      )

      JsonLDObject(
        Map[IRI, JsonLDValue](
          "@id"            -> JsonLDString(node.id),
          "@type"          -> JsonLDString(listNodeType),
          listNodePosition -> JsonLDInt(node.position),
          hasRootNode      -> JsonLDUtil.iriToJsonLDObject(node.hasRootNode),
        ) ++ children ++ label ++ comment,
      )
    }

    val listinfo = list.listinfo

    val label    = labelEntry(listinfo.labels, allLanguages, userLang, fallbackLang)
    val comment  = commentEntry(listinfo.comments, allLanguages, userLang, fallbackLang)
    val children = Option.when(list.children.nonEmpty)(
      hasSubListNode -> JsonLDArray(
        list.children.map(childNode => makeNode(childNode.asInstanceOf[ListChildNodeADM])),
      ),
    )

    val body = JsonLDObject(
      Map[IRI, JsonLDValue](
        "@id"             -> JsonLDString(listinfo.id),
        "@type"           -> JsonLDString(listNodeType),
        isRootNode        -> JsonLDBoolean(true),
        attachedToProject -> JsonLDUtil.iriToJsonLDObject(listinfo.projectIri),
      ) ++ children ++ label ++ comment,
    )

    JsonLDDocument(body, sharedContext)
  }
}

/**
 * @param node         the node to be returned.
 * @param userLang     the user's preferred language.
 * @param fallbackLang the fallback language if the preferred language is not available.
 * @param allLanguages if `true`, emit `rdfs:label` / `rdfs:comment` as JSON-LD arrays of
 *                     language-tagged objects sorted by BCP-47 tag; if `false` (default)
 *                     emit the legacy single-string shape in the user's preferred language.
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
    import ListV2Iris.*

    val body: JsonLDObject = node match {

      case root: ListRootNodeInfoADM =>
        val label   = labelEntry(root.labels, allLanguages, userLang, fallbackLang)
        val comment = commentEntry(root.comments, allLanguages, userLang, fallbackLang)

        JsonLDObject(
          Map[IRI, JsonLDValue](
            "@id"   -> JsonLDString(root.id),
            "@type" -> JsonLDString(listNodeType),
          ) ++ label ++ comment,
        )

      case child: ListChildNodeInfoADM =>
        val label   = labelEntry(child.labels, allLanguages, userLang, fallbackLang)
        val comment = commentEntry(child.comments, allLanguages, userLang, fallbackLang)

        JsonLDObject(
          Map[IRI, JsonLDValue](
            "@id"            -> JsonLDString(child.id),
            "@type"          -> JsonLDString(listNodeType),
            listNodePosition -> JsonLDInt(child.position),
            hasRootNode      -> JsonLDUtil.iriToJsonLDObject(child.hasRootNode),
          ) ++ label ++ comment,
        )
    }

    JsonLDDocument(body, sharedContext)
  }

}
