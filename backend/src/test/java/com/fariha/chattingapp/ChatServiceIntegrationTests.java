package com.fariha.chattingapp;

import com.fariha.chattingapp.dto.ConversationDto;
import com.fariha.chattingapp.dto.MessageDto;
import com.fariha.chattingapp.dto.MessagePageResponse;
import com.fariha.chattingapp.dto.SendMessageRequest;
import com.fariha.chattingapp.entity.UserAccount;
import com.fariha.chattingapp.repository.ChatMessageRepository;
import com.fariha.chattingapp.repository.ConversationRepository;
import com.fariha.chattingapp.repository.MessageReceiptRepository;
import com.fariha.chattingapp.repository.UserAccountRepository;
import com.fariha.chattingapp.service.ChatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class ChatServiceIntegrationTests {
    @Autowired
    private ChatService chatService;

    @Autowired
    private UserAccountRepository userRepository;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private ChatMessageRepository messageRepository;

    @Autowired
    private MessageReceiptRepository receiptRepository;

    private UserAccount alice;
    private UserAccount bob;

    @BeforeEach
    void cleanDatabase() {
        receiptRepository.deleteAll();
        messageRepository.deleteAll();
        conversationRepository.deleteAll();
        userRepository.deleteAll();

        alice = saveUser("alice", "Alice", "+8801744444444");
        bob = saveUser("bob", "Bob", "+8801755555555");
    }

    @Test
    void messageHistoryUsesCursorPagination() throws InterruptedException {
        ConversationDto conversation = chatService.createDirectConversation(bob.getId(), alice);

        chatService.sendMessage(new SendMessageRequest(conversation.id(), "one", "TEXT", null), alice);
        Thread.sleep(2);
        chatService.sendMessage(new SendMessageRequest(conversation.id(), "two", "TEXT", null), alice);
        Thread.sleep(2);
        chatService.sendMessage(new SendMessageRequest(conversation.id(), "three", "TEXT", null), alice);

        MessagePageResponse firstPage = chatService.getMessages(conversation.id(), null, 2, alice);
        assertThat(firstPage.messages()).extracting(message -> message.content()).containsExactly("two", "three");
        assertThat(firstPage.hasMore()).isTrue();
        assertThat(firstPage.nextBefore()).isNotNull();

        MessagePageResponse secondPage = chatService.getMessages(conversation.id(), firstPage.nextBefore(), 2, alice);
        assertThat(secondPage.messages()).extracting(message -> message.content()).containsExactly("one");
        assertThat(secondPage.hasMore()).isFalse();
    }

    @Test
    void senderCannotMarkOwnMessageRead() {
        ConversationDto conversation = chatService.createDirectConversation(bob.getId(), alice);
        String messageId = chatService.sendMessage(
                new SendMessageRequest(conversation.id(), "hello", "TEXT", null),
                alice
        ).id();

        assertThatThrownBy(() -> chatService.updateMessageStatus(messageId, ChatService.parseStatus("READ"), alice))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403 FORBIDDEN");

        assertThat(chatService.updateMessageStatus(messageId, ChatService.parseStatus("READ"), bob).status())
                .isEqualTo("READ");
    }

    @Test
    void conversationListShowsUnreadIncomingMessagesForViewer() {
        ConversationDto conversation = chatService.createDirectConversation(bob.getId(), alice);
        String messageId = chatService.sendMessage(
                new SendMessageRequest(conversation.id(), "hello", "TEXT", null),
                alice
        ).id();

        assertThat(chatService.listConversations(alice).getFirst().unreadCount()).isZero();
        assertThat(chatService.listConversations(bob).getFirst().unreadCount()).isEqualTo(1);

        chatService.updateMessageStatus(messageId, ChatService.parseStatus("READ"), bob);

        assertThat(chatService.listConversations(bob).getFirst().unreadCount()).isZero();
    }

    @Test
    void directConversationIsReusedForSameParticipants() {
        ConversationDto first = chatService.createDirectConversation(bob.getId(), alice);
        ConversationDto second = chatService.createDirectConversation(alice.getId(), bob);

        assertThat(second.id()).isEqualTo(first.id());
    }

    @Test
    void senderCannotAttachAnotherUsersMedia() {
        ConversationDto conversation = chatService.createDirectConversation(bob.getId(), alice);

        assertThatThrownBy(() -> chatService.sendMessage(
                new SendMessageRequest(conversation.id(), "image", "IMAGE", null, "users/" + bob.getId() + "/image.png"),
                alice
        ))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403 FORBIDDEN");
    }

    @Test
    void imageMessageRequiresMedia() {
        ConversationDto conversation = chatService.createDirectConversation(bob.getId(), alice);

        assertThatThrownBy(() -> chatService.sendMessage(
                new SendMessageRequest(conversation.id(), "caption only", "IMAGE", null, null),
                alice
        ))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400 BAD_REQUEST");
    }

    @Test
    void externalMediaUrlsAreRejected() {
        ConversationDto conversation = chatService.createDirectConversation(bob.getId(), alice);

        assertThatThrownBy(() -> chatService.sendMessage(
                new SendMessageRequest(conversation.id(), "external", "IMAGE", "https://example.com/image.png", null),
                alice
        ))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400 BAD_REQUEST");
    }

    @Test
    void deletingConversationHidesItOnlyForCurrentUser() {
        ConversationDto conversation = chatService.createDirectConversation(bob.getId(), alice);

        chatService.deleteConversationForUser(conversation.id(), alice);

        assertThat(chatService.listConversations(alice)).isEmpty();
        assertThat(chatService.listConversations(bob)).hasSize(1);
        assertThatThrownBy(() -> chatService.getMessages(conversation.id(), null, 50, alice))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404 NOT_FOUND");
    }

    @Test
    void newMessageRestoresHiddenConversationForParticipants() {
        ConversationDto conversation = chatService.createDirectConversation(bob.getId(), alice);
        chatService.deleteConversationForUser(conversation.id(), alice);

        chatService.sendMessage(new SendMessageRequest(conversation.id(), "new message", "TEXT", null), bob);

        assertThat(chatService.listConversations(alice)).hasSize(1);
    }

    @Test
    void deleteMessageForMeHidesItOnlyForCurrentUser() {
        ConversationDto conversation = chatService.createDirectConversation(bob.getId(), alice);
        String messageId = chatService.sendMessage(
                new SendMessageRequest(conversation.id(), "private cleanup", "TEXT", null),
                alice
        ).id();

        chatService.deleteMessageForMe(messageId, bob);

        assertThat(chatService.getMessages(conversation.id(), null, 50, bob).messages()).isEmpty();
        assertThat(chatService.getMessages(conversation.id(), null, 50, alice).messages())
                .extracting(MessageDto::content)
                .containsExactly("private cleanup");
        assertThat(chatService.listConversations(bob).getFirst().unreadCount()).isZero();
    }

    @Test
    void deleteMessageForEveryoneLeavesTombstoneForParticipants() {
        ConversationDto conversation = chatService.createDirectConversation(bob.getId(), alice);
        String messageId = chatService.sendMessage(
                new SendMessageRequest(conversation.id(), "remove everywhere", "TEXT", null),
                alice
        ).id();

        assertThatThrownBy(() -> chatService.deleteMessageForEveryone(messageId, bob))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403 FORBIDDEN");

        MessageDto deleted = chatService.deleteMessageForEveryone(messageId, alice);

        assertThat(deleted.deletedForEveryone()).isTrue();
        assertThat(deleted.expired()).isFalse();
        assertThat(deleted.content()).isEmpty();
        assertThat(chatService.getMessages(conversation.id(), null, 50, bob).messages().getFirst().deletedForEveryone())
                .isTrue();
    }

    @Test
    void temporaryMessageExpiresForParticipants() throws InterruptedException {
        ConversationDto conversation = chatService.createDirectConversation(bob.getId(), alice);
        String messageId = chatService.sendMessage(
                new SendMessageRequest(conversation.id(), "temporary", "TEXT", null, null, 1),
                alice
        ).id();

        Thread.sleep(1100);

        MessageDto expired = chatService.getMessages(conversation.id(), null, 50, bob)
                .messages()
                .stream()
                .filter(message -> message.id().equals(messageId))
                .findFirst()
                .orElseThrow();
        assertThat(expired.deletedForEveryone()).isTrue();
        assertThat(expired.expired()).isTrue();
        assertThat(expired.content()).isEmpty();
        assertThat(chatService.listConversations(bob).getFirst().unreadCount()).isZero();
    }

    private UserAccount saveUser(String username, String displayName, String phoneNumber) {
        UserAccount user = new UserAccount(username, displayName, "hash");
        user.setPhoneNumber(phoneNumber);
        return userRepository.save(user);
    }
}
