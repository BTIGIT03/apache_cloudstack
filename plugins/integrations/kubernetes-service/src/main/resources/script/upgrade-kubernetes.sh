#!/bin/bash -e
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.

# Version 1.14 and below needs extra flags with kubeadm upgrade node
if [ $# -lt 4 ]; then
    echo "Invalid input. Valid usage: ./upgrade-kubernetes.sh UPGRADE_VERSION IS_CONTROL_NODE IS_OLD_VERSION IS_EJECT_ISO IS_EXTERNAL_CNI"
    echo "eg: ./upgrade-kubernetes.sh 1.16.3 true false false"
    exit 1
fi
UPGRADE_VERSION="${1}"
IS_MAIN_CONTROL=""
if [ $# -gt 1 ]; then
  IS_MAIN_CONTROL="${2}"
fi
IS_OLD_VERSION=""
if [ $# -gt 2 ]; then
  IS_OLD_VERSION="${3}"
fi
EJECT_ISO_FROM_OS=false
if [ $# -gt 3 ]; then
  EJECT_ISO_FROM_OS="${4}"
fi
EXTERNAL_CNI=false
if [ $# -gt 4 ]; then
  EXTERNAL_CNI="${5}"
fi

export PATH=$PATH:/opt/bin
if [[ "$PATH" != *:/usr/sbin && "$PATH" != *:/usr/sbin:* ]]; then
  export PATH=$PATH:/usr/sbin
fi

ISO_MOUNT_DIR=/mnt/k8sdisk
BINARIES_DIR=${ISO_MOUNT_DIR}/

OFFLINE_INSTALL_ATTEMPT_SLEEP=5
MAX_OFFLINE_INSTALL_ATTEMPTS=10
offline_attempts=1
iso_drive_path=""
while true; do
  if (( "$offline_attempts" > "$MAX_OFFLINE_INSTALL_ATTEMPTS" )); then
    echo "Warning: Offline install timed out!"
    break
  fi
  set +e
  output=`blkid -o device -t LABEL=CDROM`
  set -e
  if [ "$output" != "" ]; then
    while read -r line; do
      if [ ! -d "${ISO_MOUNT_DIR}" ]; then
        mkdir "${ISO_MOUNT_DIR}"
      fi
      retval=0
      set +e
      mount -o ro "${line}" "${ISO_MOUNT_DIR}"
      retval=$?
      set -e
      if [ $retval -eq 0 ]; then
        if [ -d "$BINARIES_DIR" ]; then
          iso_drive_path="${line}"
          break
        else
          umount "${line}" && rmdir "${ISO_MOUNT_DIR}"
        fi
      fi
    done <<< "$output"
  fi
  if [ -d "$BINARIES_DIR" ]; then
    break
  fi
  echo "Waiting for Binaries directory $BINARIES_DIR to be available, sleeping for $OFFLINE_INSTALL_ATTEMPT_SLEEP seconds, attempt: $offline_attempts"
  sleep $OFFLINE_INSTALL_ATTEMPT_SLEEP
  offline_attempts=$[$offline_attempts + 1]
done

if [ -d "$BINARIES_DIR" ]; then
  ### Binaries available offline ###
  echo "Installing binaries from ${BINARIES_DIR}"

  cd /opt/bin

  cp ${BINARIES_DIR}/k8s/kubeadm /opt/bin
  chmod +x kubeadm

  output=`ls ${BINARIES_DIR}/docker/`
  if [ "$output" != "" ]; then
    while read -r line; do
        ctr -n k8s.io image import "${BINARIES_DIR}/docker/$line"
    done <<< "$output"
  fi
  if [ -e "${BINARIES_DIR}/provider.yaml" ]; then
    mkdir -p /opt/provider
    cp "${BINARIES_DIR}/provider.yaml" /opt/provider/provider.yaml
  fi

  # Fetch the autoscaler if present
  if [ -e "${BINARIES_DIR}/autoscaler.yaml" ]; then
    mkdir -p /opt/autoscaler
    cp "${BINARIES_DIR}/autoscaler.yaml" /opt/autoscaler/autoscaler_tmpl.yaml
  fi

  PAUSE_IMAGE=`ctr -n k8s.io images ls -q | grep "pause" | sort | tail -n 1`
  echo $PAUSE_IMAGE
  if [ -n "$PAUSE_IMAGE" ]; then
    sed -i "s|sandbox_image = .*|sandbox_image = \"$PAUSE_IMAGE\"|g" /etc/containerd/config.toml
  fi

  tar -f "${BINARIES_DIR}/cni/cni-plugins-"*64.tgz -C /opt/cni/bin -xz
  tar -f "${BINARIES_DIR}/cri-tools/crictl-linux-"*64.tar.gz -C /opt/bin -xz

  if [ "${IS_MAIN_CONTROL}" == 'true' ]; then
    set +e
    kubeadm --v=5 upgrade apply ${UPGRADE_VERSION} -y
    retval=$?
    set -e
    if [ $retval -ne 0 ]; then
      kubeadm --v=5 upgrade apply ${UPGRADE_VERSION} --ignore-preflight-errors=CoreDNSUnsupportedPlugins -y
    fi
  else
    if [ "${IS_OLD_VERSION}" == 'true' ]; then
      kubeadm --v=5 upgrade node config --kubelet-version ${UPGRADE_VERSION}
    else
      kubeadm --v=5 upgrade node
    fi
  fi

  systemctl stop kubelet
  cp -a ${BINARIES_DIR}/k8s/{kubelet,kubectl} /opt/bin
  chmod +x /opt/bin/{kubelet,kubectl}

  systemctl daemon-reload
  systemctl restart containerd
  systemctl restart kubelet

  if [ "${IS_MAIN_CONTROL}" == 'true' ]; then
    if [[ ${EXTERNAL_CNI} == true ]]; then
      /opt/bin/kubectl apply -f ${BINARIES_DIR}/network.yaml
    fi
    /opt/bin/kubectl apply -f ${BINARIES_DIR}/dashboard.yaml
  fi

  umount "${ISO_MOUNT_DIR}" && rmdir "${ISO_MOUNT_DIR}"
  if [ "$EJECT_ISO_FROM_OS" = true ] && [ "$iso_drive_path" != "" ]; then
    eject "${iso_drive_path}"
  fi
else
  echo "ERROR: Unable to access Binaries directory for upgrade version ${UPGRADE_VERSION}"
  exit 1
fi
