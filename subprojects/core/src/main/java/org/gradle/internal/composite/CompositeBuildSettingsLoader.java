/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.internal.composite;

import org.gradle.StartParameter;
import org.gradle.api.initialization.ConfigurableIncludedBuild;
import org.gradle.api.internal.BuildDefinition;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.SettingsInternal;
import org.gradle.composite.internal.IncludedBuildRegistry;
import org.gradle.initialization.IncludedBuildSpec;
import org.gradle.initialization.NestedBuildFactory;
import org.gradle.initialization.SettingsLoader;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.RunnableBuildOperation;
import org.gradle.internal.progress.BuildOperationDescriptor;
import org.gradle.plugin.management.internal.DefaultPluginRequests;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;

import static org.gradle.initialization.StartParameterBuildOptions.ContinueOption;

public class CompositeBuildSettingsLoader implements SettingsLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger(CompositeBuildSettingsLoader.class);
    private final SettingsLoader delegate;
    private final NestedBuildFactory nestedBuildFactory;
    private final IncludedBuildRegistry includedBuildRegistry;
    private final StartParameter startParameter;
    private final BuildOperationExecutor buildOperationExecutor;

    public CompositeBuildSettingsLoader(SettingsLoader delegate, NestedBuildFactory nestedBuildFactory, IncludedBuildRegistry includedBuildRegistry, StartParameter startParameter, BuildOperationExecutor buildOperationExecutor) {
        this.delegate = delegate;
        this.nestedBuildFactory = nestedBuildFactory;
        this.includedBuildRegistry = includedBuildRegistry;
        this.startParameter = startParameter;
        this.buildOperationExecutor = buildOperationExecutor;
    }

    @Override
    public SettingsInternal findAndLoadSettings(final GradleInternal gradle) {
        final SettingsInternal settings = delegate.findAndLoadSettings(gradle);

        final List<IncludedBuildSpec> settingsFileInclusions = settings.getIncludedBuilds();
        final List<File> startParamInclusions = gradle.getStartParameter().getIncludedBuilds();
        boolean isComposite = !settingsFileInclusions.isEmpty() || !startParamInclusions.isEmpty();

        if (isComposite) {
            maybeInformAboutContinueOnFailureLimitation(startParameter);

            buildOperationExecutor.run(new RunnableBuildOperation() {
                @Override
                public void run(BuildOperationContext context) {
                    for (IncludedBuildSpec includedBuildSpec : settingsFileInclusions) {
                        // TODO: Allow builds to inject into explicitly included builds
                        ConfigurableIncludedBuild includedBuild = includedBuildRegistry.addExplicitBuild(BuildDefinition.fromStartParameterForBuild(gradle.getStartParameter(), includedBuildSpec.rootDir, DefaultPluginRequests.EMPTY), nestedBuildFactory);
                        includedBuildSpec.configurer.execute(includedBuild);
                    }

                    for (File rootDir : startParamInclusions) {
                        // TODO: Allow builds to inject into explicitly included builds
                        includedBuildRegistry.addExplicitBuild(BuildDefinition.fromStartParameterForBuild(gradle.getStartParameter(), rootDir, DefaultPluginRequests.EMPTY), nestedBuildFactory);
                    }

                    // Lock-in explicitly included builds
                    includedBuildRegistry.validateExplicitIncludedBuilds(settings);
                }

                @Override
                public BuildOperationDescriptor.Builder description() {
                    return BuildOperationDescriptor.displayName("Configure included builds");
                }
            });
        }

        return settings;
    }

    private void maybeInformAboutContinueOnFailureLimitation(StartParameter startParameter) {
        if (startParameter.isContinueOnFailure()) {
            LOGGER.warn("Using '--{}' with a composite build does not collect all failures.", ContinueOption.LONG_OPTION);
        }
    }
}
