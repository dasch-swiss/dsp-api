/*
 * Copyright © 2021 Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.models

sealed trait FileValueType {
  val value: String
}
object FileValueType {
  case object DocumentFileValue extends FileValueType {
    val value = "knora-api:DocumentFileValue"
  }
  case object StillImageFileValue extends FileValueType {
    val value = "knora-api:StillImageFileValue"
  }
  case object TextFileValue extends FileValueType {
    val value = "knora-api:TextFileValue"
  }
}

sealed abstract case class UploadFileRequest private (value: String)
object UploadFileRequest {
  def make(
    className: String,
    internalFilename: String,
    fileValueType: FileValueType,
    shortcode: String = "0001",
    ontologyName: String = "anything"
  ): UploadFileRequest = {
    val context = ontologyName match {
      case "anything" => ""","anything": "http://0.0.0.0:3333/ontology/0001/anything/v2#" """
      case _          => ""
    }
    val propName = fileValueType match {
      case FileValueType.DocumentFileValue   => "knora-api:hasDocumentFileValue"
      case FileValueType.StillImageFileValue => "knora-api:hasStillImageFileValue"
      case FileValueType.TextFileValue       => "knora-api:hasTextFileValue"
    }
    val value = s"""{
                   |  "@type" : "$ontologyName:$className",
                   |  "$propName" : {
                   |    "@type" : "${fileValueType.value}",
                   |    "knora-api:fileValueHasFilename" : "$internalFilename"
                   |  },
                   |  "knora-api:attachedToProject" : {
                   |    "@id" : "http://rdfh.ch/projects/$shortcode"
                   |  },
                   |  "rdfs:label" : "test label",
                   |  "@context" : {
                   |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
                   |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
                   |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
                   |    "xsd" : "http://www.w3.org/2001/XMLSchema#"
                   |    $context
                   |  }
                   |}""".stripMargin
    new UploadFileRequest(value) {}
  }
}

sealed abstract case class ChangeFileRequest private (value: String)
object ChangeFileRequest {
  def make(
    className: String,
    fileValueType: FileValueType,
    resourceIRI: String,
    internalFilename: String,
    valueIRI: String,
    ontologyName: String = "anything"
  ): ChangeFileRequest = {
    val ontologyIRI = ontologyName match {
      case "anything" => "http://0.0.0.0:3333/ontology/0001/anything/v2#"
    }
    val propName = fileValueType match {
      case FileValueType.DocumentFileValue   => "knora-api:hasDocumentFileValue"
      case FileValueType.StillImageFileValue => "knora-api:hasStillImageFileValue"
    }
    val value =
      s"""{
         |  "@id" : "$resourceIRI",
         |  "@type" : "$ontologyName:$className",
         |  "$propName" : {
         |    "@id" : "$valueIRI",
         |    "@type" : "${fileValueType.value}",
         |    "knora-api:fileValueHasFilename" : "$internalFilename"
         |  },
         |  "@context" : {
         |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
         |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
         |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
         |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
         |    "$ontologyName" : "$ontologyIRI"
         |  }
         |}""".stripMargin
    new ChangeFileRequest(value) {}
  }
}
