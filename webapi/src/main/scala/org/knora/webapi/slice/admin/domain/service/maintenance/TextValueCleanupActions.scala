/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.service.maintenance

import zio.*

import org.knora.webapi.slice.admin.domain.model.MaintenanceAction
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Ask
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Select

final case class TextValueCleanupSimpleTextInOntoAction(
  triplestoreService: TriplestoreService,
) extends MaintenanceAction[Unit]() {

  override def shouldExecute: Task[Boolean] =
    triplestoreService.query(
      Ask(
        """|PREFIX owl: <http://www.w3.org/2002/07/owl#>
           |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
           |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
           |PREFIX kb: <http://www.knora.org/ontology/knora-base#>
           |PREFIX sg:  <http://www.knora.org/ontology/salsah-gui#>
           |ASK
           |WHERE {
           |  ?textValProp a owl:ObjectProperty ;
           |     sg:guiElement sg:Richtext .
           |  FILTER NOT EXISTS {
           |    ?s ?textValProp ?textVal .
           |    ?textVal a kb:TextValue .
           |    ?textVal kb:valueHasMapping ?mapping
           |  }
           |}""".stripMargin,
      ),
    )

  override def execute(params: Unit): Task[Unit] =
    for {
      propsRes <- triplestoreService.query(
                    Select(
                      """|PREFIX owl: <http://www.w3.org/2002/07/owl#>
                         |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                         |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                         |PREFIX kb: <http://www.knora.org/ontology/knora-base#>
                         |PREFIX sg:  <http://www.knora.org/ontology/salsah-gui#>
                         |SELECT DISTINCT ?textValProp
                         |WHERE {
                         |  ?textValProp a owl:ObjectProperty ;
                         |     sg:guiElement sg:Richtext .
                         |  FILTER NOT EXISTS {
                         |    ?s ?textValProp ?textVal .
                         |    ?textVal a kb:TextValue .
                         |    ?textVal kb:valueHasMapping ?mapping
                         |  }
                         |} LIMIT 5""".stripMargin,
                    ),
                  )
      props = propsRes.results.bindings.flatMap(_.rowMap.get("textValProp"))
    } yield ()
}

final case class TextValueCleanupSimpleTextInDataAction() {
  throw new NotImplementedError("TextValueCleanupSimpleTextInDataAction is not implemented")
}

final case class TextValueCleanupRichtextInOntoAction() {
  throw new NotImplementedError("TextValueCleanupRichtextInOntoAction is not implemented")
}

final case class TextValueCleanupRichtextInDataAction() {
  throw new NotImplementedError("TextValueCleanupRichtextInDataAction is not implemented")
}

final case class TextValueCleanupCustomMarkupAction() {
  throw new NotImplementedError("TextValueCleanupCustomMarkupAction is not implemented")
}
