/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.triplestore.upgrade.plugins

import org.junit.runner.RunWith
import zio.test.Spec
import zio.test.ZIOSpecDefault
import zio.test.assertTrue

import dsp.errors.AssertionException
import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.util.rdf.*

@RunWith(classOf[DspZTestJUnitRunner])
class UpgradePluginPR2081Spec extends ZIOSpecDefault with UpgradePluginSpec {

  private def getDateValue(model: RdfModel, subj: IriNode, pred: IriNode): String = {
    val statement = model.find(subj = Some(subj), pred = Some(pred), obj = None).toSet.head
    statement.obj match {
      case literal: DatatypeLiteral if literal.datatype == OntologyConstants.Xsd.DateTime =>
        literal.value
      case other => throw AssertionException(s"Unexpected object for $pred: $other")
    }
  }

  val spec: Spec[Any, Nothing] = suite("Upgrade plugin PR2081")(
    test("fix invalid date serializations") {
      val resource1            = JenaNodeFactory.makeIriNode("http://rdfh.ch/0001/55UrkgTKR2SEQgnsLWI9ma")
      val resource2            = JenaNodeFactory.makeIriNode("http://rdfh.ch/0001/55UrkgTKR2SEQgnsLWI9mb")
      val resource3            = JenaNodeFactory.makeIriNode("http://rdfh.ch/0001/55UrkgTKR2SEQgnsLWI9mc")
      val creationDate         = JenaNodeFactory.makeIriNode(OntologyConstants.KnoraBase.CreationDate)
      val lastModificationDate = JenaNodeFactory.makeIriNode(OntologyConstants.KnoraBase.LastModificationDate)
      val deletionDate         = JenaNodeFactory.makeIriNode(OntologyConstants.KnoraBase.DeleteDate)

      // Parse the input file.
      val model: RdfModel = trigFileToModel("test_data/upgrade/pr2081.trig")

      // Store previous values
      val resource1CreationDate = getDateValue(model, resource1, creationDate)
      val resource2CreationDate = getDateValue(model, resource2, creationDate)
      val resource3CreationDate = getDateValue(model, resource3, creationDate) // only this one should stay the same

      // Use the plugin to transform the input.
      val plugin = new UpgradePluginPR2081()
      plugin.transform(model)

      // get the new values after transformation
      val newResource1CreationDate         = getDateValue(model, resource1, creationDate)
      val newResource2CreationDate         = getDateValue(model, resource2, creationDate)
      val newResource2LastModificationDate = getDateValue(model, resource2, lastModificationDate)
      val newResource3CreationDate         =
        getDateValue(model, resource3, creationDate) // only this one should have stayed the same
      val newResource3LastModificationDate = getDateValue(model, resource3, lastModificationDate)
      val newResource3DeletionDate         = getDateValue(model, resource3, deletionDate)

      // Check that the dates were fixed.
      assertTrue(
        newResource1CreationDate != resource1CreationDate,
        newResource1CreationDate.endsWith("Z"),
        newResource2CreationDate != resource2CreationDate,
        newResource2CreationDate.endsWith("Z"),
        newResource2LastModificationDate != resource2CreationDate,
        newResource2LastModificationDate.endsWith("Z"),
        newResource3CreationDate == resource3CreationDate,
        newResource3CreationDate.endsWith("Z"),
        newResource3LastModificationDate != resource3CreationDate,
        newResource3LastModificationDate.endsWith("Z"),
        newResource3DeletionDate != resource3CreationDate,
        newResource3DeletionDate.endsWith("Z"),
      )
    },
  )
}
