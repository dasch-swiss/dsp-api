/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and Sepideh Alassi.
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

import akka.pattern._
import org.knora.webapi._
import org.knora.webapi.messages.v1.responder.ontologymessages.{EntityInfoGetRequestV1, EntityInfoGetResponseV1}
import org.knora.webapi.messages.v1.responder.searchmessages._
import org.knora.webapi.messages.v1.responder.valuemessages.KnoraCalendarV1
import org.knora.webapi.messages.v1.store.triplestoremessages.{SparqlSelectRequest, SparqlSelectResponse, VariableResultsRow}
import org.knora.webapi.responders.Responder
import org.knora.webapi.twirl.SearchCriterion
import org.knora.webapi.util.ActorUtil._
import org.knora.webapi.util.{DateUtilV1, PermissionUtilV1}

import scala.concurrent.Future


/**
  * Responds to requests for user search queries and returns responses in Knora API
  * v1 format.
  */
class SearchResponderV1 extends Responder {
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

    /**
      * Represents a matching value in a search result.
      *
      * @param valueTypeIri        the type of the value that matched.
      * @param propertyIri         the IRI of the property that points to the value.
      * @param propertyLabel       the label of the property that points to the value.
      * @param literal             the literal that matched.
      * @param valuePermissionCode the user's permission code on the value.
      */
    private case class MatchingValue(valueTypeIri: IRI,
                                     propertyIri: IRI,
                                     propertyLabel: String,
                                     literal: String,
                                     valuePermissionCode: Option[Int])

    val valueUtilV1 = new ValueUtilV1(settings)

    def receive = {
        case searchGetRequest: FulltextSearchGetRequestV1 => future2Message(sender(), fulltextSearchV1(searchGetRequest), log)
        case searchGetRequest: ExtendedSearchGetRequestV1 => future2Message(sender(), extendedSearchV1(searchGetRequest), log)
        case other => handleUnexpectedMessage(sender(), other, log, this.getClass.getName)
    }

    /**
      * Performs a fulltext search (simple search) and returns a [[SearchGetResponseV1]] in Knora API v1 format.
      *
      * @param searchGetRequest the user search request.
      * @return a [[SearchGetResponseV1]] containing the search results.
      */
    private def fulltextSearchV1(searchGetRequest: FulltextSearchGetRequestV1): Future[SearchGetResponseV1] = {

        val limit = checkLimit(searchGetRequest.showNRows)

        val searchTerms = searchGetRequest.searchValue.split(" ")
        // TODO: handle case in which the user submits strings enclosed by double quotes
        // the double quotes are escaped with a backslash during input validation in route

        for {
        // Get the search results with paging.
            searchSparql <- Future(queries.sparql.v1.txt.searchFulltext(
                triplestore = settings.triplestoreType,
                searchTerms = searchTerms,
                preferredLanguage = searchGetRequest.userProfile.userData.lang,
                fallbackLanguage = settings.fallbackLanguage,
                projectIriOption = searchGetRequest.filterByProject,
                restypeIriOption = searchGetRequest.filterByRestype
            ).toString())

            // _ = println("================" + pagingSparql)

            searchResponse: SparqlSelectResponse <- (storeManager ? SparqlSelectRequest(searchSparql)).mapTo[SparqlSelectResponse]

            // Get the IRIs of all the properties mentioned in the search results.
            propertyIris: Set[IRI] = searchResponse.results.bindings.flatMap(_.rowMap.get("resourceProperty")).toSet

            // Get the IRIs of all the resource classes mentioned in the search results.
            resourceClassIris: Set[IRI] = searchResponse.results.bindings.map(_.rowMap("resourceClass")).toSet

            // Get information about those entities from the ontology responder.

            entityInfoRequest = EntityInfoGetRequestV1(
                resourceClassIris = resourceClassIris,
                propertyIris = propertyIris,
                userProfile = searchGetRequest.userProfile
            )

            entityInfoResponse <- (responderManager ? entityInfoRequest).mapTo[EntityInfoGetResponseV1]

            // Group the search results by resource IRI.
            groupedByResourceIri: Map[IRI, Seq[VariableResultsRow]] = searchResponse.results.bindings.groupBy(_.rowMap("resource"))

            // Convert the query result rows into SearchResultRowV1 objects.

            subjects: Vector[SearchResultRowV1] = groupedByResourceIri.foldLeft(Vector.empty[SearchResultRowV1]) {
                case (subjectsAcc, (resourceIri, rows)) =>
                    val firstRowMap = rows.head.rowMap

                    // Does the user have permission to see the resource?

                    val resourceCreator = firstRowMap("resourceCreator")
                    val resourceProject = firstRowMap("resourceProject")
                    val resourcePermissions = firstRowMap("resourcePermissions")

                    val resourcePermissionCode: Option[Int] = PermissionUtilV1.getUserPermissionV1(subjectIri = resourceIri, subjectCreator = resourceCreator, subjectProject = resourceProject, subjectPermissionLiteral = resourcePermissions, userProfile = searchGetRequest.userProfile)

                    if (resourcePermissionCode.nonEmpty) {
                        // Yes. Get more information about the resource.

                        val resourceClassIri = firstRowMap("resourceClass")
                        val resourceEntityInfoMap = entityInfoResponse.resourceEntityInfoMap(resourceClassIri)
                        val resourceClassLabel = resourceEntityInfoMap.getPredicateObject(predicateIri = OntologyConstants.Rdfs.Label, preferredLangs = Some(searchGetRequest.userProfile.userData.lang, settings.fallbackLanguage))
                        val resourceClassIcon = resourceEntityInfoMap.getPredicateObject(OntologyConstants.KnoraBase.ResourceIcon)
                        val resourceLabel = firstRowMap.getOrElse("resourceLabel", throw InconsistentTriplestoreDataException(s"Resource $resourceIri has no rdfs:label"))

                        // Collect the matching values in the resource.
                        val mapOfMatchingValues: Map[IRI, MatchingValue] = rows.filter(_.rowMap.get("valueObject").nonEmpty).foldLeft(Map.empty[IRI, MatchingValue]) {
                            case (valuesAcc, row) =>
                                // Convert the permissions on the matching value object into a ValueProps.
                                val valueIri = row.rowMap("valueObject")
                                val literal = row.rowMap("literal")
                                val valueCreator = row.rowMap("valueCreator")
                                val valuePermissionsLiteral = row.rowMap("valuePermissions")
                                val valuePermissionCode = PermissionUtilV1.getUserPermissionV1(
                                    subjectIri = valueIri, subjectCreator = valueCreator,
                                    subjectProject = resourceProject,
                                    subjectPermissionLiteral = valuePermissionsLiteral,
                                    userProfile = searchGetRequest.userProfile
                                )

                                val value: Option[(IRI, MatchingValue)] = valuePermissionCode.map {
                                    permissionCode =>
                                        val propertyIri = row.rowMap("resourceProperty")
                                        val propertyLabel = entityInfoResponse.propertyEntityInfoMap(propertyIri).getPredicateObject(OntologyConstants.Rdfs.Label, preferredLangs = Some(searchGetRequest.userProfile.userData.lang, settings.fallbackLanguage)) match {
                                            case Some(label) => label
                                            case None => throw InconsistentTriplestoreDataException(s"Property $propertyIri has no rdfs:label")
                                        }

                                        valueIri -> MatchingValue(
                                            valueTypeIri = row.rowMap("valueObjectType"),
                                            propertyIri = propertyIri,
                                            propertyLabel = propertyLabel,
                                            literal = literal,
                                            valuePermissionCode = valuePermissionCode
                                        )
                                }

                                valuesAcc ++ value
                        }

                        // Sort by value IRI, then by property IRI, so the results are consistent between requests.
                        val vectorOfMatchingValues: Vector[(IRI, MatchingValue)] = mapOfMatchingValues.toVector
                        val matchingValuesSortedByValueIri: Vector[MatchingValue] = vectorOfMatchingValues.sortBy(_._1).map(_._2)
                        val matchingValues: Vector[MatchingValue] = matchingValuesSortedByValueIri.sortBy(_.propertyIri)

                        // Does the user have permission to see at least one matching value in the resource, or did the resource's label match?
                        if (matchingValues.nonEmpty || rows.exists(_.rowMap.get("valueObject").isEmpty)) {
                            // Yes. Make a search result for the resource.

                            val resourceClassIconURL = resourceClassIcon.map {
                                resClassIcon => valueUtilV1.makeResourceClassIconURL(resourceClassIri, resClassIcon)
                            }

                            subjectsAcc :+ SearchResultRowV1(
                                obj_id = resourceIri,
                                preview_path = firstRowMap.get("previewPath") match {
                                    case Some(path) => Some(valueUtilV1.makeSipiImagePreviewGetUrlFromFilename(path))
                                    case None =>
                                        // If there is no preview image, use the resource class icon from the ontology.
                                        resourceClassIconURL
                                },
                                iconsrc = resourceClassIconURL,
                                icontitle = resourceClassLabel,
                                iconlabel = resourceClassLabel,
                                valuetype_id = OntologyConstants.Rdfs.Label +: matchingValues.map(_.valueTypeIri),
                                valuelabel = "Label" +: matchingValues.map(_.propertyLabel),
                                value = resourceLabel +: matchingValues.map(_.literal),
                                preview_nx = firstRowMap.get("previewDimX") match {
                                    case Some(previewDimX) => previewDimX.toInt
                                    case None => settings.defaultIconSizeDimX
                                },
                                preview_ny = firstRowMap.get("previewDimY") match {
                                    case Some(previewDimY) => previewDimY.toInt
                                    case None => settings.defaultIconSizeDimY
                                },
                                rights = resourcePermissionCode
                            )
                        } else {
                            // The user doesn't have permission to see any of the matching values.
                            subjectsAcc
                        }
                    } else {
                        // The user doesn't have permission to see the resource.
                        subjectsAcc
                    }
            }.sortBy(_.obj_id) // Sort the matching resources by resource IRI so paging works.

            (maxPreviewDimX, maxPreviewDimY) = findMaxPreviewDimensions(subjects)

            // Get the requested page of results.
            resultsPage = subjects.slice(searchGetRequest.startAt, searchGetRequest.startAt + limit)

            results = SearchGetResponseV1(
                subjects = resultsPage,
                nhits = subjects.size.toString,
                thumb_max = SearchPreviewDimensionsV1(maxPreviewDimX, maxPreviewDimY),
                paging = makePaging(offset = searchGetRequest.startAt, limit = limit, resultCount = subjects.size)
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

                    // Ensure that the property's objectClassConstraint is valid, and that the specified operator can be
                    // used with it.
                    validTypeCompopCombos.get(propertyObjectClassConstraint) match {
                        case Some(validOps) =>
                            if (!validOps.contains(compop)) {
                                // The class specified in the property's objectClassConstraint can't be used with the specified operator.
                                throw BadRequestException(s"Operator $compop cannot be used with property $prop")
                            }

                        case None =>
                            // The class specified in the property's objectClassConstraint is invalid.
                            throw BadRequestException(s"Property $prop has an invalid knora-base:objectClassConstraint: $propertyObjectClassConstraint")
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

                                val datestring = InputValidation.toDate(searchval, () => throw BadRequestException(s"Invalid date format: $searchval"))

                                // parse date: Calendar:YYYY-MM-DD[:YYYY-MM-DD]
                                val parsedDate = datestring.split(InputValidation.calendar_separator)
                                val calendar = KnoraCalendarV1.lookup(parsedDate(0))

                                // val daysInMonth = Calendar.DAY_OF_MONTH // will be used to determine the number of days in the given month
                                // val monthsInYear = Calendar.MONTH // will be used to determine the number of months in the given year (generic for other calendars)

                                val (dateStart, dateEnd) = if (parsedDate.length > 2) {
                                    // it is a period: 0 : cal | 1 : start | 2 : end

                                    val periodStart = DateUtilV1.dateString2DateRange(parsedDate(1), calendar).start
                                    val periodEnd = DateUtilV1.dateString2DateRange(parsedDate(2), calendar).end

                                    val start = DateUtilV1.convertDateToJulianDayNumber(periodStart)
                                    val end = DateUtilV1.convertDateToJulianDayNumber(periodEnd)

                                    // check if end is bigger than start (the user could have submitted a period where start is bigger than end)
                                    if (start > end) throw BadRequestException(s"Invalid input for period: start is bigger than end: $searchval")

                                    (start, end)
                                } else {
                                    // no period: 0 : cal | 1 : start

                                    val dateRange = DateUtilV1.dateString2DateRange(parsedDate(1), calendar)

                                    val start = DateUtilV1.convertDateToJulianDayNumber(dateRange.start)
                                    val end = DateUtilV1.convertDateToJulianDayNumber(dateRange.end)

                                    (start, end)
                                }

                                searchParamWithoutValue.copy(
                                    dateStart = Some(dateStart),
                                    dateEnd = Some(dateEnd)
                                )

                            case OntologyConstants.KnoraBase.TextValue =>
                                // http://www.morelab.deusto.es/code_injection/
                                // http://stackoverflow.com/questions/29601839/prevent-sparql-injection-generic-solution-triplestore-independent
                                val searchString = InputValidation.toSparqlEncodedString(searchval, () => throw BadRequestException(s"Invalid search string: '$searchval'"))

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
                                val searchString = InputValidation.toInt(searchval, () => throw BadRequestException(s"Given searchval is not an integer: $searchval")).toString
                                searchParamWithoutValue.copy(searchValue = Some(searchString))

                            case OntologyConstants.KnoraBase.DecimalValue =>
                                // check if string is a decimal number
                                val searchString = InputValidation.toBigDecimal(searchval, () => throw BadRequestException(s"Given searchval is not a decimal number: $searchval")).toString
                                searchParamWithoutValue.copy(searchValue = Some(searchString))

                            case OntologyConstants.KnoraBase.Resource =>
                                // check if string is a valid IRI
                                val searchString = InputValidation.toIri(searchval, () => throw BadRequestException(s"Given searchval is not a valid IRI: $searchval"))
                                searchParamWithoutValue.copy(searchValue = Some(searchString))

                            case OntologyConstants.KnoraBase.ColorValue =>
                                // check if string is a hexadecimal RGB-color value
                                val searchString = InputValidation.toColor(searchval, () => throw BadRequestException(s"Invalid color format: $searchval"))
                                searchParamWithoutValue.copy(searchValue = Some(searchString))

                            case OntologyConstants.KnoraBase.GeomValue =>
                                // this only will be used with compop EXISTS
                                searchParamWithoutValue.copy(searchValue = Some(""))

                            case OntologyConstants.KnoraBase.ListValue =>
                                // check if string represents a node in a list
                                val searchString = InputValidation.toIri(searchval, () => throw BadRequestException(s"Given searchval is not a formally valid IRI $searchval"))
                                searchParamWithoutValue.copy(searchValue = Some(searchString))

                            case other => throw BadRequestException(s"The value type for the given property $prop is unknown.")
                        }
                    }
                }
            )

            // Get the search results.
            searchSparql = queries.sparql.v1.txt.searchExtended(
                triplestore = settings.triplestoreType,
                searchCriteria = searchCriteria,
                preferredLanguage = searchGetRequest.userProfile.userData.lang,
                fallbackLanguage = settings.fallbackLanguage,
                projectIriOption = searchGetRequest.filterByProject,
                restypeIriOption = searchGetRequest.filterByRestype,
                ownerIriOption = searchGetRequest.filterByOwner
            ).toString()

            // _ = println(searchSparql)

            searchResponse: SparqlSelectResponse <- (storeManager ? SparqlSelectRequest(searchSparql)).mapTo[SparqlSelectResponse]

            // Collect all the resource class IRIs mentioned in the search results.
            resourceClassIris: Set[IRI] = searchResponse.results.bindings.map(_.rowMap("resourceClass")).toSet

            // Get information about those resource classes from the ontology responder.

            entityInfoRequest = EntityInfoGetRequestV1(
                resourceClassIris = resourceClassIris,
                userProfile = searchGetRequest.userProfile
            )

            entityInfoResponse <- (responderManager ? entityInfoRequest).mapTo[EntityInfoGetResponseV1]

            // Group the search results by resource IRI.
            groupedByResourceIri: Map[IRI, Seq[VariableResultsRow]] = searchResponse.results.bindings.groupBy(_.rowMap("resource"))

            // Convert the query result rows into SearchResultRowV1 objects.

            subjects: Vector[SearchResultRowV1] = groupedByResourceIri.foldLeft(Vector.empty[SearchResultRowV1]) {
                case (subjectsAcc, (resourceIri, rows)) =>
                    val firstRowMap = rows.head.rowMap

                    // Does the user have permission to see the resource?

                    val resourceCreator = firstRowMap("resourceCreator")
                    val resourceProject = firstRowMap("resourceProject")
                    val resourcePermissions = firstRowMap("resourcePermissions")

                    val resourcePermissionCode: Option[Int] = PermissionUtilV1.getUserPermissionV1(subjectIri = resourceIri, subjectCreator = resourceCreator, subjectProject = resourceProject, subjectPermissionLiteral = resourcePermissions, userProfile = searchGetRequest.userProfile)

                    if (resourcePermissionCode.nonEmpty) {
                        // Yes. Get more information about the resource.

                        val resourceClassIri = firstRowMap("resourceClass")
                        val resourceEntityInfoMap = entityInfoResponse.resourceEntityInfoMap(resourceClassIri)
                        val resourceClassLabel = resourceEntityInfoMap.getPredicateObject(predicateIri = OntologyConstants.Rdfs.Label, preferredLangs = Some(searchGetRequest.userProfile.userData.lang, settings.fallbackLanguage))
                        val resourceClassIcon = resourceEntityInfoMap.getPredicateObject(OntologyConstants.KnoraBase.ResourceIcon)
                        val resourceLabel = firstRowMap.getOrElse("resourceLabel", throw InconsistentTriplestoreDataException(s"Resource $resourceIri has no rdfs:label"))

                        // If there were search criteria referring to values, collect the matching values in the resource.
                        val matchingValues: Vector[MatchingValue] = if (searchCriteria.nonEmpty) {
                            // Construct a Map of value IRIs to MatchingValue objects found in the resource.
                            val mapOfMatchingValues: Map[IRI, MatchingValue] = rows.foldLeft(Map.empty[IRI, MatchingValue]) {
                                case (valuesAcc: Map[IRI, MatchingValue], row: VariableResultsRow) =>
                                    // For each row, get the matching value for each search criterion.
                                    val valuesInRow: Seq[(IRI, MatchingValue)] = searchCriteria.zipWithIndex.map {
                                        case (searchCriterion, index) =>
                                            val valueIri = row.rowMap(s"valueObject$index")
                                            val literal = row.rowMap(s"literal$index")
                                            val valuePermissionLiteral = row.rowMap(s"valuePermissions$index")
                                            val valueCreator = row.rowMap(s"valueCreator$index")

                                            // Is the matching value object a LinkValue?
                                            val valuePermissionCode = if (searchCriterion.valueType == OntologyConstants.KnoraBase.Resource) {
                                                // Yes.
                                                val linkValuePermissionCode = PermissionUtilV1.getUserPermissionV1(
                                                    subjectIri = valueIri,
                                                    subjectCreator = valueCreator,
                                                    subjectProject = resourceProject,
                                                    subjectPermissionLiteral = valuePermissionLiteral,
                                                    userProfile = searchGetRequest.userProfile
                                                )

                                                // Get the permission code for the target resource.
                                                val targetResourceIri = row.rowMap(s"targetResource$index")
                                                val targetResourceCreator = row.rowMap(s"targetResourceCreator$index")
                                                val targetResourceProject = row.rowMap(s"targetResourceProject$index")
                                                val targetResourcePermissionLiteral = row.rowMap(s"targetResourcePermissions$index")

                                                val targetResourcePermissionCode = PermissionUtilV1.getUserPermissionV1(subjectIri = targetResourceIri, subjectCreator = targetResourceCreator, subjectProject = targetResourceProject, subjectPermissionLiteral = targetResourcePermissionLiteral, userProfile = searchGetRequest.userProfile)

                                                // Only allow the user to see the match if they have view permission on both the link value and the target resource.
                                                Seq(linkValuePermissionCode, targetResourcePermissionCode).min
                                            } else {
                                                // The matching object is an ordinary value, not a LinkValue.
                                                PermissionUtilV1.getUserPermissionV1(
                                                    subjectIri = valueIri,
                                                    subjectCreator = valueCreator,
                                                    subjectProject = resourceProject,
                                                    subjectPermissionLiteral = valuePermissionLiteral,
                                                    userProfile = searchGetRequest.userProfile
                                                )
                                            }

                                            val propertyIri = searchCriterion.propertyIri
                                            val propertyLabel = propertyInfo.propertyEntityInfoMap(propertyIri).getPredicateObject(predicateIri = OntologyConstants.Rdfs.Label, preferredLangs = Some(searchGetRequest.userProfile.userData.lang, settings.fallbackLanguage)) match {
                                                case Some(label) => label
                                                case None => throw InconsistentTriplestoreDataException(s"Property $propertyIri has no rdfs:label")
                                            }

                                            valueIri -> MatchingValue(
                                                valueTypeIri = searchCriterion.valueType,
                                                propertyIri = propertyIri,
                                                propertyLabel = propertyLabel,
                                                literal = literal,
                                                valuePermissionCode = valuePermissionCode
                                            )
                                    }

                                    // Filter out the values that the user doesn't have permission to see.
                                    val filteredValues: Seq[(IRI, MatchingValue)] = valuesInRow.filter {
                                        case (matchingValueIri, matchingValue) => matchingValue.valuePermissionCode.nonEmpty
                                    }

                                    valuesAcc ++ filteredValues
                            }

                            // Sort by value IRI, then by property IRI, so the results are consistent between requests.
                            val vectorOfMatchingValues: Vector[(IRI, MatchingValue)] = mapOfMatchingValues.toVector
                            val matchingValuesSortedByValueIri: Vector[MatchingValue] = vectorOfMatchingValues.sortBy(_._1).map(_._2)
                            matchingValuesSortedByValueIri.sortBy(_.propertyIri)
                        } else {
                            Vector.empty[MatchingValue]
                        }

                        // Does the user have permission to see at least one matching value in the resource, or were there no search criteria referring to values?
                        if (matchingValues.nonEmpty || searchCriteria.isEmpty) {
                            // Yes. Make a search result for the resource.

                            val resourceClassIconURL = resourceClassIcon.map {
                                resClassIcon => valueUtilV1.makeResourceClassIconURL(resourceClassIri, resClassIcon)
                            }

                            subjectsAcc :+ SearchResultRowV1(
                                obj_id = resourceIri,
                                preview_path = firstRowMap.get("previewPath") match {
                                    case Some(path) => Some(valueUtilV1.makeSipiImagePreviewGetUrlFromFilename(path))
                                    case None =>
                                        // If there is no preview image, use the resource class icon from the ontology.
                                        resourceClassIconURL
                                },
                                iconsrc = resourceClassIconURL,
                                icontitle = resourceClassLabel,
                                iconlabel = resourceClassLabel,
                                valuetype_id = OntologyConstants.Rdfs.Label +: matchingValues.map(_.valueTypeIri),
                                valuelabel = "Label" +: matchingValues.map(_.propertyLabel),
                                value = resourceLabel +: matchingValues.map(_.literal),
                                preview_nx = firstRowMap.get("previewDimX") match {
                                    case Some(previewDimX) => previewDimX.toInt
                                    case None => settings.defaultIconSizeDimX
                                },
                                preview_ny = firstRowMap.get("previewDimY") match {
                                    case Some(previewDimY) => previewDimY.toInt
                                    case None => settings.defaultIconSizeDimY
                                },
                                rights = resourcePermissionCode
                            )
                        } else {
                            // The user doesn't have permission to see any of the matching values.
                            subjectsAcc
                        }
                    } else {
                        // The user doesn't have permission to see the resource.
                        subjectsAcc
                    }

            }.sortBy(_.obj_id) // Sort the matching resources by resource IRI so paging works.

            (maxPreviewDimX, maxPreviewDimY) = findMaxPreviewDimensions(subjects)

            // Get the requested page of results.
            resultsPage = subjects.slice(searchGetRequest.startAt, searchGetRequest.startAt + limit)

            results = SearchGetResponseV1(
                subjects = resultsPage,
                nhits = subjects.size.toString,
                thumb_max = SearchPreviewDimensionsV1(maxPreviewDimX, maxPreviewDimY),
                paging = makePaging(offset = searchGetRequest.startAt, limit = limit, resultCount = subjects.size)
            )
        } yield results
    }

    /**
      * Creates a list of available search result pages.
      *
      * @param offset      the requested result offset.
      * @param limit       the maximum number of results per page.
      * @param resultCount the total number of results found.
      * @return a list of [[SearchResultPage]] objects.
      */
    private def makePaging(offset: Int, limit: Int, resultCount: Int): Seq[SearchResultPage] = {
        val pageRemainder = resultCount % limit
        val numPages = (resultCount / limit) + (if (pageRemainder > 0) 1 else 0)
        val currentPageNum = offset / limit // The offset might put us in the middle of a page, but that's OK.

        (0 until numPages).map {
            pageNum =>
                SearchResultPage(
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
