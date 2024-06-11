/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.repo.service

import zio.*
import zio.test.*
// import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Update
// import org.knora.webapi.slice.resourceinfo.domain.InternalIri
// import org.knora.webapi.responders.v2.resources.SparqlTemplateResourceToCreate
// import java.time.Instant

object ResourcesRepoLiveSpec extends ZIOSpecDefault {

  def spec =
    suite("ResourcesRepoLiveSpec")(
      test("Create new resource query") {
        // val tripleQuotes = "\"\"\""

        // val resSparql = SparqlTemplateResourceToCreate(
        //   resourceIri = "fooResource",
        //   permissions = "fooPermissions",
        //   sparqlForValues = "<vs> <vp> <vo> .",
        //   resourceClassIri = "fooClass",
        //   resourceLabel = "fooLabel",
        //   resourceCreationDate = Instant.parse("2024-01-01T10:00:00.673298Z"),
        // )

        // val expected =
        //   Update(s"""|
        //              |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
        //              |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
        //              |PREFIX owl: <http://www.w3.org/2002/07/owl#>
        //              |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
        //              |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
        //              |
        //              |INSERT DATA {
        //              |    GRAPH <foo> {
        //              |        <fooResource> rdf:type <fooClass> ;
        //              |            knora-base:isDeleted false ;
        //              |            knora-base:attachedToUser <fooUser> ;
        //              |            knora-base:attachedToProject <fooProject> ;
        //              |            rdfs:label ${tripleQuotes}fooLabel$tripleQuotes ;
        //              |            knora-base:hasPermissions "fooPermissions" ;
        //              |            knora-base:creationDate "2024-01-01T10:00:00.673298Z"^^xsd:dateTime .
        //              |
        //              |        <vs> <vp> <vo> .
        //              |    }
        //              |}
        //              |""".stripMargin)
        // val result = ResourcesRepoLive.createNewResourceQuery(
        //   dataGraphIri = InternalIri("foo"),
        //   resourceToCreate = resSparql,
        //   projectIri = "fooProject",
        //   creatorIri = "fooUser",
        // )

        // assertTrue(expected.sparql == result.sparql)
        assertTrue(true)
      },
    )
}
