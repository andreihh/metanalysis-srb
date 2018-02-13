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
import org.metanalysis.core.model.SourceNode.Companion.ENTITY_SEPARATOR
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

    private fun isPublic(id: String): Boolean =
        project.get<Function?>(id)?.modifiers?.contains("public") == true

    private fun aggregate(publicOnly: Boolean): Map<String, Graph> {
        val graphs = hashMapOf<String, Graph>()
        for ((parent, changesByPair) in jointChangesByParent) {
            fun String.simpleId(): String =
                removePrefix("$parent$ENTITY_SEPARATOR")

            val changesById = changesByParent.getValue(parent)
            val group = 1
            val nodes = changesById.keys
                .filter { !publicOnly || isPublic(it) }
                .map { Graph.Node(it.simpleId(), group) }
                .toSet()
            val links = hashSetOf<Graph.Link>()
            for ((pair, jointCount) in changesByPair) {
                val (id1, id2) = pair
                if (publicOnly && (!isPublic(id1) || !isPublic(id2))) continue
                val countId1 = changesById.getValue(id1)
                val countId2 = changesById.getValue(id2)
                val totalCount = countId1 + countId2 - jointCount
                val length = 1.0 * totalCount / jointCount
                val weight = sqrt(1.0 * jointCount) / length
                links += Graph.Link(id1.simpleId(), id2.simpleId(), weight)
            }
            graphs[parent] = Graph(nodes, links)
        }
        return graphs
    }

    companion object {
        fun visit(
            history: Iterable<Transaction>,
            publicOnly: Boolean = false
        ): Map<String, Graph> {
            val visitor = HistoryVisitor()
            for (transaction in history) {
                visitor.analyze(transaction)
            }
            return visitor.aggregate(publicOnly)
        }
    }
}
