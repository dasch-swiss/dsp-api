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

package org.knora.webapi.e2e.v2

import java.util

import com.github.jsonldjava.core.{JsonLdOptions, JsonLdProcessor}
import com.github.jsonldjava.utils.JsonUtils
import org.knora.webapi.{IRI, InvalidApiJsonException}

import scala.collection.JavaConverters._

object ResponseCheckerR2RV2 {

    private val numberOfItemsMember = "http://schema.org/numberOfItems"

    private val itemListElementMember = "http://schema.org/itemListElement"

    /**
      * Checks for the number of expected results to be returned.
      *
      * @param responseJson   the response send back by the search route.
      * @param expectedNumber the expected number of results for the query.
      * @return an assertion that the actual amount of results corresponds with the expected number of results.
      */
    def checkNumberOfItems(responseJson: String, expectedNumber: Int) = {

        val res = JsonUtils.fromString(responseJson)

        val compacted: Map[IRI, Any] = JsonLdProcessor.compact(res, new util.HashMap[String, String](), new JsonLdOptions()).asScala.toMap

        val numberOfItems: Any = compacted.getOrElse(numberOfItemsMember, throw InvalidApiJsonException(s"member '$numberOfItemsMember' not given for search response."))

        assert(numberOfItems.isInstanceOf[Int] && numberOfItems == expectedNumber, s"expected $expectedNumber resources, but $numberOfItems given")

    }

}