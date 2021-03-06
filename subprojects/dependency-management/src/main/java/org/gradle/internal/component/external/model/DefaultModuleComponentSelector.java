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
package org.gradle.internal.component.external.model;

import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.artifacts.VersionConstraint;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.internal.artifacts.ImmutableVersionConstraint;
import org.gradle.api.internal.artifacts.dependencies.DefaultImmutableVersionConstraint;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.ImmutableAttributes;

public class DefaultModuleComponentSelector implements ModuleComponentSelector {
    private final String group;
    private final String module;
    private final ImmutableVersionConstraint versionConstraint;
    private final ImmutableAttributes attributes;

    private DefaultModuleComponentSelector(String group, String module, ImmutableVersionConstraint version, ImmutableAttributes attributes) {
        assert group != null : "group cannot be null";
        assert module != null : "module cannot be null";
        assert version != null : "version cannot be null";
        assert attributes != null : "attributes cannot be null";
        this.group = group;
        this.module = module;
        this.versionConstraint = version;
        this.attributes = attributes;
    }

    public String getDisplayName() {
        StringBuilder builder = new StringBuilder(group.length() + module.length() + versionConstraint.getPreferredVersion().length() + 2);
        builder.append(group);
        builder.append(":");
        builder.append(module);
        if (versionConstraint.getPreferredVersion().length() > 0) {
            builder.append(":");
            builder.append(versionConstraint.getPreferredVersion());
        }
        if (versionConstraint.getBranch() != null) {
            builder.append(" (branch: ");
            builder.append(versionConstraint.getBranch());
            builder.append(")");
        }
        return builder.toString();
    }

    public String getGroup() {
        return group;
    }

    public String getModule() {
        return module;
    }

    public String getVersion() {
        return versionConstraint.getPreferredVersion();
    }

    @Override
    public VersionConstraint getVersionConstraint() {
        return versionConstraint;
    }

    @Override
    public AttributeContainer getAttributes() {
        return attributes;
    }

    public boolean matchesStrictly(ComponentIdentifier identifier) {
        assert identifier != null : "identifier cannot be null";

        if (identifier instanceof ModuleComponentIdentifier) {
            ModuleComponentIdentifier moduleComponentIdentifier = (ModuleComponentIdentifier) identifier;
            return module.equals(moduleComponentIdentifier.getModule())
                && group.equals(moduleComponentIdentifier.getGroup())
                && versionConstraint.getPreferredVersion().equals(moduleComponentIdentifier.getVersion());
        }

        return false;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        DefaultModuleComponentSelector that = (DefaultModuleComponentSelector) o;

        if (!group.equals(that.group)) {
            return false;
        }
        if (!module.equals(that.module)) {
            return false;
        }
        if (!versionConstraint.equals(that.versionConstraint)) {
            return false;
        }
        if (!attributes.equals(that.attributes)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = group.hashCode();
        result = 31 * result + module.hashCode();
        result = 31 * result + versionConstraint.hashCode();
        result = 31 * result + attributes.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return getDisplayName();
    }

    public static ModuleComponentSelector newSelector(String group, String name, VersionConstraint version, AttributeContainer attributes) {
        return new DefaultModuleComponentSelector(group, name, DefaultImmutableVersionConstraint.of(version), ((AttributeContainerInternal)attributes).asImmutable());
    }

    public static ModuleComponentSelector newSelector(String group, String name, VersionConstraint version) {
        return new DefaultModuleComponentSelector(group, name, DefaultImmutableVersionConstraint.of(version), ImmutableAttributes.EMPTY);
    }

    public static ModuleComponentSelector newSelector(String group, String name, String version) {
        return new DefaultModuleComponentSelector(group, name, DefaultImmutableVersionConstraint.of(version), ImmutableAttributes.EMPTY);
    }

    public static ModuleComponentSelector newSelector(ModuleVersionSelector selector) {
        return new DefaultModuleComponentSelector(selector.getGroup(), selector.getName(), DefaultImmutableVersionConstraint.of(selector.getVersion()), ImmutableAttributes.EMPTY);
    }
}
