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

package org.metanalysis.srb.java

import org.metanalysis.core.model.Function
import org.metanalysis.core.model.Project
import org.metanalysis.core.model.SourceNode
import org.metanalysis.core.model.Type
import org.metanalysis.core.model.Variable
import org.metanalysis.core.model.parentId
import org.metanalysis.srb.core.VisibilityAnalyzer

class JavaAnalyzer : VisibilityAnalyzer() {
    override fun canProcess(sourcePath: String): Boolean =
        sourcePath.endsWith(".java")

    private val SourceNode?.isInterface: Boolean
        get() = this is Type && INTERFACE in modifiers

    override fun isPublic(project: Project, nodeId: String): Boolean {
        val node = project[nodeId]
        return when (node) {
            is Type -> PUBLIC in node.modifiers
            is Variable -> PUBLIC in node.modifiers
            is Function ->
                PUBLIC in node.modifiers || project[node.parentId].isInterface
            else -> false
        }
    }

    companion object {
        const val PUBLIC: String = "public"
        const val INTERFACE: String = "interface"
    }
}
