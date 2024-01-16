package org.knora.webapi.messages.util.rdf

import scala.jdk.CollectionConverters.*
import org.apache.jena.rdf.model.Resource
import org.apache.jena.rdf.model.Model
import zio.*

import dsp.valueobjects.V2

final case class NewRdfResource(private val res: Resource, private val model: Model) {

  def getStringLiteralByProperty(propertyIri: String): Task[String] = ZIO.attempt {
    val property = model.createProperty(propertyIri)
    res.getProperty(property).getLiteral.getString
  }

  def getStringLiteralsByProperty(propertyIri: String): Task[List[String]] = ZIO.attempt {
    val property = model.createProperty(propertyIri)
    res.listProperties(property).toList.asScala.toList.map(_.getLiteral.getString)
  }

  def getLangStringLiteralsByProperty(propertyIri: String): Task[List[V2.StringLiteralV2]] = ZIO.attempt {
    val property = model.createProperty(propertyIri)
    res.listProperties(property).toList.asScala.toList.map { stmt =>
      val lang       = stmt.getLiteral.getLanguage
      val langOption = Option.when(lang.nonEmpty)(lang)
      val value      = stmt.getLiteral.getString
      V2.StringLiteralV2(value, langOption)
    }
  }

  def getBooleanLiteralByProperty(propertyIri: String): Task[Boolean] = ZIO.attempt {
    val property = model.createProperty(propertyIri)
    res.getProperty(property).getLiteral.getBoolean
  }

  def getObjectIriByProperty(propertyIri: String): Task[String] = ZIO.attempt {
    val property = model.createProperty(propertyIri)
    res.getProperty(property).getResource.getURI
  }

  def getObjectIrisByProperty(propertyIri: String): Task[List[String]] = ZIO.attempt {
    val property = model.createProperty(propertyIri)
    res.listProperties(property).toList.asScala.toList.map(_.getResource.getURI)
  }

}

final case class NewRdfModel(private val model: Model) {
  def getResource(subjectIri: String): Task[NewRdfResource] = ZIO.attempt {
    val resource = model.getResource(subjectIri)
    NewRdfResource(resource, model)
  }
}
