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

package org.knora.webapi.util

import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}



class SparqlUtilSpec extends WordSpecLike with Matchers with BeforeAndAfterAll {

    "For SparqlUtil " when {
        "value2SparqlLiteral is called " should {
            "convert a String to a Sparql literal " in {
                SparqlUtil.any2SparqlLiteral("test") should be ("\"test\"^^xsd:string")
            }
            "convert a Boolean to a Sparql literal " in {
                SparqlUtil.any2SparqlLiteral(true) should be ("\"true\"^^xsd:boolean")
                SparqlUtil.any2SparqlLiteral(false) should be ("\"false\"^^xsd:boolean")
            }
        }
    }


}
