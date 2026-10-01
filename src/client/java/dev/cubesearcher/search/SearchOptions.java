package dev.cubesearcher.search;

import java.util.List;
import java.util.function.Predicate;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 一次搜索的全部可配置参数，来自 {@code config/cubesearcher.properties}。
 *
 * @param blocks      目标方块；命中结构里的每一格都必须是其中之一
 * @param length      长（格）
 * @param width       宽（格）
 * @param height      高（格）
 * @param orientation 长、宽与 X / Z 水平轴的对应关系
 */
public record SearchOptions(List<Block> blocks, int length, int width, int height, Orientation orientation) {
	/** 单边最大格数，防止配置写出离谱的数值。 */
	public static final int MAX_SIZE = 64;

	public SearchOptions {
		blocks = List.copyOf(blocks);
		length = clamp(length);
		width = clamp(width);
		height = clamp(height);
	}

	/** 长、宽与水平轴的对应方式（即需求里的「是否区分长、宽的 x/z 方向」）。 */
	public enum Orientation {
		/** 不区分：长沿 X 或沿 Z 都算命中（默认，等价于最初需求③）。 */
		BOTH,
		/** 区分：只匹配「长沿 X 轴、宽沿 Z 轴」。 */
		LENGTH_ALONG_X,
		/** 区分：只匹配「长沿 Z 轴、宽沿 X 轴」。 */
		LENGTH_ALONG_Z
	}

	/** 这一格是否属于目标方块。 */
	public boolean matches(BlockState state) {
		for (int i = 0; i < blocks.size(); i++) {
			if (state.is(blocks.get(i))) {
				return true;
			}
		}

		return false;
	}

	/** 给 {@code LevelChunkSection#maybeHas} 用的谓词。 */
	public Predicate<BlockState> predicate() {
		return this::matches;
	}

	/** 结构格数（长 x 宽 x 高）。 */
	public int volume() {
		return length * width * height;
	}

	private static int clamp(int value) {
		return Math.max(1, Math.min(MAX_SIZE, value));
	}
}
