/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology.repo.service

import java.time.Instant
import scala.language.postfixOps
import dsp.constants.SalsahGui

import org.knora.webapi.CoreSpec
import org.knora.webapi.InternalSchema
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.messages.store.triplestoremessages.SmartIriLiteralV2
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.messages.v2.responder.ontologymessages.PredicateInfoV2
import org.knora.webapi.messages.v2.responder.ontologymessages.PropertyInfoContentV2
import org.knora.webapi.messages.v2.responder.ontologymessages.ReadPropertyInfoV2
import org.knora.webapi.responders.v2.ontology.OntologyHelpers
import org.knora.webapi.routing.UnsafeZioRun
import org.knora.webapi.slice.ontology.repo.model.OntologyCacheData

/**
 * This spec is used to test [[org.knora.webapi.slice.ontology.repo.service.OntologyCache]].
 */
class OntologyCacheSpec extends CoreSpec {

  private def getCacheData = UnsafeZioRun.runOrThrow(OntologyCache.getCacheData)

  private implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

  override lazy val rdfDataObjects: List[RdfDataObject] = List(
    RdfDataObject(
      path = "test_data/ontologies/books-onto.ttl",
      name = "http://www.knora.org/ontology/0001/books"
    ),
    RdfDataObject(
      path = "test_data/all_data/books-data.ttl",
      name = "http://www.knora.org/data/0001/books"
    )
  )

  val CACHE_NOT_AVAILABLE_ERROR = "Cache not available"

  "The cache" should {

    "successfully load the cache data" in {
      UnsafeZioRun.runOrThrow(OntologyCache.getCacheData.map(_.ontologies)).size should equal(13)
    }

    "when a property was removed from an ontology, remove it from the cache as well." in {
      val iri: SmartIri       = stringFormatter.toSmartIri(rdfDataObjects.head.name)
      val hasTitlePropertyIri = stringFormatter.toSmartIri(s"${rdfDataObjects.head.name}#hasTitle")

      val previousCacheData: OntologyCacheData = getCacheData

      val previousBooksMaybe = previousCacheData.ontologies.get(iri)

      previousBooksMaybe match {
        case Some(previousBooks) =>
          // copy books-onto but remove :hasTitle property
          val newBooks = previousBooks.copy(
            ontologyMetadata = previousBooks.ontologyMetadata.copy(
              lastModificationDate = Some(Instant.now())
            ),
            properties = previousBooks.properties.view.filterKeys(_ != hasTitlePropertyIri).toMap
          )

          // update cache
          val _ = UnsafeZioRun.runOrThrow(OntologyCache.cacheUpdatedOntologyWithoutUpdatingMaps(iri, newBooks))

          // read back the cache
          val newCachedCacheData = getCacheData

          // ensure that the cache updated correctly
          val newCachedBooksMaybe = newCachedCacheData.ontologies.get(iri)
          newCachedBooksMaybe match {
            case Some(newCachedBooks) =>
              // check length
              assert(newCachedBooks.properties.size != previousBooks.properties.size)
              assert(newCachedBooks.properties.size == newBooks.properties.size)

              // check actual property
              previousBooks.properties should contain key hasTitlePropertyIri
              newCachedBooks.properties should not contain key(hasTitlePropertyIri)

            case None => fail("no books found in cache after update")
          }

        case None => fail("no books found in cache before update")
      }
    }

    "when a property was added to an ontology, add a value property to the cache." in {

      val iri: SmartIri             = stringFormatter.toSmartIri(rdfDataObjects.head.name)
      val hasDescriptionPropertyIri = stringFormatter.toSmartIri(s"${rdfDataObjects.head.name}#hasDescription")

      val previousCacheData = getCacheData

      val previousBooksMaybe = previousCacheData.ontologies.get(iri)
      previousBooksMaybe match {
        case Some(previousBooks) =>
          // copy books-onto but add :hasDescription property
          val descriptionProp = ReadPropertyInfoV2(
            entityInfoContent = PropertyInfoContentV2(
              propertyIri = hasDescriptionPropertyIri,
              predicates = Map(
                stringFormatter.toSmartIri(OntologyConstants.Rdf.Type) -> PredicateInfoV2(
                  predicateIri = stringFormatter.toSmartIri(OntologyConstants.Rdf.Type),
                  Seq(SmartIriLiteralV2(stringFormatter.toSmartIri(OntologyConstants.Owl.ObjectProperty)))
                ),
                stringFormatter.toSmartIri(OntologyConstants.Rdfs.Label) -> PredicateInfoV2(
                  predicateIri = stringFormatter.toSmartIri(OntologyConstants.Rdfs.Label),
                  Seq(
                    StringLiteralV2("A Description", language = Some("en")),
                    StringLiteralV2("Eine Beschreibung", language = Some("de"))
                  )
                ),
                stringFormatter.toSmartIri(OntologyConstants.KnoraBase.SubjectClassConstraint) -> PredicateInfoV2(
                  predicateIri = stringFormatter.toSmartIri(OntologyConstants.KnoraBase.SubjectClassConstraint),
                  Seq(SmartIriLiteralV2(iri))
                ),
                stringFormatter.toSmartIri(OntologyConstants.KnoraBase.ObjectClassConstraint) -> PredicateInfoV2(
                  predicateIri = stringFormatter.toSmartIri(OntologyConstants.KnoraBase.ObjectClassConstraint),
                  Seq(SmartIriLiteralV2(stringFormatter.toSmartIri(OntologyConstants.KnoraBase.TextValue)))
                ),
                stringFormatter.toSmartIri(SalsahGui.GuiElementClass) -> PredicateInfoV2(
                  predicateIri = stringFormatter.toSmartIri(SalsahGui.GuiElementClass),
                  Seq(SmartIriLiteralV2(stringFormatter.toSmartIri(SalsahGui.SimpleText)))
                ),
                stringFormatter.toSmartIri(SalsahGui.GuiAttribute) -> PredicateInfoV2(
                  predicateIri = stringFormatter.toSmartIri(SalsahGui.GuiAttribute),
                  Seq(
                    StringLiteralV2("size=80"),
                    StringLiteralV2("maxlength=255")
                  )
                )
              ),
              subPropertyOf = Set(stringFormatter.toSmartIri(OntologyConstants.KnoraBase.HasValue)),
              ontologySchema = InternalSchema
            ),
            isResourceProp = true,
            isEditable = true
          )
          val newProps = previousBooks.properties + (hasDescriptionPropertyIri -> descriptionProp)
          val newBooks = previousBooks.copy(
            ontologyMetadata = previousBooks.ontologyMetadata.copy(
              lastModificationDate = Some(Instant.now())
            ),
            properties = newProps
          )

          // update cache
          val _ = UnsafeZioRun.runOrThrow(OntologyCache.cacheUpdatedOntologyWithoutUpdatingMaps(iri, newBooks))

          // read back the cache
          val newCachedCacheData = getCacheData

          // ensure that the cache updated correctly
          val newCachedBooksMaybe = newCachedCacheData.ontologies.get(iri)
          newCachedBooksMaybe match {
            case Some(newCachedBooks) =>
              // check length
              assert(newCachedBooks.properties.size != previousBooks.properties.size)
              assert(newCachedBooks.properties.size == newBooks.properties.size)

              // check actual property
              previousBooks.properties should not contain key(hasDescriptionPropertyIri)
              newCachedBooks.properties should contain key hasDescriptionPropertyIri

            case None => fail(message = CACHE_NOT_AVAILABLE_ERROR)
          }

        case None => fail(message = CACHE_NOT_AVAILABLE_ERROR)
      }
    }

    "when a property was added to an ontology, add a link property and a link value property to the cache." in {

      val ontologyIri        = stringFormatter.toSmartIri("http://www.knora.org/ontology/0001/books")
      val hasPagePropertyIri = stringFormatter.toSmartIri("http://www.knora.org/ontology/0001/books#hasPage")
      val pagePropertyIri    = stringFormatter.toSmartIri("http://www.knora.org/ontology/0001/books#Page")
      val hasPageValuePropertyIri =
        stringFormatter.toSmartIri("http://www.knora.org/ontology/0001/books#hasPageValue")
      val bookIri = stringFormatter.toSmartIri("http://rdfh.ch/0001/book-instance-01")

      val previousCacheData = getCacheData
      previousCacheData.ontologies.get(ontologyIri) match {
        case Some(previousBooks) =>
          // copy books-ontology but add link from book to page
          val linkPropertyInfoContent = PropertyInfoContentV2(
            propertyIri = hasPagePropertyIri,
            predicates = Map(
              stringFormatter.toSmartIri(OntologyConstants.Rdf.Type) -> PredicateInfoV2(
                predicateIri = stringFormatter.toSmartIri(OntologyConstants.Rdf.Type),
                Seq(SmartIriLiteralV2(stringFormatter.toSmartIri(OntologyConstants.Owl.ObjectProperty)))
              ),
              stringFormatter.toSmartIri(OntologyConstants.Rdfs.Label) -> PredicateInfoV2(
                predicateIri = stringFormatter.toSmartIri(OntologyConstants.Rdfs.Label),
                Seq(
                  StringLiteralV2("Seite im Buch", language = Some("de")),
                  StringLiteralV2("Page in the book", language = Some("en"))
                )
              ),
              stringFormatter.toSmartIri(OntologyConstants.KnoraBase.SubjectClassConstraint) -> PredicateInfoV2(
                predicateIri = stringFormatter.toSmartIri(OntologyConstants.KnoraBase.SubjectClassConstraint),
                Seq(SmartIriLiteralV2(bookIri))
              ),
              stringFormatter.toSmartIri(OntologyConstants.KnoraBase.ObjectClassConstraint) -> PredicateInfoV2(
                predicateIri = stringFormatter.toSmartIri(OntologyConstants.KnoraBase.ObjectClassConstraint),
                Seq(SmartIriLiteralV2(pagePropertyIri))
              ),
              stringFormatter.toSmartIri(SalsahGui.GuiElementClass) -> PredicateInfoV2(
                predicateIri = stringFormatter.toSmartIri(SalsahGui.GuiElementClass),
                Seq(SmartIriLiteralV2(stringFormatter.toSmartIri(SalsahGui.Searchbox)))
              )
            ),
            subPropertyOf = Set(stringFormatter.toSmartIri(OntologyConstants.KnoraBase.HasLinkTo)),
            ontologySchema = InternalSchema
          )
          val hasPageProperties = ReadPropertyInfoV2(
            entityInfoContent = linkPropertyInfoContent,
            isResourceProp = true,
            isEditable = true,
            isLinkProp = true
          )
          val hasPageValueProperties = ReadPropertyInfoV2(
            entityInfoContent = OntologyHelpers.linkPropertyDefToLinkValuePropertyDef(linkPropertyInfoContent),
            isResourceProp = true,
            isEditable = true,
            isLinkValueProp = true
          )
          val newProps = previousBooks.properties +
            (hasPagePropertyIri      -> hasPageProperties) +
            (hasPageValuePropertyIri -> hasPageValueProperties)
          val newBooks = previousBooks.copy(
            ontologyMetadata = previousBooks.ontologyMetadata.copy(
              lastModificationDate = Some(Instant.now())
            ),
            properties = newProps
          )

          // update cache
          val _ = UnsafeZioRun.runOrThrow(OntologyCache.cacheUpdatedOntologyWithoutUpdatingMaps(ontologyIri, newBooks))

          // read back the cache
          val newCachedCacheData = getCacheData

          // ensure that the cache updated correctly
          newCachedCacheData.ontologies.get(ontologyIri) match {
            case Some(newCachedBooks) =>
              // check length
              assert(newCachedBooks.properties.size != previousBooks.properties.size)
              assert(newCachedBooks.properties.size == newBooks.properties.size)

              // check actual property
              previousBooks.properties should not contain key(hasPagePropertyIri)
              previousBooks.properties should not contain key(hasPageValuePropertyIri)
              newCachedBooks.properties should contain key hasPagePropertyIri
              newCachedBooks.properties should contain key hasPageValuePropertyIri

              // check isEditable == true
              val newHasPageValuePropertyMaybe = newCachedBooks.properties.get(hasPageValuePropertyIri)
              newHasPageValuePropertyMaybe should not equal None
              newHasPageValuePropertyMaybe match {
                case Some(newHasPageValueProperty) =>
                  assert(newHasPageValueProperty.isEditable)
                  assert(newHasPageValueProperty.isLinkValueProp)

                case None => fail(message = CACHE_NOT_AVAILABLE_ERROR)
              }

              newCachedBooks should equal(newBooks)

            case None => fail(message = CACHE_NOT_AVAILABLE_ERROR)
          }

        case None => fail(message = CACHE_NOT_AVAILABLE_ERROR)
      }
    }
  }

}
