package com.apocscode.mcai.task;

import com.apocscode.mcai.MCAi;
import com.apocscode.mcai.entity.CompanionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Task: Mine ores nearby.
 * Supports targeted ore type (e.g. only iron) or all ores.
 * Uses OreGuide for ore identification and tool-tier checks.
 * Expands search radius progressively (16→32→48→64) if no ores found.
 */
public class MineOresTask extends CompanionTask {

    private static final int[] EXPAND_RADII = {32, 48, 64}; // fallback search radii
    private int radius;
    private final int maxOres;
    @Nullable
    private final OreGuide.Ore targetOre; // null = mine all ores
    private final Deque<BlockPos> targets = new ArrayDeque<>();
    private BlockPos currentTarget;
    private int stuckTimer = 0;
    private int oresMined = 0;
    private int scanAttempts = 0;
    private int consecutiveSkips = 0;
    private static final int MAX_SCAN_ATTEMPTS = 3;
    private static final int STUCK_TIMEOUT_TICKS = 60; // 3 seconds per block
    private static final int MAX_CONSECUTIVE_SKIPS = 3;
    private static final int TOOL_LOW_DURABILITY = 10;
    private boolean toolWarningGiven = false;
    private boolean foodWarningGiven = false;
    private int emergencyDigAttempts = 0;

    /** Constructor for mining all ore types. */
    public MineOresTask(CompanionEntity companion, int radius, int maxOres) {
        this(companion, radius, maxOres, null);
    }

    /** Constructor with optional targeted ore type. */
    public MineOresTask(CompanionEntity companion, int radius, int maxOres, @Nullable OreGuide.Ore targetOre) {
        super(companion);
        this.radius = radius;
        this.maxOres = maxOres > 0 ? maxOres : 999;
        this.targetOre = targetOre;
    }

    private void finishMining() {
        if (oresMined >= maxOres) complete();
        else fail("Добыто " + oresMined + " из " + maxOres + " блоков руды. Проверь путь, кирку и наличие руды рядом.");
    }

    @Override
    public String getTaskName() {
        String oreLabel = targetOre != null ? com.apocscode.mcai.ai.PlayerReplies.resource(targetOre.name) : "руда";
        return "Добыча руды: " + oreLabel + " (r=" + radius + ")";
    }

    @Override
    public int getProgressPercent() {
        return maxOres > 0 ? (oresMined * 100) / maxOres : -1;
    }

    @Override
    protected void start() {
        scanForOres();
        if (targets.isEmpty()) {
            // Expand search radius progressively before giving up
            for (int expandRadius : EXPAND_RADII) {
                if (expandRadius <= radius) continue;
                MCAi.LOGGER.info("MineOresTask: no ores at r={}, expanding to r={}", radius, expandRadius);
                radius = expandRadius;
                scanForOres();
                if (!targets.isEmpty()) break;
            }
        }
        if (targets.isEmpty()) {
            String oreLabel = targetOre != null ? com.apocscode.mcai.ai.PlayerReplies.resource(targetOre.name) : "руда";
            int currentY = companion.blockPosition().getY();
            String yHint = "";
            if (targetOre != null) {
                if (currentY < targetOre.minY || currentY > targetOre.maxY) {
                    yHint = " Моя высота Y=" + currentY + "; ресурс " + targetOre.name +
                            " встречается между Y=" + targetOre.minY + " и Y=" + targetOre.maxY +
                            ". Лучшая высота Y=" + targetOre.bestY + ".";
                } else {
                    yHint = " Моя высота Y=" + currentY + " (right range,; ресурс none visible in " + radius + " блоков).";
                }
            }
            say("Не найдено: " + oreLabel + " рядом." + yHint);
            fail("Не найдено: " + oreLabel + "; радиус поиска: " + radius + " блоков");
            return;
        }
        String oreLabel = targetOre != null ? com.apocscode.mcai.ai.PlayerReplies.resource(targetOre.name) : "руда";
        say("Найдено: " + targets.size() + " " + oreLabel + " блоков для добычи.");
    }

    @Override
    protected void tick() {
        if (oresMined >= maxOres) {
            String oreLabel = targetOre != null ? com.apocscode.mcai.ai.PlayerReplies.resource(targetOre.name) : "руда";
            say("Добыто: " + oresMined + " " + oreLabel + "!");
            finishMining();
            return;
        }

        // Health check — eat food if HP < 50%
        if (BlockHelper.tryEatIfLowHealth(companion, 0.5f)) {
            say("Ем, чтобы восстановить здоровье.");
        } else if (!foodWarningGiven && companion.getHealth() / companion.getMaxHealth() < 0.3f) {
            say("Мало здоровья, а еды нет.");
            foodWarningGiven = true;
        }

        // Tool durability check
        if (!toolWarningGiven && !BlockHelper.hasUsablePickaxe(companion, 0)) {
            // Try auto-crafting a new pickaxe before giving up
            if (BlockHelper.tryAutoCraftPickaxe(companion)) {
                String oreLabel = targetOre != null ? com.apocscode.mcai.ai.PlayerReplies.resource(targetOre.name) : "руда";
                say("Сделал кирку, продолжаю добычу: " + oreLabel + ".");
            } else {
                String oreLabel = targetOre != null ? com.apocscode.mcai.ai.PlayerReplies.resource(targetOre.name) : "руда";
                say("Нет подходящей кирки, изготовить её не удалось. Добыто: " + oresMined + " " + oreLabel + " на данный момент.");
                toolWarningGiven = true;
                finishMining();
                return;
            }
        }

        if (targets.isEmpty()) {
            scanAttempts++;
            if (scanAttempts > MAX_SCAN_ATTEMPTS) {
                String oreLabel = targetOre != null ? com.apocscode.mcai.ai.PlayerReplies.resource(targetOre.name) : "руда";
                if (oresMined == 0) {
                    say("Не удалось найти: " + oreLabel + " после " + MAX_SCAN_ATTEMPTS + " поисков.");
                    fail("Не найдено: " + oreLabel + " после " + MAX_SCAN_ATTEMPTS + " поисков");
                } else {
                    say("Добыча завершена. Добыто: " + oresMined + " " + oreLabel + ".");
                    finishMining();
                }
                return;
            }
            scanForOres();
            if (targets.isEmpty()) {
                String oreLabel = targetOre != null ? com.apocscode.mcai.ai.PlayerReplies.resource(targetOre.name) : "руда";
                if (oresMined == 0) {
                    say("Не найдено: " + oreLabel + " рядом.");
                    fail("Не найдено: " + oreLabel + "; радиус поиска: " + radius + " блоков");
                } else {
                    say("Больше не найдено: " + oreLabel + "; добыто: " + oresMined + " всего.");
                    finishMining();
                }
                return;
            }
        }

        if (currentTarget == null) {
            currentTarget = targets.peek();
            stuckTimer = 0;
        }

        if (companion.level().getBlockState(currentTarget).isAir()) {
            targets.poll();
            currentTarget = null;
            return;
        }

        if (isInReach(currentTarget, 3.5)) {
            // Safety: skip ores that would expose lava
            if (!BlockHelper.isSafeToMine(companion.level(), currentTarget)) {
                targets.poll();
                currentTarget = null;
                stuckTimer = 0;
                return;
            }
            // Tool-tier check: skip ores the companion can't harvest
            BlockState targetState = companion.level().getBlockState(currentTarget);
            if (!companion.canHarvestBlock(targetState)) {
                targets.poll();
                currentTarget = null;
                stuckTimer = 0;
                consecutiveSkips++;
                if (consecutiveSkips >= MAX_CONSECUTIVE_SKIPS) {
                    OreGuide.Ore ore = OreGuide.identifyOre(targetState);
                    String tierHint = ore != null
                            ? " Нужна кирка уровня " + ore.tierName() + " или выше."
                            : " Нужна кирка уровня a better pickaxe.";
                    say("Нет подходящей кирки для этой руды." + tierHint);
                    fail("Недостаточный уровень кирки" + tierHint);
                    return;
                }
                return;
            }
            companion.equipBestToolForBlock(targetState);
            boolean broken = BlockHelper.breakBlock(companion, currentTarget);
            // Handle falling blocks (gravel/sand) above the mined ore
            handleFallingBlocks(currentTarget.above());
            targets.poll();
            currentTarget = null;
            stuckTimer = 0;
            consecutiveSkips = 0;
            if (broken) oresMined++;
        } else {
            navigateTo(currentTarget);
            stuckTimer++;
            if (stuckTimer > STUCK_TIMEOUT_TICKS) {
                // Try emergency dig-out before skipping
                if (emergencyDigAttempts < 3) {
                    boolean dug = BlockHelper.emergencyDigOut(companion);
                    if (dug) {
                        emergencyDigAttempts++;
                        stuckTimer = 0;
                        return;
                    }
                }
                targets.poll();
                currentTarget = null;
                stuckTimer = 0;
                consecutiveSkips++;
                if (consecutiveSkips >= MAX_CONSECUTIVE_SKIPS) {
                    if (oresMined == 0) {
                        say("Не могу добраться до руды.");
                        fail("Не удалось добраться до руды");
                    } else {
                        say("Оставшаяся руда недоступна. Добыто: " + oresMined + ".");
                        finishMining();
                    }
                    return;
                }
            }
        }
    }

    /**
     * Handle gravity-affected blocks (gravel, sand, concrete powder) above a mined position.
     * Prevents falling blocks from burying the companion after mining an ore.
     */
    private void handleFallingBlocks(BlockPos abovePos) {
        Level level = companion.level();
        int maxFalling = 10;
        BlockPos checkPos = abovePos;
        for (int i = 0; i < maxFalling; i++) {
            if (level.getBlockState(checkPos).getBlock() instanceof FallingBlock) {
                companion.equipBestToolForBlock(level.getBlockState(checkPos));
                BlockHelper.breakBlock(companion, checkPos);
                checkPos = checkPos.above();
            } else {
                break;
            }
        }
    }

    @Override
    protected void cleanup() {
        targets.clear();
    }

    private void scanForOres() {
        targets.clear();
        List<BlockPos> found;
        if (targetOre != null) {
            found = scanForSpecificOre(companion, targetOre, radius, maxOres - oresMined);
        } else {
            found = BlockHelper.scanForOres(companion, radius, maxOres - oresMined);
        }
        targets.addAll(found);
    }

    /**
     * Scan for a specific ore type within radius.
     */
    private static List<BlockPos> scanForSpecificOre(CompanionEntity companion, OreGuide.Ore ore,
                                                      int radius, int maxResults) {
        BlockPos center = companion.blockPosition();
        Level level = companion.level();
        List<BlockPos> results = new ArrayList<>();

        // Clamp Y to world bounds (-64 to 319 in overworld)
        int minY = Math.max(-radius, level.getMinBuildHeight() - center.getY());
        int maxY = Math.min(radius, level.getMaxBuildHeight() - 1 - center.getY());

        for (int x = -radius; x <= radius; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos pos = center.offset(x, y, z);
                    // Skip blocks inside the home area
                    if (companion.isInHomeArea(pos)) continue;
                    BlockState state = companion.level().getBlockState(pos);
                    if (ore.matches(state)) {
                        results.add(pos);
                    }
                }
            }
        }

        // Sort by distance (mine closest first)
        results.sort((a, b) -> {
            double distA = companion.distanceToSqr(a.getX() + 0.5, a.getY() + 0.5, a.getZ() + 0.5);
            double distB = companion.distanceToSqr(b.getX() + 0.5, b.getY() + 0.5, b.getZ() + 0.5);
            return Double.compare(distA, distB);
        });

        if (results.size() > maxResults) return results.subList(0, maxResults);
        return results;
    }
}
