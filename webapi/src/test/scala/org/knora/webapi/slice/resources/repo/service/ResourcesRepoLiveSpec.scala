/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.repo.service

import zio.*
import zio.test.*
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Update
import org.knora.webapi.slice.resourceinfo.domain.InternalIri
import java.time.Instant

object ResourcesRepoLiveSpec extends ZIOSpecDefault {

  def spec =
    suite("ResourcesRepoLiveSpec")(
      test("Create new resource query") {
        val tripleQuotes = "\"\"\""

        val graphIri         = InternalIri("fooGraph")
        val projectIri       = "fooProject"
        val userIri          = "fooUser"
        val resourceIri      = "fooResource"
        val resourceClassIri = "fooClass"
        val label            = "fooLabel"
        val creationDate     = Instant.parse("2024-01-01T10:00:00.673298Z")
        val permissions      = "fooPermissions"

        val resourceDefinition = ResourceReadyToCreate(
          resourceIri = resourceIri,
          resourceClassIri = resourceClassIri,
          resourceLabel = label,
          creationDate = creationDate,
          permissions = permissions,
          newValueInfos = Seq.empty,
          linkUpdates = Seq.empty,
        )

        val expected =
          Update(s"""|
                     |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                     |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                     |PREFIX owl: <http://www.w3.org/2002/07/owl#>
                     |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
                     |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
                     |
                     |INSERT DATA {
                     |    GRAPH <${graphIri.value}> {
                     |        <$resourceIri> rdf:type <$resourceClassIri> ;
                     |            knora-base:isDeleted false ;
                     |            knora-base:attachedToUser <$userIri> ;
                     |            knora-base:attachedToProject <$projectIri> ;
                     |            rdfs:label $tripleQuotes$label$tripleQuotes ;
                     |            knora-base:hasPermissions "$permissions" ;
                     |            knora-base:creationDate "$creationDate"^^xsd:dateTime .
                     |
                     |        
                     |
                     |
                     |        
                     |    }
                     |}
                     |""".stripMargin)
        val result = ResourcesRepoLive.createNewResourceQuery(
          dataGraphIri = graphIri,
          resourceToCreate = resourceDefinition,
          projectIri = projectIri,
          creatorIri = userIri,
        )

        assertTrue(expected.sparql == result.sparql)
      },
    )
}
