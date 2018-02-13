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
import java.io.File
import java.io.OutputStream

private const val EPS = 1e-4

fun printGraph(graph: Graph, out: OutputStream, thresholdPercent: Int = 0) {
    require(thresholdPercent in 0..100) { "Invalid threshold percent!" }

    val links = graph.links.sortedByDescending(Graph.Link::value)
    val threshold =
        if (thresholdPercent == 0 || links.isEmpty()) 1.0 * Int.MAX_VALUE
        else links[(thresholdPercent - 1) * links.size / 100].value
    val newLinks = links.dropWhile { threshold - it.value < EPS }.toSet()
    val newGraph = graph.copy(links = newLinks)

    JsonModule.serialize(out, newGraph)
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
        val file = "$path.json"
            .replace(oldChar = PATH_SEPARATOR, newChar = '_')
            .replace(oldChar = ENTITY_SEPARATOR, newChar = '_')
        File(directory, file).outputStream().use { out ->
            printGraph(graph, out, thresholdPercent)
        }
    }
}
