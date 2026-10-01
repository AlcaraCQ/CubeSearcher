package dev.cubesearcher.search;

import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/**
 * 在客户端「渲染范围内」的已加载区块里搜索由目标方块组成的长方体结构。
 *
 * <p>目标方块与结构尺寸（长 / 宽 / 高）以及「是否区分长宽的 x/z 方向」全部来自
 * {@link SearchOptions}（即 {@code config/cubesearcher.properties}），本类不再写死任何数值。</p>
 *
 * <p>搜索顺序：以玩家所在区块为中心，一圈一圈由近到远地扫描；
 * 一旦命中就立刻返回（即「找到后停止搜索」），因此得到的一定是最近的那个结构。</p>
 *
 * <p>性能：先看区块里每个 16x16x16 小节的调色板（{@link LevelChunkSection#maybeHas}），
 * 只有确实含有目标方块的小节才会真正逐格读取。命中判定会提前短路，
 * 所以即使把结构尺寸配得比较大，正常世界里一次搜索仍然是毫秒级的。</p>
 */
public final class StructureSearcher {
	/**
	 * 最多向外找多少个区块。
	 * 客户端只会加载渲染距离以内的区块，所以「已加载」等价于「在渲染范围内」，
	 * 这个上限只是为了防止极端情况下的空转（原版渲染距离上限是 32）。
	 */
	public static final int MAX_RADIUS_CHUNKS = 32;

	private StructureSearcher() {
	}

	/**
	 * 从 {@code origin}（一般传玩家所在方块坐标）向外搜索。
	 *
	 * @param options 目标方块、结构尺寸与朝向规则
	 * @return 找到的结构；渲染范围内没有则返回 {@code null}
	 */
	@Nullable
	public static FoundStructure search(ClientLevel level, BlockPos origin, SearchOptions options) {
		final int centerChunkX = origin.getX() >> 4;
		final int centerChunkZ = origin.getZ() >> 4;

		// 半径为 0 的圈是玩家所在区块，之后一圈比一圈远。
		for (int radius = 0; radius <= MAX_RADIUS_CHUNKS; radius++) {
			for (int dz = -radius; dz <= radius; dz++) {
				for (int dx = -radius; dx <= radius; dx++) {
					// 只处理当前这一圈（切比雪夫距离正好等于 radius 的格子）
					if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
						continue;
					}

					FoundStructure found = scanChunk(level, centerChunkX + dx, centerChunkZ + dz, options);

					if (found != null) {
						return found;
					}
				}
			}
		}

		return null;
	}

	/**
	 * 扫描单个区块：先挑出所有目标方块，再以它们为最小角去尝试各种朝向。
	 */
	@Nullable
	private static FoundStructure scanChunk(ClientLevel level, int chunkX, int chunkZ, SearchOptions options) {
		// requireChunk = false：区块没加载就返回 null，绝不会因此加载/生成区块
		final ChunkAccess chunkAccess = level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);

		if (!(chunkAccess instanceof LevelChunk chunk)) {
			return null;
		}

		final LevelChunkSection[] sections = chunk.getSections();
		final Predicate<BlockState> isTarget = options.predicate();
		final int originX = chunkX << 4;
		final int originZ = chunkZ << 4;

		for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
			final LevelChunkSection section = sections[sectionIndex];

			// 空小节 / 调色板里没有目标方块的小节直接跳过
			if (section == null || section.hasOnlyAir() || !section.maybeHas(isTarget)) {
				continue;
			}

			final int originY = chunk.getSectionYFromSectionIndex(sectionIndex) << 4;

			for (int y = 0; y < 16; y++) {
				for (int z = 0; z < 16; z++) {
					for (int x = 0; x < 16; x++) {
						if (!options.matches(section.getBlockState(x, y, z))) {
							continue;
						}

						final FoundStructure found = matchAt(level, originX + x, originY + y, originZ + z, options);

						if (found != null) {
							return found;
						}
					}
				}
			}
		}

		return null;
	}

	/**
	 * 以 (x, y, z) 作为结构的最小角，按配置的朝向规则尝试匹配。
	 *
	 * <p>因为结构整体都是目标方块，所以最小角一定也是目标方块，
	 * 因此只需在所有目标方块上尝试即可覆盖全部结构。</p>
	 */
	@Nullable
	private static FoundStructure matchAt(ClientLevel level, int x, int y, int z, SearchOptions options) {
		final int length = options.length();
		final int width = options.width();
		final int height = options.height();

		switch (options.orientation()) {
			case LENGTH_ALONG_X -> {
				// 只承认「长沿 X、宽沿 Z」
				if (isCompleteBox(level, options, x, y, z, length, height, width)) {
					return new FoundStructure(x, y, z, length, height, width);
				}
			}
			case LENGTH_ALONG_Z -> {
				// 只承认「长沿 Z、宽沿 X」
				if (isCompleteBox(level, options, x, y, z, width, height, length)) {
					return new FoundStructure(x, y, z, width, height, length);
				}
			}
			case BOTH -> {
				if (isCompleteBox(level, options, x, y, z, length, height, width)) {
					return new FoundStructure(x, y, z, length, height, width);
				}

				// 长宽相等时两种朝向是同一个盒子，没必要再算一遍
				if (length != width && isCompleteBox(level, options, x, y, z, width, height, length)) {
					return new FoundStructure(x, y, z, width, height, length);
				}
			}
		}

		return null;
	}

	/**
	 * 检查从 (minX, minY, minZ) 开始、尺寸为 sizeX x sizeY x sizeZ 的长方体是否全部是目标方块。
	 * 未加载的位置读取到的是空气，自然判定为不匹配。
	 */
	private static boolean isCompleteBox(ClientLevel level, SearchOptions options,
			int minX, int minY, int minZ, int sizeX, int sizeY, int sizeZ) {
		for (int dy = 0; dy < sizeY; dy++) {
			for (int dz = 0; dz < sizeZ; dz++) {
				for (int dx = 0; dx < sizeX; dx++) {
					final BlockPos pos = new BlockPos(minX + dx, minY + dy, minZ + dz);

					if (!options.matches(level.getBlockState(pos))) {
						return false;
					}
				}
			}
		}

		return true;
	}
}
