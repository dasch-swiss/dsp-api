/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.standoff.service

import zio.*

import dsp.errors.*
import org.knora.webapi.*
import org.knora.webapi.config.AppConfig
import org.knora.webapi.messages.IriConversions.*
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.util.standoff.StandoffTagUtilV2
import org.knora.webapi.messages.util.standoff.StandoffTagUtilV2.XMLTagItem
import org.knora.webapi.messages.v2.responder.ontologymessages.OwlCardinality.*
import org.knora.webapi.messages.v2.responder.ontologymessages.ReadClassInfoV2
import org.knora.webapi.messages.v2.responder.ontologymessages.StandoffEntityInfoGetResponseV2
import org.knora.webapi.messages.v2.responder.standoffmessages.*
import org.knora.webapi.responders.v2.OntologyResponderV2
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.service.KnoraProjectService
import org.knora.webapi.slice.common.StandoffMappingElementIri
import org.knora.webapi.slice.common.StandoffMappingIri
import org.knora.webapi.slice.infrastructure.CacheManager
import org.knora.webapi.slice.infrastructure.EhCache
import org.knora.webapi.slice.ontology.domain.model.Cardinality.AtLeastOne
import org.knora.webapi.slice.ontology.domain.model.Cardinality.ExactlyOne
import org.knora.webapi.slice.resources.repo.GetMappingQuery
import org.knora.webapi.slice.resources.repo.model.MappingElement
import org.knora.webapi.slice.resources.repo.model.MappingStandoffDatatypeClass
import org.knora.webapi.slice.resources.repo.model.MappingXMLAttribute
import org.knora.webapi.slice.standoff.repo.GetXslTransformationMetadataQuery
import org.knora.webapi.store.iiif.api.SipiService
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Construct

/**
 * Looks up XML→standoff mappings and the XSL transformations they reference.
 *
 * Extracted from `StandoffResponderV2` so that callers can invoke these read
 * operations directly instead of via `MessageRelay`. Mapping creation
 * (`createMappingV2`) remains on `StandoffResponderV2` and uses this service
 * for the XSL transformation lookup it needs internally.
 */
trait StandoffMappingService {

  /**
   * Loads a mapping from the cache or, on miss, from the triplestore. Validates
   * the standoff classes/properties referenced by the mapping against the
   * ontology.
   */
  def getMappingV2(mappingIri: StandoffMappingIri): Task[GetMappingResponseV2]

  /**
   * Resolves the URL of the XSL transformation file in Sipi and fetches its
   * contents (cached after the first request).
   */
  def getXSLTransformation(xslTransformationIri: IRI): Task[String]

  /**
   * Validates the standoff classes/properties referenced by `mappingXMLtoStandoff`
   * against the ontology and returns the resolved entities. Used by mapping
   * creation to fail fast before persisting an invalid mapping.
   */
  def getStandoffEntitiesFromMappingV2(
    mappingXMLtoStandoff: MappingXMLtoStandoff,
  ): Task[StandoffEntityInfoGetResponseV2]
}

final class StandoffMappingServiceLive(
  appConfig: AppConfig,
  triplestore: TriplestoreService,
  ontologyResponder: OntologyResponderV2,
  sipiService: SipiService,
  projectService: KnoraProjectService,
  xsltCache: EhCache[String, String],
  mappingCache: EhCache[String, MappingXMLtoStandoff],
) extends StandoffMappingService {

  private val xmlMimeTypes = Set("text/xml", "application/xml", "application/xslt+xml")

  override def getMappingV2(mappingIri: StandoffMappingIri): Task[GetMappingResponseV2] = {
    val mapping: Task[GetMappingResponseV2] =
      mappingCache.get(mappingIri.value) match {
        case Some(m) => getStandoffEntitiesFromMappingV2(m).map(GetMappingResponseV2(mappingIri, m, _))
        case None    =>
          for {
            m        <- getMappingFromTriplestore(mappingIri)
            entities <- getStandoffEntitiesFromMappingV2(m)
          } yield GetMappingResponseV2(mappingIri, m, entities)
      }
    mapping.mapError { case e: Exception =>
      BadRequestException(s"An error occurred when requesting mapping $mappingIri: ${e.getMessage}")
    }
  }

  override def getXSLTransformation(
    xslTransformationIri: IRI,
  ): Task[String] = {
    val Q       = GetXslTransformationMetadataQuery
    val xsltUrl = (for {
      result <- triplestore.select(Q.build(xslTransformationIri))

      row <- result.results.bindings match {
               case head +: Nil => ZIO.succeed(head.rowMap)
               case Nil         =>
                 ZIO.fail(NotFoundException(s"XSL transformation $xslTransformationIri not found"))
               case _ =>
                 ZIO.fail(
                   InconsistentRepositoryDataException(
                     s"${OntologyConstants.KnoraBase.XSLTransformation} $xslTransformationIri is supposed to have exactly one value of type ${OntologyConstants.KnoraBase.TextFileValue}",
                   ),
                 )
             }

      _ <- ZIO
             .fail(
               BadRequestException(
                 s"Resource $xslTransformationIri is not a ${OntologyConstants.KnoraBase.XSLTransformation}",
               ),
             )
             .when(row(Q.resourceClass) != OntologyConstants.KnoraBase.XSLTransformation)

      mimeType     = row(Q.internalMimeType)
      fileValueIri = row(Q.fileValueIri)
      _           <-
        ZIO
          .fail(
            BadRequestException(
              s"Expected $fileValueIri to be an XML file referring to an XSL transformation, but it has MIME type $mimeType",
            ),
          )
          .when(!xmlMimeTypes.contains(mimeType))

      projectIri <- ZIO.fromEither(ProjectIri.from(row(Q.projectIri))).mapError(BadRequestException.apply)
      project    <- projectService
                   .findById(projectIri)
                   .someOrFail(InconsistentRepositoryDataException(s"Project $projectIri not found"))
    } yield s"${appConfig.sipi.internalBaseUrl}/${project.shortcode.value}/${row(Q.internalFilename)}/file").mapError {
      case notFound: NotFoundException =>
        BadRequestException(s"XSL transformation $xslTransformationIri not found: ${notFound.message}")
    }

    for {
      url  <- xsltUrl
      xslt <- xsltCache.get(url) match {
                case Some(cached) => ZIO.succeed(cached)
                case None         =>
                  sipiService
                    .getTextFileRequest(url, this.getClass.getName)
                    .tap(c => ZIO.succeed(xsltCache.put(url, c)))
              }
    } yield xslt
  }

  /**
   * Reads a mapping from the triplestore and parses it into a `MappingXMLtoStandoff`.
   * Caches the result.
   */
  private def getMappingFromTriplestore(mappingIri: StandoffMappingIri): Task[MappingXMLtoStandoff] =
    for {
      mappingResponse <- triplestore.query(Construct(GetMappingQuery.build(mappingIri.value)))

      _ = if (mappingResponse.statements.isEmpty) {
            throw BadRequestException(s"mapping $mappingIri does not exist in triplestore")
          }

      (mappingElementStatements, otherStatements) =
        mappingResponse.statements.partition { case (_: IRI, assertions: Seq[(IRI, String)]) =>
          assertions.contains((OntologyConstants.Rdf.Type, OntologyConstants.KnoraBase.MappingElement))
        }

      mappingElements =
        mappingElementStatements.map { case (subjectIri: IRI, assertions: Seq[(IRI, String)]) =>
          val assertionsAsMap: Map[IRI, String] = assertions.toMap

          val attributes: Seq[MappingXMLAttribute] = assertions.filter { case (propIri, _) =>
            propIri == OntologyConstants.KnoraBase.MappingHasXMLAttribute
          }.map { case (_: IRI, attributeElementIri: String) =>
            val attributeStatementsAsMap: Map[IRI, String] = otherStatements(attributeElementIri).toMap
            MappingXMLAttribute(
              attributeName = attributeStatementsAsMap(OntologyConstants.KnoraBase.MappingHasXMLAttributename),
              namespace = attributeStatementsAsMap(OntologyConstants.KnoraBase.MappingHasXMLNamespace),
              standoffProperty = attributeStatementsAsMap(OntologyConstants.KnoraBase.MappingHasStandoffProperty),
              mappingXMLAttributeElementIri = StandoffMappingElementIri.unsafeFrom(attributeElementIri),
            )
          }

          val dataTypeOption: Option[IRI] =
            assertionsAsMap.get(OntologyConstants.KnoraBase.MappingHasStandoffDataTypeClass)

          MappingElement(
            tagName = assertionsAsMap(OntologyConstants.KnoraBase.MappingHasXMLTagname),
            namespace = assertionsAsMap(OntologyConstants.KnoraBase.MappingHasXMLNamespace),
            className = assertionsAsMap(OntologyConstants.KnoraBase.MappingHasXMLClass),
            standoffClass = assertionsAsMap(OntologyConstants.KnoraBase.MappingHasStandoffClass),
            mappingElementIri = StandoffMappingElementIri.unsafeFrom(subjectIri),
            standoffDataTypeClass = dataTypeOption match {
              case Some(dataTypeElementIri: IRI) =>
                val dataTypeAssertionsAsMap: Map[IRI, String] = otherStatements(dataTypeElementIri).toMap
                Some(
                  MappingStandoffDatatypeClass(
                    datatype = dataTypeAssertionsAsMap(OntologyConstants.KnoraBase.MappingHasStandoffClass),
                    attributeName = dataTypeAssertionsAsMap(OntologyConstants.KnoraBase.MappingHasXMLAttributename),
                    mappingStandoffDataTypeClassElementIri = StandoffMappingElementIri.unsafeFrom(dataTypeElementIri),
                  ),
                )
              case None => None
            },
            attributes = attributes,
            separatorRequired = assertionsAsMap(OntologyConstants.KnoraBase.MappingElementRequiresSeparator).toBoolean,
          )
        }.toSeq

      defaultXSLTransformationOption =
        otherStatements(mappingIri.value).find { case (pred: IRI, _: String) =>
          pred == OntologyConstants.KnoraBase.MappingHasDefaultXSLTransformation
        }.map { case (_: IRI, xslTransformationIri: IRI) => xslTransformationIri }

      mappingXMLToStandoff =
        StandoffMappingService.transformMappingElementsToMappingXMLtoStandoff(
          mappingElements,
          defaultXSLTransformationOption,
        )

      _ = mappingCache.put(mappingIri.value, mappingXMLToStandoff)
    } yield mappingXMLToStandoff

  override def getStandoffEntitiesFromMappingV2(
    mappingXMLtoStandoff: MappingXMLtoStandoff,
  ): Task[StandoffEntityInfoGetResponseV2] = {
    implicit val sf: StringFormatter = StringFormatter.getGeneralInstance

    val mappingStandoffToXML: Map[IRI, XMLTagItem] = StandoffTagUtilV2.invertXMLToStandoffMapping(mappingXMLtoStandoff)
    val standoffTagIrisFromMapping: Set[IRI]       = mappingStandoffToXML.keySet
    val standoffPropertyIrisFromMapping: Set[IRI]  = mappingStandoffToXML.values.foldLeft(Set.empty[IRI]) {
      (acc, xmlTag) => acc ++ xmlTag.attributes.keySet
    }

    val systemOrDatatypePropsAsAttr: Set[IRI] = standoffPropertyIrisFromMapping.intersect(
      StandoffProperties.systemProperties ++ StandoffProperties.dataTypeProperties,
    )
    if (systemOrDatatypePropsAsAttr.nonEmpty)
      throw InvalidStandoffException(
        s"attempt to define attributes for system or data type properties: ${systemOrDatatypePropsAsAttr.mkString(", ")}",
      )

    for {
      standoffClassEntities <- ontologyResponder.getStandoffEntityInfoResponseV2(
                                 standoffClassIris = standoffTagIrisFromMapping.map(_.toSmartIri),
                               )

      _ = if (standoffTagIrisFromMapping.map(_.toSmartIri) != standoffClassEntities.standoffClassInfoMap.keySet) {
            throw NotFoundException(
              s"the ontology responder could not find information about these standoff classes: ${(standoffTagIrisFromMapping
                  .map(_.toSmartIri) -- standoffClassEntities.standoffClassInfoMap.keySet).mkString(", ")}",
            )
          }

      standoffPropertyIrisFromOntologyResponder: Set[SmartIri] =
        standoffClassEntities.standoffClassInfoMap.foldLeft(Set.empty[SmartIri]) {
          case (acc, (_, standoffClassEntity: ReadClassInfoV2)) =>
            acc ++ standoffClassEntity.allCardinalities.keySet
        }

      standoffPropertyEntities <- ontologyResponder.getStandoffEntityInfoResponseV2(
                                    standoffPropertyIris = standoffPropertyIrisFromOntologyResponder,
                                  )

      propertyDefinitionsFromMappingFoundInOntology: Set[SmartIri] =
        standoffPropertyEntities.standoffPropertyInfoMap.keySet
          .intersect(standoffPropertyIrisFromMapping.map(_.toSmartIri))

      _ <-
        ZIO.fail {
          NotFoundException(
            s"the ontology responder could not find information about these standoff properties: " +
              s"${(standoffPropertyIrisFromMapping.map(_.toSmartIri) -- propertyDefinitionsFromMappingFoundInOntology)
                  .mkString(", ")}",
          )
        }.when(standoffPropertyIrisFromMapping.map(_.toSmartIri) != propertyDefinitionsFromMappingFoundInOntology)

      _ <- ZIO.attempt {
             mappingStandoffToXML.foreach { case (standoffClass: IRI, xmlTag: XMLTagItem) =>
               val standoffPropertiesForStandoffClass: Set[SmartIri] = xmlTag.attributes.keySet.map(_.toSmartIri)

               val cardinalitiesFound = standoffClassEntities
                 .standoffClassInfoMap(standoffClass.toSmartIri)
                 .allCardinalities
                 .keySet
                 .intersect(standoffPropertiesForStandoffClass)

               if (standoffPropertiesForStandoffClass != cardinalitiesFound) {
                 throw NotFoundException(
                   s"the following standoff properties have no cardinality for $standoffClass: ${(standoffPropertiesForStandoffClass -- cardinalitiesFound)
                       .mkString(", ")}",
                 )
               }

               val requiredPropsForClass: Set[SmartIri] = standoffClassEntities
                 .standoffClassInfoMap(standoffClass.toSmartIri)
                 .allCardinalities
                 .filter { case (_: SmartIri, card: KnoraCardinalityInfo) =>
                   card.cardinality == ExactlyOne || card.cardinality == AtLeastOne
                 }
                 .keySet -- StandoffProperties.systemProperties.map(
                 _.toSmartIri,
               ) -- StandoffProperties.dataTypeProperties.map(_.toSmartIri)

               if (standoffPropertiesForStandoffClass.intersect(requiredPropsForClass) != requiredPropsForClass) {
                 throw NotFoundException(
                   s"the following required standoff properties are not defined for the standoff class $standoffClass: ${(requiredPropsForClass -- standoffPropertiesForStandoffClass)
                       .mkString(", ")}",
                 )
               }

               standoffClassEntities.standoffClassInfoMap(standoffClass.toSmartIri).standoffDataType match {
                 case Some(dataType: StandoffDataTypeClasses.Value) =>
                   val dataTypeFromMapping: XMLStandoffDataTypeClass = xmlTag.tagItem.mapping.dataType.getOrElse(
                     throw InvalidStandoffException(s"no data type provided for $standoffClass, but $dataType required"),
                   )
                   if (dataTypeFromMapping.standoffDataTypeClass != dataType) {
                     throw InvalidStandoffException(
                       s"wrong data type ${dataTypeFromMapping.standoffDataTypeClass} provided for $standoffClass, but $dataType required",
                     )
                   }
                 case None =>
                   if (xmlTag.tagItem.mapping.dataType.nonEmpty) {
                     throw InvalidStandoffException(
                       s"no data type expected for $standoffClass, but ${xmlTag.tagItem.mapping.dataType.get.standoffDataTypeClass} given",
                     )
                   }
               }
             }
           }
    } yield StandoffEntityInfoGetResponseV2(
      standoffClassInfoMap = standoffClassEntities.standoffClassInfoMap,
      standoffPropertyInfoMap = standoffPropertyEntities.standoffPropertyInfoMap,
    )
  }
}

object StandoffMappingService {

  /**
   * Pure transformation used both during mapping creation (in
   * `StandoffResponderV2.createMappingV2`) and when reading a mapping back from
   * the triplestore.
   */
  def transformMappingElementsToMappingXMLtoStandoff(
    mappingElements: Seq[MappingElement],
    defaultXSLTransformation: Option[IRI],
  ): MappingXMLtoStandoff = {
    val mappingXMLToStandoff = mappingElements.foldLeft(
      MappingXMLtoStandoff(
        namespace = Map.empty[String, Map[String, Map[String, XMLTag]]],
        defaultXSLTransformation = None,
      ),
    ) { case (acc, curEle) =>
      val tagname   = curEle.tagName
      val namespace = curEle.namespace
      val classname = curEle.className

      val namespaceMap: Map[String, Map[String, XMLTag]] =
        acc.namespace.getOrElse(namespace, Map.empty[String, Map[String, XMLTag]])

      val standoffClassIri = curEle.standoffClass

      val attributeNodes: Seq[MappingXMLAttribute]                         = curEle.attributes
      val attributeNodesByNamespace: Map[String, Seq[MappingXMLAttribute]] = attributeNodes.groupBy(_.namespace)

      val attributes: Map[String, Map[String, IRI]] = attributeNodesByNamespace.map { case (ns, attrNodes) =>
        val attributesInNamespace: Map[String, IRI] = attrNodes.foldLeft(Map.empty[String, IRI]) {
          case (acc, attrEle) =>
            val attrName = attrEle.attributeName
            if (acc.contains(attrName)) {
              throw BadRequestException("Duplicate attribute name in namespace")
            }
            acc + (attrName -> attrEle.standoffProperty)
        }
        ns -> attributesInNamespace
      }

      val dataTypeOption: Option[XMLStandoffDataTypeClass] = curEle.standoffDataTypeClass match {
        case Some(dataTypeClass: MappingStandoffDatatypeClass) =>
          val dataType = StandoffDataTypeClasses.lookup(
            dataTypeClass.datatype,
            throw BadRequestException(s"Invalid data type provided for $tagname"),
          )
          Some(
            XMLStandoffDataTypeClass(
              standoffDataTypeClass = dataType,
              dataTypeXMLAttribute = dataTypeClass.attributeName,
            ),
          )
        case None => None
      }

      val newNamespaceMap: Map[String, Map[String, XMLTag]] = namespaceMap.get(tagname) match {
        case Some(tagMap: Map[String, XMLTag]) =>
          tagMap.get(classname) match {
            case Some(_) => throw BadRequestException("Duplicate tag and classname combination in the same namespace")
            case None    =>
              val xmlElementDef = XMLTag(
                name = tagname,
                mapping = XMLTagToStandoffClass(
                  standoffClassIri = standoffClassIri,
                  attributesToProps = attributes,
                  dataType = dataTypeOption,
                ),
                separatorRequired = curEle.separatorRequired,
              )
              val combinedClassDef: Map[String, XMLTag] = namespaceMap(tagname) + (classname -> xmlElementDef)
              namespaceMap + (tagname -> combinedClassDef)
          }
        case None =>
          namespaceMap + (tagname -> Map(
            classname -> XMLTag(
              name = tagname,
              mapping = XMLTagToStandoffClass(
                standoffClassIri = standoffClassIri,
                attributesToProps = attributes,
                dataType = dataTypeOption,
              ),
              separatorRequired = curEle.separatorRequired,
            ),
          ))
      }

      MappingXMLtoStandoff(
        namespace = acc.namespace + (namespace -> newNamespaceMap),
        defaultXSLTransformation = defaultXSLTransformation,
      )
    }

    // run inversion checks for duplicate use of standoff class IRIs and property IRIs
    StandoffTagUtilV2.invertXMLToStandoffMapping(mappingXMLToStandoff)
    mappingXMLToStandoff
  }
}

object StandoffMappingServiceLive {
  private val cachesLayer: URLayer[CacheManager, EhCache[String, String] & EhCache[String, MappingXMLtoStandoff]] =
    ZLayer.fromZIO(ZIO.serviceWithZIO[CacheManager](_.createCache[String, String]("xsltCache"))) ++
      ZLayer.fromZIO(
        ZIO.serviceWithZIO[CacheManager](_.createCache[String, MappingXMLtoStandoff]("mappingCache")),
      )

  val layer: URLayer[
    AppConfig & TriplestoreService & OntologyResponderV2 & SipiService & KnoraProjectService & CacheManager,
    StandoffMappingService,
  ] =
    cachesLayer >>> ZLayer.derive[StandoffMappingServiceLive]
}
