package com.mohamedgamal.masareef;

import java.util.LinkedHashMap;

/** One Android SMS_RECEIVED intent contains the PDUs of one message, ordered by part. */
final class SmsParts {
    static final class Message {
        final String sender;
        final StringBuilder body=new StringBuilder();
        long when;
        Message(String sender,long when){this.sender=sender;this.when=when;}
    }
    final LinkedHashMap<String,Message> messages=new LinkedHashMap<>();
    void add(String sender,long when,String body) {
        if(sender==null||body==null)return;
        Message message=messages.computeIfAbsent(sender,key->new Message(key,when));
        message.when=Math.min(message.when,when);message.body.append(body);
    }
}
