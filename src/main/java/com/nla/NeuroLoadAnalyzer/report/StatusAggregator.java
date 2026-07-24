package com.nla.NeuroLoadAnalyzer.report;

import com.nla.NeuroLoadAnalyzer.plugin.PluginRunStatus;

import java.util.Collection;

/**
 * Aggregates child statuses for parent cards.
 *
 * <p>Chain (worst → best): {@code FAIL > WARN > NO_DATA > OK > SKIP > INFO}.
 * INFO becomes the parent status only when every child is INFO.
 */
public final class StatusAggregator {

	private StatusAggregator() {
	}

	public static PluginRunStatus aggregate(Collection<PluginRunStatus> statuses) {
		if (statuses == null || statuses.isEmpty()) {
			return PluginRunStatus.OK;
		}

		boolean anyFail = false;
		boolean anyWarn = false;
		boolean anyNoData = false;
		boolean anyOk = false;
		boolean anySkip = false;
		boolean anyInfo = false;

		for (PluginRunStatus status : statuses) {
			if (status == null) {
				continue;
			}
			switch (status) {
				case FAIL -> anyFail = true;
				case WARN -> anyWarn = true;
				case NO_DATA -> anyNoData = true;
				case OK -> anyOk = true;
				case SKIP -> anySkip = true;
				case INFO -> anyInfo = true;
			}
		}

		if (anyFail) {
			return PluginRunStatus.FAIL;
		}
		if (anyWarn) {
			return PluginRunStatus.WARN;
		}
		if (anyNoData) {
			return PluginRunStatus.NO_DATA;
		}
		if (anyOk) {
			return PluginRunStatus.OK;
		}
		if (anySkip) {
			return PluginRunStatus.SKIP;
		}
		if (anyInfo) {
			return PluginRunStatus.INFO;
		}
		return PluginRunStatus.OK;
	}

	/** CSS class: green / orange / red / yellow / gray / blue. */
	public static String cssClass(PluginRunStatus status) {
		if (status == null) {
			return "green";
		}
		return switch (status) {
			case OK -> "green";
			case WARN -> "orange";
			case FAIL -> "red";
			case NO_DATA -> "yellow";
			case SKIP -> "gray";
			case INFO -> "blue";
		};
	}

	public static String label(PluginRunStatus status) {
		if (status == null) {
			return "OK";
		}
		return switch (status) {
			case OK -> "OK";
			case WARN -> "Warn";
			case FAIL -> "Fail";
			case NO_DATA -> "No Data";
			case SKIP -> "Skip";
			case INFO -> "Info";
		};
	}
}
