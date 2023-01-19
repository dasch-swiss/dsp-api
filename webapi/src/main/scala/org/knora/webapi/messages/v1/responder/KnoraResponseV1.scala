/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.v1.responder

import org.knora.webapi.messages.traits.Jsonable

/**
 * A trait for Knora API v1 response messages. Any response message can be converted into JSON.
 */
trait KnoraResponseV1 extends Jsonable
