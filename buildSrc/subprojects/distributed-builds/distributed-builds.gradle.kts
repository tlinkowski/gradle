gradlePlugin {
    plugins {
        register("distributedBuild") {
            id = "gradlebuild.distributed-build"
            implementationClass = "org.gradle.gradlebuild.distributed.DistributedBuildPlugin"
        }
    }
}
