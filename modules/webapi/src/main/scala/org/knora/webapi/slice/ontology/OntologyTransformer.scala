/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology

import org.apache.jena.datatypes.xsd.XSDDatatype
import org.apache.jena.graph.Node
import org.apache.jena.graph.NodeFactory
import org.apache.jena.graph.Triple
import org.apache.jena.query.DatasetFactory
import org.apache.jena.rdf.model.Model
import org.apache.jena.rdf.model.Property
import org.apache.jena.rdf.model.RDFNode
import org.apache.jena.rdf.model.Resource
import org.apache.jena.riot.Lang
import org.apache.jena.riot.RDFDataMgr
import org.apache.jena.riot.RDFParser
import org.apache.jena.riot.system.StreamRDF
import org.apache.jena.riot.system.StreamRDFBase
import org.apache.jena.riot.system.StreamRDFLib
import org.apache.jena.sparql.core.Quad
import zio.Clock
import zio.Scope
import zio.Task
import zio.ZIO
import zio.ZLayer

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import scala.jdk.CollectionConverters.*

import dsp.valueobjects.UuidUtil
import org.knora.webapi.config.AppConfig
import org.knora.webapi.messages.OntologyConstants.KnoraBase
import org.knora.webapi.messages.OntologyConstants.Rdf
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionType
import org.knora.webapi.messages.util.CalendarDateRangeV2
import org.knora.webapi.messages.util.CalendarNameV2
import org.knora.webapi.messages.util.DateEraV2
import org.knora.webapi.messages.util.PermissionUtilADM
import org.knora.webapi.messages.util.standoff.StandoffStringUtil
import org.knora.webapi.messages.util.standoff.StandoffTagUtilV2
import org.knora.webapi.messages.v2.responder.standoffmessages.*
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.model.Permission
import org.knora.webapi.slice.admin.domain.model.UserIri
import org.knora.webapi.slice.admin.domain.service.KnoraGroupRepo
import org.knora.webapi.slice.admin.domain.service.KnoraUserRepo
import org.knora.webapi.slice.admin.domain.service.ProjectService
import org.knora.webapi.slice.common.ResourceIri
import org.knora.webapi.slice.common.StandoffMappingIri
import org.knora.webapi.slice.common.ValueIri
import org.knora.webapi.slice.common.jena.RdfDataMgr
import org.knora.webapi.slice.standoff.service.StandoffMappingService

final case class TransformerError(message: String)

/**
 * Supplies the metadata that cannot be derived from the input JSON-LD or from `Clock`.
 *
 * @param attachedToUser    the user every imported resource and value is attached to.
 * @param attachedToProject the project every imported resource belongs to; overrides any value in the input.
 *                          Also determines the data named graph every output quad is assigned to — derived from
 *                          the project, never from payload content (`@graph` declarations in the input are ignored).
 * @param permissions       the formatted `knora-base:hasPermissions` string used for every resource and value.
 */
final case class ConversionContext(
  attachedToUser: UserIri,
  attachedToProject: KnoraProject,
  permissions: String,
)

final class OntologyTransformer(
  sf: StringFormatter,
  standoffMappingService: StandoffMappingService,
  idSource: IdSource,
) { self =>

  /**
   * Converts a JSON-LD payload in the public Knora-API v2 representation into the internal `knora-base` graph
   * representation, written out as NQuads. The returned file is a scoped temp file: it is deleted when the caller's
   * `Scope` closes.
   */
  def toKnoraBase(rdf: Path, ctx: ConversionContext): ZIO[Scope, TransformerError, Path] =
    toInternalSchema(rdf).flatMap(restructure(_, ctx))

  /**
   * Stage 1 — streams the JSON-LD through Jena and renames every IRI from the external to the internal schema. A pure
   * IRI rename with no structural changes. Package-private so the stage-1 contract suites can target it directly.
   */
  private[ontology] def toInternalSchema(rdf: Path): ZIO[Scope, TransformerError, Path] =
    (for {
      nq <- tempFile("onto-transformer-", ".nq")
      _  <- ZIO.attemptBlocking {
             val in = new BufferedInputStream(new FileInputStream(rdf.toFile))
             try {
               val os = new BufferedOutputStream(new FileOutputStream(nq.toFile))
               try {
                 val writer = StreamRDFLib.writer(os)
                 val sink   = rewritingSink(writer)
                 sink.start()
                 try RDFParser.source(in).lang(Lang.JSONLD).parse(sink)
                 finally sink.finish()
               } finally os.close()
             } finally in.close()
           }
    } yield nq)
      .mapError(e => TransformerError(s"Failed to transform RDF: ${e.getMessage}"))

  /**
   * Stage 2 — structural transformations on the stage-1 NQuads to produce valid `knora-base`. Loads the bounded
   * intermediate file into a Jena [[Model]] so cross-triple context is available. The output is written as NQuads
   * with every quad assigned to the project's data named graph, ready to stream into the triplestore.
   */
  private def restructure(nq: Path, ctx: ConversionContext): ZIO[Scope, TransformerError, Path] =
    (for {
      now   <- Clock.instant
      model <- RdfDataMgr.loadModel(nq, Lang.NTRIPLES)
      _     <- ZIO.attempt(addResourceMetadata(model, ctx, now))
      _     <- ZIO.attempt(addValueMetadata(model, ctx, now))
      _     <- ZIO.attempt(addTextValueType(model))
      _     <- ZIO.attempt(convertDateValues(model))
      _     <- ZIO.attempt(convertLinkValues(model))
      _     <- convertRichtextValues(model, now)
      _     <- ZIO.attempt(addValueHasString(model))
      kb    <- tempFile("onto-transformer-kb-", ".nq")
      graph  = ProjectService.projectDataNamedGraphV2(ctx.attachedToProject)
      _     <- writeNQuads(model, graph.value, kb)
    } yield kb)
      .mapError(e => TransformerError(s"Failed to restructure RDF: ${e.getMessage}"))

  /** Writes the model as NQuads with every quad assigned to the given named graph. */
  private def writeNQuads(model: Model, graph: String, target: Path): zio.Task[Unit] =
    ZIO.attemptBlocking {
      val dataset = DatasetFactory.create()
      try {
        dataset.addNamedModel(graph, model)
        val os = new BufferedOutputStream(new FileOutputStream(target.toFile))
        try RDFDataMgr.write(os, dataset, Lang.NQUADS)
        finally os.close()
      } finally dataset.close()
    }

  /**
   * A temp file under the configured `tmpDatadir`, scoped to the surrounding `Scope`: created on acquire, deleted on
   * release.
   */
  private def tempFile(prefix: String, suffix: String): ZIO[Scope, Throwable, Path] =
    for {
      dir  <- AppConfig.config(_.tmpDatadir).map(Path.of(_))
      _    <- ZIO.attemptBlocking(Files.createDirectories(dir))
      file <- ZIO.acquireRelease(ZIO.attemptBlocking(Files.createTempFile(dir, prefix, suffix)))(p =>
                ZIO.attemptBlocking(Files.deleteIfExists(p)).ignore,
              )
    } yield file

  /**
   * Step 1 — synthesise the cardinality-1 `knora-base` metadata on every resource. Resources are identified by IRI
   * shape ([[ResourceIri.from]] succeeds); value nodes carry an extra `/values/` segment and are skipped here.
   *
   * `isMainResource` is intentionally not emitted: `knora-base.ttl` documents it as a SPARQL-CONSTRUCT artifact that is
   * never persisted. `lastModificationDate` is omitted on first import.
   */
  private def addResourceMetadata(model: Model, ctx: ConversionContext, now: Instant): Unit = {
    val attachedToUser    = model.createProperty(KnoraBase.AttachedToUser)
    val attachedToProject = model.createProperty(KnoraBase.AttachedToProject)
    val hasPermissions    = model.createProperty(KnoraBase.HasPermissions)
    val creationDate      = model.createProperty(KnoraBase.CreationDate)
    val isDeleted         = model.createProperty(KnoraBase.IsDeleted)

    val userResource    = model.createResource(ctx.attachedToUser.value)
    val projectResource = model.createResource(ctx.attachedToProject.id.value)
    val creationDateLit = model.createTypedLiteral(now.toString, XSDDatatype.XSDdateTime)
    val falseLit        = model.createTypedLiteral("false", XSDDatatype.XSDboolean)

    val resources = model.listSubjects().asScala.filter { s =>
      s.isURIResource && ResourceIri.from(s.getURI).isRight
    }
    resources.foreach { r =>
      r.addProperty(attachedToUser, userResource)
      r.addProperty(attachedToProject, projectResource)
      r.addProperty(hasPermissions, ctx.permissions)
      r.addProperty(creationDate, creationDateLit)
      r.addProperty(isDeleted, falseLit)
    }
  }

  /**
   * Step 2 — synthesise the cardinality-1 `knora-base` metadata on every value. Values are identified by IRI shape
   * ([[ValueIri.from]] succeeds) and keep their input IRI; `valueHasUUID` is the IRI's own UUID segment. Any incoming
   * system metadata is dropped first so synthesized values win. `valueHasString` is deferred.
   */
  private def addValueMetadata(model: Model, ctx: ConversionContext, now: Instant): Unit = {
    val attachedToUser    = model.createProperty(KnoraBase.AttachedToUser)
    val hasPermissions    = model.createProperty(KnoraBase.HasPermissions)
    val isDeleted         = model.createProperty(KnoraBase.IsDeleted)
    val valueCreationDate = model.createProperty(KnoraBase.ValueCreationDate)
    val valueHasUUID      = model.createProperty(KnoraBase.ValueHasUUID)

    val userResource    = model.createResource(ctx.attachedToUser.value)
    val creationDateLit = model.createTypedLiteral(now.toString, XSDDatatype.XSDdateTime)
    val falseLit        = model.createTypedLiteral("false", XSDDatatype.XSDboolean)

    model.listSubjects().asScala.flatMap(s => asValueIri(s).map((s, _))).foreach { case (v, iri) =>
      v.removeAll(attachedToUser)
        .removeAll(hasPermissions)
        .removeAll(valueCreationDate)
        .removeAll(valueHasUUID)
        .removeAll(isDeleted)
      v.addProperty(attachedToUser, userResource)
      v.addProperty(hasPermissions, ctx.permissions)
      v.addProperty(valueCreationDate, creationDateLit)
      v.addProperty(valueHasUUID, iri.valueId.value)
      v.addProperty(isDeleted, falseLit)
    }
  }

  /**
   * Sets `knora-base:hasTextValueType` on every `TextValue`: `FormattedText` when it carries the rich-text
   * `textValueAsXml`, otherwise `UnformattedText`. This is value-type metadata orthogonal to the standoff
   * conversion, so it is its own step; it must run before [[convertRichtextValues]] drops `textValueAsXml`.
   * Mirrors the create path, which persists `hasTextValueType` on every text value.
   */
  private def addTextValueType(model: Model): Unit = {
    val rdfType          = model.createProperty(Rdf.Type)
    val textValueType    = KnoraBase.TextValue
    val textValueAsXml   = model.createProperty(KnoraBase.KnoraBasePrefixExpansion + "textValueAsXml")
    val hasTextValueType = model.createProperty(KnoraBase.HasTextValueType)
    val formattedText    = model.createResource(KnoraBase.FormattedText)
    val unformattedText  = model.createResource(KnoraBase.UnformattedText)

    val textValues = model
      .listSubjects()
      .asScala
      .filter { s =>
        isValue(s) &&
        Option(s.getProperty(rdfType))
          .map(_.getObject)
          .exists(n => n.isURIResource && n.asResource.getURI == textValueType)
      }
      .toList

    textValues.foreach { v =>
      val valueType = if (v.hasProperty(textValueAsXml)) formattedText else unformattedText
      v.addProperty(hasTextValueType, valueType)
    }
  }

  /**
   * Step 3 — collapse the v2 `dateValueHas{Calendar,Start*,End*}` properties of every `DateValue` into the
   * `knora-base` JDN form: `valueHasCalendar`, `valueHasStart/EndJDN`, `valueHasStart/EndPrecision`, plus a
   * `valueHasString` rendering of the date range. Eras are not stored in `knora-base`; they only feed the JDN math.
   * JDN computation and the date-range string are delegated to [[CalendarDateRangeV2]].
   */
  private def convertDateValues(model: Model): Unit = {
    val rdfType   = model.createProperty(Rdf.Type)
    val dateValue = KnoraBase.DateValue

    // The v2 date properties, renamed into the knora-base namespace by stage 1.
    val srcCalendar   = model.createProperty(KnoraBase.KnoraBasePrefixExpansion + "dateValueHasCalendar")
    val srcStartYear  = model.createProperty(KnoraBase.KnoraBasePrefixExpansion + "dateValueHasStartYear")
    val srcStartMonth = model.createProperty(KnoraBase.KnoraBasePrefixExpansion + "dateValueHasStartMonth")
    val srcStartDay   = model.createProperty(KnoraBase.KnoraBasePrefixExpansion + "dateValueHasStartDay")
    val srcStartEra   = model.createProperty(KnoraBase.KnoraBasePrefixExpansion + "dateValueHasStartEra")
    val srcEndYear    = model.createProperty(KnoraBase.KnoraBasePrefixExpansion + "dateValueHasEndYear")
    val srcEndMonth   = model.createProperty(KnoraBase.KnoraBasePrefixExpansion + "dateValueHasEndMonth")
    val srcEndDay     = model.createProperty(KnoraBase.KnoraBasePrefixExpansion + "dateValueHasEndDay")
    val srcEndEra     = model.createProperty(KnoraBase.KnoraBasePrefixExpansion + "dateValueHasEndEra")
    val srcProps      =
      List(
        srcCalendar,
        srcStartYear,
        srcStartMonth,
        srcStartDay,
        srcStartEra,
        srcEndYear,
        srcEndMonth,
        srcEndDay,
        srcEndEra,
      )

    val valueHasCalendar       = model.createProperty(KnoraBase.ValueHasCalendar)
    val valueHasStartJDN       = model.createProperty(KnoraBase.ValueHasStartJDN)
    val valueHasEndJDN         = model.createProperty(KnoraBase.ValueHasEndJDN)
    val valueHasStartPrecision = model.createProperty(KnoraBase.ValueHasStartPrecision)
    val valueHasEndPrecision   = model.createProperty(KnoraBase.ValueHasEndPrecision)
    val valueHasString         = model.createProperty(KnoraBase.ValueHasString)

    def stringOf(v: Resource, p: Property): Option[String] =
      Option(v.getProperty(p)).map(_.getObject).collect { case n if n.isLiteral => n.asLiteral.getLexicalForm }
    def intOf(v: Resource, p: Property): Option[Int] =
      Option(v.getProperty(p)).map(_.getObject).collect { case n if n.isLiteral => n.asLiteral.getInt }
    def eraOf(v: Resource, p: Property): Option[DateEraV2] =
      stringOf(v, p).flatMap(DateEraV2.fromString(_).toOption)

    val dateValues = model.listSubjects().asScala.filter { s =>
      asValueIri(s).isDefined &&
      Option(s.getProperty(rdfType)).map(_.getObject).exists(n => n.isURIResource && n.asResource.getURI == dateValue)
    }

    def required[A](opt: Option[A], what: String, v: Resource): A =
      opt.getOrElse(throw new IllegalArgumentException(s"DateValue $v has no $what"))

    dateValues.foreach { v =>
      val calendarStr  = required(stringOf(v, srcCalendar), "calendar", v)
      val calendarName =
        CalendarNameV2.fromString(calendarStr).fold(msg => throw new IllegalArgumentException(msg), identity)

      val range = CalendarDateRangeV2
        .fromComponents(
          calendarName,
          required(intOf(v, srcStartYear), "start year", v),
          intOf(v, srcStartMonth),
          intOf(v, srcStartDay),
          eraOf(v, srcStartEra),
          required(intOf(v, srcEndYear), "end year", v),
          intOf(v, srcEndMonth),
          intOf(v, srcEndDay),
          eraOf(v, srcEndEra),
        )
        .fold(msg => throw new IllegalArgumentException(msg), identity)
      val (startJDN, endJDN) = range.toJulianDayRange

      srcProps.foreach(v.removeAll)
      v.addProperty(valueHasCalendar, calendarStr)
      v.addProperty(valueHasStartJDN, model.createTypedLiteral(startJDN.toString, XSDDatatype.XSDinteger))
      v.addProperty(valueHasEndJDN, model.createTypedLiteral(endJDN.toString, XSDDatatype.XSDinteger))
      v.addProperty(valueHasStartPrecision, range.startCalendarDate.precision.toString)
      v.addProperty(valueHasEndPrecision, range.endCalendarDate.precision.toString)
      v.addProperty(valueHasString, range.toString)
    }
  }

  /**
   * Step 4 — reify every `LinkValue` as an `rdf:Statement` and add the direct-link triple, mirroring
   * `ResourcesRepoLive.buildLinkValuePatterns`. The link property is found from the unique `<resource> <linkProp>
   * <value>` edge; the direct property is the link property without its `Value` suffix. The `linkValueHasTargetIri`
   * is replaced by `rdf:subject`/`rdf:predicate`/`rdf:object` plus `valueHasRefCount` (always 1 for an explicit user
   * link) and a `valueHasString` of the target IRI. Standoff (salsah-link) links are separate system LinkValues on the
   * `hasStandoffLinkTo` rail, emitted by [[convertRichtextValues]]; they never merge into these.
   */
  private def convertLinkValues(model: Model): Unit = {
    val rdfType            = model.createProperty(Rdf.Type)
    val linkValueType      = KnoraBase.LinkValue
    val linkValueHasTarget = model.createProperty(KnoraBase.KnoraBasePrefixExpansion + "linkValueHasTargetIri")
    val rdfSubject         = model.createProperty(Rdf.Subject)
    val rdfPredicate       = model.createProperty(Rdf.Predicate)
    val rdfObject          = model.createProperty(Rdf.Object)
    val valueHasRefCount   = model.createProperty(KnoraBase.ValueHasRefCount)
    val valueHasString     = model.createProperty(KnoraBase.ValueHasString)

    val linkValues = model.listSubjects().asScala.filter { s =>
      isValue(s) &&
      Option(s.getProperty(rdfType))
        .map(_.getObject)
        .exists(n => n.isURIResource && n.asResource.getURI == linkValueType)
    }

    linkValues.foreach { v =>
      val parent = model.listStatements(null, null, v).asScala.toList match {
        case st :: Nil => st
        case other     =>
          throw new IllegalArgumentException(s"LinkValue $v must have exactly one incoming edge, found ${other.size}")
      }
      val resource = parent.getSubject
      val linkProp = parent.getPredicate.getURI
      val target   = Option(v.getProperty(linkValueHasTarget))
        .map(_.getObject)
        .collect { case n if n.isURIResource => n.asResource }
        .getOrElse(throw new IllegalArgumentException(s"LinkValue $v has no linkValueHasTargetIri"))

      val directProp = model.createProperty(linkProp.stripSuffix("Value"))

      resource.addProperty(directProp, target)
      v.removeAll(linkValueHasTarget)
      v.addProperty(rdfSubject, resource)
      v.addProperty(rdfPredicate, model.createResource(directProp.getURI))
      v.addProperty(rdfObject, target)
      v.addProperty(valueHasRefCount, model.createTypedLiteral("1", XSDDatatype.XSDinteger))
      v.addProperty(valueHasString, target.getURI)
    }
  }

  /**
   * Step 5 — convert every rich-text `TextValue` (one carrying `textValueAsXml`) to `knora-base` standoff, reusing
   * `StandoffTagUtilV2.convertXMLtoStandoffTagV2` and the standard mapping exactly as the resource-create path does.
   * Emits the standoff-tag nodes, `valueHasStandoff`, `valueHasMaxStandoffStartIndex` and `valueHasMapping`, sets
   * `valueHasString` to the plain-text projection and drops `textValueAsXml`. The naive-renamed
   * `knora-base#textValueHasMapping` has no knora-base equivalent and is removed from every text value.
   *
   * This runs as a real effect (it loads the mapping and mints tag UUIDs), so rejections use `ZIO.fail` and every
   * synchronous throw is wrapped in `ZIO.attempt`, keeping them in the typed error channel that `restructure`'s
   * `.mapError` turns into a `TransformerError`.
   */
  private def convertRichtextValues(model: Model, now: Instant): Task[Unit] = {
    val rdfType        = model.createProperty(Rdf.Type)
    val textValueType  = KnoraBase.TextValue
    val textValueAsXml = model.createProperty(KnoraBase.KnoraBasePrefixExpansion + "textValueAsXml")

    // Materialise before mutating: this pass mints new tag and LinkValue nodes into the same model while iterating.
    val richtextValues = model
      .listSubjects()
      .asScala
      .filter(s =>
        isValue(s) &&
          Option(s.getProperty(rdfType))
            .map(_.getObject)
            .exists(n => n.isURIResource && n.asResource.getURI == textValueType) &&
          s.hasProperty(textValueAsXml),
      )
      .toList

    for {
      perValue <- ZIO.foreach(richtextValues)(convertRichtextValue(model, _))
      _        <- ZIO.attempt(dropTextValueHasMapping(model))
      _        <- emitStandoffLinkValues(model, perValue, now)
    } yield ()
  }

  /** Converts one rich-text value and returns its owning resource and the salsah-link targets it references. */
  private def convertRichtextValue(model: Model, v: Resource): Task[(Resource, Set[String])] =
    for {
      xml     <- ZIO.attempt(requireTextValueAsXml(model, v))
      _       <- rejectCustomMapping(model, v)
      mapping <- standoffMappingService.getMappingV2(StandoffMappingIri.StandardMapping)
      tws     <-
        ZIO.attempt(StandoffTagUtilV2.convertXMLtoStandoffTagV2(xml, mapping, acceptStandoffLinksToClientIDs = false))
      tags  <- ZIO.foreach(tws.standoffTagV2)(tag => idSource.freshStandoffTagUuid.map(uuid => tag.copy(uuid = uuid)))
      owner <- ZIO.attempt(owningResource(model, v))
      _     <- ZIO.attempt(emitStandoff(model, v, tws.text, tags))
    } yield (owner, standoffLinkTargets(tags))

  private def requireTextValueAsXml(model: Model, v: Resource): String = {
    val textValueAsXml = model.createProperty(KnoraBase.KnoraBasePrefixExpansion + "textValueAsXml")
    Option(v.getProperty(textValueAsXml))
      .map(_.getObject)
      .collect { case n if n.isLiteral => n.asLiteral.getLexicalForm }
      .getOrElse(throw new IllegalArgumentException(s"TextValue $v has no textValueAsXml"))
  }

  /** Rejects a `TextValue` referencing a non-standard mapping (REQ-6.2), before the mapping is loaded. */
  private def rejectCustomMapping(model: Model, v: Resource): Task[Unit] = {
    val textValueHasMapping = model.createProperty(KnoraBase.KnoraBasePrefixExpansion + "textValueHasMapping")
    Option(v.getProperty(textValueHasMapping))
      .map(_.getObject)
      .collect { case n if n.isURIResource => n.asResource.getURI } match {
      case Some(iri) if iri != KnoraBase.StandardMapping =>
        ZIO.fail(
          new IllegalArgumentException(
            s"TextValue $v references unsupported custom mapping <$iri>; only the standard mapping is supported",
          ),
        )
      case _ => ZIO.unit
    }
  }

  /** Removes the naive-renamed `knora-base#textValueHasMapping` (which has no knora-base equivalent) everywhere. */
  private def dropTextValueHasMapping(model: Model): Unit = {
    val textValueHasMapping = model.createProperty(KnoraBase.KnoraBasePrefixExpansion + "textValueHasMapping")
    val _                   = model.removeAll(null, textValueHasMapping, null)
  }

  private def emitStandoff(model: Model, v: Resource, plainText: String, tags: Seq[StandoffTagV2]): Unit = {
    val textValueAsXml                = model.createProperty(KnoraBase.KnoraBasePrefixExpansion + "textValueAsXml")
    val valueHasString                = model.createProperty(KnoraBase.ValueHasString)
    val valueHasMapping               = model.createProperty(KnoraBase.ValueHasMapping)
    val valueHasStandoff              = model.createProperty(KnoraBase.ValueHasStandoff)
    val valueHasMaxStandoffStartIndex = model.createProperty(KnoraBase.ValueHasMaxStandoffStartIndex)

    val valueIri        = v.getURI
    val startIndexToIri =
      tags.map(t => t.startIndex -> StandoffStringUtil.makeRandomStandoffTagIri(valueIri, t.startIndex)).toMap

    v.removeAll(textValueAsXml)
    v.removeAll(valueHasString)
    v.addProperty(valueHasString, plainText)
    v.addProperty(valueHasMapping, model.createResource(KnoraBase.StandardMapping))
    v.addProperty(valueHasMaxStandoffStartIndex, intLiteral(model, tags.map(_.startIndex).max))
    tags.foreach { tag =>
      val tagRes = model.createResource(startIndexToIri(tag.startIndex))
      v.addProperty(valueHasStandoff, tagRes)
      emitStandoffTag(model, tagRes, tag, startIndexToIri)
    }
  }

  private def emitStandoffTag(
    model: Model,
    tagRes: Resource,
    tag: StandoffTagV2,
    startIndexToIri: Map[Int, String],
  ): Unit = {
    val rdfType                     = model.createProperty(Rdf.Type)
    val standoffTagHasStart         = model.createProperty(KnoraBase.StandoffTagHasStart)
    val standoffTagHasEnd           = model.createProperty(KnoraBase.StandoffTagHasEnd)
    val standoffTagHasStartIndex    = model.createProperty(KnoraBase.StandoffTagHasStartIndex)
    val standoffTagHasEndIndex      = model.createProperty(KnoraBase.StandoffTagHasEndIndex)
    val standoffTagHasStartParent   = model.createProperty(KnoraBase.StandoffTagHasStartParent)
    val standoffTagHasEndParent     = model.createProperty(KnoraBase.StandoffTagHasEndParent)
    val standoffTagHasOriginalXMLID = model.createProperty(KnoraBase.StandoffTagHasOriginalXMLID)
    val standoffTagHasUUID          = model.createProperty(KnoraBase.StandoffTagHasUUID)

    tagRes.addProperty(rdfType, model.createResource(tag.standoffTagClassIri.toString))
    tagRes.addProperty(standoffTagHasStart, intLiteral(model, tag.startPosition))
    tagRes.addProperty(standoffTagHasEnd, intLiteral(model, tag.endPosition))
    tagRes.addProperty(standoffTagHasStartIndex, intLiteral(model, tag.startIndex))
    tag.endIndex.foreach(i => tagRes.addProperty(standoffTagHasEndIndex, intLiteral(model, i)))
    tag.startParentIndex.foreach(p =>
      tagRes.addProperty(standoffTagHasStartParent, model.createResource(startIndexToIri(p))),
    )
    tag.endParentIndex.foreach(p =>
      tagRes.addProperty(standoffTagHasEndParent, model.createResource(startIndexToIri(p))),
    )
    tag.originalXMLID.foreach(id => tagRes.addProperty(standoffTagHasOriginalXMLID, id))
    tagRes.addProperty(standoffTagHasUUID, UuidUtil.base64Encode(tag.uuid))
    tag.attributes.foreach(attr => emitStandoffAttribute(model, tagRes, attr))
  }

  /** Emits one triple per standoff-tag attribute, typing the object as `standoffAttributeLiterals` does on write. */
  private def emitStandoffAttribute(model: Model, tagRes: Resource, attr: StandoffTagAttributeV2): Unit = {
    val p = model.createProperty(attr.standoffPropertyIri.toString)
    val _ = attr match {
      case a: StandoffTagIriAttributeV2               => tagRes.addProperty(p, model.createResource(a.value))
      case a: StandoffTagInternalReferenceAttributeV2 => tagRes.addProperty(p, model.createResource(a.value))
      case a: StandoffTagUriAttributeV2               =>
        tagRes.addProperty(p, model.createTypedLiteral(a.value, XSDDatatype.XSDanyURI))
      case a: StandoffTagStringAttributeV2  => tagRes.addProperty(p, a.value)
      case a: StandoffTagIntegerAttributeV2 => tagRes.addProperty(p, intLiteral(model, a.value))
      case a: StandoffTagDecimalAttributeV2 =>
        tagRes.addProperty(p, model.createTypedLiteral(a.value.toString, XSDDatatype.XSDdecimal))
      case a: StandoffTagBooleanAttributeV2 =>
        tagRes.addProperty(p, model.createTypedLiteral(a.value.toString, XSDDatatype.XSDboolean))
      case a: StandoffTagTimeAttributeV2 =>
        tagRes.addProperty(p, model.createTypedLiteral(a.value.toString, XSDDatatype.XSDdateTime))
    }
  }

  private def intLiteral(model: Model, n: Int) = model.createTypedLiteral(n.toString, XSDDatatype.XSDinteger)

  /** Recovers a value's owning resource from its single incoming edge, mirroring [[convertLinkValues]]. */
  private def owningResource(model: Model, v: Resource): Resource =
    model.listStatements(null, null, v).asScala.toList match {
      case st :: Nil => st.getSubject
      case other     =>
        throw new IllegalArgumentException(s"TextValue $v must have exactly one incoming edge, found ${other.size}")
    }

  /** The distinct salsah-link targets a text value links to: the `standoffTagHasLink` of its `StandoffLinkTag` tags. */
  private def standoffLinkTargets(tags: Seq[StandoffTagV2]): Set[String] =
    tags.flatMap { tag =>
      if (tag.dataType.contains(StandoffDataTypeClasses.StandoffLinkTag))
        tag.attributes.collectFirst {
          case a: StandoffTagIriAttributeV2 if a.standoffPropertyIri.toString == KnoraBase.StandoffTagHasLink => a.value
        }
      else None
    }.toSet

  /**
   * Emits the system `hasStandoffLinkTo`/`hasStandoffLinkToValue` LinkValues for the salsah-links across a resource's
   * text values, mirroring `CreateResourceV2Handler.generateInsertSparqlForStandoffLinksInMultipleValues`. The
   * reference count per target is the number of the resource's text values linking to it (multiple links to one target
   * within a single text value collapse to one, via the per-value `Set`). Resources and targets are processed in
   * sorted order so the `IdSource`-derived LinkValue IRIs/UUIDs are reproducible.
   */
  private def emitStandoffLinkValues(model: Model, perValue: Seq[(Resource, Set[String])], now: Instant): Task[Unit] =
    ZIO.foreachDiscard(perValue.groupBy(_._1.getURI).toSeq.sortBy(_._1)) { case (_, group) =>
      val resource = group.head._1
      val counts   = group.flatMap(_._2).groupBy(identity).view.mapValues(_.size).toMap
      ZIO.foreachDiscard(counts.keys.toList.sorted) { target =>
        for {
          resourceIri  <- ZIO.fromEither(ResourceIri.from(resource.getURI)).mapError(new IllegalArgumentException(_))
          linkValueIri <- idSource.freshLinkValueIri(resourceIri)
          _            <- ZIO.attempt(emitStandoffLinkValue(model, resource, target, counts(target), linkValueIri, now))
        } yield ()
      }
    }

  private def emitStandoffLinkValue(
    model: Model,
    resource: Resource,
    target: String,
    refCount: Int,
    linkValueIri: ValueIri,
    now: Instant,
  ): Unit = {
    val hasStandoffLinkTo      = model.createProperty(KnoraBase.HasStandoffLinkTo)
    val hasStandoffLinkToValue = model.createProperty(KnoraBase.HasStandoffLinkToValue)
    val rdfSubject             = model.createProperty(Rdf.Subject)
    val rdfPredicate           = model.createProperty(Rdf.Predicate)
    val rdfObject              = model.createProperty(Rdf.Object)
    val valueHasRefCount       = model.createProperty(KnoraBase.ValueHasRefCount)
    val valueHasString         = model.createProperty(KnoraBase.ValueHasString)
    val isDeleted              = model.createProperty(KnoraBase.IsDeleted)
    val valueCreationDate      = model.createProperty(KnoraBase.ValueCreationDate)
    val attachedToUser         = model.createProperty(KnoraBase.AttachedToUser)
    val hasPermissions         = model.createProperty(KnoraBase.HasPermissions)
    val valueHasUUID           = model.createProperty(KnoraBase.ValueHasUUID)

    val targetRes = model.createResource(target)
    val linkValue = model.createResource(linkValueIri.value)
    val linkProp  = model.createResource(KnoraBase.HasStandoffLinkTo)

    resource.addProperty(hasStandoffLinkTo, targetRes)
    resource.addProperty(hasStandoffLinkToValue, linkValue)
    linkValue.addProperty(model.createProperty(Rdf.Type), model.createResource(KnoraBase.LinkValue))
    linkValue.addProperty(rdfSubject, resource)
    linkValue.addProperty(rdfPredicate, linkProp)
    linkValue.addProperty(rdfObject, targetRes)
    linkValue.addProperty(valueHasString, target)
    linkValue.addProperty(valueHasRefCount, intLiteral(model, refCount))
    linkValue.addProperty(isDeleted, model.createTypedLiteral("false", XSDDatatype.XSDboolean))
    linkValue.addProperty(valueCreationDate, model.createTypedLiteral(now.toString, XSDDatatype.XSDdateTime))
    linkValue.addProperty(attachedToUser, model.createResource(systemUser))
    linkValue.addProperty(hasPermissions, standoffLinkValuePermissions)
    val _ = linkValue.addProperty(valueHasUUID, linkValueIri.valueId.value)
  }

  private val systemUser: String = KnoraUserRepo.builtIn.SystemUser.id.value

  /** The fixed permissions on every standoff-link LinkValue, built identically to the create path. */
  private val standoffLinkValuePermissions: String =
    PermissionUtilADM.formatPermissionADMs(
      Set(
        PermissionADM.from(Permission.ObjectAccess.ChangeRights, KnoraUserRepo.builtIn.SystemUser.id.value),
        PermissionADM.from(Permission.ObjectAccess.View, KnoraGroupRepo.builtIn.UnknownUser.id.value),
      ),
      PermissionType.OAP,
    )

  /**
   * Step 2 (`valueHasString`) — derive the plain-text `knora-base:valueHasString` from each value's content. Scalar
   * values use the lexical form of their content literal; `ListValue` falls back to the list-node IRI. `TextValue`
   * already carries `valueHasString` from the stage-1 rename and is left untouched. `DateValue` (step 3), `LinkValue`
   * (step 4), rich text (step 5) and file values (step 6) are handled where those structures are built.
   */
  private def addValueHasString(model: Model): Unit = {
    val rdfType          = model.createProperty(Rdf.Type)
    val valueHasString   = model.createProperty(KnoraBase.ValueHasString)
    val valueHasListNode = model.createProperty(KnoraBase.ValueHasListNode)

    val literalContent: Map[String, Property] = Map(
      KnoraBase.BooleanValue -> KnoraBase.ValueHasBoolean,
      KnoraBase.IntValue     -> KnoraBase.ValueHasInteger,
      KnoraBase.DecimalValue -> KnoraBase.ValueHasDecimal,
      KnoraBase.ColorValue   -> KnoraBase.ValueHasColor,
      KnoraBase.UriValue     -> KnoraBase.ValueHasUri,
      KnoraBase.GeonameValue -> KnoraBase.ValueHasGeonameCode,
      KnoraBase.TimeValue    -> KnoraBase.ValueHasTimeStamp,
    ).map { case (cls, prop) => cls -> model.createProperty(prop) }

    def iriOf(v: Resource, p: Property): Option[String] =
      Option(v.getProperty(p)).map(_.getObject).collect { case n if n.isURIResource => n.asResource.getURI }

    def typeOf(v: Resource): Option[String] = iriOf(v, rdfType)

    def lexicalOf(v: Resource, p: Property): Option[String] =
      Option(v.getProperty(p)).map(_.getObject).collect { case n if n.isLiteral => n.asLiteral.getLexicalForm }

    model.listSubjects().asScala.filter(isValue).foreach { v =>
      if (!v.hasProperty(valueHasString)) {
        val string = typeOf(v).flatMap {
          case cls if literalContent.contains(cls) => lexicalOf(v, literalContent(cls))
          case KnoraBase.ListValue                 => iriOf(v, valueHasListNode)
          case _                                   => None
        }
        string.foreach(v.addProperty(valueHasString, _))
      }
    }
  }

  private def asValueIri(n: RDFNode): Option[ValueIri] =
    Option.when(n.isURIResource)(n.asResource.getURI).flatMap(ValueIri.from(_).toOption)

  private def isValue(n: RDFNode): Boolean = asValueIri(n).isDefined

  private def rewritingSink(downstream: StreamRDF): StreamRDF = {
    def rewriteUri(uri: String): String =
      if (uri != null) sf.toSmartIri(uri).toInternalSchema.toString else uri

    def rewriteNode(n: Node): Node =
      if (n != null && n.isURI) NodeFactory.createURI(rewriteUri(n.getURI)) else n

    new StreamRDFBase {
      override def start(): Unit                             = downstream.start()
      override def finish(): Unit                            = downstream.finish()
      override def base(base: String): Unit                  = downstream.base(base)
      override def prefix(prefix: String, iri: String): Unit = downstream.prefix(prefix, rewriteUri(iri))
      override def triple(t: Triple): Unit                   =
        downstream.triple(
          Triple.create(rewriteNode(t.getSubject), rewriteNode(t.getPredicate), rewriteNode(t.getObject)),
        )
      // The output graph is derived solely from the project; `@graph` declarations in the payload are ignored,
      // so quads are flattened to triples here.
      override def quad(q: Quad): Unit =
        downstream.triple(
          Triple.create(rewriteNode(q.getSubject), rewriteNode(q.getPredicate), rewriteNode(q.getObject)),
        )
    }
  }
}

object OntologyTransformer {
  val layer: ZLayer[StringFormatter & StandoffMappingService & IdSource, Nothing, OntologyTransformer] =
    ZLayer.derive[OntologyTransformer]
}
