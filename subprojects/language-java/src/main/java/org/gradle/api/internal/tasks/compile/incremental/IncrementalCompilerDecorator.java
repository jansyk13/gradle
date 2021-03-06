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

import org.gradle.api.internal.tasks.compile.CleaningJavaCompiler;
import org.gradle.api.internal.tasks.compile.JavaCompileSpec;
import org.gradle.api.internal.tasks.compile.incremental.cache.TaskScopedCompileCaches;
import org.gradle.api.internal.tasks.compile.incremental.deps.ClassSetAnalysis;
import org.gradle.api.internal.tasks.compile.incremental.deps.ClassSetAnalysisData;
import org.gradle.api.internal.tasks.compile.incremental.jar.JarClasspathSnapshotMaker;
import org.gradle.api.internal.tasks.compile.incremental.jar.PreviousCompilation;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.incremental.IncrementalTaskInputs;
import org.gradle.language.base.internal.compile.Compiler;

/**
 * Decorates a non-incremental Java compiler (like javac) so that it can be invoked incrementally.
 */
public class IncrementalCompilerDecorator {

    private static final Logger LOG = Logging.getLogger(IncrementalCompilerDecorator.class);
    private final JarClasspathSnapshotMaker jarClasspathSnapshotMaker;
    private final TaskScopedCompileCaches compileCaches;
    private final CleaningJavaCompiler cleaningCompiler;
    private final RecompilationSpecProvider staleClassDetecter;
    private final ClassSetAnalysisUpdater classSetAnalysisUpdater;
    private final CompilationSourceDirs sourceDirs;
    private final Compiler<JavaCompileSpec> rebuildAllCompiler;
    private final IncrementalCompilationInitializer compilationInitializer;

    public IncrementalCompilerDecorator(JarClasspathSnapshotMaker jarClasspathSnapshotMaker, TaskScopedCompileCaches compileCaches,
                                        IncrementalCompilationInitializer compilationInitializer, CleaningJavaCompiler cleaningCompiler,
                                        RecompilationSpecProvider staleClassDetecter, ClassSetAnalysisUpdater classSetAnalysisUpdater,
                                        CompilationSourceDirs sourceDirs, Compiler<JavaCompileSpec> rebuildAllCompiler) {
        this.jarClasspathSnapshotMaker = jarClasspathSnapshotMaker;
        this.compileCaches = compileCaches;
        this.compilationInitializer = compilationInitializer;
        this.cleaningCompiler = cleaningCompiler;
        this.staleClassDetecter = staleClassDetecter;
        this.classSetAnalysisUpdater = classSetAnalysisUpdater;
        this.sourceDirs = sourceDirs;
        this.rebuildAllCompiler = rebuildAllCompiler;
    }

    public Compiler<JavaCompileSpec> prepareCompiler(IncrementalTaskInputs inputs) {
        Compiler<JavaCompileSpec> compiler = getCompiler(inputs, sourceDirs);
        return new IncrementalResultStoringDecorator(compiler, jarClasspathSnapshotMaker, classSetAnalysisUpdater, compileCaches.getAnnotationProcessorPathStore());
    }

    private Compiler<JavaCompileSpec> getCompiler(IncrementalTaskInputs inputs, CompilationSourceDirs sourceDirs) {
        if (!inputs.isIncremental()) {
            LOG.info("Full recompilation is required because no incremental change information is available. This is usually caused by clean builds or changing compiler arguments.");
            return rebuildAllCompiler;
        }
        if (!sourceDirs.canInferSourceRoots()) {
            LOG.info("Full recompilation is required because the source roots could not be inferred.");
            return rebuildAllCompiler;
        }
        ClassSetAnalysisData data = compileCaches.getLocalClassSetAnalysisStore().get();
        if (data == null) {
            LOG.info("Full recompilation is required because no previous class analysis is available.");
            return rebuildAllCompiler;
        }
        PreviousCompilation previousCompilation = new PreviousCompilation(new ClassSetAnalysis(data), compileCaches.getLocalJarClasspathSnapshotStore(), compileCaches.getJarSnapshotCache(), compileCaches.getAnnotationProcessorPathStore());
        return new SelectiveCompiler(inputs, previousCompilation, cleaningCompiler, rebuildAllCompiler, staleClassDetecter, compilationInitializer, jarClasspathSnapshotMaker);
    }
}
