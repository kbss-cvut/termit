/*
 * TermIt
 * Copyright (C) 2025 Czech Technical University in Prague
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package cz.cvut.kbss.termit.websocket.util;

import jakarta.annotation.Nonnull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.ChannelInterceptor;

import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * Caches any message sent to the intercepted channel
 */
public class CachingChannelInterceptor implements ChannelInterceptor {

    private final BlockingQueue<Message<?>> messages = new ArrayBlockingQueue<>(100);

    @Override
    public Message<?> preSend(@Nonnull Message<?> message, @Nonnull MessageChannel channel) {
        this.messages.add(message);
        return message;
    }

    public void reset() {
        this.messages.clear();
    }

    public List<Message<?>> getMessages() {
        return List.copyOf(messages);
    }
}
