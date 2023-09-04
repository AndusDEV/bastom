package dev.andus.bastom;

import dev.andus.bastom.commands.Commands;
import dev.andus.bastom.commands.Permissions;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.Git;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.player.PlayerLoginEvent;
import net.minestom.server.event.server.ServerListPingEvent;
import net.minestom.server.extras.MojangAuth;
import net.minestom.server.extras.bungee.BungeeCordProxy;
import net.minestom.server.extras.velocity.VelocityProxy;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.InstanceManager;
import net.minestom.server.instance.block.Block;
import net.minestom.server.ping.ResponseData;
import net.minestom.server.ping.ServerListPingType;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public class Server {
    public static final String VERSION = "&version";
    private static final String MOTD_FILE = "motd.txt";
    private static final String HELP_FILE = "help.txt";
    private static final String START_SCRIPT_FILENAME = "start.sh";

    public static void main(String[] args) throws IOException {
        Settings.read();
        if (Settings.getTps() != null)
            System.setProperty("minestom.tps", Settings.getTps());
        if (Settings.getChunkViewDistance() != null)
            System.setProperty("minestom.chunk-view-distance", Settings.getChunkViewDistance());
        if (Settings.getEntityViewDistance() != null)
            System.setProperty("minestom.entity-view-distance", Settings.getEntityViewDistance());
        if (Settings.isTerminalDisabled())
            System.setProperty("minestom.terminal.disabled", "");

        if (! (args.length > 0 && args[0].equalsIgnoreCase("-q"))) {
            MinecraftServer.LOGGER.info("====== VERSIONS ======");
            MinecraftServer.LOGGER.info("Java: " + Runtime.version());
            MinecraftServer.LOGGER.info("&Name: " + VERSION);
            MinecraftServer.LOGGER.info("Minestom commit: " + Git.commit());
            MinecraftServer.LOGGER.info("Supported protocol: %d (%s)".formatted(MinecraftServer.PROTOCOL_VERSION, MinecraftServer.VERSION_NAME));
            MinecraftServer.LOGGER.info("======================");
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("-v")) System.exit(0);

        // Create motd file
        File motdTextFile = new File(MOTD_FILE);
        if (motdTextFile.isDirectory()) MinecraftServer.LOGGER.warn("Can't create motd.txt file!");
        if (!motdTextFile.isFile()) {
            MinecraftServer.LOGGER.info("Creating motd file.");
            Files.copy(
                    Objects.requireNonNull(Server.class.getClassLoader().getResourceAsStream(MOTD_FILE)),
                    motdTextFile.toPath());
            MinecraftServer.LOGGER.info("Modify the motd.txt file to change the server motd.");
        }

        // Create help file
        File helpTextFile = new File(HELP_FILE);
        if (helpTextFile.isDirectory()) MinecraftServer.LOGGER.warn("Can't create help.txt file!");
        if (!helpTextFile.isFile()) {
            MinecraftServer.LOGGER.info("Creating help file.");
            Files.copy(
                    Objects.requireNonNull(Server.class.getClassLoader().getResourceAsStream(HELP_FILE)),
                    helpTextFile.toPath());
            MinecraftServer.LOGGER.info("Modify the help.txt file to change /help output.");
        }

        // Create start script
        File startScriptFile = new File(START_SCRIPT_FILENAME);
        if (startScriptFile.isDirectory()) MinecraftServer.LOGGER.warn("Can't create startup script!");
        if (!startScriptFile.isFile()) {
            MinecraftServer.LOGGER.info("Creating startup script.");
            Files.copy(
                    Objects.requireNonNull(Server.class.getClassLoader().getResourceAsStream(START_SCRIPT_FILENAME)),
                    startScriptFile.toPath());
            Runtime.getRuntime().exec("chmod u+x start.sh");
            MinecraftServer.LOGGER.info("Use './start.sh' to start the server.");
            System.exit(0);
        }

        // Actually start server
        MinecraftServer server = MinecraftServer.init();
        InstanceManager instanceManager = MinecraftServer.getInstanceManager();

        if (Settings.worldGen()) {
            // Create the instance
            InstanceContainer instanceContainer = instanceManager.createInstanceContainer();
            instanceContainer.setGenerator(unit ->
                    unit.modifier().fillHeight(0, 4, Block.GRASS_BLOCK));
            // Add an event callback to specify the spawning instance (and the spawn position)
            GlobalEventHandler globalEventHandler = MinecraftServer.getGlobalEventHandler();
            globalEventHandler.addListener(PlayerLoginEvent.class, event -> {
                final Player player = event.getPlayer();
                event.setSpawningInstance(instanceContainer);
                player.setRespawnPoint(new Pos(0, 5, 0));
            });
        }

        MinecraftServer.getGlobalEventHandler().addListener(PlayerLoginEvent.class, event -> {
            if (instanceManager.getInstances().isEmpty())
                event.getPlayer().kick(Component.text("There is no instance available!", NamedTextColor.RED));
        });

        var commandManager = MinecraftServer.getCommandManager();
        var consoleSender = commandManager.getConsoleSender();
        commandManager.register(Commands.SHUTDOWN);
        commandManager.register(Commands.RESTART);
        commandManager.register(Commands.EXTENSIONS);
        commandManager.register(Commands.PLAYER_LIST);
        commandManager.register(Commands.HELP);
        consoleSender.addPermission(Permissions.SHUTDOWN);
        consoleSender.addPermission(Permissions.RESTART);
        consoleSender.addPermission(Permissions.EXTENSIONS);
        MinecraftServer.getExtensionManager().setExtensionDataRoot(Path.of("config"));

        switch (Settings.getMode()) {
            case OFFLINE:
                break;
            case ONLINE:
                MojangAuth.init();
                break;
            case BUNGEECORD:
                BungeeCordProxy.enable();
                break;
            case VELOCITY:
                if (!Settings.hasVelocitySecret())
                    throw new IllegalArgumentException("The velocity secret is mandatory.");
                VelocityProxy.enable(Settings.getVelocitySecret());
        }

        GlobalEventHandler globalEventHandler = MinecraftServer.getGlobalEventHandler();
        globalEventHandler.addListener(ServerListPingEvent.class, event -> {
            ResponseData responseData = event.getResponseData();
            responseData.setDescription(Utils.readFromFile("motd.txt", "§7A §bBastom §7Server!"));
        });

        MinecraftServer.LOGGER.info("Running in " + Settings.getMode() + " mode.");
        MinecraftServer.LOGGER.info("MOTD set to: " + Utils.readFromFile("motd.txt", "§7A §bBastom §7Server!"));
        MinecraftServer.LOGGER.info("Listening on " + Settings.getServerIp() + ":" + Settings.getServerPort());

        server.start(Settings.getServerIp(), Settings.getServerPort());
    }
}