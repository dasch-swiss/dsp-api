/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.triplestore.upgrade.plugins

import com.typesafe.scalalogging.LazyLogging
import dsp.errors.AssertionException
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.util.rdf._

class UpgradePluginPR2081Spec extends UpgradePluginSpec with LazyLogging {
  private val nodeFactory: RdfNodeFactory = RdfFeatureFactory.getRdfNodeFactory()

  private def getDateValue(model: RdfModel, subj: IriNode, pred: IriNode): String = {
    val statement = model.find(subj = Some(subj), pred = Some(pred), obj = None).toSet.head
    statement.obj match {
      case literal: DatatypeLiteral if literal.datatype == OntologyConstants.Xsd.DateTime =>
        literal.value
      case other => throw AssertionException(s"Unexpected object for $pred: $other")
    }
  }

  "Upgrade plugin PR2081" should {

    "fix invalid date serializations" in {
      val resource1            = nodeFactory.makeIriNode("http://rdfh.ch/0001/55UrkgTKR2SEQgnsLWI9ma")
      val resource2            = nodeFactory.makeIriNode("http://rdfh.ch/0001/55UrkgTKR2SEQgnsLWI9mb")
      val resource3            = nodeFactory.makeIriNode("http://rdfh.ch/0001/55UrkgTKR2SEQgnsLWI9mc")
      val creationDate         = nodeFactory.makeIriNode(OntologyConstants.KnoraBase.CreationDate)
      val lastModificationDate = nodeFactory.makeIriNode(OntologyConstants.KnoraBase.LastModificationDate)
      val deletionDate         = nodeFactory.makeIriNode(OntologyConstants.KnoraBase.DeleteDate)

      // Parse the input file.
      val model: RdfModel = trigFileToModel("../test_data/upgrade/pr2081.trig")

      // Store previous values
      val resource1CreationDate         = getDateValue(model, resource1, creationDate)
      val resource2CreationDate         = getDateValue(model, resource2, creationDate)
      val resource2LastModificationDate = getDateValue(model, resource2, lastModificationDate)
      val resource3CreationDate         = getDateValue(model, resource3, creationDate) // only this one should stay the same
      val resource3LastModificationDate = getDateValue(model, resource3, lastModificationDate)
      val resource3DeletionDate         = getDateValue(model, resource3, deletionDate)

      // Use the plugin to transform the input.
      val plugin = new UpgradePluginPR2081(log)
      plugin.transform(model)

      // get the new values after transformation
      val newResource1CreationDate         = getDateValue(model, resource1, creationDate)
      val newResource2CreationDate         = getDateValue(model, resource2, creationDate)
      val newResource2LastModificationDate = getDateValue(model, resource2, lastModificationDate)
      val newResource3CreationDate =
        getDateValue(model, resource3, creationDate) // only this one should have stayed the same
      val newResource3LastModificationDate = getDateValue(model, resource3, lastModificationDate)
      val newResource3DeletionDate         = getDateValue(model, resource3, deletionDate)

      // Check that the dates were fixed.
      newResource1CreationDate should not equal (resource1CreationDate)
      newResource1CreationDate should endWith("Z")

      newResource2CreationDate should not equal (resource2CreationDate)
      newResource2CreationDate should endWith("Z")
      newResource2LastModificationDate should not equal (resource2CreationDate)
      newResource2LastModificationDate should endWith("Z")

      newResource3CreationDate should equal(resource3CreationDate) // is equal!
      newResource3CreationDate should endWith("Z")
      newResource3LastModificationDate should not equal (resource3CreationDate)
      newResource3LastModificationDate should endWith("Z")
      newResource3DeletionDate should not equal (resource3CreationDate)
      newResource3DeletionDate should endWith("Z")
    }
  }
}
