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

package org.gradle.api.publish.maven

import org.gradle.integtests.fixtures.publish.maven.AbstractMavenPublishIntegTest

class MavenGradleModuleMetadataPublishIntegrationTest extends AbstractMavenPublishIntegTest {
    def setup() {
        buildFile << """
// TODO - use public APIs when available
class TestComponent implements org.gradle.api.internal.component.SoftwareComponentInternal, ComponentWithVariants {
    String name
    Set usages = []
    Set variants = []
}

class TestUsage implements org.gradle.api.internal.component.UsageContext {
    String name
    Usage usage
    Set dependencies = []
    Set dependencyConstraints = []
    Set artifacts = []
    Set capabilities = []
    Set globalExcludes = []
    AttributeContainer attributes
}

class TestVariant implements org.gradle.api.internal.component.SoftwareComponentInternal {
    String name
    Set usages = []
}

class TestCapability implements Capability {
    String group
    String name
    String version
}

    allprojects {
        configurations { implementation }
    }
"""
    }

    def "generates metadata for component with no variants"() {
        given:
        settingsFile << "rootProject.name = 'root'"
        buildFile << """
            apply plugin: 'maven-publish'

            group = 'group'
            version = '1.0'

            def comp = new TestComponent()

            publishing {
                repositories {
                    maven { url "${mavenRepo.uri}" }
                }
                publications {
                    maven(MavenPublication) {
                        from comp
                    }
                }
            }
        """

        when:
        succeeds 'publish'

        then:
        def module = mavenRepo.module('group', 'root', '1.0')
        module.assertPublished()
        module.parsedModuleMetadata.variants.empty
    }

    def "maps project dependencies"() {
        given:
        settingsFile << """rootProject.name = 'root'
            include 'a', 'b'
"""
        buildFile << """
            allprojects {
                apply plugin: 'maven-publish'
    
                group = 'group'
                version = '1.0'

                publishing {
                    repositories {
                        maven { url "${mavenRepo.uri}" }
                    }
                }
            }

            def comp = new TestComponent()
            comp.usages.add(new TestUsage(
                    name: 'api',
                    usage: objects.named(Usage, 'api'), 
                    dependencies: configurations.implementation.allDependencies, 
                    attributes: configurations.implementation.attributes))

            dependencies {
                implementation project(':a')
                implementation project(':b')
            }

            publishing {
                publications {
                    maven(MavenPublication) {
                        from comp
                    }
                }
            }
            
            project(':a') {
                publishing {
                    publications {
                        maven(MavenPublication) {
                            groupId = 'group.a' 
                            artifactId = 'lib_a'
                            version = '4.5'
                        }
                    }
                }
            }
            project(':b') {
                publishing {
                    publications {
                        maven(MavenPublication) {
                            groupId = 'group.b' 
                            artifactId = 'utils'
                            version = '0.01'
                        }
                    }
                }
            }
        """

        when:
        succeeds 'publish'

        then:
        def module = mavenRepo.module('group', 'root', '1.0')
        module.assertPublished()
        module.parsedModuleMetadata.variants.size() == 1
        def api = module.parsedModuleMetadata.variant('api')
        api.dependencies[0].coords == 'group.a:lib_a:4.5'
        api.dependencies[1].coords == 'group.b:utils:0.01'
    }

    def "publishes component with strict and prefer dependencies"() {
        settingsFile << "rootProject.name = 'root'"
        buildFile << """
            apply plugin: 'maven-publish'

            group = 'group'
            version = '1.0'

            def comp = new TestComponent()
            comp.usages.add(new TestUsage(
                    name: 'api',
                    usage: objects.named(Usage, 'api'), 
                    dependencies: configurations.implementation.allDependencies, 
                    attributes: configurations.implementation.attributes))

            dependencies {
                implementation("org:foo") {
                    version {
                        strictly '1.0'
                    }
                }
                implementation("org:bar") {
                    version {
                        prefer '2.0'
                    }
                }
                implementation("org:baz:3.0")
            }

            publishing {
                repositories {
                    maven { url "${mavenRepo.uri}" }
                }
                publications {
                    maven(MavenPublication) {
                        from comp
                    }
                }
            }
        """

        when:
        succeeds 'publish'

        then:
        def module = mavenRepo.module('group', 'root', '1.0')
        module.assertPublished()
        module.parsedModuleMetadata.variants.size() == 1
        def variant = module.parsedModuleMetadata.variants[0]
        variant.dependencies.size() == 3

        variant.dependencies[0].group == 'org'
        variant.dependencies[0].module == 'foo'
        variant.dependencies[0].version == '1.0'
        variant.dependencies[0].prefers == null
        variant.dependencies[0].strictly == '1.0'
        variant.dependencies[0].rejectsVersion == []

        variant.dependencies[1].group == 'org'
        variant.dependencies[1].module == 'bar'
        variant.dependencies[1].version == null
        variant.dependencies[1].prefers == '2.0'
        variant.dependencies[1].strictly == null
        variant.dependencies[1].rejectsVersion == []

        variant.dependencies[2].group == 'org'
        variant.dependencies[2].module == 'baz'
        variant.dependencies[2].version == '3.0'
        variant.dependencies[2].prefers == null
        variant.dependencies[2].strictly == null
        variant.dependencies[2].rejectsVersion == []
    }

    def "publishes component with dependency constraints"() {
        settingsFile << "rootProject.name = 'root'"
        buildFile << """
            apply plugin: 'maven-publish'

            group = 'group'
            version = '1.0'

            def comp = new TestComponent()
            comp.usages.add(new TestUsage(
                    name: 'api',
                    usage: objects.named(Usage, 'api'), 
                    dependencies: configurations.implementation.allDependencies.withType(ModuleDependency),
                    dependencyConstraints: configurations.implementation.allDependencyConstraints,
                    attributes: configurations.implementation.attributes))

            dependencies {
                constraints {
                    implementation("org:foo:1.0")
                }
            }

            publishing {
                repositories {
                    maven { url "${mavenRepo.uri}" }
                }
                publications {
                    maven(MavenPublication) {
                        from comp
                    }
                }
            }
        """

        when:
        succeeds 'publish'

        then:
        def module = mavenRepo.module('group', 'root', '1.0')
        module.assertPublished()
        module.parsedModuleMetadata.variants.size() == 1
        def variant = module.parsedModuleMetadata.variants[0]
        variant.dependencies.empty
        variant.dependencyConstraints.size() == 1

        variant.dependencyConstraints[0].group == 'org'
        variant.dependencyConstraints[0].module == 'foo'
        variant.dependencyConstraints[0].version == '1.0'
        variant.dependencyConstraints[0].rejectsVersion == []
    }

    def "publishes component with version rejects"() {
        settingsFile << "rootProject.name = 'root'"
        buildFile << """
            apply plugin: 'maven-publish'

            group = 'group'
            version = '1.0'

            def comp = new TestComponent()
            comp.usages.add(new TestUsage(
                    name: 'api',
                    usage: objects.named(Usage, 'api'), 
                    dependencies: configurations.implementation.allDependencies, 
                    attributes: configurations.implementation.attributes))

            dependencies {
                implementation("org:foo") {
                    version {
                        require '1.0'
                        reject '1.1', '[1.3,1.4]'
                    }
                }
                implementation("org:bar:2.0")
            }

            publishing {
                repositories {
                    maven { url "${mavenRepo.uri}" }
                }
                publications {
                    maven(MavenPublication) {
                        from comp
                    }
                }
            }
        """

        when:
        succeeds 'publish'

        then:
        def module = mavenRepo.module('group', 'root', '1.0')
        module.assertPublished()
        module.parsedModuleMetadata.variants.size() == 1
        def variant = module.parsedModuleMetadata.variants[0]
        variant.dependencies.size() == 2

        variant.dependencies[0].group == 'org'
        variant.dependencies[0].module == 'foo'
        variant.dependencies[0].version == '1.0'
        variant.dependencies[0].rejectsVersion == ['1.1', '[1.3,1.4]']

        variant.dependencies[1].group == 'org'
        variant.dependencies[1].module == 'bar'
        variant.dependencies[1].version == '2.0'
        variant.dependencies[1].rejectsVersion == []
    }

    def "publishes dependency reasons"() {
        settingsFile << "rootProject.name = 'root'"
        buildFile << """
            apply plugin: 'maven-publish'

            group = 'group'
            version = '1.0'

            def comp = new TestComponent()
            comp.usages.add(new TestUsage(
                    name: 'api',
                    usage: objects.named(Usage, 'api'), 
                    dependencies: configurations.implementation.allDependencies.withType(ModuleDependency),
                    dependencyConstraints: configurations.implementation.allDependencyConstraints,
                    attributes: configurations.implementation.attributes))

            dependencies {
                implementation("org:foo:1.0") {
                   because 'version 1.0 is tested'                
                }
                constraints {                
                    implementation("org:bar:2.0") {
                        because 'because 2.0 is cool'
                    }
                }
            }

            publishing {
                repositories {
                    maven { url "${mavenRepo.uri}" }
                }
                publications {
                    maven(MavenPublication) {
                        from comp
                    }
                }
            }
        """

        when:
        succeeds 'publish'

        then:
        def module = mavenRepo.module('group', 'root', '1.0')
        module.assertPublished()
        module.parsedModuleMetadata.variant('api') {
            dependency('org:foo:1.0') {
                hasReason 'version 1.0 is tested'
            }
            constraint('org:bar:2.0') {
                hasReason 'because 2.0 is cool'
            }
            noMoreDependencies()
        }
    }

    def "publishes capabilities"() {
        settingsFile << "rootProject.name = 'root'"
        buildFile << """
            apply plugin: 'maven-publish'

            group = 'group'
            version = '1.0'

            def comp = new TestComponent()
            comp.usages.add(new TestUsage(
                    name: 'api',
                    usage: objects.named(Usage, 'api'), 
                    attributes: configurations.implementation.attributes,
                    capabilities: [new TestCapability(group:'org.test', name: 'test', version: '1')]))

            publishing {
                repositories {
                    maven { url "${mavenRepo.uri}" }
                }
                publications {
                    maven(MavenPublication) {
                        from comp
                    }
                }
            }
        """

        when:
        succeeds 'publish'

        then:
        def module = mavenRepo.module('group', 'root', '1.0')
        module.assertPublished()
        module.parsedModuleMetadata.variant('api') {
            capability('org.test', 'test', '1')
            noMoreCapabilities()
            noMoreDependencies()
        }
    }

    def "publishes dependency/constraint attributes"() {
        settingsFile << "rootProject.name = 'root'"
        buildFile << """
            apply plugin: 'maven-publish'

            group = 'group'
            version = '1.0'

            def comp = new TestComponent()
            def attr1 = Attribute.of('custom', String)
            def attr2 = Attribute.of('nice', Boolean)
            
            comp.usages.add(new TestUsage(
                    name: 'api',
                    usage: objects.named(Usage, 'api'), 
                    dependencies: configurations.implementation.allDependencies.withType(ModuleDependency),
                    dependencyConstraints: configurations.implementation.allDependencyConstraints,
                    attributes: configurations.implementation.attributes))

            dependencies {
                implementation("org:foo:1.0") {
                   attributes {
                      attribute(attr1, 'foo')
                   }
                }
                constraints {                
                    implementation("org:bar:2.0") {
                        attributes {
                           attribute(attr2, true)
                        }
                    }
                }
            }

            publishing {
                repositories {
                    maven { url "${mavenRepo.uri}" }
                }
                publications {
                    maven(MavenPublication) {
                        from comp
                    }
                }
            }
        """

        when:
        succeeds 'publish'

        then:
        def module = mavenRepo.module('group', 'root', '1.0')
        module.assertPublished()
        module.parsedModuleMetadata.variant('api') {
            dependency('org:foo:1.0') {
                hasAttribute('custom', 'foo')
            }
            constraint('org:bar:2.0') {
                hasAttribute('nice', true)
            }
            noMoreDependencies()
        }
    }
}
