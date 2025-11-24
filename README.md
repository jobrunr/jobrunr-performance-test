## JobRunr Pro Performance Test

### Description
This project contains performance tests for JobRunr, JobRunr Pro and other tools using various databases.

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

On a dedicated Hetzner [EX44 Server](https://www.hetzner.com/dedicated-rootserver/ex44/), we achieved the following results:

- 