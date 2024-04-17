/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.service.maintenance

import zio.Task
import zio.ZIO

import org.knora.webapi.store.triplestore.api.TriplestoreService

final case class EkwsSegmentMaintenance(ts: TriplestoreService) {

  def convertEkwsSequenceToSegment(): Task[Unit] =
    ZIO.succeed {
      // WITH <g1>
      // DELETE {
      // a b c
      // } INSERT {
      // x y z
      // } WHERE {
      // ...
      // };
      // DELETE {
      // a b c
      // } INSERT {
      // x y z
      // } WHERE {
      // ...
      // };
      // TODO:
      // - data
      //   - graph: http://www.knora.org/data/0812/ekws
      //     - http://www.knora.org/ontology/0812/ekws#Sequence                   -> http://www.knora.org/ontology/knora-base#Segment
      //     - http://www.knora.org/ontology/0812/ekws#hasTitle                   -> http://www.knora.org/ontology/knora-base#hasTitle
      //     - http://www.knora.org/ontology/knora-base#hasSequenceBounds         -> http://www.knora.org/ontology/knora-base#hasSegmentBounds
      //     - http://www.knora.org/ontology/knora-base#isSequenceOf              -> http://www.knora.org/ontology/knora-base#isVideoSegmentOf
      //     - http://www.knora.org/ontology/knora-base#isSequenceOfValue         -> http://www.knora.org/ontology/knora-base#isVideoSegmentOfValue
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
}
