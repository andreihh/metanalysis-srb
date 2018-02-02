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
import org.metanalysis.core.model.EditVariable
import org.metanalysis.core.model.Function
import org.metanalysis.core.model.Project
import org.metanalysis.core.model.RemoveNode
import org.metanalysis.core.model.SourceEntity
import org.metanalysis.core.model.SourceNode
import org.metanalysis.core.model.parentId
import org.metanalysis.core.model.walkSourceTree
import org.metanalysis.core.repository.Transaction
import kotlin.math.sqrt

class HistoryVisitor private constructor() {
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
            val changes = changesByParent[parent] ?: hashMapOf()
            val jointChanges = jointChangesByParent[parent] ?: hashMapOf()

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

            changesByParent[parent] = changes
            jointChangesByParent[parent] = jointChanges
        }
    }

    private fun aggregate(): Map<String, Graph> {
        val graphs = hashMapOf<String, Graph>()
        for ((parent, changesByPair) in jointChangesByParent) {
            val changesById = changesByParent.getValue(parent)
            val nodes = changesById.keys
            val edges = hashSetOf<Graph.Edge>()
            for ((pair, jointChanges) in changesByPair) {
                val (id1, id2) = pair
                val changesId1 = changesById.getValue(id1)
                val changesId2 = changesById.getValue(id2)
                val totalChanges = changesId1 + changesId2 - jointChanges
                val cost =
                    1.0 * jointChanges / totalChanges * sqrt(1.0 * totalChanges)
                val weight = 1.0 / (cost + 1)
                edges += Graph.Edge(id1, id2, weight)
            }
            graphs[parent] = Graph(nodes, edges)
        }
        return graphs
    }

    companion object {
        fun visit(history: Iterable<Transaction>): Map<String, Graph> {
            val visitor = HistoryVisitor()
            for (transaction in history) {
                visitor.analyze(transaction)
            }
            return visitor.aggregate()
        }
    }
}
