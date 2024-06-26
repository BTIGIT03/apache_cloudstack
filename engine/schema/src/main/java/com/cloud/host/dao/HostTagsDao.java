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
package com.cloud.host.dao;

import java.util.List;

import com.cloud.host.HostTagVO;
import com.cloud.utils.db.GenericDao;
import org.apache.cloudstack.api.response.HostTagResponse;
import org.apache.cloudstack.framework.config.ConfigKey;

public interface HostTagsDao extends GenericDao<HostTagVO, Long> {

    ConfigKey<Long> hostTagRuleExecutionTimeout = new ConfigKey<>("Advanced", Long.class, "host.tag.rule.execution.timeout", "2000", "The maximum runtime, in milliseconds, " +
        "to execute a host tag rule; if it is reached, a timeout will happen.", true);

    void persist(long hostId, List<String> hostTags, Boolean isTagARule);

    List<HostTagVO> getHostTags(long hostId);

    List<String> getDistinctImplicitHostTags(List<Long> hostIds, String[] implicitHostTags);

    void deleteTags(long hostId);

    boolean updateImplicitTags(long hostId, List<String> hostTags);

    List<HostTagVO> getExplicitHostTags(long hostId);

    List<HostTagVO> findHostRuleTags();

    HostTagResponse newHostTagResponse(HostTagVO hostTag);

    List<HostTagVO> searchByIds(Long... hostTagIds);
}
