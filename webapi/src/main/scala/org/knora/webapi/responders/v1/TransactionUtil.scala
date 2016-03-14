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
package org.knora.webapi.responders.v1

import java.util.UUID

import akka.actor.ActorSelection
import akka.pattern._
import akka.util.Timeout
import org.knora.webapi.messages.v1respondermessages.triplestoremessages._

import scala.concurrent.{ExecutionContext, Future}

/**
  * Helps responders use the store module's transaction management, ensuring that a transaction is
  * rolled back if an error occurs.
  */
object TransactionUtil {
    /**
      * Sends [[BeginUpdateTransaction]] to the store manager, calls a function that performs one or more
      * SPARQL updates, then sends [[CommitUpdateTransaction]] to the store manager. If an error occurs,
      * sends [[RollbackUpdateTransaction]] to the store manager.
      *
      * @param task a function that takes the transaction ID as an argument, and sends one or more SPARQL updates
      *             to the store manager using that transaction ID.
      * @param storeManager the store manager.
      * @return the return value of `task`, or a failed future if an error occurred.
      */
    def runInUpdateTransaction[T](task: UUID => Future[T], storeManager: ActorSelection)(implicit timeout: Timeout, executionContext: ExecutionContext): Future[T] = {
        for {
        // Begin a transaction, getting the transaction ID from the store module.
            updateTransactionBegun <- (storeManager ? BeginUpdateTransaction()).mapTo[UpdateTransactionBegun]
            transactionID = updateTransactionBegun.transactionID

            // Run the task, then commit the transaction.
            taskResultFuture = for {
                result <- task(transactionID)
                _ <- (storeManager ? CommitUpdateTransaction(transactionID)).mapTo[UpdateTransactionCommitted]
            } yield result

            // If the task or the commit failed, roll back the transaction and return a failed future containing
            // the exception that occurred.
            recoveredTaskResult <- taskResultFuture.recoverWith {
                case err: Exception =>
                    (storeManager ? RollbackUpdateTransaction(transactionID)).mapTo[UpdateTransactionRolledBack].flatMap {
                        _ => Future.failed(err)
                    }
            }
        } yield recoveredTaskResult
    }
}
*/
