""" generate a srcjar from provided *.scala source files """
def _generate_srcjar_impl(ctx):
    # declare the output file (the source jar that we want to generate)
    out = ctx.actions.declare_file(ctx.label.name + ".srcjar")

    # declare the action that will generate the output file (the source jar)
    # TODO: calculate ctx.attr.prefix
    ctx.actions.run(
        inputs = ctx.files.srcs,
        outputs = [out],
        arguments = ["cf"] + [out.path] + ["-C"] + [ctx.attr.prefix] + ["."],
        progress_message = "Bundling srcs into srcjar",
        executable = "jar",
    )

    return [DefaultInfo(files = depset([out]))]

generate_srcjar = rule(
    implementation = _generate_srcjar_impl,
    attrs = {
        "username": attr.string(),
        "prefix": attr.string(),
        "srcs": attr.label_list(
            allow_files = True,
            mandatory = True
        ),
    },
)
