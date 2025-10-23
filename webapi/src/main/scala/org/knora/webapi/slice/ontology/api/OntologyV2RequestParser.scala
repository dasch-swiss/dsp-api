/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology.api

import eu.timepit.refined.types.string.NonEmptyString
import org.apache.jena.query.Dataset
import org.apache.jena.rdf.model.*
import org.apache.jena.vocabulary.OWL2 as OWL
import org.apache.jena.vocabulary.RDFS
import zio.*
import zio.prelude.Validation

import java.time.Instant
import java.util.UUID
import scala.collection.immutable.Seq
import scala.jdk.CollectionConverters.*
import scala.language.implicitConversions

import dsp.constants.SalsahGui
import dsp.valueobjects.Schema.GuiObject
import org.knora.webapi.ApiV2Complex
import org.knora.webapi.messages.OntologyConstants.KnoraApiV2Complex as KA
import org.knora.webapi.messages.OntologyConstants.KnoraBase as KB
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.store.triplestoremessages.BooleanLiteralV2
import org.knora.webapi.messages.store.triplestoremessages.OntologyLiteralV2
import org.knora.webapi.messages.store.triplestoremessages.SmartIriLiteralV2
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.messages.v2.responder.ontologymessages.CanDeleteCardinalitiesFromClassRequestV2
import org.knora.webapi.messages.v2.responder.ontologymessages.ChangeClassLabelsOrCommentsRequestV2
import org.knora.webapi.messages.v2.responder.ontologymessages.ChangePropertyGuiElementRequest
import org.knora.webapi.messages.v2.responder.ontologymessages.ClassInfoContentV2
import org.knora.webapi.messages.v2.responder.ontologymessages.CreateOntologyRequestV2
import org.knora.webapi.messages.v2.responder.ontologymessages.CreatePropertyRequestV2
import org.knora.webapi.messages.v2.responder.ontologymessages.DeleteCardinalitiesFromClassRequestV2
import org.knora.webapi.messages.v2.responder.ontologymessages.LabelOrComment
import org.knora.webapi.messages.v2.responder.ontologymessages.OwlCardinality.KnoraCardinalityInfo
import org.knora.webapi.messages.v2.responder.ontologymessages.PredicateInfoV2
import org.knora.webapi.messages.v2.responder.ontologymessages.PropertyInfoContentV2
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.common.KnoraIris
import org.knora.webapi.slice.common.KnoraIris.OntologyIri
import org.knora.webapi.slice.common.KnoraIris.PropertyIri
import org.knora.webapi.slice.common.KnoraIris.ResourceClassIri
import org.knora.webapi.slice.common.jena.DatasetOps
import org.knora.webapi.slice.common.jena.DatasetOps.*
import org.knora.webapi.slice.common.jena.JenaConversions.given_Conversion_String_Property
import org.knora.webapi.slice.common.jena.ModelOps
import org.knora.webapi.slice.common.jena.ModelOps.*
import org.knora.webapi.slice.common.jena.ResourceOps.*
import org.knora.webapi.slice.common.jena.StatementOps.*
import org.knora.webapi.slice.common.service.IriConverter
import org.knora.webapi.slice.ontology.domain.model.Cardinality

final case class OntologyV2RequestParser(iriConverter: IriConverter) {

  private final case class OntologyMetadata(
    ontologyIri: OntologyIri,
    label: Option[String],
    comment: Option[NonEmptyString],
    lastModificationDate: Instant,
  )

  def changeOntologyMetadataRequestV2(
    jsonLd: String,
    apiRequestId: UUID,
    requestingUser: User,
  ): IO[String, ChangeOntologyMetadataRequestV2] = ZIO.scoped {
    for {
      model <- ModelOps.fromJsonLd(jsonLd)
      meta  <- extractOntologyMetadata(model)
    } yield ChangeOntologyMetadataRequestV2(
      meta.ontologyIri,
      meta.label,
      meta.comment,
      meta.lastModificationDate,
      apiRequestId,
      requestingUser,
    )
  }

  private def extractOntologyMetadata(m: Model): ZIO[Scope, String, OntologyMetadata] =
    for {
      r                    <- ZIO.fromEither(m.singleRootResource)
      ontologyIri          <- uriAsOntologyIri(r)
      label                <- ZIO.fromEither(r.objectStringOption(RDFS.label))
      comment              <- ZIO.fromEither(ontologyRdfsComment(r))
      lastModificationDate <- ZIO.fromEither(r.objectInstant(KA.LastModificationDate))
    } yield OntologyMetadata(ontologyIri, label, comment, lastModificationDate)

  private def ontologyRdfsComment(r: Resource): Either[String, Option[NonEmptyString]] = r
    .objectStringOption(RDFS.comment, s => NonEmptyString.from(s).left.map(_ => "Ontology comment may not be empty"))

  private def uriAsOntologyIri(r: Resource): ZIO[Scope, String, OntologyIri] = ZIO
    .fromOption(r.uri)
    .orElseFail("No IRI found")
    .flatMap(iriConverter.asSmartIri(_).mapError(_.getMessage))
    .flatMap(sIri => ZIO.fromEither(OntologyIri.fromApiV2Complex(sIri)))

  def createOntologyRequestV2(
    jsonLd: String,
    apiRequestId: UUID,
    requestingUser: User,
  ): IO[String, CreateOntologyRequestV2] =
    import ModelOps.*
    ZIO.scoped {
      ModelOps.fromJsonLd(jsonLd).flatMap { m =>
        ZIO.fromEither {
          for {
            r          <- m.singleRootResource
            name       <- r.objectString(KA.OntologyName)
            projectIri <- r.objectUri(KA.AttachedToProject, ProjectIri.from)
            isShared   <- r.objectBooleanOption(KA.IsShared).map(_.getOrElse(false))
            label      <- r.objectString(RDFS.label)
            comment    <- ontologyRdfsComment(r)
          } yield CreateOntologyRequestV2(name, projectIri, isShared, label, comment, apiRequestId, requestingUser)
        }
      }
    }

  def createClassRequestV2(jsonLd: String, apiRequestId: UUID, requestingUser: User): IO[String, CreateClassRequestV2] =
    ZIO.scoped {
      for {
        ds        <- DatasetOps.fromJsonLd(jsonLd)
        meta      <- extractOntologyMetadata(ds.defaultModel)
        classInfo <- extractClassInfo(ds, meta)
        _ <-
          ZIO
            .fail(
              s"Ontology for class '${classInfo.classIri.toString}' does not match ontology ${meta.ontologyIri.toString}",
            )
            .unless(meta.ontologyIri.smartIri == classInfo.classIri.getOntologyFromEntity)
      } yield CreateClassRequestV2(
        classInfo,
        meta.lastModificationDate,
        apiRequestId,
        requestingUser,
      )
    }

  private def extractClassInfo(ds: Dataset, meta: OntologyMetadata): ZIO[Scope, String, ClassInfoContentV2] =
    for {
      classModel    <- ZIO.fromOption(ds.namedModel(meta.ontologyIri.toString)).orElseFail("No class definition found")
      r             <- ZIO.fromEither(classModel.singleRootResource)
      classIri      <- extractClassIri(r)
      predicates    <- extractPredicates(r)
      cardinalities <- extractCardinalities(r)
      datatypeInfo   = None
      subClasses    <- extractSubClasses(r).map(_.map(_.smartIri))
    } yield ClassInfoContentV2(classIri.smartIri, predicates, cardinalities, datatypeInfo, subClasses, ApiV2Complex)

  private def extractClassIri(r: Resource): ZIO[Scope, String, ResourceClassIri] =
    ZIO.fromOption(r.uri).orElseFail("No class IRI found").flatMap(iriConverter.asResourceClassIriApiV2Complex)

  private def extractPredicates(r: Resource): ZIO[Scope, String, Map[SmartIri, PredicateInfoV2]] =
    val propertyIter: Map[String, List[Statement]] = r
      .listProperties()
      .asScala
      .filterNot(_.predicateUri == null)
      .filterNot(_.predicateUri == RDFS.subPropertyOf.toString)
      .filterNot(_.predicateUri == RDFS.subClassOf.toString)
      .toList
      .groupBy(_.predicateUri)
    ZIO.foreach(propertyIter) { case (iri, stmts) => extractPredicateInfo(iri, stmts) }.map(_.toMap).logError

  private def extractPredicateInfo(
    iri: String,
    stmts: List[Statement],
  ): ZIO[Scope, String, (SmartIri, PredicateInfoV2)] =
    for {
      propertyIri <- iriConverter.asSmartIri(iri).mapError(_.getMessage)
      objects     <- ZIO.foreach(stmts)(asOntologyLiteralV2)
    } yield (propertyIri, PredicateInfoV2(propertyIri, objects))

  private def asOntologyLiteralV2(stmt: Statement): ZIO[Scope, String, OntologyLiteralV2] =
    stmt.getObject match
      case res: Resource => iriConverter.asSmartIri(res.getURI).mapBoth(_.getMessage, SmartIriLiteralV2.apply)
      case literal: Literal =>
        literal.getValue match
          case str: String          => ZIO.succeed(StringLiteralV2.from(str, Option(literal.getLanguage).filter(_.nonEmpty)))
          case b: java.lang.Boolean => ZIO.succeed(BooleanLiteralV2(b))
          case _                    => ZIO.fail(s"Unsupported literal type: ${literal.getValue.getClass}")

  private def extractSubClasses(r: Resource): ZIO[Scope, String, Set[ResourceClassIri]] = {
    val subclasses: Set[String] = r.listProperties(RDFS.subClassOf).asScala.flatMap(_.objectAsUri.toOption).toSet
    ZIO.foreach(subclasses)(iriConverter.asResourceClassIri)
  }

  private def extractCardinalities(r: Resource): ZIO[Scope, String, Map[SmartIri, KnoraCardinalityInfo]] = {
    val cardinalities: Either[String, Map[String, KnoraCardinalityInfo]] =
      r.listProperties(RDFS.subClassOf)
        .asScala
        .flatMap(asKnoraCardinalityResource)
        .map { res =>
          for {
            prop <- res.objectUri(OWL.onProperty)
            card <- asKnoraCardinalityInfo(res)
          } yield (prop, card)
        }
        .foldLeft(Right(Map.empty))(
          (
            acc: Either[String, Map[String, KnoraCardinalityInfo]],
            elem: Either[String, (String, KnoraCardinalityInfo)],
          ) =>
            (acc, elem) match
              case (Right(accMap), Right(elemTuple)) => Right(accMap + elemTuple)
              case (Left(err), Left(err2))           => Left(err + "," + err2)
              case (Left(err), _)                    => Left(err)
              case (_, Left(err))                    => Left(err),
        )

    ZIO
      .fromEither(cardinalities)
      .flatMap(ZIO.foreach(_) { case (key, value) => iriConverter.asPropertyIri(key).map(p => (p.smartIri, value)) })
  }

  private def asKnoraCardinalityResource(stmt: Statement): Option[Resource] =
    def isKnoraCardinality(res: Resource): Boolean =
      res.isAnon && res.hasProperty(OWL.onProperty) && res.objectRdfClass().contains(OWL.Restriction.toString)
    stmt.getObject match
      case res: Resource if isKnoraCardinality(res) => Some(res)
      case _                                        => None

  private def asKnoraCardinalityInfo(bNode: Resource): Either[String, KnoraCardinalityInfo] = {
    val minMaxEither: Either[String, (Int, Option[Int])] = for {
      max  <- bNode.objectIntOption(OWL.maxCardinality)
      min  <- bNode.objectIntOption(OWL.minCardinality)
      card <- bNode.objectIntOption(OWL.cardinality)
    } yield (card.orElse(min).getOrElse(0), card.orElse(max))

    for {
      minMax      <- minMaxEither
      cardinality <- Cardinality.from.tupled.apply(minMax)
      guiOrder    <- bNode.objectIntOption(SalsahGui.External.GuiOrder)
    } yield KnoraCardinalityInfo(cardinality, guiOrder)
  }

  def changeClassLabelsOrCommentsRequestV2(
    jsonLd: String,
    apiRequestId: UUID,
    requestingUser: User,
  ): IO[String, ChangeClassLabelsOrCommentsRequestV2] =
    ZIO.scoped {
      for {
        ds        <- DatasetOps.fromJsonLd(jsonLd)
        meta      <- extractOntologyMetadata(ds.defaultModel)
        classInfo <- extractClassInfo(ds, meta)
        classIri  <- ZIO.fromEither(ResourceClassIri.fromApiV2Complex(classInfo.classIri))
        preds = classInfo.predicates.filter { case (iri, _) =>
                  iri.toIri == RDFS.label.toString || iri.toIri == RDFS.comment.toString
                }
        _ <- ZIO.fail(s"Class '${classIri.toString}' does not have any updates").unless(preds.nonEmpty)
        _ <- ZIO.fail(s"Class '$classIri may only label or comment to update").unless(preds.size == 1)
        labelOrComment <- ZIO
                            .fromOption(LabelOrComment.fromString(preds.keys.head.toString))
                            .orElseFail(s"Invalid predicate: ${preds.keys.head}")
        newObjects = preds.head._2.objects.collect { case sl: StringLiteralV2 => sl }
      } yield ChangeClassLabelsOrCommentsRequestV2(
        classIri,
        labelOrComment,
        newObjects,
        meta.lastModificationDate,
        apiRequestId,
        requestingUser,
      )
    }

  def addCardinalitiesToClassRequestV2(
    jsonLd: String,
    apiRequestId: UUID,
    requestingUser: User,
  ): IO[String, AddCardinalitiesToClassRequestV2] =
    constructClassRelatedRequest(
      jsonLd,
      apiRequestId,
      requestingUser,
      AddCardinalitiesToClassRequestV2.apply,
      (_, classInfo) => ZIO.fail("No cardinalities specified").when(classInfo.directCardinalities.isEmpty).unit,
    )

  def replaceClassCardinalitiesRequestV2(
    jsonLd: String,
    apiRequestId: UUID,
    requestingUser: User,
  ): IO[String, ReplaceClassCardinalitiesRequestV2] =
    constructClassRelatedRequest(jsonLd, apiRequestId, requestingUser, ReplaceClassCardinalitiesRequestV2.apply)

  def canDeleteCardinalitiesFromClassRequestV2(
    jsonLd: String,
    apiRequestId: UUID,
    requestingUser: User,
  ): IO[String, CanDeleteCardinalitiesFromClassRequestV2] =
    constructClassRelatedRequest(jsonLd, apiRequestId, requestingUser, CanDeleteCardinalitiesFromClassRequestV2.apply)

  def deleteCardinalitiesFromClassRequestV2(
    jsonLd: String,
    apiRequestId: UUID,
    requestingUser: User,
  ): IO[String, DeleteCardinalitiesFromClassRequestV2] =
    constructClassRelatedRequest(jsonLd, apiRequestId, requestingUser, DeleteCardinalitiesFromClassRequestV2.apply)

  def changeGuiOrderRequestV2(
    jsonLd: String,
    apiRequestId: UUID,
    requestingUser: User,
  ): IO[String, ChangeGuiOrderRequestV2] =
    constructClassRelatedRequest(
      jsonLd,
      apiRequestId,
      requestingUser,
      ChangeGuiOrderRequestV2.apply,
      (_, classInfo) => ZIO.fail("No cardinalities specified").when(classInfo.directCardinalities.isEmpty).unit,
    )

  private def constructClassRelatedRequest[A](
    jsonLd: String,
    apiRequestId: UUID,
    requestingUser: User,
    f: (ClassInfoContentV2, Instant, UUID, User) => A,
    checkConstraints: (OntologyMetadata, ClassInfoContentV2) => IO[String, Unit] = (_, _) => ZIO.unit,
  ): IO[String, A] =
    ZIO.scoped {
      for {
        ds        <- DatasetOps.fromJsonLd(jsonLd)
        meta      <- extractOntologyMetadata(ds.defaultModel)
        classInfo <- extractClassInfo(ds, meta)
        _         <- checkConstraints(meta, classInfo)
      } yield f(classInfo, meta.lastModificationDate, apiRequestId, requestingUser)
    }

  def createPropertyRequestV2(jsonLd: String, apiRequestId: UUID, user: User): IO[String, CreatePropertyRequestV2] =
    ZIO.scoped {
      for {
        ds           <- DatasetOps.fromJsonLd(jsonLd)
        meta         <- extractOntologyMetadata(ds.defaultModel)
        propertyInfo <- extractPropertyInfo(ds, meta)
        _            <- sanityCheckCreate(propertyInfo)
      } yield CreatePropertyRequestV2(propertyInfo, meta.lastModificationDate, apiRequestId, user)
    }

  private def extractPropertyInfo(ds: Dataset, meta: OntologyMetadata): ZIO[Scope, String, PropertyInfoContentV2] =
    for {
      classModel    <- ZIO.fromOption(ds.namedModel(meta.ontologyIri.toString)).orElseFail("No property definition found")
      r             <- ZIO.fromEither(classModel.singleRootResource)
      propertyIri   <- extractPropertyIri(r)
      predicates    <- extractPredicates(r)
      subPropertyOf <- extractSubPropertyOf(r).map(_.map(_.smartIri))

      propertyInfo = PropertyInfoContentV2(propertyIri.smartIri, predicates, subPropertyOf, ApiV2Complex)
    } yield propertyInfo

  private def extractPropertyIri(r: Resource): ZIO[Scope, String, PropertyIri] =
    ZIO.fromOption(r.uri).orElseFail("No property IRI found").flatMap(iriConverter.asPropertyIriApiV2Complex)

  private def extractSubPropertyOf(r: Resource): ZIO[Scope, String, Set[ResourceClassIri]] = {
    val subclasses: Set[String] = r.listProperties(RDFS.subPropertyOf).asScala.flatMap(_.objectAsUri.toOption).toSet
    ZIO.foreach(subclasses)(iriConverter.asResourceClassIri)
  }

  private def sanityCheckCreate(propertyInfo: PropertyInfoContentV2): ZIO[Scope, String, Unit] = for {
    _                      <- ZIO.fail("No predicates found").when(propertyInfo.predicates.isEmpty)
    subjectType            <- iriConverter.asSmartIri(KA.SubjectType).orDie
    objectType             <- iriConverter.asSmartIri(KA.ObjectType).orDie
    subjectClassConstraint <- iriConverter.asSmartIri(KB.SubjectClassConstraint).orDie
    rdfsLabel              <- iriConverter.asSmartIri(RDFS.label.toString).orDie
    rdfsComment            <- iriConverter.asSmartIri(RDFS.comment.toString).orDie
    guiElementProp         <- iriConverter.asSmartIri(SalsahGui.External.GuiElementProp).orDie
    guiAttribute           <- iriConverter.asSmartIri(SalsahGui.External.GuiAttribute).orDie

    isPropertyIri = Validation.fromEither(PropertyIri.from(propertyInfo.propertyIri))
    validSubjectType =
      propertyInfo.getIriObject(subjectType) match
        case None    => Validation.succeed(None)
        case Some(t) => isApiV2ComplexEntityIri(t, "Invalid knora-api:subjectType").map(Some(_))
    hasObjectType =
      propertyInfo.getIriObject(objectType) match
        case None    => Validation.fail("Missing knora-api:objectType")
        case Some(t) => isApiV2ComplexEntityIri(t, "Invalid knora-api:objectType")
    subPropertyOfNotEmpty =
      propertyInfo.subPropertyOf match
        case set if set.isEmpty => Validation.fail("SuperProperties cannot be empty")
        case _                  => Validation.succeed(())
    subjectClassConstraintIsIri =
      propertyInfo.predicates.get(subjectClassConstraint) match
        case None             => Validation.succeed(())
        case Some(predicates) => ensureSingleIri(predicates.objects, "knora-base:subjectClassConstraint")
    labelsAreValid =
      propertyInfo.predicates.get(rdfsLabel) match
        case None       => Validation.fail("Missing rdfs:label")
        case Some(info) => ensureOnlyStringLiteralsWithLanguage(info.objects, "rdfs:label")
    commentIsValid =
      propertyInfo.predicates.get(rdfsComment) match
        case None       => Validation.succeed(())
        case Some(info) => ensureOnlyStringLiteralsWithLanguage(info.objects, "rdfs:comment")
    guiObjectValid = ensureValidGuiObject(propertyInfo.predicates, guiElementProp, guiAttribute)

    _ <- Validation
           .validate(
             isPropertyIri,
             validSubjectType,
             hasObjectType,
             subPropertyOfNotEmpty,
             subjectClassConstraintIsIri,
             labelsAreValid,
             commentIsValid,
             guiObjectValid,
           )
           .toZIO
  } yield ()

  private def isApiV2ComplexEntityIri(s: SmartIri, failMsg: String): Validation[String, SmartIri] =
    if s.isKnoraApiV2EntityIri && s.isApiV2ComplexSchema then Validation.succeed(s)
    else Validation.fail(s"$failMsg: ${s.toComplexSchema.toIri}")

  private def ensureSingleIri(literals: Seq[OntologyLiteralV2], prop: String): Validation[String, SmartIri] =
    literals match
      case Seq(SmartIriLiteralV2(iri)) => Validation.succeed(iri)
      case _                           => Validation.fail(s"$prop: Expected single IRI, got: ${literals.mkString(", ")}")

  private def ensureOnlyStringLiteralsWithLanguage(
    literals: Seq[OntologyLiteralV2],
    prop: String,
  ): Validation[String, Seq[StringLiteralV2]] =
    ensureOnlyStringLiterals(literals, prop)
      .flatMap(strLit =>
        strLit.filter(_.language.isEmpty) match
          case Nil         => Validation.succeed(strLit)
          case withoutLang => Validation.fail(s"$prop: String literals without language: ${withoutLang.mkString(", ")}"),
      )

  private def ensureOnlyStringLiterals(
    literals: Seq[OntologyLiteralV2],
    prop: String,
  ): Validation[String, Seq[StringLiteralV2]] = {
    val nonStringLiterals = literals.filter(pred => !pred.isInstanceOf[StringLiteralV2])
    if (nonStringLiterals.isEmpty) Validation.succeed(literals.collect { case s: StringLiteralV2 => s })
    else Validation.fail(s"$prop: Non-string literals: ${nonStringLiterals.mkString(", ")}")
  }

  private def ensureValidGuiObject(
    predicates: Map[SmartIri, PredicateInfoV2],
    guiElementProp: SmartIri,
    guiAttribute: SmartIri,
  ): Validation[String, GuiObject] = {
    val guiElement = predicates.get(guiElementProp)
    val guiElementVal = guiElement match
      case None => Validation.succeed(())
      case Some(info) =>
        info.objects.head match
          case _: SmartIriLiteralV2 => Validation.succeed(())
          case other                => Validation.fail(s"Unexpected object for salsah-gui:guiElement: $other")

    val guiAttributes   = predicates.get(guiAttribute).map(_.objects).getOrElse(Seq.empty)
    val guiAttributeVal = ensureOnlyStringLiterals(guiAttributes, "salsah-gui:guiAttribute")

    val guiAttrStr = guiAttributes.collect { case StringLiteralV2(value, _) => value }.toSet
    val guiElementStr =
      guiElement.flatMap(_.objects.headOption).collect { case SmartIriLiteralV2(value) => value.toInternalSchema.toIri }
    val validGui = GuiObject.makeFromStrings(guiAttrStr, guiElementStr).mapError(_.getMessage)
    Validation.validate(guiElementVal, guiAttributeVal, validGui).map { case (_, _, gui) => gui }
  }

  def changePropertyGuiElementRequest(
    jsonLd: String,
    apiRequestId: UUID,
    user: User,
  ): IO[String, ChangePropertyGuiElementRequest] = ZIO.scoped {
    for {
      ds           <- DatasetOps.fromJsonLd(jsonLd)
      meta         <- extractOntologyMetadata(ds.defaultModel)
      propertyInfo <- extractPropertyInfo(ds, meta)
      propertyIri  <- ZIO.fromEither(PropertyIri.from(propertyInfo.propertyIri))

      guiElementProp      <- iriConverter.asSmartIri(SalsahGui.External.GuiElementProp).orDie
      guiAttribute        <- iriConverter.asSmartIri(SalsahGui.External.GuiAttribute).orDie
      guiElement          <- ensureValidGuiObject(propertyInfo.predicates, guiElementProp, guiAttribute).toZIO
      lastModificationDate = meta.lastModificationDate
    } yield ChangePropertyGuiElementRequest(propertyIri, guiElement, lastModificationDate, apiRequestId, user)
  }

  def changePropertyLabelsOrCommentsRequestV2(
    jsonLd: String,
    apiRequestId: UUID,
    user: User,
  ): IO[String, ChangePropertyLabelsOrCommentsRequestV2] = ZIO.scoped {
    for {
      ds                          <- DatasetOps.fromJsonLd(jsonLd)
      meta                        <- extractOntologyMetadata(ds.defaultModel)
      propertyInfo                <- extractPropertyInfo(ds, meta)
      propertyIri                 <- ZIO.fromEither(PropertyIri.from(propertyInfo.propertyIri))
      rdfsLabel                   <- iriConverter.asSmartIri(RDFS.label.toString).orDie
      rdfsComment                 <- iriConverter.asSmartIri(RDFS.comment.toString).orDie
      what                        <- labelOrComment(propertyInfo.predicates, rdfsLabel, rdfsComment).toZIO
      (labelOrComment, newObjects) = what
    } yield ChangePropertyLabelsOrCommentsRequestV2(
      propertyIri,
      labelOrComment,
      newObjects,
      meta.lastModificationDate,
      apiRequestId,
      user,
    )
  }

  private def labelOrComment(
    predicates: Map[SmartIri, PredicateInfoV2],
    rdfsLabel: SmartIri,
    rdfComment: SmartIri,
  ): Validation[String, (LabelOrComment, Seq[StringLiteralV2])] =
    (predicates.get(rdfsLabel), predicates.get(rdfComment)) match {
      case (Some(label), None) =>
        ensureOnlyStringLiteralsWithLanguage(label.objects, "rdfs:label").map((LabelOrComment.Label, _))
      case (None, Some(comment)) =>
        ensureOnlyStringLiteralsWithLanguage(comment.objects, "rdfs:comment").map((LabelOrComment.Comment, _))
      case (Some(label), Some(comment)) => Validation.fail(s"Both label and comment found: $label, $comment")
      case (None, None)                 => Validation.fail("No label or comment found")
    }
}

object OntologyV2RequestParser {
  val layer = ZLayer.derive[OntologyV2RequestParser]
}
