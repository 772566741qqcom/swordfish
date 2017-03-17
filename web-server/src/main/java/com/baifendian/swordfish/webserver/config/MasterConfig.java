/*
 * Copyright (C) 2017 Baifendian Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.baifendian.swordfish.webserver.config;

import com.bfd.harpc.common.configure.PropertiesConfiguration;

/**
 * Master 配置信息 <p>
 *
 * @author : dsfan
 * @date : 2016年10月28日
 */
public class MasterConfig {
  /**
   * 失败重试次数,默认为 2 次
   */
  public static int failRetryCount = PropertiesConfiguration.getValue("masterToWorker.failRetry.count", 2);

  /**
   * 失败重试的队列大小,默认为 10000
   */
  public static int failRetryQueueSize = PropertiesConfiguration.getValue("masterToWorker.failRetry.queueSize", 10000);
}