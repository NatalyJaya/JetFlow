# CodeBundle: JetFlow
Generated: 2026-04-25T07:31:18.930Z
Root: /home/lluc/Documents/GitHub/JetFlow
Files: 26

## How to apply changes
- Return changes as **unified diffs** per file whenever possible.
- Files are delimited with `<!-- FILE: ... -->` markers.

## Project tree
```
├─ .github
│  ├─ dependabot.yml
│  └─ workflows
│     ├─ build.yml
│     ├─ release.yml
│     └─ run-ui-tests.yml
├─ .gitignore
├─ .run
│  ├─ Run Plugin.run.xml
│  ├─ Run Tests.run.xml
│  └─ Run Verifications.run.xml
├─ build.gradle.kts
├─ CHANGELOG.md
├─ gradle
│  └─ wrapper
│     └─ gradle-wrapper.properties
├─ gradle.properties
├─ gradlew
├─ gradlew.bat
├─ README.md
├─ settings.gradle.kts
└─ src
   ├─ main
   │  ├─ kotlin
   │  │  └─ com
   │  │     └─ github
   │  │        └─ natalyjaya
   │  │           └─ jetflo
   │  │              ├─ MyBundle.kt
   │  │              ├─ services
   │  │              │  └─ MyProjectService.kt
   │  │              ├─ startup
   │  │              │  └─ MyProjectActivity.kt
   │  │              ├─ toolWindow
   │  │              │  └─ MyToolWindowFactory.kt
   │  │              └─ ui
   │  │                 └─ RockyFloatingActivity.kt
   │  └─ resources
   │     ├─ messages
   │     │  └─ MyBundle.properties
   │     └─ META-INF
   │        └─ plugin.xml
   └─ test
      ├─ kotlin
      │  └─ com
      │     └─ github
      │        └─ natalyjaya
      │           └─ jetflo
      │              └─ MyPluginTest.kt
      └─ testData
         └─ rename
            ├─ foo_after.xml
            └─ foo.xml
```

## Files list

- `.github/dependabot.yml` (459 bytes)
- `.github/workflows/build.yml` (6369 bytes)
- `.github/workflows/release.yml` (3397 bytes)
- `.github/workflows/run-ui-tests.yml` (1770 bytes)
- `.gitignore` (56 bytes)
- `.run/Run Plugin.run.xml` (1063 bytes)
- `.run/Run Tests.run.xml` (1040 bytes)
- `.run/Run Verifications.run.xml` (1055 bytes)
- `build.gradle.kts` (2533 bytes)
- `CHANGELOG.md` (241 bytes)
- `gradle.properties` (518 bytes)
- `gradle/wrapper/gradle-wrapper.properties` (252 bytes)
- `gradlew` (8654 bytes)
- `gradlew.bat` (2803 bytes)
- `README.md` (3353 bytes)
- `settings.gradle.kts` (116 bytes)
- `src/main/kotlin/com/github/natalyjaya/jetflo/MyBundle.kt` (569 bytes)
- `src/main/kotlin/com/github/natalyjaya/jetflo/services/MyProjectService.kt` (600 bytes)
- `src/main/kotlin/com/github/natalyjaya/jetflo/startup/MyProjectActivity.kt` (446 bytes)
- `src/main/kotlin/com/github/natalyjaya/jetflo/toolWindow/MyToolWindowFactory.kt` (1645 bytes)
- `src/main/kotlin/com/github/natalyjaya/jetflo/ui/RockyFloatingActivity.kt` (8979 bytes)
- `src/main/resources/messages/MyBundle.properties` (90 bytes)
- `src/main/resources/META-INF/plugin.xml` (648 bytes)
- `src/test/kotlin/com/github/natalyjaya/jetflo/MyPluginTest.kt` (1278 bytes)
- `src/test/testData/rename/foo_after.xml` (32 bytes)
- `src/test/testData/rename/foo.xml` (39 bytes)

---
<!-- FILE: .github/dependabot.yml -->
## .github/dependabot.yml

```yaml
# Dependabot configuration:
# https://docs.github.com/en/free-pro-team@latest/github/administering-a-repository/configuration-options-for-dependency-updates

version: 2
updates:
  # Maintain dependencies for Gradle dependencies
  - package-ecosystem: "gradle"
    directory: "/"
    schedule:
      interval: "daily"
  # Maintain dependencies for GitHub Actions
  - package-ecosystem: "github-actions"
    directory: "/"
    schedule:
      interval: "daily"

```
<!-- END_FILE -->

---
<!-- FILE: .github/workflows/build.yml -->
## .github/workflows/build.yml

```yaml
# GitHub Actions Workflow is created for testing and preparing the plugin release in the following steps:
# - Validate Gradle Wrapper.
# - Run 'test' and 'verifyPlugin' tasks.
# - Run the 'buildPlugin' task and prepare artifact for further tests.
# - Run the 'runPluginVerifier' task.
# - Create a draft release.
#
# The workflow is triggered on push and pull_request events.
#
# GitHub Actions reference: https://help.github.com/en/actions
#
## JBIJPPTPL

name: Build
on:
  # Trigger the workflow on pushes to only the 'main' branch (this avoids duplicate checks being run e.g., for dependabot pull requests)
  push:
    branches: [ main ]
  # Trigger the workflow on any pull request
  pull_request:

concurrency:
  group: ${{ github.workflow }}-${{ github.event.pull_request.number || github.ref }}
  cancel-in-progress: true

jobs:

  # Prepare the environment and build the plugin
  build:
    name: Build
    runs-on: ubuntu-latest
    steps:

      # Free GitHub Actions Environment Disk Space
      - name: Maximize Build Space
        uses: jlumbroso/free-disk-space@v1.3.1
        with:
          tool-cache: false
          large-packages: false

      # Check out the current repository
      - name: Fetch Sources
        uses: actions/checkout@v6

      # Set up the Java environment for the next steps
      - name: Setup Java
        uses: actions/setup-java@v5
        with:
          distribution: zulu
          java-version: 21

      # Setup Gradle
      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v5

      # Build plugin
      - name: Build plugin
        run: ./gradlew buildPlugin

      # Prepare plugin archive content for creating artifact
      - name: Prepare Plugin Artifact
        id: artifact
        shell: bash
        run: |
          cd ${{ github.workspace }}/build/distributions
          FILENAME=`ls *.zip`
          unzip "$FILENAME" -d content

          echo "filename=${FILENAME:0:-4}" >> $GITHUB_OUTPUT

      # Store an already-built plugin as an artifact for downloading
      - name: Upload artifact
        uses: actions/upload-artifact@v6
        with:
          name: ${{ steps.artifact.outputs.filename }}
          path: ./build/distributions/content/*/*

  # Run tests and upload a code coverage report
  test:
    name: Test
    needs: [ build ]
    runs-on: ubuntu-latest
    steps:

      # Free GitHub Actions Environment Disk Space
      - name: Maximize Build Space
        uses: jlumbroso/free-disk-space@v1.3.1
        with:
          tool-cache: false
          large-packages: false

      # Check out the current repository
      - name: Fetch Sources
        uses: actions/checkout@v6

      # Set up the Java environment for the next steps
      - name: Setup Java
        uses: actions/setup-java@v5
        with:
          distribution: zulu
          java-version: 21

      # Setup Gradle
      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v5
        with:
          cache-read-only: true

      # Run tests
      - name: Run Tests
        run: ./gradlew check

      # Collect Tests Result of failed tests
      - name: Collect Tests Result
        if: ${{ failure() }}
        uses: actions/upload-artifact@v6
        with:
          name: tests-result
          path: ${{ github.workspace }}/build/reports/tests

  # Run plugin structure verification along with IntelliJ Plugin Verifier
  verify:
    name: Verify plugin
    needs: [ build ]
    runs-on: ubuntu-latest
    steps:

      # Free GitHub Actions Environment Disk Space
      - name: Maximize Build Space
        uses: jlumbroso/free-disk-space@v1.3.1
        with:
          tool-cache: false
          large-packages: false

      # Check out the current repository
      - name: Fetch Sources
        uses: actions/checkout@v6

      # Set up the Java environment for the next steps
      - name: Setup Java
        uses: actions/setup-java@v5
        with:
          distribution: zulu
          java-version: 21

      # Setup Gradle
      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v5
        with:
          cache-read-only: true

      # Run Verify Plugin task and IntelliJ Plugin Verifier tool
      - name: Run Plugin Verification tasks
        run: ./gradlew verifyPlugin

      # Collect Plugin Verifier Result
      - name: Collect Plugin Verifier Result
        if: ${{ always() }}
        uses: actions/upload-artifact@v6
        with:
          name: pluginVerifier-result
          path: ${{ github.workspace }}/build/reports/pluginVerifier

  # Prepare a draft release for GitHub Releases page for the manual verification
  # If accepted and published, the release workflow would be triggered
  releaseDraft:
    name: Release draft
    if: github.event_name != 'pull_request'
    needs: [ build, test, verify ]
    runs-on: ubuntu-latest
    permissions:
      contents: write
    steps:

      # Check out the current repository
      - name: Fetch Sources
        uses: actions/checkout@v6

      # Set up the Java environment for the next steps
      - name: Setup Java
        uses: actions/setup-java@v5
        with:
          distribution: zulu
          java-version: 21

      # Setup Gradle
      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v5
        with:
          cache-read-only: true

      # Remove old release drafts by using the curl request for the available releases with a draft flag
      - name: Remove Old Release Drafts
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
        run: |
          gh api repos/{owner}/{repo}/releases \
            --jq '.[] | select(.draft == true) | .id' \
            | xargs -r -I '{}' gh api -X DELETE repos/{owner}/{repo}/releases/{}

      # Create a new release draft which is not publicly visible and requires manual acceptance
      - name: Create Release Draft
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
        run: |
          VERSION=$(./gradlew properties --property version --quiet --console=plain | tail -n 1 | cut -f2- -d ' ')
          RELEASE_NOTE="./build/tmp/release_note.txt"
          ./gradlew getChangelog --unreleased --no-header --quiet --console=plain --output-file=$RELEASE_NOTE

          gh release create $VERSION \
            --draft \
            --title $VERSION \
            --notes-file $RELEASE_NOTE

```
<!-- END_FILE -->

---
<!-- FILE: .github/workflows/release.yml -->
## .github/workflows/release.yml

```yaml
# GitHub Actions Workflow created for handling the release process based on the draft release prepared with the Build workflow.
# Running the publishPlugin task requires all the following secrets to be provided: PUBLISH_TOKEN, PRIVATE_KEY, PRIVATE_KEY_PASSWORD, CERTIFICATE_CHAIN.
# See https://plugins.jetbrains.com/docs/intellij/plugin-signing.html for more information.

name: Release
on:
  release:
    types: [ prereleased, released ]

jobs:

  # Prepare and publish the plugin to JetBrains Marketplace repository
  release:
    name: Publish Plugin
    runs-on: ubuntu-latest
    permissions:
      contents: write
      pull-requests: write
    steps:

      # Free GitHub Actions Environment Disk Space
      - name: Maximize Build Space
        uses: jlumbroso/free-disk-space@v1.3.1
        with:
          tool-cache: false
          large-packages: false

      # Check out the current repository
      - name: Fetch Sources
        uses: actions/checkout@v6
        with:
          ref: ${{ github.event.release.tag_name }}

      # Set up the Java environment for the next steps
      - name: Setup Java
        uses: actions/setup-java@v5
        with:
          distribution: zulu
          java-version: 21

      # Setup Gradle
      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v5
        with:
          cache-read-only: true

      # Update the Unreleased section with the current release note
      - name: Patch Changelog
        if: ${{ github.event.release.body != '' }}
        env:
          CHANGELOG: ${{ github.event.release.body }}
        run: |
          RELEASE_NOTE="./build/tmp/release_note.txt"
          mkdir -p "$(dirname "$RELEASE_NOTE")"
          echo "$CHANGELOG" > $RELEASE_NOTE

          ./gradlew patchChangelog --release-note-file=$RELEASE_NOTE

      # Publish the plugin to JetBrains Marketplace
      - name: Publish Plugin
        env:
          PUBLISH_TOKEN: ${{ secrets.PUBLISH_TOKEN }}
          CERTIFICATE_CHAIN: ${{ secrets.CERTIFICATE_CHAIN }}
          PRIVATE_KEY: ${{ secrets.PRIVATE_KEY }}
          PRIVATE_KEY_PASSWORD: ${{ secrets.PRIVATE_KEY_PASSWORD }}
        run: ./gradlew publishPlugin

      # Upload an artifact as a release asset
      - name: Upload Release Asset
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
        run: gh release upload ${{ github.event.release.tag_name }} ./build/distributions/*

      # Create a pull request
      - name: Create Pull Request
        if: ${{ github.event.release.body != '' }}
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
        run: |
          VERSION="${{ github.event.release.tag_name }}"
          BRANCH="changelog-update-$VERSION"
          LABEL="release changelog"

          git config user.email "action@github.com"
          git config user.name "GitHub Action"

          git checkout -b $BRANCH
          git commit -am "Changelog update - $VERSION"
          git push --set-upstream origin $BRANCH

          gh label create "$LABEL" \
            --description "Pull requests with release changelog update" \
            --force \
            || true

          gh pr create \
            --title "Changelog update - \`$VERSION\`" \
            --body "Current pull request contains patched \`CHANGELOG.md\` file for the \`$VERSION\` version." \
            --label "$LABEL" \
            --head $BRANCH

```
<!-- END_FILE -->

---
<!-- FILE: .github/workflows/run-ui-tests.yml -->
## .github/workflows/run-ui-tests.yml

```yaml
# GitHub Actions Workflow for launching UI tests on Linux, Windows, and Mac in the following steps:
# - Prepare and launch IDE with your plugin and robot-server plugin, which is needed to interact with the UI.
# - Wait for IDE to start.
# - Run UI tests with a separate Gradle task.
#
# Please check https://github.com/JetBrains/intellij-ui-test-robot for information about UI tests with IntelliJ Platform.
#
# Workflow is triggered manually.

name: Run UI Tests
on:
  workflow_dispatch

jobs:

  testUI:
    runs-on: ${{ matrix.os }}
    strategy:
      fail-fast: false
      matrix:
        include:
          - os: ubuntu-latest
            runIde: |
              export DISPLAY=:99.0
              Xvfb -ac :99 -screen 0 1920x1080x16 &
              gradle runIdeForUiTests &
          - os: windows-latest
            runIde: start gradlew.bat runIdeForUiTests
          - os: macos-latest
            runIde: ./gradlew runIdeForUiTests &

    steps:

      # Check out the current repository
      - name: Fetch Sources
        uses: actions/checkout@v6

      # Set up the Java environment for the next steps
      - name: Setup Java
        uses: actions/setup-java@v5
        with:
          distribution: zulu
          java-version: 21

      # Setup Gradle
      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v5
        with:
          cache-read-only: true

      # Run IDEA prepared for UI testing
      - name: Run IDE
        run: ${{ matrix.runIde }}

      # Wait for IDEA to be started
      - name: Health Check
        uses: jtalk/url-health-check-action@v4
        with:
          url: http://127.0.0.1:8082
          max-attempts: 15
          retry-delay: 30s

      # Run tests
      - name: Tests
        run: ./gradlew test

```
<!-- END_FILE -->

---
<!-- FILE: .gitignore -->
## .gitignore

```
.DS_Store
.gradle
.idea
.intellijPlatform
.kotlin
build

```
<!-- END_FILE -->

---
<!-- FILE: .run/Run Plugin.run.xml -->
## .run/Run Plugin.run.xml

```
<component name="ProjectRunConfigurationManager">
  <configuration default="false" name="Run Plugin" type="GradleRunConfiguration" factoryName="Gradle">
    <log_file alias="IDE logs" path="$PROJECT_DIR$/.intellijPlatform/sandbox/*/*/log/idea.log" show_all="true" />
    <ExternalSystemSettings>
      <option name="executionName" />
      <option name="externalProjectPath" value="$PROJECT_DIR$" />
      <option name="externalSystemIdString" value="GRADLE" />
      <option name="scriptParameters" value="" />
      <option name="taskDescriptions">
        <list />
      </option>
      <option name="taskNames">
        <list>
          <option value="runIde" />
        </list>
      </option>
      <option name="vmOptions" value="" />
    </ExternalSystemSettings>
    <ExternalSystemDebugServerProcess>false</ExternalSystemDebugServerProcess>
    <ExternalSystemReattachDebugProcess>true</ExternalSystemReattachDebugProcess>
    <DebugAllEnabled>false</DebugAllEnabled>
    <RunAsTest>false</RunAsTest>
    <method v="2" />
  </configuration>
</component>

```
<!-- END_FILE -->

---
<!-- FILE: .run/Run Tests.run.xml -->
## .run/Run Tests.run.xml

```
<component name="ProjectRunConfigurationManager">
  <configuration default="false" name="Run Tests" type="GradleRunConfiguration" factoryName="Gradle">
    <log_file alias="idea.log" path="$PROJECT_DIR$/build/idea-sandbox/system/log/idea.log" />
    <ExternalSystemSettings>
      <option name="executionName" />
      <option name="externalProjectPath" value="$PROJECT_DIR$" />
      <option name="externalSystemIdString" value="GRADLE" />
      <option name="scriptParameters" value="" />
      <option name="taskDescriptions">
        <list />
      </option>
      <option name="taskNames">
        <list>
          <option value="check" />
        </list>
      </option>
      <option name="vmOptions" value="" />
    </ExternalSystemSettings>
    <ExternalSystemDebugServerProcess>true</ExternalSystemDebugServerProcess>
    <ExternalSystemReattachDebugProcess>true</ExternalSystemReattachDebugProcess>
    <DebugAllEnabled>false</DebugAllEnabled>
    <RunAsTest>true</RunAsTest>
    <method v="2" />
  </configuration>
</component>

```
<!-- END_FILE -->

---
<!-- FILE: .run/Run Verifications.run.xml -->
## .run/Run Verifications.run.xml

```
<component name="ProjectRunConfigurationManager">
  <configuration default="false" name="Run Verifications" type="GradleRunConfiguration" factoryName="Gradle">
    <log_file alias="idea.log" path="$PROJECT_DIR$/build/idea-sandbox/system/log/idea.log" />
    <ExternalSystemSettings>
      <option name="executionName" />
      <option name="externalProjectPath" value="$PROJECT_DIR$" />
      <option name="externalSystemIdString" value="GRADLE" />
      <option name="scriptParameters" value="" />
      <option name="taskDescriptions">
        <list />
      </option>
      <option name="taskNames">
        <list>
          <option value="verifyPlugin" />
        </list>
      </option>
      <option name="vmOptions" value="" />
    </ExternalSystemSettings>
    <ExternalSystemDebugServerProcess>true</ExternalSystemDebugServerProcess>
    <ExternalSystemReattachDebugProcess>true</ExternalSystemReattachDebugProcess>
    <DebugAllEnabled>false</DebugAllEnabled>
    <RunAsTest>false</RunAsTest>
    <method v="2" />
  </configuration>
</component>

```
<!-- END_FILE -->

---
<!-- FILE: build.gradle.kts -->
## build.gradle.kts

```
import org.jetbrains.changelog.Changelog
import org.jetbrains.changelog.markdownToHTML
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("org.jetbrains.kotlin.jvm") version "2.2.0"
    id("org.jetbrains.intellij.platform") version "2.14.0"
    id("org.jetbrains.changelog") version "2.2.0"
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")

    // IntelliJ Platform Gradle Plugin Dependencies Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
    intellijPlatform {
        intellijIdea("2025.2.6.1")
        testFramework(TestFrameworkType.Platform)
    }
    implementation("org.json:json:20231013")
}

// Configure IntelliJ Platform Gradle Plugin - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-extension.html
intellijPlatform {
    pluginConfiguration {
        // Extract the <!-- Plugin description --> section from README.md and provide for the plugin's manifest
        description = providers.fileContents(layout.projectDirectory.file("README.md")).asText.map {
            val start = "<!-- Plugin description -->"
            val end = "<!-- Plugin description end -->"

            with(it.lines()) {
                if (!containsAll(listOf(start, end))) {
                    throw GradleException("Plugin description section not found in README.md:\n$start ... $end")
                }
                subList(indexOf(start) + 1, indexOf(end)).joinToString("\n").let(::markdownToHTML)
            }
        }

        val changelog = project.changelog // local variable for configuration cache compatibility
        // Get the latest available change notes from the changelog file
        changeNotes = version.map { pluginVersion ->
            with(changelog) {
                renderItem(
                    (getOrNull(pluginVersion) ?: getUnreleased())
                        .withHeader(false)
                        .withEmptySections(false),
                    Changelog.OutputType.HTML,
                )
            }
        }
    }
}

// Configure Gradle Changelog Plugin - read more: https://github.com/JetBrains/gradle-changelog-plugin
changelog {
    groups.empty()
    repositoryUrl = providers.gradleProperty("pluginRepositoryUrl")
    versionPrefix = ""
}

tasks {
    publishPlugin {
        dependsOn(patchChangelog)
    }
}

```
<!-- END_FILE -->

---
<!-- FILE: CHANGELOG.md -->
## CHANGELOG.md

```md
<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# jetFlo Changelog

## [Unreleased]
### Added
- Initial scaffold created from [IntelliJ Platform Plugin Template](https://github.com/JetBrains/intellij-platform-plugin-template)

```
<!-- END_FILE -->

---
<!-- FILE: gradle.properties -->
## gradle.properties

```
group = com.github.natalyjaya.jetflo
version = 0.0.1

pluginRepositoryUrl = https://github.com/NatalyJaya/jetFlo

# Opt-out flag for bundling Kotlin standard library -> https://jb.gg/intellij-platform-kotlin-stdlib
kotlin.stdlib.default.dependency = false

# Enable Gradle Configuration Cache -> https://docs.gradle.org/current/userguide/configuration_cache.html
org.gradle.configuration-cache = true

# Enable Gradle Build Cache -> https://docs.gradle.org/current/userguide/build_cache.html
org.gradle.caching = true

```
<!-- END_FILE -->

---
<!-- FILE: gradle/wrapper/gradle-wrapper.properties -->
## gradle/wrapper/gradle-wrapper.properties

```
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-9.4.1-bin.zip
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists

```
<!-- END_FILE -->

---
<!-- FILE: gradlew -->
## gradlew

```
#!/bin/sh

#
# Copyright © 2015 the original authors.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
# SPDX-License-Identifier: Apache-2.0
#

##############################################################################
#
#   Gradle start up script for POSIX generated by Gradle.
#
#   Important for running:
#
#   (1) You need a POSIX-compliant shell to run this script. If your /bin/sh is
#       noncompliant, but you have some other compliant shell such as ksh or
#       bash, then to run this script, type that shell name before the whole
#       command line, like:
#
#           ksh Gradle
#
#       Busybox and similar reduced shells will NOT work, because this script
#       requires all of these POSIX shell features:
#         * functions;
#         * expansions «$var», «${var}», «${var:-default}», «${var+SET}»,
#           «${var#prefix}», «${var%suffix}», and «$( cmd )»;
#         * compound commands having a testable exit status, especially «case»;
#         * various built-in commands including «command», «set», and «ulimit».
#
#   Important for patching:
#
#   (2) This script targets any POSIX shell, so it avoids extensions provided
#       by Bash, Ksh, etc; in particular arrays are avoided.
#
#       The "traditional" practice of packing multiple parameters into a
#       space-separated string is a well documented source of bugs and security
#       problems, so this is (mostly) avoided, by progressively accumulating
#       options in "$@", and eventually passing that to Java.
#
#       Where the inherited environment variables (DEFAULT_JVM_OPTS, JAVA_OPTS,
#       and GRADLE_OPTS) rely on word-splitting, this is performed explicitly;
#       see the in-line comments for details.
#
#       There are tweaks for specific operating systems such as AIX, CygWin,
#       Darwin, MinGW, and NonStop.
#
#   (3) This script is generated from the Groovy template
#       https://github.com/gradle/gradle/blob/2d6327017519d23b96af35865dc997fcb544fb40/platforms/jvm/plugins-application/src/main/resources/org/gradle/api/internal/plugins/unixStartScript.txt
#       within the Gradle project.
#
#       You can find Gradle at https://github.com/gradle/gradle/.
#
##############################################################################

# Attempt to set APP_HOME

# Resolve links: $0 may be a link
app_path=$0

# Need this for daisy-chained symlinks.
while
    APP_HOME=${app_path%"${app_path##*/}"}  # leaves a trailing /; empty if no leading path
    [ -h "$app_path" ]
do
    ls=$( ls -ld "$app_path" )
    link=${ls#*' -> '}
    case $link in             #(
      /*)   app_path=$link ;; #(
      *)    app_path=$APP_HOME$link ;;
    esac
done

# This is normally unused
# shellcheck disable=SC2034
APP_BASE_NAME=${0##*/}
# Discard cd standard output in case $CDPATH is set (https://github.com/gradle/gradle/issues/25036)
APP_HOME=$( cd -P "${APP_HOME:-./}" > /dev/null && printf '%s\n' "$PWD" ) || exit

# Use the maximum available, or set MAX_FD != -1 to use that value.
MAX_FD=maximum

warn () {
    echo "$*"
} >&2

die () {
    echo
    echo "$*"
    echo
    exit 1
} >&2

# OS specific support (must be 'true' or 'false').
cygwin=false
msys=false
darwin=false
nonstop=false
case "$( uname )" in                #(
  CYGWIN* )         cygwin=true  ;; #(
  Darwin* )         darwin=true  ;; #(
  MSYS* | MINGW* )  msys=true    ;; #(
  NONSTOP* )        nonstop=true ;;
esac



# Determine the Java command to use to start the JVM.
if [ -n "$JAVA_HOME" ] ; then
    if [ -x "$JAVA_HOME/jre/sh/java" ] ; then
        # IBM's JDK on AIX uses strange locations for the executables
        JAVACMD=$JAVA_HOME/jre/sh/java
    else
        JAVACMD=$JAVA_HOME/bin/java
    fi
    if [ ! -x "$JAVACMD" ] ; then
        die "ERROR: JAVA_HOME is set to an invalid directory: $JAVA_HOME

Please set the JAVA_HOME variable in your environment to match the
location of your Java installation."
    fi
else
    JAVACMD=java
    if ! command -v java >/dev/null 2>&1
    then
        die "ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.

Please set the JAVA_HOME variable in your environment to match the
location of your Java installation."
    fi
fi

# Increase the maximum file descriptors if we can.
if ! "$cygwin" && ! "$darwin" && ! "$nonstop" ; then
    case $MAX_FD in #(
      max*)
        # In POSIX sh, ulimit -H is undefined. That's why the result is checked to see if it worked.
        # shellcheck disable=SC2039,SC3045
        MAX_FD=$( ulimit -H -n ) ||
            warn "Could not query maximum file descriptor limit"
    esac
    case $MAX_FD in  #(
      '' | soft) :;; #(
      *)
        # In POSIX sh, ulimit -n is undefined. That's why the result is checked to see if it worked.
        # shellcheck disable=SC2039,SC3045
        ulimit -n "$MAX_FD" ||
            warn "Could not set maximum file descriptor limit to $MAX_FD"
    esac
fi

# Collect all arguments for the java command, stacking in reverse order:
#   * args from the command line
#   * the main class name
#   * -classpath
#   * -D...appname settings
#   * --module-path (only if needed)
#   * DEFAULT_JVM_OPTS, JAVA_OPTS, and GRADLE_OPTS environment variables.

# For Cygwin or MSYS, switch paths to Windows format before running java
if "$cygwin" || "$msys" ; then
    APP_HOME=$( cygpath --path --mixed "$APP_HOME" )

    JAVACMD=$( cygpath --unix "$JAVACMD" )

    # Now convert the arguments - kludge to limit ourselves to /bin/sh
    for arg do
        if
            case $arg in                                #(
              -*)   false ;;                            # don't mess with options #(
              /?*)  t=${arg#/} t=/${t%%/*}              # looks like a POSIX filepath
                    [ -e "$t" ] ;;                      #(
              *)    false ;;
            esac
        then
            arg=$( cygpath --path --ignore --mixed "$arg" )
        fi
        # Roll the args list around exactly as many times as the number of
        # args, so each arg winds up back in the position where it started, but
        # possibly modified.
        #
        # NB: a `for` loop captures its iteration list before it begins, so
        # changing the positional parameters here affects neither the number of
        # iterations, nor the values presented in `arg`.
        shift                   # remove old arg
        set -- "$@" "$arg"      # push replacement arg
    done
fi


# Add default JVM options here. You can also use JAVA_OPTS and GRADLE_OPTS to pass JVM options to this script.
DEFAULT_JVM_OPTS='"-Xmx64m" "-Xms64m"'

# Collect all arguments for the java command:
#   * DEFAULT_JVM_OPTS, JAVA_OPTS, and optsEnvironmentVar are not allowed to contain shell fragments,
#     and any embedded shellness will be escaped.
#   * For example: A user cannot expect ${Hostname} to be expanded, as it is an environment variable and will be
#     treated as '${Hostname}' itself on the command line.

set -- \
        "-Dorg.gradle.appname=$APP_BASE_NAME" \
        -jar "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" \
        "$@"

# Stop when "xargs" is not available.
if ! command -v xargs >/dev/null 2>&1
then
    die "xargs is not available"
fi

# Use "xargs" to parse quoted args.
#
# With -n1 it outputs one arg per line, with the quotes and backslashes removed.
#
# In Bash we could simply go:
#
#   readarray ARGS < <( xargs -n1 <<<"$var" ) &&
#   set -- "${ARGS[@]}" "$@"
#
# but POSIX shell has neither arrays nor command substitution, so instead we
# post-process each arg (as a line of input to sed) to backslash-escape any
# character that might be a shell metacharacter, then use eval to reverse
# that process (while maintaining the separation between arguments), and wrap
# the whole thing up as a single "set" statement.
#
# This will of course break if any of these variables contains a newline or
# an unmatched quote.
#

eval "set -- $(
        printf '%s\n' "$DEFAULT_JVM_OPTS $JAVA_OPTS $GRADLE_OPTS" |
        xargs -n1 |
        sed ' s~[^-[:alnum:]+,./:=@_]~\\&~g; ' |
        tr '\n' ' '
    )" '"$@"'

exec "$JAVACMD" "$@"

```
<!-- END_FILE -->

---
<!-- FILE: gradlew.bat -->
## gradlew.bat

```
@rem
@rem Copyright 2015 the original author or authors.
@rem
@rem Licensed under the Apache License, Version 2.0 (the "License");
@rem you may not use this file except in compliance with the License.
@rem You may obtain a copy of the License at
@rem
@rem      https://www.apache.org/licenses/LICENSE-2.0
@rem
@rem Unless required by applicable law or agreed to in writing, software
@rem distributed under the License is distributed on an "AS IS" BASIS,
@rem WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
@rem See the License for the specific language governing permissions and
@rem limitations under the License.
@rem
@rem SPDX-License-Identifier: Apache-2.0
@rem

@if "%DEBUG%"=="" @echo off
@rem ##########################################################################
@rem
@rem  Gradle startup script for Windows
@rem
@rem ##########################################################################

@rem Set local scope for the variables with windows NT shell
if "%OS%"=="Windows_NT" setlocal

set DIRNAME=%~dp0
if "%DIRNAME%"=="" set DIRNAME=.
@rem This is normally unused
set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%

@rem Resolve any "." and ".." in APP_HOME to make it shorter.
for %%i in ("%APP_HOME%") do set APP_HOME=%%~fi

@rem Add default JVM options here. You can also use JAVA_OPTS and GRADLE_OPTS to pass JVM options to this script.
set DEFAULT_JVM_OPTS="-Xmx64m" "-Xms64m"

@rem Find java.exe
if defined JAVA_HOME goto findJavaFromJavaHome

set JAVA_EXE=java.exe
%JAVA_EXE% -version >NUL 2>&1
if %ERRORLEVEL% equ 0 goto execute

echo. 1>&2
echo ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH. 1>&2
echo. 1>&2
echo Please set the JAVA_HOME variable in your environment to match the 1>&2
echo location of your Java installation. 1>&2

goto fail

:findJavaFromJavaHome
set JAVA_HOME=%JAVA_HOME:"=%
set JAVA_EXE=%JAVA_HOME%/bin/java.exe

if exist "%JAVA_EXE%" goto execute

echo. 1>&2
echo ERROR: JAVA_HOME is set to an invalid directory: %JAVA_HOME% 1>&2
echo. 1>&2
echo Please set the JAVA_HOME variable in your environment to match the 1>&2
echo location of your Java installation. 1>&2

goto fail

:execute
@rem Setup the command line



@rem Execute Gradle
"%JAVA_EXE%" %DEFAULT_JVM_OPTS% %JAVA_OPTS% %GRADLE_OPTS% "-Dorg.gradle.appname=%APP_BASE_NAME%" -jar "%APP_HOME%\gradle\wrapper\gradle-wrapper.jar" %*

:end
@rem End local scope for the variables with windows NT shell
if %ERRORLEVEL% equ 0 goto mainEnd

:fail
rem Set variable GRADLE_EXIT_CONSOLE if you need the _script_ return code instead of
rem the _cmd.exe /c_ return code!
set EXIT_CODE=%ERRORLEVEL%
if %EXIT_CODE% equ 0 set EXIT_CODE=1
if not ""=="%GRADLE_EXIT_CONSOLE%" exit %EXIT_CODE%
exit /b %EXIT_CODE%

:mainEnd
if "%OS%"=="Windows_NT" endlocal

:omega

```
<!-- END_FILE -->

---
<!-- FILE: README.md -->
## README.md

```md
# jetFlo

![Build](https://github.com/NatalyJaya/jetFlo/workflows/Build/badge.svg)
[![Version](https://img.shields.io/jetbrains/plugin/v/MARKETPLACE_ID.svg)](https://plugins.jetbrains.com/plugin/MARKETPLACE_ID)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/MARKETPLACE_ID.svg)](https://plugins.jetbrains.com/plugin/MARKETPLACE_ID)

## Template ToDo list
- [x] Create a new [IntelliJ Platform Plugin Template][template] project.
- [ ] Get familiar with the [template documentation][template].
- [ ] Adjust the [group](./gradle.properties), as well as the [id](./src/main/resources/META-INF/plugin.xml), [name](./src/main/resources/META-INF/plugin.xml), and [sources package](./src/main/kotlin).
- [ ] Adjust the plugin description in `README` (see [Tips][docs:plugin-description])
- [ ] Review the [Legal Agreements](https://plugins.jetbrains.com/docs/marketplace/legal-agreements.html?from=IJPluginTemplate).
- [ ] [Publish a plugin manually](https://plugins.jetbrains.com/docs/intellij/publishing-plugin.html?from=IJPluginTemplate) for the first time.
- [ ] Set the `MARKETPLACE_ID` in the above README badges. You can obtain it once the plugin is published to JetBrains Marketplace.
- [ ] Set the [Plugin Signing](https://plugins.jetbrains.com/docs/intellij/plugin-signing.html?from=IJPluginTemplate) related [secrets](https://github.com/JetBrains/intellij-platform-plugin-template#environment-variables).
- [ ] Set the [Deployment Token](https://plugins.jetbrains.com/docs/marketplace/plugin-upload.html?from=IJPluginTemplate).
- [ ] Click the <kbd>Watch</kbd> button on the top of the [IntelliJ Platform Plugin Template][template] to be notified about releases containing new features and fixes.

<!-- Plugin description -->
This Fancy IntelliJ Platform Plugin is going to be your implementation of the brilliant ideas that you have.

This specific section is a source for the [plugin.xml](/src/main/resources/META-INF/plugin.xml) file which will be extracted by the [Gradle](/build.gradle.kts) during the build process.

To keep everything working, do not remove `<!-- ... -->` sections. 
<!-- Plugin description end -->

## Installation

- Using the IDE built-in plugin system:

  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>Marketplace</kbd> > <kbd>Search for "jetFlo"</kbd> >
  <kbd>Install</kbd>

- Using JetBrains Marketplace:

  Go to [JetBrains Marketplace](https://plugins.jetbrains.com/plugin/MARKETPLACE_ID) and install it by clicking the <kbd>Install to ...</kbd> button in case your IDE is running.

  You can also download the [latest release](https://plugins.jetbrains.com/plugin/MARKETPLACE_ID/versions) from JetBrains Marketplace and install it manually using
  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>⚙️</kbd> > <kbd>Install plugin from disk...</kbd>

- Manually:

  Download the [latest release](https://github.com/NatalyJaya/jetFlo/releases/latest) and install it manually using
  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>⚙️</kbd> > <kbd>Install plugin from disk...</kbd>


---
Plugin based on the [IntelliJ Platform Plugin Template][template].

[template]: https://github.com/JetBrains/intellij-platform-plugin-template
[docs:plugin-description]: https://plugins.jetbrains.com/docs/intellij/plugin-user-experience.html#plugin-description-and-presentation

```
<!-- END_FILE -->

---
<!-- FILE: settings.gradle.kts -->
## settings.gradle.kts

```
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "jetFlo"

```
<!-- END_FILE -->

---
<!-- FILE: src/main/kotlin/com/github/natalyjaya/jetflo/MyBundle.kt -->
## src/main/kotlin/com/github/natalyjaya/jetflo/MyBundle.kt

```
package com.github.natalyjaya.jetflo

import com.intellij.DynamicBundle
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey

@NonNls
private const val BUNDLE = "messages.MyBundle"

object MyBundle : DynamicBundle(BUNDLE) {

    @JvmStatic
    fun message(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any) =
        getMessage(key, *params)

    @Suppress("unused")
    @JvmStatic
    fun messagePointer(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any) =
        getLazyMessage(key, *params)
}

```
<!-- END_FILE -->

---
<!-- FILE: src/main/kotlin/com/github/natalyjaya/jetflo/services/MyProjectService.kt -->
## src/main/kotlin/com/github/natalyjaya/jetflo/services/MyProjectService.kt

```
package com.github.natalyjaya.jetflo.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.github.natalyjaya.jetflo.MyBundle

@Service(Service.Level.PROJECT)
class MyProjectService(project: Project) {

    init {
        thisLogger().info(MyBundle.message("projectService", project.name))
        thisLogger().warn("Don't forget to remove all non-needed sample code files with their corresponding registration entries in `plugin.xml`.")
    }

    fun getRandomNumber() = (1..100).random()
}

```
<!-- END_FILE -->

---
<!-- FILE: src/main/kotlin/com/github/natalyjaya/jetflo/startup/MyProjectActivity.kt -->
## src/main/kotlin/com/github/natalyjaya/jetflo/startup/MyProjectActivity.kt

```
package com.github.natalyjaya.jetflo.startup

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

class MyProjectActivity : ProjectActivity {

    override suspend fun execute(project: Project) {
        thisLogger().warn("Don't forget to remove all non-needed sample code files with their corresponding registration entries in `plugin.xml`.")
    }
}

```
<!-- END_FILE -->

---
<!-- FILE: src/main/kotlin/com/github/natalyjaya/jetflo/toolWindow/MyToolWindowFactory.kt -->
## src/main/kotlin/com/github/natalyjaya/jetflo/toolWindow/MyToolWindowFactory.kt

```
package com.github.natalyjaya.jetflo.toolWindow

import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.content.ContentFactory
import com.github.natalyjaya.jetflo.MyBundle
import com.github.natalyjaya.jetflo.services.MyProjectService
import javax.swing.JButton


class MyToolWindowFactory : ToolWindowFactory {

    init {
        thisLogger().warn("Don't forget to remove all non-needed sample code files with their corresponding registration entries in `plugin.xml`.")
    }

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val myToolWindow = MyToolWindow(toolWindow)
        val content = ContentFactory.getInstance().createContent(myToolWindow.getContent(), null, false)
        toolWindow.contentManager.addContent(content)
    }

    override fun shouldBeAvailable(project: Project) = true

    class MyToolWindow(toolWindow: ToolWindow) {

        private val service = toolWindow.project.service<MyProjectService>()

        fun getContent() = JBPanel<JBPanel<*>>().apply {
            val label = JBLabel(MyBundle.message("randomLabel", "?"))

            add(label)
            add(JButton(MyBundle.message("shuffle")).apply {
                addActionListener {
                    label.text = MyBundle.message("randomLabel", service.getRandomNumber())
                }
            })
        }
    }
}

```
<!-- END_FILE -->

---
<!-- FILE: src/main/kotlin/com/github/natalyjaya/jetflo/ui/RockyFloatingActivity.kt -->
## src/main/kotlin/com/github/natalyjaya/jetflo/ui/RockyFloatingActivity.kt

```
package com.github.natalyjaya.jetflo.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.wm.WindowManager
import java.awt.*
import java.awt.event.*
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import javax.swing.*
import org.json.JSONArray

private const val GITHUB_API_BASE = "https://api.github.com/repos/actions/starter-workflows/contents/ci"
private const val GITHUB_RAW_BASE = "https://raw.githubusercontent.com/actions/starter-workflows/main/ci"

// Variables de tamaño unificadas
private const val WIDGET_W  = 200
private const val WIDGET_H  = 220
private const val ROCKY_W    = 80  // Asegúrate de que termine en Y
private const val ROCKY_H    = 80
private const val MARGIN     = 10

class RockyFloatingActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        SwingUtilities.invokeLater {
            val frame = WindowManager.getInstance().getFrame(project) ?: return@invokeLater
            val layeredPane = frame.layeredPane

            val rockyWidget = RockyWidget(project)
            layeredPane.add(rockyWidget, JLayeredPane.PALETTE_LAYER)

            fun reposition() {
                val lh = layeredPane.height
                rockyWidget.setBounds(MARGIN, (lh - WIDGET_H - 40).coerceAtLeast(0), WIDGET_W, WIDGET_H)
            }

            reposition()
            layeredPane.addComponentListener(object : ComponentAdapter() {
                override fun componentResized(e: ComponentEvent?) = reposition()
            })

            // --- FLUJO DE MENSAJES LENTO ---
            // 1. Saludo inicial
            Timer(1500) {
                rockyWidget.showMessage("Hi! I'm Rocky") {
                    // 2. Segunda parte tras 2 segundos de pausa
                    Timer(2000) {
                        rockyWidget.showMessage("I'll help you with your CI.")
                    }.apply { isRepeats = false; start() }
                }
            }.apply { isRepeats = false; start() }
        }
    }
}

class RockyWidget(private val project: Project) : JPanel(null) {
    private var bubbleLines: List<String> = emptyList()
    private var isTalking = false
    private var bobOffset = 0f
    private var bobDirection = 1
    private var isLoading = false
    private var visibleChars = 0
    private var fullText = ""

    private val rockyImage: Image? = javaClass.getResource("/icons/stand.png")?.let { ImageIcon(it).image }

    private val stackCombo = JComboBox<String>().apply {
        isVisible = false
        isEnabled = false
        font = Font("SansSerif", Font.PLAIN, 12)
    }

    private val applyBtn = JButton("▶").apply {
        isVisible = false
        background = Color(88, 101, 242)
        foreground = Color.WHITE
        isFocusPainted = false
    }

    init {
        isOpaque = false
        // Posicionamiento de UI
        val comboY = WIDGET_H - ROCKY_H - 35
        stackCombo.setBounds(0, comboY, WIDGET_W - 50, 30)
        applyBtn.setBounds(WIDGET_W - 46, comboY, 44, 30)

        add(stackCombo)
        add(applyBtn)

        applyBtn.addActionListener { onApply() }
        startBobAnimation()
        loadStacksFromGitHub()
    }

    private fun startBobAnimation() {
        Timer(50) {
            if (!isTalking && !isLoading) {
                bobOffset += bobDirection * 0.6f
                if (bobOffset > 4f || bobOffset < -4f) bobDirection *= -1
                repaint()
            }
        }.start()
    }

    fun showMessage(text: String, onFinished: (() -> Unit)? = null) {
        isTalking = true
        fullText = text
        visibleChars = 0

        val typewriter = Timer(60, null) // Velocidad de escritura lenta (60ms)
        typewriter.addActionListener {
            if (visibleChars < fullText.length) {
                visibleChars++
                bubbleLines = fullText.substring(0, visibleChars).split("\n")
                repaint()
            } else {
                (it.source as Timer).stop()
                onFinished?.invoke()
                // El mensaje se queda un tiempo antes de borrarse
                Timer(3500) {
                    if (fullText == text) {
                        isTalking = false
                        bubbleLines = emptyList()
                        repaint()
                    }
                }.apply { isRepeats = false; start() }
            }
        }
        typewriter.start()
    }

    private fun loadStacksFromGitHub() {
        Thread {
            try {
                val json = fetchUrl(GITHUB_API_BASE)
                val names = JSONArray(json).let { arr ->
                    (0 until arr.length()).map { arr.getJSONObject(it).getString("name") }
                        .filter { it.endsWith(".yml") || it.endsWith(".yaml") }
                        .map { it.removeSuffix(".yml").removeSuffix(".yaml") }
                }

                // Esperamos 7 segundos para que dé tiempo a las presentaciones iniciales
                Thread.sleep(7000)

                SwingUtilities.invokeLater {
                    stackCombo.removeAllItems()
                    stackCombo.addItem("— Select a stack —")
                    names.forEach { stackCombo.addItem(it) }

                    stackCombo.isVisible = true
                    applyBtn.isVisible = true
                    stackCombo.isEnabled = true
                    applyBtn.isEnabled = true

                    showMessage("Nice! Stacks are ready.\nPick one to implement CI.")
                    revalidate()
                }
            } catch (e: Exception) {
                SwingUtilities.invokeLater { showMessage("GitHub connection failed") }
            }
        }.apply { isDaemon = true; start() }
    }

    private fun onApply() {
        val selected = stackCombo.selectedItem as? String ?: return
        if (selected.startsWith("—")) return
        isLoading = true
        applyBtn.isEnabled = false
        showMessage("Setting up $selected...")

        Thread {
            try {
                val content = fetchUrl("$GITHUB_RAW_BASE/$selected.yml")
                createWorkflowFile(content)
                SwingUtilities.invokeLater {
                    isLoading = false
                    applyBtn.isEnabled = true
                    showMessage("All set!\nCheck .github/workflows")
                }
            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    isLoading = false
                    applyBtn.isEnabled = true
                    showMessage("Error downloading")
                }
            }
        }.apply { isDaemon = true; start() }
    }

    private fun createWorkflowFile(content: String) {
        val basePath = project.basePath ?: return
        val workflowDir = File("$basePath/.github/workflows").apply { mkdirs() }
        val targetFile = File(workflowDir, "main.yml").apply { writeText(content) }

        ApplicationManager.getApplication().invokeLater {
            LocalFileSystem.getInstance().refreshAndFindFileByIoFile(targetFile)?.let { vf ->
                VfsUtil.markDirtyAndRefresh(false, false, false, vf)
            }
        }
    }

    private fun fetchUrl(urlStr: String): String {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        conn.setRequestProperty("User-Agent", "JetFlo-Plugin")
        return conn.inputStream.bufferedReader().readText()
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2 = g as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

        val rX = (width - ROCKY_W) / 2
        val rY = height - ROCKY_H - 4 + (if (!isTalking) bobOffset.toInt() else -2)

        if (bubbleLines.isNotEmpty()) {
            val pad = 12
            g2.font = Font("SansSerif", Font.BOLD, 12)
            val fm = g2.fontMetrics
            val maxW = bubbleLines.maxOf { fm.stringWidth(it) }
            val bW = maxW + pad * 2
            val bH = bubbleLines.size * fm.height + pad * 2
            val bX = (width - bW) / 2
            val bY = if (stackCombo.isVisible) stackCombo.y - bH - 10 else rY - bH - 10

            g2.color = Color(255, 255, 255, 250)
            g2.fillRoundRect(bX, bY, bW, bH, 18, 18)
            g2.color = Color(88, 101, 242, 80)
            g2.drawRoundRect(bX, bY, bW, bH, 18, 18)

            g2.color = Color.BLACK
            bubbleLines.forEachIndexed { i, line ->
                g2.drawString(line, bX + (bW - fm.stringWidth(line)) / 2, bY + pad + fm.ascent + i * fm.height)
            }
        }

        if (rockyImage != null) {
            g2.drawImage(rockyImage, rX, rY, ROCKY_W, ROCKY_H, null)
        }
    }
}

```
<!-- END_FILE -->

---
<!-- FILE: src/main/resources/messages/MyBundle.properties -->
## src/main/resources/messages/MyBundle.properties

```
projectService=Project service: {0}
randomLabel=The random number is: {0}
shuffle=Shuffle

```
<!-- END_FILE -->

---
<!-- FILE: src/main/resources/META-INF/plugin.xml -->
## src/main/resources/META-INF/plugin.xml

```
<!-- Plugin Configuration File. Read more: https://plugins.jetbrains.com/docs/intellij/plugin-configuration-file.html -->
<idea-plugin>
    <id>com.github.natalyjaya.jetflo</id>
    <name>jetFlo</name>
    <vendor>natalyjaya</vendor>

    <depends>com.intellij.modules.platform</depends>

    <resource-bundle>messages.MyBundle</resource-bundle>

    <extensions defaultExtensionNs="com.intellij">
        <toolWindow factoryClass="com.github.natalyjaya.jetflo.toolWindow.MyToolWindowFactory" id="MyToolWindow"/>
        <postStartupActivity implementation="com.github.natalyjaya.jetflo.ui.RockyFloatingActivity"/>
    </extensions>
</idea-plugin>

```
<!-- END_FILE -->

---
<!-- FILE: src/test/kotlin/com/github/natalyjaya/jetflo/MyPluginTest.kt -->
## src/test/kotlin/com/github/natalyjaya/jetflo/MyPluginTest.kt

```
package com.github.natalyjaya.jetflo

import com.intellij.ide.highlighter.XmlFileType
import com.intellij.openapi.components.service
import com.intellij.psi.xml.XmlFile
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.PsiErrorElementUtil
import com.github.natalyjaya.jetflo.services.MyProjectService

@TestDataPath("\$CONTENT_ROOT/src/test/testData")
class MyPluginTest : BasePlatformTestCase() {

    fun testXMLFile() {
        val psiFile = myFixture.configureByText(XmlFileType.INSTANCE, "<foo>bar</foo>")
        val xmlFile = assertInstanceOf(psiFile, XmlFile::class.java)

        assertFalse(PsiErrorElementUtil.hasErrors(project, xmlFile.virtualFile))

        assertNotNull(xmlFile.rootTag)

        xmlFile.rootTag?.let {
            assertEquals("foo", it.name)
            assertEquals("bar", it.value.text)
        }
    }

    fun testRename() {
        myFixture.testRename("foo.xml", "foo_after.xml", "a2")
    }

    fun testProjectService() {
        val projectService = project.service<MyProjectService>()

        assertNotSame(projectService.getRandomNumber(), projectService.getRandomNumber())
    }

    override fun getTestDataPath() = "src/test/testData/rename"
}

```
<!-- END_FILE -->

---
<!-- FILE: src/test/testData/rename/foo_after.xml -->
## src/test/testData/rename/foo_after.xml

```
<root>
    <a2>Foo</a2>
</root>

```
<!-- END_FILE -->

---
<!-- FILE: src/test/testData/rename/foo.xml -->
## src/test/testData/rename/foo.xml

```
<root>
    <a<caret>1>Foo</a1>
</root>

```
<!-- END_FILE -->


---
## Bundle notes
- Redaction: enabled (mask: `***REDACTED***`).
- No truncations.

