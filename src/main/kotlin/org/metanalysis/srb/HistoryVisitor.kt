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

import org.metanalysis.core.model.AddNode
import org.metanalysis.core.model.EditFunction
import org.metanalysis.core.model.Function
import org.metanalysis.core.model.Project
import org.metanalysis.core.model.RemoveNode
import org.metanalysis.core.model.SourceEntity
import org.metanalysis.core.model.SourceNode
import org.metanalysis.core.model.SourceUnit
import org.metanalysis.core.model.sourcePath
import org.metanalysis.core.model.walkSourceTree
import org.metanalysis.core.repository.Transaction
import org.metanalysis.srb.Graph.Edge
import org.metanalysis.srb.Graph.Node

class HistoryVisitor private constructor(options: Options) {
    private val maxChangeSet = options.maxChangeSet
    private val minCoupling = options.minCoupling
    private val minRevisions = options.minRevisions
    private val minBlobSize = options.minBlobSize
    private val minBlobDensity = options.minBlobDensity

    private val project = Project.empty()
    private val changes = hashMapOf<String, Int>()
    private val jointChanges = hashMapOf<String, HashMap<String, Int>>()

    private fun getSourcePath(id: String): String =
        project.get<SourceEntity>(id).sourcePath

    private fun visit(edit: AddNode): Set<String> {
        val addedNodes = edit.node.walkSourceTree()
        val addedFunctions = addedNodes.filterIsInstance<Function>()
        return addedFunctions.map(Function::id).toSet()
    }

    private fun visit(edit: RemoveNode): Set<String> {
        val removedNode = project.get<SourceNode>(edit.id)
        val removedIds = removedNode
            .walkSourceTree()
            .filterIsInstance<Function>()
            .map(Function::id)
            .toSet()

        changes -= removedIds
        for (id in removedIds) {
            for (otherId in jointChanges[id].orEmpty().keys) {
                jointChanges[otherId]?.remove(id)
            }
            jointChanges -= id
        }

        return removedIds
    }

    private fun visit(transaction: Transaction): Set<String> {
        val editedIds = hashSetOf<String>()
        for (edit in transaction.edits) {
            when (edit) {
                is AddNode -> editedIds += visit(edit)
                is RemoveNode -> editedIds -= visit(edit)
                is EditFunction -> editedIds += edit.id
            }
            project.apply(edit)
        }
        return editedIds
    }

    private fun analyze(transaction: Transaction) {
        val editedIds = visit(transaction)
        for (id in editedIds) {
            changes[id] = (changes[id] ?: 0) + 1
        }
        if (transaction.changeSet.size > maxChangeSet) return
        for (id1 in editedIds) {
            for (id2 in editedIds) {
                if (id1 == id2) continue
                val jointChangesWithId1 = jointChanges.getOrPut(id1, ::HashMap)
                jointChangesWithId1[id2] = (jointChangesWithId1[id2] ?: 0) + 1
            }
        }
    }

    private fun takeNode(id: String): Boolean = true

    private fun takeEdge(edge: Graph.Edge): Boolean {
        val (_, _, revisions, coupling) = edge
        return revisions >= minRevisions && coupling >= minCoupling
    }

    private fun aggregateGraphs(): HashMap<String, Graph> {
        val graphs = hashMapOf<String, Graph>()
        val idsByFile = changes.keys
            .groupBy(::getSourcePath)
            .mapValues { (_, ids) -> ids.toSet() }

        for ((path, ids) in idsByFile) {
            val edges = ids.flatMap { id1 ->
                jointChanges[id1].orEmpty()
                    .filter { (id2, _) -> id1 < id2 }
                    .map { (id2, revisions) ->
                        val countId1 = changes.getValue(id1)
                        val countId2 = changes.getValue(id2)
                        val totalCount = countId1 + countId2 - revisions
                        val coupling = 1.0 * revisions / totalCount
                        Edge(id1, id2, revisions, coupling)
                    }
            }.filter(::takeEdge)

            val nodes =
                (ids + edges.flatMap { (id1, id2, _, _) -> listOf(id1, id2) })
                    .filter(::takeNode)
                    .map { id -> Node(id, changes.getValue(id)) }
                    .toSet()

            graphs[path] = Graph(path, nodes, edges)
        }
        return graphs
    }

    private fun aggregate(
        graphs: HashMap<String, Graph>,
        unit: SourceUnit
    ): FileReport {
        val graph = graphs[unit.id]
        val blobs = graph?.findBlobs(minBlobSize, minBlobDensity).orEmpty()
        if (graph != null) {
            graphs[unit.id] = graph.colorNodes(blobs)
        }
        val antiBlob = null
        return FileReport(unit.path, blobs, antiBlob)
    }

    private fun aggregate(): Report {
        val graphs = aggregateGraphs()
        val files = project.sources
            .map { aggregate(graphs, it) }
            .sortedByDescending(FileReport::value)
        return Report(files, graphs.values.toList())
    }

    data class Options(
        val maxChangeSet: Int,
        val minCoupling: Double,
        val minRevisions: Int,
        val minBlobSize: Int,
        val minBlobDensity: Double
    ) {

        init {
            require(maxChangeSet > 0) { "Invalid change set '$maxChangeSet'!" }
            require(minCoupling >= 0.0) { "Invalid coupling '$minCoupling'!" }
            require(minRevisions > 0) { "Invalid revisions '$minRevisions'!" }
            require(minBlobSize > 0) { "Invalid blob size '$minBlobSize'!" }
            require(minBlobDensity >= 0.0) {
                "Invalid blob density '$minBlobDensity'!"
            }
        }
    }

    companion object {
        fun analyze(
            history: Iterable<Transaction>,
            options: Options
        ): Report {
            val visitor = HistoryVisitor(options)
            history.forEach(visitor::analyze)
            return visitor.aggregate()
        }
    }
}
