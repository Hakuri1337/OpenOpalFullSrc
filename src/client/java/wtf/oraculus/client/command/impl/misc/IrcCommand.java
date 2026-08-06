package wtf.oraculus.client.command.impl.misc;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.command.CommandSource;
import wtf.oraculus.client.OraculusClient;
import wtf.oraculus.client.command.Command;
import wtf.oraculus.client.irc.IrcService;
import wtf.oraculus.client.irc.IrcUser;
import wtf.oraculus.utility.misc.chat.ChatUtility;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;

public final class IrcCommand extends Command {
    public IrcCommand() {
        super("irc", "Oraculus 游戏内聊天与在线用户");
    }

    @Override
    protected void onCommand(final LiteralArgumentBuilder<CommandSource> builder) {
        builder.then(literal("send").then(argument("message", StringArgumentType.greedyString()).executes(context -> {
            final String message = StringArgumentType.getString(context, "message");
            service().sendMessage(message).thenAccept(sent -> {
                if (!sent) ChatUtility.error("IRC 消息发送失败，请检查连接状态。");
            });
            return SINGLE_SUCCESS;
        })));
        builder.then(literal("list").executes(context -> {
            final var users = service().users();
            ChatUtility.print("IRC 在线用户（" + users.size() + "）：");
            for (final IrcUser user : users)
                ChatUtility.print(" - [" + user.tier() + "] " + user.username()
                        + (user.minecraftProfileName().isBlank() ? "" : " / " + user.minecraftProfileName()));
            return SINGLE_SUCCESS;
        }));
        builder.then(literal("status").executes(context -> {
            final IrcService irc = service();
            ChatUtility.print("IRC：" + irc.status() + "，在线列表 " + irc.users().size() + " 人");
            return SINGLE_SUCCESS;
        }));
        builder.then(literal("reconnect").executes(context -> {
            service().reconnect();
            ChatUtility.print("已请求重新连接 IRC。");
            return SINGLE_SUCCESS;
        }));
    }

    private static IrcService service() {
        return OraculusClient.getInstance().getIrcService();
    }
}
