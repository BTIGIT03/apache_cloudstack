// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.
package com.cloud.cluster;

import java.rmi.RemoteException;

import org.apache.cloudstack.framework.config.ConfigKey;

import com.cloud.utils.component.Adapter;

public interface ClusterServiceAdapter extends Adapter {
    final ConfigKey<Integer> ClusterMessageTimeOut = new ConfigKey<Integer>(Integer.class, "cluster.message.timeout.seconds", "Advance", "300",
        "Time (in seconds) to wait before a inter-management server message post times out.", true);

    public ClusterService getPeerService(String strPeer) throws RemoteException;

    public int getServicePort();
}
