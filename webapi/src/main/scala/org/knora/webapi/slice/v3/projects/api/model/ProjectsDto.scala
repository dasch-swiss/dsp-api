/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.v3.projects.api.model

import sttp.tapir.Codec
import sttp.tapir.CodecFormat
import sttp.tapir.Schema
import sttp.tapir.Validator
import zio.json.*

import org.knora.webapi.slice.v3.projects.domain.model.*
import org.knora.webapi.slice.v3.projects.domain.model.DomainTypes.*

object ProjectsDto {

  final case class ProjectShortcodeParam(value: String) {
    def toDomain: Either[String, ProjectShortcode] = ProjectShortcode.from(value)
  }

  object ProjectShortcodeParam {
    private val shortcodeValidator: Validator[String] = Validator.pattern("^[0-9A-F]{4}$")

    def from(value: String): Either[String, ProjectShortcodeParam] =
      shortcodeValidator.apply(value) match {
        case Nil => Right(ProjectShortcodeParam(value))
        case errors =>
          Left(
            s"Invalid project shortcode format. Expected 4-character uppercase hex string, got: $value. Errors: ${errors.mkString(", ")}",
          )
      }

    given Codec[String, ProjectShortcodeParam, CodecFormat.TextPlain] =
      Codec
        .parsedString(ProjectShortcodeParam.apply)
        .validate(shortcodeValidator.contramap(_.value))

    given Schema[ProjectShortcodeParam] = Schema.string
      .description(
        "Project shortcode - a 4-character uppercase hexadecimal identifier (e.g., '0001', '08FF'). Must be exactly 4 uppercase hexadecimal characters.",
      )
      .encodedExample("08FF")

    given JsonCodec[ProjectShortcodeParam] = JsonCodec.string.transformOrFail(
      from,
      _.value,
    )
  }

  final case class ProjectResponseDto(
    shortcode: String,
    shortname: String,
    iri: String,
    fullName: Option[String],
    description: Map[String, String],
    status: Boolean,
    lists: List[ListPreviewResponseDto],
    ontologies: List[OntologyResponseDto],
  )

  final case class ResourceCountsResponseDto(
    counts: List[OntologyResourceCountsResponseDto],
  )

  final case class ListPreviewResponseDto(
    iri: String,
    labels: Map[String, String],
  )

  final case class OntologyResponseDto(
    iri: String,
    label: String,
    classes: List[AvailableClassResponseDto],
  )

  final case class AvailableClassResponseDto(
    iri: String,
    labels: Map[String, String],
  )

  final case class OntologyResourceCountsResponseDto(
    ontologyLabel: String,
    classes: List[ClassCountResponseDto],
  )

  final case class ClassCountResponseDto(
    iri: String,
    instanceCount: Int,
  )

  implicit val availableClassResponseDtoCodec: JsonCodec[AvailableClassResponseDto] =
    DeriveJsonCodec.gen[AvailableClassResponseDto]
  implicit val classCountResponseDtoCodec: JsonCodec[ClassCountResponseDto] = DeriveJsonCodec.gen[ClassCountResponseDto]
  implicit val ontologyResourceCountsResponseDtoCodec: JsonCodec[OntologyResourceCountsResponseDto] =
    DeriveJsonCodec.gen[OntologyResourceCountsResponseDto]
  implicit val resourceCountsResponseDtoCodec: JsonCodec[ResourceCountsResponseDto] =
    DeriveJsonCodec.gen[ResourceCountsResponseDto]
  implicit val ontologyResponseDtoCodec: JsonCodec[OntologyResponseDto] = DeriveJsonCodec.gen[OntologyResponseDto]
  implicit val listPreviewResponseDtoCodec: JsonCodec[ListPreviewResponseDto] =
    DeriveJsonCodec.gen[ListPreviewResponseDto]
  implicit val projectResponseDtoCodec: JsonCodec[ProjectResponseDto] = DeriveJsonCodec.gen[ProjectResponseDto]

  object ProjectResponseDto {
    def from(project: ProjectInfo): ProjectResponseDto =
      ProjectResponseDto(
        shortcode = project.shortcode.value,
        shortname = project.shortname.value,
        iri = project.iri.value,
        fullName = project.fullName,
        description = MultilingualText.toMap(project.description),
        status = project.status,
        lists = project.lists.map(ListPreviewResponseDto.from),
        ontologies = project.ontologies.map(OntologyResponseDto.from),
      )
  }

  object ResourceCountsResponseDto {
    def from(counts: List[OntologyResourceCounts]): ResourceCountsResponseDto =
      ResourceCountsResponseDto(
        counts = counts.map(OntologyResourceCountsResponseDto.from),
      )
  }

  object ListPreviewResponseDto {
    def from(listPreview: ListPreview): ListPreviewResponseDto =
      ListPreviewResponseDto(
        iri = listPreview.iri.value,
        labels = MultilingualText.toMap(listPreview.labels),
      )
  }

  object OntologyResponseDto {
    def from(ontology: OntologyWithClasses): OntologyResponseDto =
      OntologyResponseDto(
        iri = ontology.iri.value,
        label = ontology.label,
        classes = ontology.classes.map(AvailableClassResponseDto.from),
      )
  }

  object AvailableClassResponseDto {
    def from(availableClass: AvailableClass): AvailableClassResponseDto =
      AvailableClassResponseDto(
        iri = availableClass.iri.value,
        labels = MultilingualText.toMap(availableClass.labels),
      )
  }

  object OntologyResourceCountsResponseDto {
    def from(counts: OntologyResourceCounts): OntologyResourceCountsResponseDto =
      OntologyResourceCountsResponseDto(
        ontologyLabel = counts.ontologyLabel,
        classes = counts.classes.map(ClassCountResponseDto.from),
      )
  }

  object ClassCountResponseDto {
    def from(classCount: ClassCount): ClassCountResponseDto =
      ClassCountResponseDto(
        iri = classCount.iri.value,
        instanceCount = classCount.instanceCount,
      )
  }
}
