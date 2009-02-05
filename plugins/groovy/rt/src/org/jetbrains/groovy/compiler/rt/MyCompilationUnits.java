package org.jetbrains.groovy.compiler.rt;

/*
 * Copyright 2000-2007 JetBrains s.r.o.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import groovy.lang.GroovyRuntimeException;
import org.codehaus.groovy.ast.ASTNode;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.control.*;
import org.codehaus.groovy.control.messages.*;
import org.codehaus.groovy.syntax.SyntaxException;
import org.codehaus.groovy.tools.GroovyClass;

import java.io.File;
import java.io.IOException;
import java.util.*;


public class MyCompilationUnits {

  final CompilationUnit sourceCompilationUnit;
  final CompilationUnit testCompilationUnit;

  private final List sourceFilesToCompile = new ArrayList();
  private final List testFilesToCompile = new ArrayList();

  MyCompilationUnits(CompilationUnit sourceCompilationUnit, CompilationUnit testCompilationUnit) {
    this.sourceCompilationUnit = sourceCompilationUnit;
    this.testCompilationUnit = testCompilationUnit;
  }

  public void add(File fileToCompile, boolean inTestSourceFolder) {
    if (inTestSourceFolder) {
      addTest(fileToCompile);
    } else {
      addSource(fileToCompile);
    }
  }

  private void addSource(File file) {
    sourceFilesToCompile.add(sourceCompilationUnit.addSource(new File(file.getPath())));
  }

  private void addTest(File file) {
    testFilesToCompile.add(testCompilationUnit.addSource(new File(file.getPath())));
  }

  public void compile(MessageCollector collector, List compiledFiles, List filesToRecompile) {
    compile(collector, sourceCompilationUnit, compiledFiles, filesToRecompile);
    compile(collector, testCompilationUnit, compiledFiles, filesToRecompile);
  }

  void compile(MessageCollector collector, CompilationUnit compilationUnit, List compiledFiles, List filesToRecompile) {
    try {
      compilationUnit.compile();
      addCompiledFiles(compilationUnit, compiledFiles);
    } catch (CompilationFailedException e) {
      processCompilationException(e, collector);
    } catch (IOException e) {
      processException(e, collector);
    } finally {
      addWarnings(compilationUnit.getErrorCollector(), collector);
    }
  }

  private void addCompiledFiles(CompilationUnit compilationUnit, List compiledFiles) throws IOException {
    File targetDirectory = compilationUnit.getConfiguration().getTargetDirectory();

    String outputPath = targetDirectory.getCanonicalPath().replace(File.separatorChar, '/');
    final SortedSet allClasses = new TreeSet();
    List listOfClasses = compilationUnit.getClasses();
    for (int i = 0; i < listOfClasses.size(); i++) {
      allClasses.add(((GroovyClass)listOfClasses.get(i)).getName());
    }

    for (Iterator iterator = compilationUnit.iterator(); iterator.hasNext();) {
      SourceUnit sourceUnit = (SourceUnit) iterator.next();
      String fileName = sourceUnit.getName();
      //for debug purposes
      System.out.println("source: " + fileName);
      System.out.print("classes:");
      final List topLevelClasses = sourceUnit.getAST().getClasses();

      for (int i = 0; i < topLevelClasses.size(); i++) {
        final String topLevel = ((ClassNode)topLevelClasses.get(i)).getName();
        final String nested = topLevel + "$";
        final SortedSet tail = allClasses.tailSet(topLevel);
        for (Iterator tailIter = tail.iterator(); tailIter.hasNext();) {
          String className = (String)tailIter.next();
          if (className.equals(topLevel) || className.startsWith(nested)) {
            tailIter.remove();
            System.out.print("  " + className);
            compiledFiles.add(new OutputItemImpl(outputPath, outputPath + "/" + className.replace('.', '/') + ".class", fileName));
          } else {
            break;
          }
        }
      }
      System.out.println("");
    }
  }

  private void addWarnings(ErrorCollector errorCollector, MessageCollector collector) {
    for (int i = 0; i < errorCollector.getWarningCount(); i++) {
      WarningMessage warning = errorCollector.getWarning(i);
      collector.addMessage(MessageCollector.WARNING, warning.getMessage(), null, -1, -1);
    }
  }

  private void processCompilationException(Exception exception, MessageCollector collector) {
    if (exception instanceof MultipleCompilationErrorsException) {
      MultipleCompilationErrorsException multipleCompilationErrorsException = (MultipleCompilationErrorsException) exception;
      ErrorCollector errorCollector = multipleCompilationErrorsException.getErrorCollector();
      for (int i = 0; i < errorCollector.getErrorCount(); i++) {
        processException(errorCollector.getError(i), collector);
      }
    } else {
      processException(exception, collector);
    }
  }

  private void processException(Message message, MessageCollector collector) {
    if (message instanceof SyntaxErrorMessage) {
      SyntaxErrorMessage syntaxErrorMessage = (SyntaxErrorMessage) message;
      addErrorMessage(syntaxErrorMessage.getCause(), collector);
    } else if (message instanceof ExceptionMessage) {
      ExceptionMessage exceptionMessage = (ExceptionMessage) message;
      processException(exceptionMessage.getCause(), collector);
    } else if (message instanceof SimpleMessage) {
      addErrorMessage((SimpleMessage) message, collector);
    } else {
      collector.addMessage(MessageCollector.ERROR, "An unknown error occurred.", null, -1, -1);
    }
  }

  private void processException(Exception exception, MessageCollector collector) {
    if (exception instanceof GroovyRuntimeException) {
      addErrorMessage((GroovyRuntimeException) exception, collector);
    } else {
      collector.addMessage(MessageCollector.ERROR, exception.getMessage(), null, -1, -1);
    }
  }

  private static String LINE_AT = " @ line ";

  private void addErrorMessage(SyntaxException exception, MessageCollector collector) {
    String message = exception.getMessage();
    String justMessage = message.substring(0, message.lastIndexOf(LINE_AT));
    collector.addMessage(MessageCollector.ERROR, justMessage, pathToUrl(exception.getSourceLocator()),
        exception.getLine(), exception.getStartColumn());
  }

  private void addErrorMessage(GroovyRuntimeException exception, MessageCollector collector) {
    ASTNode astNode = exception.getNode();
    collector.addMessage(MessageCollector.ERROR, exception.getMessageWithoutLocationText(),
        exception.getModule().getDescription(),
        astNode.getLineNumber(), astNode.getColumnNumber());
  }

  private void addErrorMessage(SimpleMessage message, MessageCollector collector) {
    collector.addMessage(MessageCollector.ERROR, message.getMessage(), null, -1, -1);
  }

  private String pathToUrl(String path) {
    return "file" + "://" + path;
  }

  private String getNameWithoutExtension(String filename) {
    String name = (new File(filename)).getName();
    int startExtentionIndex = name.lastIndexOf(".");

    return name.substring(0, startExtentionIndex);
  }

  public interface OutputItem {
    String getOutputPath();

    String getSourceFile();

    String getOutputRootDirectory();
  }

  public class OutputItemImpl implements OutputItem {

    private String myOutputPath;
    private String myOutputDir;
    private String mySourceFileName;

    public OutputItemImpl(String outputDir, String outputPath, String sourceFileName) {
      myOutputDir = outputDir;
      myOutputPath = outputPath;
      mySourceFileName = sourceFileName;
    }

    public String getOutputPath() {
      return myOutputPath;
    }

    public String getOutputRootDirectory() {
      return myOutputDir;
    }

    public String getSourceFile() {
      return mySourceFileName;
    }
  }
}