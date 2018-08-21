// Copyright 2018 The Bazel Authors. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.devtools.build.lib.blackbox.bazel;

import com.google.devtools.build.lib.blackbox.framework.BlackBoxTestContext;
import com.google.devtools.build.lib.blackbox.framework.ToolsSetup;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Setup for Bazel default tools */
public class DefaultToolsSetup implements ToolsSetup {
  @Override
  public void setup(BlackBoxTestContext context) throws IOException {
    Path outputRoot = Files.createTempDirectory(context.getTmpDir(), "root").toAbsolutePath();
    context.write(
        ".bazelrc",
        "startup --output_user_root=" + outputRoot.toString(),
        "build -j 8"); // TODO(ichern) not sure about this option, copying from the existing shell
  }
}