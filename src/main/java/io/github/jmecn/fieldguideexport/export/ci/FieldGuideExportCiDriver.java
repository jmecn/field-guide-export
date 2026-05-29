package io.github.jmecn.fieldguideexport.export.ci;

import io.github.jmecn.fieldguideexport.export.CombinedExportOrchestrator;
import io.github.jmecn.fieldguideexport.export.FieldGuideExportPaths;
import io.github.jmecn.fieldguideexport.export.GuideExportOrchestrator;
import io.github.jmecn.fieldguideexport.mod.FieldGuideExportMod;
import io.github.jmecn.minecraftwebexport.export.ci.ExportCiProperties;
import io.github.jmecn.minecraftwebexport.export.ci.ExportWorldCreator;
import io.github.jmecn.minecraftwebexport.export.emi.EmiExportReadiness;
import io.github.jmecn.minecraftwebexport.export.module.ExportResult;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;

/**
 * CI driver: menu → void world → EMI ready → guide (+ optional scoped EMI) → exit.
 *
 * <p>Enable with {@code -Dfieldguide.runExportAndExit=true}. Do not also set
 * {@code -DminecraftWebExport.runExportAndExit=true} (mwe driver would duplicate).</p>
 */
public final class FieldGuideExportCiDriver {

    private static final int HEARTBEAT_TICKS = 200;

    private final Path gameDirectory;

    public FieldGuideExportCiDriver(Path gameDirectory) {
        this.gameDirectory = gameDirectory;
    }

    public void register() {
        Logger logger = FieldGuideExportMod.LOGGER;
        Path exportRoot = FieldGuideExportPaths.resolveExportRoot(gameDirectory);
        logger.info(
                "mode=fieldguide.runExportAndExit, world={}, exportRoot={}, guide={}, exportEmi={}, warmupTicks={}, timeoutSeconds={}",
                ExportWorldCreator.saveName(),
                exportRoot.toAbsolutePath(),
                FieldGuideExportPaths.guideDirectory(gameDirectory).toAbsolutePath(),
                FieldGuideExportCiProperties.exportEmi(),
                warmupTicks(),
                FieldGuideExportCiProperties.exportTimeoutSeconds());
        MinecraftForge.EVENT_BUS.register(new AutoExportHandler(gameDirectory, exportRoot, logger));
    }

    private static int warmupTicks() {
        if (FieldGuideExportCiProperties.exportEmi()) {
            return ExportCiProperties.exportWarmupTicks();
        }
        return FieldGuideExportCiProperties.exportWarmupTicks();
    }

    private static int worldDelayTicks() {
        if (FieldGuideExportCiProperties.exportEmi()) {
            return ExportCiProperties.exportWorldDelayTicks();
        }
        return FieldGuideExportCiProperties.exportWorldDelayTicks();
    }

    static boolean isFatalMenuScreen(Minecraft client) {
        if (client.screen == null) {
            return false;
        }
        String simple = client.screen.getClass().getSimpleName();
        return switch (simple) {
            case "LoadingErrorScreen", "ErrorScreen", "KubeJSErrorScreen", "DisconnectedScreen" -> true;
            default -> false;
        };
    }

    static boolean isIdleMenuReady(Minecraft client) {
        if (client.getOverlay() instanceof LoadingOverlay) {
            return false;
        }
        if (client.screen == null || client.level != null || client.player != null) {
            return false;
        }
        return !isFatalMenuScreen(client);
    }

    private static final class StateLogger {
        private final Logger logger;
        private String state = "";
        private int sameStateTicks;

        StateLogger(Logger logger) {
            this.logger = logger;
        }

        void tick(String newState) {
            if (!newState.equals(state)) {
                state = newState;
                sameStateTicks = 0;
                logger.info(newState);
            } else if (++sameStateTicks % HEARTBEAT_TICKS == 0) {
                logger.info("{} (still waiting, {} ticks)", newState, sameStateTicks);
            }
        }
    }

    private static final class AutoExportHandler {

        private enum Phase {ARMED, WORLD_OPENING, WARMUP, DONE}

        private final Path gameDirectory;
        private final Path exportRoot;
        private final Logger logger;
        private final StateLogger stateLog;

        private Phase phase = Phase.ARMED;
        private boolean worldRequestSent;
        private int worldDelayTicks;
        private int warmupTicks;
        private long startNanos;

        AutoExportHandler(Path gameDirectory, Path exportRoot, Logger logger) {
            this.gameDirectory = gameDirectory;
            this.exportRoot = exportRoot;
            this.logger = logger;
            this.stateLog = new StateLogger(logger);
        }

        @SubscribeEvent
        public void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END || phase == Phase.DONE) {
                return;
            }
            if (!FieldGuideExportCiProperties.runExportAndExit()) {
                return;
            }

            Minecraft client = Minecraft.getInstance();

            if (phase == Phase.ARMED) {
                startNanos = System.nanoTime();
                phase = Phase.WORLD_OPENING;
                logger.info("fieldguide.runExportAndExit: armed (timeout={}s)",
                        FieldGuideExportCiProperties.exportTimeoutSeconds());
            }

            if (isFatalMenuScreen(client)) {
                phase = Phase.DONE;
                logger.error("fatal menu screen ({}); aborting export",
                        client.screen.getClass().getName());
                System.exit(1);
                return;
            }
            if (FieldGuideExportCiProperties.timedOut(startNanos)) {
                String phaseAtTimeout = phase.name();
                phase = Phase.DONE;
                String screen = client.screen == null ? "null" : client.screen.getClass().getName();
                logger.error("export timed out after {}s (phase={}, player={}, level={}, screen={})",
                        FieldGuideExportCiProperties.exportTimeoutSeconds(),
                        phaseAtTimeout,
                        client.player != null,
                        client.level != null,
                        screen);
                System.exit(1);
                return;
            }

            switch (phase) {
                case WORLD_OPENING -> tickWorldOpening(client);
                case WARMUP -> tickWarmup(client);
                default -> {}
            }
        }

        private void tickWorldOpening(Minecraft client) {
            if (client.player != null && client.level != null) {
                phase = Phase.WARMUP;
                warmupTicks = 0;
                logger.info("player + level present, warming up {} ticks", warmupTicks());
                return;
            }

            if (!worldRequestSent) {
                if (!isIdleMenuReady(client)) {
                    worldDelayTicks = 0;
                    if (client.getOverlay() instanceof LoadingOverlay) {
                        stateLog.tick("waiting: resource reload (LoadingOverlay)");
                    } else if (client.screen == null) {
                        stateLog.tick("waiting: no screen yet");
                    } else {
                        stateLog.tick("waiting: menu not ready (screen="
                                + client.screen.getClass().getSimpleName() + ")");
                    }
                    return;
                }
                boolean reuseSave = ExportWorldCreator.saveExists(client);
                int delayTarget = reuseSave ? 0 : worldDelayTicks();
                if (delayTarget > 0 && worldDelayTicks < delayTarget) {
                    worldDelayTicks++;
                    if (worldDelayTicks == 1 || worldDelayTicks % HEARTBEAT_TICKS == 0
                            || worldDelayTicks == delayTarget) {
                        logger.info("world create delay {}/{} ticks", worldDelayTicks, delayTarget);
                    }
                    return;
                }
                worldRequestSent = true;
                if (reuseSave) {
                    logger.info("opening cached world '{}'", ExportWorldCreator.saveName());
                    ExportWorldCreator.openExisting(client);
                } else {
                    logger.info("creating void world '{}'", ExportWorldCreator.saveName());
                    ExportWorldCreator.createAndLoad(client);
                }
                return;
            }

            String screen = client.screen == null ? "null" : client.screen.getClass().getSimpleName();
            stateLog.tick("waiting: world loading (screen=" + screen + ")");
        }

        private void tickWarmup(Minecraft client) {
            if (client.player == null || client.level == null) {
                stateLog.tick("warning: lost player/level during warmup");
                phase = Phase.WORLD_OPENING;
                worldRequestSent = true;
                return;
            }

            if (FieldGuideExportCiProperties.exportEmi()) {
                if (EmiExportReadiness.isReloadFailed()) {
                    phase = Phase.DONE;
                    logger.error("EMI reload failed; aborting export");
                    System.exit(1);
                    return;
                }
                if (!EmiExportReadiness.isReadyForExport(client)) {
                    if (warmupTicks % HEARTBEAT_TICKS == 0) {
                        stateLog.tick("waiting: EMI (status="
                                + EmiExportReadiness.reloadStatusLabel() + ")");
                    }
                    warmupTicks = 0;
                    return;
                }
            }

            int target = warmupTicks();
            if (warmupTicks < target) {
                warmupTicks++;
                if (warmupTicks % HEARTBEAT_TICKS == 0 || warmupTicks == target) {
                    logger.info("warmup {}/{}", warmupTicks, target);
                }
                return;
            }

            phase = Phase.DONE;
            runExport(client);
        }

        private void runExport(Minecraft client) {
            try {
                if (FieldGuideExportCiProperties.exportEmi()) {
                    CombinedExportOrchestrator.CombinedExportResult result =
                            CombinedExportOrchestrator.run(exportRoot, gameDirectory, client);
                    ExportResult emi = result.emiResult();
                    logger.info(
                            "combined export finished (recipes={}/{}, items={}), exiting 0",
                            emi.recipesWritten(),
                            emi.recipesRequested(),
                            emi.itemIndexCount());
                } else {
                    Path guideDir = FieldGuideExportPaths.guideDirectory(gameDirectory);
                    Component message = GuideExportOrchestrator.run(guideDir);
                    logger.info("guide export finished: {}, exiting 0", message.getString());
                }
                System.exit(0);
            } catch (Exception e) {
                logger.error("export failed for {}", exportRoot.toAbsolutePath(), e);
                System.exit(1);
            }
        }
    }
}
