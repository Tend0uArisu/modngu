package org.modngu.modngu.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.text.Normalizer;

public class ModnguClient implements ClientModInitializer {

    private enum State {
        IDLE, SENDING_START, WAITING_FOR_QUEST, FOUND_HON_THE,
        SENDING_CANCEL, SENDING_COLLECT, DELAY
    }

    private volatile State state = State.IDLE;
    private volatile State nextStateAfterDelay = State.IDLE;
    private long delayUntilMs = 0;
    private volatile boolean isAutoSlayerActive = false;
    private static final long DELAY_MS = 2000;

    @Override
    public void onInitializeClient() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(
                ClientCommandManager.literal("modngu")
                    .then(ClientCommandManager.literal("slayer")
                        .executes(context -> {
                            isAutoSlayerActive = !isAutoSlayerActive;
                            if (isAutoSlayerActive) {
                                state = State.SENDING_START;
                                sendClientMsg("§a[Modngu] Auto Slayer §lBẬT§r§a! Đang tìm Hồn Thể...");
                            } else {
                                state = State.IDLE;
                                sendClientMsg("§c[Modngu] Auto Slayer §lTẮT§r§c!");
                            }
                            return 1;
                        })
                    )
            );
        });

        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!isAutoSlayerActive) return;

            String rawChat = message.getString();
            String clean = normalizeText(rawChat);

            if (clean.contains("cannot start two tasks simultaneously")) {
                state = State.SENDING_CANCEL;
                return;
            }

            if (clean.contains("bat dau nhiem vu slayer")) {
                if (clean.contains("[lv.800] hon the")) {
                    sendClientMsg("§a[Modngu] ĐÃ TÌM THẤY [LV.800] HỒN THỂ! Đang làm...");
                    state = State.FOUND_HON_THE;
                } else {
                    String[] parts = clean.split("diet");
                    String questInfo = parts.length > 1 ? parts[1].trim() : "unknown";
                    sendClientMsg("§e[Modngu] Sai quest (" + questInfo + ") → Hủy...");
                    state = State.SENDING_CANCEL;
                }
            }

            if (clean.contains("hoan thanh nhiem vu slayer")) {
                sendClientMsg("§b[Modngu] Hoàn thành! Nhận thưởng...");
                state = State.SENDING_COLLECT;
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!isAutoSlayerActive || client.player == null || client.getNetworkHandler() == null) return;

            switch (state) {
                case IDLE:
                    break;

                case SENDING_START:
                    executeCommand("starttask");
                    state = State.WAITING_FOR_QUEST;
                    break;

                case WAITING_FOR_QUEST:
                    break;

                case FOUND_HON_THE:
                    break;

                case SENDING_CANCEL:
                    executeCommand("canceltask");
                    setDelay(DELAY_MS, State.SENDING_START);
                    break;

                case SENDING_COLLECT:
                    executeCommand("collectreward");
                    setDelay(DELAY_MS, State.SENDING_START);
                    break;

                case DELAY:
                    if (System.currentTimeMillis() >= delayUntilMs) {
                        state = nextStateAfterDelay;
                    }
                    break;
            }
        });
    }

    private void setDelay(long ms, State nextState) {
        delayUntilMs = System.currentTimeMillis() + ms;
        nextStateAfterDelay = nextState;
        state = State.DELAY;
    }

    private String normalizeText(String text) {
        String replaced = text.replace("đ", "d").replace("Đ", "d");
        String nfd = Normalizer.normalize(replaced, Normalizer.Form.NFD);
        return nfd.replaceAll("\\p{InCombiningDiacriticalMarks}+", "").toLowerCase();
    }

    private void executeCommand(String cmd) {
        var handler = MinecraftClient.getInstance().getNetworkHandler();
        if (handler != null) handler.sendChatCommand(cmd);
    }

    private void sendClientMsg(String msg) {
        var player = MinecraftClient.getInstance().player;
        if (player != null) player.sendMessage(Text.of(msg), false);
    }
}
