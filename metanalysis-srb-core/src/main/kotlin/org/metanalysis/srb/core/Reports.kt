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

data class Report(
    val files: List<FileReport>,
    val graphs: List<Graph>
)

data class FileReport(
    val file: String,
    val components: List<Set<String>>,
    val blobs: List<Graph.Subgraph>,
    val types: List<TypeReport>
) {

    val value: Int =
        if (components.graphSize > MAX_GRAPH_SIZE) components.graphSize
        else blobs.size
}

data class TypeReport(
    val name: String,
    val components: List<Set<String>>,
    val blobs: List<Graph.Subgraph>,
    val types: List<TypeReport>
) {

    val value: Int =
        if (components.graphSize > MAX_GRAPH_SIZE) components.graphSize
        else blobs.size
}

private val List<Set<String>>.graphSize: Int get() = sumBy(Set<String>::size)
