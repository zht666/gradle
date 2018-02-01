/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal.tasks.testing.junitplatform;

import com.google.common.collect.ImmutableSet;
import org.gradle.api.internal.tasks.testing.detection.AbstractTestFrameworkDetector;
import org.gradle.api.internal.tasks.testing.detection.ClassFileExtractionManager;
import org.gradle.api.internal.tasks.testing.detection.TestClassVisitor;
import org.gradle.api.internal.tasks.testing.detection.TestFrameworkDetector;
import org.gradle.api.internal.tasks.testing.junit.JUnitDetector;
import org.gradle.api.internal.tasks.testing.junit.JUnitTestClassDetector;

import java.util.Set;

public class JUnitPlatformDetector extends AbstractTestFrameworkDetector<TestClassVisitor> {
    private final boolean detectLegacyJUnit;

    public JUnitPlatformDetector(ClassFileExtractionManager classFileExtractionManager, boolean detectLegacyJUnit) {
        super(classFileExtractionManager);
        this.detectLegacyJUnit = detectLegacyJUnit;
    }

    @Override
    protected TestClassVisitor createClassVisitor() {
        if (detectLegacyJUnit) {
            return new MixJUnitTestClassDetector(this);
        } else {
            return new JUnitPlatformTestClassDetector(this);
        }
    }

    @Override
    protected boolean isKnownTestCaseClassName(String testCaseClassName) {
        return detectLegacyJUnit && JUnitDetector.isKnownJUnitTestCaseClassName(testCaseClassName);
    }


    private static class MixJUnitTestClassDetector extends TestClassVisitor {
        private static final Set<String> METHOD_ANNOTATIONS = ImmutableSet.<String>builder()
            .addAll(JUnitTestClassDetector.METHOD_ANNOTATIONS)
            .addAll(JUnitPlatformTestClassDetector.METHOD_ANNOTATIONS)
            .build();
        private static final Set<String> CLASS_ANNOTATIONS = ImmutableSet.<String>builder()
            .addAll(JUnitTestClassDetector.CLASS_ANNOTATIONS)
            .addAll(JUnitPlatformTestClassDetector.CLASS_ANNOTATIONS)
            .build();

        private MixJUnitTestClassDetector(TestFrameworkDetector detector) {
            super(detector);
        }

        @Override
        protected boolean ignoreMethodsInAbstractClass() {
            return false;
        }

        @Override
        protected boolean ignoreNonStaticInnerClass() {
            return false;
        }

        @Override
        protected Set<String> getTestMethodAnnotations() {
            return METHOD_ANNOTATIONS;
        }

        @Override
        protected Set<String> getTestClassAnnotations() {
            return CLASS_ANNOTATIONS;
        }
    }
}
