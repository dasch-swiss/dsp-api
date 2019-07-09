/*
 * Copyright © 2015-2019 the contributors (see Contributors.md).
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

package org.knora.webapi.util.clientapi

/**
  * Represents a client API as input to the generator back end.
  *
  * @param apiDef          the API definition.
  * @param clientClassDefs the class definitions used in the API.
  */
case class ClientApiBackendInput(apiDef: ClientApi, clientClassDefs: Set[ClientClassDefinition])

/**
  * Represents a file containing generated client API source code.
  *
  * @param filePath the filename in which the source code should be saved.
  * @param text     the source code.
  */
case class ClientSourceCodeFileContent(filePath: String, text: String)

/**
  * A trait for client API code generator back ends. A back end is responsible for producing client API library
  * source code in a particular programming language.
  */
trait GeneratorBackEnd {
    /**
      * Generates client API source code.
      *
      * @param apis the APIs from which source code is to be generated.
      * @return the generated source code.
      */
    def generateClientSourceCode(apis: Set[ClientApiBackendInput]): Set[ClientSourceCodeFileContent]
}
