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
import java.io.File
import java.io.PrintStream

private const val EPS = 1e-4

fun printGraph(
    label: String,
    graph: Graph,
    out: PrintStream,
    thresholdPercent: Int = 0
) {
    require(thresholdPercent in 0..100) { "Invalid threshold percent!" }

    out.println("strict graph {")
    out.println("  graph [label=\"$label\"];")
    out.println("  node [shape=box];")

    for (node in graph.nodes) {
        val simpleNode = node.removePrefix("$label$ENTITY_SEPARATOR")
        out.println("  \"$node\" [label=\"$simpleNode\"];")
    }

    val edges = graph.edges.sortedByDescending(Graph.Edge::length)
    val threshold =
        if (thresholdPercent == 0 || edges.isEmpty()) 1.0 * Int.MAX_VALUE
        else edges[(thresholdPercent - 1) * edges.size / 100].length

    for ((u, v, l, w) in edges.dropWhile { threshold - it.length < EPS }) {
        out.println("  \"$u\" -- \"$v\" [len=%.4f, weight=%.4f];".format(l, w))
    }

    out.println("}")
}

private fun loadRepository() =
    PersistentRepository.load() ?: error("Repository not found!")

fun main(args: Array<String>) {
    val thresholdPercent = args
        .singleOrNull { it.startsWith("--threshold-percent=") }
        ?.removePrefix("--threshold-percent=")
        ?.toInt()
        ?: 0

    val repository = loadRepository()
    val graphs = HistoryVisitor.visit(repository.getHistory())

    val directory = File(".metanalysis-srb")
    directory.mkdir()
    for ((path, graph) in graphs) {
        val file = "$path.gv"
            .replace(oldChar = PATH_SEPARATOR, newChar = '_')
            .replace(oldChar = ENTITY_SEPARATOR, newChar = '_')
        PrintStream(File(directory, file)).use { out ->
            printGraph(path, graph, out, thresholdPercent)
        }
    }
}
