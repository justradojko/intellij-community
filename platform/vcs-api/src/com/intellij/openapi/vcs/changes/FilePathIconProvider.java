// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public interface FilePathIconProvider {
  ExtensionPointName<FilePathIconProvider> EP_NAME =
    ExtensionPointName.create("com.intellij.openapi.vcs.changes.ui.filePathIconProvider");

  @Nullable
  Icon getIcon(@NotNull FilePath filePath, @Nullable Project project);
}
