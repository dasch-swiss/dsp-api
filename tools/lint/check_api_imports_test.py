#!/usr/bin/env python3
import contextlib
import io
import os
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import check_api_imports  # noqa: E402

MAIN = "modules/webapi/src/main/scala/org/knora/webapi/"


class CheckApiImportsTest(unittest.TestCase):
    def setUp(self):
        self._tmp = tempfile.TemporaryDirectory()
        self.root = self._tmp.name
        self.allowlist = self._write("allowlist.txt", "")

    def tearDown(self):
        self._tmp.cleanup()

    def _write(self, name, content):
        path = os.path.join(self.root, name)
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "w", encoding="utf-8") as f:
            f.write(content)
        return path

    def _source(self, rel, body):
        return self._write(MAIN + rel, body)

    def _run(self, *paths):
        with contextlib.redirect_stdout(io.StringIO()) as out:
            code = check_api_imports.main(["check_api_imports.py", "--allowlist", self.allowlist, *paths])
        return code, out.getvalue()

    def test_clean_domain_file_passes(self):
        path = self._source("slice/admin/domain/A.scala", "package org.knora.webapi.slice.admin\nimport zio.*\n")
        self.assertEqual(self._run(path)[0], 0)

    def test_import_from_domain_fails(self):
        path = self._source("slice/admin/domain/A.scala", "import org.knora.webapi.slice.api.admin.model.Dto\n")
        code, out = self._run(path)
        self.assertEqual(code, 1)
        self.assertIn("A.scala:1", out)

    def test_fully_qualified_reference_fails(self):
        path = self._source("responders/R.scala", "val x: org.knora.webapi.slice.api.v2.Foo = ???\n")
        self.assertEqual(self._run(path)[0], 1)

    def test_comments_and_package_clause_are_ignored(self):
        body = (
            "package org.knora.webapi.slice.api.v3.export_\n"
            "// see org.knora.webapi.slice.api.v3\n"
            "val a = 1 // org.knora.webapi.slice.api\n"
        )
        path = self._source("slice/export/E.scala", body)
        self.assertEqual(self._run(path)[0], 0)

    def test_files_inside_slice_api_are_not_checked(self):
        path = self._source("slice/api/admin/X.scala", "import org.knora.webapi.slice.api.admin.model.Dto\n")
        other = self._source("slice/admin/domain/A.scala", "import zio.*\n")
        self.assertEqual(self._run(path, other)[0], 0)

    def test_allowlisted_file_passes(self):
        path = self._source("slice/admin/domain/A.scala", "import org.knora.webapi.slice.api.admin.model.Dto\n")
        self._write("allowlist.txt", f"{path}  # baseline\n")
        self.assertEqual(self._run(path)[0], 0)

    def test_stale_allowlist_entry_fails(self):
        path = self._source("slice/admin/domain/A.scala", "import zio.*\n")
        self._write("allowlist.txt", f"{path}  # baseline\n")
        code, out = self._run(path)
        self.assertEqual(code, 1)
        self.assertIn("stale", out)

    def test_entry_without_reason_fails(self):
        path = self._source("slice/admin/domain/A.scala", "import org.knora.webapi.slice.api.admin.model.Dto\n")
        self._write("allowlist.txt", f"{path}\n")
        code, out = self._run(path)
        self.assertEqual(code, 1)
        self.assertIn("malformed", out)

    def test_no_webapi_main_files_fails(self):
        path = self._write("modules/ingest/src/main/scala/I.scala", "import zio.*\n")
        self.assertEqual(self._run(path)[0], 1)


if __name__ == "__main__":
    unittest.main()
