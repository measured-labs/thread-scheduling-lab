package com.example.threadcomparison.model;

public record ComparisonResult(
    SamplingResult manualThread,
    SamplingResult scheduledThread,
    String winner,
    String summary
) {}
