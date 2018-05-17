/*
 * Copyright © 2015-2018 the contributors (see Contributors.md).
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

package org.knora.webapi.messages.v2.responder.listsmessages

import org.knora.webapi._
import org.knora.webapi.messages.admin.responder.listsmessages.{ListADM, ListNodeADM}
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralSequenceV2
import org.knora.webapi.messages.v2.responder.{KnoraRequestV2, KnoraResponseV2}
import org.knora.webapi.util.IriConversions._
import org.knora.webapi.util.StringFormatter
import org.knora.webapi.util.jsonld.{JsonLDArray, JsonLDDocument, JsonLDObject, JsonLDString}


/**
  * An abstract trait representing a Knora v2 API request message that can be sent to `StandoffResponderV2`.
  */
sealed trait ListsResponderRequestV2 extends KnoraRequestV2

/**
  * Requests a list. A successful response will be a [[ListsGetRequestV2]]
  *
  * @param listIris       the IRIs of the lists.
  * @param requestingUser the user making the request.
  */
case class ListsGetRequestV2(listIris: Seq[IRI],
                             requestingUser: UserADM) extends ListsResponderRequestV2

/**
  * Represents an answer to a [[ListsGetRequestV2]].
  *
  * @param lists the list the are to be returned.
  */
case class ListsGetResponseV2(lists: Vector[ListADM], userLang: String, fallbackLang: String) extends KnoraResponseV2 {

    def toJsonLDDocument(targetSchema: ApiV2Schema, settings: SettingsImpl): JsonLDDocument = {

        implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

        def makeMapIriToJSONLDString(iri: IRI, stringVals: StringLiteralSequenceV2): Map[IRI, JsonLDString] = {
            Map(
                iri -> stringVals.getPreferredLanguage(userLang, fallbackLang)
            ).collect {
                case (iri: IRI, Some(strVal: String)) => iri -> JsonLDString(strVal)
            }
        }

        /**
          * Given a [[ListNodeADM]], constructs a [[JsonLDObject]].
          *
          * @param node the node to be turned into JSON-LD.
          * @return a [[JsonLDObject]] representing the node.
          */
        def makeNode(node: ListNodeADM): JsonLDObject = {

            val label: Map[IRI, JsonLDString] = makeMapIriToJSONLDString(OntologyConstants.Rdfs.Label, node.labels)

            val comment: Map[IRI, JsonLDString] = makeMapIriToJSONLDString(OntologyConstants.Rdfs.Comment, node.comments)

            val children: Map[IRI, JsonLDArray] = if (node.children.nonEmpty) {
                Map(
                    OntologyConstants.KnoraBase.HasSubListNode.toSmartIri.toOntologySchema(targetSchema).toString -> JsonLDArray(
                        node.children.map {
                            childNode: ListNodeADM =>
                                makeNode(childNode) // recursion
                        })
                )
            } else {
                // no children (abort condition for recursion)
                Map.empty[IRI, JsonLDArray]
            }

            JsonLDObject(
                Map(
                    "@id" -> JsonLDString(node.id),
                    "@type" -> JsonLDString(OntologyConstants.KnoraBase.ListNode.toSmartIri.toOntologySchema(targetSchema).toString)
                ) ++ children ++ label ++ comment
            )
        }

        val label: Map[IRI, JsonLDString] = makeMapIriToJSONLDString(OntologyConstants.Rdfs.Label, lists.head.listinfo.labels)

        val comment: Map[IRI, JsonLDString] = makeMapIriToJSONLDString(OntologyConstants.Rdfs.Comment, lists.head.listinfo.comments)

        val children: Map[IRI, JsonLDArray] = if (lists.head.children.nonEmpty) {
            Map(
                OntologyConstants.KnoraBase.HasSubListNode.toSmartIri.toOntologySchema(targetSchema).toString -> JsonLDArray(lists.head.children.map {
                    childNode: ListNodeADM =>
                        makeNode(childNode)
                }))
        } else {
            Map.empty[IRI, JsonLDArray]
        }

        val body = JsonLDObject(Map(
            "@id" -> JsonLDString(lists.head.listinfo.id),
            "@type" -> JsonLDString(OntologyConstants.KnoraBase.ListNode.toSmartIri.toOntologySchema(targetSchema).toString)
        ) ++ children ++ label ++ comment)

        val context = JsonLDObject(Map(
            "rdfs" -> JsonLDString("http://www.w3.org/2000/01/rdf-schema#"),
            "rdf" -> JsonLDString("http://www.w3.org/1999/02/22-rdf-syntax-ns#"),
            "owl" -> JsonLDString("http://www.w3.org/2002/07/owl#"),
            "xsd" -> JsonLDString("http://www.w3.org/2001/XMLSchema#")
        ))

        JsonLDDocument(body, context)
    }

}

