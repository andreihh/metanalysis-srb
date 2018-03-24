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

import org.metanalysis.core.repository.Transaction
import org.metanalysis.srb.Graph.Subgraph

class SingleResponsibilityBreakersAnalyzer(
    maxChangeSet: Int,
    minRevisions: Int,
    minCoupling: Double,
    private val minBlobDensity: Double,
    private val maxAntiCoupling: Double,
    private val minAntiBlobSize: Int
) {
    private val historyAnalyzer =
        HistoryAnalyzer(maxChangeSet, minRevisions, minCoupling)

    init {
        require(minBlobDensity >= 0.0) {
            "Invalid blob density '$minBlobDensity'!"
        }
        require(maxAntiCoupling >= 0.0) {
            "Invalid coupling '$maxAntiCoupling'!"
        }
        require(minAntiBlobSize > 0) {
            "Invalid anti blob size '$minAntiBlobSize'!"
        }
    }

    fun analyze(history: Iterable<Transaction>): Report {
        val graphs = historyAnalyzer.analyze(history).graphs.map { graph ->
            graph.filterNodes { (label, _) -> label.startsWith(graph.label) }
        }
        val coloredGraphs = mutableListOf<ColoredGraph>()
        val files = mutableListOf<FileReport>()
        for (graph in graphs) {
            val blobs = graph.findBlobs(minBlobDensity)
            val antiBlob = graph.findAntiBlob(maxAntiCoupling, minAntiBlobSize)
            coloredGraphs += graph.colorNodes(blobs, antiBlob)
            files += FileReport(graph.label, blobs, antiBlob)
        }
        files.sortByDescending(FileReport::value)
        return Report(files, coloredGraphs)
    }

    data class Report(
        val files: List<FileReport>,
        val coloredGraphs: List<ColoredGraph>
    )

    data class FileReport(
        val file: String,
        val blobs: List<Subgraph>,
        val antiBlob: Subgraph?
    ) {
        val category: String = "SOLID Breakers"
        val name: String = "Single Responsibility Breakers"
        val value: Int = blobs.size + if (antiBlob != null) 1 else 0
    }
}

private fun Graph.colorNodes(
    blobs: List<Subgraph>,
    antiBlob: Subgraph?
): ColoredGraph {
    val groups = blobs.map(Subgraph::nodes) + listOfNotNull(antiBlob?.nodes)
    return colorNodes(groups)
}
