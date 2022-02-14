/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.models

import java.time.Instant
import scala.annotation.tailrec

object Comments {
  def handleOptionalComment(comment: Option[LangString]): String =
    comment match {
      case Some(value) =>
        s"""
           |    "rdfs:comment" : {
           |      "@language" : "${value.language}",
           |      "@value" : "${value.value}"
           |    },
           |""".stripMargin
      case None => ""
    }
}

final case class LangString(language: String, value: String)

sealed abstract case class CreateClassRequest private (value: String)
object CreateClassRequest {
  def make(
    ontologyName: String,
    lastModificationDate: Instant,
    className: String,
    label: LangString,
    comment: Option[LangString]
  ): CreateClassRequest = {
    val ontologyId = s"http://0.0.0.0:3333/ontology/0001/$ontologyName/v2"
    val maybeComment: String = Comments.handleOptionalComment(comment)

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
                   |      "@language" : "${label.language}",
                   |      "@value" : "${label.value}"
                   |    },
                   |    $maybeComment
                   |    "rdfs:subClassOf" : [
                   |      {
                   |        "@id": "knora-api:Resource"
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
}

sealed abstract case class CreatePropertyRequest private (value: String)
object CreatePropertyRequest {
  def make(
    ontologyName: String,
    lastModificationDate: Instant,
    propertyName: String,
    subjectClassName: Option[String],
    propertyType: PropertyValueType,
    label: LangString,
    comment: Option[LangString]
  ): CreatePropertyRequest = {
    val LocalHost_Ontology = "http://0.0.0.0:3333/ontology"
    val ontologyId = LocalHost_Ontology + s"/0001/$ontologyName/v2"
    val maybeComment: String = Comments.handleOptionalComment(comment)

    val optionalSubjectClass = subjectClassName match {
      case Some(subjectName) => s"""
                                   |"knora-api:subjectType" : {
                                   |      "@id" : "$ontologyName:$subjectName"
                                   |    },
                                   |""".stripMargin
      case None              => ""
    }

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
                   |        "@language" : "${label.language}",
                   |        "@value" : "${label.value}"
                   |      },
                   |      "rdfs:subPropertyOf" : {
                   |        "@id" : "knora-api:hasValue"
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
    val value = 1
  }
  case object MinCardinalityOne extends CardinalityRestriction {
    val cardinality = "owl:minCardinality"
    val value = 1
  }
  case object MinCardinalityZero extends CardinalityRestriction {
    val cardinality = "owl:minCardinality"
    val value = 0
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
    val ontologyId = s"http://0.0.0.0:3333/ontology/0001/$ontologyName/v2"
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
