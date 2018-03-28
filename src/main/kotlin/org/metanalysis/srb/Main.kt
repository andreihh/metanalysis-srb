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
import org.metanalysis.core.serialization.JsonModule
import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.ExecutionException
import picocli.CommandLine.Option
import picocli.CommandLine.RunAll
import picocli.CommandLine.defaultExceptionHandler
import java.io.File
import kotlin.system.exitProcess

@Command(
    name = "metanalysis-srb",
    mixinStandardHelpOptions = true,
    version = ["0.2"],
    description = [],
    showDefaultValues = true
)
class Main : Runnable {
    @Option(
        names = ["--max-change-set"],
        description = ["the maximum number of changed files in a revision"]
    )
    private var maxChangeSet: Int = 100

    @Option(
        names = ["--min-revisions"],
        description = [
            "the minimum number of revisions of a method or coupling relation"
        ]
    )
    private var minRevisions: Int = 5

    @Option(
        names = ["--min-coupling"],
        description = ["the minimum temporal coupling between two methods"]
    )
    private var minCoupling: Double = 0.1

    @Option(
        names = ["--min-blob-density"],
        description = [
            "the minimum average degree (sum of coupling) of methods in a blob"
        ]
    )
    private var minBlobDensity: Double = 2.5

    @Option(
        names = ["--max-anti-coupling"],
        description = [
            "the maximum degree (sum of coupling) of a method in an anti-blob"
        ]
    )
    private var maxAntiCoupling: Double = 0.5

    @Option(
        names = ["--min-anti-blob-size"],
        description = ["the minimum size of of an anti-blob"]
    )
    private var minAntiBlobSize: Int = 10

    override fun run() {
        val analyzer = SingleResponsibilityBreakersAnalyzer(
            maxChangeSet = maxChangeSet,
            minRevisions = minRevisions,
            minCoupling = minCoupling,
            minBlobDensity = minBlobDensity,
            maxAntiCoupling = maxAntiCoupling,
            minAntiBlobSize = minAntiBlobSize
        )
        val repository = PersistentRepository.load()
            ?: error("Repository not found!")
        val report = analyzer.analyze(repository.getHistory())
        JsonModule.serialize(System.out, report.files)
        for (coloredGraph in report.coloredGraphs) {
            val directory = File(".metanalysis-srb")
            val graphDirectory = File(directory, coloredGraph.graph.label)
            graphDirectory.mkdirs()
            val graphFile = File(graphDirectory, "graph.json")
            graphFile.outputStream().use { out ->
                JsonModule.serialize(out, coloredGraph)
            }
        }
    }
}

fun main(vararg args: String) {
    val cmd = CommandLine(Main())
    val exceptionHandler = defaultExceptionHandler().andExit(1)
    try {
        cmd.parseWithHandlers(RunAll(), exceptionHandler , *args)
    } catch (e: ExecutionException) {
        System.err.println(e.message)
        e.printStackTrace(System.err)
        exitProcess(1)
    }
}
