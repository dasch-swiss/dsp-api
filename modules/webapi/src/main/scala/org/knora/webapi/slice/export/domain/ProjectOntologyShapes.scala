/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.`export`.domain

import org.apache.jena.datatypes.xsd.XSDDatatype
import org.apache.jena.rdf.model.Model
import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.rdf.model.Resource
import org.apache.jena.rdf.model.ResourceFactory
import org.apache.jena.riot.Lang
import org.apache.jena.riot.RDFDataMgr
import org.apache.jena.vocabulary.RDF

import java.io.StringWriter

import dsp.constants.SalsahGui
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.store.triplestoremessages.PlainStringLiteralV2
import org.knora.webapi.messages.v2.responder.ontologymessages.OwlCardinality.KnoraCardinalityInfo
import org.knora.webapi.messages.v2.responder.ontologymessages.ReadClassInfoV2
import org.knora.webapi.messages.v2.responder.ontologymessages.ReadOntologyV2
import org.knora.webapi.messages.v2.responder.ontologymessages.ReadPropertyInfoV2
import org.knora.webapi.slice.ontology.domain.model.Cardinality.*

/**
 * Generates SHACL node shapes from the resource classes of the project ontologies, and the shape that restricts the
 * class of every imported resource. Built-in ontologies contribute property definitions but no class shapes.
 */
object ProjectOntologyShapes {

  private val ShNs                       = "http://www.w3.org/ns/shacl#"
  private def sh(name: String): Resource = ResourceFactory.createResource(ShNs + name)
  private def shProp(name: String)       = ResourceFactory.createProperty(ShNs + name)

  /** The gui attribute that names the list of a list property, e.g. `hlist=<http://rdfh.ch/lists/0001/abc>`. */
  private val HListAttribute = "^hlist=<(.+)>$".r

  private val Kb = OntologyConstants.KnoraBase.KnoraBasePrefixExpansion

  private lazy val ObjectClassConstraintIri: SmartIri =
    StringFormatter.getGeneralInstance.toSmartIri(OntologyConstants.KnoraBase.ObjectClassConstraint)

  /** IRI of the shape that restricts the class of every node attached to the project. */
  val ResourceClassShapeIri: String = Kb + "ImportResourceClass_GeneratedShape"

  /** The built-in classes that a project may instantiate directly. They get no generated shape. */
  private val AllowedBuiltInClasses: List[String] =
    List("Region", "LinkObj", "VideoSegment", "AudioSegment").map(Kb + _)

  /** The IRIs of list nodes and ontologies as the API generates them; no resource IRI matches either pattern. */
  private val ListNodeIriPattern = "^http://rdfh\\.ch/lists/"
  private val OntologyIriPattern = "^http://www\\.knora\\.org/ontology/"

  /**
   * @param ontologies the built-in and project ontologies in the internal schema.
   * @return the shapes as Turtle.
   */
  def generate(ontologies: List[ReadOntologyV2]): String = {
    val propertyDefs = ontologies.flatMap(_.properties).toMap
    val classes      = shapedClasses(ontologies)
    val model        = ModelFactory.createDefaultModel()
    classes.foreach(addNodeShape(model, _, propertyDefs))
    addResourceClassShape(model, classes.map(_.entityInfoContent.classIri.toString).sorted)
    serialize(model)
  }

  private def addResourceClassShape(model: Model, projectClasses: List[String]): Unit = {
    def iriBranch(cls: String, iriPattern: String) =
      model
        .createResource()
        .addProperty(shProp("class"), model.createResource(cls))
        .addProperty(shProp("pattern"), iriPattern)
    val allowedTypes = (projectClasses ++ AllowedBuiltInClasses).map(model.createResource)
    val typeBranch   = model
      .createResource()
      .addProperty(shProp("path"), RDF.`type`)
      .addLiteral(shProp("minCount"), model.createTypedLiteral("1", XSDDatatype.XSDinteger))
      .addProperty(shProp("in"), model.createList(allowedTypes*))
    val branches: List[Resource] = List(
      iriBranch(Kb + "ListNode", ListNodeIriPattern),
      iriBranch(OntologyConstants.Owl.Ontology, OntologyIriPattern),
      typeBranch,
    )
    val builtIns = AllowedBuiltInClasses.map(c => "knora-base:" + c.stripPrefix(Kb)).mkString(", ")
    model
      .createResource(ResourceClassShapeIri)
      .addProperty(RDF.`type`, sh("NodeShape"))
      .addProperty(shProp("targetSubjectsOf"), model.createResource(Kb + "attachedToProject"))
      .addProperty(
        shProp("description"),
        "Every node attached to a project is a list node, an ontology, or a resource whose class is exactly a " +
          "resource class of the project or a built-in class that a project may instantiate.",
      )
      .addProperty(shProp("or"), model.createList(branches*))
      .addProperty(
        shProp("message"),
        "A node with knora-base:attachedToProject must be a knora-base:ListNode, an owl:Ontology, or a resource of " +
          s"a class defined in the project ontologies or one of $builtIns",
      )
    ()
  }

  private def shapedClasses(ontologies: List[ReadOntologyV2]): List[ReadClassInfoV2] =
    ontologies
      .filterNot(_.ontologyMetadata.ontologyIri.isKnoraBuiltInDefinitionIri)
      .flatMap(_.classes.values)
      .filter(_.isResourceClass)

  private def addNodeShape(
    model: Model,
    classInfo: ReadClassInfoV2,
    propertyDefs: Map[SmartIri, ReadPropertyInfoV2],
  ): Unit = {
    val classIri = classInfo.entityInfoContent.classIri.toString
    val shape    = model.createResource(s"${classIri}_GeneratedShape")
    shape.addProperty(RDF.`type`, sh("NodeShape"))
    shape.addProperty(shProp("target"), sparqlTarget(model, classIri))
    addClosedConstraint(model, shape, classInfo)
    classInfo.allResourcePropertyCardinalities.toList
      .filterNot((propIri, _) => propertyDefs.get(propIri).exists(_.isLinkValueProp))
      .foreach { (propIri, card) =>
        shape.addProperty(shProp("property"), propertyShape(model, propIri, card, propertyDefs))
        propertyDefs.get(propIri).flatMap(listRootNode).foreach { root =>
          shape.addProperty(shProp("property"), listMembershipShape(model, propIri, root))
        }
      }
  }

  /**
   * Closes the shape: besides `rdf:type` and every property with a cardinality on the class, including the system
   * properties inherited from `knora-base:Resource`, an instance may carry no other property.
   */
  private def addClosedConstraint(model: Model, shape: Resource, classInfo: ReadClassInfoV2): Unit = {
    val ignored = RDF.`type`.getURI :: classInfo.allCardinalities.keys.map(_.toString).toList
    shape.addLiteral(shProp("closed"), model.createTypedLiteral("true", XSDDatatype.XSDboolean))
    shape.addProperty(
      shProp("ignoredProperties"),
      model.createList(ignored.distinct.sorted.map(model.createResource)*),
    )
    ()
  }

  private def sparqlTarget(model: Model, classIri: String): Resource =
    model
      .createResource()
      .addProperty(RDF.`type`, sh("SPARQLTarget"))
      .addProperty(shProp("select"), s"SELECT ?this WHERE { ?this a <$classIri> }")

  private def propertyShape(
    model: Model,
    propIri: SmartIri,
    card: KnoraCardinalityInfo,
    propertyDefs: Map[SmartIri, ReadPropertyInfoV2],
  ): Resource = {
    val shape = model.createResource().addProperty(shProp("path"), model.createResource(propIri.toString))
    addCounts(model, shape, card)
    propertyDefs.get(propIri).flatMap(objectClassConstraint).foreach { cls =>
      shape.addProperty(shProp("class"), model.createResource(cls))
    }
    shape
  }

  /** The property shape that requires every list node of a list property to belong to the list its `hlist` names. */
  private def listMembershipShape(model: Model, propIri: SmartIri, rootNode: String): Resource =
    model
      .createResource()
      .addProperty(
        shProp("path"),
        model.createList(
          model.createResource(propIri.toString),
          model.createResource(Kb + "valueHasListNode"),
          model.createResource(Kb + "hasRootNode"),
        ),
      )
      .addProperty(shProp("in"), model.createList(model.createResource(rootNode)))
      .addProperty(
        shProp("description"),
        "Every list node of a value of this property belongs to the list that the property's hlist gui attribute names.",
      )
      .addProperty(
        shProp("message"),
        s"Every value of <$propIri> must point to a list node of the list <$rootNode>",
      )

  /** The root node IRI of the `hlist=<rootNodeIri>` gui attribute of a list property, if it has one. */
  private def listRootNode(prop: ReadPropertyInfoV2): Option[String] =
    if (objectClassConstraint(prop).contains(OntologyConstants.KnoraBase.ListValue))
      prop.entityInfoContent.predicates.collect {
        case (predIri, info) if predIri.toString == SalsahGui.GuiAttribute => info.objects
      }.flatten.collectFirst { case PlainStringLiteralV2(HListAttribute(root)) => root }
    else None

  private def addCounts(model: Model, shape: Resource, card: KnoraCardinalityInfo): Unit = {
    def count(name: String, n: Int): Unit = {
      shape.addLiteral(shProp(name), model.createTypedLiteral(n.toString, XSDDatatype.XSDinteger))
      ()
    }
    card.cardinality match {
      case ExactlyOne => count("minCount", 1); count("maxCount", 1)
      case ZeroOrOne  => count("maxCount", 1)
      case AtLeastOne => count("minCount", 1)
      case Unbounded  => ()
    }
  }

  private def objectClassConstraint(prop: ReadPropertyInfoV2): Option[String] =
    prop.entityInfoContent.getPredicateIriObject(ObjectClassConstraintIri).map(_.toString)

  private def serialize(model: Model): String = {
    val out = new StringWriter()
    RDFDataMgr.write(out, model, Lang.TURTLE)
    out.toString
  }
}
