## JobRunr Pro Performance Test

### Description
This project contains performance tests for JobRunr, JobRunr Pro and other tools using various databases. 

> The goal of this test project is to measure the performance of these different tools. 
> That's why the actual jobs are just minimal and do not contain any real business logic.

### Prerequisites
- Java 25
- Docker

### How to run
There are various scenarios which can be tested. For JobRunr OSS and other tools, there is a basic scenario to test how fast jobs are being processed.

#### 1. Install the script to have autocomplete for the run-scenario command
```console
source ./run-scenario.sh
```

#### 2. Run the scenario
```console
run-scenario JobRunr Scenario01ProcessJobs Postgres
```

Please note that for JobRunrPro, you will need to set the JOBRUNRPRO_LICENSE environment variable.
```console
export JOBRUNRPRO_LICENSE=<license key>
```


## Test Results
Our test results can be found in the folder logbooks, grouped by scenario.

On a dedicated Hetzner [EX44 Server](https://www.hetzner.com/dedicated-rootserver/ex44/), we achieved the following results for a scenario with 500.000 jobs:

|Date & Time                   |Host name                   |Java version|Tool       |Tool Version                     |StorageProvider|amount of created jobs|amount of succeeded jobs|creation duration|processing duration|job throughput (jobs / sec)|
|------------------------------|----------------------------|------------|-----------|---------------------------------|---------------|----------------------|------------------------|-----------------|-------------------|---------------------------|
|2025-11-24T09:58:30.966479351Z|Ubuntu-2204-jammy-amd64-base|21.0.4+7-LTS|JobRunr    |1.0.0-SNAPSHOT (master)          |postgres:18.0  |500000                |500000                  |PT16.464804885S  |PT2M28.078702888S  |3378.38                    |
|2025-11-24T10:02:11.812289606Z|Ubuntu-2204-jammy-amd64-base|21.0.4+7-LTS|JobRunr Pro|1.0.0-SNAPSHOT (master@30e8d468f)|postgres:18.0  |500000                |500000                  |PT22.035607589S  |PT3M28.119956226S  |2403.85                    |
|2025-11-24T10:08:45.308751361Z|Ubuntu-2204-jammy-amd64-base|21.0.4+7-LTS|quartz     |2.5.1-SNAPSHOT (branch unknown)  |postgres:18.0  |500000                |500000                  |PT57M53.5989764S |PT57M17.435478266S |145.48                     |

> The difference in job throughput between JobRunr Pro and JobRunr OSS is because of database complexity and out-of-date table statistics. If database statistics are updated, we have a throughput of 3067.48 jobs / sec.  

### Scenarios
There are many scenarios that are available for JobRunr Pro, including:
- Scenario00CombinedScenario: a combination of all other scenarios (including rate limiting, dynamic queues, ...)
- Scenario01ProcessJobs: Creates 500.000 jobs and processes them.
- Scenario02ProcessJobsInDynamicQueues: Creates 500.000 jobs in multiple dynamic queues and processes them.
- Scenario04BatchJobs: Creates 10 batch jobs which in turn create 50.000 child jobs.

### Todo:
Originally, after all jobs were created, database statistics were updated for JobRunr. 
As we wanted to open this up to the community, we disabled it as we do not know yet which tables need updated statistics for Quartz. 
This also has an impact on the throughput of JobRunr Pro as the job table is a bit more complex. 