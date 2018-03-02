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

import java.util.PriorityQueue

fun Graph.filterEdges(threshold: Double): Graph {
    require(threshold >= 0.0) { "Invalid threshold '$threshold'!" }
    val newEdges = edges.filter { (_, _, _, w) -> w >threshold }
    return copy(edges = newEdges)
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

private fun Graph.colorNodesByComponent(): Graph {
    val sets = DisjointSets()
    for (edge in edges.sortedByDescending(Graph.Edge::weight)) {
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
        Graph.Node(label, newColors.getValue(label))
    }.toSet()

    return copy(nodes = newNodes)
}

private fun Graph.colorNodesByBlobs(): Graph {
    val newColors = nodes.associateTo(hashMapOf()) { it.label to it.color }

    val blobs = arrayListOf<Set<String>>()
    findBlobs(this, 5, 0.25, blobs)
    var nextColor = nodes.map(Graph.Node::color).max() ?: 0
    for (blob in blobs) {
        nextColor++
        for (node in blob) {
            newColors[node] = nextColor
        }
    }
    val newNodes = nodes.map { (label, _) ->
        Graph.Node(label, newColors.getValue(label))
    }.toSet()

    return copy(nodes = newNodes)
}

fun Graph.colorNodes(): Graph =
    //if (nodes.size < 300) colorNodesByComponent().colorNodesByBlobs()
    //else colorNodesByComponent()
    if (nodes.size < 300) colorNodesByBlobs() else this

private fun findBlob(
    graph: Graph,
    minSize: Int,
    minDensity: Double
): Set<String> {
    require(minSize > 1)
    require(minDensity > 0.0)

    val degrees = hashMapOf<String, Double>()
    val edges = hashMapOf<String, HashSet<Graph.Edge>>()
    val nodes = graph.nodes.map(Graph.Node::label).toHashSet()
    for (edge in graph.edges) {
        val (u, v, _, w) = edge
        degrees[u] = (degrees[u] ?: 0.0) + w
        degrees[v] = (degrees[v] ?: 0.0) + w
        edges.getOrPut(u, ::hashSetOf) += edge
        edges.getOrPut(v, ::hashSetOf) += edge
    }

    val heap = PriorityQueue<String>(maxOf(1, nodes.size)) { u, v ->
        compareValues(degrees[v], degrees[u])
    }
    heap += nodes

    var blob = emptySet<String>()
    var blobDensity = 0.0

    var degreeSum = degrees.values.sum()
    fun density() = degreeSum / (nodes.size * (nodes.size - 1))
    while (nodes.size >= minSize) {
        if (blob.isEmpty() || density() > blobDensity) {
            blob = nodes.toSet()
            blobDensity = density()
        }

        val node = heap.poll()
        for (edge in edges[node].orEmpty()) {
            val (u, v, _, w) = edge
            val other = if (u == node) v else u

            heap -= other
            degrees[other] = degrees.getValue(other) - w
            edges.getValue(other) -= edge
            degreeSum -= 2 * w
            heap += other
        }
        nodes -= node
        degrees -= node
        edges -= node
    }

    /*if (graph.label.endsWith("") && blobDensity >= minDensity) {
        println("Found blob with size ${blob.size} and density $blobDensity!")
        println("Blob nodes: $blob")
        println("Blob edges:")
        graph.edges.filter { (u, v, _, _) -> u in blob && v in blob }
            .forEach(::println)
    }*/
    return if (blobDensity >= minDensity) blob else emptySet()
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
    blobs: ArrayList<Set<String>>
) {
    val blob = findBlob(graph, minSize, minDensity)
    if (blob.isNotEmpty()) {
        blobs.add(blob)
        val remainingGraph = graph - blob
        findBlobs(remainingGraph, minSize, minDensity, blobs)
    }
}
