/*
 * Copyright 2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.internal.tasks.compile.incremental;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.gradle.api.file.FileTree;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.FileVisitor;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.internal.tasks.compile.JavaCompileSpec;
import org.gradle.api.internal.tasks.compile.incremental.recomp.RecompilationSpec;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.internal.Factory;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

class IncrementalCompilationInitializer {
    private final FileOperations fileOperations;
    private final FileTree sourceTree;

    public IncrementalCompilationInitializer(FileOperations fileOperations, FileTree sourceTree) {
        this.fileOperations = fileOperations;
        this.sourceTree = sourceTree;
    }

    public void initializeCompilation(JavaCompileSpec spec, RecompilationSpec recompilationSpec) {
        if (!recompilationSpec.isBuildNeeded()) {
            spec.setSourceFiles(ImmutableSet.<File>of());
            spec.setClasses(Collections.<String>emptySet());
            return;
        }
        Factory<PatternSet> patternSetFactory = fileOperations.getFileResolver().getPatternSetFactory();
        PatternSet classesToDelete = patternSetFactory.create();
        PatternSet sourceToCompile = patternSetFactory.create();

        preparePatterns(recompilationSpec.getClassesToCompile(), classesToDelete, sourceToCompile);
        spec.setSourceFiles(narrowDownSourcesToCompile(sourceTree, sourceToCompile));
        includePreviousCompilationOutputOnClasspath(spec);
        addClassesToProcess(spec, recompilationSpec);
        deleteStaleFilesIn(classesToDelete, spec.getDestinationDir());
        deleteStaleFilesIn(classesToDelete, spec.getCompileOptions().getAnnotationProcessorGeneratedSourcesDirectory());
    }

    private Iterable<File> narrowDownSourcesToCompile(FileTree sourceTree, PatternSet sourceToCompile) {
        return sourceTree.matching(sourceToCompile);
    }

    private void includePreviousCompilationOutputOnClasspath(JavaCompileSpec spec) {
        List<File> classpath = Lists.newArrayList(spec.getCompileClasspath());
        File destinationDir = spec.getDestinationDir();
        classpath.add(destinationDir);
        spec.setCompileClasspath(classpath);
    }

    @VisibleForTesting
    void addClassesToProcess(JavaCompileSpec spec, RecompilationSpec recompilationSpec) {
        Set<String> classesToProcess = Sets.newHashSet(recompilationSpec.getClassesToProcess());
        classesToProcess.removeAll(recompilationSpec.getClassesToCompile());
        spec.setClasses(classesToProcess);
    }

    private void deleteStaleFilesIn(PatternSet filesToDelete, final File destinationDir) {
        if (destinationDir == null) {
            return;
        }
        fileOperations.fileTree(destinationDir).matching(filesToDelete).visit(new FileVisitor() {

            @Override
            public void visitDir(FileVisitDetails dirDetails) {
            }

            @Override
            public void visitFile(FileVisitDetails fileDetails) {
                File file = fileDetails.getFile();
                file.delete();
                deleteEmptyParents(file);
            }

            private void deleteEmptyParents(File file) {
                File parentDir = file.getParentFile();
                if (parentDir != null && !parentDir.equals(destinationDir) && isEmpty(parentDir)) {
                    parentDir.delete();
                    deleteEmptyParents(parentDir);
                }
            }

            private boolean isEmpty(File dir) {
                String[] children = dir.list();
                return children != null && children.length == 0;
            }
        });
    }

    @VisibleForTesting
    void preparePatterns(Collection<String> staleClasses, PatternSet filesToDelete, PatternSet sourceToCompile) {
        for (String staleClass : staleClasses) {
            String path = staleClass.replaceAll("\\.", "/");
            filesToDelete.include(path.concat(".class"));
            filesToDelete.include(path.concat(".java"));
            filesToDelete.include(path.concat("$*.class"));
            filesToDelete.include(path.concat("$*.java"));

            sourceToCompile.include(path.concat(".java"));
            sourceToCompile.include(path.concat("$*.java"));
        }
    }
}
