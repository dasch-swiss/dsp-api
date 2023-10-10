/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.triplestore

import org.apache.commons.io.IOUtils
import org.apache.jena.query.Dataset
import org.apache.jena.query.DatasetFactory
import org.apache.jena.query.ReadWrite
import org.apache.jena.rdf.model.Model
import org.apache.jena.rdf.model.Resource
import org.apache.jena.riot.Lang
import org.apache.jena.riot.RDFDataMgr
import org.apache.jena.tdb2.TDB2Factory
import zio._

import scala.jdk.CollectionConverters._
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject

object TestDatasetBuilder {
  def empty: TestDataset              = TestDataset(TDB2Factory.createDataset())
  def minimal: TestDataset            = empty.withMinimal
  def allTestOntologies: TestDataset  = empty.withAllTestOntologies
  def allTestData: TestDataset        = empty.withAllTestData
  def trig(trig: String): TestDataset = minimal.withTrig(trig)
}

final case class TestDataset(private val dataset: Dataset) {
  def withTrig(trig: String): TestDataset = {
    val ds: Dataset                     = readTrigDataset(trig)
    val graphs: List[(Resource, Model)] = ds.listModelNames().asScala.toList.map(n => (n, ds.getNamedModel(n)))
    graphs.foreach { case (n, m) => TestDataset.transactionalWrite(dataset, n, m) }
    TestDataset(dataset)
  }
  def withMinimal: TestDataset = {
    val graphs: List[(String, Model)] =
      StandardDataset.minimal.map(rdfObject => (rdfObject.name, readModelFromFile("../" + rdfObject.path)))
    graphs.foreach { case (n, m) => TestDataset.transactionalWrite(dataset, n, m) }
    TestDataset(dataset)
  }
  def withAllTestOntologies: TestDataset = {
    val graphs: List[(String, Model)] =
      StandardDataset.allOntologies.map(rdfObject => (rdfObject.name, readModelFromFile("../" + rdfObject.path)))
    graphs.foreach { case (n, m) => println(n); TestDataset.transactionalWrite(dataset, n, m) }
    TestDataset(dataset)
  }
  def withAllTestData: TestDataset                           = ???
  def withAdditional(dataset: StandardDataset*): TestDataset = this // XXX: implement
  def toLayer: TaskLayer[Ref[Dataset]]                       = ZLayer.fromZIO(Ref.make(dataset))

  private def readTrigDataset(trig: String): Dataset = {
    val ds  = DatasetFactory.createGeneral()
    val inS = IOUtils.toInputStream(trig, "UTF-8")
    RDFDataMgr.read(ds, inS, Lang.TRIG)
    ds
  }

  private def readModelFromFile(path: String): Model = RDFDataMgr.loadModel(path)

  def debug = {
    dataset.begin(ReadWrite.READ)
    println("Dataset:")
    println(s"  default: ${dataset.getDefaultModel.size()}")
    println(s"  union: ${dataset.getUnionModel.size()}")
    dataset.listNames().asScala.foreach { name =>
      println(s"     $name: ${dataset.getNamedModel(name).size()}")
    }
    dataset.end()
    this
  }
}

object TestDataset {
  def transactionalWrite(ds: Dataset, graph: Resource, model: Model) = { // LATER: may throw?
    ds.begin(ReadWrite.WRITE)
    try {
      ds.addNamedModel(graph, model)
      ds.commit()
    } finally {
      ds.end()
    }
    ds
  }
  def transactionalWrite(ds: Dataset, graph: String, model: Model) = { // LATER: may throw?
    ds.begin(ReadWrite.WRITE)
    try {
      ds.addNamedModel(graph, model)
      ds.commit()
    } finally {
      ds.end()
    }
    ds
  }
}

sealed trait StandardDataset
object StandardDataset {
  case object AnythingOntology extends StandardDataset
  case object AnythingData     extends StandardDataset

  private val adminData = RdfDataObject(
    path = "test_data/project_data/admin-data.ttl",
    name = "http://www.knora.org/data/admin"
  )
  private val permissionsData = RdfDataObject(
    path = "test_data/project_data/permissions-data.ttl",
    name = "http://www.knora.org/data/permissions"
  )
  private val adminDataMinimal = RdfDataObject(
    path = "test_data/project_data/admin-data-minimal.ttl",
    name = "http://www.knora.org/data/admin"
  )
  private val permissionsDataMinimal = RdfDataObject(
    path = "test_data/project_data/permissions-data-minimal.ttl",
    name = "http://www.knora.org/data/permissions"
  )
  private val knoraAdmin = RdfDataObject(
    path = "knora-ontologies/knora-admin.ttl",
    name = "http://www.knora.org/ontology/knora-admin"
  )
  private val knoraBase = RdfDataObject(
    path = "knora-ontologies/knora-base.ttl",
    name = "http://www.knora.org/ontology/knora-base"
  )
  private val standoff = RdfDataObject(
    path = "knora-ontologies/standoff-onto.ttl",
    name = "http://www.knora.org/ontology/standoff"
  )
  private val standoffData = RdfDataObject(
    path = "knora-ontologies/standoff-data.ttl",
    name = "http://www.knora.org/data/standoff"
  )
  private val salsahGui = RdfDataObject(
    path = "knora-ontologies/salsah-gui.ttl",
    name = "http://www.knora.org/ontology/salsah-gui"
  )

  val anythingOnto = RdfDataObject(
    path = "test_data/project_ontologies/anything-onto.ttl",
    name = "http://www.knora.org/ontology/0001/anything"
  )
  val somethingOnto = RdfDataObject(
    path = "test_data/project_ontologies/something-onto.ttl",
    name = "http://www.knora.org/ontology/0001/something"
  )
  val imagesOnto = RdfDataObject( // TODO: remove?
    path = "test_data/project_ontologies/images-onto.ttl",
    name = "http://www.knora.org/ontology/00FF/images"
  )
  val beolOnto = RdfDataObject(
    path = "test_data/project_ontologies/beol-onto.ttl",
    name = "http://www.knora.org/ontology/0801/beol"
  )
  val biblioOnto = RdfDataObject(
    path = "test_data/project_ontologies/biblio-onto.ttl",
    name = "http://www.knora.org/ontology/0801/biblio"
  )
  val incunabulaOnto = RdfDataObject( // TODO: remove
    path = "test_data/project_ontologies/incunabula-onto.ttl",
    name = "http://www.knora.org/ontology/0803/incunabula"
  )
  val dokubibOnto = RdfDataObject(
    path = "test_data/project_ontologies/dokubib-onto.ttl",
    name = "http://www.knora.org/ontology/0804/dokubib"
  )
  val webernOnto = RdfDataObject(
    path = "test_data/project_ontologies/webern-onto.ttl",
    name = "http://www.knora.org/ontology/0806/webern"
  )
  val systemData = RdfDataObject( // TODO: remove
    path = "test_data/project_data/system-data.ttl",
    name = "http://www.knora.org/data/0000/SystemProject"
  )

  val allOntologies = List(
    anythingOnto,
    somethingOnto,
    imagesOnto,
    beolOnto,
    biblioOnto,
    incunabulaOnto,
    dokubibOnto,
    webernOnto,
    systemData,
    adminData,
    permissionsData
  )

  val minimal = List(
    knoraAdmin,
    knoraBase,
    standoff,
    standoffData,
    salsahGui,
    adminDataMinimal,
    permissionsDataMinimal
  )
}
