<!---
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
-->

# Stores Endpoint

This endpoint allows manipulation of the triplestore content.

` POST admin/store/ResetTriplestoreContent` resets the triplestore content, given that the `allowReloadOverHttp`
configuration flag is set to `true`. This route is mostly used in tests.
