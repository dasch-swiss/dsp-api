/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.store.sipimessages

import org.knora.webapi.core.RelayedMessage
import org.knora.webapi.messages.store.StoreRequest
import org.knora.webapi.slice.admin.domain.model.User

/**
 * An abstract trait for messages that can be sent to the [[org.knora.webapi.store.iiif.api.SipiService]]
 */
sealed trait IIIFRequest extends StoreRequest with RelayedMessage

// NOTE: consider renaming all with Ingest prefixes
sealed trait SipiRequest extends IIIFRequest

/**
 * Asks Sipi for a text file. Currently only for UTF8 encoded text files.
 *
 * @param fileUrl        the URL pointing to the file.
 * @param requestingUser the user making the request.
 */
case class SipiGetTextFileRequest(fileUrl: String, requestingUser: User) extends SipiRequest

/**
 * Represents a response for [[SipiGetTextFileRequest]].
 *
 * @param content the file content.
 */
case class SipiGetTextFileResponse(content: String)
