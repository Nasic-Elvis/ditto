/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.connectivity.messaging;

import static org.eclipse.ditto.services.connectivity.messaging.validation.ConnectionValidator.resolveConnectionIdPlaceholder;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.acks.AcknowledgementLabel;
import org.eclipse.ditto.model.base.acks.AcknowledgementRequest;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.ConnectionId;
import org.eclipse.ditto.model.connectivity.ConnectionType;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.PayloadMapping;
import org.eclipse.ditto.model.connectivity.PayloadMappingDefinition;
import org.eclipse.ditto.model.connectivity.Target;
import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.protocoladapter.ProtocolAdapter;
import org.eclipse.ditto.protocoladapter.ProtocolFactory;
import org.eclipse.ditto.services.connectivity.mapping.DefaultMessageMapperFactory;
import org.eclipse.ditto.services.connectivity.mapping.DittoMessageMapper;
import org.eclipse.ditto.services.connectivity.mapping.MessageMapper;
import org.eclipse.ditto.services.connectivity.mapping.MessageMapperFactory;
import org.eclipse.ditto.services.connectivity.mapping.MessageMapperRegistry;
import org.eclipse.ditto.services.connectivity.messaging.config.ConnectivityConfig;
import org.eclipse.ditto.services.connectivity.messaging.mappingoutcome.MappingOutcome;
import org.eclipse.ditto.services.connectivity.util.ConnectivityMdcEntryKey;
import org.eclipse.ditto.services.models.connectivity.ExternalMessage;
import org.eclipse.ditto.services.models.connectivity.ExternalMessageFactory;
import org.eclipse.ditto.services.models.connectivity.OutboundSignal;
import org.eclipse.ditto.services.models.connectivity.OutboundSignalFactory;
import org.eclipse.ditto.services.utils.akka.logging.ThreadSafeDittoLoggingAdapter;
import org.eclipse.ditto.signals.base.Signal;

import akka.actor.ActorSystem;

/**
 * Processes outgoing {@link Signal}s to {@link ExternalMessage}s.
 * Encapsulates the message processing logic from the message mapping processor actor.
 */
public final class OutboundMappingProcessor extends AbstractMappingProcessor<OutboundSignal, OutboundSignal.Mapped> {

    private final ProtocolAdapter protocolAdapter;

    private OutboundMappingProcessor(final ConnectionId connectionId,
            final ConnectionType connectionType,
            final MessageMapperRegistry registry,
            final ThreadSafeDittoLoggingAdapter logger,
            final ProtocolAdapter protocolAdapter) {

        super(registry, logger, connectionId, connectionType);
        this.protocolAdapter = protocolAdapter;
    }

    /**
     * Initializes a new command processor with mappers defined in mapping mappingContext.
     * The dynamic access is needed to instantiate message mappers for an actor system.
     *
     * @param connectionId the connection ID that the processor works for.
     * @param connectionType the type of the connection that the processor works for.
     * @param mappingDefinition the configured mappings used by this processor
     * @param actorSystem the dynamic access used for message mapper instantiation.
     * @param connectivityConfig the configuration settings of the Connectivity service.
     * @param protocolAdapter the ProtocolAdapter to be used.
     * @param logger the logging adapter to be used for log statements.
     * @return the processor instance.
     * @throws org.eclipse.ditto.model.connectivity.MessageMapperConfigurationInvalidException if the configuration of
     * one of the {@code mappingContext} is invalid.
     * @throws org.eclipse.ditto.model.connectivity.MessageMapperConfigurationFailedException if the configuration of
     * one of the {@code mappingContext} failed for a mapper specific reason.
     */
    public static OutboundMappingProcessor of(final ConnectionId connectionId,
            final ConnectionType connectionType,
            final PayloadMappingDefinition mappingDefinition,
            final ActorSystem actorSystem,
            final ConnectivityConfig connectivityConfig,
            final ProtocolAdapter protocolAdapter,
            final ThreadSafeDittoLoggingAdapter logger) {

        final ThreadSafeDittoLoggingAdapter loggerWithConnectionId =
                logger.withMdcEntry(ConnectivityMdcEntryKey.CONNECTION_ID, connectionId);

        final MessageMapperFactory messageMapperFactory =
                DefaultMessageMapperFactory.of(connectionId, actorSystem, connectivityConfig.getMappingConfig(),
                        loggerWithConnectionId);
        final MessageMapperRegistry registry =
                messageMapperFactory.registryOf(DittoMessageMapper.CONTEXT, mappingDefinition);

        return new OutboundMappingProcessor(connectionId, connectionType, registry, loggerWithConnectionId,
                protocolAdapter);
    }

    /**
     * Processes an {@link OutboundSignal} to 0..n {@link OutboundSignal.Mapped} signals or errors.
     *
     * @param outboundSignal the outboundSignal to be processed.
     * @return the list of mapping outcomes.
     */
    @Override
    List<MappingOutcome<OutboundSignal.Mapped>> process(final OutboundSignal outboundSignal) {
        final List<OutboundSignal.Mappable> mappableSignals;
        if (outboundSignal.getTargets().isEmpty()) {
            // responses/errors do not have a target assigned, read mapper used for inbound message from internal header
            final PayloadMapping payloadMapping = outboundSignal.getSource()
                    .getDittoHeaders()
                    .getInboundPayloadMapper()
                    .map(ConnectivityModelFactory::newPayloadMapping)
                    .orElseGet(ConnectivityModelFactory::emptyPayloadMapping); // fallback to default payload mapping
            final OutboundSignal.Mappable mappableSignal =
                    OutboundSignalFactory.newMappableOutboundSignal(outboundSignal.getSource(),
                            outboundSignal.getTargets(), payloadMapping);
            mappableSignals = Collections.singletonList(mappableSignal);
        } else {
            // group targets with exact same list of mappers together to avoid redundant mappings
            mappableSignals = outboundSignal.getTargets()
                    .stream()
                    .collect(Collectors.groupingBy(Target::getPayloadMapping, LinkedHashMap::new, Collectors.toList()))
                    .entrySet()
                    .stream()
                    .map(e -> OutboundSignalFactory.newMappableOutboundSignal(outboundSignal.getSource(), e.getValue(),
                            e.getKey()))
                    .collect(Collectors.toList());
        }
        return processMappableSignals(outboundSignal, mappableSignals);
    }

    private List<MappingOutcome<OutboundSignal.Mapped>> processMappableSignals(final OutboundSignal outboundSignal,
            final List<OutboundSignal.Mappable> mappableSignals) {

        final MappingTimer timer = MappingTimer.outbound(connectionId, connectionType);

        final Set<AcknowledgementLabel> issuedAckLabels = outboundSignal.getTargets()
                .stream()
                .map(Target::getIssuedAcknowledgementLabel)
                .flatMap(Optional::stream)
                .map(ackLabel -> resolveConnectionIdPlaceholder(connectionIdResolver, ackLabel))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toSet());

        final Signal<?> signalToMap;
        if (!issuedAckLabels.isEmpty()) {
            final DittoHeaders dittoHeaders = outboundSignal.getSource().getDittoHeaders();
            final Set<AcknowledgementRequest> publishedAckRequests = dittoHeaders.getAcknowledgementRequests();
            publishedAckRequests.removeIf(ackRequest -> issuedAckLabels.contains(ackRequest.getLabel()));
            signalToMap = outboundSignal.getSource().setDittoHeaders(dittoHeaders.toBuilder()
                    .acknowledgementRequests(publishedAckRequests)
                    .build());
        } else {
            signalToMap = outboundSignal.getSource();
        }

        final Adaptable adaptableWithoutExtra =
                timer.protocol(() -> protocolAdapter.toAdaptable(signalToMap));
        final Adaptable adaptable = outboundSignal.getExtra()
                .map(extra -> ProtocolFactory.setExtra(adaptableWithoutExtra, extra))
                .orElse(adaptableWithoutExtra);

        return timer.overall(() -> mappableSignals.stream()
                .flatMap(mappableSignal -> {
                    final Signal<?> source = mappableSignal.getSource();
                    final List<Target> targets = mappableSignal.getTargets();
                    final List<MessageMapper> mappers = getMappers(mappableSignal.getPayloadMapping());
                    logger.withCorrelationId(adaptable)
                            .debug("Resolved mappers for message {} to targets {}: {}", source, targets, mappers);
                    // convert messages in the order of payload mapping and forward to result handler
                    return mappers.stream().flatMap(mapper -> runMapper(mappableSignal, adaptable, mapper, timer));
                })
                .collect(Collectors.toList()));
    }

    private Stream<MappingOutcome<OutboundSignal.Mapped>> runMapper(final OutboundSignal.Mappable outboundSignal,
            final Adaptable adaptable,
            final MessageMapper mapper,
            final MappingTimer timer) {

        try {
            if (shouldMapMessageByConditions(outboundSignal, mapper)) {
                logger.withCorrelationId(adaptable)
                        .debug("Applying mapper <{}> to message <{}>", mapper.getId(), adaptable);

                final List<ExternalMessage> messages =
                        timer.payload(mapper.getId(), () -> checkForNull(mapper.map(adaptable)));

                logger.withCorrelationId(adaptable)
                        .debug("Mapping <{}> produced <{}> messages.", mapper.getId(), messages.size());

                if (messages.isEmpty()) {
                    return Stream.of(MappingOutcome.dropped(null));
                } else {
                    return messages.stream()
                            .map(em -> {
                                final ExternalMessage externalMessage =
                                        ExternalMessageFactory.newExternalMessageBuilder(em)
                                                .withTopicPath(adaptable.getTopicPath())
                                                .withInternalHeaders(outboundSignal.getSource().getDittoHeaders())
                                                .build();
                                final OutboundSignal.Mapped mapped =
                                        OutboundSignalFactory.newMappedOutboundSignal(outboundSignal, adaptable,
                                                externalMessage);
                                return MappingOutcome.mapped(mapped, adaptable.getTopicPath(), null);
                            });
                }
            } else {
                logger.withCorrelationId(adaptable)
                        .debug("Not mapping message with mapper <{}> as MessageMapper conditions {} were not matched.",
                                mapper.getId(), mapper.getIncomingConditions());
                return Stream.of(MappingOutcome.dropped(null));
            }
        } catch (final Exception e) {
            return Stream.of(
                    MappingOutcome.error(toDittoRuntimeException(e, mapper, adaptable), adaptable.getTopicPath(), null)
            );
        }
    }

    private static DittoRuntimeException toDittoRuntimeException(final Throwable error, final MessageMapper mapper,
            final Adaptable adaptable) {
        return DittoRuntimeException.asDittoRuntimeException(error, e -> {
            final DittoHeaders headers = adaptable.getDittoHeaders();
            final String contentType = headers.getOrDefault(ExternalMessage.CONTENT_TYPE_HEADER, "");
            return buildMappingFailedException("outbound", contentType, mapper.getId(), headers, e);
        });
    }

    private boolean shouldMapMessageByConditions(final OutboundSignal.Mappable mappable,
            final MessageMapper mapper) {
        return resolveConditions(mapper.getOutgoingConditions().values(),
                Resolvers.forOutboundSignal(mappable, connectionId));
    }

    private static <T> List<T> checkForNull(@Nullable final List<T> messages) {
        return messages == null ? List.of() : messages;
    }

}
