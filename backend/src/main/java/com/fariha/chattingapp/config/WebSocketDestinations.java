package com.fariha.chattingapp.config;

public final class WebSocketDestinations {
    public static final String APPLICATION_PREFIX = "/app";
    public static final String TOPIC_PREFIX = "/topic";
    public static final String QUEUE_PREFIX = "/queue";
    public static final String USER_PREFIX = "/user";

    public static final String USER_CONVERSATIONS_QUEUE = QUEUE_PREFIX + "/conversations";
    public static final String USER_CONVERSATIONS_DESTINATION = USER_PREFIX + USER_CONVERSATIONS_QUEUE;

    public static final String CONVERSATION_TOPIC_PREFIX = TOPIC_PREFIX + "/conversations/";

    private WebSocketDestinations() {
    }

    public static String conversation(String conversationId) {
        return CONVERSATION_TOPIC_PREFIX + conversationId;
    }

    public static String conversationStatus(String conversationId) {
        return conversation(conversationId) + "/status";
    }

    public static String conversationTyping(String conversationId) {
        return conversation(conversationId) + "/typing";
    }

    public static String conversationCalls(String conversationId) {
        return conversation(conversationId) + "/calls";
    }
}
