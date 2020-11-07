/*
 * Copyright 2018 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.google.j2cl.transpiler;

import static com.google.j2cl.common.SourceUtils.checkSourceFiles;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.j2cl.common.CommandLineTool;
import com.google.j2cl.common.OutputUtils;
import com.google.j2cl.common.OutputUtils.Output;
import com.google.j2cl.common.Problems;
import com.google.j2cl.common.SourceUtils;
import com.google.j2cl.transpiler.backend.Backend;
import com.google.j2cl.transpiler.frontend.Frontend;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

/** A javac-like command line driver for J2clTranspiler. */
public final class J2clCommandLineRunner extends CommandLineTool {

  @Argument(metaVar = "<source files>", required = true)
  List<String> files = new ArrayList<>();

  @Option(
      name = "-classpath",
      aliases = "-cp",
      metaVar = "<path>",
      usage = "Specifies where to find user class files and annotation processors.")
  String classPath = "";

  @Option(
      name = "-nativesourcepath",
      metaVar = "<path>",
      usage = "Specifies where to find zip files containing native.js files for native methods.")
  String nativeSourcePath = "";

  @Option(
      name = "-d",
      metaVar = "<path>",
      usage = "Directory or zip into which to place compiled output.")
  Path output = Paths.get(".");

  @Option(
      name = "-readablesourcemaps",
      usage = "Coerces generated source maps to human readable form.",
      hidden = true)
  boolean readableSourceMaps = false;

  @Option(
      name = "-generatekytheindexingmetadata",
      usage =
          "Generates Kythe indexing metadata and appends it onto the generated JavaScript files.",
      hidden = true)
  boolean generateKytheIndexingMetadata = false;

  @Option(
      name = "-frontend",
      metaVar = "(JDT | JAVAC)",
      usage = "Select the frontend to use: JDT (default), JAVAC (experimental).",
      hidden = true)
  Frontend frontEnd = Frontend.JDT;

  private J2clCommandLineRunner() {
    super("j2cl");
  }

  @Override
  protected void run(Problems problems) {
    try (Output out = OutputUtils.initOutput(this.output, problems)) {
      J2clTranspiler.transpile(createOptions(out.getRoot(), problems), problems);
    }
  }

  private J2clTranspilerOptions createOptions(Path outputPath, Problems problems) {
    checkSourceFiles(problems, files, ".java", ".srcjar", ".jar");

    if (this.readableSourceMaps && this.generateKytheIndexingMetadata) {
      problems.warning(
          "Readable source maps are not available when generating Kythe indexing metadata.");
      this.readableSourceMaps = false;
    }

    return J2clTranspilerOptions.newBuilder()
        .setSources(
            SourceUtils.getAllSources(this.files, problems)
                .filter(p -> p.sourcePath().endsWith(".java"))
                .collect(ImmutableList.toImmutableList()))
        .setNativeSources(
            SourceUtils.getAllSources(getPathEntries(this.nativeSourcePath), problems)
                .filter(p -> p.sourcePath().endsWith(".native.js"))
                .collect(ImmutableList.toImmutableList()))
        .setClasspaths(getPathEntries(this.classPath))
        .setOutput(outputPath)
        .setEmitReadableSourceMap(this.readableSourceMaps)
        .setEmitReadableLibraryInfo(false)
        .setGenerateKytheIndexingMetadata(this.generateKytheIndexingMetadata)
        .setFrontend(this.frontEnd)
        .setBackend(Backend.CLOSURE)
        .build();
  }

  private static List<String> getPathEntries(String path) {
    List<String> entries = new ArrayList<>();
    for (String entry : Splitter.on(File.pathSeparatorChar).omitEmptyStrings().split(path)) {
      if (new File(entry).exists()) {
        entries.add(entry);
      }
    }
    return entries;
  }

  // Exists for testing, should be removed when tests stop using flags.
  static Problems runForTest(String[] args) {
    return new J2clCommandLineRunner().processRequest(args);
  }

  public static int run(String[] args) {
    return new J2clCommandLineRunner().execute(args);
  }

  public static void main(String[] args) {
    System.exit(run(args));
  }
}
