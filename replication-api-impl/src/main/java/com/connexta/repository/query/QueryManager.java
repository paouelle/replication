/**
 * Copyright (c) Connexta
 *
 * <p>This is free software: you can redistribute it and/or modify it under the terms of the GNU
 * Lesser General Public License as published by the Free Software Foundation, either version 3 of
 * the License, or any later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details. A copy of the GNU Lesser General Public
 * License is distributed along with this program and can be found at
 * <http://www.gnu.org/licenses/lgpl.html>.
 */
package com.connexta.repository.query;

import com.connexta.ion.replication.api.data.ReplicationSite;
import com.connexta.ion.replication.api.impl.NodeAdapters;
import com.connexta.ion.replication.api.persistence.SiteManager;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Managers for instantiating {@link QueryService} class based on configuration. */
public class QueryManager {
  private static final Logger LOGGER = LoggerFactory.getLogger(QueryManager.class);

  /**
   * Pool size. Not being configured since this is temporary for now until we moved to distributed
   * micro-services.
   */
  private static final int POOL_SIZE = 5;

  /** Startup delay for polling for configuration changes in second. */
  private static final long STARTUP_DELAY = TimeUnit.MINUTES.toSeconds(1);

  /** Default polling period for configuration changes in seconds. */
  private static final long DEFAULT_REPLICATION_PERIOD = TimeUnit.MINUTES.toSeconds(5);

  /** A utility for obtaining node adapter factories based on node types. */
  private final NodeAdapters adapters;

  /** The site configuration manager. */
  private final SiteManager siteManager;

  /** Sets of identifiers for the sites to be managed or empty to manage all sites. */
  private final Set<String> sites;

  /** The polling period for configuration changes in seconds. */
  private final long period;

  private final ScheduledExecutorService executor;

  /** Sets of active query services keyed by site its. */
  private final Map<String, QueryService> services = new ConcurrentHashMap<>();

  /**
   * Instantiate a new query manager.
   *
   * @param adapters a utility for obtaining node adapter factories based on node types
   * @param siteManager the site configuration manager
   * @param sites identifiers for the sites to be managed or empty to manage all sites
   * @param period the polling period for configuration changes in seconds
   */
  public QueryManager(
      NodeAdapters adapters, SiteManager siteManager, Stream<String> sites, long period) {
    this(
        adapters,
        siteManager,
        sites,
        period,
        Executors.newScheduledThreadPool(QueryManager.POOL_SIZE));
  }

  private QueryManager(
      NodeAdapters adapters,
      SiteManager siteManager,
      Stream<String> sites,
      long period,
      ScheduledExecutorService executor) {
    this.adapters = adapters;
    this.siteManager = siteManager;
    this.sites = sites.collect(Collectors.toSet());
    this.period = period > 0L ? period : QueryManager.DEFAULT_REPLICATION_PERIOD;
    this.executor = executor;
  }

  /**
   * Initializes the query manager and kick start all query services to start polling sites that
   * must be polled.
   */
  public void init() {
    if (sites.isEmpty()) {
      LOGGER.info("Managing queries for all configured sites.");
    } else {
      LOGGER.info("Managing queries for sites: {}.", sites);
    }
    executor.scheduleAtFixedRate(
        this::reloadSiteConfigs, QueryManager.STARTUP_DELAY, period, TimeUnit.SECONDS);
  }

  /** Destroys the query manager and stop all currently running query services. */
  public void destroy() {
    LOGGER.info("Shutting down query managers");
    executor.shutdownNow();
  }

  private void reloadSiteConfigs() {
    final Map<String, ReplicationSite> newSites =
        siteManager
            .objects()
            .collect(Collectors.toMap(ReplicationSite::getId, Function.identity()));

    // stop query services for sites that are no longer configured
    // update those that were already started
    for (final Iterator<Map.Entry<String, QueryService>> i = services.entrySet().iterator();
        i.hasNext(); ) {
      final Map.Entry<String, QueryService> e = i.next();
      final ReplicationSite updatedSite = newSites.remove(e.getKey());

      if (updatedSite == null) {
        i.remove();
        e.getValue().stop();
      } else {
        e.getValue().update(updatedSite);
      }
    }
    // stop query services for sites that must no longer be only polled
    newSites
        .values()
        .stream()
        .filter(((Predicate<ReplicationSite>) QueryManager::mustBePolled).negate())
        .map(this::remove)
        .forEach(QueryService::stop);
    // start query services for newly discovered sites that must be polled
    newSites
        .values()
        .stream()
        .filter(QueryManager::mustBePolled)
        .map(this::newService)
        .forEach(QueryService::start);
  }

  private QueryService newService(ReplicationSite site) {
    return services.computeIfAbsent(site.getId(), i -> new QueryService(site, adapters, executor));
  }

  private QueryService remove(ReplicationSite site) {
    return services.remove(site.getId());
  }

  private static boolean mustBePolled(ReplicationSite site) {
    return site.getType().mustBePolled();
  }
}
