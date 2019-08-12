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

import com.connexta.ion.replication.api.NodeAdapter;
import com.connexta.ion.replication.api.data.ReplicationSite;
import com.connexta.ion.replication.api.impl.NodeAdapters;
import java.util.concurrent.ScheduledExecutorService;

/**
 * The <code>QueryService</code> class implements the logic for querying a specific DDF-based site
 * for changes to be replicated or harvested.
 */
public class QueryService {
  /** A utility for obtaining node adapter factories based on node types. */
  private final NodeAdapters adapters;

  private final ScheduledExecutorService executor;
  /** The current configuration for the site being queried by this query service. */
  private ReplicationSite site;
  /** The adapter to communicate with a given site. */
  private NodeAdapter adapter;

  /**
   * Instantiates a new query service.
   *
   * @param site the configuration for the site to be queried by this query service
   * @param adapters a utility for obtaining node adapter factories based on node types
   * @param executor the executor to use for scheduling polls
   */
  public QueryService(
      ReplicationSite site, NodeAdapters adapters, ScheduledExecutorService executor) {
    this.site = site;
    this.adapters = adapters;
    this.executor = executor;
  }

  /**
   * Updates the configuration for the site currently being queried by this query service.
   *
   * @param site the new site configuration
   */
  public void update(ReplicationSite site) {
    synchronized (this) {
      this.site = site;
      this.adapter = adapters.factoryFor(site.getType()).create(site.getUrl());
    }
  }

  /** Starts this query service. */
  public void start() {}

  /** Stops this query service. */
  public void stop() {}

  @Override
  public String toString() {
    return String.format(
        "QueryService{site=%s[%s], adapter=%s}",
        site.getName(), site.getId(), adapter.getSystemName());
  }
}
