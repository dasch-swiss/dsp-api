/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.repo

import zio.*

import org.knora.sparqlbuilder.*
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.service.ProjectService
import org.knora.webapi.slice.admin.repo.AssetReferencesRepo.FileReference
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Select

/** Reads which internal filenames the file values of a project's data graph name. */
final case class AssetReferencesRepo(private val triplestore: TriplestoreService) {

  /**
   * One row per internal filename and deletion state of the resource that holds the file value. A previous version
   * of a file value counts as a reference too, held by the resource of its current version.
   */
  def findFileReferences(project: KnoraProject): Task[Chunk[FileReference]] =
    triplestore
      .query(AssetReferencesRepo.fileReferencesQuery(project))
      .flatMap(result =>
        ZIO.foreach(Chunk.fromIterable(result.results.bindings))(row =>
          ZIO.attempt(
            FileReference(row.getRequired("filename"), row.getRequired("resourceDeleted").toBoolean),
          ),
        ),
      )
}

object AssetReferencesRepo {

  final case class FileReference(internalFilename: String, resourceDeleted: Boolean)

  /**
   * `knora-base:attachedToProject` keeps `?resource` a resource: a newer version of a value also points at the
   * older one, through `knora-base:previousValue`.
   */
  private[repo] def fileReferencesQuery(project: KnoraProject): Select = {
    val dataGraph = Iri.unsafeFrom(ProjectService.projectDataNamedGraphV2(project).value)
    Select(
      sparql"""|PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
               |
               |SELECT DISTINCT ?filename ?resourceDeleted
               |WHERE {
               |  GRAPH $dataGraph {
               |    ?fileValue knora-base:internalFilename ?filename .
               |    ?currentValue knora-base:previousValue* ?fileValue .
               |    ?resource ?prop ?currentValue ;
               |      knora-base:attachedToProject ?project ;
               |      knora-base:isDeleted ?resourceDeleted .
               |  }
               |}""".render,
    )
  }

  val layer = ZLayer.derive[AssetReferencesRepo]
}
