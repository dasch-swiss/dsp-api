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

/*
 * Transaction management for SPARQL updates over HTTP is currently commented out because of
 * issue <https://github.com/dhlab-basel/Knora/issues/85>.
 */

/*

package org.knora.webapi.store.triplestore.http

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

import org.knora.webapi.TransactionManagementException
import org.knora.webapi.util.JavaFunctionUtil

/**
  * Provides transaction management for [[HttpTriplestoreActor]].
  *
  * The SPARQL Update specification provides no transaction management. GraphDB runs each HTTP request
  * in a separate transaction. Therefore, the only way to perform multiple update operations in a single
  * triplestore transaction is to concatenate them and send them as a single HTTP request. This is useful
  * because an API request may need to perform multiple SPARQL updates, and database consistency constraints
  * might not be met until all the update operations have completed. Sending all the updates as a single
  * request ensures that consistency checks will be performed on the combined results of all the updates.
  *
  * Each transaction is given a [[UUID]]. When [[HttpTriplestoreActor]] receives an update request, it uses
  * [[HttpTriplestoreTransactionManager]] to store the operation in memory. When
  * [[HttpTriplestoreActor]] receives a request to commit the transaction, it retrieves the concatenated
  * updates for the transaction from [[HttpTriplestoreTransactionManager]], and sends them to the triplestore
  * in a single HTTP request.
  *
  * To ensure that not-yet-committed transactions are rolled back (i.e. discarded) if an error occurs,
  * responders should use [[org.knora.webapi.responders.v1.TransactionUtil]].
  */
object HttpTriplestoreTransactionManager {

    // A Map of transaction IDs to sequences of update operations.
    private val transactionMap = new ConcurrentHashMap[UUID, Vector[String]]

    /**
      * Stores a SPARQL update operation for a transaction.
      * @param transactionID the transaction ID.
      * @param sparqlUpdate the SPARQL update to store.
      */
    def addUpdateToTransaction(transactionID: UUID, sparqlUpdate: String): Unit = {
        transactionMap.merge(
            transactionID,
            Vector(sparqlUpdate),
            JavaFunctionUtil.biFunction({ (currentUpdates, _) => currentUpdates :+ sparqlUpdate })
        )
    }

    /**
      * Retrieves the updates stored for a transaction, concatenates them into a single SPARQL update
      * request, and forgets the stored updates.
      * @param transactionID the transaction ID.
      * @return the concatenated updates.
      */
    def concatenateAndForgetUpdates(transactionID: UUID): String = {
        val concatenatedUpdates = Option(transactionMap.get(transactionID)) match {
            case Some(updates) => updates.mkString("\n;\n")
            case None => throw TransactionManagementException(s"Transaction $transactionID not found")
        }

        transactionMap.remove(transactionID)
        concatenatedUpdates
    }

    /**
      * Forgets the updates stored for a transaction.
      * @param transactionID the transaction ID.
      */
    def forgetUpdates(transactionID: UUID): Unit = {
        transactionMap.remove(transactionID)
    }
}

*/