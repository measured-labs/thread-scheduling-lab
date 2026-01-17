package com.example.threadcomparison.model;

import java.util.List;

public record SamplingResult(
    String method,
    int totalSamples,
    long durationMs,
    int expectedSamples,
    double accuracy,
    double avgInterval,
    long minInterval,
    long maxInterval,
    double stdDeviation,
    double avgCpu,
    double peakCpu
) {}
