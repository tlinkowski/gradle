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

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.gradlebuild.distributed.model.BuildInvocation
import org.gradle.gradlebuild.distributed.model.Stage
import java.net.URL
import javax.inject.Inject


open class RunDistributedStageTask @Inject constructor(@Internal val stage: Stage) : DefaultTask() {

    @TaskAction
    fun runDistributed() {
        println("Executing stage on CI: ${stage.name} - ${stage.description}")

        stage.buildInvocation.forEach { invocation ->
            //FIXME expand for subprojects as done in plugin already
            triggerJob(invocation)
        }

        Thread.sleep(5000)
    }

    private
    fun triggerJob(invocation: BuildInvocation) {
        val commit = "d10e26a2dde145" //TODO get from git and check that working copy is not dirty
        val jobURL = "http://localhost:8080/job/GradleWorker" //TODO job for environment e.g. 'GradleWorkerOpenJDK11'

        val getJobNumber = URL("$jobURL/lastBuild/buildNumber")
        val trigger = URL("$jobURL/buildWithParameters?tasks=${invocation.buildType.tasks.joinToString("%20")}&commit=$commit")

        println("Starting - $trigger")

        /*val lastBuild = getJobNumber.readText().toInt()
        trigger.readText()
        var currentBuild = lastBuild
        while (currentBuild == lastBuild) {
            Thread.sleep(100)
            currentBuild = getJobNumber.readText().toInt()
        }

        val statusCheck = URL("$jobURL/$currentBuild/api/xml?depth=0")
        while (statusCheck.readText().contains("<building>true</building>")) {
            Thread.sleep(500)
        }*/

        Thread.sleep(2000)

        //FIXME split starting and wait for finish
        println("Finished - ${invocation.buildType.name} on ${invocation.buildEnvironment.os} - https://scans.gradle.com/s/x1x1x13x4xx4")
    }
}
