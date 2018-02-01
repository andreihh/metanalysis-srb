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

import org.metanalysis.core.model.ProjectEdit
import org.metanalysis.core.model.SourceNode.Companion.ENTITY_SEPARATOR
import org.metanalysis.core.repository.PersistentRepository

fun main(args: Array<String>) {
    val repository = PersistentRepository.load() ?: return
    val changes = hashMapOf<String, Int>()
    val jointChanges = hashMapOf<String, HashMap<Pair<String, String>, Int>>()

    for (transaction in repository.getHistory()) {
        val editedIds = transaction.edits.map(ProjectEdit::id)
        val editedIdsBySource =
            editedIds.groupBy { it.substringBefore(ENTITY_SEPARATOR) }

        for (id in editedIds) {
            changes[id] = (changes[id] ?: 0) + 1
        }

        for ((path, ids) in editedIdsBySource) {
            val pairChanges = jointChanges[path] ?: hashMapOf()
            for (id1 in ids) {
                for (id2 in ids) {
                    if (id1 >= id2) continue
                    pairChanges[id1 to id2] = (pairChanges[id1 to id2] ?: 0) + 1
                }
            }
            jointChanges[path] = pairChanges
        }
    }

    for ((path, edges) in jointChanges) {
        println(path)
        for ((edge, cost) in edges) {
            val (id1, id2) = edge
            val weight = 1F * cost / maxOf(changes[id1] ?: 0, changes[id2] ?: 0)
            println("- ($id1, $id2): $weight")
        }
    }
}
