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

package org.gradle.integtests.resolve

import org.gradle.integtests.fixtures.GradleMetadataResolveRunner
import org.gradle.integtests.fixtures.RequiredFeature
import org.gradle.integtests.fixtures.RequiredFeatures

@RequiredFeatures(
    @RequiredFeature(feature = GradleMetadataResolveRunner.GRADLE_METADATA, value = "true")
)
class AlignmentConstraintsIntegrationTest extends AbstractModuleDependencyResolveTest {

    def setup() {
        buildFile << """
            configurations { conf }
        """
        executer.withStackTraceChecksDisabled()
    }


    def "library author can make sure modules are aligned using cross-module constraints"() {
        given:
        repository {
            ['1.0', '1.1'].each { version ->
                "org:jersey-core:$version" {
                    constraint(group:'org', artifact: 'jersey-server', version: version, reason: "Align with jersey-core $version")
                }
                "org:jersey-server:$version" {
                    constraint(group:'org', artifact: 'jersey-core', version: version, reason: "Align with jersey-server $version")
                }
            }
        }
        buildFile << """
            dependencies {
                conf 'org:jersey-core:1.0'
                conf 'org:jersey-server:1.1'
            }
        """

        when:
        repositoryInteractions {
            'org:jersey-core:1.0' {
                expectGetMetadata()
            }
            'org:jersey-core:1.1' {
                expectResolve()
            }
            'org:jersey-server:1.1' {
                expectResolve()
            }

        }
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge('org:jersey-core:1.0', 'org:jersey-core:1.1') {
                    module('org:jersey-server:1.1') // edge created by constraint
                }.byReason('Align with jersey-server 1.1')
                module('org:jersey-server:1.1') {
                    module('org:jersey-core:1.1') // edge created by constraint
                }
            }
        }
    }

    def "library author can make sure modules are aligned using platform definition"() {
        given:
        repository {
            ['1.0', '1.1'].each { version ->
                "org:jersey-core:$version" {
                    dependsOn("org:jersey-platform:$version")
                }
                "org:jersey-server:$version" {
                    dependsOn("org:jersey-platform:$version")
                }
                "org:jersey-platform:$version" {
                    constraint(group:'org', artifact: 'jersey-core', version: version, reason: "Align with jersey-platform $version")
                    constraint(group:'org', artifact: 'jersey-server', version: version, reason: "Align with jersey-platform $version")
                }
            }
        }
        buildFile << """
            dependencies {
                conf 'org:jersey-core:1.0'
                conf 'org:jersey-server:1.1'
            }
        """

        when:
        repositoryInteractions {
            'org:jersey-platform:1.0' {
                expectGetMetadata()
            }
            'org:jersey-platform:1.1' {
                expectResolve()
            }
            'org:jersey-core:1.0' {
                expectGetMetadata()
            }
            'org:jersey-core:1.1' {
                expectResolve()
            }
            'org:jersey-server:1.1' {
                expectResolve()
            }

        }
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge('org:jersey-core:1.0', 'org:jersey-core:1.1') {
                    module('org:jersey-platform:1.1')
                }.byReason('Align with jersey-platform 1.1')
                module('org:jersey-server:1.1') {
                    module('org:jersey-platform:1.1') {
                        module('org:jersey-core:1.1')
                        module('org:jersey-server:1.1')
                    }
                }
            }
        }
    }

    def "can declare a module set"() {
        given:
        repository {
            ['1.0', '1.1'].each { version ->
                "org:jersey-core:$version"()
                "org:jersey-server:$version"()
            }
        }
        withModuleSets()

        buildFile << """
            dependencies {
                conf 'org:jersey-core:1.0'
                conf 'org:jersey-server:1.1'
            }
            
            // NOT published
            moduleSet('Jersey') {
               include 'org:jersey-core'
               include 'org:jersey-server'
            }
        """

        when:
        repositoryInteractions {
            'org:jersey-core:1.0' {
                expectGetMetadata()
            }
            'org:jersey-core:1.1' {
                expectResolve()
            }
            'org:jersey-server:1.1' {
                expectResolve()
            }

        }
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge('org:jersey-core:1.0', 'org:jersey-core:1.1') {
                    module('org:jersey-server:1.1') // edge created by constraint
                }.byReason('Align Jersey 1.1')
                module('org:jersey-server:1.1') {
                    module('org:jersey-core:1.1') // edge created by constraint
                }
            }
        }
    }

    private void withModuleSets() {
        buildFile << """

            class ModuleSet {
                String name
                Set<String> modules = []
                ModuleSet include(String module) { modules << module; this }
            }
            
            def moduleSetsById = [:].withDefault { [] }

            ext.moduleSet = { String name, Closure cl ->
                def ms = new ModuleSet(name: name)
                cl.delegate = ms
                cl()
                ms.modules.each {
                    moduleSetsById[it] << ms
                }
            }

            dependencies {
                components.all { details ->
                    String module = id.module.toString()
                    moduleSetsById[module]?.each { moduleSet ->
                       allVariants {
                          withDependencyConstraints {
                             moduleSet.modules.each { mid ->
                                if (mid != module) {
                                   add("\$mid:\$id.version") {
                                      because "Align \$moduleSet.name \$id.version"
                                   }
                                }
                             }
                          }
                       }
                    }
                }
            }
        """
    }
}

