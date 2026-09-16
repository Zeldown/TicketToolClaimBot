package fr.zeldown.ticketbot.stats;

import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.util.List;

import lombok.NonNull;

public final class ChartRenderer {

	private static final int TOP = 214;
	private static final int BOTTOM = ChartTheme.HEIGHT - 58;

	private ChartRenderer() {}

	public static byte[] heatmap(final @NonNull String title, final String subtitle, final long[][] values, final @NonNull List<String> rows) {
		final ChartCanvas canvas = ChartCanvas.create(title, subtitle);
		final int cell = (ChartTheme.WIDTH - ChartTheme.LEFT - ChartTheme.PADDING) / 24;
		final int height = (ChartTheme.HEIGHT - 170) / Math.max(1, values.length);
		long max = 1L;
		for (final long[] row : values) {
			max = Math.max(max, ChartRenderer.max(row));
		}

		for (int row = 0; row < values.length; row++) {
			final int y = 108 + row * height;
			canvas.text(rows.get(row).substring(0, 3), ChartTheme.PADDING, y + height / 2 + 4, ChartTheme.regular(12F), ChartTheme.MUTED);
			for (int hour = 0; hour < 24; hour++) {
				canvas.rounded(ChartTheme.LEFT + hour * cell, y, cell - 4, height - 6, 7, ChartRenderer.shade((float) values[row][hour] / max));
			}
		}

		for (int hour = 0; hour < 24; hour += 3) {
			canvas.text(hour + "h", ChartTheme.LEFT + hour * cell, ChartTheme.HEIGHT - 44, ChartTheme.regular(11F), ChartTheme.MUTED);
		}

		final Font font = ChartTheme.regular(11F);
		final int end = ChartTheme.WIDTH - ChartTheme.PADDING - canvas.width("plus", font) - 10;
		for (int step = 0; step < 24; step++) {
			canvas.rounded(end - 120 + step * 5, ChartTheme.HEIGHT - 34, 4, 9, 2, ChartRenderer.shade(step / 23F));
		}

		canvas.right("moins", end - 130, ChartTheme.HEIGHT - 26, font, ChartTheme.MUTED);
		return canvas.text("plus", end + 10, ChartTheme.HEIGHT - 26, font, ChartTheme.MUTED).export();
	}

	public static byte[] ranking(final @NonNull String title, final String subtitle, final @NonNull List<ChartKpi> kpis, final @NonNull List<String> labels, final long[] values) {
		final ChartCanvas canvas = ChartCanvas.create(title, subtitle).kpis(kpis);
		final long max = Math.max(1L, ChartRenderer.max(values));
		final int start = kpis.isEmpty() ? 110 : 214;
		final int area = ChartTheme.HEIGHT - start - 40;
		final int slot = Math.min(46, area / Math.max(1, values.length));
		final int top = start + (area - slot * values.length) / 2;
		final int left = 210;
		for (int index = 0; index < values.length; index++) {
			final int y = top + index * slot;
			final int width = (int) (values[index] * (ChartTheme.WIDTH - left - ChartTheme.PADDING - 60) / max);
			canvas.circle(ChartTheme.PADDING, y + slot / 2 - 22, 22, ChartTheme.SURFACE);
			canvas.text(Integer.toString(index + 1), ChartTheme.PADDING + (index < 9 ? 8 : 4), y + slot / 2 - 6, ChartTheme.bold(12F), ChartTheme.MUTED);
			canvas.text(ChartRenderer.cut(canvas, labels.get(index), left - 78), ChartTheme.PADDING + 32, y + slot / 2 - 5, ChartTheme.regular(14F), ChartTheme.TEXT);
			canvas.rounded(left, y + slot / 2 - 19, ChartTheme.WIDTH - left - ChartTheme.PADDING - 60, 18, 9, ChartTheme.SURFACE);
			canvas.rounded(left, y + slot / 2 - 19, width, 18, 9, new GradientPaint(left, 0F, ChartTheme.PRIMARY, left + Math.max(1, width), 0F, ChartTheme.ACCENT));
			canvas.text(Long.toString(values[index]), ChartTheme.WIDTH - ChartTheme.PADDING - 48, y + slot / 2 - 5, ChartTheme.bold(14F), ChartTheme.TEXT);
		}
		return canvas.export();
	}

	public static byte[] table(final @NonNull String title, final String subtitle, final @NonNull List<ChartKpi> kpis, final @NonNull List<String> columns, final @NonNull List<String[]> rows) {
		final ChartCanvas canvas = ChartCanvas.create(title, subtitle).kpis(kpis);
		final int top = kpis.isEmpty() ? 112 : 224;
		final int width = ChartTheme.WIDTH - ChartTheme.PADDING * 2;
		final int first = ChartTheme.PADDING + width * 34 / 100;
		final int slot = (ChartTheme.WIDTH - ChartTheme.PADDING - first) / Math.max(1, columns.size() - 1);
		final int height = Math.min(46, (ChartTheme.HEIGHT - top - 54) / Math.max(1, rows.size()));
		canvas.text(columns.get(0).toUpperCase(), ChartTheme.PADDING, top, ChartTheme.regular(12F), ChartTheme.MUTED);
		for (int column = 1; column < columns.size(); column++) {
			canvas.right(columns.get(column).toUpperCase(), first + slot * column, top, ChartTheme.regular(12F), ChartTheme.MUTED);
		}

		for (int row = 0; row < rows.size(); row++) {
			final String[] values = rows.get(row);
			final int y = top + 20 + row * height;
			if (row % 2 == 0) {
				canvas.rounded(ChartTheme.PADDING - 12, y, width + 24, height - 5, 10, ChartTheme.SURFACE);
			}

			canvas.text(ChartRenderer.cut(canvas, values[0], first - ChartTheme.PADDING - 20), ChartTheme.PADDING, y + height / 2 + 2, ChartTheme.bold(14F), ChartTheme.TEXT);
			for (int column = 1; column < values.length; column++) {
				canvas.right(values[column], first + slot * column, y + height / 2 + 2, ChartTheme.regular(14F), ChartTheme.TEXT);
			}
		}
		return canvas.export();
	}

	public static byte[] trend(final @NonNull String title, final String subtitle, final @NonNull List<ChartKpi> kpis, final @NonNull List<String> labels, final long[] first, final long[] second, final @NonNull String primary, final @NonNull String secondary) {
		final ChartCanvas canvas = ChartCanvas.create(title, subtitle).legend(primary, secondary).kpis(kpis);
		final long max = Math.max(1L, Math.max(ChartRenderer.max(first), ChartRenderer.max(second)));
		canvas.grid(ChartRenderer.TOP, ChartRenderer.BOTTOM, max);
		canvas.area(second, max, ChartRenderer.TOP, ChartRenderer.BOTTOM, ChartTheme.SUCCESS);
		canvas.area(first, max, ChartRenderer.TOP, ChartRenderer.BOTTOM, ChartTheme.PRIMARY);

		final double slot = (double) (ChartTheme.WIDTH - ChartTheme.PADDING - ChartTheme.LEFT) / Math.max(1, labels.size() - 1);
		final int every = Math.max(1, labels.size() / 8);
		for (int index = 0; index < labels.size(); index += every) {
			canvas.text(labels.get(index), (int) (ChartTheme.LEFT + index * slot) - 14, ChartRenderer.BOTTOM + 26, ChartTheme.regular(11F), ChartTheme.MUTED);
		}
		return canvas.export();
	}

	private static long max(final long[] values) {
		long max = 0L;
		for (final long value : values) {
			max = Math.max(max, value);
		}
		return max;
	}

	private static @NonNull Color shade(final float ratio) {
		if (ratio <= 0F) {
			return ChartTheme.SURFACE;
		}
		return ratio < 0.5F ? ChartTheme.mix(ChartTheme.SURFACE, ChartTheme.PRIMARY, 0.25F + ratio * 1.5F) : ChartTheme.mix(ChartTheme.PRIMARY, ChartTheme.ACCENT, (ratio - 0.5F) * 2F);
	}

	private static @NonNull String cut(final @NonNull ChartCanvas canvas, final @NonNull String text, final int width) {
		final Font font = ChartTheme.regular(14F);
		String cut = text;
		while (cut.length() > 3 && canvas.width(cut + "…", font) > width) {
			cut = cut.substring(0, cut.length() - 1);
		}
		return cut.equals(text) ? text : cut + "…";
	}

}