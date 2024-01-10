package org.knora.webapi.messages.util.rdf

import scala.jdk.CollectionConverters.*
import org.apache.jena.rdf.model.Resource
import org.apache.jena.rdf.model.Model

import scala.util.Try

final case class NewRdfResource(private val res: Resource, private val model: Model) {

  def getStringLiteralByProperty(propertyIri: String): Option[String] = Try {
    val property = model.createProperty(propertyIri)
    res.getProperty(property).getLiteral.getString
  }.toOption

  def getObjectIriByProperty(propertyIri: String): Option[String] = {
    val property = model.createProperty(propertyIri)
    Option.when(property != null && property.isURIResource)(res.getProperty(property).getResource.getURI)
  }

  def getObjectIrisByProperty(propertyIri: String): Seq[String] = {
    val property = model.createProperty(propertyIri)
    res.listProperties(property).toList.asScala.toList.map(_.getResource.getURI)
  }

}

final case class NewRdfModel(private val model: Model) {
  def getResource(subjectIri: String): Option[NewRdfResource] = {
    val resource = model.getResource(subjectIri)
    Option.when(NewRdfModel.resourceNonEmpty(resource))(NewRdfResource(resource, model))
  }
}

object NewRdfModel {
  def resourceNonEmpty(r: Resource): Boolean =
    r.listProperties().hasNext
}
