/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.models

import dsp.valueobjects.LangString
import dsp.valueobjects.LanguageCode

import java.time.Instant
import scala.annotation.tailrec

object Comments {
  def handleOptionalComment(comment: Option[LangString]): String =
    comment match {
      case Some(value) =>
        s"""
           |    "rdfs:comment" : {
           |      "@language" : "${value.language.value}",
           |      "@value" : "${value.value}"
           |    },
           |""".stripMargin
      case None => ""
    }
}

sealed abstract case class CreateClassRequest private (value: String)
object CreateClassRequest {

  /**
   * Makes a CreateClassRequest (JSON-LD).
   *
   * @param ontologyName         the ontology name
   * @param lastModificationDate the LMD of the ontology
   * @param className            name of the class to be created
   * @param label                the label of the class
   * @param comment              the comment of the class
   * @param subClassOf           optional superclass. defaults to "knora-api:Resource". (Needs to be of format `PREFIX:ResourceName`)
   *
   * @return a JSON-LD representation of the request wrapped in a CreateClassRequest object
   */
  def make(
    ontologyName: String,
    lastModificationDate: Instant,
    className: String,
    label: LangString = LangString.unsafeMake(LanguageCode.en, "Label"),
    comment: Option[LangString] = None,
    subClassOf: Option[String] = None
  ): CreateClassRequest = {
    val ontologyId           = s"http://0.0.0.0:3333/ontology/0001/$ontologyName/v2"
    val maybeComment: String = Comments.handleOptionalComment(comment)
    val superClass           = subClassOf.getOrElse("knora-api:Resource")

    val value = s"""{
                   |  "@id" : "$ontologyId",
                   |  "@type" : "owl:Ontology",
                   |  "knora-api:lastModificationDate" : {
                   |    "@type" : "xsd:dateTimeStamp",
                   |    "@value" : "$lastModificationDate"
                   |  },
                   |  "@graph" : [ {
                   |    "@id" : "$ontologyName:$className",
                   |    "@type" : "owl:Class",
                   |    "rdfs:label" : {
                   |      "@language" : "${label.language.value}",
                   |      "@value" : "${label.value}"
                   |    },
                   |    $maybeComment
                   |    "rdfs:subClassOf" : [
                   |      {
                   |        "@id": "$superClass"
                   |      }
                   |    ]
                   |  } ],
                   |  "@context" : {
                   |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
                   |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
                   |    "salsah-gui" : "http://api.knora.org/ontology/salsah-gui/v2#",
                   |    "owl" : "http://www.w3.org/2002/07/owl#",
                   |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
                   |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
                   |    "$ontologyName" : "$ontologyId#"
                   |  }
                   |}""".stripMargin
    new CreateClassRequest(value) {}
  }
}

sealed trait PropertyValueType {
  val value: String
}
object PropertyValueType {
  case object TextValue extends PropertyValueType {
    val value = "knora-api:TextValue"
  }
  case object IntValue extends PropertyValueType {
    val value = "knora-api:IntValue"
  }
  case object LinkValue extends PropertyValueType {
    val value = "knora-api:LinkValue"
  }
  case object Resource extends PropertyValueType {
    val value = "knora-api:Resource"
  }
}

sealed abstract case class CreatePropertyRequest private (value: String)
object CreatePropertyRequest {
  def make(
    ontologyName: String,
    lastModificationDate: Instant,
    propertyName: String,
    subjectClassName: Option[String],
    propertyType: PropertyValueType,
    label: LangString = LangString.unsafeMake(LanguageCode.en, "Label"),
    comment: Option[LangString] = None,
    subPropertyOf: Option[String] = None
  ): CreatePropertyRequest = {
    val LocalHost_Ontology   = "http://0.0.0.0:3333/ontology"
    val ontologyId           = LocalHost_Ontology + s"/0001/$ontologyName/v2"
    val maybeComment: String = Comments.handleOptionalComment(comment)

    val optionalSubjectClass = subjectClassName match {
      case Some(subjectName) => s"""
                                   |"knora-api:subjectType" : {
                                   |      "@id" : "$ontologyName:$subjectName"
                                   |    },
                                   |""".stripMargin
      case None => ""
    }
    val superProperty = subPropertyOf.getOrElse("knora-api:hasValue")

    val value = s"""{
                   |  "@id" : "$ontologyId",
                   |  "@type" : "owl:Ontology",
                   |  "knora-api:lastModificationDate" : {
                   |    "@type" : "xsd:dateTimeStamp",
                   |    "@value" : "$lastModificationDate"
                   |  },
                   |  "@graph" : [ {
                   |      "@id" : "$ontologyName:$propertyName",
                   |      "@type" : "owl:ObjectProperty",
                   |      $optionalSubjectClass
                   |      "knora-api:objectType" : {
                   |        "@id" : "${propertyType.value}"
                   |      },
                   |      $maybeComment
                   |      "rdfs:label" : {
                   |        "@language" : "${label.language.value}",
                   |        "@value" : "${label.value}"
                   |      },
                   |      "rdfs:subPropertyOf" : {
                   |        "@id" : "$superProperty"
                   |      }
                   |  } ],
                   |  "@context" : {
                   |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
                   |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
                   |    "salsah-gui" : "http://api.knora.org/ontology/salsah-gui/v2#",
                   |    "owl" : "http://www.w3.org/2002/07/owl#",
                   |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
                   |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
                   |    "$ontologyName" : "$ontologyId#"
                   |  }
                   |}""".stripMargin
    new CreatePropertyRequest(value) {}
  }
}

sealed trait CardinalityRestriction {
  val cardinality: String
  val value: Int
}
object CardinalityRestriction {
  case object MaxCardinalityOne extends CardinalityRestriction {
    val cardinality = "owl:maxCardinality"
    val value       = 1
  }
  case object MinCardinalityOne extends CardinalityRestriction {
    val cardinality = "owl:minCardinality"
    val value       = 1
  }
  case object MinCardinalityZero extends CardinalityRestriction {
    val cardinality = "owl:minCardinality"
    val value       = 0
  }
  case object CardinalityOne extends CardinalityRestriction {
    val cardinality = "owl:cardinality"
    val value       = 1
  }
}

final case class Property(ontology: String, property: String)
final case class Restriction(restriction: CardinalityRestriction, onProperty: Property) {
  def stringify(): String =
    s"""
       | {
       |    "@type": "owl:Restriction",
       |    "${restriction.cardinality}" : ${restriction.value},
       |    "owl:onProperty" : {
       |      "@id" : "${onProperty.ontology}:${onProperty.property}"
       |    }
       | }
       |""".stripMargin
}

sealed abstract case class AddCardinalitiesRequest private (value: String)
object AddCardinalitiesRequest {

  @tailrec
  private def stringifyRestrictions(restrictions: List[Restriction], acc: String = ""): String =
    restrictions match {
      case Nil       => acc
      case r :: Nil  => acc + r.stringify()
      case r :: rest => stringifyRestrictions(restrictions = rest, acc + r.stringify() + ", ")
    }

  def make(
    ontologyName: String,
    lastModificationDate: Instant,
    className: String,
    restrictions: List[Restriction]
  ): AddCardinalitiesRequest = {
    val ontologyId                 = s"http://0.0.0.0:3333/ontology/0001/$ontologyName/v2"
    val restrictionsString: String = stringifyRestrictions(restrictions)
    val value = s"""
                   |{
                   |  "@id" : "$ontologyId",
                   |  "@type" : "owl:Ontology",
                   |  "knora-api:lastModificationDate" : {
                   |    "@type" : "xsd:dateTimeStamp",
                   |    "@value" : "$lastModificationDate"
                   |  },
                   |  "@graph" : [ {
                   |    "@id" : "$ontologyName:$className",
                   |    "@type" : "owl:Class",
                   |    "rdfs:subClassOf" : [
                   |      $restrictionsString
                   |    ]
                   |  } ],
                   |  "@context" : {
                   |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
                   |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
                   |    "owl" : "http://www.w3.org/2002/07/owl#",
                   |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
                   |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
                   |    "$ontologyName" : "$ontologyId#"
                   |  }
                   |}
            """.stripMargin
    new AddCardinalitiesRequest(value) {}
  }
}
