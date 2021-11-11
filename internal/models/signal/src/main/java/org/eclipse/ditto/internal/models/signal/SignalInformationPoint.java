/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.internal.models.signal;

import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.entity.id.WithEntityId;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.model.signals.WithType;
import org.eclipse.ditto.messages.model.signals.commands.MessageCommand;
import org.eclipse.ditto.messages.model.signals.commands.MessageCommandResponse;
import org.eclipse.ditto.things.model.signals.commands.ThingCommand;
import org.eclipse.ditto.things.model.signals.commands.ThingCommandResponse;

/**
 * Provides dedicated information about specified {@code Signal} arguments.
 *
 * @since 2.2.0
 */
@Immutable
public final class SignalInformationPoint {

    private static final String CHANNEL_LIVE_VALUE = "live";

    private SignalInformationPoint() {
        throw new AssertionError();
    }

    /**
     * Indicates whether the specified signal argument is a live command.
     *
     * @param signal the signal to be checked.
     * @return {@code true} if {@code signal} is a live command, i.e. either a message command or a thing command with
     * channel {@value CHANNEL_LIVE_VALUE} in its headers.
     * {@code false} if {@code signal} is not a live command.
     */
    public static boolean isLiveCommand(@Nullable final Signal<?> signal) {
        return isMessageCommand(signal) || isThingCommand(signal) && isChannelLive(signal);
    }

    /**
     * Indicates whether the specified signal argument is a {@link MessageCommand}.
     *
     * @param signal the signal to be checked.
     * @return {@code true} if {@code signal} is a {@code MessageCommand}, {@code false} else.
     */
    public static boolean isMessageCommand(@Nullable final Signal<?> signal) {
        return hasTypePrefix(signal, MessageCommand.TYPE_PREFIX);
    }

    private static boolean hasTypePrefix(@Nullable final WithType signal, final String typePrefix) {
        final boolean result;
        if (null != signal) {
            final var signalType = signal.getType();
            result = signalType.startsWith(typePrefix);
        } else {
            result = false;
        }
        return result;
    }

    /**
     * Indicates whether the specified signal argument is a {@link ThingCommand}.
     *
     * @param signal the signal to be checked.
     * @return {@code true} if {@code signal} is a {@code ThingCommand}, {@code false} else.
     */
    public static boolean isThingCommand(@Nullable final Signal<?> signal) {
        return hasTypePrefix(signal, ThingCommand.TYPE_PREFIX);
    }

    /**
     * Indicates whether the specified signal is a live command response.
     *
     * @param signal the signal to be checked.
     * @return {@code true} if {@code signal} is a live command response, i.e. either a message command response
     * or a thing command response with channel {@value CHANNEL_LIVE_VALUE} in its headers.
     * {@code false} if {@code signal} is not a live command response.
     */
    public static boolean isLiveCommandResponse(@Nullable final Signal<?> signal) {
        return isMessageCommandResponse(signal) || isThingCommandResponse(signal) && isChannelLive(signal);
    }

    /**
     * Indicates whether the specified signal argument is a {@link MessageCommandResponse}.
     *
     * @param signal the signal to be checked.
     * @return {@code true} if {@code signal} is a {@code MessageCommandResponse}, {@code false} else.
     */
    public static boolean isMessageCommandResponse(@Nullable final Signal<?> signal) {
        return hasTypePrefix(signal, MessageCommandResponse.TYPE_PREFIX);
    }

    /**
     * Indicates whether the specified signal argument is a {@link ThingCommandResponse}.
     *
     * @param signal the signal to be checked.
     * @return {@code true} if {@code signal} is a {@code ThingCommandResponse}, {@code false} else.
     */
    public static boolean isThingCommandResponse(@Nullable final Signal<?> signal) {
        return hasTypePrefix(signal, ThingCommandResponse.TYPE_PREFIX);
    }

    /**
     * Indicates whether the headers of the specified signal argument contain channel {@value CHANNEL_LIVE_VALUE}.
     *
     * @param signal the signal to be checked.
     * @return {@code true} if the headers of {@code signal} contain the channel {@value CHANNEL_LIVE_VALUE}.
     */
    public static boolean isChannelLive(@Nullable final WithDittoHeaders signal) {
        final boolean result;
        if (null != signal) {
            final var dittoHeaders = signal.getDittoHeaders();
            result = dittoHeaders.getChannel().filter(CHANNEL_LIVE_VALUE::equals).isPresent();
        } else {
            result = false;
        }
        return result;
    }

    /**
     * Indicates whether the specified signal argument provides an entity ID.
     *
     * @param signal the signal to be checked.
     * @return {@code true} if {@code signal} provides an entity ID because it implements {@link WithEntityId}.
     * {@code false} else.
     */
    public static boolean isWithEntityId(@Nullable final Signal<?> signal) {
        final boolean result;
        if (null != signal) {
            result = WithEntityId.class.isAssignableFrom(signal.getClass());
        } else {
            result = false;
        }
        return result;
    }

    /**
     * Returns the {@link EntityId} for the specified signal argument.
     *
     * @param signal the signal to get the entity ID from.
     * @return an {@code Optional} containing the signal's entity ID if it provides one, an empty {@code Optional} else.
     * @see #isWithEntityId(Signal)
     */
    public static Optional<EntityId> getEntityId(@Nullable final Signal<?> signal) {
        final Optional<EntityId> result;
        if (isWithEntityId(signal)) {
            result = Optional.of(((WithEntityId) signal).getEntityId());
        } else {
            result = Optional.empty();
        }
        return result;
    }

    /**
     * Returns the optional correlation ID of the specified {@code Signal} argument's headers.
     *
     * @param signal the signal to get the optional correlation ID from.
     * @return the optional correlation ID. The optional is empty if {@code signal} is {@code null}.
     */
    public static Optional<String> getCorrelationId(@Nullable final Signal<?> signal) {
        final Optional<String> result;
        if (null != signal) {
            final var signalDittoHeaders = signal.getDittoHeaders();
            result = signalDittoHeaders.getCorrelationId();
        } else {
            result = Optional.empty();
        }
        return result;
    }

}
