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

import org.metanalysis.core.model.AddNode
import org.metanalysis.core.model.EditFunction
import org.metanalysis.core.model.EditVariable
import org.metanalysis.core.model.Function
import org.metanalysis.core.model.Project
import org.metanalysis.core.model.RemoveNode
import org.metanalysis.core.model.SourceEntity
import org.metanalysis.core.model.SourceNode
import org.metanalysis.core.model.SourceNode.Companion.ENTITY_SEPARATOR
import org.metanalysis.core.model.SourceUnit
import org.metanalysis.core.model.Type
import org.metanalysis.core.model.parentId
import org.metanalysis.core.model.walkSourceTree
import org.metanalysis.core.repository.Transaction
import org.metanalysis.srb.core.Graph.Edge
import org.metanalysis.srb.core.Graph.Node

class HistoryVisitor private constructor(options: Options) {
    private val publicOnly = options.publicOnly
    private val minCoupling = options.minCoupling
    private val minRevisions = options.minRevisions
    private val minBlobSize = options.minBlobSize
    private val minBlobDensity = options.minBlobDensity

    private val project = Project.empty()
    private val changesByParent = hashMapOf<String, HashMap<String, Int>>()
    private val jointChangesByParent =
        hashMapOf<String, HashMap<Pair<String, String>, Int>>()

    private fun getParentId(id: String): String =
        project.get<SourceEntity>(id).parentId

    private fun visit(edit: AddNode): Set<String> {
        val addedNodes = edit.node.walkSourceTree()
        val addedFunctions = addedNodes.filterIsInstance<Function>()
        return addedFunctions.map(Function::id).toSet()
    }

    private fun visit(edit: RemoveNode): Set<String> {
        val removedNode = project.get<SourceNode>(edit.id)
        val removedNodes = removedNode.walkSourceTree()
        val removedFunctions = removedNodes.filterIsInstance<Function>()
        val removedIds = removedNodes.map(SourceNode::id).toSet()

        for (function in removedFunctions) {
            val parentId = function.parentId

            val changes = changesByParent.getValue(parentId)
            changes -= function.id

            val jointChanges = jointChangesByParent.getValue(parentId)
            val toRemove = jointChanges.keys
                .filter { (id1, id2) -> id1 in removedIds || id2 in removedIds }
            jointChanges -= toRemove
        }

        changesByParent -= removedIds
        jointChangesByParent -= removedIds
        return removedFunctions.map(Function::id).toSet()
    }

    private fun visit(transaction: Transaction): Map<String, List<String>> {
        val editedIds = hashSetOf<String>()
        for (edit in transaction.edits) {
            when (edit) {
                is AddNode -> editedIds += visit(edit)
                is RemoveNode -> editedIds -= visit(edit)
                is EditFunction -> editedIds += edit.id
                is EditVariable -> {
                    val parent = project.get<SourceNode>(getParentId(edit.id))
                    if (parent is Function) {
                        editedIds += parent.id
                    }
                }
            }
            project.apply(edit)
        }
        return editedIds.groupBy(::getParentId)
    }

    private fun analyze(transaction: Transaction) {
        val editedIdsByParent = visit(transaction)
        for ((parent, editedIds) in editedIdsByParent) {
            val changes = changesByParent.getOrPut(parent, ::HashMap)
            val jointChanges = jointChangesByParent.getOrPut(parent, ::HashMap)

            for (id in editedIds) {
                changes[id] = (changes[id] ?: 0) + 1
            }
            for (id1 in editedIds) {
                for (id2 in editedIds) {
                    if (id1 >= id2) continue
                    val pair = id1 to id2
                    jointChanges[pair] = (jointChanges[pair] ?: 0) + 1
                }
            }
        }
    }

    private fun isPublic(id: String): Boolean =
        VisibilityAnalyzer.isPublic(project, id)

    private fun takeNode(id: String): Boolean =
        !publicOnly || isPublic(id)

    private fun takeEdge(edge: Graph.Edge): Boolean {
        val (id1, id2, coupling, revisions) = edge
        return (!publicOnly || (isPublic(id1) && isPublic(id2)))
            && coupling >= minCoupling
            && revisions >= minRevisions
    }

    private fun aggregateGraphs(): HashMap<String, Graph> {
        val graphs = hashMapOf<String, Graph>()
        for ((parent, changesByPair) in jointChangesByParent) {
            fun String.label(): String =
                removePrefix("$parent$ENTITY_SEPARATOR")

            val changesById = changesByParent.getValue(parent)
            val nodes = changesById.keys
                .filter(::takeNode)
                .map { Node(it.label()) }
                .toSet()
            val edges = changesByPair.map { (pair, jointCount) ->
                val (id1, id2) = pair
                val countId1 = changesById.getValue(id1)
                val countId2 = changesById.getValue(id2)
                val revisions = countId1 + countId2 - jointCount
                val coupling = 1.0 * jointCount / revisions
                Edge(id1.label(), id2.label(), coupling, revisions)
            }.filter(::takeEdge)
            graphs[parent] = Graph(parent, nodes, edges)
        }
        return graphs
    }

    private fun aggregate(
        graphs: HashMap<String, Graph>,
        type: Type
    ): TypeReport {
        val types = type.members
            .filterIsInstance<Type>()
            .map { aggregate(graphs, it) }
            .sortedByDescending(TypeReport::value)
        val graph = graphs[type.id]
        val components = graph?.findComponents().orEmpty()
        val blobs = graph?.findBlobs(minBlobSize, minBlobDensity).orEmpty()
        if (graph != null) {
            graphs[type.id] = graph.colorNodes(blobs)
        }
        return TypeReport(type.name, components, blobs, types)
    }

    private fun aggregate(
        graphs: HashMap<String, Graph>,
        unit: SourceUnit
    ): FileReport {
        val types = unit.entities
            .filterIsInstance<Type>()
            .map { aggregate(graphs, it) }
            .sortedByDescending(TypeReport::value)
        val graph = graphs[unit.id]
        val components = graph?.findComponents().orEmpty()
        val blobs = graph?.findBlobs(minBlobSize, minBlobDensity).orEmpty()
        if (graph != null) {
            graphs[unit.id] = graph.colorNodes(blobs)
        }
        return FileReport(unit.path, components, blobs, types)
    }

    private fun aggregate(): Report {
        val graphs = aggregateGraphs()
        val files = project.sources
            .map { aggregate(graphs, it) }
            .sortedByDescending(FileReport::value)
        return Report(files, graphs.values.toList())
    }

    data class Options(
        val publicOnly: Boolean,
        val minCoupling: Double,
        val minRevisions: Int,
        val minBlobSize: Int,
        val minBlobDensity: Double
    ) {

        init {
            require(minCoupling >= 0.0) { "Invalid coupling '$minCoupling'!" }
            require(minRevisions > 0) { "Invalid revisions '$minRevisions'!" }
            require(minBlobSize > 1) { "Invalid blob size '$minBlobSize'!" }
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
