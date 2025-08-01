//
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
//

package org.apache.cloudstack.backup;

import com.cloud.agent.api.Command;
import com.cloud.agent.api.LogLevel;

public class GetBackupStorageStatsCommand extends Command {
    private String backupRepoType;
    private String backupRepoAddress;
    @LogLevel(LogLevel.Log4jLevel.Off)
    private String mountOptions;

    public GetBackupStorageStatsCommand(String backupRepoType, String backupRepoAddress, String mountOptions) {
        super();
        this.backupRepoType = backupRepoType;
        this.backupRepoAddress = backupRepoAddress;
        this.mountOptions = mountOptions;
    }

    public String getBackupRepoType() {
        return backupRepoType;
    }

    public void setBackupRepoType(String backupRepoType) {
        this.backupRepoType = backupRepoType;
    }

    public String getBackupRepoAddress() {
        return backupRepoAddress;
    }

    public void setBackupRepoAddress(String backupRepoAddress) {
        this.backupRepoAddress = backupRepoAddress;
    }

    public String getMountOptions() {
        return mountOptions == null ? "" : mountOptions;
    }

    public void setMountOptions(String mountOptions) {
        this.mountOptions = mountOptions;
    }

    @Override
    public boolean executeInSequence() {
        return true;
    }
}
