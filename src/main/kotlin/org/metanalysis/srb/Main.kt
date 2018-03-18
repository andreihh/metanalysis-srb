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
import org.metanalysis.core.repository.Repository
import org.metanalysis.core.serialization.JsonModule
import org.metanalysis.srb.core.HistoryVisitor.Companion.analyze
import org.metanalysis.srb.core.HistoryVisitor.Options
import java.io.File

private fun loadRepository(): Repository =
    PersistentRepository.load() ?: error("Repository not found!")

fun main(args: Array<String>) {
    val publicOnly = "--public-only" in args
    val minCoupling = args
        .singleOrNull { it.startsWith("--min-coupling=") }
        ?.removePrefix("--min-coupling=")
        ?.toDouble()
        ?: 0.1
    val maxChangeSet = args
        .singleOrNull { it.startsWith("--max-change-set=") }
        ?.removePrefix("--max-change-set=")
        ?.toInt()
        ?: 50
    val minRevisions = args
        .singleOrNull { it.startsWith("--min-revisions=") }
        ?.removePrefix("--min-revisions=")
        ?.toInt()
        ?: 5
    val minBlobSize = args
        .singleOrNull { it.startsWith("--min-blob-size=") }
        ?.removePrefix("--min-blob-size=")
        ?.toInt()
        ?: 1
    val minBlobDensity = args
        .singleOrNull { it.startsWith("--min-blob-density=") }
        ?.removePrefix("--min-blob-density=")
        ?.toDouble()
        ?: 2.5

    val options = Options(
        publicOnly = publicOnly,
        maxChangeSet = maxChangeSet,
        minCoupling = minCoupling,
        minRevisions = minRevisions,
        minBlobSize = minBlobSize,
        minBlobDensity = minBlobDensity
    )

    val repository = loadRepository()
    val report = analyze(repository.getHistory(), options)
    JsonModule.serialize(System.out, report.files)
    for (graph in report.graphs) {
        val path = graph.label.replace(ENTITY_SEPARATOR, PATH_SEPARATOR)
        val directory = File(".metanalysis-srb")
        val graphDirectory = File(directory, path)
        graphDirectory.mkdirs()
        val graphFile = File(graphDirectory, "graph.json")
        graphFile.outputStream().use { out ->
            JsonModule.serialize(out, graph)
        }
    }
}
