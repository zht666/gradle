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
package org.gradle.api.plugins;

import com.google.common.collect.ImmutableSet;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.DependencyConstraint;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.attributes.Usage;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.component.SoftwareComponentInternal;
import org.gradle.api.internal.component.UsageContext;
import org.gradle.api.model.ObjectFactory;

import javax.inject.Inject;
import java.util.Collections;
import java.util.Set;

public class PlatformPlugin implements Plugin<Project> {

    public static final String PLATFORM_CONFIGURATION_NAME = "platform";

    private final ObjectFactory objectFactory;

    @Inject
    public PlatformPlugin(ObjectFactory objectFactory) {
        this.objectFactory = objectFactory;
    }

    @Override
    public void apply(Project project) {
        Configuration platformConfiguration = createPlatformConfiguration(project);
        createComponent(project, platformConfiguration);
    }

    private void createComponent(Project project, Configuration platformConfiguration) {
        project.getComponents().add(objectFactory.newInstance(DefaultPlatformComponent.class, platformConfiguration));
    }

    private Configuration createPlatformConfiguration(Project project) {
        Configuration platform = project.getConfigurations().create(PLATFORM_CONFIGURATION_NAME);
        platform.setCanBeResolved(false);
        platform.setCanBeConsumed(true);
        return platform;
    }


    public static class DefaultPlatformComponent implements SoftwareComponentInternal {

        private final UsageContext usage;

        @Inject
        public DefaultPlatformComponent(ObjectFactory objectFactory, Configuration platformConfiguration) {
            this.usage = new DefaultPlatformUsageContext(objectFactory.named(Usage.class, "default"), platformConfiguration);
        }

        @Override
        public Set<? extends UsageContext> getUsages() {
            return Collections.singleton(usage);
        }

        @Override
        public String getName() {
            return "platform";
        }
    }

    private static class DefaultPlatformUsageContext implements UsageContext {

        private final Usage usage;
        private final Configuration platformConfiguration;

        private DefaultPlatformUsageContext(Usage usage, Configuration platformConfiguration) {
            this.usage = usage;
            this.platformConfiguration = platformConfiguration;
        }

        @Override
        public Usage getUsage() {
            return usage;
        }

        @Override
        public Set<? extends PublishArtifact> getArtifacts() {
            return Collections.emptySet();
        }

        @Override
        public Set<? extends ModuleDependency> getDependencies() {
            return platformConfiguration.getIncoming().getDependencies().withType(ModuleDependency.class);
        }

        @Override
        public Set<? extends DependencyConstraint> getDependencyConstraints() {
            return platformConfiguration.getIncoming().getDependencyConstraints();
        }

        @Override
        public Set<? extends Capability> getCapabilities() {
            return ImmutableSet.copyOf(platformConfiguration.getOutgoing().getCapabilities());
        }

        @Override
        public String getName() {
            return usage.getName();
        }

        @Override
        public AttributeContainer getAttributes() {
            return ImmutableAttributes.EMPTY;
        }
    }
}
