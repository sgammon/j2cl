"""Helpers to create a JDK system module."""

load("@bazel_skylib//rules:common_settings.bzl", "BuildSettingInfo")

def _jdk_system(ctx):
    bootclasspath = ctx.file.bootclasspath
    if not ctx.attr._modules_enabled[BuildSettingInfo].value:
        return [DefaultInfo(files = depset([bootclasspath]))]

    system = ctx.actions.declare_directory("%s" % ctx.label.name)
    java_runtime = ctx.attr._runtime[java_common.JavaRuntimeInfo]
    zip_tool = ctx.executable._zip

    ctx.actions.run_shell(
        inputs = depset([bootclasspath], transitive = [java_runtime.files]),
        outputs = [system],
        tools = [zip_tool],
        command = "\n".join([
            "set -eux",
            "JAVABASE=%s" % java_runtime.java_home,
            "MODULE_DIR=%s" % system.path,
            "WD=$(pwd)",
            "TMP=$(mktemp -d)",
            'VERSION=$("${JAVABASE}/bin/jlink" --version)',
            'rm -rf "${MODULE_DIR}"',
            'mkdir "${TMP}/classes" "${TMP}/jmod"',
            '(cd ${TMP}/classes && "${WD}/%s" x "${WD}/%s")' % (zip_tool.path, bootclasspath.path),
            '"${JAVABASE}/bin/jmod" create \\',
            "  --module-version ${VERSION} \\",
            "  --target-platform linux-amd64 \\",
            '  --class-path "${TMP}/classes" \\',
            '  "${TMP}/jmod/module.jmod"',
            '"${JAVABASE}/bin/jlink" \\',
            '    --module-path "${TMP}/jmod" \\',
            "    --add-modules java.base \\",
            "    --disable-plugin system-modules \\",
            '    --output "${MODULE_DIR}"',
            'cp "${JAVABASE}/lib/jrt-fs.jar" "${MODULE_DIR}/lib/"',
            'rm -rf "${TMP}" || true',
        ]),
    )

    return [
        java_common.BootClassPathInfo(
            system = system,
            bootclasspath = [bootclasspath],
        ),
        DefaultInfo(files = depset([system, bootclasspath])),
    ]

jdk_system = rule(
    implementation = _jdk_system,
    attrs = {
        "bootclasspath": attr.label(
            cfg = "target",
            allow_single_file = True,
        ),
        "_runtime": attr.label(
            default = Label("@bazel_tools//tools/jdk:current_host_java_runtime"),
            cfg = "host",
            providers = [java_common.JavaRuntimeInfo],
        ),
        "_zip": attr.label(
            executable = True,
            cfg = "host",
            default = Label("@bazel_tools//tools/zip:zipper"),
        ),
        "_modules_enabled": attr.label(
            default = Label("//:enable_experimental_java11_support"),
        ),
    },
)
