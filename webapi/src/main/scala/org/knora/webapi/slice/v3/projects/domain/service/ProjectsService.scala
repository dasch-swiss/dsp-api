/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.v3.projects.domain.service

import zio.*

import org.knora.webapi.messages.admin.responder.listsmessages.ListRootNodeInfoADM
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralSequenceV2
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.messages.v2.responder.ontologymessages.ReadOntologyV2
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Description
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.v3.projects.api.model.V3ProjectNotFoundException
import org.knora.webapi.slice.v3.projects.domain.model.*
import org.knora.webapi.slice.v3.projects.domain.model.DomainTypes.*

final case class ProjectsService(
  projectsRepo: ProjectsRepo,
) {

  def findProjectInfoByIri(id: ProjectIri): Task[Option[ProjectInfo]] =
    projectsRepo.findProjectByIri(id).flatMap(ZIO.foreach(_)(convertToV3ProjectInfo))

  def findResourceCountsById(id: ProjectIri): Task[Option[List[OntologyResourceCounts]]] =
    projectsRepo.findProjectByIri(id).flatMap(ZIO.foreach(_)(convertToResourceCounts))

  def warmResourceCountsCache(id: ProjectIri): Task[Unit] =
    projectsRepo
      .findProjectByIri(id)
      .flatMap(ZIO.foreach(_)(warmCacheForProject))
      .forkDaemon
      .unit

  private def warmCacheForProject(knoraProject: KnoraProject): Task[Unit] =
    for {
      ontologies <- fetchProjectOntologies(knoraProject.id)
      _          <- ZIO.foreachDiscard(ontologies)(warmCacheForOntology(_, knoraProject))
    } yield ()

  private def warmCacheForOntology(ontology: Ontology, knoraProject: KnoraProject): Task[Unit] =
    for {
      classInfo <- projectsRepo.getClassesFromOntology(ontology.iri)
      classIris  = extractClassIris(classInfo)
      _ <- ZIO.when(classIris.nonEmpty) {
             projectsRepo.countInstancesByClasses(knoraProject.shortcode, knoraProject.shortname, classIris)
           }
    } yield ()

  def findProjectIriByShortcode(shortcode: Shortcode): Task[ProjectIri] =
    projectsRepo
      .findProjectByShortcode(shortcode)
      .someOrFail(V3ProjectNotFoundException(shortcode.value))
      .map(_.id)

  private def fetchProjectOntologies(projectId: ProjectIri): Task[List[Ontology]] =
    for {
      readOntologies <- projectsRepo.findOntologiesByProject(projectId)
      ontologyModels <- ZIO.foreach(readOntologies)(createOntologyFromReadOntologyV2)
    } yield ontologyModels

  private def fetchProjectLists(projectId: ProjectIri): Task[List[ListPreview]] =
    for {
      listsResponse <- projectsRepo.findListsByProject(projectId)
      lists         <- ZIO.foreach(listsResponse.lists.toList)(createListPreviewFromADM)
    } yield lists

  private def convertToV3ProjectInfo(
    knoraProject: KnoraProject,
  ): Task[ProjectInfo] =
    for {
      ontologies            <- fetchProjectOntologies(knoraProject.id)
      lists                 <- fetchProjectLists(knoraProject.id)
      ontologiesWithClasses <- enrichOntologiesWithClasses(ontologies)
      projectInfo           <- convertKnoraProjectToV3Info(knoraProject, ontologiesWithClasses, lists)
    } yield projectInfo

  private def enrichOntologiesWithClasses(ontologies: List[Ontology]): Task[List[OntologyWithClasses]] =
    ZIO.collectAll(ontologies.map(fetchOntologyWithClasses))

  private def convertToResourceCounts(
    knoraProject: KnoraProject,
  ): Task[List[OntologyResourceCounts]] =
    for {
      ontologies     <- fetchProjectOntologies(knoraProject.id)
      resourceCounts <- generateAllResourceCounts(ontologies, knoraProject)
    } yield resourceCounts

  private def generateAllResourceCounts(
    ontologies: List[Ontology],
    knoraProject: KnoraProject,
  ): Task[List[OntologyResourceCounts]] =
    ZIO.collectAll(ontologies.map(generateResourceCountsForOntology(_, knoraProject)))

  private def fetchOntologyWithClasses(ontology: Ontology): Task[OntologyWithClasses] =
    for {
      classInfo        <- projectsRepo.getClassesFromOntology(ontology.iri)
      availableClasses <- ZIO.foreach(classInfo)(validateAndCreateAvailableClass)
      ontologyWithClasses = OntologyWithClasses(
                              iri = ontology.iri,
                              label = ontology.label,
                              classes = availableClasses,
                            )
    } yield ontologyWithClasses

  private def validateAndCreateAvailableClass(classInfo: (String, Map[String, String])): Task[AvailableClass] = {
    val (classIri, labels) = classInfo
    for {
      classIriValidated <- ZIO
                             .fromEither(ClassIri.from(classIri))
                             .mapError(msg => new IllegalArgumentException(s"Invalid class IRI: $msg"))
      validatedLabels <- ZIO
                           .fromEither(MultilingualText.from(labels))
                           .mapError(msg => new IllegalArgumentException(s"Invalid multilingual labels: $msg"))
    } yield AvailableClass(iri = classIriValidated, labels = validatedLabels)
  }

  private def generateResourceCountsForOntology(
    ontology: Ontology,
    knoraProject: KnoraProject,
  ): Task[OntologyResourceCounts] =
    for {
      classInfo      <- projectsRepo.getClassesFromOntology(ontology.iri)
      classIris       = extractClassIris(classInfo)
      instanceCounts <- projectsRepo.countInstancesByClasses(knoraProject.shortcode, knoraProject.shortname, classIris)
      classCounts    <- ZIO.foreach(classInfo)(createClassCountFromInfo(_, instanceCounts))
      resourceCounts  = OntologyResourceCounts(ontologyLabel = ontology.label, classes = classCounts)
    } yield resourceCounts

  private def extractClassIris(classInfo: List[(String, Map[String, String])]): List[String] =
    classInfo.map(_._1)

  private def createClassCountFromInfo(
    classInfo: (String, Map[String, String]),
    instanceCounts: Map[String, Int],
  ): Task[ClassCount] = {
    val (classIri, _) = classInfo
    for {
      classIriValidated <- ZIO
                             .fromEither(ClassIri.from(classIri))
                             .mapError(msg => new IllegalArgumentException(s"Invalid class IRI: $msg"))
      count = instanceCounts.getOrElse(classIri, 0)
    } yield ClassCount(iri = classIriValidated, instanceCount = count)
  }

  private def convertKnoraProjectToV3Info(
    knoraProject: KnoraProject,
    ontologies: List[OntologyWithClasses],
    lists: List[ListPreview],
  ): Task[ProjectInfo] =
    for {
      projectIri <- ZIO
                      .fromEither(DomainTypes.ProjectIri.from(knoraProject.id.value))
                      .mapError(msg => new IllegalArgumentException(s"Invalid project IRI: $msg"))
      descriptions <- convertMultiLangDescriptions(knoraProject.description.toList)
      shortcodeValidated <- ZIO
                              .fromEither(ProjectShortcode.from(knoraProject.shortcode.value))
                              .mapError(msg => new IllegalArgumentException(s"Invalid shortcode: $msg"))
      shortnameValidated <- ZIO
                              .fromEither(ProjectShortname.from(knoraProject.shortname.value))
                              .mapError(msg => new IllegalArgumentException(s"Invalid shortname: $msg"))
      projectInfo = ProjectInfo(
                      shortcode = shortcodeValidated,
                      shortname = shortnameValidated,
                      iri = projectIri,
                      fullName = knoraProject.longname.map(_.value),
                      description = descriptions,
                      status = knoraProject.status.value,
                      lists = lists,
                      ontologies = ontologies,
                    )
    } yield projectInfo

  private def convertMultiLangDescriptions(
    descriptions: List[Description],
  ): Task[MultilingualText] =
    val descriptionsMap = buildLanguageMapFromDescriptions(descriptions)
    ZIO
      .fromEither(MultilingualText.from(descriptionsMap))
      .mapError(msg => new IllegalArgumentException(s"Invalid multilingual descriptions: $msg"))

  private def buildLanguageMapFromDescriptions(descriptions: List[Description]): Map[String, String] =
    descriptions.map { desc =>
      val lang = desc.value.language.getOrElse("en")
      lang -> desc.value.value
    }.toMap

  private def createOntologyFromReadOntologyV2(
    readOntologyV2: ReadOntologyV2,
  ): Task[Ontology] =
    for {
      ontologyIri <- ZIO
                       .fromEither(OntologyIri.from(readOntologyV2.ontologyMetadata.ontologyIri.toString))
                       .mapError(msg => new IllegalArgumentException(s"Invalid ontology IRI: $msg"))
      // BR: Ontology label is required - all ontologies must have a meaningful label
      ontologyLabel <- ZIO
                         .fromOption(readOntologyV2.ontologyMetadata.label)
                         .orElseFail(new IllegalStateException(s"Ontology ${ontologyIri.value} has no label defined"))
      ontology = Ontology(
                   iri = ontologyIri,
                   label = ontologyLabel,
                 )
    } yield ontology

  private def createListPreviewFromADM(
    listInfo: ListRootNodeInfoADM,
  ): Task[ListPreview] =
    for {
      listIri <- ZIO
                   .fromEither(ListIri.from(listInfo.id))
                   .mapError(msg => new IllegalArgumentException(s"Invalid list IRI: $msg"))
      labelsMap = ProjectsService.convertStringLiteralSequenceToMap(listInfo.labels)
      labels <- ZIO
                  .fromEither(MultilingualText.from(labelsMap))
                  .mapError(msg => new IllegalArgumentException(s"Invalid multilingual labels: $msg"))
      listPreview = ListPreview(
                      iri = listIri,
                      labels = labels,
                    )
    } yield listPreview
}

object ProjectsService {
  val layer = ZLayer.derive[ProjectsService]

  def convertStringLiteralSequenceToMap(
    literals: StringLiteralSequenceV2,
  ): Map[String, String] =
    buildLanguageMapFromStringLiterals(literals.stringLiterals)

  private def buildLanguageMapFromStringLiterals(
    stringLiterals: Seq[StringLiteralV2],
  ): Map[String, String] =
    stringLiterals.map { literal =>
      val lang = literal.language.getOrElse("en")
      lang -> literal.value
    }.toMap
}
