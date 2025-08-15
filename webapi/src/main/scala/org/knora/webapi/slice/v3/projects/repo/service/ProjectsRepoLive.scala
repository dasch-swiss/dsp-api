/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.v3.projects.repo.service

import zio.*
import zio.cache.Cache
import zio.cache.Lookup

import org.knora.webapi.messages.admin.responder.listsmessages.ListsGetResponseADM
import org.knora.webapi.messages.v2.responder.ontologymessages.ReadOntologyV2
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortname
import org.knora.webapi.slice.v3.projects.domain.model.DomainTypes.*
import org.knora.webapi.slice.v3.projects.domain.model.ProjectsRepo

private final case class InstanceCountCacheKey(
  shortcode: Shortcode,
  shortname: Shortname,
  classIris: List[String],
)

final case class ProjectsRepoLive(
  underlying: ProjectsRepo,
  instanceCountsCache: Cache[InstanceCountCacheKey, Throwable, Map[String, Int]],
  ontologyClassesCache: Cache[OntologyIri, Throwable, List[(String, Map[String, String])]],
  projectByIdCache: Cache[ProjectIri, Throwable, Option[KnoraProject]],
  projectByShortcodeCache: Cache[Shortcode, Throwable, Option[KnoraProject]],
  ontologiesByProjectCache: Cache[ProjectIri, Throwable, List[ReadOntologyV2]],
  listsByProjectCache: Cache[ProjectIri, Throwable, ListsGetResponseADM],
) extends ProjectsRepo {

  override def findProjectByIri(id: ProjectIri): Task[Option[KnoraProject]] =
    projectByIdCache
      .get(id)
      .tapError(error => ZIO.logWarning(s"V3 Projects Cache: Error in findProjectById cache for ${id.value}: $error"))

  override def findProjectByShortcode(shortcode: Shortcode): Task[Option[KnoraProject]] =
    projectByShortcodeCache
      .get(shortcode)
      .tapError(error =>
        ZIO.logWarning(s"V3 Projects Cache: Error in findProjectByShortcode cache for ${shortcode.value}: $error"),
      )

  override def findOntologiesByProject(projectId: ProjectIri): Task[List[ReadOntologyV2]] =
    ontologiesByProjectCache
      .get(projectId)
      .tapError(error =>
        ZIO.logWarning(s"V3 Projects Cache: Error in findOntologiesByProject cache for ${projectId.value}: $error"),
      )

  override def findListsByProject(projectId: ProjectIri): Task[ListsGetResponseADM] =
    listsByProjectCache
      .get(projectId)
      .tapError(error =>
        ZIO.logWarning(s"V3 Projects Cache: Error in findListsByProject cache for ${projectId.value}: $error"),
      )

  override def countInstancesByClasses(
    shortcode: Shortcode,
    shortname: Shortname,
    classIris: List[String],
  ): Task[Map[String, Int]] =
    if (classIris.isEmpty) {
      ZIO.succeed(Map.empty)
    } else {
      val cacheKey = InstanceCountCacheKey(shortcode, shortname, classIris.sorted)
      instanceCountsCache
        .get(cacheKey)
        .tapError(error =>
          ZIO.logWarning(
            s"V3 Projects Cache: Error in countInstancesByClasses cache for ${shortcode.value}/${shortname.value}: $error",
          ),
        )
    }

  override def getClassesFromOntology(ontologyIri: OntologyIri): Task[List[(String, Map[String, String])]] =
    ontologyClassesCache.get(ontologyIri)

}

object ProjectsRepoLive {
  val layer: ZLayer[ProjectsRepoDb, Nothing, ProjectsRepoLive] =
    ZLayer.fromZIO {
      for {
        underlying <- ZIO.service[ProjectsRepoDb]

        instanceCountsCache <-
          Cache.make(
            capacity = 1000,
            timeToLive = 15.minutes,
            lookup = Lookup { (key: InstanceCountCacheKey) =>
              underlying.countInstancesByClasses(key.shortcode, key.shortname, key.classIris)
            },
          )

        ontologyClassesCache <-
          Cache.make(
            capacity = 500,
            timeToLive = 1.hour,
            lookup = Lookup { (ontologyIri: OntologyIri) =>
              underlying.getClassesFromOntology(ontologyIri)
            },
          )

        projectByIdCache <-
          Cache.make(
            capacity = 200,
            timeToLive = 5.minutes,
            lookup = Lookup { (id: ProjectIri) =>
              underlying.findProjectByIri(id)
            },
          )

        projectByShortcodeCache <-
          Cache.make(
            capacity = 200,
            timeToLive = 5.minutes,
            lookup = Lookup { (shortcode: Shortcode) =>
              underlying.findProjectByShortcode(shortcode)
            },
          )

        ontologiesByProjectCache <-
          Cache.make(
            capacity = 200,
            timeToLive = 5.minutes,
            lookup = Lookup { (projectId: ProjectIri) =>
              underlying.findOntologiesByProject(projectId)
            },
          )

        listsByProjectCache <-
          Cache.make(
            capacity = 200,
            timeToLive = 5.minutes,
            lookup = Lookup { (projectId: ProjectIri) =>
              underlying.findListsByProject(projectId)
            },
          )

      } yield ProjectsRepoLive(
        underlying,
        instanceCountsCache,
        ontologyClassesCache,
        projectByIdCache,
        projectByShortcodeCache,
        ontologiesByProjectCache,
        listsByProjectCache,
      )
    }
}
