/*
 * Copyright © 2015-2018 the contributors (see Contributors.md).
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

package org.knora.webapi.responders.v2.search

import org.knora.webapi.util.IriConversions._
import org.knora.webapi.util.StringFormatter
import org.knora.webapi.{CoreSpec, OntologyConstants}

/**
  * Tests [[SparqlTransformer]].
  */
class SparqlTransformerSpec extends CoreSpec() {

    protected implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    "The GravsearchUtilV2 object" should {

        "create a syntactically valid base name from a given variable" in {

            val baseName = SparqlTransformer.escapeEntityForVariable(QueryVariable("book"))

            baseName should ===("book")

        }

        "create a syntactically valid base name from a given data IRI" in {

            val baseName = SparqlTransformer.escapeEntityForVariable(IriRef("http://rdfh.ch/users/91e19f1e01".toSmartIri))

            baseName should ===("httprdfhchusers91e19f1e01")

        }

        "create a syntactically valid base name from a given ontology IRI" in {

            val baseName = SparqlTransformer.escapeEntityForVariable(IriRef("http://www.knora.org/ontology/0803/incunabula#book".toSmartIri))

            baseName should ===("httpwwwknoraorgontology0803incunabulabook")

        }

        "create a syntactically valid base name from a given string literal" in {

            val baseName = SparqlTransformer.escapeEntityForVariable(XsdLiteral("dumm", OntologyConstants.Xsd.String.toSmartIri))

            baseName should ===("dumm")

        }

        "create a unique variable name based on an entity and a property" in {
            val generatedQueryVar =
                SparqlTransformer.createUniqueVariableNameFromEntityAndProperty(
                    QueryVariable("linkingProp1"),
                    OntologyConstants.KnoraBase.HasLinkToValue
                )

            generatedQueryVar should ===(QueryVariable("linkingProp1__hasLinkToValue"))
        }


    }

}

