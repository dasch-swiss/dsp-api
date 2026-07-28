"""scalafmt-enabled Scala rule wrappers.

Wraps the standard rules_scala rules with the scalafmt phase (`ext_scalafmt`) so
each target with `format = True` gets implicit `<target>.format` (rewrite in
place) and `<target>.format-test` (check) outputs. Same-named re-exports so
module BUILD files keep identical rule names — swapping the load line does not
change how Metals/IntelliJ resolve the rules.

Replaces sbt-scalafmt (`sbt fmt` / `sbt check`). Config: //:.scalafmt.conf,
wired as the toolchain default via `scala_deps.scalafmt` in MODULE.bazel.
"""

load(
    "@rules_scala//scala:advanced_usage/scala.bzl",
    "make_scala_binary",
    "make_scala_junit_test",
    "make_scala_library",
    "make_scala_macro_library",
)
load("@rules_scala//scala/scalafmt:phase_scalafmt_ext.bzl", "ext_scalafmt")

scala_library = make_scala_library(ext_scalafmt)
scala_macro_library = make_scala_macro_library(ext_scalafmt)
scala_junit_test = make_scala_junit_test(ext_scalafmt)
scala_binary = make_scala_binary(ext_scalafmt)
