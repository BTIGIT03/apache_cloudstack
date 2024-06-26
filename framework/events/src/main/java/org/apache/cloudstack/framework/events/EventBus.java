/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.cloudstack.framework.events;

import java.util.UUID;

/**
 * Interface to publish and subscribe to CloudStack events
 *
 */
public interface EventBus {

    String getName();

    /**
     * publish an event on to the event bus
     *
     * @param event event that needs to be published on the event bus
     */
    void publish(Event event) throws EventBusException;

    /**
     * subscribe to events that matches specified event topics
     *
     * @param topic defines category and type of the events being subscribed to
     * @param subscriber subscriber that intends to receive event notification
     * @return UUID returns the subscription ID
     */
    UUID subscribe(EventTopic topic, EventSubscriber subscriber) throws EventBusException;

    /**
     * unsubscribe to events of a category and a type
     *
     * @param subscriber subscriber that intends to unsubscribe from the event notification
     */
    void unsubscribe(UUID subscriberId, EventSubscriber subscriber) throws EventBusException;

}
