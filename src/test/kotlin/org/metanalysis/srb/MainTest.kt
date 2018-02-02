/*
 * Copyright 2018 Andrei Heidelbacher <andrei.heidelbacher@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.metanalysis.srb

import org.junit.After
import org.junit.Before
import org.junit.Test
import org.metanalysis.core.repository.PersistentRepository
import org.metanalysis.core.repository.PersistentRepository.Companion.persist
import org.metanalysis.test.core.repository.repository

class MainTest {
    @Before fun setUpRepository() {
        repository {
            transaction("0") {
                addSourceUnit("Main.java") {
                    function("getVersion(String)") {
                        parameter("name") {}
                    }
                }
            }
            transaction("1") {
                addFunction("Main.java:setVersion(String)") {
                    parameter("name") {}
                }
                addType("Main.java:Main") {
                    function("getName()") {}
                }
            }
            transaction("2") {
                editFunction("Main.java:setVersion(String)") {
                    modifiers { +"private" }
                }
                editVariable("Main.java:getVersion(String):name") {
                    modifiers { +"public" }
                }
                removeNode("Main.java:Main")
            }
        }.persist()
    }

    @Test fun `smoke test`() {
        main(emptyArray())
    }

    @After fun cleanUpRepository() {
        PersistentRepository.clean()
    }
}
