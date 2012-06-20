/*
 * Copyright 2012 the original author or authors.
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

package org.gradle

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Issue
import spock.lang.Ignore

class BuildSourceConcurrencyTest extends AbstractIntegrationSpec {

    @Ignore("not yet fixed")
    @Issue("http://issues.gradle.org/browse/GRADLE-2032")
    def "can simultaneously run gradle on projects with buildSrc"() {
        given:
        file("buildSrc").mkdir()
        buildFile.text = """
        task blocking << {
            while(!file("block.lock").exists()){
                sleep 100
            }
        }

        task releasing << {
            file("block.lock").createNewFile()
        }
        """
        when:
        executer.withTasks("blocking").start()
        def handleRun2 = executer.withTasks("releasing").start()

        then:
        def finish = handleRun2.waitForFinish()

        finish.error.empty
    }
}
