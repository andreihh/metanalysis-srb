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

import org.metanalysis.srb.Graph.Edge
import org.metanalysis.srb.Graph.Node
import org.metanalysis.srb.Graph.Subgraph
import java.util.PriorityQueue

internal const val MAX_GRAPH_SIZE: Int = 300

internal fun Graph.findBlobs(minDensity: Double): List<Subgraph> {
    require(minDensity >= 0.0) { "Invalid blob density '$minDensity'!" }
    if (nodes.size > MAX_GRAPH_SIZE) {
        return nodes.map { (label, _, _) ->
            Subgraph(nodes = setOf(label), density = 0.0)
        }
    }
    val blobs = arrayListOf<Subgraph>()
    for (component in findComponents()) {
        findBlobs(this.intersect(component), minDensity, blobs)
    }
    return blobs
}

internal fun Graph.findAntiBlob(
    maxCoupling: Double,
    minSize: Int
): Set<String>? {
    require(maxCoupling >= 0.0) { "Invalid anti-blob coupling '$maxCoupling'!" }
    require(minSize > 0) { "Invalid anti-blob size '$minSize'!" }
    val coupling = hashMapOf<String, Double>()
    for ((u, v, _, c) in edges) {
        coupling[u] = (coupling[u] ?: 0.0) + c
        coupling[v] = (coupling[v] ?: 0.0) + c
    }
    val antiBlob = hashSetOf<String>()
    for ((label, _) in nodes) {
        if ((coupling[label] ?: 0.0) <= maxCoupling) {
            antiBlob += label
        }
    }
    return if (antiBlob.size >= minSize) antiBlob else null
}

internal fun Graph.colorNodes(
    blobs: List<Subgraph>,
    antiBlob: Set<String>?
): Graph {
    val newColors = hashMapOf<String, Int>()
    val colorGroups = blobs.map(Subgraph::nodes) + listOfNotNull(antiBlob)
    var color = 0
    for (group in colorGroups) {
        color++
        for (node in group) {
            newColors[node] = color
        }
    }

    val newNodes = nodes.map { (label, revisions, _) ->
        val newColor = newColors[label] ?: 0
        Graph.Node(label, revisions, newColor)
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

private fun Graph.findComponents(): List<Set<String>> {
    val sets = DisjointSets()
    for (edge in edges) {
        sets.merge(edge.source, edge.target)
    }
    val components = hashMapOf<String, HashSet<String>>()
    for (node in nodes) {
        val root = sets[node.label]
        components.getOrPut(root, ::HashSet) += node.label
    }
    return components.values.toList()
}

private fun findBlob(
    graph: Graph,
    minDensity: Double
): Subgraph? {
    val degrees = hashMapOf<String, Double>()
    val edges = hashMapOf<String, HashSet<Edge>>()
    val nodes = graph.nodes.map(Node::label).toHashSet()
    for (edge in graph.edges) {
        val (u, v, _, c) = edge
        degrees[u] = (degrees[u] ?: 0.0) + c
        degrees[v] = (degrees[v] ?: 0.0) + c
        edges.getOrPut(u, ::HashSet) += edge
        edges.getOrPut(v, ::HashSet) += edge
    }

    val heap = PriorityQueue<String>(nodes.size.coerceAtLeast(1)) { u, v ->
        compareValues(degrees[u], degrees[v])
    }
    heap += nodes

    var blob: Set<String>? = null
    var blobDensity = 0.0

    var degreeSum = degrees.values.sum()
    fun density() = degreeSum / nodes.size

    while (nodes.isNotEmpty()) {
        if (blob == null || density() > blobDensity) {
            blob = nodes.toSet()
            blobDensity = density()
        }

        val node = heap.poll()
        for (edge in edges[node].orEmpty()) {
            val (u, v, _, c) = edge
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

private fun Graph.intersect(nodes: Set<String>): Graph {
    val newNodes = this.nodes.filter { (label, _) -> label in nodes }
    val newEdges = edges.filter { (u, v, _, _) -> u in nodes && v in nodes }
    return copy(nodes = newNodes.toSet(), edges = newEdges)
}

private fun findBlobs(
    graph: Graph,
    minDensity: Double,
    blobs: ArrayList<Subgraph>
) {
    val blob = findBlob(graph, minDensity)
    if (blob != null) {
        blobs.add(blob)
        val remainingGraph = graph - blob.nodes
        findBlobs(remainingGraph, minDensity, blobs)
    }
}
