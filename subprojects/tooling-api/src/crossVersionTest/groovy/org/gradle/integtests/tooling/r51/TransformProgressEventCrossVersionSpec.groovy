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

package org.gradle.integtests.tooling.r51


import org.gradle.integtests.tooling.fixture.ProgressEvents
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.test.fixtures.file.TestFile
import org.gradle.tooling.BuildException
import org.gradle.tooling.events.OperationType

@ToolingApiVersion('>=5.1')
@TargetGradleVersion('>=5.1')
class TransformProgressEventCrossVersionSpec extends ToolingApiSpecification {

    def events = ProgressEvents.create()

    void setup() {
        settingsFile << """
            include 'lib', 'app'
        """
        buildFile << """
            def artifactType = Attribute.of('artifactType', String)
            subprojects {
                apply plugin: 'java'
            }
            project(":app") {
                dependencies {
                    compile project(":lib")
                }
            }
        """
    }

    def "reports successful transform progress events"() {
        given:
        withFileSizerTransform()

        when:
        runBuild("resolve")

        then:
        def transformOperation = events.operation("Transform artifact lib.jar (project :lib) with FileSizer")
        with(transformOperation) {
            transform
            descriptor.transformer.displayName == "FileSizer"
            descriptor.subject.displayName == "artifact lib.jar (project :lib)"
            successful
        }
        with(events.operation("Task :app:resolve")) {
            task
            successful
            descriptor.dependencies == [transformOperation.descriptor] as Set
        }
    }

    def "reports failed transform progress events"() {
        given:
        buildFile << """
            ${getBrokenTransform('intentional failure')}
            project(":app") {
                ${registerTransform('BrokenTransform')}
                ${createResolveTask()}
            }
        """

        when:
        runBuild("resolve")

        then:
        thrown(BuildException)
        with(events.operation("Transform artifact lib.jar (project :lib) with BrokenTransform")) {
            transform
            descriptor.transformer.displayName == "BrokenTransform"
            descriptor.subject.displayName == "artifact lib.jar (project :lib)"
            !successful
            failures.size() == 1
            failures[0].description.contains("intentional failure")
        }
    }

    def "does not report transform progress events when TRANSFORM operations are not requested"() {
        given:
        withFileSizerTransform()

        when:
        runBuild("resolve", EnumSet.complementOf(EnumSet.of(OperationType.TRANSFORM)))

        then:
        events.operations.findAll { it.transform }.empty
    }

    def "reports dependencies of transform operations"() {
        given:
        buildFile << """
            $fileSizer
            $fileNamer
            project(":app") {
                ${registerTransform('FileSizer', 'jar', 'size')}
                ${registerTransform('FileNamer', 'size', 'name')}
                ${createResolveTask('name')}
            }
        """

        when:
        runBuild("resolve")

        then:
        def taskOperation = events.operation("Task :lib:jar")
        def firstTransformOperation = events.operation("Transform artifact lib.jar (project :lib) with FileSizer")
        with(firstTransformOperation) {
            transform
            descriptor.transformer.displayName == "FileSizer"
            descriptor.subject.displayName == "artifact lib.jar (project :lib)"
            descriptor.dependencies == [taskOperation.descriptor] as Set
            successful
        }
        def secondTransformOperation = events.operation("Transform artifact lib.jar (project :lib) with FileNamer")
        with(secondTransformOperation) {
            transform
            descriptor.transformer.displayName == "FileNamer"
            descriptor.subject.displayName == "artifact lib.jar (project :lib)"
            descriptor.dependencies == [firstTransformOperation.descriptor] as Set
            successful
        }
    }

    private TestFile withFileSizerTransform() {
        buildFile << """
            $fileSizer
            project(":app") {
                ${registerTransform('FileSizer')}
                ${createResolveTask()}
            }
        """
    }

    private static String getFileSizer() {
        """
            class FileSizer extends ArtifactTransform {
                List<File> transform(File input) {
                    def output = new File(outputDirectory, input.name + ".txt")
                    output.text = String.valueOf(input.length())
                    return [output]
                }
            }
        """
    }

    def getFileNamer() {
        """
            class FileNamer extends ArtifactTransform {
                List<File> transform(File input) {
                    def output = new File(outputDirectory, input.name + ".txt")
                    output.text = String.valueOf(input.name)
                    return [output]
                }
            }
        """
    }

    def getBrokenTransform(String message) {
        """
            class BrokenTransform extends ArtifactTransform {
                List<File> transform(File input) {
                    throw new GradleException("$message")
                }
            }
        """
    }

    def registerTransform(String transformImplementation, String from = "jar", String to = "size") {
        """
            dependencies {
                registerTransform {
                    from.attribute(artifactType, '$from')
                    to.attribute(artifactType, '$to')
                    artifactTransform($transformImplementation)
                }
            }
        """
    }

    def createResolveTask(String attribute = "size") {
        """
            task resolve(type: Copy) {
                from configurations.compile.incoming.artifactView {
                    attributes { it.attribute(artifactType, '$attribute') }
                }.artifacts.artifactFiles
                into "\$buildDir/libs"
            }
        """
    }

    private Object runBuild(String task, Set<OperationType> operationTypes = EnumSet.allOf(OperationType)) {
        withConnection {
            newBuild()
                .forTasks(task)
                .addProgressListener(events, operationTypes)
                .run()
        }
    }

}
