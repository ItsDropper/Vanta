package dev.itsdropper.vanta.ui.views;

import dev.itsdropper.vanta.launcher.account.Account;
import dev.itsdropper.vanta.launcher.service.AccountService;
import dev.itsdropper.vanta.launcher.social.VantaSocialClient;
import dev.itsdropper.vanta.launcher.social.VantaSocialClient.Conversation;
import dev.itsdropper.vanta.launcher.social.VantaSocialClient.Friend;
import dev.itsdropper.vanta.launcher.social.VantaSocialClient.FriendRequest;
import dev.itsdropper.vanta.launcher.social.VantaSocialClient.Message;
import dev.itsdropper.vanta.launcher.social.VantaSocialClient.SocialUser;
import dev.itsdropper.vanta.ui.LauncherSettings;
import dev.itsdropper.vanta.ui.LauncherViewLifecycle;
import dev.itsdropper.vanta.ui.Localization;
import dev.itsdropper.vanta.ui.async.UiExecutor;
import dev.itsdropper.vanta.ui.components.PageHeader;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class SocialView extends VBox implements LauncherViewLifecycle {
    private record Overview(List<Friend> friends, List<FriendRequest> incoming, List<Conversation> conversations) {}

    private final AccountService accountService;
    private final TextField apiUrl = new TextField();
    private final TextField searchField = new TextField();
    private final TextArea messageInput = new TextArea();
    private final Label connectionStatus = new Label();
    private final Label chatTitle = new Label(Localization.text("social.selectConversation"));
    private final Label chatSubtitle = new Label();
    private final VBox searchResults = new VBox(6);
    private final VBox incomingRequests = new VBox(6);
    private final VBox friends = new VBox(6);
    private final VBox conversations = new VBox(6);
    private final VBox messageList = new VBox(10);
    private final ScrollPane messageScroll = new ScrollPane(messageList);
    private final Button refreshButton = secondary(Localization.text("social.refresh"));
    private final Button searchButton = secondary(Localization.text("social.search"));
    private final Button sendButton = primary(Localization.text("social.send"));
    private final Button saveEndpointButton = secondary(Localization.text("social.saveEndpoint"));
    private final AtomicBoolean polling = new AtomicBoolean();
    private final Timeline poller = new Timeline(new KeyFrame(Duration.seconds(4), event -> pollMessages()));

    private volatile VantaSocialClient client;
    private volatile Account currentAccount;
    private volatile String selectedConversationId;
    private boolean viewActive;
    private final AccountService.AccountListener accountListener =
            account -> UiExecutor.shared().dispatchToUi(this::handleAccountChanged);

    public SocialView(AccountService accountService) {
        this.accountService = accountService;
        getStyleClass().add("social-view");
        apiUrl.setText(LauncherSettings.getVantaSocialApiBaseUrl());
        apiUrl.setPromptText("https://vanta-social-api.<your-subdomain>.workers.dev");
        apiUrl.setAccessibleText(Localization.text("social.apiUrl"));
        apiUrl.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(apiUrl, Priority.ALWAYS);
        saveEndpointButton.setOnAction(event -> saveEndpoint());
        refreshButton.setOnAction(event -> refreshOverview());
        searchButton.setOnAction(event -> searchUsers());
        searchField.setPromptText(Localization.text("social.searchPlaceholder"));
        searchField.setOnAction(event -> searchUsers());
        sendButton.setOnAction(event -> sendMessage());
        messageInput.setPromptText(Localization.text("social.enterMessage"));
        messageInput.setWrapText(true);
        messageInput.setPrefRowCount(2);
        messageInput.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER && event.isControlDown()) {
                event.consume();
                sendMessage();
            }
        });

        VBox header = new VBox(14);
        header.setPadding(new Insets(24, 24, 0, 24));
        Label apiLabel = section("social.apiUrl");
        Label apiHelp = new Label(Localization.text("social.endpointHelp"));
        apiHelp.getStyleClass().add("muted");
        apiHelp.setWrapText(true);
        HBox endpoint = new HBox(8, apiUrl, saveEndpointButton);
        endpoint.setAlignment(Pos.CENTER_LEFT);
        connectionStatus.getStyleClass().add("social-connection-status");
        header.getChildren().addAll(
                new PageHeader(Localization.text("social.title"), Localization.text("social.subtitle"),
                        "page-title", "muted", refreshButton),
                card(apiLabel, apiHelp, endpoint, connectionStatus)
        );

        VBox left = new VBox(12, searchCard(), card(section("social.incoming"), incomingRequests),
                card(section("social.friends"), friends), card(section("social.conversations"), conversations));
        left.getStyleClass().add("social-sidebar");
        ScrollPane leftScroll = new ScrollPane(left);
        leftScroll.setFitToWidth(true);
        leftScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        leftScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        leftScroll.getStyleClass().add("social-list-scroll");
        leftScroll.setPrefWidth(340);
        leftScroll.setMinWidth(290);
        leftScroll.setMaxWidth(360);

        VBox chat = chatPanel();
        HBox main = new HBox(14, leftScroll, chat);
        main.setPadding(new Insets(18, 24, 24, 24));
        HBox.setHgrow(chat, Priority.ALWAYS);
        getChildren().addAll(header, main);
        VBox.setVgrow(main, Priority.ALWAYS);
        messageScroll.setFitToWidth(true);
        messageScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        messageScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        messageScroll.getStyleClass().add("social-message-scroll");
        poller.setCycleCount(Timeline.INDEFINITE);
        showChatPlaceholder(Localization.text("social.selectConversation"));
    }

    private VBox searchCard() {
        HBox row = new HBox(8, searchField, searchButton);
        HBox.setHgrow(searchField, Priority.ALWAYS);
        return card(section("social.addFriend"), new VBox(8, row, searchResults));
    }

    private VBox chatPanel() {
        chatTitle.getStyleClass().add("section-title");
        chatSubtitle.getStyleClass().add("muted");
        VBox chatHeader = new VBox(4, chatTitle, chatSubtitle);
        chatHeader.getStyleClass().add("social-chat-header");
        HBox composer = new HBox(10, messageInput, sendButton);
        composer.setAlignment(Pos.BOTTOM_RIGHT);
        HBox.setHgrow(messageInput, Priority.ALWAYS);
        composer.getStyleClass().add("social-composer");
        VBox panel = new VBox(12, chatHeader, messageScroll, composer);
        panel.getStyleClass().addAll("settings-card", "social-chat-panel");
        VBox.setVgrow(messageScroll, Priority.ALWAYS);
        panel.setMinWidth(0);
        panel.setMaxWidth(Double.MAX_VALUE);
        return panel;
    }

    private VBox card(Node... nodes) {
        VBox box = new VBox(10, nodes);
        box.getStyleClass().add("settings-card");
        return box;
    }

    private Label section(String key) {
        Label label = new Label(Localization.text(key));
        label.getStyleClass().add("section-title");
        return label;
    }

    private static Button secondary(String text) {
        Button button = new Button(text);
        button.getStyleClass().add("secondary-button");
        return button;
    }

    private static Button primary(String text) {
        Button button = new Button(text);
        button.getStyleClass().add("primary-button");
        return button;
    }

    private VantaSocialClient api() {
        if (client == null) client = new VantaSocialClient(apiUrl.getText());
        return client;
    }

    private void saveEndpoint() {
        try {
            LauncherSettings.setVantaSocialApiBaseUrl(apiUrl.getText());
            apiUrl.setText(LauncherSettings.getVantaSocialApiBaseUrl());
            client = new VantaSocialClient(apiUrl.getText());
            selectedConversationId = null;
            showChatPlaceholder(Localization.text("social.selectConversation"));
            setStatus(Localization.text("social.endpointSaved"));
            refreshOverview();
        } catch (IllegalArgumentException failure) {
            setStatus(Localization.text("social.endpointInvalid"));
        }
    }

    private void refreshOverview() {
        Account account = accountService.getCurrentAccount();
        currentAccount = account;
        if (account == null) {
            clearLists();
            setStatus(Localization.text("social.notSignedIn"));
            showChatPlaceholder(Localization.text("social.notSignedIn"));
            return;
        }
        final VantaSocialClient api;
        try {
            api = api();
        } catch (IllegalArgumentException failure) {
            setStatus(Localization.text("social.endpointInvalid"));
            return;
        }
        execute(Localization.text("social.connecting"), () -> new Overview(
                api.getFriends(account), api.getIncomingRequests(account), api.getConversations(account)
        ), result -> {
            renderFriends(result.friends());
            renderRequests(result.incoming());
            renderConversations(result.conversations());
            setStatus(Localization.text("social.connected").formatted(account.getUsername()));
            if (selectedConversationId != null) loadMessages(false);
        });
    }

    private void handleAccountChanged() {
        Account selected = accountService.getCurrentAccount();
        String previous = currentAccount == null ? null : currentAccount.getUuid();
        String next = selected == null ? null : selected.getUuid();
        if (java.util.Objects.equals(previous, next)) return;
        currentAccount = selected;
        client = null;
        selectedConversationId = null;
        clearLists();
        showChatPlaceholder(selected == null ? Localization.text("social.notSignedIn")
                : Localization.text("social.selectConversation"));
        if (viewActive) refreshOverview();
    }

    private void clearLists() {
        searchResults.getChildren().clear();
        incomingRequests.getChildren().clear();
        friends.getChildren().clear();
        conversations.getChildren().clear();
    }

    private void searchUsers() {
        Account account = accountService.getCurrentAccount();
        String query = searchField.getText() == null ? "" : searchField.getText().trim();
        if (account == null) {
            setStatus(Localization.text("social.notSignedIn"));
            return;
        }
        if (query.length() < 3) {
            setStatus(Localization.text("social.searchTooShort"));
            return;
        }
        searchButton.setDisable(true);
        execute(Localization.text("social.searching"), () -> api().searchUsers(account, query), results -> {
            searchButton.setDisable(false);
            renderSearchResults(results);
        }, failure -> {
            searchButton.setDisable(false);
            setStatus(errorMessage(failure));
        });
    }

    private void renderSearchResults(List<SocialUser> results) {
        searchResults.getChildren().clear();
        if (results.isEmpty()) {
            searchResults.getChildren().add(empty("social.searchNoResults"));
            return;
        }
        for (SocialUser user : results) {
            Label name = new Label(user.minecraftUsername());
            name.getStyleClass().add("social-list-title");
            Label display = new Label(user.displayName());
            display.getStyleClass().add("muted");
            VBox labels = new VBox(2, name, display);
            HBox.setHgrow(labels, Priority.ALWAYS);
            Button add = secondary(Localization.text("social.add"));
            add.setOnAction(event -> sendFriendRequest(user));
            HBox row = new HBox(8, labels, add);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("social-list-row");
            searchResults.getChildren().add(row);
        }
    }

    private void sendFriendRequest(SocialUser user) {
        Account account = accountService.getCurrentAccount();
        execute(Localization.text("social.sendingRequest"), () -> {
            api().sendFriendRequest(account, user.minecraftUsername());
            return null;
        }, ignored -> {
            setStatus(Localization.text("social.friendRequestSent").formatted(user.minecraftUsername()));
            refreshOverview();
        });
    }

    private void renderRequests(List<FriendRequest> values) {
        incomingRequests.getChildren().clear();
        if (values.isEmpty()) {
            incomingRequests.getChildren().add(empty("social.emptyIncoming"));
            return;
        }
        for (FriendRequest request : values) {
            Label name = new Label(request.user().minecraftUsername());
            name.getStyleClass().add("social-list-title");
            Button accept = primary(Localization.text("social.accept"));
            Button decline = secondary(Localization.text("social.decline"));
            accept.setOnAction(event -> respond(request, true));
            decline.setOnAction(event -> respond(request, false));
            VBox row = new VBox(6, name, new HBox(6, accept, decline));
            row.getStyleClass().add("social-list-item");
            incomingRequests.getChildren().add(row);
        }
    }

    private void respond(FriendRequest request, boolean accept) {
        Account account = accountService.getCurrentAccount();
        execute(accept ? Localization.text("social.accepting") : Localization.text("social.declining"), () -> {
            if (accept) api().acceptFriendRequest(account, request.id());
            else api().declineFriendRequest(account, request.id());
            return null;
        }, ignored -> refreshOverview());
    }

    private void renderFriends(List<Friend> values) {
        friends.getChildren().clear();
        if (values.isEmpty()) {
            friends.getChildren().add(empty("social.emptyFriends"));
            return;
        }
        for (Friend friend : values) {
            Label name = new Label(friend.minecraftUsername());
            name.getStyleClass().add("social-list-title");
            Button open = secondary(Localization.text("social.chat"));
            open.setOnAction(event -> openConversation(friend.id(), friend.minecraftUsername()));
            HBox row = new HBox(8, name, open);
            row.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(name, Priority.ALWAYS);
            row.getStyleClass().add("social-list-row");
            friends.getChildren().add(row);
        }
    }

    private void renderConversations(List<Conversation> values) {
        conversations.getChildren().clear();
        if (values.isEmpty()) {
            conversations.getChildren().add(empty("social.emptyConversations"));
            return;
        }
        for (Conversation conversation : values) {
            String name = conversation.otherUser() == null
                    ? Localization.text("social.groupConversation") : conversation.otherUser().minecraftUsername();
            Label title = new Label(name);
            title.getStyleClass().add("social-list-title");
            Label preview = new Label(conversation.lastMessage() == null
                    ? Localization.text("social.noMessagesYet") : conversation.lastMessage());
            preview.getStyleClass().add("muted");
            VBox labels = new VBox(2, title, preview);
            HBox.setHgrow(labels, Priority.ALWAYS);
            Button open = new Button(Localization.text("social.open"));
            open.getStyleClass().add("vanta-button-quiet");
            open.setOnAction(event -> selectConversation(conversation.id(), name));
            HBox row = new HBox(6, labels, open);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("social-list-row");
            conversations.getChildren().add(row);
        }
    }

    private void openConversation(String userId, String username) {
        Account account = accountService.getCurrentAccount();
        execute(Localization.text("social.openingConversation"), () ->
                api().openDirectConversation(account, userId), id -> {
            selectConversation(id, username);
            refreshOverview();
        });
    }

    private void selectConversation(String id, String username) {
        selectedConversationId = id;
        chatTitle.setText(username);
        chatSubtitle.setText(Localization.text("social.chatWith").formatted(username));
        messageInput.setDisable(false);
        sendButton.setDisable(false);
        loadMessages(false);
    }

    private void loadMessages(boolean silent) {
        String id = selectedConversationId;
        Account account = accountService.getCurrentAccount();
        if (id == null || account == null || !polling.compareAndSet(false, true)) return;
        execute(silent ? null : Localization.text("social.loadingMessages"), () -> api().getMessages(account, id),
                messages -> {
                    polling.set(false);
                    if (id.equals(selectedConversationId)) renderMessages(messages);
                }, failure -> polling.set(false));
    }

    private void pollMessages() {
        if (viewActive && selectedConversationId != null) loadMessages(true);
    }

    private void renderMessages(List<Message> values) {
        messageList.getChildren().clear();
        if (values.isEmpty()) {
            messageList.getChildren().add(empty("social.noMessagesYet"));
            return;
        }
        for (Message message : values) {
            boolean own = currentAccount != null && currentAccount.getUuid().equals(message.senderId());
            Label body = new Label(message.deleted() ? Localization.text("social.messageDeleted") : message.content());
            body.setWrapText(true);
            body.setMaxWidth(520);
            body.getStyleClass().add("social-message-content");
            String time = DateTimeFormatter.ofPattern("HH:mm")
                    .withZone(ZoneId.systemDefault()).format(Instant.ofEpochSecond(message.createdAt()));
            Label meta = new Label((own ? Localization.text("social.you") : message.senderMinecraftUsername())
                    + " · " + time);
            meta.getStyleClass().add("social-message-meta");
            VBox bubble = new VBox(5, body, meta);
            bubble.getStyleClass().add(own ? "social-message-own" : "social-message-other");
            HBox row = new HBox(bubble);
            row.setAlignment(own ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
            row.getStyleClass().add("social-message-row");
            messageList.getChildren().add(row);
        }
        messageScroll.layout();
        messageScroll.setVvalue(1.0);
    }

    private void sendMessage() {
        Account account = accountService.getCurrentAccount();
        String id = selectedConversationId;
        String content = messageInput.getText() == null ? "" : messageInput.getText().trim();
        if (account == null || id == null || content.isEmpty()) return;
        if (content.length() > 4000) {
            setStatus(Localization.text("social.messageTooLong"));
            return;
        }
        sendButton.setDisable(true);
        execute(Localization.text("social.sending"), () -> {
            api().sendMessage(account, id, content);
            return api().getMessages(account, id);
        }, messages -> {
            if (id.equals(selectedConversationId)) {
                messageInput.clear();
                renderMessages(messages);
            }
            sendButton.setDisable(false);
        }, failure -> {
            sendButton.setDisable(false);
            setStatus(errorMessage(failure));
        });
    }

    private Label empty(String key) {
        Label label = new Label(Localization.text(key));
        label.getStyleClass().add("muted");
        label.setWrapText(true);
        return label;
    }

    private void showChatPlaceholder(String message) {
        messageList.getChildren().setAll(empty("social.noMessagesYet"));
        chatTitle.setText(message);
        chatSubtitle.setText("");
        messageInput.setDisable(true);
        sendButton.setDisable(true);
    }

    private void setStatus(String message) {
        connectionStatus.setText(message == null ? "" : message);
    }

    private <T> void execute(String activity, Callable<T> work, Consumer<T> success) {
        execute(activity, work, success, failure -> setStatus(errorMessage(failure)));
    }

    private <T> void execute(String activity, Callable<T> work, Consumer<T> success,
                             Consumer<Exception> onFailure) {
        if (activity != null && !activity.isBlank()) setStatus(activity);
        try {
            UiExecutor.shared().execute(() -> {
                try {
                    T result = work.call();
                    UiExecutor.shared().dispatchToUi(() -> success.accept(result));
                } catch (Exception failure) {
                    UiExecutor.shared().dispatchToUi(() -> onFailure.accept(failure));
                }
            });
        } catch (RuntimeException rejected) {
            onFailure.accept(new IOException("Vanta's background task queue is full.", rejected));
        }
    }

    private String errorMessage(Exception failure) {
        if (failure instanceof VantaSocialClient.ApiException apiFailure) {
            return apiFailure.getMessage() == null || apiFailure.getMessage().isBlank()
                    ? Localization.text("social.requestFailed") : apiFailure.getMessage();
        }
        if (failure instanceof InterruptedException) {
            Thread.currentThread().interrupt();
            return Localization.text("social.requestInterrupted");
        }
        if (failure instanceof IOException) {
            return Localization.text("social.connectionFailed") + " " + failure.getMessage();
        }
        return Localization.text("social.requestFailed");
    }

    @Override public void onShown() {
        viewActive = true;
        accountService.addListener(accountListener);
        poller.play();
        refreshOverview();
    }

    @Override public void onHidden() {
        viewActive = false;
        accountService.removeListener(accountListener);
        poller.stop();
    }
}
