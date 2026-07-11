"""OCI helpers for the dsp-api image build (Phase 3: knora-api, dsp-ingest; Phase 5: fuseki).

`runtime_jars` collects a scala_library/scala_binary's transitive runtime classpath, for
packing into a lib-dir layer (see `oci_stamped_labels`'s sibling doc below for why we don't
use rules_scala's implicit `_deploy.jar` fat jar).

`oci_stamped_labels` emits an OCI labels file (`name=value` per line, the format
`oci_image(labels=<file>)` expects) mixing static and workspace-status (stamped) values —
`oci_image`'s dict form can't stamp, so this mirrors //tools/buildinfo's `ctx.info_file`
substitution for the label case.

`image_rootfs_extract` flattens an image tarball with `crane export` (via the
rules_oci-registered hermetic crane toolchain) and repacks a chosen subset of paths into a
layer tar, forcing root ownership. Uses the bsdtar toolchain rules_oci/tar.bzl already
registers, so no host `tar` dependency (consistent behaviour on macOS dev machines and
Linux RBE).
"""

load("@bazel_skylib//lib:shell.bzl", "shell")
load("@rules_java//java/common:java_info.bzl", "JavaInfo")

# Shared by every image's oci_image(env=...): the OTel javaagent auto-instruments
# HTTP client + Netty by default, which double-instruments dsp-api services that
# already trace these manually (sttp4/zio-http middleware).
OTEL_DISABLE_ENV = {
    "OTEL_INSTRUMENTATION_JAVA_HTTP_CLIENT_ENABLED": "false",
    "OTEL_INSTRUMENTATION_NETTY_ENABLED": "false",
}

def _runtime_jars_impl(ctx):
    jars = depset(transitive = [d[JavaInfo].transitive_runtime_jars for d in ctx.attr.deps])
    return [DefaultInfo(files = jars)]

runtime_jars = rule(
    implementation = _runtime_jars_impl,
    attrs = {
        "deps": attr.label_list(providers = [[JavaInfo]], mandatory = True),
    },
)

def _oci_stamped_labels_impl(ctx):
    out = ctx.actions.declare_file(ctx.attr.name + ".labels.txt")
    tmpl = ctx.actions.declare_file(ctx.attr.name + ".labels.in")
    info = ctx.info_file  # bazel-out/stable-status.txt

    lines = ["%s=%s" % (k, v) for k, v in ctx.attr.labels.items()]
    lines += ["%s=__%s__" % (k, v) for k, v in ctx.attr.stamp_labels.items()]
    ctx.actions.write(tmpl, "\n".join(lines) + "\n")

    # Unique stamp keys → substitute their status values. Bash parameter
    # substitution (not sed) so a value containing regex-replacement
    # metacharacters (&, \) can't corrupt the output; grep failing on a
    # missing/mistyped status key aborts the build (set -e) instead of
    # silently substituting an empty string.
    keys = {v: True for v in ctx.attr.stamp_labels.values()}
    script = "set -euo pipefail\n"
    script += 'content="$(cat %s)"\n' % tmpl.path
    for key in keys:
        script += "V=\"$(grep '^%s ' %s | cut -d' ' -f2-)\"\n" % (key, info.path)
        script += 'content="${content//__%s__/$V}"\n' % key
    script += 'printf "%%s" "$content" > %s\n' % out.path

    ctx.actions.run_shell(
        inputs = [tmpl, info],
        outputs = [out],
        command = script,
        mnemonic = "OciLabels",
        progress_message = "Generating %s" % out.short_path,
    )
    return [DefaultInfo(files = depset([out]))]

oci_stamped_labels = rule(
    implementation = _oci_stamped_labels_impl,
    attrs = {
        "labels": attr.string_dict(doc = "static label name -> value"),
        "stamp_labels": attr.string_dict(doc = "label name -> workspace-status key"),
    },
)

def _image_rootfs_extract_impl(ctx):
    crane = ctx.toolchains["@rules_oci//oci:crane_toolchain_type"].crane_info.binary
    bsdtar_info = ctx.toolchains["@tar.bzl//tar/toolchain:type"].tarinfo
    bsdtar = bsdtar_info.binary

    out = ctx.actions.declare_file(ctx.attr.name + ".tar")
    img = ctx.file.image_tar
    paths = " ".join([shell.quote(p) for p in ctx.attr.paths])
    tops = " ".join([shell.quote(p) for p in ctx.attr.top_level])

    # oci_load's default `format = "docker"` tarball is a single-image (docker-save format)
    # archive; `crane export -` auto-detects that format from stdin. If a future rules_oci
    # only supports `format = "oci"` here, retry with that and pipe the OCI-layout tar instead
    # -- crane export accepts both from stdin.
    script = """
set -euo pipefail
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
"{crane}" export - "$work/full.tar" < "{img}"
mkdir -p "$work/root"
"{bsdtar}" -x -f "$work/full.tar" -C "$work/root" {paths}
"{bsdtar}" -c --uid 0 --gid 0 -f "{out}" -C "$work/root" {tops}
""".format(crane = crane.path, bsdtar = bsdtar.path, img = img.path, out = out.path, paths = paths, tops = tops)

    ctx.actions.run_shell(
        inputs = [img],
        outputs = [out],
        tools = [crane, bsdtar],
        command = script,
        env = bsdtar_info.default_env,
        mnemonic = "CraneRootfsExtract",
        progress_message = "Extracting %s" % out.short_path,
    )
    return [DefaultInfo(files = depset([out]))]

image_rootfs_extract = rule(
    implementation = _image_rootfs_extract_impl,
    attrs = {
        "image_tar": attr.label(allow_single_file = [".tar"], mandatory = True),
        "paths": attr.string_list(mandatory = True, doc = "tar members to extract (no leading /)"),
        "top_level": attr.string_list(mandatory = True, doc = "top-level dirs/files to repack into the output layer"),
    },
    toolchains = [
        "@rules_oci//oci:crane_toolchain_type",
        "@tar.bzl//tar/toolchain:type",
    ],
)
