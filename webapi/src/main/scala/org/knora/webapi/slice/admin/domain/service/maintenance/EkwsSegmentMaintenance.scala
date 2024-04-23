/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.service.maintenance

import zio.Task
import zio.ZIO

import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Ask

final case class EkwsSegmentMaintenance(triplestoreService: TriplestoreService) extends MaintenanceAction {
  val shouldExecuteQuery =
    """|PREFIX ekws: <http://www.knora.org/ontology/0812/ekws#>
       |ASK
       |WHERE {
       |  {
       |    ?s a ekws:Sequence .
       |  } UNION {
       |    ?s ekws:hasTitle ?o .
       |  } UNION {
       |    ?s ekws:isRepresentationOf ?o .
       |    ?s ekws:isRepresentationOfValue ?o .
       |  } UNION {
       |    ?s ekws:hasDescription ?o .
       |  }
       |}
       |""".stripMargin

  val executeQuery =
    """|PREFIX ekws: <http://www.knora.org/ontology/0812/ekws#>
       |PREFIX kb: <http://www.knora.org/ontology/knora-base#>
       |DELETE {
       |  GRAPH <http://www.knora.org/data/0812/ekws> {
       |    ?s a ekws:Sequence ;
       |      kb:isSequenceOf ?of ;
       |      kb:isSequenceOfValue ?ofVal ;
       |      kb:hasSequenceBounds ?bounds ;
       |      ekws:hasTitle ?title ;
       |      ekws:isRepresentationOf ?rep ;
       |      ekws:isRepresentationOfValue ?repVal ;
       |      ekws:hasDescription ?desc .
       |  }
       |}
       |INSERT {
       |  GRAPH <http://www.knora.org/data/0812/ekws> {
       |    ?s a kb:Segment ;
       |      kb:isVideoSegmentOf ?of ;
       |      kb:isVideoSegmentOfValue ?ofVal ;
       |      kb:hasSegmentBounds ?bounds ;
       |      kb:hasTitle ?title ;
       |      kb:relatesTo ?rep ;
       |      kb:relatesToValue ?repVal ;
       |      kb:hasDescription ?desc .
       |  }
       |}
       |WHERE {
       |  GRAPH <http://www.knora.org/data/0812/ekws> {
       |    ?s a ekws:Sequence ;
       |      kb:isSequenceOf ?of ;
       |      kb:isSequenceOfValue ?ofVal ;
       |      kb:hasSequenceBounds ?bounds .
       |    OPTIONAL {
       |      ?s ekws:hasTitle ?title .
       |    }
       |    OPTIONAL {
       |      ?s ekws:isRepresentationOf ?rep ;
       |         ekws:isRepresentationOfValue ?repVal .
       |    }
       |    OPTIONAL {
       |      ?s ekws:hasDescription ?desc .
       |    }
       |  }
       |}
       |""".stripMargin

  def convertEkwsSequenceToSegment(): Task[Unit] =
    ZIO.succeed {
      // TODO:
      // - data
      //   - graph: http://www.knora.org/data/0812/ekws
      //     - http://www.knora.org/ontology/0812/ekws#Sequence                   -> http://www.knora.org/ontology/knora-base#Segment
      //     - http://www.knora.org/ontology/knora-base#isSequenceOf              -> http://www.knora.org/ontology/knora-base#isVideoSegmentOf
      //     - http://www.knora.org/ontology/knora-base#isSequenceOfValue         -> http://www.knora.org/ontology/knora-base#isVideoSegmentOfValue
      //     - http://www.knora.org/ontology/knora-base#hasSequenceBounds         -> http://www.knora.org/ontology/knora-base#hasSegmentBounds
      //     - http://www.knora.org/ontology/0812/ekws#hasTitle                   -> http://www.knora.org/ontology/knora-base#hasTitle
      //     - http://www.knora.org/ontology/0812/ekws#isRepresentationOf         -> http://www.knora.org/ontology/knora-base#relatesTo
      //     - http://www.knora.org/ontology/0812/ekws#isRepresentationOfValue    -> http://www.knora.org/ontology/knora-base#relatesToValue
      //     - http://www.knora.org/ontology/0812/ekws#hasDescription>            -> http://www.knora.org/ontology/knora-base#hasDescription
      //   - `Sequence` in subject and object position
      //   - all properties in predicate position
      // - ontology:
      //   - nothing -> do this through the GUI after the maintenance
      println("Starting convert EKWS sequence to segment maintenance action")
      println("Finished convert EKWS sequence to segment maintenance action")
    }

  override def execute: Task[Unit] = ???

  override def shouldExecute: Task[Boolean] = triplestoreService.query(Ask(shouldExecuteQuery))
}
