package com.nla.NeuroLoadAnalyzer.report;

import com.nla.NeuroLoadAnalyzer.plugin.PluginRunStatus;

import java.util.Collection;

/**
 * Aggregates child statuses for parent cards.
 *
 * <p><b>Default policy (non-blocking Skip):</b>
 * {@code Fail > Warn > NoData > OK}; Skip is used only when there is no decisive status.
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
		return PluginRunStatus.OK;
	}

	/** CSS class: green / orange / red / yellow / gray. */
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
		};
	}
}
