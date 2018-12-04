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

package org.gradle.api.internal.artifacts.transform

import com.google.common.collect.ImmutableCollection
import org.gradle.api.Action
import org.gradle.api.Task
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvableArtifact
import org.gradle.api.internal.tasks.TaskDependencyContainer
import org.gradle.execution.plan.Node
import org.gradle.execution.plan.TaskDependencyResolver
import org.jetbrains.annotations.NotNull
import spock.lang.Specification

class TransformationNodeSpec extends Specification {

    def artifact = Mock(ResolvableArtifact)
    def dependencyResolver = Mock(TaskDependencyResolver)
    def artifactNode = new TestNode()
    def hardSuccessor = Mock(Action)
    def transformationStep = Mock(TransformationStep)
    def graphDependenciesResolver = Mock(ExecutionGraphDependenciesResolver)

    def "initial node adds dependency on artifact node and dependencies"() {
        def container = Stub(TaskDependencyContainer)
        def additionalNode = new TestNode()

        given:
        def node = TransformationNode.initial(transformationStep, artifact, Stub(ArtifactTransformDependenciesProvider), graphDependenciesResolver)

        when:
        node.resolveDependencies(dependencyResolver, hardSuccessor)

        then:
        1 * dependencyResolver.resolveDependenciesFor(null, artifact) >> [artifactNode]
        1 * hardSuccessor.execute(artifactNode)
        1 * graphDependenciesResolver.computeDependencyNodes(transformationStep) >> container
        1 * dependencyResolver.resolveDependenciesFor(null, container) >> [additionalNode]
        1 * hardSuccessor.execute(additionalNode)
        0 * hardSuccessor._
    }

    def "chained node with empty extra resolver only adds dependency on previous step and dependencies"() {
        def container = Stub(TaskDependencyContainer)
        def additionalNode = new TestNode()
        def initialNode = TransformationNode.initial(Stub(TransformationStep), artifact, Stub(ArtifactTransformDependenciesProvider), Stub(ExecutionGraphDependenciesResolver))

        given:
        def node = TransformationNode.chained(transformationStep, initialNode, Stub(ArtifactTransformDependenciesProvider), graphDependenciesResolver)

        when:
        node.resolveDependencies(dependencyResolver, hardSuccessor)

        then:
        1 * hardSuccessor.execute(initialNode)
        1 * graphDependenciesResolver.computeDependencyNodes(transformationStep) >> container
        1 * dependencyResolver.resolveDependenciesFor(null, container) >> [additionalNode]
        1 * hardSuccessor.execute(additionalNode)
        0 * hardSuccessor._
    }

    class TestNode extends Node {

        @Override
        void collectTaskInto(ImmutableCollection.Builder<Task> builder) {

        }

        @Override
        Throwable getNodeFailure() {
            return null
        }

        @Override
        void rethrowNodeFailure() {

        }

        @Override
        void prepareForExecution() {

        }

        @Override
        void resolveDependencies(TaskDependencyResolver dependencyResolver, Action<Node> processHardSuccessor) {

        }

        @Override
        Set<Node> getFinalizers() {
            return null
        }

        @Override
        String toString() {
            return null
        }

        @Override
        int compareTo(@NotNull Node o) {
            return 0
        }
    }
}
