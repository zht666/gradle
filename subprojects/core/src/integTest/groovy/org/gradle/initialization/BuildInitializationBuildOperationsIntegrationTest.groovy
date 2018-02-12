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

package org.gradle.initialization

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.BuildOperationNotificationsFixture
import org.gradle.integtests.fixtures.BuildOperationsFixture
import spock.lang.Ignore

class BuildInitializationBuildOperationsIntegrationTest extends AbstractIntegrationSpec {

    final buildOperations = new BuildOperationsFixture(executer, temporaryFolder)

    @SuppressWarnings("GroovyUnusedDeclaration")
    final operationNotificationsFixture = new BuildOperationNotificationsFixture(executer, temporaryFolder)

    def "build operations are fired and build path is exposed"() {
        buildFile << """
            task foo {
                doLast {
                    println 'foo'
                }
            }
        """
        when:
        succeeds('foo')

        def loadBuildBuildOperation = buildOperations.first(LoadBuildBuildOperationType)
        def evaluateSettingsBuildOperation = buildOperations.first(EvaluateSettingsBuildOperationType)
        def configureBuildBuildOperations = buildOperations.first(ConfigureBuildBuildOperationType)
        def loadProjectsBuildOperation = buildOperations.first(LoadProjectsBuildOperationType)

        then:
        loadBuildBuildOperation.details.buildPath == ":"
        loadBuildBuildOperation.result.isEmpty()

        evaluateSettingsBuildOperation.details.buildPath == ":"
        evaluateSettingsBuildOperation.result.isEmpty()
        assert loadBuildBuildOperation.id == evaluateSettingsBuildOperation.parentId

        configureBuildBuildOperations.details.buildPath == ":"
        configureBuildBuildOperations.result.isEmpty()

        loadProjectsBuildOperation.details.buildPath == ":"
        loadProjectsBuildOperation.result.rootProject.projectDir == settingsFile.parent
        buildOperations.first('Configure build').id == loadProjectsBuildOperation.parentId
    }

//    def "can emit notifications for nested builds"() {
//        when:
//        file("buildSrc/build.gradle") << ""
//        file("a/buildSrc/build.gradle") << ""
//        file("a/build.gradle") << "task t"
//        file("a/settings.gradle") << ""
//        file("settings.gradle") << "includeBuild 'a'"
//        buildScript """
//           ${registerListenerWithDrainRecordings()}
//            task t {
//                dependsOn gradle.includedBuild("a").task(":t")
//            }
//        """
//
//        succeeds "t"
//
//        then:
//        started(LoadBuildBuildOperationType.Details, [buildPath: ":"])
//
//        started(LoadBuildBuildOperationType.Details, [buildPath: ":"])
//        started(LoadBuildBuildOperationType.Details, [buildPath: ":buildSrc"])
//        started(LoadBuildBuildOperationType.Details, [buildPath: ":a"])
//        started(LoadBuildBuildOperationType.Details, [buildPath: ":a:buildSrc"])
//
//        started(EvaluateSettingsBuildOperationType.Details, [settingsDir: file('buildSrc').absolutePath, settingsFile: file('buildSrc/settings.gradle').absolutePath, buildPath: ":buildSrc"])
//        started(EvaluateSettingsBuildOperationType.Details, [settingsDir: file('a').absolutePath, settingsFile: file('a/settings.gradle').absolutePath, buildPath: ":a"])
//        started(EvaluateSettingsBuildOperationType.Details, [settingsDir: file('a/buildSrc').absolutePath, settingsFile: file('a/buildSrc/settings.gradle').absolutePath, buildPath: ":a:buildSrc"])
//        started(EvaluateSettingsBuildOperationType.Details, [settingsDir: file('.').absolutePath, settingsFile: file('settings.gradle').absolutePath, buildPath: ":"])
//
//        started(LoadProjectsBuildOperationType.Details, [buildPath: ":buildSrc"])
//        started(LoadProjectsBuildOperationType.Details, [buildPath: ":a:buildSrc"])
//        started(LoadProjectsBuildOperationType.Details, [buildPath: ":a"])
//        started(LoadProjectsBuildOperationType.Details, [buildPath: ":"])
//
//        started(ConfigureProjectBuildOperationType.Details, [buildPath: ":buildSrc", projectPath: ":"])
//        started(ConfigureProjectBuildOperationType.Details, [buildPath: ":a:buildSrc", projectPath: ":"])
//        started(ConfigureProjectBuildOperationType.Details, [buildPath: ":a", projectPath: ":"])
//        started(ConfigureProjectBuildOperationType.Details, [buildPath: ":", projectPath: ":"])
//
//        started(ConfigureProjectBuildOperationType.Details, [buildPath: ":buildSrc", projectPath: ":"])
//        started(ConfigureProjectBuildOperationType.Details, [buildPath: ":a:buildSrc", projectPath: ":"])
//        started(ConfigureProjectBuildOperationType.Details, [buildPath: ":a", projectPath: ":"])
//        started(ConfigureProjectBuildOperationType.Details, [buildPath: ":", projectPath: ":"])
//
//        // evaluate hierarchies
//        op(LoadBuildBuildOperationType.Details, [buildPath: ":"]).parentId == null // Run build build operation is not typed -> no id.
//        op(LoadBuildBuildOperationType.Details, [buildPath: ":a"]).parentId == null // Run build build operation is not typed -> no id.
//        op(LoadBuildBuildOperationType.Details, [buildPath: ":buildSrc"]).parentId == op(BuildBuildSrcBuildOperationType.Details, [buildPath: ':']).id
//        op(LoadBuildBuildOperationType.Details, [buildPath: ":a:buildSrc"]).parentId == op(BuildBuildSrcBuildOperationType.Details, [buildPath: ':a']).id
//
//        op(EvaluateSettingsBuildOperationType.Details, [buildPath: ":"]).parentId == op(LoadBuildBuildOperationType.Details, [buildPath: ":"]).id
//        op(EvaluateSettingsBuildOperationType.Details, [buildPath: ":a"]).parentId == op(LoadBuildBuildOperationType.Details, [buildPath: ":a"]).id
//        op(EvaluateSettingsBuildOperationType.Details, [buildPath: ":buildSrc"]).parentId == op(LoadBuildBuildOperationType.Details, [buildPath: ":buildSrc"]).id
//        op(EvaluateSettingsBuildOperationType.Details, [buildPath: ":a:buildSrc"]).parentId == op(LoadBuildBuildOperationType.Details, [buildPath: ":a:buildSrc"]).id
//
//        op(ConfigureBuildBuildOperationType.Details, [buildPath: ":"]).parentId == null // Run build build operation is not typed -> no id
//        op(ConfigureBuildBuildOperationType.Details, [buildPath: ":a"]).parentId == null // Run build build operation is not typed -> no id
//        op(ConfigureBuildBuildOperationType.Details, [buildPath: ":buildSrc"]).parentId == op(BuildBuildSrcBuildOperationType.Details, [buildPath: ':']).id
//        op(ConfigureBuildBuildOperationType.Details, [buildPath: ":a:buildSrc"]).parentId == op(BuildBuildSrcBuildOperationType.Details, [buildPath: ':a']).id
//
//        op(LoadProjectsBuildOperationType.Details, [buildPath: ":"]).parentId == op(ConfigureBuildBuildOperationType.Details, [buildPath: ":"]).id
//        op(LoadProjectsBuildOperationType.Details, [buildPath: ":a"]).parentId == op(ConfigureBuildBuildOperationType.Details, [buildPath: ":a"]).id
//        op(LoadProjectsBuildOperationType.Details, [buildPath: ":buildSrc"]).parentId == op(ConfigureBuildBuildOperationType.Details, [buildPath: ":buildSrc"]).id
//        op(LoadProjectsBuildOperationType.Details, [buildPath: ":a:buildSrc"]).parentId == op(ConfigureBuildBuildOperationType.Details, [buildPath: ":a:buildSrc"]).id
//    }

    def "build operations for composite builds are fired and build path is exposed"() {
        buildFile << """
            apply plugin:'java'
            
            dependencies {
                compile 'org.acme:nested:+'
            }
        """
        def nestedSettings = file("nested/settings.gradle")
        nestedSettings.text = "rootProject.name = 'nested'"
        file("nested/build.gradle").text = """
            apply plugin: 'java'
            group = 'org.acme'
        """
        when:
        succeeds('build', '--include-build', 'nested')

        def loadBuildBuildOperations = buildOperations.all(LoadBuildBuildOperationType)
        def evaluateSettingsBuildOperations = buildOperations.all(EvaluateSettingsBuildOperationType)
        def configureBuildBuildOperations = buildOperations.all(ConfigureBuildBuildOperationType)
        def loadProjectsBuildOperations = buildOperations.all(LoadProjectsBuildOperationType)

        then:
        loadBuildBuildOperations*.details.buildPath == [':nested', ':']
        loadBuildBuildOperations*.result.isEmpty() == [true, true]

        evaluateSettingsBuildOperations*.details.buildPath == [':nested', ':']
        evaluateSettingsBuildOperations*.result.isEmpty() == [true, true]

        configureBuildBuildOperations*.details.buildPath == [':nested', ':']
        configureBuildBuildOperations*.result.isEmpty() == [true, true]

        loadProjectsBuildOperations*.details.buildPath == [':nested', ':']
        loadProjectsBuildOperations*.result.rootProject.projectDir == [nestedSettings.parent, settingsFile.parent]
    }

}
