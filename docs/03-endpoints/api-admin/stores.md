# Stores Endpoint

This endpoint allows manipulation of the triplestore content.

`POST admin/store/ResetTriplestoreContent` resets the triplestore content, given that the `allowReloadOverHttp`
configuration flag is set to `true`. This route is mostly used in tests.
