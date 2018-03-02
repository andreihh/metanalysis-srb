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

import org.metanalysis.core.model.SourceNode.Companion.ENTITY_SEPARATOR
import org.metanalysis.core.model.SourceNode.Companion.PATH_SEPARATOR
import org.metanalysis.core.repository.PersistentRepository
import org.metanalysis.core.serialization.JsonModule
import org.metanalysis.srb.core.Graph
import org.metanalysis.srb.core.HistoryVisitor
import org.metanalysis.srb.core.colorNodes
import org.metanalysis.srb.core.filterEdges
import java.io.File
import java.io.OutputStream

private fun printGraph(graph: Graph, out: OutputStream, threshold: Double) {
    JsonModule.serialize(out, graph.filterEdges(threshold))
}

private fun loadRepository() =
    PersistentRepository.load() ?: error("Repository not found!")

fun main(args: Array<String>) {
    val publicOnly = "--public-only" in args
    val threshold = args
        .singleOrNull { it.startsWith("--threshold=") }
        ?.removePrefix("--threshold=")
        ?.toDouble()
        ?: 0.0

    val repository = loadRepository()
    val graphs = HistoryVisitor.visit(repository.getHistory(), publicOnly)

    val directory = File(".metanalysis-srb")
    directory.mkdir()
    for ((path, graph) in graphs) {
        val file = "$path.json"
            .replace(oldChar = PATH_SEPARATOR, newChar = '_')
            .replace(oldChar = ENTITY_SEPARATOR, newChar = '_')
        File(directory, file).outputStream().use { out ->
            printGraph(graph.colorNodes(), out, threshold)
        }
    }

    val histogram = IntArray(20) { 0 }
    graphs.values.flatMap(Graph::edges).map(Graph.Edge::weight).forEach {
        histogram[(5 * it).toInt()]++
    }
    val sum = histogram.sum()
    for (i in histogram.indices) {
        println("[${0.2 * i}, ${0.2 * (i + 1)}): ${1.0 * histogram[i] / sum}")
    }

    graphs.values
        .map { it.filterEdges(threshold) }
        .map(Graph::colorNodes)
        .sortedByDescending {
        it.nodes.maxBy(Graph.Node::color)?.color ?: 0
        }.forEach {
            println("${it.label}: ${it.nodes.maxBy(Graph.Node::color)?.color}")
        }
}
