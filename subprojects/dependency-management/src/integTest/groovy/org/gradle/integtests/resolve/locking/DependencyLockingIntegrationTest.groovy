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

package org.gradle.integtests.resolve.locking

import org.gradle.integtests.fixtures.AbstractDependencyResolutionTest

class DependencyLockingIntegrationTest extends AbstractDependencyResolutionTest {

    def lockfileFixture = new LockfileFixture(testDirectory: testDirectory)

    def setup() {
        settingsFile << "rootProject.name = 'depLock'"
    }

    def 'succeeds with lock file present'() {
        mavenRepo.module('org', 'foo', '1.0').publish()
        mavenRepo.module('org', 'foo', '1.1').publish()

        buildFile << """
dependencyLocking {
    lockAllConfigurations()
}

repositories {
    maven {
        name 'repo'
        url '${mavenRepo.uri}'
    }
}
configurations {
    lockedConf
}

dependencies {
    lockedConf 'org:foo:1.+'
}
"""

        lockfileFixture.createLockfile('lockedConf',['org:foo:1.0'])

        when:
        succeeds 'dependencies'

        then:
        outputContains("org:foo:1.0")
    }

    def 'succeeds without lock file present and does not create one'() {
        mavenRepo.module('org', 'foo', '1.0').publish()

        buildFile << """
dependencyLocking {
    lockAllConfigurations()
}

repositories {
    maven {
        name 'repo'
        url '${mavenRepo.uri}'
    }
}
configurations {
    unlockedConf
}

dependencies {
    unlockedConf 'org:foo:1.+'
}
"""

        when:
        succeeds 'dependencies'

        then:
        lockfileFixture.expectMissing('unlockedConf')
    }

    def 'does not write-locks for unlocked configuration'() {
        mavenRepo.module('org', 'foo', '1.0').publish()

        buildFile << """
repositories {
    maven {
        name 'repo'
        url '${mavenRepo.uri}'
    }
}
configurations {
    unlockedConf
}

dependencies {
    unlockedConf 'org:foo:1.+'
}
"""

        when:
        succeeds 'dependencies', '--write-locks'

        then:
        lockfileFixture.expectMissing('unlockedConf')
    }

    def 'fails with out-of-date lock file'() {
        mavenRepo.module('org', 'foo', '1.0').publish()
        mavenRepo.module('org', 'foo', '1.1').publish()

        buildFile << """
dependencyLocking {
    lockAllConfigurations()
}

repositories {
    maven {
        name 'repo'
        url '${mavenRepo.uri}'
    }
}
configurations {
    lockedConf
}

dependencies {
    constraints {
        lockedConf('org:foo') {
            version {
                prefer '1.1'
            }
        }
    }
    lockedConf 'org:foo:1.+'
}
"""

        lockfileFixture.createLockfile('lockedConf',['org:foo:1.0'])

        when:
        fails 'dependencies'

        then:
        failure.assertHasCause("Cannot find a version of 'org:foo' that satisfies the version constraints: \n" +
            "   Dependency path ':depLock:unspecified' --> 'org:foo' prefers '1.+'\n" +
            "   Constraint path ':depLock:unspecified' --> 'org:foo' prefers '1.1'\n" +
            "   Constraint path ':depLock:unspecified' --> 'org:foo' prefers '1.0', rejects ']1.0,)' because of the following reason: dependency was locked to version 1.0")
    }

    def 'fails when lock file entry not resolved'() {
        mavenRepo.module('org', 'foo', '1.0').publish()
        mavenRepo.module('org', 'bar', '1.0').publish()

        buildFile << """
dependencyLocking {
    lockAllConfigurations()
}

repositories {
    maven {
        name 'repo'
        url '${mavenRepo.uri}'
    }
}
configurations {
    lockedConf
}

dependencies {
    lockedConf 'org:foo:1.+'
}
"""

        lockfileFixture.createLockfile('lockedConf',['org:bar:1.0', 'org:foo:1.0'])

        when:
        fails 'dependencies'

        then:
        failure.assertHasCause("Dependency lock state for configuration 'lockedConf' is out of date:: Did not resolve 'org:bar:1.0' which is part of the lock state")
    }

    def 'writes dependency lock file when requested'() {
        mavenRepo.module('org', 'foo', '1.0').publish()
        mavenRepo.module('org', 'bar', '1.0').publish()

        buildFile << """
dependencyLocking {
    lockAllConfigurations()
}

repositories {
    maven {
        name 'repo'
        url '${mavenRepo.uri}'
    }
}
configurations {
    lockedConf
}

dependencies {
    lockedConf 'org:foo:1.+'
    lockedConf 'org:bar:1.+'
}
"""

        when:
        succeeds'dependencies', '--write-locks', '--refresh-dependencies'

        then:
        lockfileFixture.verifyLockfile('lockedConf', ['org:foo:1.0', 'org:bar:1.0'])

    }

    def 'upgrades lock file'() {
        mavenRepo.module('org', 'foo', '1.0').publish()
        mavenRepo.module('org', 'foo', '1.1').publish()

        buildFile << """
dependencyLocking {
    lockAllConfigurations()
}

repositories {
    maven {
        name 'repo'
        url '${mavenRepo.uri}'
    }
}
configurations {
    lockedConf
}

dependencies {
    lockedConf 'org:foo:1.+'
}
"""


        lockfileFixture.createLockfile('lockedConf', ["org:foo:1.0"])

        when:
        succeeds 'dependencies', '--write-locks'

        then:
        lockfileFixture.verifyLockfile('lockedConf', ['org:foo:1.1'])
    }

    def 'fails and details all out lock issues'() {
        mavenRepo.module('org', 'foo', '1.0').publish()
        mavenRepo.module('org', 'foo', '1.1').publish()
        mavenRepo.module('org', 'bar', '1.0').publish()

        buildFile << """
dependencyLocking {
    lockAllConfigurations()
}

repositories {
    maven {
        name 'repo'
        url '${mavenRepo.uri}'
    }
}
configurations {
    lockedConf
}

dependencies {
    constraints {
        lockedConf('org:foo') {
            version {
                prefer '1.1'
            }
        }
    }
    lockedConf 'org:foo:[1.0, 1.1]'
}
"""

        lockfileFixture.createLockfile('lockedConf',['org:bar:1.0', 'org:foo:1.0'])

        when:
        fails 'dependencies'

        then:
        failure.assertHasCause("Cannot find a version of 'org:foo' that satisfies the version constraints: \n" +
            "   Dependency path ':depLock:unspecified' --> 'org:foo' prefers '[1.0, 1.1]'\n" +
            "   Constraint path ':depLock:unspecified' --> 'org:foo' prefers '1.1'\n" +
            "   Constraint path ':depLock:unspecified' --> 'org:foo' prefers '1.0', rejects ']1.0,)' because of the following reason: dependency was locked to version 1.0")
//        failure.assertHasCause("Dependency lock out of date:\n" +
//            "\tLock file contained 'org:bar:1.0' but it is not part of the resolved modules\n" +
//            "\tLock file expected 'org:foo:1.0' but resolution result was 'org:foo:1.1'")
    }

    def 'fails when new dependencies appear'() {
        mavenRepo.module('org', 'foo', '1.0').publish()
        mavenRepo.module('org', 'bar', '1.0').publish()

        buildFile << """
dependencyLocking {
    lockAllConfigurations()
}

repositories {
    maven {
        name 'repo'
        url '${mavenRepo.uri}'
    }
}
configurations {
    lockedConf
}

dependencies {
    lockedConf 'org:foo:1.+'
    lockedConf 'org:bar:1.+'
}
"""

        lockfileFixture.createLockfile('lockedConf',['org:foo:1.0'])

        when:
        fails 'dependencies'

        then:
        failure.assertHasCause("Dependency lock state for configuration 'lockedConf' is out of date:: Resolved 'org:bar:1.0' which is not part of the lock state")
    }

    def 'counts dependencies with multiple paths as one instance'() {
        def foo = mavenRepo.module('org', 'foo', '1.0').publish()
        mavenRepo.module('org', 'bar', '1.0').dependsOn(foo).publish()

        buildFile << """
dependencyLocking {
    lockAllConfigurations()
}

repositories {
    maven {
        name 'repo'
        url '${mavenRepo.uri}'
    }
}
configurations {
    lockedConf
}

dependencies {
    lockedConf 'org:foo:1.+'
    lockedConf 'org:bar:1.+'
}
"""

        lockfileFixture.createLockfile('lockedConf',['org:foo:1.0', 'org:bar:1.0'])

        when:
        succeeds 'dependencies'

        then:
        outputContains("org:foo:1.0")
    }

    def 'does not write duplicates in the lockfile'() {
        def foo = mavenRepo.module('org', 'foo', '1.0').publish()
        mavenRepo.module('org', 'bar', '1.0').dependsOn(foo).publish()

        buildFile << """
dependencyLocking {
    lockAllConfigurations()
}

repositories {
    maven {
        name 'repo'
        url '${mavenRepo.uri}'
    }
}
configurations {
    lockedConf
}

dependencies {
    lockedConf 'org:foo:1.+'
    lockedConf 'org:bar:1.+'
}
"""

        when:
        succeeds 'dependencies', '--write-locks'

        then:
        lockfileFixture.verifyLockfile('lockedConf', ['org:foo:1.0', 'org:bar:1.0'])
    }
}
