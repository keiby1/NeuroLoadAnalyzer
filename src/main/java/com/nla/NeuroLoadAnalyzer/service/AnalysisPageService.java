package com.nla.NeuroLoadAnalyzer.service;

import com.nla.NeuroLoadAnalyzer.dto.AnalysisReport;
import com.nla.NeuroLoadAnalyzer.plugin.PluginResult;
import com.nla.NeuroLoadAnalyzer.plugin.PluginRunStatus;
import com.nla.NeuroLoadAnalyzer.report.ReportTreeBuilder.PurposeReportNode;
import com.nla.NeuroLoadAnalyzer.report.ReportTreeBuilder.SoftwareReportNode;
import com.nla.NeuroLoadAnalyzer.report.ReportTreeBuilder.TypeReportGroup;
import com.nla.NeuroLoadAnalyzer.report.ReportTreeBuilder.ValueReportNode;
import com.nla.NeuroLoadAnalyzer.report.StatusAggregator;
import org.springframework.stereotype.Service;

/**
 * Builds HTML shell (spinner) and hierarchical card report (ExampleReport styles).
 */
@Service
public class AnalysisPageService {

	public String loadingPage() {
		return """
				<!DOCTYPE html>
				<html lang="ru">
				<head>
				<meta charset="UTF-8">
				<meta name="viewport" content="width=device-width, initial-scale=1">
				<title>NLA</title>
				<style>
				  * { margin: 0; padding: 0; box-sizing: border-box; }
				  body {
				    font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
				    background-color: #f5f5f5;
				    padding: 20px;
				    color: #333;
				  }
				  .wrap { max-width: 1200px; margin: 0 auto; }
				  .page-header {
				    display: flex;
				    align-items: baseline;
				    justify-content: flex-start;
				    margin-bottom: 20px;
				  }
				  .brand {
				    text-align: left;
				    color: #333;
				    font-size: 1.5em;
				    font-weight: 700;
				    letter-spacing: 0.02em;
				  }
				  .nla-meta {
				    color: #666;
				    font-size: .95rem;
				    margin: 0 0 1.25rem;
				    text-align: left;
				  }
				  #status {
				    display: flex;
				    flex-direction: column;
				    align-items: center;
				    justify-content: center;
				    gap: 1rem;
				    min-height: 220px;
				  }
				  .spinner {
				    width: 48px;
				    height: 48px;
				    border-radius: 50%;
				    border: 4px solid #e0e0e0;
				    border-top-color: #4CAF50;
				    animation: spin 0.8s linear infinite;
				  }
				  @keyframes spin { to { transform: rotate(360deg); } }
				  .status-text { color: #666; font-size: 0.95rem; }
				  #result { display: none; }
				  #result.visible { display: block; }
				  #status.hidden { display: none; }
				  .error {
				    color: #9b1c1c;
				    background: #FFEBEE;
				    border: 1px solid #ffcdd2;
				    border-radius: 8px;
				    padding: 0.9rem 1rem;
				  }

				  .summary-cards {
				    display: grid;
				    grid-template-columns: repeat(5, 1fr);
				    gap: 16px;
				    margin-bottom: 24px;
				  }
				  @media (max-width: 900px) {
				    .summary-cards { grid-template-columns: repeat(2, 1fr); }
				  }
				  .summary-card {
				    background: white;
				    border-radius: 12px;
				    padding: 18px 16px;
				    box-shadow: 0 4px 6px rgba(0, 0, 0, 0.1);
				    border-left: 5px solid;
				    text-align: center;
				  }
				  .summary-card.green {
				    border-left-color: #4CAF50;
				    background: linear-gradient(135deg, #E8F5E8 0%, #F1F8E9 100%);
				  }
				  .summary-card.red {
				    border-left-color: #F44336;
				    background: linear-gradient(135deg, #FFEBEE 0%, #FCE4EC 100%);
				  }
				  .summary-card.yellow {
				    border-left-color: #FF9800;
				    background: linear-gradient(135deg, #FFF8E1 0%, #FFF3E0 100%);
				  }
				  .summary-card.orange {
				    border-left-color: #FB8C00;
				    background: linear-gradient(135deg, #FFF3E0 0%, #FFE0B2 100%);
				  }
				  .summary-card.gray {
				    border-left-color: #9E9E9E;
				    background: linear-gradient(135deg, #F5F5F5 0%, #EEEEEE 100%);
				  }
				  .summary-card-title {
				    font-size: 0.95em;
				    font-weight: 600;
				    color: #555;
				    margin-bottom: 8px;
				  }
				  .summary-card-count {
				    font-size: 2em;
				    font-weight: 700;
				    color: #333;
				    line-height: 1.1;
				  }
				  .type-group { margin-bottom: 24px; }
				  .cards-container {
				    display: grid;
				    grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
				    gap: 20px;
				    margin-bottom: 20px;
				  }
				  .card {
				    background: white;
				    border-radius: 12px;
				    padding: 20px;
				    box-shadow: 0 4px 6px rgba(0, 0, 0, 0.1);
				    cursor: pointer;
				    transition: all 0.3s ease;
				    border-left: 5px solid;
				    position: relative;
				  }
				  .card:hover {
				    transform: translateY(-2px);
				    box-shadow: 0 8px 15px rgba(0, 0, 0, 0.15);
				  }
				  .card.green {
				    border-left-color: #4CAF50;
				    background: linear-gradient(135deg, #E8F5E8 0%, #F1F8E9 100%);
				  }
				  .card.red {
				    border-left-color: #F44336;
				    background: linear-gradient(135deg, #FFEBEE 0%, #FCE4EC 100%);
				  }
				  .card.yellow {
				    border-left-color: #FF9800;
				    background: linear-gradient(135deg, #FFF8E1 0%, #FFF3E0 100%);
				  }
				  .card.orange {
				    border-left-color: #FB8C00;
				    background: linear-gradient(135deg, #FFF3E0 0%, #FFE0B2 100%);
				  }
				  .card.gray {
				    border-left-color: #9E9E9E;
				    background: linear-gradient(135deg, #F5F5F5 0%, #EEEEEE 100%);
				  }
				  .card-title {
				    font-size: 1.35em;
				    font-weight: bold;
				    margin-bottom: 10px;
				    color: #333;
				    padding-right: 28px;
				  }
				  .card-content { color: #666; font-size: 0.9em; }
				  .card.has-children::after {
				    content: '▶';
				    position: absolute;
				    right: 20px;
				    top: 28px;
				    font-size: 1.1em;
				    color: #999;
				    transition: transform 0.3s ease;
				  }
				  .card.expanded::after { transform: rotate(90deg); }
				  .sub-cards {
				    display: none;
				    margin-top: 15px;
				    padding-left: 20px;
				    border-left: 2px solid #e0e0e0;
				  }
				  .sub-cards.show { display: block; }
				  .sub-card {
				    background: #fafafa;
				    border-radius: 8px;
				    padding: 15px;
				    margin-bottom: 10px;
				    border-left: 3px solid;
				    transition: all 0.3s ease;
				    position: relative;
				  }
				  .sub-card:hover { background: #f0f0f0; }
				  .sub-card.has-children { cursor: pointer; }
				  .sub-card.has-children:hover {
				    background: #e3f2fd;
				    transform: translateX(5px);
				    box-shadow: 0 4px 8px rgba(0, 0, 0, 0.15);
				  }
				  .sub-card.green { border-left-color: #4CAF50; }
				  .sub-card.red { border-left-color: #F44336; }
				  .sub-card.yellow { border-left-color: #FF9800; }
				  .sub-card.orange { border-left-color: #FB8C00; }
				  .sub-card.gray { border-left-color: #9E9E9E; }
				  .sub-card-title {
				    font-weight: bold;
				    margin-bottom: 5px;
				    color: #333;
				    padding-right: 24px;
				  }
				  .sub-card-value { color: #666; font-size: 0.9em; }
				  .sub-card.has-children::after {
				    content: '▶';
				    position: absolute;
				    right: 14px;
				    top: 16px;
				    color: #999;
				    transition: transform 0.3s ease;
				  }
				  .sub-card.expanded::after { transform: rotate(90deg); }
				  .status-indicator {
				    display: inline-block;
				    width: 12px;
				    height: 12px;
				    border-radius: 50%;
				    margin-right: 8px;
				  }
				  .status-indicator.green { background-color: #4CAF50; }
				  .status-indicator.red { background-color: #F44336; }
				  .status-indicator.yellow { background-color: #FF9800; }
				  .status-indicator.orange { background-color: #FB8C00; }
				  .status-indicator.gray { background-color: #9E9E9E; }
				  .status-label {
				    display: inline-block;
				    margin-left: 8px;
				    font-size: 0.8em;
				    font-weight: 600;
				    color: #666;
				  }
				  .rule-line {
				    margin-top: 6px;
				    padding: 8px 10px;
				    background: white;
				    border-radius: 6px;
				    border: 1px solid #eee;
				    font-size: 0.85em;
				  }
				  .rule-line code {
				    display: block;
				    margin-top: 4px;
				    font-size: 0.75em;
				    color: #888;
				    word-break: break-all;
				  }
				  .metric-detail-card {
				    cursor: pointer;
				  }
				  .metric-detail-card:hover {
				    background: #e3f2fd;
				    transform: translateX(5px);
				    box-shadow: 0 4px 8px rgba(0, 0, 0, 0.15);
				  }
				  .nla-modal-overlay {
				    display: none;
				    position: fixed;
				    inset: 0;
				    background: rgba(0, 0, 0, 0.45);
				    z-index: 1000;
				    align-items: center;
				    justify-content: center;
				    padding: 24px;
				  }
				  .nla-modal-overlay.open { display: flex; }
				  .nla-modal {
				    background: #fff;
				    border-radius: 12px;
				    box-shadow: 0 12px 40px rgba(0, 0, 0, 0.25);
				    max-width: 860px;
				    width: 100%;
				    max-height: 85vh;
				    overflow: auto;
				    position: relative;
				    padding: 24px 28px 28px;
				  }
				  .nla-modal-close {
				    position: absolute;
				    top: 12px;
				    right: 14px;
				    border: none;
				    background: transparent;
				    font-size: 1.6em;
				    line-height: 1;
				    color: #888;
				    cursor: pointer;
				    padding: 4px 8px;
				  }
				  .nla-modal-close:hover { color: #333; }
				  .nla-modal-body .sub-card {
				    margin: 0;
				    cursor: default;
				    transform: none !important;
				    box-shadow: none;
				  }
				  .nla-modal-body .sub-card:hover {
				    background: #fafafa;
				    transform: none;
				    box-shadow: none;
				  }
				  .nla-modal-body .sub-card-title {
				    font-size: 1.25em;
				    padding-right: 36px;
				  }
				  .nla-modal-body .sub-card-value {
				    font-size: 1em;
				    margin-top: 8px;
				  }
				  .nla-modal-body .rule-line {
				    margin-top: 14px;
				    padding: 14px 16px;
				  }
				  .nla-modal-body .rule-line code {
				    font-size: 0.95em;
				    color: #333;
				    white-space: pre-wrap;
				    word-break: break-word;
				  }
				</style>
				</head>
				<body>
				<div class="wrap">
				  <div class="page-header">
				    <div class="brand">NLA</div>
				  </div>
				  <div id="status" aria-live="polite">
				    <div class="spinner" aria-hidden="true"></div>
				    <div class="status-text">Выполняется анализ…</div>
				  </div>
				  <div id="result"></div>
				</div>
				<div id="nla-modal-overlay" class="nla-modal-overlay" aria-hidden="true">
				    <div class="nla-modal" role="dialog" aria-modal="true">
				    <button type="button" class="nla-modal-close" id="nla-modal-close" aria-label="Закрыть">&times;</button>
				    <div id="nla-modal-body" class="nla-modal-body"></div>
				  </div>
				</div>
				<script>
				function toggleNlaCard(card) {
				  const subCards = card.querySelector(':scope > .sub-cards');
				  if (!subCards) return;
				  if (subCards.classList.contains('show')) {
				    subCards.classList.remove('show');
				    card.classList.remove('expanded');
				  } else {
				    subCards.classList.add('show');
				    card.classList.add('expanded');
				  }
				}
				function openNlaMetricModal(card) {
				  const overlay = document.getElementById('nla-modal-overlay');
				  const body = document.getElementById('nla-modal-body');
				  if (!overlay || !body) return;
				  const clone = card.cloneNode(true);
				  clone.classList.remove('metric-detail-card');
				  body.innerHTML = '';
				  body.appendChild(clone);
				  overlay.classList.add('open');
				  overlay.setAttribute('aria-hidden', 'false');
				}
				function closeNlaMetricModal() {
				  const overlay = document.getElementById('nla-modal-overlay');
				  const body = document.getElementById('nla-modal-body');
				  if (!overlay) return;
				  overlay.classList.remove('open');
				  overlay.setAttribute('aria-hidden', 'true');
				  if (body) body.innerHTML = '';
				}
				function initNlaReportCards(root) {
				  if (!root) return;
				  root.querySelectorAll('.card.has-children, .sub-card.has-children').forEach(card => {
				    card.addEventListener('click', function(e) {
				      const subCards = card.querySelector(':scope > .sub-cards');
				      const isClickOnSubCards = subCards && subCards.contains(e.target);
				      if (!isClickOnSubCards) {
				        e.stopPropagation();
				        toggleNlaCard(card);
				      }
				    });
				  });
				  root.querySelectorAll('.metric-detail-card').forEach(card => {
				    card.addEventListener('click', function(e) {
				      e.stopPropagation();
				      openNlaMetricModal(card);
				    });
				  });
				}
				document.getElementById('nla-modal-close').addEventListener('click', closeNlaMetricModal);
				document.getElementById('nla-modal-overlay').addEventListener('click', function(e) {
				  if (e.target === this) closeNlaMetricModal();
				});
				document.addEventListener('keydown', function(e) {
				  if (e.key === 'Escape') closeNlaMetricModal();
				});
				(async function () {
				  const statusEl = document.getElementById('status');
				  const resultEl = document.getElementById('result');
				  try {
				    const response = await fetch('/analyze/result' + window.location.search, {
				      headers: { 'Accept': 'text/html' }
				    });
				    const html = await response.text();
				    if (!response.ok) {
				      throw new Error(html || ('HTTP ' + response.status));
				    }
				    resultEl.innerHTML = html;
				    statusEl.classList.add('hidden');
				    resultEl.classList.add('visible');
				    initNlaReportCards(resultEl);
				  } catch (e) {
				    statusEl.innerHTML = '<div class="error">Не удалось выполнить анализ: '
				      + (e && e.message ? e.message : e) + '</div>';
				  }
				})();
				</script>
				</body>
				</html>
				""";
	}

	public String renderReport(AnalysisReport report) {
		StringBuilder html = new StringBuilder();
		html.append("<p class=\"nla-meta\">")
				.append(esc(formatTimeWindow(report.timeRange())))
				.append("</p>");

		appendSummaryCards(html, report.pluginResults());

		if (report.typeGroups().isEmpty()) {
			html.append("<p class=\"nla-meta\">Нет параметров вида Тип_Софт_Назначение для анализа.</p>");
			return html.toString();
		}

		for (TypeReportGroup typeGroup : report.typeGroups()) {
			String typeCss = StatusAggregator.cssClass(typeGroup.status());
			html.append("<div class=\"type-group\">");
			html.append("<div class=\"card ").append(typeCss).append(" has-children\">");
			html.append("<div class=\"card-title\">")
					.append(indicator(typeGroup.status()))
					.append(esc(typeGroup.displayName()))
					.append(statusLabel(typeGroup.status()))
					.append("</div>");
			html.append("<div class=\"card-content\">")
					.append(typeGroup.softwares().size())
					.append(plural(typeGroup.softwares().size(), " тип ПО", " типа ПО", " типов ПО"))
					.append("</div>");

			html.append("<div class=\"sub-cards\"><div class=\"cards-container\">");
			for (SoftwareReportNode software : typeGroup.softwares()) {
				appendSoftwareCard(html, software);
			}
			html.append("</div></div>"); // cards-container, sub-cards
			html.append("</div></div>"); // card, type-group
		}

		return html.toString();
	}

	private void appendSummaryCards(StringBuilder html, java.util.List<PluginResult> results) {
		int fail = 0;
		int warn = 0;
		int noData = 0;
		int skip = 0;
		int ok = 0;
		if (results != null) {
			for (PluginResult result : results) {
				if (result == null || result.status() == null) {
					continue;
				}
				switch (result.status()) {
					case FAIL -> fail++;
					case WARN -> warn++;
					case NO_DATA -> noData++;
					case SKIP -> skip++;
					case OK -> ok++;
				}
			}
		}

		html.append("<div class=\"summary-cards\">");
		appendSummaryCard(html, PluginRunStatus.FAIL, fail);
		appendSummaryCard(html, PluginRunStatus.WARN, warn);
		appendSummaryCard(html, PluginRunStatus.NO_DATA, noData);
		appendSummaryCard(html, PluginRunStatus.SKIP, skip);
		appendSummaryCard(html, PluginRunStatus.OK, ok);
		html.append("</div>");
	}

	private static void appendSummaryCard(StringBuilder html, PluginRunStatus status, int count) {
		html.append("<div class=\"summary-card ").append(StatusAggregator.cssClass(status)).append("\">")
				.append("<div class=\"summary-card-title\">")
				.append(indicator(status))
				.append(esc(StatusAggregator.label(status)))
				.append("</div>")
				.append("<div class=\"summary-card-count\">").append(count).append("</div>")
				.append("</div>");
	}

	private void appendSoftwareCard(StringBuilder html, SoftwareReportNode software) {
		String css = StatusAggregator.cssClass(software.status());
		html.append("<div class=\"card ").append(css).append(" has-children\">");
		html.append("<div class=\"card-title\">")
				.append(indicator(software.status()))
				.append(esc(software.software()))
				.append(statusLabel(software.status()))
				.append("</div>");
		html.append("<div class=\"card-content\">")
				.append(software.purposes().size())
				.append(plural(software.purposes().size(), " назначение", " назначения", " назначений"))
				.append("</div>");
		html.append("<div class=\"sub-cards\">");
		for (PurposeReportNode purpose : software.purposes()) {
			appendPurposeCard(html, purpose);
		}
		html.append("</div></div>");
	}

	private void appendPurposeCard(StringBuilder html, PurposeReportNode purpose) {
		String css = StatusAggregator.cssClass(purpose.status());
		html.append("<div class=\"sub-card ").append(css).append(" has-children\">");
		html.append("<div class=\"sub-card-title\">")
				.append(indicator(purpose.status()))
				.append(esc(purpose.purpose()))
				.append(statusLabel(purpose.status()))
				.append("</div>");
		html.append("<div class=\"sub-card-value\">")
				.append(purpose.values().size())
				.append(plural(purpose.values().size(), " значение", " значения", " значений"))
				.append("</div>");
		html.append("<div class=\"sub-cards\">");
		for (ValueReportNode value : purpose.values()) {
			appendValueCard(html, value);
		}
		html.append("</div></div>");
	}

	private void appendValueCard(StringBuilder html, ValueReportNode value) {
		String css = StatusAggregator.cssClass(value.status());
		html.append("<div class=\"sub-card ").append(css).append(" has-children\">");
		html.append("<div class=\"sub-card-title\">")
				.append(indicator(value.status()))
				.append(esc(value.parameterValue()))
				.append(statusLabel(value.status()))
				.append("</div>");
		html.append("<div class=\"sub-card-value\">")
				.append(esc(value.parameterName()))
				.append("</div>");
		html.append("<div class=\"sub-cards\">");
		for (PluginResult result : value.results()) {
			String ruleCss = StatusAggregator.cssClass(result.status());
			html.append("<div class=\"sub-card metric-detail-card ").append(ruleCss).append("\">");
			html.append("<div class=\"sub-card-title\">")
					.append(indicator(result.status()))
					.append(esc(result.pluginName()))
					.append(statusLabel(result.status()))
					.append("</div>");
			html.append("<div class=\"sub-card-value\">");
			if (result.slopePctPerHour() != null || result.deltaAbsBytes() != null) {
				html.append(esc(nullToEmpty(result.message())));
				html.append("<div class=\"rule-line\">");
				if (result.slopePctPerHour() != null) {
					html.append("slope: ").append(formatNumber(result.slopePctPerHour())).append("%/ч");
					if (result.slopeBytesPerHour() != null) {
						html.append(" (").append(formatNumber(result.slopeBytesPerHour() / (1024 * 1024)))
								.append(" МиБ/ч)");
					}
				}
				if (result.deltaAbsBytes() != null) {
					if (result.slopePctPerHour() != null) {
						html.append(" · ");
					}
					html.append("Δ: ").append(formatNumber(result.deltaAbsBytes() / (1024 * 1024))).append(" МиБ");
					if (result.deltaPct() != null) {
						html.append(" (").append(formatNumber(result.deltaPct())).append("%)");
					}
				}
				html.append("</div>");
			} else if (result.metricValue() != null) {
				html.append("значение: ").append(formatNumber(result.metricValue()))
						.append(" · условие: ").append(esc(result.conditionDescription()));
			} else {
				html.append(esc(nullToEmpty(result.message())));
			}
			if (result.boundQuery() != null && !result.boundQuery().isBlank()) {
				html.append("<div class=\"rule-line\"><code>")
						.append(esc(result.boundQuery()))
						.append("</code></div>");
			}
			html.append("</div></div>");
		}
		html.append("</div></div>");
	}

	private static String formatTimeWindow(com.nla.NeuroLoadAnalyzer.util.TimeRange timeRange) {
		if (timeRange == null) {
			return "Период: —";
		}
		String from = formatGrafanaTimestamp(timeRange.fromMs());
		String to = formatGrafanaTimestamp(timeRange.toMs());
		return "Период: " + from + " — " + to;
	}

	private static String formatGrafanaTimestamp(Long epochMs) {
		if (epochMs == null) {
			return "—";
		}
		return java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
				.withZone(java.time.ZoneId.systemDefault())
				.format(java.time.Instant.ofEpochMilli(epochMs));
	}

	private static String indicator(PluginRunStatus status) {
		return "<span class=\"status-indicator " + StatusAggregator.cssClass(status) + "\"></span>";
	}

	private static String statusLabel(PluginRunStatus status) {
		return "<span class=\"status-label\">" + esc(StatusAggregator.label(status)) + "</span>";
	}

	private static String plural(int n, String one, String few, String many) {
		int nAbs = Math.abs(n) % 100;
		int n1 = nAbs % 10;
		if (nAbs > 10 && nAbs < 20) {
			return many;
		}
		if (n1 > 1 && n1 < 5) {
			return few;
		}
		if (n1 == 1) {
			return one;
		}
		return many;
	}

	private static String formatNumber(double value) {
		if (Math.rint(value) == value && Math.abs(value) < 1e12) {
			return String.valueOf((long) value);
		}
		return String.format(java.util.Locale.ROOT, "%.3f", value);
	}

	private static String nullToEmpty(String value) {
		return value == null ? "" : value;
	}

	private static String esc(String value) {
		if (value == null) {
			return "";
		}
		return value
				.replace("&", "&amp;")
				.replace("<", "&lt;")
				.replace(">", "&gt;")
				.replace("\"", "&quot;");
	}
}
