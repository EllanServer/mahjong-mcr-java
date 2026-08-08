# Benchmarking

`McrMicroBenchmark` is a zero-dependency, deterministic smoke benchmark. It measures
one representative ambiguous standard evaluation and full wait enumeration:

```shell
./gradlew microBenchmark
```

Direct invocation accepts warm-up and measured evaluation counts:

```shell
java -cp build/classes/java/main \
  top.ellan.mahjong.rules.mcr.benchmark.McrMicroBenchmark 100000 1000000
```

The output reports `ns/op`, `ops/s`, and a checksum that keeps the result observable.
It is suitable for catching large regressions on the same host/JDK, not for publishing
cross-machine performance claims: it does not provide JMH isolation, allocation
profiling, forks, confidence intervals, or percentile latency.

Latest local smoke run (2026-08-08, GraalVM JDK 25.0.2, default task settings):

| Operation | Iterations | Result |
|---|---:|---:|
| representative evaluation | 1,000,000 | 4,869.6 ns/op; 205,356 ops/s |
| full 34-kind wait scan | 10,000 | 79,041.5 ns/op; 12,652 ops/s |

These numbers are a reproducibility breadcrumb for this host, not a portable SLA.

Before establishing a release performance budget, add a test-only JMH module and
measure at least:

- common standard hands and maximum-decomposition hands;
- seven pairs, thirteen orphans, fully disconnected and knitted straight;
- full 34-tile wait enumeration;
- one, four and sixteen evaluator threads;
- throughput, `p50/p99`, and bytes allocated per operation on JDK 21 and 25.

Correctness tests and differential corpora are release gates. A faster result must
never replace exhaustive decomposition or silently skip an implemented fan.
