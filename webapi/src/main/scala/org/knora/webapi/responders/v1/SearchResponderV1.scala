/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and André Fatton.
 *
 * This file is part of Knora.
 *
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.responders.v1

import akka.actor.Status
import akka.pattern._
import org.knora.webapi._
import org.knora.webapi.messages.v1.responder.ontologymessages.{EntityInfoGetRequestV1, EntityInfoGetResponseV1, ResourceEntityInfoV1}
import org.knora.webapi.messages.v1.responder.searchmessages._
import org.knora.webapi.messages.v1.responder.valuemessages.KnoraCalendarV1
import org.knora.webapi.messages.v1.store.triplestoremessages.{SparqlSelectRequest, SparqlSelectResponse}
import org.knora.webapi.twirl.SearchCriterion
import org.knora.webapi.util.ActorUtil._
import org.knora.webapi.util.{DateUtilV1, FormatConstants}

import scala.concurrent.Future


/**
  * Responds to requests for user search queries and returns responses in Knora API
  * v1 format.
  */
class SearchResponderV1 extends ResponderV1 {
    // Valid combinations of value types and comparison operators, for determining whether a requested search
    // criterion is valid. The valid comparison operators for search criteria involving link properties can be
    // found in this Map under OntologyConstants.KnoraBase.Resource.
    private val validTypeCompopCombos: Map[IRI, Set[SearchComparisonOperatorV1.Value]] = Map(
        OntologyConstants.KnoraBase.DateValue -> Set(
            SearchComparisonOperatorV1.EQ,
            SearchComparisonOperatorV1.NOT_EQ,
            SearchComparisonOperatorV1.GT,
            SearchComparisonOperatorV1.GT_EQ,
            SearchComparisonOperatorV1.LT,
            SearchComparisonOperatorV1.LT_EQ,
            SearchComparisonOperatorV1.EXISTS
        ),
        OntologyConstants.KnoraBase.IntValue -> Set(
            SearchComparisonOperatorV1.EQ,
            SearchComparisonOperatorV1.NOT_EQ,
            SearchComparisonOperatorV1.GT,
            SearchComparisonOperatorV1.GT_EQ,
            SearchComparisonOperatorV1.LT,
            SearchComparisonOperatorV1.LT_EQ,
            SearchComparisonOperatorV1.EXISTS
        ),
        OntologyConstants.KnoraBase.DecimalValue -> Set(
            SearchComparisonOperatorV1.EQ,
            SearchComparisonOperatorV1.NOT_EQ,
            SearchComparisonOperatorV1.GT,
            SearchComparisonOperatorV1.GT_EQ,
            SearchComparisonOperatorV1.LT,
            SearchComparisonOperatorV1.LT_EQ,
            SearchComparisonOperatorV1.EXISTS
        ),
        OntologyConstants.KnoraBase.TextValue -> Set(
            SearchComparisonOperatorV1.MATCH_BOOLEAN,
            SearchComparisonOperatorV1.MATCH,
            SearchComparisonOperatorV1.EQ,
            SearchComparisonOperatorV1.NOT_EQ,
            SearchComparisonOperatorV1.LIKE,
            SearchComparisonOperatorV1.NOT_LIKE,
            SearchComparisonOperatorV1.EXISTS
        ),
        OntologyConstants.KnoraBase.GeomValue -> Set(
            SearchComparisonOperatorV1.EXISTS
        ),
        OntologyConstants.KnoraBase.Resource -> Set(
            SearchComparisonOperatorV1.EQ,
            SearchComparisonOperatorV1.EXISTS
        ),
        OntologyConstants.KnoraBase.ColorValue -> Set(
            SearchComparisonOperatorV1.EQ,
            SearchComparisonOperatorV1.EXISTS
        ),
        OntologyConstants.KnoraBase.ListValue -> Set(
            SearchComparisonOperatorV1.EQ,
            SearchComparisonOperatorV1.EXISTS
        )
    )

    val valueUtilV1 = new ValueUtilV1(settings)

    def receive = {
        case searchGetRequest: FulltextSearchGetRequestV1 => future2Message(sender(), fulltextSearchV1(searchGetRequest), log)
        case searchGetRequest: ExtendedSearchGetRequestV1 => future2Message(sender(), extendedSearchV1(searchGetRequest), log)
        case other => sender ! Status.Failure(UnexpectedMessageException(s"Unexpected message $other of type ${other.getClass.getCanonicalName}"))
    }

    /**
      * Performs a fulltext search (simple search) and returns a [[SearchGetResponseV1]] in Knora API v1 format.
      *
      * @param searchGetRequest the user search request.
      * @return a [[SearchGetResponseV1]] containing the search results.
      */
    private def fulltextSearchV1(searchGetRequest: FulltextSearchGetRequestV1): Future[SearchGetResponseV1] = {

        case class SearchResultRowWithEntityIris(resourceClassIri: IRI, propertyIri: Option[IRI], searchResultRow: SearchResultRowV1)

        val limit = checkLimit(searchGetRequest.showNRows)

        val searchTerms = searchGetRequest.searchValue.split(" ")
        // TODO: handle case in which the user submits strings enclosed by double quotes
        // the double quotes are escaped with a backslash during input validation in route

        for {
        // Get the number of search results.
            countSparql <- Future {
                queries.sparql.v1.txt.searchFulltext(
                    countResults = true,
                    searchTerms = searchTerms,
                    triplestore = settings.triplestoreType,
                    preferredLanguage = searchGetRequest.userProfile.userData.lang,
                    fallbackLanguage = settings.fallbackLanguage,
                    projectIriOption = searchGetRequest.filterByProject,
                    restypeIriOption = searchGetRequest.filterByRestype,
                    offsetOption = None,
                    limitOption = None,
                    separator = FormatConstants.INFORMATION_SEPARATOR_ONE
                ).toString()
            }
            // _ = println("================" + countSparql)
            countResponse: SparqlSelectResponse <- (storeManager ? SparqlSelectRequest(countSparql)).mapTo[SparqlSelectResponse]
            resultCount = countResponse.results.bindings.head.rowMap("count").toInt
            // _ = println(s"Result count: $resultCount")

            // Get the search results with paging.
            pagingSparql = queries.sparql.v1.txt.searchFulltext(
                countResults = false,
                searchTerms = searchTerms,
                triplestore = settings.triplestoreType,
                preferredLanguage = searchGetRequest.userProfile.userData.lang,
                fallbackLanguage = settings.fallbackLanguage,
                projectIriOption = searchGetRequest.filterByProject,
                restypeIriOption = searchGetRequest.filterByRestype,
                offsetOption = Some(searchGetRequest.startAt),
                limitOption = Some(limit),
                separator = FormatConstants.INFORMATION_SEPARATOR_ONE
            ).toString()

            // _ = println("================" + pagingSparql)

            searchResponse: SparqlSelectResponse <- (storeManager ? SparqlSelectRequest(pagingSparql)).mapTo[SparqlSelectResponse]

            searchResultRowsWithEntityIris: Seq[SearchResultRowWithEntityIris] = searchResponse.results.bindings.map {
                row =>
                    val resourceIri = row.rowMap("resource")
                    val attachedToUser = row.rowMap("attachedToUser")
                    val attachedToProject = row.rowMap("attachedToProject")
                    val permissionAssertions = row.rowMap("permissionAssertions")
                    val assertions = PermissionUtilV1.parsePermissions(permissionAssertions, attachedToUser, attachedToProject)
                    val permission = PermissionUtilV1.getUserPermissionV1(resourceIri, assertions, searchGetRequest.userProfile)

                    // The 'match' column contains the matching literal's value type and value, delimited
                    // by a non-printing delimiter character, Unicode INFORMATION SEPARATOR ONE, that should never occur in data.
                    // This only exists in case the matching literal was not an `rdfs:label`.
                    val (matchValueTypeID: Option[String], resourceProperty: Option[IRI], matchValue: Option[String]) = row.rowMap.get("match") match {
                        case Some(matchingValues: String) =>
                            val Array(valType, resProp, matchVal) = matchingValues.split(FormatConstants.INFORMATION_SEPARATOR_ONE)
                            (Some(valType), Some(resProp), Some(matchVal))
                        case None => (None, None, None)
                    }



                    val searchResultRow = SearchResultRowV1(
                        obj_id = resourceIri,
                        preview_path = row.rowMap.get("previewPath") match {
                            case Some(previewPath) => Some(valueUtilV1.makeSipiImagePreviewGetUrlFromFilename(previewPath))
                            case None => None
                        },
                        iconsrc = None,
                        iconlabel = None,
                        icontitle = None,
                        valuetype_id = matchValueTypeID match {
                            case Some(valType) => Vector(OntologyConstants.Rdfs.Label, valType)
                            case None => Vector(OntologyConstants.Rdfs.Label)
                        },
                        valuelabel = Vector("Label"),
                        value = matchValue match {
                            case Some(matchVal) => Vector(row.rowMap("resourceLabel"), matchVal)
                            case None => Vector(row.rowMap("resourceLabel"))
                        },
                        preview_nx = row.rowMap.get("previewDimX") match {
                            case Some(previewDimX) => previewDimX.toInt
                            case None => settings.defaultIconSizeDimX
                        },
                        preview_ny = row.rowMap.get("previewDimY") match {
                            case Some(previewDimY) => previewDimY.toInt
                            case None => settings.defaultIconSizeDimY
                        },
                        rights = permission
                    )

                    SearchResultRowWithEntityIris(
                        resourceClassIri = row.rowMap("resourceClass"),
                        propertyIri = resourceProperty,
                        searchResultRow = searchResultRow
                    )
            }.filter(_.searchResultRow.rights.nonEmpty) // only return the rows the given user has at least restricted view permissions on


            // Get the IRIs of all the properties mentioned in the search results.
            propertyIris: Set[IRI] = searchResultRowsWithEntityIris.flatMap(_.propertyIri).toSet

            // Get the IRIs of all the resource classes mentioned in the search results.
            resourceClassIris: Set[IRI] = searchResultRowsWithEntityIris.map(_.resourceClassIri).toSet

            // Get information about those entities from the ontology responder.

            entityInfoRequest = EntityInfoGetRequestV1(
                resourceClassIris = resourceClassIris,
                propertyIris = propertyIris,
                userProfile = searchGetRequest.userProfile
            )

            entityInfoResponse <- (responderManager ? entityInfoRequest).mapTo[EntityInfoGetResponseV1]

            // Add that information to the search results.

            subjects = searchResultRowsWithEntityIris.map {
                (searchResultRowWithEntityIris: SearchResultRowWithEntityIris) =>
                    val searchResultRow: SearchResultRowV1 = searchResultRowWithEntityIris.searchResultRow
                    val resourceEntityInfoMap: ResourceEntityInfoV1 = entityInfoResponse.resourceEntityInfoMap(searchResultRowWithEntityIris.resourceClassIri)
                    val resourceClassLabel = resourceEntityInfoMap.getPredicateObject(OntologyConstants.Rdfs.Label)
                    val resourceClassIcon = resourceEntityInfoMap.getPredicateObject(OntologyConstants.KnoraBase.ResourceIcon)
                    val resourceClassIri = searchResultRowWithEntityIris.resourceClassIri

                    val resourceClassIconURL = resourceClassIcon match {
                        case Some(resClassIcon) => Some(valueUtilV1.makeResourceClassIconURL(resourceClassIri, resClassIcon))
                        case _ => None
                    }

                    searchResultRow.copy(
                        preview_path = searchResultRow.preview_path match {
                            case Some(path) => Some(path)
                            case None =>
                                // If there is no preview image, use the resource class icon from the ontology.
                                resourceClassIconURL
                        },
                        icontitle = resourceClassLabel,
                        iconlabel = resourceClassLabel,
                        iconsrc = resourceClassIconURL,
                        valuelabel = searchResultRow.valuelabel ++ searchResultRowWithEntityIris.propertyIri.flatMap {
                            iri =>
                                // If the search result contained a property IRI, add its label here.
                                val propertyEntityInfoMap = entityInfoResponse.propertyEntityInfoMap(iri)
                                propertyEntityInfoMap.getPredicateObject(OntologyConstants.Rdfs.Label)
                        }
                    )
            }

            (maxPreviewDimX, maxPreviewDimY) = findMaxPreviewDimensions(subjects)

            results = SearchGetResponseV1(
                userdata = searchGetRequest.userProfile.userData,
                subjects = subjects,
                nhits = resultCount.toString,
                thumb_max = SearchPreviewDimensionsV1(maxPreviewDimX, maxPreviewDimY),
                paging = makePaging(offset = searchGetRequest.startAt, limit = limit, resultCount = resultCount)
            )
        } yield results
    }

    /**
      * Performs an extended search (structured search) and returns a [[SearchGetResponseV1]] in Knora API v1 format.
      *
      * @param searchGetRequest the user search request
      * @return a [[SearchGetResponseV1]] containing the search results.
      */
    private def extendedSearchV1(searchGetRequest: ExtendedSearchGetRequestV1): Future[SearchGetResponseV1] = {

        import org.knora.webapi.util.InputValidation

        val limit = checkLimit(searchGetRequest.showNRows)

        for {
        // get information about all the properties involved
            propertyInfo: EntityInfoGetResponseV1 <- (responderManager ? EntityInfoGetRequestV1(propertyIris = searchGetRequest.propertyIri.toSet, userProfile = searchGetRequest.userProfile)).mapTo[EntityInfoGetResponseV1]

            /*
             * handle parallel lists here: propertyIri, comparisonOperator, SearchValue
             *
             * transform parallel Lists into one List/Iterable of Map/HashMap
             * by calling "zipped" on a 3Tuple of Lists and calling map on its result
             *
             * http://stackoverflow.com/questions/1157564/zipwith-mapping-over-multiple-seq-in-scala
             */
            searchCriteria: Seq[SearchCriterion] = (searchGetRequest.propertyIri, searchGetRequest.compareProps, searchGetRequest.searchValue).zipped.map(
                (prop, compop, searchval) => {
                    val propertyEntityInfo = propertyInfo.propertyEntityInfoMap(prop)

                    // If the property is a linking property, we pretend its knora-base:objectClassConstraint is knora-base:Resource, so validTypeCompopCombos will work.
                    val propertyObjectClassConstraint: IRI = if (propertyEntityInfo.isLinkProp) {
                        OntologyConstants.KnoraBase.Resource
                    } else {
                        propertyEntityInfo.getPredicateObject(OntologyConstants.KnoraBase.ObjectClassConstraint).getOrElse(throw InconsistentTriplestoreDataException(s"Property $prop has no knora-base:objectClassConstraint"))
                    }

                    // check if the valuetype of the given propertyIri conforms to the given compop
                    if (!validTypeCompopCombos(propertyObjectClassConstraint).contains(compop)) {
                        // the given combination of propertyIri valtype and compop is not allowed
                        throw new BadRequestException(s"The combination of propertyIri and compop is invalid")
                    }

                    val searchParamWithoutValue = SearchCriterion(
                        propertyIri = prop,
                        comparisonOperator = compop,
                        valueType = propertyObjectClassConstraint
                    )

                    // check and convert the searchval if necessary (e.g. check if a given string is numeric or convert a date string to a date)

                    if (compop == SearchComparisonOperatorV1.EXISTS) {
                        // EXISTS doesn't need the searchval at all.
                        searchParamWithoutValue
                    } else {
                        propertyObjectClassConstraint match {
                            case OntologyConstants.KnoraBase.DateValue =>

                                //
                                // It is a date, parse and convert it to JD
                                //

                                val datestring = InputValidation.toDate(searchval, () => throw new BadRequestException(s"Invalid date format: $searchval"))

                                // parse date: Calendar:YYYY-MM-DD[:YYYY-MM-DD]
                                val parsedDate = datestring.split(InputValidation.calendar_separator)
                                val calendar = KnoraCalendarV1.lookup(parsedDate(0))

                                // val daysInMonth = Calendar.DAY_OF_MONTH // will be used to determine the number of days in the given month
                                // val monthsInYear = Calendar.MONTH // will be used to determine the number of months in the given year (generic for other calendars)

                                val (dateStart, dateEnd) = if (parsedDate.length > 2) {
                                    // it is a period: 0 : cal | 1 : start | 2 : end

                                    val periodStart = DateUtilV1.dateString2DateRange(parsedDate(1), calendar).start
                                    val periodEnd = DateUtilV1.dateString2DateRange(parsedDate(2), calendar).end

                                    val start = DateUtilV1.convertDateToJulianDay(periodStart)
                                    val end = DateUtilV1.convertDateToJulianDay(periodEnd)

                                    // check if end is bigger than start (the user could have submitted a period where start is bigger than end)
                                    if (start > end) throw BadRequestException(s"Invalid input for period: start is bigger than end: $searchval")

                                    (start, end)
                                } else {
                                    // no period: 0 : cal | 1 : start

                                    val dateRange = DateUtilV1.dateString2DateRange(parsedDate(1), calendar)

                                    val start = DateUtilV1.convertDateToJulianDay(dateRange.start)
                                    val end = DateUtilV1.convertDateToJulianDay(dateRange.end)

                                    (start, end)
                                }

                                searchParamWithoutValue.copy(
                                    dateStart = Some(dateStart),
                                    dateEnd = Some(dateEnd)
                                )

                            case OntologyConstants.KnoraBase.TextValue =>
                                // http://www.morelab.deusto.es/code_injection/
                                // http://stackoverflow.com/questions/29601839/prevent-sparql-injection-generic-solution-triplestore-independent
                                val searchString = InputValidation.toSparqlEncodedString(searchval)

                                val (matchBooleanPositiveTerms, matchBooleanNegativeTerms) = if (compop == SearchComparisonOperatorV1.MATCH_BOOLEAN) {
                                    val terms = searchString.asInstanceOf[String].split("\\s+").toSet
                                    val negativeTerms = terms.filter(_.startsWith("-"))
                                    val positiveTerms = terms -- negativeTerms
                                    val negativeTermsWithoutPrefixes = negativeTerms.map(_.stripPrefix("-"))
                                    val positiveTermsWithoutPrefixes = positiveTerms.map(_.stripPrefix("+"))
                                    (positiveTermsWithoutPrefixes, negativeTermsWithoutPrefixes)
                                } else {
                                    (Set.empty[String], Set.empty[String])
                                }

                                searchParamWithoutValue.copy(
                                    searchValue = Some(searchString),
                                    matchBooleanPositiveTerms = matchBooleanPositiveTerms,
                                    matchBooleanNegativeTerms = matchBooleanNegativeTerms
                                )

                            case OntologyConstants.KnoraBase.IntValue =>
                                // check if string is an integer
                                val searchString = InputValidation.toInt(searchval, () => throw new BadRequestException(s"Given searchval is not an integer: $searchval")).toString
                                searchParamWithoutValue.copy(searchValue = Some(searchString))

                            case OntologyConstants.KnoraBase.DecimalValue =>
                                // check if string is a decimal number
                                val searchString = InputValidation.toBigDecimal(searchval, () => throw new BadRequestException(s"Given searchval is not a decimal number: $searchval")).toString
                                searchParamWithoutValue.copy(searchValue = Some(searchString))

                            case OntologyConstants.KnoraBase.Resource =>
                                // check if string is a valid IRI
                                val searchString = InputValidation.toIri(searchval, () => throw new BadRequestException(s"Given searchval is not a valid IRI: $searchval"))
                                searchParamWithoutValue.copy(searchValue = Some(searchString))

                            case OntologyConstants.KnoraBase.ColorValue =>
                                // check if string is a hexadecimal RGB-color value
                                val searchString = InputValidation.toColor(searchval, () => throw new BadRequestException(s"Invalid color format: $searchval"))
                                searchParamWithoutValue.copy(searchValue = Some(searchString))

                            case OntologyConstants.KnoraBase.GeomValue =>
                                // this only will be used with compop EXISTS
                                searchParamWithoutValue.copy(searchValue = Some(""))

                            case OntologyConstants.KnoraBase.ListValue =>
                                // check if string represents a node in a list
                                val searchString = InputValidation.toIri(searchval, () => throw new BadRequestException(s"Given searchval is not a formally valid IRI $searchval"))
                                searchParamWithoutValue.copy(searchValue = Some(searchString))

                            case other => throw new BadRequestException(s"The value type for the given property $prop is unknown.")
                        }
                    }
                }
            )

            // Count the number of search results.
            countSparql = queries.sparql.v1.txt.searchExtended(
                countResults = true,
                searchCriteria = searchCriteria,
                triplestore = settings.triplestoreType,
                preferredLanguage = searchGetRequest.userProfile.userData.lang,
                fallbackLanguage = settings.fallbackLanguage,
                projectIriOption = searchGetRequest.filterByProject,
                restypeIriOption = searchGetRequest.filterByRestype,
                ownerIriOption = searchGetRequest.filterByOwner,
                offsetOption = None,
                limitOption = None
            ).toString()
            countResponse: SparqlSelectResponse <- (storeManager ? SparqlSelectRequest(countSparql)).mapTo[SparqlSelectResponse]
            resultCount = countResponse.results.bindings.head.rowMap("count").toInt
            // _ = log.debug(s"Result count: $resultCount")

            // Get the search results with paging.
            pagingSparql = queries.sparql.v1.txt.searchExtended(
                countResults = false,
                searchCriteria = searchCriteria,
                triplestore = settings.triplestoreType,
                preferredLanguage = searchGetRequest.userProfile.userData.lang,
                fallbackLanguage = settings.fallbackLanguage,
                projectIriOption = searchGetRequest.filterByProject,
                restypeIriOption = searchGetRequest.filterByRestype,
                ownerIriOption = searchGetRequest.filterByOwner,
                offsetOption = Some(searchGetRequest.startAt),
                limitOption = Some(limit)
            ).toString()

            searchResponse: SparqlSelectResponse <- (storeManager ? SparqlSelectRequest(pagingSparql)).mapTo[SparqlSelectResponse]

            // Collect all the resource class IRIs mentioned in the search results.
            resourceClassIris: Set[IRI] = searchResponse.results.bindings.map(_.rowMap("resourceClass")).toSet

            // Get information about those resource classes from the ontology responder.

            entityInfoRequest = EntityInfoGetRequestV1(
                resourceClassIris = resourceClassIris,
                userProfile = searchGetRequest.userProfile
            )

            entityInfoResponse <- (responderManager ? entityInfoRequest).mapTo[EntityInfoGetResponseV1]

            // Convert the query result rows into SearchResultRowV1 objects.

            subjects: Seq[SearchResultRowV1] = searchResponse.results.bindings.map {
                row =>
                    val resourceClassIri = row.rowMap("resourceClass")
                    val resourceEntityInfoMap = entityInfoResponse.resourceEntityInfoMap(resourceClassIri)
                    val resourceClassLabel = resourceEntityInfoMap.getPredicateObject(OntologyConstants.Rdfs.Label)
                    val resourceClassIcon = resourceEntityInfoMap.getPredicateObject(OntologyConstants.KnoraBase.ResourceIcon) // TODO: make a Sipi URL.

                    val resourceIri = row.rowMap("resource")
                    val attachedToUser = row.rowMap("attachedToUser")
                    val attachedToProject = row.rowMap("attachedToProject")
                    val permissionAssertions = row.rowMap("permissionAssertions")
                    val assertions = PermissionUtilV1.parsePermissions(permissionAssertions, attachedToUser, attachedToProject)
                    val permission = PermissionUtilV1.getUserPermissionV1(resourceIri, assertions, searchGetRequest.userProfile)
                    val literals = searchCriteria.indices.map(paramNum => row.rowMap(s"literal$paramNum"))
                    val valueTypeIDs = searchCriteria.map(_.valueType)
                    val valueLabels = searchCriteria.map {
                        param =>
                            val propertyIri = param.propertyIri
                            propertyInfo.propertyEntityInfoMap(propertyIri).getPredicateObject(OntologyConstants.Rdfs.Label) match {
                                case Some(label) => label
                                case None => throw InconsistentTriplestoreDataException(s"Property $propertyIri has no rdfs:label")
                            }
                    }

                    val resourceClassIconURL = resourceClassIcon match {
                        case Some(resClassIcon) => Some(valueUtilV1.makeResourceClassIconURL(resourceClassIri, resClassIcon))
                        case _ => None
                    }

                    SearchResultRowV1(
                        obj_id = resourceIri,
                        preview_path = row.rowMap.get("previewPath") match {
                            case Some(path) => Some(valueUtilV1.makeSipiImagePreviewGetUrlFromFilename(path))
                            case None =>
                                // If there is no preview image, use the resource class icon from the ontology.
                                resourceClassIconURL
                        },
                        iconsrc = resourceClassIconURL,
                        icontitle = resourceClassLabel,
                        iconlabel = resourceClassLabel,
                        valuetype_id = OntologyConstants.Rdfs.Label +: valueTypeIDs,
                        valuelabel = "Label" +: valueLabels,
                        value = row.rowMap("resourceLabel") +: literals,
                        preview_nx = row.rowMap.get("previewDimX") match {
                            case Some(previewDimX) => previewDimX.toInt
                            case None => settings.defaultIconSizeDimX
                        },
                        preview_ny = row.rowMap.get("previewDimY") match {
                            case Some(previewDimY) => previewDimY.toInt
                            case None => settings.defaultIconSizeDimY
                        },
                        rights = permission
                    )
            }.filter(_.rights.nonEmpty) // only return the rows the given user has at least restricted view permissions on

            (maxPreviewDimX, maxPreviewDimY) = findMaxPreviewDimensions(subjects)

            results = SearchGetResponseV1(
                userdata = searchGetRequest.userProfile.userData,
                subjects = subjects,
                nhits = resultCount.toString,
                thumb_max = SearchPreviewDimensionsV1(maxPreviewDimX, maxPreviewDimY),
                paging = makePaging(offset = searchGetRequest.startAt, limit = limit, resultCount = resultCount)
            )
        } yield results
    }

    /**
      * Creates a list of available search result pages.
      *
      * @param offset the requested result offset.
      * @param limit the maximum number of results per page.
      * @param resultCount the total number of results found.
      * @return a list of [[SearchResultPage]] objects.
      */
    private def makePaging(offset: Int, limit: Int, resultCount: Int): Seq[SearchResultPage] = {
        val pageRemainder = resultCount % limit
        val numPages = (resultCount / limit) + (if (pageRemainder > 0) 1 else 0)
        val currentPageNum = offset / limit // The offset might put us in the middle of a page, but that's OK.

        (0 until numPages).map {
            pageNum => SearchResultPage(
                current = pageNum == currentPageNum,
                start_at = pageNum * limit,
                show_nrows = if (pageNum < numPages - 1) {
                    limit
                } else {
                    // The last page might contain fewer results than the limit.
                    pageRemainder
                }
            )
        }
    }

    /**
      * Checks the requested search result limit to ensure it's within acceptable bounds.
      *
      * @return the corrected search result limit.
      */
    private def checkLimit(limit: Int): Int = {
        if (limit <= 0) {
            throw BadRequestException("Search limit must be greater than 0")
        }

        if (limit > settings.maxResultsPerSearchResultPage) settings.maxResultsPerSearchResultPage else limit
    }

    /**
      * Given a list of search results, finds the maximum X and Y dimensions of their preview images.
      *
      * @param subjects a list of search results.
      * @return the maximum X and Y dimensions of the preview images of the search results.
      */
    private def findMaxPreviewDimensions(subjects: Seq[SearchResultRowV1]): (Int, Int) = {
        subjects.foldLeft((0, 0)) {
            case ((accX, accY), searchResultRow) =>
                val newMaxX = if (searchResultRow.preview_nx > accX) searchResultRow.preview_nx else accX
                val newMaxY = if (searchResultRow.preview_ny > accY) searchResultRow.preview_ny else accY
                (newMaxX, newMaxY)
        }
    }
}
