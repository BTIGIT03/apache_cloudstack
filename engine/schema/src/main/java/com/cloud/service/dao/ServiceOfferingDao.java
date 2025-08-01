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
package com.cloud.service.dao;

import java.util.List;
import java.util.Map;

import com.cloud.service.ServiceOfferingVO;
import com.cloud.storage.Storage.ProvisioningType;
import com.cloud.utils.db.GenericDao;
import com.cloud.utils.db.SearchBuilder;
import com.cloud.vm.VirtualMachine;

/*
 * Data Access Object for service_offering table
 */
public interface ServiceOfferingDao extends GenericDao<ServiceOfferingVO, Long> {
    ServiceOfferingVO findByName(String name);

    List<ServiceOfferingVO> createSystemServiceOfferings(String name, String uniqueName, int cpuCount, int ramSize, int cpuSpeed,
            Integer rateMbps, Integer multicastRateMbps, boolean offerHA, String displayText, ProvisioningType provisioningType,
            boolean recreatable, String tags, boolean systemUse, VirtualMachine.Type vmType, boolean defaultUse);

    ServiceOfferingVO persistSystemServiceOffering(ServiceOfferingVO vo);

    ServiceOfferingVO persistDeafultServiceOffering(ServiceOfferingVO offering);

    void loadDetails(ServiceOfferingVO serviceOffering);

    void saveDetails(ServiceOfferingVO serviceOffering);

    ServiceOfferingVO findById(Long vmId, long serviceOfferingId);

    ServiceOfferingVO findByIdIncludingRemoved(Long vmId, long serviceOfferingId);

    boolean isDynamic(long serviceOfferingId);

    ServiceOfferingVO getComputeOffering(ServiceOfferingVO serviceOffering, Map<String, String> customParameters);

    ServiceOfferingVO findDefaultSystemOffering(String offeringName, Boolean useLocalStorage);

    List<ServiceOfferingVO> listPublicByCpuAndMemory(Integer cpus, Integer memory);

    ServiceOfferingVO findServiceOfferingByComputeOnlyDiskOffering(long diskOfferingId, boolean includingRemoved);

    List<Long> listIdsByHostTag(String tag);

    void addCheckForGpuEnabled(SearchBuilder<ServiceOfferingVO> serviceOfferingSearch, Boolean gpuEnabled);
}
