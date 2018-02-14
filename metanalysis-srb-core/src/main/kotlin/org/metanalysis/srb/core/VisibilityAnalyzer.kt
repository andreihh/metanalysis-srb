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

package org.metanalysis.srb.core

import org.metanalysis.core.model.Project
import org.metanalysis.core.model.sourcePath
import java.util.ServiceLoader

abstract class VisibilityAnalyzer {
    protected abstract fun canProcess(sourcePath: String): Boolean

    protected abstract fun isPublic(project: Project, nodeId: String): Boolean

    companion object {
        private val analyzers =
            ServiceLoader.load(VisibilityAnalyzer::class.java)

        private fun findAnalyzer(
            project: Project,
            id: String
        ): VisibilityAnalyzer? {
            val sourcePath = project[id]?.sourcePath ?: return null
            return analyzers.find { it.canProcess(sourcePath) }
        }

        fun isPublic(project: Project, nodeId: String): Boolean =
            findAnalyzer(project, nodeId)?.isPublic(project, nodeId) == true
    }
}
