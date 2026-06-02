/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology

import org.apache.jena.datatypes.xsd.XSDDatatype
import org.apache.jena.graph.Node
import org.apache.jena.graph.NodeFactory
import org.apache.jena.graph.Triple
import org.apache.jena.rdf.model.Model
import org.apache.jena.rdf.model.Property
import org.apache.jena.rdf.model.RDFNode
import org.apache.jena.rdf.model.Resource
import org.apache.jena.riot.Lang
import org.apache.jena.riot.RDFParser
import org.apache.jena.riot.system.StreamRDF
import org.apache.jena.riot.system.StreamRDFBase
import org.apache.jena.riot.system.StreamRDFLib
import org.apache.jena.sparql.core.Quad
import zio.Clock
import zio.Scope
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

import org.knora.webapi.config.AppConfig
import org.knora.webapi.messages.OntologyConstants.KnoraBase
import org.knora.webapi.messages.OntologyConstants.Rdf
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.util.CalendarDateRangeV2
import org.knora.webapi.messages.util.CalendarNameV2
import org.knora.webapi.messages.util.DateEraV2
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.UserIri
import org.knora.webapi.slice.common.ResourceIri
import org.knora.webapi.slice.common.ValueIri
import org.knora.webapi.slice.common.jena.RdfDataMgr

final case class TransformerError(message: String)

/**
 * Supplies the metadata that cannot be derived from the input JSON-LD or from `Clock`.
 *
 * @param attachedToUser    the user every imported resource and value is attached to.
 * @param attachedToProject the project every imported resource belongs to; overrides any value in the input.
 * @param permissions       the formatted `knora-base:hasPermissions` string used for every resource and value.
 */
final case class ConversionContext(
  attachedToUser: UserIri,
  attachedToProject: ProjectIri,
  permissions: String,
)

final class OntologyTransformer(sf: StringFormatter) { self =>

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
   * intermediate file into a Jena [[Model]] so cross-triple context is available.
   */
  private def restructure(nq: Path, ctx: ConversionContext): ZIO[Scope, TransformerError, Path] =
    (for {
      now   <- Clock.instant
      model <- RdfDataMgr.loadModel(nq, Lang.NTRIPLES)
      _     <- ZIO.attempt(addResourceMetadata(model, ctx, now))
      _     <- ZIO.attempt(addValueMetadata(model, ctx, now))
      _     <- ZIO.attempt(convertDateValues(model))
      _     <- ZIO.attempt(convertLinkValues(model))
      _     <- ZIO.attempt(addValueHasString(model))
      kb    <- tempFile("onto-transformer-kb-", ".nq")
      _     <- RdfDataMgr.write(model, kb, Lang.NTRIPLES)
    } yield kb)
      .mapError(e => TransformerError(s"Failed to restructure RDF: ${e.getMessage}"))

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
    val projectResource = model.createResource(ctx.attachedToProject.value)
    val creationDateLit = model.createTypedLiteral(now.toString, XSDDatatype.XSDdateTimeStamp)
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
    val creationDateLit = model.createTypedLiteral(now.toString, XSDDatatype.XSDdateTimeStamp)
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
   * is replaced by `rdf:subject`/`rdf:predicate`/`rdf:object` plus `valueHasRefCount` (1 for an explicit link) and a
   * `valueHasString` of the target IRI. Standoff-derived links (step 5) will find-and-bump these by
   * `(resource, directProp, target)` rather than duplicate them.
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
      override def quad(q: Quad): Unit =
        downstream.quad(
          Quad.create(
            rewriteNode(q.getGraph),
            rewriteNode(q.getSubject),
            rewriteNode(q.getPredicate),
            rewriteNode(q.getObject),
          ),
        )
    }
  }
}

object OntologyTransformer {
  val layer: ZLayer[StringFormatter, Nothing, OntologyTransformer] = ZLayer.derive[OntologyTransformer]
}
