""" generate a srcjar from provided *.scala source files """

def _generate_srcjar_impl(ctx):
    print("inside rule: ", ctx.label)
    print("srcs: ", ctx.files.srcs)
    out = ctx.actions.declare_file(ctx.label.name + ".srcjar")
    ctx.actions.write(
        output = out,
        content = "Hello {}!\n".format(ctx.attr.username),
    )
    return [DefaultInfo(files = depset([out]))]

generate_srcjar = rule(
    implementation = _generate_srcjar_impl,
    attrs = {
        "username": attr.string(),
        "srcs": attr.label_list(),
    },
)

print("bzl file evaluation")
