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

package org.gradle.language.nativeplatform.internal.incremental;

import org.gradle.language.nativeplatform.internal.Include;
import org.testng.collections.Maps;

import java.io.File;
import java.util.Map;

public class CachingSourceIncludesResolver implements SourceIncludesResolver {
    private final SourceIncludesResolver delegate;
    private final Map<Include, IncludeResolutionResult> cache = Maps.newHashMap();

    public CachingSourceIncludesResolver(SourceIncludesResolver delegate) {
        this.delegate = delegate;
    }

    @Override
    public IncludeResolutionResult resolveInclude(File sourceFile, Include include, MacroLookup visibleMacros) {
        IncludeResolutionResult result = cache.get(include);
        if (!canReuseResult(result)) {
            result = delegate.resolveInclude(sourceFile, include, visibleMacros);
            cache.put(include, result);
        }
        return result;
    }

    private boolean canReuseResult(IncludeResolutionResult result) {
        // TODO: Wrong!
        return result != null;
    }
}
