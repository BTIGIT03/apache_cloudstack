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

import javax.inject.Inject;

import org.springframework.stereotype.Component;

import com.cloud.utils.db.GenericDaoBase;
import com.cloud.utils.db.SearchBuilder;
import com.cloud.utils.db.SearchCriteria;

@Component
public class Site2SiteVpnGatewayDaoImpl extends GenericDaoBase<Site2SiteVpnGatewayVO, Long> implements Site2SiteVpnGatewayDao {
    @Inject
    protected IPAddressDao _addrDao;


    private final SearchBuilder<Site2SiteVpnGatewayVO> AllFieldsSearch;

    protected Site2SiteVpnGatewayDaoImpl() {
        AllFieldsSearch = createSearchBuilder();
        AllFieldsSearch.and("vpcId", AllFieldsSearch.entity().getVpcId(), SearchCriteria.Op.EQ);
        AllFieldsSearch.and("ipAddressId", AllFieldsSearch.entity().getAddrId(), SearchCriteria.Op.EQ);
        AllFieldsSearch.done();
    }

    @Override
    public Site2SiteVpnGatewayVO findByVpcId(long vpcId) {
        SearchCriteria<Site2SiteVpnGatewayVO> sc = AllFieldsSearch.create();
        sc.setParameters("vpcId", vpcId);
        return findOneBy(sc);
    }

    @Override
    public Site2SiteVpnGatewayVO findByPublicIpAddress(long ipAddressId) {
        SearchCriteria<Site2SiteVpnGatewayVO> sc = AllFieldsSearch.create();
        sc.setParameters("ipAddressId", ipAddressId);
        return findOneBy(sc);
    }
}
