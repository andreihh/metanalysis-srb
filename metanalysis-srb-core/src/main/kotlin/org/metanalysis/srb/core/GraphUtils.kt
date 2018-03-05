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

import org.metanalysis.srb.core.Graph.Edge
import org.metanalysis.srb.core.Graph.Node
import org.metanalysis.srb.core.Graph.Subgraph
import java.util.PriorityQueue

internal const val MAX_GRAPH_SIZE: Int = 300

internal fun Graph.findComponents(): List<Set<String>> {
    val sets = DisjointSets()
    for (edge in edges) {
        sets.merge(edge.source, edge.target)
    }
    val components = hashMapOf<String, HashSet<String>>()
    for (node in nodes) {
        val root = sets[node.label]
        components.getOrPut(root, ::hashSetOf) += node.label
    }
    return components.values.toList()
}

internal fun Graph.findBlobs(
    minSize: Int,
    minDensity: Double
): List<Subgraph> {
    require(minSize > 1) { "Invalid blob size threshold '$minSize'!" }
    require(minDensity >= 0) { "Invalid blob density threshold '$minDensity'!" }
    if (nodes.size > MAX_GRAPH_SIZE) return emptyList()
    val blobs = arrayListOf<Subgraph>()
    findBlobs(this, minSize, minDensity, blobs)
    return blobs
}

internal fun Graph.colorNodes(
    components: List<Set<String>>,
    blobs: List<Subgraph>
): Graph {
    val newColors = hashMapOf<String, Int>()
    val colorGroups = components + blobs.map(Subgraph::nodes)
    var color = 0
    for (group in colorGroups) {
        color++
        for (node in group) {
            newColors[node] = color
        }
    }

    val newNodes = nodes.map { (label, _) ->
        Graph.Node(label = label, color = newColors[label] ?: 0)
    }
    return copy(nodes = newNodes.toSet())
}

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

private fun findBlob(
    graph: Graph,
    minSize: Int,
    minDensity: Double
): Subgraph? {
    val degrees = hashMapOf<String, Double>()
    val edges = hashMapOf<String, HashSet<Edge>>()
    val nodes = graph.nodes.map(Node::label).toHashSet()
    for (edge in graph.edges) {
        val (u, v, c, _) = edge
        degrees[u] = (degrees[u] ?: 0.0) + c
        degrees[v] = (degrees[v] ?: 0.0) + c
        edges.getOrPut(u, ::hashSetOf) += edge
        edges.getOrPut(v, ::hashSetOf) += edge
    }

    val heap = PriorityQueue<String>(maxOf(1, nodes.size)) { u, v ->
        compareValues(degrees[u], degrees[v])
    }
    heap += nodes

    var blob: Set<String>? = null
    var blobDensity = 0.0

    var degreeSum = degrees.values.sum()
    fun density() = degreeSum / (nodes.size * (nodes.size - 1))

    while (nodes.size >= minSize) {
        if (blob == null || density() > blobDensity) {
            blob = nodes.toSet()
            blobDensity = density()
        }

        val node = heap.poll()
        for (edge in edges[node].orEmpty()) {
            val (u, v, c, _) = edge
            val other = if (u == node) v else u

            heap -= other
            degrees[other] = degrees.getValue(other) - c
            edges.getValue(other) -= edge
            degreeSum -= 2 * c
            heap += other
        }
        nodes -= node
        degrees -= node
        edges -= node
    }

    return if (blob == null || blobDensity < minDensity) null
    else Subgraph(blob, blobDensity)
}

private operator fun Graph.minus(nodes: Set<String>): Graph {
    val newNodes = this.nodes.filterNot { (label, _) -> label in nodes }
    val newEdges = edges.filterNot { (u, v, _, _) -> u in nodes || v in nodes }
    return copy(nodes = newNodes.toSet(), edges = newEdges)
}

private tailrec fun findBlobs(
    graph: Graph,
    minSize: Int,
    minDensity: Double,
    blobs: ArrayList<Subgraph>
) {
    val blob = findBlob(graph, minSize, minDensity)
    if (blob != null) {
        blobs.add(blob)
        val remainingGraph = graph - blob.nodes
        findBlobs(remainingGraph, minSize, minDensity, blobs)
    }
}
