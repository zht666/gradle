/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.file

import org.gradle.api.internal.file.collections.ImmutableFileCollection
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.util.TextUtil
import spock.lang.Unroll

class ProjectLayoutIntegrationTest extends AbstractIntegrationSpec {
    private static final String STRING_CALLABLE = 'new java.util.concurrent.Callable<String>() { String call() { return "%s/src/resource/file.txt" } }'

    def "can access the project dir and build dir"() {
        buildFile << """
            println "project dir: " + layout.projectDirectory.asFile
            def b = layout.buildDirectory
            println "build dir: " + b.get()
            buildDir = "output"
            println "build dir 2: " + b.get()
"""

        when:
        run()

        then:
        outputContains("project dir: " + testDirectory)
        outputContains("build dir: " + testDirectory.file("build"))
        outputContains("build dir 2: " + testDirectory.file("output"))
    }

    def "layout is available for injection"() {
        buildFile << """
            import javax.inject.Inject
            
            class SomeTask extends DefaultTask {
                @Inject
                ProjectLayout getLayout() { null }
                
                @TaskAction
                void go() {
                    println "task build dir: " + layout.buildDirectory.get() 
                }
            }
            
            class SomePlugin implements Plugin<Project> {
                @Inject SomePlugin(ProjectLayout layout) {
                    println "plugin build dir: " + layout.buildDirectory.get()
                }
                
                void apply(Project p) {
                    p.tasks.create("show", SomeTask)
                }
            }
            
            apply plugin: SomePlugin
            buildDir = "output"
"""

        when:
        run("show")

        then:
        outputContains("plugin build dir: " + testDirectory.file("build"))
        outputContains("task build dir: " + testDirectory.file("output"))
    }

    def "can define and resolve calculated directory relative to project and build directory"() {
        buildFile << """
            def childDirName = "child"
            def srcDir = layout.projectDir.dir("src").dir(providers.provider { childDirName })
            def outputDir = layout.buildDirectory.dir(providers.provider { childDirName })
            println "src dir 1: " + srcDir.get()
            println "output dir 1: " + outputDir.get()
            buildDir = "output/some-dir"
            childDirName = "other-child"
            println "src dir 2: " + srcDir.get()
            println "output dir 2: " + outputDir.get()
"""

        when:
        run()

        then:
        outputContains("src dir 1: " + testDirectory.file("src/child"))
        outputContains("output dir 1: " + testDirectory.file("build/child"))
        outputContains("src dir 2: " + testDirectory.file("src/other-child"))
        outputContains("output dir 2: " + testDirectory.file("output/some-dir/other-child"))
    }

    def "can define and resolve calculated file relative to project and build directory"() {
        buildFile << """
            def childDirName = "child"
            def srcFile = layout.projectDir.dir("src").file(providers.provider { childDirName })
            def outputFile = layout.buildDirectory.file(providers.provider { childDirName })
            println "src file 1: " + srcFile.get()
            println "output file 1: " + outputFile.get()
            buildDir = "output/some-dir"
            childDirName = "other-child"
            println "src file 2: " + srcFile.get()
            println "output file 2: " + outputFile.get()
"""

        when:
        run()

        then:
        outputContains("src file 1: " + testDirectory.file("src/child"))
        outputContains("output file 1: " + testDirectory.file("build/child"))
        outputContains("src file 2: " + testDirectory.file("src/other-child"))
        outputContains("output file 2: " + testDirectory.file("output/some-dir/other-child"))
    }

    def "can use file() method to resolve locations created relative to the project dir and build dir"() {
        buildFile << """
            def location = $expression
            println "location: " + file(location)
"""

        when:
        run()

        then:
        outputContains("location: " + testDirectory.file(resolvesTo))

        where:
        expression                                                             | resolvesTo
        "layout.projectDirectory.dir('src/main/java')"                         | "src/main/java"
        "layout.projectDirectory.dir(providers.provider { 'src/main/java' })"  | "src/main/java"
        "layout.projectDirectory.file('src/main/java')"                        | "src/main/java"
        "layout.projectDirectory.file(providers.provider { 'src/main/java' })" | "src/main/java"
        "layout.buildDirectory.dir('classes/main')"                            | "build/classes/main"
        "layout.buildDirectory.dir(providers.provider { 'classes/main' })"     | "build/classes/main"
        "layout.buildDirectory.file('classes/main')"                           | "build/classes/main"
        "layout.buildDirectory.file(providers.provider { 'classes/main' })"    | "build/classes/main"
    }

    def "can construct file collection containing locations created relative to the project dir and build dir"() {
        buildFile << """
            def l = $expression
            def c = files(l)
            println "files 1: " + c.files
            buildDir = 'output'
            println "files 2: " + c.files
"""

        when:
        run()

        then:
        outputContains("files 1: [" + testDirectory.file(resolvesTo1) + "]")
        outputContains("files 2: [" + testDirectory.file(resolvesTo2) + "]")

        where:
        expression                                   | resolvesTo1          | resolvesTo2
        "layout.buildDirectory.dir('classes/main')"  | "build/classes/main" | "output/classes/main"
        "layout.buildDirectory.file('exe/main.exe')" | "build/exe/main.exe" | "output/exe/main.exe"
    }

    @Unroll
    def 'can create empty #collectionType'() {
        given:
        buildFile << """
            def fileCollection = $expression
            println("size = \${fileCollection.files.size()}")
        """

        when:
        run()

        then:
        outputContains('size = 0')

        where:
        collectionType               | expression
        'FileCollection'             | 'project.layout.filesFor()'
        'ConfigurableFileCollection' | 'project.layout.mutableFilesFor()'
    }

    @Unroll
    def 'can create #collectionType containing #content'() {
        given:
        file('src/resource/file.txt') << "some text"

        buildFile << """
            def fileCollection = ${TextUtil.normaliseFileSeparators(String.format(expressionTemplate, testDirectory.absolutePath))}
            println("size = \${fileCollection.files.size()}")
        """

        when:
        run()

        then:
        outputContains('size = 1')

        where:
        collectionType               | content          | expressionTemplate
        'FileCollection'             | 'String'         | 'project.layout.filesFor("%s/src/resource/file.txt")'
        'FileCollection'             | 'File'           | 'project.layout.filesFor(new File("%s", "src/resource/file.txt"))'
        'FileCollection'             | 'Path'           | 'project.layout.filesFor(java.nio.file.Paths.get("%s/src/resource/file.txt"))'
        'FileCollection'             | 'URI'            | 'project.layout.filesFor(new File("%s", "/src/resource/file.txt").toURI())'
        'FileCollection'             | 'URL'            | 'project.layout.filesFor(new File("%s", "/src/resource/file.txt").toURI().toURL())'
        'FileCollection'             | 'Directory'      | 'project.layout.filesFor(project.layout.projectDirectory)'
        'FileCollection'             | 'RegularFile'    | 'project.layout.filesFor(project.layout.projectDirectory.file("src/resource/file.txt"))'
        'FileCollection'             | 'Closure'        | 'project.layout.filesFor({ "%s/src/resource/file.txt" })'
        'FileCollection'             | 'List'           | 'project.layout.filesFor([ "%s/src/resource/file.txt" ])'
        'FileCollection'             | 'array'          | 'project.layout.filesFor([ "%s/src/resource/file.txt" ] as Object[])'
        'FileCollection'             | 'FileCollection' | "project.layout.filesFor(${ImmutableFileCollection.name}.of(new File('%s/src/resource/file.txt')))"
        'FileCollection'             | 'Callable'       | "project.layout.filesFor($STRING_CALLABLE)"
        'FileCollection'             | 'Provider'       | "project.layout.filesFor(provider($STRING_CALLABLE))"
        'FileCollection'             | 'nested objects' | "project.layout.filesFor({[{$STRING_CALLABLE}]})"

        'ConfigurableFileCollection' | 'String'         | 'project.layout.mutableFilesFor("%s/src/resource/file.txt")'
        'ConfigurableFileCollection' | 'File'           | 'project.layout.mutableFilesFor(new File("%s", "src/resource/file.txt"))'
        'ConfigurableFileCollection' | 'Path'           | 'project.layout.mutableFilesFor(java.nio.file.Paths.get("%s/src/resource/file.txt"))'
        'ConfigurableFileCollection' | 'URI'            | 'project.layout.mutableFilesFor(new File("%s", "/src/resource/file.txt").toURI())'
        'ConfigurableFileCollection' | 'URL'            | 'project.layout.mutableFilesFor(new File("%s", "/src/resource/file.txt").toURI().toURL())'
        'ConfigurableFileCollection' | 'Directory'      | 'project.layout.mutableFilesFor(project.layout.projectDirectory)'
        'ConfigurableFileCollection' | 'RegularFile'    | 'project.layout.mutableFilesFor(project.layout.projectDirectory.file("src/resource/file.txt"))'
        'ConfigurableFileCollection' | 'Closure'        | 'project.layout.mutableFilesFor({ "%s/src/resource/file.txt" })'
        'ConfigurableFileCollection' | 'List'           | 'project.layout.mutableFilesFor([ "%s/src/resource/file.txt" ])'
        'ConfigurableFileCollection' | 'array'          | 'project.layout.mutableFilesFor([ "%s/src/resource/file.txt" ] as Object[])'
        'ConfigurableFileCollection' | 'FileCollection' | "project.layout.mutableFilesFor(${ImmutableFileCollection.name}.of(new File('%s/src/resource/file.txt')))"
        'ConfigurableFileCollection' | 'Callable'       | "project.layout.mutableFilesFor($STRING_CALLABLE)"
        'ConfigurableFileCollection' | 'Provider'       | "project.layout.mutableFilesFor(provider($STRING_CALLABLE))"
        'ConfigurableFileCollection' | 'nested objects' | "project.layout.mutableFilesFor({[{$STRING_CALLABLE}]})"
    }

    @Unroll
    def 'can create #collectionType with #dependencyType dependency'() {
        buildFile << """
            task myTask {
                def outputFile = new File('${TextUtil.normaliseFileSeparators(testDirectory.absolutePath)}', 'build/resource/file.txt')
                doLast {
                    outputFile.text = "some text"
                }
                outputs.file outputFile
            }

            def fileCollection = $expression
            println("files = \${fileCollection.files}")
        """

        when:
        run('myTask')

        then:
        outputContains("files = [${new File(testDirectory.absolutePath , '/build/resource/file.txt').absolutePath}]")

        where:
        collectionType               | dependencyType | expression
        'FileCollection'             | 'Task'         | 'project.layout.filesFor(project.tasks.myTask)'
        'FileCollection'             | 'TaskOutputs'  | 'project.layout.filesFor(project.tasks.myTask.outputs)'
        'ConfigurableFileCollection' | 'Task'         | 'project.layout.mutableFilesFor(project.tasks.myTask)'
        'ConfigurableFileCollection' | 'TaskOutputs'  | 'project.layout.mutableFilesFor(project.tasks.myTask.outputs)'
    }

    @Unroll
    def '#methodName enforces build dependencies when given Task as input'() {
        buildFile << """
            task producer {
                def outputFile = new File('${TextUtil.normaliseFileSeparators(testDirectory.absolutePath)}', 'build/resource/file.txt')
                outputs.file outputFile
                doLast {
                    outputFile.text = "some text"
                }
            }

            task consumer {
                def fileCollection = project.layout.$methodName(project.tasks.producer)
                inputs.files fileCollection
                doLast {
                    println("files = \${fileCollection.files}")
                }
            }
        """

        when:
        run('consumer')

        then:
        executed(':producer', ':consumer')
        outputContains("files = [${new File(testDirectory.absolutePath, '/build/resource/file.txt').absolutePath}]")

        where:
        methodName << ['filesFor', 'mutableFilesFor']
    }

    @Unroll
    def 'can create #collectionType with Configuration dependency'() {
        file('src/resource/file.txt') << "some text"
        buildFile << """
            configurations {
                other
            }

            dependencies {
                other files("${TextUtil.normaliseFileSeparators(testDirectory.absolutePath)}/src/resource/file.txt")
            }

            def fileCollection = $expression
            println("files = \${fileCollection.files}")
        """

        when:
        run()

        then:
        outputContains("files = [${new File(testDirectory.absolutePath, '/src/resource/file.txt').absolutePath}]")

        where:
        collectionType               | expression
        'FileCollection'             | 'project.layout.filesFor(configurations.other)'
        'ConfigurableFileCollection' | 'project.layout.mutableFilesFor(configurations.other)'
    }

    @Unroll
    def 'fails to resolve #collectionType with null element'() {
        buildFile << """
            def fileCollection = $expression
            println("size = \${fileCollection.files.size()}")
        """

        expect:
        fails('help')
        errorOutput.contains('java.lang.NullPointerException')

        where:
        collectionType               | expression
        'FileCollection (Object...)' | 'project.layout.filesFor((Object) null)'
        'FileCollection (File...)'   | 'project.layout.filesFor((File) null)'
        'ConfigurableFileCollection' | 'project.layout.mutableFilesFor(null)'
    }
}
