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

data class Graph(
    val label: String,
    val nodes: Set<Node>,
    val edges: List<Edge>
) {

    data class Node(val label: String, val revisions: Int, val color: Int = 0)

    data class Edge(
        val source: String,
        val target: String,
        val revisions: Int,
        val coupling: Double
    )

    data class Subgraph(val nodes: Set<String>, val density: Double)
}
