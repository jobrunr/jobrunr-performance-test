
58028ms - processed 129999 jobs / 129997 index | 2240 jobs/sec (overall) | 2617.6 / jobs/sec (last 10 sec)
58030ms - processed 130000 jobs / 129978 index | 2240 jobs/sec (overall) | 2617.7 / jobs/sec (last 10 sec)


JobRunr
223757ms - processed 499999 jobs / 499989 index | 2234 jobs/sec (overall) | 2494.8 jobs/sec (last 10 sec)
223757ms - processed 500000 jobs / 499966 index | 2234 jobs/sec (overall) | 2494.9 jobs/sec (last 10 sec)


JobRunr Pro
348060ms - processed 499999 jobs / 499998 index | 1436 jobs/sec (overall) | 1631.6 jobs/sec (last 10 sec)
348061ms - processed 500000 jobs / 499999 index | 1436 jobs/sec (overall) | 1631.7 jobs/sec (last 10 sec)


23:10:00.008 [backgroundjob-worker-pool-3-thread-18] INFO  o.j.performance.PerformanceTestJob - 47968ms / PT47.968S - processed 246901 jobs / 246900 index | 5147 jobs/sec (overall) | 3848.7 jobs/sec (last 10 sec)
23:10:00.720 [main] INFO  org.performance.start.Main - Processing took 49762ms
23:18:57.006 [backgroundjob-worker-pool-3-thread-10] INFO  o.j.performance.PerformanceTestJob - 52666ms / PT52.666S - processed 248678 jobs / 248677 index | 4721 jobs/sec (overall) | 3761.3 jobs/sec (last 10 sec)
23:18:57.393 [main] INFO  org.performance.start.Main - Processing took 54139ms
23:14:54.000 [backgroundjob-worker-pool-3-thread-28] INFO  o.j.performance.PerformanceTestJob - 79297ms / PT1M19.297S - processed 249675 jobs / 249677 index | 3148 jobs/sec (overall) | 3456.8 jobs/sec (last 10 sec)
23:14:54.113 [main] INFO  org.performance.start.Main - Processing took 80551ms



## 2024-01-08
### Hetzner 
#### Pro v6 
15:22:24.000 [backgroundjob-worker-pool-3-thread-28] INFO  o.j.performance.PerformanceTestJob - 62191ms / PT1M2.191S - processed 249454 jobs / 249444 index | 4011 jobs/sec (overall) | 3535.8 jobs/sec (last 10 sec)
15:22:24.571 [org.performance.start.Main.main()] INFO  org.performance.start.Main - Processing took 63854ms

#### Pro v7
15:25:13.004 [backgroundjob-zookeeper-pool-3-thread-132] INFO  o.j.performance.PerformanceTestJob - 77291ms / PT1M17.291S - processed 249099 jobs / 249098 index | 3222 jobs/sec (overall) | 2785.7 jobs/sec (last 10 sec)
15:25:13.289 [org.performance.start.Main.main()] INFO  org.performance.start.Main - Processing took 78690ms

22:00:22.000 [backgroundjob-zookeeper-pool-3-thread-115] INFO  o.j.performance.PerformanceTestJob - 106921ms / PT1M46.921S - processed 247754 jobs / 247753 index | 2317 jobs/sec (overall) | 2100.3 jobs/sec (last 10 sec)
22:00:22.978 [org.performance.start.Main.main()] INFO  org.performance.start.Main - Processing took 109024ms

14:32:25.002 [backgroundjob-zookeeper-pool-3-thread-71] INFO  o.j.performance.PerformanceTestJob - 117725ms / PT1M57.725S - processed 248215 jobs / 248214 index | 2108 jobs/sec (overall) | 2149.2 jobs/sec (last 10 sec)
14:32:25.789 [org.performance.start.Main.main()] INFO  org.performance.start.Main - Processing took 119634ms

==> using Smart Queue 
15:39:02.000 [backgroundjob-zookeeper-pool-4-thread-1] INFO  o.j.performance.PerformanceTestJob - 44696ms / PT44.696S - processed 247755 jobs / 247754 index | 5543 jobs/sec (overall) | 5942.1 jobs/sec (last 10 sec)
15:39:02.380 [org.performance.start.Main.main()] INFO  org.performance.start.Main - Processing took 46208ms

#### OSS v6
16:05:38.001 [backgroundjob-worker-pool-3-thread-38] INFO  o.j.performance.PerformanceTestJob - 52394ms / PT52.394S - processed 249863 jobs / 249863 index | 4768 jobs/sec (overall) | 3924.2 jobs/sec (last 10 sec)
16:05:38.026 [org.performance.start.Main.main()] INFO  org.performance.start.Main - Processing took 53499ms

## 2024-01-10
### Local Server 
17:49:04.003 [backgroundjob-worker-pool-3-thread-13] INFO  o.j.performance.PerformanceTestJob - 1769788ms / PT29M29.788S - processed 499868 jobs / 499868 index | 282 jobs/sec (overall) | 201.9 jobs/sec (last 10 sec)
17:49:04.640 [main] INFO  org.performance.start.Main - Processing took 1771516ms

#### Pro v7
16:42:33.000 [backgroundjob-zookeeper-pool-3-thread-11] INFO  o.j.performance.PerformanceTestJob - 827587ms / PT13M47.587S - processed 499378 jobs / 499377 index | 603 jobs/sec (overall) | 584.6 jobs/sec (last 10 sec)
16:42:33.987 [main] INFO  org.performance.start.Main - Processing took 829634ms







### Macbook Pro no queue
 Processing took 112219ms
19:29:26.541 [extShutdownHook] INFO  o.jobrunr.server.BackgroundJobServer - BackgroundJobServer (Ronalds-MBP.fritz.box - 0a0f8008-fae2-44ff-86c2-cdd50cc81590) and BackgroundJobPerformers - stopping (waiting for all jobs to complete - max 10 seconds)









API:
- https://api.jobrunr.io/carbon-impact: no country or region => try to determine using IP
- https://api.jobrunr.io/carbon-impact?country=BE: country BELGIUM
- https://api.jobrunr.io/carbon-impact?country=US&state=CA: country BELGIUM
- https://api.jobrunr.io/carbon-impact?cloudprovider=azure&region=west-europe


response:
```json 
{
  "country": "BE",
  "state": "N/A",
  "date": "2024-01-18",
  "dayAheadAvailability": "12:00:00",
  "slots": [
      {
            "from": "02:00:00",
               "to": "03:00:00",
               "rank": 1
      },
      {
         "from": "13:00:00",
         "to": "14:00:00",
         "rank": 2
        },
        {
          "from": "12:00:00",
         "to": "13:00:00",
         "rank": 3       
        }
  ]
}
```

=> if it must be done before the weekend => we do it last day before it must be done
=> if it must be done after the weekend => we run it in the weekend

Table:
id, country, state, date, json

index on country, state, date
 


  "cpu_stats": {
    "cpu_usage": {
      "total_usage": 402439808000,
                     508813533000
      "usage_in_kernelmode": 111201113000,
      "usage_in_usermode": 291238694000
    },
    "system_cpu_usage": 61778026940000000,
    "online_cpus": 20,
    "throttling_data": {
      "periods": 0,
      "throttled_periods": 0,
      "throttled_time": 0
    }
  },
  

  "cpu_stats": {
    "cpu_usage": {
      "total_usage": 508813533000,
      "usage_in_kernelmode": 150283245000,
      "usage_in_usermode": 358530288000
    },
    "system_cpu_usage": 61782325970000000,
    "online_cpus": 20,
    "throttling_data": {
      "periods": 0,
      "throttled_periods": 0,
      "throttled_time": 0
    }
  },




