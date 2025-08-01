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

package com.cloud.agent.api;

import org.apache.cloudstack.storage.volume.VolumeOnStorageTO;

import java.util.Map;

public class CheckVolumeAnswer extends Answer {

    private long size;
    private Map<VolumeOnStorageTO.Detail, String> volumeDetails;

    CheckVolumeAnswer() {
    }

    public CheckVolumeAnswer(CheckVolumeCommand cmd, final boolean success, String details, long size,
                             Map<VolumeOnStorageTO.Detail, String> volumeDetails) {
        super(cmd, success, details);
        this.size = size;
        this.volumeDetails = volumeDetails;
    }

    public long getSize() {
        return size;
    }

    public Map<VolumeOnStorageTO.Detail, String> getVolumeDetails() {
        return volumeDetails;
    }

    public String getString() {
        return "CheckVolumeAnswer [size=" + size + "]";
    }
}
