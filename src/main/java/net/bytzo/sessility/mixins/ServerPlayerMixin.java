package net.bytzo.sessility.mixins;

import com.mojang.authlib.GameProfile;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.bytzo.sessility.Sessility;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.commands.CommandSource;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.*;
import java.util.function.Predicate;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin extends Player {
    @Shadow @Final
    public MinecraftServer server;
    @Shadow
    public ServerGamePacketListenerImpl connection;
    @Shadow
    private boolean disconnected;

    @Unique
    private boolean sessile = false;
    @Unique
    private final Map<UUID, Boolean> afkMap = new HashMap<>();

    @Shadow
    public abstract long getLastActionTime();

    public ServerPlayerMixin(Level level, BlockPos blockPos, float f, GameProfile gameProfile) {
        super(level, blockPos, f, gameProfile);
    }

    @Inject(method = "tick()V", at = @At(value = "RETURN"))
    private void postTick(CallbackInfo callbackInfo) {
        // Avoid making the player sessile if the timeout is invalid or if the player is
        // already sessile.
        if (Sessility.settings().properties().sessileTimeout <= 0 || this.sessile) {
            return;
        }

        var idleTime = Util.getMillis() - this.getLastActionTime();
        var timeout = Sessility.settings().properties().sessileTimeout * 1000;

        // If idle longer than the timeout, make the player sessile.
        if (idleTime > timeout) {
            this.setSessile(true);
        }
    }

    @Inject(method = "resetLastActionTime()V", at = @At("HEAD"))
    private void preResetLastActionTime(CallbackInfo callbackInfo) {
        // If action is taken, make the player not sessile.
        this.setSessile(false);
    }

    @Inject(method = "disconnect", at = @At("HEAD"))
    private void onDisconnect(CallbackInfo ci) {
        this.afkMap.remove(this.getUUID());
    }

    @Unique
    public void setSessile(boolean sessile) {
        // Only update the player's sessility if it has changed. This prevents
        // unnecessarily broadcasting the player's display name to all players.
        if (sessile != this.sessile) {
            // Update session
            this.sessile = sessile;
            this.afkMap.put(this.getUUID(), this.sessile);
            // Logic
            ServerPlayer player = (ServerPlayer) (Object) this;
            if(!Permissions.check(player, "sessility.bypass")) {
                // Starts the afk timer
                if (this.sessile) {
                    Timer timer = new Timer();
                    timer.schedule(
                            new TimerTask() {
                                public void run() {
                                    if (afkMap.getOrDefault(player.getUUID(), false)) {
                                        afkDisconnect();
                                    }
                                }
                            }, Sessility.settings().properties().afkTimeout * 1000L);
                }
                // Broadcasts the custom sessile or motile message, if present.
                String broadcastMessage = sessile ?
                        Sessility.settings().properties().messageSessile :
                        Sessility.settings().properties().messageMotile;
                if (!broadcastMessage.isBlank()) {
                    var translatedMessage = new TranslatableComponent(broadcastMessage, this.getGameProfile().getName());
                    var formattedMessage = translatedMessage.withStyle(ChatFormatting.YELLOW);
                    this.server.getPlayerList().broadcastMessage(formattedMessage, ChatType.SYSTEM, Util.NIL_UUID);
                }
            }
        }
    }

    @Unique
    private void afkDisconnect() {
        if (!this.disconnected) {
            String broadcastMessage = Sessility.settings().properties().messageAfkKick;
            var translatedMessage = new TranslatableComponent(broadcastMessage, this.getGameProfile().getName());
            var formattedMessage = translatedMessage.withStyle(ChatFormatting.YELLOW);
            this.connection.disconnect(formattedMessage);
        }
    }
}
