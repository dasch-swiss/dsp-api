"""Generate a file using a template.
It is much more memory-efficient to use a template file than creating the whole
content during the analysis phase.
"""

# Label of the template file to use.
_TEMPLATE = "//tools/buildstamp:BuildInfoTemplate.scala"

def _gen_build_info_impl(ctx):
    ctx.actions.expand_template(
        template = ctx.file._template,
        output = ctx.outputs.source_file,
        substitutions = {
            "{AKKA_VERSION}": ctx.attr.akka_version,
            "{AKKA_HTTP_VERSION}": ctx.attr.akka_http_version,
            "{SIPI_VERSION}": ctx.attr.sipi_version,

        },
    )

gen_build_info = rule(
    implementation = _gen_build_info_impl,
    attrs = {
        "akka_version": attr.string(mandatory = True),
        "akka_http_version": attr.string(mandatory = True),
        "sipi_version": attr.string(mandatory = True),
        "_template": attr.label(
            default = Label(_TEMPLATE),
            allow_single_file = True,
        ),
    },
    outputs = {"source_file": "%{name}.scala"},
)
