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

package org.gradle.gradlebuild.packaging

import org.gradle.api.Plugin
import org.gradle.api.Project

import org.gradle.kotlin.dsl.*


/**
 * A map from artifact name to a set of class name prefixes that should be kept.
 * Artifacts matched by this map will be minified to only contain the specified
 * classes and the classes they depend on. The classes are not relocated, they all
 * remain in their original namespace. This reduces the final Gradle distribution
 * size and makes us more conscious of which parts of a library we really need.
 */
open class MinifyPlugin : Plugin<Project> {

    override fun apply(project: Project): Unit = project.run {
        val keepPatterns = mapOf(
            "fastutil" to setOf(
                "it.unimi.dsi.fastutil.ints.IntOpenHashSet",
                "it.unimi.dsi.fastutil.ints.IntSets"
            )
        )

        allprojects {
            plugins.withId("java-base") {
                dependencies {
                    attributesSchema {
                        attribute(minified)
                    }
                    artifactTypes.getByName("jar") {
                        attributes.attribute(minified, java.lang.Boolean.FALSE)
                    }
                    registerTransform {
                        /*
                         * TODO Why do I have to add artifactType=jar here? According to
                         * the declaration above, it's the only artifact type for which
                         * minified=false anyway. If I don't add this, the transform chain
                         * in binary-compatibility.gradle no longer works.
                         */
                        from.attribute(minified, false).attribute(artifactType, "jar")
                        to.attribute(minified, true).attribute(artifactType, "jar")
                        artifactTransform(MinifyTransform::class.java) {
                            params(keepPatterns)
                        }
                    }
                }
                configurations.all {
                    /*
                     * Some of our projects still depend on matching the default
                     * configuration. As soon as any attribute is added, the default
                     * configuraiton is no longer a valid match. To work around this
                     * we only add the "minified" attribute in places where we already
                     * use other attributes.
                     */
                    afterEvaluate {
                        if (!attributes.isEmpty) {
                            attributes.attribute(minified, true)
                        }
                    }
                }
            }
        }
    }
}
