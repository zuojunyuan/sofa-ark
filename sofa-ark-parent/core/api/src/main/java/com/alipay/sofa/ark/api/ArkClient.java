/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alipay.sofa.ark.api;

import com.alipay.sofa.ark.common.log.ArkLogger;
import com.alipay.sofa.ark.common.log.ArkLoggerFactory;
import com.alipay.sofa.ark.common.util.AssertUtils;
import com.alipay.sofa.ark.common.util.BizIdentityUtils;
import com.alipay.sofa.ark.spi.model.Biz;
import com.alipay.sofa.ark.spi.model.BizInfo;
import com.alipay.sofa.ark.spi.model.BizState;
import com.alipay.sofa.ark.spi.service.biz.BizFactoryService;
import com.alipay.sofa.ark.spi.service.biz.BizManagerService;
import com.alipay.sofa.ark.spi.service.injection.InjectionService;

import java.io.File;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * API used to operate biz
 *
 * @author qilong.zql
 * @since 0.6.0
 */
public class ArkClient {

    private static ArkLogger         LOGGER = ArkLoggerFactory.getDefaultLogger();
    private static BizManagerService bizManagerService;
    private static BizFactoryService bizFactoryService;
    private static InjectionService  injectionService;
    private static String[]          arguments;

    public static InjectionService getInjectionService() {
        return injectionService;
    }

    public static void setInjectionService(InjectionService injectionService) {
        ArkClient.injectionService = injectionService;
    }

    public static BizManagerService getBizManagerService() {
        return bizManagerService;
    }

    public static void setBizManagerService(BizManagerService bizManagerService) {
        ArkClient.bizManagerService = bizManagerService;
    }

    public static BizFactoryService getBizFactoryService() {
        return bizFactoryService;
    }

    public static void setBizFactoryService(BizFactoryService bizFactoryService) {
        ArkClient.bizFactoryService = bizFactoryService;
    }

    public static String[] getArguments() {
        return arguments;
    }

    public static void setArguments(String[] arguments) {
        ArkClient.arguments = arguments;
    }

    /**
     * Install Biz throw file
     *
     * @param bizFile
     * @throws Throwable
     */
    public static ClientResponse installBiz(File bizFile) throws Throwable {
        AssertUtils.assertNotNull(bizFactoryService, "bizFactoryService must not be null!");
        AssertUtils.assertNotNull(bizManagerService, "bizFactoryService must not be null!");
        AssertUtils.assertNotNull(bizFile, "bizFile must not be null!");

        Biz biz = bizFactoryService.createBiz(bizFile);
        ClientResponse response = new ClientResponse();
        if (bizManagerService.getBizByIdentity(biz.getIdentity()) != null
            || !bizManagerService.registerBiz(biz)) {
            return response.setCode(ResponseCode.REPEAT_BIZ).setMessage(
                String.format("Biz: %s has been installed or registered.", biz.getIdentity()));
        }

        try {
            biz.start(arguments);
            response.setCode(ResponseCode.SUCCESS)
                .setMessage(String.format("Install Biz: %s success.", biz.getIdentity()))
                .setBizInfos(Collections.<BizInfo> singleton(biz));
            LOGGER.info(response.getMessage());
            return response;
        } catch (Throwable throwable) {
            response.setCode(ResponseCode.FAILED).setMessage(
                String.format("Install Biz: %s fail.", biz.getIdentity()));
            LOGGER.error(response.getMessage(), throwable);

            bizManagerService.unRegisterBizStrictly(biz.getBizName(), biz.getBizVersion());
            try {
                biz.stop();
            } catch (Throwable e) {
                LOGGER.error(String.format("UnInstall Biz: %s fail.", biz.getIdentity()), e);
                throw e;
            }
            return response;
        }
    }

    /**
     * Uninstall biz.
     *
     * @param bizName
     * @param bizVersion
     * @return
     * @throws Throwable
     */
    public static ClientResponse uninstallBiz(String bizName, String bizVersion) throws Throwable {
        AssertUtils.assertNotNull(bizFactoryService, "bizFactoryService must not be null!");
        AssertUtils.assertNotNull(bizManagerService, "bizFactoryService must not be null!");
        AssertUtils.assertNotNull(bizName, "bizName must not be null!");
        AssertUtils.assertNotNull(bizVersion, "bizVersion must not be null!");

        Biz biz = bizManagerService.unRegisterBiz(bizName, bizVersion);
        ClientResponse response = new ClientResponse().setCode(ResponseCode.NOT_FOUND_BIZ)
            .setMessage(
                String.format("Uninstall biz: %s not found.",
                    BizIdentityUtils.generateBizIdentity(bizName, bizVersion)));
        if (biz != null) {
            try {
                biz.stop();
            } catch (Throwable throwable) {
                LOGGER
                    .error(String.format("UnInstall Biz: %s fail.", biz.getIdentity()), throwable);
                throw throwable;
            }
            response.setCode(ResponseCode.SUCCESS).setMessage(
                String.format("Uninstall biz: %s success.", biz.getIdentity()));
        }
        LOGGER.info(response.getMessage());
        return response;
    }

    /**
     * Check all {@link com.alipay.sofa.ark.spi.model.BizInfo}
     *
     * @return
     */
    public static ClientResponse checkBiz() {
        return checkBiz(null, null);
    }

    /**
     * Check all {@link com.alipay.sofa.ark.spi.model.BizInfo} with specified bizName
     *
     * @param bizName
     * @return
     */
    public static ClientResponse checkBiz(String bizName) {
        return checkBiz(bizName, null);
    }

    /**
     * Check all {@link com.alipay.sofa.ark.spi.model.BizInfo} with specified bizName and bizVersion
     *
     * @param bizName
     * @param bizVersion
     * @return
     */
    public static ClientResponse checkBiz(String bizName, String bizVersion) {
        AssertUtils.assertNotNull(bizFactoryService, "bizFactoryService must not be null!");
        AssertUtils.assertNotNull(bizManagerService, "bizFactoryService must not be null!");

        ClientResponse response = new ClientResponse();
        Set<BizInfo> bizInfoSet = new HashSet<>();
        if (bizName != null && bizVersion != null) {
            Biz biz = bizManagerService.getBiz(bizName, bizVersion);
            if (biz != null) {
                bizInfoSet.add(biz);
            }
        } else if (bizName != null) {
            bizInfoSet.addAll(bizManagerService.getBiz(bizName));
        } else {
            bizInfoSet.addAll(bizManagerService.getBizInOrder());
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Biz count=%d", bizInfoSet.size())).append("\n");
        for (BizInfo bizInfo : bizInfoSet) {
            sb.append(
                String.format("bizName=%s, bizVersion=%s, bizState=%s", bizInfo.getBizName(),
                    bizInfo.getBizVersion(), bizInfo.getBizState())).append("\n");
        }
        response.setCode(ResponseCode.SUCCESS).setBizInfos(bizInfoSet).setMessage(sb.toString());
        LOGGER.info(String.format("Check Biz: %s", response.getMessage()));
        return response;
    }

    /**
     * Active biz with specified bizName and bizVersion
     *
     * @param bizName
     * @param bizVersion
     * @return
     */
    public static ClientResponse switchBiz(String bizName, String bizVersion) {
        AssertUtils.assertNotNull(bizFactoryService, "bizFactoryService must not be null!");
        AssertUtils.assertNotNull(bizManagerService, "bizFactoryService must not be null!");
        AssertUtils.assertNotNull(bizName, "bizName must not be null!");
        AssertUtils.assertNotNull(bizVersion, "bizVersion must not be null!");

        Biz biz = bizManagerService.getBiz(bizName, bizVersion);
        ClientResponse response = new ClientResponse().setCode(ResponseCode.NOT_FOUND_BIZ)
            .setMessage(
                String.format("Switch biz: %s not found.",
                    BizIdentityUtils.generateBizIdentity(bizName, bizVersion)));
        if (biz != null) {
            if (biz.getBizState() != BizState.ACTIVATED
                && biz.getBizState() != BizState.DEACTIVATED) {
                response.setCode(ResponseCode.ILLEGAL_STATE_BIZ).setMessage(
                    String.format("Switch Biz: %s's state must not be %s.", biz.getIdentity(),
                        biz.getBizState()));
            } else {
                bizManagerService.activeBiz(bizName, bizVersion);
                response.setCode(ResponseCode.SUCCESS).setMessage(
                    String.format("Switch biz: %s is activated.", biz.getIdentity()));
            }
        }
        LOGGER.info(response.getMessage());
        return response;
    }
}