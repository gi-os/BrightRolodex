pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}

/**
 * No GitHub Packages repository here, unlike the rest of the family.
 *
 * The other Bright* apps pull `com.gios:light-common` for the wheel, shake-to-report and the
 * LightSync backup provider. GitHub Packages has no anonymous read even for a public package,
 * and a *new* repository's own `GITHUB_TOKEN` is not granted access to a package published
 * from a different repository — so the first CI run of a fresh repo fails on
 * "Could not find com.gios:light-common", which reads exactly like a credentials bug and is
 * not one.
 *
 * So this app vendors the ~120 lines of wheel handling it actually needs (`hw/`) and does
 * without the reporter and the backup provider until the repository has GPR_USER / GPR_TOKEN
 * secrets. Everything resolves from Google and Maven Central.
 */
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "BrightRolodex"
include(":app")
