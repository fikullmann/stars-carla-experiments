/*
 * Copyright 2023 The STARS Carla Experiments Authors
 * SPDX-License-Identifier: Apache-2.0
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

package tools.aqua.stars.carla.experiments

import java.io.File
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.zip.ZipFile
import kotlin.io.path.name
import tools.aqua.stars.core.evaluation.TSCEvaluation
import tools.aqua.stars.core.metric.metrics.evaluation.*
import tools.aqua.stars.core.metric.metrics.postEvaluation.*
import tools.aqua.stars.data.av.Actor
import tools.aqua.stars.data.av.Segment
import tools.aqua.stars.data.av.TickData
import tools.aqua.stars.data.av.metrics.AverageVehiclesInEgosBlockMetric
import tools.aqua.stars.import.carla.CarlaSimulationRunsWrapper
import tools.aqua.stars.import.carla.loadSegments

fun main() {
  if (!File("stars-reproduction-source").exists()) {
    println("The experiments data is missing.")
    if (!File("stars-reproduction-source.zip").exists()) {
      println("The experiments data zip file is missing.")
      if (DOWNLOAD_EXPERIMENTS_DATA) {
        println("Start with downloading the experiments data. This may take a while.")
        downloadExperimentsData()
        println("Finished downloading.")
      }
    }
    println("Extract experiments data from zip file.")
    extractZipFile(zipFile = File("stars-reproduction-source.zip"), extractTo = File("."), true)
  }

  val tsc = tsc()

  val simulationRunsWrappers = getSimulationRuns()
  val segments =
      loadSegments(simulationRunsWrappers, USE_EVERY_VEHICLE_AS_EGO, MIN_SEGMENT_TICK_COUNT)

  val tscEvaluation =
      TSCEvaluation(tsc = tsc, segments = segments, projectionIgnoreList = PROJECTION_IGNORE_LIST)

  tscEvaluation.registerMetricProvider(AverageVehiclesInEgosBlockMetric())
  tscEvaluation.registerMetricProvider(SegmentCountMetric())
  tscEvaluation.registerMetricProvider(SegmentDurationPerIdentifierMetric())
  tscEvaluation.registerMetricProvider(TotalSegmentTimeLengthMetric())
  val validTSCInstancesPerProjectionMetric =
      ValidTSCInstancesPerProjectionMetric<Actor, TickData, Segment>()
  tscEvaluation.registerMetricProvider(validTSCInstancesPerProjectionMetric)
  tscEvaluation.registerMetricProvider(InvalidTSCInstancesPerProjectionMetric())
  tscEvaluation.registerMetricProvider(MissedTSCInstancesPerProjectionMetric())
  tscEvaluation.registerMetricProvider(
      MissingPredicateCombinationsPerProjectionMetric(validTSCInstancesPerProjectionMetric))
  val test = FailedMonitorsMetric(validTSCInstancesPerProjectionMetric)
  tscEvaluation.registerMetricProvider(test)

  tscEvaluation.runEvaluation()
  val result = test.evaluate()
}

fun getSimulationRuns(): List<CarlaSimulationRunsWrapper> {
  val mapFolders =
      File(SIMULATION_RUN_FOLDER)
          .walk()
          .filter {
            it.isDirectory &&
                it != File(SIMULATION_RUN_FOLDER) &&
                STATIC_FILTER_REGEX.toRegex().containsMatchIn(it.name)
          }
          .toList()
  return mapFolders.mapNotNull { mapFolder ->
    var staticFile: Path? = null
    val dynamicFiles = mutableListOf<Path>()
    mapFolder.walk().forEach { mapFile ->
      if (mapFile.nameWithoutExtension.contains("static_data") &&
          STATIC_FILTER_REGEX.toRegex().containsMatchIn(mapFile.name)) {
        staticFile = mapFile.toPath()
      }
      if (mapFile.nameWithoutExtension.contains("dynamic_data") &&
          FILTER_REGEX.toRegex().containsMatchIn(mapFile.name)) {
        dynamicFiles.add(mapFile.toPath())
      }
    }
    if (dynamicFiles.isEmpty()) {
      return@mapNotNull null
    }

    dynamicFiles.sortBy {
      "_seed([0-9]{1,4})".toRegex().find(it.fileName.name)?.groups?.get(1)?.value?.toInt() ?: 0
    }
    return@mapNotNull CarlaSimulationRunsWrapper(staticFile!!, dynamicFiles)
  }
}

fun downloadExperimentsData() {
  URL("https://zenodo.org/record/8131947/files/stars-reproduction-source.zip?download=1")
      .openStream()
      .use { Files.copy(it, Paths.get("stars-reproduction-source.zip")) }
}

/**
 * Extract a zip file into any directory
 *
 * @param zipFile src zip file
 * @param extractTo directory to extract into. There will be new folder with the zip's name inside
 *   [extractTo] directory.
 * @param extractHere no extra folder will be created and will be extracted directly inside
 *   [extractTo] folder.
 * @return the extracted directory i.e, [extractTo] folder if [extractHere] is `true` and
 *   [extractTo]\zipFile\ folder otherwise.
 */
private fun extractZipFile(
    zipFile: File,
    extractTo: File,
    extractHere: Boolean = false,
): File? {
  return try {
    val outputDir =
        if (extractHere) {
          extractTo
        } else {
          File(extractTo, zipFile.nameWithoutExtension)
        }

    ZipFile(zipFile).use { zip ->
      zip.entries().asSequence().forEach { entry ->
        zip.getInputStream(entry).use { input ->
          if (entry.isDirectory) {
            val d = File(outputDir, entry.name)
            if (!d.exists()) d.mkdirs()
          } else {
            val f = File(outputDir, entry.name)
            if (f.parentFile?.exists() != true) f.parentFile?.mkdirs()

            f.outputStream().use { output -> input.copyTo(output) }
          }
        }
      }
    }
    extractTo
  } catch (e: Exception) {
    e.printStackTrace()
    null
  }
}
