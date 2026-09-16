package fr.zeldown.ticketbot.stats;

import java.awt.Color;
import java.awt.Font;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class ChartTheme {

	public static final int   SCALE = 2;
	public static final int   WIDTH = 920;
	public static final int   HEIGHT = 480;
	public static final int   PADDING = 40;
	public static final int   LEFT = 78;
	public static final Color GRID = new Color(0x35373C);
	public static final Color TEXT = new Color(0xF2F3F5);
	public static final Color MUTED = new Color(0x949BA4);
	public static final Color ACCENT = new Color(0xEB459E);
	public static final Color DANGER = new Color(0xED4245);
	public static final Color PRIMARY = new Color(0x5865F2);
	public static final Color SURFACE = new Color(0x2B2D31);
	public static final Color SUCCESS = new Color(0x3BA55D);
	public static final Color BACKGROUND = new Color(0x1E1F22);

	private static Font bold;
	private static Font regular;

	private ChartTheme() {}

	public static void load(final String regular, final String bold) {
		ChartTheme.regular = ChartTheme.read(regular);
		ChartTheme.bold = ChartTheme.read(bold);
		if (ChartTheme.bold == null && ChartTheme.regular != null) {
			ChartTheme.bold = ChartTheme.regular.deriveFont(Font.BOLD);
		}
	}

	public static @NonNull Font bold(final float size) {
		return ChartTheme.bold == null ? new Font(Font.SANS_SERIF, Font.BOLD, (int) size) : ChartTheme.bold.deriveFont(size);
	}

	public static @NonNull Font regular(final float size) {
		return ChartTheme.regular == null ? new Font(Font.SANS_SERIF, Font.PLAIN, (int) size) : ChartTheme.regular.deriveFont(size);
	}

	public static @NonNull Color alpha(final @NonNull Color color, final int alpha) {
		return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
	}

	public static @NonNull Color mix(final @NonNull Color from, final @NonNull Color to, final float ratio) {
		final float bounded = Math.max(0F, Math.min(1F, ratio));
		return new Color((int) (from.getRed() + (to.getRed() - from.getRed()) * bounded), (int) (from.getGreen() + (to.getGreen() - from.getGreen()) * bounded), (int) (from.getBlue() + (to.getBlue() - from.getBlue()) * bounded));
	}

	private static Font read(final String path) {
		if (path == null || path.isEmpty()) {
			return null;
		}

		final Path file = Paths.get(path);
		if (!Files.isRegularFile(file)) {
			log.warn("Font {} not found, falling back to the system font", file);
			return null;
		}

		try {
			return Font.createFont(Font.TRUETYPE_FONT, file.toFile());
		} catch (final Exception e) {
			log.error("Unable to load the font {}", file, e);
			return null;
		}
	}

}