package dev.cubesearcher.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;

import dev.cubesearcher.search.SearchOptions;

/**
 * 模组配置：{@code config/cubesearcher.properties}。
 *
 * <p>提供三个可配置项：</p>
 * <ol>
 *     <li>{@code blocks} —— 要搜索的方块（方块 id，多个用逗号分隔）；</li>
 *     <li>{@code length} / {@code width} / {@code height} —— 结构的长、宽、高格数；</li>
 *     <li>{@code orientation} —— 是否区分长、宽所处的水平轴
 *     （{@code both} 不区分 / {@code length_along_x} / {@code length_along_z}）。</li>
 * </ol>
 *
 * <p>文件不存在时会自动生成一份带注释的默认配置。每次按下快捷键都会重新读取，
 * 所以改完文件回到游戏按一下快捷键就生效，不需要重启游戏。
 * 任何一项写错都只会让该项回退到默认值并给出提示，不会导致搜索崩溃。</p>
 *
 * <p>本类只在客户端主线程（tick）上使用。</p>
 */
public final class CubeSearcherConfig {
	public static final String FILE_NAME = "cubesearcher.properties";

	private static final Logger LOGGER = LoggerFactory.getLogger("cubesearcher");
	private static final CubeSearcherConfig INSTANCE = new CubeSearcherConfig();

	private static final String DEFAULT_BLOCK_ID = "minecraft:light_blue_stained_glass";
	private static final int DEFAULT_LENGTH = 3;
	private static final int DEFAULT_WIDTH = 2;
	private static final int DEFAULT_HEIGHT = 2;
	private static final SearchOptions.Orientation DEFAULT_ORIENTATION = SearchOptions.Orientation.BOTH;

	private static final String DEFAULT_FILE = """
			# ===== Cube Searcher 配置 =====
			# 改完保存后，回到游戏按一次快捷键即生效（每次按快捷键都会重新读取本文件）。
			# 方块 id 请用英文、小写；方块 id 可以省略 "minecraft:" 前缀。

			# 1) 要搜索的方块：可以写多个，用英文逗号分隔。
			#    例：light_blue_stained_glass
			#    例：light_blue_stained_glass,light_gray_stained_glass,glass
			blocks=light_blue_stained_glass

			# 2) 结构尺寸（格）：长 x 宽 x 高，取值 1~64
			length=3
			width=2
			height=2

			# 3) 是否区分「长 / 宽」所处的水平轴：
			#    both           = 不区分，长沿 X 或沿 Z 都算命中（默认）
			#    length_along_x = 区分，只匹配「长沿 X 轴、宽沿 Z 轴」
			#    length_along_z = 区分，只匹配「长沿 Z 轴、宽沿 X 轴」
			orientation=both
			""";

	private List<Block> blocks = List.of();
	private List<String> blockIds = List.of();
	private int length = DEFAULT_LENGTH;
	private int width = DEFAULT_WIDTH;
	private int height = DEFAULT_HEIGHT;
	private SearchOptions.Orientation orientation = DEFAULT_ORIENTATION;

	private String warning;
	private String reportedWarning;
	private String appliedSignature;

	private CubeSearcherConfig() {
	}

	public static CubeSearcherConfig get() {
		return INSTANCE;
	}

	/** 配置文件路径（{@code .minecraft/config/cubesearcher.properties}）。 */
	public static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
	}

	/**
	 * 读取（必要时创建）配置文件。可以在每次搜索前调用。
	 */
	public void reload() {
		final StringBuilder problems = new StringBuilder();

		try {
			final Path file = file();

			if (!Files.exists(file)) {
				if (file.getParent() != null) {
					Files.createDirectories(file.getParent());
				}

				Files.writeString(file, DEFAULT_FILE, StandardCharsets.UTF_8);
				LOGGER.info("已生成默认配置文件 {}", file);
			}

			apply(parse(Files.readAllLines(file, StandardCharsets.UTF_8)), problems);
		} catch (IOException e) {
			problems.append("读取配置失败：").append(e.getMessage()).append("；");
		}

		warning = problems.isEmpty() ? null : problems.toString().trim();

		if (warning != null) {
			LOGGER.warn("配置存在问题，相关项已回退到默认值：{}", warning);
		}

		final String signature = blockIds + " " + length + "x" + width + "x" + height + " " + orientation;

		if (!signature.equals(appliedSignature)) {
			appliedSignature = signature;
			LOGGER.info("当前搜索设置：方块={} 尺寸={}x{}x{} 方向={}", blockIds, length, width, height, orientation);
		}
	}

	/** 当前的搜索参数。 */
	public SearchOptions getOptions() {
		return new SearchOptions(blocks, length, width, height, orientation);
	}

	/** 当前已解析成功的方块 id 列表，仅用于日志/展示。 */
	public List<String> getBlockIds() {
		return blockIds;
	}

	/**
	 * 取出「还没提示过」的配置问题，用于在聊天栏提醒玩家一次。
	 *
	 * @return 新出现（或内容变了）的问题描述；没有问题、或已经提示过同样的问题时返回 {@code null}
	 */
	@Nullable
	public String takeNewWarning() {
		if (Objects.equals(warning, reportedWarning)) {
			return null;
		}

		reportedWarning = warning;
		return warning;
	}

	private void apply(Map<String, String> values, StringBuilder problems) {
		applyBlocks(values, problems);
		length = readSize(values, "length", DEFAULT_LENGTH, problems);
		width = readSize(values, "width", DEFAULT_WIDTH, problems);
		height = readSize(values, "height", DEFAULT_HEIGHT, problems);
		orientation = readOrientation(values.get("orientation"), problems);
	}

	private void applyBlocks(Map<String, String> values, StringBuilder problems) {
		String raw = values.get("blocks");

		if (raw == null) {
			raw = values.get("block");
		}

		final List<Block> resolved = new ArrayList<>();
		final List<String> ids = new ArrayList<>();

		if (raw != null && !raw.isBlank()) {
			for (String token : raw.split(",")) {
				final String id = token.trim();

				if (id.isEmpty()) {
					continue;
				}

				final Identifier identifier = Identifier.tryParse(id.contains(":") ? id : "minecraft:" + id);
				final Block block = identifier == null ? null : resolve(identifier);

				if (block == null) {
					problems.append("未知方块 ").append(id).append("；");
				} else {
					resolved.add(block);
					ids.add(identifier.toString());
				}
			}
		}

		if (resolved.isEmpty()) {
			final Identifier fallback = Identifier.tryParse(DEFAULT_BLOCK_ID);
			final Block block = fallback == null ? null : resolve(fallback);

			if (block != null) {
				resolved.add(block);
				ids.add(fallback.toString());
			}

			problems.append("方块列表为空，已回退到 ").append(DEFAULT_BLOCK_ID).append("；");
		}

		blocks = List.copyOf(resolved);
		blockIds = List.copyOf(ids);
	}

	/**
	 * 解析方块 id。
	 *
	 * <p>这里先 {@code containsKey} 再取值：{@code BuiltInRegistries.BLOCK} 是
	 * {@code DefaultedRegistry}，对未知 id 直接取值会静默返回默认方块（空气），
	 * 那样就会变成一个「搜索空气」的离谱配置。</p>
	 */
	@Nullable
	private static Block resolve(Identifier identifier) {
		if (!BuiltInRegistries.BLOCK.containsKey(identifier)) {
			return null;
		}

		return BuiltInRegistries.BLOCK.getValue(identifier);
	}

	private static int readSize(Map<String, String> values, String key, int fallback, StringBuilder problems) {
		final String raw = values.get(key);

		if (raw == null || raw.isBlank()) {
			return fallback;
		}

		try {
			final int value = Integer.parseInt(raw.trim());

			if (value < 1 || value > SearchOptions.MAX_SIZE) {
				problems.append(key).append("=").append(value).append(" 超出 1~").append(SearchOptions.MAX_SIZE)
						.append("，已改用 ").append(fallback).append("；");
				return fallback;
			}

			return value;
		} catch (NumberFormatException e) {
			problems.append(key).append("=").append(raw).append(" 不是整数，已改用 ").append(fallback).append("；");
			return fallback;
		}
	}

	private static SearchOptions.Orientation readOrientation(String raw, StringBuilder problems) {
		if (raw == null || raw.isBlank()) {
			return DEFAULT_ORIENTATION;
		}

		return switch (raw.trim().toLowerCase(Locale.ROOT)) {
			case "both", "any" -> SearchOptions.Orientation.BOTH;
			case "length_along_x", "x" -> SearchOptions.Orientation.LENGTH_ALONG_X;
			case "length_along_z", "z" -> SearchOptions.Orientation.LENGTH_ALONG_Z;
			default -> {
				problems.append("orientation=").append(raw)
						.append(" 无法识别（可选 both / length_along_x / length_along_z），已按 both 处理；");
				yield DEFAULT_ORIENTATION;
			}
		};
	}

	/** 极简 properties 解析：忽略空行与 # / ! 开头的注释，按第一个 = 或 : 切分。 */
	private static Map<String, String> parse(List<String> lines) {
		final Map<String, String> values = new HashMap<>();

		for (String rawLine : lines) {
			final String line = rawLine.trim();

			if (line.isEmpty() || line.startsWith("#") || line.startsWith("!")) {
				continue;
			}

			int separator = line.indexOf('=');

			if (separator < 0) {
				separator = line.indexOf(':');
			}

			if (separator <= 0) {
				continue;
			}

			final String key = line.substring(0, separator).trim().toLowerCase(Locale.ROOT);
			final String value = line.substring(separator + 1).trim();
			values.put(key, value);
		}

		return values;
	}
}
