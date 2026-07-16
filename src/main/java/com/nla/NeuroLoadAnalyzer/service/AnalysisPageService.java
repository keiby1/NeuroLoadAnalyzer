package com.nla.NeuroLoadAnalyzer.service;

import com.nla.NeuroLoadAnalyzer.dto.AnalysisReport;
import com.nla.NeuroLoadAnalyzer.dto.TypedTarget;
import com.nla.NeuroLoadAnalyzer.plugin.PluginResult;
import com.nla.NeuroLoadAnalyzer.plugin.PluginRunStatus;
import org.springframework.stereotype.Service;

/**
 * Builds HTML shell (spinner) and result markup for the analysis flow.
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
				<title>NeuroLoadAnalyzer</title>
				<style>
				  :root {
				    --bg: #f3f5f7;
				    --ink: #1a2330;
				    --muted: #5c6b7a;
				    --ring: #2f6fed;
				    --track: #d5dde6;
				  }
				  * { box-sizing: border-box; }
				  body {
				    margin: 0;
				    min-height: 100vh;
				    font-family: "Segoe UI", system-ui, sans-serif;
				    color: var(--ink);
				    background:
				      radial-gradient(1200px 500px at 10% -10%, #e8eef8 0%, transparent 55%),
				      radial-gradient(900px 400px at 100% 0%, #e6f0ea 0%, transparent 50%),
				      var(--bg);
				  }
				  .wrap {
				    max-width: 960px;
				    margin: 0 auto;
				    padding: 2.5rem 1.25rem 3rem;
				  }
				  h1 {
				    margin: 0 0 0.35rem;
				    font-size: 1.35rem;
				    font-weight: 650;
				    letter-spacing: -0.02em;
				  }
				  .subtitle {
				    margin: 0 0 2rem;
				    color: var(--muted);
				    font-size: 0.95rem;
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
				    border: 4px solid var(--track);
				    border-top-color: var(--ring);
				    animation: spin 0.8s linear infinite;
				  }
				  @keyframes spin { to { transform: rotate(360deg); } }
				  .status-text { color: var(--muted); font-size: 0.95rem; }
				  #result { display: none; }
				  #result.visible { display: block; }
				  #status.hidden { display: none; }
				  .error {
				    color: #9b1c1c;
				    background: #fdecec;
				    border: 1px solid #f3c1c1;
				    border-radius: 8px;
				    padding: 0.9rem 1rem;
				  }
				</style>
				</head>
				<body>
				<div class="wrap">
				  <h1>NeuroLoadAnalyzer</h1>
				  <p class="subtitle">Анализ метрик</p>
				  <div id="status" aria-live="polite">
				    <div class="spinner" aria-hidden="true"></div>
				    <div class="status-text">Выполняется анализ…</div>
				  </div>
				  <div id="result"></div>
				</div>
				<script>
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
		html.append("<style>")
				.append(".nla-section{margin:0 0 1.5rem}")
				.append(".nla-section h2{margin:0 0 .6rem;font-size:1.05rem}")
				.append(".nla-meta{color:#5c6b7a;font-size:.9rem;margin:0 0 1rem}")
				.append("table.nla{width:100%;border-collapse:collapse;font-size:.92rem}")
				.append("table.nla th,table.nla td{border-bottom:1px solid #d5dde6;padding:.55rem .4rem;text-align:left;vertical-align:top}")
				.append("table.nla th{color:#5c6b7a;font-weight:600}")
				.append(".badge{display:inline-block;padding:.15rem .5rem;border-radius:999px;font-size:.8rem;font-weight:600}")
				.append(".badge-ok{background:#e5f6ea;color:#1f7a3f}")
				.append(".badge-violation{background:#fdecec;color:#9b1c1c}")
				.append(".badge-skipped{background:#eef1f4;color:#5c6b7a}")
				.append(".badge-error{background:#fff4e5;color:#9a5b00}")
				.append("code{font-size:.8rem;word-break:break-all}")
				.append("</style>");

		html.append("<p class=\"nla-meta\">Каталог плагинов: ")
				.append(esc(report.catalogSource()))
				.append(" · range=")
				.append(esc(report.timeRange().rangeForPromQl()))
				.append(" · evalTime=")
				.append(report.timeRange().evaluationTimeSec() == null ? "now" : report.timeRange().evaluationTimeSec())
				.append("</p>");

		html.append("<div class=\"nla-section\"><h2>Цели (Тип_Софт_Назначение)</h2>");
		if (report.typedTargets().isEmpty()) {
			html.append("<p class=\"nla-meta\">Параметры вида Тип_Софт_Назначение не найдены.</p>");
		} else {
			html.append("<table class=\"nla\"><thead><tr>")
					.append("<th>Параметр</th><th>Тип</th><th>Софт</th><th>Назначение</th><th>Значение</th>")
					.append("</tr></thead><tbody>");
			for (TypedTarget target : report.typedTargets()) {
				html.append("<tr>")
						.append("<td>").append(esc(target.canonicalName())).append("</td>")
						.append("<td>").append(esc(target.type())).append("</td>")
						.append("<td>").append(esc(target.software())).append("</td>")
						.append("<td>").append(esc(target.purpose())).append("</td>")
						.append("<td>").append(esc(target.value())).append("</td>")
						.append("</tr>");
			}
			html.append("</tbody></table>");
		}
		html.append("</div>");

		html.append("<div class=\"nla-section\"><h2>Результаты плагинов</h2>");
		if (report.pluginResults().isEmpty()) {
			html.append("<p>Тут будет результат</p>");
		} else {
			html.append("<table class=\"nla\"><thead><tr>")
					.append("<th>Правило</th><th>Статус</th><th>Значение</th><th>Условие</th><th>Сообщение</th>")
					.append("</tr></thead><tbody>");
			for (PluginResult result : report.pluginResults()) {
				html.append("<tr>")
						.append("<td>").append(esc(result.pluginName())).append("</td>")
						.append("<td>").append(statusBadge(result.status())).append("</td>")
						.append("<td>")
						.append(result.metricValue() == null ? "—" : formatNumber(result.metricValue()))
						.append("</td>")
						.append("<td>").append(esc(result.conditionDescription())).append("</td>")
						.append("<td>").append(esc(nullToEmpty(result.message())));
				if (result.boundQuery() != null && !result.boundQuery().isBlank()) {
					html.append("<br><code>").append(esc(result.boundQuery())).append("</code>");
				}
				html.append("</td></tr>");
			}
			html.append("</tbody></table>");
		}
		html.append("</div>");

		return html.toString();
	}

	private static String statusBadge(PluginRunStatus status) {
		String css = switch (status) {
			case OK -> "badge-ok";
			case VIOLATION -> "badge-violation";
			case SKIPPED -> "badge-skipped";
			case ERROR -> "badge-error";
		};
		return "<span class=\"badge " + css + "\">" + esc(status.name()) + "</span>";
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
