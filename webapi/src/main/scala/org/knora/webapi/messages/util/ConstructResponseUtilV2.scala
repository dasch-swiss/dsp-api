/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.util

import zio.*

import java.time.Instant
import java.util.UUID

import dsp.errors.BadRequestException
import dsp.errors.InconsistentRepositoryDataException
import dsp.errors.NotFoundException
import dsp.errors.NotImplementedException
import dsp.valueobjects.UuidUtil
import org.knora.webapi.*
import org.knora.webapi.config.AppConfig
import org.knora.webapi.messages.IriConversions.*
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.listsmessages.ChildNodeInfoGetResponseADM
import org.knora.webapi.messages.store.triplestoremessages.*
import org.knora.webapi.messages.util.ConstructResponseRdfData.*
import org.knora.webapi.messages.util.standoff.StandoffTagUtilV2
import org.knora.webapi.messages.v2.responder.resourcemessages.ReadResourceV2
import org.knora.webapi.messages.v2.responder.resourcemessages.ReadResourcesSequenceV2
import org.knora.webapi.messages.v2.responder.standoffmessages.GetMappingResponseV2
import org.knora.webapi.messages.v2.responder.valuemessages.*
import org.knora.webapi.responders.admin.ListsResponder
import org.knora.webapi.slice.admin.domain.model.Authorship
import org.knora.webapi.slice.admin.domain.model.CopyrightHolder
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.LicenseIri
import org.knora.webapi.slice.admin.domain.model.ListProperties.ListIri
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.admin.domain.service.ProjectService
import org.knora.webapi.slice.common.ResourceIri
import org.knora.webapi.slice.common.StandoffMappingIri
import org.knora.webapi.slice.common.ValueIri
import org.knora.webapi.slice.common.domain.InternalIri
import org.knora.webapi.slice.resources.IiifImageRequestUrl
import org.knora.webapi.slice.standoff.service.StandoffMappingService
import org.knora.webapi.store.iiif.errors.SipiException

final class ConstructResponseUtilV2(
  appConfig: AppConfig,
  standoffMappingService: StandoffMappingService,
  listsResponder: ListsResponder,
  standoffTagUtilV2: StandoffTagUtilV2,
  projectService: ProjectService,
)(implicit val stringFormatter: StringFormatter) {

  /**
   * Turns a SPARQL CONSTRUCT response into a [[MainResourcesAndValueRdfData]] tree (resources, values,
   * nested dependent resources, incoming links). Fails with [[InconsistentRepositoryDataException]] if
   * the response contains a malformed resource IRI. See [[ConstructResponseRdfDataParser]] for the
   * implementation.
   */
  def splitMainResourcesAndValueRdfData(
    constructQueryResults: SparqlExtendedConstructResponse,
    requestingUser: User,
  ): IO[InconsistentRepositoryDataException, MainResourcesAndValueRdfData] =
    ConstructResponseRdfDataParser.splitMainResourcesAndValueRdfData(constructQueryResults, requestingUser)

  /**
   * Collect all mapping Iris referred to in the given value assertions.
   *
   * @param valuePropertyAssertions the given assertions (property -> value object).
   * @return a set of mapping Iris.
   */
  private def getMappingIrisFromValuePropertyAssertions(valuePropertyAssertions: RdfPropertyValues): Set[IRI] =
    valuePropertyAssertions.foldLeft(Set.empty[IRI]) {
      case (acc: Set[IRI], (_: SmartIri, valObjs: Seq[ValueRdfData])) =>
        val mappings: Seq[String] = valObjs.filter { (valObj: ValueRdfData) =>
          valObj.valueObjectClass == OntologyConstants.KnoraBase.TextValue.toSmartIri && valObj.assertions.contains(
            OntologyConstants.KnoraBase.ValueHasMapping.toSmartIri,
          )
        }.map { (textValObj: ValueRdfData) =>
          textValObj.requireIriObject(OntologyConstants.KnoraBase.ValueHasMapping.toSmartIri)
        }

        // get mappings from linked resources
        val mappingsFromReferredResources: Set[IRI] = valObjs.filter { (valObj: ValueRdfData) =>
          valObj.nestedResource.nonEmpty
        }.flatMap { (valObj: ValueRdfData) =>
          val referredRes: ResourceWithValueRdfData = valObj.nestedResource.get

          // recurse on the nested resource's values
          getMappingIrisFromValuePropertyAssertions(referredRes.valuePropertyAssertions)
        }.toSet

        acc ++ mappings ++ mappingsFromReferredResources
    }

  /**
   * Given a [[ValueRdfData]], constructs a [[TextValueContentV2]]. This method is used to process a text value
   * as returned in an API response, as well as to process a page of standoff markup that is being queried
   * separately from its text value.
   *
   * @param valueObject               the given [[ValueRdfData]].
   * @param valueObjectValueHasString the value's `knora-base:valueHasString`.
   * @param valueCommentOption        the value's comment, if any.
   * @param mappings                  the mappings needed for standoff conversions and XSL transformations.
   * @param requestingUser            the user making the request.
   * @return a [[TextValueContentV2]].
   */
  private def makeTextValueContentV2(
    valueObject: ValueRdfData,
    valueObjectValueHasString: Option[String],
    valueCommentOption: Option[String],
    mappings: Map[StandoffMappingIri, MappingAndXSLTransformation],
    requestingUser: User,
  ): Task[TextValueContentV2] = {
    // Any knora-base:TextValue may have a language
    val valueLanguageOption: Option[String] =
      valueObject.maybeStringObject(OntologyConstants.KnoraBase.ValueHasLanguage.toSmartIri)

    if (valueObject.standoff.nonEmpty) {
      for {
        mappingIri <- ZIO.foreach(valueObject.maybeIriObject(OntologyConstants.KnoraBase.ValueHasMapping.toSmartIri)) {
                        iri =>
                          ZIO
                            .fromEither(StandoffMappingIri.from(iri))
                            .mapBoth(_ => InconsistentRepositoryDataException(s"Invalid mapping IRI $iri"), identity)
                      }
        mappingAndXsltTransformation = mappingIri.flatMap(mappings.get)
        standoff                    <- standoffTagUtilV2.createStandoffTagsV2FromConstructResults(
                      standoffAssertions = valueObject.standoff,
                      requestingUser = requestingUser,
                    )
        textTypeInferred = mappingIri.map(_.value) match
                             case None                                              => TextValueType.UnformattedText
                             case Some(OntologyConstants.KnoraBase.StandardMapping) => TextValueType.FormattedText
                             case Some(iri)                                         => TextValueType.CustomFormattedText(InternalIri(iri))
        textType = valueObject
                     .maybeIriObject(OntologyConstants.KnoraBase.HasTextValueType.toSmartIri)
                     .flatMap {
                       case OntologyConstants.KnoraBase.UnformattedText     => Some(TextValueType.UnformattedText)
                       case OntologyConstants.KnoraBase.FormattedText       => Some(TextValueType.FormattedText)
                       case OntologyConstants.KnoraBase.CustomFormattedText =>
                         mappingIri.map(iri => TextValueType.CustomFormattedText(InternalIri(iri.value)))
                       case OntologyConstants.KnoraBase.UndefinedTextType => None
                       case _                                             => None
                     }
                     .getOrElse(textTypeInferred)
      } yield TextValueContentV2(
        ontologySchema = InternalSchema,
        maybeValueHasString = valueObjectValueHasString,
        textValueType = textType,
        valueHasLanguage = valueLanguageOption,
        standoff = standoff,
        mappingIri = mappingIri,
        mapping = mappingAndXsltTransformation.map(_.mapping),
        xslt = mappingAndXsltTransformation.flatMap(_.XSLTransformation),
        comment = valueCommentOption,
      )
    } else {
      // The query returned no standoff markup.
      ZIO.succeed(
        TextValueContentV2(
          ontologySchema = InternalSchema,
          maybeValueHasString = valueObjectValueHasString,
          textValueType = TextValueType.UnformattedText,
          valueHasLanguage = valueLanguageOption,
          comment = valueCommentOption,
        ),
      )
    }
  }

  /**
   * Given a [[ValueRdfData]], constructs a [[FileValueContentV2]].
   *
   * @param valueType                 the IRI of the file value type
   * @param valueObject               the given [[ValueRdfData]].
   * @param valueCommentOption        the value's comment, if any.
   * @return a [[FileValueContentV2]].
   */
  private def makeFileValueContentV2(valueType: IRI, valueObject: ValueRdfData, valueCommentOption: Option[IRI]) = {
    val licenseIri =
      valueObject.maybeIriObject(OntologyConstants.KnoraBase.HasLicense.toSmartIri).map(LicenseIri.unsafeFrom)
    val fileValue = FileValueV2(
      internalMimeType = valueObject.requireStringObject(OntologyConstants.KnoraBase.InternalMimeType.toSmartIri),
      internalFilename = valueObject.requireStringObject(OntologyConstants.KnoraBase.InternalFilename.toSmartIri),
      originalFilename = valueObject.maybeStringObject(OntologyConstants.KnoraBase.OriginalFilename.toSmartIri),
      originalMimeType = valueObject.maybeStringObject(OntologyConstants.KnoraBase.OriginalMimeType.toSmartIri),
      copyrightHolder = valueObject
        .maybeStringObject(OntologyConstants.KnoraBase.HasCopyrightHolder.toSmartIri)
        .map(CopyrightHolder.unsafeFrom),
      authorship = valueObject
        .maybeStringListObject(OntologyConstants.KnoraBase.HasAuthorship.toSmartIri)
        .map(_.map(Authorship.unsafeFrom).toList),
      licenseIri,
    )

    valueType match {
      case OntologyConstants.KnoraBase.StillImageFileValue =>
        ZIO.succeed(
          StillImageFileValueContentV2(
            ontologySchema = InternalSchema,
            fileValue = fileValue,
            dimX = valueObject.requireIntObject(OntologyConstants.KnoraBase.DimX.toSmartIri),
            dimY = valueObject.requireIntObject(OntologyConstants.KnoraBase.DimY.toSmartIri),
            comment = valueCommentOption,
          ),
        )

      case OntologyConstants.KnoraBase.StillImageVectorFileValue =>
        ZIO.succeed(
          StillImageVectorFileValueContentV2(
            ontologySchema = InternalSchema,
            fileValue = fileValue,
            comment = valueCommentOption,
          ),
        )

      case OntologyConstants.KnoraBase.StillImageExternalFileValue =>
        ZIO.succeed(
          StillImageExternalFileValueContentV2(
            InternalSchema,
            fileValue,
            IiifImageRequestUrl.unsafeFrom(
              valueObject.requireStringObject(OntologyConstants.KnoraBase.ExternalUrl.toSmartIri),
            ),
            comment = valueCommentOption,
          ),
        )

      case OntologyConstants.KnoraBase.DocumentFileValue =>
        ZIO.succeed(
          DocumentFileValueContentV2(
            ontologySchema = InternalSchema,
            fileValue = fileValue,
            pageCount = valueObject.maybeIntObject(OntologyConstants.KnoraBase.PageCount.toSmartIri),
            dimX = valueObject.maybeIntObject(OntologyConstants.KnoraBase.DimX.toSmartIri),
            dimY = valueObject.maybeIntObject(OntologyConstants.KnoraBase.DimY.toSmartIri),
            comment = valueCommentOption,
          ),
        )

      case OntologyConstants.KnoraBase.TextFileValue =>
        ZIO.succeed(
          TextFileValueContentV2(
            ontologySchema = InternalSchema,
            fileValue = fileValue,
            comment = valueCommentOption,
          ),
        )

      case OntologyConstants.KnoraBase.AudioFileValue =>
        ZIO.succeed(
          AudioFileValueContentV2(
            ontologySchema = InternalSchema,
            fileValue = fileValue,
            comment = valueCommentOption,
          ),
        )

      case OntologyConstants.KnoraBase.MovingImageFileValue =>
        ZIO.succeed(
          MovingImageFileValueContentV2(
            ontologySchema = InternalSchema,
            fileValue = fileValue,
            comment = valueCommentOption,
          ),
        )

      case OntologyConstants.KnoraBase.ArchiveFileValue =>
        ZIO.succeed(
          ArchiveFileValueContentV2(
            ontologySchema = InternalSchema,
            fileValue = fileValue,
            comment = valueCommentOption,
          ),
        )

      case _ => ZIO.fail(InconsistentRepositoryDataException(s"Unexpected file value type: $valueType"))
    }
  }

  /**
   * Given a [[ValueRdfData]], constructs a [[LinkValueContentV2]].
   *
   * @param valueObject               the given [[ValueRdfData]].
   * @param valueCommentOption        the value's comment, if any.
   * @param mappings                  the mappings needed for standoff conversions and XSL transformations.
   * @param queryStandoff             if `true`, make separate queries to get the standoff for text values.
   * @param versionDate               if defined, represents the requested time in the the resources' version history.
   * @param targetSchema              the schema of the response.
   * @param requestingUser            the user making the request.
   * @return a [[LinkValueContentV2]].
   */
  private def makeLinkValueContentV2(
    valueObject: ValueRdfData,
    valueCommentOption: Option[IRI],
    mappings: Map[StandoffMappingIri, MappingAndXSLTransformation],
    queryStandoff: Boolean,
    versionDate: Option[Instant],
    targetSchema: ApiV2Schema,
    requestingUser: User,
  ) = {
    val referredResourceIri: IRI = if (valueObject.isIncomingLink) {
      valueObject.requireIriObject(OntologyConstants.Rdf.Subject.toSmartIri)
    } else {
      valueObject.requireIriObject(OntologyConstants.Rdf.Object.toSmartIri)
    }

    for {
      referredResIri <- ZIO.fromEither(ResourceIri.from(referredResourceIri)).mapError(BadRequestException.apply)
      linkValue       = LinkValueContentV2(
                    ontologySchema = InternalSchema,
                    referredResourceIri = referredResIri,
                    isIncomingLink = valueObject.isIncomingLink,
                    nestedResource = None,
                    comment = valueCommentOption,
                  )
      // Is there a nested resource in the link value?
      result <- valueObject.nestedResource match {
                  case Some(nestedResourceAssertions: ResourceWithValueRdfData) =>
                    // Yes. Construct a ReadResourceV2 representing the nested resource.
                    constructReadResourceV2(
                      resourceIri = referredResIri,
                      resourceWithValueRdfData = nestedResourceAssertions,
                      mappings = mappings,
                      queryStandoff = queryStandoff,
                      versionDate = versionDate,
                      requestingUser = requestingUser,
                      targetSchema = targetSchema,
                    ).map(nestedResource => linkValue.copy(nestedResource = Some(nestedResource)))

                  case None =>
                    // There is no nested resource.
                    ZIO.succeed(linkValue)
                }
    } yield result
  }

  private def makeDateValueContentV2(
    valueObject: ValueRdfData,
    valueCommentOption: Option[String],
  ): DateValueContentV2 = {
    val startPrecisionStr =
      valueObject.requireStringObject(OntologyConstants.KnoraBase.ValueHasStartPrecision.toSmartIri)
    val endPrecisionStr =
      valueObject.requireStringObject(OntologyConstants.KnoraBase.ValueHasEndPrecision.toSmartIri)
    val calendarNameStr = valueObject.requireStringObject(OntologyConstants.KnoraBase.ValueHasCalendar.toSmartIri)

    DateValueContentV2(
      ontologySchema = InternalSchema,
      valueHasStartJDN = valueObject.requireIntObject(OntologyConstants.KnoraBase.ValueHasStartJDN.toSmartIri),
      valueHasEndJDN = valueObject.requireIntObject(OntologyConstants.KnoraBase.ValueHasEndJDN.toSmartIri),
      valueHasStartPrecision = DatePrecisionV2.parse(
        startPrecisionStr,
        throw InconsistentRepositoryDataException(s"Invalid date precision: $startPrecisionStr"),
      ),
      valueHasEndPrecision = DatePrecisionV2.parse(
        endPrecisionStr,
        throw InconsistentRepositoryDataException(s"Invalid date precision: $endPrecisionStr"),
      ),
      valueHasCalendar = CalendarNameV2.parse(
        calendarNameStr,
        throw InconsistentRepositoryDataException(s"Invalid calendar name: $calendarNameStr"),
      ),
      comment = valueCommentOption,
    )
  }

  private def makeIntegerValueContentV2(
    valueObject: ValueRdfData,
    valueCommentOption: Option[String],
  ): IntegerValueContentV2 =
    IntegerValueContentV2(
      ontologySchema = InternalSchema,
      valueHasInteger = valueObject.requireIntObject(OntologyConstants.KnoraBase.ValueHasInteger.toSmartIri),
      comment = valueCommentOption,
    )

  private def makeDecimalValueContentV2(
    valueObject: ValueRdfData,
    valueCommentOption: Option[String],
  ): DecimalValueContentV2 =
    DecimalValueContentV2(
      ontologySchema = InternalSchema,
      valueHasDecimal = valueObject.requireDecimalObject(OntologyConstants.KnoraBase.ValueHasDecimal.toSmartIri),
      comment = valueCommentOption,
    )

  private def makeBooleanValueContentV2(
    valueObject: ValueRdfData,
    valueCommentOption: Option[String],
  ): BooleanValueContentV2 =
    BooleanValueContentV2(
      ontologySchema = InternalSchema,
      valueHasBoolean = valueObject.requireBooleanObject(OntologyConstants.KnoraBase.ValueHasBoolean.toSmartIri),
      comment = valueCommentOption,
    )

  private def makeUriValueContentV2(
    valueObject: ValueRdfData,
    valueCommentOption: Option[String],
  ): UriValueContentV2 =
    UriValueContentV2(
      ontologySchema = InternalSchema,
      valueHasUri = valueObject.requireIriObject(OntologyConstants.KnoraBase.ValueHasUri.toSmartIri),
      comment = valueCommentOption,
    )

  private def makeColorValueContentV2(
    valueObject: ValueRdfData,
    valueCommentOption: Option[String],
  ): ColorValueContentV2 =
    ColorValueContentV2(
      ontologySchema = InternalSchema,
      valueHasColor = valueObject.requireStringObject(OntologyConstants.KnoraBase.ValueHasColor.toSmartIri),
      comment = valueCommentOption,
    )

  private def makeGeomValueContentV2(
    valueObject: ValueRdfData,
    valueCommentOption: Option[String],
  ): GeomValueContentV2 =
    GeomValueContentV2(
      ontologySchema = InternalSchema,
      valueHasGeometry = valueObject.requireStringObject(OntologyConstants.KnoraBase.ValueHasGeometry.toSmartIri),
      comment = valueCommentOption,
    )

  private def makeGeonameValueContentV2(
    valueObject: ValueRdfData,
    valueCommentOption: Option[String],
  ): GeonameValueContentV2 =
    GeonameValueContentV2(
      ontologySchema = InternalSchema,
      valueHasGeonameCode = valueObject.requireStringObject(OntologyConstants.KnoraBase.ValueHasGeonameCode.toSmartIri),
      comment = valueCommentOption,
    )

  private def makeIntervalValueContentV2(
    valueObject: ValueRdfData,
    valueCommentOption: Option[String],
  ): IntervalValueContentV2 =
    IntervalValueContentV2(
      ontologySchema = InternalSchema,
      valueHasIntervalStart =
        valueObject.requireDecimalObject(OntologyConstants.KnoraBase.ValueHasIntervalStart.toSmartIri),
      valueHasIntervalEnd =
        valueObject.requireDecimalObject(OntologyConstants.KnoraBase.ValueHasIntervalEnd.toSmartIri),
      comment = valueCommentOption,
    )

  private def makeTimeValueContentV2(
    valueObject: ValueRdfData,
    valueCommentOption: Option[String],
  ): TimeValueContentV2 =
    TimeValueContentV2(
      ontologySchema = InternalSchema,
      valueHasTimeStamp = valueObject.requireDateTimeObject(OntologyConstants.KnoraBase.ValueHasTimeStamp.toSmartIri),
      comment = valueCommentOption,
    )

  /**
   * Builds a [[HierarchicalListValueContentV2]]. In the simple schema the list node label is required and
   * is fetched via [[ListsResponder]]; in the complex schema the label is omitted.
   */
  private def makeHierarchicalListValueContentV2(
    valueObject: ValueRdfData,
    valueCommentOption: Option[String],
    targetSchema: ApiV2Schema,
    requestingUser: User,
  ): Task[HierarchicalListValueContentV2] = {
    val listNodeIri: IRI = valueObject.requireIriObject(OntologyConstants.KnoraBase.ValueHasListNode.toSmartIri)
    val listNode         = HierarchicalListValueContentV2(
      ontologySchema = InternalSchema,
      valueHasListNode = listNodeIri,
      listNodeLabel = None,
      comment = valueCommentOption,
    )

    targetSchema match {
      case ApiV2Simple =>
        listsResponder
          .listNodeInfoGetRequestADM(ListIri.unsafeFrom(listNodeIri))
          .flatMap(r =>
            ZIO
              .fromOption(r.asOpt[ChildNodeInfoGetResponseADM])
              .orElseFail(NotFoundException(s"List node not found: $listNodeIri")),
          )
          .map(_.nodeinfo.getLabelInPreferredLanguage(requestingUser.lang, appConfig.fallbackLanguage))
          .map(label => listNode.copy(listNodeLabel = label))
      case ApiV2Complex => ZIO.succeed(listNode)
    }
  }

  /**
   * Dispatches a [[ValueRdfData]] to the appropriate `make…ValueContentV2` helper based on its value class.
   */
  private def createValueContentV2FromValueRdfData(
    valueObject: ValueRdfData,
    mappings: Map[StandoffMappingIri, MappingAndXSLTransformation],
    queryStandoff: Boolean,
    versionDate: Option[Instant] = None,
    targetSchema: ApiV2Schema,
    requestingUser: User,
  ): Task[ValueContentV2] = {
    val valueObjectValueHasString =
      valueObject.maybeStringObject(OntologyConstants.KnoraBase.ValueHasString.toSmartIri)
    val valueCommentOption =
      valueObject.maybeStringObject(OntologyConstants.KnoraBase.ValueHasComment.toSmartIri)

    valueObject.valueObjectClass.toString match {
      case OntologyConstants.KnoraBase.TextValue =>
        makeTextValueContentV2(valueObject, valueObjectValueHasString, valueCommentOption, mappings, requestingUser)
      case OntologyConstants.KnoraBase.DateValue => ZIO.succeed(makeDateValueContentV2(valueObject, valueCommentOption))
      case OntologyConstants.KnoraBase.IntValue  =>
        ZIO.succeed(makeIntegerValueContentV2(valueObject, valueCommentOption))
      case OntologyConstants.KnoraBase.DecimalValue =>
        ZIO.succeed(makeDecimalValueContentV2(valueObject, valueCommentOption))
      case OntologyConstants.KnoraBase.BooleanValue =>
        ZIO.succeed(makeBooleanValueContentV2(valueObject, valueCommentOption))
      case OntologyConstants.KnoraBase.UriValue   => ZIO.succeed(makeUriValueContentV2(valueObject, valueCommentOption))
      case OntologyConstants.KnoraBase.ColorValue =>
        ZIO.succeed(makeColorValueContentV2(valueObject, valueCommentOption))
      case OntologyConstants.KnoraBase.GeomValue    => ZIO.succeed(makeGeomValueContentV2(valueObject, valueCommentOption))
      case OntologyConstants.KnoraBase.GeonameValue =>
        ZIO.succeed(makeGeonameValueContentV2(valueObject, valueCommentOption))
      case OntologyConstants.KnoraBase.ListValue =>
        makeHierarchicalListValueContentV2(valueObject, valueCommentOption, targetSchema, requestingUser)
      case OntologyConstants.KnoraBase.IntervalValue =>
        ZIO.succeed(makeIntervalValueContentV2(valueObject, valueCommentOption))
      case OntologyConstants.KnoraBase.TimeValue => ZIO.succeed(makeTimeValueContentV2(valueObject, valueCommentOption))
      case OntologyConstants.KnoraBase.LinkValue =>
        makeLinkValueContentV2(
          valueObject,
          valueCommentOption,
          mappings,
          queryStandoff,
          versionDate,
          targetSchema,
          requestingUser,
        )
      case fileValueClass if OntologyConstants.KnoraBase.FileValueClasses.contains(fileValueClass) =>
        makeFileValueContentV2(fileValueClass, valueObject, valueCommentOption)
      case other => throw NotImplementedException(s"Not implemented yet: $other")
    }
  }

  /**
   * Creates a [[ReadResourceV2]] from a [[ResourceWithValueRdfData]].
   *
   * @param resourceIri              the IRI of the resource.
   * @param resourceWithValueRdfData the Rdf data belonging to the resource.
   * @param mappings                 the mappings needed for standoff conversions and XSL transformations.
   * @param queryStandoff            if `true`, make separate queries to get the standoff for text values.
   * @param versionDate              if defined, represents the requested time in the the resources' version history.
   * @param targetSchema             the schema of the response.
   * @param requestingUser           the user making the request.
   * @return a [[ReadResourceV2]].
   */
  private def constructReadResourceV2(
    resourceIri: ResourceIri,
    resourceWithValueRdfData: ResourceWithValueRdfData,
    mappings: Map[StandoffMappingIri, MappingAndXSLTransformation],
    queryStandoff: Boolean,
    versionDate: Option[Instant],
    targetSchema: ApiV2Schema,
    requestingUser: User,
  ): Task[ReadResourceV2] = {
    val resourceLabel    = resourceWithValueRdfData.requireStringObject(OntologyConstants.Rdfs.Label.toSmartIri)
    val resourceClassStr = resourceWithValueRdfData.requireIriObject(OntologyConstants.Rdf.Type.toSmartIri)
    val resourceClass    = resourceClassStr.toSmartIriWithErr(
      throw InconsistentRepositoryDataException(
        s"Couldn't parse rdf:type of resource <$resourceIri>: <$resourceClassStr>",
      ),
    )
    val resourceAttachedToUser =
      resourceWithValueRdfData.requireIriObject(OntologyConstants.KnoraBase.AttachedToUser.toSmartIri)
    val resourceAttachedToProject =
      resourceWithValueRdfData.requireIriObject(OntologyConstants.KnoraBase.AttachedToProject.toSmartIri)
    val resourcePermissions =
      resourceWithValueRdfData.requireStringObject(OntologyConstants.KnoraBase.HasPermissions.toSmartIri)
    val resourceCreationDate =
      resourceWithValueRdfData.requireDateTimeObject(OntologyConstants.KnoraBase.CreationDate.toSmartIri)
    val resourceLastModificationDate =
      resourceWithValueRdfData.maybeDateTimeObject(OntologyConstants.KnoraBase.LastModificationDate.toSmartIri)
    val resourceDeletionInfo = deletionInfoOf(resourceWithValueRdfData)
    val resourceAuthorship   =
      resourceWithValueRdfData
        .maybeStringListObject(OntologyConstants.KnoraBase.HasResourceAuthorship.toSmartIri)
        .map(_.map(Authorship.unsafeFrom).toSeq)
        .getOrElse(Seq.empty)

    for {
      projectIri <- ZIO.fromEither(ProjectIri.from(resourceAttachedToProject)).mapError(BadRequestException.apply)
      project    <-
        projectService.findById(projectIri).someOrFail(NotFoundException(s"Project '${projectIri.value}' not found"))
      valueObjects <- ZIO.foreach(resourceWithValueRdfData.valuePropertyAssertions) { (property, valObjs) =>
                        ZIO
                          .foreach(sortValuesByOrderThenIri(valObjs)) { valObj =>
                            buildReadValueV2(valObj, mappings, queryStandoff, targetSchema, requestingUser)
                          }
                          .map(property -> _)
                      }
    } yield ReadResourceV2(
      resourceIri = resourceIri,
      resourceClassIri = resourceClass,
      label = resourceLabel,
      attachedToUser = resourceAttachedToUser,
      projectADM = project,
      permissions = resourcePermissions,
      userPermission = resourceWithValueRdfData.userPermission.get,
      values = valueObjects,
      creationDate = resourceCreationDate,
      lastModificationDate = resourceLastModificationDate,
      versionDate = versionDate,
      deletionInfo = resourceDeletionInfo,
      resourceAuthorship = resourceAuthorship,
    )
  }

  /**
   * Orders a property's values by `knora-base:valueHasOrder` (defaulting to 0), then by value IRI as a tiebreaker.
   */
  private def sortValuesByOrderThenIri(values: Seq[ValueRdfData]): Seq[ValueRdfData] =
    values
      .sortBy(_.valueIri.value)
      .sortBy(_.maybeIntObject(OntologyConstants.KnoraBase.ValueHasOrder.toSmartIri).getOrElse(0))

  /**
   * Reads `knora-base:isDeleted` / `deleteDate` / `deleteComment` off any [[RdfData]] and returns a [[DeletionInfo]]
   * when the entity is marked as deleted, otherwise `None`.
   */
  private def deletionInfoOf(rdfData: RdfData): Option[DeletionInfo] =
    rdfData
      .maybeBooleanObject(OntologyConstants.KnoraBase.IsDeleted.toSmartIri)
      .filter(identity)
      .map(_ =>
        DeletionInfo(
          deleteDate = rdfData.requireDateTimeObject(OntologyConstants.KnoraBase.DeleteDate.toSmartIri),
          maybeDeleteComment = rdfData.maybeStringObject(OntologyConstants.KnoraBase.DeleteComment.toSmartIri),
        ),
      )

  /**
   * Builds a single [[ReadValueV2]] from a [[ValueRdfData]], wrapping the value-class-specific content
   * (`LinkValueContentV2`, `TextValueContentV2`, or anything else) with the common value metadata.
   */
  private def buildReadValueV2(
    valObj: ValueRdfData,
    mappings: Map[StandoffMappingIri, MappingAndXSLTransformation],
    queryStandoff: Boolean,
    targetSchema: ApiV2Schema,
    requestingUser: User,
  ): Task[ReadValueV2] =
    for {
      valueContent <- createValueContentV2FromValueRdfData(
                        valueObject = valObj,
                        mappings = mappings,
                        queryStandoff = queryStandoff,
                        targetSchema = targetSchema,
                        requestingUser = requestingUser,
                      )
      previousValueIri <- ZIO.foreach(
                            valObj.maybeIriObject(OntologyConstants.KnoraBase.PreviousValue.toSmartIri),
                          )(iri =>
                            ZIO.fromEither(ValueIri.from(iri)).mapError(InconsistentRepositoryDataException.apply),
                          )
    } yield {
      val attachedToUser     = valObj.requireIriObject(OntologyConstants.KnoraBase.AttachedToUser.toSmartIri)
      val permissions        = valObj.requireStringObject(OntologyConstants.KnoraBase.HasPermissions.toSmartIri)
      val valueCreationDate  = valObj.requireDateTimeObject(OntologyConstants.KnoraBase.ValueCreationDate.toSmartIri)
      val valueDeletionInfo  = deletionInfoOf(valObj)
      val valueHasUUID: UUID =
        UuidUtil.decode(valObj.requireStringObject(OntologyConstants.KnoraBase.ValueHasUUID.toSmartIri))

      valueContent match {
        case linkValueContentV2: LinkValueContentV2 =>
          ReadLinkValueV2(
            valueIri = valObj.valueIri,
            attachedToUser = attachedToUser,
            permissions = permissions,
            userPermission = valObj.userPermission,
            valueCreationDate = valueCreationDate,
            valueHasUUID = valueHasUUID,
            valueContent = linkValueContentV2,
            valueHasRefCount = valObj.requireIntObject(OntologyConstants.KnoraBase.ValueHasRefCount.toSmartIri),
            previousValueIri = previousValueIri,
            deletionInfo = valueDeletionInfo,
          )

        case textValueContentV2: TextValueContentV2 =>
          ReadTextValueV2(
            valueIri = valObj.valueIri,
            attachedToUser = attachedToUser,
            permissions = permissions,
            userPermission = valObj.userPermission,
            valueCreationDate = valueCreationDate,
            valueHasUUID = valueHasUUID,
            valueContent = textValueContentV2,
            valueHasMaxStandoffStartIndex =
              valObj.maybeIntObject(OntologyConstants.KnoraBase.ValueHasMaxStandoffStartIndex.toSmartIri),
            previousValueIri = previousValueIri,
            deletionInfo = valueDeletionInfo,
          )

        case otherValueContentV2: ValueContentV2 =>
          ReadOtherValueV2(
            valueIri = valObj.valueIri,
            attachedToUser = attachedToUser,
            permissions = permissions,
            userPermission = valObj.userPermission,
            valueCreationDate = valueCreationDate,
            valueHasUUID = valueHasUUID,
            valueContent = otherValueContentV2,
            previousValueIri = previousValueIri,
            deletionInfo = valueDeletionInfo,
          )
      }
    }

  /**
   * Creates an API response.
   *
   * @param mainResourcesAndValueRdfData the query results.
   * @param orderByResourceIri           the order in which the resources should be returned. This sequence
   *                                     contains the resource IRIs received from the triplestore before filtering
   *                                     for permissions, but after filtering for duplicates.
   * @param pageSizeBeforeFiltering      the number of resources returned before filtering for permissions and duplicates.
   * @param mappings                     the mappings to convert standoff to XML, if any.
   * @param queryStandoff                if `true`, make separate queries to get the standoff for text values.
   * @param calculateMayHaveMoreResults  if `true`, calculate whether there may be more results for the query.
   * @param versionDate                  if defined, represents the requested time in the the resources' version history.
   * @param targetSchema                 the schema of response.
   * @param requestingUser               the user making the request.
   * @return a collection of [[ReadResourceV2]] representing the search results.
   */
  def createApiResponse(
    mainResourcesAndValueRdfData: MainResourcesAndValueRdfData,
    orderByResourceIri: Seq[IRI],
    pageSizeBeforeFiltering: Int,
    mappings: Map[StandoffMappingIri, MappingAndXSLTransformation] =
      Map.empty[StandoffMappingIri, MappingAndXSLTransformation],
    queryStandoff: Boolean,
    calculateMayHaveMoreResults: Boolean,
    versionDate: Option[Instant],
    targetSchema: ApiV2Schema,
    requestingUser: User,
  ): Task[ReadResourcesSequenceV2] = {

    val visibleResourceIris: Seq[ResourceIri] =
      orderByResourceIri.flatMap(ResourceIri.from(_).toOption).filter(mainResourcesAndValueRdfData.resources.keySet)

    // iterate over visibleResourceIris and construct the response in the correct order
    val readResourceFutures: Vector[Task[ReadResourceV2]] = visibleResourceIris.map { (resourceIri: ResourceIri) =>
      val data = mainResourcesAndValueRdfData.resources(resourceIri)
      constructReadResourceV2(
        resourceIri = resourceIri,
        resourceWithValueRdfData = data,
        mappings = mappings,
        queryStandoff = queryStandoff,
        versionDate = versionDate,
        targetSchema = targetSchema,
        requestingUser = requestingUser,
      )
    }.toVector

    for {
      resources <- ZIO.collectAll(readResourceFutures)

      // If we got a full page of results from the triplestore (before filtering for permissions), there
      // might be at least one more page of results that the user could request.
      mayHaveMoreResults =
        calculateMayHaveMoreResults && pageSizeBeforeFiltering == appConfig.v2.resourcesSequence.resultsPerPage
    } yield ReadResourcesSequenceV2(
      resources = resources,
      hiddenResourceIris = mainResourcesAndValueRdfData.hiddenResourceIris,
      mayHaveMoreResults = mayHaveMoreResults,
    )
  }

  /**
   * Gets mappings referred to in query results [[Map[IRI, ResourceWithValueRdfData]]].
   *
   * @param queryResultsSeparated query results referring to mappings.
   * @return the referred mappings.
   */
  def mappingsFromQueryResults(
    queryResultsSeparated: RdfResources,
  ): Task[Map[StandoffMappingIri, MappingAndXSLTransformation]] = {

    // collect the Iris of the mappings referred to in the resources' text values
    val mappingIris = queryResultsSeparated.flatMap { case (_, assertions: ResourceWithValueRdfData) =>
      getMappingIrisFromValuePropertyAssertions(assertions.valuePropertyAssertions)
    }.toSet

    for {
      mappingResponses <- ZIO.foreach(mappingIris)(iri =>
                            ZIO
                              .fromEither(StandoffMappingIri.from(iri))
                              .mapError(BadRequestException.apply)
                              .flatMap(standoffMappingService.getMappingV2),
                          )

      // get the default XSL transformations
      mappingsWithFuture =
        mappingResponses.map { (mapping: GetMappingResponseV2) =>
          for {
            // if given, get the default XSL transformation
            xsltOption <-
              ZIO.foreach(mapping.mapping.defaultXSLTransformation) { transformationIri =>
                standoffMappingService
                  .getXSLTransformation(transformationIri)
                  .mapError { case notFound: NotFoundException =>
                    SipiException(
                      s"Default XSL transformation <$transformationIri> not found for mapping <${mapping.mappingIri}>: ${notFound.message}",
                    )
                  }
              }
          } yield mapping.mappingIri -> MappingAndXSLTransformation(
            mapping = mapping.mapping,
            standoffEntities = mapping.standoffEntities,
            XSLTransformation = xsltOption,
          )

        }
      mappings <- ZIO.collectAll(mappingsWithFuture)
    } yield mappings.toMap
  }
}
object ConstructResponseUtilV2 {
  val layer = ZLayer.derive[ConstructResponseUtilV2]
}
