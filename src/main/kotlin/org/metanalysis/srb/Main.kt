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

import org.metanalysis.core.repository.PersistentRepository

fun printGraph(label: String, graph: Graph) {
    println("strict graph \"$label\" {")
    for (node in graph.nodes) {
        val simpleNode = node.removePrefix(label)
        println("  \"$node\" [label=\"$simpleNode\"];")
    }
    for ((u, v, w) in graph.edges) {
        println("  \"$u\" -- \"$v\" [len=5 weight=$w];")
    }
    println("}")
}

fun main(args: Array<String>) {
    val repository = PersistentRepository.load()
        ?: error("Repository not found!")
    val graphs = HistoryVisitor.visit(repository.getHistory())

    if (args.size == 1) {
        val path = args[0]
        val graph = graphs[path] ?: error("Invalid source path!")
        printGraph(path, graph)
    } else {
        for ((path, graph) in graphs) {
            printGraph(path, graph)
        }
    }
}
