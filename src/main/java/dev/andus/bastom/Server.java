package dev.andus.bastom;

import dev.andus.bastom.commands.Commands;
import dev.andus.bastom.commands.Permissions;
import net.hollowcube.minestom.extensions.ExtensionBootstrap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.logger.slf4j.ComponentLoggerProvider;
import net.minestom.server.MinecraftServer;
import net.minestom.server.adventure.MinestomAdventure;
import net.minestom.server.adventure.provider.MinestomComponentLoggerProvider;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.player.PlayerLoginEvent;
import net.minestom.server.event.server.ServerListPingEvent;
import net.minestom.server.extensions.Extension;
import net.minestom.server.extensions.ExtensionManager;
import net.minestom.server.extras.MojangAuth;
import net.minestom.server.extras.bungee.BungeeCordProxy;
import net.minestom.server.extras.lan.OpenToLAN;
import net.minestom.server.extras.velocity.VelocityProxy;
import net.minestom.server.instance.AnvilLoader;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.InstanceManager;
import net.minestom.server.instance.block.Block;
import net.minestom.server.ping.ResponseData;
import net.minestom.server.thread.MinestomThread;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public class Server {
    public static final Logger logger = LoggerFactory.getLogger(Server.class);

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
            logger.info("====== VERSIONS ======");
            logger.info("Java: " + Runtime.version());
            logger.info("&Name: " + VERSION);
            logger.info("Minestom(-ce) commit: " + "&commit");
            logger.info("Supported protocol: %d (%s)".formatted(MinecraftServer.PROTOCOL_VERSION, MinecraftServer.VERSION_NAME));
            logger.info("======================");
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("-v")) System.exit(0);

        // Create motd file
        File motdTextFile = new File(MOTD_FILE);
        if (motdTextFile.isDirectory()) logger.warn("Can't create motd.txt file!");
        if (!motdTextFile.isFile()) {
            logger.info("Creating motd file.");
            Files.copy(
                    Objects.requireNonNull(Server.class.getClassLoader().getResourceAsStream(MOTD_FILE)),
                    motdTextFile.toPath());
            logger.info("Modify the motd.txt file to change the server motd.");
        }

        // Create help file
        File helpTextFile = new File(HELP_FILE);
        if (helpTextFile.isDirectory()) logger.warn("Can't create help.txt file!");
        if (!helpTextFile.isFile()) {
            logger.info("Creating help file.");
            Files.copy(
                    Objects.requireNonNull(Server.class.getClassLoader().getResourceAsStream(HELP_FILE)),
                    helpTextFile.toPath());
            logger.info("Modify the help.txt file to change /help output.");
        }

        // Create start script
        File startScriptFile = new File(START_SCRIPT_FILENAME);
        if (startScriptFile.isDirectory()) logger.warn("Can't create startup script!");
        if (!startScriptFile.isFile()) {
            logger.info("Creating startup script.");
            Files.copy(
                    Objects.requireNonNull(Server.class.getClassLoader().getResourceAsStream(START_SCRIPT_FILENAME)),
                    startScriptFile.toPath());
            Runtime.getRuntime().exec("chmod u+x start.sh");
            logger.info("Use './start.sh' to start the server.");
            logger.info("You can modify the server settings using settings.json file, and world using worlds.json");
            System.exit(0);
        }

        // Actually start server
        ExtensionBootstrap server = ExtensionBootstrap.init();;
        setupWorld();
        registerCommands();

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

        if (Settings.isOpenToLAN()) {
            OpenToLAN.open();
        }

        logger.info("Running in " + Settings.getMode() + " mode. (Opened to LAN: " + OpenToLAN.isOpen() + ")");
        logger.info("MOTD set to: " + Utils.readFromFile("motd.txt", "§7A §bBastom §7Server!"));
        logger.info("Listening on " + Settings.getServerIp() + ":" + Settings.getServerPort());

        server.start(Settings.getServerIp(), Settings.getServerPort());
    }

    public static void setupWorld() {
        InstanceManager instanceManager = MinecraftServer.getInstanceManager();
        if (Settings.isInstanceEnabled() && Settings.getWorldType() == Settings.WorldType.FLAT) {
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
        } else if (Settings.isInstanceEnabled() && Settings.getWorldType() == Settings.WorldType.ANVIL) {
            InstanceContainer instanceContainer = instanceManager.createInstanceContainer();
            instanceContainer.setChunkLoader(new AnvilLoader(Settings.getWorldLoc()));
            GlobalEventHandler globalEventHandler = MinecraftServer.getGlobalEventHandler();
            globalEventHandler.addListener(PlayerLoginEvent.class, event -> {
                final Player player = event.getPlayer();
                event.setSpawningInstance(instanceContainer);
                // Figure out how to get spawn coordinates
            });
        } else {

        }

        MinecraftServer.getGlobalEventHandler().addListener(PlayerLoginEvent.class, event -> {
            if (instanceManager.getInstances().isEmpty())
                event.getPlayer().kick(Component.text("There is no instance available!\n" +
                        "You can enable a basic flat instance in worlds.json using \"FLAT\" as world_type\n" +
                        "Or use \"ANVIL\" as world_type and load an existing Minecraft world from a folder.", NamedTextColor.RED));
        });
    }
    
    public static void registerCommands() {
        var commandManager = MinecraftServer.getCommandManager();
        var consoleSender = commandManager.getConsoleSender();
        commandManager.register(Commands.SHUTDOWN);
        commandManager.register(Commands.RESTART);
        commandManager.register(Commands.EXTENSIONS);
        commandManager.register(Commands.PLAYER_LIST);
        commandManager.register(Commands.HELP);
        commandManager.register(Commands.SERVER_INFO);
        consoleSender.addPermission(Permissions.SHUTDOWN);
        consoleSender.addPermission(Permissions.RESTART);
        consoleSender.addPermission(Permissions.EXTENSIONS);
        consoleSender.addPermission(Permissions.SERVER_INFO);
        ExtensionBootstrap.getExtensionManager().setExtensionDataRoot(Path.of("config"));
    }
}