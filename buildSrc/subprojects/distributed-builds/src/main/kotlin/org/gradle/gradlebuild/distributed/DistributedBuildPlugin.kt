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

package org.gradle.gradlebuild.distributed

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider
import org.gradle.gradlebuild.distributed.model.BuildInvocation
import org.gradle.gradlebuild.distributed.model.Stage
import org.gradle.kotlin.dsl.*


open class DistributedBuildPlugin : Plugin<Project> {

    override fun apply(project: Project): Unit = project.run {
        val pipeline = extensions.create("distributedBuild", DistributedBuildExtension::class, DefaultPipeline.pipeline).pipeline

        registerRunDistributedStageTasks(pipeline.stages)

        afterEvaluate {
            val startParameterTasks = gradle.startParameter.taskNames
            val stagesToExecute = pipeline.allStages().filter { startParameterTasks.contains(it.asTaskName()) }
            val additionalTasks = mutableListOf<String>()

            stagesToExecute.map { it.allStages() }.flatten().forEach { stage ->
                val stageTask = tasks.getByName(stage.asTaskName())
                stage.buildInvocation.forEach { invocation ->
                    invocation.buildType.tasks.forEach { taskName ->
                        if (taskName.contains(":")) {
                            val task = findTaskForInvocation(taskName, invocation)
                            task.dependsOn(stageTask)
                            additionalTasks.add(task.path)
                        } else {
                            subprojects.toList().subList(0, 2).forEach { subproject ->
                                if (subproject.tasks.findByName(taskName) != null) {
                                    val task = subproject.findTaskForInvocation(taskName, invocation)
                                    task.dependsOn(stageTask)
                                    additionalTasks.add(task.path)
                                }
                            }
                        }
                    }
                }
                gradle.startParameter.setTaskNames(startParameterTasks + additionalTasks)
            }
        }
    }

    private
    fun Project.findTaskForInvocation(taskName: String, invocation: BuildInvocation) = tasks.getByPath(taskName).let { baseTask ->
        if (invocation.buildType.environmentSpecific) {
            tasks.getByName(invocation.buildEnvironment.asEnvironmentSpecificTaskName(baseTask.name))
        } else {
            baseTask
        }
    }

    private
    fun Project.registerRunDistributedStageTasks(stages: List<Stage>, dependent: TaskProvider<RunDistributedStageTask>? = null) {
        stages.forEach { stage ->
            if (tasks.findByName(stage.asTaskName()) == null) {
                val task = tasks.register(stage.asTaskName(), RunDistributedStageTask::class, stage)
                dependent?.configure {
                    dependsOn(task)
                }
                registerRunDistributedStageTasks(stage.dependencies, task)
            }
        }
    }

    private
    fun Stage.asTaskName() = "runDistributedStage${name.replace(" ", "")}"
}
