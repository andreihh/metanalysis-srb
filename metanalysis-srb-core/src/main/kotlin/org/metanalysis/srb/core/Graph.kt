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
    val edges: Set<Edge>
) {

    data class Node(val label: String, val color: Int = 0)

    data class Edge(
        val source: String,
        val target: String,
        val length: Double,
        val weight: Double
    )

    private class DisjointSets {
        private val parent = hashMapOf<String, String>()

        operator fun get(x: String): String {
            var root = x
            while (root in parent) {
                root = parent.getValue(root)
            }
            return root
        }

        fun merge(x: String, y: String): Boolean {
            val rootX = get(x)
            val rootY = get(y)
            if (rootX != rootY) {
                parent[rootX] = rootY
            }
            return rootX != rootY
        }
    }

    fun colorNodesByComponent(): Graph {
        val sets = DisjointSets()
        for (edge in edges.sortedByDescending(Edge::weight)) {
            sets.merge(edge.source, edge.target)
        }

        val newColors = hashMapOf<String, Int>()
        var count = 0
        for ((label, _) in nodes) {
            val root = sets[label]
            if (root !in newColors) {
                newColors[root] = count++
            }
            newColors[label] = newColors.getValue(root)
        }

        val newNodes = nodes.map { (label, _) ->
            Node(label, newColors.getValue(label))
        }.toSet()

        return Graph(label, newNodes, edges)
    }
}
