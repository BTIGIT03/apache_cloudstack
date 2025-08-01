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
package com.cloud.network.dao;

import com.cloud.network.element.NetrisProviderVO;
import com.cloud.utils.db.DB;
import com.cloud.utils.db.GenericDaoBase;
import com.cloud.utils.db.SearchBuilder;
import com.cloud.utils.db.SearchCriteria;
import org.springframework.stereotype.Component;

@Component
@DB()
public class NetrisProviderDaoImpl extends GenericDaoBase<NetrisProviderVO, Long> implements NetrisProviderDao {

    final SearchBuilder<NetrisProviderVO> allFieldsSearch;

    public NetrisProviderDaoImpl() {
        super();
        allFieldsSearch = createSearchBuilder();
        allFieldsSearch.and("id", allFieldsSearch.entity().getId(),
                SearchCriteria.Op.EQ);
        allFieldsSearch.and("uuid", allFieldsSearch.entity().getUuid(),
                SearchCriteria.Op.EQ);
        allFieldsSearch.and("hostname", allFieldsSearch.entity().getUrl(),
                SearchCriteria.Op.EQ);
        allFieldsSearch.and("zone_id", allFieldsSearch.entity().getZoneId(),
                SearchCriteria.Op.EQ);
        allFieldsSearch.done();
    }

    @Override
    public NetrisProviderVO findByZoneId(long zoneId) {
        SearchCriteria<NetrisProviderVO> sc = allFieldsSearch.create();
        sc.setParameters("zone_id", zoneId);
        return findOneBy(sc);
    }
}
