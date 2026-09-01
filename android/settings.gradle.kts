pluginManagement {
    repositories {
        // 阿里云镜像放在最前面：国内直连 dl.google.com 经常解析不到插件。
        // 如果你有稳定的代理、想用官方源，把下面三行注释掉即可（后面的 google() /
        // mavenCentral() / gradlePluginPortal() 就是官方源）。
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/public")
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/public")
        google()
        mavenCentral()
    }
}

rootProject.name = "COPDTrendDemo"
include(":app")
