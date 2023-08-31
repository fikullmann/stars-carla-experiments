# STARS experiments based on data from CARLA

This repository analyzes driving data recorded with the [Carla Simulator](https://carla.org/) using the [STARS 
framework](https://github.com/tudo-aqua/stars).

## Setup

The analysis requires the recorded data. To receive the data, there are two options:
1. Set `DOWNLOAD_EXPERIMENTS_DATA` in `experimentsConfiguration.kt` to `true`. This will automatically download and 
   unzip the necessary data.
2. Manually download the data. 
   1. Go to the [Zenodo artifact](https://zenodo.org/record/8131947) where the experiments data is stored
   2. Download the `stars-reproduction-source.zip`
   3. Place the Zip-File into the root folder of this project.

**Remark:** The downloaded data has a size of approximately 1.3GB. The downloaded zip-file will be extracted during 
the analysis. Make sure, that you have at least 3GB of free space.

## Running the Analysis

This project is a Gradle project with a shipped gradle wrapper. To execute the analysis simply execute:

- Linux/Mac: `./gradlew run`
- Windows: `./gradlew.bat run`

## Analysis Results

After the analysis is finished you can find the results in the `analysis-result-logs` subfolder which will be 
created during the analysis.

For each execution of the analysis pipeline a subfolder with the start date and time ist created. In it, each metric 
of the analysis has its own subfolder. The analysis separates the results of each metric into different categories 
with different detail levels. 
- `*-severe.txt` lists all failed metric results
- `*-warning.txt` lists all warnings that occurred during analysis
- `*-info.txt` contains the summarized result of the metric
- `*-fine.txt` contains a more detailed result of the metric
- `*-finer.txt` contains all possible results of the metric
- `*-finest.txt` contains all possible results of the metric including meta information
