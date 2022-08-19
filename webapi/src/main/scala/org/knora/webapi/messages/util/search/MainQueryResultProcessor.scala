/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.util.search

import dsp.errors.AssertionException
import org.knora.webapi._
import org.knora.webapi.messages.IriConversions._
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.util.ConstructResponseUtilV2
import org.knora.webapi.messages.util.ConstructResponseUtilV2.RdfPropertyValues
import org.knora.webapi.messages.util.ConstructResponseUtilV2.RdfResources
import org.knora.webapi.messages.util.ConstructResponseUtilV2.ResourceWithValueRdfData
import org.knora.webapi.messages.util.ConstructResponseUtilV2.ValueRdfData
import org.knora.webapi.messages.util.search.gravsearch.mainquery.GravsearchMainQueryGenerator.ValueObjectVariablesAndValueObjectIris
import org.knora.webapi.messages.util.search.gravsearch.prequery.NonTriplestoreSpecificGravsearchToPrequeryTransformer
import org.knora.webapi.messages.util.search.gravsearch.types.GravsearchTypeInspectionResult

object MainQueryResultProcessor {

  /**
   * Given the results of the main query, filters out all values that the user did not ask for in the input query,
   * i.e that are not present in its CONSTRUCT clause.
   *
   * @param queryResultsWithFullGraphPattern        results with full graph pattern (that user has sufficient permissions on).
   * @param valueObjectVarsAndIrisPerMainResource   value object variables and their Iris per main resource.
   * @param allResourceVariablesFromTypeInspection  all variables representing resources.
   * @param dependentResourceIrisFromTypeInspection Iris of dependent resources used in the input query.
   * @param transformer                             the transformer that was used to turn the input query into the prequery.
   * @param typeInspectionResult                    results of type inspection of the input query.
   * @return results with only the values the user asked for in the input query's CONSTRUCT clause.
   */
  // TODO apparently not needed, work is taken care in ConstructResponseUtilV2.nestResources
  def getRequestedValuesFromResultsWithFullGraphPattern(
    queryResultsWithFullGraphPattern: RdfResources,
    valueObjectVarsAndIrisPerMainResource: ValueObjectVariablesAndValueObjectIris,
    allResourceVariablesFromTypeInspection: Set[QueryVariable],
    dependentResourceIrisFromTypeInspection: Set[IRI],
    transformer: NonTriplestoreSpecificGravsearchToPrequeryTransformer,
    typeInspectionResult: GravsearchTypeInspectionResult,
    inputQuery: ConstructQuery
  ): RdfResources = {
    implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    // sort out those value objects that the user did not ask for in the input query's CONSTRUCT clause
    // those are present in the input query's WHERE clause but not in its CONSTRUCT clause

    // for each resource variable (both main and dependent resources),
    // collect the value object variables associated with it in the input query's CONSTRUCT clause
    // resource variables from types inspection are used
    //
    // Example: the statement "?page incunabula:seqnum ?seqnum ." is contained in the input query's CONSTRUCT clause.
    // ?seqnum (?seqnum__Concat) is a requested value and is associated with the resource variable ?page.
    val requestedValueObjectVariablesForAllResVars: Set[QueryVariable] =
      allResourceVariablesFromTypeInspection.flatMap { resVar =>
        transformer.getValueGroupConcatVariablesForResource(resVar)
      }

    // for each resource Iri (only dependent resources),
    // collect the value object variables associated with it in the input query's CONSTRUCT clause
    // dependent resource Iris from types inspection are used
    //
    // Example: the statement "<http://rdfh.ch/5e77e98d2603> incunabula:title ?title ." is contained in the input query's CONSTRUCT clause.
    // ?title (?title__Concat) is a requested value and is associated with the dependent resource Iri <http://rdfh.ch/5e77e98d2603>.
    val requestedValueObjectVariablesForDependentResIris: Set[QueryVariable] =
      dependentResourceIrisFromTypeInspection.flatMap { depResIri =>
        transformer.getValueGroupConcatVariablesForResource(IriRef(iri = depResIri.toSmartIri))
      }

    // combine all value object variables into one set
    val allRequestedValueObjectVariables: Set[QueryVariable] =
      requestedValueObjectVariablesForAllResVars ++ requestedValueObjectVariablesForDependentResIris

    // collect requested value object Iris for each main resource
    val requestedValObjIrisPerMainResource: Map[IRI, Set[IRI]] = queryResultsWithFullGraphPattern.keySet.map {
      mainResIri =>
        // get all value object variables and Iris for the current main resource
        val valueObjIrisForRes: Map[QueryVariable, Set[IRI]] =
          valueObjectVarsAndIrisPerMainResource.valueObjectVariablesAndValueObjectIris(mainResIri)

        // get those value object Iris from the results that the user asked for in the input query's CONSTRUCT clause
        val valObjIrisRequestedForRes: Set[IRI] = allRequestedValueObjectVariables.flatMap {
          requestedQueryVar: QueryVariable =>
            valueObjIrisForRes.getOrElse(
              requestedQueryVar,
              throw AssertionException(
                s"key $requestedQueryVar is absent in prequery's value object IRIs collection for resource $mainResIri"
              )
            )
        }

        mainResIri -> valObjIrisRequestedForRes
    }.toMap

    // for each main resource, get only the requested value objects
    queryResultsWithFullGraphPattern.map { case (mainResIri: IRI, assertions: ResourceWithValueRdfData) =>
      // get the Iris of all the value objects requested for the current main resource
      val valueObjIrisRequestedForRes: Set[IRI] = requestedValObjIrisPerMainResource.getOrElse(
        mainResIri,
        throw AssertionException(
          s"key $mainResIri is absent in requested value object IRIs collection for resource $mainResIri"
        )
      )

      /**
       * Recursively filters out those values that the user does not want to see.
       * Starts with the values of the main resource and also processes link values, possibly containing dependent resources with values.
       *
       * @param values the values to be filtered.
       * @return filtered values.
       */
      def traverseAndFilterValues(values: ResourceWithValueRdfData): RdfPropertyValues =
        values.valuePropertyAssertions.foldLeft(ConstructResponseUtilV2.emptyRdfPropertyValues) {
          case (acc, (propIri: SmartIri, values: Seq[ValueRdfData])) =>
            // filter values for the current resource
            val valuesFiltered: Seq[ValueRdfData] = values.filter { valueObj: ValueRdfData =>
              // only return those value objects whose Iris are contained in valueObjIrisRequestedForRes
              valueObjIrisRequestedForRes(valueObj.subjectIri)
            }

            // if there are link values including a target resource, apply filter to their values too
            val valuesFilteredRecursively: Seq[ValueRdfData] = valuesFiltered.map { valObj: ValueRdfData =>
              if (valObj.nestedResource.nonEmpty) {

                val targetResourceAssertions: ResourceWithValueRdfData = valObj.nestedResource.get

                // apply filter to the target resource's values
                val targetResourceAssertionsFiltered: RdfPropertyValues =
                  traverseAndFilterValues(targetResourceAssertions)

                valObj.copy(
                  nestedResource = Some(
                    targetResourceAssertions.copy(
                      valuePropertyAssertions = targetResourceAssertionsFiltered
                    )
                  )
                )
              } else {
                valObj
              }
            }

            // ignore properties if there are no value objects to be displayed.
            // if the user does not want to see a value, the property pointing to that value has to be ignored.
            if (valuesFilteredRecursively.nonEmpty) {
              acc + (propIri -> valuesFilteredRecursively)
            } else {
              // ignore this property since there are no value objects
              // Example: the input query's WHERE clause contains the statement "?page incunabula:seqnum ?seqnum .",
              // but the statement is not present in its CONSTRUCT clause. Therefore, the property incunabula:seqnum can be ignored
              // since no value objects are returned for it.
              acc
            }
        }

      // filter values for the current main resource
      val requestedValuePropertyAssertions: RdfPropertyValues = traverseAndFilterValues(assertions)

      // only return the requested values for the current main resource
      mainResIri -> assertions.copy(
        valuePropertyAssertions = requestedValuePropertyAssertions
      )
    }
  }
}
