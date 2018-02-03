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

fun printGraph(
    label: String,
    graph: Graph,
    out: PrintStream,
    scale: Boolean,
    length: Int
) {
    out.println("strict graph {")
    out.println("  graph [label=\"$label\"];")
    out.println("  node [shape=box];")
    out.println("  edge [len=$length];")
    if (scale) {
        out.println("  overlap=scale;")
    }
    for (node in graph.nodes) {
        val simpleNode = node.removePrefix("$label$ENTITY_SEPARATOR")
        out.println("  \"$node\" [label=\"$simpleNode\"];")
    }
    for ((u, v, w) in graph.edges) {
        out.println("  \"$u\" -- \"$v\" [weight=%.5f];".format(w))
    }
    out.println("}")
}

fun main(args: Array<String>) {
    val scale = "--scale" in args
    val length = args
        .find { it.startsWith(prefix = "--length=") }
        ?.removePrefix("--length=")
        ?.toInt()
        ?: if (scale) 1 else 5

    val repository = PersistentRepository.load()
        ?: error("Repository not found!")
    val graphs = HistoryVisitor.visit(repository.getHistory())
    val directory = File(".metanalysis-srb")
    directory.mkdir()
    for ((path, graph) in graphs) {
        val file = "$path.gv"
            .replace(oldChar = PATH_SEPARATOR, newChar = '_')
            .replace(oldChar = ENTITY_SEPARATOR, newChar = '_')
        PrintStream(File(directory, file)).use { out ->
            printGraph(path, graph, out, scale, length)
        }
    }
}
