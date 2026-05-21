package com.fariha.chattingapp;

import com.fariha.chattingapp.dto.ConversationDto;
import com.fariha.chattingapp.dto.MessagePageResponse;
import com.fariha.chattingapp.dto.SendMessageRequest;
import com.fariha.chattingapp.entity.UserAccount;
import com.fariha.chattingapp.repository.ChatMessageRepository;
import com.fariha.chattingapp.repository.ConversationRepository;
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

    private UserAccount alice;
    private UserAccount bob;

    @BeforeEach
    void cleanDatabase() {
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
    void directConversationIsReusedForSameParticipants() {
        ConversationDto first = chatService.createDirectConversation(bob.getId(), alice);
        ConversationDto second = chatService.createDirectConversation(alice.getId(), bob);

        assertThat(second.id()).isEqualTo(first.id());
    }

    private UserAccount saveUser(String username, String displayName, String phoneNumber) {
        UserAccount user = new UserAccount(username, displayName, "hash");
        user.setPhoneNumber(phoneNumber);
        return userRepository.save(user);
    }
}
