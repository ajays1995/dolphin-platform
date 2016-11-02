package com.canoo.dolphin.server.event.context;

import com.canoo.dolphin.event.Subscription;
import com.canoo.dolphin.server.bootstrap.DolphinPlatformBootstrap;
import com.canoo.dolphin.server.context.DolphinContext;
import com.canoo.dolphin.server.event.DolphinEventBus;
import com.canoo.dolphin.server.event.Message;
import com.canoo.dolphin.server.event.MessageListener;
import com.canoo.dolphin.server.event.Topic;
import com.canoo.dolphin.util.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class DefaultDolphinEventBus implements DolphinEventBus {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultDolphinEventBus.class);

    private final Map<Topic<?>, List<MessageListener<?>>> receiverPerSession = new ConcurrentHashMap<>();

    @Override
    public <T> void publish(final Topic<T> topic, final T data) {
        Assert.requireNonNull(topic, "topic");
        LOG.trace("Publishing data for topic {}", topic.getName());
        final List<MessageListener<?>> listeners = receiverPerSession.get(topic);
        if(listeners != null) {
            final long timestamp = System.currentTimeMillis();
            for(MessageListener<?> listener : listeners) {
                listener.onMessage(new Message() {
                    @Override
                    public Topic<?> getTopic() {
                        return topic;
                    }

                    @Override
                    public Object getData() {
                        return data;
                    }

                    @Override
                    public long getSendTimestamp() {
                        return timestamp;
                    }
                });
            }
        }
    }

    @Override
    public <T> Subscription subscribe(final Topic<T> topic, final MessageListener<? super T> handler) {
        Assert.requireNonNull(topic, "topic");
        Assert.requireNonNull(handler, "handler");
        final DolphinContext currentContext = DolphinPlatformBootstrap.getInstance().getCurrentContext();
        if(currentContext == null) {
            throw new IllegalStateException("Subscription can only be done from Dolphin Context!");
        }

        LOG.trace("Adding subscription for topic {} in Dolphin Platform context {}", topic.getName(), currentContext.getId());
        List<MessageListener<?>> listeners = receiverPerSession.get(topic);
        if(listeners == null) {
            listeners = new CopyOnWriteArrayList<>();
            receiverPerSession.put(topic, listeners);
        }

        final MessageListener<? super T> listener = new MessageListener<T>() {
            @Override
            public void onMessage(final Message<T> message) {
                if(DolphinPlatformBootstrap.getInstance().isCurrentContext(currentContext.getId())) {
                    LOG.trace("Event listener for topic {} can be called directly in Dolphin Platform context {}", topic.getName(), currentContext.getId());
                    ((MessageListener<T>)handler).onMessage(message);
                } else {
                    LOG.trace("Event listener for topic {} must be called later in Dolphin Platform context {}", topic.getName(), currentContext.getId());
                    currentContext.getCurrentDolphinSession().runLater(new Runnable() {

                        @Override
                        public void run() {
                            LOG.trace("Calling event listener for topic {} in Dolphin Platform context {}", topic.getName(), currentContext.getId());
                            ((MessageListener<T>)handler).onMessage(message);
                        }
                    });
                }
            }
        };
        listeners.add(listener);
        return new Subscription() {
            @Override
            public void unsubscribe() {
                LOG.trace("Removing subscription for topic {} in Dolphin Platform context {}", topic.getName(), currentContext.getId());
                receiverPerSession.get(topic).remove(listener);
            }
        };
    }
}
