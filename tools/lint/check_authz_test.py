#!/usr/bin/env python3
import contextlib
import io
import os
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import check_authz  # noqa: E402


class CheckAuthzTest(unittest.TestCase):
    def setUp(self):
        self._tmp = tempfile.TemporaryDirectory()
        self.root = self._tmp.name
        self.unused_allowlist = self._write("unused_user_allowlist.txt", "")
        self.system_allowlist = self._write("system_user_allowlist.txt", "")

    def tearDown(self):
        self._tmp.cleanup()

    def _write(self, name, content):
        path = os.path.join(self.root, name)
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "w", encoding="utf-8") as f:
            f.write(content)
        return path

    def _source(self, body, name="A.scala"):
        return self._write(f"m/src/main/scala/{name}", body)

    def _run(self, *paths):
        return check_authz.main(
            [
                "check_authz.py",
                "--unused-user-allowlist",
                self.unused_allowlist,
                "--system-user-allowlist",
                self.system_allowlist,
                *paths,
            ]
        )

    def _errors(self, *paths):
        unused, unused_errors = check_authz.read_allowlist(self.unused_allowlist, 2, check_authz.ISSUE_RE)
        system, system_errors = check_authz.read_allowlist(self.system_allowlist, 1)
        errors, _ = check_authz.check([self._source("val ok = 1\n", "Ok.scala"), *paths], unused, system)
        return unused_errors + system_errors + errors

    def assertOnlyError(self, errors, fragment):
        self.assertEqual(len(errors), 1, errors)
        self.assertIn(fragment, errors[0])

    def test_unused_user_param_fails(self):
        src = self._source("  def count(@unused user: User)(q: String) = ???\n")
        self.assertOnlyError(self._errors(src), "@unused on a User parameter of `count`")

    def test_qualified_unused_requesting_user_fails(self):
        src = self._source("  def run(\n    @scala.annotation.unused requestingUser: User,\n  ) = ???\n")
        self.assertOnlyError(self._errors(src), "of `run`")

    def test_using_and_fully_qualified_user_fail(self):
        src = self._source("  def run(@unused using u: org.example.User) = ???\n")
        self.assertOnlyError(self._errors(src), "of `run`")

    def test_constructor_param_is_attributed_to_its_class(self):
        src = self._source("  def count = 1\nfinal class A(@unused user: User)\n")
        self.unused_allowlist = self._write("unused_user_allowlist.txt", f"{src} A  # DEV-0000\n")
        self.assertEqual(self._errors(src), [])

    def test_unused_unit_and_user_like_types_pass(self):
        src = self._source("  def run(user: User)(@unused x: Unit, @unused i: UserIri) = ???\n")
        self.assertEqual(self._errors(src), [])

    def test_allow_listed_unused_user_passes(self):
        src = self._source("  def count(@unused user: User) = ???\n")
        self.unused_allowlist = self._write("unused_user_allowlist.txt", f"# header\n\n{src} count  # DEV-0000\n")
        self.assertEqual(self._run(src), 0)

    def test_unused_user_allow_listed_for_another_method_fails(self):
        src = self._source("  def count(@unused user: User) = ???\n  def other(@unused user: User) = ???\n")
        self.unused_allowlist = self._write("unused_user_allowlist.txt", f"{src} other  # DEV-0000\n")
        self.assertOnlyError(self._errors(src), "of `count`")

    def test_stale_unused_user_entry_fails(self):
        src = self._source("  def count(user: User) = ???\n")
        self.unused_allowlist = self._write("unused_user_allowlist.txt", f"{src} count  # DEV-0000\n")
        self.assertOnlyError(self._errors(src), "stale unused-User allow-list entry")

    def test_system_user_not_allow_listed_fails(self):
        src = self._source("val u = KnoraSystemInstances.Users.SystemUser\n")
        self.assertOnlyError(self._errors(src), "SystemUser outside the allow-list")
        self.assertEqual(self._run(src), 1)

    def test_is_system_user_passes(self):
        self.assertEqual(self._run(self._source("if (user.isSystemUser) ()\n")), 0)

    def test_system_user_in_comments_passes(self):
        src = self._source(
            "// as SystemUser\n/** SystemUser\n * SystemUser\n */\nval u = user // not SystemUser\n"
        )
        self.assertEqual(self._errors(src), [])

    def test_system_user_after_a_url_in_a_string_fails(self):
        src = self._source('val u = ("http://example.org", SystemUser)\n')
        self.assertOnlyError(self._errors(src), "SystemUser outside the allow-list")

    def test_allow_listed_system_user_passes(self):
        src = self._source("val u = SystemUser\n")
        self.system_allowlist = self._write("system_user_allowlist.txt", f"{src}  # reason\n")
        self.assertEqual(self._run(src), 0)

    def test_stale_system_user_entry_fails(self):
        src = self._source("val u = 1\n")
        self.system_allowlist = self._write("system_user_allowlist.txt", f"{src}  # reason\n")
        self.assertOnlyError(self._errors(src), "stale SystemUser allow-list entry")
        self.assertEqual(self._run(src), 1)

    def test_entry_without_reason_fails(self):
        src = self._source("val u = SystemUser\n")
        self.system_allowlist = self._write("system_user_allowlist.txt", f"{src}\n")
        self.assertIn("malformed entry", self._errors(src)[0])

    def test_entry_with_wrong_field_count_fails(self):
        src = self._source("  def count(@unused user: User) = ???\n")
        self.unused_allowlist = self._write("unused_user_allowlist.txt", f"{src}  # DEV-0000\n")
        self.assertIn("malformed entry", self._errors(src)[0])

    def test_entry_with_empty_reason_fails(self):
        src = self._source("val u = SystemUser\n")
        self.system_allowlist = self._write("system_user_allowlist.txt", f"{src}  #\n")
        self.assertIn("malformed entry", self._errors(src)[0])

    def test_unused_user_entry_without_issue_id_fails(self):
        src = self._source("  def count(@unused user: User) = ???\n")
        self.unused_allowlist = self._write("unused_user_allowlist.txt", f"{src} count  # todo\n")
        self.assertIn("malformed entry", self._errors(src)[0])

    def test_comment_lines_do_not_change_the_owner(self):
        src = self._source("  def count(\n    // the object of this\n    @unused user: User) = ???\n")
        self.assertOnlyError(self._errors(src), "of `count`")

    def test_hint_is_printed_once_per_rule(self):
        src = self._source("  def a(@unused u: User) = 1\n  def b(@unused u: User) = 1\n")
        out = io.StringIO()
        with contextlib.redirect_stdout(out):
            self.assertEqual(self._run(src), 1)
        self.assertEqual(out.getvalue().count(check_authz.UNUSED_USER_HINT), 1)
        self.assertNotIn(check_authz.SYSTEM_USER_HINT, out.getvalue())

    def test_test_sources_are_ignored(self):
        main_src = self._source("val ok = 1\n")
        test_src = self._write("m/src/test/scala/A.scala", "val u = SystemUser\ndef f(@unused u: User) = 1\n")
        self.assertEqual(self._run(main_src, test_src), 0)

    def test_no_main_sources_fails(self):
        self.assertEqual(self._run(self._write("m/src/test/scala/A.scala", "val ok = 1\n")), 1)


if __name__ == "__main__":
    unittest.main()
