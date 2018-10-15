/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.utils.persistence.mongo.namespace;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

import akka.NotUsed;
import akka.stream.javadsl.Source;

/**
 * Persistence operations on the level of namespaces.
 *
 * @param <S> type of namespace selections
 */
public interface NamespaceOps<S> {

    /**
     * Purge documents in a namespace from one collection.
     *
     * @param selection collection-filter pair to identify documents belonging to a namespace.
     * @return source of any error during the purge.
     */
    Source<Optional<Throwable>, NotUsed> purge(final S selection);

    /**
     * Purge documents in a namespace from all given collections.
     *
     * @param selections collection-filter pairs to identify documents belonging to a namespace.
     * @return source of any errors during the purge.
     */
    default Source<List<Throwable>, NotUsed> purgeAll(final Collection<S> selections) {
        return Source.from(selections)
                .flatMapConcat(this::purge)
                .flatMapConcat(result -> result.map(Source::single).orElseGet(Source::empty))
                .fold(new LinkedList<>(), NamespaceOps::appendToMutableList);
    }

    static <T> List<T> appendToMutableList(final List<T> xs, final T x) {
        xs.add(x);
        return xs;
    }
}
