/*
 * Copyright 2015 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.google.j2cl.junit.integration;

import static org.junit.Assert.assertTrue;

import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import com.google.common.io.CharStreams;
import com.google.common.io.Files;
import com.google.common.util.concurrent.SettableFuture;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/** Helps to run jsunit_tests on matrix to perform integration testing for j2cl's junit support. */
public abstract class IntegrationTestBase {

  public enum TestMode {
    JAVA(""),
    J2CL_UNCOMPILED("-j2cl"),
    J2CL_COMPILED("-j2cl_compiled");

    public final String postfix;

    private TestMode(String postfix) {
      this.postfix = postfix;
    }

    public boolean isJ2cl() {
      return this == J2CL_UNCOMPILED || this == J2CL_COMPILED;
    }
  }

  @Parameters(name = "{0}")
  public static Iterable<Object[]> data() {
    return Arrays.asList(
        new TestMode[] {TestMode.JAVA},
        new TestMode[] {TestMode.J2CL_UNCOMPILED},
        new TestMode[] {TestMode.J2CL_COMPILED});
  }

  @Parameter public TestMode testMode;

  protected TestResult.Builder newTestResultBuilder() {
    return TestResult.builder().packageName(getTestDataPackage());
  }

  protected TestAsserter assertThat(List<String> consoleLogs) {
    return new TestAsserter(testMode, consoleLogs);
  }

  protected void runStacktraceTest(String testName) throws Exception {
    TestResult testResult =
        newTestResultBuilder().testClassName(testName).addTestFailure("test").build();

    List<String> logLines = runTest(testName);
    assertThat(logLines).matches(testResult);

    Stacktrace stacktrace = loadStackTrace(testName);
    assertThat(logLines).matches(stacktrace);
  }

  private Stacktrace loadStackTrace(String testName) throws IOException {
    return Stacktrace.parse(
        Files.asCharSource(getStackTraceFile(testName), StandardCharsets.UTF_8).read());
  }

  private File getStackTraceFile(String testName) throws IOException {
    switch (testMode) {
      case J2CL_COMPILED:
        File compiledFile = getTestDataFile(testName + ".stacktrace_j2cl_compiled.txt");
        if (compiledFile.exists()) {
          return compiledFile;
        }
        // fall through
      case J2CL_UNCOMPILED:
        File uncompiledFile = getTestDataFile(testName + ".stacktrace_j2cl.txt");
        if (uncompiledFile.exists()) {
          return uncompiledFile;
        }
        // fall through
      default:
        return getTestDataFile(testName + ".stacktrace.txt");
    }
  }

  protected List<String> runTest(String testName) throws Exception {
    File executable = getTestDataFile(testName + testMode.postfix);
    assertTrue("Missing the test in classpath", executable.exists());
    List<String> logs = runTestBinary(executable.getAbsolutePath());

    // Cleanup log message for jsunit until "Start" log.
    if (testMode.isJ2cl()) {
      int startIndex = Iterables.indexOf(logs, x -> x.endsWith("  Start"));
      logs = logs.subList(startIndex, logs.size());
    }

    return logs;
  }

  private static List<String> runTestBinary(String binaryPath) throws Exception {
    // Passing --nooutputredirect since the testing infrastructure hides the standard output
    // for passing tests, this makes sure we can see our output.
    ProcessBuilder pb =
        new ProcessBuilder(binaryPath, "--nooutputredirect").redirectErrorStream(true);
    Map<String, String> environment = pb.environment();

    // making sure that we are not requesting sharding for our jsunit_tests
    // We shard the actual java_test for it to run in parallel
    environment.remove("TEST_TOTAL_SHARDS");
    environment.remove("TEST_SHARD_INDEX");
    environment.remove("TEST_SHARD_STATUS_FILE");

    Process p = pb.start();
    final SettableFuture<String> consoleOutput = SettableFuture.create();

    // Read stdout / stderr from the process so it can not block
    new Thread(
            () -> {
              try {
                consoleOutput.set(
                    CharStreams.toString(
                        new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8)));
              } catch (IOException e) {
                consoleOutput.setException(e);
              }
            })
        .start();

    return Splitter.on('\n').omitEmptyStrings().splitToList(consoleOutput.get());
  }

  protected String symbolName(String endSymbol) {
    return getTestDataPackage() + "." + endSymbol;
  }

  protected File getTestDataFile(String fileName) {
    String javaTestsRoot = "third_party/java_src/j2cl/junit/generator/javatests/";
    return new File(javaTestsRoot + getTestDataPackage().replace('.', '/'), fileName);
  }

  private String getTestDataPackage() {
    return getClass().getPackage().getName() + ".data";
  }
}
