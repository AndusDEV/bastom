package dev.andus.bastom.commands;

import dev.andus.bastom.Settings;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.CommandSender;
import net.minestom.server.command.ServerSender;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.CommandContext;

public class ExtensionsCommand extends Command {
    public ExtensionsCommand() {
        super("extensions", "ext", "exts");
        setCondition((sender, commandString) -> (sender instanceof ServerSender)
                || (Settings.extCommandEnabled())); // || (Settings.extCommandEnabled() && sender.hasPermission(Permissions.EXTENSIONS)));
        setDefaultExecutor(this::execute);
    }

    private void execute(CommandSender sender, CommandContext context) {
        if (sender.isPlayer()) {
            if (!Settings.extCommandEnabled()) {
                sender.sendMessage("This command can only be used by the server console!");
                return;
            }
        }

        String extensions = String.join(", ", MinecraftServer.getExtensionManager().getExtensions().toString());
        sender.sendMessage("Loaded Extensions: " + extensions);
    }
}
