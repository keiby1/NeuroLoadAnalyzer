package com.nla.NeuroLoadAnalyzer.util;

/**
 * Grafana-compatible time window: HTTP uses milliseconds, PromQL / VM use seconds.
 */
public final class TimeRange {

	private final String rangeForPromQl;
	private final Long evaluationTimeSec;
	private final Long fromMs;
	private final Long toMs;

	private TimeRange(String rangeForPromQl, Long evaluationTimeSec, Long fromMs, Long toMs) {
		this.rangeForPromQl = rangeForPromQl;
		this.evaluationTimeSec = evaluationTimeSec;
		this.fromMs = fromMs;
		this.toMs = toMs;
	}

	/**
	 * @param fromMs      Grafana {@code from} in ms; may be null
	 * @param toMs        Grafana {@code to} in ms; may be null
	 * @param defaultRange fallback window when from/to are absent (e.g. {@code "1h"})
	 */
	public static TimeRange of(Long fromMs, Long toMs, String defaultRange) {
		if (fromMs != null && toMs != null && toMs > fromMs) {
			long rangeSec = (toMs - fromMs) / 1000L;
			return new TimeRange(rangeSec + "s", toMs / 1000L, fromMs, toMs);
		}
		return new TimeRange(defaultRange, null, fromMs, toMs);
	}

	/** Duration string for subquery windows, e.g. {@code "3600s"} or {@code "1h"}. */
	public String rangeForPromQl() {
		return rangeForPromQl;
	}

	/** Instant evaluation time (Unix seconds), or {@code null} for "now". */
	public Long evaluationTimeSec() {
		return evaluationTimeSec;
	}

	public Long fromMs() {
		return fromMs;
	}

	public Long toMs() {
		return toMs;
	}

	public boolean hasExplicitWindow() {
		return evaluationTimeSec != null;
	}
}
