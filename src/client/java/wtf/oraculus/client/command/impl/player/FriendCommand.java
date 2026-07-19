package wtf.oraculus.client.command.impl.player;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.command.CommandSource;
import wtf.oraculus.client.command.Command;
import wtf.oraculus.client.feature.helper.impl.LocalDataWatch;
import wtf.oraculus.utility.misc.chat.ChatUtility;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;

public final class
FriendCommand extends Command {

    public FriendCommand() {
        super("friend", "Exempts users from kill aura", "f");
    }

    @Override
    protected void onCommand(LiteralArgumentBuilder<CommandSource> builder) {
        builder.then(literal("add").then(argument("player", StringArgumentType.word()).executes(context -> {
            String playerName = StringArgumentType.getString(context, "player");

            if (LocalDataWatch.getFriendList().contains(playerName.toUpperCase())) {
                ChatUtility.error(playerName + " is already on your friends list!");
                return SINGLE_SUCCESS;
            }

            LocalDataWatch.getFriendList().add(playerName.toUpperCase());
            ChatUtility.print(playerName + " has been added to your friends list!");
            return SINGLE_SUCCESS;
        })));
        builder.then(literal("remove").then(argument("player", StringArgumentType.word()).executes(context -> {
            String playerName = StringArgumentType.getString(context, "player");

            if (LocalDataWatch.getFriendList().contains(playerName.toUpperCase())) {
                LocalDataWatch.getFriendList().remove(playerName.toUpperCase());
                ChatUtility.print(playerName + " has been removed from your friends list!");

                return SINGLE_SUCCESS;
            }
            ChatUtility.error(playerName + " is not on your friends list!");

            return SINGLE_SUCCESS;
        })));
    }
}
