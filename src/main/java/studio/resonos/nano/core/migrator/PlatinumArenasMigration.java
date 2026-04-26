package studio.resonos.nano.core.migrator;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import studio.resonos.nano.NanoArenas;
import studio.resonos.nano.core.arena.Arena;
import studio.resonos.nano.core.arena.impl.StandaloneArena;
import studio.resonos.nano.core.util.CC;
import studio.resonos.nano.core.util.FoliaScheduler;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

public class PlatinumArenasMigration {

    private static final byte SECTION_SPLIT = '\u0002';
    private static final int BUFFER_SIZE = 8192;
    private static final ThreadLocal<Inflater> INFLATER_CACHE =
            ThreadLocal.withInitial(Inflater::new);

    public static List<Arena> migrateFromPlatinumArenas(Player player) {
        File pluginsFolder = NanoArenas.get().getDataFolder().getParentFile();
        File platinumArenasFolder = new File(pluginsFolder, "PlatinumArenas");

        List<Arena> migratedArenas = Collections.synchronizedList(new ArrayList<>());

        if (!platinumArenasFolder.exists() || !platinumArenasFolder.isDirectory()) {
            NanoArenas.get().getLogger().warning("PlatinumArenas folder not found: " + platinumArenasFolder.getPath());
            return migratedArenas;
        }

        File arenasFolder = new File(platinumArenasFolder, "Arenas");
        if (!arenasFolder.exists()) {
            NanoArenas.get().getLogger().warning("PlatinumArenas/Arenas folder not found");
            return migratedArenas;
        }

        File[] arenaFiles = arenasFolder.listFiles((dir, name) ->
                name.toLowerCase().endsWith(".dat") || name.toLowerCase().endsWith(".datc"));

        if (arenaFiles == null || arenaFiles.length == 0) {
            NanoArenas.get().getLogger().info("No PlatinumArenas arena files found");
            return migratedArenas;
        }

        sendMessage(player, "&8[&bNanoArenas&8] &fFound " + arenaFiles.length + " PlatinumArenas arena files to migrate.");
        sendMessage(player, "&8[&bNanoArenas&8] &fStarting migration of PlatinumArenas arenas...");

        long startTime = System.currentTimeMillis();
        AtomicInteger successCount = new AtomicInteger(0);

        // Thread pool sized to available processors
        int threadCount = Math.min(arenaFiles.length, Runtime.getRuntime().availableProcessors());
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (File file : arenaFiles) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    long fileStartTime = System.currentTimeMillis();
                    Arena arena = loadPlatinumArenaBasicInfo(file);

                    // Thread-safe check and add
                    synchronized (Arena.getArenaNames()) {
                        if (Arena.getArenaNames().contains(arena.getName())) {
                            sendMessage(player, "&8[&bNanoArenas&8] &cAn arena with the name '"
                                    + arena.getName() + "' already exists. Skipping migration for this arena.");
                            return;
                        }
                        Arena.getArenaNames().add(arena.getName());
                    }

                    migratedArenas.add(arena);
                    Arena.getArenas().add(arena);

                    // File I/O on async thread
                    arena.save();

                    successCount.incrementAndGet();

                    long fileDuration = System.currentTimeMillis() - fileStartTime;
                    sendMessage(player, "&8[&bNanoArenas&8] &fMigrated arena &b" + arena.getName()
                            + " &fin &a" + fileDuration + "ms");

                } catch (Exception e) {
                    NanoArenas.get().getLogger().warning("Failed to migrate arena from file: " + file.getName());
                    e.printStackTrace();
                }
            }, executor);

            futures.add(future);
        }

        // Wait for all migrations to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        executor.shutdown();

        // Defer expensive schematic creation to async batch operation
        FoliaScheduler.runTaskAsync(NanoArenas.get(), () -> {
            for (Arena arena : migratedArenas) {
                try {
                    arena.createSchematic();
                } catch (Exception e) {
                    NanoArenas.get().getLogger().warning("Failed to create schematic for: " + arena.getName());
                    e.printStackTrace();
                }
            }
        });

        // Schedule arena reset timers on the global region (main on Paper)
        FoliaScheduler.runTask(NanoArenas.get(), () -> {
            for (Arena arena : migratedArenas) {
                NanoArenas.get().getResetScheduler().schedule(arena);
            }
        });

        long duration = System.currentTimeMillis() - startTime;
        long durationSeconds = duration / 1000;

        if (durationSeconds < 1) {
            sendMessage(player, "&8[&bNanoArenas&8] &aSuccessfully Migrated &b" + successCount.get()
                    + " &aarenas from &ePlatinumArenas &ain &b" + duration + "ms");
        } else {
            sendMessage(player, "&8[&bNanoArenas&8] &aSuccessfully Migrated &b" + successCount.get()
                    + " &aarenas from &ePlatinumArenas &ain &b" + durationSeconds + " seconds");
        }

        return migratedArenas;
    }

    private static void sendMessage(CommandSender sender, String message) {
        String translatedMessage = CC.translate(message);
        // Route through the global region scheduler so messages are delivered
        // safely whether we're called from an async migration thread or the
        // command thread.
        FoliaScheduler.runTask(NanoArenas.get(), () -> {
            Bukkit.getConsoleSender().sendMessage(translatedMessage);
            if (sender != null) {
                sender.sendMessage(translatedMessage);
            }
        });
    }

    private static Arena loadPlatinumArenaBasicInfo(File file) throws Exception {
        byte[] readBytes;

        // Buffered I/O for better performance
        try (FileInputStream fis = new FileInputStream(file);
             BufferedInputStream bis = new BufferedInputStream(fis, BUFFER_SIZE)) {
            readBytes = bis.readAllBytes();
        }

        if (file.getName().toLowerCase().endsWith(".datc")) {
            readBytes = decompress(readBytes);
            if (readBytes == null) {
                throw new Exception("Failed to decompress arena file");
            }
        }

        int firstSectionSplit = indexOf(readBytes, SECTION_SPLIT);
        if (firstSectionSplit == -1) {
            throw new Exception("Invalid arena file format - no section split found");
        }

        byte[] header = Arrays.copyOfRange(readBytes, 0, firstSectionSplit);
        String headerString = new String(header, StandardCharsets.US_ASCII);

        String[] headerParts = headerString.split(",");

        if (headerParts.length < 9) {
            throw new Exception("Invalid header format");
        }

        String name = headerParts[1];
        String worldName = headerParts[2];
        int x1 = Integer.parseInt(headerParts[3]);
        int y1 = Integer.parseInt(headerParts[4]);
        int z1 = Integer.parseInt(headerParts[5]);
        int x2 = Integer.parseInt(headerParts[6]);
        int y2 = Integer.parseInt(headerParts[7]);
        int z2 = Integer.parseInt(headerParts[8]);

        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            NanoArenas.get().getLogger().warning("World '" + worldName + "' not found for arena '" + name + "', using default world");
            world = Bukkit.getWorlds().get(0);
        }

        Location corner1 = new Location(world, x1, y1, z1);
        Location corner2 = new Location(world, x2, y2, z2);

        return new StandaloneArena(name, corner1, corner2);
    }

    private static byte[] decompress(byte[] bytes) {
        Inflater decompresser = INFLATER_CACHE.get();
        decompresser.reset();
        decompresser.setInput(bytes);

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream(bytes.length)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            while (!decompresser.finished()) {
                int count = decompresser.inflate(buffer);
                outputStream.write(buffer, 0, count);
            }
            return outputStream.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static int indexOf(byte[] array, byte value) {
        for (int i = 0; i < array.length; i++) {
            if (array[i] == value) {
                return i;
            }
        }
        return -1;
    }
}