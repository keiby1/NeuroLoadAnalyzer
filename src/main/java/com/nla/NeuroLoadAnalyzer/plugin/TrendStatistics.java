package com.nla.NeuroLoadAnalyzer.plugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Compact Sen's slope + Mann–Kendall helpers for trend detection (no external libs).
 */
public final class TrendStatistics {

	private TrendStatistics() {
	}

	public record FitResult(
			double senSlopePerSec,
			double mannKendallS,
			double mannKendallZ,
			double pValueTwoTailed,
			boolean significant
	) {
	}

	/**
	 * @param alpha significance level (e.g. 0.05)
	 */
	public static FitResult fit(List<MetricPoint> series, double alpha) {
		int n = series.size();
		if (n < 3) {
			return new FitResult(0, 0, 0, 1.0, false);
		}

		List<Double> slopes = new ArrayList<>();
		long s = 0;
		for (int i = 0; i < n - 1; i++) {
			MetricPoint a = series.get(i);
			for (int j = i + 1; j < n; j++) {
				MetricPoint b = series.get(j);
				long dt = b.timestampSec() - a.timestampSec();
				if (dt <= 0) {
					continue;
				}
				double dy = b.value() - a.value();
				slopes.add(dy / dt);
				int cmp = Double.compare(b.value(), a.value());
				if (cmp > 0) {
					s++;
				} else if (cmp < 0) {
					s--;
				}
			}
		}

		double senSlope = median(slopes);
		double varS = mannKendallVariance(n);
		double z;
		if (s > 0) {
			z = (s - 1) / Math.sqrt(varS);
		} else if (s < 0) {
			z = (s + 1) / Math.sqrt(varS);
		} else {
			z = 0;
		}
		double p = 2.0 * (1.0 - standardNormalCdf(Math.abs(z)));
		boolean significant = p < alpha && Math.abs(senSlope) > 0;
		return new FitResult(senSlope, s, z, p, significant);
	}

	private static double mannKendallVariance(int n) {
		// No tie correction (values are continuous floats) — adequate for our use.
		return n * (n - 1.0) * (2.0 * n + 5.0) / 18.0;
	}

	private static double median(List<Double> values) {
		if (values.isEmpty()) {
			return 0;
		}
		List<Double> sorted = new ArrayList<>(values);
		Collections.sort(sorted);
		int m = sorted.size();
		if (m % 2 == 1) {
			return sorted.get(m / 2);
		}
		return 0.5 * (sorted.get(m / 2 - 1) + sorted.get(m / 2));
	}

	/** Approximation of Φ(z) via erf. */
	static double standardNormalCdf(double z) {
		return 0.5 * (1.0 + erf(z / Math.sqrt(2.0)));
	}

	/** Abramowitz & Stegun 7.1.26 approximation. */
	private static double erf(double x) {
		boolean neg = x < 0;
		double ax = Math.abs(x);
		double t = 1.0 / (1.0 + 0.3275911 * ax);
		double a1 = 0.254829592;
		double a2 = -0.284496736;
		double a3 = 1.421413741;
		double a4 = -1.453152027;
		double a5 = 1.061405429;
		double y = 1.0 - (((((a5 * t + a4) * t) + a3) * t + a2) * t + a1) * t * Math.exp(-ax * ax);
		return neg ? -y : y;
	}
}
