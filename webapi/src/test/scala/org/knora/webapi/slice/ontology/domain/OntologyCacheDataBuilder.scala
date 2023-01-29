package org.knora.webapi.slice.ontology.domain

import org.knora.webapi.ApiV2Complex
import org.knora.webapi.InternalSchema
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.v2.responder.ontologymessages.ClassInfoContentV2
import org.knora.webapi.messages.v2.responder.ontologymessages.OntologyMetadataV2
import org.knora.webapi.messages.v2.responder.ontologymessages.OwlCardinality.KnoraCardinalityInfo
import org.knora.webapi.messages.v2.responder.ontologymessages.ReadClassInfoV2
import org.knora.webapi.messages.v2.responder.ontologymessages.ReadOntologyV2
import org.knora.webapi.responders.v2.ontology.Cache._
import org.knora.webapi.slice.ontology.domain.ReadClassInfoV2Builder.ClassInfoContentV2Builder.BuilderClassInfoContentV2Builder
import org.knora.webapi.slice.ontology.domain.SmartIriConversion.BetterSmartIri
import org.knora.webapi.slice.ontology.domain.SmartIriConversion.BetterSmartIriKeyMap
import org.knora.webapi.slice.ontology.domain.SmartIriConversion.TestSmartIriFromInternalIri
import org.knora.webapi.slice.ontology.domain.model.Cardinality
import org.knora.webapi.slice.resourceinfo.domain.InternalIri

object SmartIriConversion {

  implicit class BetterSmartIri(iri: SmartIri) {
    def internal: SmartIri = iri.toOntologySchema(InternalSchema)
  }

  implicit class BetterSmartIriKeyMap[V](iris: Map[SmartIri, V]) {
    def internal: Map[SmartIri, V] = iris.map { case (a, b) => (a.internal, b) }
  }

  implicit class TestSmartIriFromInternalIri(internalIri: InternalIri) {
    private implicit val sf: StringFormatter = {
      StringFormatter.initForTest()
      StringFormatter.getGeneralInstance
    }

    def smartIri: SmartIri = sf.toSmartIri(internalIri.value, requireInternal = true)
  }
}

object OntologyCacheDataBuilder {

  case class Builder(ocd: OntologyCacheData = empty) {
    def addOntology(ro: ReadOntologyV2): Builder = copy(
      ocd.copy(ontologies = ocd.ontologies ++ Map(ro.ontologyMetadata.ontologyIri -> ro))
    )

    def addOntology(ro: ReadOntologyV2Builder.Builder): Builder = addOntology(ro.build)

    def build: OntologyCacheData = ocd
  }

  implicit class BuilderOntologyCacheData(ocd: OntologyCacheData) {
    def toBuilder: Builder = Builder(ocd)
  }

  val empty: OntologyCacheData =
    OntologyCacheData(Map.empty, Map.empty, Map.empty, Map.empty, Map.empty, Map.empty, Map.empty, Map.empty, Set.empty)

  val builder: Builder                        = Builder(empty)

  def builder(ontologyIri: SmartIri): Builder = Builder(empty).addOntology(ReadOntologyV2Builder.builder(ontologyIri))

  def cardinalitiesMap(propertyIri: SmartIri, cardinality: Cardinality): Map[SmartIri, KnoraCardinalityInfo] =
    Map(propertyIri.internal -> KnoraCardinalityInfo(cardinality))
}

object ReadOntologyV2Builder {
  case class Builder(ro: ReadOntologyV2) {

    def addClassInfo(info: ReadClassInfoV2): Builder =
      copy(ro = ro.copy(classes = ro.classes + (info.entityInfoContent.classIri.internal -> info)))

    def addClassInfo(infoBuilder: ReadClassInfoV2Builder.Builder): Builder = addClassInfo(infoBuilder.build)

    def build: ReadOntologyV2 = ro
  }

  implicit class BuilderReadOntologyV2(rci: ReadOntologyV2) {
    def toBuilder: Builder = Builder(rci)
  }

  def builder(ontologyIri: InternalIri): Builder = Builder(
    ReadOntologyV2(OntologyMetadataV2(ontologyIri.smartIri), Map.empty)
  )

  def builder(ontologyIri: SmartIri): Builder = Builder(
    ReadOntologyV2(OntologyMetadataV2(ontologyIri.internal), Map.empty)
  )
}

object ReadClassInfoV2Builder {

  case class Builder(rci: ReadClassInfoV2) {
    def build: ReadClassInfoV2 = rci

    def setSuperClassIris(iris: List[SmartIri]): Builder =
      copy(rci.copy(allBaseClasses = iris.map(_.internal)))

    def setSuperClassIri(iri: SmartIri): Builder = setSuperClassIris(List(iri))

    def setEntityInfoContent(content: ClassInfoContentV2): Builder = copy(rci.copy(entityInfoContent = content))

    def setEntityInfoContent(builder: ClassInfoContentV2Builder.Builder): Builder = setEntityInfoContent(builder.build)

    def setDirectCardinalities(c: Map[SmartIri, KnoraCardinalityInfo]): Builder =
      setEntityInfoContent(rci.entityInfoContent.toBuilder.setDirectCardinalities(c))

    def setInheritedCardinalities(c: Map[SmartIri, KnoraCardinalityInfo]): Builder =
      copy(rci = rci.copy(inheritedCardinalities = c.internal))

    def addSuperClass(classIri: InternalIri): Builder =
      addSuperClass(classIri.smartIri)

    def addSuperClass(classIri: SmartIri): Builder =
      copy(rci = rci.copy(allBaseClasses = rci.allBaseClasses.prepended(classIri)))
  }

  implicit class BuilderReadClassInfoV2(rci: ReadClassInfoV2) {
    def toBuilder: Builder = Builder(rci)
  }

  def builder(classIri: InternalIri): Builder = builder(classIri.smartIri)

  def builder(classIri: SmartIri): Builder = Builder(
    ReadClassInfoV2(ClassInfoContentV2Builder.builder(classIri).build, allBaseClasses = List.empty)
  )

  object ClassInfoContentV2Builder {

    case class Builder(cic: ClassInfoContentV2) {
      def build: ClassInfoContentV2 = cic

      def setDirectCardinalities(c: Map[SmartIri, KnoraCardinalityInfo]): Builder =
        copy(cic = cic.copy(directCardinalities = c.internal))
    }

    implicit class BuilderClassInfoContentV2Builder(cic: ClassInfoContentV2) {
      def toBuilder: Builder = Builder(cic)
    }

    def builder(classIri: SmartIri): Builder = Builder(
      ClassInfoContentV2(classIri.internal, ontologySchema = ApiV2Complex)
    )
  }
}
