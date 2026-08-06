package mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.suggestion.Suggestions;
import net.minecraft.client.gui.screen.ChatInputSuggestor;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.command.CommandSource;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import wtf.oraculus.client.command.repository.CommandRepository;

import java.util.concurrent.CompletableFuture;

import static wtf.oraculus.client.Constants.mc;

@Mixin(ChatInputSuggestor.class)
public abstract class ChatInputSuggestorMixin {

    @Shadow
    private @Nullable ParseResults<CommandSource> parse;

    @Shadow
    private boolean completingSuggestions;

    @Shadow
    @Final
    private TextFieldWidget textField;

    @Shadow
    private @Nullable CompletableFuture<Suggestions> pendingSuggestions;

    @Shadow
    protected abstract void showCommandSuggestions();

    @Shadow
    @Nullable
    private ChatInputSuggestor.@Nullable SuggestionWindow window;

    private ChatInputSuggestorMixin() {
    }

    @Inject(method = "refresh",
            at = @At(value = "INVOKE", target = "Lcom/mojang/brigadier/StringReader;canRead()Z", remap = false),
            cancellable = true
    )
    private void onRefresh(CallbackInfo ci, @Local StringReader reader) {
        String prefix = ".";
        int length = prefix.length();

        if (reader.canRead(length) && reader.getString().startsWith(prefix, reader.getCursor())) {
            reader.setCursor(reader.getCursor() + length);

            if (this.parse == null) {
                this.parse = CommandRepository.parse(reader);
            }

            int cursor = textField.getCursor();
            if (cursor >= 1 && (this.window == null || !this.completingSuggestions)) {
                this.pendingSuggestions = CommandRepository.getCompletionSuggestions(this.parse, cursor);
                this.pendingSuggestions.thenRun(() -> {
                    if (this.pendingSuggestions.isDone()) {
                        this.showCommandSuggestions();
                    }
                });
            }

            ci.cancel();
        }
    }

}
