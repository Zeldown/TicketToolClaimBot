package fr.zeldown.ticketbot.stats;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

import javax.imageio.ImageIO;

import lombok.NonNull;

public final class ChartCanvas {

	private static final float[] DASH = { 3F, 6F };

	private final BufferedImage image;
	private final Graphics2D    graphics;

	private ChartCanvas(final BufferedImage image, final Graphics2D graphics) {
		this.image = image;
		this.graphics = graphics;
	}

	public static @NonNull ChartCanvas create(final @NonNull String title, final String subtitle) {
		final BufferedImage image = new BufferedImage(ChartTheme.WIDTH * ChartTheme.SCALE, ChartTheme.HEIGHT * ChartTheme.SCALE, BufferedImage.TYPE_INT_RGB);
		final Graphics2D graphics = image.createGraphics();
		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
		graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		graphics.scale(ChartTheme.SCALE, ChartTheme.SCALE);
		graphics.setColor(ChartTheme.BACKGROUND);
		graphics.fillRect(0, 0, ChartTheme.WIDTH, ChartTheme.HEIGHT);

		final ChartCanvas canvas = new ChartCanvas(image, graphics);
		canvas.text(title, ChartTheme.PADDING, 54, ChartTheme.bold(25F), ChartTheme.TEXT);
		return subtitle == null ? canvas : canvas.text(subtitle, ChartTheme.PADDING, 78, ChartTheme.regular(13F), ChartTheme.MUTED);
	}

	public byte[] export() {
		this.graphics.dispose();
		try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			ImageIO.write(this.image, "png", output);
			return output.toByteArray();
		} catch (final IOException e) {
			throw new UncheckedIOException("Unable to render the chart", e);
		}
	}

	public @NonNull ChartCanvas kpis(final @NonNull List<ChartKpi> kpis) {
		final int slot = (ChartTheme.WIDTH - ChartTheme.PADDING * 2) / Math.max(1, kpis.size());
		for (int index = 0; index < kpis.size(); index++) {
			final ChartKpi kpi = kpis.get(index);
			final int x = ChartTheme.PADDING + index * slot;
			this.rounded(x, 100, slot - 16, 92, 16, ChartTheme.SURFACE);
			this.text(kpi.getLabel().toUpperCase(), x + 20, 128, ChartTheme.regular(12F), ChartTheme.MUTED);
			this.text(kpi.getValue(), x + 20, 168, ChartTheme.bold(30F), ChartTheme.TEXT);
			if (kpi.getDelta() != null) {
				final Color color = kpi.isPositive() ? ChartTheme.SUCCESS : ChartTheme.DANGER;
				final int width = this.width(kpi.getDelta(), ChartTheme.bold(12F)) + 20;
				this.rounded(x + slot - width - 32, 140, width, 26, 13, ChartTheme.alpha(color, 45));
				this.text(kpi.getDelta(), x + slot - width - 22, 158, ChartTheme.bold(12F), color);
			}
		}
		return this;
	}

	public int width(final @NonNull String text, final @NonNull Font font) {
		return this.graphics.getFontMetrics(font).stringWidth(text);
	}

	public @NonNull ChartCanvas grid(final int top, final int bottom, final long max) {
		final Font font = ChartTheme.regular(11F);
		this.graphics.setStroke(new BasicStroke(1F, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1F, ChartCanvas.DASH, 0F));
		for (int step = 0; step <= 4; step++) {
			final int y = bottom - (bottom - top) * step / 4;
			this.graphics.setColor(ChartTheme.GRID);
			this.graphics.drawLine(ChartTheme.LEFT, y, ChartTheme.WIDTH - ChartTheme.PADDING, y);
			this.right(Long.toString(max * step / 4), ChartTheme.LEFT - 14, y + 4, font, ChartTheme.MUTED);
		}

		this.graphics.setStroke(new BasicStroke(1F));
		return this;
	}

	public @NonNull ChartCanvas legend(final @NonNull String first, final @NonNull String second) {
		final Font font = ChartTheme.regular(13F);
		final int right = ChartTheme.WIDTH - ChartTheme.PADDING;
		final int secondary = right - this.width(second, font);
		final int primary = secondary - this.width(first, font) - 34;
		this.circle(primary - 16, 40, 9, ChartTheme.PRIMARY).text(first, primary, 48, font, ChartTheme.MUTED);
		return this.circle(secondary - 16, 40, 9, ChartTheme.SUCCESS).text(second, secondary, 48, font, ChartTheme.MUTED);
	}

	public @NonNull ChartCanvas circle(final int x, final int y, final int size, final @NonNull Paint paint) {
		this.graphics.setPaint(paint);
		this.graphics.fillOval(x, y, size, size);
		return this;
	}

	public @NonNull ChartCanvas area(final long[] values, final long max, final int top, final int bottom, final @NonNull Color color) {
		final int[] xs = new int[values.length];
		final int[] ys = new int[values.length];
		final double slot = (double) (ChartTheme.WIDTH - ChartTheme.PADDING - ChartTheme.LEFT) / Math.max(1, values.length - 1);
		for (int index = 0; index < values.length; index++) {
			xs[index] = (int) (ChartTheme.LEFT + index * slot);
			ys[index] = (int) (bottom - (double) values[index] * (bottom - top) / Math.max(1L, max));
		}

		final Path2D line = this.curve(xs, ys);
		final Path2D fill = (Path2D) line.clone();
		fill.lineTo(xs[xs.length - 1], bottom);
		fill.lineTo(xs[0], bottom);
		fill.closePath();
		this.graphics.setPaint(new GradientPaint(0F, top, ChartTheme.alpha(color, 110), 0F, bottom, ChartTheme.alpha(color, 0)));
		this.graphics.fill(fill);
		this.graphics.setColor(color);
		this.graphics.setStroke(new BasicStroke(3F, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		this.graphics.draw(line);
		this.graphics.setStroke(new BasicStroke(1F));
		return this.circle(xs[xs.length - 1] - 5, ys[ys.length - 1] - 5, 10, color);
	}

	public @NonNull ChartCanvas text(final @NonNull String text, final int x, final int y, final @NonNull Font font, final @NonNull Color color) {
		this.graphics.setFont(font);
		this.graphics.setColor(color);
		this.graphics.drawString(text, x, y);
		return this;
	}

	public @NonNull ChartCanvas right(final @NonNull String text, final int x, final int y, final @NonNull Font font, final @NonNull Color color) {
		return this.text(text, x - this.width(text, font), y, font, color);
	}

	public @NonNull ChartCanvas rounded(final int x, final int y, final int width, final int height, final int radius, final @NonNull Paint paint) {
		this.graphics.setPaint(paint);
		this.graphics.fillRoundRect(x, y, Math.max(1, width), Math.max(1, height), radius, radius);
		return this;
	}

	private @NonNull Path2D curve(final int[] xs, final int[] ys) {
		final Path2D path = new Path2D.Double();
		path.moveTo(xs[0], ys[0]);
		for (int index = 0; index < xs.length - 1; index++) {
			final int previous = Math.max(0, index - 1);
			final int next = Math.min(xs.length - 1, index + 2);
			final double firstX = xs[index] + (xs[index + 1] - xs[previous]) / 6D;
			final double firstY = ys[index] + (ys[index + 1] - ys[previous]) / 6D;
			final double secondX = xs[index + 1] - (xs[next] - xs[index]) / 6D;
			final double secondY = ys[index + 1] - (ys[next] - ys[index]) / 6D;
			path.curveTo(firstX, firstY, secondX, secondY, xs[index + 1], ys[index + 1]);
		}
		return path;
	}

}