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

import org.gradle.gradlebuild.distributed.model.BuildEnvironment
import org.gradle.gradlebuild.distributed.model.BuildInvocation
import org.gradle.gradlebuild.distributed.model.BuildType
import org.gradle.gradlebuild.distributed.model.DistributionPipeline
import org.gradle.gradlebuild.distributed.model.Stage


object DefaultPipeline {
    private
    val linuxJava11 = BuildEnvironment("Linux", "Open JDK 11")
    private
    val linuxJava8 = BuildEnvironment("Linux", "Open JDK 8")
    private
    val windowsJava8 = BuildEnvironment("Windows 7", "Open JDK 8")

    private
    val compileAll = BuildType("compileAllBuild", listOf(":createBuildReceipt", "compileAll"),
        environmentSpecific = false)
    private
    val sanityCheck = BuildType("sanityCheck", listOf("codeQuality", ":docs:checkstyleApi", ":allIncubationReportsZip",
        ":docs:check", ":distributions:checkBinaryCompatibility", ":docs:javadocAll", ":architectureTest:test"),
        environmentSpecific = false)
    private
    val quickTest = BuildType("quickTest", listOf("integTest", "crossVersionTest"))

    private
    val compile = Stage("Compile", "Compile all code as preparation for everything else", listOf(
        BuildInvocation(compileAll, linuxJava11)
    ))
    private
    val quickFeedback = Stage("Quick Feedback", "Run checks and functional tests (embedded executer, Linux)", listOf(
        BuildInvocation(sanityCheck, linuxJava11),
        BuildInvocation(quickTest, linuxJava11)
    ), dependencies = listOf(compile))
    private
    val readyForMerge = Stage("Ready For Merge", "Run performance and functional tests (against distribution)", listOf(
        BuildInvocation(quickTest, windowsJava8)
    ), dependencies = listOf(quickFeedback))

    val pipeline = DistributionPipeline(listOf(readyForMerge))
}
