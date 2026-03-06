package org.modngu.modngu.Client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import java.text.Normalizer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class ModnguClient implements ClientModInitializer {
    // Trạng thái bật/tắt của Auto
    private boolean isAutoSlayerActive = true;

    @Override
    public void onInitializeClient() {
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!isAutoSlayerActive) return;

            // 1. Lấy tin nhắn thô từ server
            String rawChat = message.getString();

            // In ra Console IntelliJ để theo dõi quá trình lọc
            System.out.println(">>> SLAYER_LOG: " + rawChat);

            // 2. Chuẩn hóa: bỏ dấu + viết thường để so sánh an toàn nhất
            String clean = normalizeText(rawChat);

            // LOGIC A: Khi bắt đầu nhiệm vụ
            if (clean.contains("bat dau nhiem vu slayer")) {
                // Kiểm tra xem có phải "Hồn Thể" (hon the) không
                if (clean.contains("hon the")) {
                    sendClientMsg("§a[Modngu] ĐÃ TÌM THẤY HỒN THỂ! Dừng lại để làm.");
                } else {
                    // BUG FIX 1: Dùng clean.split() thay vì rawChat.split()
                    // vì clean đã normalize nên "diệt" -> "diet"
                    String[] parts = clean.split("diet");
                    String questInfo = parts.length > 1 ? parts[1].trim() : clean;
                    sendClientMsg("§e[Modngu] Sai quest (" + questInfo + ")");
                    executeCommand("canceltask");

                    // Đợi 1 giây (1000ms) để server xử lý, tránh lỗi "Cannot start two tasks"
                    // BUG FIX 2: Capture biến local để tránh race condition với isAutoSlayerActive
                    final boolean shouldStart = isAutoSlayerActive;
                    CompletableFuture.delayedExecutor(1000, TimeUnit.MILLISECONDS).execute(() -> {
                        if (shouldStart && isAutoSlayerActive) executeCommand("starttask");
                    });
                }
            }

            // LOGIC B: Khi hoàn thành nhiệm vụ
            if (clean.contains("hoan thanh nhiem vu slayer")) {
                sendClientMsg("§b[Modngu] Hoàn thành! Đang tự nhận thưởng...");
                executeCommand("collectreward");
            }
        });
    }

    // BUG FIX 3: Thêm xử lý "Đ" hoa -> "d", và normalize "đ" trước khi xóa diacritics
    private String normalizeText(String text) {
        // Bước 1: Replace "đ"/"Đ" trước (vì NFD không tách được chữ này)
        String replaced = text.replace("đ", "d").replace("Đ", "d");
        // Bước 2: NFD normalize rồi xóa combining diacritical marks
        String nfdNormalizedString = Normalizer.normalize(replaced, Normalizer.Form.NFD);
        return nfdNormalizedString.replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .toLowerCase();
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